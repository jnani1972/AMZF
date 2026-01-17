/**
 * Portfolio Management Page
 * Admin page to manage all user portfolios
 */

import { useState, useMemo } from 'react';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Button } from '../../components/atoms/Button/Button';
import { Input } from '../../components/atoms/Input/Input';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import { RefreshCw, PlusCircle, Briefcase, Edit, Trash2, DollarSign, TrendingUp, ArrowUp, ArrowDown, ArrowUpDown, Eye } from 'lucide-react';
import { useAllPortfolios, useAllUsers } from '../../hooks/useApi';
import { apiClient } from '../../lib/api';
import { PageHeader } from '../../components/organisms/PageHeader/PageHeader';
import { SummaryCards } from '../../components/organisms/SummaryCards/SummaryCards';

type SortKey = 'name' | 'userId' | 'capital' | 'availableCapital' | 'totalValue' | 'totalPnl' | 'createdAt';
type SortDirection = 'asc' | 'desc' | null;

/**
 * Portfolio management component
 */
export function PortfolioManagement() {
  const { data: portfolios, loading, error, refetch } = useAllPortfolios();
  const { data: users } = useAllUsers();

  // Sorting state - default: latest created first
  const [sortKey, setSortKey] = useState<SortKey>('createdAt');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);

  // Create form state
  const [selectedUserId, setSelectedUserId] = useState('');
  const [portfolioName, setPortfolioName] = useState('');
  const [initialCapital, setInitialCapital] = useState('');

  // Edit modal state
  const [showEditModal, setShowEditModal] = useState(false);
  const [editPortfolio, setEditPortfolio] = useState<any>(null);
  const [editError, setEditError] = useState<string | null>(null);

  // View modal state
  const [showViewModal, setShowViewModal] = useState(false);
  const [viewPortfolio, setViewPortfolio] = useState<any>(null);

  // Filter and sort portfolios
  const filteredPortfolios = useMemo(() => {
    // First filter
    const filtered = portfolios
      ? portfolios.filter((p) =>
          p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          p.userId.toLowerCase().includes(searchQuery.toLowerCase())
        )
      : [];

    // Then sort
    if (!sortKey || !sortDirection) return filtered;

    return [...filtered].sort((a, b) => {
      let aVal: any = a[sortKey];
      let bVal: any = b[sortKey];

      // Handle dates
      if (sortKey === 'createdAt') {
        aVal = new Date(aVal).getTime();
        bVal = new Date(bVal).getTime();
      }

      // Handle strings
      if (typeof aVal === 'string') {
        aVal = aVal.toLowerCase();
        bVal = bVal.toLowerCase();
      }

      let comparison = 0;
      if (aVal < bVal) comparison = -1;
      if (aVal > bVal) comparison = 1;

      return sortDirection === 'asc' ? comparison : -comparison;
    });
  }, [portfolios, searchQuery, sortKey, sortDirection]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else if (sortDirection === 'desc') {
        setSortDirection(null);
        setSortKey('createdAt');
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
   * Handle create portfolio
   */
  const handleCreate = async () => {
    setCreateError(null);

    if (!selectedUserId || !portfolioName || !initialCapital) {
      setCreateError('Please fill in all required fields');
      return;
    }

    const capital = parseFloat(initialCapital);
    if (isNaN(capital) || capital <= 0) {
      setCreateError('Please enter a valid capital amount');
      return;
    }

    const response = await apiClient.createPortfolio({
      userId: selectedUserId,
      name: portfolioName,
      totalCapital: capital,
    });

    if (response.success) {
      setShowCreateModal(false);
      setSelectedUserId('');
      setPortfolioName('');
      setInitialCapital('');
      refetch();
    } else {
      setCreateError(response.error || 'Failed to create portfolio');
    }
  };

  /**
   * Open edit modal
   */
  const openEditModal = (portfolio: any) => {
    setEditPortfolio({ ...portfolio });
    setEditError(null);
    setShowEditModal(true);
  };

  /**
   * Handle edit portfolio
   */
  const handleEdit = async () => {
    setEditError(null);

    if (!editPortfolio || !editPortfolio.name) {
      setEditError('Portfolio name is required');
      return;
    }

    const response = await apiClient.updatePortfolio(editPortfolio.id, {
      name: editPortfolio.name,
      capital: editPortfolio.capital,
    });

    if (response.success) {
      setShowEditModal(false);
      refetch();
    } else {
      setEditError(response.error || 'Failed to update portfolio');
    }
  };

  /**
   * Handle delete portfolio
   */
  const handleDelete = async (portfolioId: string, portfolioName: string) => {
    if (!confirm(`Are you sure you want to delete portfolio "${portfolioName}"? This action cannot be undone.`)) {
      return;
    }

    const response = await apiClient.deletePortfolio(portfolioId);
    if (response.success) {
      refetch();
    } else {
      alert(response.error || 'Failed to delete portfolio');
    }
  };

  /**
   * Open view modal
   */
  const openViewModal = (portfolio: any) => {
    setViewPortfolio(portfolio);
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
          Failed to load portfolios: {error}
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
          title="Portfolios"
          description="Manage user portfolios and capital allocation"
          actions={
            <>
              <Button variant="secondary" iconLeft={<RefreshCw size={20} />} onClick={refetch}>
                Refresh
              </Button>
              <Button
                variant="primary"
                iconLeft={<PlusCircle size={20} />}
                onClick={() => setShowCreateModal(true)}
              >
                Create Portfolio
              </Button>
            </>
          }
        />

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
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="sortable-header" onClick={() => handleSort('name')}>
                    <div className="table-header-content">
                      <span>Portfolio</span>
                      {getSortIcon('name')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('userId')}>
                    <div className="table-header-content">
                      <span>User</span>
                      {getSortIcon('userId')}
                    </div>
                  </th>
                  <th className="sortable-header text-right" onClick={() => handleSort('capital')}>
                    <div className="table-header-content">
                      <span>Capital</span>
                      {getSortIcon('capital')}
                    </div>
                  </th>
                  <th className="sortable-header text-right" onClick={() => handleSort('availableCapital')}>
                    <div className="table-header-content">
                      <span>Available</span>
                      {getSortIcon('availableCapital')}
                    </div>
                  </th>
                  <th className="sortable-header text-right" onClick={() => handleSort('totalValue')}>
                    <div className="table-header-content">
                      <span>Total Value</span>
                      {getSortIcon('totalValue')}
                    </div>
                  </th>
                  <th className="sortable-header text-right" onClick={() => handleSort('totalPnl')}>
                    <div className="table-header-content">
                      <span>P&L</span>
                      {getSortIcon('totalPnl')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('createdAt')}>
                    <div className="table-header-content">
                      <span>Created</span>
                      {getSortIcon('createdAt')}
                    </div>
                  </th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredPortfolios.length > 0 ? (
                  filteredPortfolios.map((portfolio) => (
                    <tr key={portfolio.id}>
                      <td>
                        <div className="table-primary">{portfolio.name}</div>
                        <div className="table-secondary">ID: {portfolio.id.substring(0, 12)}...</div>
                      </td>
                      <td>
                        <div className="table-secondary">{portfolio.userId}</div>
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
                            className={`table-currency ${
                              portfolio.totalPnl >= 0 ? 'text-profit' : 'text-loss'
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
                      <td className="text-right">
                        <div className="table-actions">
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Eye size={16} />}
                            onClick={() => openViewModal(portfolio)}
                          >
                            <></>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Edit size={16} />}
                            onClick={() => openEditModal(portfolio)}
                          >
                            <></>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Trash2 size={16} />}
                            onClick={() => handleDelete(portfolio.id, portfolio.name)}
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
                    <td colSpan={8} className="table-empty">
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

        {/* Summary Cards */}
        {portfolios && portfolios.length > 0 && (
          <SummaryCards
            cards={[
              {
                icon: <Briefcase size={20} />,
                iconBgColor: 'bg-blue-100',
                iconColor: 'text-blue-600',
                label: 'Total Portfolios',
                value: portfolios.length,
              },
              {
                icon: <DollarSign size={20} />,
                iconBgColor: 'bg-purple-100',
                iconColor: 'text-purple-600',
                label: 'Total AUM',
                value: `₹${(portfolios.reduce((sum, p) => sum + p.totalValue, 0) / 100000).toFixed(1)}L`,
              },
              {
                icon: <TrendingUp size={20} />,
                iconBgColor: 'bg-green-100',
                iconColor: 'text-green-600',
                label: 'Total Capital',
                value: `₹${(portfolios.reduce((sum, p) => sum + p.capital, 0) / 100000).toFixed(1)}L`,
              },
              {
                icon: <TrendingUp size={20} />,
                iconBgColor: portfolios.reduce((sum, p) => sum + p.totalPnl, 0) >= 0 ? 'bg-green-100' : 'bg-red-100',
                iconColor: portfolios.reduce((sum, p) => sum + p.totalPnl, 0) >= 0 ? 'text-green-600' : 'text-red-600',
                label: 'Total P&L',
                value: `${portfolios.reduce((sum, p) => sum + p.totalPnl, 0) >= 0 ? '+' : ''}₹${(Math.abs(portfolios.reduce((sum, p) => sum + p.totalPnl, 0)) / 1000).toFixed(1)}K`,
                valueColor: portfolios.reduce((sum, p) => sum + p.totalPnl, 0) >= 0 ? 'text-green-600' : 'text-red-600',
              },
            ]}
          />
        )}
      </main>

      {/* Create Portfolio Modal */}
      {showCreateModal && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowCreateModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--md animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-blue-500">
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

                {createError && (
                  <Alert variant="error" onDismiss={() => setCreateError(null)}>
                    {createError}
                  </Alert>
                )}

                <div className="form-spacing">
                  <div>
                    <Text variant="label" className="mb-2">
                      User *
                    </Text>
                    <select
                      className="input input--md w-full"
                      value={selectedUserId}
                      onChange={(e) => setSelectedUserId(e.target.value)}
                    >
                      <option key="placeholder" value="">
                        Select user...
                      </option>
                      {users?.map((user) => (
                        <option key={user.userId} value={user.userId}>
                          {user.email} ({user.displayName})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <Text variant="label" className="mb-2">
                      Portfolio Name *
                    </Text>
                    <Input
                      type="text"
                      placeholder="e.g., Main Trading Account"
                      value={portfolioName}
                      onChange={(e) => setPortfolioName(e.target.value)}
                      fullWidth
                      required
                    />
                  </div>
                  <div>
                    <Text variant="label" className="mb-2">
                      Initial Capital *
                    </Text>
                    <Input
                      type="number"
                      placeholder="100000"
                      value={initialCapital}
                      onChange={(e) => setInitialCapital(e.target.value)}
                      fullWidth
                      required
                    />
                  </div>
                </div>

                <div className="form-actions form-actions--stack-mobile">
                  <Button variant="secondary" fullWidth onClick={() => setShowCreateModal(false)}>
                    Cancel
                  </Button>
                  <Button variant="primary" fullWidth onClick={handleCreate}>
                    Create Portfolio
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        </>
      )}

      {/* Edit Portfolio Modal */}
      {showEditModal && editPortfolio && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowEditModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--md animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-primary">
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">Edit Portfolio</Text>
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
                    Portfolio ID: <strong>{editPortfolio.id.substring(0, 12)}...</strong>
                  </Text>
                </Alert>

                <div className="form-spacing">
                  <div>
                    <Text variant="label" className="mb-2">
                      Portfolio Name *
                    </Text>
                    <Input
                      type="text"
                      placeholder="e.g., Main Trading Account"
                      value={editPortfolio.name}
                      onChange={(e) => setEditPortfolio({ ...editPortfolio, name: e.target.value })}
                      fullWidth
                      required
                    />
                  </div>
                  <div>
                    <Text variant="label" className="mb-2">
                      Capital
                    </Text>
                    <Input
                      type="number"
                      value={editPortfolio.capital}
                      onChange={(e) => setEditPortfolio({ ...editPortfolio, capital: parseFloat(e.target.value) })}
                      fullWidth
                    />
                    <Text variant="small" className="text-muted mt-1">
                      Current: ₹{editPortfolio.capital.toLocaleString('en-IN')}
                    </Text>
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

      {/* View Portfolio Modal */}
      {showViewModal && viewPortfolio && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowViewModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--lg animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-green-500">
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">Portfolio Details</Text>
                  <Button variant="ghost" size="sm" onClick={() => setShowViewModal(false)}>
                    Close
                  </Button>
                </div>

                <div className="space-y-4">
                  <div className="grid-2 gap-4">
                    <div>
                      <Text variant="small" className="text-muted">Portfolio Name</Text>
                      <Text variant="h4" className="mt-1">{viewPortfolio.name}</Text>
                    </div>
                    <div>
                      <Text variant="small" className="text-muted">User ID</Text>
                      <Text variant="body" className="mt-1">{viewPortfolio.userId}</Text>
                    </div>
                  </div>

                  <div className="grid-2 gap-4">
                    <div>
                      <Text variant="small" className="text-muted">Total Capital</Text>
                      <Text variant="h4" className="mt-1">₹{viewPortfolio.capital.toLocaleString('en-IN')}</Text>
                    </div>
                    <div>
                      <Text variant="small" className="text-muted">Available Capital</Text>
                      <Text variant="h4" className="mt-1">₹{viewPortfolio.availableCapital.toLocaleString('en-IN')}</Text>
                    </div>
                  </div>

                  <div className="grid-2 gap-4">
                    <div>
                      <Text variant="small" className="text-muted">Total Value</Text>
                      <Text variant="h4" className="mt-1">₹{viewPortfolio.totalValue.toLocaleString('en-IN')}</Text>
                    </div>
                    <div>
                      <Text variant="small" className="text-muted">Total P&L</Text>
                      <div className="mt-1 flex items-center gap-2">
                        <Text variant="h4" className={viewPortfolio.totalPnl >= 0 ? 'text-profit' : 'text-loss'}>
                          {viewPortfolio.totalPnl >= 0 ? '+' : ''}₹{viewPortfolio.totalPnl.toLocaleString('en-IN')}
                        </Text>
                        <Badge variant={viewPortfolio.totalPnl >= 0 ? 'profit' : 'loss'}>
                          {viewPortfolio.totalPnl >= 0 ? '+' : ''}{viewPortfolio.totalPnlPercent.toFixed(2)}%
                        </Badge>
                      </div>
                    </div>
                  </div>

                  <div>
                    <Text variant="small" className="text-muted">Portfolio ID</Text>
                    <Text variant="small" className="mt-1 font-mono">{viewPortfolio.id}</Text>
                  </div>

                  <div>
                    <Text variant="small" className="text-muted">Created</Text>
                    <Text variant="body" className="mt-1">
                      {new Date(viewPortfolio.createdAt).toLocaleString()}
                    </Text>
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
