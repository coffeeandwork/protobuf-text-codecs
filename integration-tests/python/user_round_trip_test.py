#!/usr/bin/env python3
"""
Cross-language round-trip test for User/Address JSON array serialization.
Deserializes a JSON string (from command line or hardcoded), verifies fields,
then serializes from scratch and verifies the output matches.
"""
import sys
import json
import os

# Add the generated code directory to the path.
# The shell script sets PYTHONPATH, but we also try the parent of this script.
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Import generated classes (generated into the example/ package)
from example.user import User
from example.address import Address

EXPECTED_JSON = '["Alice","Smith",30,["123 Main Street","Springfield","IL",62704]]'

passed = 0
failed = 0


def check(field, expected, actual):
    global passed, failed
    if expected == actual:
        print(f"  PASS: {field} = {actual!r}")
        passed += 1
    else:
        print(f"  FAIL: {field}")
        print(f"    expected: {expected!r}")
        print(f"    actual:   {actual!r}")
        failed += 1


def test_deserialize(json_str):
    """Deserialize a JSON string and verify all fields."""
    print("--- Test: Deserialize User from JSON ---")
    print(f"  Input: {json_str}")

    user = User.from_json_string(json_str)

    check("firstname", "Alice", user.firstname)
    check("lastname", "Smith", user.lastname)
    check("age", 30, user.age)

    if user.address is None:
        global failed
        print("  FAIL: address is None, expected non-null")
        failed += 1
    else:
        check("address.street", "123 Main Street", user.address.street)
        check("address.city", "Springfield", user.address.city)
        check("address.state", "IL", user.address.state)
        check("address.zip", 62704, user.address.zip)


def test_serialize():
    """Serialize a User from scratch and verify JSON output."""
    print("--- Test: Serialize User to JSON ---")

    addr = Address()
    addr.street = "123 Main Street"
    addr.city = "Springfield"
    addr.state = "IL"
    addr.zip = 62704

    user = User()
    user.firstname = "Alice"
    user.lastname = "Smith"
    user.age = 30
    user.address = addr

    json_str = user.to_json_string()
    print(f"  Serialized: {json_str}")

    # Normalize for comparison: parse and re-dump with no spaces
    expected_normalized = json.dumps(json.loads(EXPECTED_JSON), separators=(",", ":"))
    actual_normalized = json.dumps(json.loads(json_str), separators=(",", ":"))

    global passed, failed
    if expected_normalized == actual_normalized:
        print("  PASS: Serialization matches expected JSON")
        passed += 1
    else:
        print("  FAIL: Serialization mismatch")
        print(f"    expected: {expected_normalized}")
        print(f"    actual:   {actual_normalized}")
        failed += 1


def test_round_trip():
    """Full round trip: serialize -> deserialize -> serialize."""
    print("--- Test: Full round-trip (serialize -> deserialize -> serialize) ---")

    addr = Address()
    addr.street = "123 Main Street"
    addr.city = "Springfield"
    addr.state = "IL"
    addr.zip = 62704

    user = User()
    user.firstname = "Alice"
    user.lastname = "Smith"
    user.age = 30
    user.address = addr

    json1 = user.to_json_string()
    user2 = User.from_json_string(json1)
    json2 = user2.to_json_string()

    json1_normalized = json.dumps(json.loads(json1), separators=(",", ":"))
    json2_normalized = json.dumps(json.loads(json2), separators=(",", ":"))

    global passed, failed
    if json1_normalized == json2_normalized:
        print("  PASS: Round-trip produces identical JSON")
        passed += 1
    else:
        print("  FAIL: Round-trip JSON mismatch")
        print(f"    first:  {json1_normalized}")
        print(f"    second: {json2_normalized}")
        failed += 1


def main():
    # Use command line arg if provided, otherwise use hardcoded expected value
    if len(sys.argv) > 1:
        json_input = sys.argv[1]
    else:
        json_input = EXPECTED_JSON

    test_deserialize(json_input)
    test_serialize()
    test_round_trip()

    print()
    print(f"Results: {passed} passed, {failed} failed")

    if failed > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
