#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
"${MVN:-mvn}" -B -ntp dependency:build-classpath -Dmdep.outputFile=target/probe-classpath.txt
mkdir -p target/probe-classes
probe_classpath="target/classes:$(cat target/probe-classpath.txt)"
javac --release 25 -proc:none -cp "$probe_classpath" -d target/probe-classes integration-test/PayoutProbe.java
cp integration-test/plugin.yml target/probe-classes/plugin.yml
jar --create --file target/ExcellentJobsPayoutProbe.jar -C target/probe-classes .
