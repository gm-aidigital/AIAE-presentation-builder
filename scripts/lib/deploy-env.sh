#!/usr/bin/env bash
#
# deploy-env.sh — maps PRD_*-prefixed overrides onto their canonical names.
#
# Sourced (never executed) by replit-build.sh and replit-run.sh. The .replit
# [deployment] section is the only caller of those two scripts; the Replit
# workspace boots the app through [workflows] instead and never sources this
# file. So the base variables hold the DEV Clerk instance and are what the
# workspace uses, while the published app gets the PRD_ values.
#
# To give the published app its own value for <NAME>, set PRD_<NAME> in
# Replit Configurations (or Secrets, for the secret key). Leave PRD_<NAME>
# unset to share the base value across both environments.
#
# Sourced with `set -u` active, hence the `:-` guards on indirect lookups.

DEPLOY_OVERRIDABLE_VARS=(
  CLERK_PUBLISHABLE_KEY
  CLERK_SECRET_KEY
  AUTH_AUTHORIZED_PARTIES
  APP_SECURITY_CSP_FRAME_ANCESTORS
)

for _deploy_var in "${DEPLOY_OVERRIDABLE_VARS[@]}"; do
  _deploy_src="PRD_${_deploy_var}"
  if [ -n "${!_deploy_src:-}" ]; then
    export "${_deploy_var}=${!_deploy_src}"
    # Log names only — CLERK_SECRET_KEY must never reach the build log.
    echo "[deploy-env] ${_deploy_var} <- ${_deploy_src}"
  else
    echo "[deploy-env] WARNING: ${_deploy_src} is unset;" \
      "the published app will use the base ${_deploy_var}" >&2
  fi
done

unset _deploy_var _deploy_src

# Wildcard azp patterns are a workspace-only convenience (ephemeral Replit preview
# hostnames). The published app must never inherit them from the base environment, so
# clear the variable unconditionally instead of listing it above as PRD_-overridable.
# With it blank, ClerkJwtClaimsValidator matches AUTH_AUTHORIZED_PARTIES exactly.
export AUTH_AUTHORIZED_PARTY_PATTERNS=""
echo "[deploy-env] AUTH_AUTHORIZED_PARTY_PATTERNS cleared (exact azp match only)"

# The EOM deck template is being migrated: the new template is switched on in the Replit workspace only,
# and the published app must keep building EOM decks from the EOC template until that work is signed off.
# The workspace and the published app share this environment, so a value set for testing would otherwise
# reach production on the next deploy. Clear it unconditionally — the same treatment
# AUTH_AUTHORIZED_PARTY_PATTERNS gets above — instead of relying on nobody setting it.
# To roll the new EOM template out to production, delete these two lines deliberately.
export EOM_SLIDES_TEMPLATE_ID=""
echo "[deploy-env] EOM_SLIDES_TEMPLATE_ID cleared (published app keeps the EOC-based EOM deck)"
