#!/usr/bin/env bash
# The release-pipeline check of bdi-quarkus-parent: applications OUTSIDE the reactor, with versions of their own.
#   1. sample-app (7.2.0) inherits the parent: contract, lint, governance and packaging come from it.
#   2. bom-only-app (3.0.0) imports the BOM and copies the mandatory plugin section.
#   3. Negative cases on a copy of sample-app: a packaged secret fails the lint; a direct Quarkus extension
#      fails the governance rule; a platform artifact at another version fails too.
# Requires the platform installed in the local repository (./mvnw install -DskipTests from the repository root).
# MAVEN_ARGS carries extra flags, e.g. -Dcommunity=true in a sandbox without access to the Red Hat repository.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
mvn="$root/mvnw -B -ntp ${MAVEN_ARGS:-}"

# the parent's own version and the platform version it imports must be the same release
declared=$(sed -n 's#.*<version>\(.*\)</version>.*#\1#p' "$here/../pom.xml" | head -1)
platform=$(sed -n 's#.*<bdi.platform.version>\(.*\)</bdi.platform.version>.*#\1#p' "$here/../pom.xml")
[ "$declared" = "$platform" ] || { echo "bdi-quarkus-parent $declared imports the platform at $platform: bump both"; exit 1; }

echo "== sample-app (inherits bdi-quarkus-parent, version 7.2.0)"
$mvn -f "$here/sample-app/pom.xml" clean verify
unzip -l "$here/sample-app/target/sample-app-7.2.0-quarkus-app.zip" | grep -q quarkus-app/quarkus-run.jar
unzip -p "$here/sample-app/target/sample-app-7.2.0.jar" META-INF/config-contract.json | grep -q '"sample.greeting"'
# the resolved platform artifacts are the platform's version, not the application's
$mvn -f "$here/sample-app/pom.xml" dependency:list -DincludeGroupIds=it.bancaditalia.quarkus -DoutputFile=target/deps.txt -q
grep -q "bdi-rest-jackson:jar:$platform" "$here/sample-app/target/deps.txt" || { echo "bdi-rest-jackson resolved at the wrong version:"; cat "$here/sample-app/target/deps.txt"; exit 1; }

echo "== bom-only-app (imports the BOM, version 3.0.0)"
$mvn -f "$here/bom-only-app/pom.xml" clean verify
unzip -p "$here/bom-only-app/target/bom-only-app-3.0.0.jar" META-INF/config-contract.json | grep -q '"sample.greeting"'

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
expect_failure() { # <name> <grep pattern in the output>
  if $mvn -f "$work/$1/pom.xml" -q clean verify > "$work/$1.log" 2>&1; then
    echo "$1: the build should have failed"; exit 1
  fi
  grep -q -- "$2" "$work/$1.log" || { echo "$1 failed for another reason:"; tail -40 "$work/$1.log"; exit 1; }
  echo "== $1: refused as expected ($2)"
}

cp -r "$here/sample-app" "$work/packaged-secret"
printf 'sample:\n  api-token: hunter2\nquarkus:\n  http:\n    port: 8081\n' >> "$work/packaged-secret/src/main/resources/application.yaml"
expect_failure packaged-secret "environment-owned configuration"

cp -r "$here/sample-app" "$work/direct-extension"
sed -i 's#<dependencies>#<dependencies><dependency><groupId>io.quarkus</groupId><artifactId>quarkus-rest</artifactId></dependency>#' "$work/direct-extension/pom.xml"
expect_failure direct-extension "bdi-\* starter"

cp -r "$here/sample-app" "$work/version-override"
sed -i 's#<artifactId>bdi-rest-jackson</artifactId>#<artifactId>bdi-rest-jackson</artifactId><version>0.0.1</version>#' "$work/version-override/pom.xml"
expect_failure version-override "0.0.1"

echo "== bdi-quarkus-parent: all shapes verified"
