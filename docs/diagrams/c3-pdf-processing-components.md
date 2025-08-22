# C3 Component Diagram - PDF Processing Engine

**Version**: 1.0  
**Date**: 2025-08-22  
**Author**: Architecture Analysis  
**Related**: [C1 Context](./c1-context.md) | [C2 Container](./c2-container.md)

## PDF Processing Engine Components

This diagram shows the internal components within the PDF Processing Engine container and how they collaborate to provide PDF watermarking functionality.

```mermaid
graph TB
    %% External containers
    WebServer[🌐 Web Server<br/>ZIO HTTP]
    FileManager[📁 File Management<br/>ZIO + File I/O]
    ConfigService[⚙️ Configuration Service<br/>ZIO Config]
    
    %% PDF Processing Engine Components
    subgraph "PDF Processing Engine Container"
        %% Domain Layer
        DomainModels[📋 Domain Models<br/>com.pdfwatermarks.domain<br/>- WatermarkConfig<br/>- PdfDocument<br/>- ColorConfig<br/>- PositionConfig]
        
        %% Service Layer
        PDFProcessor[⚙️ PDF Processor<br/>com.pdfwatermarks.pdf.PdfProcessor<br/>- PDF loading & validation<br/>- Document metadata extraction<br/>- Multi-page processing<br/>- Error handling]
        
        WatermarkRenderer[🎨 Watermark Renderer<br/>com.pdfwatermarks.pdf.WatermarkRenderer<br/>- Text rendering<br/>- Position calculation<br/>- Rotation & scaling<br/>- Color application]
        
        PDFManipulator[🔧 PDF Manipulator<br/>com.pdfwatermarks.pdf.PdfManipulator<br/>- Document modification<br/>- Page content streams<br/>- Graphics state management<br/>- Font handling]
        
        %% Service Interfaces
        ProcessingService[🔌 PDF Processing Service<br/>com.pdfwatermarks.services.PdfProcessingService<br/>- loadPdf()<br/>- applyWatermarks()<br/>- validateDocument()]
        
        RenderingService[🔌 Watermark Rendering Service<br/>com.pdfwatermarks.services.WatermarkRenderingService<br/>- renderWatermarks()<br/>- calculatePositions()<br/>- applyColors()]
        
        ValidationService[✅ Validation Service<br/>com.pdfwatermarks.services.ValidationService<br/>- validateConfig()<br/>- validatePdf()<br/>- sanitizeInputs()]
        
        %% Utility Components
        FontScaling[📏 Font Scaling Utils<br/>com.pdfwatermarks.domain.FontScaling<br/>- Dynamic font sizing<br/>- Page dimension analysis<br/>- Document type recommendations<br/>- Scaling calculations]
        
        ColorUtils[🎨 Color Utilities<br/>com.pdfwatermarks.domain.ColorUtils<br/>- Color palette management<br/>- Contrast calculations<br/>- Random color generation<br/>- Accessibility checks]
        
        PositionUtils[📍 Position Utilities<br/>com.pdfwatermarks.domain.PositionUtils<br/>- Coordinate calculations<br/>- Template positioning<br/>- Overlap detection<br/>- Boundary validation]
        
        %% Error Handling
        ErrorPatterns[⚠️ Error Patterns<br/>com.pdfwatermarks.errors.ErrorPatterns<br/>- Safely wrapper<br/>- Error recovery<br/>- Exception mapping<br/>- Retry logic]
        
        %% Monitoring
        PerfMonitoring[📊 Performance Monitoring<br/>com.pdfwatermarks.logging.PerformanceMonitoring<br/>- Processing metrics<br/>- Timing measurements<br/>- Resource usage<br/>- Performance logging]
    end
    
    %% External dependencies
    PDFBox[📚 Apache PDFBox 3.0.3<br/>PDF Processing Library]
    ZIORuntime[🔧 ZIO Runtime<br/>Effect execution]
    
    %% External interactions
    WebServer -->|Processing requests<br/>WatermarkConfig| ProcessingService
    ProcessingService -->|Processing results<br/>File references| WebServer
    
    FileManager <-->|File I/O operations<br/>PDF read/write| PDFProcessor
    ConfigService -->|Configuration data<br/>Validation rules| ValidationService
    
    %% Internal component interactions
    ProcessingService -->|PDF operations| PDFProcessor
    ProcessingService -->|Watermark rendering| RenderingService
    ProcessingService -->|Input validation| ValidationService
    
    PDFProcessor -->|Document manipulation| PDFManipulator
    PDFProcessor -->|Domain objects| DomainModels
    PDFProcessor -->|Error handling| ErrorPatterns
    
    RenderingService -->|Watermark application| WatermarkRenderer
    RenderingService -->|Position calculations| PositionUtils
    RenderingService -->|Color management| ColorUtils
    RenderingService -->|Font sizing| FontScaling
    
    WatermarkRenderer -->|PDF manipulation| PDFManipulator
    WatermarkRenderer -->|Domain objects| DomainModels
    WatermarkRenderer -->|Performance tracking| PerfMonitoring
    
    ValidationService -->|Domain validation| DomainModels
    ValidationService -->|Error patterns| ErrorPatterns
    
    %% Utility interactions
    FontScaling -->|Domain models| DomainModels
    ColorUtils -->|Domain models| DomainModels
    PositionUtils -->|Domain models| DomainModels
    
    %% External dependencies
    PDFProcessor -->|PDF library calls| PDFBox
    PDFManipulator -->|PDF operations| PDFBox
    WatermarkRenderer -->|Rendering operations| PDFBox
    
    ProcessingService -->|ZIO effects| ZIORuntime
    RenderingService -->|ZIO effects| ZIORuntime
    ValidationService -->|ZIO effects| ZIORuntime
    
    %% Performance monitoring
    PDFProcessor -.->|Processing metrics| PerfMonitoring
    WatermarkRenderer -.->|Rendering metrics| PerfMonitoring
    PDFManipulator -.->|Manipulation metrics| PerfMonitoring
    
    %% Error handling flows
    PDFProcessor -.->|Error recovery| ErrorPatterns
    WatermarkRenderer -.->|Error handling| ErrorPatterns
    PDFManipulator -.->|Exception mapping| ErrorPatterns
    
    %% Styling
    classDef externalClass fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    classDef domainClass fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    classDef serviceClass fill:#e8f5e8,stroke:#2e7d32,stroke-width:2px
    classDef utilClass fill:#fce4ec,stroke:#c2185b,stroke-width:2px
    classDef infraClass fill:#e3f2fd,stroke:#1976d2,stroke-width:2px
    
    class WebServer,FileManager,ConfigService,PDFBox,ZIORuntime externalClass
    class DomainModels,FontScaling,ColorUtils,PositionUtils domainClass
    class ProcessingService,RenderingService,ValidationService,PDFProcessor,WatermarkRenderer,PDFManipulator serviceClass
    class ErrorPatterns,PerfMonitoring infraClass
```

## Component Responsibilities

### Service Layer

#### PDF Processing Service
- **Interface**: Primary entry point for PDF processing operations
- **Responsibilities**: Orchestrate PDF loading, validation, and watermark application
- **Dependencies**: PDF Processor, Watermark Rendering Service, Validation Service
- **ZIO Integration**: Effect composition, error handling, resource management

#### Watermark Rendering Service  
- **Interface**: Watermark-specific rendering operations
- **Responsibilities**: Coordinate watermark rendering, position calculation, color application
- **Dependencies**: Watermark Renderer, Position Utils, Color Utils, Font Scaling
- **ZIO Integration**: Concurrent rendering, fiber-based processing

#### Validation Service
- **Interface**: Input validation and sanitization
- **Responsibilities**: Validate PDF files, watermark configurations, business rules
- **Dependencies**: Domain Models, Error Patterns
- **ZIO Integration**: Validation effects, error accumulation

### Core Components

#### PDF Processor
- **Purpose**: Core PDF document handling and processing
- **Key Functions**: 
  - `loadPdf(file: File): IO[DomainError, PdfDocument]`
  - Document metadata extraction and validation
  - Multi-page processing coordination
- **Integration**: Direct PDFBox integration with ZIO error handling

#### Watermark Renderer  
- **Purpose**: Watermark text rendering and placement
- **Key Functions**:
  - `applyWatermarks(file: File, config: WatermarkConfig): IO[DomainError, File]`
  - Text positioning, rotation, color application
  - Per-letter color support, transparency handling
- **Integration**: PDFBox graphics operations wrapped in ZIO effects

#### PDF Manipulator
- **Purpose**: Low-level PDF document manipulation
- **Key Functions**:
  - Content stream management
  - Graphics state operations
  - Font and resource handling
- **Integration**: Direct PDFBox API usage with resource management

### Domain Layer

#### Domain Models
- **Comprehensive Types**: WatermarkConfig, PdfDocument, ColorConfig, PositionConfig
- **Functional Design**: Immutable case classes, enums for configurations
- **Rich Modeling**: Support for fixed/random/template-based configurations
- **JSON Integration**: ZIO JSON codecs for serialization

### Utility Components

#### Font Scaling Utils
- **Dynamic Sizing**: Page dimension-based font scaling
- **Document Type Awareness**: Different scaling for legal, business, academic docs
- **Mathematical Models**: Area-based scaling calculations
- **Recommendations**: Font size suggestions based on context

#### Color Utilities
- **Palette Management**: Predefined and custom color palettes
- **Contrast Analysis**: Accessibility and readability checks
- **Random Generation**: Controlled randomization with seeds
- **Color Space Operations**: RGB/HSV conversions and manipulations

#### Position Utils
- **Template System**: Predefined positioning templates (corners, center, grid)
- **Coordinate Calculations**: Mathematical positioning algorithms
- **Overlap Detection**: Prevention of watermark collisions
- **Boundary Validation**: Ensure positioning within page bounds

### Infrastructure Components

#### Error Patterns
- **Safe Wrappers**: `safely` combinator for exception handling
- **Error Recovery**: Retry logic and fallback strategies
- **Exception Mapping**: Convert Java exceptions to domain errors
- **ZIO Integration**: Typed error handling with ZIO error channel

#### Performance Monitoring
- **Metrics Collection**: Processing times, resource usage, throughput
- **Structured Logging**: Performance events with context
- **Resource Tracking**: Memory usage, file handles, thread pool stats
- **ZIO Integration**: Fiber-safe metrics collection

## Architectural Patterns

### ZIO Effect Composition
- **Service Interfaces**: All services return `ZIO[Environment, DomainError, Result]`
- **Error Handling**: Typed errors with recovery and transformation
- **Resource Management**: Automatic cleanup with ZIO resource handling
- **Concurrency**: Fiber-based concurrent processing for multiple watermarks

### Functional Design
- **Immutable Domain**: All domain objects are immutable case classes
- **Pure Functions**: Business logic functions are side-effect free
- **Effect Isolation**: Side effects isolated in ZIO effects
- **Compositional**: Components compose naturally through ZIO environment

### Dependency Injection
- **ZLayer Integration**: All services provided through ZIO layers
- **Environment Composition**: Services composed in application layers
- **Test Isolation**: Easy mocking through layer substitution
- **Compile-time Safety**: Missing dependencies caught at compilation

## Key Interactions

1. **PDF Loading**: Web Server → Processing Service → PDF Processor → PDFBox
2. **Watermark Rendering**: Processing Service → Rendering Service → Watermark Renderer → PDF Manipulator → PDFBox  
3. **Configuration Validation**: Web Server → Processing Service → Validation Service → Domain Models
4. **Error Recovery**: Any Component → Error Patterns → Typed Domain Errors
5. **Performance Tracking**: All Components → Performance Monitoring → Structured Logs

## Quality Attributes

- **Reliability**: Comprehensive error handling, typed errors, resource cleanup
- **Performance**: Streaming processing, efficient memory usage, fiber concurrency  
- **Maintainability**: Clear separation of concerns, functional composition
- **Testability**: Pure functions, dependency injection, effect isolation
- **Extensibility**: Plugin architecture for new watermark types, configuration options