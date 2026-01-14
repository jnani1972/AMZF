import React, { useState, useEffect } from 'react';
import { Database, Check, X, RefreshCw, AlertCircle } from 'lucide-react';

const DataBrokerConfig = ({ token }) => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [testing, setTesting] = useState(false);
  const [dataBroker, setDataBroker] = useState(null);
  const [session, setSession] = useState(null);
  const [brokers, setBrokers] = useState([]);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState('');
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [profileData, setProfileData] = useState(null);

  // Form state
  const [selectedBrokerId, setSelectedBrokerId] = useState('');
  const [credentials, setCredentials] = useState('{}');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      // Load available brokers
      const brokersRes = await fetch('http://localhost:9090/api/admin/brokers', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (!brokersRes.ok) throw new Error('Failed to load brokers');
      const brokersData = await brokersRes.json();
      setBrokers(brokersData);

      // Load current data broker configuration
      const dataBrokerRes = await fetch('http://localhost:9090/api/admin/data-broker', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (dataBrokerRes.ok) {
        const dataBrokerData = await dataBrokerRes.json();
        setDataBroker(dataBrokerData);
        if (dataBrokerData) {
          setSelectedBrokerId(dataBrokerData.brokerId);
          // Don't show credentials for security
          setCredentials('{"apiKey":"***","apiSecret":"***"}');

          // Load session information
          const sessionRes = await fetch(`http://localhost:9090/api/admin/brokers/${dataBrokerData.userBrokerId}/session`, {
            headers: { 'Authorization': `Bearer ${token}` }
          });
          if (sessionRes.ok) {
            const sessionData = await sessionRes.json();
            setSession(sessionData);
          }
        }
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleConnectOAuth = async () => {
    if (!dataBroker || !dataBroker.userBrokerId) {
      setError('Please configure data broker first (save credentials)');
      return;
    }

    setConnecting(true);
    setError(null);

    try {
      // Get OAuth URL from backend
      const response = await fetch(`http://localhost:9090/api/admin/brokers/${dataBroker.userBrokerId}/oauth-url`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to generate OAuth URL');
      }

      const data = await response.json();

      // Store token in sessionStorage for callback
      sessionStorage.setItem('authToken', token);
      sessionStorage.setItem('userBrokerId', dataBroker.userBrokerId);

      // Redirect to Fyers OAuth
      window.location.href = data.oauthUrl;
    } catch (err) {
      setError(err.message);
      setConnecting(false);
    }
  };

  const handleDisconnect = async () => {
    if (!dataBroker || !dataBroker.userBrokerId) return;

    if (!confirm('Are you sure you want to disconnect the broker? This will revoke the current session.')) {
      return;
    }

    try {
      const response = await fetch(`http://localhost:9090/api/admin/brokers/${dataBroker.userBrokerId}/disconnect`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to disconnect');
      }

      setSuccessMessage('Broker disconnected successfully');
      loadData(); // Reload to show updated status
    } catch (err) {
      setError(err.message);
    }
  };

  const handleSave = async () => {
    setError(null);
    setSuccessMessage('');

    // Validate inputs
    if (!selectedBrokerId) {
      setError('Please select a broker');
      return;
    }

    let credentialsObj;
    try {
      credentialsObj = JSON.parse(credentials);
    } catch (e) {
      setError('Invalid JSON format for credentials');
      return;
    }

    setSaving(true);
    try {
      const response = await fetch('http://localhost:9090/api/admin/data-broker', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          brokerId: selectedBrokerId,
          credentials: credentialsObj
        })
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to configure data broker');
      }

      const result = await response.json();
      setSuccessMessage('Data broker configured successfully!');
      loadData(); // Reload to show updated status
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleTestConnection = async () => {
    if (!dataBroker || !dataBroker.userBrokerId) {
      setError('Please configure data broker first (save credentials)');
      return;
    }

    setTesting(true);
    setError(null);
    setSuccessMessage('');

    try {
      const response = await fetch(`http://localhost:9090/api/admin/brokers/${dataBroker.userBrokerId}/test-connection`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Connection test failed');
      }

      const data = await response.json();

      if (data.success) {
        setProfileData(data.profile);
        setShowProfileModal(true);
      } else {
        throw new Error(data.error || 'Connection test failed');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setTesting(false);
    }
  };

  const handleSaveConnection = async () => {
    if (!dataBroker || !dataBroker.userBrokerId || !profileData) {
      return;
    }

    try {
      const response = await fetch(`http://localhost:9090/api/admin/brokers/${dataBroker.userBrokerId}/save-connection`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          profile: profileData
        })
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to save connection');
      }

      setSuccessMessage('Connection saved successfully!');
      setShowProfileModal(false);
      loadData(); // Reload to show updated status
    } catch (err) {
      setError(err.message);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <RefreshCw className="w-8 h-8 text-purple-500 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6 space-y-6">
      {/* Header */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
        <div className="flex items-center space-x-3 mb-2">
          <Database className="w-6 h-6 text-purple-500" />
          <h2 className="text-xl font-bold text-white">System Data Broker</h2>
        </div>
        <p className="text-sm text-gray-400">
          Configure the system-wide broker for market data (ticks, candles, quotes). Only ONE data broker should be active.
        </p>
      </div>

      {/* Current Status */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-white mb-4">Current Status</h3>
        {dataBroker ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-gray-400">Broker:</span>
              <span className="text-white font-medium">{dataBroker.brokerName || dataBroker.brokerId}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-400">Connection Status:</span>
              <span className={`flex items-center space-x-2 ${dataBroker.connected ? 'text-green-500' : 'text-red-500'}`}>
                {dataBroker.connected ? (
                  <><Check className="w-4 h-4" /> <span>Connected</span></>
                ) : (
                  <><X className="w-4 h-4" /> <span>Disconnected</span></>
                )}
              </span>
            </div>

            {/* Session Status */}
            {session && session.hasSession && (
              <>
                <div className="flex items-center justify-between">
                  <span className="text-gray-400">Session Status:</span>
                  <span className={`${session.isActive ? 'text-green-500' : 'text-yellow-500'}`}>
                    {session.status}
                  </span>
                </div>
                {session.validTill && (
                  <div className="flex items-center justify-between">
                    <span className="text-gray-400">Token Valid Till:</span>
                    <span className="text-white text-sm">{new Date(session.validTill).toLocaleString()}</span>
                  </div>
                )}
              </>
            )}

            {dataBroker.lastConnected && (
              <div className="flex items-center justify-between">
                <span className="text-gray-400">Last Connected:</span>
                <span className="text-white">{new Date(dataBroker.lastConnected).toLocaleString()}</span>
              </div>
            )}

            {/* Connect/Disconnect Buttons */}
            <div className="flex items-center space-x-3 pt-3 border-t border-gray-700">
              {!session || !session.isActive ? (
                <>
                  <button
                    onClick={handleConnectOAuth}
                    disabled={connecting}
                    className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
                  >
                    <Check className="w-4 h-4" />
                    <span>{connecting ? 'Connecting...' : 'Connect to Broker (OAuth)'}</span>
                  </button>
                  <button
                    onClick={handleTestConnection}
                    disabled={testing}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
                  >
                    <RefreshCw className={`w-4 h-4 ${testing ? 'animate-spin' : ''}`} />
                    <span>{testing ? 'Testing...' : 'Test Connection'}</span>
                  </button>
                </>
              ) : (
                <>
                  <button
                    onClick={handleDisconnect}
                    className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors flex items-center space-x-2"
                  >
                    <X className="w-4 h-4" />
                    <span>Disconnect</span>
                  </button>
                  <button
                    onClick={handleTestConnection}
                    disabled={testing}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
                  >
                    <RefreshCw className={`w-4 h-4 ${testing ? 'animate-spin' : ''}`} />
                    <span>{testing ? 'Testing...' : 'Test Connection'}</span>
                  </button>
                </>
              )}
              <p className="text-xs text-gray-400">
                {!session || !session.isActive
                  ? 'Click to authorize with Fyers and get access token'
                  : 'Broker is connected via OAuth'}
              </p>
            </div>

            {dataBroker.connectionError && (
              <div className="flex items-start space-x-2 mt-2 p-3 bg-red-900/20 border border-red-800 rounded">
                <AlertCircle className="w-5 h-5 text-red-500 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-red-400">Connection Error</p>
                  <p className="text-sm text-red-300">{dataBroker.connectionError}</p>
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="flex items-center space-x-2 text-yellow-500">
            <AlertCircle className="w-5 h-5" />
            <span>No data broker configured. System cannot generate signals or fetch market data.</span>
          </div>
        )}
      </div>

      {/* Configuration Form */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-white mb-4">
          {dataBroker ? 'Update Data Broker' : 'Configure Data Broker'}
        </h3>

        {error && (
          <div className="mb-4 p-3 bg-red-900/20 border border-red-800 rounded flex items-start space-x-2">
            <AlertCircle className="w-5 h-5 text-red-500 mt-0.5" />
            <p className="text-sm text-red-400">{error}</p>
          </div>
        )}

        {successMessage && (
          <div className="mb-4 p-3 bg-green-900/20 border border-green-800 rounded flex items-start space-x-2">
            <Check className="w-5 h-5 text-green-500 mt-0.5" />
            <p className="text-sm text-green-400">{successMessage}</p>
          </div>
        )}

        <div className="space-y-4">
          {/* Broker Selection */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Select Broker <span className="text-red-500">*</span>
            </label>
            <select
              value={selectedBrokerId}
              onChange={(e) => setSelectedBrokerId(e.target.value)}
              className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
            >
              <option value="">-- Select Broker --</option>
              {brokers.map(broker => (
                <option key={broker.brokerId} value={broker.brokerId}>
                  {broker.brokerName} ({broker.brokerCode})
                </option>
              ))}
            </select>
            <p className="mt-1 text-xs text-gray-400">
              Choose the broker for system-wide market data feed
            </p>
          </div>

          {/* Credentials */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">
              Credentials (JSON) <span className="text-red-500">*</span>
            </label>
            <textarea
              value={credentials}
              onChange={(e) => setCredentials(e.target.value)}
              placeholder='{"apiKey":"your_key","apiSecret":"your_secret","accessToken":"optional"}'
              rows={6}
              className="w-full bg-gray-700 border border-gray-600 text-white rounded-lg px-4 py-2 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
            />
            <p className="mt-1 text-xs text-gray-400">
              Enter broker credentials as valid JSON. Required fields depend on the broker.
            </p>
          </div>

          {/* Save Button */}
          <div className="flex justify-end space-x-3 pt-4">
            <button
              onClick={loadData}
              className="px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
            >
              <RefreshCw className="w-4 h-4 inline mr-2" />
              Refresh
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="px-6 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {saving ? 'Saving...' : (dataBroker ? 'Update Data Broker' : 'Configure Data Broker')}
            </button>
          </div>
        </div>
      </div>

      {/* Info Box */}
      <div className="bg-blue-900/20 border border-blue-800 rounded-lg p-4">
        <h4 className="text-sm font-semibold text-blue-400 mb-2">About Data Broker</h4>
        <ul className="text-sm text-blue-300 space-y-1 list-disc list-inside">
          <li>The data broker provides market data (ticks, candles, quotes) to all users</li>
          <li>Only ONE data broker should be active system-wide</li>
          <li>User brokers (for trade execution) are configured separately per user</li>
          <li>The data broker must stay connected for signal generation to work</li>
        </ul>
      </div>

      {/* Profile Modal */}
      {showProfileModal && profileData && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 max-w-2xl w-full mx-4">
            <h3 className="text-xl font-semibold text-white mb-4">Broker Profile</h3>

            <div className="bg-gray-900 rounded-lg p-4 mb-6 overflow-x-auto">
              <pre className="text-sm text-gray-300 max-h-96 whitespace-pre-wrap" style={{wordBreak: 'break-all'}}>
                {JSON.stringify(profileData, null, 2)}
              </pre>
            </div>

            <div className="flex items-center justify-end space-x-3">
              <button
                onClick={() => setShowProfileModal(false)}
                className="px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveConnection}
                className="px-6 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors flex items-center space-x-2"
              >
                <Check className="w-4 h-4" />
                <span>Save Connection</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DataBrokerConfig;
