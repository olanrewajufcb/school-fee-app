#!/bin/bash
# deploy-keycloak.sh
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

TEMP_YAML=$(mktemp /tmp/edtech-keycloak-XXXXXX.yaml)
trap 'rm -f "$TEMP_YAML"' EXIT

echo "Loading $1 environment from $ENV_FILE..."
# shellcheck source=/dev/null
source "$ENV_FILE"

echo "Generating configuration..."
envsubst < deploy/gcp/keycloak/cloud-run-keycloak.yml > "$TEMP_YAML"

# Validate placeholders
if grep -v '^[[:space:]]*#' "$TEMP_YAML" | grep -n '\${'; then
    echo "Error: Unresolved deployment placeholders remain in generated manifest:"
    grep -v '^[[:space:]]*#' "$TEMP_YAML" | grep -n '\${'
    exit 1
fi

echo "Deploying Keycloak to $ENVIRONMENT in region $REGION..."
gcloud run services replace "$TEMP_YAML" --region "$REGION" --project "$GCP_PROJECT_ID"

echo "Keycloak deployment to $ENVIRONMENT complete!"
