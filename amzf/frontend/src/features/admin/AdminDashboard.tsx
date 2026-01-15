/**
 * Admin Dashboard Page
 * System overview and statistics
 */

import { useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { Header } from '@/components/organisms/Header/Header';
import { MetricsGrid } from '@/components/organisms/MetricsGrid/MetricsGrid';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Users, Activity, DollarSign, TrendingUp } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getAdminNavItems } from '@/lib/navigation';

/**
 * Admin dashboard component
 */
export function AdminDashboard() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getAdminNavItems(location.pathname);

  return (
    <div className="min-h-screen bg-background">
      <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />

      <main className="container mx-auto p-6 space-y-6">
        {/* Page Header */}
        <div>
          <Text variant="h1" className="mb-2">
            Admin Dashboard
          </Text>
          <Text variant="body" className="text-muted">
            System overview and management
          </Text>
        </div>

        {/* Metrics Grid */}
        <MetricsGrid
          metrics={[
            {
              title: 'Total Users',
              value: '-',
              icon: <Users size={24} />,
            },
            {
              title: 'Active Brokers',
              value: '-',
              icon: <Activity size={24} />,
            },
            {
              title: 'Total AUM',
              value: '₹-',
              icon: <DollarSign size={24} />,
            },
            {
              title: 'Active Trades',
              value: '-',
              icon: <TrendingUp size={24} />,
            },
          ]}
        />

        {/* Quick Actions */}
        <Card>
          <div className="p-6">
            <Text variant="h3" className="mb-4">
              Quick Actions
            </Text>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <Link to="/admin/users">
                <Card variant="outlined" interactive className="h-full">
                  <div className="p-6">
                    <Users size={32} className="text-primary mb-3" />
                    <Text variant="h4" className="mb-2">
                      User Management
                    </Text>
                    <Text variant="body" className="text-muted text-sm">
                      View and manage system users
                    </Text>
                  </div>
                </Card>
              </Link>

              <Link to="/admin/brokers">
                <Card variant="outlined" interactive className="h-full">
                  <div className="p-6">
                    <Activity size={32} className="text-primary mb-3" />
                    <Text variant="h4" className="mb-2">
                      Broker Management
                    </Text>
                    <Text variant="body" className="text-muted text-sm">
                      Configure broker connections
                    </Text>
                  </div>
                </Card>
              </Link>

              <Link to="/admin/portfolios">
                <Card variant="outlined" interactive className="h-full">
                  <div className="p-6">
                    <DollarSign size={32} className="text-primary mb-3" />
                    <Text variant="h4" className="mb-2">
                      Portfolio Management
                    </Text>
                    <Text variant="body" className="text-muted text-sm">
                      Manage user portfolios
                    </Text>
                  </div>
                </Card>
              </Link>

              <Link to="/admin/settings">
                <Card variant="outlined" interactive className="h-full">
                  <div className="p-6">
                    <TrendingUp size={32} className="text-primary mb-3" />
                    <Text variant="h4" className="mb-2">
                      Settings
                    </Text>
                    <Text variant="body" className="text-muted text-sm">
                      System configuration
                    </Text>
                  </div>
                </Card>
              </Link>
            </div>
          </div>
        </Card>

        {/* System Status */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card>
            <div className="p-6">
              <Text variant="h3" className="mb-4">
                System Health
              </Text>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <Text variant="body">API Server</Text>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-profit" />
                    <Text variant="small" className="text-muted">
                      Operational
                    </Text>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <Text variant="body">WebSocket Server</Text>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-profit" />
                    <Text variant="small" className="text-muted">
                      Operational
                    </Text>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <Text variant="body">Database</Text>
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full bg-profit" />
                    <Text variant="small" className="text-muted">
                      Operational
                    </Text>
                  </div>
                </div>
              </div>
            </div>
          </Card>

          <Card>
            <div className="p-6">
              <Text variant="h3" className="mb-4">
                Recent Activity
              </Text>
              <div className="space-y-3">
                <Text variant="body" className="text-muted text-center py-8">
                  No recent activity
                </Text>
              </div>
            </div>
          </Card>
        </div>
      </main>
    </div>
  );
}
