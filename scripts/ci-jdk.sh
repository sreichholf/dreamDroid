#!/usr/bin/env bash
# Sourced by the agent setup scripts. Sets CI_JDK_VERSION to the JDK that CI
# uses (`java-version` in .github/actions/setup-build/action.yml, the one
# place it is set) and CI_JDK_HOME to the matching Debian/Ubuntu OpenJDK path.
#
#   source "$REPO_ROOT/scripts/ci-jdk.sh"

ci_jdk_action="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.github/actions/setup-build/action.yml"
CI_JDK_VERSION="$(sed -nE 's/^[[:space:]]*java-version:[[:space:]]*"?([0-9]+)"?[[:space:]]*$/\1/p' "$ci_jdk_action" | head -n 1)"
if [ -z "$CI_JDK_VERSION" ]; then
  echo "ci-jdk: no java-version in $ci_jdk_action" >&2
  return 1 2>/dev/null || exit 1
fi
CI_JDK_HOME="/usr/lib/jvm/java-${CI_JDK_VERSION}-openjdk-amd64"
unset ci_jdk_action
