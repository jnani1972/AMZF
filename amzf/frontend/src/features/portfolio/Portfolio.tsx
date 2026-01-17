/**
 * Portfolio Page
 * Holdings and P&L overview
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { usePortfolios } from '../../hooks/useApi';
import { Header } from '../../components/organisms/Header/Header';
import { MetricsGrid } from '../../components/organisms/MetricsGrid/MetricsGrid';
import { PortfolioSummary } from '../../components/organisms/PortfolioSummary/PortfolioSummary';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { Button } from '../../components/atoms/Button/Button';
import { Badge } from '../../components/atoms/Badge/Badge';
import { TrendingUp, TrendingDown, Wallet, PieChart, Download } from 'lucide-react';
import { getNavItems } from '../../lib/navigation';
import { useMemo } from 'react';

/**
 * Get trend direction from numeric value
 */
const getTrend = (value: number): 'up' | 'down' | 'neutral' => {
  if (value > 0) return 'up';
  if (value < 0) return 'down';
  return 'neutral';
};

/**
 * Get signed prefix for positive/negative values
 */
const getSignPrefix = (value: number): string => (value > 0 ? '+' : '');

/**
 * Get trend icon component based on P&L value
 */
const getTrendIcon = (pnl: number) =>
  pnl >= 0 ? <TrendingUp size={24} /> : <TrendingDown size={24} />;

/**
 * Get P&L badge variant
 */
const getPnlBadgeVariant = (pnl: number): 'profit' | 'loss' | 'default' => {
  if (pnl > 0) return 'profit';
  if (pnl < 0) return 'loss';
  return 'default';
};

/**
 * Get user display object from auth user
 */
const getUserDisplay = (user: any) =>
  user ? { name: user.displayName, email: user.email } : undefined;

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
  const aggregatedMetrics = useMemo(() => {
    if (!portfolios || portfolios.length === 0) {
      return {
        totalValue: 0,
        totalPnl: 0,
        totalPnlPercent: 0,
        totalCapital: 0,
        allocatedCapital: 0,
      };
    }

    const totalValue = portfolios.reduce((sum, p) => sum + p.totalValue, 0);
    const totalPnl = portfolios.reduce((sum, p) => sum + p.totalPnl, 0);
    const totalCapital = portfolios.reduce((sum, p) => sum + p.capital, 0);
    const totalPnlPercent = totalCapital > 0 ? (totalPnl / totalCapital) * 100 : 0;
    const allocatedCapital = portfolios.reduce((sum, p) => sum + p.allocatedCapital, 0);

    return {
      totalValue,
      totalPnl,
      totalPnlPercent,
      totalCapital,
      allocatedCapital,
    };
  }, [portfolios]);

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
        <Header navItems={navItems} user={getUserDisplay(user)} onLogout={logout} />
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
        <Header navItems={navItems} user={getUserDisplay(user)} onLogout={logout} />
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
        <Header navItems={navItems} user={getUserDisplay(user)} onLogout={logout} />
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
      <Header navItems={navItems} user={getUserDisplay(user)} onLogout={logout} />

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
              value: `₹${(portfolio?.totalValue || aggregatedMetrics.totalValue).toLocaleString('en-IN', {
                maximumFractionDigits: 2,
              })}`,
              icon: <Wallet size={24} />,
            },
            {
              title: 'Total P&L',
              value: `₹${(portfolio?.totalPnl || aggregatedMetrics.totalPnl).toLocaleString('en-IN', {
                maximumFractionDigits: 2,
              })}`,
              icon: getTrendIcon(portfolio?.totalPnl || aggregatedMetrics.totalPnl),
              trend: getTrend(portfolio?.totalPnl || aggregatedMetrics.totalPnl),
              trendValue: `${getSignPrefix(portfolio?.totalPnlPercent || aggregatedMetrics.totalPnlPercent)}${(portfolio?.totalPnlPercent || aggregatedMetrics.totalPnlPercent).toFixed(2)}%`,
            },
            {
              title: 'Total Capital',
              value: `₹${(portfolio?.capital || aggregatedMetrics.totalCapital).toLocaleString('en-IN', {
                maximumFractionDigits: 2,
              })}`,
              icon: <PieChart size={24} />,
            },
            {
              title: 'Allocated Capital',
              value: `₹${(portfolio?.allocatedCapital || aggregatedMetrics.allocatedCapital).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`,
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
                <Badge variant={getPnlBadgeVariant(portfolio.totalPnl)}>
                  {getSignPrefix(portfolio.totalPnl)}
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
