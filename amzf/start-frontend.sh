#!/bin/bash

echo "═══════════════════════════════════════════════════════════"
echo "AnnuPaper v04 - Frontend Startup Script"
echo "═══════════════════════════════════════════════════════════"

# STEP 1: Kill existing frontend processes on ports 4000, 4001, 4002
echo ""
echo "STEP 1/3: Killing existing processes on ports 4000, 4001, 4002..."
echo "───────────────────────────────────────────────────────────"
KILLED=0
for PORT in 4000 4001 4002; do
    PID=$(lsof -ti:$PORT)
    if [ -n "$PID" ]; then
        echo "Killing process on port $PORT (PID: $PID)..."
        kill -9 $PID 2>/dev/null
        KILLED=1
    fi
done
if [ $KILLED -eq 1 ]; then
    sleep 2
    echo "✓ Frontend processes killed."
else
    echo "✓ No existing frontend processes found."
fi

# STEP 2: Navigate and ensure dependencies
cd "$(dirname "$0")/frontend"
mkdir -p ../logs

echo ""
echo "STEP 2/3: Checking frontend dependencies..."
echo "───────────────────────────────────────────────────────────"
if [ ! -d "node_modules" ]; then
    echo "Installing npm dependencies..."
    npm install 2>&1 | tail -5
    if [ $? -ne 0 ]; then
        echo "✗ npm install failed!"
        exit 1
    fi
    echo "✓ Dependencies installed."
else
    echo "✓ Dependencies already installed (skipping npm install)."
    echo "  Note: Run 'npm install' manually if you updated package.json"
fi

# STEP 3: Start frontend
echo ""
echo "STEP 3/3: Starting frontend dev server..."
echo "───────────────────────────────────────────────────────────"
npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!

# Wait for frontend to start
echo "Waiting for frontend to initialize..."
sleep 5

# Check if frontend is running
if ps -p $FRONTEND_PID > /dev/null; then
    # Determine which port it's running on
    FRONTEND_PORT=""
    for PORT in 4000 4001 4002; do
        if lsof -ti:$PORT > /dev/null 2>&1; then
            FRONTEND_PORT=$PORT
            break
        fi
    done

    echo "✓ Frontend started successfully (PID: $FRONTEND_PID)"
    if [ -n "$FRONTEND_PORT" ]; then
        echo "✓ Frontend URL: http://localhost:$FRONTEND_PORT"
    else
        echo "✓ Frontend URL: Check logs/frontend.log for port"
    fi
    echo "✓ Logs: logs/frontend.log"
else
    echo "✗ Frontend failed to start. Check logs/frontend.log"
    tail -20 ../logs/frontend.log
    exit 1
fi

echo "═══════════════════════════════════════════════════════════"
