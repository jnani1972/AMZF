import React, { ReactNode } from 'react';
import { AlertCircle, CheckCircle, Info, XCircle, X } from 'lucide-react';
import './Alert.css';

/**
 * Alert variant types
 */
export type AlertVariant = 'success' | 'error' | 'warning' | 'info';

/**
 * Alert component props
 */
export interface AlertProps {
  /**
   * Alert content
   */
  children: ReactNode;

  /**
   * Alert variant
   * @default 'info'
   */
  variant?: AlertVariant;

  /**
   * Alert title
   */
  title?: string;

  /**
   * Whether the alert can be dismissed
   * @default false
   */
  dismissible?: boolean;

  /**
   * Callback when alert is dismissed
   */
  onDismiss?: () => void;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Whether to show an icon
   * @default true
   */
  showIcon?: boolean;
}

const iconMap: Record<AlertVariant, typeof AlertCircle> = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertCircle,
  info: Info,
};

/**
 * Alert Component
 *
 * A notification message with different severity levels.
 * Supports dismissible alerts with callbacks.
 *
 * @example
 * ```tsx
 * <Alert variant="success" title="Order Placed">
 *   Your buy order for 100 shares has been placed successfully.
 * </Alert>
 *
 * <Alert variant="error" dismissible onDismiss={handleClose}>
 *   Failed to connect to the broker. Please try again.
 * </Alert>
 * ```
 */
export const Alert: React.FC<AlertProps> = ({
  children,
  variant = 'info',
  title,
  dismissible = false,
  onDismiss,
  className = '',
  showIcon = true,
}) => {
  const Icon = iconMap[variant];

  const classNames = ['alert', `alert--${variant}`, className].filter(Boolean).join(' ');

  const handleDismiss = () => {
    if (onDismiss) {
      onDismiss();
    }
  };

  return (
    <div className={classNames} role="alert">
      {showIcon && (
        <div className="alert__icon">
          <Icon size={20} />
        </div>
      )}

      <div className="alert__content">
        {title && <div className="alert__title">{title}</div>}
        <div className="alert__message">{children}</div>
      </div>

      {dismissible && (
        <button
          className="alert__dismiss"
          onClick={handleDismiss}
          aria-label="Dismiss alert"
          type="button"
        >
          <X size={16} />
        </button>
      )}
    </div>
  );
};

export default Alert;
