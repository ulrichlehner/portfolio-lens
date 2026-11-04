#!/bin/sh
# Dev container setup, run once by VS Code after the container is created. Safe to run again.
set -e

# The checkout is bind-mounted from the host and owned by the host user, not root:
# without this git refuses it as "dubious ownership". System level, because VS Code
# copies the host's ~/.gitconfig over the global one.
git config --system --add safe.directory '*'

# Shell: Oh My Zsh plugins (aliases and completions), no theme (Starship draws the prompt).
sed -i 's/^plugins=(.*)$/plugins=(git gh docker mvn fzf)/' ~/.zshrc
sed -i 's/^ZSH_THEME=.*$/ZSH_THEME=""/' ~/.zshrc

# OpenSpec completions: writes ~/.zsh/completions/_openspec and a marked block in ~/.zshrc.
openspec completion install zsh >/dev/null

# The repository's zshrc (history, suggestions, prompt, highlighting), sourced last.
if ! grep -q '.devcontainer/zshrc' ~/.zshrc; then
  echo '[ -f "$WORKSPACE_DIR/.devcontainer/zshrc" ] && source "$WORKSPACE_DIR/.devcontainer/zshrc"' >> ~/.zshrc
fi

# GitHub's host keys, so the first fetch over SSH does not stop at a prompt.
if ! grep -q '^github.com ' /etc/ssh/ssh_known_hosts 2>/dev/null; then
  mkdir -p /etc/ssh
  ssh-keyscan -t ed25519,ecdsa,rsa github.com 2>/dev/null >> /etc/ssh/ssh_known_hosts
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh is not logged in yet: run 'gh auth login' once. The login is kept in the"
  echo "devcontainer-gh-config volume for every dev container on this machine."
fi
