# 字序 / GlyphOrder

[English](README.en.md) · [Apache-2.0](LICENSE) · [第三方声明](THIRD_PARTY_NOTICES.md) · [隐私说明](PRIVACY.md)

[下载 APK](https://github.com/RiversLee9312/GlyphOrder/releases/latest)

通过 Magisk / KernelSU 模块管理 Android 系统字体，使用 Kotlin、Jetpack Compose 与 Material 3 Expressive 构建。支持 Android 14 及以上；已在 OnePlus 11 的 LineageOS 23.2 / Android 16 和 LOS 24.0-20261023 / Android 17 上验证。KernelSU 需要先安装并启用挂载元模块，本轮验证使用 meta-overlayfs 1.3.1。

## 功能

- 导入单字面的 TTF / OTF 字体，查看原生多语言预览、字重与可变轴。
- 一键应用到全部字体、部分风格或指定的系统字体家族。
- 分别搭配 Sans、Serif、Mono、Google Sans / LOS 时钟、Casual、Cursive 和其他家族；选择“系统默认”会保留该类原始配置。
- 内置 MiSans Global、Rubik、Noto Sans、Noto Serif 与 Sarasa Fixed。脚本补字和配套字重作为隐藏依赖，不会挤占主字体列表。
- 默认启用缺字回退，保留系统 Emoji；生成与安装前检查原生字体家族数量和覆盖率。
- 应用界面支持 24 种语言；“关于”页面可离线查看项目、字体、软件依赖的完整许可正文及隐私说明。
- 应用未声明联网权限。字体、设置和生成模块保存在设备本地。

内置补字集合不代表完整 Unicode 覆盖，也不保证所有文字都采用相同设计或等宽排版。精确来源、版本、哈希、覆盖范围与授权见 [字体说明](design/FONTS.md)。

## 使用

1. 安装 APK，在当前 Magisk / KernelSU 管理器中授权 Root，再检查设备配置。
2. 选择或导入字体，按需调整补字与预览。
3. 进入“应用”，选择单字体的替换范围，或按风格混搭各类字体。
4. 点击“应用配置”生成模块，通过当前 Root 管理器的官方接口安装，然后自行确认重启。
5. 恢复时禁用本应用的模块并重启。应用不会自动重启。

使用了自带字体的应用可能保持原有排版。模块影响系统字体，请先准备可用的 Root 管理器 / ADB 恢复方式。更新 ROM 后，应先禁用旧模块并重启，再为新系统生成配置。旧系统基线不能仅凭厂商指纹相同而复用：模块同时校验 Android API 和系统增量版本。

## 构建

需要 JDK 21、Android SDK Platform 36、NDK 28.2.13676358、CMake 3.22.1 和网络可访问的 Gradle/Maven 仓库。原始字体与资源已经作为应用资源保留，普通构建无需运行字体准备脚本。

在不提交到仓库的 `local.properties` 中设置本机 SDK 路径，例如：

```properties
sdk.dir=/path/to/Android/Sdk
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
python tools/check-i18n.py
```

macOS / Linux：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
python3 tools/check-i18n.py
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。调试包使用构建机器的 Android 调试签名；正式发布需自行配置私有签名密钥，勿提交密钥或密码。

正式构建读取仓库根目录下、不提交到 Git 的 `keystore.properties`：

```properties
storeFile=/private/path/to/release.p12
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

配置后运行 `./gradlew :app:assembleRelease`（Windows 使用 `gradlew.bat`）。正式 APK 位于 `app/build/outputs/apk/release/app-release.apk`；未提供签名配置时会生成未签名包。保管好密钥与配置的离线备份，后续更新必须使用相同签名。

GitHub Release 的正式包与本地调试包签名不同，不能覆盖安装。调试版用户切换前应先备份数据、禁用字体模块并重启，再卸载调试版安装正式包。

更新依赖后，刷新并检查离线许可清单：

```powershell
.\gradlew.bat :app:exportRuntimeDependencies
python tools/prepare-legal.py
```

构建会检查运行时依赖与许可清单的一致性。字体准备脚本需要 Python 与 fontTools，图标打包另需 Pillow；具体顺序见 [字体来源](design/FONTS.md) 与 [资源说明](design/ASSETS.md)。

## 验证与恢复

2026-10-28：在官方 LOS 24.0-20261023（API 37、增量版本 `c4db0ea4ab`）与 KernelSU 3.3.1 上通过真实配置读取/生成、官方安装与停用、原有 MiSans/Noto Serif/Sarasa 配置恢复及重启后的字体服务检查。66 项单元测试通过。独立无 Root 探针在 Zygisk Next 1.5.2 的仅还原挂载模式下完成 79 个家族、4,424 次排版，实际使用的 34 个自定义字体文件路径均不可见；启用 HMA 3.8.3 与 SuSFS LKM 后结果相同。该结果不等于对所有隐藏策略或检测程序的保证。

从 0.1.1 起，生成的字体模块内置 Zygisk 字体预加载库，在应用挂载隔离前缓存所有 GlyphOrder 字体，包括 `/product/fonts` 中的字体。当前已验证 Magisk 30.7 与 Zygisk Next 1.5.2 的“仅还原挂载”模式。若隐藏策略完全阻止 Zygisk 模块加载，预加载也无法执行。升级 APK 后，需要重新应用字体配置并重启，才能更新已安装的模块。

`tools/font-probe` 是独立的极简 Android 测试应用，可用 `:font-probe:assembleDebug` 构建。将其包名 `dev.glyphorder.fontprobe` 加入待测应用使用的挂载隔离及应用列表隐藏规则后，启动测试即可覆盖系统字体家族、中英文及多个回退文字、100/400/700/900 字重和正斜体。结果保存在测试应用的 `files/result.json`，同时记录实际使用的字体路径是否仍然可见；应用不会请求 Root 或修改字体配置。

单元测试覆盖字体解析、字形覆盖、可变轴、按风格替换、XML 配置、系统家族上限与启动循环回归。冻结的 ROM XML 与覆盖率夹具位于 `app/src/test/resources/bootloop/`，不会安装模块或重启设备。

已授权的个人测试设备可运行 Android 测试。测试使用隔离的字体库与设置，安装模块的测试需要显式开启。测试方法和实际兼容边界见 [英文技术说明](README.en.md#verification)。

如需从已授权的 ADB Root Shell 禁用模块：

```sh
su -c 'touch /data/adb/modules/glyphorder_fonts/disable'
su -c '[ ! -d /data/adb/modules_update/glyphorder_fonts ] || touch /data/adb/modules_update/glyphorder_fonts/disable'
```

然后重启。不能进入系统时使用预先配置的 Magisk 安全模式或恢复方式。模块不会删除或直接改写真实的 `/system/fonts`。

## 许可证

项目原创代码与文档采用 **Apache License 2.0**，完整条款见 [LICENSE](LICENSE)，版权与归属见 [NOTICE](NOTICE)。选择该许可证可以保留宽松的修改和分发方式，并明确专利授权，与本项目使用的 Apache-2.0 软件依赖相协调。

字体、依赖库和第三方图标保留各自许可证：

- Noto、Rubik、Sarasa Fixed：SIL Open Font License 1.1。
- **MiSans / MiSans Global：小米专有字体许可协议，属于免费商用字体，不是开源字体。** 作为本应用的原样字体资源提供；不得因本项目开源而将其改为 Apache-2.0 或 OFL，不得修改字体或作为独立字体包再分发。原始协议 PDF 和可读文本随应用保留。
- AndroidX、Kotlin、libsu、Material Symbols 等：保留上游 Apache-2.0 条款与声明。

完整组件、版本、原始声明和字体协议见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，也可在应用顶部“关于”→“许可与声明”离线查看。项目许可证不授予第三方字体、商标或其他作品的额外权利。

## 贡献

欢迎通过 Issue 提供 ROM / Android 版本、复现步骤与相关错误信息；分享日志前先检查个人信息。提交代码时运行上述构建、单元测试和语言资源校验。新增依赖或字体时同步更新来源、哈希、原始授权和离线许可清单。除另行约定，提交的原创贡献按本项目 Apache-2.0 许可证提供。
