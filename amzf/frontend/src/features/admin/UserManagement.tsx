/**
 * User Management Page
 * View and manage system users
 */

import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useAllUsers } from '../../hooks/useApi';
import { Header } from '../../components/organisms/Header/Header';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Button } from '../../components/atoms/Button/Button';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import { RefreshCw, UserPlus, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getAdminNavItems } from '../../lib/navigation';

/**
 * User management component
 */
export function UserManagement() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getAdminNavItems(location.pathname);
  const { data: users, loading, error, refetch } = useAllUsers();

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
            Failed to load users: {error}
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
            <div className="flex items-center gap-2 mb-2">
              <Link to="/admin">
                <Text variant="body" className="text-muted hover:text-primary">
                  Admin
                </Text>
              </Link>
              <Text variant="body" className="text-muted">
                /
              </Text>
              <Text variant="h2">Users</Text>
            </div>
            <Text variant="body" className="text-muted">
              Manage system users and permissions
            </Text>
          </div>

          <div className="flex gap-3">
            <Button variant="secondary" iconLeft={<RefreshCw size={20} />} onClick={refetch}>
              Refresh
            </Button>
            <Button variant="primary" iconLeft={<UserPlus size={20} />}>
              Add User
            </Button>
          </div>
        </div>

        {/* Users Table */}
        <Card>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-border-light">
                  <th className="p-4 text-left">
                    <Text variant="label">Email</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Display Name</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Role</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Created</Text>
                  </th>
                  <th className="p-4 text-left">
                    <Text variant="label">Last Login</Text>
                  </th>
                  <th className="p-4 text-right">
                    <Text variant="label">Actions</Text>
                  </th>
                </tr>
              </thead>
              <tbody>
                {users && users.length > 0 ? (
                  users.map((user) => (
                    <tr
                      key={user.id}
                      className="border-b border-border-light hover:bg-surface-secondary transition-colors"
                    >
                      <td className="p-4">
                        <Text variant="body">{user.email}</Text>
                      </td>
                      <td className="p-4">
                        <Text variant="body">{user.displayName}</Text>
                      </td>
                      <td className="p-4">
                        <Badge variant={user.role === 'ADMIN' ? 'primary' : 'default'}>
                          {user.role}
                        </Badge>
                      </td>
                      <td className="p-4">
                        <Text variant="small" className="text-muted">
                          {new Date(user.createdAt).toLocaleDateString()}
                        </Text>
                      </td>
                      <td className="p-4">
                        <Text variant="small" className="text-muted">
                          {user.lastLoginAt
                            ? new Date(user.lastLoginAt).toLocaleString()
                            : 'Never'}
                        </Text>
                      </td>
                      <td className="p-4 text-right">
                        <Button variant="ghost" size="sm">
                          Edit
                        </Button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={6} className="p-12">
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
    </div>
  );
}
