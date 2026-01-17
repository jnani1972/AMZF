# AMZF Project Analysis - Draft
Date: 2026-01-17
Branch: antigravity

## Overview
AMZF (Annu Multi-Broker Framework) is a trading system currently in a refactoring phase (v1.0 Refactored). It is designed to be a modular, clean-architecture based system separating business logic from infrastructure.

## Architecture
The project follows a **Clean Architecture** pattern, enforcing separation of concerns:
- **Domain (`in.annupaper.domain`)**: Pure business entities (e.g., `Trade`, `Order`, `Signal`). No external dependencies.
- **Application (`in.annupaper.application`)**: Use cases and business logic.
- **Infrastructure (`in.annupaper.infrastructure`)**: External integrations (DB, Brokers).
- **Presentation (`in.annupaper.presentation`)**: HTTP controllers (Undertow), WebSockets.
- **Config (`in.annupaper.config`)**: centralized configuration.
- **Bootstrap (`in.annupaper.bootstrap`)**: Dependency injection and application startup (`App.java`).

## Technology Stack

### Backend
- **Language**: Java 17
- **Web Server**: Undertow 2.3.12 (No Spring Framework)
- **Database**: PostgreSQL 14+ (Driver 42.7.3) with HikariCP 5.1.0 for connection pooling.
- **JSON**: Jackson 2.17.2
- **Metrics**: Prometheus SimpleClient
- **Testing**: JUnit 5, Mockito
- **Build Tool**: Maven

### Frontend
- **Framework**: React 18
- **Build Tool**: Vite 5.4.11
- **Language**: TypeScript 5.9.3
- **Styling**: TailwindCSS 3.4.19
- **Routing**: React Router DOM 7.12.0
- **Icons**: Lucide React
- **Charts**: Recharts

### Integration
- **Brokers**:
    - **Zerodha Kite Connect**: Official SDK (optional)
    - **FYERS**: Official SDK (optional, fallback to raw WS)
- **Data Flow**: Supports using different brokers for Data Feed (e.g., Zerodha) and Order Execution (e.g., FYERS).

## Project Structure
- **Root**: `/Users/jnani/Desktop/AMZF`
    - Contains documentation (`docs/`, `*.md`), scripts, and the source directory `amzf/`.
- **Source Root**: `amzf/`
    - `src/main/java`: Backend source code.
    - `frontend/`: Frontend React application.
    - `pom.xml`: Backend dependencies.

## Key Observations
- The project is strict about "No Framework" for the backend (Undertow only).
- It emphasizes manual dependency injection (in `bootstrap` package).
- The frontend is a modern React/Vite/Tailwind stack.
- There are extensive documentation files in the root directory regarding the refactoring plan and migration guides.
