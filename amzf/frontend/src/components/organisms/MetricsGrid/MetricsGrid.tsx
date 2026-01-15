import React from 'react';
import { StatCard, StatCardProps } from '../../molecules/StatCard/StatCard';
import './MetricsGrid.css';

/**
 * MetricsGrid component props
 */
export interface MetricsGridProps {
  /**
   * Array of stat card configurations
   */
  metrics: StatCardProps[];

  /**
   * Number of columns
   * @default 'auto'
   */
  columns?: 1 | 2 | 3 | 4 | 'auto';

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * MetricsGrid Component
 *
 * Displays multiple StatCards in a responsive grid layout.
 * Automatically adapts to different screen sizes.
 *
 * @example
 * ```tsx
 * <MetricsGrid
 *   columns={3}
 *   metrics={[
 *     {
 *       icon: <Wallet />,
 *       title: 'Portfolio Value',
 *       value: '₹12,34,567',
 *       trend: 'up',
 *       trendValue: '+12.5%'
 *     },
 *     {
 *       icon: <TrendingUp />,
 *       title: 'Total P&L',
 *       value: '₹45,678',
 *       trend: 'up',
 *       trendValue: '+3.8%'
 *     }
 *   ]}
 * />
 * ```
 */
export const MetricsGrid: React.FC<MetricsGridProps> = ({
  metrics,
  columns = 'auto',
  className = '',
}) => {
  const classNames = [
    'metrics-grid',
    columns !== 'auto' && `metrics-grid--cols-${columns}`,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classNames}>
      {metrics.map((metric, index) => (
        <StatCard key={index} {...metric} />
      ))}
    </div>
  );
};

export default MetricsGrid;
