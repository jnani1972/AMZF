/**
 * Root Layout Component
 * Provides AuthProvider context to all routes
 */

import { Outlet } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthProvider';

/**
 * Root layout component
 */
export function RootLayout() {
  return (
    <AuthProvider>
      <Outlet />
    </AuthProvider>
  );
}
