import React, { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, RefreshCw, AlertCircle, CheckCircle, X } from 'lucide-react';

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
  || (import.meta?.env?.VITE_API_BASE_URL)
  || "http://localhost:9090";

const MtfSymbolConfigTable = ({ token, globalConfig }) => {
  const [symbolConfigs, setSymbolConfigs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [editingConfig, setEditingConfig] = useState(null);
  const [formData, setFormData] = useState({});

  useEffect(() => {
    fetchSymbolConfigs();
  }, []);

  const fetchSymbolConfigs = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/mtf-config/symbols`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch symbol configurations');
      const data = await res.json();
      setSymbolConfigs(data.symbols || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingConfig(null);
    setFormData({
      symbol: '',
      userBrokerId: '',
      kellyFraction: null,
      maxPositionLogLoss: null,
      minProfitPct: null
    });
    setShowModal(true);
  };

  const handleEdit = (config) => {
    setEditingConfig(config);
    setFormData(config);
    setShowModal(true);
  };

  const handleSave = async () => {
    try {
      const symbol = editingConfig ? editingConfig.symbol : formData.symbol;
      const res = await fetch(`${API_BASE_URL}/api/admin/mtf-config/symbols/${symbol}`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(formData)
      });
      if (!res.ok) throw new Error('Failed to save symbol configuration');
      setShowModal(false);
      await fetchSymbolConfigs();
    } catch (err) {
      setError(err.message);
    }
  };

  const handleDelete = async (symbol, userBrokerId) => {
    if (!confirm(`Delete configuration for ${symbol}?`)) return;

    try {
      const res = await fetch(
        `${API_BASE_URL}/api/admin/mtf-config/symbols/${symbol}?userBrokerId=${userBrokerId}`,
        {
          method: 'DELETE',
          headers: { 'Authorization': `Bearer ${token}` }
        }
      );
      if (!res.ok) throw new Error('Failed to delete configuration');
      await fetchSymbolConfigs();
    } catch (err) {
      setError(err.message);
    }
  };

  const handleFieldChange = (field, value) => {
    setFormData(prev => ({
      ...prev,
      [field]: value === '' ? null : value
    }));
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center p-8">
        <RefreshCw className="w-6 h-6 animate-spin text-purple-500" />
        <span className="ml-2 text-gray-400">Loading symbol configurations...</span>
      </div>
    );
  }

  return (
    <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-xl font-bold text-white">Symbol-Specific Overrides</h3>
          <p className="text-sm text-gray-400">Override global settings for specific symbols</p>
        </div>
        <div className="flex items-center space-x-2">
          <button
            onClick={fetchSymbolConfigs}
            className="flex items-center space-x-2 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Refresh</span>
          </button>
          <button
            onClick={handleCreate}
            className="flex items-center space-x-2 px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>Add Override</span>
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-900/20 border border-red-500 rounded-lg p-4">
          <div className="flex items-center space-x-2">
            <AlertCircle className="w-5 h-5 text-red-500" />
            <span className="text-red-400">{error}</span>
          </div>
        </div>
      )}

      {/* Table */}
      {symbolConfigs.length === 0 ? (
        <div className="text-center py-8 text-gray-400">
          <p>No symbol-specific configurations found.</p>
          <p className="text-sm mt-2">Click "Add Override" to create one.</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-700">
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-400">Symbol</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-400">User Broker ID</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-400">Kelly Fraction</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-400">Max Position Log Loss</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-gray-400">Min Profit %</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-gray-400">Actions</th>
              </tr>
            </thead>
            <tbody>
              {symbolConfigs.map((config, idx) => (
                <tr key={idx} className="border-b border-gray-700 hover:bg-gray-700/50">
                  <td className="py-3 px-4 text-white font-medium">{config.symbol}</td>
                  <td className="py-3 px-4 text-gray-400 text-sm">{config.userBrokerId}</td>
                  <td className="py-3 px-4 text-gray-300">
                    {config.kellyFraction !== null ? config.kellyFraction : (
                      <span className="text-gray-500 italic">global</span>
                    )}
                  </td>
                  <td className="py-3 px-4 text-gray-300">
                    {config.maxPositionLogLoss !== null ? config.maxPositionLogLoss : (
                      <span className="text-gray-500 italic">global</span>
                    )}
                  </td>
                  <td className="py-3 px-4 text-gray-300">
                    {config.minProfitPct !== null ? config.minProfitPct : (
                      <span className="text-gray-500 italic">global</span>
                    )}
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center justify-end space-x-2">
                      <button
                        onClick={() => handleEdit(config)}
                        className="p-2 hover:bg-gray-600 rounded-lg transition-colors"
                      >
                        <Edit className="w-4 h-4 text-blue-400" />
                      </button>
                      <button
                        onClick={() => handleDelete(config.symbol, config.userBrokerId)}
                        className="p-2 hover:bg-gray-600 rounded-lg transition-colors"
                      >
                        <Trash2 className="w-4 h-4 text-red-400" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-xl font-bold text-white">
                {editingConfig ? 'Edit Symbol Override' : 'Create Symbol Override'}
              </h3>
              <button
                onClick={() => setShowModal(false)}
                className="p-2 hover:bg-gray-700 rounded-lg transition-colors"
              >
                <X className="w-5 h-5 text-gray-400" />
              </button>
            </div>

            <div className="space-y-4">
              {/* Required Fields */}
              {!editingConfig && (
                <>
                  <TextInput
                    label="Symbol *"
                    value={formData.symbol || ''}
                    onChange={(v) => handleFieldChange('symbol', v)}
                    placeholder="e.g., NSE:RELIANCE"
                  />
                  <TextInput
                    label="User Broker ID *"
                    value={formData.userBrokerId || ''}
                    onChange={(v) => handleFieldChange('userBrokerId', v)}
                    placeholder="e.g., UB_DATA_E7DE4B"
                  />
                </>
              )}

              {/* Optional Override Fields */}
              <div className="pt-4 border-t border-gray-700">
                <p className="text-sm text-gray-400 mb-4">
                  Leave fields empty to use global defaults. Only set values you want to override.
                </p>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <NullableNumberInput
                    label="Kelly Fraction"
                    value={formData.kellyFraction}
                    onChange={(v) => handleFieldChange('kellyFraction', v)}
                    step="0.01"
                    globalValue={globalConfig?.kellyFraction}
                  />
                  <NullableNumberInput
                    label="Max Position Log Loss"
                    value={formData.maxPositionLogLoss}
                    onChange={(v) => handleFieldChange('maxPositionLogLoss', v)}
                    step="0.0001"
                    globalValue={globalConfig?.maxPositionLogLoss}
                  />
                  <NullableNumberInput
                    label="Min Profit %"
                    value={formData.minProfitPct}
                    onChange={(v) => handleFieldChange('minProfitPct', v)}
                    step="0.0001"
                    globalValue={globalConfig?.minProfitPct}
                  />
                  <NullableNumberInput
                    label="Target R-Multiple"
                    value={formData.targetRMultiple}
                    onChange={(v) => handleFieldChange('targetRMultiple', v)}
                    step="0.1"
                    globalValue={globalConfig?.targetRMultiple}
                  />
                </div>
              </div>

              {/* Actions */}
              <div className="flex justify-end space-x-2 pt-4">
                <button
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSave}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors"
                >
                  Save Override
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

// Helper Components
const TextInput = ({ label, value, onChange, placeholder }) => (
  <div>
    <label className="block text-sm font-medium text-gray-400 mb-1">{label}</label>
    <input
      type="text"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
    />
  </div>
);

const NullableNumberInput = ({ label, value, onChange, step = "1", globalValue }) => (
  <div>
    <label className="block text-sm font-medium text-gray-400 mb-1">
      {label}
      {globalValue !== undefined && (
        <span className="text-xs text-gray-500 ml-2">(global: {globalValue})</span>
      )}
    </label>
    <input
      type="number"
      value={value === null ? '' : value}
      onChange={(e) => onChange(e.target.value === '' ? null : parseFloat(e.target.value))}
      step={step}
      placeholder="Use global default"
      className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
    />
  </div>
);

export default MtfSymbolConfigTable;
