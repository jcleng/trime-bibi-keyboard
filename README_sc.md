<!--
SPDX-FileCopyrightText: 2015 - 2024 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 同文 Android 输入法平台

![build](https://github.com/osfans/trime/actions/workflows/commit-ci.yml/badge.svg?branch=develop)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/release/osfans/trime.svg)](https://github.com/osfans/trime/releases)
[![F-Droid release](https://img.shields.io/f-droid/v/com.osfans.trime.svg)](https://f-droid.org/packages/com.osfans.trime)
[![Latest build](https://img.shields.io/github/last-commit/osfans/trime.svg)](http://osfans.github.io/trime/)

[English](README.md) | 简体中文 | [繁體中文](README_tc.md)

## 关于

源于开源的[注音仓颉输入法]前端，基于著名的 [RIME] 输入法框架，使用 JNI 的 C 语言和 Android 的 Java/Kotlin 语言书写，旨在保护汉语各地方言母语，音码、形码通用的输入法平台。

[查看文档](https://github.com/osfans/trime/wiki)

## 下载

- 稳定版 <br>
  [<img alt='Get it on F-Droid' src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png' height='80px'/>](https://f-droid.org/packages/com.osfans.trime)
  [<img alt='Google Play 立即下载' src='https://play.google.com/intl/en_us/badges/images/generic/zh-cn_badge_web_generic.png' height='80px'/>](https://play.google.com/store/apps/details?id=com.osfans.trime)

- 每夜版 [点击下载](https://github.com/osfans/trime/releases/tag/nightly)

- 测试版 [点击下载](https://github.com/osfans/trime/actions)

- 配置文档 [rimerc](https://github.com/Bambooin/rimerc)

## 本地语音输入（BiBi 键盘）

本分支（BiBi 键盘）在同文的基础上增加了本地语音输入。语音识别**并非内置**：输入法通过 AIDL 绑定一个独立的语音 App，把音频交给它识别。在本分支中，长按映射为 `VOICE_ASSIST` 的按键会进入“按住说话”模式，并在整个键盘区域上叠一层实时音频波形，而不再切换到其他语音输入法。

完整说明见文档站：<https://bibidocs.brycewg.com/>

### 前置条件

1. 安装语音 App「说点啥」，包名可以是 `com.brycewg.asrkb`（开源版）或 `com.brycewg.asrkb.pro`（Pro 版）。
2. 在该 App 内开启**「允许外部输入法联动（AIDL）」**。

如果未安装该 App，或未开启 AIDL 开关，长按语音键会提示服务未找到，或提示需要在说点啥中开启外部输入法联动。

### 工作方式

- 本分支中「说点啥 AIDL 语音输入」开关（`asrkb_aidl_voice_input`）被**强制开启**：读取恒为 `true`，写入也被改写为 `true`，设置页显示为开且无法关闭。
- 因此长按动作解析为 Android `KEYCODE_VOICE_ASSIST` 的按键会直接进入按住说话模式并显示波形；不再走旧逻辑（`CommonKeyboardActionListener.switchToVoiceInputMethod()`，即切换到系统设置中配置的语音输入法）。
- 松手即停止录音并上屏识别结果。

### 设置项

后两项在 AIDL 语音输入开关开启时才生效。

| 设置项 | key | 默认 |
| --- | --- | --- |
| 说点啥 AIDL 语音输入 | `asrkb_aidl_voice_input` | 开（强制） |
| 录音时暂停其他媒体 | `asrkb_duck_media_on_record` | 开 |
| 工具栏麦克风按钮 | `asrkb_aidl_voice_toolbar_button` | 关 |

开启「工具栏麦克风按钮」后，工具栏会显示麦克风按钮：点按开始录音，再次点按停止并上屏。开关切换后如未立即生效，请在键盘样式中切换到其他主题再切换回来以刷新 UI。

### 键位配置

在主题中使用键名 `VOICE_ASSIST`，它对应 Android 的 `KEYCODE_VOICE_ASSIST`，长按处理器正是按 `action.code == KeyEvent.KEYCODE_VOICE_ASSIST` 匹配的。

本仓库中已有的示例：

```yaml
# app/src/main/assets/shared/tongwenfeng.trime.yaml（preset_keyboards 段，约 3901、3942 行）
- {label: '✾', click: Keyboard_v2, long_click: VOICE_ASSIST, composing: Keyboard_v, ascii: 'v', width: 11.5, key_back_color: bh4, key_text_color: th4}

# app/src/main/assets/shared/tongwenfeng.trime.yaml（约 3962 行，preset_keys 段）
VOICE_ASSIST: {label: 语音, functional: true, send: VOICE_ASSIST}

# app/src/main/assets/shared/trime.yaml（约 863 行，preset_keys 段）
VOICE_ASSIST: {label: 語音, send: VOICE_ASSIST}

# app/src/main/assets/shared/trime.yaml（约 1260、1656 行，空格键长按）
- {click: space, long_click: VOICE_ASSIST, width: 30}
```

自己写配置的最小示例：在 `preset_keyboards` 的某个键盘布局里给一个键加 `long_click: VOICE_ASSIST`，并在 `preset_keys`（或该主题的键定义段）里定义 `VOICE_ASSIST`：

```yaml
preset_keyboards:
  qwerty:
    keys:
      - {click: space, long_click: VOICE_ASSIST, width: 30}

preset_keys:
  VOICE_ASSIST: {label: 语音, send: VOICE_ASSIST}
```

常见坑：

- 布局里加了 `long_click: VOICE_ASSIST`，但 `preset_keys`（或主题的键定义段）里缺少 `VOICE_ASSIST` 条目，动作无法解析，长按什么都不发生。
- 用了别的键名（例如自定义 `voice`），得到的 code 匹配不上 `KEYCODE_VOICE_ASSIST`，长按会退化成旧行为（切换到别的输入法）或什么都不发生。
- 只在 `click` 上绑 `VOICE_ASSIST` 会保留普通点按行为；按住说话是由长按动作触发的。

## 沿革

TRIME 是 Tongwen RIME 或是 ThaeRvInputMEthod 的缩写:

- 最初，输入法是写给[泰如拼音](http://taerv.nguyoeh.com/ime/)（tae5 rv2）的，中文名为“泰如输入法”;
- 然后，添加了吴语等方言码表，做成了一个输入法平台，更名为“汉字方言输入法”;
- 后来，兼容了五笔、两笔等形码，在太空卫士、征羽的建议下，更名为“[同文输入法平台 2.x](https://github.com/osfans/trime-legacy)”。寓意音码形码同台，方言官话同文。
- 之后，借助 JNI 技术，享受了 [librime](https://github.com/rime/librime) 的成果，升级为“同文输入法平台 3.x”，简称“同文输入法”。

现在欢迎你前来[贡献](CONTRIBUTING.md) ～！

## 开发入门

### 准备

#### 开发环境要求

- Android SDK 和 Android NDK
  - 如果还不熟悉 Android 开发，建议安装 [Android Studio](https://developer.android.google.cn/studio)，它会自动安装并配置 Android 开发环境。

- JDK（OpenJDK）17
- Python 3 (用于给 OpenCC 生成词典文本文件）

#### Windows 上的前提条件

当前构建配置会使构建过程中创建符号链接，开发者需要：

- 启用[开发者模式](https://learn.microsoft.com/zh-cn/windows/apps/get-started/enable-your-device-for-development) 以在无管理员权限的情况下创建符号链接。

- 启用 `git` 的符号链接支持：

  ```powershell
  git config --global core.symlinks true
  ```

如果无法或者不想启用上述设置也没关系。构建系统会自动在符号链接创建失败时使用复制代替。

### 构建

#### 1. 克隆此项目并拉取所有子模块。

```sh
git clone git@github.com:osfans/trime.git
git submodule update --init --recursive
# 可以使用部分克隆节约时间
git submodule update --init --recursive --filter=blob:none
```

#### 2. 编译调试版本:

```sh
# On Linux or macOS
make debug

# On Windows
.\gradlew assembleDebug
```

#### 3. 编译正式版本：

请创建 `keystore.properties` 文件，包含以下内容，注明[签名信息](https://developer.android.com/studio/publish/app-signing.html)：

```gradle.properties
storePassword=myStorePassword
keyPassword=mykeyPassword
keyAlias=myKeyAlias
storeFile=myStoreFileLocation
```

然后执行：

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

在 Linux 或 macOS 上执行 `make clean`，Windows 上执行 `.\gradlew clean`。

其他问题:

1. 首先尝试 `make clean`
2. 确保你的仓库与最新版本一致。如果你修改了一个或更多的子模块，请确保它们与当前仓库版本兼容。
3. 如果问题依然存在（不太可能）, 尝试进行一次新的克隆。
4. 检查是否有 PR/issue 与你的问题相关。 如果有的话，尝试他们的解决方案。
5. 如果以上方法都不工作，你可以提一个 issue 来寻求帮助(可选)。

## 鸣谢

- 开发：[osfans](https://github.com/osfans)
- 贡献：[boboIqiqi](https://github.com/boboIqiqi)、[Bambooin](https://github.com/Bambooin)、[senchi96](https://github.com/senchi96)、[heiher](https://github.com/heiher)、[abay](https://github.com/a342191555)、[iovxw](https://github.com/iovxw)、[huyz-git](https://github.com/huyz-git)、[tumuyan](https://github.com/tumuyan)、[WhiredPlanck](https://github.com/WhiredPlanck)、[nopdan](https://github.com/nopdan)......
- [维基](https://github.com/osfans/trime/wiki)：[xiaoqun2016](https://github.com/xiaoqun2016)、[boboIqiqi](https://github.com/boboIqiqi)......
- 翻译：天真可爱的满满（繁体中文）、点解（英文）......
- 键盘：天真可爱的满满、皛筱晓小笨鱼、吴琛 11、熊猫阿 Bo、默默ㄇㄛ ˋ......
- 捐赠：[Releases](https://github.com/osfans/trime/releases) 中的“打赏”实时更新
- 社区：在 [Issues](https://github.com/osfans/trime/issues)、[QQ 群 (811142286)](https://jq.qq.com/?_wv=1027&k=AXdR80HN)、[QQ 群 (224230445)](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=pg_q7UVumWYLq1Rk8kIAqkK1xGt64VnX&authKey=04m9l7OBO5H5vgrEL8IbpsmtnptWM60xy%2FUwYCfyvw9VcRhe8zRzAS1ezoemZdFr&noverify=0&group_code=224230445)、[Google Play](https://play.google.com/store/apps/details?id=com.osfans.trime)、[贴吧](http://tieba.baidu.com/f?kw=rime)、[Telegram](https://t.me/trime_dev) 中反馈意见的网友
- 项目：[RIME]、[OpenCC]、[注音仓颉输入法]等开源项目

## 第三方库

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
- [注音仓颉输入法](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

[注音仓颉输入法]: https://code.google.com/p/android-traditional-chinese-ime/
[RIME]: http://rime.im
[OpenCC]: https://github.com/BYVoid/OpenCC
