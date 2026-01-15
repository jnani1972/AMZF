import type { Preview } from '@storybook/react-vite';
import { ThemeProvider } from '../src/lib/theme';
import '../src/styles/theme.css';
import React from 'react';

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },

    // Accessibility addon configuration
    a11y: {
      test: 'todo',
      config: {
        rules: [
          {
            id: 'color-contrast',
            enabled: true,
          },
          {
            id: 'label',
            enabled: true,
          },
        ],
      },
    },

    // Viewport addon configuration
    viewport: {
      viewports: {
        mobile: {
          name: 'Mobile',
          styles: {
            width: '390px',
            height: '844px',
          },
        },
        mobileSE: {
          name: 'Mobile SE',
          styles: {
            width: '320px',
            height: '568px',
          },
        },
        tablet: {
          name: 'Tablet',
          styles: {
            width: '768px',
            height: '1024px',
          },
        },
        tabletPro: {
          name: 'Tablet Pro',
          styles: {
            width: '1024px',
            height: '1366px',
          },
        },
        desktop: {
          name: 'Desktop',
          styles: {
            width: '1440px',
            height: '900px',
          },
        },
        wide: {
          name: 'Wide',
          styles: {
            width: '1920px',
            height: '1080px',
          },
        },
      },
    },

    // Layout configuration
    layout: 'padded',

    // Backgrounds configuration
    backgrounds: {
      default: 'light',
      values: [
        {
          name: 'light',
          value: '#ffffff',
        },
        {
          name: 'dark',
          value: '#0f172a',
        },
      ],
    },
  },

  // Global decorators
  decorators: [
    (Story) => (
      <ThemeProvider>
        <div
          style={{
            fontFamily: 'var(--font-family-sans)',
            backgroundColor: 'var(--color-bg-primary)',
            color: 'var(--color-text-primary)',
            padding: '1rem',
          }}
        >
          <Story />
        </div>
      </ThemeProvider>
    ),
  ],
};

export default preview;
