import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import keycloak, { initKeycloak, login, logout, hasRole } from '@/lib/keycloak';
import api from '@/lib/api';
import type { UserProfile, AuthContextType } from '@/types/auth';

interface ApiResponse<T> {
    success: boolean;
    data: T;
}

const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [isLoading, setIsLoading] = useState(true);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [user, setUser] = useState<UserProfile | null>(null);
    const [error, setError] = useState<string | null>(null);

    // Initialize Keycloak on mount
    useEffect(() => {
        let mounted = true;

        initKeycloak()
            .then((authenticated) => {
                if (!mounted) return;

                setIsAuthenticated(authenticated);

                if (authenticated) {
                    fetchUserProfile();
                } else {
                    setIsLoading(false);
                }
            })
            .catch((err) => {
                if (!mounted) return;
                console.error('Auth initialization failed:', err);
                setError('Failed to initialize authentication');
                setIsLoading(false);
            });

        // Listen for Keycloak events
        keycloak.onAuthSuccess = () => {
            if (mounted) {
                setIsAuthenticated(true);
                fetchUserProfile();
            }
        };

        keycloak.onAuthError = () => {
            if (mounted) {
                setIsAuthenticated(false);
                setUser(null);
                setError('Authentication error');
                setIsLoading(false);
            }
        };

        keycloak.onAuthRefreshSuccess = () => {
            if (mounted && !user) {
                fetchUserProfile();
            }
        };

        return () => {
            mounted = false;
        };
    }, []);

    // Fetch user profile from backend
    const fetchUserProfile = async () => {
        try {
            setIsLoading(true);
            const response = await api.get<ApiResponse<UserProfile>>('/api/v1/auth/me');
            setUser(response.data.data);
            setError(null);
        } catch (err) {
            console.error('Failed to fetch user profile:', err);
            setError('Failed to load user profile');
        } finally {
            setIsLoading(false);
        }
    };

    // Role checking using Keycloak (JWT authoritative)
    const checkRole = useCallback((role: string): boolean => {
        return hasRole(role);
    }, []);

    const value: AuthContextType = {
        isLoading,
        isAuthenticated,
        user,
        error,
        login,
        logout,
        hasRole: checkRole,
        isParent: user?.userType === 'PARENT',
        isSchoolAdmin: user?.userType === 'SCHOOL_ADMIN',
        isAccountant: user?.userType === 'ACCOUNTANT',
        isTeacher: user?.userType === 'TEACHER',
        isSuperAdmin: user?.userType === 'SUPER_ADMIN',
    };

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};

// Hook to use auth context
export const useAuth = (): AuthContextType => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};

export default AuthProvider;
