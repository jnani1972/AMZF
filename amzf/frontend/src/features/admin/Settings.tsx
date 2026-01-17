/**
 * Settings Page
 * System settings and configuration
 */

import { useState, useEffect } from 'react';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Button } from '../../components/atoms/Button/Button';
import { Input } from '../../components/atoms/Input/Input';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { Save, Settings as SettingsIcon, TrendingUp, Database, Bell } from 'lucide-react';
import { apiClient } from '../../lib/api';
import type { MtfGlobalConfig } from '../../types';

/**
 * Settings component
 */
export function Settings() {
  const [loading, setLoading] = useState(true);
  const [hasChanges, setHasChanges] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [config, setConfig] = useState<MtfGlobalConfig | null>(null);

  // MTF Configuration - LTF/ITF/HTF (1m, 25m, 125m)
  const [ltfMinutes, setLtfMinutes] = useState(1);
  const [ltfCount, setLtfCount] = useState(375);
  const [ltfWeight, setLtfWeight] = useState(20);

  const [itfMinutes, setItfMinutes] = useState(25);
  const [itfCount, setItfCount] = useState(75);
  const [itfWeight, setItfWeight] = useState(30);

  const [htfMinutes, setHtfMinutes] = useState(125);
  const [htfCount, setHtfCount] = useState(175);
  const [htfWeight, setHtfWeight] = useState(50);

  // System Settings
  const [maxConcurrentOrders, setMaxConcurrentOrders] = useState(10);
  const [orderTimeout, setOrderTimeout] = useState(30);
  const [wsReconnectDelay, setWsReconnectDelay] = useState(5);
  const [dataRetentionDays, setDataRetentionDays] = useState(90);
  const [inactivityTimeout, setInactivityTimeout] = useState(15); // In minutes

  // Notification Settings
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [tradeAlerts, setTradeAlerts] = useState(true);
  const [systemAlerts, setSystemAlerts] = useState(true);

  const mtfTimeframes = [1, 25, 125] as const;

  /**
   * Load MTF configuration on mount
   */
  useEffect(() => {
    const loadConfig = async () => {
      setLoading(true);
      const response = await apiClient.getGlobalMTFConfig();

      if (response.success && response.data) {
        setConfig(response.data);

        // Set LTF (Lower Timeframe)
        setLtfMinutes(response.data.ltfCandleMinutes);
        setLtfCount(response.data.ltfCandleCount);
        setLtfWeight(response.data.ltfWeight);

        // Set ITF (Intermediate Timeframe)
        setItfMinutes(response.data.itfCandleMinutes);
        setItfCount(response.data.itfCandleCount);
        setItfWeight(response.data.itfWeight);

        // Set HTF (Higher Timeframe)
        setHtfMinutes(response.data.htfCandleMinutes);
        setHtfCount(response.data.htfCandleCount);
        setHtfWeight(response.data.htfWeight);
      }

      // Load inactivity timeout from localStorage (default: 15 minutes)
      const storedTimeout = localStorage.getItem('inactivity_timeout_minutes');
      if (storedTimeout) {
        setInactivityTimeout(parseInt(storedTimeout, 10));
      }

      setLoading(false);
    };

    loadConfig();
  }, []);

  /**
   * Handle save settings
   */
  const handleSave = async () => {
    setSaveError(null);

    // Validate inactivity timeout (min 1 minute, max 1440 minutes / 24 hours)
    if (inactivityTimeout < 1 || inactivityTimeout > 1440) {
      setSaveError('Inactivity timeout must be between 1 and 1440 minutes (24 hours)');
      return;
    }

    const updates: Partial<MtfGlobalConfig> = {
      ltfCandleMinutes: ltfMinutes,
      ltfCandleCount: ltfCount,
      ltfWeight: ltfWeight,

      itfCandleMinutes: itfMinutes,
      itfCandleCount: itfCount,
      itfWeight: itfWeight,

      htfCandleMinutes: htfMinutes,
      htfCandleCount: htfCount,
      htfWeight: htfWeight,
    };

    const response = await apiClient.updateGlobalMTFConfig(updates);

    if (response.success) {
      // Save inactivity timeout to localStorage
      localStorage.setItem('inactivity_timeout_minutes', inactivityTimeout.toString());

      setSaveSuccess(true);
      setHasChanges(false);
      setTimeout(() => setSaveSuccess(false), 3000);
    } else {
      setSaveError(response.error || 'Failed to save settings');
    }
  };

  /**
   * Mark as changed
   */
  const markChanged = () => {
    if (!hasChanges) setHasChanges(true);
  };

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <main className="container mx-auto p-6">
        <div className="flex items-center justify-center py-12">
          <Spinner size="lg" variant="primary" />
        </div>
      </main>
    );
  }

  return (
    <main className="container mx-auto p-6 space-y-6">
        {/* Page Header */}
        <div className="flex items-center justify-between">
          <div>
            <Text variant="h2" className="mb-2">Settings</Text>
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

        {/* Error Alert */}
        {saveError && (
          <Alert variant="error" onDismiss={() => setSaveError(null)}>
            {saveError}
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
                  Configure LTF/ITF/HTF analysis parameters (1m, 25m, 125m)
                </Text>
              </div>
            </div>

            <div className="form-grid form-grid--cols-3">
              {/* LTF - Lower Timeframe */}
              <div className="space-y-4">
                <div>
                  <Text variant="label" className="mb-2">
                    LTF (Lower Timeframe)
                  </Text>
                  <div className="flex flex-wrap gap-2">
                    <Badge variant="primary">{ltfMinutes} minutes</Badge>
                  </div>
                  <Text variant="small" className="text-muted mt-2">
                    Fast timeframe for entry precision
                  </Text>
                </div>

                <div>
                  <Text variant="label" className="mb-2">
                    Candle Count
                  </Text>
                  <Input
                    type="number"
                    value={ltfCount}
                    onChange={(e) => {
                      setLtfCount(Number(e.target.value));
                      markChanged();
                    }}
                    fullWidth
                  />
                </div>

                <div>
                  <Text variant="label" className="mb-2">
                    Weight (%)
                  </Text>
                  <Input
                    type="number"
                    value={ltfWeight}
                    onChange={(e) => {
                      setLtfWeight(Number(e.target.value));
                      markChanged();
                    }}
                    fullWidth
                  />
                </div>
              </div>

              {/* ITF - Intermediate Timeframe */}
              <div className="space-y-4">
                <div>
                  <Text variant="label" className="mb-2">
                    ITF (Intermediate Timeframe)
                  </Text>
                  <div className="flex flex-wrap gap-2">
                    <Badge variant="primary">{itfMinutes} minutes</Badge>
                  </div>
                  <Text variant="small" className="text-muted mt-2">
                    Medium timeframe for trend confirmation
                  </Text>
                </div>

                <div>
                  <Text variant="label" className="mb-2">
                    Candle Count
                  </Text>
                  <Input
                    type="number"
                    value={itfCount}
                    onChange={(e) => {
                      setItfCount(Number(e.target.value));
                      markChanged();
                    }}
                    fullWidth
                  />
                </div>

                <div>
                  <Text variant="label" className="mb-2">
                    Weight (%)
                  </Text>
                  <Input
                    type="number"
                    value={itfWeight}
                    onChange={(e) => {
                      setItfWeight(Number(e.target.value));
                      markChanged();
                    }}
                    fullWidth
                  />
                </div>
              </div>

              {/* HTF - Higher Timeframe */}
              <div className="space-y-4">
                <div>
                  <Text variant="label" className="mb-2">
                    HTF (Higher Timeframe)
                  </Text>
                  <div className="flex flex-wrap gap-2">
                    <Badge variant="primary">{htfMinutes} minutes</Badge>
                  </div>
                  <Text variant="small" className="text-muted mt-2">
                    Slow timeframe for trend direction
                  </Text>
                </div>

                <div>
                  <Text variant="label" className="mb-2">
                    Candle Count
                  </Text>
                  <Input
                    type="number"
                    value={htfCount}
                    onChange={(e) => {
                      setHtfCount(Number(e.target.value));
                      markChanged();
                    }}
                    fullWidth
                  />
                </div>

                <div>
                  <Text variant="label" className="mb-2">
                    Weight (%)
                  </Text>
                  <Input
                    type="number"
                    value={htfWeight}
                    onChange={(e) => {
                      setHtfWeight(Number(e.target.value));
                      markChanged();
                    }}
                    fullWidth
                  />
                </div>
              </div>
            </div>

            <Alert variant="info">
              <Text variant="body" className="text-sm">
                <strong>Current Configuration:</strong> LTF {ltfMinutes}m (Count: {ltfCount}, Weight: {ltfWeight}%) • ITF {itfMinutes}m (Count: {itfCount}, Weight: {itfWeight}%) • HTF {htfMinutes}m (Count: {htfCount}, Weight: {htfWeight}%)
              </Text>
            </Alert>

            <Alert variant="warning">
              <Text variant="body" className="text-sm">
                <strong>Note:</strong> MTF system uses 25× multiplier - LTF: 1m, ITF: 25m (25× LTF), HTF: 125m (25× ITF). Changing these values requires system restart.
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

            <div className="form-grid form-grid--cols-2">
              {/* Inactivity Timeout */}
              <div>
                <Text variant="label" className="mb-2">
                  Session Inactivity Timeout (minutes)
                </Text>
                <Input
                  type="number"
                  value={inactivityTimeout}
                  onChange={(e) => {
                    setInactivityTimeout(Number(e.target.value));
                    markChanged();
                  }}
                  fullWidth
                  min={1}
                  max={1440}
                />
                <Text variant="small" className="text-muted mt-1">
                  Auto-logout after inactivity (1-1440 min)
                </Text>
              </div>

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

            <Alert variant="info">
              <Text variant="body" className="text-sm">
                <strong>Security Note:</strong> Session Inactivity Timeout automatically logs users out after {inactivityTimeout} minutes of no activity. This protects against unauthorized access when users leave their trading terminals unattended.
              </Text>
            </Alert>
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
  );
}
