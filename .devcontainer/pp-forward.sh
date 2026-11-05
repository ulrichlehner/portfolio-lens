#!/bin/sh
# Forwards the container's 127.0.0.1:${LENS_PP_PORT:-5712} to the same port on the Docker host.
#
# Portfolio Performance binds the host's 127.0.0.1 and accepts only requests whose Host header is
# a loopback address. Lens keeps talking to http://127.0.0.1:5712 inside the container, so the Host
# header stays loopback, and socat carries the connection to host.docker.internal, which Docker
# Desktop delivers to the host's loopback interface.
#
# Started in the background by the dev container (postStartCommand) and by scripts/dev.sh run.
port="${LENS_PP_PORT:-5712}"
target="${LENS_PP_FORWARD_HOST:-host.docker.internal}"

if ! command -v socat >/dev/null 2>&1; then
    echo "pp-forward: socat is missing, rebuild the dev image" >&2
    exit 1
fi

exec socat "TCP-LISTEN:${port},bind=127.0.0.1,reuseaddr,fork" "TCP:${target}:${port}"
