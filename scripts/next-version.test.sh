#!/usr/bin/env bash
# Tests for next-version.sh. Run: bash scripts/next-version.test.sh
set -uo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/next-version.sh"
failed=0
total=0

report() { # name expected actual
  total=$((total + 1))
  if [ "$2" = "$3" ]; then
    echo "ok   $1"
  else
    echo "FAIL $1: expected [$2] actual [$3]"
    failed=$((failed + 1))
  fi
}

# check name expected last input   (input is a printf format with \0 separators)
check() {
  local actual
  actual=$(printf "$4" | "$script" "$3")
  report "$1" "$2" "$actual"
}

check_changes() { # name expected input
  local actual
  actual=$(printf "$3" | "$script" --changes)
  report "$1" "$2" "$actual"
}

check_status() { # name expected-status args...
  local name="$1" expected="$2" status=0
  shift 2
  printf '' | "$script" "$@" >/dev/null 2>&1 || status=$?
  report "$name" "$expected" "$status"
}

check "fix and docs -> patch" "1.1.1" 1.1.0 'fix: a\0docs: b\0docs: c\0'
check "two feat and fix -> minor" "1.2.0" 1.1.1 'feat: a\0feat: b\0fix: c\0'
check "feat with ! -> major" "2.0.0" 1.2.0 'feat(job)!: rename a setting\0'
check "BREAKING CHANGE footer -> major" "2.0.0" 1.2.0 'fix: something\n\nBREAKING CHANGE: the setting is gone\0'
check "fix! -> major" "2.0.0" 1.2.0 'fix!: something\0'
check "refactor! -> major" "2.0.0" 1.2.0 'refactor!: something\0'
check "perf -> patch" "1.2.1" 1.2.0 'perf(page): faster table\0'
check "no convention -> nothing" "" 1.2.0 'Update the readme\0'
check "docs and chore only -> nothing" "" 1.2.0 'docs: a\0chore(release): 1.2.0\0'
check "empty input -> nothing" "" 1.2.0 ''
check "marker mid-line -> nothing" "" 1.2.0 'docs: notes\n\nThis mentions BREAKING CHANGE: in the middle of a sentence\0'
check "capital type -> nothing" "" 1.2.0 'Feat: capital letter\0'
check "minor 9 -> 10" "1.10.0" 1.9.9 'feat: x\0'
check "last record without NUL" "1.0.1" 1.0.0 'docs: a\0fix: b'

check_changes "--changes lists releasable subjects" \
  "$(printf 'feat: a\nfix(page): c\nrefactor!: e')" \
  'feat: a\0docs: b\0fix(page): c\0chore: d\0refactor!: e\0'
check_changes "--changes with only docs" "" 'docs: b\0'

check_status "missing version -> status 2" 2
check_status "malformed version -> status 2" 2 1.2

echo "$((total - failed)) of $total cases ok"
[ "$failed" -eq 0 ] || exit 1
