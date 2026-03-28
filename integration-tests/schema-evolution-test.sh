#!/usr/bin/env bash
#
# Schema evolution test for protoc-gen-jsonarray.
#
# Tests forward and backward compatibility of JSON array encoding:
#   1. Serialize with v1 (3 fields), deserialize with v2 (4 fields) --
#      new field should get its default value
#   2. Serialize with v2 (4 fields), deserialize with v1 (3 fields) --
#      extra field should be silently ignored
#
# Prerequisites: protoc, python3 on PATH
# Uses /tmp for all generated code

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_JAR="$PROJECT_ROOT/plugin/build/libs/protoc-gen-jsonarray.jar"
PLUGIN_SCRIPT="$PROJECT_ROOT/protoc-gen-jsonarray"
WORK_DIR="/tmp/jsonarray-schema-evolution-test-$$"

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

# ── Create proto files ────────────────────────────────────────────────

mkdir -p "$WORK_DIR/v1-proto" "$WORK_DIR/v2-proto"
mkdir -p "$WORK_DIR/v1-gen" "$WORK_DIR/v2-gen"

# v1: Person with 3 fields
cat > "$WORK_DIR/v1-proto/person.proto" << 'PROTO'
syntax = "proto3";
package evolution;
message Person {
  string name = 1;
  int32 age = 2;
  string email = 3;
}
PROTO

# v2: Person with 4 fields (added phone at field number 4)
cat > "$WORK_DIR/v2-proto/person.proto" << 'PROTO'
syntax = "proto3";
package evolution;
message Person {
  string name = 1;
  int32 age = 2;
  string email = 3;
  string phone = 4;
}
PROTO

echo "=== Generating code ==="

# Generate Python code for both versions
echo "  Generating v1 Python code (3 fields)..."
protoc \
    --plugin=protoc-gen-jsonarray="$PLUGIN_SCRIPT" \
    --jsonarray_out="lang=python:$WORK_DIR/v1-gen" \
    --proto_path="$WORK_DIR/v1-proto" \
    "$WORK_DIR/v1-proto/person.proto"

echo "  Generating v2 Python code (4 fields)..."
protoc \
    --plugin=protoc-gen-jsonarray="$PLUGIN_SCRIPT" \
    --jsonarray_out="lang=python:$WORK_DIR/v2-gen" \
    --proto_path="$WORK_DIR/v2-proto" \
    "$WORK_DIR/v2-proto/person.proto"

# Create __init__.py files
find "$WORK_DIR/v1-gen" -type d -exec touch {}/__init__.py \;
find "$WORK_DIR/v2-gen" -type d -exec touch {}/__init__.py \;

echo ""

# ── Test 1: v1 serialize -> v2 deserialize (forward compatibility) ────

echo "=== Test 1: Forward compatibility (v1 -> v2) ==="
echo "  Serialize with v1 (3 fields), deserialize with v2 (4 fields)"
echo "  New field (phone) should have default value (empty string)"

V1_JSON=$(PYTHONPATH="$WORK_DIR/v1-gen" python3 -c "
from evolution.person import Person
p = Person()
p.name = 'Alice'
p.age = 30
p.email = 'alice@example.com'
print(p.to_json_string())
")
echo "  v1 serialized: $V1_JSON"

FORWARD_RESULT=$(PYTHONPATH="$WORK_DIR/v2-gen" python3 << PYEOF
import sys
from evolution.person import Person

json_str = '''$V1_JSON'''
p = Person.from_json_string(json_str)

errors = []
if p.name != "Alice":
    errors.append(f"name: expected 'Alice', got {p.name!r}")
if p.age != 30:
    errors.append(f"age: expected 30, got {p.age!r}")
if p.email != "alice@example.com":
    errors.append(f"email: expected 'alice@example.com', got {p.email!r}")
if p.phone != "":
    errors.append(f"phone: expected '' (default), got {p.phone!r}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    print("All fields correct (phone has default value)")
PYEOF
) || true
echo "  $FORWARD_RESULT"

if echo "$FORWARD_RESULT" | grep -q "ERROR:"; then
    fail_test "Forward compatibility (v1 -> v2)" "See errors above"
else
    pass_test "Forward compatibility (v1 -> v2): new field gets default"
fi

echo ""

# ── Test 2: v2 serialize -> v1 deserialize (backward compatibility) ───

echo "=== Test 2: Backward compatibility (v2 -> v1) ==="
echo "  Serialize with v2 (4 fields), deserialize with v1 (3 fields)"
echo "  Extra field (phone) should be silently ignored"

V2_JSON=$(PYTHONPATH="$WORK_DIR/v2-gen" python3 -c "
from evolution.person import Person
p = Person()
p.name = 'Bob'
p.age = 25
p.email = 'bob@example.com'
p.phone = '555-0123'
print(p.to_json_string())
")
echo "  v2 serialized: $V2_JSON"

BACKWARD_RESULT=$(PYTHONPATH="$WORK_DIR/v1-gen" python3 << PYEOF
import sys
from evolution.person import Person

json_str = '''$V2_JSON'''
p = Person.from_json_string(json_str)

errors = []
if p.name != "Bob":
    errors.append(f"name: expected 'Bob', got {p.name!r}")
if p.age != 25:
    errors.append(f"age: expected 25, got {p.age!r}")
if p.email != "bob@example.com":
    errors.append(f"email: expected 'bob@example.com', got {p.email!r}")

# v1 Person should NOT have a 'phone' attribute
if hasattr(p, 'phone') and p.phone != "":
    errors.append(f"phone attribute should not be set on v1, got {p.phone!r}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    print("All fields correct (extra field ignored)")
PYEOF
) || true
echo "  $BACKWARD_RESULT"

if echo "$BACKWARD_RESULT" | grep -q "ERROR:"; then
    fail_test "Backward compatibility (v2 -> v1)" "See errors above"
else
    pass_test "Backward compatibility (v2 -> v1): extra field ignored"
fi

echo ""

# ── Test 3: v2 round-trip preserves new field ─────────────────────────

echo "=== Test 3: v2 round-trip (serialize -> deserialize -> serialize) ==="

ROUNDTRIP_RESULT=$(PYTHONPATH="$WORK_DIR/v2-gen" python3 << PYEOF
import json, sys
from evolution.person import Person

p = Person()
p.name = "Charlie"
p.age = 35
p.email = "charlie@example.com"
p.phone = "555-9999"

json1 = p.to_json_string()
p2 = Person.from_json_string(json1)
json2 = p2.to_json_string()

n1 = json.dumps(json.loads(json1), separators=(",", ":"))
n2 = json.dumps(json.loads(json2), separators=(",", ":"))

if n1 == n2:
    print(f"Round-trip OK: {n1}")
else:
    print(f"ERROR: Round-trip mismatch")
    print(f"  first:  {n1}")
    print(f"  second: {n2}")
    sys.exit(1)
PYEOF
) || true
echo "  $ROUNDTRIP_RESULT"

if echo "$ROUNDTRIP_RESULT" | grep -q "ERROR:"; then
    fail_test "v2 round-trip" "See errors above"
else
    pass_test "v2 round-trip preserves all fields including new field"
fi

echo ""

# ── Test 4: Field number gaps work correctly ──────────────────────────

echo "=== Test 4: Field number gaps (non-contiguous field numbers) ==="

mkdir -p "$WORK_DIR/gap-proto" "$WORK_DIR/gap-gen"

cat > "$WORK_DIR/gap-proto/sparse.proto" << 'PROTO'
syntax = "proto3";
package evolution;
message Sparse {
  string name = 1;
  int32 value = 5;
}
PROTO

protoc \
    --plugin=protoc-gen-jsonarray="$PLUGIN_SCRIPT" \
    --jsonarray_out="lang=python:$WORK_DIR/gap-gen" \
    --proto_path="$WORK_DIR/gap-proto" \
    "$WORK_DIR/gap-proto/sparse.proto"

find "$WORK_DIR/gap-gen" -type d -exec touch {}/__init__.py \;

GAP_RESULT=$(PYTHONPATH="$WORK_DIR/gap-gen" python3 << PYEOF
import json, sys
from evolution.sparse import Sparse

s = Sparse()
s.name = "test"
s.value = 42

json_str = s.to_json_string()
data = json.loads(json_str)

# Field 1 is at position 0, fields 2-4 should be null (gaps), field 5 at position 4
errors = []
if len(data) != 5:
    errors.append(f"expected array length 5, got {len(data)}")
elif data[0] != "test":
    errors.append(f"position 0: expected 'test', got {data[0]!r}")
elif data[1] is not None:
    errors.append(f"position 1: expected null (gap), got {data[1]!r}")
elif data[2] is not None:
    errors.append(f"position 2: expected null (gap), got {data[2]!r}")
elif data[3] is not None:
    errors.append(f"position 3: expected null (gap), got {data[3]!r}")
elif data[4] != 42:
    errors.append(f"position 4: expected 42, got {data[4]!r}")

if errors:
    for e in errors:
        print(f"ERROR: {e}")
    sys.exit(1)
else:
    # Round-trip
    s2 = Sparse.from_json_string(json_str)
    if s2.name != "test" or s2.value != 42:
        print(f"ERROR: round-trip failed: name={s2.name!r}, value={s2.value!r}")
        sys.exit(1)
    print(f"Gaps handled correctly: {json_str}")
PYEOF
) || true
echo "  $GAP_RESULT"

if echo "$GAP_RESULT" | grep -q "ERROR:"; then
    fail_test "Field number gaps" "See errors above"
else
    pass_test "Field number gaps produce null entries and round-trip correctly"
fi

echo ""

# ── Summary ───────────────────────────────────────────────────────────

echo "============================================"
echo "  Schema evolution test summary"
echo "  Passed: $tests_passed"
echo "  Failed: $tests_failed"
echo "============================================"

if [ "$tests_failed" -gt 0 ]; then
    exit 1
fi
