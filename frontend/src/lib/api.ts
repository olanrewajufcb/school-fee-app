import axios from 'axios';
import keycloak, { getToken } from './keycloak';
import { useSchoolStore } from '@/store/schoolStore';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
    headers: {
        'Content-Type': 'application/json',
    },
});

// Attach token to every request
api.interceptors.request.use(
    async (config) => {
        if (keycloak.isTokenExpired(30)) {
            try {
                await keycloak.updateToken(30);
            } catch {
                keycloak.login();
                return Promise.reject('Token refresh failed');
            }
        }

        const token = getToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        // Auth profile bootstrap must be tenant-neutral. A stale selected school
        // from localStorage must never influence identity creation/sync.
        const schoolId = useSchoolStore.getState().selectedSchoolId;
        const requestUrl = config.url ?? '';
        const isAuthProfileRequest = requestUrl.includes('/api/v1/auth/me') || requestUrl.endsWith('/auth/me');
        if (schoolId && !isAuthProfileRequest) {
            config.headers['X-School-ID'] = schoolId;
        }

        return config;
    },
    (error) => Promise.reject(error)
);

// Handle 401 responses
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        if (error.response?.status === 401) {
            keycloak.logout();
        }
        return Promise.reject(error);
    }
);

export default api;
