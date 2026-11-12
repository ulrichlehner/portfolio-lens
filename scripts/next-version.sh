#!/usr/bin/env bash
# Computes the next release version from Conventional Commits.
#
# Usage:
#   scripts/next-version.sh <last-final-version>
#       Reads commit messages from standard input, separated by NUL bytes
#       (git log -z --no-merges --format=%B <range>). Prints the next version
#       X.Y.Z on one line, or nothing when no release is due. Exit status 0.
#   scripts/next-version.sh --changes
#       Reads the same input and prints the subject of every releasable
#       commit, one per line, in input order.
#   A missing or malformed version gives a message on standard error and
#   exit status 2.
#
# Rule:
#   The subject (first line) is matched against ^(type)(\(scope\))?(!)?: with
#   a lower-case type. A "!" there, or any line starting with
#   "BREAKING CHANGE:" or "BREAKING-CHANGE:", means MAJOR. Otherwise feat
#   means MINOR, and fix or perf mean PATCH. Anything else raises nothing.
#   The highest level found is applied once: MAJOR gives (X+1).0.0, MINOR
#   gives X.(Y+1).0, PATCH gives X.Y.(Z+1).
set -euo pipefail

mode=version
last=
if [ "${1:-}" = "--changes" ]; then
  mode=changes
else
  last="${1:-}"
  if ! printf '%s\n' "$last" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "usage: $0 <last-final-version X.Y.Z> | --changes" >&2
    exit 2
  fi
fi

level=0 # 0 none, 1 patch, 2 minor, 3 major

# Sets the global rank (0..3) of one commit message.
rank_of() {
  local msg="$1" subject
  rank=0
  subject=$(printf '%s\n' "$msg" | head -n 1)
  if printf '%s\n' "$subject" | grep -Eq '^[a-z]+(\([^)]*\))?!: '; then
    rank=3
  elif printf '%s\n' "$msg" | grep -Eq '^BREAKING[ -]CHANGE:'; then
    rank=3
  elif printf '%s\n' "$subject" | grep -Eq '^feat(\([^)]*\))?: '; then
    rank=2
  elif printf '%s\n' "$subject" | grep -Eq '^(fix|perf)(\([^)]*\))?: '; then
    rank=1
  fi
}

process() {
  local msg="$1"
  [ -n "$msg" ] || return 0
  rank_of "$msg"
  if [ "$rank" -gt 0 ]; then
    [ "$rank" -gt "$level" ] && level=$rank
    if [ "$mode" = changes ]; then
      printf '%s\n' "$msg" | head -n 1
    fi
  fi
}

while IFS= read -r -d '' msg; do
  process "$msg"
done
# a last record without trailing NUL stays in $msg
if [ -n "${msg:-}" ]; then
  process "$msg"
fi

[ "$mode" = version ] || exit 0
[ "$level" -gt 0 ] || exit 0

major=${last%%.*}
rest=${last#*.}
minor=${rest%%.*}
patch=${rest#*.}
case $level in
  3) echo "$((major + 1)).0.0" ;;
  2) echo "$major.$((minor + 1)).0" ;;
  1) echo "$major.$minor.$((patch + 1))" ;;
esac
