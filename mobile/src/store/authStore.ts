import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { UserProfile } from '../types';
import ENV from '../config/environment';

interface AuthState {
  user: UserProfile | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setAuth: (user: UserProfile, token: string) => Promise<void>;
  setUser: (user: UserProfile) => void;
  logout: () => Promise<void>;
  initializeAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: true,

  setAuth: async (user, token) => {
    try {
      await SecureStore.setItemAsync(ENV.STORAGE_KEYS.ACCESS_TOKEN, token);
      await SecureStore.setItemAsync(ENV.STORAGE_KEYS.ACTIVE_USER, JSON.stringify(user));
    } catch {}
    set({ user, token, isAuthenticated: true, isLoading: false });
  },

  setUser: (user) => {
    set({ user });
  },

  logout: async () => {
    try {
      await SecureStore.deleteItemAsync(ENV.STORAGE_KEYS.ACCESS_TOKEN);
      await SecureStore.deleteItemAsync(ENV.STORAGE_KEYS.ACTIVE_USER);
      await SecureStore.deleteItemAsync(ENV.STORAGE_KEYS.ACTIVE_STUDENT_ID);
    } catch {}
    set({ user: null, token: null, isAuthenticated: false, isLoading: false });
  },

  initializeAuth: async () => {
    try {
      const token = await SecureStore.getItemAsync(ENV.STORAGE_KEYS.ACCESS_TOKEN);
      const userStr = await SecureStore.getItemAsync(ENV.STORAGE_KEYS.ACTIVE_USER);
      if (token && userStr) {
        const user = JSON.parse(userStr) as UserProfile;
        set({ user, token, isAuthenticated: true, isLoading: false });
        return;
      }
    } catch {}
    set({ user: null, token: null, isAuthenticated: false, isLoading: false });
  },
}));

export default useAuthStore;
