import React from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '@/components/auth/AuthProvider';
import { ParentOnboardingFlow } from '@/components/auth/ParentOnboardingFlow';
import { School } from 'lucide-react';

export const ParentJoinPage: React.FC = () => {
  const { isAuthenticated, isLoading, login } = useAuth();
  const navigate = useNavigate();

  // Already authenticated — redirect to dashboard
  if (isAuthenticated && !isLoading) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md text-center mb-8 animate-in fade-in slide-in-from-top-4">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-100 shadow-sm border border-blue-200">
          <School className="h-8 w-8 text-blue-600" />
        </div>
        <h1 className="text-3xl font-bold text-gray-900 tracking-tight">SchoolFee</h1>
      </div>

      <div className="w-full max-w-md">
        <ParentOnboardingFlow 
          onComplete={() => login()} 
          onGoToSignIn={() => navigate('/login')} 
        />
      </div>

      <div className="mt-8 text-center text-sm text-gray-500 max-w-sm">
        <p>By continuing, you agree to our Terms of Service and Privacy Policy.</p>
      </div>
    </div>
  );
};

export default ParentJoinPage;
