/**
 * OAuth Callback Page
 * Handles OAuth redirect from broker platforms
 */

import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Spinner } from '@/components/atoms/Spinner/Spinner';
import { Card } from '@/components/atoms/Card/Card';
import { Text } from '@/components/atoms/Text/Text';
import { Alert } from '@/components/atoms/Alert/Alert';
import { Button } from '@/components/atoms/Button/Button';

/**
 * OAuth callback component
 */
export function OAuthCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const processOAuthCallback = async () => {
      // Extract OAuth parameters
      const code = searchParams.get('code');
      const state = searchParams.get('state');
      const errorParam = searchParams.get('error');
      const errorDescription = searchParams.get('error_description');

      // Handle OAuth error
      if (errorParam) {
        setError(errorDescription || errorParam);
        return;
      }

      // Validate required parameters
      if (!code || !state) {
        setError('Invalid OAuth callback: missing code or state');
        return;
      }

      try {
        // In a real implementation, you would:
        // 1. Send the OAuth code to your backend
        // 2. Backend exchanges code for access token
        // 3. Backend creates/updates UserBrokerSession
        // 4. Return success status

        // For now, simulate the process
        await new Promise((resolve) => setTimeout(resolve, 2000));

        // Navigate to broker settings on success
        navigate('/settings/brokers', {
          state: { message: 'Broker connected successfully' },
        });
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to process OAuth callback');
      }
    };

    processOAuthCallback();
  }, [searchParams, navigate]);

  /**
   * Render error state
   */
  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background p-4">
        <div className="w-full max-w-md">
          <Card variant="outlined">
            <div className="p-6 space-y-6">
              <div className="text-center">
                <Text variant="h2" className="mb-2">
                  Connection Failed
                </Text>
                <Text variant="body" className="text-muted">
                  We couldn't connect your broker account
                </Text>
              </div>

              <Alert variant="error">{error}</Alert>

              <div className="flex gap-3">
                <Button variant="secondary" fullWidth onClick={() => navigate('/')}>
                  Go to Dashboard
                </Button>
                <Button variant="primary" fullWidth onClick={() => navigate('/settings/brokers')}>
                  Try Again
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  /**
   * Render processing state
   */
  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <div className="w-full max-w-md">
        <Card variant="outlined">
          <div className="p-8 text-center space-y-6">
            <Spinner size="lg" variant="primary" />

            <div>
              <Text variant="h2" className="mb-2">
                Connecting Broker
              </Text>
              <Text variant="body" className="text-muted">
                Please wait while we securely connect your broker account...
              </Text>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
}
