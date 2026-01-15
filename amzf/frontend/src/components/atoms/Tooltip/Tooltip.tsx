import React, { ReactNode, useState } from 'react';
import './Tooltip.css';

/**
 * Tooltip placement types
 */
export type TooltipPlacement = 'top' | 'bottom' | 'left' | 'right';

/**
 * Tooltip component props
 */
export interface TooltipProps {
  /**
   * Element that triggers the tooltip
   */
  children: ReactNode;

  /**
   * Tooltip content
   */
  content: ReactNode;

  /**
   * Tooltip placement
   * @default 'top'
   */
  placement?: TooltipPlacement;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Delay before showing tooltip (ms)
   * @default 200
   */
  delay?: number;
}

/**
 * Tooltip Component
 *
 * A simple tooltip that appears on hover.
 * Uses pure CSS positioning for performance.
 *
 * @example
 * ```tsx
 * <Tooltip content="Click to save">
 *   <Button>Save</Button>
 * </Tooltip>
 *
 * <Tooltip content="Total P&L for today" placement="right">
 *   <Text>₹12,345</Text>
 * </Tooltip>
 * ```
 */
export const Tooltip: React.FC<TooltipProps> = ({
  children,
  content,
  placement = 'top',
  className = '',
  delay = 200,
}) => {
  const [isVisible, setIsVisible] = useState(false);
  const [timeoutId, setTimeoutId] = useState<NodeJS.Timeout | null>(null);

  const handleMouseEnter = () => {
    const id = setTimeout(() => {
      setIsVisible(true);
    }, delay);
    setTimeoutId(id);
  };

  const handleMouseLeave = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
    setIsVisible(false);
  };

  const classNames = [
    'tooltip',
    `tooltip--${placement}`,
    isVisible && 'tooltip--visible',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      className="tooltip-wrapper"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onFocus={handleMouseEnter}
      onBlur={handleMouseLeave}
    >
      {children}
      <div className={classNames} role="tooltip">
        {content}
      </div>
    </div>
  );
};

export default Tooltip;
