import React, { ReactNode, ElementType } from 'react';
import './Text.css';

/**
 * Text variant types
 */
export type TextVariant =
  | 'h1'
  | 'h2'
  | 'h3'
  | 'h4'
  | 'body'
  | 'small'
  | 'caption'
  | 'label';

/**
 * Text weight types
 */
export type TextWeight = 'regular' | 'medium' | 'semibold' | 'bold';

/**
 * Text color types
 */
export type TextColor =
  | 'primary'
  | 'secondary'
  | 'tertiary'
  | 'inverse'
  | 'profit'
  | 'loss'
  | 'success'
  | 'error'
  | 'warning';

/**
 * Text component props
 */
export interface TextProps {
  /**
   * Text content
   */
  children: ReactNode;

  /**
   * Typography variant
   * @default 'body'
   */
  variant?: TextVariant;

  /**
   * Font weight
   */
  weight?: TextWeight;

  /**
   * Text color
   */
  color?: TextColor;

  /**
   * HTML element to render
   */
  as?: ElementType;

  /**
   * Whether to truncate text with ellipsis
   * @default false
   */
  truncate?: boolean;

  /**
   * Text alignment
   */
  align?: 'left' | 'center' | 'right';

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Text Component
 *
 * A flexible typography component with predefined variants and styling.
 * Automatically maps variants to semantic HTML elements.
 *
 * @example
 * ```tsx
 * <Text variant="h1">Dashboard</Text>
 * <Text variant="body" color="secondary">Last updated 5 mins ago</Text>
 * <Text variant="label" weight="semibold">Portfolio Value</Text>
 * <Text color="profit">+₹12,345</Text>
 * ```
 */
export const Text: React.FC<TextProps> = ({
  children,
  variant = 'body',
  weight,
  color,
  as,
  truncate = false,
  align,
  className = '',
}) => {
  // Default element mapping
  const defaultElementMap: Record<TextVariant, ElementType> = {
    h1: 'h1',
    h2: 'h2',
    h3: 'h3',
    h4: 'h4',
    body: 'p',
    small: 'span',
    caption: 'span',
    label: 'span',
  };

  const Component = as || defaultElementMap[variant];

  const classNames = [
    'text',
    `text--${variant}`,
    weight && `text--weight-${weight}`,
    color && `text--color-${color}`,
    truncate && 'text--truncate',
    align && `text--align-${align}`,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return <Component className={classNames}>{children}</Component>;
};

export default Text;
