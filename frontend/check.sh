#!/bin/sh
# Basic frontend checks used by the Jenkins pipeline (no build tools needed).
set -e
cd "$(dirname "$0")"

for f in index login register dashboard book track admin; do
  [ -f "public/$f.html" ] || { echo "MISSING public/$f.html"; exit 1; }
done

# every local script/stylesheet referenced from an HTML page must exist
for ref in $(grep -ho 'src="js/[^"]*"\|href="css/[^"]*"' public/*.html | sed 's/.*="\(.*\)"/\1/' | sort -u); do
  [ -f "public/$ref" ] || { echo "BROKEN REFERENCE: $ref"; exit 1; }
done

if command -v node >/dev/null 2>&1; then
  for f in public/js/*.js; do node --check "$f"; done
  echo "JavaScript syntax OK"
else
  echo "node not installed - skipping JavaScript syntax check"
fi
echo "Frontend checks passed"
