# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the plugin
./gradlew build

# Run the plugin in a development IDE instance
./gradlew runIde

# Run tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.github.rccccat.ideacallgraph.service.CallGraphServiceJavaTest"

# Clean build
./gradlew clean build

# Format code (required before commits)
./gradlew ktfmtFormat

# Verify plugin compatibility
./gradlew verifyPlugin

# Build plugin distribution (creates zip in build/distributions)
./gradlew buildPlugin
```

## Project Overview

This is an IntelliJ IDEA plugin that generates and visualizes method call graphs for Java code. It supports Spring Framework and MyBatis integration.

## Architecture

### Core Flow
1. **Action Trigger**: `GenerateCallGraphAction` is invoked from editor context menu (Ctrl+Alt+G)
2. **Build Graph**: `CallGraphServiceImpl` delegates to `CallGraphBuilder` to traverse PSI elements
3. **Display**: `CallGraphToolWindowContent` renders the graph as an interactive tree

### Key Components

**Core Layer** (`core/`):
- `CallGraphBuilder` - Orchestrates graph building
- `visitor/CallVisitor` + `JavaCallVisitor` - PSI traversal
- `traversal/GraphTraverser` + `DepthFirstTraverser` - Traversal strategy
- `resolver/TypeResolver` + `InterfaceResolver` - Type resolution and interface dispatch

**Framework Layer** (`framework/`):
- `spring/SpringAnalyzer` - Unified Spring analysis facade (with `JavaSpringAnalyzer`, `SpringInjectionAnalyzer`)
- `mybatis/MyBatisAnalyzer` - Mapper/XML SQL resolution

**IDE Layer** (`ide/`):
- `model/IdeCallGraph` + `IdeCallGraphNode` - PSI-aware graph model with SmartPsiElementPointer
- `psi/PsiNodeFactory` - PSI → graph node conversion

**API/Data Model** (`api/`):
- `CallGraphService` - Public service interface
- `model/CallGraphData` - Pure data structures (no PSI dependencies)

**Service Layer** (`service/`):
- `CallGraphServiceImpl` - Main service implementation
- `AnalyzerRegistry` - Framework analyzer registration

**Export** (`export/`):
- `JsonExporter` / `CodeExtractor` - JSON export with code snippets
- `SpringApiScanner` / `MyBatisMapperScanner` - Endpoint/mapping scanning

### Plugin Registration

- Plugin ID: `com.github.rccccat.ideacallgraph`
- Entry points defined in `src/main/resources/META-INF/plugin.xml`
- Depends on `com.intellij.java`

## Technical Notes

- Target IntelliJ Platform: 2024.3+ (build 243+)
- JVM toolchain: Java 21
- Uses SmartPsiElementPointer to safely reference PSI elements across EDT operations
- ReadAction.compute() is used for PSI access from background threads
- Depth limits: separate settings for project code vs third-party libraries
