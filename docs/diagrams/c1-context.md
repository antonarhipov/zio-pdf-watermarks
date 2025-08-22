# C1 Context Diagram - PDF Watermarking Application

**Version**: 1.0  
**Date**: 2025-08-22  
**Author**: Architecture Analysis  

## System Context

This diagram shows the PDF Watermarking Application in relation to its users and external systems.

```mermaid
graph TB
    %% External Actors
    User[👤 End User<br/>Web Browser User]
    SysAdmin[👤 System Administrator<br/>Operations Team]
    
    %% Main System
    PDFApp[📦 PDF Watermarking Application<br/>Web-based PDF watermarking service<br/>Built with Scala 3, ZIO, ZIO HTTP]
    
    %% External Systems & Dependencies
    FileSystem[💾 File System<br/>Local/Network Storage<br/>Temporary file management]
    JVM[🔧 Java Virtual Machine<br/>Runtime Environment<br/>Memory & GC management]
    PDFBox[📚 Apache PDFBox<br/>PDF Processing Library<br/>PDF manipulation & rendering]
    
    %% User interactions
    User -->|1. Upload PDF files<br/>HTTP multipart| PDFApp
    User -->|2. Configure watermarks<br/>JSON/Form data| PDFApp
    User -->|3. Download processed PDFs<br/>HTTP response| PDFApp
    PDFApp -->|4. Watermarked PDF<br/>Binary stream| User
    
    %% Admin interactions
    SysAdmin -->|Monitor health<br/>HTTP GET /health| PDFApp
    SysAdmin -->|View logs<br/>Structured logging| PDFApp
    PDFApp -->|Health status<br/>JSON response| SysAdmin
    PDFApp -->|Application logs<br/>SLF4J/Logback| SysAdmin
    
    %% System dependencies
    PDFApp -->|Read/Write PDFs<br/>File I/O operations| FileSystem
    PDFApp -->|Store temp files<br/>Cleanup management| FileSystem
    FileSystem -->|File access<br/>Storage operations| PDFApp
    
    PDFApp -->|PDF processing<br/>Library calls| PDFBox
    PDFBox -->|Processing results<br/>Manipulated documents| PDFApp
    
    PDFApp -->|Runtime execution<br/>ZIO effects| JVM
    JVM -->|Memory management<br/>Garbage collection| PDFApp
    
    %% Styling
    classDef userClass fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    classDef systemClass fill:#fff3e0,stroke:#ef6c00,stroke-width:3px
    classDef externalClass fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px
    
    class User,SysAdmin userClass
    class PDFApp systemClass
    class FileSystem,JVM,PDFBox externalClass
```

## Legend

| Symbol | Meaning |
|--------|---------|
| 👤 | Person/User |
| 📦 | Software System |
| 💾 | Data Store |
| 🔧 | Runtime/Platform |
| 📚 | External Library |

## System Boundary

**Inside the boundary**: PDF Watermarking Application
- Web interface for file upload and configuration
- PDF processing and watermark application
- Session and temporary file management
- Health monitoring and logging

**Outside the boundary**: External actors and dependencies
- End users accessing via web browsers
- System administrators for monitoring
- File system for storage
- JVM runtime environment
- Apache PDFBox library for PDF operations

## Key Data Flows

1. **PDF Upload**: Users upload PDF files via multipart HTTP requests
2. **Configuration**: Users submit watermark configuration as JSON
3. **Processing**: Application processes PDFs using PDFBox library
4. **Storage**: Temporary files managed on local/network file system
5. **Download**: Processed PDFs delivered as HTTP binary responses
6. **Monitoring**: Health checks and structured logging for operations

## Quality Attributes

- **Usability**: Web-based interface accessible via standard browsers
- **Reliability**: Graceful error handling and recovery
- **Performance**: Efficient PDF processing with memory management
- **Security**: File validation and secure temporary file handling
- **Operability**: Health monitoring and structured logging
- **Maintainability**: Modular architecture with clear boundaries