import React from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { Card } from '@/components/atoms/Card';
import { Text } from '@/components/atoms/Text';
import { Badge } from '@/components/atoms/Badge';
import './PriceCard.css';

/**
 * PriceCard component props
 */
export interface PriceCardProps {
  /**
   * Symbol/Instrument name
   */
  symbol: string;

  /**
   * Current price
   */
  price: number;

  /**
   * Change amount
   */
  change?: number;

  /**
   * Change percentage
   */
  changePercent?: number;

  /**
   * Whether the card is clickable
   * @default false
   */
  interactive?: boolean;

  /**
   * Click handler
   */
  onClick?: () => void;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Currency symbol
   * @default '₹'
   */
  currency?: string;

  /**
   * Symbol description
   */
  description?: string;

  /**
   * Whether to show compact view
   * @default false
   */
  compact?: boolean;
}

/**
 * PriceCard Component
 *
 * Displays a symbol with its current price and change percentage.
 * Automatically color-codes positive (green) and negative (red) changes.
 *
 * @example
 * ```tsx
 * <PriceCard
 *   symbol="RELIANCE"
 *   price={2456.75}
 *   change={23.50}
 *   changePercent={0.97}
 *   description="Reliance Industries Ltd."
 * />
 * ```
 */
export const PriceCard: React.FC<PriceCardProps> = ({
  symbol,
  price,
  change,
  changePercent,
  interactive = false,
  onClick,
  className = '',
  currency = '₹',
  description,
  compact = false,
}) => {
  const isPositive = (change ?? 0) >= 0;
  const changeColor = isPositive ? 'profit' : 'loss';
  const TrendIcon = isPositive ? TrendingUp : TrendingDown;

  const formattedPrice = price.toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  const formattedChange = change
    ? `${isPositive ? '+' : ''}${change.toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}`
    : null;

  const formattedChangePercent = changePercent
    ? `${isPositive ? '+' : ''}${changePercent.toFixed(2)}%`
    : null;

  const classNames = ['price-card', compact && 'price-card--compact', className]
    .filter(Boolean)
    .join(' ');

  return (
    <Card
      className={classNames}
      interactive={interactive}
      onClick={onClick}
      padding={compact ? 'sm' : 'md'}
    >
      <div className="price-card__header">
        <div className="price-card__symbol-container">
          <Text variant="label" weight="semibold">
            {symbol}
          </Text>
          {description && !compact && (
            <Text variant="caption" color="secondary">
              {description}
            </Text>
          )}
        </div>
        {changePercent !== undefined && (
          <Badge variant={changeColor} size="sm">
            <TrendIcon size={12} />
            {formattedChangePercent}
          </Badge>
        )}
      </div>

      <div className="price-card__body">
        <Text variant={compact ? 'body' : 'h3'} weight="bold">
          {currency}
          {formattedPrice}
        </Text>
        {formattedChange && (
          <Text variant="small" color={changeColor} weight="medium">
            {formattedChange}
          </Text>
        )}
      </div>
    </Card>
  );
};

export default PriceCard;
