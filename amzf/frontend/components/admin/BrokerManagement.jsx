import React, { useState, useEffect } from 'react';
import { Network, Plus, RefreshCw, X, CheckCircle, AlertCircle, Trash2, Power } from 'lucide-react';

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
  || (import.meta?.env?.VITE_API_BASE_URL)
  || "http://localhost:9090";

const BrokerManagement = ({ token }) => {
  const [brokers, setBrokers] = useState([]);
  const [users, setUsers] = useState([]);
  const [userBrokers, setUserBrokers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [formData, setFormData] = useState({
    userId: '',
    brokerId: '',
    credentials: '{}',
    isDataBroker: false
  });
  const [submitStatus, setSubmitStatus] = useState(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [brokersRes, usersRes, userBrokersRes] = await Promise.all([
        fetch(`${API_BASE_URL}/api/admin/brokers`, {
          headers: { 'Authorization': `Bearer ${token}` }
        }),
        fetch(`${API_BASE_URL}/api/admin/users`, {
          headers: { 'Authorization': `Bearer ${token}` }
        }),
        fetch(`${API_BASE_URL}/api/admin/user-brokers`, {
          headers: { 'Authorization': `Bearer ${token}` }
        })
      ]);

      if (!brokersRes.ok || !usersRes.ok || !userBrokersRes.ok) {
        throw new Error('Failed to fetch data');
      }

      const brokersData = await brokersRes.json();
      const usersData = await usersRes.json();
      const userBrokersData = await userBrokersRes.json();

      setBrokers(brokersData);
      setUsers(usersData.filter(u => u.role !== 'ADMIN')); // Exclude admin users
      setUserBrokers(userBrokersData);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [token]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitStatus({ type: 'loading', message: 'Creating user-broker link...' });

    try {
      // Validate JSON
      const credentialsObj = JSON.parse(formData.credentials);

      const res = await fetch(`${API_BASE_URL}/api/admin/user-brokers`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          userId: formData.userId,
          brokerId: formData.brokerId,
          credentials: credentialsObj,
          isDataBroker: formData.isDataBroker
        })
      });

      const data = await res.json();

      if (data.success) {
        setSubmitStatus({ type: 'success', message: `User-broker created: ${data.userBrokerId}` });
        setTimeout(() => {
          setShowModal(false);
          setFormData({ userId: '', brokerId: '', credentials: '{}', isDataBroker: false });
          setSubmitStatus(null);
          fetchData(); // Refresh data to show new user-broker
        }, 2000);
      } else {
        setSubmitStatus({ type: 'error', message: data.error || 'Failed to create user-broker' });
      }
    } catch (err) {
      setSubmitStatus({ type: 'error', message: err.message });
    }
  };

  const handleDeleteUserBroker = async (userBrokerId) => {
    if (!confirm(`Are you sure you want to delete user-broker ${userBrokerId}?`)) {
      return;
    }

    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/user-brokers/${userBrokerId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      const data = await res.json();

      if (data.success) {
        fetchData(); // Refresh data
      } else {
        setError(data.error || 'Failed to delete user-broker');
      }
    } catch (err) {
      setError(err.message);
    }
  };

  const handleToggleUserBroker = async (userBrokerId) => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/admin/user-brokers/${userBrokerId}/toggle`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      const data = await res.json();

      if (data.success) {
        fetchData(); // Refresh data
      } else {
        setError(data.error || 'Failed to toggle user-broker');
      }
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <Network className="w-6 h-6 text-purple-500" />
          <h2 className="text-xl font-bold text-white">Broker Management</h2>
        </div>
        <div className="flex space-x-2">
          <button
            onClick={fetchData}
            disabled={loading}
            className="flex items-center space-x-2 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
          <button
            onClick={() => setShowModal(true)}
            className="flex items-center space-x-2 px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>Link Broker to User</span>
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-900 border border-red-700 text-red-200 px-4 py-3 rounded-lg">
          <p className="font-medium">Error loading data</p>
          <p className="text-sm">{error}</p>
        </div>
      )}

      {/* Brokers Table */}
      {!loading && !error && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          <div className="px-6 py-4 bg-gray-750 border-b border-gray-700">
            <h3 className="text-lg font-semibold text-white">Available Brokers</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-750 border-b border-gray-700">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Broker ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Code
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Name
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-700">
                {brokers.length === 0 ? (
                  <tr>
                    <td colSpan="4" className="px-6 py-8 text-center text-gray-400">
                      No brokers found
                    </td>
                  </tr>
                ) : (
                  brokers.map(broker => (
                    <tr key={broker.brokerId} className="hover:bg-gray-750 transition-colors">
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-gray-300">
                        {broker.brokerId}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-purple-400 font-semibold">
                        {broker.brokerCode}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-white">
                        {broker.brokerName}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          broker.status === 'ACTIVE'
                            ? 'bg-green-900 text-green-200 border border-green-700'
                            : 'bg-gray-700 text-gray-300 border border-gray-600'
                        }`}>
                          {broker.status}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* User-Broker Combinations Table */}
      {!loading && !error && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
          <div className="px-6 py-4 bg-gray-750 border-b border-gray-700">
            <h3 className="text-lg font-semibold text-white">User-Broker Combinations</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-750 border-b border-gray-700">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    User-Broker ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    User ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Display Name
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Broker ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Role
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                    Connected
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
                {userBrokers.length === 0 ? (
                  <tr>
                    <td colSpan="8" className="px-6 py-8 text-center text-gray-400">
                      No user-broker combinations found. Create one using the "Link Broker to User" button.
                    </td>
                  </tr>
                ) : (
                  userBrokers.map(ub => (
                    <tr key={ub.userBrokerId} className="hover:bg-gray-750 transition-colors">
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-purple-400 font-semibold">
                        {ub.userBrokerId}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-gray-300">
                        {ub.userId}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-300">
                        {ub.displayName}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-gray-300">
                        {ub.brokerId}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          ub.role === 'DATA'
                            ? 'bg-blue-900 text-blue-200 border border-blue-700'
                            : 'bg-green-900 text-green-200 border border-green-700'
                        }`}>
                          {ub.role}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          ub.connected
                            ? 'bg-green-900 text-green-200 border border-green-700'
                            : 'bg-gray-700 text-gray-300 border border-gray-600'
                        }`}>
                          {ub.connected ? 'YES' : 'NO'}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          ub.status === 'ACTIVE'
                            ? 'bg-green-900 text-green-200 border border-green-700'
                            : 'bg-gray-700 text-gray-300 border border-gray-600'
                        }`}>
                          {ub.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-center">
                        <div className="flex items-center justify-center gap-2">
                          <button
                            onClick={() => handleToggleUserBroker(ub.userBrokerId)}
                            className={`p-1.5 rounded transition-colors ${
                              ub.enabled
                                ? 'text-green-400 hover:bg-green-900/30 hover:text-green-300'
                                : 'text-gray-500 hover:bg-gray-700 hover:text-gray-400'
                            }`}
                            title={ub.enabled ? 'Disable user-broker' : 'Enable user-broker'}
                          >
                            <Power className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDeleteUserBroker(ub.userBrokerId)}
                            className="p-1.5 text-red-400 hover:bg-red-900/30 hover:text-red-300 rounded transition-colors"
                            title="Delete user-broker"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          {userBrokers.length > 0 && (
            <div className="px-6 py-3 bg-gray-750 border-t border-gray-700">
              <p className="text-sm text-gray-400">
                Total: <span className="font-medium text-white">{userBrokers.length}</span> user-broker combination{userBrokers.length !== 1 ? 's' : ''}
              </p>
            </div>
          )}
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-75 flex items-center justify-center z-50">
          <div className="bg-gray-800 border border-gray-700 rounded-lg max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-700">
              <h3 className="text-xl font-bold text-white">Link Broker to User</h3>
              <button
                onClick={() => {
                  setShowModal(false);
                  setSubmitStatus(null);
                }}
                className="text-gray-400 hover:text-white transition-colors"
              >
                <X className="w-6 h-6" />
              </button>
            </div>

            {/* Modal Body */}
            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              {/* User Select */}
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Select User
                </label>
                <select
                  value={formData.userId}
                  onChange={(e) => setFormData({ ...formData, userId: e.target.value })}
                  required
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

              {/* Broker Select */}
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Select Broker
                </label>
                <select
                  value={formData.brokerId}
                  onChange={(e) => setFormData({ ...formData, brokerId: e.target.value })}
                  required
                  className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                >
                  <option value="">-- Select Broker --</option>
                  {brokers.filter(b => b.status === 'ACTIVE').map(broker => (
                    <option key={broker.brokerId} value={broker.brokerId}>
                      {broker.brokerName} ({broker.brokerCode})
                    </option>
                  ))}
                </select>
              </div>

              {/* Credentials JSON */}
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Credentials (JSON)
                </label>
                <textarea
                  value={formData.credentials}
                  onChange={(e) => setFormData({ ...formData, credentials: e.target.value })}
                  required
                  rows={6}
                  placeholder='{"apiKey": "xxx", "apiSecret": "yyy", "accessToken": "zzz"}'
                  className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
                />
                <p className="mt-1 text-xs text-gray-400">
                  Enter broker credentials as valid JSON
                </p>
              </div>

              {/* Is Data Broker Checkbox */}
              <div className="flex items-center">
                <input
                  type="checkbox"
                  id="isDataBroker"
                  checked={formData.isDataBroker}
                  onChange={(e) => setFormData({ ...formData, isDataBroker: e.target.checked })}
                  className="w-4 h-4 text-purple-600 bg-gray-700 border-gray-600 rounded focus:ring-purple-500 focus:ring-2"
                />
                <label htmlFor="isDataBroker" className="ml-2 text-sm text-gray-300">
                  Use as Data Broker (system-wide market data)
                </label>
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
              <div className="flex justify-end space-x-3 pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setShowModal(false);
                    setSubmitStatus(null);
                  }}
                  className="px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitStatus?.type === 'loading'}
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors disabled:opacity-50"
                >
                  Create User-Broker Link
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default BrokerManagement;
