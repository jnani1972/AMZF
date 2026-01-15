/**
 * Register Page
 * New user registration
 */

import { useState, FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { Button } from '@/components/atoms/Button/Button';
import { Input } from '@/components/atoms/Input/Input';
import { Card } from '@/components/atoms/Card/Card';
import { Text } from '@/components/atoms/Text/Text';
import { Alert } from '@/components/atoms/Alert/Alert';
import { UserPlus } from 'lucide-react';

/**
 * Register page component
 */
export function Register() {
  const { register, loading } = useAuth();
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  /**
   * Handle form submission
   */
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validation
    if (!email || !displayName || !password || !confirmPassword) {
      setError('Please fill in all fields');
      return;
    }

    if (!email.includes('@')) {
      setError('Please enter a valid email address');
      return;
    }

    if (displayName.length < 2) {
      setError('Display name must be at least 2 characters');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    // Attempt registration
    const result = await register(email, password, displayName);

    if (!result.success) {
      setError(result.error || 'Registration failed. Please try again.');
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
            Create your account
          </Text>
        </div>

        {/* Registration Form */}
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

            {/* Display Name Field */}
            <div>
              <label htmlFor="displayName" className="block mb-2">
                <Text variant="label">Display Name</Text>
              </label>
              <Input
                id="displayName"
                type="text"
                placeholder="John Doe"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                state={error ? 'error' : 'default'}
                disabled={loading}
                fullWidth
                autoComplete="name"
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
                placeholder="At least 8 characters"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                state={error ? 'error' : 'default'}
                disabled={loading}
                fullWidth
                autoComplete="new-password"
                required
              />
            </div>

            {/* Confirm Password Field */}
            <div>
              <label htmlFor="confirmPassword" className="block mb-2">
                <Text variant="label">Confirm Password</Text>
              </label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="Re-enter your password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                state={error ? 'error' : 'default'}
                disabled={loading}
                fullWidth
                autoComplete="new-password"
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
              iconLeft={<UserPlus size={20} />}
            >
              Create Account
            </Button>

            {/* Login Link */}
            <div className="text-center">
              <Text variant="small" className="text-muted">
                Already have an account?{' '}
                <Link
                  to="/auth/login"
                  className="text-primary hover:underline font-medium"
                >
                  Sign in
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
