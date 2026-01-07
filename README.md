# Call Graph for Java

Call Graph for Java 是一个 IntelliJ IDEA 插件，用于从 Java 代码生成和可视化调用图。它可以追踪方法调用链、解析 Spring 依赖注入，并将 MyBatis Mapper 方法关联到 SQL 语句。

## 功能特性

- **Java 支持**: 从 `PsiMethod` 构建调用图
- **交互式调用图树**: 可展开的树形结构，支持深度和子节点限制
- **Spring 感知分析**: Controller/Service/端点识别，DI 感知的接口解析，`@Qualifier`/`@Primary` 支持
- **MyBatis 集成**: 从注解/XML 检测 Mapper 接口，添加 SQL 语句节点
- **Spring API 浏览器**: 列出端点，支持搜索和导出筛选的调用图到 JSONL
- **MyBatis 映射浏览器**: 浏览 SQL 映射，跳转到 Mapper 接口或 XML
- **JSON 导出（含代码）**: 美化/紧凑 JSON，包含每个节点的源码
- **导航和上下文操作**: 双击导航，复制签名，查看方法详情
- **可配置分析**: 深度限制，排除模式（包/类/方法/签名），方法过滤器，接口解析，MyBatis XML 扫描
- **后台任务**: 进度指示器和索引检查

## 架构

插件采用分层架构，分离 PSI 分析、框架集成和 UI：

```
com.github.rccccat.callgraphjava/
├── actions/                # 编辑器操作（生成调用图）
├── api/                    # 公共接口和纯数据模型
│   ├── CallGraphService
│   └── model/              # CallGraphData/CallGraphNodeData/CallType（无 PSI 依赖）
├── core/                   # 核心分析引擎
│   ├── CallGraphBuilder    # 协调图构建
│   ├── visitor/            # 访问者模式用于代码遍历
│   │   ├── CallVisitor
│   │   └── JavaCallVisitor
│   ├── traversal/          # 图遍历策略
│   │   ├── GraphTraverser
│   │   └── DepthFirstTraverser
│   ├── resolver/           # 类型和接口解析
│   │   ├── TypeResolver
│   │   └── InterfaceResolver
├── framework/              # 框架特定分析器
│   ├── spring/             # SpringAnalyzer + JavaSpringAnalyzer + SpringInjectionAnalyzer
│   └── mybatis/            # MyBatisAnalyzer
├── export/                 # 导出和扫描工具
│   ├── JsonExporter
│   ├── CodeExtractor
│   ├── SpringApiScanner
│   └── MyBatisMapperScanner
├── ide/                    # IDE 集成层
│   ├── model/              # IdeCallGraphNode, IdeCallGraph（含 PSI 指针）
│   └── psi/                # PsiNodeFactory
├── service/                # 服务层
│   └── CallGraphServiceImpl
├── settings/               # 配置和 UI
│   ├── CallGraphConfigurable
│   ├── CallGraphProjectSettings
│   └── CallGraphAppSettings
├── toolWindow/             # 工具窗口 UI
│   ├── MyToolWindowFactory
│   ├── CallGraphToolWindowContent
│   ├── SpringApisToolWindowContent
│   └── MyBatisMappingsToolWindowContent
├── ui/                     # UI 组件
│   ├── CallGraphTreeRenderer
│   ├── CallGraphNodeNavigator
│   ├── CallGraphNodeText
│   ├── JsonPreviewDialog
│   └── toolwindow/
│       └── TreeConfiguration
└── util/                   # 注解辅助工具和 PSI 工具
    ├── AnnotationUtils
    ├── AnnotationSearch
    ├── FrameworkAnnotations
    └── ProjectCodeUtils
```

### 关键设计模式

- **访问者模式**: `CallVisitor` 接口配合 Java 实现，用于可扩展的遍历
- **策略模式**: `GraphTraverser` 用于遍历算法
- **外观模式**: `SpringAnalyzer` 提供统一的 Spring 分析访问
- **服务层**: IDE 特定与纯数据模型的清晰分离

<!-- Plugin description -->
Call Graph for Java is a call graph analysis tool for IntelliJ IDEA that generates interactive visualizations of method call hierarchies in Java projects. It understands Spring endpoints and dependency injection, links MyBatis mapper methods to SQL statements, and supports JSON export with embedded code. The tool window includes dedicated tabs for call graphs, Spring APIs, and MyBatis mappings, plus search and JSONL export for Spring endpoints.
<!-- Plugin description end -->

## 使用方法

### 基本调用图生成

1. **生成调用图**:
   - 在 Java 代码中右键点击任意方法
   - 从上下文菜单选择「生成调用图」
   - 或使用快捷键 `Ctrl+Alt+G`（Windows/Linux）/ `Cmd+Alt+G`（Mac）

2. **查看结果**:
   - 调用图将显示在「Call Graph」工具窗口中
   - 通过树形结构浏览方法依赖关系
   - 双击任意节点跳转到对应代码
   - 右键点击节点可复制签名或查看方法详情

3. **导出数据**:
   - 在工具窗口中使用「查看 JSON」预览调用图
   - 导出的 JSON 包含每个节点的源码，便于离线分析

### Spring API 浏览器

1. **浏览 API**:
   - 打开工具窗口中的「Spring APIs」标签页
   - 点击「刷新」扫描项目中的所有 Spring 端点
   - 使用搜索框按类名、方法名或签名过滤

2. **导出 API**:
   - 点击「导出」将筛选的端点导出为 JSONL
   - 每行包含一个端点的调用图

### MyBatis 映射浏览器

1. **浏览映射**:
   - 打开工具窗口中的「MyBatis Mappings」标签页
   - 点击「刷新」扫描所有 Mapper 方法
   - 双击跳转到 Mapper 接口或 XML SQL

### 配置

1. **自定义分析**:
   - 前往 `Settings/Preferences > Tools > Call Graph`
   - 配置项目/第三方库深度限制
   - 设置排除模式（正则）用于包/类/方法/签名过滤
   - 控制接口实现解析和遍历广度
   - 启用完整 XML 扫描用于 MyBatis（较慢但更完整）

## 安装

- 使用 IDE 内置插件系统：

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>搜索 "Call Graph for Java"</kbd> >
  <kbd>Install</kbd>

- 手动安装：

  下载 [最新版本](https://github.com/rccccat/call-graph-for-java/releases/latest) 并手动安装：
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## 系统要求

- IntelliJ IDEA 2023.1 或更高版本（build 241+）
- Java 项目
- 需要完成项目索引才能进行准确分析

## 支持的框架

- **Spring Framework**: 检测 Controller、Service、REST 端点和 DI 模式
- **MyBatis**: 追踪 Mapper 接口、注解 SQL 和 XML SQL 映射
- **标准 Java**: 所有方法调用和函数调用

## 开发

### 构建命令

```bash
# 构建插件
./gradlew build

# 在开发 IDE 实例中运行插件
./gradlew runIde

# 运行测试
./gradlew test

# 运行指定测试类
./gradlew test --tests "com.github.rccccat.callgraphjava.service.CallGraphServiceJavaTest"

# 构建插件发布包（在 build/distributions 生成 zip）
./gradlew buildPlugin

# 格式化代码（提交前必须执行）
./gradlew ktfmtFormat

# 验证插件兼容性
./gradlew verifyPlugin

# 使用本地 IDE 发布版验证（可选）
./gradlew verifyPlugin -PlocalIdePath="/Applications/IntelliJ IDEA CE.app"
```

### 技术栈

- Kotlin 2.2.x
- IntelliJ Platform SDK 2023.1+
- JDK 17 toolchain
- Gradle with Kotlin DSL
- Gson for JSON serialization

## 贡献

1. Fork 仓库
2. 创建功能分支
3. 进行修改
4. 运行 `./gradlew ktfmtFormat` 格式化代码
5. 运行 `./gradlew test` 确保测试通过
6. 提交 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见仓库。
