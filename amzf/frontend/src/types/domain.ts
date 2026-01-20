/**
 * Domain Type Definitions
 * Maps backend domain models to TypeScript types
 */

// ============================================================================
// Common Types
// ============================================================================

export type EventType = 'SIGNAL' | 'TRADE' | 'ORDER' | 'BROKER' | 'SYSTEM';

export type EventScope = 'GLOBAL' | 'USER' | 'USER_BROKER';

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

// ============================================================================
// User & Portfolio
// ============================================================================

export interface User {
  userId: string;
  email: string;
  displayName: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  lastLoginAt?: string;
  suspendedAt?: string;
  suspendedBy?: string;
  activatedAt?: string;
  activatedBy?: string;
  statusHistory?: UserStatusChange[];
}

export type UserRole = 'USER' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';

export interface UserStatusChange {
  changedAt: string;
  changedBy: string;
  fromStatus: UserStatus;
  toStatus: UserStatus;
  reason?: string;
}

export interface Portfolio {
  id: string;
  userId: string;
  name: string;
  capital: number;
  allocatedCapital: number;
  availableCapital: number;
  totalValue: number;
  totalPnl: number;
  totalPnlPercent: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface WsSession {
  sessionId: string;
  userId: string;
  connectedAt: Date;
  lastHeartbeat: Date;
}

// ============================================================================
// Broker
// ============================================================================

export interface Broker {
  id: string;
  name: string;
  displayName: string;
  apiUrl: string;
  websocketUrl?: string;
  logoUrl?: string;
  supportedFeatures: string[];
  enabled: boolean;
}

export interface UserBroker {
  userBrokerId: string;
  userId: string;
  displayName: string;
  brokerId: string;
  brokerName: string;
  role: BrokerRole;
  connected: boolean;
  enabled: boolean;
  status: string;
  lastConnected?: string;
  connectionError?: string;
  createdAt: string;
}

export type BrokerRole = 'DATA' | 'EXEC';

export type BrokerHealthStatus = 'healthy' | 'degraded' | 'down' | 'unknown';

export interface UserBrokerSession {
  id: string;
  userBrokerId: string;
  accessToken: string;
  refreshToken?: string;
  expiresAt: Date;
  createdAt: Date;
}

export interface OAuthConfig {
  clientId: string;
  redirectUri: string;
  scopes: string[];
}

// ============================================================================
// Market Data
// ============================================================================

export interface Tick {
  symbol: string;
  ltp: number;
  volume: number;
  timestamp: Date;
  bid?: number;
  ask?: number;
  open?: number;
  high?: number;
  low?: number;
  close?: number;
}

export interface Candle {
  symbol: string;
  timeframe: Timeframe;
  timestamp: Date;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export type Timeframe = '1m' | '5m' | '15m' | '1h' | '1d';

export interface Instrument {
  symbol: string;
  name: string;
  exchange: string;
  lotSize: number;
  tickSize: number;
  instrumentType: InstrumentType;
  expiryDate?: Date;
  strikePrice?: number;
}

export type InstrumentType = 'EQUITY' | 'FUTURES' | 'OPTIONS' | 'CURRENCY';

export interface Watchlist {
  id?: string;
  userId?: string;
  symbol: string;
  lotSize?: number;
  tickSize?: number;
  lastPrice?: number;
  enabled?: boolean;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface WatchlistTemplate {
  templateId: string;
  templateName: string;
  description?: string;
  displayOrder?: number;
  enabled?: boolean;
  createdAt?: Date;
}

export interface WatchlistSelected {
  selectedId: string;
  name: string;
  sourceTemplateId: string;
  description?: string;
  enabled?: boolean;
  createdAt?: Date;
  symbolCount?: number;
}

export interface WatchlistTemplateSymbol {
  id: string;
  templateId: string;
  symbol: string;
  lotSize?: number;
  tickSize?: number;
  createdAt?: Date;
}

export interface MarketData {
  symbol: string;
  ltp: number;
  change: number;
  changePercent: number;
  volume: number;
  dayHigh: number;
  dayLow: number;
  fiftyTwoWeekHigh: number;
  fiftyTwoWeekLow: number;
  open: number;
  close: number;
  timestamp: Date;
}

// ============================================================================
// Trading Signals
// ============================================================================

export interface Signal {
  id: string;
  seq: number;
  symbol: string;
  direction: Direction;
  strength: SignalStrength;
  timeframe: Timeframe;
  entryPrice: number;
  stopLoss?: number;
  target?: number;
  reason: string;
  confidence: number;
  mtfAnalysis?: MTFAnalysis;
  generatedAt: Date;
  expiresAt?: Date;
}

export type Direction = 'BUY' | 'SELL';

export type SignalStrength = 'WEAK' | 'MODERATE' | 'STRONG' | 'VERY_STRONG';

export interface ExitSignal {
  id: string;
  tradeId: string;
  exitType: ExitType;
  exitPrice: number;
  reason: string;
  generatedAt: Date;
}

export type ExitType = 'TARGET' | 'STOP_LOSS' | 'TRAILING_STOP' | 'TIME_BASED' | 'MANUAL';

export interface MTFAnalysis {
  primary: TimeframeAnalysis;
  secondary: TimeframeAnalysis;
  tertiary: TimeframeAnalysis;
  confluenceStrength: ConfluenceStrength;
  recommendation: string;
}

export interface TimeframeAnalysis {
  timeframe: Timeframe;
  trend: Trend;
  support: number;
  resistance: number;
  indicators: Record<string, number>;
}

export type Trend = 'BULLISH' | 'BEARISH' | 'NEUTRAL';

export type ConfluenceStrength = 'WEAK' | 'MODERATE' | 'STRONG';

export interface MTFConfig {
  userId: string;
  primaryTimeframe: Timeframe;
  secondaryTimeframe: Timeframe;
  tertiaryTimeframe: Timeframe;
  indicators: string[];
}

/**
 * Global MTF Configuration (matches backend MtfGlobalConfig)
 * All 54 configuration fields for multi-timeframe analysis
 */
export interface MtfGlobalConfig {
  configId: string;

  // HTF (Higher Timeframe) Config - 125 minutes
  htfCandleCount: number;
  htfCandleMinutes: number;
  htfWeight: number;

  // ITF (Intermediate Timeframe) Config - 25 minutes
  itfCandleCount: number;
  itfCandleMinutes: number;
  itfWeight: number;

  // LTF (Lower Timeframe) Config - 1 minute
  ltfCandleCount: number;
  ltfCandleMinutes: number;
  ltfWeight: number;

  // Zone Detection
  buyZonePct: number;
  htfBuyZonePct?: number;
  itfBuyZonePct?: number;
  ltfBuyZonePct?: number;

  // Confluence Settings
  minConfluenceType: string;
  strengthThresholdVeryStrong: number;
  strengthThresholdStrong: number;
  strengthThresholdModerate: number;
  multiplierVeryStrong: number;
  multiplierStrong: number;
  multiplierModerate: number;
  multiplierWeak: number;

  // Log-Utility Constraints
  maxPositionLogLoss: number;
  maxPortfolioLogLoss: number;
  maxSymbolLogLoss: number;

  // Kelly Sizing
  kellyFraction: number;
  maxKellyMultiplier: number;

  // Entry Pricing
  useLimitOrders: boolean;
  entryOffsetPct: number;

  // Exit Targets
  minProfitPct: number;
  targetRMultiple: number;
  stretchRMultiple: number;
  useTrailingStop: boolean;
  trailingStopActivationPct: number;
  trailingStopDistancePct: number;

  // Averaging Re-Entry Gates
  minReentrySpacingAtrMultiplier: number;

  // Velocity Throttling
  rangeAtrThresholdWide: number;
  rangeAtrThresholdHealthy: number;
  rangeAtrThresholdTight: number;
  velocityMultiplierWide: number;
  velocityMultiplierHealthy: number;
  velocityMultiplierTight: number;
  velocityMultiplierCompressed: number;
  bodyRatioThresholdLow: number;
  bodyRatioThresholdCritical: number;
  bodyRatioPenaltyLow: number;
  bodyRatioPenaltyCritical: number;
  rangeLookbackBars: number;
  stressThrottleEnabled: boolean;
  maxStressDrawdown: number;

  // Utility Asymmetry Gate
  utilityAlpha: number;
  utilityBeta: number;
  utilityLambda: number;
  minAdvantageRatio: number;
  utilityGateEnabled: boolean;

  // Timestamps
  createdAt?: string;
  updatedAt?: string;
}

// ============================================================================
// Trading & Orders
// ============================================================================

export interface Trade {
  id: string;
  userId: string;
  portfolioId: string;
  symbol: string;
  direction: Direction;
  quantity: number;
  entryPrice: number;
  exitPrice?: number;
  stopLoss?: number;
  target?: number;
  pnl?: number;
  pnlPercent?: number;
  status: TradeStatus;
  entryTime: Date;
  exitTime?: Date;
  signalId?: string;
  brokerOrderIds: string[];
}

export type TradeStatus = 'OPEN' | 'CLOSED' | 'CANCELLED';

export interface TradeIntent {
  id: string;
  userId: string;
  signalId: string;
  symbol: string;
  direction: Direction;
  quantity: number;
  orderType: OrderType;
  limitPrice?: number;
  stopPrice?: number;
  status: IntentStatus;
  createdAt: Date;
  executedAt?: Date;
}

export type IntentStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXECUTED' | 'CANCELLED';

export interface ExitIntent {
  id: string;
  tradeId: string;
  exitType: ExitType;
  quantity: number;
  orderType: OrderType;
  limitPrice?: number;
  status: IntentStatus;
  createdAt: Date;
  executedAt?: Date;
}

export interface TradeEvent {
  id: string;
  tradeId: string;
  eventType: TradeEventType;
  details: Record<string, unknown>;
  timestamp: Date;
}

export type TradeEventType =
  | 'ENTRY_ORDER_PLACED'
  | 'ENTRY_ORDER_FILLED'
  | 'STOP_LOSS_UPDATED'
  | 'TARGET_UPDATED'
  | 'EXIT_ORDER_PLACED'
  | 'EXIT_ORDER_FILLED'
  | 'TRADE_CLOSED';

// ============================================================================
// Orders
// ============================================================================

export interface OrderRequest {
  symbol: string;
  direction: Direction;
  quantity: number;
  orderType: OrderType;
  limitPrice?: number;
  stopPrice?: number;
  timeInForce: TimeInForce;
  productType: ProductType;
  tag?: string;
}

export interface OrderResponse {
  brokerOrderId: string;
  symbol: string;
  status: OrderStatus;
  direction: Direction;
  orderType: OrderType;
  productType: ProductType;
  quantity: number;
  filledQuantity: number;
  pendingQuantity: number;
  orderPrice?: number;
  avgFillPrice?: number;
  orderTime: Date;
  fillTime?: Date;
  statusMessage?: string;
  tag?: string;
  extendedData?: Record<string, unknown>;
}

export type OrderType = 'MARKET' | 'LIMIT' | 'STOP_LOSS' | 'STOP_LOSS_MARKET';

export type OrderStatus =
  | 'PENDING'
  | 'OPEN'
  | 'TRIGGER_PENDING'
  | 'COMPLETE'
  | 'CANCELLED'
  | 'REJECTED'
  | 'AFTER_MARKET_ORDER_REQ_RECEIVED'
  | 'MODIFY_PENDING'
  | 'CANCEL_PENDING';

export type TimeInForce = 'DAY' | 'IOC' | 'GTC';

export type ProductType = 'INTRADAY' | 'DELIVERY' | 'MARGIN';

// ============================================================================
// API Response Types
// ============================================================================

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
}

export interface BootstrapData {
  user: User;
  portfolios: Portfolio[];
  brokers: UserBroker[];
  settings: Record<string, unknown>;
}

// ============================================================================
// WebSocket Message Types
// ============================================================================

export interface WsMessage<T = unknown> {
  type: WsMessageType;
  scope: EventScope;
  data: T;
  timestamp: Date;
}

export type WsMessageType =
  | 'TICK'
  | 'SIGNAL'
  | 'TRADE_UPDATE'
  | 'ORDER_UPDATE'
  | 'BROKER_STATUS'
  | 'SYSTEM_ALERT'
  | 'HEARTBEAT'
  | 'PING'
  | 'PONG';

export interface WsSubscription {
  topic: string;
  symbols?: string[];
}

// ============================================================================
// Error Types
// ============================================================================

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  timestamp: Date;
}

export class ApiException extends Error {
  constructor(
    public code: string,
    message: string,
    public details?: Record<string, unknown>
  ) {
    super(message);
    this.name = 'ApiException';
  }
}
