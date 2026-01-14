import React, { useState, useEffect } from 'react';
import { LineChart, Line, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import {
  Activity, TrendingUp, TrendingDown, AlertTriangle, CheckCircle,
  RefreshCw, DollarSign, Target, BarChart2, Clock, AlertCircle
} from 'lucide-react';

const REFRESH_INTERVAL = 30000; // 30 seconds
const COLORS = ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#00f2fe', '#43e97b'];

export default function MonitoringDashboard() {
  const [systemHealth, setSystemHealth] = useState(null);
  const [performance, setPerformance] = useState(null);
  const [brokerStatus, setBrokerStatus] = useState(null);
  const [exitHealth, setExitHealth] = useState(null);
  const [risk, setRisk] = useState(null);
  const [errors, setErrors] = useState(null);
  const [alerts, setAlerts] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchAllData = async () => {
    try {
      const [healthRes, perfRes, brokerRes, exitRes, riskRes, errorsRes, alertsRes] = await Promise.all([
        fetch('/api/monitoring/system-health'),
        fetch('/api/monitoring/performance'),
        fetch('/api/monitoring/broker-status'),
        fetch('/api/monitoring/exit-health'),
        fetch('/api/monitoring/risk'),
        fetch('/api/monitoring/errors'),
        fetch('/api/monitoring/alerts')
      ]);

      setSystemHealth(await healthRes.json());
      setPerformance(await perfRes.json());
      setBrokerStatus(await brokerRes.json());
      setExitHealth(await exitRes.json());
      setRisk(await riskRes.json());
      setErrors(await errorsRes.json());
      setAlerts(await alertsRes.json());
      setLastUpdated(new Date());
      setLoading(false);
    } catch (error) {
      console.error('Failed to fetch monitoring data:', error);
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAllData();
    const interval = setInterval(fetchAllData, REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, []);

  const formatCurrency = (value) => {
    if (value === null || value === undefined) return '₹0';
    return '₹' + Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 });
  };

  const formatTime = (date) => {
    if (!date) return '-';
    return new Date(date).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="flex items-center gap-3">
          <RefreshCw className="w-6 h-6 animate-spin text-purple-600" />
          <span className="text-lg text-gray-700">Loading monitoring data...</span>
        </div>
      </div>
    );
  }

  const activeTrades = systemHealth?.activeTrades || {};
  const today = performance?.today || {};
  const weeklyTrend = performance?.weeklyTrend || [];
  const exitReasons = exitHealth?.exitReasons || [];
  const stuckOrders = exitHealth?.stuckOrders || [];
  const exposure = risk?.exposure || {};
  const concentrations = risk?.topConcentrations || [];
  const recentErrors = errors?.errors || [];
  const activeAlerts = alerts?.alerts || [];

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50 p-6">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div className="bg-white rounded-xl shadow-lg p-6">
          <div className="flex justify-between items-center">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
                <Activity className="w-8 h-8 text-purple-600" />
                Monitoring Dashboard
              </h1>
              <p className="text-gray-600 mt-1">Real-time system health and performance</p>
            </div>
            <div className="text-right">
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <RefreshCw className="w-4 h-4" />
                <span>Last updated: {lastUpdated ? formatTime(lastUpdated) : '-'}</span>
              </div>
              <div className="text-xs text-gray-400 mt-1">Auto-refresh every 30s</div>
            </div>
          </div>
        </div>

        {/* Key Metrics Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <MetricCard
            icon={<Target className="w-6 h-6" />}
            label="Open Trades"
            value={activeTrades.totalOpenTrades || 0}
            subtext={`${activeTrades.longPositions || 0} Long, ${activeTrades.shortPositions || 0} Short`}
            color="blue"
          />
          <MetricCard
            icon={<DollarSign className="w-6 h-6" />}
            label="Today's P&L"
            value={formatCurrency(today.totalPnl)}
            subtext={`${today.tradesClosed || 0} trades closed`}
            color={today.totalPnl >= 0 ? 'green' : 'red'}
          />
          <MetricCard
            icon={<TrendingUp className="w-6 h-6" />}
            label="Win Rate"
            value={`${(today.winRatePercent || 0).toFixed(1)}%`}
            subtext={`${today.winningTrades || 0}W / ${today.losingTrades || 0}L`}
            color="purple"
          />
          <MetricCard
            icon={<BarChart2 className="w-6 h-6" />}
            label="Total Exposure"
            value={formatCurrency(activeTrades.totalExposure)}
            subtext={`Avg hold: ${(activeTrades.avgHoldingHours || 0).toFixed(1)} hours`}
            color="indigo"
          />
        </div>

        {/* Active Alerts */}
        <div className="bg-white rounded-xl shadow-lg p-6">
          <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
            <AlertTriangle className="w-6 h-6 text-orange-500" />
            Active Alerts
          </h2>
          {activeAlerts.length === 0 ? (
            <div className="text-center py-12">
              <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-3" />
              <div className="text-lg font-medium text-gray-700">All systems operational</div>
              <div className="text-sm text-gray-500 mt-1">No critical issues detected</div>
            </div>
          ) : (
            <div className="space-y-3">
              {activeAlerts.map((alert, idx) => (
                <AlertItem key={idx} alert={alert} />
              ))}
            </div>
          )}
        </div>

        {/* Charts Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Weekly P&L Trend */}
          <ChartCard title="Weekly P&L Trend">
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={[...weeklyTrend].reverse()}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" tick={{fontSize: 12}} />
                <YAxis tick={{fontSize: 12}} tickFormatter={(val) => '₹' + val.toLocaleString()} />
                <Tooltip formatter={(val) => formatCurrency(val)} />
                <Legend />
                <Line type="monotone" dataKey="dailyPnl" stroke="#667eea" strokeWidth={2} name="Daily P&L" />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>

          {/* Exit Reasons */}
          <ChartCard title="Exit Reasons Distribution">
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={exitReasons}
                  dataKey="count"
                  nameKey="exitReason"
                  cx="50%"
                  cy="50%"
                  outerRadius={100}
                  label
                >
                  {exitReasons.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>

          {/* Risk Concentration */}
          <ChartCard title="Risk Concentration (Top Symbols)">
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={concentrations}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="symbol" tick={{fontSize: 12}} />
                <YAxis tick={{fontSize: 12}} tickFormatter={(val) => val + '%'} />
                <Tooltip formatter={(val) => val.toFixed(2) + '%'} />
                <Bar dataKey="percentOfTotal" fill="#667eea" name="% of Total Exposure" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>

          {/* Pending Operations */}
          <ChartCard title="Pending Operations">
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={systemHealth?.pendingOperations || []}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="type" tick={{fontSize: 12}} />
                <YAxis tick={{fontSize: 12}} />
                <Tooltip />
                <Bar dataKey="count" fill="#10b981" name="Pending Count" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>

        {/* Data Tables */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Broker Status */}
          <TableCard title="Broker Connection Status">
            {brokerStatus?.brokers?.length === 0 ? (
              <div className="text-center py-8 text-gray-500">No brokers configured</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b-2 border-gray-200">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">Broker</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">User</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">Status</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">Session</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {brokerStatus?.brokers?.map((broker, idx) => (
                      <tr key={idx} className="hover:bg-gray-50">
                        <td className="px-4 py-3 text-sm font-medium text-gray-900">{broker.brokerName}</td>
                        <td className="px-4 py-3 text-sm text-gray-700">{broker.username}</td>
                        <td className="px-4 py-3 text-sm text-gray-700">{broker.connectionStatus}</td>
                        <td className="px-4 py-3">
                          <SessionBadge health={broker.sessionHealth} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </TableCard>

          {/* Recent Errors */}
          <TableCard title="Recent Errors (Last 24h)">
            {recentErrors.length === 0 ? (
              <div className="text-center py-8">
                <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-2" />
                <div className="text-gray-700 font-medium">No errors in last 24 hours</div>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b-2 border-gray-200">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">Time</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">Source</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">Symbol</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold text-gray-600 uppercase">Error</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {recentErrors.slice(0, 10).map((error, idx) => (
                      <tr key={idx} className="hover:bg-gray-50">
                        <td className="px-4 py-3 text-sm text-gray-700">{formatTime(error.createdAt)}</td>
                        <td className="px-4 py-3 text-sm text-gray-700">{error.source}</td>
                        <td className="px-4 py-3 text-sm text-gray-700">{error.symbol || '-'}</td>
                        <td className="px-4 py-3 text-sm text-gray-700 truncate max-w-xs">
                          {error.errorMessage || error.errorCode || '-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </TableCard>
        </div>
      </div>
    </div>
  );
}

// Metric Card Component
function MetricCard({ icon, label, value, subtext, color }) {
  const colorClasses = {
    blue: 'from-blue-500 to-blue-600',
    green: 'from-green-500 to-green-600',
    red: 'from-red-500 to-red-600',
    purple: 'from-purple-500 to-purple-600',
    indigo: 'from-indigo-500 to-indigo-600'
  };

  return (
    <div className="bg-white rounded-xl shadow-lg p-6 hover:shadow-xl transition-shadow">
      <div className={`inline-flex p-3 rounded-lg bg-gradient-to-br ${colorClasses[color]} text-white mb-3`}>
        {icon}
      </div>
      <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">{label}</div>
      <div className={`text-3xl font-bold mb-1 ${color === 'green' ? 'text-green-600' : color === 'red' ? 'text-red-600' : 'text-gray-900'}`}>
        {value}
      </div>
      <div className="text-sm text-gray-600">{subtext}</div>
    </div>
  );
}

// Alert Item Component
function AlertItem({ alert }) {
  const severityConfig = {
    CRITICAL: { bg: 'bg-red-50', border: 'border-red-500', icon: '🚨', textColor: 'text-red-800' },
    HIGH: { bg: 'bg-orange-50', border: 'border-orange-500', icon: '⚠️', textColor: 'text-orange-800' },
    MEDIUM: { bg: 'bg-blue-50', border: 'border-blue-500', icon: '⚡', textColor: 'text-blue-800' }
  };

  const config = severityConfig[alert.severity] || severityConfig.MEDIUM;

  return (
    <div className={`${config.bg} border-l-4 ${config.border} rounded-lg p-4 flex items-start gap-3`}>
      <div className="text-2xl">{config.icon}</div>
      <div className="flex-1">
        <div className={`text-xs font-bold uppercase tracking-wide ${config.textColor}`}>
          {alert.severity}
        </div>
        <div className="text-sm font-medium text-gray-900 mt-1">
          {alert.message}
        </div>
      </div>
    </div>
  );
}

// Chart Card Component
function ChartCard({ title, children }) {
  return (
    <div className="bg-white rounded-xl shadow-lg p-6">
      <h3 className="text-lg font-bold text-gray-900 mb-4">{title}</h3>
      {children}
    </div>
  );
}

// Table Card Component
function TableCard({ title, children }) {
  return (
    <div className="bg-white rounded-xl shadow-lg p-6">
      <h3 className="text-lg font-bold text-gray-900 mb-4">{title}</h3>
      {children}
    </div>
  );
}

// Session Badge Component
function SessionBadge({ health }) {
  const config = {
    VALID: { bg: 'bg-green-100', text: 'text-green-800', label: 'Valid' },
    EXPIRED: { bg: 'bg-red-100', text: 'text-red-800', label: 'Expired' },
    EXPIRING_SOON: { bg: 'bg-yellow-100', text: 'text-yellow-800', label: 'Expiring Soon' }
  };

  const badge = config[health] || config.VALID;

  return (
    <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded ${badge.bg} ${badge.text}`}>
      {badge.label}
    </span>
  );
}
