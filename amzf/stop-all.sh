#!/bin/bash

echo "═══════════════════════════════════════════════════════════"
echo "AnnuPaper v04 - Stopping All Servers"
echo "═══════════════════════════════════════════════════════════"

# Stop backend (port 9090)
echo "Stopping backend..."
PID=$(lsof -ti:9090)
if [[ -n "$PID" ]]; then
    kill -9 $PID 2>/dev/null
    echo "✓ Backend stopped (PID: $PID)"
else
    echo "  No backend process found"
fi

# Stop frontend (ports 4000, 4001, 4002)
echo "Stopping frontend..."
STOPPED=0
for PORT in 4000 4001 4002; do
    PID=$(lsof -ti:$PORT)
    if [[ -n "$PID" ]]; then
        kill -9 $PID 2>/dev/null
        echo "✓ Frontend stopped on port $PORT (PID: $PID)"
        STOPPED=1
    fi
done

if [[ $STOPPED -eq 0 ]]; then
    echo "  No frontend process found"
fi

echo "═══════════════════════════════════════════════════════════"
echo "✓ All servers stopped"
echo "═══════════════════════════════════════════════════════════"
