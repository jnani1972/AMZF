import React, { useState, useEffect } from 'react';
import { Eye, Plus, RefreshCw, Trash2, CheckCircle, AlertCircle, Network, List, Layers, Globe, Power, Settings } from 'lucide-react';
import WatchlistTemplateManagement from './WatchlistTemplateManagement';

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
  || (import.meta?.env?.VITE_API_BASE_URL)
  || "http://localhost:9090";

const WatchlistManagement = ({ token }) => {
  const [activeTab, setActiveTab] = useState('templates'); // templates | selected | default | users

  // Templates state (Level 1)
  const [templates, setTemplates] = useState([]);
  const [selectedTemplate, setSelectedTemplate] = useState(null);
  const [templateSymbols, setTemplateSymbols] = useState([]);
  const [selectedSymbols, setSelectedSymbols] = useState([]);

  // Selected watchlists state (Level 2)
  const [selectedWatchlists, setSelectedWatchlists] = useState([]);
  const [viewingSelected, setViewingSelected] = useState(null);
  const [selectedWatchlistSymbols, setSelectedWatchlistSymbols] = useState([]);

  // Default watchlist state (Level 3)
  const [defaultWatchlist, setDefaultWatchlist] = useState([]);

  // User watchlists state (Level 4 - existing functionality)
  const [users, setUsers] = useState([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [watchlist, setWatchlist] = useState([]);
  const [userBrokers, setUserBrokers] = useState([]);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [formData, setFormData] = useState({
    userBrokerId: '',
    symbol: ''
  });
  const [submitStatus, setSubmitStatus] = useState(null);

  // Autocomplete state
  const [instrumentSearch, setInstrumentSearch] = useState('');
  const [instrumentResults, setInstrumentResults] = useState([]);
  const [showInstrumentDropdown, setShowInstrumentDropdown] = useState(false);

  // Search instruments for autocomplete
  const searchInstruments = async (query) => {
    if (!query || query.length < 2) {
      setInstrumentResults([]);
      setShowInstrumentDropdown(false);
      return;
    }

    try {
      const res = await fetch(`${API_BASE_URL}/api/instruments/search?q=${encodeURIComponent(query)}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to search instruments');
      const data = await res.json();
      setInstrumentResults(data);
      setShowInstrumentDropdown(true);
    } catch (err) {
      console.error('Instrument search error:', err);
      setInstrumentResults([]);
    }
  };

  // Debounce instrument search
  useEffect(() => {
    const timer = setTimeout(() => {
      if (instrumentSearch) {
        searchInstruments(instrumentSearch);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [instrumentSearch]);

  // Delete watchlist item
  const handleDeleteWatchlistItem = async (id) => {
    if (!confirm('Are you sure you want to delete this watchlist item?')) {
      return;
    }

    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      const data = await res.json();

      if (data.success) {
        fetchWatchlist(selectedUserId);
      } else {
        setError(data.error || 'Failed to delete watchlist item');
      }
    } catch (err) {
      setError(err.message);
    }
  };

  // Toggle watchlist item enabled status
  const handleToggleWatchlistItem = async (id, currentEnabled) => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist/${id}/toggle`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ enabled: !currentEnabled })
      });

      const data = await res.json();

      if (data.success) {
        fetchWatchlist(selectedUserId);
      } else {
        setError(data.error || 'Failed to toggle watchlist item');
      }
    } catch (err) {
      setError(err.message);
    }
  };

  // Fetch templates (Level 1)
  const fetchTemplates = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch templates');
      const data = await res.json();
      setTemplates(data);
    } catch (err) {
      setError(err.message);
    }
  };

  // Fetch template symbols
  const fetchTemplateSymbols = async (templateId) => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates/${templateId}/symbols`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch template symbols');
      const data = await res.json();
      setTemplateSymbols(data);
      setSelectedSymbols([]);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Create selected watchlist from template
  const createSelectedWatchlist = async () => {
    if (!selectedTemplate || selectedSymbols.length === 0) {
      setError('Please select at least one symbol');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-selected`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          sourceTemplateId: selectedTemplate.templateId,
          symbols: selectedSymbols
        })
      });

      const data = await res.json();

      if (!res.ok) {
        // Show actual backend error message
        const errorMsg = data.error || data.message || `Server error: ${res.status}`;
        throw new Error(errorMsg);
      }

      setSuccessMessage(`Selected watchlist created: ${data.selectedId}. Synced to all users.`);
      setSelectedSymbols([]);
      setSelectedTemplate(null);
      setTemplateSymbols([]);
      fetchSelectedWatchlists();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Fetch selected watchlists (Level 2)
  const fetchSelectedWatchlists = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-selected`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch selected watchlists');
      const data = await res.json();
      setSelectedWatchlists(data);
    } catch (err) {
      setError(err.message);
    }
  };

  // Fetch selected watchlist symbols
  const fetchSelectedWatchlistSymbols = async (selectedId) => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-selected/${selectedId}/symbols`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch selected watchlist symbols');
      const data = await res.json();
      setSelectedWatchlistSymbols(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Delete selected watchlist
  const deleteSelectedWatchlist = async (selectedId) => {
    if (!confirm('Delete this selected watchlist? This will update all user watchlists.')) return;

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-selected/${selectedId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to delete selected watchlist');

      setSuccessMessage('Selected watchlist deleted and users synced');
      fetchSelectedWatchlists();
      setViewingSelected(null);
      setTimeout(() => setSuccessMessage(null), 3000);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Fetch default watchlist (Level 3)
  const fetchDefaultWatchlist = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-default`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch default watchlist');
      const data = await res.json();
      setDefaultWatchlist(data.symbols || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Manual sync to all users
  const syncToAllUsers = async () => {
    if (!confirm('Sync default watchlist to all user-brokers? This will update all users.')) return;

    setLoading(true);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-sync`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to sync watchlists');

      setSuccessMessage('Watchlist synced to all user-brokers');
      setTimeout(() => setSuccessMessage(null), 3000);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // User watchlists functions (existing Level 4 functionality)
  const fetchUsers = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/users`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch users');
      const data = await res.json();
      setUsers(data.filter(u => u.role !== 'ADMIN'));
    } catch (err) {
      setError(err.message);
    }
  };

  const fetchUserBrokers = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/user-brokers`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch user-brokers');
      const data = await res.json();
      setUserBrokers(data);
    } catch (err) {
      setError(err.message);
    }
  };

  const fetchWatchlist = async (userId) => {
    if (!userId) {
      setWatchlist([]);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist?userId=${userId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch watchlist');
      const data = await res.json();
      setWatchlist(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.userBrokerId || !formData.symbol) {
      setSubmitStatus({ type: 'error', message: 'Please fill all fields' });
      return;
    }

    setSubmitStatus({ type: 'loading', message: 'Adding symbol to watchlist...' });

    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/watchlist`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          userBrokerId: formData.userBrokerId,
          symbol: formData.symbol.toUpperCase()
        })
      });

      const data = await res.json();

      if (data.success) {
        setSubmitStatus({ type: 'success', message: 'Symbol added to watchlist' });
        setFormData({ ...formData, symbol: '' });
        setInstrumentSearch('');
        setInstrumentResults([]);
        setShowInstrumentDropdown(false);
        fetchWatchlist(selectedUserId);
        setTimeout(() => setSubmitStatus(null), 3000);
      } else {
        setSubmitStatus({ type: 'error', message: data.error || 'Failed to add symbol' });
      }
    } catch (err) {
      setSubmitStatus({ type: 'error', message: err.message });
    }
  };

  // Load data based on active tab
  useEffect(() => {
    if (activeTab === 'templates') {
      fetchTemplates();
    } else if (activeTab === 'selected') {
      fetchSelectedWatchlists();
    } else if (activeTab === 'default') {
      fetchDefaultWatchlist();
    } else if (activeTab === 'users') {
      fetchUsers();
      fetchUserBrokers();
    }
  }, [activeTab, token]);

  useEffect(() => {
    if (selectedUserId) {
      fetchWatchlist(selectedUserId);
    }
  }, [selectedUserId]);

  // Filter user-brokers for the selected user
  const selectedUserBrokers = selectedUserId
    ? userBrokers.filter(ub => ub.userId === selectedUserId)
    : [];

  // FIX: Deduplicate watchlist by symbol (group by symbol, show comma-separated broker IDs)
  const deduplicatedWatchlist = React.useMemo(() => {
    const symbolMap = new Map();

    for (const item of watchlist) {
      if (!symbolMap.has(item.symbol)) {
        symbolMap.set(item.symbol, {
          symbol: item.symbol,
          lotSize: item.lotSize,
          tickSize: item.tickSize,
          isCustom: item.isCustom,
          enabled: item.enabled,
          items: []
        });
      }
      symbolMap.get(item.symbol).items.push(item);
    }

    return Array.from(symbolMap.values());
  }, [watchlist]);

  const tabs = [
    { id: 'templates', name: 'Templates (L1)', icon: List },
    { id: 'selected', name: 'Selected (L2)', icon: Layers },
    { id: 'default', name: 'Default (L3)', icon: Globe },
    { id: 'users', name: 'User Watchlists (L4)', icon: Eye },
    { id: 'template-mgmt', name: 'Template Management', icon: Settings }
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <Network className="w-6 h-6 text-purple-500" />
          <h2 className="text-xl font-bold text-white">Watchlist Management</h2>
        </div>
      </div>

      {/* Global Messages */}
      {error && (
        <div className="bg-red-900 border border-red-700 text-red-200 px-4 py-3 rounded-lg">
          <div className="flex items-center space-x-2">
            <AlertCircle className="w-5 h-5" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-red-300 text-sm underline mt-1">
            Dismiss
          </button>
        </div>
      )}

      {successMessage && (
        <div className="bg-green-900 border border-green-700 text-green-200 px-4 py-3 rounded-lg">
          <div className="flex items-center space-x-2">
            <CheckCircle className="w-5 h-5" />
            <span>{successMessage}</span>
          </div>
        </div>
      )}

      {/* Tab Navigation */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
        <div className="flex border-b border-gray-700">
          {tabs.map(tab => {
            const Icon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setActiveTab(tab.id);
                  setError(null);
                  setSuccessMessage(null);
                }}
                className={`flex-1 flex items-center justify-center space-x-2 px-4 py-3 transition-colors ${
                  activeTab === tab.id
                    ? 'bg-purple-600 text-white border-b-2 border-purple-400'
                    : 'bg-gray-800 text-gray-400 hover:bg-gray-750'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span className="text-sm font-medium">{tab.name}</span>
              </button>
            );
          })}
        </div>

        {/* Tab Content */}
        <div className="p-6">
          {/* Templates Tab (Level 1) */}
          {activeTab === 'templates' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-lg font-semibold text-white mb-4">Watchlist Templates</h3>
                <p className="text-sm text-gray-400 mb-4">
                  Select symbols from a template to create a new selected watchlist
                </p>
              </div>

              {templates.length === 0 ? (
                <div className="text-center text-gray-400 py-8">
                  No templates found
                </div>
              ) : (
                <div className="space-y-4">
                  {templates.map(template => (
                    <div key={template.templateId} className="border border-gray-700 rounded-lg overflow-hidden">
                      <button
                        onClick={() => {
                          if (selectedTemplate?.templateId === template.templateId) {
                            setSelectedTemplate(null);
                            setTemplateSymbols([]);
                            setSelectedSymbols([]);
                          } else {
                            setSelectedTemplate(template);
                            fetchTemplateSymbols(template.templateId);
                          }
                        }}
                        className="w-full px-4 py-3 bg-gray-750 hover:bg-gray-700 flex items-center justify-between transition-colors"
                      >
                        <div className="flex items-center space-x-3">
                          <List className="w-5 h-5 text-purple-400" />
                          <div className="text-left">
                            <div className="font-medium text-white">{template.templateName}</div>
                            <div className="text-sm text-gray-400">{template.description}</div>
                          </div>
                        </div>
                        <span className="text-gray-400">
                          {selectedTemplate?.templateId === template.templateId ? '▼' : '▶'}
                        </span>
                      </button>

                      {selectedTemplate?.templateId === template.templateId && (
                        <div className="p-4 bg-gray-800 space-y-4">
                          {loading ? (
                            <div className="text-center py-4">
                              <RefreshCw className="w-6 h-6 text-purple-500 animate-spin mx-auto" />
                            </div>
                          ) : (
                            <>
                              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                {templateSymbols.map(sym => (
                                  <label
                                    key={sym.id}
                                    className={`flex items-center space-x-2 px-3 py-2 rounded-lg cursor-pointer transition-colors ${
                                      selectedSymbols.includes(sym.symbol)
                                        ? 'bg-purple-600 text-white'
                                        : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                                    }`}
                                  >
                                    <input
                                      type="checkbox"
                                      checked={selectedSymbols.includes(sym.symbol)}
                                      onChange={(e) => {
                                        if (e.target.checked) {
                                          setSelectedSymbols([...selectedSymbols, sym.symbol]);
                                        } else {
                                          setSelectedSymbols(selectedSymbols.filter(s => s !== sym.symbol));
                                        }
                                      }}
                                      className="form-checkbox"
                                    />
                                    <div className="flex-1">
                                      <span className="text-sm font-mono font-semibold">{sym.symbol}</span>
                                      <div className="text-xs opacity-75">
                                        Lot: {sym.lotSize || 1} | Tick: ₹{sym.tickSize || '0.05'}
                                      </div>
                                    </div>
                                  </label>
                                ))}
                              </div>

                              {selectedSymbols.length > 0 && (
                                <div className="flex items-center justify-between pt-4 border-t border-gray-700">
                                  <span className="text-sm text-gray-400">
                                    {selectedSymbols.length} symbol{selectedSymbols.length !== 1 ? 's' : ''} selected
                                  </span>
                                  <button
                                    onClick={createSelectedWatchlist}
                                    disabled={loading}
                                    className="flex items-center space-x-2 px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg transition-colors disabled:opacity-50"
                                  >
                                    <Plus className="w-4 h-4" />
                                    <span>Create Selected Watchlist</span>
                                  </button>
                                </div>
                              )}
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Selected Watchlists Tab (Level 2) */}
          {activeTab === 'selected' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-white mb-2">Selected Watchlists</h3>
                  <p className="text-sm text-gray-400">
                    Admin-curated watchlists from templates
                  </p>
                </div>
                <button
                  onClick={fetchSelectedWatchlists}
                  className="flex items-center space-x-2 px-3 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors text-sm"
                >
                  <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                  <span>Refresh</span>
                </button>
              </div>

              {selectedWatchlists.length === 0 ? (
                <div className="text-center text-gray-400 py-8">
                  No selected watchlists found. Create one from the Templates tab.
                </div>
              ) : (
                <div className="space-y-4">
                  {selectedWatchlists.map(sel => (
                    <div key={sel.selectedId} className="border border-gray-700 rounded-lg overflow-hidden">
                      <button
                        onClick={() => {
                          if (viewingSelected?.selectedId === sel.selectedId) {
                            setViewingSelected(null);
                            setSelectedWatchlistSymbols([]);
                          } else {
                            setViewingSelected(sel);
                            fetchSelectedWatchlistSymbols(sel.selectedId);
                          }
                        }}
                        className="w-full px-4 py-3 bg-gray-750 hover:bg-gray-700 flex items-center justify-between transition-colors"
                      >
                        <div className="flex items-center space-x-3">
                          <Layers className="w-5 h-5 text-green-400" />
                          <div className="text-left">
                            <div className="font-medium text-white">{sel.name}</div>
                            <div className="text-sm text-gray-400">{sel.description}</div>
                          </div>
                        </div>
                        <span className="text-gray-400">
                          {viewingSelected?.selectedId === sel.selectedId ? '▼' : '▶'}
                        </span>
                      </button>

                      {viewingSelected?.selectedId === sel.selectedId && (
                        <div className="p-4 bg-gray-800 space-y-4">
                          {loading ? (
                            <div className="text-center py-4">
                              <RefreshCw className="w-6 h-6 text-purple-500 animate-spin mx-auto" />
                            </div>
                          ) : (
                            <>
                              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                {selectedWatchlistSymbols.map(sym => (
                                  <div
                                    key={sym.id}
                                    className="px-3 py-2 bg-gray-700 text-gray-200 rounded-lg"
                                  >
                                    <div className="text-sm font-mono font-semibold">{sym.symbol}</div>
                                    <div className="text-xs text-gray-400 mt-1">
                                      Lot: {sym.lotSize || 1} | Tick: ₹{sym.tickSize || '0.05'}
                                    </div>
                                  </div>
                                ))}
                              </div>

                              <div className="flex items-center justify-between pt-4 border-t border-gray-700">
                                <span className="text-sm text-gray-400">
                                  {selectedWatchlistSymbols.length} symbol{selectedWatchlistSymbols.length !== 1 ? 's' : ''}
                                </span>
                                <button
                                  onClick={() => deleteSelectedWatchlist(sel.selectedId)}
                                  disabled={loading}
                                  className="flex items-center space-x-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors disabled:opacity-50 text-sm"
                                >
                                  <Trash2 className="w-4 h-4" />
                                  <span>Delete</span>
                                </button>
                              </div>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Default Watchlist Tab (Level 3) */}
          {activeTab === 'default' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-white mb-2">Default Watchlist (Level 3)</h3>
                  <p className="text-sm text-gray-400">
                    Merged and deduplicated from all selected watchlists. Auto-synced to all users.
                  </p>
                </div>
                <div className="flex items-center space-x-2">
                  <button
                    onClick={fetchDefaultWatchlist}
                    className="flex items-center space-x-2 px-3 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors text-sm"
                  >
                    <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                    <span>Refresh</span>
                  </button>
                  <button
                    onClick={syncToAllUsers}
                    disabled={loading}
                    className="flex items-center space-x-2 px-3 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors text-sm disabled:opacity-50"
                  >
                    <Network className="w-4 h-4" />
                    <span>Sync to All Users</span>
                  </button>
                </div>
              </div>

              {loading ? (
                <div className="text-center py-8">
                  <RefreshCw className="w-8 h-8 text-purple-500 animate-spin mx-auto mb-2" />
                  <p className="text-gray-400">Loading default watchlist...</p>
                </div>
              ) : defaultWatchlist.length === 0 ? (
                <div className="text-center text-gray-400 py-8">
                  No symbols in default watchlist. Create selected watchlists from templates first.
                </div>
              ) : (
                <div className="bg-gray-750 border border-gray-700 rounded-lg p-6">
                  <div className="mb-4">
                    <span className="text-sm text-gray-400">
                      Total: <span className="font-semibold text-white">{defaultWatchlist.length}</span> unique symbols
                    </span>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
                    {defaultWatchlist.map((sym, idx) => (
                      <div
                        key={sym.symbol || idx}
                        className="px-3 py-2 bg-gray-800 text-gray-200 rounded-lg"
                      >
                        <div className="text-sm font-mono font-semibold">{sym.symbol}</div>
                        <div className="text-xs text-gray-400 mt-1">
                          Lot: {sym.lotSize || 1} | Tick: ₹{sym.tickSize || '0.05'}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* User Watchlists Tab (Level 4 - existing functionality) */}
          {activeTab === 'users' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-lg font-semibold text-white mb-2">User-Broker Watchlists (Level 4)</h3>
                <p className="text-sm text-gray-400">
                  View and manage individual user watchlists. Includes synced symbols + custom additions.
                </p>
              </div>

              {/* User Selection */}
              <div className="bg-gray-750 border border-gray-700 rounded-lg p-4">
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Select User
                </label>
                <select
                  value={selectedUserId}
                  onChange={(e) => {
                    setSelectedUserId(e.target.value);
                    setFormData({ userBrokerId: '', symbol: '' });
                  }}
                  className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                >
                  <option value="">-- Select User --</option>
                  {users.map(user => (
                    <option key={user.userId} value={user.userId}>
                      {user.email} ({user.displayName}) - {user.userId}
                    </option>
                  ))}
                </select>
              </div>

              {/* Add to Watchlist Form */}
              {selectedUserId && (
                <div className="bg-gray-750 border border-gray-700 rounded-lg p-4">
                  <h4 className="text-md font-semibold text-white mb-4">Add Custom Symbol</h4>

                  {selectedUserBrokers.length === 0 && (
                    <div className="bg-yellow-900 border border-yellow-700 text-yellow-200 px-4 py-3 rounded-lg mb-4">
                      <p className="font-medium">No user-broker links found</p>
                      <p className="text-sm">Please link a broker to this user first in the Broker Management tab.</p>
                    </div>
                  )}

                  <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-300 mb-2">
                        User-Broker ID
                      </label>
                      <select
                        value={formData.userBrokerId}
                        onChange={(e) => setFormData({ ...formData, userBrokerId: e.target.value })}
                        required
                        disabled={selectedUserBrokers.length === 0}
                        className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 disabled:opacity-50"
                      >
                        <option value="">-- Select User-Broker --</option>
                        {selectedUserBrokers.map(ub => (
                          <option key={ub.userBrokerId} value={ub.userBrokerId}>
                            {ub.userBrokerId} - {ub.brokerId} ({ub.role})
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="relative">
                      <label className="block text-sm font-medium text-gray-300 mb-2">
                        Symbol
                      </label>
                      <input
                        type="text"
                        value={instrumentSearch}
                        onChange={(e) => {
                          setInstrumentSearch(e.target.value.toUpperCase());
                          setShowInstrumentDropdown(true);
                        }}
                        onFocus={() => {
                          if (instrumentResults.length > 0) {
                            setShowInstrumentDropdown(true);
                          }
                        }}
                        placeholder="Search symbols... (e.g., RELIANCE)"
                        className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 uppercase focus:outline-none focus:ring-2 focus:ring-purple-500"
                        autoComplete="off"
                      />

                      {/* Autocomplete Dropdown */}
                      {showInstrumentDropdown && instrumentResults.length > 0 && (
                        <div className="absolute z-50 w-full mt-1 bg-gray-800 border border-gray-600 rounded-lg shadow-lg max-h-64 overflow-y-auto">
                          {instrumentResults.map((instrument, idx) => (
                            <button
                              key={idx}
                              type="button"
                              onClick={() => {
                                setFormData({ ...formData, symbol: instrument.symbol });
                                setInstrumentSearch(instrument.symbol);
                                setShowInstrumentDropdown(false);
                              }}
                              className="w-full text-left px-4 py-2 hover:bg-gray-700 transition-colors border-b border-gray-700 last:border-0"
                            >
                              <div className="font-mono text-sm text-purple-400">{instrument.symbol}</div>
                              <div className="text-xs text-gray-400">{instrument.name}</div>
                            </button>
                          ))}
                        </div>
                      )}

                      {/* Info text */}
                      <p className="text-xs text-gray-400 mt-1">
                        {instrumentSearch.length < 2 ? 'Type at least 2 characters to search' :
                         instrumentResults.length === 0 && instrumentSearch.length >= 2 ? 'No instruments found' :
                         ''}
                      </p>
                    </div>

                    {submitStatus && (
                      <div className={`p-4 rounded-lg border ${
                        submitStatus.type === 'success'
                          ? 'bg-green-900 border-green-700 text-green-200'
                          : submitStatus.type === 'error'
                          ? 'bg-red-900 border-red-700 text-red-200'
                          : 'bg-blue-900 border-blue-700 text-blue-200'
                      }`}>
                        <div className="flex items-center space-x-2">
                          {submitStatus.type === 'success' ? (
                            <CheckCircle className="w-5 h-5" />
                          ) : submitStatus.type === 'error' ? (
                            <AlertCircle className="w-5 h-5" />
                          ) : (
                            <RefreshCw className="w-5 h-5 animate-spin" />
                          )}
                          <span>{submitStatus.message}</span>
                        </div>
                      </div>
                    )}

                    <button
                      type="submit"
                      disabled={submitStatus?.type === 'loading' || selectedUserBrokers.length === 0}
                      className="flex items-center space-x-2 px-6 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors disabled:opacity-50"
                    >
                      <Plus className="w-4 h-4" />
                      <span>Add Custom Symbol</span>
                    </button>
                  </form>
                </div>
              )}

              {/* Watchlist Table */}
              {selectedUserId && (
                <div className="bg-gray-750 border border-gray-700 rounded-lg overflow-hidden">
                  <div className="px-6 py-4 bg-gray-800 border-b border-gray-700 flex items-center justify-between">
                    <h4 className="text-md font-semibold text-white">Current Watchlist</h4>
                    <button
                      onClick={() => fetchWatchlist(selectedUserId)}
                      disabled={loading}
                      className="flex items-center space-x-2 px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors disabled:opacity-50 text-sm"
                    >
                      <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                      <span>Refresh</span>
                    </button>
                  </div>

                  {loading ? (
                    <div className="p-8 text-center">
                      <RefreshCw className="w-8 h-8 text-purple-500 animate-spin mx-auto mb-2" />
                      <p className="text-gray-400">Loading watchlist...</p>
                    </div>
                  ) : watchlist.length === 0 ? (
                    <div className="p-8 text-center text-gray-400">
                      No watchlist entries found for this user
                    </div>
                  ) : (
                    <>
                      <div className="overflow-x-auto">
                        <table className="w-full">
                          <thead className="bg-gray-800 border-b border-gray-700">
                            <tr>
                              <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                                Symbol
                              </th>
                              <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                                Lot Size
                              </th>
                              <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                                Tick Size
                              </th>
                              <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                                Type
                              </th>
                              <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                                User-Brokers
                              </th>
                              <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                                Status
                              </th>
                              <th className="px-6 py-3 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">
                                Actions
                              </th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-700">
                            {deduplicatedWatchlist.map(group => (
                              <tr key={group.symbol} className="hover:bg-gray-700 transition-colors">
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-white font-semibold font-mono">
                                  {group.symbol}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-300">
                                  {group.lotSize || 1}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-300">
                                  ₹{group.tickSize || '0.05'}
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm">
                                  <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                                    group.isCustom
                                      ? 'bg-blue-900 text-blue-200 border border-blue-700'
                                      : 'bg-gray-700 text-gray-300 border border-gray-600'
                                  }`}>
                                    {group.isCustom ? 'Custom' : 'Default'}
                                  </span>
                                </td>
                                <td className="px-6 py-4 text-sm font-mono text-gray-300">
                                  <div className="flex flex-wrap gap-1">
                                    {group.items.map((item, idx) => (
                                      <span key={item.id} className="inline">
                                        {item.userBrokerId}{idx < group.items.length - 1 ? ',' : ''}
                                      </span>
                                    ))}
                                  </div>
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm">
                                  <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                                    group.enabled
                                      ? 'bg-green-900 text-green-200 border border-green-700'
                                      : 'bg-gray-700 text-gray-300 border border-gray-600'
                                  }`}>
                                    {group.enabled ? 'ENABLED' : 'DISABLED'}
                                  </span>
                                </td>
                                <td className="px-6 py-4 whitespace-nowrap text-sm text-center">
                                  <div className="flex items-center justify-center gap-2">
                                    <button
                                      onClick={() => {
                                        // Toggle all instances of this symbol
                                        group.items.forEach(item => handleToggleWatchlistItem(item.id, item.enabled));
                                      }}
                                      className={`p-1.5 rounded transition-colors ${
                                        group.enabled
                                          ? 'text-green-400 hover:bg-green-900/30 hover:text-green-300'
                                          : 'text-gray-500 hover:bg-gray-700 hover:text-gray-400'
                                      }`}
                                      title={group.enabled ? 'Disable symbol' : 'Enable symbol'}
                                    >
                                      <Power className="w-4 h-4" />
                                    </button>
                                    <button
                                      onClick={() => {
                                        // Delete all instances of this symbol
                                        if (confirm(`Delete ${group.symbol} from all user-brokers?`)) {
                                          group.items.forEach(item => handleDeleteWatchlistItem(item.id));
                                        }
                                      }}
                                      className="p-1.5 text-red-400 hover:bg-red-900/30 hover:text-red-300 rounded transition-colors"
                                      title="Delete symbol from all brokers"
                                    >
                                      <Trash2 className="w-4 h-4" />
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>

                      <div className="bg-gray-800 border-t border-gray-700 px-6 py-3">
                        <p className="text-sm text-gray-400">
                          Total: <span className="font-medium text-white">{deduplicatedWatchlist.length}</span> unique symbol{deduplicatedWatchlist.length !== 1 ? 's' : ''}
                        </p>
                      </div>
                    </>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Template Management Tab */}
          {activeTab === 'template-mgmt' && (
            <WatchlistTemplateManagement token={token} />
          )}
        </div>
      </div>
    </div>
  );
};

export default WatchlistManagement;
