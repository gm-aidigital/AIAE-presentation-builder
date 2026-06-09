#!/usr/bin/env bash
#
# replit-run.sh — invoked by .replit [deployment].run.
#
# Picks the application jar from the standard Maven output dir. Excludes
# the `.original` jar that spring-boot-maven-plugin keeps alongside the
# repackaged fat jar (running .original results in "no main manifest").

set -euo pipefail

cd "$(dirname "$0")/.."

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
exec java -XX:TieredStopAtLevel=1 -Dspring.jmx.enabled=false -jar "${JAR}"
