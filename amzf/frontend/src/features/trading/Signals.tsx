/**
 * Signals Page
 * Trading signals with intent creation
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useSignals } from '../../hooks/useApi';
import { useSignalUpdates } from '../../hooks/useWebSocket';
import { Header } from '../../components/organisms/Header/Header';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Button } from '../../components/atoms/Button/Button';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import type { Signal } from '../../types';
import { TrendingUp, RefreshCw, Target } from 'lucide-react';
import { getNavItems } from '../../lib/navigation';

/**
 * Signals component
 */
export function Signals() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getNavItems(location.pathname);
  const { data: signals, loading, error, refetch } = useSignals(undefined, 50);
  const [liveSignals, setLiveSignals] = useState<Signal[]>([]);

  // Listen for real-time signal updates
  useSignalUpdates((signal) => {
    setLiveSignals((prev) => {
      const index = prev.findIndex((s) => s.id === signal.id);
      if (index >= 0) {
        const updated = [...prev];
        updated[index] = signal;
        return updated;
      }
      return [signal, ...prev];
    });
  });

  // Merge API signals with live updates
  const allSignals = signals
    ? [...liveSignals, ...signals.filter((s) => !liveSignals.find((ls) => ls.id === s.id))]
    : liveSignals;

  /**
   * Get badge variant for signal strength
   */
  const getStrengthVariant = (strength: Signal['strength']) => {
    switch (strength) {
      case 'VERY_STRONG':
        return 'success';
      case 'STRONG':
        return 'info';
      case 'MODERATE':
        return 'warning';
      case 'WEAK':
        return 'default';
      default:
        return 'default';
    }
  };

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <div className="min-h-screen bg-background">
        <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />
        <main className="container mx-auto p-6">
          <div className="flex items-center justify-center py-12">
            <Spinner size="lg" variant="primary" />
          </div>
        </main>
      </div>
    );
  }

  /**
   * Render error state
   */
  if (error) {
    return (
      <div className="min-h-screen bg-background">
        <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />
        <main className="container mx-auto p-6">
          <Alert variant="error">
            Failed to load signals: {error}
            <Button variant="secondary" size="sm" onClick={refetch} className="mt-3">
              Retry
            </Button>
          </Alert>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />

      <main className="container mx-auto p-6 space-y-6">
        {/* Page Header */}
        <div className="flex items-center justify-between">
          <div>
            <Text variant="h1" className="mb-2">
              Trading Signals
            </Text>
            <Text variant="body" className="text-muted">
              AI-generated trading opportunities
            </Text>
          </div>

          <Button
            variant="secondary"
            iconLeft={<RefreshCw size={20} />}
            onClick={refetch}
          >
            Refresh
          </Button>
        </div>

        {/* Signals Grid */}
        {allSignals.length > 0 ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {allSignals.map((signal) => (
              <Card key={signal.id} variant="outlined" interactive>
                <div className="p-6 space-y-4">
                  {/* Header */}
                  <div className="flex items-start justify-between">
                    <div>
                      <Text variant="h3" className="mb-1">
                        {signal.symbol}
                      </Text>
                      <div className="flex items-center gap-2">
                        <Badge
                          variant={signal.direction === 'BUY' ? 'success' : 'error'}
                        >
                          {signal.direction}
                        </Badge>
                        <Badge variant={getStrengthVariant(signal.strength)}>
                          {signal.strength}
                        </Badge>
                        <Badge variant="default">{signal.timeframe}</Badge>
                      </div>
                    </div>
                    <Text variant="small" className="text-muted">
                      #{signal.seq}
                    </Text>
                  </div>

                  {/* Price Levels */}
                  <div className="grid grid-cols-3 gap-4 py-3 border-y border-border-light">
                    <div>
                      <Text variant="small" className="text-muted mb-1">
                        Entry
                      </Text>
                      <Text variant="label" className="text-primary">
                        ₹{signal.entryPrice.toFixed(2)}
                      </Text>
                    </div>
                    {signal.target && (
                      <div>
                        <Text variant="small" className="text-muted mb-1">
                          Target
                        </Text>
                        <Text variant="label" className="text-profit">
                          ₹{signal.target.toFixed(2)}
                        </Text>
                      </div>
                    )}
                    {signal.stopLoss && (
                      <div>
                        <Text variant="small" className="text-muted mb-1">
                          Stop Loss
                        </Text>
                        <Text variant="label" className="text-loss">
                          ₹{signal.stopLoss.toFixed(2)}
                        </Text>
                      </div>
                    )}
                  </div>

                  {/* Reason */}
                  <div>
                    <Text variant="small" className="text-muted mb-1">
                      Analysis
                    </Text>
                    <Text variant="body" className="text-sm">
                      {signal.reason}
                    </Text>
                  </div>

                  {/* MTF Analysis */}
                  {signal.mtfAnalysis && (
                    <div>
                      <Text variant="small" className="text-muted mb-2">
                        Multi-Timeframe Analysis
                      </Text>
                      <div className="space-y-2">
                        <div className="flex items-center gap-2 text-sm">
                          <Text variant="small" className="w-20">
                            {signal.mtfAnalysis.primary.timeframe}:
                          </Text>
                          <Badge
                            variant={
                              signal.mtfAnalysis.primary.trend === 'BULLISH'
                                ? 'success'
                                : signal.mtfAnalysis.primary.trend === 'BEARISH'
                                ? 'error'
                                : 'default'
                            }
                          >
                            {signal.mtfAnalysis.primary.trend}
                          </Badge>
                        </div>
                        <div className="flex items-center gap-2 text-sm">
                          <Text variant="small" className="w-20">
                            Confluence:
                          </Text>
                          <Badge
                            variant={getStrengthVariant(
                              signal.mtfAnalysis.confluenceStrength === 'STRONG'
                                ? 'STRONG'
                                : 'MODERATE'
                            )}
                          >
                            {signal.mtfAnalysis.confluenceStrength}
                          </Badge>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Metadata */}
                  <div className="flex items-center justify-between pt-3 border-t border-border-light">
                    <div>
                      <Text variant="small" className="text-muted">
                        Confidence: {(signal.confidence * 100).toFixed(0)}%
                      </Text>
                      <Text variant="small" className="text-muted">
                        {new Date(signal.generatedAt).toLocaleString()}
                      </Text>
                    </div>
                    <Button variant="primary" size="sm" iconLeft={<Target size={16} />}>
                      Create Intent
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        ) : (
          <Card>
            <div className="p-12">
              <EmptyState
                icon={<TrendingUp size={48} />}
                title="No Signals Available"
                description="There are no trading signals at the moment. Check back later for new opportunities."
              />
            </div>
          </Card>
        )}
      </main>
    </div>
  );
}
