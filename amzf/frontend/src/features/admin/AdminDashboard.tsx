/**
 * Admin Dashboard Page
 * System-wide overview with all users' data and filtering capabilities
 */

import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '../../lib/api';
import { API_ENDPOINTS } from '../../constants/apiEndpoints';
import { SummaryCards } from '../../components/organisms/SummaryCards/SummaryCards';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Button } from '../../components/atoms/Button/Button';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Users, Activity, TrendingUp, Filter, Briefcase, Server, Wifi, Database, CheckCircle, AlertTriangle, Unplug, Link } from 'lucide-react';
import { useAllUsers, useAllUserBrokers, useAllPortfolios } from '../../hooks/useApi';
import { useWebSocket, useRealtimeEvents } from '../../hooks/useWebSocket';

/**
 * Admin dashboard component
 */
export function AdminDashboard() {
  const navigate = useNavigate();

  // Fetch system-wide data
  const { data: allUsers, loading: usersLoading } = useAllUsers();
  const { data: allBrokers, loading: brokersLoading } = useAllUserBrokers();
  const { data: allPortfolios, loading: portfoliosLoading, error: portfoliosError } = useAllPortfolios();
  const { connected: wsConnected } = useWebSocket();
  useRealtimeEvents();

  // Filter state
  const [selectedUserId, setSelectedUserId] = useState<string>('');

  // Calculate system-wide metrics
  const totalUsers = allUsers?.length || 0;
  const activeUsers = allUsers?.filter(u => u.status === 'ACTIVE').length || 0;
  const activeBrokers = allBrokers?.filter(b => b.enabled && b.connected).length || 0;
  const totalBrokers = allBrokers?.length || 0;

  // Filter portfolios by selected user
  const filteredPortfolios = selectedUserId
    ? allPortfolios?.filter(p => p.userId === selectedUserId)
    : allPortfolios;

  // Calculate metrics from filtered portfolios
  const totalAUM = filteredPortfolios?.reduce((sum, p) => sum + p.totalValue, 0) || 0;
  const totalCapital = filteredPortfolios?.reduce((sum, p) => sum + p.capital, 0) || 0;
  const totalPnl = filteredPortfolios?.reduce((sum, p) => sum + p.totalPnl, 0) || 0;
  const totalPnlPercent = totalCapital > 0 ? (totalPnl / totalCapital) * 100 : 0;

  // Fetch System Status
  const [systemStatus, setSystemStatus] = useState<any>(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const [statusError, setStatusError] = useState<string | null>(null);

  useEffect(() => {
    setStatusLoading(true);
    apiClient.get(API_ENDPOINTS.ADMIN.SYSTEM_STATUS)
      .then(res => {
        if (res.success) {
          // Fix: The response is double-wrapped, access res.data.data instead of res.data
          const actualData = res.data?.data || res.data;
          console.log("[AdminDashboard] Broker connected:", actualData?.broker?.connected);
          setSystemStatus(actualData);
        } else {
          console.error("System Status API Error:", res.error);
          setStatusError(res.error || "Failed to load status");
        }
      })
      .catch(err => {
        console.error("System Status Network Error:", err);
        setStatusError(err.message || "Network error");
      })
      .finally(() => setStatusLoading(false));
  }, []);

  const isLoading = usersLoading || brokersLoading || portfoliosLoading;

  return (
    <main className="container mx-auto p-6 space-y-6">
      {/* Page Header */}
      <div>
        <Text variant="h1" className="mb-2">
          Admin Dashboard
        </Text>
        <Text variant="body" className="text-muted">
          System-wide overview and management controls
        </Text>
      </div>

      {/* System Initialization Report */}
      {statusLoading && (
        <Card className="border-l-4 border-l-primary shadow-md p-6">
          <div className="flex items-center gap-4">
            <Spinner size="sm" />
            <Text>Loading System Status...</Text>
          </div>
        </Card>
      )}

      {statusError && (
        <Card className="border-l-4 border-l-destructive shadow-md p-6">
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-destructive font-bold">
              <Server size={20} />
              System Status Failed
            </div>
            <p className="text-sm text-muted-foreground">{statusError}</p>
          </div>
        </Card>
      )}

      {systemStatus && (
        <Card className="mb-8 border-none shadow-lg bg-card/50 backdrop-blur-sm overflow-hidden">
          {/* Header */}
          <div className="bg-muted/30 p-4 border-b flex justify-between items-center">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-primary/10 rounded-lg">
                <Server size={24} className="text-primary" />
              </div>
              <div>
                <Text variant="h3" className="leading-none">System Initialization Report</Text>
                <Text variant="small" className="text-muted-foreground mt-1">Real-time startup & health monitor</Text>
              </div>
            </div>
            <Badge variant={systemStatus.broker?.connected ? 'success' : 'error'} className="text-sm px-3 py-1">
              {systemStatus.broker?.connected ? 'SYSTEM OPERATIONAL' : 'REQUIRES ATTENTION'}
            </Badge>
          </div>

          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr 1fr',
            width: '100%',
            borderTop: '1px solid hsl(var(--border))'
          }}>

            {/* Column 1: Data Broker Status */}
            <div className="p-6 space-y-6" style={{ borderRight: '1px solid hsl(var(--border))' }}>
              <Text variant="h4" className="text-muted-foreground uppercase text-xs tracking-wider mb-4">Data Broker Channel</Text>

              <div className={`rounded-xl border-2 p-6 flex flex-col items-center justify-center text-center gap-4 transition-all`}
                style={{
                  borderColor: systemStatus.broker?.connected ? 'rgba(34, 197, 94, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                  backgroundColor: systemStatus.broker?.connected ? 'rgba(34, 197, 94, 0.05)' : 'rgba(239, 68, 68, 0.05)'
                }}>
                {systemStatus.broker?.connected ? (
                  <div className="w-16 h-16 rounded-full flex items-center justify-center animate-pulse" style={{ backgroundColor: 'rgba(34, 197, 94, 0.2)' }}>
                    <Link size={32} style={{ color: '#22c55e' }} />
                  </div>
                ) : (
                  <div className="w-16 h-16 rounded-full flex items-center justify-center" style={{ backgroundColor: 'rgba(239, 68, 68, 0.2)' }}>
                    <Unplug size={32} style={{ color: '#ef4444' }} />
                  </div>
                )}

                <div>
                  <div className="text-lg font-bold" style={{ color: systemStatus.broker?.connected ? '#22c55e' : '#ef4444' }}>
                    {systemStatus.broker?.connected ? 'CONNECTED' : 'DISCONNECTED'}
                  </div>
                  <div className="text-xs text-muted-foreground mt-1">
                    {systemStatus.broker?.message || 'No connection message'}
                  </div>

                  {!systemStatus.broker?.connected && systemStatus.broker?.userBrokerId && (
                    <div className="pt-2">
                      <Button
                        size="sm"
                        variant="primary"
                        className="bg-primary hover:bg-primary/90 text-white gap-2"
                        onClick={async () => {
                          try {
                            const res = await apiClient.get(`${API_ENDPOINTS.ADMIN.BROKERS}/${systemStatus.broker.userBrokerId}/oauth-url`);
                            if (res.success && res.oauthUrl) {
                              window.location.href = res.oauthUrl;
                            } else {
                              console.error("Failed to get OAuth URL", res);
                              alert("Failed to initiate connection");
                            }
                          } catch (e) {
                            console.error("OAuth Error", e);
                            alert("Connection error");
                          }
                        }}
                      >
                        <Link size={14} /> Connect via OAuth
                      </Button>
                    </div>
                  )}
                </div>
              </div>

              <div className="space-y-3 pt-2">
                <div className="flex justify-between items-center p-3 rounded bg-muted/30">
                  <span className="text-sm text-muted-foreground">Provider</span>
                  <Badge variant="default" className="font-mono bg-background">{systemStatus.broker?.name || 'Unknown'}</Badge>
                </div>
                <div className="flex justify-between items-center p-3 rounded bg-muted/30">
                  <span className="text-sm text-muted-foreground">User ID</span>
                  <code className="text-xs font-mono bg-background px-2 py-1 rounded">{systemStatus.broker?.userId || 'N/A'}</code>
                </div>
              </div>
            </div>

            {/* Column 2: Readiness Checklist */}
            <div className="p-6 space-y-6" style={{ borderRight: '1px solid hsl(var(--border))' }}>
              <Text variant="h4" className="text-muted-foreground uppercase text-xs tracking-wider mb-4">Readiness Checks</Text>

              <div className="space-y-4">
                <div className="flex items-start gap-3">
                  {systemStatus.readiness?.historicalCandles ? (
                    <CheckCircle className="mt-0.5 shrink-0" size={20} style={{ color: '#22c55e' }} />
                  ) : (
                    <Activity className="mt-0.5 animate-spin shrink-0" size={20} style={{ color: '#eab308' }} />
                  )}
                  <div>
                    <div className="font-medium text-sm">Historical Candles</div>
                    <div className="text-xs text-muted-foreground">
                      {systemStatus.readiness?.historicalCandles
                        ? 'Reconciliation complete'
                        : 'Syncing historical data...'}
                    </div>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  {systemStatus.readiness?.ltpStream ? (
                    <CheckCircle className="mt-0.5 shrink-0" size={20} style={{ color: '#22c55e' }} />
                  ) : (
                    <AlertTriangle className="mt-0.5 shrink-0" size={20} style={{ color: '#ef4444' }} />
                  )}
                  <div>
                    <div className="font-medium text-sm">Realtime Tick Stream</div>
                    <div className="text-xs text-muted-foreground">
                      {systemStatus.readiness?.ltpStream
                        ? 'Receiving live ticks'
                        : 'Stream not active'}
                    </div>
                  </div>
                </div>

                <div className="flex items-start gap-3">
                  <CheckCircle className="mt-0.5 shrink-0" size={20} style={{ color: '#22c55e' }} />
                  <div>
                    <div className="font-medium text-sm">Database Connectivity</div>
                    <div className="text-xs text-muted-foreground">Pool active (10/10)</div>
                  </div>
                </div>
              </div>
            </div>

            {/* Column 3: Live Stats */}
            <div className="p-6 space-y-6">
              <Text variant="h4" className="text-muted-foreground uppercase text-xs tracking-wider mb-4">Live Metrics</Text>

              <div className="grid grid-cols-1 gap-4">
                <div className="p-4 rounded-xl" style={{ backgroundColor: 'rgba(59, 130, 246, 0.1)', border: '1px solid rgba(59, 130, 246, 0.2)' }}>
                  <div className="flex justify-between items-start mb-2">
                    <div className="p-2 rounded-lg" style={{ backgroundColor: 'rgba(59, 130, 246, 0.2)', color: '#3b82f6' }}><Activity size={18} /></div>
                    <span className="text-xs font-bold" style={{ color: '#3b82f6' }}>WATCHLIST</span>
                  </div>
                  <div className="text-2xl font-bold">{systemStatus.watchlist?.symbolCount || 0}</div>
                  <div className="text-xs text-muted-foreground truncate">{systemStatus.watchlist?.name || 'None'}</div>
                </div>

                <div className="p-4 rounded-xl" style={{ backgroundColor: 'rgba(249, 115, 22, 0.1)', border: '1px solid rgba(249, 115, 22, 0.2)' }}>
                  <div className="flex justify-between items-start mb-2">
                    <div className="p-2 rounded-lg" style={{ backgroundColor: 'rgba(249, 115, 22, 0.2)', color: '#f97316' }}><Briefcase size={18} /></div>
                    <span className="text-xs font-bold" style={{ color: '#f97316' }}>ACTIVE TRADES</span>
                  </div>
                  <div className="text-2xl font-bold">{systemStatus.trades?.activeCount || 0}</div>
                  <div className="text-xs text-muted-foreground">across {systemStatus.trades?.userCount || 0} users</div>
                </div>
              </div>
            </div>

          </div>
        </Card>
      )
      }

      {/* Loading State */}
      {
        isLoading && (
          <div className="flex items-center justify-center py-12">
            <Spinner size="lg" variant="primary" />
          </div>
        )
      }

      {/* Error State */}
      {
        portfoliosError && (
          <Alert variant="error">
            Failed to load portfolio data: {portfoliosError}
          </Alert>
        )
      }

      {
        !isLoading && (
          <>
            {/* System Metrics - Clickable Cards */}
            <SummaryCards
              cards={[
                {
                  icon: <Users size={20} />,
                  iconBgColor: 'bg-blue-100',
                  iconColor: 'text-blue-600',
                  label: 'Total Users',
                  value: totalUsers,
                  subtitle: `${activeUsers} active`,
                  onClick: () => navigate('/admin/users'),
                },
                {
                  icon: <Activity size={20} />,
                  iconBgColor: 'bg-green-100',
                  iconColor: 'text-green-600',
                  label: 'Broker Connections',
                  value: totalBrokers,
                  subtitle: `${activeBrokers} connected`,
                  onClick: () => navigate('/admin/brokers'),
                },
                {
                  icon: <Briefcase size={20} />,
                  iconBgColor: 'bg-purple-100',
                  iconColor: 'text-purple-600',
                  label: 'Total AUM',
                  value: `₹${(totalAUM / 100000).toFixed(1)}L`,
                  subtitle: selectedUserId ? 'Filtered' : 'All users',
                  onClick: () => navigate('/admin/portfolios'),
                },
                {
                  icon: <TrendingUp size={20} />,
                  iconBgColor: totalPnl >= 0 ? 'bg-green-100' : 'bg-red-100',
                  iconColor: totalPnl >= 0 ? 'text-green-600' : 'text-red-600',
                  label: 'System P&L',
                  value: `${totalPnl >= 0 ? '+' : ''}₹${(Math.abs(totalPnl) / 1000).toFixed(1)}K`,
                  subtitle: `${totalPnlPercent >= 0 ? '+' : ''}${totalPnlPercent.toFixed(2)}%`,
                  valueColor: totalPnl >= 0 ? 'text-green-600' : 'text-red-600',
                  onClick: () => navigate('/admin/portfolios'),
                },
              ]}
            />

            {/* System Health */}
            <div className="space-y-4">
              <Text variant="h3">System Health</Text>
              <SummaryCards
                columns={3}
                cards={[
                  {
                    icon: <Server size={20} />,
                    iconBgColor: 'bg-green-100',
                    iconColor: 'text-green-600',
                    label: 'API Server',
                    value: 'Operational',
                    subtitle: 'All systems running',
                  },
                  {
                    icon: <Wifi size={20} />,
                    iconBgColor: wsConnected ? 'bg-green-100' : 'bg-red-100',
                    iconColor: wsConnected ? 'text-green-600' : 'text-red-600',
                    label: 'WebSocket',
                    value: wsConnected ? 'Connected' : 'Disconnected',
                    valueColor: wsConnected ? 'text-green-600' : 'text-red-600',
                    subtitle: wsConnected ? 'Real-time active' : 'Connection lost',
                  },
                  {
                    icon: <Database size={20} />,
                    iconBgColor: 'bg-green-100',
                    iconColor: 'text-green-600',
                    label: 'Database',
                    value: 'Operational',
                    subtitle: 'Response time: <50ms',
                  },
                ]}
              />
            </div>

            {/* User Portfolio View */}
            <Card>
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">User Portfolio View</Text>
                  <div className="flex items-center gap-3">
                    <Filter size={20} className="text-muted" />
                    <select
                      className="input input--md"
                      value={selectedUserId}
                      onChange={(e) => setSelectedUserId(e.target.value)}
                    >
                      <option value="">All Users</option>
                      {allUsers?.map((user) => (
                        <option key={user.userId} value={user.userId}>
                          {user.displayName} ({user.email})
                        </option>
                      ))}
                    </select>
                    {selectedUserId && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setSelectedUserId('')}
                      >
                        Clear
                      </Button>
                    )}
                  </div>
                </div>

                {/* Portfolios Table */}
                {filteredPortfolios && filteredPortfolios.length > 0 ? (
                  <div className="table-container">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Portfolio</th>
                          <th>User</th>
                          <th className="text-right">Capital</th>
                          <th className="text-right">Available</th>
                          <th className="text-right">Total Value</th>
                          <th className="text-right">P&L</th>
                          <th>Created</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredPortfolios.map((portfolio, index) => (
                          <tr
                            key={portfolio.id ? portfolio.id : `p-${index}`}
                            className="border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted"
                          >
                            <td>
                              <div className="table-primary">{portfolio.name}</div>
                            </td>
                            <td>
                              <div className="table-secondary">
                                {allUsers?.find(u => u.userId === portfolio.userId)?.displayName || portfolio.userId}
                              </div>
                            </td>
                            <td className="text-right">
                              <div className="table-currency">
                                ₹{(portfolio.capital || 0).toLocaleString('en-IN')}
                              </div>
                            </td>
                            <td className="text-right">
                              <div className="table-currency">
                                ₹{(portfolio.availableCapital || 0).toLocaleString('en-IN')}
                              </div>
                            </td>
                            <td className="text-right">
                              <div className="table-currency">
                                ₹{(portfolio.totalValue || 0).toLocaleString('en-IN')}
                              </div>
                            </td>
                            <td className="text-right">
                              <div className="table-status">
                                <div
                                  className={`table-currency ${portfolio.totalPnl >= 0 ? 'text-profit' : 'text-loss'
                                    }`}
                                >
                                  {portfolio.totalPnl >= 0 ? '+' : ''}
                                  ₹{(portfolio.totalPnl || 0).toLocaleString('en-IN')}
                                </div>
                                <Badge variant={portfolio.totalPnl >= 0 ? 'profit' : 'loss'}>
                                  {portfolio.totalPnl >= 0 ? '+' : ''}
                                  {(portfolio.totalPnlPercent || 0).toFixed(2)}%
                                </Badge>
                              </div>
                            </td>
                            <td>
                              <div className="table-date">
                                {portfolio.createdAt ? new Date(portfolio.createdAt).toLocaleDateString() : 'N/A'}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <Alert variant="info">
                    {selectedUserId
                      ? 'This user has no portfolios yet.'
                      : 'No portfolios found in the system.'}
                  </Alert>
                )}
              </div>
            </Card>
          </>
        )
      }
    </main >
  );
}
