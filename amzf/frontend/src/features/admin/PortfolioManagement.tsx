/**
 * Portfolio Management Page
 * Admin page to manage all user portfolios
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { Header } from '@/components/organisms/Header/Header';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Badge } from '@/components/atoms/Badge/Badge';
import { Button } from '@/components/atoms/Button/Button';
import { Input } from '@/components/atoms/Input/Input';
import { Alert } from '@/components/atoms/Alert/Alert';
import { Spinner } from '@/components/atoms/Spinner/Spinner';
import { EmptyState } from '@/components/molecules/EmptyState/EmptyState';
import { RefreshCw, PlusCircle, Briefcase, Edit, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getAdminNavItems } from '@/lib/navigation';

/**
 * Mock portfolio data structure
 */
interface AdminPortfolio {
  id: string;
  userId: string;
  userName: string;
  name: string;
  capital: number;
  allocatedCapital: number;
  availableCapital: number;
  totalValue: number;
  totalPnl: number;
  totalPnlPercent: number;
  createdAt: Date;
  isActive: boolean;
}

/**
 * Portfolio management component
 */
export function PortfolioManagement() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getAdminNavItems(location.pathname);
  const [loading] = useState(false);
  const [error] = useState<string | null>(null);
  const [portfolios] = useState<AdminPortfolio[]>([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Filter portfolios by search query
  const filteredPortfolios = portfolios.filter(
    (p) =>
      p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.userName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.userId.toLowerCase().includes(searchQuery.toLowerCase())
  );

  /**
   * Handle refresh
   */
  const handleRefresh = () => {
    console.log('Refreshing portfolios...');
  };

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
            Failed to load portfolios: {error}
            <Button variant="secondary" size="sm" onClick={handleRefresh} className="mt-3">
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
              <Text variant="h2">Portfolios</Text>
            </div>
            <Text variant="body" className="text-muted">
              Manage user portfolios and capital allocation
            </Text>
          </div>

          <div className="flex gap-3">
            <Button variant="secondary" iconLeft={<RefreshCw size={20} />} onClick={handleRefresh}>
              Refresh
            </Button>
            <Button
              variant="primary"
              iconLeft={<PlusCircle size={20} />}
              onClick={() => setShowCreateModal(true)}
            >
              Create Portfolio
            </Button>
          </div>
        </div>

        {/* Search Bar */}
        <Card>
          <div className="p-6">
            <Input
              type="search"
              placeholder="Search by portfolio name, user, or user ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              fullWidth
            />
          </div>
        </Card>

        {/* Portfolios Table */}
        <Card>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-border-light">
                  <th className="p-4 text-left">
                    <Text variant="label">Portfolio</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">User</Text>
                  </th>
                  <th className="p-4 text-right">
                    <Text variant="label">Capital</Text>
                  </th>
                  <th className="p-4 text-right">
                    <Text variant="label">Available</Text>
                  </th>
                  <th className="p-4 text-right">
                    <Text variant="label">Total Value</Text>
                  </th>
                  <th className="p-4 text-right">
                    <Text variant="label">P&L</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Status</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Created</Text>
                  </th>
                  <th className="p-4 text-right">
                    <Text variant="label">Actions</Text>
                  </th>
                </tr>
              </thead>
              <tbody>
                {filteredPortfolios.length > 0 ? (
                  filteredPortfolios.map((portfolio) => (
                    <tr
                      key={portfolio.id}
                      className="border-b border-border-light hover:bg-surface-secondary transition-colors"
                    >
                      <td className="p-4">
                        <Text variant="body" className="font-medium">
                          {portfolio.name}
                        </Text>
                        <Text variant="small" className="text-muted">
                          {portfolio.id.slice(0, 8)}...
                        </Text>
                      </td>
                      <td className="p-4">
                        <Text variant="body">{portfolio.userName}</Text>
                        <Text variant="small" className="text-muted font-mono">
                          {portfolio.userId.slice(0, 8)}...
                        </Text>
                      </td>
                      <td className="p-4 text-right">
                        <Text variant="body">
                          ₹{portfolio.capital.toLocaleString('en-IN')}
                        </Text>
                      </td>
                      <td className="p-4 text-right">
                        <Text variant="body">
                          ₹{portfolio.availableCapital.toLocaleString('en-IN')}
                        </Text>
                      </td>
                      <td className="p-4 text-right">
                        <Text variant="body">
                          ₹{portfolio.totalValue.toLocaleString('en-IN')}
                        </Text>
                      </td>
                      <td className="p-4 text-right">
                        <div>
                          <Text
                            variant="body"
                            className={
                              portfolio.totalPnl >= 0 ? 'text-profit' : 'text-loss'
                            }
                          >
                            {portfolio.totalPnl >= 0 ? '+' : ''}
                            ₹{portfolio.totalPnl.toLocaleString('en-IN')}
                          </Text>
                          <Badge
                            variant={portfolio.totalPnl >= 0 ? 'profit' : 'loss'}
                            className="mt-1"
                          >
                            {portfolio.totalPnl >= 0 ? '+' : ''}
                            {portfolio.totalPnlPercent.toFixed(2)}%
                          </Badge>
                        </div>
                      </td>
                      <td className="p-4">
                        <Badge variant={portfolio.isActive ? 'success' : 'default'}>
                          {portfolio.isActive ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className="p-4">
                        <Text variant="small" className="text-muted">
                          {new Date(portfolio.createdAt).toLocaleDateString()}
                        </Text>
                      </td>
                      <td className="p-4 text-right">
                        <div className="flex gap-2 justify-end">
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Edit size={16} />}
                          >
                            <></>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Trash2 size={16} />}
                            className="text-loss"
                          >
                            <></>
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={9} className="p-12">
                      <EmptyState
                        icon={<Briefcase size={48} />}
                        title={
                          searchQuery
                            ? 'No Portfolios Found'
                            : 'No Portfolios Created'
                        }
                        description={
                          searchQuery
                            ? `No portfolios match "${searchQuery}"`
                            : 'Create a portfolio to get started'
                        }
                        ctaText={searchQuery ? undefined : 'Create Portfolio'}
                        onCtaClick={
                          searchQuery ? undefined : () => setShowCreateModal(true)
                        }
                      />
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>

        {/* Summary Card */}
        {portfolios.length > 0 && (
          <Card>
            <div className="p-6">
              <Text variant="h3" className="mb-4">
                Summary
              </Text>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Total Portfolios
                  </Text>
                  <Text variant="h3">{portfolios.length}</Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Total AUM
                  </Text>
                  <Text variant="h3">
                    ₹
                    {portfolios
                      .reduce((sum, p) => sum + p.totalValue, 0)
                      .toLocaleString('en-IN')}
                  </Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Total Capital
                  </Text>
                  <Text variant="h3">
                    ₹
                    {portfolios
                      .reduce((sum, p) => sum + p.capital, 0)
                      .toLocaleString('en-IN')}
                  </Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Active Portfolios
                  </Text>
                  <Text variant="h3">
                    {portfolios.filter((p) => p.isActive).length}
                  </Text>
                </div>
              </div>
            </div>
          </Card>
        )}
      </main>

      {/* Create Portfolio Modal */}
      {showCreateModal && (
        <div
          className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50"
          onClick={() => setShowCreateModal(false)}
        >
          <div className="w-full max-w-md" onClick={(e) => e.stopPropagation()}>
            <Card>
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">Create Portfolio</Text>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setShowCreateModal(false)}
                  >
                    Close
                  </Button>
                </div>

                <Alert variant="info">
                  Portfolio creation functionality will be implemented with backend integration.
                </Alert>

                <div className="space-y-4">
                  <div>
                    <Text variant="label" className="mb-2">
                      User ID
                    </Text>
                    <Input type="text" placeholder="Enter user ID" fullWidth />
                  </div>
                  <div>
                    <Text variant="label" className="mb-2">
                      Portfolio Name
                    </Text>
                    <Input type="text" placeholder="e.g., Main Trading Account" fullWidth />
                  </div>
                  <div>
                    <Text variant="label" className="mb-2">
                      Initial Capital
                    </Text>
                    <Input type="number" placeholder="100000" fullWidth />
                  </div>
                </div>

                <div className="flex gap-3">
                  <Button
                    variant="secondary"
                    fullWidth
                    onClick={() => setShowCreateModal(false)}
                  >
                    Cancel
                  </Button>
                  <Button variant="primary" fullWidth>
                    Create
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
