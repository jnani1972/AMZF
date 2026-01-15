import React from 'react';
import './Spinner.css';

/**
 * Spinner size types
 */
export type SpinnerSize = 'sm' | 'md' | 'lg' | 'xl';

/**
 * Spinner variant types
 */
export type SpinnerVariant = 'default' | 'primary' | 'profit' | 'loss';

/**
 * Spinner component props
 */
export interface SpinnerProps {
  /**
   * Spinner size
   * @default 'md'
   */
  size?: SpinnerSize;

  /**
   * Visual style variant
   * @default 'default'
   */
  variant?: SpinnerVariant;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Accessible label for screen readers
   * @default 'Loading'
   */
  label?: string;
}

/**
 * Spinner Component
 *
 * A loading indicator with multiple sizes and variants.
 *
 * @example
 * ```tsx
 * <Spinner size="md" variant="primary" />
 * <Spinner size="lg" label="Loading data..." />
 * ```
 */
export const Spinner: React.FC<SpinnerProps> = ({
  size = 'md',
  variant = 'default',
  className = '',
  label = 'Loading',
}) => {
  const classNames = ['spinner', `spinner--${size}`, `spinner--${variant}`, className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classNames} role="status" aria-label={label}>
      <svg className="spinner__svg" viewBox="0 0 50 50">
        <circle
          className="spinner__circle"
          cx="25"
          cy="25"
          r="20"
          fill="none"
          strokeWidth="4"
        />
      </svg>
      <span className="spinner__sr-only">{label}</span>
    </div>
  );
};

export default Spinner;
