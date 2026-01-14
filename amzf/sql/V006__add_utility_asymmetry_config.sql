-- V006: Add Utility Asymmetry Constitutional Parameters
-- Adds 3× advantage gate with piecewise utility function in log-return space

ALTER TABLE mtf_global_config
-- Utility Asymmetry Gate Parameters
ADD COLUMN utility_alpha NUMERIC(4,2) DEFAULT 0.60,
ADD COLUMN utility_beta NUMERIC(4,2) DEFAULT 1.40,
ADD COLUMN utility_lambda NUMERIC(4,2) DEFAULT 1.00,
ADD COLUMN min_advantage_ratio NUMERIC(4,2) DEFAULT 3.00,

-- Utility Gate Control
ADD COLUMN utility_gate_enabled BOOLEAN DEFAULT TRUE;

-- Update existing DEFAULT config with explicit values
UPDATE mtf_global_config
SET utility_alpha = 0.60,
    utility_beta = 1.40,
    utility_lambda = 1.00,
    min_advantage_ratio = 3.00,
    utility_gate_enabled = TRUE
WHERE config_id = 'DEFAULT';

-- Add constraint comments for clarity
COMMENT ON COLUMN mtf_global_config.utility_alpha IS 'Upside utility concavity: α ∈ [0.40, 0.80], default 0.60. Lower = more concave (faster saturation).';
COMMENT ON COLUMN mtf_global_config.utility_beta IS 'Downside utility convexity: β ∈ [1.10, 2.00], default 1.40. Higher = more convex (faster acceleration).';
COMMENT ON COLUMN mtf_global_config.utility_lambda IS 'Loss aversion multiplier: λ ∈ [1.00, 3.00], default 1.00. Keep at 1.0 if 3× is in gate formula.';
COMMENT ON COLUMN mtf_global_config.min_advantage_ratio IS 'Minimum advantage ratio required: p·U(π) ≥ ratio·(1-p)·|U(ℓ)|. Constitutional default: 3.0.';
COMMENT ON COLUMN mtf_global_config.utility_gate_enabled IS 'Enable utility asymmetry gate. Cannot be disabled in production without constitutional override.';
