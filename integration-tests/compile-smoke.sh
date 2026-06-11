#!/usr/bin/env bash
#
# Compile-smoke test: generates code for one language (both formats) from all
# test protos, then syntax/compile-checks the output with the language's real
# toolchain. Catches generated code that no compiler has ever parsed.
#
# Usage: compile-smoke.sh <language>
#   <language> is one of the 17 canonical names: java python javascript
#   typescript c cpp rust zig go csharp kotlin swift dart php ruby objc perl
#
# Prerequisites: protoc on PATH, plugin jars built
#   (./gradlew :plugin:shadowJar :plugin:pbtkShadowJar), and the target
#   language's toolchain on PATH. Exits 2 if the toolchain is missing.
#
# Optional env:
#   KOTLIN_CP  classpath for kotlinc (kotlinx-serialization jars if needed)

set -euo pipefail

LANG_NAME=${1:?usage: compile-smoke.sh <language>}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROTO_DIR="$PROJECT_ROOT/test-protos/src/main/proto"
JA_PLUGIN="$PROJECT_ROOT/protoc-gen-jsonarray"
PB_PLUGIN="$PROJECT_ROOT/protoc-gen-pbtkurl"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/compile-smoke-$LANG_NAME.XXXXXX")"

cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT

die() { echo "FAIL($LANG_NAME): $*" >&2; exit 1; }
skip() { echo "SKIP($LANG_NAME): $*" >&2; exit 2; }
need() { command -v "$1" >/dev/null || skip "'$1' not on PATH"; }

[ -f "$PROJECT_ROOT/plugin/build/libs/protoc-gen-jsonarray.jar" ] \
    || die "plugin jars not built; run ./gradlew :plugin:shadowJar :plugin:pbtkShadowJar"
need protoc

PROTOS=("$PROTO_DIR"/address.proto "$PROTO_DIR"/user.proto \
        "$PROTO_DIR"/kitchen_sink.proto "$PROTO_DIR"/edge_cases.proto \
        "$PROTO_DIR"/proto2_test.proto)

# ── Generate both formats ────────────────────────────────────────────────

for fmt in jsonarray pbtkurl; do
    mkdir -p "$WORK_DIR/$fmt"
done
protoc --plugin=protoc-gen-jsonarray="$JA_PLUGIN" \
       --jsonarray_out=lang="$LANG_NAME":"$WORK_DIR/jsonarray" \
       -I "$PROTO_DIR" "${PROTOS[@]}" || die "jsonarray generation failed"
protoc --plugin=protoc-gen-pbtkurl="$PB_PLUGIN" \
       --pbtkurl_out=lang="$LANG_NAME":"$WORK_DIR/pbtkurl" \
       -I "$PROTO_DIR" "${PROTOS[@]}" || die "pbtkurl generation failed"

count=$(find "$WORK_DIR" -type f | wc -l | tr -d ' ')
[ "$count" -gt 0 ] || die "no files generated"
echo "Generated $count files for $LANG_NAME"

# ── Per-language checks ──────────────────────────────────────────────────
# Each check runs once per format directory ($1 = jsonarray|pbtkurl dir).

check_java() {
    need javac
    local out="$1/__classes"; mkdir -p "$out"
    find "$1" "$PROJECT_ROOT/runtime/java/src/main/java" -name '*.java' \
        | xargs javac -d "$out" || die "javac failed in $1"
}

check_python() {
    need python3
    python3 -m compileall -q "$1" || die "py_compile failed in $1"
}

check_javascript() {
    need node
    find "$1" -name '*.js' -print0 | xargs -0 -n1 node --check \
        || die "node --check failed in $1"
}

check_typescript() {
    command -v tsc >/dev/null || command -v npx >/dev/null || skip "tsc/npx not on PATH"
    local tsc_cmd=tsc
    command -v tsc >/dev/null || tsc_cmd="npx --yes tsc"
    # shellcheck disable=SC2046
    $tsc_cmd --noEmit --strict $(find "$1" -name '*.ts') || die "tsc failed in $1"
}

check_c() {
    need gcc
    # jsonarray C needs cJSON headers; pbtk C is dependency-free.
    local extra=()
    if [ "$(basename "$1")" = jsonarray ]; then
        for p in /usr/include /opt/homebrew/include /usr/local/include; do
            [ -f "$p/cjson/cJSON.h" ] && extra=(-I "$p") && break
        done
        [ ${#extra[@]} -gt 0 ] || skip "cJSON headers not found (install libcjson-dev)"
    fi
    find "$1" -name '*.c' -print0 | xargs -0 -n1 gcc -fsyntax-only -std=c11 \
        -I "$1" -I "$PROJECT_ROOT/runtime/c/include" "${extra[@]}" \
        || die "gcc -fsyntax-only failed in $1"
}

check_cpp() {
    need g++
    local extra=()
    if [ "$(basename "$1")" = jsonarray ]; then
        for p in /usr/include /opt/homebrew/include /usr/local/include; do
            [ -f "$p/nlohmann/json.hpp" ] && extra=(-I "$p") && break
        done
        [ ${#extra[@]} -gt 0 ] || skip "nlohmann/json headers not found (install nlohmann-json3-dev)"
    fi
    find "$1" -name '*.hpp' -print0 | xargs -0 -n1 g++ -fsyntax-only -std=c++17 \
        -I "$1" -I "$PROJECT_ROOT/runtime/cpp/include" "${extra[@]}" \
        || die "g++ -fsyntax-only failed in $1"
}

check_rust() {
    need cargo
    local crate="$1/__crate"
    mkdir -p "$crate/src"
    cat > "$crate/Cargo.toml" <<'EOF'
[package]
name = "smoke"
version = "0.0.0"
edition = "2021"

[dependencies]
serde_json = "1"
base64 = "0.22"
EOF
    {
        echo "#![allow(dead_code, unused_imports)]"
        for d in "$1"/*/; do
            [ -d "$d" ] || continue
            echo "pub mod $(basename "$d");"
        done
    } > "$crate/src/lib.rs"
    for d in "$1"/*/; do
        [ -d "$d" ] || continue
        cp -R "$d" "$crate/src/$(basename "$d")"
    done
    (cd "$crate" && cargo check -q) || die "cargo check failed in $1"
}

check_zig() {
    need zig
    find "$1" -name '*.zig' -print0 | xargs -0 -n1 zig ast-check \
        || die "zig ast-check failed in $1"
}

check_go() {
    need go
    (cd "$1" && go mod init smoke >/dev/null 2>&1 && go build ./...) \
        || die "go build failed in $1"
}

check_csharp() {
    need dotnet
    local proj="$1/__proj"
    mkdir -p "$proj"
    cat > "$proj/smoke.csproj" <<'EOF'
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <Nullable>disable</Nullable>
    <EnableDefaultCompileItems>false</EnableDefaultCompileItems>
  </PropertyGroup>
  <ItemGroup>
    <Compile Include="../**/*.cs" />
  </ItemGroup>
</Project>
EOF
    dotnet build "$proj" --nologo -v q || die "dotnet build failed in $1"
}

check_kotlin() {
    need kotlinc
    local files
    files=$(find "$1" -name '*.kt')
    if [ -n "${KOTLIN_CP:-}" ]; then
        kotlinc -cp "$KOTLIN_CP" $files -d "$1/__out.jar" || die "kotlinc failed in $1"
    else
        kotlinc $files -d "$1/__out.jar" || die "kotlinc failed in $1"
    fi
}

check_swift() {
    need swiftc
    find "$1" -name '*.swift' -print0 | xargs -0 swiftc -parse \
        || die "swiftc -parse failed in $1"
}

check_dart() {
    need dart
    dart analyze --fatal-infos --no-fatal-warnings "$1" >/dev/null \
        || dart analyze "$1" || die "dart analyze failed in $1"
}

check_php() {
    need php
    find "$1" -name '*.php' -print0 | xargs -0 -n1 php -l >/dev/null \
        || die "php -l failed in $1"
}

check_ruby() {
    need ruby
    find "$1" -name '*.rb' -print0 | xargs -0 -n1 ruby -c >/dev/null \
        || die "ruby -c failed in $1"
}

check_perl() {
    need perl
    perl -MJSON::PP -MMIME::Base64 -e1 2>/dev/null || skip "JSON::PP/MIME::Base64 missing"
    if [ "$(basename "$1")" = pbtkurl ]; then
        perl -MURI::Escape -e1 2>/dev/null || skip "URI::Escape missing (install liburi-perl)"
    fi
    find "$1" -name '*.pm' -print0 | xargs -0 -n1 perl -I "$1" -c 2>/dev/null \
        || { find "$1" -name '*.pm' -exec perl -I "$1" -c {} \; ; die "perl -c failed in $1"; }
}

check_objc() {
    need clang
    case "$(uname -s)" in
        Darwin) ;;
        *) [ -d /usr/include/GNUstep ] || skip "Foundation headers unavailable (need macOS or GNUstep)" ;;
    esac
    find "$1" -name '*.m' -print0 | xargs -0 -n1 clang -fsyntax-only -fobjc-arc \
        -I "$1" || die "clang objc syntax check failed in $1"
}

case "$LANG_NAME" in
    java|python|javascript|typescript|c|cpp|rust|zig|go|csharp|kotlin|swift|dart|php|ruby|objc|perl) ;;
    *) die "unknown language '$LANG_NAME'" ;;
esac

for fmt in jsonarray pbtkurl; do
    echo "--- checking $LANG_NAME / $fmt"
    "check_$LANG_NAME" "$WORK_DIR/$fmt"
done

echo "PASS($LANG_NAME): both formats compile-checked"
