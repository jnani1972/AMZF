import React, { ReactNode } from 'react';
import './Card.css';

/**
 * Card variant types
 */
export type CardVariant = 'default' | 'outlined' | 'elevated';

/**
 * Card component props
 */
export interface CardProps {
  /**
   * Card content
   */
  children: ReactNode;

  /**
   * Visual style variant
   * @default 'default'
   */
  variant?: CardVariant;

  /**
   * Card header content
   */
  header?: ReactNode;

  /**
   * Card footer content
   */
  footer?: ReactNode;

  /**
   * Whether the card is clickable/interactive
   * @default false
   */
  interactive?: boolean;

  /**
   * Click handler (makes card interactive)
   */
  onClick?: () => void;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Padding size
   * @default 'md'
   */
  padding?: 'none' | 'sm' | 'md' | 'lg';
}

/**
 * Card Component
 *
 * A container component with optional header and footer sections.
 * Supports different variants and interactive states.
 *
 * @example
 * ```tsx
 * <Card header="Portfolio Summary" variant="elevated">
 *   <Text>Total Value: ₹1,23,456</Text>
 * </Card>
 *
 * <Card interactive onClick={handleClick}>
 *   Click me
 * </Card>
 * ```
 */
export const Card: React.FC<CardProps> = ({
  children,
  variant = 'default',
  header,
  footer,
  interactive = false,
  onClick,
  className = '',
  padding = 'md',
}) => {
  const isInteractive = interactive || !!onClick;

  const classNames = [
    'card',
    `card--${variant}`,
    `card--padding-${padding}`,
    isInteractive && 'card--interactive',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const handleClick = () => {
    if (onClick) {
      onClick();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (isInteractive && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      handleClick();
    }
  };

  const cardProps = isInteractive
    ? {
        role: 'button',
        tabIndex: 0,
        onClick: handleClick,
        onKeyDown: handleKeyDown,
      }
    : {};

  return (
    <div className={classNames} {...cardProps}>
      {header && <div className="card__header">{header}</div>}
      <div className="card__body">{children}</div>
      {footer && <div className="card__footer">{footer}</div>}
    </div>
  );
};

export default Card;
