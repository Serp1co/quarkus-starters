#!/usr/bin/env bash
# Two instances of the packaged artifact against the same database, as two VMs behind the load balancer would be:
# one environment file, one instance file each (node name, XA object store, ports), one secrets file. Shows that
# the settlement timer (Quartz, clustered JDBC store) fires once per interval across the cluster, on either node,
# and that the Quartz cluster table lists both instances.
#
# Precedence, as verified on RHBQ 3.33: system properties > files in QUARKUS_CONFIG_LOCATIONS (first listed wins)
# > environment variables > application.yaml in the artifact > bdi-config-* defaults.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
APP="$HERE/../target/quarkus-app/quarkus-run.jar"
[ -f "$APP" ] || { echo "Build the artifact first: ./mvnw package" >&2; exit 1; }
WORK="${TMPDIR:-/tmp}/poc-b-cluster"; mkdir -p "$WORK"
INTERVAL="${SETTLEMENT_INTERVAL:-5s}"
export QUARKUS_PROFILE="${QUARKUS_PROFILE:-uat}"

start() { # node
  # the object store of the example points under /var/lib; the demo keeps it under $WORK
  sed "s|/var/lib/bdi/poc-b-ejb-heavy|$WORK|" "$HERE/config/instance-$1.yaml" > "$WORK/instance-$1.yaml"
  printf 'settlement:\n  interval: %s\n' "$INTERVAL" > "$WORK/demo-interval.yaml"
  QUARKUS_CONFIG_LOCATIONS="$HERE/secrets/secrets.yaml,$WORK/instance-$1.yaml,$WORK/demo-interval.yaml,$HERE/config/application-${QUARKUS_PROFILE}.yaml" \
    java -jar "$APP" > "$WORK/$1.log" 2>&1 &
  echo $!
}
P1=$(start node-a); P2=$(start node-b)
trap 'kill $P1 $P2 2>/dev/null' EXIT
for p in 9000 9001; do until curl -sf -o /dev/null "http://localhost:$p/q/health/ready"; do sleep 1; done; done
echo "both instances ready; the cluster as the database sees it:"; curl -s localhost:8080/api/settlement/cluster; echo
echo "node names: $(curl -s localhost:9000/q/platform | jq -r '.config[] | select(.key=="quarkus.transaction-manager.node-name") | .value') / $(curl -s localhost:9001/q/platform | jq -r '.config[] | select(.key=="quarkus.transaction-manager.node-name") | .value')"
SINCE=$(date -u +%Y-%m-%dT%H:%M:%S)
for i in $(seq 1 6); do curl -s -o /dev/null -X POST localhost:8080/api/instructions -H 'Content-Type: application/json' -d "{\"reference\":\"DEMO-$RANDOM-$i\",\"amount\":\"$((i * 10)).00\"}"; done
echo "waiting 26s for the timer ($INTERVAL interval): expect about 5 runs, one per interval, never two for the same fire"
sleep 26
curl -s localhost:8080/api/settlement/runs | jq -r --arg since "$SINCE" '.[] | select(.startedAt > $since) | "\(.startedAt[11:19]) \(.node) settled=\(.settled)"' | sort
