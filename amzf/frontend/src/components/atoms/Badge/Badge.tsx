import React, { ReactNode } from 'react';
import './Badge.css';

/**
 * Badge variant types
 */
export type BadgeVariant =
  | 'default'
  | 'primary'
  | 'success'
  | 'error'
  | 'warning'
  | 'info'
  | 'profit'
  | 'loss';

/**
 * Badge size types
 */
export type BadgeSize = 'sm' | 'md' | 'lg';

/**
 * Badge component props
 */
export interface BadgeProps {
  /**
   * Badge content
   */
  children: ReactNode;

  /**
   * Visual style variant
   * @default 'default'
   */
  variant?: BadgeVariant;

  /**
   * Badge size
   * @default 'md'
   */
  size?: BadgeSize;

  /**
   * Whether to show a dot indicator
   * @default false
   */
  dot?: boolean;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Badge Component
 *
 * A small status indicator component with multiple variants and sizes.
 * Useful for showing statuses, counts, or labels.
 *
 * @example
 * ```tsx
 * <Badge variant="success">Active</Badge>
 * <Badge variant="profit">+12.5%</Badge>
 * <Badge variant="error" dot>Disconnected</Badge>
 * ```
 */
export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'default',
  size = 'md',
  dot = false,
  className = '',
}) => {
  const classNames = [
    'badge',
    `badge--${variant}`,
    `badge--${size}`,
    dot && 'badge--with-dot',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <span className={classNames}>
      {dot && <span className="badge__dot" />}
      {children}
    </span>
  );
};

export default Badge;
