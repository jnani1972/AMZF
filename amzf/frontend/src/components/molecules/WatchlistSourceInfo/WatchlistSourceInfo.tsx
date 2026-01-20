/**
 * WatchlistSourceInfo Component
 * Shows users the hierarchical source of their watchlist symbols
 * Read-only view for transparency
 */

import { useState } from 'react';
import { Text } from '../../atoms/Text/Text';
import { Card } from '../../atoms/Card/Card';
import { Badge } from '../../atoms/Badge/Badge';
import { Button } from '../../atoms/Button/Button';
import { Info, ChevronDown, ChevronUp, Layers, FolderOpen, Eye } from 'lucide-react';

export interface WatchlistSource {
  templateName: string;
  selectedWatchlistName?: string;
  symbolCount: number;
  description?: string;
}

export interface WatchlistSourceInfoProps {
  sources: WatchlistSource[];
  totalSymbols: number;
  className?: string;
}

/**
 * WatchlistSourceInfo - Shows users where their watchlist symbols come from
 */
export function WatchlistSourceInfo({
  sources,
  totalSymbols,
  className = '',
}: WatchlistSourceInfoProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <Card className={className}>
      <div className="p-4 space-y-4">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div className="flex items-start gap-3">
            <Info size={24} className="text-info mt-1" />
            <div>
              <Text variant="h4" className="mb-1">
                Your Watchlist Sources
              </Text>
              <Text variant="small" className="text-muted">
                Your watchlist contains {totalSymbols} symbols from{' '}
                {sources.length === 1 ? '1 source' : `${sources.length} sources`}
              </Text>
            </div>
          </div>
          <Button
            variant="ghost"
            size="sm"
            iconLeft={isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            onClick={() => setIsExpanded(!isExpanded)}
          >
            {isExpanded ? 'Hide' : 'Show'} Details
          </Button>
        </div>

        {/* Expanded Details */}
        {isExpanded && (
          <div className="space-y-3 pt-3 border-t border-border">
            <div className="grid grid-cols-1 gap-3">
              {sources.map((source, index) => (
                <div
                  key={index}
                  className="p-3 bg-surface-secondary rounded-lg border border-border"
                >
                  {/* Template Level */}
                  <div className="flex items-center gap-2 mb-2">
                    <Layers size={16} className="text-primary" />
                    <Text variant="label" weight="medium">
                      {source.templateName}
                    </Text>
                    <Badge variant="default" size="sm">
                      Template
                    </Badge>
                  </div>

                  {/* Selected Watchlist Level */}
                  {source.selectedWatchlistName && (
                    <div className="flex items-center gap-2 ml-6 mb-2">
                      <FolderOpen size={14} className="text-warning" />
                      <Text variant="small" className="text-muted">
                        via {source.selectedWatchlistName}
                      </Text>
                    </div>
                  )}

                  {/* Symbol Count */}
                  <div className="flex items-center gap-2 ml-6">
                    <Eye size={14} className="text-success" />
                    <Text variant="small" className="text-muted">
                      {source.symbolCount} symbol{source.symbolCount !== 1 ? 's' : ''} in your
                      watchlist
                    </Text>
                  </div>

                  {/* Description */}
                  {source.description && (
                    <div className="mt-2 ml-6">
                      <Text variant="small" className="text-muted">
                        {source.description}
                      </Text>
                    </div>
                  )}
                </div>
              ))}
            </div>

            {/* Hierarchy Explanation */}
            <div className="p-3 bg-info/10 rounded-lg border border-info/30">
              <Text variant="small" className="text-muted">
                <strong>How it works:</strong> Your watchlist is automatically synced from curated
                templates managed by admins. Templates are organized into Selected Watchlists,
                which are then distributed to all users.
              </Text>
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

export default WatchlistSourceInfo;
