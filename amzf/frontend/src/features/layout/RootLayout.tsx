/**
 * Root Layout Component
 * Provides AuthProvider context to all routes
 */

import { Outlet } from 'react-router-dom';
import { AuthProvider } from '@/features/auth/AuthProvider';

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
