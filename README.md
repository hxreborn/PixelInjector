# PixelInjector

Opinionated Xposed module of some tweaks, specially for Pixel phones so I can avoid having twenty modules installed .

### System UI

- Warm Tiles: raises the limit of [3](https://android.googlesource.com/platform/frameworks/base/+/d5a204f16e7c71ffdbc6c8307a4134dcc1efd60d/packages/SystemUI/src/com/android/systemui/qs/external/TileServices.java#37) bound tiles in Quick Settings. Same hook as [qs-boundless-tiles](https://github.com/hxreborn/qs-boundless-tiles).
- PillShot: draws the app name on screenshots. Revived from eXtreames/PillShot after its repo disappeared.
- Mute screenshot sound: skips the screenshot shutter sound.
- Always expand notifications: opens notifications expanded unless you collapsed one by hand.
- Double tap status bar to sleep: double tap empty status bar space and the phone sleeps.
- Biometric Bypass: taps confirm on the biometric prompt after a face unlock. Same hook as [biometric-bypass](https://github.com/hxreborn/biometric-bypass).

### System Framework

- Force NEW_TASK: adds NEW_TASK to activity starts that match a rule. Rules go one per line as `source:target[:ir,nd]`. `ir` also catches starts that expect a result and `nd` adds NEW_DOCUMENT.
- Clipboard in background: allows selected apps to read the clipboard in the background.

### Launcher

- Double tap to sleep: double tap empty home screen space and the phone sleeps. Pixel Launcher and Launcher3.
- Hide gesture pill: makes the navigation handle transparent and keeps a chosen percent of the bottom inset under apps. A percent change restarts the launcher.
- Hide search bar: hides the search bar above the hotseat and frees the row it reserves. Pixel Launcher and Launcher3.

### Gboard

- AMOLED black keyboard: makes the tinted grey keyboard surfaces pure black in dark mode. Stock Gboard and the Morphe build. Toggling restarts Gboard. Same hook as [gboard-material-expressive-black](https://github.com/hxreborn/gboard-material-expressive-black).

### Phone

- Remove feedback chip: removes the Help us improve chip from the call list. Same hook as [gdialer-dechip](https://github.com/hxreborn/gdialer-dechip).

### File picker

- Force sort by newest first: opens folders sorted by newest instead of by name in the system file picker and the storage browser. Both DocumentsUI builds. Same hook as [remember-my-sort](https://github.com/hxreborn/remember-my-sort).

## Credits

- [5ec1cff/MyInjector](https://github.com/5ec1cff/MyInjector) for the module structure, the hot reload flow and the NEW_TASK rule grammar.
- [tehcneko/clipboardwhitelist](https://github.com/tehcneko/clipboardwhitelist) for the clipboard hook idea.
- [Mahmud0808/PixelLauncherEnhanced](https://github.com/Mahmud0808/PixelLauncherEnhanced) for the gesture pill and search bar hooks.
- [LuckyPray/DexKit](https://github.com/LuckyPray/DexKit) for the dex search library.
- [JingMatrix/Vector](https://github.com/JingMatrix/Vector) for the module log screen design.

## License

[![GPL-3.0-only](https://img.shields.io/badge/LICENSE-GPL--3.0--only-%23A42E2B?style=for-the-badge&logo=gnu&logoColor=white&logoPosition=right)](https://github.com/hxreborn/PixelInjector/blob/main/LICENSE)
