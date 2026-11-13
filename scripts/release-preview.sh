#!/usr/bin/env bash
# Tells what merging a branch would release, so a wrongly typed commit shows up in the pull
# request and not after the merge.
#
# Usage: scripts/release-preview.sh <base-ref> <head-ref>
#   Looks at the commits in <base-ref>..<head-ref> and the files they change. Prints a Markdown
#   report on standard output and, when the branch changes code that ships (src/main, pom.xml,
#   release/) but no commit is feat, fix, perf or breaking, a "::warning::" line for GitHub
#   Actions. Always exits 0, a warning does not fail the check. Exit status 2 for wrong usage.
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "usage: $0 <base-ref> <head-ref>" >&2
  exit 2
fi
base="$1"
head="$2"
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
range="${base}..${head}"

# the version only matters for the report; the level decides, so any base version will do
changes="$(git log -z --no-merges --format=%B "${range}" | "${dir}/next-version.sh" --changes)"
shipped="$(git diff --name-only "${base}...${head}" -- src/main pom.xml release | head -n 5)"

echo "### Release preview"
echo
if [ -n "${changes}" ]; then
  echo "Merging this pull request releases a new version. These commits decide it:"
  echo
  printf '%s\n' "${changes}" | sed 's/^/- `/; s/$/`/'
elif [ -n "${shipped}" ]; then
  echo "**Merging this pull request releases nothing, but it changes code that ships:**"
  echo
  printf '%s\n' "${shipped}" | sed 's/^/- `/; s/$/`/'
  echo
  echo "Only \`feat\`, \`fix\`, \`perf\` or a breaking change release. If users should get this, retype a commit."
  echo "::warning title=No release::This pull request changes shipped code but no commit is feat, fix, perf or breaking, so the merge releases nothing."
else
  echo "Merging this pull request releases nothing. It does not change shipped code."
fi
