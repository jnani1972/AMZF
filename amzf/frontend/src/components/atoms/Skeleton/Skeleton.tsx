import React from 'react';
import './Skeleton.css';

/**
 * Skeleton variant types
 */
export type SkeletonVariant = 'text' | 'circular' | 'rectangular';

/**
 * Skeleton component props
 */
export interface SkeletonProps {
  /**
   * Shape variant
   * @default 'rectangular'
   */
  variant?: SkeletonVariant;

  /**
   * Width of the skeleton
   */
  width?: string | number;

  /**
   * Height of the skeleton
   */
  height?: string | number;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Whether to animate
   * @default true
   */
  animate?: boolean;
}

/**
 * Skeleton Component
 *
 * A placeholder for loading content with pulse animation.
 *
 * @example
 * ```tsx
 * <Skeleton variant="text" width="200px" />
 * <Skeleton variant="circular" width={40} height={40} />
 * <Skeleton variant="rectangular" width="100%" height="200px" />
 * ```
 */
export const Skeleton: React.FC<SkeletonProps> = ({
  variant = 'rectangular',
  width,
  height,
  className = '',
  animate = true,
}) => {
  const classNames = [
    'skeleton',
    `skeleton--${variant}`,
    animate && 'skeleton--animate',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const style: React.CSSProperties = {};

  if (width !== undefined) {
    style.width = typeof width === 'number' ? `${width}px` : width;
  }

  if (height !== undefined) {
    style.height = typeof height === 'number' ? `${height}px` : height;
  }

  // Default heights for variants
  if (!height) {
    if (variant === 'text') {
      style.height = '1em';
    } else if (variant === 'circular') {
      style.height = width !== undefined ? style.width : '40px';
    }
  }

  return <div className={classNames} style={style} aria-busy="true" aria-live="polite" />;
};

export default Skeleton;
