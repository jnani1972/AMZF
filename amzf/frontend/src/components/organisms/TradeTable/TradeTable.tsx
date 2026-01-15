import React, { useState } from 'react';
import { Card } from '../atoms/Card';
import { Text } from '../atoms/Text';
import { Badge } from '../atoms/Badge';
import { Button } from '../atoms/Button';
import { EmptyState } from '../molecules/EmptyState';
import { Activity } from 'lucide-react';
import './TradeTable.css';

/**
 * Trade status
 */
export type TradeStatus = 'open' | 'closed';

/**
 * Trade direction
 */
export type TradeDirection = 'buy' | 'sell';

/**
 * Trade item
 */
export interface Trade {
  id: string;
  symbol: string;
  direction: TradeDirection;
  quantity: number;
  entryPrice: number;
  exitPrice?: number;
  pnl?: number;
  status: TradeStatus;
  entryTime: Date;
  exitTime?: Date;
}

/**
 * TradeTable component props
 */
export interface TradeTableProps {
  /**
   * Table title
   */
  title?: string;

  /**
   * Trades
   */
  trades: Trade[];

  /**
   * Handler when trade is clicked
   */
  onTradeClick?: (tradeId: string) => void;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * TradeTable Component
 *
 * Displays open and closed trades with filtering tabs.
 *
 * @example
 * ```tsx
 * <TradeTable
 *   title="Trades"
 *   trades={allTrades}
 *   onTradeClick={handleTradeClick}
 * />
 * ```
 */
export const TradeTable: React.FC<TradeTableProps> = ({
  title = 'Trades',
  trades,
  onTradeClick,
  className = '',
}) => {
  const [filter, setFilter] = useState<TradeStatus | 'all'>('all');

  const filteredTrades =
    filter === 'all' ? trades : trades.filter((trade) => trade.status === filter);

  const formatCurrency = (value: number) => {
    return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const formatDateTime = (date: Date) => {
    return date.toLocaleString('en-IN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const classNames = ['trade-table', className].filter(Boolean).join(' ');

  return (
    <Card className={classNames} padding="none">
      <div className="trade-table__header">
        <Text variant="h4" weight="semibold">
          {title}
        </Text>
        <div className="trade-table__filters">
          <Button
            variant={filter === 'all' ? 'primary' : 'ghost'}
            size="sm"
            onClick={() => setFilter('all')}
          >
            All
          </Button>
          <Button
            variant={filter === 'open' ? 'primary' : 'ghost'}
            size="sm"
            onClick={() => setFilter('open')}
          >
            Open
          </Button>
          <Button
            variant={filter === 'closed' ? 'primary' : 'ghost'}
            size="sm"
            onClick={() => setFilter('closed')}
          >
            Closed
          </Button>
        </div>
      </div>

      {filteredTrades.length === 0 ? (
        <div className="trade-table__empty">
          <EmptyState
            icon={<Activity size={32} />}
            title="No trades"
            description={`No ${filter === 'all' ? '' : filter} trades found`}
            size="sm"
          />
        </div>
      ) : (
        <div className="trade-table__table-container">
          <table className="trade-table__table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Direction</th>
                <th className="trade-table__col--right">Qty</th>
                <th className="trade-table__col--right">Entry Price</th>
                <th className="trade-table__col--right">Exit Price</th>
                <th className="trade-table__col--right">P&L</th>
                <th>Entry Time</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filteredTrades.map((trade) => (
                <tr
                  key={trade.id}
                  className={onTradeClick ? 'trade-table__row--clickable' : ''}
                  onClick={() => onTradeClick && onTradeClick(trade.id)}
                >
                  <td>
                    <Text variant="label" weight="semibold">
                      {trade.symbol}
                    </Text>
                  </td>
                  <td>
                    <Badge
                      variant={trade.direction === 'buy' ? 'profit' : 'loss'}
                      size="sm"
                    >
                      {trade.direction.toUpperCase()}
                    </Badge>
                  </td>
                  <td className="trade-table__col--right">{trade.quantity}</td>
                  <td className="trade-table__col--right">
                    {formatCurrency(trade.entryPrice)}
                  </td>
                  <td className="trade-table__col--right">
                    {trade.exitPrice ? formatCurrency(trade.exitPrice) : '-'}
                  </td>
                  <td className="trade-table__col--right">
                    {trade.pnl !== undefined ? (
                      <Text color={trade.pnl >= 0 ? 'profit' : 'loss'} weight="medium">
                        {formatCurrency(trade.pnl)}
                      </Text>
                    ) : (
                      '-'
                    )}
                  </td>
                  <td>
                    <Text variant="small" color="secondary">
                      {formatDateTime(trade.entryTime)}
                    </Text>
                  </td>
                  <td>
                    <Badge variant={trade.status === 'open' ? 'info' : 'default'} size="sm">
                      {trade.status}
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

export default TradeTable;
