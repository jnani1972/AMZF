import type { Meta, StoryObj } from '@storybook/react';
import { Button } from './Button';
import { ArrowRight, ShoppingCart, TrendingUp, TrendingDown } from 'lucide-react';

/**
 * Button component for actions and interactions.
 *
 * ## Features
 * - Multiple variants: primary, secondary, buy, sell, ghost
 * - Three sizes: sm, md, lg
 * - Loading state with spinner
 * - Icon support (left and right)
 * - Full width option
 * - Fully accessible (WCAG 2.1 AA)
 * - Touch-friendly (44px minimum on mobile)
 */
const meta: Meta<typeof Button> = {
  title: 'Atoms/Button',
  component: Button,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'A versatile button component with multiple variants and sizes. Includes trading-specific variants (buy/sell) with appropriate colors.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['primary', 'secondary', 'buy', 'sell', 'ghost'],
      description: 'Visual style variant',
      table: {
        defaultValue: { summary: 'primary' },
      },
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'Button size',
      table: {
        defaultValue: { summary: 'md' },
      },
    },
    fullWidth: {
      control: 'boolean',
      description: 'Whether the button takes full width',
      table: {
        defaultValue: { summary: 'false' },
      },
    },
    loading: {
      control: 'boolean',
      description: 'Loading state',
      table: {
        defaultValue: { summary: 'false' },
      },
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
      table: {
        defaultValue: { summary: 'false' },
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof Button>;

/**
 * Default button with primary variant
 */
export const Primary: Story = {
  args: {
    children: 'Primary Button',
    variant: 'primary',
    size: 'md',
  },
};

/**
 * Secondary button with border
 */
export const Secondary: Story = {
  args: {
    children: 'Secondary Button',
    variant: 'secondary',
    size: 'md',
  },
};

/**
 * Buy button for trading actions
 */
export const Buy: Story = {
  args: {
    children: 'Place Buy Order',
    variant: 'buy',
    size: 'md',
  },
};

/**
 * Sell button for trading actions
 */
export const Sell: Story = {
  args: {
    children: 'Place Sell Order',
    variant: 'sell',
    size: 'md',
  },
};

/**
 * Ghost button with transparent background
 */
export const Ghost: Story = {
  args: {
    children: 'Ghost Button',
    variant: 'ghost',
    size: 'md',
  },
};

/**
 * Small button
 */
export const Small: Story = {
  args: {
    children: 'Small Button',
    variant: 'primary',
    size: 'sm',
  },
};

/**
 * Medium button (default)
 */
export const Medium: Story = {
  args: {
    children: 'Medium Button',
    variant: 'primary',
    size: 'md',
  },
};

/**
 * Large button
 */
export const Large: Story = {
  args: {
    children: 'Large Button',
    variant: 'primary',
    size: 'lg',
  },
};

/**
 * Button with loading state
 */
export const Loading: Story = {
  args: {
    children: 'Loading...',
    variant: 'primary',
    loading: true,
  },
};

/**
 * Disabled button
 */
export const Disabled: Story = {
  args: {
    children: 'Disabled Button',
    variant: 'primary',
    disabled: true,
  },
};

/**
 * Full width button
 */
export const FullWidth: Story = {
  args: {
    children: 'Full Width Button',
    variant: 'primary',
    fullWidth: true,
  },
  parameters: {
    layout: 'padded',
  },
};

/**
 * Button with left icon
 */
export const WithLeftIcon: Story = {
  args: {
    children: 'Add to Cart',
    variant: 'primary',
    iconLeft: <ShoppingCart size={16} />,
  },
};

/**
 * Button with right icon
 */
export const WithRightIcon: Story = {
  args: {
    children: 'Continue',
    variant: 'primary',
    iconRight: <ArrowRight size={16} />,
  },
};

/**
 * Buy order with icon
 */
export const BuyWithIcon: Story = {
  args: {
    children: 'Buy Now',
    variant: 'buy',
    size: 'lg',
    iconLeft: <TrendingUp size={20} />,
  },
};

/**
 * Sell order with icon
 */
export const SellWithIcon: Story = {
  args: {
    children: 'Sell Now',
    variant: 'sell',
    size: 'lg',
    iconLeft: <TrendingDown size={20} />,
  },
};

/**
 * All variants showcase
 */
export const AllVariants: Story = {
  render: () => (
    <div className="flex flex-col gap-4 min-w-[300px]">
      <Button variant="primary">Primary</Button>
      <Button variant="secondary">Secondary</Button>
      <Button variant="buy">Buy</Button>
      <Button variant="sell">Sell</Button>
      <Button variant="ghost">Ghost</Button>
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'All available button variants displayed together.',
      },
    },
  },
};

/**
 * All sizes showcase
 */
export const AllSizes: Story = {
  render: () => (
    <div className="flex items-center gap-4">
      <Button size="sm">Small</Button>
      <Button size="md">Medium</Button>
      <Button size="lg">Large</Button>
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'All available button sizes displayed together.',
      },
    },
  },
};

/**
 * Trading buttons showcase
 */
export const TradingButtons: Story = {
  render: () => (
    <div className="flex gap-4">
      <Button variant="buy" size="lg" iconLeft={<TrendingUp size={20} />}>
        Buy 100 @ ₹1,234.50
      </Button>
      <Button variant="sell" size="lg" iconLeft={<TrendingDown size={20} />}>
        Sell 100 @ ₹1,234.50
      </Button>
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Trading-specific button variants for buy and sell orders.',
      },
    },
  },
};
