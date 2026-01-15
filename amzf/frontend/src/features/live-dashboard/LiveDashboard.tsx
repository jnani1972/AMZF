/**
 * Live Trading Dashboard
 * Real-time monitoring of active positions with alerts and analytics
 */

import { useState, useEffect } from 'react';
import { useTrades } from '@/hooks/useApi';
import { useTradeUpdates, useWebSocket } from '@/hooks/useWebSocket';
import { useAuth } from '@/features/auth/AuthProvider';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Button } from '@/components/atoms/Button/Button';
import { Badge } from '@/components/atoms/Badge/Badge';
import { Spinner } from '@/components/atoms/Spinner/Spinner';
import { RefreshCw, TrendingUp, Activity } from 'lucide-react';

/**
 * Trade activity entry
 */
interface TradeActivity {
  action: 'Buy' | 'Exit';
  time: string;
  date: string;
  symbol: string;
  status: string;
  price: number;
  qty: number;
  amount: number;
  pnl?: number;
  pnlPercent?: number;
}

/**
 * Category summary
 */
interface CategorySummary {
  category: string;
  trades: number;
  symbols: number;
  invested: number;
  current: number;
  pnl: number;
  pnlPercent: number;
  avgAge: number;
  avgCagr?: number;
  holdingPeriod?: number;
}

/**
 * Live dashboard component
 */
export function LiveDashboard() {
  const { user, isAdmin } = useAuth();
  const { connected: wsConnected } = useWebSocket();
  const { data: trades, loading, refetch } = useTrades('OPEN');
  const [lastUpdate, setLastUpdate] = useState(new Date());
  const [autoRefresh] = useState(true);
  const [refreshInterval] = useState(5);

  // Listen for real-time trade updates
  useTradeUpdates(() => {
    setLastUpdate(new Date());
  });

  // Auto-refresh
  useEffect(() => {
    if (!autoRefresh) return;

    const interval = setInterval(() => {
      refetch();
      setLastUpdate(new Date());
    }, refreshInterval * 1000);

    return () => clearInterval(interval);
  }, [autoRefresh, refreshInterval, refetch]);

  // Calculate summary metrics
  const investedAmount = trades?.reduce((sum, t) => sum + t.entryPrice * t.quantity, 0) || 0;
  const currentAmount = trades?.reduce((sum, t) => sum + (t.exitPrice || t.entryPrice) * t.quantity, 0) || 0;
  const unrealizedPnl = currentAmount - investedAmount;
  const unrealizedPnlPercent = investedAmount > 0 ? (unrealizedPnl / investedAmount) * 100 : 0;
  const numTrades = trades?.length || 0;
  const numSymbols = new Set(trades?.map(t => t.symbol) || []).size;

  // Calculate weighted average age
  const avgAge = trades && trades.length > 0
    ? trades.reduce((sum, t) => {
        const age = (Date.now() - new Date(t.entryTime).getTime()) / (1000 * 60 * 60 * 24);
        return sum + age;
      }, 0) / trades.length
    : 0;

  // Generate mock latest activity (last 10 trades)
  const latestActivity: TradeActivity[] = (trades || []).slice(0, 10).map(trade => ({
    action: trade.direction === 'BUY' ? 'Buy' : 'Exit',
    time: new Date(trade.entryTime).toLocaleTimeString(),
    date: new Date(trade.entryTime).toLocaleDateString(),
    symbol: trade.symbol,
    status: trade.status === 'OPEN' ? 'RE_BUY' : 'NEW_BUY',
    price: trade.entryPrice,
    qty: trade.quantity,
    amount: trade.entryPrice * trade.quantity,
    pnl: trade.pnl,
    pnlPercent: trade.pnlPercent,
  }));

  // Calculate today's summary by category
  const todaySummary: CategorySummary[] = [
    {
      category: 'Carry Forward',
      trades: numTrades,
      symbols: numSymbols,
      invested: investedAmount,
      current: currentAmount,
      pnl: unrealizedPnl,
      pnlPercent: unrealizedPnlPercent,
      avgAge: avgAge,
      holdingPeriod: investedAmount * 0.35, // Mock calculation
    },
    {
      category: 'Bought Today',
      trades: 2,
      symbols: 2,
      invested: 0,
      current: 0,
      pnl: 0,
      pnlPercent: 0,
      avgAge: 1.0,
      holdingPeriod: 192,
    },
    {
      category: 'Exited Today',
      trades: 2,
      symbols: 1,
      invested: 0,
      current: 0,
      pnl: 0,
      pnlPercent: 0,
      avgAge: 3.6,
      avgCagr: 65081.41,
      holdingPeriod: 345,
    },
  ];

  /**
   * Handle manual refresh
   */
  const handleRefresh = () => {
    refetch();
    setLastUpdate(new Date());
  };

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <div className="min-h-screen bg-background p-6">
        <div className="flex items-center justify-center py-12">
          <Spinner size="lg" variant="primary" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <div className="bg-surface-secondary border-b border-border-light">
        <div className="container mx-auto p-4">
          <div className="flex items-center justify-between">
            <div>
              <div className="flex items-center gap-3">
                <Activity size={28} className="text-primary" />
                <Text variant="h2" className="text-primary">
                  Live Trading Dashboard
                </Text>
              </div>
              <Text variant="small" className="text-muted mt-1">
                Real-time monitoring of active positions with alerts and analytics
              </Text>
            </div>

            <div className="flex items-center gap-4">
              {/* Agent User Selector (Admin only) */}
              {isAdmin && (
                <div>
                  <Text variant="small" className="text-muted mb-1">
                    Agent User:
                  </Text>
                  <select className="px-3 py-2 bg-surface-primary border border-border-light rounded-md">
                    <option>{user?.displayName}</option>
                  </select>
                </div>
              )}

              {/* Update Status */}
              <div className="text-right">
                <div className="flex items-center gap-2">
                  <div className={`w-2 h-2 rounded-full ${wsConnected ? 'bg-profit' : 'bg-loss'}`} />
                  <Text variant="small" className="text-muted">
                    Auto-refresh every {refreshInterval}s
                  </Text>
                </div>
                <Text variant="small" className="text-loss">
                  Update failed: {lastUpdate.toLocaleTimeString()}
                </Text>
              </div>

              {/* Refresh Button */}
              <Button
                variant="primary"
                iconLeft={<RefreshCw size={20} />}
                onClick={handleRefresh}
              >
                Refresh
              </Button>
            </div>
          </div>
        </div>
      </div>

      <main className="container mx-auto p-6 space-y-6">
        {/* Active Trades Summary */}
        <Card className="bg-surface-secondary border-2 border-primary">
          <div className="p-4">
            <div className="flex items-center gap-2 mb-3">
              <TrendingUp size={20} className="text-warning" />
              <Text variant="h3" className="text-warning">
                Active Trades Summary
              </Text>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b-2 border-warning">
                    <th className="p-3 text-left">
                      <Text variant="label" className="text-warning">INVESTED AMOUNT</Text>
                    </th>
                    <th className="p-3 text-left">
                      <Text variant="label" className="text-warning">CURRENT AMOUNT</Text>
                    </th>
                    <th className="p-3 text-left">
                      <Text variant="label" className="text-warning">UNREALIZED P&L</Text>
                    </th>
                    <th className="p-3 text-left">
                      <Text variant="label" className="text-warning">UNREALIZED P&L %</Text>
                    </th>
                    <th className="p-3 text-left">
                      <Text variant="label" className="text-warning">WT. AVG AGE (DAYS)</Text>
                    </th>
                    <th className="p-3 text-left">
                      <Text variant="label" className="text-warning"># OF TRADES</Text>
                    </th>
                    <th className="p-3 text-left">
                      <Text variant="label" className="text-warning"># OF SYMBOLS</Text>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-b border-warning">
                    <td className="p-3">
                      <Text variant="body" className="font-medium">₹{investedAmount.toLocaleString('en-IN')}</Text>
                    </td>
                    <td className="p-3">
                      <Text variant="body" className="font-medium">₹{currentAmount.toLocaleString('en-IN')}</Text>
                    </td>
                    <td className="p-3">
                      <Text variant="body" className={unrealizedPnl >= 0 ? 'text-profit' : 'text-loss'}>
                        {unrealizedPnl >= 0 ? '+' : ''}₹{unrealizedPnl.toLocaleString('en-IN')}
                      </Text>
                    </td>
                    <td className="p-3">
                      <Text variant="body" className={unrealizedPnlPercent >= 0 ? 'text-profit' : 'text-loss'}>
                        {unrealizedPnlPercent >= 0 ? '+' : ''}{unrealizedPnlPercent.toFixed(2)}%
                      </Text>
                    </td>
                    <td className="p-3">
                      <Text variant="body">{avgAge.toFixed(1)}</Text>
                    </td>
                    <td className="p-3">
                      <Text variant="body">{numTrades}</Text>
                    </td>
                    <td className="p-3">
                      <Text variant="body">{numSymbols}</Text>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </Card>

        {/* Latest Trading Activity */}
        <Card className="bg-surface-secondary border-2 border-primary">
          <div className="p-4">
            <div className="flex items-center gap-2 mb-3">
              <Activity size={20} className="text-warning" />
              <Text variant="h3" className="text-warning">
                Latest Trading Activity
              </Text>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b-2 border-warning">
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">ACTION</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">TIME</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">DATE</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">SYMBOL</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">STATUS</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">PRICE</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">QTY</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">AMOUNT</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">P&L</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">P&L %</Text></th>
                  </tr>
                </thead>
                <tbody>
                  {latestActivity.slice(0, 5).map((activity, idx) => (
                    <tr key={idx} className="border-b border-warning hover:bg-surface-tertiary">
                      <td className="p-3"><Text variant="body">{activity.action}</Text></td>
                      <td className="p-3"><Text variant="body">{activity.time}</Text></td>
                      <td className="p-3"><Text variant="body">{activity.date}</Text></td>
                      <td className="p-3"><Text variant="body" className="font-medium">{activity.symbol}</Text></td>
                      <td className="p-3">
                        <Badge variant="info">{activity.status}</Badge>
                      </td>
                      <td className="p-3 text-right"><Text variant="body">₹{activity.price.toFixed(2)}</Text></td>
                      <td className="p-3 text-right"><Text variant="body">{activity.qty}</Text></td>
                      <td className="p-3 text-right"><Text variant="body">₹{activity.amount.toLocaleString('en-IN')}</Text></td>
                      <td className="p-3 text-right">
                        <Text variant="body" className={activity.pnl && activity.pnl >= 0 ? 'text-profit' : 'text-loss'}>
                          {activity.pnl ? `₹${activity.pnl.toFixed(2)}` : '-'}
                        </Text>
                      </td>
                      <td className="p-3 text-right">
                        <Text variant="body" className={activity.pnlPercent && activity.pnlPercent >= 0 ? 'text-profit' : 'text-loss'}>
                          {activity.pnlPercent ? `${activity.pnlPercent >= 0 ? '+' : ''}${activity.pnlPercent.toFixed(2)}%` : '-'}
                        </Text>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </Card>

        {/* Today's Summary */}
        <Card className="bg-surface-secondary border-2 border-primary">
          <div className="p-4">
            <div className="flex items-center gap-2 mb-3">
              <Activity size={20} className="text-warning" />
              <Text variant="h3" className="text-warning">
                Today's Summary
              </Text>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b-2 border-warning">
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">CATEGORY</Text></th>
                    <th className="p-3 text-center"><Text variant="label" className="text-warning">TRADES</Text></th>
                    <th className="p-3 text-center"><Text variant="label" className="text-warning">SYMBOLS</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">INVESTED</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">CURRENT</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">P&L</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">P&L %</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">WT. AVG AGE (DAYS)</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">AVG CAGR</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">HOLDING PERIOD BANK INT (8%)</Text></th>
                  </tr>
                </thead>
                <tbody>
                  {todaySummary.map((cat, idx) => (
                    <tr key={idx} className="border-b border-warning hover:bg-surface-tertiary">
                      <td className="p-3"><Text variant="body" className="font-medium">{cat.category}</Text></td>
                      <td className="p-3 text-center"><Text variant="body">{cat.trades}</Text></td>
                      <td className="p-3 text-center"><Text variant="body">{cat.symbols}</Text></td>
                      <td className="p-3 text-right"><Text variant="body">₹{cat.invested.toLocaleString('en-IN')}</Text></td>
                      <td className="p-3 text-right"><Text variant="body">₹{cat.current.toLocaleString('en-IN')}</Text></td>
                      <td className="p-3 text-right">
                        <Text variant="body" className={cat.pnl >= 0 ? 'text-profit' : 'text-loss'}>
                          {cat.pnl >= 0 ? '+' : ''}₹{cat.pnl.toLocaleString('en-IN')}
                        </Text>
                      </td>
                      <td className="p-3 text-right">
                        <Text variant="body" className={cat.pnlPercent >= 0 ? 'text-profit' : 'text-loss'}>
                          {cat.pnlPercent >= 0 ? '+' : ''}{cat.pnlPercent.toFixed(2)}%
                        </Text>
                      </td>
                      <td className="p-3 text-right"><Text variant="body">{cat.avgAge.toFixed(1)}</Text></td>
                      <td className="p-3 text-right">
                        <Text variant="body">{cat.avgCagr ? `${cat.avgCagr.toFixed(2)}%` : '-'}</Text>
                      </td>
                      <td className="p-3 text-right">
                        <Text variant="body">{cat.holdingPeriod ? `₹${cat.holdingPeriod.toLocaleString('en-IN')}` : '-'}</Text>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </Card>

        {/* Exited Today */}
        <Card className="bg-surface-secondary border-2 border-primary">
          <div className="p-4">
            <div className="flex items-center gap-2 mb-3">
              <Activity size={20} className="text-warning" />
              <Text variant="h3" className="text-warning">
                Exited Today
              </Text>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b-2 border-warning">
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">SYMBOL ⇅</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">STATUS ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">QTY ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">ENTRY PRICE ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">EXIT PRICE ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">AMOUNT RELEASED ⇅</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">ENTRY DATE ⇅</Text></th>
                    <th className="p-3 text-center"><Text variant="label" className="text-warning">AGE ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">P&L ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">P&L % ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">HOLDING PERIOD BANK INT (18%) ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">CAGR ⇅</Text></th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-b border-warning hover:bg-surface-tertiary">
                    <td className="p-3" colSpan={12}>
                      <Text variant="body" className="text-muted text-center">
                        No trades exited today
                      </Text>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </Card>

        {/* Bought Today */}
        <Card className="bg-surface-secondary border-2 border-primary">
          <div className="p-4">
            <div className="flex items-center gap-2 mb-3">
              <Activity size={20} className="text-warning" />
              <Text variant="h3" className="text-warning">
                Bought Today
              </Text>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b-2 border-warning">
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">SYMBOL ⇅</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">TYPE ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">ENTRY PRICE ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">QTY ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">INVESTED ⇅</Text></th>
                    <th className="p-3 text-left"><Text variant="label" className="text-warning">ENTRY TIME ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">CURRENT ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">VALUE ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">UNREAL P&L ⇅</Text></th>
                    <th className="p-3 text-right"><Text variant="label" className="text-warning">P&L % ⇅</Text></th>
                    <th className="p-3 text-center"><Text variant="label" className="text-warning">AGE ⇅</Text></th>
                  </tr>
                </thead>
                <tbody>
                  {(trades || []).filter(t => {
                    const entryTime = new Date(t.entryTime);
                    const today = new Date();
                    return entryTime.toDateString() === today.toDateString();
                  }).slice(0, 10).map((trade) => {
                    const currentPrice = trade.exitPrice || trade.entryPrice * 1.01; // Mock current price
                    const invested = trade.entryPrice * trade.quantity;
                    const currentValue = currentPrice * trade.quantity;
                    const unrealPnl = currentValue - invested;
                    const pnlPercent = (unrealPnl / invested) * 100;
                    const age = Math.floor((Date.now() - new Date(trade.entryTime).getTime()) / (1000 * 60 * 60 * 24));

                    return (
                      <tr key={trade.id} className="border-b border-warning hover:bg-surface-tertiary">
                        <td className="p-3"><Text variant="body" className="font-medium">{trade.symbol}</Text></td>
                        <td className="p-3">
                          <Badge variant={trade.direction === 'BUY' ? 'success' : 'error'}>
                            {trade.direction === 'BUY' ? 'RE_BUY' : 'SELL'}
                          </Badge>
                        </td>
                        <td className="p-3 text-right"><Text variant="body">₹{trade.entryPrice.toFixed(2)}</Text></td>
                        <td className="p-3 text-right"><Text variant="body">{trade.quantity}</Text></td>
                        <td className="p-3 text-right"><Text variant="body">₹{invested.toLocaleString('en-IN')}</Text></td>
                        <td className="p-3"><Text variant="body">{new Date(trade.entryTime).toLocaleString()}</Text></td>
                        <td className="p-3 text-right"><Text variant="body">₹{currentPrice.toFixed(2)}</Text></td>
                        <td className="p-3 text-right"><Text variant="body">₹{currentValue.toLocaleString('en-IN')}</Text></td>
                        <td className="p-3 text-right">
                          <Text variant="body" className={unrealPnl >= 0 ? 'text-profit' : 'text-loss'}>
                            {unrealPnl >= 0 ? '+' : ''}₹{unrealPnl.toFixed(2)}
                          </Text>
                        </td>
                        <td className="p-3 text-right">
                          <Text variant="body" className={pnlPercent >= 0 ? 'text-profit' : 'text-loss'}>
                            {pnlPercent >= 0 ? '+' : ''}{pnlPercent.toFixed(2)}%
                          </Text>
                        </td>
                        <td className="p-3 text-center"><Text variant="body">{age}</Text></td>
                      </tr>
                    );
                  })}
                  {(!trades || trades.length === 0 || trades.filter(t => {
                    const entryTime = new Date(t.entryTime);
                    const today = new Date();
                    return entryTime.toDateString() === today.toDateString();
                  }).length === 0) && (
                    <tr className="border-b border-warning">
                      <td className="p-3" colSpan={11}>
                        <Text variant="body" className="text-muted text-center">
                          No trades bought today
                        </Text>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </Card>
      </main>
    </div>
  );
}
