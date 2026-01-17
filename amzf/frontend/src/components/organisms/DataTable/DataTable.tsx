/**
 * DataTable Component
 * Reusable table component with consistent styling and sorting
 */

import { ReactNode, useState, useMemo } from 'react';
import { ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react';
import { Card } from '../../atoms/Card/Card';
import { EmptyState } from '../../molecules/EmptyState/EmptyState';

export type SortDirection = 'asc' | 'desc' | null;

export interface DataTableColumn<T> {
  key: string;
  header: string;
  align?: 'left' | 'center' | 'right';
  render: (item: T) => ReactNode;
  sortable?: boolean;
  sortValue?: (item: T) => string | number | Date;
}

export interface DataTableProps<T> {
  columns: DataTableColumn<T>[];
  data: T[];
  keyExtractor: (item: T) => string;
  defaultSortKey?: string;
  defaultSortDirection?: SortDirection;
  emptyState?: {
    icon: ReactNode;
    title: string;
    description: string;
    ctaText?: string;
    onCtaClick?: () => void;
  };
}

/**
 * DataTable - Reusable sortable table component
 * Default sort: Latest on top (desc by first sortable column or specified default)
 */
export function DataTable<T>({
  columns,
  data,
  keyExtractor,
  defaultSortKey,
  defaultSortDirection = 'desc',
  emptyState,
}: DataTableProps<T>) {
  const [sortKey, setSortKey] = useState<string | null>(defaultSortKey || null);
  const [sortDirection, setSortDirection] = useState<SortDirection>(defaultSortDirection);

  // Sort data
  const sortedData = useMemo(() => {
    if (!sortKey || !sortDirection) return data;

    const column = columns.find((col) => col.key === sortKey);
    if (!column || !column.sortValue) return data;

    return [...data].sort((a, b) => {
      const aVal = column.sortValue!(a);
      const bVal = column.sortValue!(b);

      let comparison = 0;
      if (aVal < bVal) comparison = -1;
      if (aVal > bVal) comparison = 1;

      return sortDirection === 'asc' ? comparison : -comparison;
    });
  }, [data, sortKey, sortDirection, columns]);

  const handleSort = (columnKey: string) => {
    const column = columns.find((col) => col.key === columnKey);
    if (!column || !column.sortable) return;

    if (sortKey === columnKey) {
      // Toggle direction or clear sort
      if (sortDirection === 'asc') {
        setSortDirection('desc');
      } else if (sortDirection === 'desc') {
        setSortDirection(null);
        setSortKey(null);
      }
    } else {
      // New column, start with ascending
      setSortKey(columnKey);
      setSortDirection('asc');
    }
  };

  const getSortIcon = (columnKey: string) => {
    if (sortKey !== columnKey) {
      return <ArrowUpDown size={14} className="sort-icon" />;
    }
    if (sortDirection === 'asc') {
      return <ArrowUp size={14} className="sort-icon sort-icon--active" />;
    }
    if (sortDirection === 'desc') {
      return <ArrowDown size={14} className="sort-icon sort-icon--active" />;
    }
    return <ArrowUpDown size={14} className="sort-icon" />;
  };

  return (
    <Card>
      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={column.key}
                  className={`${column.align === 'right' ? 'text-right' : ''} ${
                    column.sortable ? 'sortable-header' : ''
                  }`}
                  onClick={() => column.sortable && handleSort(column.key)}
                >
                  <div className="table-header-content">
                    <span>{column.header}</span>
                    {column.sortable && getSortIcon(column.key)}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedData.length > 0 ? (
              sortedData.map((item) => (
                <tr key={keyExtractor(item)}>
                  {columns.map((column) => (
                    <td
                      key={column.key}
                      className={column.align === 'right' ? 'text-right' : ''}
                    >
                      {column.render(item)}
                    </td>
                  ))}
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={columns.length} className="table-empty">
                  {emptyState && (
                    <EmptyState
                      icon={emptyState.icon}
                      title={emptyState.title}
                      description={emptyState.description}
                      ctaText={emptyState.ctaText}
                      onCtaClick={emptyState.onCtaClick}
                    />
                  )}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
