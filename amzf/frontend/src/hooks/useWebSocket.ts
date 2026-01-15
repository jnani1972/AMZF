/**
 * WebSocket Hooks
 * React hooks for WebSocket client
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { wsClient, ConnectionState } from '@/lib/websocket';
import type { WsMessageType, Tick, Signal, Trade, OrderResponse } from '@/types';

/**
 * Hook to connect to WebSocket
 */
export function useWebSocket() {
  const [connectionState, setConnectionState] = useState<ConnectionState>(
    wsClient.getConnectionState()
  );

  useEffect(() => {
    // Connect on mount
    wsClient.connect();

    // Listen for connection state changes
    const unsubscribe = wsClient.onConnectionChange(setConnectionState);

    return () => {
      unsubscribe();
      // Don't disconnect on unmount - keep connection alive
    };
  }, []);

  return { connectionState, connected: connectionState === 'connected' };
}

/**
 * Hook to subscribe to WebSocket messages
 */
export function useWebSocketSubscription<T = unknown>(
  type: WsMessageType,
  handler: (data: T) => void,
  symbols?: string[]
) {
  const handlerRef = useRef(handler);

  // Update handler ref on changes
  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    // Subscribe to topic
    wsClient.subscribe(type, symbols);

    // Register message handler
    const unsubscribe = wsClient.on<T>(type, (data) => {
      handlerRef.current(data);
    });

    return () => {
      unsubscribe();
      wsClient.unsubscribe(type, symbols);
    };
  }, [type, symbols?.join(',')]);
}

/**
 * Hook to get real-time prices for symbols
 */
export function useRealtimePrices(symbols: string[]) {
  const [prices, setPrices] = useState<Map<string, Tick>>(new Map());
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());

  // Throttle updates to avoid excessive re-renders
  const throttleTimeout = useRef<NodeJS.Timeout | null>(null);
  const pendingUpdates = useRef<Map<string, Tick>>(new Map());

  const handleTick = useCallback((tick: Tick) => {
    pendingUpdates.current.set(tick.symbol, tick);

    if (!throttleTimeout.current) {
      throttleTimeout.current = setTimeout(() => {
        setPrices(new Map(pendingUpdates.current));
        setLastUpdate(new Date());
        throttleTimeout.current = null;
      }, 100); // Update at most every 100ms
    }
  }, []);

  useWebSocketSubscription<Tick>('TICK', handleTick, symbols);

  return { prices, lastUpdate };
}

/**
 * Hook to listen for signal updates
 */
export function useSignalUpdates(onSignal: (signal: Signal) => void) {
  useWebSocketSubscription<Signal>('SIGNAL', onSignal);
}

/**
 * Hook to listen for trade updates
 */
export function useTradeUpdates(onTrade: (trade: Trade) => void) {
  useWebSocketSubscription<Trade>('TRADE_UPDATE', onTrade);
}

/**
 * Hook to listen for order updates
 */
export function useOrderUpdates(onOrder: (order: OrderResponse) => void) {
  useWebSocketSubscription<OrderResponse>('ORDER_UPDATE', onOrder);
}

/**
 * Hook to listen for broker status updates
 */
export function useBrokerStatusUpdates(
  onStatusUpdate: (status: { brokerId: string; health: string; latency: number }) => void
) {
  useWebSocketSubscription('BROKER_STATUS', onStatusUpdate);
}

/**
 * Hook to listen for system alerts
 */
export function useSystemAlerts(onAlert: (alert: { message: string; severity: string }) => void) {
  useWebSocketSubscription('SYSTEM_ALERT', onAlert);
}

/**
 * Hook for real-time event stream
 */
export function useRealtimeEvents() {
  const [events, setEvents] = useState<Array<{ type: WsMessageType; data: unknown; timestamp: Date }>>([]);
  const maxEvents = 100;

  const handleSignal = useCallback((signal: Signal) => {
    setEvents((prev) => [
      { type: 'SIGNAL', data: signal, timestamp: new Date() },
      ...prev.slice(0, maxEvents - 1),
    ]);
  }, []);

  const handleTrade = useCallback((trade: Trade) => {
    setEvents((prev) => [
      { type: 'TRADE_UPDATE', data: trade, timestamp: new Date() },
      ...prev.slice(0, maxEvents - 1),
    ]);
  }, []);

  const handleOrder = useCallback((order: OrderResponse) => {
    setEvents((prev) => [
      { type: 'ORDER_UPDATE', data: order, timestamp: new Date() },
      ...prev.slice(0, maxEvents - 1),
    ]);
  }, []);

  useSignalUpdates(handleSignal);
  useTradeUpdates(handleTrade);
  useOrderUpdates(handleOrder);

  return { events };
}
