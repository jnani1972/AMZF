import React, { useState } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { Button, ButtonProps } from '../atoms/Button/Button';
import './OrderButton.css';

/**
 * Order direction
 */
export type OrderDirection = 'buy' | 'sell';

/**
 * OrderButton component props
 */
export interface OrderButtonProps extends Omit<ButtonProps, 'variant' | 'iconLeft'> {
  /**
   * Order direction
   */
  direction: OrderDirection;

  /**
   * Whether to require confirmation
   * @default true
   */
  requireConfirmation?: boolean;

  /**
   * Confirmation message
   */
  confirmationMessage?: string;

  /**
   * Callback when order is confirmed
   */
  onConfirm?: () => void | Promise<void>;

  /**
   * Whether the order is being placed
   * @default false
   */
  placing?: boolean;
}

/**
 * OrderButton Component
 *
 * A specialized button for placing trading orders (buy/sell).
 * Includes optional confirmation step before executing the order.
 *
 * @example
 * ```tsx
 * <OrderButton
 *   direction="buy"
 *   onConfirm={handleBuyOrder}
 *   requireConfirmation
 *   confirmationMessage="Buy 100 shares at ₹1,234.50?"
 * >
 *   Place Buy Order
 * </OrderButton>
 * ```
 */
export const OrderButton: React.FC<OrderButtonProps> = ({
  direction,
  requireConfirmation = true,
  confirmationMessage,
  onConfirm,
  placing = false,
  children,
  onClick,
  disabled,
  ...buttonProps
}) => {
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);

  const variant = direction === 'buy' ? 'buy' : 'sell';
  const Icon = direction === 'buy' ? TrendingUp : TrendingDown;

  const handleClick = async (e: React.MouseEvent<HTMLButtonElement>) => {
    if (requireConfirmation && !showConfirmation) {
      setShowConfirmation(true);
      return;
    }

    setIsProcessing(true);

    try {
      if (onConfirm) {
        await onConfirm();
      }
      if (onClick) {
        onClick(e);
      }
    } finally {
      setIsProcessing(false);
      setShowConfirmation(false);
    }
  };

  const handleCancel = () => {
    setShowConfirmation(false);
  };

  const isLoading = placing || isProcessing;
  const isDisabled = disabled || isLoading;

  if (showConfirmation && confirmationMessage) {
    return (
      <div className="order-button-group">
        <div className="order-button-confirmation">{confirmationMessage}</div>
        <div className="order-button-actions">
          <Button
            variant={variant}
            onClick={handleClick}
            loading={isLoading}
            disabled={isDisabled}
            iconLeft={<Icon size={16} />}
            size={buttonProps.size}
          >
            Confirm
          </Button>
          <Button
            variant="ghost"
            onClick={handleCancel}
            disabled={isLoading}
            size={buttonProps.size}
          >
            Cancel
          </Button>
        </div>
      </div>
    );
  }

  return (
    <Button
      variant={variant}
      onClick={handleClick}
      loading={isLoading}
      disabled={isDisabled}
      iconLeft={<Icon size={16} />}
      {...buttonProps}
    >
      {children}
    </Button>
  );
};

export default OrderButton;
