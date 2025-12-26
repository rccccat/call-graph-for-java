# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- **AI-Powered Analysis**: Integration with Large Language Models for intelligent code analysis
  - Configurable LLM settings (API endpoint, model, system prompt)
  - One-click AI analysis button in tool window
  - Comprehensive architectural insights and optimization suggestions
  - Support for OpenAI-compatible APIs and local LLM services
- **MyBatis Integration**: Complete support for MyBatis mapper tracking
  - Detection of MyBatis mapper interfaces with @Mapper and @Repository annotations
  - Support for both annotation-based SQL (@Select, @Insert, @Update, @Delete) and XML-based mappings
  - Navigation from mapper methods to corresponding XML SQL statements
  - Special rendering for SQL statement nodes with type indicators ([SELECT], [INSERT], etc.)
- **Enhanced Navigation**: Improved code navigation capabilities
  - Precise navigation to XML mapper files for MyBatis SQL statements
  - Multiple fallback mechanisms for robust navigation
  - Smart element pointer creation for XML elements
- **UI/UX Improvements**:
  - New LLM configuration panel in IDE settings
  - AI analysis results dialog with syntax highlighting
  - Progress indicators for background AI processing
  - Enhanced tree rendering with framework-specific icons
  - Contextual button states based on configuration

### Changed
- Updated plugin description to reflect new AI and MyBatis capabilities
- Enhanced call graph data model to support SQL statements and LLM metadata
- Improved error handling and user feedback throughout the application
- Refactored settings architecture to support multiple configuration panels

### Technical Improvements
- Added HTTP client for LLM API communication (replacing OpenAI Java SDK for better compatibility)
- Enhanced PSI element analysis for MyBatis framework detection
- Improved ReadAction usage for thread-safe PSI access
- Added comprehensive validation for LLM configuration settings

## [0.0.1] - 2024-08-28
### Added
- Initial release of Call Graph Plugin
- Support for Java and Kotlin method call analysis
- Interactive call graph visualization in tool window
- Spring Framework integration and endpoint detection
- JSON export functionality
- Configurable analysis depth and filtering options
- Context menu actions for code navigation
- Real-time background analysis with progress indicators
- Basic plugin structure and configuration system

### Core Features
- **Multi-language Support**: Java and Kotlin method call tracking
- **Spring Integration**: Detection of @Controller, @Service, @RestController annotations
- **Interactive UI**: Tree-based call graph visualization with double-click navigation
- **Export Capabilities**: JSON export for integration with external tools
- **Customizable Analysis**: Configurable depth limits and package filtering
- **Performance**: Background processing with progress indication

### Initial Architecture
- Core analysis engine with CallGraphBuilder
- PSI element processing and smart pointer management
- Spring-specific pattern recognition
- Tool window integration with IntelliJ Platform
- Settings persistence and configuration UI
