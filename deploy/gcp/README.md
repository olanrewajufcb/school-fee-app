# Google Cloud Deployment Assets

This directory contains configuration files and automation scripts for deploying the platform to Google Cloud Platform (GCP).

Refer to [`DEPLOYMENT_GUIDE.md`](./DEPLOYMENT_GUIDE.md) for the end-to-end setup and operations guide.

---

## Directory Contents

| File / Directory | Purpose |
| :--- | :--- |
| `DEPLOYMENT_GUIDE.md` | Complete runbook for initial GCP setup (VPC, Cloud SQL, IAM, Secrets). |
| `cloud-run-backend.yaml` | Declarative Cloud Run service manifest for Spring Boot API. |
| `cloudbuild-backend.yaml` | Cloud Build config to compile & push backend container to Artifact Registry. |
| `test.env` | Environment configuration for testing (`edtech-project-510010`). |
| `prod.env` | Environment configuration for production. |
| `keycloak/` | Dockerfile and Cloud Run manifest (`cloud-run-keycloak.yml`) for Keycloak. |
| `../deploy-backend.sh` | Automated deployment script for backend Cloud Run service. |
| `../deploy-keycloak.sh` | Automated deployment script for Keycloak Cloud Run service. |

---

## Automated Deployment Scripts

### Backend API
The backend deployment is automated by GitHub Actions (`.github/workflows/commit-stage.yml`) on branch push.

To trigger deployment manually from your local terminal:
```bash
./deploy/deploy-backend.sh test
```

### Keycloak Identity Provider
Keycloak deployment is automated via:
```bash
# 1. Build image once
gcloud builds submit deploy/gcp/keycloak \
  --tag="europe-west1-docker.pkg.dev/edtech-project-510010/edtech/keycloak:26.6.4"

# 2. Deploy service
./deploy/deploy-keycloak.sh test
```