# Google Cloud deployment assets

Use [`DEPLOYMENT_GUIDE.md`](./DEPLOYMENT_GUIDE.md) as the canonical deployment
procedure. It covers test and production projects, private networking, Cloud
SQL, Keycloak, Secret Manager, the backend service, the outbox job, Cloud
Scheduler, Vercel and Paystack.

## Files

- `DEPLOYMENT_GUIDE.md` — complete first-deployment and operations guide.
- `cloudbuild-backend.yaml` — builds and pushes the backend image only.
- `cloud-run-backend.yaml` — optional backend service template equivalent to
  the guide's `gcloud run deploy` command.
- `keycloak/Dockerfile` — optimized, pinned Keycloak image.
- `test.env` and `prod.env` — non-secret placeholders for rendering the
  optional backend service template.

## Build the backend

Run from the repository root because `gradlew`, `settings.gradle`, and
`gradle/wrapper` live there:

```bash
export REGION=europe-west1
export IMAGE_TAG="$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M%S)"

gcloud builds submit . \
  --config=deploy/gcp/cloudbuild-backend.yaml \
  --substitutions="_REGION=${REGION},_IMAGE_TAG=${IMAGE_TAG}"
```

The build configuration deliberately does not deploy Cloud Run. This allows
the first image build to succeed before a service exists and keeps deployment
configuration changes explicit.

## Optional service-template deployment

Populate the environment file first. Never place secret values in it:

```bash
source "deploy/gcp/${ENVIRONMENT}.env"

envsubst < deploy/gcp/cloud-run-backend.yaml \
  > "/tmp/schoolfee-backend-${ENVIRONMENT}.yaml"

if grep -n '\${' "/tmp/schoolfee-backend-${ENVIRONMENT}.yaml"; then
  echo "Unresolved deployment placeholders remain"
  exit 1
fi

gcloud run services replace \
  "/tmp/schoolfee-backend-${ENVIRONMENT}.yaml" \
  --region="$REGION"
```

The service account, VPC/subnet, Artifact Registry image and every referenced
Secret Manager secret must already exist. The complete guide creates them.
