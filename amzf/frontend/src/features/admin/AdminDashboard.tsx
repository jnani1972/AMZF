/**
 * Admin Dashboard Page
 * System-wide overview with all users' data and filtering capabilities
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { SummaryCards } from '../../components/organisms/SummaryCards/SummaryCards';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Button } from '../../components/atoms/Button/Button';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Users, Activity, TrendingUp, Filter, Briefcase, Server, Wifi, Database } from 'lucide-react';
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

      {/* Loading State */}
      {isLoading && (
        <div className="flex items-center justify-center py-12">
          <Spinner size="lg" variant="primary" />
        </div>
      )}

      {/* Error State */}
      {portfoliosError && (
        <Alert variant="error">
          Failed to load portfolio data: {portfoliosError}
        </Alert>
      )}

      {!isLoading && (
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
                      {filteredPortfolios.map((portfolio) => (
                        <tr key={portfolio.id}>
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
                              ₹{portfolio.capital.toLocaleString('en-IN')}
                            </div>
                          </td>
                          <td className="text-right">
                            <div className="table-currency">
                              ₹{portfolio.availableCapital.toLocaleString('en-IN')}
                            </div>
                          </td>
                          <td className="text-right">
                            <div className="table-currency">
                              ₹{portfolio.totalValue.toLocaleString('en-IN')}
                            </div>
                          </td>
                          <td className="text-right">
                            <div className="table-status">
                              <div
                                className={`table-currency ${portfolio.totalPnl >= 0 ? 'text-profit' : 'text-loss'
                                  }`}
                              >
                                {portfolio.totalPnl >= 0 ? '+' : ''}
                                ₹{portfolio.totalPnl.toLocaleString('en-IN')}
                              </div>
                              <Badge variant={portfolio.totalPnl >= 0 ? 'profit' : 'loss'}>
                                {portfolio.totalPnl >= 0 ? '+' : ''}
                                {portfolio.totalPnlPercent.toFixed(2)}%
                              </Badge>
                            </div>
                          </td>
                          <td>
                            <div className="table-date">
                              {new Date(portfolio.createdAt).toLocaleDateString()}
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
      )}
    </main>
  );
}
