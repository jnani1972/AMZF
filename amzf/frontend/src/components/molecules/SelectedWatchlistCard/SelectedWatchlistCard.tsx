import React from 'react';
import { Eye, Edit2, Trash2, Layers } from 'lucide-react';
import { Card } from '../../atoms/Card/Card';
import { Text } from '../../atoms/Text/Text';
import { Badge } from '../../atoms/Badge/Badge';
import { Button } from '../../atoms/Button/Button';
import type { WatchlistSelected } from '../../../types/domain';
import './SelectedWatchlistCard.css';

/**
 * SelectedWatchlistCard component props
 */
export interface SelectedWatchlistCardProps {
  /**
   * Selected watchlist data
   */
  watchlist: WatchlistSelected;

  /**
   * Callback when edit button is clicked
   */
  onEdit: () => void;

  /**
   * Callback when delete button is clicked
   */
  onDelete: () => void;

  /**
   * Callback when view symbols button is clicked
   */
  onViewSymbols: () => void;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * SelectedWatchlistCard Component
 *
 * Displays a selected watchlist with name, description, symbol count, and actions.
 * Used in the Selected Watchlists management page.
 *
 * @example
 * ```tsx
 * <SelectedWatchlistCard
 *   watchlist={watchlistData}
 *   onEdit={() => handleEdit(watchlist.selectedId)}
 *   onDelete={() => handleDelete(watchlist.selectedId)}
 *   onViewSymbols={() => handleView(watchlist.selectedId)}
 * />
 * ```
 */
export const SelectedWatchlistCard: React.FC<SelectedWatchlistCardProps> = ({
  watchlist,
  onEdit,
  onDelete,
  onViewSymbols,
  className = '',
}) => {
  const classNames = ['selected-watchlist-card', className].filter(Boolean).join(' ');

  return (
    <Card className={classNames} variant="default">
      <div className="selected-watchlist-card__content">
        {/* Header */}
        <div className="selected-watchlist-card__header">
          <div className="selected-watchlist-card__icon">
            <Layers size={24} className="text-primary" />
          </div>
          <div className="selected-watchlist-card__title-group">
            <Text variant="h4" weight="semibold">
              {watchlist.name}
            </Text>
            <Badge variant={watchlist.enabled ? 'success' : 'default'} size="sm">
              {watchlist.enabled ? 'Active' : 'Disabled'}
            </Badge>
          </div>
        </div>

        {/* Description */}
        {watchlist.description && (
          <div className="selected-watchlist-card__description">
            <Text variant="small" color="secondary">
              {watchlist.description}
            </Text>
          </div>
        )}

        {/* Metadata */}
        <div className="selected-watchlist-card__meta">
          <div className="selected-watchlist-card__meta-item">
            <Text variant="caption" color="secondary">
              Template
            </Text>
            <Text variant="small" weight="medium">
              {watchlist.sourceTemplateId}
            </Text>
          </div>
          <div className="selected-watchlist-card__meta-item">
            <Text variant="caption" color="secondary">
              Symbols
            </Text>
            <Text variant="small" weight="medium">
              {watchlist.symbolCount || 0}
            </Text>
          </div>
        </div>

        {/* Actions */}
        <div className="selected-watchlist-card__actions">
          <Button
            variant="secondary"
            size="sm"
            iconLeft={<Eye size={16} />}
            onClick={onViewSymbols}
            fullWidth
          >
            View Symbols
          </Button>
          <div className="selected-watchlist-card__action-buttons">
            <Button
              variant="ghost"
              size="sm"
              iconLeft={<Edit2 size={16} />}
              onClick={onEdit}
              title="Edit Symbols"
            >
              <></>
            </Button>
            <Button
              variant="ghost"
              size="sm"
              iconLeft={<Trash2 size={16} />}
              onClick={onDelete}
              className="text-loss"
              title="Delete"
            >
              <></>
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
};

export default SelectedWatchlistCard;
