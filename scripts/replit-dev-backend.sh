#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/replit-env.sh
# Workspace-only: build decks from the new template. The published app boots
# through replit-run.sh, which never sources this, and keeps the old template.
source scripts/lib/new-template-env.sh
mvn -f backend/pom.xml -DskipTests -Djacoco.skip=true -Dcheckstyle.skip=true -Dskip.frontend=true install
exec mvn -f backend/application/pom.xml -DskipTests -Djacoco.skip=true -Dcheckstyle.skip=true -Dskip.frontend=true spring-boot:run
