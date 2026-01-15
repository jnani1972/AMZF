/**
 * Protected Route Component
 * Redirects to login if user is not authenticated
 */

import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { Spinner } from '../../components/atoms/Spinner/Spinner';

/**
 * Protected route props
 */
export interface ProtectedRouteProps {
  children: ReactNode;
}

/**
 * Protected route component
 */
export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

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
    return <Navigate to="/auth/login" state={{ from: location }} replace />;
  }

  // Render protected content
  return <>{children}</>;
}
