#!/bin/bash
# Zitadel Post-Startup Configuration Script
#
# Creates a service account with client_credentials for JWT access token acquisition.
# Unlike Keycloak (realm JSON import), Zitadel requires imperative API calls.
#
# Outputs:
#   - target/zitadel-credentials.properties (consumed by TestRealm.createZitadelProvider())

set -e

ZITADEL_URL="http://localhost:3080"
PAT_FILE="${PAT_FILE:-/tmp/zitadel-admin-pat/zitadel-admin-sa.pat}"
OUTPUT_DIR="${OUTPUT_DIR:-.}"
OUTPUT_FILE="${OUTPUT_DIR}/target/zitadel-credentials.properties"

# Load admin PAT
echo "Waiting for Zitadel admin PAT..."
for i in $(seq 1 30); do
    if [ -f "$PAT_FILE" ] && [ -s "$PAT_FILE" ]; then
        ADMIN_PAT=$(cat "$PAT_FILE")
        echo "Admin PAT loaded."
        break
    fi
    [ "$i" -eq 30 ] && echo "ERROR: PAT not found at $PAT_FILE" && exit 1
    sleep 1
done

# Helpers
zitadel_api() {
    local method="$1" path="$2" data="$3"
    # Host header must match ZITADEL_EXTERNALDOMAIN for instance routing
    if [ -n "$data" ]; then
        curl -s -X "$method" "${ZITADEL_URL}${path}" \
            -H "Host: zitadel:3080" \
            -H "Authorization: Bearer ${ADMIN_PAT}" \
            -H "Content-Type: application/json" -d "$data"
    else
        curl -s -X "$method" "${ZITADEL_URL}${path}" \
            -H "Host: zitadel:3080" \
            -H "Authorization: Bearer ${ADMIN_PAT}" \
            -H "Content-Type: application/json"
    fi
}
json_field() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])" 2>/dev/null; }

echo "=== Configuring Zitadel for Integration Testing ==="

# Wait for management API (lags behind /debug/ready)
echo "Waiting for management API..."
for i in $(seq 1 30); do
    PROBE=$(zitadel_api POST /management/v1/projects '{"name":"probe"}' 2>/dev/null)
    PROBE_ID=$(echo "$PROBE" | json_field id)
    if [ -n "$PROBE_ID" ]; then
        zitadel_api DELETE "/management/v1/projects/${PROBE_ID}" > /dev/null 2>&1
        echo "Management API ready."
        break
    fi
    [ "$i" -eq 30 ] && echo "ERROR: Management API not ready" && exit 1
    sleep 1
done

# 1. Create project + roles
echo "Creating project..."
PROJECT_ID=$(zitadel_api POST /management/v1/projects '{"name":"integration-test","projectRoleAssertion":true}' | json_field id)
[ -z "$PROJECT_ID" ] && echo "ERROR: Failed to create project" && exit 1
echo "  Project: $PROJECT_ID"

zitadel_api POST "/management/v1/projects/${PROJECT_ID}/roles/_bulk" \
    '{"roles":[{"key":"user","displayName":"User Role","group":"default"}]}' > /dev/null

# 2. Create service account (machine user) with JWT access token type
echo "Creating service account..."
SERVICE_USER_ID=$(zitadel_api POST "/management/v1/users/machine" '{
    "userName": "zitadel-service",
    "name": "Service Account",
    "accessTokenType": "ACCESS_TOKEN_TYPE_JWT"
}' | json_field userId)
[ -z "$SERVICE_USER_ID" ] && echo "ERROR: Failed to create service account" && exit 1
echo "  Service user: $SERVICE_USER_ID"

# 3. Generate client secret on the service account for client_credentials grant
# In Zitadel, client_id = userName (not numeric ID), client_secret = generated secret
echo "Generating client secret..."
SECRET_RESPONSE=$(zitadel_api PUT "/management/v1/users/${SERVICE_USER_ID}/secret")
SERVICE_CLIENT_ID=$(echo "$SECRET_RESPONSE" | json_field clientId)
SERVICE_SECRET=$(echo "$SECRET_RESPONSE" | json_field clientSecret)
[ -z "$SERVICE_SECRET" ] && echo "ERROR: Failed to generate secret" && exit 1
echo "  Client ID: $SERVICE_CLIENT_ID (secret generated)"

# 4. Grant 'user' role to service account
echo "Granting roles..."
zitadel_api POST "/management/v1/users/${SERVICE_USER_ID}/grants" \
    "{\"projectId\":\"${PROJECT_ID}\",\"roleKeys\":[\"user\"]}" > /dev/null

# 5. Set user metadata for groups claim (Actions will read this)
echo "Setting user metadata..."
GROUPS_B64=$(echo -n '["test-group","users"]' | base64)
zitadel_api POST "/management/v1/users/${SERVICE_USER_ID}/metadata/groups" \
    "{\"value\":\"${GROUPS_B64}\"}" > /dev/null

# 6. Create Actions for custom claims (roles + groups)
echo "Creating Actions..."
ROLES_ACTION_ID=$(zitadel_api POST "/management/v1/actions" '{
    "name": "flattenRoles",
    "script": "function flattenRoles(ctx, api) { var roles = []; if (ctx.grants) { for (var i = 0; i < ctx.grants.length; i++) { if (ctx.grants[i].roles) { for (var j = 0; j < ctx.grants[i].roles.length; j++) { roles.push(ctx.grants[i].roles[j]); } } } } api.v1.claims.setClaim(\"roles\", roles); }",
    "allowedToFail": true, "timeout": "5s"
}' | json_field id)

GROUPS_ACTION_ID=$(zitadel_api POST "/management/v1/actions" '{
    "name": "injectGroups",
    "script": "function injectGroups(ctx, api) { if (ctx.metadata && ctx.metadata.groups) { try { api.v1.claims.setClaim(\"groups\", JSON.parse(ctx.metadata.groups)); } catch(e) {} } }",
    "allowedToFail": true, "timeout": "5s"
}' | json_field id)

# Attach to complement token flow (flow 2, trigger 4 = pre-access-token in v2.71+)
if [ -n "$ROLES_ACTION_ID" ] && [ -n "$GROUPS_ACTION_ID" ]; then
    zitadel_api POST "/management/v1/flows/2/trigger/4" \
        "{\"actionIds\":[\"${ROLES_ACTION_ID}\",\"${GROUPS_ACTION_ID}\"]}" > /dev/null 2>&1 || true
    zitadel_api POST "/management/v1/flows/2/trigger/5" \
        "{\"actionIds\":[\"${ROLES_ACTION_ID}\",\"${GROUPS_ACTION_ID}\"]}" > /dev/null 2>&1 || true
    echo "  Actions attached to triggers 4+5"
fi

# Write credentials
echo "Writing credentials to ${OUTPUT_FILE}..."
mkdir -p "$(dirname "$OUTPUT_FILE")"
cat > "$OUTPUT_FILE" << EOF
# Zitadel Integration Test Credentials (generated by setup.sh)
# Service account authenticates via client_credentials (client_id = userId)
zitadel.service.client-id=${SERVICE_CLIENT_ID}
zitadel.service.client-secret=${SERVICE_SECRET}
zitadel.project.id=${PROJECT_ID}
EOF

echo ""
echo "=== Zitadel Configuration Complete ==="
echo "  Project: $PROJECT_ID | Service: $SERVICE_CLIENT_ID ($SERVICE_USER_ID)"
