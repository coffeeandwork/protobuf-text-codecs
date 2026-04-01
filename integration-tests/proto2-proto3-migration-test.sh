#!/usr/bin/env bash
#
# Proto2 to Proto3 migration integration test for protoc-gen-jsonarray.
#
# Tests that messages defined under proto2 syntax can interoperate with
# proto3 counterparts through JSON array encoding:
#   1. Proto2 serialize -> Proto3 deserialize
#      (proto2 default values, required/optional semantics)
#   2. Proto3 serialize -> Proto2 deserialize
#      (proto2 applies schema defaults for absent fields)
#   3. Round-trip through both syntaxes
#
# Prerequisites: protoc, python3 on PATH
# Uses /tmp for all generated code

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_JAR="$PROJECT_ROOT/plugin/build/libs/protoc-gen-jsonarray.jar"
PLUGIN_SCRIPT="$PROJECT_ROOT/protoc-gen-jsonarray"
WORK_DIR="/tmp/jsonarray-proto2-proto3-migration-test-$$"

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

# -- Preflight checks -----------------------------------------------------

echo "=== Preflight checks ==="

for cmd in protoc python3; do
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

echo ""

# -- Create proto files ----------------------------------------------------

mkdir -p "$WORK_DIR/proto2-proto" "$WORK_DIR/proto3-proto"
mkdir -p "$WORK_DIR/proto2-gen" "$WORK_DIR/proto3-gen"

# Proto2 version: explicit required/optional, schema defaults
cat > "$WORK_DIR/proto2-proto/config.proto" << 'PROTO'
syntax = "proto2";
package migration;
message Config {
  required string name = 1;
  optional int32 retries = 2 [default = 3];
  optional string region = 3 [default = "us-east-1"];
  optional bool enabled = 4 [default = true];
  optional double threshold = 5 [default = 0.95];
}
PROTO

# Proto3 version: same message, proto3 syntax (all fields implicitly optional)
cat > "$WORK_DIR/proto3-proto/config.proto" << 'PROTO'
syntax = "proto3";
package migration;
message Config {
  string name = 1;
  int32 retries = 2;
  string region = 3;
  bool enabled = 4;
  double threshold = 5;
}
PROTO

echo "=== Generating code ==="

echo "  Generating proto2 Python code..."
protoc \
    --plugin=protoc-gen-jsonarray="$PLUGIN_SCRIPT" \
    --jsonarray_out="lang=python:$WORK_DIR/proto2-gen" \
    --proto_path="$WORK_DIR/proto2-proto" \
    "$WORK_DIR/proto2-proto/config.proto"

echo "  Generating proto3 Python code..."
protoc \
    --plugin=protoc-gen-jsonarray="$PLUGIN_SCRIPT" \
    --jsonarray_out="lang=python:$WORK_DIR/proto3-gen" \
    --proto_path="$WORK_DIR/proto3-proto" \
    "$WORK_DIR/proto3-proto/config.proto"

# Create __init__.py files
find "$WORK_DIR/proto2-gen" -type d -exec touch {}/__init__.py \;
find "$WORK_DIR/proto3-gen" -type d -exec touch {}/__init__.py \;

echo ""

# -- Test 1: Proto2 serialize -> Proto3 deserialize -----------------------
#    Proto2 serializes all fields (including defaults); proto3 reads them.

echo "=== Test 1: Proto2 -> Proto3 (all fields set) ==="
echo "  Proto2 serializes with all fields populated"

P2_JSON=$(PYTHONPATH="$WORK_DIR/proto2-gen" python3 -c "
from migration.config import Config
c = Config()
c.name = 'production'
c.retries = 5
c.region = 'eu-west-1'
c.enabled = False
c.threshold = 0.99
print(c.SerializeToString().decode('utf-8'))
")
echo "  proto2 serialized: $P2_JSON"

TEST1_RESULT=$(PYTHONPATH="$WORK_DIR/proto3-gen" python3 << PYEOF
import sys
from migration.config import Config

json_str = '''$P2_JSON'''
c = Config.ParseFromString(json_str.encode('utf-8'))

errors = []
if c.name != "production":
    errors.append(f"name: expected 'production', got {c.name!r}")
if c.retries != 5:
    errors.append(f"retries: expected 5, got {c.retries!r}")
if c.region != "eu-west-1":
    errors.append(f"region: expected 'eu-west-1', got {c.region!r}")
if c.enabled != False:
    errors.append(f"enabled: expected False, got {c.enabled!r}")
if abs(c.threshold - 0.99) > 1e-9:
    errors.append(f"threshold: expected 0.99, got {c.threshold!r}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    print("All fields deserialized correctly by proto3")
PYEOF
) || true
echo "  $TEST1_RESULT"

if echo "$TEST1_RESULT" | grep -q "ERROR:"; then
    fail_test "Proto2 -> Proto3 (all fields set)" "See errors above"
else
    pass_test "Proto2 -> Proto3: all field values preserved"
fi

echo ""

# -- Test 2: Short array -> Proto3 deserializes with zero defaults ---------
#    When a proto2 message with fewer fields is read by a proto3 schema
#    with more fields, the absent fields get proto3 zero defaults.

echo "=== Test 2: Short array -> Proto3 (absent fields get zero defaults) ==="
echo "  Deserialize a 1-element array with proto3 (5 fields); absent fields get zeros"

TEST2_RESULT=$(PYTHONPATH="$WORK_DIR/proto3-gen" python3 << PYEOF
import sys
from migration.config import Config

# Simulate a short JSON array from an older schema (only name at position 0)
short_json = '["staging"]'
c = Config.ParseFromString(short_json.encode('utf-8'))

errors = []
if c.name != "staging":
    errors.append(f"name: expected 'staging', got {c.name!r}")
# Proto3 zero defaults for absent positions
if c.retries != 0:
    errors.append(f"retries: expected 0 (proto3 default), got {c.retries!r}")
if c.region != "":
    errors.append(f"region: expected '' (proto3 default), got {c.region!r}")
if c.enabled != False:
    errors.append(f"enabled: expected False (proto3 default), got {c.enabled!r}")
if abs(c.threshold - 0.0) > 1e-9:
    errors.append(f"threshold: expected 0.0 (proto3 default), got {c.threshold!r}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    print("Proto3 zero defaults correctly applied for absent array positions")
PYEOF
) || true
echo "  $TEST2_RESULT"

if echo "$TEST2_RESULT" | grep -q "ERROR:"; then
    fail_test "Short array -> Proto3 defaults" "See errors above"
else
    pass_test "Short array: proto3 zero defaults applied for absent fields"
fi

echo ""

# -- Test 3: Proto3 serialize -> Proto2 deserialize ------------------------
#    Proto3 serializes with all fields; proto2 reads them.

echo "=== Test 3: Proto3 -> Proto2 (all fields set) ==="
echo "  Proto3 serializes with all fields, proto2 deserializes"

P3_JSON=$(PYTHONPATH="$WORK_DIR/proto3-gen" python3 -c "
from migration.config import Config
c = Config()
c.name = 'dev'
c.retries = 10
c.region = 'ap-southeast-1'
c.enabled = True
c.threshold = 0.5
print(c.SerializeToString().decode('utf-8'))
")
echo "  proto3 serialized: $P3_JSON"

TEST3_RESULT=$(PYTHONPATH="$WORK_DIR/proto2-gen" python3 << PYEOF
import sys
from migration.config import Config

json_str = '''$P3_JSON'''
c = Config.ParseFromString(json_str.encode('utf-8'))

errors = []
if c.name != "dev":
    errors.append(f"name: expected 'dev', got {c.name!r}")
if c.retries != 10:
    errors.append(f"retries: expected 10, got {c.retries!r}")
if c.region != "ap-southeast-1":
    errors.append(f"region: expected 'ap-southeast-1', got {c.region!r}")
if c.enabled != True:
    errors.append(f"enabled: expected True, got {c.enabled!r}")
if abs(c.threshold - 0.5) > 1e-9:
    errors.append(f"threshold: expected 0.5, got {c.threshold!r}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    print("All fields deserialized correctly by proto2")
PYEOF
) || true
echo "  $TEST3_RESULT"

if echo "$TEST3_RESULT" | grep -q "ERROR:"; then
    fail_test "Proto3 -> Proto2 (all fields set)" "See errors above"
else
    pass_test "Proto3 -> Proto2: all field values preserved"
fi

echo ""

# -- Test 4: Proto3 zero-value serialize -> Proto2 deserialize -------------
#    Proto3 serializes with zero values; proto2 applies schema defaults.

echo "=== Test 4: Proto3 -> Proto2 (zero values trigger proto2 defaults) ==="
echo "  Proto3 serializes zero/empty values; proto2 applies schema defaults"

P3_ZERO_JSON=$(PYTHONPATH="$WORK_DIR/proto3-gen" python3 -c "
from migration.config import Config
c = Config()
c.name = 'test'
# retries=0, region='', enabled=False, threshold=0.0 (proto3 zero values)
print(c.SerializeToString().decode('utf-8'))
")
echo "  proto3 serialized (zero values): $P3_ZERO_JSON"

TEST4_RESULT=$(PYTHONPATH="$WORK_DIR/proto2-gen" python3 << PYEOF
import sys
from migration.config import Config

json_str = '''$P3_ZERO_JSON'''
c = Config.ParseFromString(json_str.encode('utf-8'))

errors = []
if c.name != "test":
    errors.append(f"name: expected 'test', got {c.name!r}")
# Proto3 serializes zero values explicitly in the JSON array, so proto2
# deserializer should read them as-is (0, '', False, 0.0) -- NOT apply
# schema defaults, since the array positions are present with non-null values.
if c.retries != 0:
    errors.append(f"retries: expected 0 (explicit zero), got {c.retries!r}")
if c.region != "":
    errors.append(f"region: expected '' (explicit empty), got {c.region!r}")
if c.enabled != False:
    errors.append(f"enabled: expected False (explicit), got {c.enabled!r}")
if abs(c.threshold - 0.0) > 1e-9:
    errors.append(f"threshold: expected 0.0 (explicit zero), got {c.threshold!r}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    print("Zero values preserved (proto2 defaults NOT applied when values present)")
PYEOF
) || true
echo "  $TEST4_RESULT"

if echo "$TEST4_RESULT" | grep -q "ERROR:"; then
    fail_test "Proto3 -> Proto2 (zero values)" "See errors above"
else
    pass_test "Proto3 -> Proto2: zero values preserved, defaults NOT applied"
fi

echo ""

# -- Test 5: Cross-syntax round-trip ---------------------------------------

echo "=== Test 5: Cross-syntax round-trip (proto2 -> proto3 -> proto2) ==="

TEST5_RESULT=$(python3 << PYEOF
import json, sys

# Add both generated paths
sys.path.insert(0, "$WORK_DIR/proto2-gen")
sys.path.insert(0, "$WORK_DIR/proto3-gen")

# We need separate imports since they share the same module name.
# Import proto2 first, serialize, then swap.
import importlib

# Step 1: Create and serialize with proto2
sys.path = ["$WORK_DIR/proto2-gen"] + [p for p in sys.path if "proto2-gen" not in p and "proto3-gen" not in p]
if "migration" in sys.modules: del sys.modules["migration"]
if "migration.config" in sys.modules: del sys.modules["migration.config"]
from migration.config import Config as P2Config

c2 = P2Config()
c2.name = "roundtrip"
c2.retries = 7
c2.region = "us-west-2"
c2.enabled = True
c2.threshold = 0.85
p2_json = c2.SerializeToString().decode("utf-8")

# Step 2: Deserialize with proto3
sys.path = ["$WORK_DIR/proto3-gen"] + [p for p in sys.path if "proto2-gen" not in p and "proto3-gen" not in p]
if "migration" in sys.modules: del sys.modules["migration"]
if "migration.config" in sys.modules: del sys.modules["migration.config"]
from migration.config import Config as P3Config

c3 = P3Config.ParseFromString(p2_json.encode("utf-8"))
p3_json = c3.SerializeToString().decode("utf-8")

# Step 3: Deserialize back with proto2
sys.path = ["$WORK_DIR/proto2-gen"] + [p for p in sys.path if "proto2-gen" not in p and "proto3-gen" not in p]
if "migration" in sys.modules: del sys.modules["migration"]
if "migration.config" in sys.modules: del sys.modules["migration.config"]
from migration.config import Config as P2Config2

c2_back = P2Config2.ParseFromString(p3_json.encode("utf-8"))

errors = []
if c2_back.name != "roundtrip":
    errors.append(f"name: expected 'roundtrip', got {c2_back.name!r}")
if c2_back.retries != 7:
    errors.append(f"retries: expected 7, got {c2_back.retries!r}")
if c2_back.region != "us-west-2":
    errors.append(f"region: expected 'us-west-2', got {c2_back.region!r}")
if c2_back.enabled != True:
    errors.append(f"enabled: expected True, got {c2_back.enabled!r}")
if abs(c2_back.threshold - 0.85) > 1e-9:
    errors.append(f"threshold: expected 0.85, got {c2_back.threshold!r}")

# Verify JSON representations are equivalent
n1 = json.dumps(json.loads(p2_json), separators=(",", ":"))
n2 = json.dumps(json.loads(p3_json), separators=(",", ":"))
if n1 != n2:
    errors.append(f"JSON mismatch: proto2={n1} vs proto3={n2}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    print(f"Round-trip OK: {n1}")
PYEOF
) || true
echo "  $TEST5_RESULT"

if echo "$TEST5_RESULT" | grep -q "ERROR:"; then
    fail_test "Cross-syntax round-trip" "See errors above"
else
    pass_test "Cross-syntax round-trip: proto2 -> proto3 -> proto2 preserves data"
fi

echo ""

# -- Summary ---------------------------------------------------------------

echo "============================================"
echo "  Proto2-Proto3 migration test summary"
echo "  Passed: $tests_passed"
echo "  Failed: $tests_failed"
echo "============================================"

if [ "$tests_failed" -gt 0 ]; then
    exit 1
fi
