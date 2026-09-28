# EdTech Platform — Google Cloud Deployment Guide

This guide details the deployment of the EdTech platform to Google Cloud Platform (GCP) for project **`edtech-project-510010`** (Test) and production.

It covers:
- **Spring Boot API** on Cloud Run (automated via CI/CD)
- **Keycloak Identity Provider** on Cloud Run (automated via `deploy-keycloak.sh`)
- **PostgreSQL 16** on Cloud SQL using Direct VPC Private IP
- **Outbox Processor** on Cloud Run Jobs triggered by Cloud Scheduler
- **Secret Manager** for credential and key management
- **React Frontend** on Vercel (`test.smartbridgeedu.com` / `smartbridgeedu.com`)
- **Paystack Webhook & Payment Integration**

---

## 1. Architecture & Network Overview

```text
Vercel Frontend (test.smartbridgeedu.com)
       │ HTTPS
       ▼
Cloud Run API (api-test.smartbridgeedu.com) ──► Keycloak on Cloud Run (auth-test.smartbridgeedu.com)
       │                                              │
       │ Private IP (Direct VPC)                      │ Private IP (Direct VPC)
       └──────────────────► Cloud SQL (edtech) ◄──────┘
                                  ▲
                                  │ Private IP
       Cloud Scheduler ──► Cloud Run Outbox Job
```

- **Cloud SQL**: Isolated with private IP only (no public IP).
- **Direct VPC Egress**: Cloud Run connects directly to Cloud SQL via VPC subnet without requiring Cloud SQL Auth Proxy sidecars.
- **Automated Deployments**:
  - **Backend**: Automated via GitHub Actions (`.github/workflows/commit-stage.yml`) and `./deploy/deploy-backend.sh`.
  - **Keycloak**: Automated via `./deploy/deploy-keycloak.sh`.

---

## 2. Environment Variables & CLI Setup

Run all commands from the repository root:
```bash
cd ~/IdeaProjects/school-fee-app
```

Load deployment environment variables for the **test** environment (all project IDs, URLs, and VPC names are defined here):
```bash
source deploy/gcp/test.env
```

Configure `gcloud` context:
```bash
gcloud config configurations describe "edtech-${ENVIRONMENT}" >/dev/null 2>&1 \
  || gcloud config configurations create "edtech-${ENVIRONMENT}"

gcloud config configurations activate "edtech-${ENVIRONMENT}"
gcloud config set project "$GCP_PROJECT_ID"
gcloud config set run/region "$REGION"

# Verify active project:
gcloud config get-value project
```

---

## 3. Enable GCP Services & Create Artifact Registry

Enable required Google Cloud APIs:
```bash
gcloud services enable \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  cloudscheduler.googleapis.com \
  compute.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  logging.googleapis.com \
  monitoring.googleapis.com \
  run.googleapis.com \
  secretmanager.googleapis.com \
  servicenetworking.googleapis.com \
  sqladmin.googleapis.com
```
gcloud config set compute/region "$REGION"

Create the Docker repository in Google Artifact Registry:
```bash
gcloud artifacts repositories create edtech \
  --repository-format=docker \
  --location="$REGION" \
  --description="EdTech App container images"
```

---

## 4. Network Setup (Direct VPC & Private Peering)

Create custom VPC and subnet for Cloud Run Direct VPC egress:
```bash
# 1. Create VPC network
gcloud compute networks create "$VPC_NETWORK" \
  --subnet-mode=custom

# 2. Create Cloud Run Direct VPC egress subnet
gcloud compute networks subnets create "$VPC_SUBNET" \
  --network="$VPC_NETWORK" \
  --region="$REGION" \
  --range="10.20.0.0/24" \
  --enable-private-ip-google-access

# 3. Reserve private peering IP range for Cloud SQL
gcloud compute addresses create "google-managed-services-${VPC_NETWORK}" \
  --global \
  --purpose=VPC_PEERING \
  --prefix-length=24 \
  --network="$VPC_NETWORK"

# 4. Connect VPC peering with Service Networking
gcloud services vpc-peerings connect \
  --service=servicenetworking.googleapis.com \
  --ranges="google-managed-services-${VPC_NETWORK}" \
  --network="$VPC_NETWORK"
```

---

## 5. Provision Cloud SQL (PostgreSQL 16)

Create the Cloud SQL PostgreSQL 16 instance with private IP only:
```bash
gcloud sql instances create "$DB_INSTANCE" \
  --database-version=POSTGRES_16 \
  --edition=ENTERPRISE \
  --tier=db-g1-small \
  --region="$REGION" \
  --network="projects/${GCP_PROJECT_ID}/global/networks/${VPC_NETWORK}" \
  --no-assign-ip \
  --storage-size=20 \
  --storage-auto-increase \
  --backup-start-time=02:00
```

Create databases and users:
```bash
# 1. Create databases
gcloud sql databases create "$DB_NAME" --instance="$DB_INSTANCE"
gcloud sql databases create edtech_keycloak --instance="$DB_INSTANCE"

# 2. Prompt for database passwords (works in bash and zsh)
echo -n "Enter Backend DB Password: "; read -rs APP_DB_PASSWORD; echo
echo -n "Enter Keycloak DB Password: "; read -rs KC_DB_PASSWORD; echo

# 3. Create database users
gcloud sql users create "$DB_USER" \
  --instance="$DB_INSTANCE" \
  --password="$APP_DB_PASSWORD"

gcloud sql users create keycloak_user \
  --instance="$DB_INSTANCE" \
  --password="$KC_DB_PASSWORD"

# 4. Capture assigned Private IP
export DB_HOST="$(gcloud sql instances describe "$DB_INSTANCE" --format="value(ipAddresses[0].ipAddress)")"
echo "Cloud SQL Private IP is: $DB_HOST"
```

> **Note**: Update `DB_HOST` in `deploy/gcp/test.env` with this Private IP value.

---

## 6. Secret Manager Provisioning

Provision required application secrets:
```bash
# Helper to create or update secrets
put_secret() {
  local name="$1"
  local val="$2"
  gcloud secrets describe "$name" >/dev/null 2>&1 \
    || gcloud secrets create "$name" --replication-policy="automatic"
  echo -n "$val" | gcloud secrets versions add "$name" --data-file=-
}

# 1. Database passwords
put_secret "edtech-db-password-${ENVIRONMENT}" "$APP_DB_PASSWORD"
put_secret "edtech-keycloak-db-password-${ENVIRONMENT}" "$KC_DB_PASSWORD"

# 2. Keycloak credentials
echo -n "Enter Keycloak superadmin password: "; read -rs KC_ADMIN_PASSWORD; echo
put_secret "edtech-keycloak-admin-password-${ENVIRONMENT}" "$KC_ADMIN_PASSWORD"
# Initial placeholder for backend client secret (updated after realm import)
put_secret "edtech-keycloak-client-secret-${ENVIRONMENT}" "backend-secret-change-in-production"

# 3. Paystack API Keys
echo -n "Enter Paystack Secret Key: "; read -rs PAYSTACK_SECRET; echo
echo -n "Enter Paystack Public Key: "; read -rs PAYSTACK_PUBLIC; echo
put_secret "edtech-paystack-secret-key-${ENVIRONMENT}" "$PAYSTACK_SECRET"
put_secret "edtech-paystack-public-key-${ENVIRONMENT}" "$PAYSTACK_PUBLIC"

# 4. SMTP Mail credentials (e.g. Resend)
echo -n "Enter SMTP username: "; read -rs MAIL_USER; echo
echo -n "Enter SMTP password: "; read -rs MAIL_PASS; echo
put_secret "edtech-mail-username-${ENVIRONMENT}" "$MAIL_USER"
put_secret "edtech-mail-password-${ENVIRONMENT}" "$MAIL_PASS"
```

---

## 7. Service Accounts & IAM Setup

### Runtime Service Accounts
```bash
# 1. Create service accounts
gcloud iam service-accounts create "edtech-backend-${ENVIRONMENT}" \
  --display-name="EdTech Backend (${ENVIRONMENT})"

gcloud iam service-accounts create "edtech-keycloak-${ENVIRONMENT}" \
  --display-name="EdTech Keycloak (${ENVIRONMENT})"

# 2. Grant Secret Manager access
for SA in "edtech-backend-${ENVIRONMENT}" "edtech-keycloak-${ENVIRONMENT}"; do
  gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
    --member="serviceAccount:${SA}@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
done

# 3. Grant Cloud SQL Client to backend service account
gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
  --member="serviceAccount:edtech-backend-${ENVIRONMENT}@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"
```

### CI/CD Service Account (GitHub Actions)
```bash
# 1. Create CI service account
gcloud iam service-accounts create github-actions-ci \
  --display-name="GitHub Actions CI/CD"

# 2. Grant Artifact Registry writer
gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
  --member="serviceAccount:github-actions-ci@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

# 3. Grant Cloud Run admin
gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
  --member="serviceAccount:github-actions-ci@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# 4. Grant permission to act as runtime service accounts
gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
  --member="serviceAccount:github-actions-ci@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"

# 5. Generate JSON key for GitHub Secrets
gcloud iam service-accounts keys create /tmp/gcp-sa-key.json \
  --iam-account="github-actions-ci@${GCP_PROJECT_ID}.iam.gserviceaccount.com"

echo "=== Copy the JSON below into GitHub Secrets as 'GCP_SA_KEY' ==="
cat /tmp/gcp-sa-key.json
echo "=============================================================="
rm /tmp/gcp-sa-key.json
```

Add `GCP_SA_KEY` to GitHub Repository Settings -> **Secrets and variables** -> **Actions** -> **New repository secret**.

---

## 8. Deploy Keycloak

### Step 1: Build Keycloak Container Image (One-Time)
```bash
gcloud builds submit deploy/gcp/keycloak \
  --tag="${REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/edtech/keycloak:26.6.4"
```

### Step 2: Deploy Keycloak Service
Execute the deployment script:
```bash
chmod +x ./deploy/deploy-keycloak.sh
./deploy/deploy-keycloak.sh test
```

### Step 3: Enable Public Ingress
```bash
gcloud run services add-iam-policy-binding "edtech-keycloak-${ENVIRONMENT}" \
  --region="$REGION" \
  --member="allUsers" \
  --role="roles/run.invoker" \
  --project="$GCP_PROJECT_ID"
```

---

## 9. Keycloak Realm Setup & Client Secret

1. Open Keycloak Admin Console: `${KEYCLOAK_URL}/admin/` (or the default Cloud Run service URL).
2. Log in with username `superadmin` and password `$KC_ADMIN_PASSWORD`.
3. Select **Add Realm** (or Import Realm) -> choose `backend/keycloak/import/edtech-realm.json`.
4. Navigate to **Clients** -> `edtech-backend` -> **Credentials** tab.
5. Copy the Client Secret and update it in Secret Manager:
   ```bash
   
   
   # 1. Get an admin token
TOKEN=$(curl -s -d "client_id=admin-cli" \
-d "username=superadmin" \
-d "password=$KC_ADMIN_PASSWORD" \
-d "grant_type=password" \
"https://auth-test.smartbridgeedu.com/realms/master/protocol/openid-connect/token" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

# 2. Import the edtech realm
curl -s -X POST "https://auth-test.smartbridgeedu.com/admin/realms" \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d @backend/keycloak/import/edtech-realm.json

echo "Realm import complete!"


   echo -n "Enter Keycloak edtech-backend client secret: "; read -rs KC_CLIENT_SECRET; echo
   put_secret "edtech-keycloak-client-secret-${ENVIRONMENT}" "$KC_CLIENT_SECRET"
   ```

Verify OpenID discovery:
```bash
curl -fsS "${KEYCLOAK_URL}/realms/edtech/.well-known/openid-configuration" | head -n 10
```

---

## 10. Deploy Backend API

The backend deployment is **fully automated** in CI/CD via GitHub Actions!

### Option A: CI/CD Pipeline (Recommended)
1. Push your commit to the `development` branch (for test) or `main` (for production).
2. GitHub Actions runs `.github/workflows/commit-stage.yml`:
   - Builds and tests with Gradle.
   - Packages using the lightweight `backend/Dockerfile.ci`.
   - Pushes to Artifact Registry.
   - Deploys to Cloud Run using `./deploy/deploy-backend.sh test`.

### Option B: Local CLI Deployment
If you wish to deploy directly from your local terminal:
```bash
chmod +x ./deploy/deploy-backend.sh
./deploy/deploy-backend.sh test
```

### Step 2: Enable Public Ingress
```bash
gcloud run services add-iam-policy-binding "edtech-backend-${ENVIRONMENT}" \
  --region="$REGION" \
  --member="allUsers" \
  --role="roles/run.invoker" \
  --project="$GCP_PROJECT_ID"
```

---

## 11. Deploy Outbox Processor (Cloud Run Job + Scheduler)

The API service disables the background outbox scheduler to stay stateless. A Cloud Run Job executes once per minute via Cloud Scheduler.

### Step 1: Create Outbox Job
```bash
export OUTBOX_JOB="edtech-outbox-${ENVIRONMENT}"
export BACKEND_IMAGE="${REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/edtech/backend:latest"

cat > /tmp/outbox-env.yaml <<EOF
SPRING_PROFILES_ACTIVE: "prod,job"
SPRING_MAIN_WEB_APPLICATION_TYPE: "none"
DB_HOST: "${DB_HOST}"
DB_NAME: "${DB_NAME}"
DB_USER: "${DB_USER}"
SPRING_R2DBC_POOL_INITIAL_SIZE: "1"
SPRING_R2DBC_POOL_MAX_SIZE: "5"
SPRING_FLYWAY_ENABLED: "true"
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "${KEYCLOAK_URL}/realms/edtech"
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: "${KEYCLOAK_URL}/realms/edtech/protocol/openid-connect/certs"
KEYCLOAK_URL: "${KEYCLOAK_URL}"
KEYCLOAK_REALM: "edtech"
KEYCLOAK_ADMIN_USER: "superadmin"
FRONTEND_URL: "${FRONTEND_URL}"
APP_SCHEDULER_OUTBOX_ENABLED: "false"
SMS_ENABLED: "false"
WHATSAPP_ENABLED: "false"
EOF

gcloud run jobs deploy "$OUTBOX_JOB" \
  --image="$BACKEND_IMAGE" \
  --region="$REGION" \
  --service-account="edtech-backend-${ENVIRONMENT}@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --network="$VPC_NETWORK" \
  --subnet="$VPC_SUBNET" \
  --vpc-egress=private-ranges-only \
  --env-vars-file=/tmp/outbox-env.yaml \
  --set-secrets="SCHOOL_FEE_DB_PASSWORD=edtech-db-password-${ENVIRONMENT}:latest,FLYWAY_DB_PASSWORD=edtech-db-password-${ENVIRONMENT}:latest,KEYCLOAK_CLIENT_SECRET=edtech-keycloak-client-secret-${ENVIRONMENT}:latest,KEYCLOAK_ADMIN_PASSWORD=edtech-keycloak-admin-password-${ENVIRONMENT}:latest,MAIL_USERNAME=edtech-mail-username-${ENVIRONMENT}:latest,MAIL_PASSWORD=edtech-mail-password-${ENVIRONMENT}:latest" \
  --tasks=1 \
  --max-retries=3 \
  --task-timeout=10m \
  --cpu=1 \
  --memory=1Gi

rm /tmp/outbox-env.yaml
```

### Step 2: Schedule Job with Cloud Scheduler
```bash
export SCHEDULER_SA="edtech-scheduler-${ENVIRONMENT}@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
export SCHEDULER_JOB="edtech-outbox-schedule-${ENVIRONMENT}"

# 1. Create scheduler service account
gcloud iam service-accounts create "edtech-scheduler-${ENVIRONMENT}" \
  --display-name="EdTech Outbox Scheduler (${ENVIRONMENT})"

# 2. Grant permission to invoke job
gcloud run jobs add-iam-policy-binding "$OUTBOX_JOB" \
  --region="$REGION" \
  --member="serviceAccount:${SCHEDULER_SA}" \
  --role="roles/run.invoker"

# 3. Create Cloud Scheduler HTTP trigger
export OUTBOX_RUN_URI="https://run.googleapis.com/v2/projects/${GCP_PROJECT_ID}/locations/${REGION}/jobs/${OUTBOX_JOB}:run"

gcloud scheduler jobs create http "$SCHEDULER_JOB" \
  --location="$REGION" \
  --schedule="* * * * *" \
  --time-zone="Africa/Lagos" \
  --uri="$OUTBOX_RUN_URI" \
  --http-method=POST \
  --oauth-service-account-email="$SCHEDULER_SA" \
  --attempt-deadline=10m
```

---

## 12. Custom Domain Mapping

Map custom subdomains on Cloud Run:
```bash
# 1. Keycloak Domain
gcloud beta run domain-mappings create \
  --service="edtech-keycloak-${ENVIRONMENT}" \
  --domain="auth-test.smartbridgeedu.com" \
  --region="$REGION"

# 2. Backend API Domain
gcloud beta run domain-mappings create \
  --service="edtech-backend-${ENVIRONMENT}" \
  --domain="api-test.smartbridgeedu.com" \
  --region="$REGION"
```

Configure the DNS `CNAME` or `A` records in your DNS provider (Cloudflare, Namecheap, Route53, etc.) as displayed by the command output.

---

## 13. Paystack Webhook Configuration

In Paystack Dashboard (**Settings** -> **Preferences** -> **Webhooks**):
- **Test Webhook URL**: `https://api-test.smartbridgeedu.com/api/v1/webhooks/paystack/callback`
- **Live Webhook URL**: `https://api.smartbridgeedu.com/api/v1/webhooks/paystack/callback`

---

## 14. Frontend Deployment on Vercel

In your Vercel Dashboard, configure the project for the test environment:
- **Project Name**: `edtech-test`
- **Domain**: `test.smartbridgeedu.com`
- **Framework Preset**: Vite
- **Environment Variables**:
  ```text
  VITE_API_URL=https://api-test.smartbridgeedu.com
  VITE_KEYCLOAK_URL=https://auth-test.smartbridgeedu.com
  VITE_KEYCLOAK_REALM=edtech
  VITE_KEYCLOAK_CLIENT_ID=edtech-web
  ```

---

## 15. Smoke Test & Verification Checklist

Verify deployment health:

```bash
# 1. Check Cloud Run services
gcloud run services list --region="$REGION"

# 2. Backend Health Check
curl -fsS "${BACKEND_URL}/actuator/health"

# 3. Keycloak Health Check
curl -fsS "${KEYCLOAK_URL}/health/ready"

# 4. Outbox Execution
gcloud run jobs execute "$OUTBOX_JOB" --region="$REGION" --wait
```

- [x] Cloud SQL has private IP only and VPC peering connected.
- [x] Keycloak health endpoint returns `UP`.
- [x] Realm `edtech` imported with valid redirect URIs.
- [x] Spring Boot API starts up and reports `UP` at `/actuator/health`.
- [x] Outbox job runs successfully every minute.
- [x] Vercel frontend redirects to Keycloak authentication successfully.
