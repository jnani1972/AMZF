/**
 * SlideModal Component
 * Reusable slide-in modal that appears from the top-right
 */

import { ReactNode } from 'react';
import { Card } from '../../atoms/Card/Card';
import { Text } from '../../atoms/Text/Text';
import { Button } from '../../atoms/Button/Button';

export interface SlideModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
  width?: 'sm' | 'md' | 'lg' | 'xl';
  borderColor?: string;
}

/**
 * SlideModal - Notification-style modal that slides in from top-right
 */
export function SlideModal({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  width = 'md',
  borderColor = 'border-blue-500',
}: SlideModalProps) {
  if (!isOpen) return null;

  const widthClass = width === 'md' ? 'modal-slide-right--md' :
                     width === 'lg' ? 'modal-slide-right--lg' :
                     width === 'xl' ? 'modal-slide-right--xl' :
                     'modal-slide-right--md';

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/20 z-40 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Modal */}
      <div className={`modal-slide-right ${widthClass} animate-slide-in-right`}>
        <Card className={`shadow-2xl border-2 ${borderColor}`}>
          <div className="p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div>
                <Text variant="h3">{title}</Text>
                {subtitle && (
                  <Text variant="body" className="text-muted mt-1">
                    {subtitle}
                  </Text>
                )}
              </div>
              <Button variant="ghost" size="sm" onClick={onClose}>
                Close
              </Button>
            </div>

            {/* Content */}
            {children}
          </div>
        </Card>
      </div>
    </>
  );
}
