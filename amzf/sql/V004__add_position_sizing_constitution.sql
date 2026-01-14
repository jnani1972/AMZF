-- V004: Add Position Sizing Constitutional Parameters
-- Adds portfolio/symbol risk budgets and configurable averaging gate parameters

ALTER TABLE mtf_global_config
ADD COLUMN max_symbol_log_loss NUMERIC(6,4) DEFAULT -0.1000,
ADD COLUMN min_reentry_spacing_atr_multiplier NUMERIC(3,2) DEFAULT 2.00;

-- Update existing DEFAULT config with explicit values
UPDATE mtf_global_config
SET max_symbol_log_loss = -0.1000,
    min_reentry_spacing_atr_multiplier = 2.00
WHERE config_id = 'DEFAULT';
