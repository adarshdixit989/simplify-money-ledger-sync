#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
echo "==> compiling"
rm -rf build/selfcheck && mkdir -p build/selfcheck
javac -d build/selfcheck $(find src/main/java -name '*.java' ! -name 'MongoDocumentStore.java' ! -name 'DocumentStoreBenchmark.java')
echo
echo "==> running"
java -cp build/selfcheck in.simplifymoney.ledgersync.SelfCheck "$@"