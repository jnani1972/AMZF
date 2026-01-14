#!/bin/bash

echo "=== FYERS WebSocket Connectivity Test ==="
echo ""

# Test 1: Raw WebSocket upgrade (no auth - just to see if ELB responds)
echo "1) Testing raw WebSocket upgrade to wss://api.fyers.in/socket/v2/data/"
echo "   (This should fail auth but tell us if 503 is gone)"
echo ""

if command -v websocat &> /dev/null; then
    timeout 5 websocat wss://api.fyers.in/socket/v2/data/ 2>&1 | head -10 || echo "Connection failed or timeout"
elif command -v wscat &> /dev/null; then
    timeout 5 wscat -c "wss://api.fyers.in/socket/v2/data/" 2>&1 | head -10 || echo "Connection failed or timeout"
else
    echo "   ⚠️  Install websocat or wscat to test:"
    echo "   brew install websocat"
    echo "   OR"
    echo "   npm install -g wscat"
fi

echo ""
echo "2) Testing FYERS API endpoint (should return 200)"
curl -v -s https://api.fyers.in/api/v3/generate-authcode 2>&1 | grep -E "HTTP|503|200" | head -3

echo ""
echo "3) DNS resolution check"
dig api.fyers.in +short

echo ""
echo "=== Instructions ==="
echo "✅ Try this script on:"
echo "   - Home WiFi"
echo "   - Mobile hotspot"
echo "   - Different location"
echo ""
echo "If you get 503 on ALL networks → FYERS server issue (wait for recovery)"
echo "If one works and one doesn't → Network/ISP/WAF path issue"
echo ""
echo "Check FYERS status:"
echo "https://fyers.in (check for service announcements)"
echo "https://twitter.com/fyers_in"
