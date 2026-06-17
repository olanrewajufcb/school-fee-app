import React from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { School, LogIn, Users } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const { isAuthenticated, isLoading, login, user, error } = useAuth();
  const navigate = useNavigate();

  // Already authenticated — redirect to correct dashboard
  if (isAuthenticated && !isLoading) {
    if (user?.userType === 'SUPER_ADMIN') {
      return <Navigate to="/super-admin" replace />;
    } else if (user?.userType === 'SCHOOL_ADMIN') {
      return <Navigate to="/admin" replace />;
    } else if (user?.userType === 'ACCOUNTANT') {
      return <Navigate to="/accountant/dashboard" replace />;
    } else if (user?.userType === 'TEACHER') {
      return <Navigate to="/teacher/dashboard" replace />;
    } else if (user?.userType === 'PARENT') {
      return <Navigate to="/dashboard" replace />;
    }
    
    // If user is null, it means fetching profile failed. We shouldn't blindly redirect.
    if (!user) {
        return (
            <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-4 text-center">
                <h2 className="text-2xl font-bold text-red-600 mb-2">Profile Error</h2>
                <p className="text-gray-600 mb-4">{error || "Failed to load your profile. Please contact support."}</p>
                <Button onClick={() => window.location.reload()}>Try Again</Button>
            </div>
        );
    }
  }

  return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
        <Card className="w-full max-w-md shadow-lg border-0 bg-white/80 backdrop-blur-sm">
          <CardHeader className="text-center space-y-1">
            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-100">
              <School className="h-8 w-8 text-blue-600" />
            </div>
            <CardTitle className="text-2xl font-bold tracking-tight">SchoolFee</CardTitle>
            <CardDescription>
              Pay school fees easily. Access results and attendance.
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-6 mt-4">
            <div className="space-y-4">
              <Button
                  onClick={login}
                  className="w-full py-6 text-lg"
                  disabled={isLoading}
              >
                <LogIn className="mr-2 h-5 w-5" />
                {isLoading ? 'Loading...' : 'Staff & Existing Users Sign In'}
              </Button>
              
              <div className="relative">
                <div className="absolute inset-0 flex items-center">
                  <span className="w-full border-t border-gray-200" />
                </div>
                <div className="relative flex justify-center text-xs uppercase">
                  <span className="bg-white px-2 text-gray-500">Or</span>
                </div>
              </div>

              <Button
                  variant="outline"
                  onClick={() => navigate('/join')}
                  className="w-full py-6 text-lg border-2"
                  disabled={isLoading}
              >
                <Users className="mr-2 h-5 w-5" />
                New Parent? Setup Account
              </Button>
            </div>

            <p className="text-xs text-center text-gray-500 pt-4">
              By continuing, you agree to our Terms of Service and Privacy Policy.
            </p>
          </CardContent>
        </Card>
      </div>
  );
};

export default LoginPage;
