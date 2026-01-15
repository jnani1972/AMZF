/**
 * Authentication Provider
 * Manages authentication state, JWT tokens, and user session
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
   * Bootstrap application data
   */
  const bootstrap = useCallback(async () => {
    setLoading(true);
    const response = await apiClient.bootstrap();

    if (response.success && response.data) {
      setUser(response.data.user);
    } else {
      // No valid token or bootstrap failed
      setUser(null);
      await apiClient.logout();
    }

    setLoading(false);
  }, []);

  /**
   * Initialize auth on mount
   */
  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

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
        navigate('/');
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
        navigate('/');
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
   * Logout current user
   */
  const logout = useCallback(() => {
    apiClient.logout();
    setUser(null);
    navigate('/auth/login');
  }, [navigate]);

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
