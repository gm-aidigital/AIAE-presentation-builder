#!/usr/bin/env bash
# harvest-bootstrap.sh — source this to set up the POC→scaffold harvest environment.
#
#   source scripts/harvest-bootstrap.sh
#
# Provides: JAVA_HOME (Java 21), `mvnc`, `port_file`, `verify_module`, `verify_full`,
# `verify_gates`. Safe to source repeatedly. See migration-plan/EXECUTE-HARVEST.md.

# Paths (override by exporting before sourcing).
: "${POC:=/Users/gleb3/Desktop/Presentation-Builder-POC}"
: "${NEW:=/Users/gleb3/Desktop/report-constructor}"
export POC NEW

# --- Java 21 (Corretto) — the default `java` on this machine is 17 -----------
_j21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
if [ -n "${_j21}" ]; then
  export JAVA_HOME="${_j21}"
  echo "[harvest] JAVA_HOME=${JAVA_HOME}"
else
  echo "[harvest] WARNING: no Java 21 found via /usr/libexec/java_home -v 21 — set JAVA_HOME manually." >&2
fi

# Maven with the right JDK + the harvest-time flags (skip frontend; node is broken here).
mvnc() { ( cd "${NEW}" && JAVA_HOME="${JAVA_HOME}" mvn -Dskip.frontend=true "$@" ); }

# --- port_file <poc-relpath> <new-relpath> -----------------------------------
# Copy one file from the POC to the scaffold, renaming the Java package
# com.aidigital.reportingtool → com.aidigital.reportconstructor. Creates parent dirs.
port_file() {
  local src="${POC}/$1" dst="${NEW}/$2"
  if [ ! -f "${src}" ]; then echo "[port_file] MISSING source: ${src}" >&2; return 1; fi
  mkdir -p "$(dirname "${dst}")"
  sed 's/com\.aidigital\.reportingtool/com.aidigital.reportconstructor/g' "${src}" > "${dst}"
  echo "[port_file] ${1}  ->  ${2}"
}

# --- verification wrappers ---------------------------------------------------
# Compile one module + its deps, e.g. verify_module backend/external-services
verify_module() { mvnc -pl "$1" -am compile; }

# Full reactor test, excluding the Docker-only Liquibase Testcontainers test.
verify_full() {
  mvnc -f backend/pom.xml -Dtest='!LiquibaseChangelogSmokeTest' -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false test
}

# Architecture grep gates.
verify_gates() { ( cd "${NEW}" && bash scripts/structure-lint.sh && bash scripts/verify-gates.sh ); }

# Quick check that no harvested file kept the old package.
check_no_old_package() {
  if grep -rln "com.aidigital.reportingtool" "${NEW}/backend" 2>/dev/null; then
    echo "[harvest] ^ files still reference com.aidigital.reportingtool — fix before finishing." >&2; return 1
  fi
  echo "[harvest] OK: no com.aidigital.reportingtool references in ${NEW}/backend"
}

echo "[harvest] ready. helpers: mvnc, port_file <src> <dst>, verify_module <m>, verify_full, verify_gates, check_no_old_package"
echo "[harvest] plan: migration-plan/92-poc-harvest-plan.md  |  brief: migration-plan/EXECUTE-HARVEST.md"
