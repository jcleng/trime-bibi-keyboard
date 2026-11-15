<!--
SPDX-FileCopyrightText: 2015 - 2024 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# Trime

Rime IME for Android

![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](http://osfans.github.io/trime/)

English | [简体中文](README_sc.md) | [繁體中文](README_tc.md)

## About

Trime is originally a frontend of open-source [Android Traditional Chinese IME], based on [RIME] input method framework and written in Java/Kotlin with JNI. It is designed to protect the native language of various local dialects of Chinese and is a universal shape-based and phonetic-based input method platform.

[Documentation](https://github.com/osfans/trime/wiki)

## Download

- Stable Channel <br>
  [<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)
  [<img alt='Google Play Download Now' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height='80px'/>](https://play.google.com/store/apps/details?id=com.osfans.trime)

- Nightly Channel [Download](https://github.com/osfans/trime/releases/tag/nightly)

- Canary Channel [Download](https://github.com/osfans/trime/actions)

- Configurations [rimerc](https://github.com/Bambooin/rimerc)

## Local Voice Input (BiBi Keyboard)

This fork (BiBi Keyboard) adds local voice input to Trime. Speech recognition is **not built in**: the IME binds to a separate speech app over AIDL and streams audio to it for recognition. In this branch, long-pressing a key mapped to `VOICE_ASSIST` starts a hold-to-talk session and draws a live audio waveform over the whole keyboard area, instead of switching to another voice IME.

Full documentation: <https://bibidocs.brycewg.com/>

### Prerequisites

1. Install the speech app 「说点啥」, whose package name is either `com.brycewg.asrkb` (open-source) or `com.brycewg.asrkb.pro` (Pro).
2. Inside that app, enable **"Allow external IME link (AIDL)"** (允许外部输入法联动).

If the app is missing or the AIDL switch is off, holding the voice key reports that the service was not found, or asks you to enable the external IME link.

### How It Works

- The preference **"BiBi Keyboard AIDL voice input"** (`asrkb_aidl_voice_input`) is force-enabled in this branch: reads always return `true`, writes are rewritten to `true`, and the settings switch is shown as on and cannot be turned off.
- A long press on a key whose action resolves to Android's `KEYCODE_VOICE_ASSIST` therefore enters hold-to-talk mode and shows the waveform overlay. The legacy path (`CommonKeyboardActionListener.switchToVoiceInputMethod()`, which switched to the voice IME configured in the system settings) is no longer taken.
- Releasing the key stops recording and commits the recognized text.

### Settings

The last two items are only effective while the AIDL voice input switch is on.

| Preference | Key | Default |
| --- | --- | --- |
| BiBi Keyboard AIDL voice input | `asrkb_aidl_voice_input` | On (forced) |
| Pause other media while recording | `asrkb_duck_media_on_record` | On |
| Toolbar mic button | `asrkb_aidl_voice_toolbar_button` | Off |

With the toolbar mic button enabled, a mic button appears on the toolbar: tap once to start recording, tap again to stop and commit. If a change does not take effect immediately, switch to another theme in the keyboard style settings and switch back to refresh the UI.

### Key Mapping

Use the key name `VOICE_ASSIST` in your theme. It maps to Android's `KEYCODE_VOICE_ASSIST`, which is what the long-press handler matches (`action.code == KeyEvent.KEYCODE_VOICE_ASSIST`).

Examples already shipped in this repository:

```yaml
# app/src/main/assets/shared/tongwenfeng.trime.yaml (shared/preset_keyboards, ~line 3901, 3942)
- {label: '✾', click: Keyboard_v2, long_click: VOICE_ASSIST, composing: Keyboard_v, ascii: 'v', width: 11.5, key_back_color: bh4, key_text_color: th4}

# app/src/main/assets/shared/tongwenfeng.trime.yaml (~line 3962, shared/preset_keys)
VOICE_ASSIST: {label: 语音, functional: true, send: VOICE_ASSIST}

# app/src/main/assets/shared/trime.yaml (~line 863, shared/preset_keys)
VOICE_ASSIST: {label: 語音, send: VOICE_ASSIST}

# app/src/main/assets/shared/trime.yaml (~line 1260 and 1656, space key long press)
- {click: space, long_click: VOICE_ASSIST, width: 30}
```

Minimal example for your own theme. Add `long_click: VOICE_ASSIST` to a key in a keyboard layout under `preset_keyboards`, and define the key itself under `preset_keys` (or in the key definition section of your theme):

```yaml
preset_keyboards:
  qwerty:
    keys:
      - {click: space, long_click: VOICE_ASSIST, width: 30}

preset_keys:
  VOICE_ASSIST: {label: 语音, send: VOICE_ASSIST}
```

Common pitfalls:

- Defining the key in the layout but omitting the `VOICE_ASSIST` entry in `preset_keys` (or your theme's key section) means the action does not resolve, so nothing happens.
- Using another key name, such as a custom `voice`, produces a code that does not match `KEYCODE_VOICE_ASSIST`; the long press then falls back to the legacy behavior (switching to another IME) or does nothing.
- Binding `VOICE_ASSIST` only as a click action keeps the normal click behavior; the hold-to-talk session is triggered from the long-press action.

## History

TRIME is the abbreviation of _Tongwen RIME_ or _ThaeRv Input Method_.

From the beginning, TRIME was written for TaeRv Pinyin, and named _TaeRv Input Method (泰如输入法)_.

Then, we created an input method platform with some code tables, such as Wu dialect (吴语). We renamed it to _Chinese Character Dialect Input Method (汉字方言输入法)_.

Later, it supports Wubi and Liangbi and other shape-based input method, we branded it [_Tongwen Input Method Platform 2.0 (同文输入法平台 2.0)_](https://github.com/osfans/trime-legacy), which implies that the phonetic-based and shape-based input method on one platform, while dialects and Mandrain share one kind of characters.

Benefit from the [librime](https://github.com/rime/librime) project by JNI, we are now in version 3.0 of TRIME aka _Tongwen Input Method (同文输入法)_.

Your are now welcome to [contribution](CONTRIBUTING.md) ~ !

## Getting Started for developer

### Prepare

#### Requirements:

- Android SDK and Android NDK
  * If you are new to Android development, please install [Android Studio](https://developer.android.com/studio).

- JDK (OpenJDK) 17
- Python 3 (required by OpenCC to generate dictionary text files)

#### Prerequisites for Windows

Symbolic links will be created according to current build configurations, developers need:

- Enable [Developer Mode](https://learn.microsoft.com/en-us/windows/apps/get-started/enable-your-device-for-development) so that symlinks can be created without administrator privilege.

- Enable symlink support for `git`:

  ```powershell
  git config --global core.symlinks true
  ```

If you cannot or wouldn't like to enable anything, it doesn't matter. Copying will be used instead when error on creating symbolic links.

### Build

#### 1. Clone this project and fetch all submodules:

```sh
git clone git@github.com:osfans/trime.git
git submodule update --init --recursive
# use partial clone to save time
git submodule update --init --recursive --filter=blob:none
```

#### 2. Debug version without signature:

```sh
# On Linux or macOS
make debug

# On Windows
.\gradlew assembleDebug
```

#### 3. Release version with signture:

Create `keystore.properties` file which contains following contents for [signing information](https://developer.android.com/studio/publish/app-signing.html):

```gradle.properties
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

Then, you may run:

```sh
# On Linux or macOS
make release

# On Windows
.\gradlew assembleRelease
```

### Troubleshooting

```
Target "boost_log_setup" links to target "Boost::coroutine" but the target was not found.
```

Run `make clean` on Linux or macOS, or run `.\gradlew clean` on Windows.

Other issues:

1. Try `make clean`
2. Make sure your repo is up-to-date. If one or more submodules are modified, also make sure they are compatible with the current version.
3. If the problem still exists(very unlikely), try to make a new clone.
4. Check if this is there is an issue/PR related to your problem. If yes, try their solutions.
5. If none of them works, you may make an issue to ask for help.(optional)

## Acknowledgments

- Developer: [osfans](https://github.com/osfans)
- Contributors: [boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)、[nopdan](https://github.com/nopdan)...
- [Wiki Editors](https://github.com/osfans/trime/wiki): [xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)...
- Translators: 天真可爱的满满 (Chinese Traditional), 点解 (English) ...
- Keyboard Designers: 天真可爱的满满、皛筱晓小笨鱼、吴琛 11、熊猫阿 Bo、默默ㄇㄛ ˋ...
- Donations: See QR Code in [Releases](https://github.com/osfans/trime/releases)
- Community: Netizens who feedback in [Issues](https://github.com/osfans/trime/issues)、[QQ Group (811142286)](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、[QQ Group (224230445)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=pg_q7UVumWYLq1Rk8kIAqkK1xGt64VnX&authKey=04m9l7OBO5H5vgrEL8IbpsmtnptWM60xy%2FUwYCfyvw9VcRhe8zRzAS1ezoemZdFr&noverify=0&group_code=224230445)、[Tieba](http://tieba.baidu.com/f?kw=rime)、[Google Play](https://play.google.com/store/apps/details?id=com.osfans.trime)、[Telegram](https://t.me/trime_dev)...
- Projects: [RIME]、[OpenCC]、[Android Traditional Chinese IME] and so on.

## Third Party Libraries

- [Boost C++ Libraries](https://www.boost.org/) (Boost Software License)
- [darts-clone](https://github.com/s-yata/darts-clone) (New BSD License)
- [LevelDB](https://github.com/google/leveldb) (New BSD License)
- [libiconv](https://www.gnu.org/software/libiconv/) (LGPL License)
- [marisa-trie](https://github.com/s-yata/marisa-trie) (BSD License)
- [glog](https://github.com/google/glog) (New BSD License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [RIME](https://rime.im) (BSD License)
- [snappy](https://github.com/google/snappy)(BSD License)
- [utfcpp](https://github.com/nemtrif/utfcpp) (Boost Software License)
- [yaml-cpp](https://github.com/jbeder/yaml-cpp) (MIT License)
- [Android Traditional Chinese IME](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

[Android Traditional Chinese IME]: https://code.google.com/p/android-traditional-chinese-ime/
[RIME]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
