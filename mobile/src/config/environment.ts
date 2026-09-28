import Constants from 'expo-constants';

const extra = Constants.expoConfig?.extra || {};

export const ENV = {
  API_URL: extra.apiUrl || 'http://localhost:8080',
  KEYCLOAK_URL: extra.keycloakUrl || 'http://localhost:8081',
  REALM: 'schoolfee',
  CLIENT_ID: 'schoolfee-web',
  TIMEOUT_MS: 15000,
  APP_SCHEME: 'schoolfee',
  STORAGE_KEYS: {
    ACCESS_TOKEN: 'school_fee_access_token',
    REFRESH_TOKEN: 'school_fee_refresh_token',
    ACTIVE_USER: 'school_fee_active_user',
    ACTIVE_STUDENT_ID: 'school_fee_active_student_id',
  },
};

export default ENV;

