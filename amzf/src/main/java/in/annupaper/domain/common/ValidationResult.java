package in.annupaper.domain.common;

import in.annupaper.domain.common.ValidationErrorCode;
import in.annupaper.domain.trade.TradeIntent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of trade intent validation.
 */
public record ValidationResult(
    boolean passed,
    List<TradeIntent.ValidationError> errors,
    
    // Calculated values (if passed)
    Integer calculatedQty,
    BigDecimal calculatedValue,
    String orderType,
    BigDecimal limitPrice,
    String productType,
    
    // Risk metrics
    BigDecimal logImpact,
    BigDecimal portfolioExposureAfter
) {
    /**
     * Create a passed result.
     */
    public static ValidationResult pass(int qty, BigDecimal value, String orderType, 
                                         BigDecimal limitPrice, String productType,
                                         BigDecimal logImpact, BigDecimal exposureAfter) {
        return new ValidationResult(true, List.of(), qty, value, orderType, limitPrice, 
                                    productType, logImpact, exposureAfter);
    }
    
    /**
     * Create a failed result.
     */
    public static ValidationResult fail(List<TradeIntent.ValidationError> errors) {
        return new ValidationResult(false, errors, null, null, null, null, null, null, null);
    }
    
    /**
     * Create a failed result with a single error.
     */
    public static ValidationResult fail(ValidationErrorCode code) {
        return fail(List.of(TradeIntent.ValidationError.of(code)));
    }
    
    /**
     * Builder for accumulating errors.
     */
    public static class Builder {
        private final List<TradeIntent.ValidationError> errors = new ArrayList<>();
        private Integer qty;
        private BigDecimal value;
        private String orderType;
        private BigDecimal limitPrice;
        private String productType;
        private BigDecimal logImpact;
        private BigDecimal exposureAfter;
        
        public Builder addError(ValidationErrorCode code) {
            errors.add(TradeIntent.ValidationError.of(code));
            return this;
        }
        
        public Builder addError(ValidationErrorCode code, String field, Object expected, Object actual) {
            errors.add(TradeIntent.ValidationError.of(code, field, expected, actual));
            return this;
        }
        
        public Builder qty(int qty) { this.qty = qty; return this; }
        public Builder value(BigDecimal value) { this.value = value; return this; }
        public Builder orderType(String orderType) { this.orderType = orderType; return this; }
        public Builder limitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; return this; }
        public Builder productType(String productType) { this.productType = productType; return this; }
        public Builder logImpact(BigDecimal logImpact) { this.logImpact = logImpact; return this; }
        public Builder exposureAfter(BigDecimal exposureAfter) { this.exposureAfter = exposureAfter; return this; }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public ValidationResult build() {
            if (errors.isEmpty()) {
                return ValidationResult.pass(qty, value, orderType, limitPrice, productType, logImpact, exposureAfter);
            } else {
                return ValidationResult.fail(errors);
            }
        }
    }
}
