import React, { useState, useEffect } from 'react';
import { Settings, Save, RefreshCw, AlertCircle, CheckCircle, TrendingUp, Target, Shield } from 'lucide-react';
import MtfSymbolConfigTable from './MtfSymbolConfigTable';

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
  || (import.meta?.env?.VITE_API_BASE_URL)
  || "http://localhost:9090";

const MtfConfigManagement = ({ token }) => {
  const [activeTab, setActiveTab] = useState('global');
  const [globalConfig, setGlobalConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState(null);

  useEffect(() => {
    fetchGlobalConfig();
  }, []);

  const fetchGlobalConfig = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/mtf-config`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch MTF configuration');
      const data = await res.json();
      setGlobalConfig(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveGlobal = async () => {
    setSaving(true);
    setSaveStatus(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/mtf-config`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(globalConfig)
      });
      if (!res.ok) throw new Error('Failed to save configuration');
      setSaveStatus({ type: 'success', message: 'Configuration saved successfully! Existing signals marked as STALE.' });
      await fetchGlobalConfig();
    } catch (err) {
      setSaveStatus({ type: 'error', message: err.message });
    } finally {
      setSaving(false);
    }
  };

  const handleFieldChange = (field, value) => {
    setGlobalConfig(prev => ({
      ...prev,
      [field]: value
    }));
  };

  const tabs = [
    { id: 'global', label: 'Global Configuration', icon: Settings },
    { id: 'symbols', label: 'Symbol Overrides', icon: TrendingUp }
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center p-8">
        <RefreshCw className="w-6 h-6 animate-spin text-purple-500" />
        <span className="ml-2 text-gray-400">Loading configuration...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-900/20 border border-red-500 rounded-lg p-4">
        <div className="flex items-center space-x-2">
          <AlertCircle className="w-5 h-5 text-red-500" />
          <span className="text-red-400">{error}</span>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <Settings className="w-8 h-8 text-purple-500" />
            <div>
              <h2 className="text-2xl font-bold text-white">MTF Configuration</h2>
              <p className="text-sm text-gray-400">Multi-Timeframe Log-Safe Mean Reversion System</p>
            </div>
          </div>
          <button
            onClick={fetchGlobalConfig}
            className="flex items-center space-x-2 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex space-x-2 bg-gray-800 border border-gray-700 rounded-lg p-1">
        {tabs.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex-1 flex items-center justify-center space-x-2 px-4 py-2 rounded-md transition-all ${
                activeTab === tab.id
                  ? 'bg-purple-600 text-white'
                  : 'text-gray-400 hover:text-white hover:bg-gray-700'
              }`}
            >
              <Icon className="w-4 h-4" />
              <span className="font-medium">{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Save Status */}
      {saveStatus && (
        <div className={`border rounded-lg p-4 ${
          saveStatus.type === 'success'
            ? 'bg-green-900/20 border-green-500'
            : 'bg-red-900/20 border-red-500'
        }`}>
          <div className="flex items-center space-x-2">
            {saveStatus.type === 'success' ? (
              <CheckCircle className="w-5 h-5 text-green-500" />
            ) : (
              <AlertCircle className="w-5 h-5 text-red-500" />
            )}
            <span className={saveStatus.type === 'success' ? 'text-green-400' : 'text-red-400'}>
              {saveStatus.message}
            </span>
          </div>
        </div>
      )}

      {/* Tab Content */}
      {activeTab === 'global' && globalConfig && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 space-y-6">
          {/* Timeframe Configuration */}
          <ConfigSection title="Timeframe Configuration" icon={TrendingUp}>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <TimeframeConfig
                label="HTF (Higher Timeframe)"
                prefix="htf"
                config={globalConfig}
                onChange={handleFieldChange}
              />
              <TimeframeConfig
                label="ITF (Intermediate Timeframe)"
                prefix="itf"
                config={globalConfig}
                onChange={handleFieldChange}
              />
              <TimeframeConfig
                label="LTF (Lower Timeframe)"
                prefix="ltf"
                config={globalConfig}
                onChange={handleFieldChange}
              />
            </div>
          </ConfigSection>

          {/* Zone Detection */}
          <ConfigSection title="Zone Detection" icon={Target}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <NumberInput
                label="Buy Zone %"
                value={globalConfig.buyZonePct}
                onChange={(v) => handleFieldChange('buyZonePct', v)}
                step="0.01"
                min="0"
                max="1"
              />
            </div>
          </ConfigSection>

          {/* Confluence Settings */}
          <ConfigSection title="Confluence Settings" icon={CheckCircle}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <TextInput
                label="Min Confluence Type"
                value={globalConfig.minConfluenceType}
                onChange={(v) => handleFieldChange('minConfluenceType', v)}
              />
              <NumberInput
                label="Threshold: Very Strong"
                value={globalConfig.strengthThresholdVeryStrong}
                onChange={(v) => handleFieldChange('strengthThresholdVeryStrong', v)}
                step="0.01"
              />
              <NumberInput
                label="Threshold: Strong"
                value={globalConfig.strengthThresholdStrong}
                onChange={(v) => handleFieldChange('strengthThresholdStrong', v)}
                step="0.01"
              />
              <NumberInput
                label="Threshold: Moderate"
                value={globalConfig.strengthThresholdModerate}
                onChange={(v) => handleFieldChange('strengthThresholdModerate', v)}
                step="0.01"
              />
              <NumberInput
                label="Multiplier: Very Strong"
                value={globalConfig.multiplierVeryStrong}
                onChange={(v) => handleFieldChange('multiplierVeryStrong', v)}
                step="0.01"
              />
              <NumberInput
                label="Multiplier: Strong"
                value={globalConfig.multiplierStrong}
                onChange={(v) => handleFieldChange('multiplierStrong', v)}
                step="0.01"
              />
              <NumberInput
                label="Multiplier: Moderate"
                value={globalConfig.multiplierModerate}
                onChange={(v) => handleFieldChange('multiplierModerate', v)}
                step="0.01"
              />
              <NumberInput
                label="Multiplier: Weak"
                value={globalConfig.multiplierWeak}
                onChange={(v) => handleFieldChange('multiplierWeak', v)}
                step="0.01"
              />
            </div>
          </ConfigSection>

          {/* Log-Utility & Kelly */}
          <ConfigSection title="Risk Management" icon={Shield}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <NumberInput
                label="Max Position Log Loss"
                value={globalConfig.maxPositionLogLoss}
                onChange={(v) => handleFieldChange('maxPositionLogLoss', v)}
                step="0.0001"
              />
              <NumberInput
                label="Max Portfolio Log Loss"
                value={globalConfig.maxPortfolioLogLoss}
                onChange={(v) => handleFieldChange('maxPortfolioLogLoss', v)}
                step="0.0001"
              />
              <NumberInput
                label="Kelly Fraction"
                value={globalConfig.kellyFraction}
                onChange={(v) => handleFieldChange('kellyFraction', v)}
                step="0.01"
              />
              <NumberInput
                label="Max Kelly Multiplier"
                value={globalConfig.maxKellyMultiplier}
                onChange={(v) => handleFieldChange('maxKellyMultiplier', v)}
                step="0.01"
              />
            </div>
          </ConfigSection>

          {/* Position Sizing Constitution (Phase 1) */}
          <ConfigSection title="Position Sizing Constitution" icon={Shield}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <NumberInput
                label="Max Symbol Log Loss"
                value={globalConfig.maxSymbolLogLoss}
                onChange={(v) => handleFieldChange('maxSymbolLogLoss', v)}
                step="0.0001"
                helpText="Maximum log-loss allowed per symbol (e.g., -0.10 = 10% max loss)"
              />
              <NumberInput
                label="Min Re-entry Spacing (ATR Multiple)"
                value={globalConfig.minReentrySpacingAtrMultiplier}
                onChange={(v) => handleFieldChange('minReentrySpacingAtrMultiplier', v)}
                step="0.1"
                helpText="Minimum ATR spacing for averaging down (e.g., 2.0 = 2× ATR)"
              />
            </div>
          </ConfigSection>

          {/* Velocity Throttling (Phase 2) */}
          <ConfigSection title="Velocity Throttling" icon={TrendingUp}>
            <div className="space-y-4">
              <div className="text-sm text-gray-400 mb-2">
                Constitutional deployment throttle based on Range/ATR regime and portfolio stress
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <NumberInput
                  label="Range/ATR Threshold: WIDE (≥)"
                  value={globalConfig.rangeAtrThresholdWide}
                  onChange={(v) => handleFieldChange('rangeAtrThresholdWide', v)}
                  step="0.1"
                />
                <NumberInput
                  label="Range/ATR Threshold: HEALTHY"
                  value={globalConfig.rangeAtrThresholdHealthy}
                  onChange={(v) => handleFieldChange('rangeAtrThresholdHealthy', v)}
                  step="0.1"
                />
                <NumberInput
                  label="Range/ATR Threshold: TIGHT"
                  value={globalConfig.rangeAtrThresholdTight}
                  onChange={(v) => handleFieldChange('rangeAtrThresholdTight', v)}
                  step="0.1"
                />
                <div className="text-xs text-gray-500 flex items-center">
                  COMPRESSED: &lt; {globalConfig.rangeAtrThresholdTight}
                </div>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <NumberInput
                  label="Velocity Multiplier: WIDE"
                  value={globalConfig.velocityMultiplierWide}
                  onChange={(v) => handleFieldChange('velocityMultiplierWide', v)}
                  step="0.01"
                />
                <NumberInput
                  label="Velocity Multiplier: HEALTHY"
                  value={globalConfig.velocityMultiplierHealthy}
                  onChange={(v) => handleFieldChange('velocityMultiplierHealthy', v)}
                  step="0.01"
                />
                <NumberInput
                  label="Velocity Multiplier: TIGHT"
                  value={globalConfig.velocityMultiplierTight}
                  onChange={(v) => handleFieldChange('velocityMultiplierTight', v)}
                  step="0.01"
                />
                <NumberInput
                  label="Velocity Multiplier: COMPRESSED"
                  value={globalConfig.velocityMultiplierCompressed}
                  onChange={(v) => handleFieldChange('velocityMultiplierCompressed', v)}
                  step="0.01"
                />
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <NumberInput
                  label="Body Ratio Threshold: LOW"
                  value={globalConfig.bodyRatioThresholdLow}
                  onChange={(v) => handleFieldChange('bodyRatioThresholdLow', v)}
                  step="0.01"
                />
                <NumberInput
                  label="Body Ratio Threshold: CRITICAL"
                  value={globalConfig.bodyRatioThresholdCritical}
                  onChange={(v) => handleFieldChange('bodyRatioThresholdCritical', v)}
                  step="0.01"
                />
                <NumberInput
                  label="Body Ratio Penalty: LOW"
                  value={globalConfig.bodyRatioPenaltyLow}
                  onChange={(v) => handleFieldChange('bodyRatioPenaltyLow', v)}
                  step="0.01"
                />
                <NumberInput
                  label="Body Ratio Penalty: CRITICAL"
                  value={globalConfig.bodyRatioPenaltyCritical}
                  onChange={(v) => handleFieldChange('bodyRatioPenaltyCritical', v)}
                  step="0.01"
                />
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <NumberInput
                  label="Range Lookback Bars"
                  value={globalConfig.rangeLookbackBars}
                  onChange={(v) => handleFieldChange('rangeLookbackBars', v)}
                  step="1"
                  helpText="Number of bars for Range/ATR calculation"
                />
                <CheckboxInput
                  label="Enable Stress Throttle"
                  checked={globalConfig.stressThrottleEnabled}
                  onChange={(v) => handleFieldChange('stressThrottleEnabled', v)}
                  helpText="Apply g(stress) based on portfolio drawdown"
                />
                <NumberInput
                  label="Max Stress Drawdown"
                  value={globalConfig.maxStressDrawdown}
                  onChange={(v) => handleFieldChange('maxStressDrawdown', v)}
                  step="0.01"
                  helpText="Portfolio drawdown threshold for stress throttle"
                />
              </div>
            </div>
          </ConfigSection>

          {/* Utility Asymmetry (Phase 3) */}
          <ConfigSection title="Utility Asymmetry (3× Advantage Gate)" icon={Target}>
            <div className="space-y-4">
              <div className="text-sm text-gray-400 mb-2">
                Constitutional gate: p · U(π) ≥ ratio · (1-p) · |U(ℓ)|
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <NumberInput
                  label="Upside Concavity (α)"
                  value={globalConfig.utilityAlpha}
                  onChange={(v) => handleFieldChange('utilityAlpha', v)}
                  step="0.01"
                  helpText="U(π) = π^α for gains, α ∈ [0.40, 0.80]"
                />
                <NumberInput
                  label="Downside Convexity (β)"
                  value={globalConfig.utilityBeta}
                  onChange={(v) => handleFieldChange('utilityBeta', v)}
                  step="0.01"
                  helpText="U(ℓ) = -λ·(-ℓ)^β for losses, β ∈ [1.10, 2.00]"
                />
                <NumberInput
                  label="Loss Aversion (λ)"
                  value={globalConfig.utilityLambda}
                  onChange={(v) => handleFieldChange('utilityLambda', v)}
                  step="0.01"
                  helpText="Loss aversion multiplier, λ ∈ [1.00, 3.00]"
                />
                <NumberInput
                  label="Min Advantage Ratio"
                  value={globalConfig.minAdvantageRatio}
                  onChange={(v) => handleFieldChange('minAdvantageRatio', v)}
                  step="0.1"
                  helpText="Required advantage ratio (default: 3.0)"
                />
                <CheckboxInput
                  label="Enable Utility Gate"
                  checked={globalConfig.utilityGateEnabled}
                  onChange={(v) => handleFieldChange('utilityGateEnabled', v)}
                  helpText="Reject trades failing 3× advantage gate"
                />
              </div>
            </div>
          </ConfigSection>

          {/* Entry & Exit */}
          <ConfigSection title="Entry & Exit Targets">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <CheckboxInput
                label="Use Limit Orders"
                checked={globalConfig.useLimitOrders}
                onChange={(v) => handleFieldChange('useLimitOrders', v)}
              />
              <NumberInput
                label="Entry Offset %"
                value={globalConfig.entryOffsetPct}
                onChange={(v) => handleFieldChange('entryOffsetPct', v)}
                step="0.0001"
              />
              <NumberInput
                label="Min Profit %"
                value={globalConfig.minProfitPct}
                onChange={(v) => handleFieldChange('minProfitPct', v)}
                step="0.0001"
              />
              <NumberInput
                label="Target R-Multiple"
                value={globalConfig.targetRMultiple}
                onChange={(v) => handleFieldChange('targetRMultiple', v)}
                step="0.1"
              />
              <NumberInput
                label="Stretch R-Multiple"
                value={globalConfig.stretchRMultiple}
                onChange={(v) => handleFieldChange('stretchRMultiple', v)}
                step="0.1"
              />
              <CheckboxInput
                label="Use Trailing Stop"
                checked={globalConfig.useTrailingStop}
                onChange={(v) => handleFieldChange('useTrailingStop', v)}
                helpText="Enable dynamic stop loss that follows favorable price movement"
              />
              <NumberInput
                label="Trailing Stop Activation %"
                value={globalConfig.trailingStopActivationPct}
                onChange={(v) => handleFieldChange('trailingStopActivationPct', v)}
                step="0.01"
                min="0"
                helpText="Favorable move % required to activate trailing stop (e.g., 2.0 = 2%)"
              />
              <NumberInput
                label="Trailing Stop Distance %"
                value={globalConfig.trailingStopDistancePct}
                onChange={(v) => handleFieldChange('trailingStopDistancePct', v)}
                step="0.01"
                min="0"
                helpText="Distance from highest price to stop loss (e.g., 1.0 = 1%)"
              />
            </div>
          </ConfigSection>

          {/* Save Button */}
          <div className="flex justify-end">
            <button
              onClick={handleSaveGlobal}
              disabled={saving}
              className="flex items-center space-x-2 px-6 py-3 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-600 text-white rounded-lg transition-colors font-medium"
            >
              {saving ? (
                <>
                  <RefreshCw className="w-5 h-5 animate-spin" />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <Save className="w-5 h-5" />
                  <span>Save Configuration</span>
                </>
              )}
            </button>
          </div>
        </div>
      )}

      {activeTab === 'symbols' && (
        <MtfSymbolConfigTable token={token} globalConfig={globalConfig} />
      )}
    </div>
  );
};

// Helper Components
const ConfigSection = ({ title, icon: Icon, children }) => (
  <div className="space-y-4">
    <div className="flex items-center space-x-2 pb-2 border-b border-gray-700">
      {Icon && <Icon className="w-5 h-5 text-purple-400" />}
      <h3 className="text-lg font-semibold text-white">{title}</h3>
    </div>
    {children}
  </div>
);

const TimeframeConfig = ({ label, prefix, config, onChange }) => (
  <div className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-3">
    <h4 className="font-medium text-white text-sm">{label}</h4>
    <NumberInput
      label="Candle Count"
      value={config[`${prefix}CandleCount`]}
      onChange={(v) => onChange(`${prefix}CandleCount`, v)}
      min="1"
    />
    <NumberInput
      label="Candle Minutes"
      value={config[`${prefix}CandleMinutes`]}
      onChange={(v) => onChange(`${prefix}CandleMinutes`, v)}
      min="1"
    />
    <NumberInput
      label="Weight"
      value={config[`${prefix}Weight`]}
      onChange={(v) => onChange(`${prefix}Weight`, v)}
      step="0.01"
      min="0"
      max="1"
    />
  </div>
);

const NumberInput = ({ label, value, onChange, step = "1", min, max, helpText }) => (
  <div>
    <label className="block text-sm font-medium text-gray-400 mb-1">{label}</label>
    <input
      type="number"
      value={value}
      onChange={(e) => onChange(parseFloat(e.target.value))}
      step={step}
      min={min}
      max={max}
      className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
    />
    {helpText && (
      <p className="mt-1 text-xs text-gray-500">{helpText}</p>
    )}
  </div>
);

const TextInput = ({ label, value, onChange }) => (
  <div>
    <label className="block text-sm font-medium text-gray-400 mb-1">{label}</label>
    <input
      type="text"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
    />
  </div>
);

const CheckboxInput = ({ label, checked, onChange, helpText }) => (
  <div>
    <div className="flex items-center space-x-3">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="w-4 h-4 text-purple-600 bg-gray-900 border-gray-700 rounded focus:ring-purple-500"
      />
      <label className="text-sm font-medium text-gray-400">{label}</label>
    </div>
    {helpText && (
      <p className="mt-1 ml-7 text-xs text-gray-500">{helpText}</p>
    )}
  </div>
);

export default MtfConfigManagement;
