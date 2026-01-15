/**
 * Admin Layout
 * Routes to admin sub-pages
 */

import { Routes, Route, Navigate } from 'react-router-dom';
import { AdminDashboard } from './AdminDashboard';
import { UserManagement } from './UserManagement';
import { BrokerManagement } from './BrokerManagement';
import { PortfolioManagement } from './PortfolioManagement';
import { Settings } from './Settings';

/**
 * Admin component with routing
 */
export function Admin() {
  return (
    <Routes>
      <Route index element={<AdminDashboard />} />
      <Route path="users" element={<UserManagement />} />
      <Route path="brokers" element={<BrokerManagement />} />
      <Route path="portfolios" element={<PortfolioManagement />} />
      <Route path="settings" element={<Settings />} />
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
