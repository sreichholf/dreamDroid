#!/usr/bin/env bash
# Installs or updates Google's official Android skills (https://github.com/android/skills)
# into .agents/skills/<name>/, flattened by skill name the way `android skills add --all`
# lays them out. .claude/skills links to .agents/skills, so Claude Code picks them up too.
#
#   scripts/update-android-skills.sh [git-ref]   # default: main
set -euo pipefail

ref="${1:-main}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$root/.agents/skills"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

git clone --quiet --depth 1 --branch "$ref" https://github.com/android/skills.git "$tmp/src"
commit="$(git -C "$tmp/src" rev-parse HEAD)"

names=()
while IFS= read -r skill_md; do
    dir="$(dirname "$skill_md")"
    name="$(basename "$dir")"
    names+=("$name")
    rm -rf "${dest:?}/$name"
    cp -R "$dir" "$dest/$name"
done < <(find "$tmp/src" -path "$tmp/src/.git" -prune -o -name SKILL.md -print | sort)

{
    echo "# Android skills"
    echo
    echo "Installed by \`scripts/update-android-skills.sh\` from"
    echo "https://github.com/android/skills at \`$commit\`. Do not edit these skills here;"
    echo "re-run the script to update them. Licensed under Apache 2.0, see \`ANDROID_SKILLS_LICENSE.txt\`."
    echo
    printf -- '- `%s`\n' "${names[@]}" | sort
} > "$dest/ANDROID_SKILLS.md"
cp "$tmp/src/LICENSE.txt" "$dest/ANDROID_SKILLS_LICENSE.txt"

echo "Installed ${#names[@]} Android skills from $commit"
