import React, { useState } from 'react';
import { Card } from '../atoms/Card/Card';
import { Text } from '../atoms/Text/Text';
import { FormField } from '../molecules/FormField/FormField';
import { OrderButton } from '../molecules/OrderButton/OrderButton';
import './OrderPanel.css';

/**
 * Order type
 */
export type OrderType = 'market' | 'limit' | 'stop';

/**
 * Order form data
 */
export interface OrderFormData {
  symbol: string;
  quantity: number;
  orderType: OrderType;
  limitPrice?: number;
  stopPrice?: number;
}

/**
 * OrderPanel component props
 */
export interface OrderPanelProps {
  /**
   * Panel title
   */
  title?: string;

  /**
   * Pre-filled symbol
   */
  symbol?: string;

  /**
   * Handler when buy order is submitted
   */
  onBuy?: (order: OrderFormData) => void | Promise<void>;

  /**
   * Handler when sell order is submitted
   */
  onSell?: (order: OrderFormData) => void | Promise<void>;

  /**
   * Whether order is being placed
   * @default false
   */
  placing?: boolean;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * OrderPanel Component
 *
 * Complete order entry form with buy/sell actions.
 * Supports market, limit, and stop orders.
 *
 * @example
 * ```tsx
 * <OrderPanel
 *   title="Place Order"
 *   symbol="RELIANCE"
 *   onBuy={handleBuyOrder}
 *   onSell={handleSellOrder}
 * />
 * ```
 */
export const OrderPanel: React.FC<OrderPanelProps> = ({
  title = 'Place Order',
  symbol: initialSymbol = '',
  onBuy,
  onSell,
  placing = false,
  className = '',
}) => {
  const [symbol, setSymbol] = useState(initialSymbol);
  const [quantity, setQuantity] = useState<number>(1);
  const [orderType, setOrderType] = useState<OrderType>('market');
  const [limitPrice, setLimitPrice] = useState<number>(0);
  const [stopPrice, setStopPrice] = useState<number>(0);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!symbol.trim()) {
      newErrors.symbol = 'Symbol is required';
    }

    if (quantity <= 0) {
      newErrors.quantity = 'Quantity must be positive';
    }

    if (orderType === 'limit' && limitPrice <= 0) {
      newErrors.limitPrice = 'Limit price must be positive';
    }

    if (orderType === 'stop' && stopPrice <= 0) {
      newErrors.stopPrice = 'Stop price must be positive';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const getOrderData = (): OrderFormData => {
    return {
      symbol,
      quantity,
      orderType,
      limitPrice: orderType === 'limit' ? limitPrice : undefined,
      stopPrice: orderType === 'stop' ? stopPrice : undefined,
    };
  };

  const handleBuy = async () => {
    if (!validate()) return;
    if (onBuy) {
      await onBuy(getOrderData());
    }
  };

  const handleSell = async () => {
    if (!validate()) return;
    if (onSell) {
      await onSell(getOrderData());
    }
  };

  const getConfirmationMessage = (direction: 'buy' | 'sell') => {
    const typeText = orderType === 'market' ? 'Market' : orderType === 'limit' ? 'Limit' : 'Stop';
    const priceText =
      orderType === 'market'
        ? ''
        : ` @ ₹${orderType === 'limit' ? limitPrice : stopPrice}`;
    return `${direction === 'buy' ? 'Buy' : 'Sell'} ${quantity} ${symbol} (${typeText}${priceText})?`;
  };

  const classNames = ['order-panel', className].filter(Boolean).join(' ');

  return (
    <Card className={classNames}>
      <Text variant="h4" weight="semibold" className="order-panel__title">
        {title}
      </Text>

      <div className="order-panel__form">
        <FormField
          label="Symbol"
          type="text"
          value={symbol}
          onChange={(e) => setSymbol(e.target.value.toUpperCase())}
          placeholder="e.g., RELIANCE"
          required
          error={errors.symbol}
        />

        <FormField
          label="Quantity"
          type="number"
          value={quantity}
          onChange={(e) => setQuantity(Number(e.target.value))}
          required
          error={errors.quantity}
        />

        <div className="order-panel__field">
          <label className="order-panel__label">Order Type</label>
          <div className="order-panel__radio-group">
            <label className="order-panel__radio">
              <input
                type="radio"
                value="market"
                checked={orderType === 'market'}
                onChange={(e) => setOrderType(e.target.value as OrderType)}
              />
              <span>Market</span>
            </label>
            <label className="order-panel__radio">
              <input
                type="radio"
                value="limit"
                checked={orderType === 'limit'}
                onChange={(e) => setOrderType(e.target.value as OrderType)}
              />
              <span>Limit</span>
            </label>
            <label className="order-panel__radio">
              <input
                type="radio"
                value="stop"
                checked={orderType === 'stop'}
                onChange={(e) => setOrderType(e.target.value as OrderType)}
              />
              <span>Stop</span>
            </label>
          </div>
        </div>

        {orderType === 'limit' && (
          <FormField
            label="Limit Price"
            type="number"
            value={limitPrice}
            onChange={(e) => setLimitPrice(Number(e.target.value))}
            placeholder="0.00"
            required
            error={errors.limitPrice}
          />
        )}

        {orderType === 'stop' && (
          <FormField
            label="Stop Price"
            type="number"
            value={stopPrice}
            onChange={(e) => setStopPrice(Number(e.target.value))}
            placeholder="0.00"
            required
            error={errors.stopPrice}
          />
        )}

        <div className="order-panel__actions">
          <OrderButton
            direction="buy"
            onConfirm={handleBuy}
            placing={placing}
            requireConfirmation
            confirmationMessage={getConfirmationMessage('buy')}
            fullWidth
          >
            Buy
          </OrderButton>

          <OrderButton
            direction="sell"
            onConfirm={handleSell}
            placing={placing}
            requireConfirmation
            confirmationMessage={getConfirmationMessage('sell')}
            fullWidth
          >
            Sell
          </OrderButton>
        </div>
      </div>
    </Card>
  );
};

export default OrderPanel;
