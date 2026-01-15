/**
 * Portfolio Page
 * Holdings and P&L overview
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { usePortfolios } from '@/hooks/useApi';
import { Header } from '@/components/organisms/Header/Header';
import { MetricsGrid } from '@/components/organisms/MetricsGrid/MetricsGrid';
import { PortfolioSummary } from '@/components/organisms/PortfolioSummary/PortfolioSummary';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Alert } from '@/components/atoms/Alert/Alert';
import { Spinner } from '@/components/atoms/Spinner/Spinner';
import { Button } from '@/components/atoms/Button/Button';
import { Badge } from '@/components/atoms/Badge/Badge';
import { TrendingUp, TrendingDown, Wallet, PieChart, Download } from 'lucide-react';
import { getNavItems } from '@/lib/navigation';

/**
 * Portfolio component
 */
export function Portfolio() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getNavItems(location.pathname);
  const { data: portfolios, loading, error, refetch } = usePortfolios();
  const [selectedPortfolio, setSelectedPortfolio] = useState<string | null>(null);

  // Get selected or first portfolio
  const portfolio = selectedPortfolio
    ? portfolios?.find((p) => p.id === selectedPortfolio)
    : portfolios?.[0];

  // Calculate aggregated metrics if no specific portfolio selected
  const totalValue = portfolios?.reduce((sum, p) => sum + p.totalValue, 0) || 0;
  const totalPnl = portfolios?.reduce((sum, p) => sum + p.totalPnl, 0) || 0;
  const totalPnlPercent =
    portfolios && portfolios.length > 0
      ? (totalPnl / portfolios.reduce((sum, p) => sum + p.capital, 0)) * 100
      : 0;
  const totalCapital = portfolios?.reduce((sum, p) => sum + p.capital, 0) || 0;
  const allocatedCapital =
    portfolios?.reduce((sum, p) => sum + p.allocatedCapital, 0) || 0;

  /**
   * Handle export to CSV
   */
  const handleExport = () => {
    // TODO: Implement CSV export functionality
    console.log('Exporting portfolio data to CSV');
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
            Failed to load portfolio data: {error}
            <Button variant="secondary" size="sm" onClick={refetch} className="mt-3">
              Retry
            </Button>
          </Alert>
        </main>
      </div>
    );
  }

  /**
   * Render empty state
   */
  if (!portfolios || portfolios.length === 0) {
    return (
      <div className="min-h-screen bg-background">
        <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />
        <main className="container mx-auto p-6">
          <Card>
            <div className="p-12 text-center">
              <PieChart size={48} className="mx-auto mb-4 text-muted" />
              <Text variant="h3" className="mb-2">
                No Portfolios Found
              </Text>
              <Text variant="body" className="text-muted mb-6">
                You don't have any portfolios yet. Contact your administrator to set up a
                portfolio.
              </Text>
            </div>
          </Card>
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
            <Text variant="h1" className="mb-2">
              Portfolio
            </Text>
            <Text variant="body" className="text-muted">
              Track your holdings and performance
            </Text>
          </div>

          <Button
            variant="secondary"
            iconLeft={<Download size={20} />}
            onClick={handleExport}
          >
            Export
          </Button>
        </div>

        {/* Portfolio Selector */}
        {portfolios && portfolios.length > 1 && (
          <Card>
            <div className="p-6">
              <Text variant="label" className="mb-3">
                Select Portfolio
              </Text>
              <div className="flex flex-wrap gap-3">
                <Button
                  variant={selectedPortfolio === null ? 'primary' : 'secondary'}
                  onClick={() => setSelectedPortfolio(null)}
                >
                  All Portfolios
                </Button>
                {portfolios.map((p) => (
                  <Button
                    key={p.id}
                    variant={selectedPortfolio === p.id ? 'primary' : 'secondary'}
                    onClick={() => setSelectedPortfolio(p.id)}
                  >
                    {p.name}
                  </Button>
                ))}
              </div>
            </div>
          </Card>
        )}

        {/* Metrics Grid */}
        <MetricsGrid
          metrics={[
            {
              title: 'Total Value',
              value: `₹${(portfolio?.totalValue || totalValue).toLocaleString('en-IN', {
                maximumFractionDigits: 2,
              })}`,
              icon: <Wallet size={24} />,
            },
            {
              title: 'Total P&L',
              value: `₹${(portfolio?.totalPnl || totalPnl).toLocaleString('en-IN', {
                maximumFractionDigits: 2,
              })}`,
              icon:
                (portfolio?.totalPnl || totalPnl) >= 0 ? (
                  <TrendingUp size={24} />
                ) : (
                  <TrendingDown size={24} />
                ),
              trend:
                (portfolio?.totalPnl || totalPnl) > 0
                  ? 'up'
                  : (portfolio?.totalPnl || totalPnl) < 0
                  ? 'down'
                  : 'neutral',
              trendValue: `${
                (portfolio?.totalPnlPercent || totalPnlPercent) > 0 ? '+' : ''
              }${(portfolio?.totalPnlPercent || totalPnlPercent).toFixed(2)}%`,
            },
            {
              title: 'Total Capital',
              value: `₹${(portfolio?.capital || totalCapital).toLocaleString('en-IN', {
                maximumFractionDigits: 2,
              })}`,
              icon: <PieChart size={24} />,
            },
            {
              title: 'Allocated Capital',
              value: `₹${(
                portfolio?.allocatedCapital || allocatedCapital
              ).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`,
              icon: <PieChart size={24} />,
            },
          ]}
        />

        {/* Portfolio Details Card */}
        {portfolio && (
          <Card>
            <div className="p-6">
              <div className="flex items-center justify-between mb-6">
                <Text variant="h3">{portfolio.name}</Text>
                <Badge
                  variant={
                    portfolio.totalPnl > 0
                      ? 'profit'
                      : portfolio.totalPnl < 0
                      ? 'loss'
                      : 'default'
                  }
                >
                  {portfolio.totalPnl > 0 ? '+' : ''}
                  {portfolio.totalPnlPercent.toFixed(2)}%
                </Badge>
              </div>

              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Capital
                  </Text>
                  <Text variant="label">
                    ₹{portfolio.capital.toLocaleString('en-IN')}
                  </Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Available
                  </Text>
                  <Text variant="label">
                    ₹{portfolio.availableCapital.toLocaleString('en-IN')}
                  </Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Allocated
                  </Text>
                  <Text variant="label">
                    ₹{portfolio.allocatedCapital.toLocaleString('en-IN')}
                  </Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Created
                  </Text>
                  <Text variant="label">
                    {new Date(portfolio.createdAt).toLocaleDateString()}
                  </Text>
                </div>
              </div>
            </div>
          </Card>
        )}

        {/* Holdings Table */}
        <PortfolioSummary holdings={[]} />

        {/* Performance Chart Placeholder */}
        <Card>
          <div className="p-6">
            <Text variant="h3" className="mb-4">
              Performance Chart
            </Text>
            <div className="h-64 flex items-center justify-center bg-surface-secondary rounded-lg">
              <Text variant="body" className="text-muted">
                Chart visualization will be implemented with Recharts
              </Text>
            </div>
          </div>
        </Card>
      </main>
    </div>
  );
}
