import React, { useState, useEffect } from 'react';
import { Briefcase, Plus, RefreshCw, Search, CheckCircle, AlertCircle, DollarSign } from 'lucide-react';

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
  || (import.meta?.env?.VITE_API_BASE_URL)
  || "http://localhost:9090";

const PortfolioManagement = ({ token }) => {
  const [users, setUsers] = useState([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [formData, setFormData] = useState({
    name: 'Default Portfolio',
    totalCapital: '100000'
  });
  const [submitStatus, setSubmitStatus] = useState(null);

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

  const fetchPortfolios = async (userId) => {
    if (!userId) {
      setPortfolios([]);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/portfolios?userId=${userId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to fetch portfolios');
      const data = await res.json();
      setPortfolios(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, [token]);

  useEffect(() => {
    if (selectedUserId) {
      fetchPortfolios(selectedUserId);
    }
  }, [selectedUserId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selectedUserId) {
      setSubmitStatus({ type: 'error', message: 'Please select a user' });
      return;
    }

    setSubmitStatus({ type: 'loading', message: 'Creating portfolio...' });

    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/portfolios`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          userId: selectedUserId,
          name: formData.name,
          totalCapital: parseFloat(formData.totalCapital)
        })
      });

      const data = await res.json();

      if (data.success) {
        setSubmitStatus({ type: 'success', message: `Portfolio created: ${data.portfolioId}` });
        setFormData({ name: 'Default Portfolio', totalCapital: '100000' });
        // Refresh portfolios
        fetchPortfolios(selectedUserId);
        setTimeout(() => setSubmitStatus(null), 3000);
      } else {
        setSubmitStatus({ type: 'error', message: data.error || 'Failed to create portfolio' });
      }
    } catch (err) {
      setSubmitStatus({ type: 'error', message: err.message });
    }
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0
    }).format(amount);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <Briefcase className="w-6 h-6 text-purple-500" />
          <h2 className="text-xl font-bold text-white">Portfolio Management</h2>
        </div>
      </div>

      {/* User Selection */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
        <label className="block text-sm font-medium text-gray-300 mb-2">
          Select User
        </label>
        <select
          value={selectedUserId}
          onChange={(e) => setSelectedUserId(e.target.value)}
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

      {/* Create Portfolio Form */}
      {selectedUserId && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Create New Portfolio</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Portfolio Name */}
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Portfolio Name
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  required
                  className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                />
              </div>

              {/* Total Capital */}
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Total Capital (₹)
                </label>
                <input
                  type="number"
                  value={formData.totalCapital}
                  onChange={(e) => setFormData({ ...formData, totalCapital: e.target.value })}
                  required
                  min="0"
                  step="1000"
                  className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                />
              </div>
            </div>

            {/* Submit Status */}
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

            {/* Submit Button */}
            <button
              type="submit"
              disabled={submitStatus?.type === 'loading'}
              className="flex items-center space-x-2 px-6 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors disabled:opacity-50"
            >
              <Plus className="w-4 h-4" />
              <span>Create Portfolio</span>
            </button>
          </form>
        </div>
      )}

      {/* Portfolios List */}
      {selectedUserId && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          <div className="px-6 py-4 bg-gray-750 border-b border-gray-700 flex items-center justify-between">
            <h3 className="text-lg font-semibold text-white">User Portfolios</h3>
            <button
              onClick={() => fetchPortfolios(selectedUserId)}
              disabled={loading}
              className="flex items-center space-x-2 px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors disabled:opacity-50 text-sm"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>

          {error && (
            <div className="bg-red-900 border border-red-700 text-red-200 px-4 py-3 m-4 rounded-lg">
              <p className="font-medium">Error loading portfolios</p>
              <p className="text-sm">{error}</p>
            </div>
          )}

          {loading ? (
            <div className="p-8 text-center">
              <RefreshCw className="w-8 h-8 text-purple-500 animate-spin mx-auto mb-2" />
              <p className="text-gray-400">Loading portfolios...</p>
            </div>
          ) : portfolios.length === 0 ? (
            <div className="p-8 text-center text-gray-400">
              No portfolios found for this user
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-750 border-b border-gray-700">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                      Portfolio ID
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                      Name
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                      Total Capital
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                      Available Capital
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-700">
                  {portfolios.map(portfolio => (
                    <tr key={portfolio.portfolioId} className="hover:bg-gray-750 transition-colors">
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-gray-300">
                        {portfolio.portfolioId}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-white font-medium">
                        {portfolio.name}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-green-400 font-semibold">
                        {formatCurrency(portfolio.totalCapital)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-blue-400 font-semibold">
                        {formatCurrency(portfolio.availableCapital)}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          portfolio.status === 'ACTIVE'
                            ? 'bg-green-900 text-green-200 border border-green-700'
                            : 'bg-gray-700 text-gray-300 border border-gray-600'
                        }`}>
                          {portfolio.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default PortfolioManagement;
