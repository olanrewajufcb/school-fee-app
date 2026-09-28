#!/bin/bash
# deploy-backend.sh
set -euo pipefail

# Check environment argument
if [ "$#" -ne 1 ] || { [ "$1" != "test" ] && [ "$1" != "prod" ]; }; then
    echo "Usage: $0 [test|prod]"
    exit 1
fi

ENV_FILE="deploy/gcp/$1.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "Error: Environment file '$ENV_FILE' not found."
    exit 1
fi

# Check prerequisites
for cmd in envsubst gcloud grep; do
    if ! command -v "$cmd" &> /dev/null; then
        echo "Error: Required command '$cmd' is not installed or not in PATH."
        exit 1
    fi
done

# Temporary manifest
TEMP_YAML=$(mktemp /tmp/edtech-backend-XXXXXX.yaml)
trap 'rm -f "$TEMP_YAML"' EXIT

# Source environment variables
echo "Loading $1 environment from $ENV_FILE..."
PRESET_IMAGE_TAG="${IMAGE_TAG:-}"
# shellcheck source=/dev/null
source "$ENV_FILE"
if [ -n "$PRESET_IMAGE_TAG" ]; then
    export IMAGE_TAG="$PRESET_IMAGE_TAG"
fi

# Generate YAML
echo "Generating configuration..."
envsubst < deploy/gcp/cloud-run-backend.yaml > "$TEMP_YAML"

# Validate placeholders (ignoring comment lines)
if grep -v '^[[:space:]]*#' "$TEMP_YAML" | grep -n '\${'; then
    echo "Error: Unresolved deployment placeholders remain in generated manifest:"
    grep -v '^[[:space:]]*#' "$TEMP_YAML" | grep -n '\${'
    exit 1
fi

# Show what will be deployed
echo "Deploying to $ENVIRONMENT in region $REGION..."
grep "name:" "$TEMP_YAML" | head -1

# Deploy
gcloud run services replace "$TEMP_YAML" --region "$REGION" --project "$GCP_PROJECT_ID"

echo "Deployment to $ENVIRONMENT complete!"