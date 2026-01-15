import React from 'react';
import { Card } from '../../atoms/Card/Card';
import { Text } from '../../atoms/Text/Text';
import { Badge } from '../../atoms/Badge/Badge';
import { EmptyState } from '../../molecules/EmptyState/EmptyState';
import { Briefcase } from 'lucide-react';
import './PortfolioSummary.css';

/**
 * Portfolio holding
 */
export interface PortfolioHolding {
  symbol: string;
  quantity: number;
  avgPrice: number;
  currentPrice: number;
  pnl: number;
  pnlPercent: number;
}

/**
 * PortfolioSummary component props
 */
export interface PortfolioSummaryProps {
  /**
   * Portfolio title
   */
  title?: string;

  /**
   * Holdings
   */
  holdings: PortfolioHolding[];

  /**
   * Handler when holding is clicked
   */
  onHoldingClick?: (symbol: string) => void;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * PortfolioSummary Component
 *
 * Displays portfolio holdings with P&L information in a table format.
 *
 * @example
 * ```tsx
 * <PortfolioSummary
 *   title="Holdings"
 *   holdings={[
 *     {
 *       symbol: 'RELIANCE',
 *       quantity: 100,
 *       avgPrice: 2400,
 *       currentPrice: 2456.75,
 *       pnl: 5675,
 *       pnlPercent: 2.36
 *     }
 *   ]}
 * />
 * ```
 */
export const PortfolioSummary: React.FC<PortfolioSummaryProps> = ({
  title = 'Portfolio Holdings',
  holdings,
  onHoldingClick,
  className = '',
}) => {
  const classNames = ['portfolio-summary', className].filter(Boolean).join(' ');

  const formatCurrency = (value: number) => {
    return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  return (
    <Card className={classNames} padding="none">
      <div className="portfolio-summary__header">
        <Text variant="h4" weight="semibold">
          {title}
        </Text>
      </div>

      {holdings.length === 0 ? (
        <div className="portfolio-summary__empty">
          <EmptyState
            icon={<Briefcase size={32} />}
            title="No holdings"
            description="Your portfolio is empty"
            size="sm"
          />
        </div>
      ) : (
        <div className="portfolio-summary__table-container">
          <table className="portfolio-summary__table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th className="portfolio-summary__col--right">Qty</th>
                <th className="portfolio-summary__col--right">Avg Price</th>
                <th className="portfolio-summary__col--right">LTP</th>
                <th className="portfolio-summary__col--right">P&L</th>
                <th className="portfolio-summary__col--right">P&L %</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((holding) => (
                <tr
                  key={holding.symbol}
                  className={onHoldingClick ? 'portfolio-summary__row--clickable' : ''}
                  onClick={() => onHoldingClick && onHoldingClick(holding.symbol)}
                >
                  <td>
                    <Text variant="label" weight="semibold">
                      {holding.symbol}
                    </Text>
                  </td>
                  <td className="portfolio-summary__col--right">{holding.quantity}</td>
                  <td className="portfolio-summary__col--right">
                    {formatCurrency(holding.avgPrice)}
                  </td>
                  <td className="portfolio-summary__col--right">
                    {formatCurrency(holding.currentPrice)}
                  </td>
                  <td className="portfolio-summary__col--right">
                    <Text color={holding.pnl >= 0 ? 'profit' : 'loss'} weight="medium">
                      {formatCurrency(holding.pnl)}
                    </Text>
                  </td>
                  <td className="portfolio-summary__col--right">
                    <Badge variant={holding.pnl >= 0 ? 'profit' : 'loss'} size="sm">
                      {holding.pnlPercent >= 0 ? '+' : ''}
                      {holding.pnlPercent.toFixed(2)}%
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
};

export default PortfolioSummary;
