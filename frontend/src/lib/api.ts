import axios from 'axios';
import keycloak, { getToken } from './keycloak';

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