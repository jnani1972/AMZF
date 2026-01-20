/**
 * Broker Management Page
 * View and manage broker connections
 */

import { useState, useMemo } from 'react';
import { useAllUserBrokers, useAllUsers } from '../../hooks/useApi';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Button } from '../../components/atoms/Button/Button';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import { RefreshCw, Activity, PlusCircle, Trash2, CheckCircle, Database, ArrowUp, ArrowDown, ArrowUpDown, Edit2, Eye, Link } from 'lucide-react';
import { apiClient } from '../../lib/api';
import { API_ENDPOINTS } from '../../constants/apiEndpoints';
import { PageHeader } from '../../components/organisms/PageHeader/PageHeader';
import { SummaryCards } from '../../components/organisms/SummaryCards/SummaryCards';

type SortKey = 'displayName' | 'brokerName' | 'role' | 'status' | 'connected' | 'lastConnected';
type SortDirection = 'asc' | 'desc' | null;

/**
 * Extract sortable value from broker based on sort key
 */
const getSortValue = (broker: any, key: SortKey): any => {
  const rawValue = broker[key];

  if (key === 'lastConnected') {
    return broker.lastConnected ? new Date(broker.lastConnected).getTime() : 0;
  }

  if (key === 'connected') {
    return broker.connected ? 1 : 0;
  }

  if (key === 'status') {
    return broker.enabled && broker.status === 'ACTIVE' ? 1 : 0;
  }

  if (typeof rawValue === 'string') {
    return rawValue.toLowerCase();
  }

  return rawValue || '';
};

/**
 * Compare two values with sort direction
 */
const compareValues = (a: any, b: any, direction: SortDirection): number => {
  if (a < b) return direction === 'asc' ? -1 : 1;
  if (a > b) return direction === 'asc' ? 1 : -1;
  return 0;
};

/**
 * Broker management component
 */
export function BrokerManagement() {
  const { data: brokers, loading, error, refetch } = useAllUserBrokers();
  const { data: users } = useAllUsers();

  // Sorting state - default: latest connected first
  const [sortKey, setSortKey] = useState<SortKey>('lastConnected');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');

  // Sort brokers
  const sortedBrokers = useMemo(() => {
    if (!brokers || !sortKey || !sortDirection) return brokers || [];

    return [...brokers].sort((a, b) => {
      const aVal = getSortValue(a, sortKey);
      const bVal = getSortValue(b, sortKey);
      return compareValues(aVal, bVal, sortDirection);
    });
  }, [brokers, sortKey, sortDirection]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else if (sortDirection === 'desc') {
        setSortDirection(null);
        setSortKey('lastConnected');
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

  // Create modal state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [selectedBrokerId, setSelectedBrokerId] = useState('');
  const [selectedBrokerRole, setSelectedBrokerRole] = useState<'DATA' | 'EXEC'>('EXEC');
  const [createError, setCreateError] = useState<string | null>(null);

  // Edit modal state
  const [showEditModal, setShowEditModal] = useState(false);
  const [editBroker, setEditBroker] = useState<any | null>(null);
  const [editError, setEditError] = useState<string | null>(null);

  // Credentials state
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [accessToken, setAccessToken] = useState('');
  const [totpSecret, setTotpSecret] = useState('');
  // Additional fields
  const [brokerUserId, setBrokerUserId] = useState('');
  const [postbackUrl, setPostbackUrl] = useState('');

  // View modal state
  const [showViewModal, setShowViewModal] = useState(false);
  const [viewBroker, setViewBroker] = useState<any | null>(null);

  /**
   * Handle create user broker
   */
  const handleCreate = async () => {
    setCreateError(null);

    if (!selectedUserId || !selectedBrokerId || !selectedBrokerRole) {
      setCreateError('Please fill in all required fields');
      return;
    }

    const response = await apiClient.createUserBroker({
      userId: selectedUserId,
      brokerId: selectedBrokerId,
      brokerRole: selectedBrokerRole,
    });

    if (response.success) {
      setShowCreateModal(false);
      setSelectedUserId('');
      setSelectedBrokerId('');
      setSelectedBrokerRole('DATA');
      refetch();
    } else {
      setCreateError(response.error || 'Failed to create broker connection');
    }
  };



  /**
   * Handle delete broker
   */
  const handleDelete = async (userBrokerId: string) => {
    if (!confirm('Are you sure you want to delete this broker connection?')) {
      return;
    }

    const response = await apiClient.deleteUserBroker(userBrokerId);
    if (response.success) {
      refetch();
    }
  };

  /**
   * Open edit modal
   */
  const openEditModal = (broker: any) => {
    setEditBroker({ ...broker });

    // Unpack credentials if available
    const creds = broker.credentials || {};
    setApiKey(creds.apiKey || '');
    setApiSecret(creds.apiSecret || '');
    setAccessToken(creds.accessToken || '');
    setTotpSecret(creds.totpSecret || '');
    setBrokerUserId(creds.brokerUserId || '');
    setPostbackUrl(creds.postbackUrl || '');

    setEditError(null);
    setShowEditModal(true);
  };

  /**
   * Handle edit broker
   */
  const handleEdit = async () => {
    setEditError(null);

    if (!editBroker) return;

    // Pack credentials
    const credentials: any = {};
    if (apiKey) credentials.apiKey = apiKey;
    if (apiSecret) credentials.apiSecret = apiSecret;
    if (accessToken) credentials.accessToken = accessToken;
    if (totpSecret) credentials.totpSecret = totpSecret;
    if (brokerUserId) credentials.brokerUserId = brokerUserId;
    if (postbackUrl) credentials.postbackUrl = postbackUrl;

    const response = await apiClient.updateUserBroker(editBroker.userBrokerId, {
      role: editBroker.role,
      enabled: editBroker.enabled,
      credentials, // Send updated credentials
    });

    if (response.success) {
      setShowEditModal(false);
      refetch();
    } else {
      setEditError(response.error || 'Failed to update broker');
    }
  };

  /**
   * Open view modal
   */
  const openViewModal = (broker: any) => {
    setViewBroker(broker);
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
          Failed to load brokers: {error}
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
          title="Brokers"
          description="Manage broker connections and health"
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
                Add Broker
              </Button>
            </>
          }
        />

        {/* Summary Cards */}
        {brokers && brokers.length > 0 && (
          <SummaryCards
            cards={[
              {
                icon: <Activity size={20} />,
                iconBgColor: 'bg-blue-100',
                iconColor: 'text-blue-600',
                label: 'Total Brokers',
                value: brokers.length,
              },
              {
                icon: <CheckCircle size={20} />,
                iconBgColor: 'bg-green-100',
                iconColor: 'text-green-600',
                label: 'Active Brokers',
                value: brokers.filter((b) => b.enabled && b.status === 'ACTIVE').length,
                subtitle: `(${brokers.length > 0 ? Math.round((brokers.filter((b) => b.enabled && b.status === 'ACTIVE').length / brokers.length) * 100) : 0}%)`,
              },
              {
                icon: <CheckCircle size={20} />,
                iconBgColor: 'bg-purple-100',
                iconColor: 'text-purple-600',
                label: 'Connected',
                value: brokers.filter((b) => b.connected).length,
              },
              {
                icon: <Database size={20} />,
                iconBgColor: 'bg-orange-100',
                iconColor: 'text-orange-600',
                label: 'DATA / EXEC',
                value: `${brokers.filter((b) => b.role === 'DATA').length} / ${brokers.filter((b) => b.role === 'EXEC').length}`,
              },
            ]}
          />
        )}

        {/* Brokers Table */}
        <Card>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="sortable-header" onClick={() => handleSort('displayName')}>
                    <div className="table-header-content">
                      <span>User</span>
                      {getSortIcon('displayName')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('brokerName')}>
                    <div className="table-header-content">
                      <span>Broker</span>
                      {getSortIcon('brokerName')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('role')}>
                    <div className="table-header-content">
                      <span>Role</span>
                      {getSortIcon('role')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('status')}>
                    <div className="table-header-content">
                      <span>Status</span>
                      {getSortIcon('status')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('connected')}>
                    <div className="table-header-content">
                      <span>Health</span>
                      {getSortIcon('connected')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('lastConnected')}>
                    <div className="table-header-content">
                      <span>Last Check</span>
                      {getSortIcon('lastConnected')}
                    </div>
                  </th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {sortedBrokers && sortedBrokers.length > 0 ? (
                  sortedBrokers.map((broker) => (
                    <tr key={broker.userBrokerId}>
                      <td>
                        <div className="table-secondary">{broker.displayName || broker.userId}</div>
                      </td>
                      <td>
                        <div className="table-primary">{broker.brokerName || 'Unknown'}</div>
                      </td>
                      <td>
                        <Badge variant={broker.role === 'EXEC' ? 'primary' : 'info'}>
                          {broker.role}
                        </Badge>
                      </td>
                      <td>
                        <Badge variant={broker.enabled && broker.status === 'ACTIVE' ? 'success' : 'default'}>
                          {broker.enabled && broker.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td>
                        <Badge variant={broker.connected ? 'success' : 'warning'}>
                          {broker.connected ? 'Connected' : 'Disconnected'}
                        </Badge>
                      </td>
                      <td>
                        <div className="table-date">
                          {broker.lastConnected
                            ? new Date(broker.lastConnected).toLocaleString()
                            : 'Never'}
                        </div>
                      </td>
                      <td className="text-right">
                        <div className="table-actions">
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Eye size={16} />}
                            onClick={() => openViewModal(broker)}
                          >
                            <></>
                          </Button>
                          {!broker.connected && (
                            <Button
                              variant="ghost"
                              size="sm"
                              iconLeft={<Link size={16} />}
                              className="text-primary"
                              onClick={async () => {
                                try {
                                  // For now assume all disconnected brokers can try OAuth if they have an ID
                                  // RESPONSE WRAPPER FIX: apiClient returns { success: true, data: { ... } }
                                  // The backend returns { success: true, oauthUrl: "..." } inside data.
                                  const res = await apiClient.get<any>(`${API_ENDPOINTS.ADMIN.BROKERS}/${broker.userBrokerId}/oauth-url`);

                                  if (res.success && res.data && res.data.oauthUrl) {
                                    console.log("Redirecting to OAuth URL:", res.data.oauthUrl);
                                    window.location.href = res.data.oauthUrl;
                                  } else {
                                    console.error("Failed to get OAuth URL", res);
                                    alert(`Failed to initiate connection: ${res.error || 'Unknown error'}`);
                                  }
                                } catch (e: any) {
                                  console.error("OAuth Error", e);
                                  alert(`Connection error: ${e.message || 'Unknown error'}`);
                                }
                              }}
                            >
                              <></>
                            </Button>
                          )}
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Edit2 size={16} />}
                            onClick={() => openEditModal(broker)}
                          >
                            <></>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Trash2 size={16} />}
                            onClick={() => handleDelete(broker.userBrokerId)}
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
      </main >

      {/* Create Broker Modal */}
      {
        showCreateModal && (
          <>
            <div
              className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
              onClick={() => setShowCreateModal(false)}
            />
            <div className="modal-slide-right modal-slide-right--md animate-slide-in-right">
              <Card className="shadow-2xl border-2 border-blue-500">
                <div className="p-6 space-y-6">
                  <div className="flex items-center justify-between">
                    <Text variant="h3">Add Broker Connection</Text>
                    <Button variant="ghost" size="sm" onClick={() => setShowCreateModal(false)}>
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
                        Broker *
                      </Text>
                      <select
                        className="input input--md w-full"
                        value={selectedBrokerId}
                        onChange={(e) => setSelectedBrokerId(e.target.value)}
                      >
                        <option key="placeholder" value="">
                          Select broker...
                        </option>
                        <option key="FYERS" value="FYERS">FYERS</option>
                        <option key="ZERODHA" value="ZERODHA">Zerodha</option>
                        <option key="UPSTOX" value="UPSTOX">Upstox</option>
                        <option key="ANGEL" value="ANGEL">Angel One</option>
                      </select>
                    </div>

                    <div>
                      <Text variant="label" className="mb-2">
                        Broker Role *
                      </Text>
                      <div className="flex gap-3">
                        <Button
                          variant={selectedBrokerRole === 'DATA' ? 'primary' : 'secondary'}
                          size="md"
                          onClick={() => setSelectedBrokerRole('DATA')}
                          className="flex-1"
                        >
                          DATA
                        </Button>
                        <Button
                          variant={selectedBrokerRole === 'EXEC' ? 'primary' : 'secondary'}
                          size="md"
                          onClick={() => setSelectedBrokerRole('EXEC')}
                          className="flex-1"
                        >
                          EXEC
                        </Button>
                      </div>
                      <Text variant="small" className="text-muted mt-2">
                        DATA: Market data only • EXEC: Order execution
                      </Text>
                    </div>
                  </div>

                  <div className="form-actions form-actions--stack-mobile">
                    <Button variant="secondary" fullWidth onClick={() => setShowCreateModal(false)}>
                      Cancel
                    </Button>
                    <Button variant="primary" fullWidth onClick={handleCreate}>
                      Add Broker
                    </Button>
                  </div>
                </div>
              </Card>
            </div>
          </>
        )
      }

      {/* Edit Broker Modal */}
      {
        showEditModal && editBroker && (
          <>
            <div
              className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
              onClick={() => setShowEditModal(false)}
            />
            <div className="modal-slide-right modal-slide-right--md animate-slide-in-right">
              <Card className="shadow-2xl border-2 border-primary">
                <div className="p-6 space-y-6">
                  <div className="flex items-center justify-between">
                    <Text variant="h3">Edit Broker Connection</Text>
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
                      Broker: <strong>{editBroker.brokerName}</strong> • User: <strong>{editBroker.displayName || editBroker.userId}</strong>
                    </Text>
                  </Alert>

                  <div className="form-spacing">
                    <div>
                      <Text variant="label" className="mb-2">
                        Broker Role
                      </Text>
                      <div className="flex gap-3">
                        <Button
                          variant={editBroker.role === 'DATA' ? 'primary' : 'secondary'}
                          size="md"
                          onClick={() => setEditBroker({ ...editBroker, role: 'DATA' })}
                          className="flex-1"
                        >
                          DATA
                        </Button>
                        <Button
                          variant={editBroker.role === 'EXEC' ? 'primary' : 'secondary'}
                          size="md"
                          onClick={() => setEditBroker({ ...editBroker, role: 'EXEC' })}
                          className="flex-1"
                        >
                          EXEC
                        </Button>
                      </div>
                    </div>

                    <div>
                      <Text variant="label" className="mb-2">
                        Status
                      </Text>
                      <div className="flex gap-3">
                        <Button
                          variant={editBroker.enabled ? 'primary' : 'secondary'}
                          size="md"
                          onClick={() => setEditBroker({ ...editBroker, enabled: true })}
                          className="flex-1"
                        >
                          Enabled
                        </Button>
                        <Button
                          variant={!editBroker.enabled ? 'primary' : 'secondary'}
                          size="md"
                          onClick={() => setEditBroker({ ...editBroker, enabled: false })}
                          className="flex-1"
                        >
                          Disabled
                        </Button>
                      </div>
                    </div>

                    <div>
                      <Text variant="label" className="mb-2">
                        Credentials
                      </Text>
                      <div className="space-y-3 p-4 bg-muted/30 rounded-md border border-border">
                        {/* API Key */}
                        <div>
                          <label className="text-xs font-medium text-muted mb-1 block">API Key</label>
                          <input
                            type="text"
                            className="input input--sm w-full font-mono"
                            value={apiKey}
                            onChange={(e) => setApiKey(e.target.value)}
                            placeholder="Enter API Key"
                          />
                        </div>

                        {/* API Secret */}
                        <div>
                          <label className="text-xs font-medium text-muted mb-1 block">API Secret / Cloud Key</label>
                          <input
                            type="password"
                            className="input input--sm w-full font-mono"
                            value={apiSecret}
                            onChange={(e) => setApiSecret(e.target.value)}
                            placeholder="Enter API Secret"
                          />
                        </div>

                        {/* Client ID / User ID */}
                        <div>
                          <label className="text-xs font-medium text-muted mb-1 block">Broker Client ID / User ID</label>
                          <input
                            type="text"
                            className="input input--sm w-full font-mono"
                            value={brokerUserId}
                            onChange={(e) => setBrokerUserId(e.target.value)}
                            placeholder="e.g. DA1234 (Zerodha)"
                          />
                        </div>

                        {/* Postback URL */}
                        <div>
                          <div className="flex justify-between">
                            <label className="text-xs font-medium text-muted mb-1 block">Postback / Redirect URL</label>
                            <span className="text-[10px] text-muted-foreground">(Optional)</span>
                          </div>
                          <input
                            type="text"
                            className="input input--sm w-full font-mono"
                            value={postbackUrl}
                            onChange={(e) => setPostbackUrl(e.target.value)}
                            placeholder="https://your-domain.com/api/callback"
                          />
                        </div>

                        {/* Access Token (Optional) */}
                        <div>
                          <div className="flex justify-between">
                            <label className="text-xs font-medium text-muted mb-1 block">Access Token</label>
                            <span className="text-[10px] text-muted-foreground">(Optional if using OAuth)</span>
                          </div>
                          <input
                            type="password"
                            className="input input--sm w-full font-mono"
                            value={accessToken}
                            onChange={(e) => setAccessToken(e.target.value)}
                            placeholder="Enter Access Token"
                          />
                        </div>

                        {/* TOTP Secret (Optional) */}
                        <div>
                          <label className="text-xs font-medium text-muted mb-1 block">TOTP Secret</label>
                          <input
                            type="password"
                            className="input input--sm w-full font-mono"
                            value={totpSecret}
                            onChange={(e) => setTotpSecret(e.target.value)}
                            placeholder="Enter TOTP Secret"
                          />
                        </div>
                      </div>
                    </div>

                    <div className="form-actions space-x-3">
                      <Button variant="secondary" onClick={() => setShowEditModal(false)}>
                        Cancel
                      </Button>
                      <Button variant="primary" onClick={handleEdit}>
                        Save Changes
                      </Button>
                    </div>
                  </div>
                </div>
              </Card>
            </div>
          </>
        )
      }

      {/* View Broker Modal */}
      {
        showViewModal && viewBroker && (
          <>
            <div
              className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
              onClick={() => setShowViewModal(false)}
            />
            <div className="modal-slide-right modal-slide-right--lg animate-slide-in-right">
              <Card className="shadow-2xl border-2 border-green-500">
                <div className="p-6 space-y-6">
                  <div className="flex items-center justify-between">
                    <Text variant="h3">Broker Details</Text>
                    <Button variant="ghost" size="sm" onClick={() => setShowViewModal(false)}>
                      Close
                    </Button>
                  </div>

                  <div className="space-y-4">
                    <div className="grid-2 gap-4">
                      <div>
                        <Text variant="small" className="text-muted">Broker Name</Text>
                        <Text variant="h4" className="mt-1">{viewBroker.brokerName || 'Unknown'}</Text>
                      </div>
                      <div>
                        <Text variant="small" className="text-muted">User</Text>
                        <Text variant="body" className="mt-1">{viewBroker.displayName || viewBroker.userId}</Text>
                      </div>
                    </div>

                    <div className="grid-2 gap-4">
                      <div>
                        <Text variant="small" className="text-muted">Broker Role</Text>
                        <div className="mt-1">
                          <Badge variant={viewBroker.role === 'EXEC' ? 'primary' : 'info'}>
                            {viewBroker.role}
                          </Badge>
                        </div>
                      </div>
                      <div>
                        <Text variant="small" className="text-muted">Status</Text>
                        <div className="mt-1">
                          <Badge variant={viewBroker.enabled && viewBroker.status === 'ACTIVE' ? 'success' : 'default'}>
                            {viewBroker.enabled && viewBroker.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                          </Badge>
                        </div>
                      </div>
                    </div>

                    <div className="grid-2 gap-4">
                      <div>
                        <Text variant="small" className="text-muted">Connection Health</Text>
                        <div className="mt-1">
                          <Badge variant={viewBroker.connected ? 'success' : 'warning'}>
                            {viewBroker.connected ? 'Connected' : 'Disconnected'}
                          </Badge>
                        </div>
                      </div>
                      <div>
                        <Text variant="small" className="text-muted">Last Connected</Text>
                        <Text variant="body" className="mt-1">
                          {viewBroker.lastConnected
                            ? new Date(viewBroker.lastConnected).toLocaleString()
                            : 'Never'}
                        </Text>
                      </div>
                    </div>

                    <div>
                      <Text variant="small" className="text-muted">Broker ID</Text>
                      <Text variant="small" className="mt-1 font-mono">{viewBroker.userBrokerId}</Text>
                    </div>

                    <div>
                      <Text variant="small" className="text-muted">User ID</Text>
                      <Text variant="small" className="mt-1 font-mono">{viewBroker.userId}</Text>
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
        )
      }
    </>
  );
}
