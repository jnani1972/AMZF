/**
 * Market Watch Page
 * Watchlist and live market data
 */

import { useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useMarketWatch, useWatchlists, useUserBrokers } from '../../hooks/useApi';
import { useRealtimePrices } from '../../hooks/useWebSocket';
import { apiClient } from '../../lib/api';
import { Header } from '../../components/organisms/Header/Header';
import { DataTable } from '../../components/organisms/DataTable/DataTable';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { Button } from '../../components/atoms/Button/Button';
import { ChangeIndicator } from '../../components/atoms/ChangeIndicator/ChangeIndicator';
import { GapIndicator } from '../../components/atoms/GapIndicator/GapIndicator';
import { RangeIndicator } from '../../components/atoms/RangeIndicator/RangeIndicator';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import { SearchBar, SearchOption } from '../../components/molecules/SearchBar/SearchBar';
import { Modal } from '../../components/organisms/Modal/Modal';
import { Badge } from '../../components/atoms/Badge/Badge';
import { RefreshCw, Eye, TrendingUp, TrendingDown, Filter, List, Plus, Trash2 } from 'lucide-react';
import { getNavItems } from '../../lib/navigation';

/**
 * Market watch component
 */
export function MarketWatch() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getNavItems(location.pathname);
  const { data: marketData, loading, error, refetch } = useMarketWatch();
  const { data: watchlists, refetch: refetchWatchlists } = useWatchlists();
  const { data: userBrokers } = useUserBrokers();

  // Filter state: 'all' | 'watchlist'
  const [filterMode, setFilterMode] = useState<'all' | 'watchlist'>('all');

  // Add symbol modal state
  const [showAddModal, setShowAddModal] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<SearchOption[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  // Get symbols from watchlists or fallback to market data
  const symbols = watchlists && watchlists.length > 0
    ? watchlists.map((w) => w.symbol)
    : marketData?.map((d) => d.symbol) || [];

  // Get watchlist symbol set for filtering
  const watchlistSymbols = useMemo(() => new Set(symbols), [symbols]);

  // Get real-time prices for symbols
  const { prices, lastUpdate } = useRealtimePrices(symbols);

  // Enhance market data with real-time prices and overnight gap
  const enhancedMarketData = useMemo(() => {
    if (!marketData) return [];

    return marketData.map((data) => {
      const tick = prices.get(data.symbol);
      const ltp = tick?.ltp || data.ltp;

      // Calculate overnight gap: (open - previousClose) / previousClose * 100
      const overnightGap = data.open && data.close
        ? ((data.open - data.close) / data.close) * 100
        : 0;

      return {
        ...data,
        ltp,
        overnightGap,
      };
    });
  }, [marketData, prices]);

  // Filter market data based on filter mode
  const filteredMarketData = useMemo(() => {
    if (filterMode === 'all') {
      return enhancedMarketData;
    }
    // Show only watchlist symbols
    return enhancedMarketData.filter((item) => watchlistSymbols.has(item.symbol));
  }, [enhancedMarketData, filterMode, watchlistSymbols]);

  // Define DataTable columns
  const columns = useMemo(() => [
    {
      key: 'symbol',
      header: 'Symbol',
      align: 'left' as const,
      sortable: true,
      sortValue: (item: typeof enhancedMarketData[0]) => item.symbol,
      render: (item: typeof enhancedMarketData[0]) => (
        <Text variant="body" weight="medium">
          {item.symbol}
        </Text>
      ),
    },
    {
      key: 'ltp',
      header: 'LTP',
      align: 'right' as const,
      sortable: true,
      sortValue: (item: typeof enhancedMarketData[0]) => item.ltp,
      render: (item: typeof enhancedMarketData[0]) => (
        <Text variant="body">
          ₹{item.ltp.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
        </Text>
      ),
    },
    {
      key: 'changePercent',
      header: 'Change %',
      align: 'right' as const,
      sortable: true,
      sortValue: (item: typeof enhancedMarketData[0]) => item.changePercent,
      render: (item: typeof enhancedMarketData[0]) => (
        <ChangeIndicator value={item.changePercent} showArrow />
      ),
    },
    {
      key: 'dayHigh',
      header: 'Day High',
      align: 'right' as const,
      sortable: true,
      sortValue: (item: typeof enhancedMarketData[0]) => item.dayHigh,
      render: (item: typeof enhancedMarketData[0]) => (
        <Text variant="small" className="text-muted">
          ₹{item.dayHigh.toFixed(2)}
        </Text>
      ),
    },
    {
      key: 'dayLow',
      header: 'Day Low',
      align: 'right' as const,
      sortable: true,
      sortValue: (item: typeof enhancedMarketData[0]) => item.dayLow,
      render: (item: typeof enhancedMarketData[0]) => (
        <Text variant="small" className="text-muted">
          ₹{item.dayLow.toFixed(2)}
        </Text>
      ),
    },
    {
      key: 'volume',
      header: 'Volume',
      align: 'right' as const,
      sortable: true,
      sortValue: (item: typeof enhancedMarketData[0]) => item.volume,
      render: (item: typeof enhancedMarketData[0]) => (
        <Text variant="small">
          {(item.volume / 1000).toFixed(1)}K
        </Text>
      ),
    },
    {
      key: 'overnightGap',
      header: 'Gap %',
      align: 'right' as const,
      sortable: true,
      sortValue: (item: typeof enhancedMarketData[0]) => item.overnightGap,
      render: (item: typeof enhancedMarketData[0]) => (
        <GapIndicator value={item.overnightGap} />
      ),
    },
    {
      key: 'range',
      header: '52W Range',
      align: 'right' as const,
      sortable: false,
      render: (item: typeof enhancedMarketData[0]) => (
        <RangeIndicator
          high={item.fiftyTwoWeekHigh}
          low={item.fiftyTwoWeekLow}
          current={item.ltp}
        />
      ),
    },
    {
      key: 'actions',
      header: 'Actions',
      align: 'center' as const,
      sortable: false,
      render: (item: typeof enhancedMarketData[0]) => (
        <Button
          variant="ghost"
          size="sm"
          iconLeft={<Trash2 size={16} />}
          onClick={() => handleRemoveSymbol(item.symbol)}
          className="text-error hover:text-error-dark"
        >
          Remove
        </Button>
      ),
    },
  ], []);

  /**
   * Handle symbol search
   */
  const handleSearch = async (query: string) => {
    setSearchQuery(query);
    if (query.length < 2) {
      setSearchResults([]);
      return;
    }

    setSearchLoading(true);
    const response = await apiClient.searchSymbols(query);
    setSearchLoading(false);

    if (response.success && response.data) {
      setSearchResults(
        response.data.map((symbol) => ({
          value: symbol,
          label: symbol,
        }))
      );
    }
  };

  /**
   * Handle add symbol to watchlist
   */
  const handleAddSymbol = async (option: SearchOption) => {
    setAddError(null);

    // Get first user broker
    if (!userBrokers || userBrokers.length === 0) {
      setAddError('No broker connection found. Please connect a broker first.');
      return;
    }

    const userBrokerId = userBrokers[0].userBrokerId;

    const response = await apiClient.batchAddWatchlistSymbols({
      userBrokerId,
      symbols: [option.value],
    });

    if (response.success) {
      setShowAddModal(false);
      setSearchQuery('');
      setSearchResults([]);
      // Refetch watchlists and market data
      refetchWatchlists();
      refetch();
    } else {
      setAddError(response.error || 'Failed to add symbol');
    }
  };

  /**
   * Handle remove symbol from watchlist
   */
  const handleRemoveSymbol = async (symbol: string) => {
    if (!confirm(`Remove ${symbol} from your watchlist?`)) {
      return;
    }

    // Find the watchlist item ID for this symbol
    const watchlistItem = watchlists?.find((w) => w.symbol === symbol);
    if (!watchlistItem || !watchlistItem.id) {
      alert('Could not find watchlist item to remove');
      return;
    }

    const response = await apiClient.batchDeleteWatchlistItems([Number(watchlistItem.id)]);

    if (response.success) {
      // Refetch watchlists and market data
      refetchWatchlists();
      refetch();
    } else {
      alert(response.error || 'Failed to remove symbol');
    }
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
            {/* Add Symbol Button */}
            <Button
              variant="primary"
              size="sm"
              iconLeft={<Plus size={16} />}
              onClick={() => setShowAddModal(true)}
            >
              Add Symbol
            </Button>
            {/* Filter Toggle */}
            <div className="flex items-center gap-2 border border-border rounded-lg p-1">
              <Button
                variant={filterMode === 'all' ? 'primary' : 'ghost'}
                size="sm"
                iconLeft={<List size={16} />}
                onClick={() => setFilterMode('all')}
              >
                All ({enhancedMarketData.length})
              </Button>
              <Button
                variant={filterMode === 'watchlist' ? 'primary' : 'ghost'}
                size="sm"
                iconLeft={<Filter size={16} />}
                onClick={() => setFilterMode('watchlist')}
              >
                Watchlist ({watchlistSymbols.size})
              </Button>
            </div>
            <Button
              variant="secondary"
              iconLeft={<RefreshCw size={20} />}
              onClick={refetch}
            >
              Refresh
            </Button>
          </div>
        </div>

        {/* Market Statistics */}
        {watchlists && watchlists.length > 0 && (
          <Card>
            <div className="p-6">
              <Text variant="label" className="mb-3">
                Watchlist Statistics
              </Text>
              <div className="flex flex-wrap gap-4">
                <div>
                  <Text variant="small" className="text-muted">Total Symbols</Text>
                  <Text variant="h4" className="font-semibold">{watchlists.length}</Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted">Enabled</Text>
                  <Text variant="h4" className="font-semibold">{watchlists.filter((w) => w.enabled).length}</Text>
                </div>
                <div>
                  <Text variant="small" className="text-muted">Market Data Available</Text>
                  <Text variant="h4" className="font-semibold">{marketData?.length || 0}</Text>
                </div>
              </div>
            </div>
          </Card>
        )}

        {/* Market Data Table */}
        {filteredMarketData && filteredMarketData.length > 0 ? (
          <DataTable
            columns={columns}
            data={filteredMarketData}
            keyExtractor={(item) => item.symbol}
            defaultSortKey="changePercent"
            defaultSortDirection="desc"
            emptyState={{
              icon: <Eye size={48} />,
              title: filterMode === 'watchlist' ? 'No Watchlist Symbols' : 'No Market Data',
              description: filterMode === 'watchlist'
                ? 'Add symbols to your watchlist to track them here.'
                : 'No market data available at the moment.',
            }}
          />
        ) : (
          <Card>
            <div className="p-12">
              <EmptyState
                icon={<Eye size={48} />}
                title={filterMode === 'watchlist' ? 'No Watchlist Symbols' : 'No Market Data'}
                description={filterMode === 'watchlist'
                  ? 'Add symbols to your watchlist to track them here.'
                  : 'No market data available at the moment.'}
              />
            </div>
          </Card>
        )}

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
                        variant={(data.change || 0) >= 0 ? 'profit' : 'loss'}
                        className="text-xs"
                      >
                        {(data.change || 0) >= 0 ? '+' : ''}
                        {(data.changePercent || 0).toFixed(2)}%
                      </Badge>
                    </div>

                    <div className="flex items-end justify-between">
                      <div>
                        <Text variant="h4" className="mb-1">
                          ₹{(data.ltp || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                        </Text>
                        <Text variant="small" className="text-muted">
                          Vol: {((data.volume || 0) / 1000).toFixed(1)}K
                        </Text>
                      </div>
                      <div className="text-right">
                        {(data.change || 0) >= 0 ? (
                          <TrendingUp size={24} className="text-profit" />
                        ) : (
                          <TrendingDown size={24} className="text-loss" />
                        )}
                      </div>
                    </div>

                    <div className="mt-3 pt-3 border-t border-border-light">
                      <div className="flex justify-between text-xs">
                        <Text variant="small" className="text-muted">
                          High: ₹{(data.dayHigh || 0).toFixed(2)}
                        </Text>
                        <Text variant="small" className="text-muted">
                          Low: ₹{(data.dayLow || 0).toFixed(2)}
                        </Text>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </Card>
        )}

        {/* Add Symbol Modal */}
        {showAddModal && (
          <Modal isOpen={showAddModal} onClose={() => setShowAddModal(false)} maxWidth="md">
            <div className="p-6 space-y-4">
              <Text variant="h3">Add Symbol to Watchlist</Text>
              <Text variant="body" className="text-muted">
                Search and add a symbol to track in your watchlist
              </Text>

              {addError && (
                <Alert variant="error">{addError}</Alert>
              )}

              <SearchBar
                placeholder="Search symbols (e.g., SBIN, RELIANCE)..."
                value={searchQuery}
                onSearch={handleSearch}
                options={searchResults}
                onSelect={handleAddSymbol}
                loading={searchLoading}
                fullWidth
              />

              <div className="flex justify-end gap-3 pt-4">
                <Button
                  variant="ghost"
                  onClick={() => {
                    setShowAddModal(false);
                    setSearchQuery('');
                    setSearchResults([]);
                    setAddError(null);
                  }}
                >
                  Cancel
                </Button>
              </div>
            </div>
          </Modal>
        )}

      </main>
    </div>
  );
}
