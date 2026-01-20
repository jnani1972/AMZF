/**
 * WatchlistHierarchyDiagram Component
 * Visual representation of the 4-level watchlist hierarchy
 * Level 0: Templates → Level 1: Template Symbols → Level 2: Selected → Level 3: User Watchlists
 */

import { useState } from 'react';
import { Text } from '../../atoms/Text/Text';
import { Card } from '../../atoms/Card/Card';
import { Badge } from '../../atoms/Badge/Badge';
import { Button } from '../../atoms/Button/Button';
import { ChevronRight, ChevronDown, Layers, FileText, FolderOpen, Users } from 'lucide-react';

export interface HierarchyLevel0 {
  templateId: string;
  templateName: string;
  description?: string;
  enabled: boolean;
  symbolCount?: number;
}

export interface HierarchyLevel1 {
  id: string;
  symbol: string;
  lotSize?: number;
}

export interface HierarchyLevel2 {
  selectedId: string;
  name: string;
  sourceTemplateId: string;
  symbolCount?: number;
  enabled: boolean;
}

export interface HierarchyLevel3 {
  userId: string;
  userEmail: string;
  symbolCount: number;
}

export interface WatchlistHierarchyData {
  templates: HierarchyLevel0[];
  templateSymbols: Record<string, HierarchyLevel1[]>; // templateId -> symbols
  selectedWatchlists: HierarchyLevel2[];
  userStats?: HierarchyLevel3[]; // Optional: for admin view
}

export interface WatchlistHierarchyDiagramProps {
  data: WatchlistHierarchyData;
  mode: 'admin' | 'user';
  userId?: string; // For user mode, to highlight their data
  onTemplateClick?: (templateId: string) => void;
  onSelectedClick?: (selectedId: string) => void;
}

/**
 * WatchlistHierarchyDiagram - Shows the complete 4-level watchlist hierarchy
 */
export function WatchlistHierarchyDiagram({
  data,
  mode,
  userId,
  onTemplateClick,
  onSelectedClick,
}: WatchlistHierarchyDiagramProps) {
  const [expandedTemplates, setExpandedTemplates] = useState<Set<string>>(new Set());
  const [expandedSelected, setExpandedSelected] = useState<Set<string>>(new Set());

  const toggleTemplate = (templateId: string) => {
    const newExpanded = new Set(expandedTemplates);
    if (newExpanded.has(templateId)) {
      newExpanded.delete(templateId);
    } else {
      newExpanded.add(templateId);
    }
    setExpandedTemplates(newExpanded);
  };

  const toggleSelected = (selectedId: string) => {
    const newExpanded = new Set(expandedSelected);
    if (newExpanded.has(selectedId)) {
      newExpanded.delete(selectedId);
    } else {
      newExpanded.add(selectedId);
    }
    setExpandedSelected(newExpanded);
  };

  return (
    <div className="space-y-6">
      {/* Hierarchy Legend */}
      <Card>
        <div className="p-4 bg-surface-secondary">
          <Text variant="h4" className="mb-3">
            Watchlist Hierarchy Overview
          </Text>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-sm">
            <div className="flex items-center gap-2">
              <Layers size={20} className="text-primary" />
              <div>
                <Text variant="label">Level 0: Templates</Text>
                <Text variant="small" className="text-muted">
                  Base symbol groups
                </Text>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <FileText size={20} className="text-success" />
              <div>
                <Text variant="label">Level 1: Symbols</Text>
                <Text variant="small" className="text-muted">
                  Symbols in each template
                </Text>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <FolderOpen size={20} className="text-warning" />
              <div>
                <Text variant="label">Level 2: Selected</Text>
                <Text variant="small" className="text-muted">
                  Curated watchlists
                </Text>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Users size={20} className="text-info" />
              <div>
                <Text variant="label">Level 3: Users</Text>
                <Text variant="small" className="text-muted">
                  Individual watchlists
                </Text>
              </div>
            </div>
          </div>
        </div>
      </Card>

      {/* Hierarchy Tree */}
      <div className="space-y-4">
        {data.templates.map((template) => {
          const isExpanded = expandedTemplates.has(template.templateId);
          const templateSymbols = data.templateSymbols[template.templateId] || [];
          const relatedSelected = data.selectedWatchlists.filter(
            (s) => s.sourceTemplateId === template.templateId
          );

          return (
            <Card key={template.templateId} className="overflow-hidden">
              {/* Level 0: Template */}
              <div
                className="p-4 bg-primary/5 flex items-center justify-between cursor-pointer hover:bg-primary/10 transition-colors"
                onClick={() => toggleTemplate(template.templateId)}
              >
                <div className="flex items-center gap-3 flex-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    iconLeft={isExpanded ? <ChevronDown size={20} /> : <ChevronRight size={20} />}
                    onClick={(e) => {
                      e.stopPropagation();
                      toggleTemplate(template.templateId);
                    }}
                  />
                  <Layers size={24} className="text-primary" />
                  <div className="flex-1">
                    <div className="flex items-center gap-3">
                      <Text variant="h4">{template.templateName}</Text>
                      <Badge variant={template.enabled ? 'success' : 'default'} size="sm">
                        {template.enabled ? 'Active' : 'Disabled'}
                      </Badge>
                    </div>
                    <Text variant="small" className="text-muted">
                      {template.description || 'No description'}
                    </Text>
                  </div>
                </div>
                <div className="flex items-center gap-6">
                  <div className="text-right">
                    <Text variant="small" className="text-muted">
                      Symbols
                    </Text>
                    <Text variant="h4">{templateSymbols.length}</Text>
                  </div>
                  <div className="text-right">
                    <Text variant="small" className="text-muted">
                      Selected Lists
                    </Text>
                    <Text variant="h4">{relatedSelected.length}</Text>
                  </div>
                  {mode === 'admin' && onTemplateClick && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        onTemplateClick(template.templateId);
                      }}
                    >
                      Manage
                    </Button>
                  )}
                </div>
              </div>

              {/* Level 1 & 2: Symbols and Selected Watchlists */}
              {isExpanded && (
                <div className="p-4 space-y-4 border-t border-border">
                  {/* Level 1: Template Symbols */}
                  <div className="pl-12">
                    <div className="flex items-center gap-2 mb-3">
                      <FileText size={20} className="text-success" />
                      <Text variant="label">Template Symbols ({templateSymbols.length})</Text>
                    </div>
                    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-2">
                      {templateSymbols.map((symbol) => (
                        <div
                          key={symbol.id}
                          className="px-3 py-2 bg-success/10 rounded-lg text-center"
                        >
                          <Text variant="small" weight="medium">
                            {symbol.symbol}
                          </Text>
                        </div>
                      ))}
                      {templateSymbols.length === 0 && (
                        <div className="col-span-full text-center py-4 text-muted">
                          No symbols in this template
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Level 2: Selected Watchlists */}
                  <div className="pl-12">
                    <div className="flex items-center gap-2 mb-3">
                      <FolderOpen size={20} className="text-warning" />
                      <Text variant="label">
                        Selected Watchlists ({relatedSelected.length})
                      </Text>
                    </div>
                    <div className="space-y-2">
                      {relatedSelected.map((selected) => {
                        const isSelectedExpanded = expandedSelected.has(selected.selectedId);
                        return (
                          <div
                            key={selected.selectedId}
                            className="border border-warning/30 rounded-lg overflow-hidden"
                          >
                            <div
                              className="p-3 bg-warning/10 flex items-center justify-between cursor-pointer hover:bg-warning/20 transition-colors"
                              onClick={() => toggleSelected(selected.selectedId)}
                            >
                              <div className="flex items-center gap-2 flex-1">
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  iconLeft={
                                    isSelectedExpanded ? (
                                      <ChevronDown size={16} />
                                    ) : (
                                      <ChevronRight size={16} />
                                    )
                                  }
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    toggleSelected(selected.selectedId);
                                  }}
                                />
                                <Text variant="body" weight="medium">
                                  {selected.name}
                                </Text>
                                <Badge variant={selected.enabled ? 'success' : 'default'} size="sm">
                                  {selected.enabled ? 'Active' : 'Disabled'}
                                </Badge>
                              </div>
                              <div className="flex items-center gap-4">
                                <Text variant="small" className="text-muted">
                                  {selected.symbolCount || 0} symbols
                                </Text>
                                {mode === 'admin' && onSelectedClick && (
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      onSelectedClick(selected.selectedId);
                                    }}
                                  >
                                    Manage
                                  </Button>
                                )}
                              </div>
                            </div>

                            {/* Level 3: User Stats */}
                            {isSelectedExpanded && mode === 'admin' && data.userStats && (
                              <div className="p-3 bg-info/10 border-t border-warning/30">
                                <div className="flex items-center gap-2 mb-2">
                                  <Users size={16} className="text-info" />
                                  <Text variant="small" weight="medium">
                                    User Distribution ({data.userStats.length} users)
                                  </Text>
                                </div>
                                <div className="grid grid-cols-2 md:grid-cols-3 gap-2 text-xs">
                                  {data.userStats.map((userStat) => (
                                    <div
                                      key={userStat.userId}
                                      className="px-2 py-1 bg-white/50 rounded flex justify-between"
                                    >
                                      <span className="truncate">{userStat.userEmail}</span>
                                      <span className="text-muted ml-2">
                                        {userStat.symbolCount}
                                      </span>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        );
                      })}
                      {relatedSelected.length === 0 && (
                        <div className="text-center py-4 text-muted">
                          No selected watchlists from this template
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </Card>
          );
        })}

        {data.templates.length === 0 && (
          <Card>
            <div className="p-12 text-center">
              <Layers size={48} className="mx-auto mb-4 text-muted" />
              <Text variant="h4" className="mb-2">
                No Templates Found
              </Text>
              <Text variant="body" className="text-muted">
                {mode === 'admin'
                  ? 'Create a template to start organizing your watchlists'
                  : 'No watchlist templates available'}
              </Text>
            </div>
          </Card>
        )}
      </div>
    </div>
  );
}

export default WatchlistHierarchyDiagram;
