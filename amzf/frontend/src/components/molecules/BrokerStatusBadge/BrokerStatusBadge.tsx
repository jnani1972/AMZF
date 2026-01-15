import React from 'react';
import { Activity, AlertCircle, CheckCircle, XCircle } from 'lucide-react';
import { Badge } from '@/components/atoms/Badge';
import { Tooltip } from '@/components/atoms/Tooltip';
import './BrokerStatusBadge.css';

/**
 * Broker health status
 */
export type BrokerHealth = 'healthy' | 'degraded' | 'down' | 'unknown';

/**
 * BrokerStatusBadge component props
 */
export interface BrokerStatusBadgeProps {
  /**
   * Broker name
   */
  broker: string;

  /**
   * Health status
   */
  health: BrokerHealth;

  /**
   * Latency in milliseconds
   */
  latency?: number;

  /**
   * Last update timestamp
   */
  lastUpdate?: Date;

  /**
   * Whether to show detailed tooltip
   * @default true
   */
  showTooltip?: boolean;

  /**
   * Additional CSS class
   */
  className?: string;

  /**
   * Click handler
   */
  onClick?: () => void;
}

/**
 * BrokerStatusBadge Component
 *
 * Displays broker connection status with health indicator and latency.
 * Shows detailed information in a tooltip on hover.
 *
 * @example
 * ```tsx
 * <BrokerStatusBadge
 *   broker="FYERS"
 *   health="healthy"
 *   latency={45}
 *   lastUpdate={new Date()}
 * />
 * ```
 */
export const BrokerStatusBadge: React.FC<BrokerStatusBadgeProps> = ({
  broker,
  health,
  latency,
  lastUpdate,
  showTooltip = true,
  className = '',
  onClick,
}) => {
  const getHealthIcon = () => {
    switch (health) {
      case 'healthy':
        return <CheckCircle size={14} />;
      case 'degraded':
        return <AlertCircle size={14} />;
      case 'down':
        return <XCircle size={14} />;
      default:
        return <Activity size={14} />;
    }
  };

  const getHealthVariant = () => {
    switch (health) {
      case 'healthy':
        return 'success';
      case 'degraded':
        return 'warning';
      case 'down':
        return 'error';
      default:
        return 'default';
    }
  };

  const getHealthLabel = () => {
    switch (health) {
      case 'healthy':
        return 'Connected';
      case 'degraded':
        return 'Degraded';
      case 'down':
        return 'Disconnected';
      default:
        return 'Unknown';
    }
  };

  const getLatencyColor = (): string => {
    if (!latency) return '';
    if (latency < 100) return 'broker-status__latency--good';
    if (latency < 300) return 'broker-status__latency--fair';
    return 'broker-status__latency--poor';
  };

  const tooltipContent = (
    <div className="broker-status__tooltip">
      <div>
        <strong>{broker}</strong>
      </div>
      <div>Status: {getHealthLabel()}</div>
      {latency !== undefined && <div>Latency: {latency}ms</div>}
      {lastUpdate && <div>Updated: {lastUpdate.toLocaleTimeString()}</div>}
    </div>
  );

  const badgeContent = (
    <div className="broker-status" onClick={onClick}>
      <Badge variant={getHealthVariant()} size="md" dot>
        {getHealthIcon()}
        <span className="broker-status__name">{broker}</span>
        {latency !== undefined && (
          <span className={`broker-status__latency ${getLatencyColor()}`}>{latency}ms</span>
        )}
      </Badge>
    </div>
  );

  if (showTooltip) {
    return (
      <Tooltip content={tooltipContent} placement="bottom" className={className}>
        {badgeContent}
      </Tooltip>
    );
  }

  return <div className={className}>{badgeContent}</div>;
};

export default BrokerStatusBadge;
