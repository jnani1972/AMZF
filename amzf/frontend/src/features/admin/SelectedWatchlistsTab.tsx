/**
 * Selected Watchlists Tab
 * Manages admin-curated selected watchlists (Level 2 in watchlist hierarchy)
 */

import { useState } from 'react';
import { useSelectedWatchlists, useSelectedWatchlistSymbols, useWatchlistTemplates, useTemplateSymbols } from '../../hooks/useApi';
import { apiClient } from '../../lib/api';
import { Text } from '../../components/atoms/Text/Text';
import { Button } from '../../components/atoms/Button/Button';
import { Input } from '../../components/atoms/Input/Input';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import { SelectedWatchlistCard } from '../../components/molecules/SelectedWatchlistCard/SelectedWatchlistCard';
import { PageHeader } from '../../components/organisms/PageHeader/PageHeader';
import { Modal } from '../../components/organisms/Modal/Modal';
import { PlusCircle, X, Layers } from 'lucide-react';

/**
 * SelectedWatchlistsTab Component
 */
export function SelectedWatchlistsTab() {
  const { data: selectedWatchlists, loading, error, refetch } = useSelectedWatchlists();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showViewModal, setShowViewModal] = useState(false);
  const [selectedWatchlistId, setSelectedWatchlistId] = useState<string | null>(null);

  const handleEdit = (selectedId: string) => {
    setSelectedWatchlistId(selectedId);
    setShowEditModal(true);
  };

  const handleDelete = async (selectedId: string) => {
    if (!confirm('Are you sure you want to delete this selected watchlist? This will trigger a resync for all users.')) {
      return;
    }

    const response = await apiClient.deleteSelectedWatchlist(selectedId);
    if (response.success) {
      refetch();
    } else {
      alert(response.error || 'Failed to delete selected watchlist');
    }
  };

  const handleViewSymbols = (selectedId: string) => {
    setSelectedWatchlistId(selectedId);
    setShowViewModal(true);
  };

  if (loading && !selectedWatchlists) {
    return <Spinner size="lg" />;
  }

  return (
    <>
      <div className="space-y-6">
        <PageHeader
          title="Selected Watchlists"
          description="Admin-curated symbol lists created from templates (Level 2)"
          actions={
            <Button
              variant="primary"
              iconLeft={<PlusCircle size={20} />}
              onClick={() => setShowCreateModal(true)}
            >
              Create Selected Watchlist
            </Button>
          }
        />

        {error && <Alert variant="error">{error}</Alert>}

        {selectedWatchlists && selectedWatchlists.length === 0 && (
          <EmptyState
            title="No Selected Watchlists"
            description="Create your first selected watchlist from a template"
            icon={<Layers size={48} />}
            ctaText="Create Selected Watchlist"
            onCtaClick={() => setShowCreateModal(true)}
          />
        )}

        {selectedWatchlists && selectedWatchlists.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {selectedWatchlists.map((watchlist: any) => (
              <SelectedWatchlistCard
                key={watchlist.selectedId}
                watchlist={watchlist}
                onEdit={() => handleEdit(watchlist.selectedId)}
                onDelete={() => handleDelete(watchlist.selectedId)}
                onViewSymbols={() => handleViewSymbols(watchlist.selectedId)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <CreateSelectedWatchlistModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            refetch();
          }}
        />
      )}

      {/* Edit Modal */}
      {showEditModal && selectedWatchlistId && (
        <EditSelectedWatchlistModal
          selectedId={selectedWatchlistId}
          onClose={() => {
            setShowEditModal(false);
            setSelectedWatchlistId(null);
          }}
          onSuccess={() => {
            setShowEditModal(false);
            setSelectedWatchlistId(null);
            refetch();
          }}
        />
      )}

      {/* View Symbols Modal */}
      {showViewModal && selectedWatchlistId && (
        <ViewSymbolsModal
          selectedId={selectedWatchlistId}
          onClose={() => {
            setShowViewModal(false);
            setSelectedWatchlistId(null);
          }}
        />
      )}
    </>
  );
}

/**
 * Create Selected Watchlist Modal
 */
function CreateSelectedWatchlistModal({
  onClose,
  onSuccess,
}: {
  onClose: () => void;
  onSuccess: () => void;
}) {
  const { data: templates } = useWatchlistTemplates();
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: templateSymbols } = useTemplateSymbols(selectedTemplateId || null);

  const handleSubmit = async () => {
    if (!selectedTemplateId) {
      setError('Please select a template');
      return;
    }

    if (selectedSymbols.length === 0) {
      setError('Please select at least one symbol');
      return;
    }

    setSubmitting(true);
    setError(null);

    const response = await apiClient.createSelectedWatchlist({
      sourceTemplateId: selectedTemplateId,
      symbols: selectedSymbols,
    });

    setSubmitting(false);

    if (response.success) {
      onSuccess();
    } else {
      setError(response.error || 'Failed to create selected watchlist');
    }
  };

  const toggleSymbol = (symbol: string) => {
    setSelectedSymbols((prev) =>
      prev.includes(symbol) ? prev.filter((s) => s !== symbol) : [...prev, symbol]
    );
  };

  const filteredSymbols = templateSymbols?.filter((s: any) =>
    s.symbol.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <Modal isOpen={true} onClose={onClose} maxWidth="2xl">
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <Text variant="h3">Create Selected Watchlist</Text>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X size={20} />
          </Button>
        </div>

            {error && <Alert variant="error" onDismiss={() => setError(null)}>{error}</Alert>}

            {/* Step 1: Select Template */}
            <div>
              <Text variant="label" className="mb-2">
                Source Template
              </Text>
              <select
                className="input input--md w-full"
                value={selectedTemplateId}
                onChange={(e) => {
                  setSelectedTemplateId(e.target.value);
                  setSelectedSymbols([]);
                }}
              >
                <option value="">Select a template...</option>
                {templates?.map((template: any) => (
                  <option key={template.templateId} value={template.templateId}>
                    {template.templateName}
                  </option>
                ))}
              </select>
            </div>

            {/* Step 2: Select Symbols */}
            {selectedTemplateId && templateSymbols && (
              <>
                <div>
                  <div className="flex justify-between items-center mb-2">
                    <Text variant="label">Select Symbols ({selectedSymbols.length} selected)</Text>
                    <div className="flex gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setSelectedSymbols(templateSymbols.map((s: any) => s.symbol))}
                      >
                        Select All
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setSelectedSymbols([])}>
                        Deselect All
                      </Button>
                    </div>
                  </div>

                  <Input
                    placeholder="Search symbols..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    fullWidth
                  />
                </div>

                <div className="border rounded-lg max-h-[400px] overflow-y-auto">
                  {filteredSymbols && filteredSymbols.length > 0 ? (
                    <div className="divide-y">
                      {filteredSymbols.map((symbol: any) => (
                        <label
                          key={symbol.id}
                          className="flex items-center gap-3 p-3 hover:bg-muted/50 cursor-pointer"
                        >
                          <input
                            type="checkbox"
                            checked={selectedSymbols.includes(symbol.symbol)}
                            onChange={() => toggleSymbol(symbol.symbol)}
                            className="h-4 w-4"
                          />
                          <span className="font-mono font-medium flex-1">{symbol.symbol}</span>
                          <span className="text-sm text-muted">Lot: {symbol.lotSize || '-'}</span>
                        </label>
                      ))}
                    </div>
                  ) : (
                    <div className="p-8 text-center text-muted">
                      {searchQuery ? 'No symbols match your search' : 'No symbols in template'}
                    </div>
                  )}
                </div>
              </>
            )}

        <div className="form-actions">
          <Button variant="secondary" onClick={onClose} fullWidth>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={handleSubmit}
            disabled={submitting || !selectedTemplateId || selectedSymbols.length === 0}
            fullWidth
          >
            {submitting ? 'Creating...' : 'Create'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

/**
 * Edit Selected Watchlist Modal
 */
function EditSelectedWatchlistModal({
  selectedId,
  onClose,
  onSuccess,
}: {
  selectedId: string;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const { data: currentSymbols } = useSelectedWatchlistSymbols(selectedId);
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Initialize selected symbols when data loads
  useState(() => {
    if (currentSymbols) {
      setSelectedSymbols(currentSymbols.map((s: any) => s.symbol));
    }
  });

  const handleSubmit = async () => {
    if (selectedSymbols.length === 0) {
      setError('Please select at least one symbol');
      return;
    }

    setSubmitting(true);
    setError(null);

    const response = await apiClient.updateSelectedWatchlistSymbols(selectedId, selectedSymbols);

    setSubmitting(false);

    if (response.success) {
      onSuccess();
    } else {
      setError(response.error || 'Failed to update selected watchlist');
    }
  };

  const toggleSymbol = (symbol: string) => {
    setSelectedSymbols((prev) =>
      prev.includes(symbol) ? prev.filter((s) => s !== symbol) : [...prev, symbol]
    );
  };

  const filteredSymbols = currentSymbols?.filter((s: any) =>
    s.symbol.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <Modal isOpen={true} onClose={onClose} maxWidth="2xl">
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <Text variant="h3">Edit Selected Watchlist</Text>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X size={20} />
          </Button>
        </div>

        {error && <Alert variant="error" onDismiss={() => setError(null)}>{error}</Alert>}

        <div>
          <div className="flex justify-between items-center mb-2">
            <Text variant="label">Edit Symbols ({selectedSymbols.length} selected)</Text>
          </div>

          <Input
            placeholder="Search symbols..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            fullWidth
          />
        </div>

        <div className="border rounded-lg max-h-[400px] overflow-y-auto">
          {filteredSymbols && filteredSymbols.length > 0 ? (
            <div className="divide-y">
              {filteredSymbols.map((symbol: any) => (
                <label
                  key={symbol.id || symbol.symbol}
                  className="flex items-center gap-3 p-3 hover:bg-muted/50 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={selectedSymbols.includes(symbol.symbol)}
                    onChange={() => toggleSymbol(symbol.symbol)}
                    className="h-4 w-4"
                  />
                  <span className="font-mono font-medium flex-1">{symbol.symbol}</span>
                </label>
              ))}
            </div>
          ) : (
            <div className="p-8 text-center text-muted">
              {searchQuery ? 'No symbols match your search' : 'No symbols available'}
            </div>
          )}
        </div>

        <div className="form-actions">
          <Button variant="secondary" onClick={onClose} fullWidth>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={handleSubmit}
            disabled={submitting || selectedSymbols.length === 0}
            fullWidth
          >
            {submitting ? 'Updating...' : 'Update'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

/**
 * View Symbols Modal
 */
function ViewSymbolsModal({
  selectedId,
  onClose,
}: {
  selectedId: string;
  onClose: () => void;
}) {
  const { data: symbols } = useSelectedWatchlistSymbols(selectedId);
  const [searchQuery, setSearchQuery] = useState('');

  const filteredSymbols = symbols?.filter((s: any) =>
    s.symbol.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <Modal isOpen={true} onClose={onClose} maxWidth="2xl">
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <Text variant="h3">View Symbols</Text>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X size={20} />
          </Button>
        </div>

        <Input
          placeholder="Search symbols..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          fullWidth
        />

        <div className="border rounded-lg max-h-[500px] overflow-y-auto">
          {filteredSymbols && filteredSymbols.length > 0 ? (
            <div className="divide-y">
              {filteredSymbols.map((symbol: any) => (
                <div
                  key={symbol.id || symbol.symbol}
                  className="p-3 flex justify-between items-center"
                >
                  <span className="font-mono font-medium">{symbol.symbol}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="p-8 text-center text-muted">
              {searchQuery ? 'No symbols match your search' : 'No symbols in this watchlist'}
            </div>
          )}
        </div>

        <div className="form-actions">
          <Button variant="secondary" onClick={onClose} fullWidth>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
}
