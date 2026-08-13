#!/usr/bin/env bash
#
# Shared Replit environment normalization for dev, build, and deployment.
# Vite consumes VITE_* at build time; the backend consumes CLERK_* and AUTH_* directly.

set -euo pipefail

export PORT="${PORT:-5000}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-replit}"

export VITE_CLERK_JWT_TEMPLATE="${VITE_CLERK_JWT_TEMPLATE:-aidigital-api}"
export VITE_CLERK_PUBLISHABLE_KEY="${VITE_CLERK_PUBLISHABLE_KEY:-${CLERK_PUBLISHABLE_KEY:-}}"
export CLERK_PUBLISHABLE_KEY="${CLERK_PUBLISHABLE_KEY:-${VITE_CLERK_PUBLISHABLE_KEY:-}}"

export AUTH_ALLOWED_EMAIL_DOMAIN="${AUTH_ALLOWED_EMAIL_DOMAIN:-aidigital.com}"
export AUTH_AUTHORIZED_PARTIES="${AUTH_AUTHORIZED_PARTIES:-}"
# Dev-only wildcard azp patterns (see application.yml → app.auth.authorized-party-patterns).
# The workspace preview host changes on cluster moves, so it cannot be pinned exactly.
# `-` (not `:-`) is load-bearing: replit-build.sh sources deploy-env.sh BEFORE this file,
# and deploy-env.sh clears the variable to an empty string for the published app. `:-`
# would treat that empty value as unset and hand production the dev wildcard back.
export AUTH_AUTHORIZED_PARTY_PATTERNS="${AUTH_AUTHORIZED_PARTY_PATTERNS-https://*.replit.dev:5173,https://*.replit.dev}"
export AUTH_ISSUER_URI="${AUTH_ISSUER_URI:-}"
export AUTH_JWKS_URI="${AUTH_JWKS_URI:-}"
export AUTH_AUDIENCE="${AUTH_AUDIENCE:-}"
