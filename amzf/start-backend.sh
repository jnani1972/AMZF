#!/bin/bash

echo "═══════════════════════════════════════════════════════════"
echo "AnnuPaper v04 - Backend Startup Script"
echo "═══════════════════════════════════════════════════════════"

# STEP 1: Kill existing backend process on port 9090
echo ""
echo "STEP 1/3: Killing existing processes on port 9090..."
echo "───────────────────────────────────────────────────────────"
PID=$(lsof -ti:9090)
if [ -n "$PID" ]; then
    echo "Killing backend process (PID: $PID)..."
    kill -9 $PID 2>/dev/null
    sleep 2
    echo "✓ Backend process killed."
else
    echo "✓ No existing backend process found."
fi

# STEP 2: Navigate and rebuild
cd "$(dirname "$0")"
mkdir -p logs

echo ""
echo "STEP 2/3: Rebuilding backend application..."
echo "───────────────────────────────────────────────────────────"
mvn clean package -DskipTests 2>&1 | tail -10
if [ $? -ne 0 ]; then
    echo "✗ Build failed! Check output above."
    exit 1
fi
echo "✓ Backend rebuilt successfully."

# STEP 3: Start backend
echo ""
echo "STEP 3/3: Starting backend server..."
echo "───────────────────────────────────────────────────────────"
PORT=9090 DB_USER=jnani DB_PASS="" RELEASE_READINESS=PROD_READY java -jar target/annu-undertow-ws-v04-0.4.0.jar > logs/backend.log 2>&1 &
BACKEND_PID=$!

# Wait for backend to start
echo "Waiting for backend to initialize..."
sleep 5

# Check if backend is running
if ps -p $BACKEND_PID > /dev/null; then
    echo "✓ Backend started successfully (PID: $BACKEND_PID)"
    echo "✓ Backend URL: http://localhost:9090"
    echo "✓ Logs: logs/backend.log"
else
    echo "✗ Backend failed to start. Check logs/backend.log"
    tail -20 logs/backend.log
    exit 1
fi

echo "═══════════════════════════════════════════════════════════"
