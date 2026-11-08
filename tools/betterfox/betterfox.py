import argparse
import io
import json
import re
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

REPO = "yokoffing/Betterfox"
REPO_URL = f"https://github.com/{REPO}"
RAW = f"https://raw.githubusercontent.com/{REPO}/"
ICERAVEN_LATEST = "https://api.github.com/repos/fork-maintainers/iceraven-browser/releases/latest"
GECKO_REPO_URL = "https://github.com/mozilla-firefox/firefox"
GECKO_RAW = "https://raw.githubusercontent.com/mozilla-firefox/firefox/"
STATIC_PREFS = "modules/libpref/init/StaticPrefList.yaml"
GROUPS = ["Fastfox", "Securefox", "Peskyfox", "Smoothfox"]
SCHEMA = 2
NOTE_MAX = 90
HERE = Path(__file__).resolve().parent
DEFAULT_OUT = HERE.parents[1] / "app/src/main/assets/betterfox.json"

PREF_LINE = re.compile(r'^\s*user_pref\("([^"]+)",\s*(.+?)\);\s*(?://\s*(.*))?$')
COMMENT_LINE = re.compile(r"^\s*//\s?(.*)$")
TAG_LINE = re.compile(r"^\[([A-Z][A-Z-]{1,20})\]\s*(.*)$")
INLINE_TAG = re.compile(r"\s*\[([A-Z][A-Z-]{1,20})\]\s*")
NOISE = re.compile(r"\s*\[(?:FF\d+\+?|\d+)\]")
LINK_LINE = re.compile(r"^\[\d+\]\s*(https?://\S+)")
DEFAULT_NOTE = re.compile(r"^default[=:][^;]*;?\s*", re.I)
ABOUT_MAX = 1200
PLAIN_QUOTES = str.maketrans({"\u2018": "'", "\u2019": "'", "\u201c": '"', "\u201d": '"'})
DEFAULT_LINE = re.compile(r'^\s*(?:sticky_)?pref\("([^"]+)",\s*(.+?)\);', re.M)
HEADER_LINE = re.compile(r"^\s*//\s*PREF:\s*(.+?)\s*$")
ABI = "arm64-v8a"
APP_VERSION = re.compile(r'MOZ_APP_VERSION:\s*"([^"]+)"')
STATIC_DEFINES = {"ANDROID", "MOZ_WIDGET_ANDROID", "RELEASE_OR_BETA", "MOZILLA_OFFICIAL"}
STATIC_FLAGS = {
    "IS_ANDROID": True,
    "IS_NOT_ANDROID": False,
    "IS_NIGHTLY_BUILD": False,
    "IS_NOT_NIGHTLY_BUILD": True,
    "IS_EARLY_BETA_OR_EARLIER": False,
    "IS_NOT_EARLY_BETA_OR_EARLIER": True,
    "IS_XP_MACOSX": False,
    "IS_NOT_XP_MACOSX": True,
    "IS_XP_WIN": False,
    "IS_NOT_XP_WIN": True,
}


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def latest_tag():
    out = subprocess.run(
        ["git", "ls-remote", "--tags", "--refs", REPO_URL],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    tags = []
    for line in out.splitlines():
        sha, ref = line.split("\t")
        name = ref.removeprefix("refs/tags/")
        if re.fullmatch(r"\d+(\.\d+)*", name):
            tags.append((tuple(int(part) for part in name.split(".")), name, sha))
    if not tags:
        sys.exit("no numeric Betterfox tag")
    _, name, sha = max(tags)
    return name, sha


def tag_sha(tag):
    out = subprocess.run(
        ["git", "ls-remote", "--tags", "--refs", REPO_URL, tag],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.split()
    if not out:
        sys.exit(f"no Betterfox tag {tag}")
    return out[0]


def parse_value(raw):
    raw = raw.strip()
    if raw in ("true", "false"):
        return raw == "true"
    if re.fullmatch(r"-?\d+", raw):
        return int(raw)
    if len(raw) >= 2 and raw[0] == raw[-1] == '"':
        return json.loads(raw)
    return None


def note_of(header):
    text = re.sub(r"\s*\[[^\]]*\]", "", header)
    text = re.sub(r"\s*\([^)]*\)\s*$", "", text).split(" - ")[0].strip().rstrip(".")
    text = text[:1].upper() + text[1:]
    return text if len(text) <= NOTE_MAX else text[: NOTE_MAX - 1].rstrip() + "…"


def about_of(block, inline):
    text, tags, links = [], [], []
    current = None
    for raw in block:
        line = raw.strip()
        if not line or line.startswith("user_pref(") or line.startswith("*"):
            current = None
            continue
        link = LINK_LINE.match(line)
        if link:
            links.append(link.group(1))
            current = None
            continue
        tag = TAG_LINE.match(line)
        if tag:
            current = [tag.group(1), tag.group(2)]
            tags.append(current)
            continue
        if current is not None and not re.search(r"[.!?]$", current[1]):
            current[1] = f"{current[1]} {line}".strip()
        else:
            text.append(line)
    about = {}
    parts = INLINE_TAG.split(NOISE.sub("", " ".join(text)).strip())
    body = parts[0].strip()
    tags = [[t, NOISE.sub("", x).strip()] for t, x in tags] + [[parts[i], parts[i + 1].strip()] for i in range(1, len(parts) - 1, 2)]
    if body:
        about["text"] = body[:ABOUT_MAX]
    if tags:
        about["tags"] = [{"tag": t, "text": x[:ABOUT_MAX]} for t, x in tags if x]
    if links:
        about["links"] = links
    note = NOISE.sub("", DEFAULT_NOTE.sub("", (inline or "").strip())).strip()
    if note:
        about["inline"] = note[:ABOUT_MAX]
    return about


def parse_file(group, source):
    header = None
    block = []
    prefs = []
    for number, line in enumerate(source.splitlines(), start=1):
        match = HEADER_LINE.match(line)
        if match:
            header = match.group(1)
            block = []
            continue
        comment = COMMENT_LINE.match(line)
        if comment and header is not None and not PREF_LINE.match(line):
            block.append(comment.group(1))
            continue
        match = PREF_LINE.match(line)
        if not match or header is None:
            continue
        value = parse_value(match.group(2))
        if value is None:
            continue
        prefs.append(
            {
                "pref": match.group(1),
                "value": value,
                "group": group,
                "note": note_of(header),
                "file": f"{group}.js",
                "line": number,
                "about": about_of(block, match.group(3)),
            }
        )
    return prefs


def download_apk(directory):
    release = json.loads(fetch(ICERAVEN_LATEST))
    asset = next(a for a in release["assets"] if a["name"].endswith("arm64-v8a.apk"))
    target = Path(directory) / asset["name"]
    print(f"downloading {asset['name']}", file=sys.stderr)
    target.write_bytes(fetch(asset["browser_download_url"]))
    return target


def static_condition(expression, defines):
    expression = re.sub(r"defined\s*\(?\s*(\w+)\s*\)?", r"\1", expression.strip())
    python = []
    for word, operator in re.findall(r"(\w+)|(&&|\|\||!|\(|\))", expression):
        if word:
            python.append(str(word in defines))
        else:
            python.append({"&&": " and ", "||": " or ", "!": " not "}.get(operator, operator))
    return bool(eval("".join(python), {"__builtins__": {}}))


def static_value(raw, pref):
    raw = re.sub(r"\s+#.*$", "", raw).strip()
    flag = re.fullmatch(r"@(\w+)@", raw)
    if flag:
        if flag.group(1) not in STATIC_FLAGS:
            sys.exit(f"unknown static flag {raw} for {pref}")
        return STATIC_FLAGS[flag.group(1)]
    value = parse_value(raw)
    if value is None:
        sys.exit(f"unparsed static value {raw!r} for {pref}")
    return value


def parse_static(source, names, defines=STATIC_DEFINES):
    stack, raw_values, current = [], {}, None
    for line in source.splitlines():
        text = line.strip()
        if text.startswith("#ifdef "):
            taken = text.split()[1] in defines
            stack.append([taken, taken])
        elif text.startswith("#ifndef "):
            taken = text.split()[1] not in defines
            stack.append([taken, taken])
        elif text.startswith("#if "):
            taken = static_condition(text[4:], defines)
            stack.append([taken, taken])
        elif text.startswith("#elif ") or text == "#else" or text.startswith("#else "):
            frame = stack[-1]
            taken = text.startswith("#else") or static_condition(text[6:], defines)
            frame[0] = not frame[1] and taken
            frame[1] = frame[1] or frame[0]
        elif text == "#endif" or text.startswith("#endif "):
            stack.pop()
        elif all(frame[0] for frame in stack):
            match = re.match(r"- name: (\S+)$", text)
            if match:
                current = match.group(1)
                continue
            match = re.match(r"value:\s*(.*)$", text)
            if match and current in names:
                raw_values[current] = match.group(1)
    return {name: static_value(raw, name) for name, raw in raw_values.items()}


def parse_defaults(source):
    defaults = {}
    for name, raw in DEFAULT_LINE.findall(source):
        value = parse_value(re.sub(r"\s*//.*$", "", raw))
        if value is not None:
            defaults[name] = value
    return defaults


class AndroidSurface:
    def __init__(self, apk):
        with zipfile.ZipFile(apk) as archive:
            self.engine = archive.read(f"lib/{ABI}/libxul.so")
            omni = zipfile.ZipFile(io.BytesIO(archive.read("assets/omni.ja")))
            self.shared = parse_defaults(omni.read(f"{ABI}/greprefs.js").decode(errors="replace"))
            self.android = parse_defaults(
                omni.read(f"defaults/pref/{ABI}/geckoview-prefs.js").decode(errors="replace")
            )
            self.extra = {}
            for name in omni.namelist():
                if re.fullmatch(r"defaults/pref/[^/]+\.js", name):
                    self.extra.update(parse_defaults(omni.read(name).decode(errors="replace")))
            match = APP_VERSION.search(omni.read("modules/AppConstants.sys.mjs").decode(errors="replace"))
            if not match:
                sys.exit("no MOZ_APP_VERSION in AppConstants.sys.mjs")
            self.gecko = match.group(1)
            self.dex = [
                archive.read(name)
                for name in archive.namelist()
                if re.fullmatch(r"classes\d*\.dex", name)
            ]

    def reads(self, pref):
        return pref in self.shared or pref in self.android or b"\0" + pref.encode() + b"\0" in self.engine

    def default(self, pref):
        return self.android.get(pref, self.shared.get(pref))

    def stock(self, pref, statics):
        for table in (self.android, self.extra, self.shared, statics):
            if pref in table:
                return table[pref]
        return None

    def app_sets(self, pref):
        name = pref.encode()
        needle = bytes([len(name)]) + name + b"\0"
        return len(name) < 128 and any(needle in dex for dex in self.dex)


def security_skip_reason(pref):
    if pref.startswith("browser.safebrowsing."):
        return "preserve Safe Browsing protections"
    if pref == "security.OCSP.enabled":
        return "preserve certificate revocation checks"
    if pref == "security.csp.reporting.enabled":
        return "preserve Content Security Policy violation reporting"
    return None


def skip_reason(pref, rules):
    security_reason = security_skip_reason(pref)
    if security_reason:
        return security_reason
    if pref in rules["prefs"]:
        return rules["prefs"][pref]
    for prefix, reason in rules["prefixes"].items():
        if pref.startswith(prefix):
            return reason
    return None


def gecko_tag(gecko):
    wanted = tuple(int(part) for part in gecko.split("."))
    out = subprocess.run(
        ["git", "ls-remote", "--tags", "--refs", GECKO_REPO_URL, f"FIREFOX_{wanted[0]}_*RELEASE"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    releases = []
    for line in out.splitlines():
        name = line.split("\t")[1].removeprefix("refs/tags/")
        match = re.fullmatch(r"FIREFOX_(\d+(?:_\d+)*)_RELEASE", name)
        if match:
            version = tuple(int(part) for part in match.group(1).split("_"))
            if version <= wanted:
                releases.append((version, name))
    if not releases:
        sys.exit(f"no Gecko release tag at or below {gecko}")
    return max(releases)[1]


def static_prefs(gecko):
    tag = gecko_tag(gecko)
    print(f"reading {STATIC_PREFS} at {tag}", file=sys.stderr)
    return fetch(f"{GECKO_RAW}{tag}/{STATIC_PREFS}").decode()


def revision_for(out, prefs, meta):
    if not out.exists():
        return 1
    previous = json.loads(out.read_text())
    same = previous["prefs"] == prefs and all(previous.get(k) == v for k, v in meta.items() if k != "source")
    return previous["revision"] if same else previous["revision"] + 1


def main():
    parser = argparse.ArgumentParser(description="Generate the Android Betterfox pref list")
    parser.add_argument("--apk", help="Iceraven arm64 APK that defines the Android pref surface; latest release when omitted")
    parser.add_argument("--ref", help="Betterfox tag, latest when omitted")
    parser.add_argument("--gecko", help="Gecko version for the stock values, the APK's MOZ_APP_VERSION when omitted")
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    args = parser.parse_args()

    tag, sha = (args.ref, tag_sha(args.ref)) if args.ref else latest_tag()
    sources = {group: fetch(f"{RAW}{sha}/{group}.js").decode().translate(PLAIN_QUOTES) for group in GROUPS}
    notice = fetch(f"{RAW}{sha}/LICENSE").decode().strip()
    rules = json.loads((HERE / "skip.json").read_text())

    with tempfile.TemporaryDirectory() as scratch:
        apk = Path(args.apk) if args.apk else download_apk(scratch)
        surface = AndroidSurface(apk)
        label = apk.stem

    kept, dropped, seen = [], {}, set()
    for group in GROUPS:
        for entry in parse_file(group, sources[group]):
            pref = entry["pref"]
            if pref in seen:
                continue
            seen.add(pref)
            reason = skip_reason(pref, rules)
            if reason is None and pref not in rules["keep"] and not surface.reads(pref):
                reason = "not read by the Android engine"
            if reason is None and surface.default(pref) == entry["value"]:
                reason = "already the Android default"
            if reason is None and pref in surface.android:
                reason = "Android sets its own value in geckoview-prefs.js"
            if reason is None and surface.app_sets(pref):
                reason = "set by the app"
            if reason:
                dropped.setdefault(reason, []).append(pref)
            else:
                kept.append(entry)

    gecko = args.gecko or surface.gecko
    statics = parse_static(static_prefs(gecko), {entry["pref"] for entry in kept})
    for entry in kept:
        entry["stock"] = surface.stock(entry["pref"], statics)

    meta = {"betterfox": tag, "commit": sha, "source": label, "gecko": gecko, "notice": notice}
    out = Path(args.out)
    document = {"schema": SCHEMA, "revision": revision_for(out, kept, meta), **meta, "prefs": kept}
    out.write_text(json.dumps(document, indent=1, ensure_ascii=False) + "\n")

    for reason, names in sorted(dropped.items()):
        print(f"dropped {len(names)}: {reason}", file=sys.stderr)
        print("  " + " ".join(names), file=sys.stderr)
    counts = {group: sum(1 for p in kept if p["group"] == group) for group in GROUPS}
    unset = [p["pref"] for p in kept if p["stock"] is None]
    print(f"stock unset {len(unset)}: {' '.join(unset)}", file=sys.stderr)
    print(f"wrote {len(kept)} prefs revision {document['revision']} betterfox {tag} gecko {gecko} {counts}", file=sys.stderr)


if __name__ == "__main__":
    main()
