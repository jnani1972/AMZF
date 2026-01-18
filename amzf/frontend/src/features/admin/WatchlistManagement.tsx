/**
 * Watchlist Management Page
 * Admin page to manage watchlist symbols
 */

import { useState, useMemo } from 'react';
import { useAdminWatchlist, useAllUsers } from '../../hooks/useApi';
import { apiClient } from '../../lib/api';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Button } from '../../components/atoms/Button/Button';
import { Input } from '../../components/atoms/Input/Input';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import { RefreshCw, PlusCircle, Eye, Trash2, List, Users as UsersIcon, BarChart, ArrowUp, ArrowDown, ArrowUpDown, Edit2 } from 'lucide-react';
import { PageHeader } from '../../components/organisms/PageHeader/PageHeader';
import { SummaryCards } from '../../components/organisms/SummaryCards/SummaryCards';

type SortKey = 'symbol' | 'userId' | 'lotSize' | 'tickSize' | 'lastPrice' | 'enabled';
type SortDirection = 'asc' | 'desc' | null;

/**
 * Extract sortable value from watchlist item
 */
const getWatchlistSortValue = (item: any, key: SortKey): any => {
  const rawValue = item[key];

  if (key === 'enabled') {
    return item.enabled ? 1 : 0;
  }

  if (key === 'lotSize' || key === 'tickSize' || key === 'lastPrice') {
    return rawValue || 0;
  }

  if (typeof rawValue === 'string') {
    return rawValue.toLowerCase();
  }

  return rawValue || '';
};

/**
 * Compare values with direction
 */
const compareValues = (a: any, b: any, direction: SortDirection): number => {
  if (a < b) return direction === 'asc' ? -1 : 1;
  if (a > b) return direction === 'asc' ? 1 : -1;
  return 0;
};

/**
 * Watchlist management component
 */
export function WatchlistManagement() {
  const { data: watchlists, loading, error, refetch } = useAdminWatchlist();
  const { data: users } = useAllUsers();

  // Sorting state - default: alphabetical by symbol
  const [sortKey, setSortKey] = useState<SortKey>('symbol');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');

  const [showAddModal, setShowAddModal] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  // Add form state
  const [selectedUserId, setSelectedUserId] = useState('');
  const [symbol, setSymbol] = useState('');
  const [lotSize, setLotSize] = useState('');

  // Edit modal state
  const [showEditModal, setShowEditModal] = useState(false);
  const [editItem, setEditItem] = useState<any>(null);
  const [editError, setEditError] = useState<string | null>(null);

  // View modal state
  const [showViewModal, setShowViewModal] = useState(false);
  const [viewItem, setViewItem] = useState<any>(null);

  // Filter and sort watchlists
  const filteredWatchlists = useMemo(() => {
    // First filter
    const filtered = watchlists
      ? watchlists.filter(
        (w) =>
          w.symbol.toLowerCase().includes(searchQuery.toLowerCase()) ||
          w.userId?.toLowerCase().includes(searchQuery.toLowerCase())
      )
      : [];

    // Then sort
    if (!sortKey || !sortDirection) return filtered;

    return [...filtered].sort((a, b) => {
      const aVal = getWatchlistSortValue(a, sortKey);
      const bVal = getWatchlistSortValue(b, sortKey);
      return compareValues(aVal, bVal, sortDirection);
    });
  }, [watchlists, searchQuery, sortKey, sortDirection]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else if (sortDirection === 'desc') {
        setSortDirection(null);
        setSortKey('symbol');
      }
    } else {
      setSortKey(key);
      setSortDirection('asc');
    }
  };

  const getSortIcon = (key: SortKey) => {
    if (sortKey !== key) {
      return <ArrowUpDown size={14} className="sort-icon" />;
    }
    if (sortDirection === 'asc') {
      return <ArrowUp size={14} className="sort-icon sort-icon--active" />;
    }
    return <ArrowDown size={14} className="sort-icon sort-icon--active" />;
  };

  /**
   * Handle add watchlist item
   */
  const handleAdd = async () => {
    setAddError(null);

    if (!selectedUserId || !symbol) {
      setAddError('Please fill in all required fields');
      return;
    }

    const response = await apiClient.addWatchlistItem({
      userId: selectedUserId,
      symbol: symbol.toUpperCase(),
      lotSize: lotSize ? parseInt(lotSize) : undefined,
    });

    if (response.success) {
      setShowAddModal(false);
      setSymbol('');
      setLotSize('');
      setSelectedUserId('');
      refetch();
    } else {
      setAddError(response.error || 'Failed to add watchlist item');
    }
  };

  /**
   * Handle delete watchlist item
   */
  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this watchlist item?')) {
      return;
    }

    const response = await apiClient.deleteWatchlistItem(id);
    if (response.success) {
      refetch();
    }
  };

  /**
   * Open edit modal
   */
  const openEditModal = (item: any) => {
    setEditItem({ ...item });
    setEditError(null);
    setShowEditModal(true);
  };

  /**
   * Handle edit watchlist item
   */
  const handleEdit = async () => {
    setEditError(null);

    if (!editItem) return;

    const response = await apiClient.updateWatchlistItem(editItem.id, {
      lotSize: editItem.lotSize,
      tickSize: editItem.tickSize,
      enabled: editItem.enabled,
    });

    if (response.success) {
      setShowEditModal(false);
      refetch();
    } else {
      setEditError(response.error || 'Failed to update watchlist item');
    }
  };

  /**
   * Open view modal
   */
  const openViewModal = (item: any) => {
    setViewItem(item);
    setShowViewModal(true);
  };

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <main className="container mx-auto p-6">
        <div className="flex items-center justify-center py-12">
          <Spinner size="lg" variant="primary" />
        </div>
      </main>
    );
  }

  /**
   * Render error state
   */
  if (error) {
    return (
      <main className="container mx-auto p-6">
        <Alert variant="error">
          Failed to load watchlist: {error}
          <Button variant="secondary" size="sm" onClick={refetch} className="mt-3">
            Retry
          </Button>
        </Alert>
      </main>
    );
  }

  return (
    <>
      <main className="container mx-auto p-6 space-y-6">
        <PageHeader
          title="Watchlist"
          description="Manage watchlist symbols and configurations"
          actions={
            <>
              <Button variant="secondary" iconLeft={<RefreshCw size={20} />} onClick={refetch}>
                Refresh
              </Button>
              <Button
                variant="primary"
                iconLeft={<PlusCircle size={20} />}
                onClick={() => setShowAddModal(true)}
              >
                Add Symbol
              </Button>
            </>
          }
        />

        {/* Summary Cards */}
        {watchlists && watchlists.length > 0 && (
          <SummaryCards
            cards={[
              {
                icon: <List size={20} />,
                iconBgColor: 'bg-blue-100',
                iconColor: 'text-blue-600',
                label: 'Total Symbols',
                value: watchlists.length,
              },
              {
                icon: <UsersIcon size={20} />,
                iconBgColor: 'bg-green-100',
                iconColor: 'text-green-600',
                label: 'Active Users',
                value: new Set(watchlists.map((w) => w.userId)).size,
              },
              {
                icon: <Eye size={20} />,
                iconBgColor: 'bg-purple-100',
                iconColor: 'text-purple-600',
                label: 'Enabled',
                value: watchlists.filter((w) => w.enabled).length,
              },
              {
                icon: <BarChart size={20} />,
                iconBgColor: 'bg-orange-100',
                iconColor: 'text-orange-600',
                label: 'Avg Lot Size',
                value: Math.round(
                  watchlists.reduce((sum, w) => sum + (w.lotSize || 0), 0) / watchlists.length
                ),
              },
            ]}
          />
        )}

        {/* Search Bar */}
        <Card>
          <div className="p-6">
            <Input
              type="search"
              placeholder="Search by symbol or user..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              fullWidth
            />
          </div>
        </Card>

        {/* Watchlist Table */}
        <Card>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="sortable-header" onClick={() => handleSort('symbol')}>
                    <div className="table-header-content">
                      <span>Symbol</span>
                      {getSortIcon('symbol')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('userId')}>
                    <div className="table-header-content">
                      <span>User</span>
                      {getSortIcon('userId')}
                    </div>
                  </th>
                  <th className="sortable-header text-right" onClick={() => handleSort('lotSize')}>
                    <div className="table-header-content">
                      <span>Lot Size</span>
                      {getSortIcon('lotSize')}
                    </div>
                  </th>
                  <th className="sortable-header text-right" onClick={() => handleSort('tickSize')}>
                    <div className="table-header-content">
                      <span>Tick Size</span>
                      {getSortIcon('tickSize')}
                    </div>
                  </th>
                  <th className="sortable-header text-right" onClick={() => handleSort('lastPrice')}>
                    <div className="table-header-content">
                      <span>Last Price</span>
                      {getSortIcon('lastPrice')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('enabled')}>
                    <div className="table-header-content">
                      <span>Status</span>
                      {getSortIcon('enabled')}
                    </div>
                  </th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredWatchlists.length > 0 ? (
                  filteredWatchlists.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <div className="table-primary">{item.symbol}</div>
                      </td>
                      <td>
                        <div className="table-secondary">
                          {item.userId?.slice(0, 8)}...
                        </div>
                      </td>
                      <td className="text-right">
                        <div className="table-numeric">{item.lotSize || '-'}</div>
                      </td>
                      <td className="text-right">
                        <div className="table-currency">
                          {item.tickSize ? `₹${item.tickSize}` : '-'}
                        </div>
                      </td>
                      <td className="text-right">
                        <div className="table-currency">
                          {item.lastPrice ? `₹${item.lastPrice.toFixed(2)}` : '-'}
                        </div>
                      </td>
                      <td>
                        <Badge variant={item.enabled ? 'success' : 'default'}>
                          {item.enabled ? 'Enabled' : 'Disabled'}
                        </Badge>
                      </td>
                      <td className="text-right">
                        <div className="table-actions">
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Eye size={16} />}
                            onClick={() => openViewModal(item)}
                          >
                            <></>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Edit2 size={16} />}
                            onClick={() => openEditModal(item)}
                          >
                            <></>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Trash2 size={16} />}
                            onClick={() => handleDelete(item.id!)}
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
                    <td colSpan={7} className="table-empty">
                      <EmptyState
                        icon={<Eye size={48} />}
                        title={searchQuery ? 'No Symbols Found' : 'No Watchlist Items'}
                        description={searchQuery ? `No symbols match "${searchQuery}"` : 'Add symbols to the watchlist to get started'}
                        ctaText={!searchQuery ? 'Add Symbol' : undefined}
                        onCtaClick={!searchQuery ? () => setShowAddModal(true) : undefined}
                      />
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      </main>

      {/* Add Symbol Modal */}
      {showAddModal && (
        <div
          className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50"
          onClick={() => setShowAddModal(false)}
        >
          <div className="modal-form" onClick={(e) => e.stopPropagation()}>
            <Card>
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">Add Watchlist Symbol</Text>
                  <Button variant="ghost" size="sm" onClick={() => setShowAddModal(false)}>
                    Close
                  </Button>
                </div>

                {addError && (
                  <Alert variant="error" onDismiss={() => setAddError(null)}>
                    {addError}
                  </Alert>
                )}

                <div className="form-spacing">
                  <div>
                    <Text variant="label" className="mb-2">
                      User
                    </Text>
                    <select
                      className="input input--md w-full"
                      value={selectedUserId}
                      onChange={(e) => setSelectedUserId(e.target.value)}
                    >
                      <option key="placeholder" value="">Select user...</option>
                      {users?.map((user) => (
                        <option key={user.userId} value={user.userId}>
                          {user.email} ({user.displayName})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <Text variant="label" className="mb-2">
                      Symbol *
                    </Text>
                    <Input
                      type="text"
                      placeholder="e.g., NSE:SBIN-EQ"
                      value={symbol}
                      onChange={(e) => setSymbol(e.target.value)}
                      fullWidth
                      required
                    />
                  </div>

                  <div>
                    <Text variant="label" className="mb-2">
                      Lot Size (Optional)
                    </Text>
                    <Input
                      type="number"
                      placeholder="e.g., 1"
                      value={lotSize}
                      onChange={(e) => setLotSize(e.target.value)}
                      fullWidth
                    />
                  </div>
                </div>

                <div className="form-actions form-actions--stack-mobile">
                  <Button variant="secondary" fullWidth onClick={() => setShowAddModal(false)}>
                    Cancel
                  </Button>
                  <Button variant="primary" fullWidth onClick={handleAdd}>
                    Add Symbol
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        </div>
      )}

      {/* Edit Watchlist Item Modal */}
      {showEditModal && editItem && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowEditModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--md animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-primary">
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">Edit Watchlist Item</Text>
                  <Button variant="ghost" size="sm" onClick={() => setShowEditModal(false)}>
                    Close
                  </Button>
                </div>

                {editError && (
                  <Alert variant="error" onDismiss={() => setEditError(null)}>
                    {editError}
                  </Alert>
                )}

                <Alert variant="info">
                  <Text variant="small">
                    Symbol: <strong>{editItem.symbol}</strong>
                  </Text>
                </Alert>

                <div className="form-spacing">
                  <div>
                    <Text variant="label" className="mb-2">
                      Lot Size
                    </Text>
                    <Input
                      type="number"
                      value={editItem.lotSize || ''}
                      onChange={(e) => setEditItem({ ...editItem, lotSize: parseInt(e.target.value) || null })}
                      fullWidth
                    />
                  </div>

                  <div>
                    <Text variant="label" className="mb-2">
                      Tick Size
                    </Text>
                    <Input
                      type="number"
                      step="0.01"
                      value={editItem.tickSize || ''}
                      onChange={(e) => setEditItem({ ...editItem, tickSize: parseFloat(e.target.value) || null })}
                      fullWidth
                    />
                  </div>

                  <div>
                    <Text variant="label" className="mb-2">
                      Status
                    </Text>
                    <div className="flex gap-3">
                      <Button
                        variant={editItem.enabled ? 'primary' : 'secondary'}
                        size="md"
                        onClick={() => setEditItem({ ...editItem, enabled: true })}
                        className="flex-1"
                      >
                        Enabled
                      </Button>
                      <Button
                        variant={!editItem.enabled ? 'primary' : 'secondary'}
                        size="md"
                        onClick={() => setEditItem({ ...editItem, enabled: false })}
                        className="flex-1"
                      >
                        Disabled
                      </Button>
                    </div>
                  </div>
                </div>

                <div className="form-actions form-actions--stack-mobile">
                  <Button variant="secondary" fullWidth onClick={() => setShowEditModal(false)}>
                    Cancel
                  </Button>
                  <Button variant="primary" fullWidth onClick={handleEdit}>
                    Save Changes
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        </>
      )}

      {/* View Watchlist Item Modal */}
      {showViewModal && viewItem && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowViewModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--lg animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-green-500">
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">Watchlist Item Details</Text>
                  <Button variant="ghost" size="sm" onClick={() => setShowViewModal(false)}>
                    Close
                  </Button>
                </div>

                <div className="space-y-4">
                  <div className="grid-2 gap-4">
                    <div>
                      <Text variant="small" className="text-muted">Symbol</Text>
                      <Text variant="h4" className="mt-1">{viewItem.symbol}</Text>
                    </div>
                    <div>
                      <Text variant="small" className="text-muted">Status</Text>
                      <div className="mt-1">
                        <Badge variant={viewItem.enabled ? 'success' : 'default'}>
                          {viewItem.enabled ? 'Enabled' : 'Disabled'}
                        </Badge>
                      </div>
                    </div>
                  </div>

                  <div className="grid-2 gap-4">
                    <div>
                      <Text variant="small" className="text-muted">Lot Size</Text>
                      <Text variant="body" className="mt-1">{viewItem.lotSize || '-'}</Text>
                    </div>
                    <div>
                      <Text variant="small" className="text-muted">Tick Size</Text>
                      <Text variant="body" className="mt-1">
                        {viewItem.tickSize ? `₹${viewItem.tickSize}` : '-'}
                      </Text>
                    </div>
                  </div>

                  <div className="grid-2 gap-4">
                    <div>
                      <Text variant="small" className="text-muted">Last Price</Text>
                      <Text variant="h4" className="mt-1">
                        {viewItem.lastPrice ? `₹${viewItem.lastPrice.toFixed(2)}` : '-'}
                      </Text>
                    </div>
                    <div>
                      <Text variant="small" className="text-muted">User ID</Text>
                      <Text variant="small" className="mt-1 font-mono">
                        {viewItem.userId ? `${viewItem.userId.slice(0, 12)}...` : '-'}
                      </Text>
                    </div>
                  </div>

                  <div>
                    <Text variant="small" className="text-muted">Watchlist Item ID</Text>
                    <Text variant="small" className="mt-1 font-mono">{viewItem.id}</Text>
                  </div>
                </div>

                <div className="form-actions">
                  <Button variant="secondary" fullWidth onClick={() => setShowViewModal(false)}>
                    Close
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        </>
      )}
    </>
  );
}
