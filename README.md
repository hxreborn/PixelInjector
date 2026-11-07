<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/banner_dark.png">
    <img src="assets/banner_light.png" alt="PixelInjector" width="545">
  </picture>
</h1>

[![libxposed API 102](https://img.shields.io/badge/libxposed_API-102-ff69b4?style=flat-square)](https://central.sonatype.com/artifact/io.github.libxposed/api)
[![Release](https://img.shields.io/github/v/release/hxreborn/PixelInjector?style=flat-square)](https://github.com/hxreborn/PixelInjector/releases/latest)
[![Android 14+](https://img.shields.io/badge/Android-14%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#install)

A modular Xposed module of Pixel tweaks, each behind its own switch. I built it for my own phone, but if a tweak is useful to you and breaks on your build, I can adapt or fix it on demand.

- **One switch per tweak.** Off tweaks change nothing.
- **Applies instantly.** Switches take effect live, and updates hot reload through libxposed API 102 with no reboot.

> [!NOTE]
> I build and test it on Pixel phones. On other ROMs a tweak can miss its target. For LineageOS and other ROMs with public source, I fix the tweak from that source. A closed ROM like HyperOS, One UI or ColorOS has no source to read, so the fix needs that ROM's own SystemUI or `services.jar`.

<details>
<summary><img src="assets/icons/photo_library.svg" width="20" alt=""> Screenshots</summary>

<table>
  <tr>
    <td><img src="https://raw.githubusercontent.com/hxreborn/PixelInjector/main/assets/screenshots/dashboard.webp" width="240" alt="Dashboard"></td>
    <td><img src="https://raw.githubusercontent.com/hxreborn/PixelInjector/main/assets/screenshots/settings.webp" width="240" alt="Settings"></td>
    <td><img src="https://raw.githubusercontent.com/hxreborn/PixelInjector/main/assets/screenshots/reports.webp" width="240" alt="Your reports"></td>
  </tr>
  <tr>
    <td><img src="https://raw.githubusercontent.com/hxreborn/PixelInjector/main/assets/screenshots/report.webp" width="240" alt="Report a bug"></td>
    <td><img src="https://raw.githubusercontent.com/hxreborn/PixelInjector/main/assets/screenshots/log.webp" width="240" alt="Module log"></td>
    <td><img src="https://raw.githubusercontent.com/hxreborn/PixelInjector/main/assets/screenshots/reload.webp" width="240" alt="Hot reload"></td>
  </tr>
</table>

</details>

## Install

Needs Android 14 or newer, arm64, root and an Xposed framework with libxposed API 102 (LSPosed 2.x or Vector).

1. Download the APK from [Releases](https://github.com/hxreborn/PixelInjector/releases/latest) or the [Xposed Modules Repo](https://github.com/Xposed-Modules-Repo/eu.hxreborn.pixelinjector/releases).
2. Enable PixelInjector in your Xposed manager and select the apps of the tweaks you need in its scope.
3. Reboot once. Later updates install from the app and reload without a reboot.

## Tweaks

### <img src="assets/icons/phone_android.svg" width="20" alt=""> System UI

- **Warm Tiles**: raises the limit of [3](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37) bound tiles in Quick Settings. Same hook as <img src="assets/modules/qs-boundless-tiles.png" width="16" alt=""> [qs-boundless-tiles](https://github.com/hxreborn/qs-boundless-tiles).
- **PillShot**: draws the app name on screenshots. Skips the selected apps or stamps only them. Revived from eXtreames/PillShot after its repo disappeared.
- **Mute screenshot sound**: skips the screenshot shutter sound.
- **Empty shade message**: replaces the empty shade's trophy and text with your own text and one of 141 icons. Android 17 only.
- **Always expand notifications**: opens notifications expanded unless you collapsed one by hand.
- **Pin ongoing notifications**: stops swiping away ongoing notifications like media and VPN.
- **Charging details**: adds watts, volts, amps and battery temperature under the lock screen charging text.
- **Download chip**: shows a download's progress as a chip in the status bar. Ported from [Iconify](https://github.com/Mahmud0808/Iconify)'s ongoing action chip.
- **Fingerprint icon**: replaces the lock screen fingerprint icon with one of 34 icons from custom ROMs or your own image and can hide the circle behind it. Phones with an under-display fingerprint sensor.
- **Hide shortcut circles**: removes the circle behind the lock screen shortcuts and keeps it while a shortcut is on, like the flashlight. The icons take the lock icon color on AOSP and keep their stock colors on One UI.
- **Heads-up duration**: sets how long a heads-up notification shows, from 1 to 15 seconds.
- **Double tap status bar to sleep**: double tap empty status bar space and the phone sleeps.
- **Biometric Bypass**: taps confirm on the biometric prompt after a face unlock and plays a system sound of your choice. Same hook as <img src="assets/modules/biometric-bypass.png" width="16" alt=""> [biometric-bypass](https://github.com/hxreborn/biometric-bypass).

### <img src="assets/icons/android.svg" width="20" alt=""> System Framework

- **Force NEW_TASK**: adds NEW_TASK to activity starts that match your rules, one `source:target` per line.
- **Clipboard in background**: allows selected apps to read the clipboard in the background.

### <img src="assets/icons/home.svg" width="20" alt=""> Launcher

- **Double tap to sleep**: double tap empty home screen space and the phone locks and sleeps. Pixel Launcher and Launcher3.
- **Hide gesture pill**: makes the navigation handle transparent and keeps a chosen percent of the bottom inset under apps. A percent change restarts the launcher.
- **Hide search bar**: hides the search bar above the hotseat and frees the row it reserves. Pixel Launcher and Launcher3.
- **Hide app shortcuts**: a long press on an app shows only the system actions. Pixel Launcher and Launcher3.
- **Home menu entry**: adds PixelInjector to the home screen long press menu. Pixel Launcher and Launcher3.

> [!WARNING]
> Double tap to sleep runs `su` from Pixel Launcher. With KernelSU and APatch, grant root to Pixel Launcher by hand in the manager, not only to PixelInjector. On KernelSU it shows up after you turn on Show system apps.

### <img src="assets/icons/keyboard.svg" width="20" alt=""> Gboard

- **AMOLED black keyboard**: makes the tinted grey keyboard surfaces pure black in dark mode. Stock Gboard and the Morphe build. Toggling restarts Gboard. Same hook as <img src="assets/modules/gboard-material-expressive-black.svg" width="16" alt=""> [gboard-material-expressive-black](https://github.com/hxreborn/gboard-material-expressive-black).

### <img src="assets/icons/call.svg" width="20" alt=""> Phone

- **Remove feedback chip**: removes the Help us improve chip from the call list and its notification after calls. The chip hook is the same as <img src="assets/modules/gdialer-dechip.png" width="16" alt=""> [gdialer-dechip](https://github.com/hxreborn/gdialer-dechip).

### <img src="assets/icons/folder.svg" width="20" alt=""> File picker

- **Force sort by newest first**: opens folders newest first in the file picker and the storage browser. Same hook as <img src="assets/modules/remember-my-sort.png" width="16" alt=""> [remember-my-sort](https://github.com/hxreborn/remember-my-sort).

### <img src="assets/icons/devices.svg" width="20" alt=""> KDE Connect

- **KDE Connect Quiet**: stops KDE Connect from holding the Wi-Fi multicast lock. Toggling restarts KDE Connect.

### <img src="assets/icons/github.svg" width="20" alt=""> GitHub

- **GH FastPass**: closes the GitHub app's approved 2FA dialog right away. Same hook as <img src="assets/modules/gh-fast-pass.svg" width="16" alt=""> [gh-fast-pass](https://github.com/hxreborn/gh-fast-pass).

### <img src="assets/icons/public.svg" width="20" alt=""> Firefox

- **Material You colors**: uses your wallpaper colors in Iceraven and Firefox (stable, Beta and Nightly). Private browsing takes dark red surfaces and the red error accent.
- **Material 3 loading indicator**: shows pull to refresh as the shape morphing Material 3 indicator in your wallpaper colors.
- **Menu style: Classic popup**: opens the three-dot menu as a popup laid out like Chrome's overflow menu, with the navigation buttons on top, in place of the bottom sheet. Also in IronFox.
- **Betterfox**: sets the [Betterfox](https://github.com/yokoffing/Betterfox) prefs that the Android engine reads, per browser, with each group and pref switchable. Toggling restarts the browser.

## Report a bug

Report from the app or on [GitHub](https://github.com/hxreborn/PixelInjector/issues/new/choose), whichever you prefer. App reports are opt-in and end-to-end encrypted. Nothing leaves the phone until you tap Send. On a phone that isn't a Pixel and doesn't run an open source ROM such as LineageOS, a report about a System UI or System Framework tweak also sends that ROM's SystemUI or `services.jar` code, encrypted with the report.

1. Open Settings, tap Report a bug, say what happened and send.
2. Follow it in Your reports. My questions and the GitHub issue show there.
3. The update that fixes it says Fixes your report.

Or reach me at [hxreborn@duck.com](mailto:hxreborn@duck.com) or on [Telegram](https://t.me/hxreb0rn).

<details>
<summary>Full specs</summary>

| Part | Detail |
| --- | --- |
| Scheme | [HPKE](https://www.rfc-editor.org/rfc/rfc9180) RFC 9180 base mode |
| Key exchange | DHKEM [X25519](https://www.rfc-editor.org/rfc/rfc7748) HKDF-SHA256 |
| Cipher | [AES-256-GCM](https://csrc.nist.gov/pubs/sp/800/38/d/final) |
| Encrypted | Message, module log, app versions, ROM code from phones that aren't Pixels |
| Not encrypted | Phone model, build, framework. Bound to the ciphertext as [associated data](https://www.rfc-editor.org/rfc/rfc9180#section-5.2) |
| Developer key | `3d76ec6a` |
| Reply key | X25519 per report, wrapped by [Android Keystore](https://developer.android.com/privacy-and-security/keystore) |
| Server | `reports.hxreborn.eu` holds no private key |
| Redacted log data | Emails, phone numbers, IP and MAC addresses, Wi-Fi names, device and advertising IDs, storage paths, URL and header tokens ([Redactor](app/src/main/kotlin/eu/hxreborn/pixelinjector/support/Redactor.kt)) |
| Retention | 90 days |
| Update check | On by default in Settings. A plain request every 6 hours with no device data |

</details>

## Credits

- [5ec1cff/MyInjector](https://github.com/5ec1cff/MyInjector) for the module structure, the hot reload flow and the NEW_TASK rule grammar.
- [tehcneko/clipboardwhitelist](https://github.com/tehcneko/clipboardwhitelist) for the clipboard hook idea.
- [Mahmud0808/PixelLauncherEnhanced](https://github.com/Mahmud0808/PixelLauncherEnhanced) for the gesture pill and search bar hooks.
- [Mahmud0808/Iconify](https://github.com/Mahmud0808/Iconify) for the ongoing action chip behind the download chip.
- [LuckyPray/DexKit](https://github.com/LuckyPray/DexKit) for the dex search library.
- [JingMatrix/Vector](https://github.com/JingMatrix/Vector) for the module log screen design.
- [Material Symbols](https://github.com/google/material-design-icons) for the empty shade icons (Apache 2.0).
- [Material Components for Android](https://github.com/material-components/material-components-android) for the loading indicator shapes and motion (Apache 2.0).
- [BlazeFTL/FTL-Patches](https://github.com/BlazeFTL/FTL-Patches) for the classic popup menu.
- AospExtended's UdfpsResources for the fingerprint icons, taken from [Syberia](https://github.com/syberia-project/platform_vendor_syberia), [DerpFest](https://github.com/DerpFest-AOSP/vendor_derp), [AfterlifeOS](https://github.com/AfterlifeOS/packages_overlays_Themes), [Infinity-X](https://github.com/ProjectInfinity-X/packages_overlays_Themes), [AlphaDroid](https://github.com/AlphaDroid-Project/vendor_addons), [crDroid](https://github.com/crdroidandroid/android_vendor_addons) and [AospExtended](https://github.com/AospExtended/platform_vendor_aosp).

## License

[![GPL-3.0-only](https://img.shields.io/badge/LICENSE-GPL--3.0--only-%23A42E2B?style=for-the-badge&logo=gnu&logoColor=white&logoPosition=right)](https://github.com/hxreborn/PixelInjector/blob/main/LICENSE)
