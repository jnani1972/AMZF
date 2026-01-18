package in.annupaper.domain.model;

/**
 * Exit reasons for trade exits.
 */
public enum ExitReason {
    TARGET_HIT, // Price reached target
    STOP_LOSS, // Price hit stop loss
    TRAILING_STOP, // Trailing stop triggered
    TIME_BASED, // Max hold time exceeded
    MANUAL, // Manual user exit
    RISK_BREACH // Risk limit breach (portfolio/daily loss)
}
