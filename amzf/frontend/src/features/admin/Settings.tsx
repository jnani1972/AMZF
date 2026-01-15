/**
 * Settings Page
 * System settings and configuration
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { Header } from '@/components/organisms/Header/Header';
import { Text } from '@/components/atoms/Text/Text';
import { Card } from '@/components/atoms/Card/Card';
import { Button } from '@/components/atoms/Button/Button';
import { Input } from '@/components/atoms/Input/Input';
import { Badge } from '@/components/atoms/Badge/Badge';
import { Alert } from '@/components/atoms/Alert/Alert';
import { Save, Settings as SettingsIcon, TrendingUp, Database, Bell } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Timeframe } from '@/types';
import { getAdminNavItems } from '@/lib/navigation';

/**
 * Settings component
 */
export function Settings() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getAdminNavItems(location.pathname);
  const [hasChanges, setHasChanges] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // MTF Configuration
  const [primaryTimeframe, setPrimaryTimeframe] = useState<Timeframe>('15m');
  const [secondaryTimeframe, setSecondaryTimeframe] = useState<Timeframe>('1h');
  const [tertiaryTimeframe, setTertiaryTimeframe] = useState<Timeframe>('1d');

  // System Settings
  const [maxConcurrentOrders, setMaxConcurrentOrders] = useState(10);
  const [orderTimeout, setOrderTimeout] = useState(30);
  const [wsReconnectDelay, setWsReconnectDelay] = useState(5);
  const [dataRetentionDays, setDataRetentionDays] = useState(90);

  // Notification Settings
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [tradeAlerts, setTradeAlerts] = useState(true);
  const [systemAlerts, setSystemAlerts] = useState(true);

  const timeframes: Timeframe[] = ['1m', '5m', '15m', '1h', '1d'];

  /**
   * Handle save settings
   */
  const handleSave = () => {
    console.log('Saving settings...');
    setSaveSuccess(true);
    setHasChanges(false);
    setTimeout(() => setSaveSuccess(false), 3000);
  };

  /**
   * Mark as changed
   */
  const markChanged = () => {
    if (!hasChanges) setHasChanges(true);
  };

  return (
    <div className="min-h-screen bg-background">
      <Header navItems={navItems} user={user ? { name: user.displayName, email: user.email } : undefined} onLogout={logout} />

      <main className="container mx-auto p-6 space-y-6">
        {/* Page Header */}
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <Link to="/admin">
                <Text variant="body" className="text-muted hover:text-primary">
                  Admin
                </Text>
              </Link>
              <Text variant="body" className="text-muted">
                /
              </Text>
              <Text variant="h2">Settings</Text>
            </div>
            <Text variant="body" className="text-muted">
              Configure system settings and preferences
            </Text>
          </div>

          <Button
            variant="primary"
            iconLeft={<Save size={20} />}
            onClick={handleSave}
            disabled={!hasChanges}
          >
            Save Changes
          </Button>
        </div>

        {/* Success Alert */}
        {saveSuccess && (
          <Alert variant="success" onDismiss={() => setSaveSuccess(false)}>
            Settings saved successfully!
          </Alert>
        )}

        {/* MTF Configuration */}
        <Card>
          <div className="p-6 space-y-6">
            <div className="flex items-center gap-3">
              <TrendingUp size={24} className="text-primary" />
              <div>
                <Text variant="h3">Multi-Timeframe Configuration</Text>
                <Text variant="body" className="text-muted text-sm">
                  Configure default timeframes for MTF analysis
                </Text>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Primary Timeframe */}
              <div>
                <Text variant="label" className="mb-2">
                  Primary Timeframe
                </Text>
                <div className="flex flex-wrap gap-2">
                  {timeframes.map((tf) => (
                    <Button
                      key={tf}
                      variant={primaryTimeframe === tf ? 'primary' : 'secondary'}
                      size="sm"
                      onClick={() => {
                        setPrimaryTimeframe(tf);
                        markChanged();
                      }}
                    >
                      {tf}
                    </Button>
                  ))}
                </div>
                <Text variant="small" className="text-muted mt-2">
                  Main analysis timeframe
                </Text>
              </div>

              {/* Secondary Timeframe */}
              <div>
                <Text variant="label" className="mb-2">
                  Secondary Timeframe
                </Text>
                <div className="flex flex-wrap gap-2">
                  {timeframes.map((tf) => (
                    <Button
                      key={tf}
                      variant={secondaryTimeframe === tf ? 'primary' : 'secondary'}
                      size="sm"
                      onClick={() => {
                        setSecondaryTimeframe(tf);
                        markChanged();
                      }}
                    >
                      {tf}
                    </Button>
                  ))}
                </div>
                <Text variant="small" className="text-muted mt-2">
                  Supporting timeframe
                </Text>
              </div>

              {/* Tertiary Timeframe */}
              <div>
                <Text variant="label" className="mb-2">
                  Tertiary Timeframe
                </Text>
                <div className="flex flex-wrap gap-2">
                  {timeframes.map((tf) => (
                    <Button
                      key={tf}
                      variant={tertiaryTimeframe === tf ? 'primary' : 'secondary'}
                      size="sm"
                      onClick={() => {
                        setTertiaryTimeframe(tf);
                        markChanged();
                      }}
                    >
                      {tf}
                    </Button>
                  ))}
                </div>
                <Text variant="small" className="text-muted mt-2">
                  Long-term timeframe
                </Text>
              </div>
            </div>

            <Alert variant="info">
              <Text variant="body" className="text-sm">
                <strong>Current Configuration:</strong> {primaryTimeframe} (Primary) • {secondaryTimeframe}{' '}
                (Secondary) • {tertiaryTimeframe} (Tertiary)
              </Text>
            </Alert>
          </div>
        </Card>

        {/* System Settings */}
        <Card>
          <div className="p-6 space-y-6">
            <div className="flex items-center gap-3">
              <SettingsIcon size={24} className="text-primary" />
              <div>
                <Text variant="h3">System Settings</Text>
                <Text variant="body" className="text-muted text-sm">
                  Configure system behavior and limits
                </Text>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Max Concurrent Orders */}
              <div>
                <Text variant="label" className="mb-2">
                  Max Concurrent Orders
                </Text>
                <Input
                  type="number"
                  value={maxConcurrentOrders}
                  onChange={(e) => {
                    setMaxConcurrentOrders(Number(e.target.value));
                    markChanged();
                  }}
                  fullWidth
                />
                <Text variant="small" className="text-muted mt-1">
                  Maximum number of orders per user
                </Text>
              </div>

              {/* Order Timeout */}
              <div>
                <Text variant="label" className="mb-2">
                  Order Timeout (seconds)
                </Text>
                <Input
                  type="number"
                  value={orderTimeout}
                  onChange={(e) => {
                    setOrderTimeout(Number(e.target.value));
                    markChanged();
                  }}
                  fullWidth
                />
                <Text variant="small" className="text-muted mt-1">
                  Time before order expires
                </Text>
              </div>

              {/* WebSocket Reconnect Delay */}
              <div>
                <Text variant="label" className="mb-2">
                  WebSocket Reconnect Delay (seconds)
                </Text>
                <Input
                  type="number"
                  value={wsReconnectDelay}
                  onChange={(e) => {
                    setWsReconnectDelay(Number(e.target.value));
                    markChanged();
                  }}
                  fullWidth
                />
                <Text variant="small" className="text-muted mt-1">
                  Initial reconnection delay
                </Text>
              </div>

              {/* Data Retention */}
              <div>
                <Text variant="label" className="mb-2">
                  Data Retention (days)
                </Text>
                <Input
                  type="number"
                  value={dataRetentionDays}
                  onChange={(e) => {
                    setDataRetentionDays(Number(e.target.value));
                    markChanged();
                  }}
                  fullWidth
                />
                <Text variant="small" className="text-muted mt-1">
                  Days to retain historical data
                </Text>
              </div>
            </div>
          </div>
        </Card>

        {/* Database Settings */}
        <Card>
          <div className="p-6 space-y-6">
            <div className="flex items-center gap-3">
              <Database size={24} className="text-primary" />
              <div>
                <Text variant="h3">Database Settings</Text>
                <Text variant="body" className="text-muted text-sm">
                  Database connection and optimization settings
                </Text>
              </div>
            </div>

            <div className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-surface-secondary rounded-lg">
                <div>
                  <Text variant="body" className="font-medium">
                    Connection Pool Size
                  </Text>
                  <Text variant="small" className="text-muted">
                    Current: 20 connections
                  </Text>
                </div>
                <Badge variant="success">Optimal</Badge>
              </div>

              <div className="flex items-center justify-between p-4 bg-surface-secondary rounded-lg">
                <div>
                  <Text variant="body" className="font-medium">
                    Query Timeout
                  </Text>
                  <Text variant="small" className="text-muted">
                    Current: 30 seconds
                  </Text>
                </div>
                <Badge variant="success">Optimal</Badge>
              </div>

              <div className="flex items-center justify-between p-4 bg-surface-secondary rounded-lg">
                <div>
                  <Text variant="body" className="font-medium">
                    Auto-Vacuum
                  </Text>
                  <Text variant="small" className="text-muted">
                    Runs daily at 2:00 AM
                  </Text>
                </div>
                <Badge variant="info">Enabled</Badge>
              </div>
            </div>
          </div>
        </Card>

        {/* Notification Settings */}
        <Card>
          <div className="p-6 space-y-6">
            <div className="flex items-center gap-3">
              <Bell size={24} className="text-primary" />
              <div>
                <Text variant="h3">Notification Settings</Text>
                <Text variant="body" className="text-muted text-sm">
                  Configure system notifications
                </Text>
              </div>
            </div>

            <div className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-surface-secondary rounded-lg">
                <div>
                  <Text variant="body" className="font-medium">
                    Email Notifications
                  </Text>
                  <Text variant="small" className="text-muted">
                    Send email alerts for important events
                  </Text>
                </div>
                <Button
                  variant={emailNotifications ? 'primary' : 'secondary'}
                  size="sm"
                  onClick={() => {
                    setEmailNotifications(!emailNotifications);
                    markChanged();
                  }}
                >
                  {emailNotifications ? 'Enabled' : 'Disabled'}
                </Button>
              </div>

              <div className="flex items-center justify-between p-4 bg-surface-secondary rounded-lg">
                <div>
                  <Text variant="body" className="font-medium">
                    Trade Alerts
                  </Text>
                  <Text variant="small" className="text-muted">
                    Notify when trades are executed
                  </Text>
                </div>
                <Button
                  variant={tradeAlerts ? 'primary' : 'secondary'}
                  size="sm"
                  onClick={() => {
                    setTradeAlerts(!tradeAlerts);
                    markChanged();
                  }}
                >
                  {tradeAlerts ? 'Enabled' : 'Disabled'}
                </Button>
              </div>

              <div className="flex items-center justify-between p-4 bg-surface-secondary rounded-lg">
                <div>
                  <Text variant="body" className="font-medium">
                    System Alerts
                  </Text>
                  <Text variant="small" className="text-muted">
                    Critical system notifications
                  </Text>
                </div>
                <Button
                  variant={systemAlerts ? 'primary' : 'secondary'}
                  size="sm"
                  onClick={() => {
                    setSystemAlerts(!systemAlerts);
                    markChanged();
                  }}
                >
                  {systemAlerts ? 'Enabled' : 'Disabled'}
                </Button>
              </div>
            </div>
          </div>
        </Card>
      </main>
    </div>
  );
}
