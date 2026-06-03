#!/usr/bin/env bash
#
# replit-build.sh — invoked by .replit [deployment].build.
#
# One Maven invocation builds BOTH the React SPA and the Spring fat jar:
#   - openapi-generator (generate-sources) emits Spring interfaces from openapi.yaml
#   - frontend-maven-plugin (generate-resources → process-resources) runs
#     npm ci → npm run generate:api → npm run build → outputs to
#     ../backend/application/src/main/resources/static/
#   - spring-boot-maven-plugin (package) repackages everything (jar + SPA + changelogs) into one fat jar
#
# Skip frontend during pure-backend local dev with -Dskip.frontend=true; never in deployment.

set -euo pipefail

cd "$(dirname "$0")/.."   # works from root/scripts and scaffold/scripts

if [ -f scripts/structure-lint.sh ]; then
  bash scripts/structure-lint.sh
fi

exec mvn -f backend/pom.xml -B -DskipTests package
