#!/usr/bin/env bash
# Runs Portfolio Lens builds and tests in Docker. Nothing is installed on the host.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE=portfolio-lens-dev
VOLUME=portfolio-lens-m2
CACHE_VOLUME=portfolio-lens-cache

usage() {
    cat <<EOF
Usage: scripts/dev.sh <command>

  test [TestClass]   unit tests, optionally only one test class
  package            build the application
  check [options]    build, then check the configuration against Portfolio
                     Performance on this machine (lens check)
  run [options]      build, then make one Lens run (lens run): writes the page
                     to scratch/out/index.html
                     Both read scratch/lens.env and keep the cache in the Docker
                     volume $CACHE_VOLUME; options go to Lens, e.g. --config <file>
  page               open scratch/out/index.html in the browser on the Mac; in the dev
                     container it is served on localhost:8765 and opened through VS Code
  shell              interactive shell in the container
  clean              delete the build output
  purge              clean, then remove the Docker image and volumes
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
    check|run)
        mvn_in_docker -q package -DskipTests
        env_file=()
        [ -f "$ROOT/scratch/lens.env" ] && env_file=(--env-file "$ROOT/scratch/lens.env")
        mkdir -p "$ROOT/scratch/out"
        tty=()
        [ -t 0 ] && [ -t 1 ] && tty=(-it)
        # PP only answers loopback requests: pp-forward.sh listens on the container's
        # 127.0.0.1:5712 and forwards to the host, so Lens keeps a loopback Host header.
        docker run --rm ${tty[@]+"${tty[@]}"} ${env_file[@]+"${env_file[@]}"} \
            -v "$CACHE_VOLUME":/cache -v "$ROOT":/src -w /src \
            -e LENS_CACHE_DIR=/cache -e LENS_OUTPUT_DIR=/src/scratch/out \
            "$IMAGE" sh -c '.devcontainer/pp-forward.sh & sleep 0.3; exec java -jar target/portfolio-lens.jar "$@"' lens "$cmd" "$@"
        ;;
    page)
        page="$ROOT/scratch/out/index.html"
        [ -f "$page" ] || { echo "No page yet: run scripts/dev.sh run first" >&2; exit 1; }
        if command -v open >/dev/null 2>&1; then
            open "$page"
        elif [ -n "${BROWSER:-}" ]; then
            # Dev container: VS Code sets BROWSER to a helper that opens a link on the Mac. It
            # does not open file:// links there, but it forwards a localhost port, so the page
            # is served inside the container (loopback only) and opened through that port.
            port="${LENS_PAGE_PORT:-8765}"
            url="http://127.0.0.1:$port/index.html"
            if ! curl -fs -o /dev/null "$url"; then
                nohup jwebserver -b 127.0.0.1 -p "$port" -d "$ROOT/scratch/out" >/tmp/lens-page.log 2>&1 &
                for _ in 1 2 3 4 5 6 7 8 9 10; do curl -fs -o /dev/null "$url" && break; sleep 0.5; done
                curl -fs -o /dev/null "$url" || { echo "Could not serve the page, see /tmp/lens-page.log" >&2; exit 1; }
            fi
            "$BROWSER" "http://localhost:$port/index.html"
        else
            echo "Open $page in a browser"
        fi
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
        docker volume rm "$CACHE_VOLUME" 2>/dev/null || true
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
