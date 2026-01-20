/**
 * Modal Component
 * Reusable centered modal that appears with backdrop
 */

import { ReactNode, MouseEvent } from 'react';
import { Card } from '../../atoms/Card/Card';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  children: ReactNode;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
  className?: string;
}

/**
 * Modal - Centered modal with backdrop
 *
 * @example
 * ```tsx
 * <Modal isOpen={showModal} onClose={() => setShowModal(false)} maxWidth="lg">
 *   <div className="p-6">
 *     <Text variant="h3">Modal Title</Text>
 *     ...
 *   </div>
 * </Modal>
 * ```
 */
export function Modal({
  isOpen,
  onClose,
  children,
  maxWidth = 'md',
  className = '',
}: ModalProps) {
  if (!isOpen) return null;

  const handleBackdropClick = () => {
    onClose();
  };

  const handleContentClick = (e: MouseEvent) => {
    e.stopPropagation();
  };

  const maxWidthClass = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-xl',
    '2xl': 'max-w-2xl',
  }[maxWidth];

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50"
      onClick={handleBackdropClick}
    >
      <div
        className={`modal-form w-full ${maxWidthClass} ${className}`}
        onClick={handleContentClick}
      >
        <Card>{children}</Card>
      </div>
    </div>
  );
}

export default Modal;
