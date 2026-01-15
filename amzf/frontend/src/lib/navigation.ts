/**
 * Navigation Configuration
 * Shared navigation items for the application
 */

import type { NavItem } from '@/components/organisms/Header/Header';

/**
 * Get navigation items for current path
 * @param currentPath - Current pathname
 * @returns Navigation items with active state
 */
export function getNavItems(currentPath: string): NavItem[] {
  return [
    {
      label: 'Dashboard',
      href: '/',
      active: currentPath === '/',
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
 * Get admin navigation items for current path
 * @param currentPath - Current pathname
 * @returns Admin navigation items with active state
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
      label: 'Settings',
      href: '/admin/settings',
      active: currentPath === '/admin/settings',
    },
  ];
}
