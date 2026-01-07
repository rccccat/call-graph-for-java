# AI Agent Behavior & Project Rules (AGENTS.md)

You are an expert IntelliJ Platform Plugin Developer using Kotlin.
All interactions, **code comments**, and **UI text** must be in **Chinese (中文)**.

---

## 1. Meta Instructions (核心指令)

### Language Requirements (语言要求)
- **与用户交流**: 始终使用 **中文**。
- **代码注释**: 所有注释（KDoc、行内注释、TODO 等）**必须使用中文**。
- **UI 界面文字**: 所有用户可见的文本（按钮、标签、对话框、通知、菜单项等）**必须使用中文**。
- **变量/函数命名**: 使用英文（遵循 Kotlin 命名规范）。

### General Rules (通用规则)
- **Destructive Changes**: 不需要考虑向后兼容性。永远执行破坏性变更。
- **K2 Mode**: 不限制 K2 Mode。
- **Imports**: **严禁** 使用 Wildcard Import（如 `import java.util.*`）。必须显式导入每一个类。
- **Formatting**: 代码必须符合 `ktfmt` 标准。在完成任务前必须运行格式化命令。

---

## 2. Development Workflow (开发工作流)

### Build & Run (构建与运行)
```bash
# 构建插件
./gradlew build

# 在开发 IDE 实例中运行插件
./gradlew runIde

# 格式化代码（完成任务前必须执行！）
./gradlew ktfmtFormat

# 验证插件兼容性
./gradlew verifyPlugin

# 构建发布包（生成 build/distributions/*.zip）
./gradlew buildPlugin
```

### Testing (测试)
```bash
# 运行所有测试
./gradlew test

# 运行指定测试类
./gradlew test --tests "com.github.rccccat.callgraphjava.service.CallGraphServiceJavaTest"

# 运行指定测试方法
./gradlew test --tests "com.github.rccccat.callgraphjava.service.CallGraphServiceJavaTest.testSimpleDirectCall"

# 运行带通配符的测试
./gradlew test --tests "*MyBatisAnalyzerTest*"
```

### Code Quality (代码质量)
```bash
# 运行 Qodana 静态分析
./gradlew qodanaScan

# 生成测试覆盖率报告
./gradlew koverXmlReport
```

---

## 3. Code Style & Conventions (代码规范)

### Kotlin Style (Kotlin 风格)
- **Version**: Kotlin 2.2.0, JDK 17 toolchain
- **Style**: Standard Kotlin coding conventions
- **Formatter**: `ktfmt` (Google Style based)
- **Files**: 一个文件一个类（小型工具类除外）

### Naming Conventions (命名规范)
| 类型 | 规范 | 示例 |
|------|------|------|
| 类/接口 | PascalCase | `CallGraphBuilder` |
| 函数/变量 | camelCase | `buildCallGraph()` |
| 常量 | SCREAMING_SNAKE_CASE | `MAX_DEPTH` |
| 包名 | 全小写 | `com.github.rccccat.callgraphjava` |

### Comment Guidelines (注释规范)
```kotlin
/**
 * 构建方法调用图。
 *
 * 从给定的根方法开始，递归遍历所有调用链，
 * 并根据配置的深度限制和排除规则进行过滤。
 *
 * @param rootMethod 根方法（起始点）
 * @param maxDepth 最大遍历深度
 * @return 完整的调用图数据
 */
fun buildCallGraph(rootMethod: PsiMethod, maxDepth: Int): CallGraphData {
    // 检查缓存中是否已存在
    val cached = cache.get(rootMethod)
    if (cached != null) return cached
    
    // TODO: 支持 Kotlin 协程的调用追踪
}
```

### UI Text Guidelines (UI 文字规范)
```kotlin
// 正确示例 - 使用中文
JLabel("调用深度限制")
Messages.showInfoMessage("分析完成，共发现 ${count} 个调用", "成功")
addActionButton("刷新", AllIcons.Actions.Refresh)

// 错误示例 - 使用英文
JLabel("Max Depth")  // ❌ 禁止
Messages.showInfoMessage("Analysis complete", "Success")  // ❌ 禁止
```

### Import Guidelines (导入规范)
```kotlin
// ✅ 正确 - 显式导入
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

// ❌ 错误 - 通配符导入
import com.intellij.psi.*
```

### Error Handling (错误处理)
```kotlin
// 使用 runCatching 或 try-catch，错误消息使用中文
try {
    processMethod(method)
} catch (e: ProcessCanceledException) {
    throw e  // 不要吞掉取消异常
} catch (e: Exception) {
    LOG.warn("处理方法失败: ${method.name}", e)
    showNotification("分析出错: ${e.message}", NotificationType.ERROR)
}
```

---

## 4. Project Structure (项目结构)

```
src/main/kotlin/com/github/rccccat/callgraphjava/
├── actions/           # 编辑器动作（右键菜单等）
├── api/               # 公共接口和纯数据模型
│   └── model/         # CallGraphData 等（无 PSI 依赖）
├── core/              # 核心分析引擎
│   ├── visitor/       # 访问者模式（CallVisitor, JavaCallVisitor）
│   ├── traversal/     # 遍历策略（GraphTraverser）
│   └── resolver/      # 类型解析（TypeResolver, InterfaceResolver）
├── framework/         # 框架支持
│   ├── spring/        # Spring 分析器
│   └── mybatis/       # MyBatis 分析器
├── export/            # 导出功能（JSON, Scanner）
├── ide/               # IDE 集成层
│   ├── model/         # IdeCallGraphNode（含 PSI 指针）
│   └── psi/           # PsiNodeFactory
├── service/           # 服务层实现
├── settings/          # 配置和设置 UI
├── toolWindow/        # 工具窗口内容
├── ui/                # UI 组件
│   └── toolwindow/    # 工具窗口子组件
├── cache/             # 缓存管理
└── util/              # 工具类
```

---

## 5. Testing Pattern (测试模式)

### Test Base Class
- 测试类继承 `BasePlatformTestCase`（IntelliJ SDK）
- 使用 `myFixture` 进行内存项目操作

### Test Example
```kotlin
class MyFeatureTest : BasePlatformTestCase() {
    
    fun testSimpleCase() {
        // 准备测试代码
        myFixture.configureByText(
            "TestClass.java",
            """
            public class TestClass {
                public void caller() {
                    callee();
                }
                public void callee() {}
            }
            """.trimIndent()
        )
        
        // 获取待测方法
        val psiClass = myFixture.findClass("TestClass")
        val method = psiClass.findMethodsByName("caller", false).first()
        
        // 执行测试
        val result = service.buildCallGraph(method)
        
        // 验证结果
        assertEquals(1, result.children.size)
    }
}
```

---

## 6. Dependencies (依赖)

### Gradle Catalog
依赖版本定义在 `gradle/libs.versions.toml`：
- **Kotlin**: 2.2.0
- **IntelliJ Platform**: 2.10.5
- **JUnit**: 4.13.2
- **Gson**: 2.10.1

### Adding Dependencies
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
}
```

---

## 7. Checklist Before Completion (完成前检查清单)

在标记任务完成前，必须确认以下事项：

- [ ] **格式化**: 已运行 `./gradlew ktfmtFormat`
- [ ] **导入检查**: 没有使用 `.*` 通配符导入
- [ ] **中文注释**: 所有新增注释使用中文
- [ ] **中文 UI**: 所有新增 UI 文字使用中文
- [ ] **测试**: 已运行相关测试 `./gradlew test --tests "..."`
- [ ] **构建**: 项目可以成功构建 `./gradlew build`

---

## 8. Common Patterns (常用模式)

### PSI 操作
```kotlin
// 在读取操作中访问 PSI
ReadAction.compute<Result, Exception> {
    psiElement.text
}

// 在写入操作中修改 PSI
WriteCommandAction.runWriteCommandAction(project) {
    // 修改代码
}
```

### 后台任务
```kotlin
ProgressManager.getInstance().run(
    object : Task.Backgroundable(project, "正在分析调用图...", true) {
        override fun run(indicator: ProgressIndicator) {
            indicator.text = "正在扫描方法..."
            // 执行分析
        }
    }
)
```

### 通知
```kotlin
NotificationGroupManager.getInstance()
    .getNotificationGroup("Call Graph")
    .createNotification("分析完成", NotificationType.INFORMATION)
    .notify(project)
```

---

## 9. Debugging Tips (调试技巧)

```bash
# 在调试模式下运行 IDE
./gradlew runIde --debug-jvm

# 查看插件日志
# Help → Diagnostic Tools → Debug Log Settings
# 添加: #com.github.rccccat.callgraphjava
```

---

## 10. Resources (资源)

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [ktfmt](https://github.com/facebook/ktfmt)
