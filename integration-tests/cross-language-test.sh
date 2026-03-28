#!/usr/bin/env bash
#
# Cross-language round-trip test for protoc-gen-jsonarray.
#
# Generates Java and Python code from user.proto and address.proto, then:
#   1. Runs a Java test that serializes a User to JSON
#   2. Runs a Python test that deserializes the Java-produced JSON and verifies fields
#   3. Verifies both languages produce identical JSON output
#
# Prerequisites: protoc, javac, java, python3 on PATH
# Uses /tmp for generated code and compilation artifacts
# No external dependencies -- uses built-in JsonArrayWriter/Reader

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROTO_DIR="$PROJECT_ROOT/test-protos/src/main/proto"
PLUGIN_JAR="$PROJECT_ROOT/plugin/build/libs/protoc-gen-jsonarray.jar"
RUNTIME_DIR="$PROJECT_ROOT/runtime/java/src/main/java"
PLUGIN_SCRIPT="$PROJECT_ROOT/protoc-gen-jsonarray"
WORK_DIR="/tmp/jsonarray-cross-lang-test-$$"

EXPECTED_JSON='["Alice","Smith",30,["123 Main Street","Springfield","IL",62704]]'

tests_passed=0
tests_failed=0

pass_test() {
    echo "  PASS: $1"
    tests_passed=$((tests_passed + 1))
}

fail_test() {
    echo "  FAIL: $1"
    echo "    $2"
    tests_failed=$((tests_failed + 1))
}

cleanup() {
    if [ -d "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}
trap cleanup EXIT

# ── Preflight checks ──────────────────────────────────────────────────

echo "=== Preflight checks ==="

for cmd in protoc javac java python3; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: Required command '$cmd' not found on PATH"
        exit 1
    fi
    echo "  Found: $(command -v "$cmd")"
done

if [ ! -f "$PLUGIN_JAR" ]; then
    echo "ERROR: Plugin JAR not found at $PLUGIN_JAR"
    echo "  Run: ./gradlew :plugin:shadowJar"
    exit 1
fi
echo "  Found plugin JAR: $PLUGIN_JAR"

echo "  Runtime source: $RUNTIME_DIR"

echo ""

# ── Setup working directory ───────────────────────────────────────────

mkdir -p "$WORK_DIR/java-gen" "$WORK_DIR/python-gen" "$WORK_DIR/java-classes"

# ── Generate code ─────────────────────────────────────────────────────

echo "=== Generating code ==="

echo "  Generating Java code..."
protoc \
    --plugin=protoc-gen-jsonarray="$PLUGIN_SCRIPT" \
    --jsonarray_out="lang=java:$WORK_DIR/java-gen" \
    --proto_path="$PROTO_DIR" \
    "$PROTO_DIR/address.proto" "$PROTO_DIR/user.proto"
echo "  Java code generated."

echo "  Generating Python code..."
protoc \
    --plugin=protoc-gen-jsonarray="$PLUGIN_SCRIPT" \
    --jsonarray_out="lang=python:$WORK_DIR/python-gen" \
    --proto_path="$PROTO_DIR" \
    "$PROTO_DIR/address.proto" "$PROTO_DIR/user.proto"
echo "  Python code generated."

# Create __init__.py files for the Python package
find "$WORK_DIR/python-gen" -type d -exec touch {}/__init__.py \;

echo ""

# ── Java test ─────────────────────────────────────────────────────────

echo "=== Running Java round-trip test ==="

JAVA_CP="$WORK_DIR/java-gen:$RUNTIME_DIR"

# Copy the Java test into the work directory
cp "$SCRIPT_DIR/java/UserRoundTripTest.java" "$WORK_DIR/java-gen/UserRoundTripTest.java"

# Compile (generated code + runtime sources + test)
echo "  Compiling Java code..."
javac -cp "$JAVA_CP" \
    -d "$WORK_DIR/java-classes" \
    "$RUNTIME_DIR/dev/protocgen/textcodecs/jsonarray/runtime/JsonArrayWriter.java" \
    "$RUNTIME_DIR/dev/protocgen/textcodecs/jsonarray/runtime/JsonArrayReader.java" \
    "$WORK_DIR/java-gen/com/example/Address.java" \
    "$WORK_DIR/java-gen/com/example/User.java" \
    "$WORK_DIR/java-gen/UserRoundTripTest.java"
echo "  Compilation successful."

# Run
echo "  Running Java test..."
JAVA_OUTPUT=$(java -cp "$WORK_DIR/java-classes" UserRoundTripTest 2>&1) || true
echo "$JAVA_OUTPUT"

# Extract the serialized JSON from Java output
JAVA_JSON=$(echo "$JAVA_OUTPUT" | grep "Serialized:" | head -1 | sed 's/.*Serialized: //')
echo ""

if echo "$JAVA_OUTPUT" | grep -q "FAIL:"; then
    fail_test "Java round-trip test" "See output above"
else
    pass_test "Java round-trip test"
fi

# ── Python test ───────────────────────────────────────────────────────

echo "=== Running Python round-trip test ==="

echo "  Running Python test with Java-produced JSON..."
PYTHON_OUTPUT=$(PYTHONPATH="$WORK_DIR/python-gen" python3 "$SCRIPT_DIR/python/user_round_trip_test.py" "$JAVA_JSON" 2>&1) || true
echo "$PYTHON_OUTPUT"

# Extract the serialized JSON from Python output
PYTHON_JSON=$(echo "$PYTHON_OUTPUT" | grep "Serialized:" | head -1 | sed 's/.*Serialized: //')
echo ""

if echo "$PYTHON_OUTPUT" | grep -q "FAIL:"; then
    fail_test "Python round-trip test" "See output above"
else
    pass_test "Python round-trip test"
fi

# ── Cross-language comparison ─────────────────────────────────────────

echo "=== Cross-language JSON comparison ==="
echo "  Java JSON:   $JAVA_JSON"
echo "  Python JSON: $PYTHON_JSON"

if [ "$JAVA_JSON" = "$PYTHON_JSON" ]; then
    pass_test "Cross-language JSON output matches"
else
    # Try normalized comparison (parse and re-serialize)
    JAVA_NORMALIZED=$(python3 -c "import json,sys; print(json.dumps(json.loads(sys.argv[1]),separators=(',',':')))" "$JAVA_JSON" 2>/dev/null || echo "PARSE_ERROR")
    PYTHON_NORMALIZED=$(python3 -c "import json,sys; print(json.dumps(json.loads(sys.argv[1]),separators=(',',':')))" "$PYTHON_JSON" 2>/dev/null || echo "PARSE_ERROR")
    if [ "$JAVA_NORMALIZED" = "$PYTHON_NORMALIZED" ]; then
        pass_test "Cross-language JSON output matches (after normalization)"
    else
        fail_test "Cross-language JSON output mismatch" "Java: $JAVA_JSON  Python: $PYTHON_JSON"
    fi
fi

echo ""

# ── Expected JSON comparison ──────────────────────────────────────────

echo "=== Expected JSON comparison ==="
echo "  Expected: $EXPECTED_JSON"

if [ "$JAVA_JSON" = "$EXPECTED_JSON" ]; then
    pass_test "Java JSON matches expected output"
else
    fail_test "Java JSON does not match expected" "got: $JAVA_JSON"
fi

if [ "$PYTHON_JSON" = "$EXPECTED_JSON" ]; then
    pass_test "Python JSON matches expected output"
else
    # Python's json.dumps uses ", " and ": " by default; normalize
    PYTHON_NORMALIZED=$(python3 -c "import json,sys; print(json.dumps(json.loads(sys.argv[1]),separators=(',',':')))" "$PYTHON_JSON" 2>/dev/null || echo "PARSE_ERROR")
    EXPECTED_NORMALIZED=$(python3 -c "import json,sys; print(json.dumps(json.loads(sys.argv[1]),separators=(',',':')))" "$EXPECTED_JSON" 2>/dev/null || echo "PARSE_ERROR")
    if [ "$PYTHON_NORMALIZED" = "$EXPECTED_NORMALIZED" ]; then
        pass_test "Python JSON matches expected output (after normalization)"
    else
        fail_test "Python JSON does not match expected" "got: $PYTHON_JSON"
    fi
fi

echo ""

# ── Summary ───────────────────────────────────────────────────────────

echo "============================================"
echo "  Cross-language test summary"
echo "  Passed: $tests_passed"
echo "  Failed: $tests_failed"
echo "============================================"

if [ "$tests_failed" -gt 0 ]; then
    exit 1
fi
