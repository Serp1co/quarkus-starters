#!/usr/bin/env bash
# Runs the packaged artifact the way stage 1 (RHEL VM + systemd) does, without touching /etc: the same
# environment the AAP-managed unit sets, pointing at this directory's example rendered files.
# Not a unit file and not a launcher: both are owned by the platform roles, never by the application repo.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
APP="$HERE/../target/quarkus-app/quarkus-run.jar"
[ -f "$APP" ] || { echo "Build the artifact first: ./mvnw package" >&2; exit 1; }

export QUARKUS_CONFIG_LOCATIONS="$HERE/config/,$HERE/secrets/secrets.properties"
export QUARKUS_PROFILE="${QUARKUS_PROFILE:-uat}"

exec java ${JAVA_OPTS:-} -jar "$APP"
