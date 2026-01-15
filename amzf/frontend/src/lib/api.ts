/**
 * Type-Safe API Client
 * Provides methods for all backend endpoints
 */

import type {
  User,
  Portfolio,
  Trade,
  Signal,
  OrderRequest,
  OrderResponse,
  Watchlist,
  MarketData,
  UserBroker,
  BootstrapData,
  ApiResponse,
  TradeIntent,
  MTFConfig,
} from '../types';

/**
 * API Configuration
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090';

/**
 * Auth Response
 */
export interface AuthResponse {
  token: string;
  userId: string;
  role: string;
}

/**
 * API Client Class
 */
class ApiClient {
  private baseUrl: string;
  private token: string | null = null;

  constructor(baseUrl: string = API_BASE_URL) {
    this.baseUrl = baseUrl;
    this.loadToken();
  }

  /**
   * Load token from localStorage
   */
  private loadToken(): void {
    this.token = localStorage.getItem('auth_token');
  }

  /**
   * Save token to localStorage
   */
  private saveToken(token: string): void {
    this.token = token;
    localStorage.setItem('auth_token', token);
  }

  /**
   * Clear token from localStorage
   */
  private clearToken(): void {
    this.token = null;
    localStorage.removeItem('auth_token');
  }

  /**
   * Get authorization headers
   */
  private getHeaders(): HeadersInit {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    return headers;
  }

  /**
   * Make HTTP request
   */
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    const url = `${this.baseUrl}${endpoint}`;
    const config: RequestInit = {
      ...options,
      headers: {
        ...this.getHeaders(),
        ...options.headers,
      },
    };

    try {
      const response = await fetch(url, config);

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      return {
        success: true,
        data,
      };
    } catch (error) {
      console.error('API request failed:', error);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  }

  // ============================================================================
  // Authentication
  // ============================================================================

  /**
   * Login with email and password
   */
  async login(email: string, password: string): Promise<ApiResponse<AuthResponse>> {
    const response = await this.request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });

    if (response.success && response.data) {
      this.saveToken(response.data.token);
    }

    return response;
  }

  /**
   * Register new user
   */
  async register(
    email: string,
    password: string,
    displayName: string
  ): Promise<ApiResponse<AuthResponse>> {
    const response = await this.request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName }),
    });

    if (response.success && response.data) {
      this.saveToken(response.data.token);
    }

    return response;
  }

  /**
   * Logout current user
   */
  async logout(): Promise<void> {
    this.clearToken();
  }

  /**
   * Bootstrap application data
   */
  async bootstrap(): Promise<ApiResponse<BootstrapData>> {
    return this.request<BootstrapData>('/api/bootstrap');
  }

  // ============================================================================
  // User & Portfolio
  // ============================================================================

  /**
   * Get current user
   */
  async getCurrentUser(): Promise<ApiResponse<User>> {
    return this.request<User>('/api/user');
  }

  /**
   * Get user portfolios
   */
  async getPortfolios(): Promise<ApiResponse<Portfolio[]>> {
    return this.request<Portfolio[]>('/api/portfolios');
  }

  /**
   * Get portfolio by ID
   */
  async getPortfolio(portfolioId: string): Promise<ApiResponse<Portfolio>> {
    return this.request<Portfolio>(`/api/portfolios/${portfolioId}`);
  }

  // ============================================================================
  // Trading Signals
  // ============================================================================

  /**
   * Get signals
   */
  async getSignals(afterSeq?: number, limit: number = 50): Promise<ApiResponse<Signal[]>> {
    const params = new URLSearchParams();
    if (afterSeq !== undefined) params.append('afterSeq', afterSeq.toString());
    params.append('limit', limit.toString());

    return this.request<Signal[]>(`/api/signals?${params.toString()}`);
  }

  /**
   * Get signal by ID
   */
  async getSignal(signalId: string): Promise<ApiResponse<Signal>> {
    return this.request<Signal>(`/api/signals/${signalId}`);
  }

  /**
   * Create trade intent from signal
   */
  async createTradeIntent(
    signalId: string,
    quantity: number,
    orderType: string
  ): Promise<ApiResponse<TradeIntent>> {
    return this.request<TradeIntent>('/api/trade-intents', {
      method: 'POST',
      body: JSON.stringify({ signalId, quantity, orderType }),
    });
  }

  // ============================================================================
  // Trades
  // ============================================================================

  /**
   * Get all trades
   */
  async getTrades(status?: string): Promise<ApiResponse<Trade[]>> {
    const params = status ? `?status=${status}` : '';
    return this.request<Trade[]>(`/api/trades${params}`);
  }

  /**
   * Get trade by ID
   */
  async getTrade(tradeId: string): Promise<ApiResponse<Trade>> {
    return this.request<Trade>(`/api/trades/${tradeId}`);
  }

  /**
   * Close trade
   */
  async closeTrade(tradeId: string): Promise<ApiResponse<Trade>> {
    return this.request<Trade>(`/api/trades/${tradeId}/close`, {
      method: 'POST',
    });
  }

  // ============================================================================
  // Orders
  // ============================================================================

  /**
   * Place order
   */
  async placeOrder(order: OrderRequest): Promise<ApiResponse<OrderResponse>> {
    return this.request<OrderResponse>('/api/orders', {
      method: 'POST',
      body: JSON.stringify(order),
    });
  }

  /**
   * Get orders
   */
  async getOrders(status?: string): Promise<ApiResponse<OrderResponse[]>> {
    const params = status ? `?status=${status}` : '';
    return this.request<OrderResponse[]>(`/api/orders${params}`);
  }

  /**
   * Cancel order
   */
  async cancelOrder(orderId: string): Promise<ApiResponse<OrderResponse>> {
    return this.request<OrderResponse>(`/api/orders/${orderId}/cancel`, {
      method: 'POST',
    });
  }

  // ============================================================================
  // Market Data
  // ============================================================================

  /**
   * Get market watch data
   */
  async getMarketWatch(): Promise<ApiResponse<MarketData[]>> {
    return this.request<MarketData[]>('/api/market-watch');
  }

  /**
   * Get quote for symbol
   */
  async getQuote(symbol: string): Promise<ApiResponse<MarketData>> {
    return this.request<MarketData>(`/api/quotes/${symbol}`);
  }

  /**
   * Search symbols
   */
  async searchSymbols(query: string): Promise<ApiResponse<string[]>> {
    return this.request<string[]>(`/api/symbols/search?q=${encodeURIComponent(query)}`);
  }

  // ============================================================================
  // Watchlist
  // ============================================================================

  /**
   * Get user watchlists
   */
  async getWatchlists(): Promise<ApiResponse<Watchlist[]>> {
    return this.request<Watchlist[]>('/api/watchlists');
  }

  /**
   * Create watchlist
   */
  async createWatchlist(name: string, symbols: string[]): Promise<ApiResponse<Watchlist>> {
    return this.request<Watchlist>('/api/watchlists', {
      method: 'POST',
      body: JSON.stringify({ name, symbols }),
    });
  }

  /**
   * Update watchlist
   */
  async updateWatchlist(
    watchlistId: string,
    name: string,
    symbols: string[]
  ): Promise<ApiResponse<Watchlist>> {
    return this.request<Watchlist>(`/api/watchlists/${watchlistId}`, {
      method: 'PUT',
      body: JSON.stringify({ name, symbols }),
    });
  }

  /**
   * Delete watchlist
   */
  async deleteWatchlist(watchlistId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`/api/watchlists/${watchlistId}`, {
      method: 'DELETE',
    });
  }

  // ============================================================================
  // Brokers
  // ============================================================================

  /**
   * Get user brokers
   */
  async getUserBrokers(): Promise<ApiResponse<UserBroker[]>> {
    return this.request<UserBroker[]>('/api/user-brokers');
  }

  /**
   * Connect broker (OAuth)
   */
  async connectBroker(brokerId: string): Promise<ApiResponse<{ authUrl: string }>> {
    return this.request<{ authUrl: string }>(`/api/brokers/${brokerId}/connect`, {
      method: 'POST',
    });
  }

  /**
   * Disconnect broker
   */
  async disconnectBroker(userBrokerId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`/api/user-brokers/${userBrokerId}/disconnect`, {
      method: 'POST',
    });
  }

  // ============================================================================
  // MTF Configuration
  // ============================================================================

  /**
   * Get MTF configuration
   */
  async getMTFConfig(): Promise<ApiResponse<MTFConfig>> {
    return this.request<MTFConfig>('/api/mtf-config');
  }

  /**
   * Update MTF configuration
   */
  async updateMTFConfig(config: MTFConfig): Promise<ApiResponse<MTFConfig>> {
    return this.request<MTFConfig>('/api/mtf-config', {
      method: 'PUT',
      body: JSON.stringify(config),
    });
  }

  // ============================================================================
  // Admin (Role-gated)
  // ============================================================================

  /**
   * Get all users (admin only)
   */
  async getAllUsers(): Promise<ApiResponse<User[]>> {
    return this.request<User[]>('/api/admin/users');
  }

  /**
   * Get all user brokers (admin only)
   */
  async getAllUserBrokers(): Promise<ApiResponse<UserBroker[]>> {
    return this.request<UserBroker[]>('/api/admin/user-brokers');
  }
}

/**
 * Export singleton instance
 */
export const apiClient = new ApiClient();

export default apiClient;
