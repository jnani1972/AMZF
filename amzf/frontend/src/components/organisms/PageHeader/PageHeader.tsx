/**
 * PageHeader Component
 * Reusable page header with title, description, and actions
 */

import { ReactNode } from 'react';
import { Text } from '../../atoms/Text/Text';

export interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

/**
 * PageHeader - Standard page header layout
 */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex items-center justify-between">
      <div>
        <Text variant="h2" className="mb-2">
          {title}
        </Text>
        {description && (
          <Text variant="body" className="text-muted">
            {description}
          </Text>
        )}
      </div>
      {actions && <div className="flex gap-3">{actions}</div>}
    </div>
  );
}
