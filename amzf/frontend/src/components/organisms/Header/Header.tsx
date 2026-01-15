import React, { useState } from 'react';
import { Menu, X, Moon, Sun, User, LogOut } from 'lucide-react';
import { Button } from '../../atoms/Button/Button';
import { Text } from '../../atoms/Text/Text';
import { useTheme } from '../../../lib/theme';
import './Header.css';

/**
 * Navigation item
 */
export interface NavItem {
  label: string;
  href: string;
  active?: boolean;
}

/**
 * User info
 */
export interface UserInfo {
  name: string;
  email?: string;
}

/**
 * Header component props
 */
export interface HeaderProps {
  /**
   * App title/logo
   */
  title?: string;

  /**
   * Navigation items
   */
  navItems?: NavItem[];

  /**
   * User information
   */
  user?: UserInfo;

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
 * Header Component
 *
 * Main application header with navigation, user menu, and theme toggle.
 * Responsive with mobile hamburger menu.
 *
 * @example
 * ```tsx
 * <Header
 *   title="AMZF Trading"
 *   navItems={[
 *     { label: 'Dashboard', href: '/', active: true },
 *     { label: 'Portfolio', href: '/portfolio' },
 *     { label: 'Orders', href: '/orders' }
 *   ]}
 *   user={{ name: 'John Doe', email: 'john@example.com' }}
 *   onLogout={handleLogout}
 * />
 * ```
 */
export const Header: React.FC<HeaderProps> = ({
  title = 'AMZF Trading',
  navItems = [],
  user,
  onLogout,
  className = '',
}) => {
  const { theme, toggleTheme } = useTheme();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  const toggleMobileMenu = () => {
    setMobileMenuOpen(!mobileMenuOpen);
  };

  const toggleUserMenu = () => {
    setUserMenuOpen(!userMenuOpen);
  };

  const classNames = ['header', className].filter(Boolean).join(' ');

  return (
    <header className={classNames}>
      <div className="header__container">
        {/* Logo/Title */}
        <div className="header__brand">
          <Text variant="h4" weight="bold">
            {title}
          </Text>
        </div>

        {/* Desktop Navigation */}
        <nav className="header__nav header__nav--desktop">
          {navItems.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className={`header__nav-item ${item.active ? 'header__nav-item--active' : ''}`}
            >
              {item.label}
            </a>
          ))}
        </nav>

        {/* Actions */}
        <div className="header__actions">
          {/* Theme Toggle */}
          <Button
            variant="ghost"
            size="sm"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
            iconLeft={theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
          >
            <></>
          </Button>

          {/* User Menu */}
          {user && (
            <div className="header__user-menu">
              <Button
                variant="ghost"
                size="sm"
                onClick={toggleUserMenu}
                iconLeft={<User size={18} />}
              >
                {user.name}
              </Button>

              {userMenuOpen && (
                <div className="header__user-dropdown">
                  <div className="header__user-info">
                    <Text variant="label" weight="semibold">
                      {user.name}
                    </Text>
                    {user.email && (
                      <Text variant="caption" color="secondary">
                        {user.email}
                      </Text>
                    )}
                  </div>
                  <div className="header__user-actions">
                    {onLogout && (
                      <button className="header__user-action" onClick={onLogout}>
                        <LogOut size={16} />
                        <span>Logout</span>
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Mobile Menu Toggle */}
          <Button
            variant="ghost"
            size="sm"
            onClick={toggleMobileMenu}
            className="header__mobile-toggle"
            iconLeft={mobileMenuOpen ? <X size={18} /> : <Menu size={18} />}
            aria-label="Toggle menu"
          >
            <></>
          </Button>
        </div>
      </div>

      {/* Mobile Navigation */}
      {mobileMenuOpen && (
        <nav className="header__nav header__nav--mobile">
          {navItems.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className={`header__nav-item ${item.active ? 'header__nav-item--active' : ''}`}
              onClick={() => setMobileMenuOpen(false)}
            >
              {item.label}
            </a>
          ))}
        </nav>
      )}
    </header>
  );
};

export default Header;
