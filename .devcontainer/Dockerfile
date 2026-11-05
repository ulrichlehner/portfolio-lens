# Dev environment for Portfolio Lens: Java 21 and Maven, plus Node.js and the OpenSpec CLI
# for spec-driven planning. Used by the dev container and by scripts/dev.sh and scripts/openspec.
FROM maven:3.9-eclipse-temurin-21

# Shell tools for the dev container (.devcontainer/zshrc). socat forwards the container's
# 127.0.0.1:5712 to Portfolio Performance on the host (.devcontainer/pp-forward.sh). The Ubuntu base image strips
# /usr/share/doc, where fzf keeps its zsh key bindings, so that one folder is let back in.
RUN echo 'path-include=/usr/share/doc/fzf/*' > /etc/dpkg/dpkg.cfg.d/zz-fzf-keybindings \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        git ripgrep fzf zsh zsh-autosuggestions zsh-syntax-highlighting socat \
    && rm -rf /var/lib/apt/lists/*

# Starship prompt (.devcontainer/starship.toml), the official build for this CPU. Not the
# dev container feature: on arm64 it installed the 32-bit ARM build, which ran emulated
# and took seconds per prompt.
ARG STARSHIP_VERSION=1.27.0
RUN case "$(dpkg --print-architecture)" in \
        arm64) target=aarch64-unknown-linux-musl ;; \
        amd64) target=x86_64-unknown-linux-gnu ;; \
        *) echo "unsupported architecture" >&2; exit 1 ;; \
    esac \
    && url="https://github.com/starship/starship/releases/download/v${STARSHIP_VERSION}/starship-${target}.tar.gz" \
    && curl -fsSL -o /tmp/starship.tar.gz "$url" \
    && echo "$(curl -fsSL "$url.sha256")  /tmp/starship.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/starship.tar.gz -C /usr/local/bin starship \
    && rm /tmp/starship.tar.gz

# Node.js from the official image (OpenSpec needs Node.js 20.19 or newer)
COPY --from=node:22-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:22-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# keep in sync with the version in the generated skills (openspec update after upgrading)
ARG OPENSPEC_VERSION=1.14.3
RUN npm install -g "@fission-ai/openspec@${OPENSPEC_VERSION}" && npm cache clean --force

ENV OPENSPEC_TELEMETRY=0
