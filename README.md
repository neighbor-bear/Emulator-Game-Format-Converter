# 🎮 Emulator Game Format Converter / 模拟器游戏格式转换器

一款 Android 平台的模拟器游戏镜像格式转换工具，目前支持PSP的 ISO 与 CSO 格式之间的相互转换，以后会逐步添加更多格式转换。


## ✨ 功能特性

- **ISO → CSO**：将 ISO 镜像压缩为 CSO 格式，节省存储空间（压缩等级 9）
- **CSO → ISO**：将 CSO 镜像解压为 ISO 格式，兼容更多模拟器
- **批量转换**：支持选择文件夹，一键转换目录下所有匹配文件
- **单文件转换**：支持选择单个文件进行转换
- **实时进度**：转换过程中显示当前文件名和进度百分比
- **智能跳过**：自动跳过已存在的目标文件，避免重复转换

## 📱 截图

<!-- TODO: 添加应用截图 -->

## 💡 灵感来源

本项目的核心转换算法移植自 [CSOChef](https://github.com/RetroChef/csochef) —— 一个基于 Python 的 ISO/CSO 压缩解压工具。感谢 RetroChef 大佬的开源贡献！

CSOChef 是一个命令行工具，仅支持桌面平台（Windows/Linux/macOS）。本项目将其核心算法移植到 Android 平台，让用户可以直接在手机上完成模拟器游戏格式的转换，无需借助电脑。

## 🔧 技术细节

- **语言**：Java
- **最低 SDK**：Android 9.0 (API 28)
- **目标 SDK**：Android 15 (API 35)
- **构建工具**：Gradle 8.9
- **核心算法**：基于 zlib 的 DEFLATE 压缩/解压，CSO 格式遵循 CISO v1 规范
- **块大小**：2048 字节（标准 PSP ISO 块大小）

## 📋 使用方法

1. 授予应用"所有文件访问权限"
2. 选择转换模式：
   - **CSO（压缩ISO）**：将 ISO 文件压缩为 CSO
   - **ISO（解压CSO）**：将 CSO 文件解压为 ISO
3. 选择文件或文件夹：
   - 点击"选择文件夹"批量转换整个目录
   - 点击"选择文件"转换单个文件
4. 点击"开始转换"，等待完成

## 🏗️ 构建项目

```bash
# 克隆仓库
git clone https://github.com/neighbor-bear/Emulator-Game-Format-Converter.git

# 使用 Gradle 构建
cd Emulator-Game-Format-Converter
./gradlew assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/`。

## 📄 许可证

本项目基于 BSD-2-Clause 许可证开源，与灵感来源项目 [CSOChef](https://github.com/RetroChef/csochef) 保持一致。
