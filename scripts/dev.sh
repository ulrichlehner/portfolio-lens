#!/usr/bin/env bash
# Runs Portfolio Lens builds and tests in Docker. Nothing is installed on the host.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE=portfolio-lens-dev
VOLUME=portfolio-lens-m2

usage() {
    cat <<EOF
Usage: scripts/dev.sh <command>

  test [TestClass]   unit tests, optionally only one test class
  package            build the application
  shell              interactive shell in the container
  clean              delete the build output
  purge              clean, then remove the Docker image and volume
EOF
}

image() {
    docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" "$ROOT/.devcontainer"
}

require_pom() {
    [ -f "$ROOT/pom.xml" ] || { echo "No pom.xml yet: the build is added by the first implementation change" >&2; exit 1; }
}

mvn_in_docker() {
    require_pom
    image
    docker run --rm -v "$VOLUME":/root/.m2 -v "$ROOT":/src -w /src "$IMAGE" mvn -B "$@"
}

cmd="${1:-}"
[ $# -gt 0 ] && shift

case "$cmd" in
    test)
        if [ $# -gt 0 ]; then
            mvn_in_docker test -Dtest="$1" -Dsurefire.failIfNoSpecifiedTests=false
        else
            mvn_in_docker verify
        fi
        ;;
    package)
        mvn_in_docker package -DskipTests
        ;;
    shell)
        image
        tty=()
        [ -t 0 ] && [ -t 1 ] && tty=(-it)
        docker run --rm ${tty[@]+"${tty[@]}"} -v "$VOLUME":/root/.m2 -v "$ROOT":/src -w /src "$IMAGE" bash
        ;;
    clean)
        rm -rf "$ROOT/target"
        ;;
    purge)
        rm -rf "$ROOT/target"
        docker image rm "$IMAGE" 2>/dev/null || true
        docker volume rm "$VOLUME" 2>/dev/null || true
        ;;
    ""|-h|--help|help)
        usage
        ;;
    *)
        echo "Unknown command: $cmd" >&2
        usage >&2
        exit 1
        ;;
esac
