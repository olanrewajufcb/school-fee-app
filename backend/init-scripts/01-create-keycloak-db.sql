-- Create Keycloak database and user
CREATE USER keycloak_user WITH PASSWORD 'keycloak_password';
CREATE DATABASE sch_keycloak OWNER keycloak_user;
GRANT ALL PRIVILEGES ON DATABASE sch_keycloak TO keycloak_user;