import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/components/auth/AuthProvider';

interface RouteGuardProps {
    children: React.ReactNode;
    requiredRoles?: string[];
    requiredUserTypes?: string[];
}

export const RouteGuard: React.FC<RouteGuardProps> = ({
                                                          children,
                                                          requiredRoles,
                                                          requiredUserTypes,
                                                      }) => {
    const { isAuthenticated, isLoading, user, hasRole } = useAuth();
    const location = useLocation();

    // Show loading while auth is initializing
    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
                    <p className="text-gray-500">Loading...</p>
                </div>
            </div>
        );
    }

    // Not authenticated — redirect to login
    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    // Check roles (from JWT, synced to DB on login)
    if (requiredRoles && requiredRoles.length > 0) {
        const hasRequiredRole = requiredRoles.some((role) => hasRole(role));
        if (!hasRequiredRole) {
            return <Navigate to="/unauthorized" replace />;
        }
    }

    // Check user types
    if (requiredUserTypes && requiredUserTypes.length > 0) {
        if (!user || !requiredUserTypes.includes(user.userType)) {
            return <Navigate to="/unauthorized" replace />;
        }
    }

    return <>{children}</>;
};

export default RouteGuard;
