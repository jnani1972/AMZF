# AMZF Trading System - Refactored

**Version**: 1.0 (Refactored Architecture)
**Status**: Planning Phase
**Original System**: AnnuPaper v0.4

---

## Overview

This repository contains the refactored version of the AMZF (Annu Multi-Broker Framework) algorithmic trading system. The refactoring transforms the original flat package structure into a clean architecture with clear separation of concerns.

## What's Different?

### Architecture Transformation

**From**: Monolithic package structure with mixed responsibilities
**To**: Clean architecture with layered design

```
domain/          → Pure business entities
infrastructure/  → External integrations (brokers, DB, security)
application/     → Use cases and business logic
presentation/    → HTTP controllers, WebSocket, UI
config/          → Centralized configuration
bootstrap/       → Dependency injection and startup
```

### Key Improvements

1. **Broker Abstraction Split**
   - Separate `DataBroker` and `OrderBroker` interfaces
   - Enable multi-broker combinations (e.g., Zerodha data + FYERS orders)
   - Independent evolution of data feeds and order execution

2. **Clear Layer Boundaries**
   - Domain layer: Zero dependencies, pure business logic
   - Application layer: Use cases orchestrating domain and infrastructure
   - Infrastructure layer: External integrations (DB, APIs, messaging)
   - Presentation layer: User-facing interfaces

3. **Improved Maintainability**
   - 40+ focused packages (avg 4 files each) vs 11 packages (avg 13 files each)
   - Single Responsibility Principle throughout
   - Easier to test, extend, and debug

4. **Configuration Management**
   - Centralized in `config/` package
   - Clear priority: Environment → Database → File → Defaults
   - New variables: `DATA_FEED_MODE`, `EXECUTION_BROKER`

## Documentation

Comprehensive refactoring guides are available:

1. **[REFACTORING_PLAN.md](./REFACTORING_PLAN.md)** - Complete refactoring strategy
   - Target architecture and package structure
   - 7-week execution roadmap with daily tasks
   - Risk mitigation and rollback plans
   - Success criteria and validation steps

2. **[PACKAGE_MIGRATION_GUIDE.md](./PACKAGE_MIGRATION_GUIDE.md)** - Implementation details
   - File-by-file migration matrix (150+ files)
   - Automated shell scripts for bulk operations
   - Manual steps for complex extractions
   - Phase-by-phase validation checklists

3. **[BROKER_ABSTRACTION_GUIDE.md](./BROKER_ABSTRACTION_GUIDE.md)** - Broker split deep dive
   - Complete interface definitions with JavaDoc
   - Full adapter implementations (900+ lines)
   - Factory pattern examples
   - Testing strategies

4. **[ZERODHA_DATA_INTEGRATION_GUIDE.md](./ZERODHA_DATA_INTEGRATION_GUIDE.md)** - Zerodha integration
   - Kite Connect setup and authentication
   - WebSocket tick streaming
   - Historical data fetching

## Technology Stack

- **Language**: Java 17
- **Web Server**: Undertow (NO Spring/Spring Boot)
- **Database**: PostgreSQL with HikariCP
- **Build Tool**: Maven
- **Architecture**: Pure Java with manual dependency injection
- **Frontend**: Vue.js (separate build)

## Project Status

### Current Phase: Planning & Design

- ✅ Architecture design complete
- ✅ Refactoring plan documented
- ✅ Migration guides created
- 🔄 Awaiting team review and approval
- ⏳ Implementation not yet started

### Refactoring Timeline (Planned)

- **Week 1**: Domain layer reorganization
- **Week 2**: Broker abstraction split
- **Week 3**: Application services migration
- **Week 4**: Presentation layer extraction
- **Week 5**: Configuration centralization
- **Week 6**: Bootstrap and dependency injection
- **Week 7**: Testing and documentation

## Getting Started

### Prerequisites

```bash
# Java 17
java -version

# Maven 3.8+
mvn -version

# PostgreSQL 14+
psql --version
```

### Build (Future)

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package
mvn package

# Run
java -jar target/amzf-refactored-1.0.jar
```

### Configuration (Future)

```bash
# Copy example environment file
cp .env.example .env

# Edit configuration
nano .env

# Set required variables:
# - DB_URL, DB_USER, DB_PASS
# - JWT_SECRET
# - DATA_FEED_MODE=ZERODHA
# - EXECUTION_BROKER=FYERS
```

## Multi-Broker Setup

One of the key features of the refactored system is the ability to use different brokers for data and execution:

```bash
# Example: Use Zerodha for market data, FYERS for order execution
DATA_FEED_MODE=ZERODHA
EXECUTION_BROKER=FYERS
```

**Benefits**:
- Cost optimization (Zerodha data = ₹2,000/month vs FYERS = ₹10,000/month)
- Reliability (use best broker for each purpose)
- Flexibility (switch independently)

## Repository Structure (Planned)

```
/
├── docs/                           # Documentation
│   ├── architecture/               # Architecture diagrams
│   ├── api/                        # API documentation
│   └── guides/                     # User guides
├── src/
│   ├── main/
│   │   ├── java/in/annupaper/
│   │   │   ├── domain/             # Business entities
│   │   │   ├── infrastructure/     # External integrations
│   │   │   ├── application/        # Use cases
│   │   │   ├── presentation/       # Controllers, UI
│   │   │   ├── config/             # Configuration
│   │   │   └── bootstrap/          # Startup
│   │   └── resources/
│   │       ├── static/             # Frontend assets
│   │       └── application.yml     # App config
│   └── test/
│       ├── java/                   # Unit tests
│       └── integration/            # Integration tests
├── migrations/                     # Database migrations
├── scripts/                        # Automation scripts
├── frontend/                       # Vue.js frontend
├── pom.xml                         # Maven configuration
├── REFACTORING_PLAN.md            # Main refactoring guide
├── PACKAGE_MIGRATION_GUIDE.md     # Implementation guide
├── BROKER_ABSTRACTION_GUIDE.md    # Broker split guide
└── README.md                       # This file
```

## Contributing

This is a refactoring project. To contribute:

1. Review the refactoring plan documents
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Follow the migration guides for package structure
4. Ensure all tests pass: `mvn clean test`
5. Submit pull request with detailed description

## Testing Strategy

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test interactions between layers
- **End-to-End Tests**: Test complete user flows
- **Performance Tests**: Ensure no degradation from original system

**Target Metrics**:
- Test coverage: >80%
- Tick-to-signal latency: <500ms
- Order placement latency: <1s
- Startup time: <30s

## License

[Your License Here]

## Contact

- **Project Lead**: [Your Name]
- **Email**: [Your Email]
- **Documentation**: See docs/ folder
- **Issues**: GitHub Issues

---

## Acknowledgments

This refactoring builds upon the original AMZF trading system (AnnuPaper v0.4), transforming it into a more maintainable, scalable, and flexible architecture while preserving all existing functionality.

**Original Features Preserved**:
- Multi-timeframe (MTF) signal generation
- Multi-broker support (FYERS, Zerodha, Dhan)
- Real-time tick-based analysis
- Constitutional position sizing engine
- Exit signal coordination with trailing stops
- Order reconciliation (P0-C)
- Self-healing watchdog
- OAuth token management

**New Capabilities**:
- Mixed broker usage (Zerodha data + FYERS orders)
- Independent broker evolution
- Cleaner architecture for easier maintenance
- Better testability
- Improved modularity

---

**Last Updated**: 2026-01-14
**Refactoring Status**: Planning Phase
**Next Milestone**: Team review and approval
