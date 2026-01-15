import React, { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Card } from '@/components/atoms/Card';
import { Text } from '@/components/atoms/Text';
import { Button } from '@/components/atoms/Button';
import { SearchBar, SearchOption } from '@/components/molecules/SearchBar';
import { PriceCard } from '@/components/molecules/PriceCard';
import { EmptyState } from '@/components/molecules/EmptyState';
import './Watchlist.css';

/**
 * Watchlist item
 */
export interface WatchlistItem {
  symbol: string;
  description?: string;
  price: number;
  change?: number;
  changePercent?: number;
}

/**
 * Watchlist component props
 */
export interface WatchlistProps {
  /**
   * Watchlist title
   */
  title?: string;

  /**
   * Watchlist items
   */
  items: WatchlistItem[];

  /**
   * Available symbols for search
   */
  availableSymbols?: SearchOption[];

  /**
   * Handler when symbol is added
   */
  onAddSymbol?: (symbol: SearchOption) => void;

  /**
   * Handler when symbol is removed
   */
  onRemoveSymbol?: (symbol: string) => void;

  /**
   * Handler when symbol is clicked
   */
  onSymbolClick?: (symbol: string) => void;

  /**
   * Whether search is loading
   * @default false
   */
  searchLoading?: boolean;

  /**
   * Additional CSS class
   */
  className?: string;
}

/**
 * Watchlist Component
 *
 * Displays a list of watched symbols with live prices and change indicators.
 * Includes search functionality to add new symbols.
 *
 * @example
 * ```tsx
 * <Watchlist
 *   title="My Watchlist"
 *   items={[
 *     {
 *       symbol: 'RELIANCE',
 *       description: 'Reliance Industries',
 *       price: 2456.75,
 *       change: 23.50,
 *       changePercent: 0.97
 *     }
 *   ]}
 *   availableSymbols={allSymbols}
 *   onAddSymbol={handleAdd}
 *   onRemoveSymbol={handleRemove}
 * />
 * ```
 */
export const Watchlist: React.FC<WatchlistProps> = ({
  title = 'Watchlist',
  items,
  availableSymbols = [],
  onAddSymbol,
  onRemoveSymbol,
  onSymbolClick,
  searchLoading = false,
  className = '',
}) => {
  const [showSearch, setShowSearch] = useState(false);
  const [hoveredSymbol, setHoveredSymbol] = useState<string | null>(null);

  const handleAddClick = () => {
    setShowSearch(true);
  };

  const handleSymbolSelect = (option: SearchOption) => {
    if (onAddSymbol) {
      onAddSymbol(option);
    }
    setShowSearch(false);
  };

  const handleRemove = (symbol: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (onRemoveSymbol) {
      onRemoveSymbol(symbol);
    }
  };

  const classNames = ['watchlist', className].filter(Boolean).join(' ');

  return (
    <Card className={classNames} padding="none">
      {/* Header */}
      <div className="watchlist__header">
        <Text variant="h4" weight="semibold">
          {title}
        </Text>
        <Button
          variant="ghost"
          size="sm"
          iconLeft={<Plus size={16} />}
          onClick={handleAddClick}
        >
          Add
        </Button>
      </div>

      {/* Search Bar */}
      {showSearch && (
        <div className="watchlist__search">
          <SearchBar
            placeholder="Search symbols..."
            options={availableSymbols}
            onSelect={handleSymbolSelect}
            loading={searchLoading}
            fullWidth
          />
        </div>
      )}

      {/* Items */}
      <div className="watchlist__items">
        {items.length === 0 ? (
          <EmptyState
            icon={<Plus size={32} />}
            title="No symbols added"
            description="Add symbols to track their prices"
            size="sm"
          />
        ) : (
          items.map((item) => (
            <div
              key={item.symbol}
              className="watchlist__item"
              onMouseEnter={() => setHoveredSymbol(item.symbol)}
              onMouseLeave={() => setHoveredSymbol(null)}
            >
              <div
                className="watchlist__item-content"
                onClick={() => onSymbolClick && onSymbolClick(item.symbol)}
              >
                <PriceCard
                  symbol={item.symbol}
                  description={item.description}
                  price={item.price}
                  change={item.change}
                  changePercent={item.changePercent}
                  compact
                  interactive={!!onSymbolClick}
                />
              </div>

              {onRemoveSymbol && hoveredSymbol === item.symbol && (
                <button
                  className="watchlist__remove"
                  onClick={(e) => handleRemove(item.symbol, e)}
                  aria-label={`Remove ${item.symbol}`}
                >
                  <Trash2 size={16} />
                </button>
              )}
            </div>
          ))
        )}
      </div>
    </Card>
  );
};

export default Watchlist;
