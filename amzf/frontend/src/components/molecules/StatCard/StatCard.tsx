import React, { ReactNode } from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';
import { Card } from '../atoms/Card';
import { Text } from '../atoms/Text';
import './StatCard.css';

/**
 * Trend direction
 */
export type TrendDirection = 'up' | 'down' | 'neutral';

/**
 * StatCard component props
 */
export interface StatCardProps {
  /**
   * Icon element
   */
  icon?: ReactNode;

  /**
   * Stat title/label
   */
  title: string;

  /**
   * Main value
   */
  value: string | number;

  /**
   * Trend direction
   */
  trend?: TrendDirection;

  /**
   * Trend value (e.g., "+12%", "-5%")
   */
  trendValue?: string;

  /**
   * Subtitle/description
   */
  subtitle?: string;

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
   * Card variant
   */
  variant?: 'default' | 'outlined' | 'elevated';
}

/**
 * StatCard Component
 *
 * Displays a statistic with an icon, title, value, and optional trend indicator.
 * Useful for dashboard metrics and key performance indicators.
 *
 * @example
 * ```tsx
 * <StatCard
 *   icon={<Wallet />}
 *   title="Portfolio Value"
 *   value="₹12,34,567"
 *   trend="up"
 *   trendValue="+12.5%"
 *   subtitle="vs last month"
 * />
 * ```
 */
export const StatCard: React.FC<StatCardProps> = ({
  icon,
  title,
  value,
  trend,
  trendValue,
  subtitle,
  interactive = false,
  onClick,
  className = '',
  variant = 'default',
}) => {
  const getTrendIcon = () => {
    switch (trend) {
      case 'up':
        return <TrendingUp size={16} />;
      case 'down':
        return <TrendingDown size={16} />;
      case 'neutral':
        return <Minus size={16} />;
      default:
        return null;
    }
  };

  const getTrendColor = (): 'profit' | 'loss' | 'secondary' => {
    switch (trend) {
      case 'up':
        return 'profit';
      case 'down':
        return 'loss';
      default:
        return 'secondary';
    }
  };

  const classNames = ['stat-card', className].filter(Boolean).join(' ');

  return (
    <Card
      className={classNames}
      variant={variant}
      interactive={interactive}
      onClick={onClick}
    >
      <div className="stat-card__header">
        {icon && <div className="stat-card__icon">{icon}</div>}
        <Text variant="label" color="secondary" className="stat-card__title">
          {title}
        </Text>
      </div>

      <div className="stat-card__body">
        <Text variant="h2" weight="bold" className="stat-card__value">
          {value}
        </Text>

        {(trend || trendValue) && (
          <div className="stat-card__trend">
            {getTrendIcon()}
            <Text variant="small" color={getTrendColor()} weight="medium">
              {trendValue}
            </Text>
          </div>
        )}
      </div>

      {subtitle && (
        <div className="stat-card__footer">
          <Text variant="caption" color="secondary">
            {subtitle}
          </Text>
        </div>
      )}
    </Card>
  );
};

export default StatCard;
