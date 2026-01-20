/**
 * ChangeIndicator Component
 * Displays price change percentage with color coding
 */

import { TrendingUp, TrendingDown } from 'lucide-react';
import { Text } from '../Text/Text';

export interface ChangeIndicatorProps {
  value: number;
  showArrow?: boolean;
  size?: 'sm' | 'md';
}

/**
 * ChangeIndicator - Shows price change with color and optional arrow
 *
 * @example
 * ```tsx
 * <ChangeIndicator value={2.5} showArrow />
 * <ChangeIndicator value={-1.2} />
 * ```
 */
export function ChangeIndicator({ value, showArrow = false, size = 'md' }: ChangeIndicatorProps) {
  const isPositive = value >= 0;
  const colorClass = isPositive ? 'text-profit' : 'text-loss';
  const sign = isPositive ? '+' : '';

  return (
    <div className={`flex items-center gap-1 ${colorClass}`}>
      {showArrow && (
        <>
          {isPositive ? (
            <TrendingUp size={size === 'sm' ? 14 : 16} />
          ) : (
            <TrendingDown size={size === 'sm' ? 14 : 16} />
          )}
        </>
      )}
      <Text variant={size === 'sm' ? 'small' : 'body'} weight="medium" className={colorClass}>
        {sign}{value.toFixed(2)}%
      </Text>
    </div>
  );
}

export default ChangeIndicator;
