/**
 * WebSocket Client
 * Provides real-time data streaming with auto-reconnect
 */

import type { WsMessage, WsMessageType, WsSubscription } from '../types';

/**
 * WebSocket Configuration
 */
import { APP_CONFIG } from '../constants/config';

/**
 * WebSocket Configuration
 */
const WS_BASE_URL = APP_CONFIG.WEBSOCKET.BASE_URL;

/**
 * Connection State
 */
export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'error';

/**
 * Message Handler
 */
export type MessageHandler<T = unknown> = (data: T) => void;

/**
 * Connection Handler
 */
export type ConnectionHandler = (state: ConnectionState) => void;

/**
 * WebSocket Client Class
 */
class WebSocketClient {
  private ws: WebSocket | null = null;
  private url: string;
  private token: string | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = APP_CONFIG.WEBSOCKET.RECONNECT.MAX_ATTEMPTS;
  private reconnectDelay = APP_CONFIG.WEBSOCKET.RECONNECT.INITIAL_DELAY; // Start with 1 second
  private maxReconnectDelay = APP_CONFIG.WEBSOCKET.RECONNECT.MAX_DELAY; // Max 30 seconds
  private reconnectTimeout: NodeJS.Timeout | null = null;
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private heartbeatTimeout: NodeJS.Timeout | null = null;
  private messageHandlers: Map<WsMessageType, Set<MessageHandler>> = new Map();
  private connectionHandlers: Set<ConnectionHandler> = new Set();
  private subscriptions: Set<string> = new Set();
  private connectionState: ConnectionState = 'disconnected';

  constructor(wsUrl: string = WS_BASE_URL) {
    this.url = wsUrl;
    this.loadToken();
  }

  /**
   * Load auth token from localStorage
   */
  private loadToken(): void {
    this.token = localStorage.getItem('auth_token');
  }

  /**
   * Get connection state
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Set connection state and notify handlers
   */
  private setConnectionState(state: ConnectionState): void {
    this.connectionState = state;
    this.connectionHandlers.forEach((handler) => handler(state));
  }

  /**
   * Connect to WebSocket server
   */
  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN || this.ws?.readyState === WebSocket.CONNECTING) {
      return;
    }

    this.setConnectionState('connecting');
    this.loadToken();

    const wsUrl = this.token ? `${this.url}/ws?token=${this.token}` : `${this.url}/ws`;

    try {
      this.ws = new WebSocket(wsUrl);
      this.setupEventHandlers();
    } catch (error) {
      console.error('WebSocket connection failed:', error);
      this.setConnectionState('error');
      this.scheduleReconnect();
    }
  }

  /**
   * Setup WebSocket event handlers
   */
  private setupEventHandlers(): void {
    if (!this.ws) return;

    this.ws.onopen = () => {
      console.log('WebSocket connected');
      this.setConnectionState('connected');
      this.reconnectAttempts = 0;
      this.reconnectDelay = APP_CONFIG.WEBSOCKET.RECONNECT.INITIAL_DELAY;
      this.startHeartbeat();
      this.resubscribe();
    };

    this.ws.onmessage = (event) => {
      try {
        const message: WsMessage = JSON.parse(event.data);
        this.handleMessage(message);
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.setConnectionState('error');
    };

    this.ws.onclose = () => {
      console.log('WebSocket disconnected');
      this.setConnectionState('disconnected');
      this.stopHeartbeat();
      this.scheduleReconnect();
    };
  }

  /**
   * Handle incoming message
   */
  private handleMessage(message: WsMessage): void {
    // Handle ping/pong
    if (message.type === 'PING' || message.type === 'PONG' || message.type === 'HEARTBEAT') {
      // Ignore - connection is alive
      return;
    }

    // Dispatch to registered handlers
    const handlers = this.messageHandlers.get(message.type);
    if (handlers) {
      handlers.forEach((handler) => {
        try {
          handler(message.data);
        } catch (error) {
          console.error('Message handler error:', error);
        }
      });
    }
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    this.stopHeartbeat();
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }

    this.setConnectionState('disconnected');
  }

  /**
   * Schedule reconnection with exponential backoff
   */
  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnect attempts reached');
      return;
    }

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

    const delay = Math.min(
      this.reconnectDelay * Math.pow(2, this.reconnectAttempts),
      this.maxReconnectDelay
    );

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts + 1})`);

    this.reconnectTimeout = setTimeout(() => {
      this.reconnectAttempts++;
      this.connect();
    }, delay);
  }

  /**
   * Start heartbeat mechanism
   */
  private startHeartbeat(): void {
    this.stopHeartbeat();

    // Disabled heartbeat mechanism for now
    // The backend doesn't respond to HEARTBEAT messages yet
    // The browser will automatically detect broken WebSocket connections

    // Send heartbeat every 60 seconds (less aggressive)
    this.heartbeatInterval = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.send({ type: 'PING', data: {} });
      }
    }, APP_CONFIG.WEBSOCKET.HEARTBEAT.INTERVAL);

    // Don't enforce heartbeat timeout - let browser handle connection health
    // The WebSocket will automatically close if the connection is broken
  }



  /**
   * Stop heartbeat mechanism
   */
  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }

    if (this.heartbeatTimeout) {
      clearTimeout(this.heartbeatTimeout);
      this.heartbeatTimeout = null;
    }
  }

  /**
   * Send message to server
   */
  private send(message: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else if (this.connectionState === 'disconnected' || this.connectionState === 'error') {
      // Only warn if truly disconnected, not during initial connection
      console.warn('WebSocket not connected, cannot send message');
    }
    // Silently ignore during 'connecting' state - messages will be sent via resubscribe()
  }

  /**
   * Subscribe to topic
   */
  subscribe(topic: WsMessageType, symbols?: string[]): void {
    const subscription: WsSubscription = { topic, symbols };
    const key = JSON.stringify(subscription);

    if (!this.subscriptions.has(key)) {
      this.subscriptions.add(key);
      this.send({ type: 'SUBSCRIBE', data: subscription });
    }
  }

  /**
   * Unsubscribe from topic
   */
  unsubscribe(topic: WsMessageType, symbols?: string[]): void {
    const subscription: WsSubscription = { topic, symbols };
    const key = JSON.stringify(subscription);

    if (this.subscriptions.has(key)) {
      this.subscriptions.delete(key);
      this.send({ type: 'UNSUBSCRIBE', data: subscription });
    }
  }

  /**
   * Resubscribe to all topics after reconnection
   */
  private resubscribe(): void {
    this.subscriptions.forEach((key) => {
      const subscription: WsSubscription = JSON.parse(key);
      this.send({ type: 'SUBSCRIBE', data: subscription });
    });
  }

  /**
   * Register message handler
   */
  on<T = unknown>(type: WsMessageType, handler: MessageHandler<T>): () => void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, new Set());
    }

    this.messageHandlers.get(type)!.add(handler as MessageHandler);

    // Return unsubscribe function
    return () => {
      this.off(type, handler);
    };
  }

  /**
   * Unregister message handler
   */
  off<T = unknown>(type: WsMessageType, handler: MessageHandler<T>): void {
    const handlers = this.messageHandlers.get(type);
    if (handlers) {
      handlers.delete(handler as MessageHandler);
    }
  }

  /**
   * Register connection state handler
   */
  onConnectionChange(handler: ConnectionHandler): () => void {
    this.connectionHandlers.add(handler);

    // Return unsubscribe function
    return () => {
      this.connectionHandlers.delete(handler);
    };
  }

  /**
   * Clear all handlers
   */
  clearHandlers(): void {
    this.messageHandlers.clear();
    this.connectionHandlers.clear();
  }
}

/**
 * Export singleton instance
 */
export const wsClient = new WebSocketClient();

export default wsClient;
