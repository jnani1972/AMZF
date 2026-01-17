/**
 * Admin Layout
 * Routes to admin sub-pages with smooth transitions and left sidebar
 */

import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { LayoutDashboard, Users, Activity, Briefcase, Eye, Settings as SettingsIcon } from 'lucide-react';
import { useAuth } from '../auth/AuthProvider';
import { Sidebar, SidebarNavItem } from '../../components/organisms/Sidebar/Sidebar';
import { AdminDashboard } from './AdminDashboard';
import { UserManagement } from './UserManagement';
import { BrokerManagement } from './BrokerManagement';
import { PortfolioManagement } from './PortfolioManagement';
import { WatchlistManagement } from './WatchlistManagement';
import { Settings } from './Settings';
import { getAdminSidebarNavItems } from '../../lib/navigation';

/**
 * Icon map for navigation
 */
const iconMap = {
  dashboard: <LayoutDashboard size={18} />,
  users: <Users size={18} />,
  activity: <Activity size={18} />,
  briefcase: <Briefcase size={18} />,
  eye: <Eye size={18} />,
  settings: <SettingsIcon size={18} />,
};

/**
 * Admin component with left sidebar and routing with smooth transitions
 */
export function Admin() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navConfig = getAdminSidebarNavItems(location.pathname);

  // Convert nav config to nav items with icon components
  const navItems: SidebarNavItem[] = navConfig.map((item) => ({
    ...item,
    icon: iconMap[item.iconName],
  }));

  return (
    <div className="sidebar-layout">
      {/* Left Sidebar */}
      <Sidebar
        title="AMZF Admin"
        navItems={navItems}
        user={user ? { name: user.displayName, email: user.email } : undefined}
        onLogout={logout}
      />

      {/* Main Content Area */}
      <div className="main-content">
        <div key={location.pathname} className="page-transition">
          <Routes location={location}>
            <Route index element={<AdminDashboard />} />
            <Route path="users" element={<UserManagement />} />
            <Route path="brokers" element={<BrokerManagement />} />
            <Route path="portfolios" element={<PortfolioManagement />} />
            <Route path="watchlist" element={<WatchlistManagement />} />
            <Route path="settings" element={<Settings />} />
            <Route path="*" element={<Navigate to="/admin" replace />} />
          </Routes>
        </div>
      </div>
    </div>
  );
}
