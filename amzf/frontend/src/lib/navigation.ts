/**
 * Navigation Configuration
 * Shared navigation items for the application
 */

import type { NavItem } from '../components/organisms/Header/Header';

export interface SidebarNavConfig {
  label: string;
  href: string;
  iconName: 'dashboard' | 'users' | 'activity' | 'briefcase' | 'eye' | 'settings';
  active?: boolean;
}

/**
 * Get navigation items for current path
 * @param currentPath - Current pathname
 * @returns Navigation items with active state
 */
export function getNavItems(currentPath: string): NavItem[] {
  return [
    {
      label: 'Dashboard',
      href: '/dashboard',
      active: currentPath === '/dashboard',
    },
    {
      label: 'Portfolio',
      href: '/portfolio',
      active: currentPath === '/portfolio',
    },
    {
      label: 'Orders',
      href: '/orders',
      active: currentPath === '/orders',
    },
    {
      label: 'Market Watch',
      href: '/market-watch',
      active: currentPath === '/market-watch',
    },
    {
      label: 'Live Dashboard',
      href: '/live-dashboard',
      active: currentPath === '/live-dashboard',
    },
  ];
}

/**
 * Get admin navigation items for current path (for sidebar)
 * @param currentPath - Current pathname
 * @returns Admin sidebar navigation items with icon names and active state
 */
export function getAdminSidebarNavItems(currentPath: string): SidebarNavConfig[] {
  return [
    {
      label: 'Dashboard',
      href: '/admin',
      iconName: 'dashboard',
      active: currentPath === '/admin',
    },
    {
      label: 'Users',
      href: '/admin/users',
      iconName: 'users',
      active: currentPath === '/admin/users',
    },
    {
      label: 'Brokers',
      href: '/admin/brokers',
      iconName: 'activity',
      active: currentPath === '/admin/brokers',
    },
    {
      label: 'Portfolios',
      href: '/admin/portfolios',
      iconName: 'briefcase',
      active: currentPath === '/admin/portfolios',
    },
    {
      label: 'Watchlist',
      href: '/admin/watchlist',
      iconName: 'eye',
      active: currentPath === '/admin/watchlist',
    },
    {
      label: 'Settings',
      href: '/admin/settings',
      iconName: 'settings',
      active: currentPath === '/admin/settings',
    },
  ];
}

/**
 * Get admin navigation items for current path (for header - deprecated)
 * @param currentPath - Current pathname
 * @returns Admin navigation items with active state
 * @deprecated Use getAdminSidebarNavItems instead
 */
export function getAdminNavItems(currentPath: string): NavItem[] {
  return [
    {
      label: 'Admin',
      href: '/admin',
      active: currentPath === '/admin',
    },
    {
      label: 'Users',
      href: '/admin/users',
      active: currentPath === '/admin/users',
    },
    {
      label: 'Brokers',
      href: '/admin/brokers',
      active: currentPath === '/admin/brokers',
    },
    {
      label: 'Portfolios',
      href: '/admin/portfolios',
      active: currentPath === '/admin/portfolios',
    },
    {
      label: 'Watchlist',
      href: '/admin/watchlist',
      active: currentPath === '/admin/watchlist',
    },
    {
      label: 'Settings',
      href: '/admin/settings',
      active: currentPath === '/admin/settings',
    },
  ];
}
