/**
 * Dashboard Page
 * Main landing page after login with real-time data
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { usePortfolios, useSignals, useTrades } from '@/hooks/useApi';
import { useWebSocket, useRealtimeEvents } from '@/hooks/useWebSocket';
import { Header } from '@/components/organisms/Header/Header';
import { MetricsGrid } from '@/components/organisms/MetricsGrid/MetricsGrid';
import { PortfolioSummary } from '@/components/organisms/PortfolioSummary/PortfolioSummary';
import { TradeTable } from '@/components/organisms/TradeTable/TradeTable';
import { OrderPanel } from '@/components/organisms/OrderPanel/OrderPanel';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Button } from '@/components/atoms/Button/Button';
import { Alert } from '@/components/atoms/Alert/Alert';
import { Spinner } from '@/components/atoms/Spinner/Spinner';
import type { OrderFormData } from '@/components/organisms/OrderPanel/OrderPanel';
import { TrendingUp, Activity, BarChart3, AlertCircle } from 'lucide-react';
import { getNavItems } from '@/lib/navigation';

/**
 * Dashboard component
 */
export function Dashboard() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const { connected: wsConnected } = useWebSocket();
  const { events } = useRealtimeEvents();
  const navItems = getNavItems(location.pathname);

  // Fetch data
  const { data: portfolios, loading: portfoliosLoading, error: portfoliosError } = usePortfolios();
  const { data: signals, loading: signalsLoading } = useSignals(undefined, 10);
  const { data: trades, loading: tradesLoading } = useTrades('OPEN');

  // Order panel state
  const [showOrderPanel, setShowOrderPanel] = useState(false);

  // Calculate total portfolio metrics
  const totalValue = portfolios?.reduce((sum, p) => sum + p.totalValue, 0) || 0;
  const totalPnl = portfolios?.reduce((sum, p) => sum + p.totalPnl, 0) || 0;
  const totalPnlPercent =
    portfolios && portfolios.length > 0
      ? (totalPnl / portfolios.reduce((sum, p) => sum + p.capital, 0)) * 100
      : 0;
  const availableCapital =
    portfolios?.reduce((sum, p) => sum + p.availableCapital, 0) || 0;

  /**
   * Handle buy order
   */
  const handleBuyOrder = async (data: OrderFormData) => {
    console.log('Buy order:', data);
    // TODO: Implement actual order placement using usePlaceOrder hook
    setShowOrderPanel(false);
  };

  /**
   * Handle sell order
   */
  const handleSellOrder = async (data: OrderFormData) => {
    console.log('Sell order:', data);
    // TODO: Implement actual order placement using usePlaceOrder hook
    setShowOrderPanel(false);
  };

  /**
   * Render loading state
   */
  if (portfoliosLoading) {
    return (
      <div className="min-h-screen bg-background">
        <Header
          navItems={navItems}
          user={user ? { name: user.displayName, email: user.email } : undefined}
          onLogout={logout}
        />
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
  if (portfoliosError) {
    return (
      <div className="min-h-screen bg-background">
        <Header
          navItems={navItems}
          user={user ? { name: user.displayName, email: user.email } : undefined}
          onLogout={logout}
        />
        <main className="container mx-auto p-6">
          <Alert variant="error">
            Failed to load dashboard data: {portfoliosError}
          </Alert>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      <Header
        navItems={navItems}
        user={user ? { name: user.displayName, email: user.email } : undefined}
        onLogout={logout}
      />

      <main className="container mx-auto p-6 space-y-6">
        {/* Welcome Header */}
        <div className="flex items-center justify-between">
          <div>
            <Text variant="h1" className="mb-2">
              Welcome back, {user?.displayName}
            </Text>
            <Text variant="body" className="text-muted">
              Here's your trading overview
            </Text>
          </div>

          {/* WebSocket Status */}
          <div className="flex items-center gap-2">
            <div
              className={`w-2 h-2 rounded-full ${
                wsConnected ? 'bg-profit' : 'bg-loss'
              }`}
            />
            <Text variant="small" className="text-muted">
              {wsConnected ? 'Live' : 'Disconnected'}
            </Text>
          </div>
        </div>

        {/* Metrics Grid */}
        <MetricsGrid
          metrics={[
            {
              title: 'Total Portfolio Value',
              value: `₹${totalValue.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`,
              icon: <BarChart3 size={24} />,
              trend: totalPnlPercent > 0 ? 'up' : totalPnlPercent < 0 ? 'down' : 'neutral',
              trendValue: `${totalPnlPercent > 0 ? '+' : ''}${totalPnlPercent.toFixed(2)}%`,
            },
            {
              title: 'Total P&L',
              value: `₹${totalPnl.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`,
              icon: <TrendingUp size={24} />,
              trend: totalPnl > 0 ? 'up' : totalPnl < 0 ? 'down' : 'neutral',
              trendValue: `${totalPnl > 0 ? '+' : ''}${totalPnl.toFixed(2)}`,
            },
            {
              title: 'Available Capital',
              value: `₹${availableCapital.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`,
              icon: <Activity size={24} />,
            },
            {
              title: 'Open Trades',
              value: trades?.length.toString() || '0',
              icon: <AlertCircle size={24} />,
            },
          ]}
        />

        {/* Quick Actions */}
        <Card>
          <div className="p-6">
            <Text variant="h3" className="mb-4">
              Quick Actions
            </Text>
            <div className="flex gap-3">
              <Button
                variant="buy"
                size="lg"
                onClick={() => setShowOrderPanel(true)}
              >
                Place Order
              </Button>
              <Button variant="secondary" size="lg">
                View Market Watch
              </Button>
              <Button variant="secondary" size="lg">
                View Signals
              </Button>
            </div>
          </div>
        </Card>

        {/* Two Column Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Recent Signals */}
          <Card>
            <div className="p-6">
              <Text variant="h3" className="mb-4">
                Recent Signals
              </Text>
              {signalsLoading ? (
                <div className="flex justify-center py-8">
                  <Spinner variant="primary" />
                </div>
              ) : signals && signals.length > 0 ? (
                <div className="space-y-3">
                  {signals.slice(0, 5).map((signal) => (
                    <div
                      key={signal.id}
                      className="flex items-center justify-between p-3 bg-surface-secondary rounded-lg"
                    >
                      <div>
                        <Text variant="label" className="font-medium">
                          {signal.symbol}
                        </Text>
                        <Text variant="small" className="text-muted">
                          {signal.direction} • {signal.strength}
                        </Text>
                      </div>
                      <div className="text-right">
                        <Text variant="label">₹{signal.entryPrice}</Text>
                        <Text variant="small" className="text-muted">
                          {signal.timeframe}
                        </Text>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <Text variant="body" className="text-muted text-center py-8">
                  No signals available
                </Text>
              )}
            </div>
          </Card>

          {/* Recent Events */}
          <Card>
            <div className="p-6">
              <Text variant="h3" className="mb-4">
                Recent Events
              </Text>
              {events.length > 0 ? (
                <div className="space-y-3 max-h-80 overflow-y-auto">
                  {events.slice(0, 10).map((event, index) => (
                    <div
                      key={index}
                      className="flex items-start gap-3 p-3 bg-surface-secondary rounded-lg"
                    >
                      <div
                        className={`w-2 h-2 rounded-full mt-1.5 ${
                          event.type === 'SIGNAL'
                            ? 'bg-primary'
                            : event.type === 'TRADE_UPDATE'
                            ? 'bg-profit'
                            : 'bg-warning'
                        }`}
                      />
                      <div className="flex-1 min-w-0">
                        <Text variant="label" className="truncate">
                          {event.type}
                        </Text>
                        <Text variant="small" className="text-muted">
                          {event.timestamp.toLocaleTimeString()}
                        </Text>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <Text variant="body" className="text-muted text-center py-8">
                  No recent events
                </Text>
              )}
            </div>
          </Card>
        </div>

        {/* Open Trades */}
        {tradesLoading ? (
          <Card>
            <div className="p-6">
              <div className="flex justify-center py-8">
                <Spinner variant="primary" />
              </div>
            </div>
          </Card>
        ) : (
          <TradeTable
            trades={(trades || []).map((t) => ({
              id: t.id,
              symbol: t.symbol,
              direction: t.direction.toLowerCase() as 'buy' | 'sell',
              quantity: t.quantity,
              entryPrice: t.entryPrice,
              exitPrice: t.exitPrice,
              pnl: t.pnl,
              status: t.status.toLowerCase() as 'open' | 'closed',
              entryTime: new Date(t.entryTime),
              exitTime: t.exitTime ? new Date(t.exitTime) : undefined,
            }))}
            title="Open Trades"
          />
        )}

        {/* Portfolio Summary */}
        <PortfolioSummary holdings={[]} />
      </main>

      {/* Order Panel Modal */}
      {showOrderPanel && (
        <div
          className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50"
          onClick={() => setShowOrderPanel(false)}
        >
          <div
            className="w-full max-w-md"
            onClick={(e) => e.stopPropagation()}
          >
            <Card>
              <div className="p-6">
                <div className="flex items-center justify-between mb-6">
                  <Text variant="h3">Place Order</Text>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setShowOrderPanel(false)}
                  >
                    Close
                  </Button>
                </div>
                <OrderPanel onBuy={handleBuyOrder} onSell={handleSellOrder} />
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
