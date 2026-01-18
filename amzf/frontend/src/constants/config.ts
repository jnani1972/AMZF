/**
 * Application Configuration
 * Central source of truth for app-wide settings
 */

export const APP_CONFIG = {
    API: {
        BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090',
        TIMEOUT: 15000, // 15s default timeout
    },
    WEBSOCKET: {
        BASE_URL: import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:9090',
        RECONNECT: {
            MAX_ATTEMPTS: 10,
            INITIAL_DELAY: 1000,
            MAX_DELAY: 30000,
        },
        HEARTBEAT: {
            INTERVAL: 60000,
        },
    },
    PAGINATION: {
        DEFAULT_PAGE_SIZE: 20,
    },
} as const;
