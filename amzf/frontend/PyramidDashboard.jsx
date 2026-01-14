import React, { useState, useEffect, useCallback } from 'react';
import { LineChart, Line, AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { 
// v01 MIGRATION (Undertow + React):
// - Old dashboards used in-file mock data only.
// - We keep the old mock objects (commented) for reference and as safe fallback defaults.
// - New: optional Undertow WebSocket subscription for live updates.
//   Expected message shape (example):
//   { type: "CAPITAL_UPDATE", payload: { totalCapital, deployedCapital, reservedCapital, availableCapital } }
//   { type: "TRADE_EVENT", payload: { ... } }

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__) || (import.meta?.env?.VITE_API_BASE_URL) || "http://localhost:8080";
const WS_BASE_URL  = (typeof window !== "undefined" && window.__ANNUPAPER_WS__)  || (import.meta?.env?.VITE_WS_BASE_URL)  || "ws://localhost:8080";

/**
 * Minimal resilient WebSocket hook for Undertow.
 * - Auto-reconnect with backoff
 * - Allows subscribe/unsubscribe message pattern
 */
function useUndertowWs({ path = "/ws", onMessage, onOpen, enabled = true }) {
  const [status, setStatus] = React.useState("DISCONNECTED");
  const wsRef = React.useRef(null);
  const retryRef = React.useRef({ attempts: 0, timer: null });

  React.useEffect(() => {
    if (!enabled) return;

    const url = `${WS_BASE_URL}${path}`;
    let closedByUser = false;

    const connect = () => {
      setStatus("CONNECTING");
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        retryRef.current.attempts = 0;
        setStatus("CONNECTED");
        // v01: If backend expects an explicit subscribe, do it here
        // ws.send(JSON.stringify({ action: "subscribe", topics: ["CAPITAL_UPDATE", "TRADE_EVENT"] }));
        onOpen?.(ws);
      };

      ws.onmessage = (evt) => {
        try {
          const msg = JSON.parse(evt.data);
          onMessage?.(msg);
        } catch (e) {
          // Non-JSON payloads are ignored safely
          // console.warn("WS non-JSON message", evt.data);
        }
      };

      ws.onerror = () => {
        // Undertow will also raise close; we keep this silent
      };

      ws.onclose = () => {
        wsRef.current = null;
        setStatus("DISCONNECTED");
        if (closedByUser) return;

        // backoff: 0.5s, 1s, 2s, 4s, max 8s
        const a = Math.min(4, retryRef.current.attempts);
        const delay = 500 * (2 ** a);
        retryRef.current.attempts += 1;
        retryRef.current.timer = setTimeout(connect, delay);
      };
    };

    connect();

    return () => {
      closedByUser = true;
      if (retryRef.current.timer) clearTimeout(retryRef.current.timer);
      try {
        wsRef.current?.close();
      } catch {}
    };
  }, [path, enabled, onMessage, onOpen]);

  return { status, ws: wsRef.current };
}

  TrendingUp, TrendingDown, Activity, AlertTriangle, CheckCircle, 
  Clock, DollarSign, Target, Layers, BarChart2, Bell, Settings,
  RefreshCw, ChevronRight, ChevronDown, X, Filter, Search,
  ArrowUpRight, ArrowDownRight, Minus, Eye, Play, Pause
} from 'lucide-react';

// ═══════════════════════════════════════════════════════════════════════════
// MOCK DATA - Replace with API calls in production
// ═══════════════════════════════════════════════════════════════════════════

/* v00 MOCK DATA (kept for reference; fallback defaults)
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
  doubleConfluenceSymbols: 2,
  singleConfluenceSymbols: 1,
  noConfluenceSymbols: 0,
  weekWinRate: 78.5,
  monthWinRate: 72.3
};

const mockOpenTrades = [
  { tradeId: 't-001', symbol: 'RELIANCE', tradeNumber: 1, entryPrice: 2450.50, currentPrice: 2512.30, entryQty: 40, entryValue: 98020, unrealizedPnl: 2472, unrealizedPnlPct: 2.52, currentLogReturn: 0.025, holdingDays: 5, entryHtfZone: 2, confluenceStrength: 'VERY_STRONG', status: 'OPEN' },
  { tradeId: 't-002', symbol: 'RELIANCE', tradeNumber: 2, entryPrice: 2420.00, currentPrice: 2512.30, entryQty: 35, entryValue: 84700, unrealizedPnl: 3230, unrealizedPnlPct: 3.81, currentLogReturn: 0.037, holdingDays: 8, entryHtfZone: 1, confluenceStrength: 'STRONG', status: 'OPEN' },
  { tradeId: 't-003', symbol: 'TCS', tradeNumber: 1, entryPrice: 3850.00, currentPrice: 3920.50, entryQty: 25, entryValue: 96250, unrealizedPnl: 1762, unrealizedPnlPct: 1.83, currentLogReturn: 0.018, holdingDays: 3, entryHtfZone: 3, confluenceStrength: 'MODERATE', status: 'OPEN' },
  { tradeId: 't-004', symbol: 'INFY', tradeNumber: 1, entryPrice: 1580.00, currentPrice: 1545.20, entryQty: 60, entryValue: 94800, unrealizedPnl: -2088, unrealizedPnlPct: -2.20, currentLogReturn: -0.022, holdingDays: 2, entryHtfZone: 4, confluenceStrength: 'STRONG', status: 'OPEN' },
  { tradeId: 't-005', symbol: 'HDFC', tradeNumber: 1, entryPrice: 1720.50, currentPrice: 1785.30, entryQty: 55, entryValue: 94627, unrealizedPnl: 3564, unrealizedPnlPct: 3.77, currentLogReturn: 0.037, holdingDays: 6, entryHtfZone: 2, confluenceStrength: 'VERY_STRONG', status: 'OPEN' },
  { tradeId: 't-006', symbol: 'ICICI', tradeNumber: 1, entryPrice: 1050.00, currentPrice: 1082.40, entryQty: 90, entryValue: 94500, unrealizedPnl: 2916, unrealizedPnlPct: 3.09, currentLogReturn: 0.030, holdingDays: 4, entryHtfZone: 2, confluenceStrength: 'STRONG', status: 'OPEN' },
  { tradeId: 't-007', symbol: 'SBIN', tradeNumber: 1, entryPrice: 620.50, currentPrice: 615.80, entryQty: 150, entryValue: 93075, unrealizedPnl: -705, unrealizedPnlPct: -0.76, currentLogReturn: -0.008, holdingDays: 1, entryHtfZone: 3, confluenceStrength: 'MODERATE', status: 'OPEN' },
  { tradeId: 't-008', symbol: 'BAJFINANCE', tradeNumber: 1, entryPrice: 7250.00, currentPrice: 7480.50, entryQty: 13, entryValue: 94250, unrealizedPnl: 2996, unrealizedPnlPct: 3.18, currentLogReturn: 0.031, holdingDays: 7, entryHtfZone: 1, confluenceStrength: 'VERY_STRONG', status: 'OPEN' },
];

const mockMtfStatus = [
  { symbol: 'RELIANCE', currentPrice: 2512.30, htfLow: 2380, htfHigh: 2650, htfInBuyZone: true, htfScore: 0.22, itfLow: 2450, itfHigh: 2580, itfInBuyZone: true, itfScore: 0.28, ltfLow: 2495, ltfHigh: 2535, ltfInBuyZone: true, ltfScore: 0.18, confluenceType: 'TRIPLE', confluenceStrength: 'VERY_STRONG', entryPermitted: true, pyramidLevel: 2 },
  { symbol: 'TCS', currentPrice: 3920.50, htfLow: 3720, htfHigh: 4100, htfInBuyZone: true, htfScore: 0.35, itfLow: 3850, itfHigh: 4020, itfInBuyZone: true, itfScore: 0.32, ltfLow: 3900, ltfHigh: 3950, ltfInBuyZone: true, ltfScore: 0.25, confluenceType: 'TRIPLE', confluenceStrength: 'STRONG', entryPermitted: true, pyramidLevel: 1 },
  { symbol: 'INFY', currentPrice: 1545.20, htfLow: 1480, htfHigh: 1680, htfInBuyZone: true, htfScore: 0.42, itfLow: 1520, itfHigh: 1620, itfInBuyZone: true, itfScore: 0.38, ltfLow: 1540, ltfHigh: 1575, ltfInBuyZone: true, ltfScore: 0.12, confluenceType: 'TRIPLE', confluenceStrength: 'MODERATE', entryPermitted: true, pyramidLevel: 1 },
  { symbol: 'HDFC', currentPrice: 1785.30, htfLow: 1680, htfHigh: 1880, htfInBuyZone: true, htfScore: 0.28, itfLow: 1720, itfHigh: 1820, itfInBuyZone: true, itfScore: 0.35, ltfLow: 1770, ltfHigh: 1800, ltfInBuyZone: true, ltfScore: 0.22, confluenceType: 'TRIPLE', confluenceStrength: 'STRONG', entryPermitted: true, pyramidLevel: 1 },
  { symbol: 'ICICI', currentPrice: 1082.40, htfLow: 1020, htfHigh: 1150, htfInBuyZone: true, htfScore: 0.32, itfLow: 1050, itfHigh: 1120, itfInBuyZone: true, itfScore: 0.38, ltfLow: 1075, ltfHigh: 1095, ltfInBuyZone: true, ltfScore: 0.28, confluenceType: 'TRIPLE', confluenceStrength: 'STRONG', entryPermitted: true, pyramidLevel: 1 },
  { symbol: 'SBIN', currentPrice: 615.80, htfLow: 580, htfHigh: 680, htfInBuyZone: true, htfScore: 0.45, itfLow: 605, itfHigh: 660, itfInBuyZone: true, itfScore: 0.42, ltfLow: 610, ltfHigh: 635, ltfInBuyZone: true, ltfScore: 0.35, confluenceType: 'TRIPLE', confluenceStrength: 'MODERATE', entryPermitted: true, pyramidLevel: 1 },
  { symbol: 'BAJFINANCE', currentPrice: 7480.50, htfLow: 7100, htfHigh: 7800, htfInBuyZone: true, htfScore: 0.25, itfLow: 7300, itfHigh: 7600, itfInBuyZone: true, itfScore: 0.38, ltfLow: 7450, ltfHigh: 7520, ltfInBuyZone: true, ltfScore: 0.22, confluenceType: 'TRIPLE', confluenceStrength: 'STRONG', entryPermitted: true, pyramidLevel: 1 },
  { symbol: 'MARUTI', currentPrice: 10850.00, htfLow: 10200, htfHigh: 11500, htfInBuyZone: true, htfScore: 0.38, itfLow: 10600, itfHigh: 11100, itfInBuyZone: true, itfScore: 0.32, ltfLow: 10800, ltfHigh: 10920, ltfInBuyZone: false, ltfScore: 0.55, confluenceType: 'DOUBLE', confluenceStrength: 'WEAK', entryPermitted: false, pyramidLevel: 0 },
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
  { date: '2024-03-04', equity: 1065000, pnl: 65000 },
  { date: '2024-03-11', equity: 1082000, pnl: 82000 },
  { date: '2024-03-18', equity: 1095500, pnl: 95500 },
];

const mockAnalyticsByConfluence = [
  { strength: 'VERY_STRONG', trades: 45, wins: 42, winRate: 93.3, totalPnl: 125000, avgLogReturn: 0.048 },
  { strength: 'STRONG', trades: 78, wins: 65, winRate: 83.3, totalPnl: 185000, avgLogReturn: 0.038 },
  { strength: 'MODERATE', trades: 52, wins: 38, winRate: 73.1, totalPnl: 72000, avgLogReturn: 0.025 },
  { strength: 'WEAK', trades: 25, wins: 17, winRate: 68.0, totalPnl: 28000, avgLogReturn: 0.018 },
];

const mockAnalyticsByTrigger = [
  { trigger: 'TARGET_PROFIT', count: 76, totalPnl: 285000, avgLogReturn: 0.052, pct: 38 },
  { trigger: 'EFFECTIVE_CEILING', count: 45, totalPnl: 125000, avgLogReturn: 0.035, pct: 22.5 },
  { trigger: 'TRAILING_STOP', count: 38, totalPnl: 98000, avgLogReturn: 0.042, pct: 19 },
  { trigger: 'HTF_CEILING', count: 25, totalPnl: 68000, avgLogReturn: 0.038, pct: 12.5 },
  { trigger: 'MIN_PROFIT_RESISTANCE', count: 16, totalPnl: 24000, avgLogReturn: 0.018, pct: 8 },
];

const mockAlerts = [
  { id: 'a-001', type: 'CONFLUENCE_FORMED', severity: 'INFO', symbol: 'WIPRO', message: 'Triple confluence formed - VERY_STRONG entry opportunity', timestamp: new Date(Date.now() - 5 * 60000), isResolved: false },
  { id: 'a-002', type: 'TARGET_REACHED', severity: 'INFO', symbol: 'RELIANCE', message: 'Trade #1 reached target profit (5.2% log return)', timestamp: new Date(Date.now() - 15 * 60000), isResolved: false },
  { id: 'a-003', type: 'LOG_EXPOSURE_HIGH', severity: 'WARNING', symbol: null, message: 'Portfolio log exposure at 75% of limit', timestamp: new Date(Date.now() - 30 * 60000), isResolved: false },
  { id: 'a-004', type: 'TRAILING_STOP_TRIGGERED', severity: 'INFO', symbol: 'HDFC', message: 'Trailing stop triggered at 3.8% profit', timestamp: new Date(Date.now() - 45 * 60000), isResolved: true },
  { id: 'a-005', type: 'ORDER_FILLED', severity: 'INFO', symbol: 'TCS', message: 'Entry order filled - Trade #1 opened at ₹3,850', timestamp: new Date(Date.now() - 60 * 60000), isResolved: true },
];

const mockSystemHealth = {
  status: 'HEALTHY',
  components: [
    { component: 'database', status: 'HEALTHY', latencyMs: 12 },
    { component: 'broker.zerodha', status: 'HEALTHY', latencyMs: 45 },
    { component: 'data_feed', status: 'HEALTHY', latencyMs: 8 },
    { component: 'system_resources', status: 'HEALTHY', message: 'Memory: 45% used' },
  ]
};

// ═══════════════════════════════════════════════════════════════════════════
// UTILITY COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

const formatCurrency = (value) => {
  if (value >= 10000000) return `₹${(value / 10000000).toFixed(2)} Cr`;
  if (value >= 100000) return `₹${(value / 100000).toFixed(2)} L`;
  return `₹${value.toLocaleString('en-IN')}`;
};

const formatPercent = (value) => `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;

const getStrengthColor = (strength) => {
  switch (strength) {
    case 'VERY_STRONG': return 'text-emerald-400';
    case 'STRONG': return 'text-green-400';
    case 'MODERATE': return 'text-yellow-400';
    case 'WEAK': return 'text-orange-400';
    default: return 'text-gray-400';
  }
};

const getStrengthBg = (strength) => {
  switch (strength) {
    case 'VERY_STRONG': return 'bg-emerald-500/20 border-emerald-500/30';
    case 'STRONG': return 'bg-green-500/20 border-green-500/30';
    case 'MODERATE': return 'bg-yellow-500/20 border-yellow-500/30';
    case 'WEAK': return 'bg-orange-500/20 border-orange-500/30';
    default: return 'bg-gray-500/20 border-gray-500/30';
  }
};

const getSeverityColor = (severity) => {
  switch (severity) {
    case 'CRITICAL': return 'bg-red-500/20 text-red-400 border-red-500/30';
    case 'ERROR': return 'bg-red-500/20 text-red-400 border-red-500/30';
    case 'WARNING': return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30';
    case 'INFO': return 'bg-blue-500/20 text-blue-400 border-blue-500/30';
    default: return 'bg-gray-500/20 text-gray-400 border-gray-500/30';
  }
};

const timeAgo = (date) => {
  const seconds = Math.floor((new Date() - date) / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
};

// ═══════════════════════════════════════════════════════════════════════════
// CARD COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════
*/
// v01 DEFAULTS (safe fallback until backend is connected)
const defaultPortfolio = {
  portfolioName: "Primary Trading Portfolio",
  totalCapital: 1000000,
  deployedCapital: 0,
  reservedCapital: 0,
  availableCapital: 1000000,
  deploymentPct: 0,
  maxLogLoss: -0.05,
  currentLogExposure: 0,
  logUsagePct: 0,
  status: "ACTIVE",
};

const defaultMetrics = {
  openTrades: 0,
  profitableTrades: 0,
  losingTrades: 0,
  totalUnrealizedPnl: 0,
  todayRealizedPnl: 0,
  todayClosedTrades: 0,
  activeSymbols: 0,
  tripleConfluenceSymbols: 0,
  weekWinRate: 0,
};

// v01 COMPAT NAMES (so the existing UI code keeps working without a huge refactor)
const mockPortfolio = defaultPortfolio;
const mockMetrics = defaultMetrics;

const StatCard = ({ title, value, subtitle, icon: Icon, trend, trendValue, color = 'blue' }) => {
  const colorClasses = {
    blue: 'from-blue-500/20 to-blue-600/10 border-blue-500/30',
    green: 'from-emerald-500/20 to-emerald-600/10 border-emerald-500/30',
    red: 'from-red-500/20 to-red-600/10 border-red-500/30',
    yellow: 'from-yellow-500/20 to-yellow-600/10 border-yellow-500/30',
    purple: 'from-purple-500/20 to-purple-600/10 border-purple-500/30',
  };
  
  const iconColors = {
    blue: 'text-blue-400',
    green: 'text-emerald-400',
    red: 'text-red-400',
    yellow: 'text-yellow-400',
    purple: 'text-purple-400',
  };

  return (
    <div className={`bg-gradient-to-br ${colorClasses[color]} rounded-xl p-4 border backdrop-blur-sm`}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-gray-400 text-sm font-medium">{title}</p>
          <p className="text-2xl font-bold text-white mt-1">{value}</p>
          {subtitle && <p className="text-gray-500 text-xs mt-1">{subtitle}</p>}
        </div>
        {Icon && <Icon className={`w-8 h-8 ${iconColors[color]} opacity-80`} />}
      </div>
      {trend !== undefined && (
        <div className="flex items-center mt-3 text-sm">
          {trend === 'up' ? (
            <ArrowUpRight className="w-4 h-4 text-emerald-400 mr-1" />
          ) : trend === 'down' ? (
            <ArrowDownRight className="w-4 h-4 text-red-400 mr-1" />
          ) : (
            <Minus className="w-4 h-4 text-gray-400 mr-1" />
          )}
          <span className={trend === 'up' ? 'text-emerald-400' : trend === 'down' ? 'text-red-400' : 'text-gray-400'}>
            {trendValue}
          </span>
        </div>
      )}
    </div>
  );
};

const ProgressBar = ({ value, max, label, color = 'blue', showPercent = true }) => {
  const percent = (value / max) * 100;
  const colorClasses = {
    blue: 'bg-blue-500',
    green: 'bg-emerald-500',
    red: 'bg-red-500',
    yellow: 'bg-yellow-500',
    purple: 'bg-purple-500',
  };

  return (
    <div className="w-full">
      {label && (
        <div className="flex justify-between text-sm mb-1">
          <span className="text-gray-400">{label}</span>
          {showPercent && <span className="text-gray-300">{percent.toFixed(1)}%</span>}
        </div>
      )}
      <div className="w-full bg-gray-700 rounded-full h-2">
        <div
          className={`${colorClasses[color]} h-2 rounded-full transition-all duration-500`}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════════════════
// SECTION COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

const PortfolioOverview = ({ portfolio, metrics }) => {
  return (
    <div className="space-y-6">
      {/* Capital Overview */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          title="Total Capital"
          value={formatCurrency(portfolio.totalCapital)}
          icon={DollarSign}
          color="blue"
        />
        <StatCard
          title="Deployed"
          value={formatCurrency(portfolio.deployedCapital)}
          subtitle={`${portfolio.deploymentPct}% of capital`}
          icon={Layers}
          color="purple"
        />
        <StatCard
          title="Unrealized P&L"
          value={formatCurrency(metrics.totalUnrealizedPnl)}
          trend={metrics.totalUnrealizedPnl >= 0 ? 'up' : 'down'}
          trendValue={formatPercent((metrics.totalUnrealizedPnl / portfolio.totalCapital) * 100)}
          icon={TrendingUp}
          color={metrics.totalUnrealizedPnl >= 0 ? 'green' : 'red'}
        />
        <StatCard
          title="Today's P&L"
          value={formatCurrency(metrics.todayRealizedPnl)}
          subtitle={`${metrics.todayClosedTrades} trades closed`}
          trend={metrics.todayRealizedPnl >= 0 ? 'up' : 'down'}
          trendValue={formatPercent((metrics.todayRealizedPnl / portfolio.totalCapital) * 100)}
          icon={Activity}
          color={metrics.todayRealizedPnl >= 0 ? 'green' : 'red'}
        />
      </div>

      {/* Log Exposure & Risk */}
      <div className="bg-gray-800/50 rounded-xl p-5 border border-gray-700/50">
        <h3 className="text-lg font-semibold text-white mb-4 flex items-center">
          <AlertTriangle className="w-5 h-5 mr-2 text-yellow-400" />
          Risk Exposure
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <div className="flex justify-between text-sm mb-2">
              <span className="text-gray-400">Portfolio Log Exposure</span>
              <span className={`font-medium ${portfolio.logUsagePct > 80 ? 'text-red-400' : portfolio.logUsagePct > 60 ? 'text-yellow-400' : 'text-emerald-400'}`}>
                {(portfolio.currentLogExposure * 100).toFixed(2)}% / {(portfolio.maxLogLoss * 100).toFixed(2)}%
              </span>
            </div>
            <div className="w-full bg-gray-700 rounded-full h-3">
              <div
                className={`h-3 rounded-full transition-all duration-500 ${
                  portfolio.logUsagePct > 80 ? 'bg-red-500' : portfolio.logUsagePct > 60 ? 'bg-yellow-500' : 'bg-emerald-500'
                }`}
                style={{ width: `${portfolio.logUsagePct}%` }}
              />
            </div>
            <p className="text-xs text-gray-500 mt-2">
              Log headroom: {((portfolio.maxLogLoss - portfolio.currentLogExposure) * 100).toFixed(2)}%
            </p>
          </div>
          <div>
            <div className="flex justify-between text-sm mb-2">
              <span className="text-gray-400">Capital Deployment</span>
              <span className="text-blue-400 font-medium">{portfolio.deploymentPct}%</span>
            </div>
            <div className="w-full bg-gray-700 rounded-full h-3">
              <div
                className="bg-blue-500 h-3 rounded-full transition-all duration-500"
                style={{ width: `${portfolio.deploymentPct}%` }}
              />
            </div>
            <p className="text-xs text-gray-500 mt-2">
              Available: {formatCurrency(portfolio.availableCapital)}
            </p>
          </div>
        </div>
      </div>

      {/* Trade Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-gray-800/50 rounded-xl p-4 border border-gray-700/50">
          <div className="flex items-center justify-between">
            <span className="text-gray-400 text-sm">Open Trades</span>
            <span className="text-2xl font-bold text-white">{metrics.openTrades}</span>
          </div>
          <div className="flex items-center mt-2 text-sm">
            <span className="text-emerald-400">{metrics.profitableTrades} profit</span>
            <span className="text-gray-600 mx-2">|</span>
            <span className="text-red-400">{metrics.losingTrades} loss</span>
          </div>
        </div>
        <div className="bg-gray-800/50 rounded-xl p-4 border border-gray-700/50">
          <div className="flex items-center justify-between">
            <span className="text-gray-400 text-sm">Week Win Rate</span>
            <span className="text-2xl font-bold text-emerald-400">{metrics.weekWinRate}%</span>
          </div>
        </div>
        <div className="bg-gray-800/50 rounded-xl p-4 border border-gray-700/50">
          <div className="flex items-center justify-between">
            <span className="text-gray-400 text-sm">Active Symbols</span>
            <span className="text-2xl font-bold text-white">{metrics.activeSymbols}</span>
          </div>
        </div>
        <div className="bg-gray-800/50 rounded-xl p-4 border border-gray-700/50">
          <div className="flex items-center justify-between">
            <span className="text-gray-400 text-sm">Triple Confluence</span>
            <span className="text-2xl font-bold text-emerald-400">{metrics.tripleConfluenceSymbols}</span>
          </div>
          <p className="text-xs text-gray-500 mt-1">symbols ready for entry</p>
        </div>
      </div>
    </div>
  );
};

const OpenTradesTable = ({ trades }) => {
  const [sortField, setSortField] = useState('unrealizedPnlPct');
  const [sortDir, setSortDir] = useState('desc');
  const [expandedTrade, setExpandedTrade] = useState(null);

  const sortedTrades = [...trades].sort((a, b) => {
    const aVal = a[sortField];
    const bVal = b[sortField];
    return sortDir === 'asc' ? aVal - bVal : bVal - aVal;
  });

  const handleSort = (field) => {
    if (sortField === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDir('desc');
    }
  };

  return (
    <div className="bg-gray-800/50 rounded-xl border border-gray-700/50 overflow-hidden">
      <div className="p-4 border-b border-gray-700/50 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-white flex items-center">
          <Activity className="w-5 h-5 mr-2 text-blue-400" />
          Open Trades ({trades.length})
        </h3>
        <button className="text-gray-400 hover:text-white text-sm flex items-center">
          <Filter className="w-4 h-4 mr-1" /> Filter
        </button>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-900/50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">Symbol</th>
              <th className="px-4 py-3 text-right text-xs font-medium text-gray-400 uppercase tracking-wider cursor-pointer hover:text-white" onClick={() => handleSort('entryPrice')}>Entry</th>
              <th className="px-4 py-3 text-right text-xs font-medium text-gray-400 uppercase tracking-wider cursor-pointer hover:text-white" onClick={() => handleSort('currentPrice')}>Current</th>
              <th className="px-4 py-3 text-right text-xs font-medium text-gray-400 uppercase tracking-wider cursor-pointer hover:text-white" onClick={() => handleSort('unrealizedPnl')}>P&L</th>
              <th className="px-4 py-3 text-right text-xs font-medium text-gray-400 uppercase tracking-wider cursor-pointer hover:text-white" onClick={() => handleSort('currentLogReturn')}>Log %</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Zone</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Strength</th>
              <th className="px-4 py-3 text-right text-xs font-medium text-gray-400 uppercase tracking-wider cursor-pointer hover:text-white" onClick={() => handleSort('holdingDays')}>Days</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-700/50">
            {sortedTrades.map((trade) => (
              <React.Fragment key={trade.tradeId}>
                <tr className="hover:bg-gray-700/30 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center">
                      <button
                        onClick={() => setExpandedTrade(expandedTrade === trade.tradeId ? null : trade.tradeId)}
                        className="mr-2 text-gray-400 hover:text-white"
                      >
                        {expandedTrade === trade.tradeId ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                      </button>
                      <div>
                        <span className="text-white font-medium">{trade.symbol}</span>
                        <span className="text-gray-500 text-xs ml-2">#{trade.tradeNumber}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right text-gray-300">₹{trade.entryPrice.toFixed(2)}</td>
                  <td className="px-4 py-3 text-right text-white font-medium">₹{trade.currentPrice.toFixed(2)}</td>
                  <td className={`px-4 py-3 text-right font-medium ${trade.unrealizedPnl >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                    {trade.unrealizedPnl >= 0 ? '+' : ''}₹{trade.unrealizedPnl.toLocaleString()}
                  </td>
                  <td className={`px-4 py-3 text-right font-medium ${trade.currentLogReturn >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                    {(trade.currentLogReturn * 100).toFixed(2)}%
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-500/20 text-blue-400 border border-blue-500/30">
                      Z{trade.entryHtfZone}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium border ${getStrengthBg(trade.confluenceStrength)} ${getStrengthColor(trade.confluenceStrength)}`}>
                      {trade.confluenceStrength}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-gray-400">{trade.holdingDays}</td>
                  <td className="px-4 py-3 text-center">
                    <button className="text-gray-400 hover:text-white p-1 rounded hover:bg-gray-700">
                      <Eye className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
                {expandedTrade === trade.tradeId && (
                  <tr className="bg-gray-900/50">
                    <td colSpan={9} className="px-4 py-4">
                      <div className="grid grid-cols-4 gap-4 text-sm">
                        <div>
                          <span className="text-gray-500">Entry Value:</span>
                          <span className="text-white ml-2">{formatCurrency(trade.entryValue)}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Quantity:</span>
                          <span className="text-white ml-2">{trade.entryQty}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">P&L %:</span>
                          <span className={`ml-2 ${trade.unrealizedPnlPct >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                            {formatPercent(trade.unrealizedPnlPct)}
                          </span>
                        </div>
                        <div>
                          <span className="text-gray-500">Status:</span>
                          <span className="text-emerald-400 ml-2">{trade.status}</span>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const MtfStatusGrid = ({ mtfData }) => {
  return (
    <div className="bg-gray-800/50 rounded-xl border border-gray-700/50 overflow-hidden">
      <div className="p-4 border-b border-gray-700/50 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-white flex items-center">
          <Layers className="w-5 h-5 mr-2 text-purple-400" />
          MTF Confluence Status
        </h3>
        <div className="flex items-center space-x-2 text-xs">
          <span className="flex items-center"><span className="w-2 h-2 rounded-full bg-emerald-500 mr-1"></span> In Buy Zone</span>
          <span className="flex items-center"><span className="w-2 h-2 rounded-full bg-gray-500 mr-1"></span> Above Zone</span>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-900/50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">Symbol</th>
              <th className="px-4 py-3 text-right text-xs font-medium text-gray-400 uppercase tracking-wider">Price</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">HTF</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">ITF</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">LTF</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Confluence</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Entry</th>
              <th className="px-4 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Pyramid</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-700/50">
            {mtfData.map((item) => (
              <tr key={item.symbol} className="hover:bg-gray-700/30 transition-colors">
                <td className="px-4 py-3">
                  <span className="text-white font-medium">{item.symbol}</span>
                </td>
                <td className="px-4 py-3 text-right text-white font-medium">
                  ₹{item.currentPrice.toLocaleString()}
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-col items-center">
                    <span className={`w-3 h-3 rounded-full ${item.htfInBuyZone ? 'bg-emerald-500' : 'bg-gray-500'}`}></span>
                    <span className="text-xs text-gray-500 mt-1">{(item.htfScore * 100).toFixed(0)}%</span>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-col items-center">
                    <span className={`w-3 h-3 rounded-full ${item.itfInBuyZone ? 'bg-emerald-500' : 'bg-gray-500'}`}></span>
                    <span className="text-xs text-gray-500 mt-1">{(item.itfScore * 100).toFixed(0)}%</span>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-col items-center">
                    <span className={`w-3 h-3 rounded-full ${item.ltfInBuyZone ? 'bg-emerald-500' : 'bg-gray-500'}`}></span>
                    <span className="text-xs text-gray-500 mt-1">{(item.ltfScore * 100).toFixed(0)}%</span>
                  </div>
                </td>
                <td className="px-4 py-3 text-center">
                  <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium border ${getStrengthBg(item.confluenceStrength)} ${getStrengthColor(item.confluenceStrength)}`}>
                    {item.confluenceType}
                  </span>
                </td>
                <td className="px-4 py-3 text-center">
                  {item.entryPermitted ? (
                    <CheckCircle className="w-5 h-5 text-emerald-400 mx-auto" />
                  ) : (
                    <X className="w-5 h-5 text-gray-500 mx-auto" />
                  )}
                </td>
                <td className="px-4 py-3 text-center">
                  <span className="text-white font-medium">{item.pyramidLevel}</span>
                  <span className="text-gray-500">/10</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

const EquityCurveChart = ({ data }) => {
  return (
    <div className="bg-gray-800/50 rounded-xl border border-gray-700/50 p-4">
      <h3 className="text-lg font-semibold text-white mb-4 flex items-center">
        <TrendingUp className="w-5 h-5 mr-2 text-emerald-400" />
        Equity Curve
      </h3>
      <ResponsiveContainer width="100%" height={300}>
        <AreaChart data={data}>
          <defs>
            <linearGradient id="equityGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="#10B981" stopOpacity={0.3}/>
              <stop offset="95%" stopColor="#10B981" stopOpacity={0}/>
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
          <XAxis dataKey="date" stroke="#9CA3AF" fontSize={12} />
          <YAxis stroke="#9CA3AF" fontSize={12} tickFormatter={(v) => `₹${(v/100000).toFixed(0)}L`} />
          <Tooltip
            contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '8px' }}
            labelStyle={{ color: '#9CA3AF' }}
            formatter={(value) => [`₹${value.toLocaleString()}`, 'Equity']}
          />
          <Area type="monotone" dataKey="equity" stroke="#10B981" fill="url(#equityGradient)" strokeWidth={2} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
};

const AnalyticsCharts = ({ confluenceData, triggerData }) => {
  const COLORS = ['#10B981', '#22C55E', '#EAB308', '#F97316'];
  const TRIGGER_COLORS = ['#3B82F6', '#8B5CF6', '#EC4899', '#F59E0B', '#6B7280'];

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      {/* Performance by Confluence */}
      <div className="bg-gray-800/50 rounded-xl border border-gray-700/50 p-4">
        <h3 className="text-lg font-semibold text-white mb-4 flex items-center">
          <BarChart2 className="w-5 h-5 mr-2 text-blue-400" />
          Performance by Confluence
        </h3>
        <ResponsiveContainer width="100%" height={250}>
          <BarChart data={confluenceData} layout="vertical">
            <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
            <XAxis type="number" stroke="#9CA3AF" fontSize={12} />
            <YAxis type="category" dataKey="strength" stroke="#9CA3AF" fontSize={12} width={100} />
            <Tooltip
              contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '8px' }}
              formatter={(value, name) => [name === 'winRate' ? `${value}%` : `₹${value.toLocaleString()}`, name === 'winRate' ? 'Win Rate' : 'Total P&L']}
            />
            <Bar dataKey="winRate" fill="#10B981" name="Win Rate" radius={[0, 4, 4, 0]} />
          </BarChart>
        </ResponsiveContainer>
        <div className="mt-4 grid grid-cols-2 gap-2">
          {confluenceData.map((item, idx) => (
            <div key={item.strength} className="flex items-center justify-between text-sm">
              <span className={getStrengthColor(item.strength)}>{item.strength}</span>
              <span className="text-gray-400">{item.trades} trades, {item.winRate}% win</span>
            </div>
          ))}
        </div>
      </div>

      {/* Exit Trigger Distribution */}
      <div className="bg-gray-800/50 rounded-xl border border-gray-700/50 p-4">
        <h3 className="text-lg font-semibold text-white mb-4 flex items-center">
          <Target className="w-5 h-5 mr-2 text-purple-400" />
          Exit Trigger Distribution
        </h3>
        <ResponsiveContainer width="100%" height={250}>
          <PieChart>
            <Pie
              data={triggerData}
              cx="50%"
              cy="50%"
              innerRadius={60}
              outerRadius={100}
              paddingAngle={2}
              dataKey="count"
              nameKey="trigger"
              label={({ trigger, pct }) => `${pct}%`}
              labelLine={false}
            >
              {triggerData.map((entry, index) => (
                <Cell key={entry.trigger} fill={TRIGGER_COLORS[index % TRIGGER_COLORS.length]} />
              ))}
            </Pie>
            <Tooltip
              contentStyle={{ backgroundColor: '#1F2937', border: '1px solid #374151', borderRadius: '8px' }}
              formatter={(value, name, props) => [`${value} trades (${props.payload.pct}%)`, props.payload.trigger]}
            />
          </PieChart>
        </ResponsiveContainer>
        <div className="mt-4 space-y-1">
          {triggerData.map((item, idx) => (
            <div key={item.trigger} className="flex items-center justify-between text-sm">
              <div className="flex items-center">
                <span className="w-3 h-3 rounded-full mr-2" style={{ backgroundColor: TRIGGER_COLORS[idx] }}></span>
                <span className="text-gray-300">{item.trigger.replace('_', ' ')}</span>
              </div>
              <span className="text-gray-400">{formatCurrency(item.totalPnl)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

const AlertsPanel = ({ alerts, onDismiss }) => {
  return (
    <div className="bg-gray-800/50 rounded-xl border border-gray-700/50 overflow-hidden">
      <div className="p-4 border-b border-gray-700/50 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-white flex items-center">
          <Bell className="w-5 h-5 mr-2 text-yellow-400" />
          Recent Alerts
        </h3>
        <span className="text-xs text-gray-400">{alerts.filter(a => !a.isResolved).length} unresolved</span>
      </div>
      <div className="max-h-96 overflow-y-auto">
        {alerts.map((alert) => (
          <div
            key={alert.id}
            className={`p-4 border-b border-gray-700/30 ${alert.isResolved ? 'opacity-50' : ''} hover:bg-gray-700/20 transition-colors`}
          >
            <div className="flex items-start justify-between">
              <div className="flex items-start space-x-3">
                <span className={`inline-flex items-center px-2 py-1 rounded text-xs font-medium border ${getSeverityColor(alert.severity)}`}>
                  {alert.severity}
                </span>
                <div>
                  <p className="text-white text-sm">{alert.message}</p>
                  <div className="flex items-center mt-1 text-xs text-gray-500">
                    {alert.symbol && <span className="mr-2">{alert.symbol}</span>}
                    <Clock className="w-3 h-3 mr-1" />
                    <span>{timeAgo(alert.timestamp)}</span>
                  </div>
                </div>
              </div>
              {!alert.isResolved && (
                <button
                  onClick={() => onDismiss(alert.id)}
                  className="text-gray-400 hover:text-white p-1"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

const SystemHealthPanel = ({ health }) => {
  const getStatusColor = (status) => {
    switch (status) {
      case 'HEALTHY': return 'bg-emerald-500';
      case 'DEGRADED': return 'bg-yellow-500';
      case 'UNHEALTHY': return 'bg-red-500';
      default: return 'bg-gray-500';
    }
  };

  return (
    <div className="bg-gray-800/50 rounded-xl border border-gray-700/50 p-4">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-white flex items-center">
          <Activity className="w-5 h-5 mr-2 text-emerald-400" />
          System Health
        </h3>
        <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${
          health.status === 'HEALTHY' ? 'bg-emerald-500/20 text-emerald-400' :
          health.status === 'DEGRADED' ? 'bg-yellow-500/20 text-yellow-400' :
          'bg-red-500/20 text-red-400'
        }`}>
          {health.status}
        </span>
      </div>
      <div className="space-y-3">
        {health.components.map((comp) => (
          <div key={comp.component} className="flex items-center justify-between">
            <div className="flex items-center">
              <span className={`w-2 h-2 rounded-full ${getStatusColor(comp.status)} mr-2`}></span>
              <span className="text-gray-300 text-sm capitalize">{comp.component.replace('.', ' ')}</span>
            </div>
            <span className="text-gray-500 text-xs">
              {comp.latencyMs !== undefined ? `${comp.latencyMs}ms` : comp.message}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════════════════
// MAIN APP COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

const PyramidDashboard = () => {
  const [activeTab, setActiveTab] = useState('overview');
  const [portfolio, setPortfolio] = useState(mockPortfolio);
  const [metrics, setMetrics] = useState(mockMetrics);
  const [openTrades, setOpenTrades] = useState(mockOpenTrades);
  const [mtfStatus, setMtfStatus] = useState(mockMtfStatus);
  const [equityCurve, setEquityCurve] = useState(mockEquityCurve);
  const [alerts, setAlerts] = useState(mockAlerts);
  const [systemHealth, setSystemHealth] = useState(mockSystemHealth);
  const [isLive, setIsLive] = useState(true);
  const [lastUpdate, setLastUpdate] = useState(new Date());

  // Simulate real-time updates
  useEffect(() => {
    if (!isLive) return;

    const interval = setInterval(() => {
      // Simulate price updates
      setOpenTrades(prev => prev.map(trade => {
        const change = (Math.random() - 0.5) * 0.005;
        const newPrice = trade.currentPrice * (1 + change);
        const pnl = (newPrice - trade.entryPrice) * trade.entryQty;
        const pnlPct = (pnl / trade.entryValue) * 100;
        return {
          ...trade,
          currentPrice: parseFloat(newPrice.toFixed(2)),
          unrealizedPnl: parseFloat(pnl.toFixed(0)),
          unrealizedPnlPct: parseFloat(pnlPct.toFixed(2)),
          currentLogReturn: parseFloat((Math.log(newPrice / trade.entryPrice)).toFixed(4))
        };
      }));
      setLastUpdate(new Date());
    }, 5000);

    return () => clearInterval(interval);
  }, [isLive]);

  const handleDismissAlert = (alertId) => {
    setAlerts(prev => prev.map(a => a.id === alertId ? { ...a, isResolved: true } : a));
  };

  const tabs = [
    { id: 'overview', label: 'Overview', icon: Activity },
    { id: 'trades', label: 'Trades', icon: TrendingUp },
    { id: 'mtf', label: 'MTF Status', icon: Layers },
    { id: 'analytics', label: 'Analytics', icon: BarChart2 },
    { id: 'alerts', label: 'Alerts', icon: Bell, badge: alerts.filter(a => !a.isResolved).length },
  ];

  return (
    <div className="min-h-screen bg-gray-900 text-gray-100">
      {/* Header */}
      <header className="bg-gray-800/80 backdrop-blur-sm border-b border-gray-700/50 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 py-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <div className="flex items-center">
                <div className="w-10 h-10 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
                  <Layers className="w-6 h-6 text-white" />
                </div>
                <div className="ml-3">
                  <h1 className="text-xl font-bold text-white">MTF Pyramid</h1>
                  <p className="text-xs text-gray-400">Trading System</p>
                </div>
              </div>
              <div className="hidden md:flex items-center px-3 py-1 bg-gray-700/50 rounded-lg border border-gray-600/50">
                <span className={`w-2 h-2 rounded-full ${portfolio.status === 'ACTIVE' ? 'bg-emerald-500' : 'bg-yellow-500'} mr-2`}></span>
                <span className="text-sm text-gray-300">{portfolio.portfolioName}</span>
              </div>
            </div>
            <div className="flex items-center space-x-4">
              <div className="hidden md:flex items-center text-xs text-gray-400">
                <Clock className="w-4 h-4 mr-1" />
                Last update: {lastUpdate.toLocaleTimeString()}
              </div>
              <button
                onClick={() => setIsLive(!isLive)}
                className={`flex items-center px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isLive ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-gray-700 text-gray-400 border border-gray-600'
                }`}
              >
                {isLive ? <Play className="w-4 h-4 mr-1" /> : <Pause className="w-4 h-4 mr-1" />}
                {isLive ? 'Live' : 'Paused'}
              </button>
              <button className="p-2 text-gray-400 hover:text-white rounded-lg hover:bg-gray-700">
                <RefreshCw className="w-5 h-5" />
              </button>
              <button className="p-2 text-gray-400 hover:text-white rounded-lg hover:bg-gray-700">
                <Settings className="w-5 h-5" />
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Navigation Tabs */}
      <nav className="bg-gray-800/50 border-b border-gray-700/50">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex space-x-1 overflow-x-auto">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center px-4 py-3 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                    activeTab === tab.id
                      ? 'border-blue-500 text-blue-400'
                      : 'border-transparent text-gray-400 hover:text-white hover:border-gray-500'
                  }`}
                >
                  <Icon className="w-4 h-4 mr-2" />
                  {tab.label}
                  {tab.badge > 0 && (
                    <span className="ml-2 px-1.5 py-0.5 text-xs bg-red-500 text-white rounded-full">
                      {tab.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 py-6">
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <PortfolioOverview portfolio={portfolio} metrics={metrics} />
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2">
                <EquityCurveChart data={equityCurve} />
              </div>
              <div className="space-y-6">
                <SystemHealthPanel health={systemHealth} />
                <AlertsPanel alerts={alerts.slice(0, 5)} onDismiss={handleDismissAlert} />
              </div>
            </div>
          </div>
        )}

        {activeTab === 'trades' && (
          <div className="space-y-6">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <StatCard
                title="Open Trades"
                value={openTrades.length}
                icon={Activity}
                color="blue"
              />
              <StatCard
                title="In Profit"
                value={openTrades.filter(t => t.unrealizedPnl > 0).length}
                icon={TrendingUp}
                color="green"
              />
              <StatCard
                title="Total Unrealized"
                value={formatCurrency(openTrades.reduce((sum, t) => sum + t.unrealizedPnl, 0))}
                icon={DollarSign}
                color={openTrades.reduce((sum, t) => sum + t.unrealizedPnl, 0) >= 0 ? 'green' : 'red'}
              />
              <StatCard
                title="Avg Log Return"
                value={`${(openTrades.reduce((sum, t) => sum + t.currentLogReturn, 0) / openTrades.length * 100).toFixed(2)}%`}
                icon={Target}
                color="purple"
              />
            </div>
            <OpenTradesTable trades={openTrades} />
          </div>
        )}

        {activeTab === 'mtf' && (
          <div className="space-y-6">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <StatCard
                title="Triple Confluence"
                value={mtfStatus.filter(s => s.confluenceType === 'TRIPLE').length}
                subtitle="Entry permitted"
                icon={CheckCircle}
                color="green"
              />
              <StatCard
                title="Double Confluence"
                value={mtfStatus.filter(s => s.confluenceType === 'DOUBLE').length}
                subtitle="Waiting for LTF"
                icon={Clock}
                color="yellow"
              />
              <StatCard
                title="Active Pyramids"
                value={mtfStatus.reduce((sum, s) => sum + s.pyramidLevel, 0)}
                subtitle={`Across ${mtfStatus.filter(s => s.pyramidLevel > 0).length} symbols`}
                icon={Layers}
                color="purple"
              />
              <StatCard
                title="VERY_STRONG"
                value={mtfStatus.filter(s => s.confluenceStrength === 'VERY_STRONG').length}
                subtitle="Best opportunities"
                icon={Target}
                color="blue"
              />
            </div>
            <MtfStatusGrid mtfData={mtfStatus} />
          </div>
        )}

        {activeTab === 'analytics' && (
          <div className="space-y-6">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <StatCard
                title="Total Trades"
                value="200"
                subtitle="Last 30 days"
                icon={Activity}
                color="blue"
              />
              <StatCard
                title="Win Rate"
                value="78.5%"
                trend="up"
                trendValue="+3.2% vs prev month"
                icon={TrendingUp}
                color="green"
              />
              <StatCard
                title="Total P&L"
                value={formatCurrency(410000)}
                subtitle="Last 30 days"
                icon={DollarSign}
                color="green"
              />
              <StatCard
                title="Profit Factor"
                value="2.85"
                icon={Target}
                color="purple"
              />
            </div>
            <EquityCurveChart data={equityCurve} />
            <AnalyticsCharts confluenceData={mockAnalyticsByConfluence} triggerData={mockAnalyticsByTrigger} />
          </div>
        )}

        {activeTab === 'alerts' && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-semibold text-white">All Alerts</h2>
              <div className="flex items-center space-x-2">
                <select className="bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300">
                  <option>All Severities</option>
                  <option>Critical</option>
                  <option>Warning</option>
                  <option>Info</option>
                </select>
                <select className="bg-gray-800 border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300">
                  <option>All Types</option>
                  <option>Trade Alerts</option>
                  <option>Risk Alerts</option>
                  <option>System Alerts</option>
                </select>
              </div>
            </div>
            <AlertsPanel alerts={alerts} onDismiss={handleDismissAlert} />
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="bg-gray-800/50 border-t border-gray-700/50 mt-8">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between text-sm text-gray-500">
            <span>MTF Pyramid Trading System v1.0</span>
            <div className="flex items-center space-x-4">
              <span className="flex items-center">
                <span className="w-2 h-2 rounded-full bg-emerald-500 mr-2"></span>
                Connected to Zerodha
              </span>
              <span>Market: Open</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default PyramidDashboard;