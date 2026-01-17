/**
 * Sidebar Component
 * Left sidebar navigation for admin layout
 */

import React from 'react';
import { Link } from 'react-router-dom';
import { Moon, Sun, LogOut, User } from 'lucide-react';
import { Button } from '../../atoms/Button/Button';
import { Text } from '../../atoms/Text/Text';
import { useTheme } from '../../../lib/theme';
import './Sidebar.css';

/**
 * Navigation item
 */
export interface SidebarNavItem {
  label: string;
  href: string;
  icon?: React.ReactNode;
  active?: boolean;
}

/**
 * User info
 */
export interface SidebarUserInfo {
  name: string;
  email?: string;
}

/**
 * Sidebar component props
 */
export interface SidebarProps {
  /**
   * App title/logo
   */
  title?: string;

  /**
   * Navigation items
   */
  navItems?: SidebarNavItem[];

  /**
   * User information
   */
  user?: SidebarUserInfo;

  /**
   * Logout handler
   */
  onLogout?: () => void;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Sidebar Component
 *
 * Left sidebar navigation with vertical menu items
 *
 * @example
 * ```tsx
 * <Sidebar
 *   title="AMZF Admin"
 *   navItems={[
 *     { label: 'Dashboard', href: '/admin', icon: <HomeIcon />, active: true },
 *     { label: 'Users', href: '/admin/users', icon: <UsersIcon /> }
 *   ]}
 *   user={{ name: 'Admin', email: 'admin@amzf.com' }}
 *   onLogout={handleLogout}
 * />
 * ```
 */
export const Sidebar: React.FC<SidebarProps> = ({
  title = 'AMZF Admin',
  navItems = [],
  user,
  onLogout,
  className = '',
}) => {
  const { theme, toggleTheme } = useTheme();

  const classNames = ['sidebar', className].filter(Boolean).join(' ');

  return (
    <aside className={classNames}>
      {/* Logo/Title */}
      <div className="sidebar__header">
        <Text variant="h4" weight="bold" className="text-primary">
          {title}
        </Text>
      </div>

      {/* Navigation */}
      <nav className="sidebar__nav">
        {navItems.map((item) => (
          <Link
            key={item.href}
            to={item.href}
            className={`sidebar__nav-item ${item.active ? 'sidebar__nav-item--active' : ''}`}
          >
            {item.icon && <span className="sidebar__nav-icon">{item.icon}</span>}
            <span className="sidebar__nav-label">{item.label}</span>
          </Link>
        ))}
      </nav>

      {/* Footer */}
      <div className="sidebar__footer">
        {/* Theme Toggle */}
        <Button
          variant="ghost"
          size="sm"
          onClick={toggleTheme}
          aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
          iconLeft={theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
          fullWidth
        >
          {theme === 'light' ? 'Dark Mode' : 'Light Mode'}
        </Button>

        {/* User Info & Logout */}
        {user && (
          <div className="sidebar__user">
            <div className="sidebar__user-info">
              <div className="flex items-center gap-2 mb-2">
                <User size={16} className="text-muted" />
                <Text variant="label" weight="semibold" className="truncate">
                  {user.name}
                </Text>
              </div>
              {user.email && (
                <Text variant="caption" className="text-muted truncate">
                  {user.email}
                </Text>
              )}
            </div>
            {onLogout && (
              <Button
                variant="ghost"
                size="sm"
                onClick={onLogout}
                iconLeft={<LogOut size={16} />}
                fullWidth
                className="mt-3"
              >
                Logout
              </Button>
            )}
          </div>
        )}
      </div>
    </aside>
  );
};

export default Sidebar;
