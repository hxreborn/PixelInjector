## [1.24.4](https://github.com/hxreborn/PixelInjector/compare/v1.24.3...v1.24.4) (2026-11-09)

### Bug Fixes

* **tweak:** add ChargingDetails support for One UI 8 ([54d7eb9](https://github.com/hxreborn/PixelInjector/commit/54d7eb9e403a3d4bb313269292b3d39e44e8cbc3))
* **tweak:** add DownloadChip tint support for One UI 8 ([d0a2426](https://github.com/hxreborn/PixelInjector/commit/d0a2426c3aa089792014b6e1c99d32e9bcab634d))
* **tweak:** add HideShortcutCircles support for One UI 8 ([fa83025](https://github.com/hxreborn/PixelInjector/commit/fa830257b82a10dcac33d9382dc13cf0567c34a9))
* **tweak:** add PillShot support for One UI 8 ([e02f70d](https://github.com/hxreborn/PixelInjector/commit/e02f70ddd9056cdd41f07c94b2201ad893555189))
* **tweak:** add ScreenshotSound support for One UI 8 ([7114b77](https://github.com/hxreborn/PixelInjector/commit/7114b7728d4b61b298a1b231b06c7713ddd71e5b))
* **tweak:** back up FirefoxBetterfox profiles inside the browser ([e11c9c3](https://github.com/hxreborn/PixelInjector/commit/e11c9c31ed356d66415a005d4adef02fbedca754))
* **tweak:** exclude security prefs from the FirefoxBetterfox list ([349fe7e](https://github.com/hxreborn/PixelInjector/commit/349fe7e0414e388e9a13bc2401162f1f1cbb3c6c))
* **tweak:** report FingerprintIcon unavailable on One UI ([8d5ae55](https://github.com/hxreborn/PixelInjector/commit/8d5ae559c059219556e9c44e7fa0e5d0a65d4b20))
* **ui:** run test intent commands against the opened browser ([894b1f9](https://github.com/hxreborn/PixelInjector/commit/894b1f903a414fc8fe0c8888391b4425f9c112bf))

### Dependencies

* **deps:** bump androidx.compose.material3:material3 ([ce986d7](https://github.com/hxreborn/PixelInjector/commit/ce986d7e06092dca22d6283f457f04c1066339eb))
* **deps:** bump com.android.application from 9.4.1 to 9.5.0 ([3f49541](https://github.com/hxreborn/PixelInjector/commit/3f49541f22a538a1b6788e6d434b2144e3964608))
* **deps:** bump com.android.application from 9.5.0 to 9.5.1 ([449a8df](https://github.com/hxreborn/PixelInjector/commit/449a8df9d814768135c57cae550f9d4041c537a4))
* **deps:** update Android libraries and Gradle ([8b0f2d7](https://github.com/hxreborn/PixelInjector/commit/8b0f2d71142e0ef56403de2432e7bd2dd32672a3))

## [1.24.3](https://github.com/hxreborn/PixelInjector/compare/v1.24.2...v1.24.3) (2026-11-06)

### Bug Fixes

* **report:** link HeadsUpDuration and DownloadChip fixes ([1c28f66](https://github.com/hxreborn/PixelInjector/commit/1c28f66d21c6e6055baf09ffa7a65f8e51625e5b)), closes [#51](https://github.com/hxreborn/PixelInjector/issues/51) [#52](https://github.com/hxreborn/PixelInjector/issues/52)

## [1.24.2](https://github.com/hxreborn/PixelInjector/compare/v1.24.1...v1.24.2) (2026-11-06)

### Bug Fixes

* **tweak:** restore HeadsUpDuration on Android 15 ([0a79285](https://github.com/hxreborn/PixelInjector/commit/0a79285c9d2400d7915b08b59e75cbb14122e08d))
* **tweak:** support DownloadChip on custom ROMs ([a6d6eac](https://github.com/hxreborn/PixelInjector/commit/a6d6eac80c0b77676b13f00e536dcd8e12a7bd69))

## [1.24.1](https://github.com/hxreborn/PixelInjector/compare/v1.24.0...v1.24.1) (2026-11-02)

### Bug Fixes

* **tweak:** catch Exception and LinkageError in HideShortcutCircles ([d8d23e2](https://github.com/hxreborn/PixelInjector/commit/d8d23e2c4a914b81732df78f974584b4bca636cc))
* **tweak:** name the searched members in HideShortcutCircles errors ([128ee02](https://github.com/hxreborn/PixelInjector/commit/128ee029bfe64865b394ebb84103559535c3722c))

## [1.24.0](https://github.com/hxreborn/PixelInjector/compare/v1.23.0...v1.24.0) (2026-11-02)

### Features

* **tweak:** add HideShortcutCircles for lock screen shortcuts ([06cf694](https://github.com/hxreborn/PixelInjector/commit/06cf69443c9239cff850839f01e6e5de78844f8e)), closes [#50](https://github.com/hxreborn/PixelInjector/issues/50)

### Refactor

* **hook:** share RecomposeTrigger and proceedReplacing ([e3ba201](https://github.com/hxreborn/PixelInjector/commit/e3ba201fc5c9df712cbad8d336ab7af974b58842))

## [1.23.0](https://github.com/hxreborn/PixelInjector/compare/v1.22.1...v1.23.0) (2026-10-31)

### Features

* **tweak:** remove the Help us improve notification in DialerDechip ([d2efd80](https://github.com/hxreborn/PixelInjector/commit/d2efd809faf0fc93a7a9ebb472bd8dd037ea6ce6))

## [1.22.1](https://github.com/hxreborn/PixelInjector/compare/v1.22.0...v1.22.1) (2026-10-31)

### Bug Fixes

* **tweak:** call FingerprintIcon getIconState on the live view ([2218eef](https://github.com/hxreborn/PixelInjector/commit/2218eef81e7c189bc4003ed357f0f909c0c3f355)), closes [#47](https://github.com/hxreborn/PixelInjector/issues/47)
* **tweak:** resolve TaskbarHandle without mIsRegionDark on Android 14 ([be809c9](https://github.com/hxreborn/PixelInjector/commit/be809c99c1062c9ea08edf8558358c0f62822aff))

## [1.22.0](https://github.com/hxreborn/PixelInjector/compare/v1.21.0...v1.22.0) (2026-10-29)

### Features

* **tweak:** add support for IronFox in Firefox tweaks ([4427f09](https://github.com/hxreborn/PixelInjector/commit/4427f09a458711fbb04ef53ab92623807338f94d)), closes [#46](https://github.com/hxreborn/PixelInjector/issues/46)

### Bug Fixes

* **hook:** log install skips for processes without tweaks at debug ([a145982](https://github.com/hxreborn/PixelInjector/commit/a14598289cbfe7ad6e8a04e48f1bcff3827d5bdf))
* **reload:** hide processes without tweaks from hot reload ([77a5cf0](https://github.com/hxreborn/PixelInjector/commit/77a5cf04daf6774470e65351f3b9e30ee0b2419d))
* **tweak:** stop FirefoxClassicMenu crashing Iceraven 2.52.0 ([99a04c4](https://github.com/hxreborn/PixelInjector/commit/99a04c45de5637157312db34b4984e42fb3be571))
* **tweak:** strip DialerDechip chips from the Compose call log model ([916aebc](https://github.com/hxreborn/PixelInjector/commit/916aebce158ad1de2c0ece59e930cd1cec045486))

### Performance Improvements

* **log:** recompose only the subtitle while the module log scrolls ([c2d81a8](https://github.com/hxreborn/PixelInjector/commit/c2d81a8c51c66d4b493a7be69d4bb55f107eb493))

## [1.21.0](https://github.com/hxreborn/PixelInjector/compare/v1.20.0...v1.21.0) (2026-10-27)

### Features

* **tweak:** add FirefoxBetterfox with per pref sources and backups ([ca3aea3](https://github.com/hxreborn/PixelInjector/commit/ca3aea3ff7d8bdd0073f1da055c84d1dd9d3582a))
* **tweak:** add FirefoxClassicMenu popup menu for Iceraven and Firefox ([fc07da6](https://github.com/hxreborn/PixelInjector/commit/fc07da629fa5349a989c7780e4a4ecdfc713489a))
* **tweak:** apply Firefox tweak toggles without restarting the browser ([c24ce1f](https://github.com/hxreborn/PixelInjector/commit/c24ce1f415e5a4a85e61b31c87562d5f93187da4))
* **tweak:** set Firefox tweaks per browser with one section each ([29eea7a](https://github.com/hxreborn/PixelInjector/commit/29eea7af776152fb2e3f1326fb40d44acfe8531f))

### Bug Fixes

* **tweak:** keep FirefoxMaterialYou pure black only in the OLED palette ([e5d22f7](https://github.com/hxreborn/PixelInjector/commit/e5d22f74006fac232fdb1c3f04ed63290bb5f26a))
* **tweak:** map FirefoxMaterialYou tertiary roles to wallpaper primary ([3c3b704](https://github.com/hxreborn/PixelInjector/commit/3c3b70417847d039c3cd8222025a1333842ca71f))
* **tweak:** recolor FirefoxMaterialYou through a declared overlayable ([e0d4c8f](https://github.com/hxreborn/PixelInjector/commit/e0d4c8fefcc4b95de8acb032dfa97e463341d2e4))

## [1.20.0](https://github.com/hxreborn/PixelInjector/compare/v1.19.0...v1.20.0) (2026-10-25)

### Features

* **tweak:** add FirefoxLoadingIndicator for Iceraven ([070a66e](https://github.com/hxreborn/PixelInjector/commit/070a66e04919733634e15879f69b39caec8795a3))

### Bug Fixes

* **hook:** let hook failures propagate past tweak install catches ([d6a7fc5](https://github.com/hxreborn/PixelInjector/commit/d6a7fc5de00477c66e0f0ee6d970bc35b15d9578))

### Dependencies

* **deps:** bump aboutlibraries from 15.2.0 to 15.3.0 ([77bb115](https://github.com/hxreborn/PixelInjector/commit/77bb115537fffead2aa58f2316ee7f032b62f904))
* **deps:** bump androidx.compose.material3:material3 ([4996bdb](https://github.com/hxreborn/PixelInjector/commit/4996bdb52efa1eb42ba69f53fc3bb35758a2d82f))
* **deps:** bump gradle-wrapper from 9.8.1 to 9.8.2 ([73d42b1](https://github.com/hxreborn/PixelInjector/commit/73d42b1aeb3c912698e6e807de3bf5c4d7159a97))
* **deps:** bump org.jetbrains.kotlinx:kotlinx-serialization-core ([5abfc48](https://github.com/hxreborn/PixelInjector/commit/5abfc488a4d3be3851bae134aa5706c045dce757))

## [1.19.0](https://github.com/hxreborn/PixelInjector/compare/v1.18.0...v1.19.0) (2026-10-24)

### Features

* **report:** require root to send a report ([3df7e92](https://github.com/hxreborn/PixelInjector/commit/3df7e92950724300f38190b240fa7d5694b89132))
* **report:** show a notice when the module is not loaded ([6c3a4c9](https://github.com/hxreborn/PixelInjector/commit/6c3a4c90de02f76147d92a31446158b6956e13e0))

## [1.18.0](https://github.com/hxreborn/PixelInjector/compare/v1.17.0...v1.18.0) (2026-10-22)

### Features

* **tweak:** add FirefoxMaterialYou for Iceraven and Firefox ([#39](https://github.com/hxreborn/PixelInjector/issues/39)) ([871664a](https://github.com/hxreborn/PixelInjector/commit/871664a9079566eb95e74a00123abc8e0c122a62))

## [1.17.0](https://github.com/hxreborn/PixelInjector/compare/v1.16.0...v1.17.0) (2026-10-21)

### Features

* **update:** group changelog notes by type with area chips ([299abc3](https://github.com/hxreborn/PixelInjector/commit/299abc36df4f62077bf935bfb65a8ab13ace2ab5))

### Bug Fixes

* **tweak:** draw FingerprintIcon on the Android 14 UDFPS keyguard view ([50acb3c](https://github.com/hxreborn/PixelInjector/commit/50acb3cf464ce2073c25fa3abcb17a99c1d6c076)), closes [#38](https://github.com/hxreborn/PixelInjector/issues/38)

## [1.16.0](https://github.com/hxreborn/PixelInjector/compare/v1.15.0...v1.16.0) (2026-10-20)

### Features

* **ui:** animate the update check action swap in settings ([fdbf0d3](https://github.com/hxreborn/PixelInjector/commit/fdbf0d350d3f2ce2e470ce868f3bdbae79613fd5))
* **ui:** confirm or reject the manual update check with haptics ([629579b](https://github.com/hxreborn/PixelInjector/commit/629579bb884cbb3475721a8263687d64e765d155))
* **ui:** morph the reload result badge between cookie and sunny ([1b841f1](https://github.com/hxreborn/PixelInjector/commit/1b841f1b77f3e013a2b4c6bb99f180dd6cbb84f5))
* **ui:** morph the remaining buttons on press ([613e31f](https://github.com/hxreborn/PixelInjector/commit/613e31fe2ef4c57377334d25d64adbc4ced5beb4))
* **ui:** pick download chip options with a connected button group ([233e1c4](https://github.com/hxreborn/PixelInjector/commit/233e1c4826b755a20bbdc63f1e75ddfc15d64921))
* **ui:** resize the reload result chip as its status swaps ([3244224](https://github.com/hxreborn/PixelInjector/commit/324422422300bf929a0a3abe628a12006a68846f))
* **ui:** tick haptics when switching the app filter mode ([ecdedec](https://github.com/hxreborn/PixelInjector/commit/ecdedec914db775bb85fe9cbb07e341846d7ee25))

### Bug Fixes

* **ui:** keep the report delete confirmation across recreation ([2cc0dc8](https://github.com/hxreborn/PixelInjector/commit/2cc0dc87621bac9529e7eae002871e6bace0bfbe))

### Performance Improvements

* **ui:** draw the download chip preview progress without recomposing ([d260694](https://github.com/hxreborn/PixelInjector/commit/d260694451f2b588ddd1f25a111302590fc74cc8))

## [1.15.0](https://github.com/hxreborn/PixelInjector/compare/v1.14.0...v1.15.0) (2026-10-20)

### Features

* **ui:** open the github issue template chooser from about ([b1d0cb8](https://github.com/hxreborn/PixelInjector/commit/b1d0cb83b5ea0233e15501f0560cd0bbfd6c9cbf))

### Bug Fixes

* **ui:** separate changelog releases with cards instead of dividers ([f16af42](https://github.com/hxreborn/PixelInjector/commit/f16af4292f17f452aa98a573c0f3e33ce753b8e9))

## [1.14.0](https://github.com/hxreborn/PixelInjector/compare/v1.13.1...v1.14.0) (2026-10-20)

### Features

* **tweak:** add ten FingerprintIcon icons from crDroid and AospExtended ([80982aa](https://github.com/hxreborn/PixelInjector/commit/80982aad03c5f9c5f72216e0315fa2b2a1bfc7b0))

## [1.13.1](https://github.com/hxreborn/PixelInjector/compare/v1.13.0...v1.13.1) (2026-10-20)

### Bug Fixes

* **tweak:** draw FingerprintIcon picker icons on a lock screen backdrop ([5601896](https://github.com/hxreborn/PixelInjector/commit/5601896b48f9812fabf28d538c5604966a75d1bb))

## [1.13.0](https://github.com/hxreborn/PixelInjector/compare/v1.12.0...v1.13.0) (2026-10-20)

### Features

* **update:** group changelog notes by area with commit links ([13d5f9b](https://github.com/hxreborn/PixelInjector/commit/13d5f9b94676f3e11f1ef0289e35618316e64509))

### Bug Fixes

* **tweak:** find any EmptyShade view collector and carry it on reload ([0654408](https://github.com/hxreborn/PixelInjector/commit/065440884e0ffa5c267c5de37a0c12d6de911ebe))
* **tweak:** find the ScreenshotSound lambda by its log string ([f3300b4](https://github.com/hxreborn/PixelInjector/commit/f3300b47841f975ea0f31446c11e954a9a7d8d2a))
* **tweak:** install HomeMenuEntry on LineageOS 24 and AOSP launchers ([4e813d0](https://github.com/hxreborn/PixelInjector/commit/4e813d0523d2cf7216cee24b63d39a3d37a7d398))
* **tweak:** prefer the exact BiometricBypass onDialogAnimatedIn name ([2aa7f6f](https://github.com/hxreborn/PixelInjector/commit/2aa7f6fe10f1fb9073908184e338d4b8b4ea5747))
* **tweak:** resolve DownloadChip heads-up methods at any arity ([d15464c](https://github.com/hxreborn/PixelInjector/commit/d15464c3e01024b71858776a54e32aed818ed909))
* **tweak:** resolve PillShot captureDisplay at any arity ([82f90ad](https://github.com/hxreborn/PixelInjector/commit/82f90adefb08688cbddab7980fcf52957ade3934))
* **tweak:** skip an abstract ChargingDetails computePowerIndication ([e1794e5](https://github.com/hxreborn/PixelInjector/commit/e1794e56b60c04cd25be67befb5e442f15c47d80))

## [1.12.0](https://github.com/hxreborn/PixelInjector/compare/v1.11.1...v1.12.0) (2026-10-18)

### Features

* **report:** attach ROM code to reports from non-Pixel phones ([ecd9307](https://github.com/hxreborn/PixelInjector/commit/ecd9307c5f23f155478f169ce620fd2ced8d8662))
* **tweak:** add DownloadChip settings sheet ([f7ea290](https://github.com/hxreborn/PixelInjector/commit/f7ea290c103ade73ed70c40952eb44b4f45c4d67))
* **tweak:** add DownloadChip side, icon and hide options ([189340b](https://github.com/hxreborn/PixelInjector/commit/189340b804bcf25a383eb5faab43f5e0169220ed))
* **tweak:** add DownloadChip status bar progress chip ([7432602](https://github.com/hxreborn/PixelInjector/commit/74326020da0e5c96b2e05c8892c72c22b24add1c))

### Bug Fixes

* **tweak:** add ChargingDetails support for HyperOS 3 ([cf66f23](https://github.com/hxreborn/PixelInjector/commit/cf66f2361752d9f38f6aab150ca14913620debed)), closes [#31](https://github.com/hxreborn/PixelInjector/issues/31)
* **tweak:** add HomeMenuEntry support for Pixel Launcher CP41.260831 ([f0e01ed](https://github.com/hxreborn/PixelInjector/commit/f0e01edfea66cfe475a88a66140d0763bf835b23))
* **tweak:** find the GhFastPass state enum by its constant ([b6e411c](https://github.com/hxreborn/PixelInjector/commit/b6e411c33ac084fbf9a510429b66bad05074dd34))
* **tweak:** read the PillShot app from ScreenshotData.topComponent ([a7eaa63](https://github.com/hxreborn/PixelInjector/commit/a7eaa63f4d9cf3e985da316ff8eea47069a55242))

### Refactor

* rename vague and metaphorical identifiers ([0b1978d](https://github.com/hxreborn/PixelInjector/commit/0b1978dd729bfa2d263c4dc77a82ef5fe0d48ecf))

## [1.11.1](https://github.com/hxreborn/PixelInjector/compare/v1.11.0...v1.11.1) (2026-10-17)

### Bug Fixes

* **hook:** carry DexKit results across hot reload ([efc6f26](https://github.com/hxreborn/PixelInjector/commit/efc6f2651c2b094c7a2eb9f49e099b60047c3ae4)), closes [#28](https://github.com/hxreborn/PixelInjector/issues/28)
* **tweak:** add ExpandNotifications support for Motorola Android 16 ([26f07e4](https://github.com/hxreborn/PixelInjector/commit/26f07e45fd001fbf25cb58fe4c4f18f4babcc936)), closes [#29](https://github.com/hxreborn/PixelInjector/issues/29)
* **tweak:** add HomeMenuEntry support for Pixel Launcher CP3A ([2241eb2](https://github.com/hxreborn/PixelInjector/commit/2241eb2010958e1376d22fdd0ca34029d7161748)), closes [#27](https://github.com/hxreborn/PixelInjector/issues/27)

## [1.11.0](https://github.com/hxreborn/PixelInjector/compare/v1.10.0...v1.11.0) (2026-10-17)

### Features

* **log:** replace the tweak chip row with a tweak filter menu ([7693643](https://github.com/hxreborn/PixelInjector/commit/76936432bb5c22f82ba1f8725a46e0c6db70ba26))
* **update:** collapse the release notes on the update card ([089ef66](https://github.com/hxreborn/PixelInjector/commit/089ef66e35db29596d24207fe0426301206f7b10))

### Bug Fixes

* **hook:** log the class name as member on a missing class ([52be9e6](https://github.com/hxreborn/PixelInjector/commit/52be9e6df6f624fbda90d616db3e26720fb00396))
* **reload:** fade the hot reload list under the action buttons ([4cbb4da](https://github.com/hxreborn/PixelInjector/commit/4cbb4da8b0215c194bb3b492881d4f0bb175b61d))
* **tweak:** add HideAppShortcuts support for Pixel Launcher CP2A ([5c1ddc8](https://github.com/hxreborn/PixelInjector/commit/5c1ddc894b446b3c343359767ce6378e93dc1868)), closes [#25](https://github.com/hxreborn/PixelInjector/issues/25)
* **tweak:** add HomeMenuEntry support for Pixel Launcher CP2A ([7888087](https://github.com/hxreborn/PixelInjector/commit/7888087f1d84fa23f34ed14c6c45d6e356467064)), closes [#26](https://github.com/hxreborn/PixelInjector/issues/26)

## [1.10.0](https://github.com/hxreborn/PixelInjector/compare/v1.9.1...v1.10.0) (2026-10-17)

### Features

* **app:** add a date format setting ([120574f](https://github.com/hxreborn/PixelInjector/commit/120574f35eefa67b6a41eaeed8364c8eb2320ab8))
* **app:** add an about screen with changelog and licenses ([7cd2c02](https://github.com/hxreborn/PixelInjector/commit/7cd2c02519e3009d16f017f1aab2373c40120c41))
* **app:** add button icons and a scrollable log to hot reload ([2c161b3](https://github.com/hxreborn/PixelInjector/commit/2c161b334e7def1328912826ff14d2e367c4fb64))
* **app:** expand every release note from the update card ([bacc86a](https://github.com/hxreborn/PixelInjector/commit/bacc86afcfef472382fbccc56474ab989dcb2668))
* **app:** offer install anyway for a release-signed update ([c53dd43](https://github.com/hxreborn/PixelInjector/commit/c53dd4381ad3f6eb811c6a5d57ae6d129c84f72f))
* **app:** remove hook status sharing ([5364cf3](https://github.com/hxreborn/PixelInjector/commit/5364cf3ac6b37353d851aa3279461b3c6597e57f))
* **app:** show tweak options as compact rows under a divider ([1f682f6](https://github.com/hxreborn/PixelInjector/commit/1f682f6661cfd9a9b18868e19066ddacea5b6b43))
* **hook:** add a PixelInjector row to the home long press menu ([d2a54d6](https://github.com/hxreborn/PixelInjector/commit/d2a54d6592df364d445babcaf3134a4bf91de445))
* **hook:** block swipe dismissal of ongoing notifications ([8a73608](https://github.com/hxreborn/PixelInjector/commit/8a736088e18136d446952b62e7460e2548dbe9c3))
* **hook:** hide app shortcuts in the launcher long press popup ([f42c915](https://github.com/hxreborn/PixelInjector/commit/f42c91576429a42557552940ae8a7c331d917a29))
* **hook:** replace the lock screen fingerprint icon and circle ([6764f5e](https://github.com/hxreborn/PixelInjector/commit/6764f5ec686122062a1a7fef5345263bee758329))
* **hook:** set the heads-up notification display time ([7137782](https://github.com/hxreborn/PixelInjector/commit/713778269fc7783124e712f7b667b2d8def79eb2))
* **hook:** show watts volts amps and temperature while charging ([ccce19c](https://github.com/hxreborn/PixelInjector/commit/ccce19c487b011bc1e814b606927a06473353611))

### Bug Fixes

* **app:** cut a pasted report message at the limit ([0c2c7d7](https://github.com/hxreborn/PixelInjector/commit/0c2c7d79e4363fa27a3127776fc33b382eea567d))
* **app:** hide the report button on the root sleep error ([3fdba93](https://github.com/hxreborn/PixelInjector/commit/3fdba932baa2f3fea7d70e2e56a474531b1fdb48))
* **app:** ignore a failed log archive write ([37cbd4b](https://github.com/hxreborn/PixelInjector/commit/37cbd4b824bbb8d2852bf43c4f97d882d86deccf))
* **app:** read the module log under one lock ([505807a](https://github.com/hxreborn/PixelInjector/commit/505807a8b37cc88209069b4835c25e71c51390ad))
* **app:** redact quoted SSIDs and SD card paths ([4b0a850](https://github.com/hxreborn/PixelInjector/commit/4b0a850e970a3cb0c2daffadb302824533af83c6))
* **app:** reuse the cached GitHub release and back off on rate limits ([c2c973e](https://github.com/hxreborn/PixelInjector/commit/c2c973ecf0ac9e14cb33ed604f762f5d9621e3d6))
* **app:** show the group symbol for a section without a package ([54ace2d](https://github.com/hxreborn/PixelInjector/commit/54ace2daa29d5315ddb84740b598ad160157edb7))
* **app:** write the hot reload log off the main thread ([0a233e3](https://github.com/hxreborn/PixelInjector/commit/0a233e3aa581108cde6f5e86674c75e220ff87db))
* **app:** write the newest report list to the ledger ([cc78a68](https://github.com/hxreborn/PixelInjector/commit/cc78a689ce97edda90c99976cc61eb8ffe671cbe))

### Refactor

* **app:** flatten wrapped conditions and single-branch whens ([5d9fbfd](https://github.com/hxreborn/PixelInjector/commit/5d9fbfd58bfd5173a69f58be13ef119280b8d586))

## [1.9.1](https://github.com/hxreborn/PixelInjector/compare/v1.9.0...v1.9.1) (2026-10-16)

### Bug Fixes

* **app:** ask for root only when su was denied ([61ee5d6](https://github.com/hxreborn/PixelInjector/commit/61ee5d6dedbd9f93a48c355d1a972a76a7d83a31))
* **app:** ignore error lines from processes that died ([a213289](https://github.com/hxreborn/PixelInjector/commit/a213289c9a87ab6bb4f8cb1e83b69b1001ade1d4))
* **app:** label scrubbed log data as redacted ([c1ffced](https://github.com/hxreborn/PixelInjector/commit/c1ffcedc55ad4164da0b6043dfc4015b68064214))
* **app:** note that double tap to sleep needs root for the home app ([ce6f74a](https://github.com/hxreborn/PixelInjector/commit/ce6f74ab4514890a5a0ea8dfdd749531429db5b5))
* **app:** redact phone numbers and advertising IDs from reports ([d9efeeb](https://github.com/hxreborn/PixelInjector/commit/d9efeebf2ffee726cee4c24663966e794d88b471))
* **app:** redact phone numbers without a plus prefix ([e6b8ce9](https://github.com/hxreborn/PixelInjector/commit/e6b8ce96b4542cb78b898e34055e36346c9bbb1e))
* **app:** remove the update check and module log row summaries ([c1d765a](https://github.com/hxreborn/PixelInjector/commit/c1d765a0d128e553fd944e2144623bc5f994820e))
* **app:** report tweaks that log runtime errors as error ([bd73690](https://github.com/hxreborn/PixelInjector/commit/bd73690be9a2ed21cef9c282a036761243d1c583))
* **app:** scope the redaction disclosure to the module log ([c80f698](https://github.com/hxreborn/PixelInjector/commit/c80f698d321635b90209a27f88dbabff1aab6620))
* **app:** show a tweak's runtime error on its tile ([b8a18de](https://github.com/hxreborn/PixelInjector/commit/b8a18de37104383b594e31742aecfd741b186e87))
* **app:** show the update snackbar once ([dfc2801](https://github.com/hxreborn/PixelInjector/commit/dfc2801172da97f0d41595838ed9b867af2d7af2))
* **app:** time out a hot reload that never answers ([0fd14c7](https://github.com/hxreborn/PixelInjector/commit/0fd14c73b552641238f0f905df8d5e2b8c37c5e8))
* **hook:** log why a root sleep failed ([9ea6b4f](https://github.com/hxreborn/PixelInjector/commit/9ea6b4f125e018cdb48ef7e2b613889d866f66ac))
* **hook:** resolve the notifyUpdateLayoutParams overload ([c016d3e](https://github.com/hxreborn/PixelInjector/commit/c016d3eed213c0f59ebbfdf1af578915757da071)), closes [#21](https://github.com/hxreborn/PixelInjector/issues/21)

## [1.9.0](https://github.com/hxreborn/PixelInjector/compare/v1.8.0...v1.9.0) (2026-10-14)

### Features

* **app:** add a switch for automatic update checks ([657ba68](https://github.com/hxreborn/PixelInjector/commit/657ba68fd28bfcca86cfc1e15b07f75c9e0c10f0))
* **app:** add an option to hide unavailable tweaks ([6a635c3](https://github.com/hxreborn/PixelInjector/commit/6a635c3f2ab5d96621665b06052bd586fc33fa93))
* **app:** encrypt developer notes end to end ([af18fce](https://github.com/hxreborn/PixelInjector/commit/af18fce386eb9118ea46649c4325975e97973a59))
* **app:** group settings into updates support privacy and about ([018ef42](https://github.com/hxreborn/PixelInjector/commit/018ef42941cac5395cd6a82e99b5c44af8d6296b))
* **app:** hide package names in section headers unless enabled ([0839355](https://github.com/hxreborn/PixelInjector/commit/083935557c03e752444a76f10cf0ca252e2f45e0))
* **app:** list encryption specs with spec links in the report sheet ([5c08175](https://github.com/hxreborn/PixelInjector/commit/5c08175303d6a9f9a2fb1dd2d54a4a6e0ac76cc9))
* **app:** mark the release note line that fixes your report ([8a872d4](https://github.com/hxreborn/PixelInjector/commit/8a872d4148639f9a76ce3e3e45dbd39fe70f44cf))
* **app:** nest option rows inside their tweak card ([86eda61](https://github.com/hxreborn/PixelInjector/commit/86eda61b2cef26745a1806e68f059ab622609937))
* **app:** show app and tweak icons on dashboard and settings rows ([5559248](https://github.com/hxreborn/PixelInjector/commit/5559248b6eb9c7e39dff92f4be95d6397dfa2fbd))
* **app:** show app icon and label on hot reload rows ([23e5c1b](https://github.com/hxreborn/PixelInjector/commit/23e5c1b97d80895dbe938f8b79d5c06e476b3a67))
* **app:** show each report as status, latest note and issue link ([9e95a8e](https://github.com/hxreborn/PixelInjector/commit/9e95a8e4ea76447bfc589ddbf1b026a7f4c37d4a))
* **app:** stack screens with shared axis and scaled predictive back ([ca20e8c](https://github.com/hxreborn/PixelInjector/commit/ca20e8c57ae72acc44586e7405c931871a56a999))

### Bug Fixes

* **app:** fit settings and report sheets to landscape ([7deb381](https://github.com/hxreborn/PixelInjector/commit/7deb3812bea3f47b12402922191ee7f64ef5bab4))
* **app:** keep sheets below the status bar ([766c617](https://github.com/hxreborn/PixelInjector/commit/766c61746369bad5546ce3c963f0ef6d0022a29a))
* **app:** make sharing hook status opt-in ([77e8b3a](https://github.com/hxreborn/PixelInjector/commit/77e8b3a889f1e71cd7386f17fa03bb9a33208000))
* **app:** open the update from anywhere on the version row ([aed24d4](https://github.com/hxreborn/PixelInjector/commit/aed24d4acda00e9b6d49b1bcfb2bcd2c3263ee4f))
* **app:** rename Send a report to Report a bug ([9174801](https://github.com/hxreborn/PixelInjector/commit/9174801969da38b620c687a174842b708e8e12aa))
* **app:** rewrite report and update copy to say what each screen does ([58abd1b](https://github.com/hxreborn/PixelInjector/commit/58abd1b9f3d8d1591ab53f30a303b3835e54bbe9))
* **app:** scroll the targets list under a transparent action bar ([cc22240](https://github.com/hxreborn/PixelInjector/commit/cc22240589192a4f5ac80b050b7439b5d015f87b))
* **app:** tighten hierarchy of settings reports and update sheets ([8d08017](https://github.com/hxreborn/PixelInjector/commit/8d08017fbe498cae927ca91a8d7eee42ab0255c0))

### Refactor

* **app:** remove the module log button from the toolbar ([58b0aaf](https://github.com/hxreborn/PixelInjector/commit/58b0aaf014df2807a38ff35c63c3e65507e1bc7d))

## [1.8.0](https://github.com/hxreborn/PixelInjector/compare/v1.7.1...v1.8.0) (2026-10-13)

### Features

* **app:** send private reports and install updates in the app ([c111617](https://github.com/hxreborn/PixelInjector/commit/c111617df9a1be7bc57a859dacf354d8038f0bd7))
* **app:** show the conversation on each report ([18ccff4](https://github.com/hxreborn/PixelInjector/commit/18ccff43b7e7fd2ea78dd28ae85c57382d320302))

## [1.7.1](https://github.com/hxreborn/PixelInjector/compare/v1.7.0...v1.7.1) (2026-10-12)

### Bug Fixes

* **hook:** hook the View empty shade when the Compose one is missing ([d806dff](https://github.com/hxreborn/PixelInjector/commit/d806dff5e8360bb96eec89868511cdce98097315))

## [1.7.0](https://github.com/hxreborn/PixelInjector/compare/v1.6.0...v1.7.0) (2026-10-05)

### Features

* **app:** draw a scrollbar on the dashboard list ([b6b82f7](https://github.com/hxreborn/PixelInjector/commit/b6b82f7b5de2de47df312de04e2e48e8e2e5d84a))
* **hook:** add GH FastPass closing the approved 2FA dialog in the GitHub app ([b936f16](https://github.com/hxreborn/PixelInjector/commit/b936f16385b5dadef6466fd07c2d73581337b25e))

### Bug Fixes

* **app:** register the prefs listener before sending the first snapshot ([3801d51](https://github.com/hxreborn/PixelInjector/commit/3801d51af747b246df5eed9e4faf4a45680970ee))
* **hook:** catch failures inside the posted confirm retries ([043d61f](https://github.com/hxreborn/PixelInjector/commit/043d61f797d3c0d1e0d932e104511821c710dbc6))
* **hook:** drop the DexKit bridge reference before closing it ([d1e9d65](https://github.com/hxreborn/PixelInjector/commit/d1e9d65303363e637780194ee6fe6758e4507cd2))
* **hook:** keep the unlock sound while Biometric Bypass is off ([ef4d909](https://github.com/hxreborn/PixelInjector/commit/ef4d909d304dfa36269f21b40760552aa0d261c2))

### Refactor

* **app:** collapse single line lambdas in the prefs sync and log parser ([82f2c45](https://github.com/hxreborn/PixelInjector/commit/82f2c450e7e13a5961fdd85d9fe3bf865eeb2b8b))
* **hook:** name every bindings value and pending state explicitly ([3dfe6d9](https://github.com/hxreborn/PixelInjector/commit/3dfe6d9efc4a335785af700869c48187743b2d32))
* **util:** catch only missing members in the reflection helpers ([c28c464](https://github.com/hxreborn/PixelInjector/commit/c28c4645813f2c5872bff4ae06b193e89d460eae))

## [1.6.0](https://github.com/hxreborn/PixelInjector/compare/v1.5.0...v1.6.0) (2026-10-05)

### Features

* **app:** exit the toolbar on scroll and morph buttons, toggles, checkboxes and menu items ([c76340b](https://github.com/hxreborn/PixelInjector/commit/c76340b50f87ca149abe82f934465adffae6123d))

### Bug Fixes

* **app:** shrink the collapsed dashboard title and right align link tile chevrons ([2710bc0](https://github.com/hxreborn/PixelInjector/commit/2710bc03673334f190da1240e0eb8b1c26309d4e))

### Refactor

* **app:** replace the scrollbar gist with the material3 scrollbar modifier ([ab0a501](https://github.com/hxreborn/PixelInjector/commit/ab0a501e327edad124ab5f589e6cdaa74d2cfe95))

## [1.5.0](https://github.com/hxreborn/PixelInjector/compare/v1.4.2...v1.5.0) (2026-10-04)

### Features

* **app:** list every hooked process in the hot reload sheet ([c603a2b](https://github.com/hxreborn/PixelInjector/commit/c603a2b665dca20a698ab487a6c14133ca9f96d7))

### Bug Fixes

* **app:** decode the loaded version chip as major.minor.patch ([54c9f9f](https://github.com/hxreborn/PixelInjector/commit/54c9f9fc5485cbd9d0f2fd347045021b37d63160))

## [1.4.2](https://github.com/hxreborn/PixelInjector/compare/v1.4.1...v1.4.2) (2026-10-02)

### Bug Fixes

* **module:** accept hot reload in processes without a target ([1302ed4](https://github.com/hxreborn/PixelInjector/commit/1302ed4f4f0f7f8f453e34c07f7971cbbb78374d))

## [1.4.1](https://github.com/hxreborn/PixelInjector/compare/v1.4.0...v1.4.1) (2026-10-02)

### Bug Fixes

* **hook:** lock the phone before double tap to sleep turns the screen off ([aa1725b](https://github.com/hxreborn/PixelInjector/commit/aa1725b3274d8d5acf87b72dda96e2fe924f4f03))

## [1.4.0](https://github.com/hxreborn/PixelInjector/compare/v1.3.0...v1.4.0) (2026-10-01)

### Features

* **hook:** add Empty Shade to replace the empty shade text and icon ([767f39f](https://github.com/hxreborn/PixelInjector/commit/767f39fdfb45ac05f8755cc1bc040d0b93151264))

## [1.3.0](https://github.com/hxreborn/PixelInjector/compare/v1.2.0...v1.3.0) (2026-09-28)

### Features

* **hook:** add a skip or only-selected app filter to PillShot ([d85eed1](https://github.com/hxreborn/PixelInjector/commit/d85eed197297c1babb6bc25dd6a75e185d397f26)), closes [#10](https://github.com/hxreborn/PixelInjector/issues/10)

### Refactor

* **app:** put Copy after Close in the hot reload sheet ([18eb7db](https://github.com/hxreborn/PixelInjector/commit/18eb7db883343567e88d3570ff92708522e8a967))

## [1.2.0](https://github.com/hxreborn/PixelInjector/compare/v1.1.0...v1.2.0) (2026-09-24)

### Features

* **hook:** add KDE Connect Quiet to block the Wi-Fi multicast lock ([cac024b](https://github.com/hxreborn/PixelInjector/commit/cac024b468aab1d3e873c4bd55699a0cb9cba7dd))

## [1.1.0](https://github.com/hxreborn/PixelInjector/compare/v1.0.2...v1.1.0) (2026-09-07)

### Features

* **app:** add a confirm sound picker for Biometric Bypass ([c51ee18](https://github.com/hxreborn/PixelInjector/commit/c51ee188b01bd64b65e5deb7b4f42207a73b4ce3))

### Bug Fixes

* **hook:** drop the click sound when BiometricBypass taps confirm ([2928fd0](https://github.com/hxreborn/PixelInjector/commit/2928fd05ba141d6142a8463bd64a52ca6d653155))
* **hook:** press the power key instead of goToSleep in SleepSignal ([3dd59c2](https://github.com/hxreborn/PixelInjector/commit/3dd59c2766155b4853a0638f4f8d95e3b72e444f))

## [1.0.2](https://github.com/hxreborn/PixelInjector/compare/v1.0.1...v1.0.2) (2026-09-06)

### Bug Fixes

* **hook:** hook ImageCaptureImpl in PillShot for Android 14 and 15 ([8495322](https://github.com/hxreborn/PixelInjector/commit/8495322b8744c66b73579914a00012eca6909f73))

## [1.0.1](https://github.com/hxreborn/PixelInjector/compare/v1.0.0...v1.0.1) (2026-09-06)

### Bug Fixes

* **app:** open the dashboard at the top on cold start ([e41f73a](https://github.com/hxreborn/PixelInjector/commit/e41f73a62a00918357046d7370d22a70e194881d))

## 1.0.0 (2026-09-06)

### Features

* **app:** add companion app ([96cce4a](https://github.com/hxreborn/PixelInjector/commit/96cce4ad24012300b6669fdd4cbc13cc9c8064ff))
* **hook:** add Dialer tweak ([5aa5d35](https://github.com/hxreborn/PixelInjector/commit/5aa5d357c4c89a74b7778679a38385a4580d90ef))
* **hook:** add Files tweak ([c842dbd](https://github.com/hxreborn/PixelInjector/commit/c842dbda0a0868a0181163f0e53c6af9cc1e8101))
* **hook:** add Gboard tweak ([e7e49a3](https://github.com/hxreborn/PixelInjector/commit/e7e49a3dc154ae3fd4a9d4f7157b9181d80cd573))
* **hook:** add launcher tweaks ([18e1a9d](https://github.com/hxreborn/PixelInjector/commit/18e1a9ddddcbd1d24ba8ca460905e2ce6ccee0d5))
* **hook:** add module entry with tweak registry and hot reload ([98ee2b7](https://github.com/hxreborn/PixelInjector/commit/98ee2b71ed3138aa7cae34dc086c7f767149c103))
* **hook:** add System UI tweaks ([be103fb](https://github.com/hxreborn/PixelInjector/commit/be103fb020d9ecaa028c4691613bae33d9bde890))
* **hook:** add system_server tweaks ([6a581e3](https://github.com/hxreborn/PixelInjector/commit/6a581e33f3181ece51c3c324971309e3dcf933ea))
