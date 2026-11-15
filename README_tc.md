<!--
SPDX-FileCopyrightText: 2015 - 2024 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 同文 Android 輸入法平臺

![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](http://osfans.github.io/trime/)

[English](README.md) | [简体中文](README_sc.md) | 繁體中文

## 關於

源於開源的[注音倉頡輸入法]前端，基於著名的 [RIME] 輸入法框架，使用 JNI 的 C 語言和 Android 的 Java/Kotlin 語言書寫，旨在保護漢語各地方言母語，音碼、形碼通用的輸入法平臺。

[查看文檔](https://github.com/osfans/trime/wiki)

## 下載

- 穩定版 <br>
  [<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)
  [<img alt='Google Play 立即下載' src='https://play.google.com/intl/en_us/badges/images/generic/zh-tw_badge_web_generic.png' height='80px'/>](https://play.google.com/store/apps/details?id=com.osfans.trime)

- 每夜版 [點選下載](https://github.com/osfans/trime/releases/tag/nightly)

- 測試版 [點選下載](https://github.com/osfans/trime/actions)

- 配置文件 [rimerc](https://github.com/Bambooin/rimerc)

## 本機語音輸入（BiBi 鍵盤）

本分支（BiBi 鍵盤）在同文的基礎上新增了本機語音輸入。語音識別**並非內建**：輸入法透過 AIDL 繫結一個獨立的語音 App，把音訊交給它識別。在本分支中，長按對應到 `VOICE_ASSIST` 的按鍵會進入「按住說話」模式，並在整個鍵盤區域上疊一層即時音訊波形，而不再切換到其他語音輸入法。

完整說明見文件站：<https://bibidocs.brycewg.com/>

### 前置條件

1. 安裝語音 App「說點啥」，套件名稱可以是 `com.brycewg.asrkb`（開源版）或 `com.brycewg.asrkb.pro`（Pro 版）。
2. 在該 App 內開啟**「允許外部輸入法聯動（AIDL）」**。

如果未安裝該 App，或未開啟 AIDL 開關，長按語音鍵會提示服務未找到，或提示需要在說點啥中開啟外部輸入法聯動。

### 運作方式

- 本分支中「說點啥 AIDL 語音輸入」開關（`asrkb_aidl_voice_input`）被**強制開啟**：讀取恆為 `true`，寫入也被改寫為 `true`，設定頁顯示為開且無法關閉。
- 因此長按動作解析為 Android `KEYCODE_VOICE_ASSIST` 的按鍵會直接進入按住說話模式並顯示波形；不再走舊邏輯（`CommonKeyboardActionListener.switchToVoiceInputMethod()`，即切換到系統設定中設定的語音輸入法）。
- 放開即停止錄音並上屏識別結果。

### 設定項

後兩項在 AIDL 語音輸入開關開啟時才生效。

| 設定項 | key | 預設 |
| --- | --- | --- |
| 說點啥 AIDL 語音輸入 | `asrkb_aidl_voice_input` | 開（強制） |
| 錄音時暫停其他媒體 | `asrkb_duck_media_on_record` | 開 |
| 工具列麥克風按鈕 | `asrkb_aidl_voice_toolbar_button` | 關 |

開啟「工具列麥克風按鈕」後，工具列會顯示麥克風按鈕：點按開始錄音，再次點按停止並上屏。開關切換後若未立即生效，請在鍵盤樣式中切換到其他主題再切換回來以重新整理 UI。

### 鍵位設定

在主題中使用鍵名 `VOICE_ASSIST`，它對應 Android 的 `KEYCODE_VOICE_ASSIST`，長按處理器正是依 `action.code == KeyEvent.KEYCODE_VOICE_ASSIST` 比對的。

本儲存庫中已有的範例：

```yaml
# app/src/main/assets/shared/tongwenfeng.trime.yaml（preset_keyboards 段，約 3901、3942 行）
- {label: '✾', click: Keyboard_v2, long_click: VOICE_ASSIST, composing: Keyboard_v, ascii: 'v', width: 11.5, key_back_color: bh4, key_text_color: th4}

# app/src/main/assets/shared/tongwenfeng.trime.yaml（約 3962 行，preset_keys 段）
VOICE_ASSIST: {label: 语音, functional: true, send: VOICE_ASSIST}

# app/src/main/assets/shared/trime.yaml（約 863 行，preset_keys 段）
VOICE_ASSIST: {label: 語音, send: VOICE_ASSIST}

# app/src/main/assets/shared/trime.yaml（約 1260、1656 行，空格鍵長按）
- {click: space, long_click: VOICE_ASSIST, width: 30}
```

自己寫設定的最小範例：在 `preset_keyboards` 的某個鍵盤佈局裡給一個鍵加 `long_click: VOICE_ASSIST`，並在 `preset_keys`（或該主題的鍵定義段）裡定義 `VOICE_ASSIST`：

```yaml
preset_keyboards:
  qwerty:
    keys:
      - {click: space, long_click: VOICE_ASSIST, width: 30}

preset_keys:
  VOICE_ASSIST: {label: 語音, send: VOICE_ASSIST}
```

常見坑：

- 佈局裡加了 `long_click: VOICE_ASSIST`，但 `preset_keys`（或主題的鍵定義段）裡缺少 `VOICE_ASSIST` 條目，動作無法解析，長按什麼都不發生。
- 用了別的鍵名（例如自訂 `voice`），得到的 code 比對不上 `KEYCODE_VOICE_ASSIST`，長按會退化成舊行為（切換到別的輸入法）或什麼都不發生。
- 只在 `click` 上綁 `VOICE_ASSIST` 會保留普通點按行為；按住說話是由長按動作觸發的。

## 沿革

TRIME 是 Tongwen RIME 或是 ThaeRvInputMEthod 的縮寫:

- 最初，輸入法是寫給[泰如拼音](http://taerv.nguyoeh.com/ime/)（tae5 rv2）的，中文名為“泰如輸入法”;
- 然後，添加了吳語等方言碼錶，做成了一個輸入法平臺，更名為“漢字方言輸入法”;
- 後來，相容了五筆、兩筆等形碼，在太空衛士、徵羽的建議下，更名為“[同文輸入法平臺 2.x](https://github.com/osfans/trime-legacy)”。寓意音碼形碼同臺，方言官話同文。
- 之後，藉助 JNI 技術，享受了 [librime](https://github.com/rime/librime) 的成果，升級為“同文輸入法平臺 3.x”，簡稱“同文輸入法”。

現在歡迎你前來[貢獻](CONTRIBUTING.md) ～！

## 開發入門

### 準備

#### 開發環境要求

- Android SDK 和 Android NDK
  - 如果還不熟悉 Android 開發，建議安裝 [Android Studio](https://developer.android.com/studio)，它會自動安裝並配置 Android 開發環境。

- JDK（OpenJDK）17
- Python 3 (用於給 OpenCC 生成詞典文字檔案)

#### Windows 上的前提條件

當前構建配置會使構建過程中建立符號連結，開發者需要：

- 啟用[開發者模式](https://learn.microsoft.com/zh-cn/windows/apps/get-started/enable-your-device-for-development) 以在無管理員許可權的情況下建立符號連結。

- 啟用 `git` 的符號連結支援：

  ```powershell
  git config --global core.symlinks true
  ```

如果無法或者不想啟用上述設定也沒關係。構建系統會自動在符號連結建立失敗時使用複製代替。

### 構建

#### 1. 克隆此專案並拉取所有子模組。

```sh
git clone git@github.com:osfans/trime.git
git submodule update --init --recursive
# 可以使用部分克隆節省時間
git submodule update --init --recursive --filter=blob:none
```

#### 2. 編譯除錯版本:

```sh
# On Linux or macOS
make debug

# On Windows
.\gradlew assembleDebug
```

#### 3. 編譯正式版本：

請建立 `keystore.properties` 檔案，包含以下內容，註明[簽名信息](https://developer.android.com/studio/publish/app-signing.html)：

```gradle.properties
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

然後執行：

```sh
# On Linux or macOS
make release

# On Windows
.\gradlew assembleRelease
```

### 故障排除

```
Target "boost_log_setup" links to target "Boost::coroutine" but the target was not found.
```

在 Linux 或 macOS 上執行 `make clean`，Windows 上執行 `.\gradlew clean`。

其他問題:

1. 首先嚐試 `make clean`
2. 確保你的倉庫與最新版本一致。如果你修改了一個或更多的子模組，請確保它們與當前倉庫版本相容。
3. 如果問題依然存在（不太可能）, 嘗試進行一次新的克隆。
4. 檢查是否有 PR/issue 與你的問題相關。 如果有的話，嘗試他們的解決方案。
5. 如果以上方法都不工作，你可以提一個 issue 來尋求幫助(可選)。

## 鳴謝

- 開發：[osfans](https://github.com/osfans)
- 貢獻：[boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)、[nopdan](https://github.com/nopdan)......
- [維基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)......
- 翻譯：天真可愛的滿滿（繁體中文）、點解（英文）......
- 鍵盤：天真可愛的滿滿、皛筱曉小笨魚、吳琛 11、熊貓阿 Bo、默默ㄇㄛ ˋ......
- 捐贈：[Releases](https://github.com/osfans/trime/releases) 中的“打賞”實時更新
- 社群：在 [Issues](https://github.com/osfans/trime/issues)、[QQ 群 (811142286)](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、[QQ 群 (224230445)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=pg_q7UVumWYLq1Rk8kIAqkK1xGt64VnX&authKey=04m9l7OBO5H5vgrEL8IbpsmtnptWM60xy%2FUwYCfyvw9VcRhe8zRzAS1ezoemZdFr&noverify=0&group_code=224230445)、[貼吧](http://tieba.baidu.com/f?kw=rime)、[Google Play](https://play.google.com/store/apps/details?id=com.osfans.trime)、[Telegram](https://t.me/trime_dev) 中反饋意見的網友
- 專案：[RIME]、[OpenCC]、[注音倉頡輸入法]等開源專案

## 第三方庫

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
- [注音倉頡輸入法](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

[注音倉頡輸入法]: https://code.google.com/p/android-traditional-chinese-ime/
[RIME]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
