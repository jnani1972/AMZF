/**
 * Authentication Provider
 * Manages authentication state, JWT tokens, and user session
 * Includes inactivity timeout detection
 */

import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  ReactNode,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '../../lib/api';
import type { User } from '../../types';

/**
 * Default inactivity timeout in minutes
 */
const DEFAULT_INACTIVITY_TIMEOUT_MINUTES = 15;

/**
 * localStorage keys
 */
const LAST_ACTIVITY_KEY = 'last_activity';
const INACTIVITY_TIMEOUT_KEY = 'inactivity_timeout_minutes';

/**
 * Get inactivity timeout in milliseconds from localStorage
 */
const getInactivityTimeout = (): number => {
  const storedMinutes = localStorage.getItem(INACTIVITY_TIMEOUT_KEY);
  const minutes = storedMinutes ? parseInt(storedMinutes, 10) : DEFAULT_INACTIVITY_TIMEOUT_MINUTES;

  // Validate range (1-1440 minutes)
  const validMinutes = Math.max(1, Math.min(minutes, 1440));

  return validMinutes * 60 * 1000; // Convert to milliseconds
};

/**
 * Auth context state
 */
export interface AuthContextState {
  user: User | null;
  loading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; error?: string }>;
  register: (
    email: string,
    password: string,
    displayName: string
  ) => Promise<{ success: boolean; error?: string }>;
  logout: () => void;
  bootstrap: () => Promise<void>;
}

/**
 * Auth context
 */
const AuthContext = createContext<AuthContextState | undefined>(undefined);

/**
 * Auth provider props
 */
export interface AuthProviderProps {
  children: ReactNode;
}

/**
 * Auth provider component
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  /**
   * Update last activity timestamp
   */
  const updateActivity = useCallback(() => {
    // Check if user exists by reading from state, but don't depend on user to avoid infinite loop
    const authToken = localStorage.getItem('auth_token');
    if (authToken) {
      localStorage.setItem(LAST_ACTIVITY_KEY, Date.now().toString());
    }
  }, []); // No dependencies - stable function

  /**
   * Check if session has expired due to inactivity
   */
  const checkInactivity = useCallback(() => {
    const lastActivity = localStorage.getItem(LAST_ACTIVITY_KEY);
    const authToken = localStorage.getItem('auth_token');

    if (!lastActivity || !authToken) {
      return false;
    }

    const timeSinceActivity = Date.now() - parseInt(lastActivity, 10);
    const inactivityTimeout = getInactivityTimeout();
    return timeSinceActivity > inactivityTimeout;
  }, []); // No dependencies - stable function

  /**
   * Bootstrap application data
   */
  const bootstrap = useCallback(async () => {
    setLoading(true);

    // Check for inactivity timeout before bootstrapping
    // Timeout is configurable via Admin Settings (default: 15 minutes)
    if (checkInactivity()) {
      const timeoutMinutes = Math.floor(getInactivityTimeout() / 60000);
      console.log(`Session expired due to inactivity (${timeoutMinutes} minutes)`);
      await apiClient.logout();
      localStorage.removeItem(LAST_ACTIVITY_KEY);
      setUser(null);
      setLoading(false);
      return;
    }

    const response = await apiClient.bootstrap();

    if (response.success && response.data) {
      setUser(response.data.user);
      updateActivity(); // Update activity on successful bootstrap
    } else {
      // No valid token or bootstrap failed
      setUser(null);
      await apiClient.logout();
      localStorage.removeItem(LAST_ACTIVITY_KEY);
    }

    setLoading(false);
  }, [checkInactivity, updateActivity]);

  /**
   * Initialize auth on mount (run once)
   */
  useEffect(() => {
    bootstrap();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Only run once on mount

  /**
   * Logout current user
   */
  const logout = useCallback(() => {
    apiClient.logout();
    localStorage.removeItem(LAST_ACTIVITY_KEY); // Clear activity timestamp
    setUser(null);
    navigate('/auth/login');
  }, [navigate]);

  /**
   * Setup activity tracking and inactivity checker
   */
  useEffect(() => {
    if (!user) return;

    // Track user activity on various events
    const activityEvents = ['mousedown', 'keydown', 'scroll', 'touchstart', 'click'];

    activityEvents.forEach((event) => {
      window.addEventListener(event, updateActivity);
    });

    // Check for inactivity every minute
    const inactivityCheckInterval = setInterval(() => {
      if (checkInactivity()) {
        const timeoutMinutes = Math.floor(getInactivityTimeout() / 60000);
        console.log(`User inactive for ${timeoutMinutes} minutes, logging out...`);
        logout();
      }
    }, 60 * 1000); // Check every minute

    // Cleanup
    return () => {
      activityEvents.forEach((event) => {
        window.removeEventListener(event, updateActivity);
      });
      clearInterval(inactivityCheckInterval);
    };
  }, [user, updateActivity, checkInactivity, logout]);

  /**
   * Login with email and password
   */
  const login = useCallback(
    async (email: string, password: string) => {
      setLoading(true);

      const response = await apiClient.login(email, password);

      if (response.success && response.data) {
        // Fetch user data after successful login
        await bootstrap();

        // Set initial activity timestamp
        localStorage.setItem(LAST_ACTIVITY_KEY, Date.now().toString());

        // Role-based routing: Admin users go to admin panel, regular users go to dashboard
        const userRole = response.data.role;
        if (userRole === 'ADMIN') {
          navigate('/admin');
        } else {
          navigate('/dashboard');
        }

        return { success: true };
      }

      setLoading(false);
      return {
        success: false,
        error: response.error || 'Login failed',
      };
    },
    [bootstrap, navigate]
  );

  /**
   * Register new user
   */
  const register = useCallback(
    async (email: string, password: string, displayName: string) => {
      setLoading(true);

      const response = await apiClient.register(email, password, displayName);

      if (response.success && response.data) {
        // Fetch user data after successful registration
        await bootstrap();

        // Set initial activity timestamp
        localStorage.setItem(LAST_ACTIVITY_KEY, Date.now().toString());

        // Role-based routing: Admin users go to admin panel, regular users go to dashboard
        const userRole = response.data.role;
        if (userRole === 'ADMIN') {
          navigate('/admin');
        } else {
          navigate('/dashboard');
        }

        return { success: true };
      }

      setLoading(false);
      return {
        success: false,
        error: response.error || 'Registration failed',
      };
    },
    [bootstrap, navigate]
  );

  /**
   * Computed values
   */
  const isAuthenticated = !!user;
  const isAdmin = user?.role === 'ADMIN';

  const value: AuthContextState = {
    user,
    loading,
    isAuthenticated,
    isAdmin,
    login,
    register,
    logout,
    bootstrap,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Hook to use auth context
 */
export function useAuth() {
  const context = useContext(AuthContext);

  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }

  return context;
}
