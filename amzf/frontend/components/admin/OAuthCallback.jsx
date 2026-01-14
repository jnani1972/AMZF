import React, { useEffect, useState } from 'react';
import { CheckCircle, XCircle, RefreshCw } from 'lucide-react';

const OAuthCallback = () => {
  const [status, setStatus] = useState('processing'); // processing, success, error
  const [message, setMessage] = useState('Processing FYERS OAuth callback...');
  const [details, setDetails] = useState(null);

  useEffect(() => {
    handleCallback();
  }, []);

  const handleCallback = async () => {
    try {
      // Get query parameters from FYERS redirect
      const params = new URLSearchParams(window.location.search);
      const authCode = params.get('auth_code');
      const state = params.get('state');

      // Validate required parameters
      if (!authCode) {
        throw new Error('Missing auth_code in URL. FYERS did not return an authorization code.');
      }

      if (!state) {
        throw new Error('Missing state parameter in URL. Invalid OAuth callback.');
      }

      setMessage('Exchanging authorization code for access token...');

      // Call NEW backend endpoint to exchange token
      const response = await fetch('/api/fyers/oauth/exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ authCode, state })
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.error || `Token exchange failed (HTTP ${response.status})`);
      }

      const data = await response.json();

      if (!data.ok) {
        throw new Error(data.message || 'Token exchange failed');
      }

      // Success!
      setStatus('success');
      setDetails({
        userBrokerId: data.userBrokerId,
        sessionId: data.sessionId,
        alreadyDone: data.alreadyDone
      });

      if (data.alreadyDone) {
        setMessage('✅ Login already completed (safe refresh). You can close this tab.');
      } else {
        setMessage('✅ Login successful! FYERS connection established. You can close this tab.');
      }

      // Optional: Redirect to dashboard after 2 seconds
      setTimeout(() => {
        window.location.href = '/';
      }, 2000);

    } catch (error) {
      setStatus('error');
      setMessage(error.message);
    }
  };

  const retryLogin = () => {
    setStatus('processing');
    setMessage('Retrying OAuth callback...');
    handleCallback();
  };

  return (
    <div className="min-h-screen bg-gray-900 flex items-center justify-center p-6">
      <div className="max-w-md w-full bg-gray-800 border border-gray-700 rounded-lg p-8 text-center">
        {status === 'processing' && (
          <>
            <RefreshCw className="w-16 h-16 text-purple-500 mx-auto mb-4 animate-spin" />
            <h2 className="text-2xl font-bold text-white mb-2">Connecting to FYERS</h2>
            <p className="text-gray-400">{message}</p>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-white mb-2">
              {details?.alreadyDone ? 'Already Connected!' : 'Connection Successful!'}
            </h2>
            <p className="text-gray-400 mb-4">{message}</p>

            {details && (
              <div className="text-left bg-gray-900 border border-gray-700 rounded p-4 mb-4">
                <div className="text-xs text-gray-500 space-y-1">
                  <div><span className="font-semibold">Broker ID:</span> {details.userBrokerId}</div>
                  <div><span className="font-semibold">Session:</span> {details.sessionId}</div>
                  {details.alreadyDone && (
                    <div className="text-yellow-500 mt-2">
                      ⚠️ Idempotent: Token was already exchanged
                    </div>
                  )}
                </div>
              </div>
            )}

            <p className="text-sm text-gray-500">Redirecting to dashboard...</p>
          </>
        )}

        {status === 'error' && (
          <>
            <XCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-white mb-2">Connection Failed</h2>
            <p className="text-red-400 mb-4">{message}</p>

            <div className="space-y-3">
              <button
                onClick={retryLogin}
                className="w-full px-6 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors"
              >
                Retry
              </button>

              <button
                onClick={() => window.location.href = '/'}
                className="w-full px-6 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
              >
                Return to Dashboard
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default OAuthCallback;
