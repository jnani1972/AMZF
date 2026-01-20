/**
 * API Endpoints
 * Central source of truth for all API routes
 */

export const API_ENDPOINTS = {
    AUTH: {
        LOGIN: '/api/auth/login',
        REGISTER: '/api/auth/register',
    },
    BOOTSTRAP: '/api/bootstrap',
    USER: {
        PROFILE: '/api/user',
        PORTFOLIOS: '/api/portfolios',
    },
    MARKET: {
        SIGNALS: '/api/signals',
        TRADE_INTENTS: '/api/trade-intents',
        TRADES: '/api/trades',
        ORDERS: '/api/orders',
        MARKET_WATCH: '/api/market-watch',
        QUOTES: '/api/quotes', // + /{symbol}
        SYMBOL_SEARCH: '/api/symbols/search',
        // WATCHLISTS: '/api/watchlists', // Handled separately
    },
    WATCHLISTS: {
        BASE: '/api/watchlists',
    },
    BROKERS: {
        USER_BROKERS: '/api/user-brokers',
        CONNECT: (brokerId: string) => `/api/brokers/${brokerId}/connect`,
    },
    MTF: {
        CONFIG: '/api/mtf-config',
        GLOBAL_CONFIG: '/api/mtf-config/global',
    },
    ADMIN: {
        USERS: '/api/admin/users',
        USER_BROKERS: '/api/admin/user-brokers',
        BROKERS: '/api/admin/brokers',
        PORTFOLIOS: '/api/admin/portfolios',
        WATCHLIST: '/api/admin/watchlist',
        WATCHLIST_TEMPLATES: '/api/admin/watchlist-templates',
        WATCHLIST_SELECTED: '/api/admin/watchlist-selected',
        WATCHLIST_DEFAULT: '/api/admin/watchlist-default',
        SYSTEM_STATUS: '/api/admin/system-status',
    }
} as const;
