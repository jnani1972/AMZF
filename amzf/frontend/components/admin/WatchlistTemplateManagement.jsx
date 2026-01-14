import React, { useState, useEffect } from 'react';
import { Search, Plus, Trash2, X, Filter } from 'lucide-react';

const API_BASE_URL = 'http://localhost:9090';

export default function WatchlistTemplateManagement({ token }) {
    const [templates, setTemplates] = useState([]);
    const [selectedTemplate, setSelectedTemplate] = useState(null);
    const [templateSymbols, setTemplateSymbols] = useState([]);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newTemplate, setNewTemplate] = useState({ templateName: '', description: '' });

    // Add symbol states
    const [instrumentSearch, setInstrumentSearch] = useState('');
    const [instrumentResults, setInstrumentResults] = useState([]);
    const [showInstrumentDropdown, setShowInstrumentDropdown] = useState(false);

    // Search within template
    const [symbolFilter, setSymbolFilter] = useState('');

    // Fetch all templates
    const fetchTemplates = async () => {
        if (!token) {
            console.error('No token available');
            setTemplates([]);
            return;
        }

        try {
            const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            if (!res.ok) {
                const errorText = await res.text();
                console.error('Failed to fetch templates:', res.status, res.statusText, errorText);
                setTemplates([]);
                return;
            }

            const data = await res.json();
            setTemplates(Array.isArray(data) ? data : []);
        } catch (error) {
            console.error('Failed to fetch templates:', error);
            setTemplates([]);
        }
    };

    // Fetch symbols for selected template
    const fetchTemplateSymbols = async (templateId) => {
        try {
            const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates/${templateId}/symbols`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            const data = await res.json();
            setTemplateSymbols(data);
        } catch (error) {
            console.error('Failed to fetch template symbols:', error);
        }
    };

    // Search instruments
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
            const data = await res.json();
            setInstrumentResults(data);
            setShowInstrumentDropdown(true);
        } catch (error) {
            console.error('Failed to search instruments:', error);
        }
    };

    // Create new template
    const handleCreateTemplate = async () => {
        if (!newTemplate.templateName) {
            alert('Template name is required');
            return;
        }

        try {
            const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    templateName: newTemplate.templateName,
                    description: newTemplate.description,
                    displayOrder: templates.length + 1
                })
            });
            const result = await res.json();

            if (result.success) {
                alert('Template created successfully');
                setShowCreateModal(false);
                setNewTemplate({ templateName: '', description: '' });
                fetchTemplates();
            } else {
                alert('Failed to create template');
            }
        } catch (error) {
            console.error('Failed to create template:', error);
            alert('Error creating template');
        }
    };

    // Add symbol to template
    const handleAddSymbol = async (symbol) => {
        if (!selectedTemplate) return;

        try {
            const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates/${selectedTemplate.templateId}/symbols`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    symbol: symbol,
                    displayOrder: templateSymbols.length + 1
                })
            });
            const result = await res.json();

            if (result.success) {
                setInstrumentSearch('');
                setShowInstrumentDropdown(false);
                fetchTemplateSymbols(selectedTemplate.templateId);
            } else {
                alert('Failed to add symbol');
            }
        } catch (error) {
            console.error('Failed to add symbol:', error);
            alert('Error adding symbol');
        }
    };

    // Delete symbol from template
    const handleDeleteSymbol = async (symbolId) => {
        if (!confirm('Are you sure you want to delete this symbol?')) return;

        try {
            const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates/symbols/${symbolId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            const result = await res.json();

            if (result.success) {
                fetchTemplateSymbols(selectedTemplate.templateId);
            } else {
                alert('Failed to delete symbol');
            }
        } catch (error) {
            console.error('Failed to delete symbol:', error);
            alert('Error deleting symbol');
        }
    };

    // Delete template
    const handleDeleteTemplate = async (templateId) => {
        if (!confirm('Are you sure you want to delete this template? This will also delete all symbols in it.')) return;

        try {
            const res = await fetch(`${API_BASE_URL}/api/admin/watchlist-templates/${templateId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            const result = await res.json();

            if (result.success) {
                if (selectedTemplate && selectedTemplate.templateId === templateId) {
                    setSelectedTemplate(null);
                    setTemplateSymbols([]);
                }
                fetchTemplates();
            } else {
                alert('Failed to delete template');
            }
        } catch (error) {
            console.error('Failed to delete template:', error);
            alert('Error deleting template');
        }
    };

    // Select template and load symbols
    const handleSelectTemplate = (template) => {
        setSelectedTemplate(template);
        fetchTemplateSymbols(template.templateId);
        setSymbolFilter('');
    };

    useEffect(() => {
        if (token) {
            fetchTemplates();
        }
    }, [token]);

    useEffect(() => {
        const debounce = setTimeout(() => {
            searchInstruments(instrumentSearch);
        }, 300);
        return () => clearTimeout(debounce);
    }, [instrumentSearch]);

    // Filter symbols by search
    const filteredSymbols = templateSymbols.filter(sym =>
        sym.symbol.toUpperCase().includes(symbolFilter.toUpperCase())
    );

    return (
        <div className="p-6">
            <div className="flex justify-between items-center mb-6">
                <h2 className="text-2xl font-bold text-gray-800">Watchlist Template Management</h2>
                <button
                    onClick={() => setShowCreateModal(true)}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 flex items-center gap-2"
                >
                    <Plus size={20} />
                    Create Template
                </button>
            </div>

            <div className="grid grid-cols-3 gap-6">
                {/* Templates List */}
                <div className="bg-white rounded-lg shadow p-4">
                    <h3 className="text-lg font-semibold mb-4 text-gray-700">Templates</h3>
                    {templates.length === 0 ? (
                        <p className="text-gray-500 text-sm">No templates found</p>
                    ) : (
                        <div className="space-y-2">
                            {templates.map((template) => (
                                <div
                                    key={template.templateId}
                                    className={`p-3 border rounded-lg cursor-pointer hover:bg-gray-50 ${
                                        selectedTemplate?.templateId === template.templateId
                                            ? 'bg-blue-50 border-blue-500'
                                            : 'border-gray-200'
                                    }`}
                                    onClick={() => handleSelectTemplate(template)}
                                >
                                    <div className="flex justify-between items-start">
                                        <div className="flex-1">
                                            <h4 className="font-medium text-gray-800">{template.templateName}</h4>
                                            {template.description && (
                                                <p className="text-sm text-gray-500 mt-1">{template.description}</p>
                                            )}
                                        </div>
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handleDeleteTemplate(template.templateId);
                                            }}
                                            className="text-red-500 hover:text-red-700 p-1"
                                        >
                                            <Trash2 size={16} />
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Template Symbols */}
                <div className="col-span-2 bg-white rounded-lg shadow p-4">
                    {!selectedTemplate ? (
                        <p className="text-gray-500 text-center py-8">Select a template to view symbols</p>
                    ) : (
                        <>
                            <div className="mb-4">
                                <h3 className="text-lg font-semibold text-gray-700 mb-2">
                                    {selectedTemplate.templateName} - Symbols ({templateSymbols.length})
                                </h3>

                                {/* Add Symbol Section */}
                                <div className="mb-4 relative">
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Add Symbol
                                    </label>
                                    <div className="relative">
                                        <input
                                            type="text"
                                            value={instrumentSearch}
                                            onChange={(e) => setInstrumentSearch(e.target.value)}
                                            placeholder="Search instruments (e.g., RELIANCE, TCS)"
                                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                        />
                                        <Search className="absolute right-3 top-2.5 text-gray-400" size={20} />
                                    </div>

                                    {/* Autocomplete Dropdown */}
                                    {showInstrumentDropdown && instrumentResults.length > 0 && (
                                        <div className="absolute z-10 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-lg max-h-60 overflow-y-auto">
                                            {instrumentResults.map((inst, idx) => (
                                                <div
                                                    key={idx}
                                                    onClick={() => handleAddSymbol(inst.symbol)}
                                                    className="px-4 py-2 hover:bg-blue-50 cursor-pointer border-b border-gray-100 last:border-b-0"
                                                >
                                                    <div className="font-medium text-gray-800">{inst.symbol}</div>
                                                    <div className="text-sm text-gray-500">{inst.name}</div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                {/* Search within template */}
                                <div className="relative mb-4">
                                    <input
                                        type="text"
                                        value={symbolFilter}
                                        onChange={(e) => setSymbolFilter(e.target.value)}
                                        placeholder="Filter symbols in template..."
                                        className="w-full px-3 py-2 pl-10 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                    />
                                    <Filter className="absolute left-3 top-2.5 text-gray-400" size={20} />
                                </div>
                            </div>

                            {/* Symbols Table */}
                            {filteredSymbols.length === 0 ? (
                                <p className="text-gray-500 text-center py-4">
                                    {symbolFilter ? 'No symbols match your filter' : 'No symbols in this template. Add some above.'}
                                </p>
                            ) : (
                                <div className="overflow-x-auto">
                                    <table className="min-w-full divide-y divide-gray-200">
                                        <thead className="bg-gray-50">
                                            <tr>
                                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Symbol</th>
                                                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Order</th>
                                                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Actions</th>
                                            </tr>
                                        </thead>
                                        <tbody className="bg-white divide-y divide-gray-200">
                                            {filteredSymbols.map((symbol) => (
                                                <tr key={symbol.id} className="hover:bg-gray-50">
                                                    <td className="px-4 py-3 text-sm font-medium text-gray-800">{symbol.symbol}</td>
                                                    <td className="px-4 py-3 text-sm text-gray-600">{symbol.displayOrder}</td>
                                                    <td className="px-4 py-3 text-sm text-right">
                                                        <button
                                                            onClick={() => handleDeleteSymbol(symbol.id)}
                                                            className="text-red-500 hover:text-red-700 p-1"
                                                        >
                                                            <Trash2 size={16} />
                                                        </button>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </>
                    )}
                </div>
            </div>

            {/* Create Template Modal */}
            {showCreateModal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="text-lg font-semibold text-gray-800">Create New Template</h3>
                            <button
                                onClick={() => setShowCreateModal(false)}
                                className="text-gray-400 hover:text-gray-600"
                            >
                                <X size={20} />
                            </button>
                        </div>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Template Name *
                                </label>
                                <input
                                    type="text"
                                    value={newTemplate.templateName}
                                    onChange={(e) => setNewTemplate({ ...newTemplate, templateName: e.target.value })}
                                    placeholder="e.g., Nifty 50"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Description
                                </label>
                                <textarea
                                    value={newTemplate.description}
                                    onChange={(e) => setNewTemplate({ ...newTemplate, description: e.target.value })}
                                    placeholder="Optional description..."
                                    rows={3}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                />
                            </div>

                            <div className="flex gap-3 pt-4">
                                <button
                                    onClick={() => setShowCreateModal(false)}
                                    className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={handleCreateTemplate}
                                    className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                                >
                                    Create
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
