package in.annupaper.application.port.input;

import in.annupaper.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeManagementService - Single Owner of Trade Lifecycle
 *
 * ENFORCEMENT CONTRACT:
 * - ONLY this service can create trade rows
 * - ONLY this service can transition trade states
 * - ONLY this service can place/modify/cancel orders
 * - ONLY this service can decide exit
 * - ONLY this service can close trades and compute P&L
 *
 * All other services must:
 * - Emit inputs (ticks, signals, broker updates)
 * - Query state (read-only)
 * - Persist via repository methods (not direct SQL)
 *
 * This is the ONLY writer per trade. All lifecycle mutations are serialized
 * through a partitioned executor (actor model) to prevent race conditions.
 *
 * See: TRADE_MANAGEMENT_ARCHITECTURE.md for full design.
 */
public interface TradeManagementService {

        /**
         * Create trade row for approved intent (SINGLE WRITER ENFORCEMENT).
         *
         * This is the ONLY method that creates trade rows in the system.
         * Called by OrderExecutionService before placing broker order.
         *
         * Steps:
         * 1. Calculate NEWBUY/REBUY classification
         * 2. Generate trade ID
         * 3. Create trade row with status=CREATED
         * 4. PERSIST to database (single-writer enforcement)
         * 5. Return trade for order placement
         *
         * @param intent Approved trade intent
         * @param signal Original entry signal
         * @return Created trade (status=CREATED, PERSISTED to DB)
         */
        Trade createTradeForIntent(TradeIntent intent, Signal signal);

        /**
         * Handle approved intent from validation layer.
         * Creates trade, calculates NEWBUY/REBUY, places entry order.
         *
         * State transition: NONE → CREATED → ENTRY_SUBMITTED
         *
         * @param intent Approved trade intent from validation
         */
        void onIntentApproved(TradeIntent intent);

        /**
         * Handle broker order update (fill, rejection, partial fill).
         * Updates trade state based on broker reality.
         *
         * State transitions:
         * - ENTRY_SUBMITTED → PENDING (accepted)
         * - PENDING → OPEN (filled)
         * - EXITING → CLOSED (exit filled)
         * - Any → REJECTED (broker rejection)
         *
         * @param update Broker order update
         */
        void onBrokerOrderUpdate(BrokerOrderUpdate update);

        /**
         * Handle market price update (tick or candle close).
         * Monitors OPEN trades for exit conditions.
         *
         * For each OPEN trade matching symbol:
         * - Check target hit
         * - Check stop loss breach
         * - Check time exit
         * - Apply brick movement filter
         * - If qualified: place exit order
         *
         * @param symbol    Symbol (e.g., "NSE:INFY")
         * @param ltp       Last traded price
         * @param timestamp Price timestamp
         */
        void onPriceUpdate(String symbol, BigDecimal ltp, Instant timestamp);

        /**
         * Reconcile pending trades with broker reality.
         * Called by scheduler (e.g., every 30 seconds).
         *
         * Queries broker for order status and heals any state drift.
         */
        void reconcilePendingTrades();

        /**
         * Update trailing stop prices for a trade.
         * Called by ExitSignalService when favorable price movement detected.
         *
         * Updates trade state with new highest price and trailing stop price.
         * Activates trailing stop if not already active and activation threshold
         * reached.
         *
         * @param tradeId      Trade to update
         * @param highestPrice Highest price reached since entry
         * @param stopPrice    Trailing stop price (highestPrice - distance%)
         * @param activate     Whether to activate trailing stop (first time threshold
         *                     reached)
         */
        void updateTrailingStop(String tradeId, BigDecimal highestPrice, BigDecimal stopPrice, boolean activate);

        /**
         * Update trade with exit order placement details (SINGLE WRITER ENFORCEMENT).
         * Called by ExitOrderExecutionService after broker accepts exit order.
         *
         * @param tradeId     Trade to update
         * @param exitOrderId Broker order ID
         * @param placedAt    Order placement timestamp
         */
        void updateTradeExitOrderPlaced(String tradeId, String exitOrderId, Instant placedAt);

        /**
         * Close trade after exit order fill (SINGLE WRITER ENFORCEMENT).
         * Called by ExitOrderReconciler after broker confirms exit fill.
         *
         * Calculates P&L, log return, holding period.
         * Updates trade status to CLOSED.
         *
         * @param tradeId       Trade to close
         * @param exitPrice     Exit fill price from broker
         * @param exitQty       Exit fill quantity
         * @param exitReason    Exit reason (TARGET_HIT, STOP_LOSS, etc.)
         * @param exitTimestamp Exit fill timestamp
         */
        void closeTradeOnExitFill(String tradeId, BigDecimal exitPrice, Integer exitQty, String exitReason,
                        Instant exitTimestamp);

        /**
         * Mark trade as REJECTED by intent ID (SINGLE WRITER ENFORCEMENT).
         * Called by OrderExecutionService when broker rejects order or execution fails.
         *
         * Finds trade by intentId, marks as REJECTED with error details.
         * If no trade found, logs warning (idempotent).
         *
         * @param intentId     Intent ID to find trade
         * @param errorCode    Error code from broker/system
         * @param errorMessage Error message
         */
        void markTradeRejectedByIntentId(String intentId, String errorCode, String errorMessage);

        /**
         * Rebuild active trade index from database.
         * Called on startup to populate in-memory index.
         */
        void rebuildActiveIndex();

        /**
         * Shutdown the service gracefully.
         * Waits for pending operations to complete.
         */
        void shutdown();
}
