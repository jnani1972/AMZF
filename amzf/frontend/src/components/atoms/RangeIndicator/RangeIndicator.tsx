/**
 * RangeIndicator Component
 * Visualizes current price within 52-week high/low range
 */

import { Text } from '../Text/Text';

export interface RangeIndicatorProps {
  high: number;
  low: number;
  current: number;
}

/**
 * RangeIndicator - Shows current price position in 52-week range
 *
 * @example
 * ```tsx
 * <RangeIndicator high={200} low={150} current={180} />
 * ```
 */
export function RangeIndicator({ high, low, current }: RangeIndicatorProps) {
  // Calculate percentage position (0-100%)
  const range = high - low;
  const position = range > 0 ? ((current - low) / range) * 100 : 50;
  const clampedPosition = Math.min(Math.max(position, 0), 100);

  return (
    <div className="flex items-center gap-2">
      <Text variant="small" className="text-muted min-w-[3rem] text-right">
        ₹{low.toFixed(0)}
      </Text>
      <div className="w-24 h-2 bg-gray-200 rounded-full overflow-hidden">
        <div
          className="h-full bg-blue-500 rounded-full transition-all"
          style={{ width: `${clampedPosition}%` }}
        />
      </div>
      <Text variant="small" className="text-muted min-w-[3rem]">
        ₹{high.toFixed(0)}
      </Text>
    </div>
  );
}

export default RangeIndicator;
