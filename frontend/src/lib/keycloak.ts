import Keycloak from 'keycloak-js';

const KEYCLOAK_CONFIG = {
    url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
    realm: 'schoolfee',
    clientId: 'schoolfee-web',
};

const keycloak = new Keycloak(KEYCLOAK_CONFIG);

export default keycloak;

let initPromise: Promise<boolean> | null = null;

export const initKeycloak = (): Promise<boolean> => {
    if (initPromise) {
        return initPromise;
    }

    initPromise = (async () => {
        try {
            const authenticated = await withTimeout(
                keycloak.init({
                    onLoad: 'check-sso',
                    silentCheckSsoRedirectUri:
                        window.location.origin + '/silent-check-sso.html',
                    pkceMethod: 'S256',
                    checkLoginIframe: false,
                }),
                8000,
            );

            keycloak.onTokenExpired = () => {
                keycloak.updateToken(30).catch(() => {
                    console.warn('Token refresh failed, redirecting to login');
                    keycloak.login();
                });
            };
            // Also handle refresh errors gracefully
            keycloak.onAuthRefreshError = () => {
                console.warn('Auth refresh error, redirecting to login');
                keycloak.login();
            };
            return authenticated;
        } catch (error) {
            console.warn('Keycloak initialization failed:', error);
            return false;
        }
    })();

    return initPromise;
};

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
    return new Promise((resolve, reject) => {
        const timeoutId = window.setTimeout(() => {
            reject(new Error('Keycloak initialization timed out'));
        }, timeoutMs);

        promise
            .then(resolve)
            .catch(reject)
            .finally(() => window.clearTimeout(timeoutId));
    });
}

export const login = () => keycloak.login();
export const logout = () => keycloak.logout();
export const getToken = (): string | undefined => keycloak.token;
export const isAuthenticated = (): boolean => !!keycloak.authenticated;
export const hasRole = (role: string): boolean => keycloak.hasRealmRole(role);
