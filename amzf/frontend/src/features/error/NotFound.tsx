/**
 * 404 Not Found Page
 */

import { useNavigate } from 'react-router-dom';
import { Card } from '../../components/atoms/Card/Card';
import { Text } from '../../components/atoms/Text/Text';
import { Button } from '../../components/atoms/Button/Button';
import { FileQuestion, Home, ArrowLeft } from 'lucide-react';

/**
 * Not found component
 */
export function NotFound() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <div className="form-container">
        <Card variant="outlined">
          <div className="p-8 text-center space-y-6">
            {/* Icon */}
            <div className="flex justify-center">
              <FileQuestion size={64} className="text-muted" />
            </div>

            {/* Error Code */}
            <div>
              <Text variant="h1" className="text-6xl font-bold text-primary mb-2">
                404
              </Text>
              <Text variant="h2" className="mb-2">
                Page Not Found
              </Text>
              <Text variant="body" className="text-muted">
                The page you're looking for doesn't exist or has been moved.
              </Text>
            </div>

            {/* Actions */}
            <div className="flex flex-col gap-3">
              <Button
                variant="primary"
                fullWidth
                onClick={() => navigate('/')}
                iconLeft={<Home size={20} />}
              >
                Go to Dashboard
              </Button>
              <Button
                variant="secondary"
                fullWidth
                onClick={() => navigate(-1)}
                iconLeft={<ArrowLeft size={20} />}
              >
                Go Back
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
}
