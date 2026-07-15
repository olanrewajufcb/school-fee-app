# School Fee App — Google Cloud Deployment Guide

This guide deploys:

- the React frontend to Vercel;
- the Spring Boot API to Cloud Run;
- PostgreSQL 16 to Cloud SQL using private IP;
- Keycloak to Cloud Run;
- the outbox processor as a Cloud Run Job triggered by Cloud Scheduler;
- credentials and API keys through Secret Manager.

Test and production use separate Google Cloud projects. Run the entire guide
once for `test`, verify it, and only then repeat it for `prod`.

> This is a first-deployment guide, not a substitute for Terraform. Once the
> deployment is stable, codify these resources with Terraform so changes are
> reviewable and reproducible.

## 1. Architecture and important constraints

```text
Vercel
   │ HTTPS
   ▼
Cloud Run API ───────────────► Keycloak on Cloud Run
   │                                  │
   │ private IP                       │ private IP
   └──────────────► Cloud SQL ◄────────┘
                         ▲
                         │ private IP
Cloud Scheduler ─► Cloud Run Outbox Job
```

- Cloud SQL has no public IP.
- Cloud Run uses Direct VPC egress; no `localhost` database assumption and no
  Cloud SQL Auth Proxy sidecar are required.
- The API is public at the Cloud Run edge because Vercel and Paystack must call
  it. Spring Security continues to enforce application authentication.
- The outbox scheduler is disabled in the API and runs once per minute as a
  Cloud Run Job.
- Do not deploy Mailpit as a normal Cloud Run service for SMTP. Cloud Run
  service ingress is HTTP/gRPC, not arbitrary SMTP. Use a transactional SMTP
  provider for cloud environments. Mailpit remains useful locally.
- The Keycloak deployment below intentionally uses one Cloud Run instance.
  This is functional but not highly available. Use GKE, Compute Engine, or a
  managed Keycloak provider when production identity availability requires
  multiple clustered instances.

Official references:

- [Cloud Run Direct VPC egress](https://cloud.google.com/run/docs/configuring/vpc-direct-vpc)
- [Cloud SQL private IP](https://cloud.google.com/sql/docs/postgres/configure-private-ip)
- [Cloud Run secrets](https://cloud.google.com/run/docs/configuring/services/secrets)
- [Schedule Cloud Run Jobs](https://cloud.google.com/run/docs/execute/jobs-on-schedule)
- [Keycloak production configuration](https://www.keycloak.org/server/configuration-production)

## 2. Prerequisites

Install:

- Google Cloud CLI;
- `git`;
- `openssl`;
- Vercel CLI, or access to the Vercel dashboard.

Create:

- one GCP project for test;
- one GCP project for production;
- a billing account linked to each project;
- one Vercel project with Preview and Production environments;
- an SMTP account;
- Paystack test and live credentials.

Authenticate:

```bash
gcloud auth login
gcloud components update
```

Always run repository commands from the project root:

```bash
cd ~/IdeaProjects/school-fee-app
```

Before deployment, rotate every credential that has ever appeared in source
control or logs. Remove real credentials used as fallback defaults in
`application.yaml`.

## 3. Select an environment

Use a named gcloud configuration to reduce accidental production changes.

### Test

```bash
export ENVIRONMENT="test"
export PROJECT_ID="your-schoolfee-test-project"
export REGION="europe-west1"
export FRONTEND_URL="https://your-test-project.vercel.app"
```

### Production

```bash
export ENVIRONMENT="prod"
export PROJECT_ID="your-schoolfee-production-project"
export REGION="europe-west1"
export FRONTEND_URL="https://your-production-domain.example"
```

Set the shared resource names:

```bash
export VPC_NETWORK="schoolfee-${ENVIRONMENT}"
export VPC_SUBNET="schoolfee-cloudrun-${ENVIRONMENT}"
export PRIVATE_SERVICE_RANGE="schoolfee-managed-services-${ENVIRONMENT}"
export DB_INSTANCE="schoolfee-db-${ENVIRONMENT}"

export BACKEND_SERVICE="schoolfee-backend-${ENVIRONMENT}"
export KEYCLOAK_SERVICE="schoolfee-keycloak-${ENVIRONMENT}"
export OUTBOX_JOB="schoolfee-outbox-${ENVIRONMENT}"
export SCHEDULER_JOB="schoolfee-outbox-trigger-${ENVIRONMENT}"

export BACKEND_SA_NAME="sf-backend-${ENVIRONMENT}"
export KEYCLOAK_SA_NAME="sf-keycloak-${ENVIRONMENT}"
export SCHEDULER_SA_NAME="sf-scheduler-${ENVIRONMENT}"

export BACKEND_SA="${BACKEND_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
export KEYCLOAK_SA="${KEYCLOAK_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
export SCHEDULER_SA="${SCHEDULER_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

export IMAGE_TAG="$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M%S)"
export BACKEND_IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/schoolfee/backend:${IMAGE_TAG}"
export KEYCLOAK_IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/schoolfee/keycloak:26.6.4"
```

Create or activate the gcloud configuration:

```bash
gcloud config configurations describe "schoolfee-${ENVIRONMENT}" >/dev/null 2>&1 \
  || gcloud config configurations create "schoolfee-${ENVIRONMENT}"

gcloud config configurations activate "schoolfee-${ENVIRONMENT}"
gcloud config set project "$PROJECT_ID"
gcloud config set run/region "$REGION"
gcloud config set compute/region "$REGION"

gcloud config get-value project
```

Confirm that the printed project is the intended environment before continuing.

## 4. Enable Google Cloud APIs

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

Create the Artifact Registry repository if it does not exist:

```bash
gcloud artifacts repositories describe schoolfee \
  --location="$REGION" >/dev/null 2>&1 \
  || gcloud artifacts repositories create schoolfee \
       --repository-format=docker \
       --location="$REGION" \
       --description="School Fee App container images"
```

Grant the project's default Cloud Build identity permission to push images:

```bash
export CLOUD_BUILD_SA="$(gcloud builds get-default-service-account)"

gcloud artifacts repositories add-iam-policy-binding schoolfee \
  --location="$REGION" \
  --member="serviceAccount:${CLOUD_BUILD_SA}" \
  --role="roles/artifactregistry.writer"
```

## 5. Create the private network

Create a custom VPC and a `/24` subnet for Cloud Run Direct VPC egress:

```bash
gcloud compute networks describe "$VPC_NETWORK" >/dev/null 2>&1 \
  || gcloud compute networks create "$VPC_NETWORK" \
       --subnet-mode=custom

gcloud compute networks subnets describe "$VPC_SUBNET" \
  --region="$REGION" >/dev/null 2>&1 \
  || gcloud compute networks subnets create "$VPC_SUBNET" \
       --network="$VPC_NETWORK" \
       --region="$REGION" \
       --range="10.20.0.0/24" \
       --enable-private-ip-google-access
```

Reserve an address range and establish private services access for Cloud SQL:

```bash
gcloud compute addresses describe "$PRIVATE_SERVICE_RANGE" \
  --global >/dev/null 2>&1 \
  || gcloud compute addresses create "$PRIVATE_SERVICE_RANGE" \
       --global \
       --purpose=VPC_PEERING \
       --prefix-length=24 \
       --network="$VPC_NETWORK"

gcloud services vpc-peerings list \
  --network="$VPC_NETWORK" \
  --format="value(service)" | grep -q "servicenetworking.googleapis.com" \
  || gcloud services vpc-peerings connect \
       --service=servicenetworking.googleapis.com \
       --ranges="$PRIVATE_SERVICE_RANGE" \
       --network="$VPC_NETWORK"
```

## 6. Create service accounts

```bash
create_service_account() {
  local account_id="$1"
  local display_name="$2"

  gcloud iam service-accounts describe \
    "${account_id}@${PROJECT_ID}.iam.gserviceaccount.com" >/dev/null 2>&1 \
    || gcloud iam service-accounts create "$account_id" \
         --display-name="$display_name"
}

create_service_account "$BACKEND_SA_NAME" "School Fee backend and outbox"
create_service_account "$KEYCLOAK_SA_NAME" "School Fee Keycloak"
create_service_account "$SCHEDULER_SA_NAME" "School Fee Scheduler invoker"
```

The identity performing deployments needs permission to use the runtime service
accounts. If you are not a project Owner, ask an administrator to grant you
`roles/iam.serviceAccountUser` on the backend and Keycloak service accounts.

## 7. Create Cloud SQL

Use a small shared-core instance only in test. Production receives a custom,
regional instance.

### Test instance

```bash
if ! gcloud sql instances describe "$DB_INSTANCE" >/dev/null 2>&1; then
  gcloud sql instances create "$DB_INSTANCE" \
    --database-version=POSTGRES_16 \
    --edition=ENTERPRISE \
    --tier=db-g1-small \
    --availability-type=ZONAL \
    --region="$REGION" \
    --network="projects/${PROJECT_ID}/global/networks/${VPC_NETWORK}" \
    --no-assign-ip \
    --storage-type=SSD \
    --storage-size=20 \
    --storage-auto-increase \
    --backup-start-time=02:00 \
    --enable-point-in-time-recovery
fi
```

### Production instance

Run this instead when `ENVIRONMENT=prod`:

```bash
if ! gcloud sql instances describe "$DB_INSTANCE" >/dev/null 2>&1; then
  gcloud sql instances create "$DB_INSTANCE" \
    --database-version=POSTGRES_16 \
    --edition=ENTERPRISE \
    --cpu=2 \
    --memory=7680MiB \
    --availability-type=REGIONAL \
    --region="$REGION" \
    --network="projects/${PROJECT_ID}/global/networks/${VPC_NETWORK}" \
    --no-assign-ip \
    --storage-type=SSD \
    --storage-size=50 \
    --storage-auto-increase \
    --backup-start-time=02:00 \
    --enable-point-in-time-recovery \
    --deletion-protection
fi
```

Create the two databases:

```bash
gcloud sql databases describe schoolfee_db \
  --instance="$DB_INSTANCE" >/dev/null 2>&1 \
  || gcloud sql databases create schoolfee_db --instance="$DB_INSTANCE"

gcloud sql databases describe sch_keycloak \
  --instance="$DB_INSTANCE" >/dev/null 2>&1 \
  || gcloud sql databases create sch_keycloak --instance="$DB_INSTANCE"
```

Generate separate application and Keycloak database passwords:

```bash
read -rsp "Application database password: " APP_DB_PASSWORD
echo
read -rsp "Keycloak database password: " KEYCLOAK_DB_PASSWORD
echo

if gcloud sql users describe sch_fee_user \
  --instance="$DB_INSTANCE" >/dev/null 2>&1; then
  gcloud sql users set-password sch_fee_user \
    --instance="$DB_INSTANCE" \
    --password="$APP_DB_PASSWORD"
else
  gcloud sql users create sch_fee_user \
    --instance="$DB_INSTANCE" \
    --password="$APP_DB_PASSWORD"
fi

if gcloud sql users describe keycloak_user \
  --instance="$DB_INSTANCE" >/dev/null 2>&1; then
  gcloud sql users set-password keycloak_user \
    --instance="$DB_INSTANCE" \
    --password="$KEYCLOAK_DB_PASSWORD"
else
  gcloud sql users create keycloak_user \
    --instance="$DB_INSTANCE" \
    --password="$KEYCLOAK_DB_PASSWORD"
fi
```

Capture the private address:

```bash
export DB_PRIVATE_IP="$(
  gcloud sql instances describe "$DB_INSTANCE" \
    --format="value(ipAddresses[0].ipAddress)"
)"

test -n "$DB_PRIVATE_IP" && echo "Cloud SQL private IP resolved"
```

## 8. Create secrets

The helper below creates a secret or adds a new version if it already exists:

```bash
put_secret() {
  local secret_name="$1"
  local secret_value="$2"

  if gcloud secrets describe "$secret_name" >/dev/null 2>&1; then
    printf '%s' "$secret_value" \
      | gcloud secrets versions add "$secret_name" --data-file=-
  else
    printf '%s' "$secret_value" \
      | gcloud secrets create "$secret_name" \
          --replication-policy=automatic \
          --data-file=-
  fi
}

put_secret "schoolfee-db-password-${ENVIRONMENT}" "$APP_DB_PASSWORD"
put_secret "schoolfee-keycloak-db-password-${ENVIRONMENT}" "$KEYCLOAK_DB_PASSWORD"

unset APP_DB_PASSWORD KEYCLOAK_DB_PASSWORD
```

Create the remaining credentials without putting their values in command
history:

```bash
read -rsp "Keycloak bootstrap admin password: " VALUE
echo
put_secret "schoolfee-keycloak-admin-password-${ENVIRONMENT}" "$VALUE"
unset VALUE

read -rsp "Paystack secret key: " VALUE
echo
put_secret "schoolfee-paystack-secret-key-${ENVIRONMENT}" "$VALUE"
unset VALUE

read -rsp "Paystack public key: " VALUE
echo
put_secret "schoolfee-paystack-public-key-${ENVIRONMENT}" "$VALUE"
unset VALUE

read -rp "SMTP username: " VALUE
put_secret "schoolfee-mail-username-${ENVIRONMENT}" "$VALUE"
unset VALUE

read -rsp "SMTP password: " VALUE
echo
put_secret "schoolfee-mail-password-${ENVIRONMENT}" "$VALUE"
unset VALUE
```

Grant only the required runtime identities access:

```bash
for secret in \
  "schoolfee-db-password-${ENVIRONMENT}" \
  "schoolfee-keycloak-admin-password-${ENVIRONMENT}" \
  "schoolfee-paystack-secret-key-${ENVIRONMENT}" \
  "schoolfee-paystack-public-key-${ENVIRONMENT}" \
  "schoolfee-mail-username-${ENVIRONMENT}" \
  "schoolfee-mail-password-${ENVIRONMENT}"
do
  gcloud secrets add-iam-policy-binding "$secret" \
    --member="serviceAccount:${BACKEND_SA}" \
    --role="roles/secretmanager.secretAccessor"
done

for secret in \
  "schoolfee-keycloak-db-password-${ENVIRONMENT}" \
  "schoolfee-keycloak-admin-password-${ENVIRONMENT}"
do
  gcloud secrets add-iam-policy-binding "$secret" \
    --member="serviceAccount:${KEYCLOAK_SA}" \
    --role="roles/secretmanager.secretAccessor"
done
```

## 9. Build and deploy Keycloak

The repository contains an optimized Keycloak image definition at
`deploy/gcp/keycloak/Dockerfile`. The version is pinned; review Keycloak release
notes and test upgrades before changing it.

```bash
gcloud builds submit deploy/gcp/keycloak \
  --tag="$KEYCLOAK_IMAGE"
```

Deploy it privately for the initial bootstrap. TLS terminates at Cloud Run, so
Keycloak listens over HTTP inside the container and trusts Cloud Run's
`X-Forwarded-*` headers:

```bash
cat > /tmp/keycloak-env.yaml <<EOF
KC_DB: "postgres"
KC_DB_URL: "jdbc:postgresql://${DB_PRIVATE_IP}:5432/sch_keycloak"
KC_DB_USERNAME: "keycloak_user"
KC_HTTP_ENABLED: "true"
KC_PROXY_HEADERS: "xforwarded"
KC_HOSTNAME_STRICT: "false"
KC_HEALTH_ENABLED: "true"
KC_METRICS_ENABLED: "true"
KC_BOOTSTRAP_ADMIN_USERNAME: "superadmin"
EOF

gcloud run deploy "$KEYCLOAK_SERVICE" \
  --image="$KEYCLOAK_IMAGE" \
  --args=start,--optimized \
  --region="$REGION" \
  --port=8080 \
  --service-account="$KEYCLOAK_SA" \
  --network="$VPC_NETWORK" \
  --subnet="$VPC_SUBNET" \
  --vpc-egress=private-ranges-only \
  --env-vars-file=/tmp/keycloak-env.yaml \
  --set-secrets="KC_DB_PASSWORD=schoolfee-keycloak-db-password-${ENVIRONMENT}:latest,KC_BOOTSTRAP_ADMIN_PASSWORD=schoolfee-keycloak-admin-password-${ENVIRONMENT}:latest" \
  --min=1 \
  --max=1 \
  --cpu=2 \
  --memory=2Gi \
  --concurrency=40 \
  --invoker-iam-check
```

Get the generated URL, configure it as the fixed hostname, and only then make
Keycloak public:

```bash
export KEYCLOAK_URL="$(
  gcloud run services describe "$KEYCLOAK_SERVICE" \
    --region="$REGION" \
    --format="value(status.url)"
)"

gcloud run services update "$KEYCLOAK_SERVICE" \
  --region="$REGION" \
  --update-env-vars="KC_HOSTNAME=${KEYCLOAK_URL},KC_HOSTNAME_STRICT=true" \
  --no-invoker-iam-check

echo "$KEYCLOAK_URL"
```

### Import and secure the realm

1. Open `${KEYCLOAK_URL}/admin/`.
2. Sign into the master realm using `superadmin` and the bootstrap password.
3. Create/import the `schoolfee` realm from:
   `backend/keycloak/import/schoolfee-realm.json`.
4. In client `schoolfee-web`, set:
   - Valid redirect URI: `${FRONTEND_URL}/*`
   - Valid post-logout redirect URI: `${FRONTEND_URL}/*`
   - Web origin: `${FRONTEND_URL}`
5. Remove localhost and unrelated production origins from the production realm.
6. Keep PKCE S256 enabled for the public web client.
7. Regenerate the `schoolfee-backend` client secret.

Store the generated backend client secret:

```bash
read -rsp "Keycloak schoolfee-backend client secret: " VALUE
echo
put_secret "schoolfee-keycloak-client-secret-${ENVIRONMENT}" "$VALUE"
unset VALUE

gcloud secrets add-iam-policy-binding \
  "schoolfee-keycloak-client-secret-${ENVIRONMENT}" \
  --member="serviceAccount:${BACKEND_SA}" \
  --role="roles/secretmanager.secretAccessor"
```

Verify discovery before deploying the API:

```bash
curl --fail --silent \
  "${KEYCLOAK_URL}/realms/schoolfee/.well-known/openid-configuration" \
  >/dev/null && echo "Keycloak realm is reachable"
```

> The backend currently administers Keycloak through the bootstrap admin
> account. Replace this later with a least-privilege service-account client.

## 10. Build the backend image

The Gradle wrapper is at the repository root. Do not submit `./backend` as the
Docker context.

Validate locally:

```bash
./gradlew :backend:bootJar
```

Build and push with Cloud Build:

```bash
gcloud builds submit . \
  --config=deploy/gcp/cloudbuild-backend.yaml \
  --substitutions="_REGION=${REGION},_IMAGE_TAG=${IMAGE_TAG}"
```

Confirm the image:

```bash
gcloud artifacts docker images describe "$BACKEND_IMAGE"
```

## 11. Deploy the backend API

Select SMTP values. These examples fit a normal authenticated SMTP provider:

```bash
export MAIL_HOST="smtp.example.com"
export MAIL_PORT="587"
export MAIL_SMTP_AUTH="true"
export MAIL_SMTP_STARTTLS="true"
```

Create the non-secret runtime configuration:

```bash
cat > /tmp/backend-env.yaml <<EOF
SPRING_PROFILES_ACTIVE: "prod,cloud"
SERVER_PORT: "8080"
DB_HOST: "${DB_PRIVATE_IP}"
SPRING_R2DBC_POOL_INITIAL_SIZE: "2"
SPRING_R2DBC_POOL_MAX_SIZE: "10"
SPRING_FLYWAY_ENABLED: "true"
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "${KEYCLOAK_URL}/realms/schoolfee"
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: "${KEYCLOAK_URL}/realms/schoolfee/protocol/openid-connect/certs"
KEYCLOAK_URL: "${KEYCLOAK_URL}"
KEYCLOAK_REALM: "schoolfee"
KEYCLOAK_ADMIN_USER: "superadmin"
FRONTEND_URL: "${FRONTEND_URL}"
APP_CORS_ALLOWED_ORIGINS: "${FRONTEND_URL}"
APP_SCHEDULER_OUTBOX_ENABLED: "false"
PAYSTACK_CALLBACK_URL: "https://placeholder.invalid/api/v1/webhooks/paystack/callback"
MAIL_HOST: "${MAIL_HOST}"
MAIL_PORT: "${MAIL_PORT}"
MAIL_SMTP_AUTH: "${MAIL_SMTP_AUTH}"
MAIL_SMTP_STARTTLS: "${MAIL_SMTP_STARTTLS}"
SMS_ENABLED: "false"
WHATSAPP_ENABLED: "false"
EOF
```

Deploy the API:

```bash
gcloud run deploy "$BACKEND_SERVICE" \
  --image="$BACKEND_IMAGE" \
  --region="$REGION" \
  --port=8080 \
  --service-account="$BACKEND_SA" \
  --network="$VPC_NETWORK" \
  --subnet="$VPC_SUBNET" \
  --vpc-egress=private-ranges-only \
  --env-vars-file=/tmp/backend-env.yaml \
  --set-secrets="SCHOOL_FEE_DB_PASSWORD=schoolfee-db-password-${ENVIRONMENT}:latest,FLYWAY_DB_PASSWORD=schoolfee-db-password-${ENVIRONMENT}:latest,KEYCLOAK_CLIENT_SECRET=schoolfee-keycloak-client-secret-${ENVIRONMENT}:latest,KEYCLOAK_ADMIN_PASSWORD=schoolfee-keycloak-admin-password-${ENVIRONMENT}:latest,PAYSTACK_SECRET_KEY=schoolfee-paystack-secret-key-${ENVIRONMENT}:latest,PAYSTACK_PUBLIC_KEY=schoolfee-paystack-public-key-${ENVIRONMENT}:latest,MAIL_USERNAME=schoolfee-mail-username-${ENVIRONMENT}:latest,MAIL_PASSWORD=schoolfee-mail-password-${ENVIRONMENT}:latest" \
  --min=1 \
  --max=5 \
  --cpu=1 \
  --memory=1Gi \
  --concurrency=40 \
  --timeout=300 \
  --cpu-throttling \
  --no-invoker-iam-check
```

Capture the API URL and replace the temporary Paystack callback:

```bash
export BACKEND_URL="$(
  gcloud run services describe "$BACKEND_SERVICE" \
    --region="$REGION" \
    --format="value(status.url)"
)"

gcloud run services update "$BACKEND_SERVICE" \
  --region="$REGION" \
  --update-env-vars="PAYSTACK_CALLBACK_URL=${BACKEND_URL}/api/v1/webhooks/paystack/callback"

echo "$BACKEND_URL"
```

Check startup and Flyway:

```bash
curl --fail "${BACKEND_URL}/actuator/health"

gcloud run services logs read "$BACKEND_SERVICE" \
  --region="$REGION" \
  --limit=100
```

If the service cannot connect to PostgreSQL, verify:

```bash
gcloud run services describe "$BACKEND_SERVICE" \
  --region="$REGION" \
  --format="yaml(spec.template.metadata.annotations,status.conditions)"

gcloud sql instances describe "$DB_INSTANCE" \
  --format="yaml(ipAddresses,settings.ipConfiguration)"
```

## 12. Deploy the outbox Cloud Run Job

The API deployment disables its in-process scheduler. The job uses the same
image and private network.

```bash
cat > /tmp/outbox-env.yaml <<EOF
SPRING_PROFILES_ACTIVE: "prod,job"
SPRING_MAIN_WEB_APPLICATION_TYPE: "none"
DB_HOST: "${DB_PRIVATE_IP}"
SPRING_R2DBC_POOL_INITIAL_SIZE: "1"
SPRING_R2DBC_POOL_MAX_SIZE: "5"
SPRING_FLYWAY_ENABLED: "true"
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "${KEYCLOAK_URL}/realms/schoolfee"
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: "${KEYCLOAK_URL}/realms/schoolfee/protocol/openid-connect/certs"
KEYCLOAK_URL: "${KEYCLOAK_URL}"
KEYCLOAK_REALM: "schoolfee"
KEYCLOAK_ADMIN_USER: "superadmin"
FRONTEND_URL: "${FRONTEND_URL}"
APP_SCHEDULER_OUTBOX_ENABLED: "false"
MAIL_HOST: "${MAIL_HOST}"
MAIL_PORT: "${MAIL_PORT}"
MAIL_SMTP_AUTH: "${MAIL_SMTP_AUTH}"
MAIL_SMTP_STARTTLS: "${MAIL_SMTP_STARTTLS}"
SMS_ENABLED: "false"
WHATSAPP_ENABLED: "false"
EOF

gcloud run jobs deploy "$OUTBOX_JOB" \
  --image="$BACKEND_IMAGE" \
  --region="$REGION" \
  --service-account="$BACKEND_SA" \
  --network="$VPC_NETWORK" \
  --subnet="$VPC_SUBNET" \
  --vpc-egress=private-ranges-only \
  --env-vars-file=/tmp/outbox-env.yaml \
  --set-secrets="SCHOOL_FEE_DB_PASSWORD=schoolfee-db-password-${ENVIRONMENT}:latest,FLYWAY_DB_PASSWORD=schoolfee-db-password-${ENVIRONMENT}:latest,KEYCLOAK_CLIENT_SECRET=schoolfee-keycloak-client-secret-${ENVIRONMENT}:latest,KEYCLOAK_ADMIN_PASSWORD=schoolfee-keycloak-admin-password-${ENVIRONMENT}:latest,MAIL_USERNAME=schoolfee-mail-username-${ENVIRONMENT}:latest,MAIL_PASSWORD=schoolfee-mail-password-${ENVIRONMENT}:latest" \
  --tasks=1 \
  --max-retries=3 \
  --task-timeout=10m \
  --cpu=1 \
  --memory=1Gi
```

Execute it once manually:

```bash
gcloud run jobs execute "$OUTBOX_JOB" \
  --region="$REGION" \
  --wait
```

Grant Scheduler permission to execute only this job:

```bash
gcloud run jobs add-iam-policy-binding "$OUTBOX_JOB" \
  --region="$REGION" \
  --member="serviceAccount:${SCHEDULER_SA}" \
  --role="roles/run.invoker"
```

Create the schedule using the current Cloud Run v2 Jobs endpoint:

```bash
export OUTBOX_RUN_URI="https://run.googleapis.com/v2/projects/${PROJECT_ID}/locations/${REGION}/jobs/${OUTBOX_JOB}:run"

gcloud scheduler jobs describe "$SCHEDULER_JOB" \
  --location="$REGION" >/dev/null 2>&1 \
  || gcloud scheduler jobs create http "$SCHEDULER_JOB" \
       --location="$REGION" \
       --schedule="* * * * *" \
       --time-zone="Africa/Lagos" \
       --uri="$OUTBOX_RUN_URI" \
       --http-method=POST \
       --oauth-service-account-email="$SCHEDULER_SA" \
       --attempt-deadline=10m
```

Test the Scheduler trigger:

```bash
gcloud scheduler jobs run "$SCHEDULER_JOB" \
  --location="$REGION"

gcloud run jobs executions list \
  --job="$OUTBOX_JOB" \
  --region="$REGION"
```

The current job processes up to 50 events per execution. The one-minute
schedule drains normal workloads quickly. If bursts can exceed that rate,
change the job runner to drain batches until none remain.

## 13. Configure Paystack

In the Paystack dashboard for the corresponding environment:

- use test keys in test and live keys only in production;
- set the callback/webhook URL to:
  `${BACKEND_URL}/api/v1/webhooks/paystack/callback`;
- confirm webhook signatures are verified by the backend;
- run a real low-value test payment;
- confirm the payment becomes `SUCCESS`, the student fee balance changes, and
  the receipt is generated.

Do not treat the browser redirect alone as proof of payment. The backend must
verify the transaction with Paystack or process a signed webhook.

## 14. Deploy and configure Vercel

Set these variables separately for Vercel Preview and Production:

```text
VITE_API_URL=https://your-backend-service-url
VITE_KEYCLOAK_URL=https://your-keycloak-service-url
```

`VITE_API_URL` is the service root and must not end in `/api`; frontend clients
already include `/api/v1/...`.

Deploy:

```bash
cd frontend
npm ci
npm run build
cd ..
```

Deploy through the Vercel dashboard or CLI. The repository contains
`frontend/vercel.json` for SPA route rewrites.

After Vercel assigns the final URL:

1. Update Keycloak client `schoolfee-web` redirect URI, post-logout URI and web
   origin.
2. Update backend `FRONTEND_URL` and `APP_CORS_ALLOWED_ORIGINS`:

```bash
export FRONTEND_URL="https://final-vercel-domain.example"

gcloud run services update "$BACKEND_SERVICE" \
  --region="$REGION" \
  --update-env-vars="FRONTEND_URL=${FRONTEND_URL},APP_CORS_ALLOWED_ORIGINS=${FRONTEND_URL}"

gcloud run jobs update "$OUTBOX_JOB" \
  --region="$REGION" \
  --update-env-vars="FRONTEND_URL=${FRONTEND_URL}"
```

Do not put localhost origins in the production Cloud Run service.

## 15. Smoke-test checklist

Verify all of the following in test before production:

```bash
curl --fail "${BACKEND_URL}/actuator/health"
curl --fail "${KEYCLOAK_URL}/realms/schoolfee/.well-known/openid-configuration"
```

- Vercel login redirects to the correct Keycloak environment.
- Keycloak tokens use `${KEYCLOAK_URL}/realms/schoolfee` as issuer.
- API authentication and role mapping work.
- Super admin can create a school.
- School and staff creation outbox events are processed within two minutes.
- Email delivery succeeds.
- Parent onboarding succeeds.
- Paystack test payment reaches `SUCCESS`.
- Attendance and results workflows load.
- Flyway reports no migration errors.
- API, Keycloak and job logs contain no credentials.

Inspect resources:

```bash
gcloud run services list --region="$REGION"
gcloud run jobs list --region="$REGION"
gcloud scheduler jobs list --location="$REGION"
gcloud sql instances describe "$DB_INSTANCE"
```

## 16. Production hardening

Before production traffic:

- use a regional Cloud SQL instance with PITR, backups and deletion protection;
- test database restoration, not merely backup creation;
- use immutable image tags, never `latest`;
- pin secret versions for production revisions;
- configure custom domains for API and Keycloak;
- restrict Keycloak admin access where practical;
- replace Keycloak master-admin password use with a least-privilege client;
- move Keycloak to a clustered platform if identity HA is required;
- cap Cloud Run maximum instances based on PostgreSQL connection capacity;
- keep `max instances × R2DBC pool size` within the database connection budget;
- configure Cloud Monitoring uptime checks and log-based alerts;
- configure budget alerts in each project;
- enable SMS and WhatsApp only after their secrets have been added;
- run dependency and container vulnerability scans;
- document incident rollback and secret rotation procedures.

Recommended alerts:

- backend or Keycloak 5xx rate;
- Cloud Run startup failures;
- Cloud SQL CPU, memory, storage and connection saturation;
- failed outbox job executions;
- old pending/failed outbox events;
- Paystack webhook failures;
- Cloud SQL backup failures.

## 17. Subsequent backend deployments

Build an immutable image:

```bash
cd ~/IdeaProjects/school-fee-app
export IMAGE_TAG="$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M%S)"
export BACKEND_IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/schoolfee/backend:${IMAGE_TAG}"

gcloud builds submit . \
  --config=deploy/gcp/cloudbuild-backend.yaml \
  --substitutions="_REGION=${REGION},_IMAGE_TAG=${IMAGE_TAG}"
```

Update both API and job to the same application version:

```bash
gcloud run services update "$BACKEND_SERVICE" \
  --region="$REGION" \
  --image="$BACKEND_IMAGE"

gcloud run jobs update "$OUTBOX_JOB" \
  --region="$REGION" \
  --image="$BACKEND_IMAGE"
```

Run smoke tests after each deployment.

## 18. Rollback

List revisions:

```bash
gcloud run revisions list \
  --service="$BACKEND_SERVICE" \
  --region="$REGION"
```

Route traffic to a known-good revision:

```bash
gcloud run services update-traffic "$BACKEND_SERVICE" \
  --region="$REGION" \
  --to-revisions="KNOWN_GOOD_REVISION=100"
```

Roll the job back by updating it to the matching known-good image:

```bash
gcloud run jobs update "$OUTBOX_JOB" \
  --region="$REGION" \
  --image="KNOWN_GOOD_IMAGE"
```

Application rollback does not automatically undo database migrations. Every
production migration must therefore be backward-compatible with the previous
application revision.
