/**
 * User Management Page
 * View and manage system users
 */

import { useState, useMemo } from 'react';
import { useAllUsers } from '../../hooks/useApi';
import { apiClient } from '../../lib/api';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Button } from '../../components/atoms/Button/Button';
import { Input } from '../../components/atoms/Input/Input';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import { RefreshCw, Edit2, Trash2, Users, UserPlus, Activity, ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react';
import type { UserRole, UserStatus, User } from '../../types';
import { PageHeader } from '../../components/organisms/PageHeader/PageHeader';
import { SummaryCards } from '../../components/organisms/SummaryCards/SummaryCards';

type SortKey = 'displayName' | 'email' | 'role' | 'status' | 'createdAt';
type SortDirection = 'asc' | 'desc' | null;

/**
 * User management component
 */
export function UserManagement() {
  const { data: users, loading, error, refetch } = useAllUsers();

  // Sorting state - default: latest created first
  const [sortKey, setSortKey] = useState<SortKey>('createdAt');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');

  // Sort users
  const sortedUsers = useMemo(() => {
    if (!users || !sortKey || !sortDirection) return users || [];

    return [...users].sort((a, b) => {
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
  }, [users, sortKey, sortDirection]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      // Toggle direction: asc → desc → null → asc
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else if (sortDirection === 'desc') {
        setSortDirection(null);
        setSortKey('createdAt'); // Reset to default
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

  // Edit modal state
  const [showEditModal, setShowEditModal] = useState(false);
  const [editUserId, setEditUserId] = useState('');
  const [editDisplayName, setEditDisplayName] = useState('');
  const [editRole, setEditRole] = useState<UserRole>('USER');
  const [editError, setEditError] = useState<string | null>(null);

  // Status change modal state
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [statusUserId, setStatusUserId] = useState('');
  const [statusUserEmail, setStatusUserEmail] = useState('');
  const [statusAction, setStatusAction] = useState<'suspend' | 'activate'>('suspend');
  const [statusReason, setStatusReason] = useState('');
  const [statusError, setStatusError] = useState<string | null>(null);

  // Status history modal state
  const [showHistoryModal, setShowHistoryModal] = useState(false);
  const [historyUser, setHistoryUser] = useState<User | null>(null);

  /**
   * Open edit modal
   */
  const handleEditClick = (userId: string, displayName: string, role: UserRole) => {
    setEditUserId(userId);
    setEditDisplayName(displayName);
    setEditRole(role);
    setEditError(null);
    setShowEditModal(true);
  };

  /**
   * Handle edit user
   */
  const handleEdit = async () => {
    setEditError(null);

    if (!editDisplayName.trim()) {
      setEditError('Display name is required');
      return;
    }

    const response = await apiClient.updateUser(editUserId, {
      displayName: editDisplayName,
      role: editRole,
    });

    if (response.success) {
      setShowEditModal(false);
      refetch();
    } else {
      setEditError(response.error || 'Failed to update user');
    }
  };

  /**
   * Handle delete user
   */
  const handleDelete = async (userId: string, email: string) => {
    if (!confirm(`Are you sure you want to delete user "${email}"? This action cannot be undone.`)) {
      return;
    }

    const response = await apiClient.deleteUser(userId);
    if (response.success) {
      refetch();
    } else {
      alert(response.error || 'Failed to delete user');
    }
  };

  /**
   * Open status change modal
   */
  const openStatusModal = (userId: string, email: string, currentStatus: UserStatus) => {
    setStatusUserId(userId);
    setStatusUserEmail(email);
    setStatusAction(currentStatus === 'ACTIVE' ? 'suspend' : 'activate');
    setStatusReason('');
    setStatusError(null);
    setShowStatusModal(true);
  };

  /**
   * Handle status change with reason
   */
  const handleStatusChange = async () => {
    setStatusError(null);

    // For suspension, require a reason
    if (statusAction === 'suspend' && !statusReason.trim()) {
      setStatusError('Please provide a reason for suspension');
      return;
    }

    const response = await apiClient.toggleUserStatus(statusUserId, statusReason || undefined);
    if (response.success) {
      setShowStatusModal(false);
      refetch();
    } else {
      setStatusError(response.error || 'Failed to change user status');
    }
  };

  /**
   * Open status history modal
   */
  const openHistoryModal = (user: User) => {
    setHistoryUser(user);
    setShowHistoryModal(true);
  };

  /**
   * Get badge variant for status
   */
  const getStatusVariant = (status: UserStatus) => {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'SUSPENDED':
        return 'warning';
      case 'DELETED':
        return 'error';
      default:
        return 'default';
    }
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
          Failed to load users: {error}
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
          title="Users"
          description="Manage system users and permissions"
          actions={
            <>
              <Button variant="secondary" iconLeft={<RefreshCw size={20} />} onClick={refetch}>
                Refresh
              </Button>
              <Button variant="primary" iconLeft={<UserPlus size={20} />}>
                Create User
              </Button>
            </>
          }
        />

        {/* Info Alert */}
        <Alert variant="info">
          <Text variant="body" className="text-sm">
            <strong>Note:</strong> Only ACTIVE users can have data (portfolios, brokers, watchlists) created against them.
            Suspended or deleted users are blocked from having new data.
          </Text>
        </Alert>

        {/* Summary Cards */}
        {users && users.length > 0 && (
          <SummaryCards
            cards={[
              {
                icon: <Users size={20} />,
                iconBgColor: 'bg-blue-100',
                iconColor: 'text-blue-600',
                label: 'Total Users',
                value: users.length,
              },
              {
                icon: <Activity size={20} />,
                iconBgColor: 'bg-green-100',
                iconColor: 'text-green-600',
                label: 'Active Users',
                value: users.filter((u) => u.status === 'ACTIVE').length,
                subtitle: `(${Math.round((users.filter((u) => u.status === 'ACTIVE').length / users.length) * 100)}%)`,
              },
              {
                icon: <Users size={20} />,
                iconBgColor: 'bg-purple-100',
                iconColor: 'text-purple-600',
                label: 'Admin Users',
                value: users.filter((u) => u.role === 'ADMIN').length,
              },
              {
                icon: <Users size={20} />,
                iconBgColor: 'bg-red-100',
                iconColor: 'text-red-600',
                label: 'Suspended',
                value: users.filter((u) => u.status === 'SUSPENDED').length,
                valueColor: 'text-red-600',
              },
            ]}
          />
        )}

        {/* Users Table */}
        <Card>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th className="sortable-header" onClick={() => handleSort('email')}>
                    <div className="table-header-content">
                      <span>Email</span>
                      {getSortIcon('email')}
                    </div>
                  </th>
                  <th className="sortable-header" onClick={() => handleSort('displayName')}>
                    <div className="table-header-content">
                      <span>Display Name</span>
                      {getSortIcon('displayName')}
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
                  <th>Status Changed</th>
                  <th className="sortable-header" onClick={() => handleSort('createdAt')}>
                    <div className="table-header-content">
                      <span>Created</span>
                      {getSortIcon('createdAt')}
                    </div>
                  </th>
                  <th>Last Login</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {sortedUsers && sortedUsers.length > 0 ? (
                  sortedUsers.map((user) => (
                    <tr key={user.userId}>
                      <td>
                        <div className="table-primary">{user.email}</div>
                      </td>
                      <td>
                        <div className="table-primary">{user.displayName}</div>
                      </td>
                      <td>
                        <Badge variant={user.role === 'ADMIN' ? 'primary' : 'default'}>
                          {user.role}
                        </Badge>
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          <Badge variant={getStatusVariant(user.status)}>
                            {user.status}
                          </Badge>
                          {user.statusHistory && user.statusHistory.length > 0 && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => openHistoryModal(user)}
                              className="text-xs"
                            >
                              View History
                            </Button>
                          )}
                        </div>
                      </td>
                      <td>
                        <div className="table-date">
                          {user.status === 'SUSPENDED' && user.suspendedAt ? (
                            <div>
                              <div className="font-medium text-loss">
                                {new Date(user.suspendedAt).toLocaleString()}
                              </div>
                              {user.suspendedBy && (
                                <div className="text-xs text-muted">by {user.suspendedBy}</div>
                              )}
                            </div>
                          ) : user.status === 'ACTIVE' && user.activatedAt ? (
                            <div>
                              <div className="font-medium text-profit">
                                {new Date(user.activatedAt).toLocaleString()}
                              </div>
                              {user.activatedBy && (
                                <div className="text-xs text-muted">by {user.activatedBy}</div>
                              )}
                            </div>
                          ) : (
                            '-'
                          )}
                        </div>
                      </td>
                      <td>
                        <div className="table-date">
                          {new Date(user.createdAt).toLocaleDateString()}
                        </div>
                      </td>
                      <td>
                        <div className="table-date">
                          {user.lastLoginAt
                            ? new Date(user.lastLoginAt).toLocaleString()
                            : 'Never'}
                        </div>
                      </td>
                      <td className="text-right">
                        <div className="table-actions">
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Edit2 size={16} />}
                            onClick={() => handleEditClick(user.userId, user.displayName, user.role)}
                          >
                            <></>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => openStatusModal(user.userId, user.email, user.status)}
                            className={user.status === 'ACTIVE' ? 'text-loss' : 'text-profit'}
                          >
                            {user.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            iconLeft={<Trash2 size={16} />}
                            onClick={() => handleDelete(user.userId, user.email)}
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
                        icon={<Users size={48} />}
                        title="No Users Found"
                        description="No users in the system yet."
                      />
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      </main>

      {/* Edit User Modal */}
      {showEditModal && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowEditModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--md animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-primary">
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">Edit User</Text>
                  <Button variant="ghost" size="sm" onClick={() => setShowEditModal(false)}>
                    Close
                  </Button>
                </div>

                {editError && (
                  <Alert variant="error" onDismiss={() => setEditError(null)}>
                    {editError}
                  </Alert>
                )}

                <div className="form-spacing">
                  <div>
                    <Text variant="label" className="mb-2">
                      Display Name *
                    </Text>
                    <Input
                      type="text"
                      placeholder="Enter display name"
                      value={editDisplayName}
                      onChange={(e) => setEditDisplayName(e.target.value)}
                      fullWidth
                      required
                    />
                  </div>

                  <div>
                    <Text variant="label" className="mb-2">
                      Role *
                    </Text>
                    <div className="flex gap-3">
                      <Button
                        variant={editRole === 'USER' ? 'primary' : 'secondary'}
                        size="md"
                        onClick={() => setEditRole('USER')}
                        className="flex-1"
                      >
                        USER
                      </Button>
                      <Button
                        variant={editRole === 'ADMIN' ? 'primary' : 'secondary'}
                        size="md"
                        onClick={() => setEditRole('ADMIN')}
                        className="flex-1"
                      >
                        ADMIN
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

      {/* Status Change Modal */}
      {showStatusModal && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowStatusModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--md animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-yellow-500">
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <Text variant="h3">
                    {statusAction === 'suspend' ? 'Suspend User' : 'Activate User'}
                  </Text>
                  <Button variant="ghost" size="sm" onClick={() => setShowStatusModal(false)}>
                    Close
                  </Button>
                </div>

                {statusError && (
                  <Alert variant="error" onDismiss={() => setStatusError(null)}>
                    {statusError}
                  </Alert>
                )}

                <Alert variant={statusAction === 'suspend' ? 'warning' : 'info'}>
                  <Text variant="body" className="text-sm">
                    {statusAction === 'suspend' ? (
                      <>
                        <strong>Warning:</strong> Suspending user <strong>{statusUserEmail}</strong> will prevent them from logging in and accessing the system. This action will be logged for audit purposes with timestamp.
                      </>
                    ) : (
                      <>
                        <strong>Info:</strong> Activating user <strong>{statusUserEmail}</strong> will allow them to log in and access the system again. This action will be logged for audit purposes with timestamp.
                      </>
                    )}
                  </Text>
                </Alert>

                <div className="form-spacing">
                  <div>
                    <Text variant="label" className="mb-2">
                      Reason {statusAction === 'suspend' && '*'}
                    </Text>
                    <textarea
                      className="input input--md w-full min-h-[100px]"
                      placeholder={
                        statusAction === 'suspend'
                          ? 'Enter reason for suspension (required)'
                          : 'Enter reason for activation (optional)'
                      }
                      value={statusReason}
                      onChange={(e) => setStatusReason(e.target.value)}
                      required={statusAction === 'suspend'}
                    />
                    <Text variant="small" className="text-muted mt-1">
                      This reason will be stored in the audit log with current date and time
                    </Text>
                  </div>
                </div>

                <div className="form-actions form-actions--stack-mobile">
                  <Button variant="secondary" fullWidth onClick={() => setShowStatusModal(false)}>
                    Cancel
                  </Button>
                  <Button
                    variant={statusAction === 'suspend' ? 'sell' : 'primary'}
                    fullWidth
                    onClick={handleStatusChange}
                  >
                    {statusAction === 'suspend' ? 'Suspend User' : 'Activate User'}
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        </>
      )}

      {/* Status History Modal */}
      {showHistoryModal && historyUser && (
        <>
          <div
            className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
            onClick={() => setShowHistoryModal(false)}
          />
          <div className="modal-slide-right modal-slide-right--xl animate-slide-in-right">
            <Card className="shadow-2xl border-2 border-blue-500">
              <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                  <div>
                    <Text variant="h3">Status History</Text>
                    <Text variant="body" className="text-muted">
                      {historyUser.email}
                    </Text>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => setShowHistoryModal(false)}>
                    Close
                  </Button>
                </div>

                {historyUser.statusHistory && historyUser.statusHistory.length > 0 ? (
                  <div className="table-container">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Date & Time</th>
                          <th>From Status</th>
                          <th>To Status</th>
                          <th>Changed By</th>
                          <th>Reason</th>
                        </tr>
                      </thead>
                      <tbody>
                        {historyUser.statusHistory.map((change, index) => (
                          <tr key={index}>
                            <td>
                              <div className="table-date">
                                {new Date(change.changedAt).toLocaleString()}
                              </div>
                            </td>
                            <td>
                              <Badge variant={getStatusVariant(change.fromStatus)}>
                                {change.fromStatus}
                              </Badge>
                            </td>
                            <td>
                              <Badge variant={getStatusVariant(change.toStatus)}>
                                {change.toStatus}
                              </Badge>
                            </td>
                            <td>
                              <div className="table-primary">{change.changedBy}</div>
                            </td>
                            <td>
                              <div className="table-secondary">
                                {change.reason || '-'}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <Alert variant="info">
                    No status change history available for this user.
                  </Alert>
                )}
              </div>
            </Card>
          </div>
        </>
      )}
    </>
  );
}
