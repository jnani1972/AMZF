/**
 * Broker Management Page
 * View and manage broker connections
 */

import { useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { useAllUserBrokers } from '@/hooks/useApi';
import { Header } from '@/components/organisms/Header/Header';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Badge } from '@/components/atoms/Badge/Badge';
import { Button } from '@/components/atoms/Button/Button';
import { Alert } from '@/components/atoms/Alert/Alert';
import { Spinner } from '@/components/atoms/Spinner/Spinner';
import { EmptyState } from '@/components/molecules/EmptyState/EmptyState';
import { BrokerStatusBadge } from '@/components/molecules/BrokerStatusBadge/BrokerStatusBadge';
import { RefreshCw, Activity } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getAdminNavItems } from '@/lib/navigation';

/**
 * Broker management component
 */
export function BrokerManagement() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getAdminNavItems(location.pathname);
  const { data: brokers, loading, error, refetch } = useAllUserBrokers();

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <div className="min-h-screen bg-background">
        <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />
        <main className="container mx-auto p-6">
          <div className="flex items-center justify-center py-12">
            <Spinner size="lg" variant="primary" />
          </div>
        </main>
      </div>
    );
  }

  /**
   * Render error state
   */
  if (error) {
    return (
      <div className="min-h-screen bg-background">
        <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />
        <main className="container mx-auto p-6">
          <Alert variant="error">
            Failed to load brokers: {error}
            <Button variant="secondary" size="sm" onClick={refetch} className="mt-3">
              Retry
            </Button>
          </Alert>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />

      <main className="container mx-auto p-6 space-y-6">
        {/* Page Header */}
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <Link to="/admin">
                <Text variant="body" className="text-muted hover:text-primary">
                  Admin
                </Text>
              </Link>
              <Text variant="body" className="text-muted">
                /
              </Text>
              <Text variant="h2">Brokers</Text>
            </div>
            <Text variant="body" className="text-muted">
              Manage broker connections and health
            </Text>
          </div>

          <Button variant="secondary" iconLeft={<RefreshCw size={20} />} onClick={refetch}>
            Refresh
          </Button>
        </div>

        {/* Brokers Table */}
        <Card>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-border-light">
                  <th className="p-4 text-left">
                    <Text variant="label">User</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Broker</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Role</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Status</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Health</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Last Check</Text>
                  </th>
                  <th className="p-4 text-right">
                    <Text variant="label">Actions</Text>
                  </th>
                </tr>
              </thead>
              <tbody>
                {brokers && brokers.length > 0 ? (
                  brokers.map((broker) => (
                    <tr
                      key={broker.id}
                      className="border-b border-border-light hover:bg-surface-secondary transition-colors"
                    >
                      <td className="p-4">
                        <Text variant="body">{broker.userId}</Text>
                      </td>
                      <td className="p-4">
                        <Text variant="body" className="font-medium">
                          {broker.brokerName}
                        </Text>
                      </td>
                      <td className="p-4">
                        <Badge variant={broker.role === 'EXEC' ? 'primary' : 'info'}>
                          {broker.role}
                        </Badge>
                      </td>
                      <td className="p-4">
                        <Badge variant={broker.isActive ? 'success' : 'default'}>
                          {broker.isActive ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className="p-4">
                        <BrokerStatusBadge
                          broker={broker.brokerName}
                          health={broker.healthStatus}
                          latency={broker.latencyMs}
                          lastUpdate={
                            broker.lastHealthCheck
                              ? new Date(broker.lastHealthCheck)
                              : undefined
                          }
                        />
                      </td>
                      <td className="p-4">
                        <Text variant="small" className="text-muted">
                          {broker.lastHealthCheck
                            ? new Date(broker.lastHealthCheck).toLocaleString()
                            : 'Never'}
                        </Text>
                      </td>
                      <td className="p-4 text-right">
                        <div className="flex gap-2 justify-end">
                          <Button variant="ghost" size="sm">
                            Edit
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className={broker.isActive ? 'text-loss' : 'text-profit'}
                          >
                            {broker.isActive ? 'Disable' : 'Enable'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={7} className="p-12">
                      <EmptyState
                        icon={<Activity size={48} />}
                        title="No Brokers Found"
                        description="No broker connections configured yet."
                      />
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      </main>
    </div>
  );
}
