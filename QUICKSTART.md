# NeuralMind Android 应用 - 快速开始指南

## 📋 项目介绍

NeuralMind 是一个基于 Android 的本地 AI 助手应用，使用 Kotlin + Jetpack Compose 构建，集成了 llama.cpp 本地推理引擎。

## 🚀 环境准备

### 必需工具

- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **JDK 17** 或更高版本
- **Android SDK API 34** 或更高版本
- **NDK r26** (可选，用于构建 llama.cpp)

### 推荐配置

- 内存：16GB+ RAM
- 硬盘：至少 10GB 可用空间
- Android 设备或模拟器，Android 8.0 (API 26) 或更高版本

## 🔧 快速开始

### 1. 克隆或打开项目

在 Android Studio 中：
- 选择 "File" → "Open"
- 选择 `neuralmind-android` 文件夹
- 等待 Gradle 同步完成

### 2. Gradle 同步

首次打开项目时，Android Studio 会自动：
- 下载 Gradle Wrapper
- 下载所有依赖库
- 配置项目

如果没有自动同步，点击：
- "File" → "Sync Project with Gradle Files"

### 3. 创建 Gradle Wrapper (可选)

如果需要手动创建 Gradle Wrapper，在项目根目录运行：

```bash
# 使用系统已安装的 Gradle
gradle wrapper --gradle-version 8.4
```

### 4. 配置 Android SDK

打开 `local.properties` 文件（如果不存在会自动创建），并确保配置正确：

```properties
sdk.dir=/path/to/your/android/sdk
# 如果需要 NDK
ndk.dir=/path/to/your/ndk
```

### 5. 构建项目

在 Android Studio 中：
- 点击 "Build" → "Make Project"
- 或使用快捷键 `Ctrl+F9`

命令行构建：
```bash
# 调试版本
./gradlew assembleDebug

# 发布版本
./gradlew assembleRelease
```

## 📱 运行应用

### 使用模拟器

1. 在 Android Studio 中，点击工具栏的设备选择器
2. 选择 "Device Manager" → 创建设备
3. 选择设备配置（推荐 Pixel 6 或更高）
4. 选择系统镜像（推荐 API 34 或更高）
5. 等待模拟器启动
6. 点击 "Run" 按钮 (绿色三角形)

### 使用真实设备

1. 在手机上启用 "开发者选项"
2. 启用 "USB 调试"
3. 使用 USB 连接手机
4. 在手机上允许 USB 调试
5. 在 Android Studio 中选择您的设备
6. 点击 "Run" 按钮

## 🏗️ 项目架构

### 核心架构：MVVM + Clean Architecture

```
┌─────────────────┐
│    UI Layer     │ ← Compose Screens & ViewModels
├─────────────────┤
│  Domain Layer   │ ← UseCases & Models
├─────────────────┤
│   Data Layer    │ ← Repository & Database
└─────────────────┘
```

### 模块说明

| 包名 | 说明 |
|------|------|
| `ui` | 用户界面，使用 Jetpack Compose |
| `viewmodel` | 视图模型，处理 UI 逻辑 |
| `domain` | 领域层，包含业务逻辑和数据模型 |
| `data` | 数据层，Room 数据库和 Repository |
| `di` | Hilt 依赖注入模块 |
| `llama` | llama.cpp 集成 |
| `device` | 设备控制功能 |

## 💾 数据库说明

### Room 数据库实体

1. **Conversation** - 对话会话
2. **Message** - 消息内容
3. **Model** - AI 模型信息
4. **Memory** - 九层记忆系统
5. **Skill** - 技能模块
6. **AutomationRule** - 自动化规则
7. **ToolModule** - 工具包模块

## 🤖 添加默认数据

应用首次启动时，会自动：
- 插入默认的 AI 模型列表（4个）
- 插入默认的技能模块（5个）
- 插入默认的工具包（6个）

这些数据定义在各个 Repository 的 `insertDefault*` 方法中。

## 🔌 集成 llama.cpp

### 当前状态

目前 llama.cpp 集成是**占位实现**，包含：
- JNI 接口定义
- 简单的 C++ 占位代码
- 推理引擎抽象

### 完整集成步骤

1. 添加 llama.cpp 作为 Git 子模块：
```bash
git submodule add https://github.com/ggerganov/llama.cpp.git
```

2. 更新 `app/src/main/cpp/CMakeLists.txt`
3. 实现完整的 JNI 绑定
4. 添加更多模型选项

## 📱 功能概览

### 已实现的核心框架

✅ **聊天模块** - UI 界面和数据结构
✅ **模型管理** - 模型库和下载功能
✅ **九层记忆** - 完整的记忆系统架构
✅ **设备控制** - WiFi、蓝牙、音量等
✅ **技能模块** - 插件化技能系统
✅ **工具包** - 开发工具集合

### 待完善的功能

🔄 **llama.cpp 完整推理**
🔄 **自动化任务调度**
🔄 **语音输入/输出**
🔄 **工具包的真实实现**

## 🐛 常见问题

### Gradle 同步失败

1. 检查网络连接
2. 清理缓存：`Build` → `Clean Project`
3. 重新同步：`File` → `Sync Project with Gradle Files`

### 构建错误

1. 确保使用正确的 JDK 版本
2. 更新 Android SDK
3. 检查 `local.properties` 配置

### 设备连接问题

1. 确认 USB 调试已启用
2. 尝试更换 USB 线
3. 检查 Android Studio 识别设备

## 📚 进一步开发

### 1. 完善聊天功能

修改 `ChatViewModel` 和 `ChatScreen`，添加：
- AI 回复生成
- 消息流式显示
- 模型切换

### 2. 实现真实的推理

在 `llama` 包中完善：
- 真正的 JNI 实现
- 模型加载
- 推理执行

### 3. 添加设备控制权限

在 `AndroidManifest.xml` 中添加必要的权限。

### 4. 集成工作管理器

实现自动化任务调度功能。

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### 代码风格

- 遵循 Kotlin 官方代码风格
- 使用 Jetpack Compose 最佳实践
- 添加适当的注释

### 提交规范

使用常规提交格式：
```
<type>(<scope>): <description>

例如：
feat(chat): add streaming support
fix(model): correct download logic
docs(readme): update setup guide
```

## 📄 许可证

MIT License

## 📞 支持

如有问题，请提交 Issue。

---

**享受使用 NeuralMind！** 🎉
