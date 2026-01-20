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
  MtfGlobalConfig,
} from '../types';

import { API_ENDPOINTS } from '../constants/apiEndpoints';
import { APP_CONFIG } from '../constants/config';

/**
 * API Configuration
 */
const API_BASE_URL = APP_CONFIG.API.BASE_URL;

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
   * Generic GET request
   */
  public async get(endpoint: string): Promise<any> {
    return this.request(endpoint, { method: 'GET' });
  }

  /**
   * Generic request wrapper
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
    const response = await this.request<AuthResponse>(API_ENDPOINTS.AUTH.LOGIN, {
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
    const response = await this.request<AuthResponse>(API_ENDPOINTS.AUTH.REGISTER, {
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
    return this.request<BootstrapData>(API_ENDPOINTS.BOOTSTRAP);
  }

  // ============================================================================
  // User & Portfolio
  // ============================================================================

  /**
   * Get current user
   */
  async getCurrentUser(): Promise<ApiResponse<User>> {
    return this.request<User>(API_ENDPOINTS.USER.PROFILE);
  }

  /**
   * Get user portfolios
   */
  async getPortfolios(): Promise<ApiResponse<Portfolio[]>> {
    return this.request<Portfolio[]>(API_ENDPOINTS.USER.PORTFOLIOS);
  }

  /**
   * Get portfolio by ID
   */
  async getPortfolio(portfolioId: string): Promise<ApiResponse<Portfolio>> {
    return this.request<Portfolio>(`${API_ENDPOINTS.USER.PORTFOLIOS}/${portfolioId}`);
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

    return this.request<Signal[]>(`${API_ENDPOINTS.MARKET.SIGNALS}?${params.toString()}`);
  }

  /**
   * Get signal by ID
   */
  async getSignal(signalId: string): Promise<ApiResponse<Signal>> {
    return this.request<Signal>(`${API_ENDPOINTS.MARKET.SIGNALS}/${signalId}`);
  }

  /**
   * Create trade intent from signal
   */
  async createTradeIntent(
    signalId: string,
    quantity: number,
    orderType: string
  ): Promise<ApiResponse<TradeIntent>> {
    return this.request<TradeIntent>(API_ENDPOINTS.MARKET.TRADE_INTENTS, {
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
    return this.request<Trade[]>(`${API_ENDPOINTS.MARKET.TRADES}${params}`);
  }

  /**
   * Get trade by ID
   */
  async getTrade(tradeId: string): Promise<ApiResponse<Trade>> {
    return this.request<Trade>(`${API_ENDPOINTS.MARKET.TRADES}/${tradeId}`);
  }

  /**
   * Close trade
   */
  async closeTrade(tradeId: string): Promise<ApiResponse<Trade>> {
    return this.request<Trade>(`${API_ENDPOINTS.MARKET.TRADES}/${tradeId}/close`, {
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
    return this.request<OrderResponse>(API_ENDPOINTS.MARKET.ORDERS, {
      method: 'POST',
      body: JSON.stringify(order),
    });
  }

  /**
   * Get orders
   */
  async getOrders(status?: string): Promise<ApiResponse<OrderResponse[]>> {
    const params = status ? `?status=${status}` : '';
    return this.request<OrderResponse[]>(`${API_ENDPOINTS.MARKET.ORDERS}${params}`);
  }

  /**
   * Cancel order
   */
  async cancelOrder(orderId: string): Promise<ApiResponse<OrderResponse>> {
    return this.request<OrderResponse>(`${API_ENDPOINTS.MARKET.ORDERS}/${orderId}/cancel`, {
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
    return this.request<MarketData[]>(API_ENDPOINTS.MARKET.MARKET_WATCH);
  }

  /**
   * Get quote for symbol
   */
  async getQuote(symbol: string): Promise<ApiResponse<MarketData>> {
    return this.request<MarketData>(`${API_ENDPOINTS.MARKET.QUOTES}/${symbol}`);
  }

  /**
   * Search symbols
   */
  async searchSymbols(query: string): Promise<ApiResponse<string[]>> {
    return this.request<string[]>(`${API_ENDPOINTS.MARKET.SYMBOL_SEARCH}?q=${encodeURIComponent(query)}`);
  }

  // ============================================================================
  // Watchlist
  // ============================================================================

  /**
   * Get user watchlists
   */
  async getWatchlists(): Promise<ApiResponse<Watchlist[]>> {
    return this.request<Watchlist[]>(API_ENDPOINTS.WATCHLISTS.BASE);
  }

  /**
   * Create watchlist
   */
  async createWatchlist(name: string, symbols: string[]): Promise<ApiResponse<Watchlist>> {
    return this.request<Watchlist>(API_ENDPOINTS.WATCHLISTS.BASE, {
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
    return this.request<Watchlist>(`${API_ENDPOINTS.WATCHLISTS.BASE}/${watchlistId}`, {
      method: 'PUT',
      body: JSON.stringify({ name, symbols }),
    });
  }

  /**
   * Delete watchlist
   */
  async deleteWatchlist(watchlistId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.WATCHLISTS.BASE}/${watchlistId}`, {
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
    return this.request<UserBroker[]>(API_ENDPOINTS.BROKERS.USER_BROKERS);
  }

  /**
   * Connect broker (OAuth)
   */
  async connectBroker(brokerId: string): Promise<ApiResponse<{ authUrl: string }>> {
    return this.request<{ authUrl: string }>(API_ENDPOINTS.BROKERS.CONNECT(brokerId), {
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
    return this.request<MTFConfig>(API_ENDPOINTS.MTF.CONFIG);
  }

  /**
   * Update MTF configuration
   */
  async updateMTFConfig(config: MTFConfig): Promise<ApiResponse<MTFConfig>> {
    return this.request<MTFConfig>(API_ENDPOINTS.MTF.CONFIG, {
      method: 'PUT',
      body: JSON.stringify(config),
    });
  }

  /**
   * Get global MTF configuration (admin only)
   */
  async getGlobalMTFConfig(): Promise<ApiResponse<MtfGlobalConfig>> {
    return this.request<MtfGlobalConfig>(API_ENDPOINTS.MTF.GLOBAL_CONFIG);
  }

  /**
   * Update global MTF configuration (admin only)
   */
  async updateGlobalMTFConfig(config: Partial<MtfGlobalConfig>): Promise<ApiResponse<MtfGlobalConfig>> {
    return this.request<MtfGlobalConfig>(API_ENDPOINTS.MTF.GLOBAL_CONFIG, {
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
    return this.request<User[]>(API_ENDPOINTS.ADMIN.USERS);
  }

  /**
   * Update user (admin only)
   */
  async updateUser(
    userId: string,
    data: { displayName: string; role: string }
  ): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.USERS}/${userId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  /**
   * Toggle user status between ACTIVE and SUSPENDED (admin only)
   */
  async toggleUserStatus(userId: string, reason?: string): Promise<ApiResponse<{ status: string }>> {
    return this.request<{ status: string }>(`${API_ENDPOINTS.ADMIN.USERS}/${userId}/toggle`, {
      method: 'POST',
      body: reason ? JSON.stringify({ reason }) : undefined,
    });
  }

  /**
   * Delete user (admin only)
   */
  async deleteUser(userId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.USERS}/${userId}`, {
      method: 'DELETE',
    });
  }

  /**
   * Get all user brokers (admin only)
   */
  async getAllUserBrokers(): Promise<ApiResponse<UserBroker[]>> {
    return this.request<UserBroker[]>(API_ENDPOINTS.ADMIN.USER_BROKERS);
  }

  /**
   * Create user broker (admin only)
   */
  async createUserBroker(data: {
    userId: string;
    brokerId: string;
    brokerRole: string;
  }): Promise<ApiResponse<UserBroker>> {
    return this.request<UserBroker>(API_ENDPOINTS.ADMIN.USER_BROKERS, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * Delete user broker (admin only)
   */
  async deleteUserBroker(userBrokerId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.USER_BROKERS}/${userBrokerId}`, {
      method: 'DELETE',
    });
  }

  /**
   * Toggle user broker active status (admin only)
   */
  async toggleUserBroker(userBrokerId: string): Promise<ApiResponse<UserBroker>> {
    return this.request<UserBroker>(`${API_ENDPOINTS.ADMIN.USER_BROKERS}/${userBrokerId}/toggle`, {
      method: 'POST',
    });
  }

  /**
   * Update user broker (admin only)
   */
  async updateUserBroker(
    userBrokerId: string,
    updates: { role?: string; enabled?: boolean; credentials?: any }
  ): Promise<ApiResponse<UserBroker>> {
    return this.request<UserBroker>(`${API_ENDPOINTS.ADMIN.USER_BROKERS}/${userBrokerId}`, {
      method: 'PUT',
      body: JSON.stringify(updates),
    });
  }

  /**
   * Test broker connection (admin only)
   */
  async testBrokerConnection(userBrokerId: string): Promise<ApiResponse<any>> {
    return this.request<any>(`${API_ENDPOINTS.ADMIN.BROKERS}/${userBrokerId}/test-connection`, {
      method: 'POST',
    });
  }

  /**
   * Disconnect broker (admin only)
   */
  async disconnectBroker(userBrokerId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.BROKERS}/${userBrokerId}/disconnect`, {
      method: 'POST',
    });
  }

  /**
   * Get broker session info (admin only)
   */
  async getBrokerSession(userBrokerId: string): Promise<ApiResponse<any>> {
    return this.request<any>(`${API_ENDPOINTS.ADMIN.BROKERS}/${userBrokerId}/session`);
  }

  /**
   * Get OAuth URL for broker (admin only)
   */
  async getOAuthUrl(userBrokerId: string): Promise<ApiResponse<{ url: string }>> {
    return this.request<{ url: string }>(`${API_ENDPOINTS.ADMIN.BROKERS}/${userBrokerId}/oauth-url`);
  }

  /**
   * Get all portfolios (admin only)
   */
  async getAllPortfolios(): Promise<ApiResponse<Portfolio[]>> {
    return this.request<Portfolio[]>(API_ENDPOINTS.ADMIN.PORTFOLIOS);
  }

  /**
   * Create portfolio (admin only)
   */
  async createPortfolio(data: {
    userId: string;
    name: string;
    totalCapital: number;
  }): Promise<ApiResponse<Portfolio>> {
    return this.request<Portfolio>(API_ENDPOINTS.ADMIN.PORTFOLIOS, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * Update portfolio (admin only)
   */
  async updatePortfolio(
    portfolioId: string,
    data: {
      name?: string;
      capital?: number;
    }
  ): Promise<ApiResponse<Portfolio>> {
    return this.request<Portfolio>(`${API_ENDPOINTS.ADMIN.PORTFOLIOS}/${portfolioId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  /**
   * Delete portfolio (admin only)
   */
  async deletePortfolio(portfolioId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.PORTFOLIOS}/${portfolioId}`, {
      method: 'DELETE',
    });
  }

  /**
   * Get watchlist (admin only)
   */
  async getWatchlist(): Promise<ApiResponse<Watchlist[]>> {
    return this.request<Watchlist[]>(API_ENDPOINTS.ADMIN.WATCHLIST);
  }

  /**
   * Add watchlist item (admin only)
   */
  async addWatchlistItem(data: {
    userId: string;
    symbol: string;
    lotSize?: number;
  }): Promise<ApiResponse<Watchlist>> {
    return this.request<Watchlist>(API_ENDPOINTS.ADMIN.WATCHLIST, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * Delete watchlist item (admin only)
   */
  async deleteWatchlistItem(id: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.WATCHLIST}/${id}`, {
      method: 'DELETE',
    });
  }

  /**
   * Toggle watchlist item (admin only)
   */
  async toggleWatchlistItem(id: string): Promise<ApiResponse<Watchlist>> {
    return this.request<Watchlist>(`${API_ENDPOINTS.ADMIN.WATCHLIST}/${id}/toggle`, {
      method: 'POST',
    });
  }

  /**
   * Update watchlist item (admin only)
   */
  async updateWatchlistItem(
    id: string,
    data: {
      lotSize?: number | null;
      tickSize?: number | null;
      enabled?: boolean;
    }
  ): Promise<ApiResponse<Watchlist>> {
    return this.request<Watchlist>(`${API_ENDPOINTS.ADMIN.WATCHLIST}/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  /**
   * Get data broker configuration (admin only)
   */
  async getDataBroker(): Promise<ApiResponse<any>> {
    return this.request<any>('/api/admin/data-broker');
  }

  /**
   * Configure data broker (admin only)
   */
  async configureDataBroker(data: { userBrokerId: string }): Promise<ApiResponse<any>> {
    return this.request<any>('/api/admin/data-broker', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * Get system health (admin only)
   */
  async getSystemHealth(): Promise<ApiResponse<any>> {
    return this.request<any>('/api/health');
  }
  // ============================================================================
  // Watchlist Templates (Admin)
  // ============================================================================

  /**
   * Get all active watchlist templates
   */
  async getWatchlistTemplates(): Promise<ApiResponse<any[]>> {
    return this.request<any[]>(API_ENDPOINTS.ADMIN.WATCHLIST_TEMPLATES);
  }

  /**
   * Create new watchlist template
   */
  async createWatchlistTemplate(data: { templateName: string; description?: string; displayOrder?: number }): Promise<ApiResponse<any>> {
    return this.request<any>(API_ENDPOINTS.ADMIN.WATCHLIST_TEMPLATES, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * Delete watchlist template
   */
  async deleteWatchlistTemplate(templateId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.WATCHLIST_TEMPLATES}/${templateId}`, {
      method: 'DELETE',
    });
  }

  /**
   * Get symbols for a template
   */
  async getTemplateSymbols(templateId: string): Promise<ApiResponse<any[]>> {
    return this.request<any[]>(`${API_ENDPOINTS.ADMIN.WATCHLIST_TEMPLATES}/${templateId}/symbols`);
  }

  /**
   * Add symbol to template
   */
  async addSymbolToTemplate(templateId: string, symbol: string): Promise<ApiResponse<any>> {
    return this.request<any>(`${API_ENDPOINTS.ADMIN.WATCHLIST_TEMPLATES}/${templateId}/symbols`, {
      method: 'POST',
      body: JSON.stringify({ symbol }),
    });
  }

  /**
   * Remove symbol from template
   */
  async removeSymbolFromTemplate(symbolId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.WATCHLIST_TEMPLATES}/symbols/${symbolId}`, {
      method: 'DELETE',
    });
  }

  // ============================================================================
  // Selected Watchlists (Admin)
  // ============================================================================

  /**
   * Get all selected watchlists
   */
  async getSelectedWatchlists(): Promise<ApiResponse<any[]>> {
    return this.request<any[]>(API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED);
  }

  /**
   * Get symbols for a selected watchlist
   */
  async getSelectedWatchlistSymbols(selectedId: string): Promise<ApiResponse<any[]>> {
    return this.request<any[]>(`${API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED}/${selectedId}/symbols`);
  }

  /**
   * Create selected watchlist from template
   */
  async createSelectedWatchlist(data: {
    sourceTemplateId: string;
    symbols: string[];
  }): Promise<ApiResponse<{ selectedId: string }>> {
    return this.request<{ selectedId: string }>(API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * Update symbols in selected watchlist
   */
  async updateSelectedWatchlistSymbols(
    selectedId: string,
    symbols: string[]
  ): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED}/${selectedId}/symbols`, {
      method: 'PUT',
      body: JSON.stringify({ symbols }),
    });
  }

  /**
   * Delete selected watchlist
   */
  async deleteSelectedWatchlist(selectedId: string): Promise<ApiResponse<void>> {
    return this.request<void>(`${API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED}/${selectedId}`, {
      method: 'DELETE',
    });
  }

  /**
   * Get merged default watchlist (Level 3)
   */
  async getDefaultWatchlist(): Promise<ApiResponse<string[]>> {
    return this.request<string[]>(API_ENDPOINTS.ADMIN.WATCHLIST_DEFAULT);
  }

  /**
   * Batch add symbols to user's watchlist
   */
  async batchAddWatchlistSymbols(data: {
    userBrokerId: string;
    symbols: string[];
  }): Promise<ApiResponse<{ added: number; skipped: number; skippedSymbols?: string[] }>> {
    return this.request<{ added: number; skipped: number; skippedSymbols?: string[] }>(
      `${API_ENDPOINTS.ADMIN.WATCHLIST}/batch-add`,
      {
        method: 'POST',
        body: JSON.stringify(data),
      }
    );
  }

  /**
   * Batch delete watchlist items
   */
  async batchDeleteWatchlistItems(ids: number[]): Promise<ApiResponse<{ deleted: number }>> {
    return this.request<{ deleted: number }>(`${API_ENDPOINTS.ADMIN.WATCHLIST}/batch-delete`, {
      method: 'DELETE',
      body: JSON.stringify({ ids }),
    });
  }

  /**
   * Batch toggle watchlist items
   */
  async batchToggleWatchlistItems(
    ids: number[],
    enabled: boolean
  ): Promise<ApiResponse<{ toggled: number }>> {
    return this.request<{ toggled: number }>(`${API_ENDPOINTS.ADMIN.WATCHLIST}/batch-toggle`, {
      method: 'POST',
      body: JSON.stringify({ ids, enabled }),
    });
  }
}

/**
 * Export singleton instance
 */
export const apiClient = new ApiClient();

export default apiClient;
