import React from 'react';
import { Alert, AlertProps } from '../atoms/Alert/Alert';
import './AlertBanner.css';

/**
 * AlertBanner component props (extends Alert props)
 */
export type AlertBannerProps = AlertProps;

/**
 * AlertBanner Component
 *
 * A full-width alert banner for top-of-page or section notifications.
 * This extends the Alert atom with banner-specific styling.
 *
 * @example
 * ```tsx
 * <AlertBanner
 *   variant="warning"
 *   title="Maintenance Scheduled"
 *   dismissible
 *   onDismiss={handleDismiss}
 * >
 *   System will be under maintenance on Sunday, 2 AM - 4 AM IST.
 * </AlertBanner>
 * ```
 */
export const AlertBanner: React.FC<AlertBannerProps> = (props) => {
  return <Alert {...props} className={`alert-banner ${props.className || ''}`} />;
};

export default AlertBanner;
