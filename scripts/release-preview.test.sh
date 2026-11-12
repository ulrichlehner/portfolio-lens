#!/usr/bin/env bash
# Tests for release-preview.sh. Run: bash scripts/release-preview.test.sh
set -uo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/release-preview.sh"
failed=0
total=0

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
cd "${work}"
mkdir scripts && cp "$(dirname "${script}")/next-version.sh" scripts/
git init -q -b main .
git config user.email t@example.invalid
git config user.name t
git config commit.gpgsign false
mkdir -p src/main docs
echo a >src/main/a.txt && git add -A && git commit -q -m "chore: start"

# branch name, commit subject, file to change; prints the report
run() {
  git checkout -q main && git checkout -q -b "$1"
  echo "$1" >"$3" && git add -A && git commit -q -m "$2"
  bash "${script}" main "$1"
}

expect() { # name pattern output
  total=$((total + 1))
  if printf '%s\n' "$3" | grep -Eq -- "$2"; then
    echo "ok   $1"
  else
    echo "FAIL $1: [$2] not in output:"
    printf '%s\n' "$3"
    failed=$((failed + 1))
  fi
}

expect_not() { # name pattern output
  total=$((total + 1))
  if printf '%s\n' "$3" | grep -Eq -- "$2"; then
    echo "FAIL $1: [$2] found in output:"
    printf '%s\n' "$3"
    failed=$((failed + 1))
  else
    echo "ok   $1"
  fi
}

out="$(run b1 'style(page): show sums' src/main/a.txt)"
expect "style on shipped code warns" '^::warning' "${out}"
expect "style on shipped code lists the file" 'src/main/a.txt' "${out}"

out="$(run b2 'feat(page): show sums' src/main/a.txt)"
expect "feat releases" 'releases a new version' "${out}"
expect_not "feat does not warn" '^::warning' "${out}"

out="$(run b3 'docs: notes' docs/n.md)"
expect "docs only releases nothing" 'does not change shipped code' "${out}"
expect_not "docs only does not warn" '^::warning' "${out}"

total=$((total + 1))
status=0
bash "${script}" main >/dev/null 2>&1 || status=$?
if [ "${status}" = 2 ]; then echo "ok   missing argument -> status 2"; else echo "FAIL missing argument: ${status}"; failed=$((failed + 1)); fi

echo "${total} checks, ${failed} failed"
[ "${failed}" -eq 0 ]
