# NeuralMind Android Application

本地 AI 助手 Android 原生应用，支持 llama.cpp 推理引擎。

## 功能特性

### 核心功能
- 🤖 **本地 AI 聊天** - 使用 llama.cpp 进行本地推理
- 📦 **模型管理** - 下载、安装和切换不同的 AI 模型
- 🧠 **九层记忆系统** - 智能记忆管理
- 📱 **设备控制** - 控制 WiFi、蓝牙、音量等
- 🤖 **自动化任务** - 时间、位置、电量触发自动化
- 🛠️ **工具包** - 代码编辑器、终端、Git 等开发工具
- 📚 **技能模块** - 可扩展的技能系统

### 技术特点
- 100% 本地运行，保护隐私
- Kotlin + Jetpack Compose 现代化架构
- Room 数据库 + Hilt 依赖注入
- llama.cpp 原生集成
- Material 3 设计

## 项目结构

```
neuralmind-android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/neuralmind/
│   │   │   │   ├── data/              # 数据层
│   │   │   │   ├── domain/            # 领域层
│   │   │   │   ├── ui/                # UI 层
│   │   │   │   ├── llama/             # llama.cpp 集成
│   │   │   │   └── device/            # 设备控制
│   │   │   ├── cpp/                   # C++ 原生代码
│   │   │   └── res/                   # 资源文件
│   │   └── test/                      # 测试
│   └── build.gradle.kts
├── llama.cpp/                         # llama.cpp 子模块
└── build.gradle.kts
```

## 快速开始

### 环境要求
- Android Studio Giraffe 或更高
- JDK 17+
- Android SDK API 34+
- NDK 26.x (可选，用于 llama.cpp)

### 构建步骤

1. 克隆项目
```bash
git clone https://github.com/your-username/neuralmind-android.git
cd neuralmind-android
```

2. 初始化子模块 (可选，用于 llama.cpp)
```bash
git submodule update --init --recursive
```

3. 在 Android Studio 中打开项目

4. 同步 Gradle 依赖

5. 连接 Android 设备或启动模拟器

6. 点击 "Run" 按钮

### 命令行构建

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建
./gradlew assembleRelease

# 运行测试
./gradlew test

# 连接设备安装
./gradlew installDebug
```

## 模块说明

### 数据层 (Data)
- **Repository** - 数据仓库，负责数据管理
- **DAO** - 数据访问对象
- **Entity** - 数据库实体

### 领域层 (Domain)
- **Model** - 领域模型
- **UseCase** - 业务逻辑

### UI 层
- **Screen** - 页面组件
- **ViewModel** - 视图模型
- **Component** - 可复用 UI 组件

### 原生集成 (Native)
- **llama.cpp** - C++ 推理引擎
- **JNI** - Java 原生接口

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

本项目采用 MIT 许可证。

## 致谢

- llama.cpp 团队
- Jetpack Compose 团队
- Android 开源社区
