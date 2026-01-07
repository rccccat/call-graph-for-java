# Call Graph for Java

Call Graph for Java is an IntelliJ IDEA plugin for generating and visualizing call graphs from Java code. It helps trace method invocation chains, resolve Spring dependency injection, and connect MyBatis mapper methods to SQL statements.

## Features

- **Java support**: Build call graphs from `PsiMethod`
- **Interactive call graph tree**: Expandable tree with depth and child limits
- **Spring-aware analysis**: Controllers/services/endpoints, DI-aware interface resolution, `@Qualifier`/`@Primary`
- **MyBatis integration**: Detect mapper interfaces from annotations/XML and add SQL statement nodes
- **Spring APIs browser**: List endpoints with search and export filtered call graphs to JSONL
- **MyBatis mappings browser**: Browse SQL mappings and jump to mapper/XML
- **JSON export with code**: Pretty/compact JSON including each node's self code
- **Navigation and context actions**: Double-click navigation, copy signatures, view method details
- **Configurable analysis**: Depth limits, exclude patterns (package/class/method/signature), method filters, interface resolution, MyBatis XML scan
- **Background tasks**: Progress indicators and indexing checks

## Architecture

The plugin follows a layered architecture separating PSI analysis, framework integrations, and UI:

```
com.github.rccccat.ideacallgraph/
├── actions/                # Editor actions (Generate Call Graph)
├── api/                    # Public interfaces and pure data models
│   ├── CallGraphService
│   └── model/              # CallGraphData/CallGraphNodeData/CallType (no PSI dependencies)
├── core/                   # Core analysis engine
│   ├── CallGraphBuilder    # Orchestrates graph building
│   ├── visitor/            # Visitor pattern for code traversal
│   │   ├── CallVisitor
│   │   └── JavaCallVisitor
│   ├── traversal/          # Graph traversal strategies
│   │   ├── GraphTraverser
│   │   └── DepthFirstTraverser
│   ├── resolver/           # Type and interface resolution
│   │   ├── TypeResolver
│   │   └── InterfaceResolver
├── framework/              # Framework-specific analyzers
│   ├── spring/             # SpringAnalyzer + JavaSpringAnalyzer + SpringInjectionAnalyzer
│   └── mybatis/            # MyBatisAnalyzer
├── export/                 # Export and scanning utilities
│   ├── JsonExporter
│   ├── CodeExtractor
│   ├── SpringApiScanner
│   └── MyBatisMapperScanner
├── ide/                    # IDE integration layer
│   ├── model/              # IdeCallGraphNode, IdeCallGraph (with PSI pointers)
│   └── psi/                # PsiNodeFactory
├── service/                # Service layer
│   └── CallGraphServiceImpl
├── settings/               # Configuration and UI
│   ├── CallGraphConfigurable
│   ├── CallGraphProjectSettings
│   └── CallGraphAppSettings
├── toolWindow/             # Tool window UI
│   ├── MyToolWindowFactory
│   ├── CallGraphToolWindowContent
│   ├── SpringApisToolWindowContent
│   └── MyBatisMappingsToolWindowContent
├── ui/                     # UI components
│   ├── CallGraphTreeRenderer
│   ├── CallGraphNodeNavigator
│   ├── CallGraphNodeText
│   ├── JsonPreviewDialog
│   └── toolwindow/
│       └── TreeConfiguration
└── util/                   # Annotation helpers and PSI utilities
    ├── AnnotationUtils
    ├── AnnotationSearch
    ├── FrameworkAnnotations
    └── ProjectCodeUtils
```

### Key Design Patterns

- **Visitor Pattern**: `CallVisitor` interface with Java implementation for extensible traversal
- **Strategy Pattern**: `GraphTraverser` for traversal algorithms
- **Facade Pattern**: `SpringAnalyzer` provides unified access to Spring analysis
- **Service Layer**: Clean separation between IDE-specific and pure data models

<!-- Plugin description -->
Call Graph for Java is a call graph analysis tool for IntelliJ IDEA that generates interactive visualizations of method call hierarchies in Java projects. It understands Spring endpoints and dependency injection, links MyBatis mapper methods to SQL statements, and supports JSON export with embedded code. The tool window includes dedicated tabs for call graphs, Spring APIs, and MyBatis mappings, plus search and JSONL export for Spring endpoints.
<!-- Plugin description end -->

## Usage

### Basic Call Graph Generation

1. **Generate Call Graph**:
   - Right-click on any method in your Java code
   - Select "Generate Call Graph" from the context menu
   - Or use the keyboard shortcut `Ctrl+Alt+G` (Windows/Linux) / `Cmd+Alt+G` (Mac)

2. **View Results**:
   - The call graph will appear in the "Call Graph" tool window
   - Navigate through the tree structure to explore method dependencies
   - Double-click on any node to jump to the corresponding code
   - Right-click nodes to copy signatures or view method details

3. **Export Data**:
   - Use "View JSON" in the tool window to preview the call graph as JSON
   - Exported JSON includes the node's self code for offline analysis

### Spring API Browser

1. **Browse APIs**:
   - Open the "Spring APIs" tab in the tool window
   - Click "Refresh" to scan all Spring endpoints in your project
   - Use the search field to filter by class, method, or signature text

2. **Export APIs**:
   - Click "Export" to export filtered endpoints as JSONL
   - Each line contains one endpoint's call graph

### MyBatis Mappings Browser

1. **Browse Mappings**:
   - Open the "MyBatis Mappings" tab in the tool window
   - Click "Refresh" to scan all mapper methods
   - Double-click to navigate to mapper interface or XML SQL

### Configuration

1. **Customize Analysis**:
   - Go to `Settings/Preferences > Tools > Call Graph`
   - Configure project/third-party depth limits
   - Set exclude patterns (regex) for package/class/method/signature and method filters
   - Control interface implementation resolution and traversal breadth
   - Enable full XML scanning for MyBatis (slower but more complete)

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Call Graph for Java"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/rccccat/idea-call-graph/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Requirements

- IntelliJ IDEA 2023.1 or later (build 241+)
- Java projects
- Project indexing must be complete for accurate analysis

## Supported Frameworks

- **Spring Framework**: Detects controllers, services, REST endpoints, and DI patterns
- **MyBatis**: Tracks mapper interfaces, annotation SQL, and XML SQL mappings
- **Standard Java**: All method calls and function invocations

## Development

### Build Commands

```bash
# Build the plugin
./gradlew build

# Run the plugin in a development IDE instance
./gradlew runIde

# Run tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.github.rccccat.ideacallgraph.service.CallGraphServiceJavaTest"

# Build plugin distribution (creates zip in build/distributions)
./gradlew buildPlugin

# Format code (required before commits)
./gradlew ktfmtFormat

# Verify plugin compatibility
./gradlew verifyPlugin

# Verify using a local IDE distribution (optional)
./gradlew verifyPlugin -PlocalIdePath="/Applications/IntelliJ IDEA CE.app"
```

### Tech Stack

- Kotlin 2.2.x
- IntelliJ Platform SDK 2023.1+
- JDK 17 toolchain
- Gradle with Kotlin DSL
- Gson for JSON serialization

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run `./gradlew ktfmtFormat` to format code
5. Run `./gradlew test` to ensure tests pass
6. Submit a pull request

## License

This project is licensed under the MIT License - see the repository for details.
