/**
 * Admin Route Component
 * Redirects to dashboard if user is not an admin
 */

import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { Card } from '../../components/atoms/Card/Card';
import { Text } from '../../components/atoms/Text/Text';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Button } from '../../components/atoms/Button/Button';
import { ShieldAlert } from 'lucide-react';

/**
 * Admin route props
 */
export interface AdminRouteProps {
  children: ReactNode;
}

/**
 * Admin route component
 */
export function AdminRoute({ children }: AdminRouteProps) {
  const { isAuthenticated, isAdmin, loading } = useAuth();

  // Show loading spinner while checking auth
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <Spinner size="lg" variant="primary" />
      </div>
    );
  }

  // Redirect to login if not authenticated
  if (!isAuthenticated) {
    return <Navigate to="/auth/login" replace />;
  }

  // Show access denied if not admin
  if (!isAdmin) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background p-4">
        <div className="w-full max-w-md">
          <Card variant="outlined">
            <div className="p-6 space-y-6">
              <div className="text-center">
                <div className="flex justify-center mb-4">
                  <ShieldAlert size={48} className="text-error" />
                </div>
                <Text variant="h2" className="mb-2">
                  Access Denied
                </Text>
                <Text variant="body" className="text-muted">
                  You don't have permission to access this page
                </Text>
              </div>

              <Alert variant="warning">
                This area is restricted to administrators only. Please contact your system
                administrator if you believe you should have access.
              </Alert>

              <Button variant="primary" fullWidth onClick={() => (window.location.href = '/')}>
                Go to Dashboard
              </Button>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  // Render admin content
  return <>{children}</>;
}
