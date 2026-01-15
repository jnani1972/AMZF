/**
 * Application Routes
 * React Router v6 configuration with lazy loading
 */

import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { RootLayout } from './features/layout/RootLayout';
import { ProtectedRoute } from './features/auth/ProtectedRoute';
import { AdminRoute } from './features/auth/AdminRoute';
import { Spinner } from './components/atoms/Spinner/Spinner';

/**
 * Loading fallback component
 */
function LoadingFallback() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <Spinner size="lg" variant="primary" />
    </div>
  );
}

/**
 * Lazy loaded components
 */

// Auth pages
const Login = lazy(() =>
  import('./features/auth/Login').then((m) => ({ default: m.Login }))
);
const Register = lazy(() =>
  import('./features/auth/Register').then((m) => ({ default: m.Register }))
);
const OAuthCallback = lazy(() =>
  import('./features/auth/OAuthCallback').then((m) => ({ default: m.OAuthCallback }))
);

// Main pages
const Dashboard = lazy(() =>
  import('./features/dashboard/Dashboard').then((m) => ({ default: m.Dashboard }))
);
const Portfolio = lazy(() =>
  import('./features/portfolio/Portfolio').then((m) => ({ default: m.Portfolio }))
);
const Orders = lazy(() =>
  import('./features/trading/Orders').then((m) => ({ default: m.Orders }))
);
const MarketWatch = lazy(() =>
  import('./features/market-watch/MarketWatch').then((m) => ({ default: m.MarketWatch }))
);

// Admin pages
const Admin = lazy(() =>
  import('./features/admin/Admin').then((m) => ({ default: m.Admin }))
);

// Live Dashboard
const LiveDashboard = lazy(() =>
  import('./features/live-dashboard/LiveDashboard').then((m) => ({ default: m.LiveDashboard }))
);

// Error pages
const NotFound = lazy(() =>
  import('./features/error/NotFound').then((m) => ({ default: m.NotFound }))
);

/**
 * Router configuration
 */
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      // Root - redirect to dashboard
      {
        path: '/',
        element: (
          <ProtectedRoute>
            <Suspense fallback={<LoadingFallback />}>
              <Dashboard />
            </Suspense>
          </ProtectedRoute>
        ),
      },

      // Portfolio
      {
        path: '/portfolio',
        element: (
          <ProtectedRoute>
            <Suspense fallback={<LoadingFallback />}>
              <Portfolio />
            </Suspense>
          </ProtectedRoute>
        ),
      },

      // Orders
      {
        path: '/orders',
        element: (
          <ProtectedRoute>
            <Suspense fallback={<LoadingFallback />}>
              <Orders />
            </Suspense>
          </ProtectedRoute>
        ),
      },

      // Market Watch
      {
        path: '/market-watch',
        element: (
          <ProtectedRoute>
            <Suspense fallback={<LoadingFallback />}>
              <MarketWatch />
            </Suspense>
          </ProtectedRoute>
        ),
      },

      // Live Dashboard
      {
        path: '/live-dashboard',
        element: (
          <ProtectedRoute>
            <Suspense fallback={<LoadingFallback />}>
              <LiveDashboard />
            </Suspense>
          </ProtectedRoute>
        ),
      },

      // Admin Panel
      {
        path: '/admin/*',
        element: (
          <ProtectedRoute>
            <AdminRoute>
              <Suspense fallback={<LoadingFallback />}>
                <Admin />
              </Suspense>
            </AdminRoute>
          </ProtectedRoute>
        ),
      },

      // Auth routes
      {
        path: '/auth',
        children: [
          {
            path: 'login',
            element: (
              <Suspense fallback={<LoadingFallback />}>
                <Login />
              </Suspense>
            ),
          },
          {
            path: 'register',
            element: (
              <Suspense fallback={<LoadingFallback />}>
                <Register />
              </Suspense>
            ),
          },
          {
            path: 'oauth-callback',
            element: (
              <Suspense fallback={<LoadingFallback />}>
                <OAuthCallback />
              </Suspense>
            ),
          },
          // Redirect /auth to /auth/login
          {
            index: true,
            element: <Navigate to="/auth/login" replace />,
          },
        ],
      },

      // 404 Not Found
      {
        path: '*',
        element: (
          <Suspense fallback={<LoadingFallback />}>
            <NotFound />
          </Suspense>
        ),
      },
    ],
  },
]);
