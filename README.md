# 字序 / GlyphOrder

[English](README.en.md) · [Apache-2.0](LICENSE) · [第三方声明](THIRD_PARTY_NOTICES.md) · [隐私说明](PRIVACY.md)

通过 Magisk 模块管理 Android 系统字体，使用 Kotlin、Jetpack Compose 与 Material 3 Expressive 构建。支持 Android 14 及以上；目前主要在 OnePlus 11 的 LineageOS 23.2 / Android 16 上验证。

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

1. 安装 APK，允许 Magisk Root 请求，再检查设备配置。
2. 选择或导入字体，按需调整补字与预览。
3. 进入“应用”，选择单字体的替换范围，或按风格混搭各类字体。
4. 点击“应用配置”生成并安装 Magisk 模块，然后自行确认重启。
5. 恢复时禁用本应用的模块并重启。应用不会自动重启。

使用了自带字体的应用可能保持原有排版。模块影响系统字体，请先准备可用的 Magisk / ADB 恢复方式。更新 ROM 后，应先禁用旧模块并重启，再为新系统生成配置。

## 构建

需要 JDK 21、Android SDK Platform 36 和网络可访问的 Gradle/Maven 仓库。原始字体与资源已经作为应用资源保留，普通构建无需运行字体准备脚本。

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

更新依赖后，刷新并检查离线许可清单：

```powershell
.\gradlew.bat :app:exportRuntimeDependencies
python tools/prepare-legal.py
```

构建会检查运行时依赖与许可清单的一致性。字体准备脚本需要 Python 与 fontTools，图标打包另需 Pillow；具体顺序见 [字体来源](design/FONTS.md) 与 [资源说明](design/ASSETS.md)。

## 验证与恢复

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

完整组件、版本、原始声明和字体协议见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，也可在应用顶部“关于”→“开源许可”离线查看。项目许可证不授予第三方字体、商标或其他作品的额外权利。

## 贡献

欢迎通过 Issue 提供 ROM / Android 版本、复现步骤与相关错误信息；分享日志前先检查个人信息。提交代码时运行上述构建、单元测试和语言资源校验。新增依赖或字体时同步更新来源、哈希、原始授权和离线许可清单。除另行约定，提交的原创贡献按本项目 Apache-2.0 许可证提供。
