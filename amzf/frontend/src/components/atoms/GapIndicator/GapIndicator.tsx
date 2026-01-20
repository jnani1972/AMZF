/**
 * GapIndicator Component
 * Displays overnight gap percentage as a badge
 */

import { Badge } from '../Badge/Badge';

export interface GapIndicatorProps {
  value: number;
}

/**
 * GapIndicator - Shows overnight gap (open vs previous close) as a colored badge
 *
 * @example
 * ```tsx
 * <GapIndicator value={2.5} />  // Green badge: +2.5%
 * <GapIndicator value={-1.2} /> // Red badge: -1.2%
 * ```
 */
export function GapIndicator({ value }: GapIndicatorProps) {
  const isPositive = value >= 0;
  const variant = isPositive ? 'profit' : 'loss';
  const sign = isPositive ? '+' : '';

  return (
    <Badge variant={variant} size="sm">
      {sign}{value.toFixed(2)}%
    </Badge>
  );
}

export default GapIndicator;
