#!/bin/bash

echo "═══════════════════════════════════════════════════════════"
echo "AnnuPaper v04 - Complete System Startup"
echo "═══════════════════════════════════════════════════════════"
echo "This script will:"
echo "  1. Kill existing processes on ports 9090, 4000-4002"
echo "  2. Rebuild backend (Maven)"
echo "  3. Start backend server"
echo "  4. Start frontend dev server"
echo "═══════════════════════════════════════════════════════════"

# Navigate to project directory
cd "$(dirname "$0")"

# Create logs directory
mkdir -p logs

# Start backend
echo ""
echo "PHASE 1/2: Backend Startup"
echo "═══════════════════════════════════════════════════════════"
bash start-backend.sh
if [[ $? -ne 0 ]]; then
    echo ""
    echo "✗ Backend startup failed! Aborting."
    exit 1
fi

# Start frontend
echo ""
echo "PHASE 2/2: Frontend Startup"
echo "═══════════════════════════════════════════════════════════"
bash start-frontend.sh
if [[ $? -ne 0 ]]; then
    echo ""
    echo "✗ Frontend startup failed! Backend is still running."
    exit 1
fi

# Success message
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "✓ AnnuPaper v04 System Started Successfully!"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "Services:"
echo "  Backend:  http://localhost:9090"
echo "  Frontend: http://localhost:4000 (or 4001/4002 if 4000 is busy)"
echo ""
echo "Admin Credentials:"
echo "  Email:    admin@annupaper.com"
echo "  Password: admin123"
echo ""
echo "Logs:"
echo "  Backend:  logs/backend.log"
echo "  Frontend: logs/frontend.log"
echo ""
echo "Commands:"
echo "  Stop all:     ./stop-all.sh"
echo "  Restart all:  ./start-all.sh"
echo "  Backend only: ./start-backend.sh"
echo "  Frontend only: ./start-frontend.sh"
echo "═══════════════════════════════════════════════════════════"
