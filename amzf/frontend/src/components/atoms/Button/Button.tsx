import React, { ButtonHTMLAttributes, ReactNode } from 'react';
import './Button.css';

/**
 * Button component variant types
 */
export type ButtonVariant = 'primary' | 'secondary' | 'buy' | 'sell' | 'ghost';

/**
 * Button component size types
 */
export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Button component props
 */
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /**
   * Button content
   */
  children: ReactNode;

  /**
   * Visual style variant
   * @default 'primary'
   */
  variant?: ButtonVariant;

  /**
   * Button size
   * @default 'md'
   */
  size?: ButtonSize;

  /**
   * Whether the button takes full width of its container
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Whether the button is in a loading state
   * @default false
   */
  loading?: boolean;

  /**
   * Icon to display before the button text
   */
  iconLeft?: ReactNode;

  /**
   * Icon to display after the button text
   */
  iconRight?: ReactNode;
}

/**
 * Button Component
 *
 * A versatile button component with multiple variants and sizes.
 * Supports trading-specific variants (buy/sell) with appropriate colors.
 *
 * @example
 * ```tsx
 * <Button variant="primary" size="md" onClick={handleClick}>
 *   Click me
 * </Button>
 *
 * <Button variant="buy" size="lg" fullWidth>
 *   Place Buy Order
 * </Button>
 *
 * <Button variant="sell" loading>
 *   Selling...
 * </Button>
 * ```
 */
export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  loading = false,
  disabled = false,
  iconLeft,
  iconRight,
  className = '',
  type = 'button',
  ...props
}) => {
  const classNames = [
    'button',
    `button--${variant}`,
    `button--${size}`,
    fullWidth && 'button--full-width',
    loading && 'button--loading',
    disabled && 'button--disabled',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type={type}
      className={classNames}
      disabled={disabled || loading}
      aria-busy={loading}
      aria-disabled={disabled || loading}
      {...props}
    >
      {loading && (
        <span className="button__spinner" aria-label="Loading">
          <svg
            className="button__spinner-icon"
            viewBox="0 0 24 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <circle
              className="button__spinner-circle"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="3"
            />
          </svg>
        </span>
      )}

      {!loading && iconLeft && <span className="button__icon-left">{iconLeft}</span>}

      <span className="button__content">{children}</span>

      {!loading && iconRight && <span className="button__icon-right">{iconRight}</span>}
    </button>
  );
};

export default Button;
