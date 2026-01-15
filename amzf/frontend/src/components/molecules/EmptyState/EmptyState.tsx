import React, { ReactNode } from 'react';
import { Text } from '../atoms/Text';
import { Button } from '../atoms/Button';
import './EmptyState.css';

/**
 * EmptyState component props
 */
export interface EmptyStateProps {
  /**
   * Icon element
   */
  icon?: ReactNode;

  /**
   * Title text
   */
  title: string;

  /**
   * Description text
   */
  description?: string;

  /**
   * Call-to-action button text
   */
  ctaText?: string;

  /**
   * Call-to-action button click handler
   */
  onCtaClick?: () => void;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Size variant
   * @default 'md'
   */
  size?: 'sm' | 'md' | 'lg';
}

/**
 * EmptyState Component
 *
 * Displays a friendly message when there's no content to show.
 * Includes optional icon, description, and call-to-action button.
 *
 * @example
 * ```tsx
 * <EmptyState
 *   icon={<Inbox size={48} />}
 *   title="No trades yet"
 *   description="Start trading to see your trades here"
 *   ctaText="Place an Order"
 *   onCtaClick={handleOpenOrderPanel}
 * />
 * ```
 */
export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  ctaText,
  onCtaClick,
  className = '',
  size = 'md',
}) => {
  const classNames = ['empty-state', `empty-state--${size}`, className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classNames}>
      {icon && <div className="empty-state__icon">{icon}</div>}

      <div className="empty-state__content">
        <Text
          variant={size === 'lg' ? 'h2' : size === 'sm' ? 'h4' : 'h3'}
          weight="semibold"
          align="center"
          className="empty-state__title"
        >
          {title}
        </Text>

        {description && (
          <Text
            variant={size === 'lg' ? 'body' : 'small'}
            color="secondary"
            align="center"
            className="empty-state__description"
          >
            {description}
          </Text>
        )}
      </div>

      {ctaText && onCtaClick && (
        <Button
          variant="primary"
          size={size === 'lg' ? 'lg' : 'md'}
          onClick={onCtaClick}
          className="empty-state__cta"
        >
          {ctaText}
        </Button>
      )}
    </div>
  );
};

export default EmptyState;
