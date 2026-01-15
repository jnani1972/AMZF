/**
 * Market Watch Page
 * Watchlist and live market data
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { useMarketWatch, useWatchlists } from '@/hooks/useApi';
import { useRealtimePrices } from '@/hooks/useWebSocket';
import { Header } from '@/components/organisms/Header/Header';
import { Watchlist } from '@/components/organisms/Watchlist/Watchlist';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Alert } from '@/components/atoms/Alert/Alert';
import { Spinner } from '@/components/atoms/Spinner/Spinner';
import { Badge } from '@/components/atoms/Badge/Badge';
import { Button } from '@/components/atoms/Button/Button';
import { EmptyState } from '@/components/molecules/EmptyState/EmptyState';
import { RefreshCw, TrendingUp, TrendingDown, Eye } from 'lucide-react';
import { getNavItems } from '@/lib/navigation';

/**
 * Market watch component
 */
export function MarketWatch() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getNavItems(location.pathname);
  const { data: marketData, loading, error, refetch } = useMarketWatch();
  const { data: watchlists } = useWatchlists();
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);

  // Get symbols from selected watchlist or use all from market data
  const symbols =
    selectedWatchlist && watchlists
      ? watchlists.find((w) => w.id === selectedWatchlist)?.symbols || []
      : marketData?.map((d) => d.symbol) || [];

  // Get real-time prices for symbols
  const { prices, lastUpdate } = useRealtimePrices(symbols);

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
            Failed to load market data: {error}
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
            <Text variant="h1" className="mb-2">
              Market Watch
            </Text>
            <Text variant="body" className="text-muted">
              Real-time market data and watchlists
            </Text>
          </div>

          <div className="flex items-center gap-3">
            {lastUpdate && (
              <Text variant="small" className="text-muted">
                Updated: {lastUpdate.toLocaleTimeString()}
              </Text>
            )}
            <Button
              variant="secondary"
              iconLeft={<RefreshCw size={20} />}
              onClick={refetch}
            >
              Refresh
            </Button>
          </div>
        </div>

        {/* Watchlist Selector */}
        {watchlists && watchlists.length > 0 && (
          <Card>
            <div className="p-6">
              <Text variant="label" className="mb-3">
                Select Watchlist
              </Text>
              <div className="flex flex-wrap gap-3">
                <Button
                  variant={selectedWatchlist === null ? 'primary' : 'secondary'}
                  onClick={() => setSelectedWatchlist(null)}
                >
                  All Markets
                </Button>
                {watchlists.map((w) => (
                  <Button
                    key={w.id}
                    variant={selectedWatchlist === w.id ? 'primary' : 'secondary'}
                    onClick={() => setSelectedWatchlist(w.id)}
                  >
                    {w.name} ({w.symbols.length})
                  </Button>
                ))}
              </div>
            </div>
          </Card>
        )}

        {/* Watchlist Component */}
        <Watchlist
          items={symbols.map((symbol) => {
            const tick = prices.get(symbol);
            const marketDataItem = marketData?.find((d) => d.symbol === symbol);
            return {
              symbol,
              price: tick?.ltp || marketDataItem?.ltp || 0,
              change: marketDataItem?.change || 0,
              changePercent: marketDataItem?.changePercent || 0,
            };
          })}
          onAddSymbol={(symbol) => console.log('Add symbol:', symbol)}
          onRemoveSymbol={(symbol) => console.log('Remove symbol:', symbol)}
        />

        {/* Market Overview Grid */}
        {marketData && marketData.length > 0 && (
          <Card>
            <div className="p-6">
              <Text variant="h3" className="mb-4">
                Market Overview
              </Text>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {marketData.slice(0, 6).map((data) => (
                  <div
                    key={data.symbol}
                    className="p-4 bg-surface-secondary rounded-lg hover:bg-surface-tertiary transition-colors cursor-pointer"
                  >
                    <div className="flex items-start justify-between mb-2">
                      <Text variant="label" className="font-medium">
                        {data.symbol}
                      </Text>
                      <Badge
                        variant={data.change >= 0 ? 'profit' : 'loss'}
                        className="text-xs"
                      >
                        {data.change >= 0 ? '+' : ''}
                        {data.changePercent.toFixed(2)}%
                      </Badge>
                    </div>

                    <div className="flex items-end justify-between">
                      <div>
                        <Text variant="h4" className="mb-1">
                          ₹{data.ltp.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                        </Text>
                        <Text variant="small" className="text-muted">
                          Vol: {(data.volume / 1000).toFixed(1)}K
                        </Text>
                      </div>
                      <div className="text-right">
                        {data.change >= 0 ? (
                          <TrendingUp size={24} className="text-profit" />
                        ) : (
                          <TrendingDown size={24} className="text-loss" />
                        )}
                      </div>
                    </div>

                    <div className="mt-3 pt-3 border-t border-border-light">
                      <div className="flex justify-between text-xs">
                        <Text variant="small" className="text-muted">
                          High: ₹{data.dayHigh.toFixed(2)}
                        </Text>
                        <Text variant="small" className="text-muted">
                          Low: ₹{data.dayLow.toFixed(2)}
                        </Text>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </Card>
        )}

        {/* Empty State */}
        {(!marketData || marketData.length === 0) && (
          <Card>
            <div className="p-12">
              <EmptyState
                icon={<Eye size={48} />}
                title="No Market Data"
                description="No market data available at the moment."
              />
            </div>
          </Card>
        )}
      </main>
    </div>
  );
}
