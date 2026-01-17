/**
 * SummaryCards Component
 * Reusable 4-column summary card grid
 */

import { ReactNode } from 'react';
import { Card } from '../../atoms/Card/Card';
import { Text } from '../../atoms/Text/Text';

export interface SummaryCardData {
  icon: ReactNode;
  iconBgColor: string;
  iconColor: string;
  label: string;
  value: string | number;
  subtitle?: string;
  valueColor?: string;
  onClick?: () => void; // Optional click handler for navigation
}

export interface SummaryCardsProps {
  cards: SummaryCardData[];
  columns?: 2 | 3 | 4; // Number of columns (default: 4)
}

/**
 * SummaryCards displays a grid of summary metric cards
 * Renders in a horizontal layout with 2, 3, or 4 columns
 * Cards can be clickable if onClick handler is provided
 */
export function SummaryCards({ cards, columns = 4 }: SummaryCardsProps) {
  const gridClass = columns === 2 ? 'grid-2' : columns === 3 ? 'grid-3' : 'summary-grid';

  return (
    <div className={gridClass}>
      {cards.map((card, index) => (
        <Card
          key={index}
          className={card.onClick ? 'summary-card--clickable' : ''}
          onClick={card.onClick}
        >
          <div className="summary-card-content">
            <div className={`summary-card-icon ${card.iconBgColor} rounded`}>
              <div className={card.iconColor}>
                {card.icon}
              </div>
            </div>
            <div className="flex-1 min-w-0">
              <Text variant="small" className="text-muted text-xs">
                {card.label}
              </Text>
              <div className="flex items-baseline gap-1">
                <Text variant="h3" className={`font-bold ${card.valueColor || ''}`}>
                  {card.value}
                </Text>
                {card.subtitle && (
                  <Text variant="small" className="text-xs font-medium">
                    {card.subtitle}
                  </Text>
                )}
              </div>
            </div>
          </div>
        </Card>
      ))}
    </div>
  );
}
