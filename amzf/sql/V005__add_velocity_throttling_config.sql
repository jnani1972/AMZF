-- V005: Add Velocity Throttling Constitutional Parameters
-- Adds structural velocity control based on Range/ATR regime and stress throttling

ALTER TABLE mtf_global_config
-- Range/ATR Velocity Regime Thresholds
ADD COLUMN range_atr_threshold_wide NUMERIC(5,2) DEFAULT 8.00,
ADD COLUMN range_atr_threshold_healthy NUMERIC(5,2) DEFAULT 5.00,
ADD COLUMN range_atr_threshold_tight NUMERIC(5,2) DEFAULT 3.00,

-- Velocity Base Multipliers (Discrete Regime Buckets)
ADD COLUMN velocity_multiplier_wide NUMERIC(4,2) DEFAULT 1.00,
ADD COLUMN velocity_multiplier_healthy NUMERIC(4,2) DEFAULT 0.75,
ADD COLUMN velocity_multiplier_tight NUMERIC(4,2) DEFAULT 0.50,
ADD COLUMN velocity_multiplier_compressed NUMERIC(4,2) DEFAULT 0.25,

-- Body Ratio Brake (Optional Penalty Only, Never Amplify)
ADD COLUMN body_ratio_threshold_low NUMERIC(4,2) DEFAULT 0.25,
ADD COLUMN body_ratio_threshold_critical NUMERIC(4,2) DEFAULT 0.15,
ADD COLUMN body_ratio_penalty_low NUMERIC(4,2) DEFAULT 0.70,
ADD COLUMN body_ratio_penalty_critical NUMERIC(4,2) DEFAULT 0.50,

-- Range Calculation Parameters
ADD COLUMN range_lookback_bars INTEGER DEFAULT 50,

-- Stress Throttling
ADD COLUMN stress_throttle_enabled BOOLEAN DEFAULT TRUE,
ADD COLUMN max_stress_drawdown NUMERIC(6,4) DEFAULT -0.0500;

-- Update existing DEFAULT config with explicit values
UPDATE mtf_global_config
SET range_atr_threshold_wide = 8.00,
    range_atr_threshold_healthy = 5.00,
    range_atr_threshold_tight = 3.00,
    velocity_multiplier_wide = 1.00,
    velocity_multiplier_healthy = 0.75,
    velocity_multiplier_tight = 0.50,
    velocity_multiplier_compressed = 0.25,
    body_ratio_threshold_low = 0.25,
    body_ratio_threshold_critical = 0.15,
    body_ratio_penalty_low = 0.70,
    body_ratio_penalty_critical = 0.50,
    range_lookback_bars = 50,
    stress_throttle_enabled = TRUE,
    max_stress_drawdown = -0.0500
WHERE config_id = 'DEFAULT';
