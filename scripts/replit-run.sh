#!/usr/bin/env bash
#
# replit-run.sh — invoked by .replit [deployment].run.
#
# Picks the application jar from the standard Maven output dir. Excludes
# the `.original` jar that spring-boot-maven-plugin keeps alongside the
# repackaged fat jar (running .original results in "no main manifest").

set -euo pipefail

cd "$(dirname "$0")/.."

# Swap in the published-app values before the JVM starts: the backend reads
# CLERK_PUBLISHABLE_KEY (issuer/JWKS derivation) and CLERK_SECRET_KEY (Clerk
# Backend API) from the environment at runtime.
if [ -f scripts/lib/deploy-env.sh ]; then
  # shellcheck source=lib/deploy-env.sh
  . scripts/lib/deploy-env.sh
fi

# Prefer the extracted (exploded) layout produced by replit-build.sh for a
# faster cold start; fall back to the fat jar if extraction is absent.
JAR="$(ls backend/application/target/extracted/*.jar 2>/dev/null | grep -v '\.original$' | head -n1)"
if [ -z "${JAR}" ]; then
  JAR="$(ls backend/application/target/*.jar 2>/dev/null | grep -v '\.original$' | head -n1)"
fi
if [ -z "${JAR}" ]; then
  echo "ERROR: no jar in backend/application/target/. Run replit-build.sh first." >&2
  exit 1
fi

# Fast-start JVM flags: TieredStopAtLevel=1 (C1-only) slashes JIT overhead
# during Spring init on the throttled Reserved VM; JMX adds nothing here.
# MaxRAMPercentage=75 gives the JVM an explicit container-aware heap ceiling
# instead of the ergonomic ~25%-of-RAM default, which is too small to survive
# a large in-memory report/export operation on this VM's limited RAM.
exec java -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=75 -Dspring.jmx.enabled=false -jar "${JAR}"
