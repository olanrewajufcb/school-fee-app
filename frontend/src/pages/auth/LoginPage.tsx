import React from 'react';
import { useAuth } from '@/components/auth/AuthProvider.tsx';
import { Navigate } from 'react-router-dom';
import { Button } from '@/components/ui/button.tsx';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card.tsx';
import { School, LogIn } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const { isAuthenticated, isLoading, login } = useAuth();

  // Already authenticated — redirect to dashboard
  if (isAuthenticated && !isLoading) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-100">
              <School className="h-8 w-8 text-blue-600" />
            </div>
            <CardTitle className="text-2xl">SchoolFee</CardTitle>
            <CardDescription>
              Pay school fees easily. Access results and attendance.
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            <Button
                onClick={login}
                className="w-full py-6 text-lg"
                disabled={isLoading}
            >
              <LogIn className="mr-2 h-5 w-5" />
              {isLoading ? 'Loading...' : 'Sign In'}
            </Button>

            <p className="text-xs text-center text-gray-500">
              By signing in, you agree to our Terms of Service and Privacy Policy.
            </p>
          </CardContent>
        </Card>
      </div>
  );
};

export default LoginPage;