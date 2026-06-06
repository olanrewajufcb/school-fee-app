import Keycloak from 'keycloak-js';

const KEYCLOAK_CONFIG = {
    url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
    realm: 'schoolfee',
    clientId: 'schoolfee-web',
};

const keycloak = new Keycloak(KEYCLOAK_CONFIG);

export default keycloak;

export const initKeycloak = async (): Promise<boolean> => {
    try {
        const authenticated = await keycloak.init({
            onLoad: 'check-sso',
            silentCheckSsoRedirectUri:
                window.location.origin + '/silent-check-sso.html',
            pkceMethod: 'S256',
            checkLoginIframe: false,
        });

        keycloak.onTokenExpired = () => {
            keycloak.updateToken(30).catch(() => {
                console.warn('Token refresh failed, redirecting to login');
                keycloak.login();
            });
        };

        return authenticated;
    } catch (error) {
        console.error('Keycloak initialization failed:', error);
        return false;
    }
};

export const login = () => keycloak.login();
export const logout = () => keycloak.logout();
export const getToken = (): string | undefined => keycloak.token;
export const isAuthenticated = (): boolean => !!keycloak.authenticated;
export const hasRole = (role: string): boolean => keycloak.hasRealmRole(role);