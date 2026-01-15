/**
 * Login Page
 * Email/password authentication
 */

import { useState, FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { Button } from '@/components/atoms/Button/Button';
import { Input } from '@/components/atoms/Input/Input';
import { Card } from '@/components/atoms/Card/Card';
import { Text } from '@/components/atoms/Text/Text';
import { Alert } from '@/components/atoms/Alert/Alert';
import { LogIn } from 'lucide-react';

/**
 * Login page component
 */
export function Login() {
  const { login, loading } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  /**
   * Handle form submission
   */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    // Basic validation
    if (!email || !password) {
      setError('Please enter both email and password');
      return;
    }

    if (!email.includes('@')) {
      setError('Please enter a valid email address');
      return;
    }

    // Attempt login
    const result = await login(email, password);

    if (!result.success) {
      setError(result.error || 'Login failed. Please try again.');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <div className="w-full max-w-md">
        {/* Logo/Header */}
        <div className="text-center mb-8">
          <Text variant="h1" className="text-primary mb-2">
            AMZF Trading
          </Text>
          <Text variant="body" className="text-muted">
            Sign in to your account
          </Text>
        </div>

        {/* Login Form */}
        <Card variant="outlined">
          <form onSubmit={handleSubmit} className="space-y-6 p-6">
            {/* Error Alert */}
            {error && (
              <Alert variant="error" onDismiss={() => setError(null)}>
                {error}
              </Alert>
            )}

            {/* Email Field */}
            <div>
              <label htmlFor="email" className="block mb-2">
                <Text variant="label">Email</Text>
              </label>
              <Input
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                state={error ? 'error' : 'default'}
                disabled={loading}
                fullWidth
                autoComplete="email"
                required
              />
            </div>

            {/* Password Field */}
            <div>
              <label htmlFor="password" className="block mb-2">
                <Text variant="label">Password</Text>
              </label>
              <Input
                id="password"
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                state={error ? 'error' : 'default'}
                disabled={loading}
                fullWidth
                autoComplete="current-password"
                required
              />
            </div>

            {/* Submit Button */}
            <Button
              type="submit"
              variant="primary"
              size="lg"
              fullWidth
              loading={loading}
              iconLeft={<LogIn size={20} />}
            >
              Sign In
            </Button>

            {/* Register Link */}
            <div className="text-center">
              <Text variant="small" className="text-muted">
                Don't have an account?{' '}
                <Link
                  to="/auth/register"
                  className="text-primary hover:underline font-medium"
                >
                  Sign up
                </Link>
              </Text>
            </div>
          </form>
        </Card>

        {/* Footer */}
        <div className="text-center mt-6">
          <Text variant="caption" className="text-muted">
            © 2024 AMZF Trading Platform
          </Text>
        </div>
      </div>
    </div>
  );
}
