# AI Agent Behavior & Project Rules (AGENTS.md)

You are an expert IntelliJ Platform Plugin Developer using Kotlin.
All interactions and code comments must be in **Chinese**.

## 1. Meta Instructions (核心指令)
- **Language**: 始终使用 **中文** 与用户交流。
- **Destructive Changes**: 不需要考虑向后兼容性 (Backward Compatibility)。永远执行破坏性变更。
- **K2 Mode**: 不限制 K2 Mode。
- **Imports**: **严禁** 使用 Wildcard Import (e.g., `import java.util.*`)。必须显式导入每一个类。
- **Formatting**: 代码必须符合 `ktfmt` 标准。在提交前必须运行格式化命令。

## 2. Development Workflow (开发工作流)

### Build & Run
- **Build Plugin**: `./gradlew build`
- **Run in IDE**: `./gradlew runIde`
- **Format Code**: `./gradlew ktfmtFormat` (CRITICAL: Run this before marking task complete)
- **Verify Plugin**: `./gradlew verifyPlugin`

### Testing
- **Run All Tests**: `./gradlew test`
- **Run Single Test Class**:
  ```bash
  ./gradlew test --tests "com.github.rccccat.ideacallgraph.service.CallGraphServiceJavaTest"
  ```
- **Run Single Test Method**:
  ```bash
  ./gradlew test --tests "com.github.rccccat.ideacallgraph.service.CallGraphServiceJavaTest.testSimpleDirectCall"
  ```

## 3. Code Style & Conventions (代码规范)

### Kotlin
- **Version**: Kotlin 2.2.0, Java 21.
- **Style**: Standard Kotlin coding conventions.
- **Indentation**: Follow `ktfmt` (Google Style based).
- **Files**: One class per file is preferred unless they are small utility classes.

### Project Structure
- **Core Logic**: `src/main/kotlin/com/github/rccccat/ideacallgraph/core/`
- **UI Components**: `src/main/kotlin/com/github/rccccat/ideacallgraph/ui/`
- **Framework Support**: `src/main/kotlin/com/github/rccccat/ideacallgraph/framework/`
  - Spring: `framework/spring/`
  - MyBatis: `framework/mybatis/`

### Testing Pattern
- Tests often extend `BasePlatformTestCase` (IntelliJ SDK).
- Use `myFixture` for in-memory project manipulation.
- Example:
  ```kotlin
  fun testFeature() {
      myFixture.configureByText("TestFile.java", "...")
      // assertions
  }
  ```

## 4. Dependencies
- **Gradle Catalog**: defined in `gradle/libs.versions.toml`.
- **JUnit**: Version 4 (`libs.junit`).
- **Gson**: Used for JSON serialization.

## 5. Checklist Before Completion
1. **Format**: Is the code formatted? (`./gradlew ktfmtFormat`)
2. **Wildcards**: Are there any `.*` imports? (Remove them)
3. **Tests**: Did you run relevant tests? (`./gradlew test ...`)
4. **Build**: Does the project build? (`./gradlew build`)
