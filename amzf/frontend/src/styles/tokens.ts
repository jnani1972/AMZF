/**
 * Design Tokens for AMZF Trading Platform
 * Central source of truth for all design system values
 */

export const colors = {
  // Brand Colors
  brand: {
    primary: '#1e40af', // blue-800
    secondary: '#3b82f6', // blue-500
    accent: '#60a5fa', // blue-400
  },

  // Trading Colors
  trading: {
    profit: '#10b981', // green-500
    profitLight: '#34d399', // green-400
    profitDark: '#059669', // green-600
    loss: '#ef4444', // red-500
    lossLight: '#f87171', // red-400
    lossDark: '#dc2626', // red-600
    neutral: '#6b7280', // gray-500
  },

  // Light Theme
  light: {
    background: {
      primary: '#ffffff', // white
      secondary: '#f8fafc', // slate-50
      tertiary: '#f1f5f9', // slate-100
    },
    surface: {
      primary: '#ffffff', // white
      secondary: '#f8fafc', // slate-50
      elevated: '#ffffff', // white with shadow
    },
    text: {
      primary: '#0f172a', // slate-900
      secondary: '#64748b', // slate-500
      tertiary: '#94a3b8', // slate-400
      inverse: '#ffffff', // white
    },
    border: {
      light: '#e2e8f0', // slate-200
      medium: '#cbd5e1', // slate-300
      dark: '#94a3b8', // slate-400
    },
  },

  // Dark Theme
  dark: {
    background: {
      primary: '#0f172a', // slate-900
      secondary: '#1e293b', // slate-800
      tertiary: '#334155', // slate-700
    },
    surface: {
      primary: '#1e293b', // slate-800
      secondary: '#334155', // slate-700
      elevated: '#475569', // slate-600
    },
    text: {
      primary: '#f1f5f9', // slate-100
      secondary: '#94a3b8', // slate-400
      tertiary: '#64748b', // slate-500
      inverse: '#0f172a', // slate-900
    },
    border: {
      light: '#334155', // slate-700
      medium: '#475569', // slate-600
      dark: '#64748b', // slate-500
    },
  },

  // Semantic Colors
  semantic: {
    success: '#10b981', // green-500
    warning: '#f59e0b', // amber-500
    error: '#ef4444', // red-500
    info: '#3b82f6', // blue-500
  },

  // Status Colors
  status: {
    active: '#10b981', // green-500
    inactive: '#6b7280', // gray-500
    pending: '#f59e0b', // amber-500
    failed: '#ef4444', // red-500
  },
} as const;

export const spacing = {
  xs: '4px',
  sm: '8px',
  md: '16px',
  lg: '24px',
  xl: '32px',
  '2xl': '48px',
  '3xl': '64px',
  '4xl': '96px',
} as const;

export const typography = {
  fontFamily: {
    sans: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif",
    mono: "'Fira Code', 'Consolas', 'Monaco', monospace",
  },
  fontSize: {
    xs: '12px',
    sm: '14px',
    base: '16px',
    lg: '18px',
    xl: '20px',
    '2xl': '24px',
    '3xl': '30px',
    '4xl': '36px',
  },
  lineHeight: {
    tight: 1.2,
    normal: 1.5,
    relaxed: 1.75,
  },
  fontWeight: {
    regular: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },
} as const;

export const borderRadius = {
  none: '0',
  sm: '4px',
  md: '8px',
  lg: '12px',
  xl: '16px',
  full: '9999px',
} as const;

export const shadows = {
  sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
  md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
  lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
  xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
} as const;

export const breakpoints = {
  mobile: 320,
  tablet: 769,
  desktop: 1025,
  wide: 1440,
} as const;

export const zIndex = {
  base: 0,
  dropdown: 1000,
  sticky: 1020,
  fixed: 1030,
  modalBackdrop: 1040,
  modal: 1050,
  popover: 1060,
  tooltip: 1070,
} as const;

export const transitions = {
  fast: '150ms ease-in-out',
  normal: '250ms ease-in-out',
  slow: '350ms ease-in-out',
} as const;

// Type exports for TypeScript
export type ColorTokens = typeof colors;
export type SpacingTokens = typeof spacing;
export type TypographyTokens = typeof typography;
export type BorderRadiusTokens = typeof borderRadius;
export type ShadowTokens = typeof shadows;
export type BreakpointTokens = typeof breakpoints;
export type ZIndexTokens = typeof zIndex;
export type TransitionTokens = typeof transitions;

// Combined design tokens type
export type DesignTokens = {
  colors: ColorTokens;
  spacing: SpacingTokens;
  typography: TypographyTokens;
  borderRadius: BorderRadiusTokens;
  shadows: ShadowTokens;
  breakpoints: BreakpointTokens;
  zIndex: ZIndexTokens;
  transitions: TransitionTokens;
};

// Export all tokens as a single object
export const tokens: DesignTokens = {
  colors,
  spacing,
  typography,
  borderRadius,
  shadows,
  breakpoints,
  zIndex,
  transitions,
} as const;
