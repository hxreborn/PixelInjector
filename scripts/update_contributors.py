#!/usr/bin/env python3
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
from pathlib import Path
from urllib.request import Request, urlopen

REPO_API = "https://api.github.com/repos/hxreborn/PixelInjector"
SCRIPT_DIR = Path(__file__).resolve().parent
SNAPSHOT = SCRIPT_DIR.parent / "app/src/main/assets/contributors"
CONTRIBUTOR_STATS = "/stats/contributors"
GITHUB_LOGIN = re.compile(r"[A-Za-z0-9-]+")


class ContributorStatsPending(Exception):
    pass


def github_json(path=""):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "PixelInjector-contributors"}
    if token := os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(6):
        with urlopen(Request(REPO_API + path, headers=headers), timeout=30) as response:
            if response.status != 202:
                data = json.load(response)
                if data or path != CONTRIBUTOR_STATS:
                    return data
        if attempt < 5:
            time.sleep(2**attempt)
    if path == CONTRIBUTOR_STATS:
        raise ContributorStatsPending
    raise SystemExit("GitHub returned no repository metadata.")


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def validate_snapshot(converter):
    rows = json.loads((SNAPSHOT / "contributors.json").read_text(encoding="utf-8"))
    if not isinstance(rows, list) or not rows:
        raise ValueError("Bundled contributor list is empty or invalid")
    logins = set()
    owners = 0
    for row in rows:
        login = row["login"]
        avatar = row["avatar"]
        if (
            not GITHUB_LOGIN.fullmatch(login)
            or login.lower() in logins
            or row["url"] != f"https://github.com/{login}"
            or avatar != f"avatars/{login}.webp"
            or any(type(row[key]) is not int or row[key] < 0 for key in ("added", "removed"))
            or type(row.get("owner", False)) is not bool
        ):
            raise ValueError(f"Invalid bundled contributor: {login}")
        logins.add(login.lower())
        owners += row.get("owner", False)
        subprocess.run([converter, f"webp:{SNAPSHOT / avatar}", "null:"], check=True)
    if owners != 1:
        raise ValueError("Bundled contributor snapshot must contain one owner")


def update():
    converter = shutil.which("magick") or "convert"
    try:
        statistics = github_json(CONTRIBUTOR_STATS)
    except ContributorStatsPending:
        validate_snapshot(converter)
        print("GitHub contributor statistics are pending. Using the bundled snapshot.")
        return
    owner = github_json()["owner"]["login"]
    rows = []
    avatar_urls = {}
    for entry in statistics:
        author = entry["author"]
        if not author:
            continue
        login = author["login"]
        if login.lower() == "semantic-release-bot":
            continue
        if author["type"] == "Bot" or login.lower().endswith(("-bot", "[bot]")):
            continue
        if not GITHUB_LOGIN.fullmatch(login):
            raise ValueError(f"Invalid GitHub login: {login}")
        row = {
            "login": login,
            "added": sum(week["a"] for week in entry["weeks"]),
            "removed": sum(week["d"] for week in entry["weeks"]),
            "url": author["html_url"],
            "avatar": f"avatars/{login}.webp",
        }
        if login == owner:
            row["owner"] = True
        rows.append(row)
        avatar_urls[login] = author["avatar_url"]

    if not rows or owner not in avatar_urls:
        raise SystemExit("GitHub contributor statistics omitted the repository owner. Snapshot left unchanged.")
    rows.sort(key=lambda row: (row["login"] != owner, -row["added"], row["login"].lower()))

    SNAPSHOT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=SNAPSHOT.parent.parent) as temporary:
        staging = Path(temporary)
        (staging / "avatars").mkdir()
        for row in rows:
            login = row["login"]
            url = avatar_urls[login]
            output = staging / row["avatar"]
            separator = "&" if "?" in url else "?"
            with urlopen(f"{url}{separator}s=96", timeout=30) as response:
                subprocess.run(
                    [converter, "-", "-resize", "96x96>", "-strip", "-define", "webp:lossless=true", str(output)],
                    input=response.read(),
                    check=True,
                )

        write_json(staging / "contributors.json", rows)
        (SNAPSHOT / "avatars").mkdir(parents=True, exist_ok=True)
        for path in (staging / "avatars").iterdir():
            path.replace(SNAPSHOT / "avatars" / path.name)
        (staging / "contributors.json").replace(SNAPSHOT / "contributors.json")
        for path in (SNAPSHOT / "avatars").glob("*.webp"):
            if path.stem not in avatar_urls:
                path.unlink()


if __name__ == "__main__":
    update()
