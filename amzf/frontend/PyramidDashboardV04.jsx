import React, { useState, useEffect, useCallback, useMemo, createContext, useContext } from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import { LineChart, Line, AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import {
  TrendingUp, TrendingDown, Activity, AlertTriangle, CheckCircle,
  Clock, DollarSign, Target, Layers, BarChart2, Bell, Settings,
  RefreshCw, ChevronRight, ChevronDown, X, Filter, Search,
  ArrowUpRight, ArrowDownRight, Minus, Eye, Play, Pause,
  LogIn, LogOut, User, Key, Mail, Shield, Wifi, WifiOff
} from 'lucide-react';
import AdminDashboard from './components/AdminDashboard';
import OAuthCallback from './components/admin/OAuthCallback';
import MarketWatch from './components/MarketWatch';
import MonitoringDashboard from './components/MonitoringDashboard';

// ═══════════════════════════════════════════════════════════════════════════
// v04 CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════

// const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
//   || (import.meta?.env?.VITE_API_BASE_URL)
//   || "http://localhost:8080";

// const WS_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_WS__)
//   || (import.meta?.env?.VITE_WS_BASE_URL)
//   || "ws://localhost:8080";

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
  || (import.meta?.env?.VITE_API_BASE_URL)
  || "http://localhost:9090";

const WS_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_WS__)
  || (import.meta?.env?.VITE_WS_BASE_URL)
  || "ws://localhost:9090";

// ═══════════════════════════════════════════════════════════════════════════
// AUTH CONTEXT (v04: JWT Token Management)
// ═══════════════════════════════════════════════════════════════════════════

const AuthContext = createContext(null);

// Export AuthContext for use in child components (e.g., AdminDashboard)
export { AuthContext };

function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(() => localStorage.getItem('annu_token'));
  const [loading, setLoading] = useState(true);

  // Validate token on mount
  useEffect(() => {
    if (token) {
      fetchBootstrap(token)
        .then(data => {
          if (data.user) {
            setUser(data.user);
          } else {
            logout();
          }
        })
        .catch(() => logout())
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (email, password) => {
    const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    const data = await res.json();
    if (data.success) {
      localStorage.setItem('annu_token', data.token);
      setToken(data.token);
      setUser({ userId: data.userId, displayName: data.displayName, role: data.role, email });
      return { success: true };
    }
    return { success: false, error: data.error || 'Login failed' };
  };

  const register = async (email, password, displayName) => {
    const res = await fetch(`${API_BASE_URL}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, displayName })
    });
    const data = await res.json();
    if (data.success) {
      localStorage.setItem('annu_token', data.token);
      setToken(data.token);
      setUser({ userId: data.userId, displayName, role: 'USER', email });
      return { success: true };
    }
    return { success: false, error: data.error || 'Registration failed' };
  };

  const logout = () => {
    localStorage.removeItem('annu_token');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
}

async function fetchBootstrap(token) {
  const res = await fetch(`${API_BASE_URL}/api/bootstrap`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (!res.ok) throw new Error('Bootstrap failed');
  return res.json();
}

// ═══════════════════════════════════════════════════════════════════════════
// v04 WEBSOCKET HOOK (with JWT auth)
// ═══════════════════════════════════════════════════════════════════════════

// Extract WebSocket event handlers to reduce nesting (SonarQube javascript:S2004)
function createWebSocketHandlers({
  onMessage,
  onOpen,
  setStatus,
  retryRef,
  wsRef,
  closedByUserRef,
  connect
}) {
  return {
    handleOpen: (ws) => {
      retryRef.current.attempts = 0;
      setStatus("CONNECTED");

      // v04: Subscribe to relevant topics
      ws.send(JSON.stringify({
        action: "subscribe",
        topics: [
          "SIGNAL_GENERATED", "SIGNAL_EXPIRED",
          "INTENT_APPROVED", "INTENT_REJECTED",
          "ORDER_CREATED", "ORDER_FILLED", "ORDER_REJECTED",
          "TRADE_CREATED", "TRADE_CLOSED",
          "PORTFOLIO_UPDATED", "CAPITAL_UPDATE",
          "SYSTEM_STATUS", "MARKET_STATUS"
        ]
      }));

      onOpen?.(ws);
    },

    handleMessage: (evt) => {
      try {
        const msg = JSON.parse(evt.data);

        // v04: Handle BATCH messages
        if (msg.type === "BATCH" && msg.payload?.events) {
          msg.payload.events.forEach(event => onMessage?.(event));
        } else if (msg.type === "ACK" || msg.type === "PONG") {
          // Connection acknowledgments
          console.log("[WS] ACK/PONG:", msg);
        } else {
          onMessage?.(msg);
        }
      } catch (e) {
        console.warn("[WS] Non-JSON message:", evt.data);
      }
    },

    handleError: (err) => {
      // Only log if we have a meaningful error message
      // Generic "error" events during normal close are ignored
      if (err.message || err.code) {
        console.error("[WS] Error:", err.message || err.code, err);
      }
    },

    handleClose: (evt) => {
      wsRef.current = null;
      setStatus("DISCONNECTED");

      // Log close event with reason if available
      if (evt.code !== 1000 && evt.code !== 1001 && !closedByUserRef.current) {
        console.warn(`[WS] Closed: code=${evt.code}, reason=${evt.reason || 'none'}`);
      }

      if (closedByUserRef.current) return;

      // Exponential backoff: 0.5s, 1s, 2s, 4s, max 8s
      const attempts = Math.min(4, retryRef.current.attempts);
      const delay = 500 * (2 ** attempts);
      retryRef.current.attempts += 1;

      if (attempts > 0) {
        console.log(`[WS] Reconnecting in ${delay}ms (attempt ${retryRef.current.attempts})...`);
      }

      retryRef.current.timer = setTimeout(connect, delay);
    }
  };
}

function useAnnuWebSocket({ onMessage, onOpen, enabled = true }) {
  const { token } = useAuth();
  const [status, setStatus] = useState("DISCONNECTED");
  const wsRef = React.useRef(null);
  const retryRef = React.useRef({ attempts: 0, timer: null });
  const closedByUserRef = React.useRef(false);

  useEffect(() => {
    if (!enabled || !token) {
      setStatus("DISCONNECTED");
      return;
    }

    closedByUserRef.current = false;

    const connect = () => {
      setStatus("CONNECTING");
      // v04: Token in query param
      const url = `${WS_BASE_URL}/ws?token=${encodeURIComponent(token)}`;
      const ws = new WebSocket(url);
      wsRef.current = ws;

      // Create handlers with reduced nesting
      const handlers = createWebSocketHandlers({
        onMessage,
        onOpen,
        setStatus,
        retryRef,
        wsRef,
        closedByUserRef,
        connect
      });

      // Attach handlers to WebSocket
      ws.onopen = () => handlers.handleOpen(ws);
      ws.onmessage = handlers.handleMessage;
      ws.onerror = handlers.handleError;
      ws.onclose = handlers.handleClose;
    };

    connect();

    return () => {
      closedByUserRef.current = true;
      if (retryRef.current.timer) clearTimeout(retryRef.current.timer);
      try { wsRef.current?.close(); } catch {}
    };
  }, [token, enabled, onMessage, onOpen]);

  const send = useCallback((data) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(typeof data === 'string' ? data : JSON.stringify(data));
    }
  }, []);

  return { status, send, ws: wsRef.current };
}

// ═══════════════════════════════════════════════════════════════════════════
// v04 API HOOKS
// ═══════════════════════════════════════════════════════════════════════════

function useApi() {
  const { token } = useAuth();
  
  const authFetch = useCallback(async (path, options = {}) => {
    const res = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        ...options.headers,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    if (!res.ok) throw new Error(`API error: ${res.status}`);
    return res.json();
  }, [token]);

  return {
    getBootstrap: () => authFetch('/api/bootstrap'),
    getEvents: (afterSeq = 0, limit = 200) => authFetch(`/api/events?afterSeq=${afterSeq}&limit=${limit}`),
    getBrokers: () => authFetch('/api/brokers'),
    getSignals: () => authFetch('/api/signals'),
    getIntents: (signalId) => authFetch(`/api/intents${signalId ? `?signalId=${signalId}` : ''}`),
    getHealth: () => fetch(`${API_BASE_URL}/api/health`).then(r => r.json())
  };
}

// ═══════════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

const formatCurrency = (value) => {
  if (value === null || value === undefined) return '₹0';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(value);
};

const formatNumber = (value, decimals = 2) => {
  if (value === null || value === undefined) return '0';
  return new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals
  }).format(value);
};

const formatPercent = (value) => {
  if (value === null || value === undefined) return '0%';
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
};

const formatTime = (date) => {
  if (!date) return '';
  const d = new Date(date);
  return d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
};

const formatDateTime = (date) => {
  if (!date) return '';
  const d = new Date(date);
  return d.toLocaleString('en-IN', { 
    month: 'short', day: 'numeric', 
    hour: '2-digit', minute: '2-digit' 
  });
};

// ═══════════════════════════════════════════════════════════════════════════
// LOGIN / REGISTER COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

function LoginPage() {
  const { login, register } = useAuth();
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    
    try {
      const result = isLogin 
        ? await login(email, password)
        : await register(email, password, displayName);
      
      if (!result.success) {
        setError(result.error);
      }
    } catch (err) {
      setError('Connection error. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center p-4">
      <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-8 w-full max-w-md shadow-2xl">
        <div className="text-center mb-8">
          <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-purple-600 rounded-xl flex items-center justify-center mx-auto mb-4">
            <Layers className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-white">AnnuPaper v04</h1>
          <p className="text-slate-400 mt-1">Multi-User Trading System</p>
        </div>

        <div className="flex gap-2 mb-6">
          <button
            onClick={() => setIsLogin(true)}
            className={`flex-1 py-2 rounded-lg font-medium transition-all ${
              isLogin 
                ? 'bg-blue-600 text-white' 
                : 'bg-slate-700/50 text-slate-400 hover:bg-slate-700'
            }`}
          >
            Login
          </button>
          <button
            onClick={() => setIsLogin(false)}
            className={`flex-1 py-2 rounded-lg font-medium transition-all ${
              !isLogin 
                ? 'bg-blue-600 text-white' 
                : 'bg-slate-700/50 text-slate-400 hover:bg-slate-700'
            }`}
          >
            Register
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {!isLogin && (
            <div>
              <label className="block text-sm text-slate-400 mb-1">Display Name</label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full bg-slate-700/50 border border-slate-600 rounded-lg pl-10 pr-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
                  placeholder="Your name"
                  autoComplete="name"
                  required={!isLogin}
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-sm text-slate-400 mb-1">Email</label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full bg-slate-700/50 border border-slate-600 rounded-lg pl-10 pr-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
                placeholder="you@example.com"
                autoComplete="email"
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-sm text-slate-400 mb-1">Password</label>
            <div className="relative">
              <Key className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full bg-slate-700/50 border border-slate-600 rounded-lg pl-10 pr-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 transition-colors"
                placeholder="••••••••"
                autoComplete={isLogin ? "current-password" : "new-password"}
                required
                minLength={8}
              />
            </div>
          </div>

          {error && (
            <div className="bg-red-500/20 border border-red-500/50 rounded-lg p-3 text-red-400 text-sm flex items-center gap-2">
              <AlertTriangle className="w-4 h-4" />
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium py-3 rounded-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {loading ? (
              <RefreshCw className="w-5 h-5 animate-spin" />
            ) : (
              <>
                <LogIn className="w-5 h-5" />
                {isLogin ? 'Sign In' : 'Create Account'}
              </>
            )}
          </button>
        </form>

        <div className="mt-6 text-center text-sm text-slate-500">
          <Shield className="w-4 h-4 inline mr-1" />
          Secured with JWT authentication
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// MOCK DATA (Fallback when API unavailable)
// ═══════════════════════════════════════════════════════════════════════════

const mockPortfolio = {
  portfolioId: 'p-001',
  portfolioName: 'Primary Trading Portfolio',
  totalCapital: 1000000,
  deployedCapital: 420000,
  availableCapital: 550000,
  reservedCapital: 30000,
  deploymentPct: 42.0,
  maxLogLoss: -0.05,
  currentLogExposure: -0.032,
  logUsagePct: 64.0,
  status: 'ACTIVE'
};

const mockMetrics = {
  openTrades: 12,
  profitableTrades: 9,
  losingTrades: 3,
  totalUnrealizedPnl: 28500,
  todayRealizedPnl: 12400,
  todayClosedTrades: 3,
  activeSymbols: 8,
  tripleConfluenceSymbols: 5,
  weekWinRate: 78.5,
  monthWinRate: 72.3
};

const mockOpenTrades = [
  { tradeId: 't-001', symbol: 'RELIANCE', tradeNumber: 1, entryPrice: 2450.50, currentPrice: 2512.30, entryQty: 40, entryValue: 98020, unrealizedPnl: 2472, unrealizedPnlPct: 2.52, currentLogReturn: 0.025, holdingDays: 5, confluenceStrength: 'VERY_STRONG', status: 'OPEN' },
  { tradeId: 't-002', symbol: 'TCS', tradeNumber: 1, entryPrice: 3850.00, currentPrice: 3920.50, entryQty: 25, entryValue: 96250, unrealizedPnl: 1762, unrealizedPnlPct: 1.83, currentLogReturn: 0.018, holdingDays: 3, confluenceStrength: 'STRONG', status: 'OPEN' },
  { tradeId: 't-003', symbol: 'INFY', tradeNumber: 1, entryPrice: 1580.00, currentPrice: 1545.20, entryQty: 60, entryValue: 94800, unrealizedPnl: -2088, unrealizedPnlPct: -2.20, currentLogReturn: -0.022, holdingDays: 2, confluenceStrength: 'MODERATE', status: 'OPEN' },
  { tradeId: 't-004', symbol: 'HDFC', tradeNumber: 1, entryPrice: 1720.50, currentPrice: 1785.30, entryQty: 55, entryValue: 94627, unrealizedPnl: 3564, unrealizedPnlPct: 3.77, currentLogReturn: 0.037, holdingDays: 6, confluenceStrength: 'VERY_STRONG', status: 'OPEN' },
  { tradeId: 't-005', symbol: 'SBIN', tradeNumber: 1, entryPrice: 620.50, currentPrice: 615.80, entryQty: 150, entryValue: 93075, unrealizedPnl: -705, unrealizedPnlPct: -0.76, currentLogReturn: -0.008, holdingDays: 1, confluenceStrength: 'MODERATE', status: 'OPEN' },
];

const mockSignals = [
  { signalId: 's-001', symbol: 'WIPRO', direction: 'BUY', signalType: 'ENTRY', confluenceType: 'TRIPLE', confluenceStrength: 'VERY_STRONG', pWin: 0.72, kelly: 0.15, refPrice: 485.50, generatedAt: new Date(Date.now() - 5 * 60000), status: 'ACTIVE' },
  { signalId: 's-002', symbol: 'MARUTI', direction: 'BUY', signalType: 'ENTRY', confluenceType: 'DOUBLE', confluenceStrength: 'STRONG', pWin: 0.58, kelly: 0.08, refPrice: 10850, generatedAt: new Date(Date.now() - 15 * 60000), status: 'ACTIVE' },
];

const mockEquityCurve = [
  { date: '2024-01-01', equity: 1000000, pnl: 0 },
  { date: '2024-01-08', equity: 1012500, pnl: 12500 },
  { date: '2024-01-15', equity: 1028000, pnl: 28000 },
  { date: '2024-01-22', equity: 1018500, pnl: 18500 },
  { date: '2024-01-29', equity: 1035000, pnl: 35000 },
  { date: '2024-02-05', equity: 1048500, pnl: 48500 },
  { date: '2024-02-12', equity: 1042000, pnl: 42000 },
  { date: '2024-02-19', equity: 1058000, pnl: 58000 },
  { date: '2024-02-26', equity: 1072500, pnl: 72500 },
];

// ═══════════════════════════════════════════════════════════════════════════
// STAT CARD COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

function StatCard({ title, value, subtitle, icon: Icon, trend, trendValue, color = 'blue' }) {
  const colorClasses = {
    blue: 'from-blue-500/20 to-blue-600/10 border-blue-500/30',
    green: 'from-green-500/20 to-green-600/10 border-green-500/30',
    red: 'from-red-500/20 to-red-600/10 border-red-500/30',
    purple: 'from-purple-500/20 to-purple-600/10 border-purple-500/30',
    yellow: 'from-yellow-500/20 to-yellow-600/10 border-yellow-500/30',
  };

  return (
    <div className={`bg-gradient-to-br ${colorClasses[color]} border rounded-xl p-4`}>
      <div className="flex items-start justify-between mb-2">
        <span className="text-slate-400 text-sm">{title}</span>
        {Icon && <Icon className="w-5 h-5 text-slate-500" />}
      </div>
      <div className="text-2xl font-bold text-white mb-1">{value}</div>
      {subtitle && <div className="text-sm text-slate-400">{subtitle}</div>}
      {trend !== undefined && (
        <div className={`flex items-center gap-1 mt-2 text-sm ${trend >= 0 ? 'text-green-400' : 'text-red-400'}`}>
          {trend >= 0 ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
          {trendValue || formatPercent(trend)}
        </div>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// EVENT LOG COMPONENT (v04: Handles new event types)
// ═══════════════════════════════════════════════════════════════════════════

function EventLog({ events }) {
  const getEventIcon = (type) => {
    switch (type) {
      case 'SIGNAL_GENERATED': return <Target className="w-4 h-4 text-blue-400" />;
      case 'INTENT_APPROVED': return <CheckCircle className="w-4 h-4 text-green-400" />;
      case 'INTENT_REJECTED': return <X className="w-4 h-4 text-red-400" />;
      case 'ORDER_FILLED': return <CheckCircle className="w-4 h-4 text-green-400" />;
      case 'TRADE_CREATED': return <TrendingUp className="w-4 h-4 text-blue-400" />;
      case 'TRADE_CLOSED': return <TrendingDown className="w-4 h-4 text-purple-400" />;
      case 'PORTFOLIO_UPDATED': return <Layers className="w-4 h-4 text-yellow-400" />;
      case 'SYSTEM_STATUS': return <Activity className="w-4 h-4 text-slate-400" />;
      default: return <Bell className="w-4 h-4 text-slate-400" />;
    }
  };

  const getScopeColor = (scope) => {
    switch (scope) {
      case 'GLOBAL': return 'bg-blue-500/20 text-blue-400';
      case 'USER': return 'bg-purple-500/20 text-purple-400';
      case 'USER_BROKER': return 'bg-green-500/20 text-green-400';
      default: return 'bg-slate-500/20 text-slate-400';
    }
  };

  return (
    <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
      <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
        <Activity className="w-5 h-5" />
        Live Events
      </h3>
      <div className="space-y-2 max-h-80 overflow-y-auto">
        {events.length === 0 ? (
          <div className="text-slate-500 text-center py-8">No events yet</div>
        ) : (
          events.slice(0, 20).map((event, idx) => (
            <div key={event.seq || idx} className="flex items-start gap-3 p-2 rounded-lg hover:bg-slate-700/30 transition-colors">
              {getEventIcon(event.type)}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-white text-sm">{event.type}</span>
                  {event.scope && (
                    <span className={`text-xs px-2 py-0.5 rounded ${getScopeColor(event.scope)}`}>
                      {event.scope}
                    </span>
                  )}
                </div>
                <div className="text-xs text-slate-400 truncate">
                  {event.payload?.symbol && <span className="mr-2">{event.payload.symbol}</span>}
                  {event.payload?.message || JSON.stringify(event.payload).slice(0, 50)}
                </div>
              </div>
              <span className="text-xs text-slate-500">{formatTime(event.ts)}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// SIGNALS PANEL (v04)
// ═══════════════════════════════════════════════════════════════════════════

function SignalsPanel({ signals }) {
  const getStrengthColor = (strength) => {
    switch (strength) {
      case 'VERY_STRONG': return 'text-green-400 bg-green-500/20';
      case 'STRONG': return 'text-blue-400 bg-blue-500/20';
      case 'MODERATE': return 'text-yellow-400 bg-yellow-500/20';
      case 'WEAK': return 'text-red-400 bg-red-500/20';
      default: return 'text-slate-400 bg-slate-500/20';
    }
  };

  return (
    <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
      <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
        <Target className="w-5 h-5" />
        Active Signals
      </h3>
      <div className="space-y-3">
        {signals.length === 0 ? (
          <div className="text-slate-500 text-center py-8">No active signals</div>
        ) : (
          signals.map((signal) => (
            <div key={signal.signalId} className="bg-slate-700/30 rounded-lg p-3">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="font-bold text-white">{signal.symbol}</span>
                  <span className={`text-xs px-2 py-0.5 rounded ${signal.direction === 'BUY' ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}`}>
                    {signal.direction}
                  </span>
                </div>
                <span className={`text-xs px-2 py-0.5 rounded ${getStrengthColor(signal.confluenceStrength)}`}>
                  {signal.confluenceStrength}
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2 text-sm">
                <div>
                  <span className="text-slate-400">P(win)</span>
                  <span className="text-white ml-2">{(signal.pWin * 100).toFixed(1)}%</span>
                </div>
                <div>
                  <span className="text-slate-400">Kelly</span>
                  <span className="text-white ml-2">{(signal.kelly * 100).toFixed(1)}%</span>
                </div>
                <div>
                  <span className="text-slate-400">Ref</span>
                  <span className="text-white ml-2">{formatCurrency(signal.refPrice)}</span>
                </div>
              </div>
              <div className="text-xs text-slate-500 mt-2">
                {formatDateTime(signal.generatedAt)}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// TRADES TABLE
// ═══════════════════════════════════════════════════════════════════════════

function TradesTable({ trades }) {
  return (
    <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl overflow-hidden">
      <div className="p-4 border-b border-slate-700/50">
        <h3 className="text-lg font-semibold text-white flex items-center gap-2">
          <BarChart2 className="w-5 h-5" />
          Open Trades
        </h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="text-sm text-slate-400 border-b border-slate-700/50">
              <th className="text-left p-3">Symbol</th>
              <th className="text-right p-3">Entry</th>
              <th className="text-right p-3">Current</th>
              <th className="text-right p-3">P&L</th>
              <th className="text-right p-3">Log Return</th>
              <th className="text-center p-3">Confluence</th>
              <th className="text-right p-3">Days</th>
            </tr>
          </thead>
          <tbody>
            {trades.map((trade) => (
              <tr key={trade.tradeId} className="border-b border-slate-700/30 hover:bg-slate-700/20 transition-colors">
                <td className="p-3">
                  <span className="font-medium text-white">{trade.symbol}</span>
                  {trade.tradeNumber > 1 && <span className="text-slate-500 ml-1">#{trade.tradeNumber}</span>}
                </td>
                <td className="p-3 text-right text-slate-300">{formatCurrency(trade.entryPrice)}</td>
                <td className="p-3 text-right text-white">{formatCurrency(trade.currentPrice)}</td>
                <td className={`p-3 text-right font-medium ${trade.unrealizedPnl >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                  {formatCurrency(trade.unrealizedPnl)}
                  <span className="text-sm ml-1">({formatPercent(trade.unrealizedPnlPct)})</span>
                </td>
                <td className={`p-3 text-right ${trade.currentLogReturn >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                  {(trade.currentLogReturn * 100).toFixed(2)}%
                </td>
                <td className="p-3 text-center">
                  <span className={`text-xs px-2 py-1 rounded ${
                    trade.confluenceStrength === 'VERY_STRONG' ? 'bg-green-500/20 text-green-400' :
                    trade.confluenceStrength === 'STRONG' ? 'bg-blue-500/20 text-blue-400' :
                    'bg-yellow-500/20 text-yellow-400'
                  }`}>
                    {trade.confluenceStrength}
                  </span>
                </td>
                <td className="p-3 text-right text-slate-400">{trade.holdingDays}d</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// EQUITY CHART
// ═══════════════════════════════════════════════════════════════════════════

function EquityChart({ data }) {
  return (
    <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
      <h3 className="text-lg font-semibold text-white mb-4">Equity Curve</h3>
      <ResponsiveContainer width="100%" height={200}>
        <AreaChart data={data}>
          <defs>
            <linearGradient id="equityGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
              <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
          <XAxis dataKey="date" stroke="#64748b" tick={{ fill: '#64748b', fontSize: 12 }} />
          <YAxis stroke="#64748b" tick={{ fill: '#64748b', fontSize: 12 }} tickFormatter={(v) => `${(v/1000000).toFixed(1)}M`} />
          <Tooltip 
            contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px' }}
            labelStyle={{ color: '#94a3b8' }}
            formatter={(value) => [formatCurrency(value), 'Equity']}
          />
          <Area type="monotone" dataKey="equity" stroke="#3b82f6" fill="url(#equityGradient)" strokeWidth={2} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// HEADER WITH CONNECTION STATUS
// ═══════════════════════════════════════════════════════════════════════════

function Header({ wsStatus, showAdminButton, onShowAdmin, showMarketWatchButton, onShowMarketWatch, onShowDashboard, onShowMonitoring }) {
  const { user, logout } = useAuth();

  return (
    <header className="bg-slate-800/50 border-b border-slate-700/50 px-6 py-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="w-10 h-10 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
            <Layers className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-white">AnnuPaper</h1>
            <p className="text-sm text-slate-400">Pyramid Trading System v04</p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          {/* Connection Status */}
          <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg ${
            wsStatus === 'CONNECTED' ? 'bg-green-500/20 text-green-400' :
            wsStatus === 'CONNECTING' ? 'bg-yellow-500/20 text-yellow-400' :
            'bg-red-500/20 text-red-400'
          }`}>
            {wsStatus === 'CONNECTED' ? <Wifi className="w-4 h-4" /> : <WifiOff className="w-4 h-4" />}
            <span className="text-sm">{wsStatus}</span>
          </div>

          {/* Navigation Buttons */}
          {onShowDashboard && (
            <button
              onClick={onShowDashboard}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-700 hover:bg-slate-600 text-white transition-colors"
            >
              <BarChart2 className="w-4 h-4" />
              <span className="text-sm font-medium">Dashboard</span>
            </button>
          )}

          {/* Market Watch Button (for all authenticated users) */}
          {showMarketWatchButton !== false && onShowMarketWatch && (
            <button
              onClick={onShowMarketWatch}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white transition-colors"
            >
              <Activity className="w-4 h-4" />
              <span className="text-sm font-medium">Market Watch</span>
            </button>
          )}

          {/* Monitoring Dashboard Button (for all authenticated users) */}
          {onShowMonitoring && (
            <button
              onClick={onShowMonitoring}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-green-600 hover:bg-green-700 text-white transition-colors"
            >
              <Activity className="w-4 h-4" />
              <span className="text-sm font-medium">Monitoring</span>
            </button>
          )}

          {/* Admin Panel Button (only for ADMIN users) */}
          {showAdminButton && onShowAdmin && (
            <button
              onClick={onShowAdmin}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-purple-600 hover:bg-purple-700 text-white transition-colors"
            >
              <Shield className="w-4 h-4" />
              <span className="text-sm font-medium">Admin Panel</span>
            </button>
          )}

          {/* User Info */}
          <div className="flex items-center gap-3">
            <div className="text-right">
              <div className="text-sm font-medium text-white">{user?.displayName}</div>
              <div className="text-xs text-slate-400">{user?.role}</div>
            </div>
            <button
              onClick={logout}
              className="p-2 rounded-lg bg-slate-700/50 hover:bg-slate-700 text-slate-400 hover:text-white transition-colors"
              title="Logout"
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// MAIN DASHBOARD
// ═══════════════════════════════════════════════════════════════════════════

function Dashboard({ showAdminButton, onShowAdmin, onShowMarketWatch, onShowMonitoring }) {
  const [portfolio, setPortfolio] = useState(mockPortfolio);
  const [metrics, setMetrics] = useState(mockMetrics);
  const [trades, setTrades] = useState(mockOpenTrades);
  const [signals, setSignals] = useState(mockSignals);
  const [events, setEvents] = useState([]);
  const [equityCurve, setEquityCurve] = useState(mockEquityCurve);

  const api = useApi();

  // Handle incoming WebSocket messages
  const handleMessage = useCallback((event) => {
    setEvents(prev => [event, ...prev].slice(0, 100));

    // Update state based on event type
    switch (event.type) {
      case 'CAPITAL_UPDATE':
      case 'PORTFOLIO_UPDATED':
        if (event.payload) {
          setPortfolio(prev => ({ ...prev, ...event.payload }));
        }
        break;
      case 'SIGNAL_GENERATED':
        if (event.payload) {
          setSignals(prev => [event.payload, ...prev].slice(0, 10));
        }
        break;
      case 'TRADE_CREATED':
        if (event.payload) {
          setTrades(prev => [event.payload, ...prev]);
        }
        break;
      case 'TRADE_CLOSED':
        if (event.payload?.tradeId) {
          setTrades(prev => prev.filter(t => t.tradeId !== event.payload.tradeId));
        }
        break;
    }
  }, []);

  const { status: wsStatus } = useAnnuWebSocket({
    onMessage: handleMessage,
    enabled: true
  });

  // Load initial data
  useEffect(() => {
    api.getBootstrap()
      .then(data => {
        if (data.portfolio) setPortfolio(data.portfolio);
        if (data.metrics) setMetrics(data.metrics);
        if (data.trades) setTrades(data.trades);
      })
      .catch(err => console.warn('Bootstrap failed, using mock data:', err));

    api.getSignals()
      .then(data => {
        if (data.signals) setSignals(data.signals);
      })
      .catch(err => console.warn('Signals failed, using mock data:', err));
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      <Header
        wsStatus={wsStatus}
        showAdminButton={showAdminButton}
        onShowAdmin={onShowAdmin}
        onShowMarketWatch={onShowMarketWatch}
        onShowMonitoring={onShowMonitoring}
      />
      
      <main className="p-6 space-y-6">
        {/* Stats Row */}
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          <StatCard
            title="Total Capital"
            value={formatCurrency(portfolio.totalCapital)}
            subtitle={`${portfolio.deploymentPct?.toFixed(1)}% deployed`}
            icon={DollarSign}
            color="blue"
          />
          <StatCard
            title="Available"
            value={formatCurrency(portfolio.availableCapital)}
            icon={DollarSign}
            color="green"
          />
          <StatCard
            title="Open Trades"
            value={metrics.openTrades}
            subtitle={`${metrics.profitableTrades} profitable`}
            icon={BarChart2}
            color="purple"
          />
          <StatCard
            title="Unrealized P&L"
            value={formatCurrency(metrics.totalUnrealizedPnl)}
            trend={metrics.totalUnrealizedPnl >= 0 ? 2.5 : -2.5}
            icon={TrendingUp}
            color={metrics.totalUnrealizedPnl >= 0 ? 'green' : 'red'}
          />
          <StatCard
            title="Today's P&L"
            value={formatCurrency(metrics.todayRealizedPnl)}
            subtitle={`${metrics.todayClosedTrades} trades closed`}
            icon={Activity}
            color={metrics.todayRealizedPnl >= 0 ? 'green' : 'red'}
          />
          <StatCard
            title="Log Exposure"
            value={`${(portfolio.currentLogExposure * 100).toFixed(1)}%`}
            subtitle={`${portfolio.logUsagePct?.toFixed(0)}% of limit`}
            icon={AlertTriangle}
            color={portfolio.logUsagePct > 80 ? 'red' : portfolio.logUsagePct > 60 ? 'yellow' : 'green'}
          />
        </div>

        {/* Main Content Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left Column */}
          <div className="lg:col-span-2 space-y-6">
            <TradesTable trades={trades} />
            <EquityChart data={equityCurve} />
          </div>

          {/* Right Column */}
          <div className="space-y-6">
            <SignalsPanel signals={signals} />
            <EventLog events={events} />
          </div>
        </div>
      </main>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════════════
// APP ROOT
// ═══════════════════════════════════════════════════════════════════════════

export default function PyramidDashboardV04() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

function AppContent() {
  const { isAuthenticated, loading, user, token, logout } = useAuth();
  const [showAdmin, setShowAdmin] = useState(false);
  const [showMarketWatch, setShowMarketWatch] = useState(false);
  const [showMonitoring, setShowMonitoring] = useState(false);

  // Check for OAuth callback route
  const isOAuthCallback = window.location.pathname === '/admin/oauth-callback';

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <RefreshCw className="w-8 h-8 text-blue-500 animate-spin" />
      </div>
    );
  }

  // OAuth callback doesn't require authentication check (it establishes the connection)
  if (isOAuthCallback) {
    return <OAuthCallback />;
  }

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  // Render Monitoring Dashboard when showMonitoring is true
  if (showMonitoring) {
    return <MonitoringDashboard />;
  }

  // Render Market Watch page when showMarketWatch is true
  if (showMarketWatch) {
    return (
      <div className="min-h-screen bg-slate-900">
        <Header
          wsStatus="connected"
          showAdminButton={user?.role === 'ADMIN'}
          onShowAdmin={() => {
            setShowMarketWatch(false);
            setShowAdmin(true);
          }}
          showMarketWatchButton={true}
          onShowMarketWatch={() => setShowMarketWatch(true)}
          onShowDashboard={() => {
            setShowMarketWatch(false);
            setShowMonitoring(false);
            setShowAdmin(false);
          }}
          onShowMonitoring={() => {
            setShowMarketWatch(false);
            setShowMonitoring(true);
          }}
        />
        <main className="container mx-auto px-6 py-6">
          <MarketWatch />
        </main>
      </div>
    );
  }

  // Render admin dashboard for admin users when showAdmin is true
  if (showAdmin && user?.role === 'ADMIN') {
    return <AdminDashboard user={user} token={token} onLogout={logout} onBack={() => setShowAdmin(false)} />;
  }

  // Default dashboard
  return <Dashboard
    showAdminButton={user?.role === 'ADMIN'}
    onShowAdmin={() => setShowAdmin(true)}
    onShowMarketWatch={() => setShowMarketWatch(true)}
    onShowMonitoring={() => setShowMonitoring(true)}
  />;
}

// ═══════════════════════════════════════════════════════════════════════════
// RENDER TO DOM
// ═══════════════════════════════════════════════════════════════════════════

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <PyramidDashboardV04 />
  </React.StrictMode>
);
