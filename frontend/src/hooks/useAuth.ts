import { useState, useEffect, useCallback } from 'react';
import keycloak, { initKeycloak, getToken } from '@/lib/keycloak';
import axios from 'axios';

// Match the new API response shape
interface ChildInfo {
  studentId: string;
  guardianId: string;
  relationship: string;
  canViewFees: boolean;
  canViewResults: boolean;
  canViewAttendance: boolean;
}

interface UserProfile {
  userId: string;
  keycloakId: string;
  email: string;
  phoneNumber: string;
  firstName: string;
  lastName: string;
  userType: 'SUPER_ADMIN' | 'SCHOOL_ADMIN' | 'ACCOUNTANT' | 'TEACHER' | 'PARENT';
  schoolId: string;
  schoolName: string;
  roles: string[];
  children: ChildInfo[];
  lastLogin: string;
  isActive: boolean;
}

interface AuthState {
  isLoading: boolean;
  isAuthenticated: boolean;
  user: UserProfile | null;
  error: string | null;
}

export function useAuth() {
  const [state, setState] = useState<AuthState>({
    isLoading: true,
    isAuthenticated: false,
    user: null,
    error: null,
  });

  // Initialize Keycloak on mount
  useEffect(() => {
    let mounted = true;

    initKeycloak()
        .then((authenticated) => {
          if (!mounted) return;

          if (authenticated) {
            fetchUserProfile();
          } else {
            setState(prev => ({
              ...prev,
              isLoading: false,
              isAuthenticated: false,
            }));
          }
        })
        .catch((error) => {
          if (!mounted) return;
          setState(prev => ({
            ...prev,
            isLoading: false,
            error: 'Failed to initialize authentication',
          }));
        });

    return () => { mounted = false; };
  }, []);

  const fetchUserProfile = async () => {
    try {
      const token = getToken();
      const response = await axios.get('/api/v1/auth/me', {
        headers: { Authorization: `Bearer ${token}` },
      });

      setState(prev => ({
        ...prev,
        isLoading: false,
        isAuthenticated: true,
        user: response.data.data,
        error: null,
      }));
    } catch (error) {
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: 'Failed to fetch user profile',
      }));
    }
  };

  const login = useCallback(() => keycloak.login(), []);
  const logout = useCallback(() => keycloak.logout(), []);
  const hasRole = useCallback((role: string) => keycloak.hasRealmRole(role), []);

  return {
    ...state,
    login,
    logout,
    hasRole,
    isParent: state.user?.userType === 'PARENT',
    isSchoolAdmin: state.user?.userType === 'SCHOOL_ADMIN',
    isAccountant: state.user?.userType === 'ACCOUNTANT',
    isTeacher: state.user?.userType === 'TEACHER',
    isSuperAdmin: state.user?.userType === 'SUPER_ADMIN',
  };
}