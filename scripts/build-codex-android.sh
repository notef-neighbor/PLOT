#!/bin/sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
codex_version="${CODEX_VERSION:-0.147.0}"
release_tag="rust-v$codex_version"
asset_name="codex-app-server-aarch64-unknown-linux-musl.tar.gz"
download_url="https://github.com/openai/codex/releases/download/$release_tag/$asset_name"
tools_root="$project_root/.tools/codex-app-server/$codex_version"
archive="$tools_root/$asset_name"
binary="$tools_root/codex-app-server-aarch64-unknown-linux-musl"
destination_dir="$project_root/app/src/main/jniLibs/arm64-v8a"
destination="$destination_dir/libcodex_android.so"

mkdir -p "$tools_root" "$destination_dir"

if [ ! -f "$archive" ]; then
  curl --fail --location --retry 10 --output "$archive" "$download_url"
fi

if [ "$codex_version" = "0.147.0" ]; then
  expected_archive_sha256="0bb78fa190cdcbc689dc34d34358b054a5c7e81a6d899d97065ea139aeb3ba9c"
  actual_archive_sha256=$(shasum -a 256 "$archive" | awk '{print $1}')
  if [ "$actual_archive_sha256" != "$expected_archive_sha256" ]; then
    echo "Codex archive checksum mismatch" >&2
    exit 1
  fi
fi

tar -xzf "$archive" -C "$tools_root"
cp "$binary" "$destination"
chmod 755 "$destination"

echo "Packaged Codex App Server $codex_version for Android arm64 at $destination"
