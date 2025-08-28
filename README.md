# Call Graph Plugin for IntelliJ IDEA

A powerful IntelliJ IDEA plugin for generating and visualizing call graphs from Java and Kotlin code. This plugin helps developers understand code dependencies, analyze method invocation chains, and navigate complex codebases more efficiently.

## Features

- **Multi-language support**: Works with both Java and Kotlin code
- **Interactive call graph visualization**: Tree-based display of method call hierarchies
- **Spring Framework integration**: Special handling for Spring endpoints and services
- **MyBatis integration**: Track calls from mapper interfaces to XML SQL statements
- **AI-powered analysis**: Leverage LLM to analyze call graphs and provide architectural insights
- **Code navigation**: Double-click to navigate to method definitions or XML files
- **JSON export**: Export call graphs to JSON format for external analysis
- **Configurable analysis**: Customize depth limits, filtering options, and analysis scope
- **Context menus**: Right-click actions for copying signatures and viewing method details
- **Real-time analysis**: Background processing with progress indicators

## Architecture

The plugin is structured into several key components:

### Core Analysis Engine
- **`CallGraphAnalyzer`**: Main analyzer for building call graphs from PSI elements
- **`CallGraphNodeFactory`**: Factory for creating call graph nodes from PSI elements
- **`SpringCallGraphAnalyzer`**: Specialized analyzer for Spring Framework patterns
- **`MyBatisCallGraphAnalyzer`**: Analyzer for MyBatis mapper interfaces and XML mappings

### Data Models
- **`CallGraph`**: Represents the complete call graph structure
- **`CallGraphNode`**: Individual nodes in the call graph
- **`CallGraphEdge`**: Connections between nodes with call type information

### User Interface
- **`CallGraphToolWindowContent`**: Main tool window for displaying call graphs
- **`CallGraphTreeRenderer`**: Custom tree renderer for call graph visualization
- **`JsonPreviewDialog`**: Dialog for viewing exported JSON data
- **`LLMAnalysisDialog`**: Dialog for displaying AI analysis results

### Actions and Settings
- **`GenerateCallGraphAction`**: Action triggered from editor context menu
- **`CallGraphSettings`**: Configuration management for analysis parameters
- **`CallGraphConfigurable`**: Settings UI in IDE preferences
- **`LLMSettings`**: Configuration for AI analysis features
- **`LLMConfigurable`**: Settings UI for LLM integration

### AI Integration
- **`LLMService`**: Service for interacting with Large Language Models
- **`LLMAnalysisDialog`**: Interface for displaying AI-generated insights

<!-- Plugin description -->
A comprehensive call graph analysis tool for IntelliJ IDEA that generates interactive visualizations of method call hierarchies in Java and Kotlin projects. Features include multi-language support, Spring Framework integration, MyBatis mapper tracking, AI-powered code analysis, configurable analysis depth, JSON export capabilities, and intuitive code navigation through double-click interactions.

This plugin helps developers understand complex codebases by visualizing method dependencies and call chains, making it easier to analyze code flow, identify potential issues, and navigate large projects efficiently. With built-in AI analysis, get architectural insights and optimization recommendations automatically.
<!-- Plugin description end -->

## Usage

### Basic Call Graph Generation

1. **Generate Call Graph**: 
   - Right-click on any method or function in your Java/Kotlin code
   - Select "Generate Call Graph" from the context menu
   - Or use the keyboard shortcut `Ctrl+Alt+G`

2. **View Results**: 
   - The call graph will appear in the "Call Graph" tool window
   - Navigate through the tree structure to explore method dependencies
   - Double-click on any node to jump to the corresponding code

3. **Export Data**: 
   - Use the "View JSON" button in the tool window to export the call graph
   - The JSON format allows for integration with external analysis tools

### AI-Powered Analysis

4. **Configure AI Analysis** (First time setup):
   - Go to `Settings/Preferences > Tools > Call Graph LLM`
   - Enter your API endpoint URL and API key
   - Customize the system prompt for analysis focus
   - Enable the feature and test connection

5. **Get AI Insights**:
   - After generating a call graph, click the "AI Analysis" button
   - Wait for the analysis to complete (progress bar will show)
   - Review architectural insights, potential issues, and optimization suggestions

### MyBatis Integration

6. **MyBatis Mapper Tracking**:
   - Call graphs automatically detect MyBatis mapper interfaces
   - Shows both annotation-based (@Select, @Insert, etc.) and XML-based SQL mappings
   - Double-click SQL nodes to navigate directly to XML mapper files

### Configuration

7. **Customize Analysis**:
   - Go to `Settings/Preferences > Tools > Call Graph`
   - Adjust analysis depth, filtering options, and other parameters

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Call Graph Plugin"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/rccccat/idea-call-graph/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Requirements

- IntelliJ IDEA 2024.3 or later
- Java/Kotlin projects
- Project indexing must be complete for accurate analysis
- For AI analysis: API access to OpenAI-compatible LLM service

## Supported Frameworks

- **Spring Framework**: Detects controllers, services, and REST endpoints
- **MyBatis**: Tracks mapper interfaces and XML SQL mappings
- **Standard Java/Kotlin**: All method calls and function invocations

## AI Analysis Features

The plugin includes built-in AI analysis capabilities that can:

- **Architecture Overview**: Identify design patterns and architectural styles
- **Component Analysis**: Understand the role of different system components
- **Issue Detection**: Find potential problems like circular dependencies and tight coupling
- **Optimization Suggestions**: Recommend performance and maintainability improvements
- **Security Analysis**: Identify potential security concerns in call flows

### Supported LLM Providers

- OpenAI (GPT-4, GPT-3.5-turbo)
- Azure OpenAI
- Any OpenAI-compatible API endpoint
- Local LLM services (Ollama, etc.)

## Development

This plugin is built using:
- Kotlin for the implementation language
- IntelliJ Platform SDK 2024.3
- Gradle with Kotlin DSL for build management
- Gson for JSON serialization
- Java HTTP Client for LLM API communication

### Project Structure

```
src/main/kotlin/com/github/rccccat/ideacallgraph/
├── actions/              # IDE actions (Generate Call Graph)
├── analysis/             # Core analysis engine
├── export/               # JSON export functionality
├── llm/                  # AI analysis integration
├── model/                # Data models (CallGraph, CallGraphNode, etc.)
├── settings/             # Plugin configuration
├── toolWindow/           # UI components
└── ui/                   # Additional UI elements
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the repository for details.
