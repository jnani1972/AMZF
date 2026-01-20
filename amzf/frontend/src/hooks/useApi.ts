/**
 * API Hooks
 * React hooks for API client methods
 */

import { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../lib/api';
import type {
  ApiResponse,
  OrderRequest,
  OrderResponse,
  TradeIntent,
  Trade,
} from '../types';

/**
 * Hook state
 */
export interface UseApiState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

/**
 * Generic API hook
 */
export function useApi<T>(
  apiMethod: () => Promise<ApiResponse<T>>,
  deps: unknown[] = []
): UseApiState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);

    const response = await apiMethod();

    if (response.success && response.data) {
      setData(response.data);
    } else {
      setError(response.error || 'Unknown error');
    }

    setLoading(false);
  }, [apiMethod]);

  useEffect(() => {
    fetchData();
  }, deps);

  return { data, loading, error, refetch: fetchData };
}

/**
 * Hook to get current user
 */
export function useCurrentUser() {
  return useApi(() => apiClient.getCurrentUser());
}

/**
 * Hook to get portfolios
 */
export function usePortfolios() {
  return useApi(() => apiClient.getPortfolios());
}

/**
 * Hook to get signals
 */
export function useSignals(afterSeq?: number, limit?: number) {
  return useApi(() => apiClient.getSignals(afterSeq, limit), [afterSeq, limit]);
}

/**
 * Hook to get trades
 */
export function useTrades(status?: string) {
  return useApi(() => apiClient.getTrades(status), [status]);
}

/**
 * Hook to get orders
 */
export function useOrders(status?: string) {
  return useApi(() => apiClient.getOrders(status), [status]);
}

/**
 * Hook to get market watch
 */
export function useMarketWatch() {
  return useApi(() => apiClient.getMarketWatch());
}

/**
 * Hook to get watchlists
 */
export function useWatchlists() {
  return useApi(() => apiClient.getWatchlists());
}

/**
 * Hook to get user brokers
 */
export function useUserBrokers() {
  return useApi(() => apiClient.getUserBrokers());
}

/**
 * Hook to get all users (admin only)
 */
export function useAllUsers() {
  return useApi(() => apiClient.getAllUsers());
}

/**
 * Hook to get all user brokers (admin only)
 */
export function useAllUserBrokers() {
  return useApi(() => apiClient.getAllUserBrokers());
}

/**
 * Hook to get all portfolios (admin only)
 */
export function useAllPortfolios() {
  return useApi(() => apiClient.getAllPortfolios());
}

/**
 * Hook to get watchlist (admin only)
 */
export function useAdminWatchlist() {
  return useApi(() => apiClient.getWatchlist());
}

/**
 * Hook to get watchlist templates (admin only)
 */
export function useWatchlistTemplates() {
  return useApi(() => apiClient.getWatchlistTemplates());
}

/**
 * Hook to get template symbols (admin only)
 */
export function useTemplateSymbols(templateId: string | null) {
  return useApi(
    () => (templateId ? apiClient.getTemplateSymbols(templateId) : Promise.resolve({ success: true, data: [] })),
    [templateId]
  );
}

/**
 * Hook to get data broker configuration (admin only)
 */
export function useDataBroker() {
  return useApi(() => apiClient.getDataBroker());
}

/**
 * Hook to get system health
 */
export function useSystemHealth() {
  return useApi(() => apiClient.getSystemHealth());
}

/**
 * Hook for API mutations
 */
export interface UseMutationState<T, V> {
  data: T | null;
  loading: boolean;
  error: string | null;
  mutate: (variables: V) => Promise<T | null>;
  reset: () => void;
}

export function useMutation<T, V = void>(
  apiMethod: (variables: V) => Promise<ApiResponse<T>>
): UseMutationState<T, V> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutate = useCallback(
    async (variables: V): Promise<T | null> => {
      setLoading(true);
      setError(null);

      const response = await apiMethod(variables);

      if (response.success && response.data) {
        setData(response.data);
        setLoading(false);
        return response.data;
      } else {
        setError(response.error || 'Unknown error');
        setLoading(false);
        return null;
      }
    },
    [apiMethod]
  );

  const reset = useCallback(() => {
    setData(null);
    setError(null);
    setLoading(false);
  }, []);

  return { data, loading, error, mutate, reset };
}

/**
 * Hook to place order
 */
export function usePlaceOrder() {
  return useMutation<OrderResponse, OrderRequest>((order: OrderRequest) =>
    apiClient.placeOrder(order)
  );
}

/**
 * Variables for creating trade intent
 */
export interface CreateTradeIntentVariables {
  signalId: string;
  quantity: number;
  orderType: string;
}

/**
 * Hook to create trade intent
 */
export function useCreateTradeIntent() {
  return useMutation<TradeIntent, CreateTradeIntentVariables>(
    ({ signalId, quantity, orderType }: CreateTradeIntentVariables) =>
      apiClient.createTradeIntent(signalId, quantity, orderType)
  );
}

/**
 * Hook to close trade
 */
export function useCloseTrade() {
  return useMutation<Trade, string>((tradeId: string) =>
    apiClient.closeTrade(tradeId)
  );
}
