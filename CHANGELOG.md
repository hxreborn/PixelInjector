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
