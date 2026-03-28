# protobuf-text-codecs

A suite of `protoc` compiler plugins that generate code for serializing Protocol Buffer messages using **human-readable text formats** instead of the standard binary encoding.

---

## Overview

Standard Protocol Buffers use a binary encoding format that is efficient but opaque -- you cannot read or inspect serialized data without a decoder. The common alternative, protobuf's JSON mapping, produces verbose output because every field includes its name as a key.

**protobuf-text-codecs** provides two compact, human-readable serialization formats:

### Format 1: Positional JSON Array (`protoc-gen-jsonarray`)

Encodes each message as a **positional JSON array**, where the array index corresponds to the field number. The result is valid JSON that is both human-readable and compact -- no field names appear in the output, only positional values.

Key properties:

- Each message serializes to a JSON array.
- Array position is determined by **field number** (`position = field_number - 1`), preserving protobuf schema evolution guarantees.
- Gaps in field numbering produce `null` entries (in practice most protos use contiguous numbers).
- Nested messages become nested arrays.
- Output is valid JSON encoded as UTF-8 bytes.
- Supports 9 target languages from a single plugin binary.

### Format 2: pbtk URL Encoding (`protoc-gen-pbtkurl`)

Encodes each message as a **URL-safe string** using the same format found in Google Maps URLs (the `!`-delimited protobuf text format). Each field is encoded as `!<field_number><type_char><value>`.

Key properties:

- Each field is self-describing: `!1sJohn!2i30!3b1` encodes name="John", age=30, active=true.
- No padding for field number gaps (unlike JSON array, absent fields are simply omitted).
- Nested messages use `!<num>m<count>` followed by sub-fields.
- URL-safe: strings are percent-encoded, bytes are base64-encoded.
- No JSON library dependency in generated code -- pure string manipulation.
- Supports 9 target languages from a single plugin binary.

---

## Motivating Example

Given this proto IDL:

```protobuf
// address.proto
syntax = "proto3";
package example;

message Address {
  string street = 1;
  string city = 2;
  string state = 3;
  int32 zip = 4;
}
```

```protobuf
// user.proto
syntax = "proto3";
package example;
import "address.proto";

message User {
  string firstname = 1;
  string lastname = 2;
  int32 age = 3;
  Address address = 4;
}
```

### JSON Array Format (Java)

```java
User user = new User();
user.setFirstname("Alice");
user.setLastname("Smith");
user.setAge(30);
user.setAddress(new Address("123 Main Street", "Springfield", "IL", 62704));

String json = user.toJsonString();
// ["Alice","Smith",30,["123 Main Street","Springfield","IL",62704]]

// Deserialize back
User parsed = User.fromJsonString(json);
```

### pbtk URL Format (Java)

```java
User user = new User();
user.setFirstname("Alice");
user.setLastname("Smith");
user.setAge(30);
user.setAddress(new Address("123 Main Street", "Springfield", "IL", 62704));

String url = user.toPbtkUrl();
// !1sAlice!2sSmith!3i30!4m4!1s123+Main+Street!2sSpringfield!3sIL!4i62704

// Deserialize back
User parsed = User.fromPbtkUrl(url);
```

### JSON Array Format (Python)

```python
user = User()
user.firstname = "Alice"
user.lastname = "Smith"
user.age = 30

addr = Address()
addr.street = "123 Main Street"
addr.city = "Springfield"
addr.state = "IL"
addr.zip = 62704
user.address = addr

json_str = user.to_json_string()
# ["Alice","Smith",30,["123 Main Street","Springfield","IL",62704]]

# Deserialize back
parsed = User.from_json_string(json_str)
```

### JSON Array Format (Go)

```go
user := &User{
    Firstname: "Alice",
    Lastname:  "Smith",
    Age:       30,
    Address: &Address{
        Street: "123 Main Street",
        City:   "Springfield",
        State:  "IL",
        Zip:    62704,
    },
}

jsonStr, err := user.ToJsonString()
// ["Alice","Smith",30,["123 Main Street","Springfield","IL",62704]]

// Deserialize back
parsed, err := UserFromJsonString(jsonStr)
```

---

## Supported Languages

| Language | `lang=` Identifier | File Extension | JSON Library | Runtime Required |
|---|---|---|---|---|
| Java | `java` | `.java` | None (zero-dependency StringBuilder) | Yes (lightweight) |
| Python | `python` | `.py` | `json` (stdlib) | No |
| JavaScript | `javascript` | `.js` | Native `JSON` | No |
| TypeScript | `typescript` | `.ts` | Native `JSON` (with type annotations) | No |
| C | `c` | `.h` / `.c` | cJSON | Yes |
| C++ | `cpp` | `.hpp` / `.cpp` | nlohmann/json | Yes |
| Rust | `rust` | `.rs` | `serde_json` | Yes |
| Zig | `zig` | `.zig` | `std.json` | No |
| Go | `go` | `.go` | `encoding/json` | No |

Languages marked "Runtime Required" ship a small runtime library that reduces generated code size. Languages without a runtime produce fully self-contained generated code.

---

## Installation

### Prerequisites

- **Java 17+** (the plugin is written in Java)
- **Gradle** (wrapper included in the repository)
- **protoc** (Protocol Buffers compiler, version 3.x+)

### Build from Source

```bash
# Clone the repository
git clone https://github.com/coffeeandwork/protobuf-text-codecs.git
cd protobuf-text-codecs

# Build both plugin fat JARs
./gradlew :plugin:shadowJar :plugin:pbtkShadowJar

# Verify the builds
ls plugin/build/libs/protoc-gen-jsonarray.jar
ls plugin/build/libs/protoc-gen-pbtkurl.jar
```

The wrapper scripts at the repository root invoke the built JARs. Make them executable:

```bash
chmod +x protoc-gen-jsonarray protoc-gen-pbtkurl
```

### Verify

```bash
# JSON Array format
protoc \
  --plugin=protoc-gen-jsonarray=./protoc-gen-jsonarray \
  --jsonarray_out=lang=java:./out-jsonarray \
  test-protos/src/main/proto/address.proto

# pbtk URL format
protoc \
  --plugin=protoc-gen-pbtkurl=./protoc-gen-pbtkurl \
  --pbtkurl_out=lang=java:./out-pbtkurl \
  test-protos/src/main/proto/address.proto
```

---

## Usage

Invoke `protoc` with the plugin and specify the target language using the `lang=` parameter:

```bash
protoc \
  --plugin=protoc-gen-jsonarray=/path/to/protoc-gen-jsonarray \
  --jsonarray_out=lang=<language>:<output_directory> \
  <proto_files...>
```

The `lang=` parameter is passed through protoc's `--jsonarray_out` option. The format is `lang=<identifier>:<output_dir>`, where `<identifier>` is one of the values from the Supported Languages table.

### Per-Language Examples

Substitute any supported language identifier from the table above:

```bash
protoc \
  --plugin=protoc-gen-jsonarray=./protoc-gen-jsonarray \
  --jsonarray_out=lang=<language>:<output-dir> \
  user.proto address.proto

# e.g. lang=java:./gen-java, lang=python:./gen-python, lang=rust:./gen-rust
```

### Generating Multiple Languages

Run `protoc` once per target language:

```bash
protoc --plugin=protoc-gen-jsonarray=./protoc-gen-jsonarray \
       --jsonarray_out=lang=java:./gen-java user.proto

protoc --plugin=protoc-gen-jsonarray=./protoc-gen-jsonarray \
       --jsonarray_out=lang=python:./gen-python user.proto

protoc --plugin=protoc-gen-jsonarray=./protoc-gen-jsonarray \
       --jsonarray_out=lang=go:./gen-go user.proto
```

### Using with Buf

The plugin works as a local plugin with [Buf](https://buf.build/) -- no special integration is needed. Add the plugin to your `buf.gen.yaml`:

**Single language:**

```yaml
version: v2
plugins:
  - local: protoc-gen-jsonarray
    out: gen/java
    opt:
      - lang=java
```

**Multiple languages in one `buf.gen.yaml`:**

```yaml
version: v2
plugins:
  - local: protoc-gen-jsonarray
    out: gen/java
    opt:
      - lang=java
  - local: protoc-gen-jsonarray
    out: gen/python
    opt:
      - lang=python
  - local: protoc-gen-jsonarray
    out: gen/go
    opt:
      - lang=go
```

Then run:

```bash
buf generate
```

Ensure that `protoc-gen-jsonarray` is on your `PATH`, or use an absolute path in the `local:` field.

---

## Encoding Rules

### Scalar and Compound Types

| Proto Type | JSON Representation | Example |
|---|---|---|
| `string` | JSON string | `"Alice"` |
| `int32`, `sint32`, `sfixed32` | JSON number | `30` |
| `int64`, `sint64`, `sfixed64` | JSON string (preserves precision beyond 2^53) | `"30"` |
| `uint32`, `fixed32` | JSON number | `30` |
| `uint64`, `fixed64` | JSON string (preserves precision beyond 2^53) | `"30"` |
| `float`, `double` | JSON number | `3.14` |
| `bool` | JSON boolean | `true` |
| `bytes` | Base64-encoded JSON string | `"AQID"` |
| `enum` | JSON number (enum integer value) | `1` |
| Nested `message` | Nested JSON array | `["addr","city","IL",62704]` |
| `repeated` scalar/message | JSON array in its slot | `[1, 2, 3]` |
| `map<string, V>` | JSON object | `{"a": 1}` |
| `map<non-string, V>` | Array of `[key, value]` pairs | `[[1,"a"],[2,"b"]]` |

### Presence and Default Values

| Scenario | JSON Representation | Example |
|---|---|---|
| Unset scalar (implicit presence, proto3 default) | Default value (`0`, `""`, `false`) | `0` |
| Unset scalar (`optional` / explicit presence) | JSON `null` | `null` |
| Unset message field | JSON `null` | `null` |
| Field-number gap (no field at that number) | JSON `null` | `null` |
| Oneof: inactive member | JSON `null` | `null` |
| Oneof: active member | The member's value | `"user@example.com"` |

### Well-Known Types

| Well-Known Type | Encoding | Example |
|---|---|---|
| `google.protobuf.Timestamp` | RFC 3339 JSON string | `"2024-01-15T10:30:00Z"` |
| `google.protobuf.Duration` | Duration string | `"1.500s"` |
| `google.protobuf.BoolValue`, `Int32Value`, `StringValue`, etc. | Unwrapped scalar or `null` | `42` or `null` |
| `google.protobuf.Struct` | JSON object (pass-through) | `{"key": "value"}` |
| `google.protobuf.Value` | JSON value (pass-through) | `"hello"` |
| `google.protobuf.ListValue` | JSON array (pass-through) | `[1, 2, 3]` |
| `google.protobuf.FieldMask` | Comma-separated string | `"foo,bar.baz"` |
| `google.protobuf.Empty` | Empty JSON array | `[]` |
| `google.protobuf.Any` | **Not supported** (rejected at generation time) | -- |

---

## pbtk URL Encoding Rules

The pbtk URL format encodes each field as `!<field_number><type_char><value>`. This is the same format used in Google Maps URLs for encoding protobuf data.

### Type Characters

| Type Char | Proto Types | Example |
|---|---|---|
| `s` | `string` (URL-encoded) | `!1sAlice` |
| `i` | `int32`, `int64`, `uint32`, `uint64`, `sint32`, `sint64`, `fixed32`, `fixed64`, `sfixed32`, `sfixed64` | `!2i40` |
| `d` | `double` | `!3d3.14` |
| `f` | `float` | `!4f2.5` |
| `b` | `bool` (0 or 1) | `!5b1` |
| `e` | `enum` (integer value) | `!6e2` |
| `z` | `bytes` (base64-encoded) | `!7zAQID` |
| `m` | Nested `message` (followed by sub-field count) | `!8m3!1sFoo!2i42!3b1` |

### Compound Types

| Proto Type | pbtk Encoding | Example |
|---|---|---|
| Nested message | `!<num>m<count><sub-fields>` | `!4m4!1sMain+St!2sSF!3sCA!4i94102` |
| Repeated field | One `!<num><type><val>` per element | `!5i1!5i2!5i3` |
| Map field | Repeated `!<num>m2!1<key>!2<val>` per entry | `!6m2!1sfoo!2i1!6m2!1sbar!2i2` |

### Key Differences from JSON Array Format

| Aspect | JSON Array | pbtk URL |
|---|---|---|
| Field identification | Positional (index = field_number - 1) | Explicit (`!<num>...`) |
| Absent fields | `null` padding | Simply omitted |
| Output format | JSON | URL-safe string |
| JSON library needed | Yes | No |
| Human readability | Good (standard JSON) | Good (compact, self-describing) |
| URL embedding | Needs URL encoding | Native URL-safe |

---

## Schema Evolution

The positional encoding strategy is designed to preserve protobuf's schema evolution guarantees. Array position is determined by field number (`position = field_number - 1`), not by declaration order.

### Adding Fields

Add new fields with field numbers **higher** than all existing fields. The new fields appear at the end of the array.

- **Old deserializer reading new data:** Ignores trailing elements it does not recognize. Fields beyond the known maximum field number are silently dropped.
- **New deserializer reading old data:** Detects that the array is shorter than expected. Missing trailing fields receive their default values (zero, empty string, `false`, or `null` for messages and optional fields).

```protobuf
// v1
message User {
  string name = 1;
  int32 age = 2;
}
// v1 output: ["Alice", 30]

// v2 (added email)
message User {
  string name = 1;
  int32 age = 2;
  string email = 3;  // new field appended
}
// v2 output: ["Alice", 30, "alice@example.com"]
// v2 deserializer reading v1 data ["Alice", 30] -> email defaults to ""
```

### Removing Fields

When a field is removed, `reserved` its field number in the proto definition. The position in the array becomes a `null` slot. Both old and new deserializers handle `null` values correctly.

```protobuf
// v2 (field 2 removed)
message User {
  string name = 1;
  reserved 2;        // was 'age'
  string email = 3;
}
// v2 output: ["Alice", null, "alice@example.com"]
```

### Non-Contiguous Field Numbers

If field numbers have gaps, the array contains `null` entries at the gap positions:

```protobuf
message Sparse {
  string name = 1;
  int32 age = 5;
}
// Output: ["Alice", null, null, null, 30]
```

In practice, most proto files use contiguous field numbers starting at 1, so no array slots are wasted.

### Best Practices

- **Append new fields** with higher field numbers; do not reuse old numbers.
- **Reserve removed field numbers** to prevent accidental reuse.
- **Never reorder field numbers** -- this is a breaking change, same as standard protobuf.

---

## API Reference

Each generated class/struct provides serialization and deserialization methods. The method names follow the conventions of each target language.

### Java

```java
// Serialize to JSON string (zero dependencies — no Jackson required)
String json = user.toJsonString();

// Serialize to UTF-8 bytes
byte[] bytes = user.toJsonBytes();

// Deserialize from JSON string
User user = User.fromJsonString(json);

// Deserialize from byte[]
User user = User.fromJsonBytes(bytes);
```

Generated classes are self-contained with no Jackson or other JSON library dependency. Fields are accessed via getters and setters (`getFirstname()`, `setFirstname()`). Optional and message fields have `has*()` presence-check methods (e.g., `hasAddress()`).

### Python

```python
# Serialize to a Python list
data = user.serialize()

# Convenience: serialize to JSON string
json_str = user.to_json_string()

# Convenience: serialize to UTF-8 bytes
json_bytes = user.to_json_bytes()

# Deserialize from a Python list
user = User.deserialize(data)

# Convenience: deserialize from JSON string
user = User.from_json_string(json_str)

# Convenience: deserialize from bytes
user = User.from_json_bytes(json_bytes)
```

Fields are accessed as Python properties (e.g., `user.firstname`). Optional and message fields have `has_*()` methods.

### Go

```go
// Serialize to []any slice
arr := user.Serialize()

// Convenience: serialize to JSON string
jsonStr, err := user.ToJsonString()

// Deserialize from []any slice
user, err := DeserializeUser(arr)

// Convenience: deserialize from JSON string
user, err := UserFromJsonString(jsonStr)
```

Generated structs use exported fields (e.g., `user.Firstname`). Nested messages are pointer types.

### JavaScript

```javascript
// Serialize to a JavaScript array
const arr = user.serialize();

// Convenience: serialize to JSON string
const json = user.toJsonString();

// Deserialize from a JavaScript array
const user = User.deserialize(arr);

// Convenience: deserialize from JSON string
const user = User.fromJsonString(json);
```

### TypeScript

```typescript
// Same API as JavaScript, with type annotations
const arr: any[] = user.serialize();
const json: string = user.toJsonString();
const user: User = User.deserialize(arr);
const user: User = User.fromJsonString(json);
```

### C

```c
// Serialize to cJSON array
cJSON* json = user_serialize(user);

// Serialize to string
char* str = user_to_json_string(user);

// Deserialize from cJSON array
User* user = user_deserialize(json);

// Deserialize from string
User* user = user_from_json_string(str);

// Memory management: caller must free
user_free(user);
cJSON_Delete(json);
free(str);
```

### C++

```cpp
// Serialize to nlohmann::json
nlohmann::json arr = user.serialize();

// Convenience: serialize to std::string
std::string json = user.toJsonString();

// Deserialize from nlohmann::json
User user = User::deserialize(arr);

// Convenience: deserialize from std::string
User user = User::fromJsonString(json);
```

### Rust

```rust
// Serialize to serde_json::Value
let value: serde_json::Value = user.serialize();

// Convenience: serialize to String
let json: String = user.to_json_string();

// Deserialize from serde_json::Value
let user: User = User::deserialize(&value)?;

// Convenience: deserialize from &str
let user: User = User::from_json_string(json_str)?;
```

### Zig

```zig
// Serialize to std.json.Value
const value = user.serialize(allocator);

// Convenience: serialize to []const u8
const json = try user.toJsonString(allocator);

// Deserialize from std.json.Value
const user = try User.deserialize(value, allocator);

// Convenience: deserialize from []const u8
const user = try User.fromJsonString(json, allocator);
```

### pbtk URL API (All Languages)

The pbtk URL plugin (`protoc-gen-pbtkurl`) generates classes with the same structure (fields, getters/setters, constructors) but with pbtk URL serialization instead of JSON array methods. No JSON library dependency is required. Method names follow each language's conventions:

**Java:**

```java
String url = user.toPbtkUrl();       // "!1sAlice!2sSmith!3i30!4m4..."
User user = User.fromPbtkUrl(url);
```

**Python:**

```python
url = user.to_pbtk_url()            # "!1sAlice!2sSmith!3i30!4m4..."
user = User.from_pbtk_url(url)
```

**C:**

```c
char* url = user_to_pbtk_url(user);  // caller must free()
User* user = user_from_pbtk_url(url);
user_free(user);
free(url);
```

Other languages follow the same pattern: `toPbtkUrl()`/`fromPbtkUrl()` (JS/TS/Go), `to_pbtk_url()`/`from_pbtk_url()` (Rust/C++), `toPbtkUrl(allocator)`/`fromPbtkUrl(url, allocator)` (Zig).

---

## Limitations

- **Both proto2 and proto3 are supported.** Proto2 features including required fields, optional fields with schema-specified defaults, and groups are handled. Extensions are incompatible with positional encoding and emit a warning (only base message fields are included).
- **No `google.protobuf.Any` support.** Positional encoding requires knowing the message schema at compile time. `Any` embeds an arbitrary message whose type is only known at runtime, making positional encoding impossible. The plugin rejects files that use `Any` with a clear error.
- **No streaming serialization.** Serialization builds a complete in-memory representation (array/list/slice) before encoding to JSON. Streaming directly to an output stream is not currently supported.
- **Single language per invocation.** Each `protoc` run produces output for one target language. To generate multiple languages, invoke `protoc` multiple times with different `lang=` values.

---

## Project Structure

```
protobuf-text-codecs/
├── build.gradle                        # Root build configuration (Spotless + Google Java Format)
├── settings.gradle                     # Gradle multi-module settings
├── protoc-gen-jsonarray                # Wrapper shell script for JSON array plugin
├── protoc-gen-pbtkurl                  # Wrapper shell script for pbtk URL plugin
├── CLAUDE.md                           # Project overview
├── docs/                               # Formal assurance documentation (6 phases)
│   ├── SYSTEM_ANALYSIS.md
│   ├── HAZARD_ANALYSIS.md
│   ├── REQUIREMENTS.md
│   ├── TEST_STRATEGY.md
│   ├── TEST_MATRIX.md
│   ├── SECURITY_ASSESSMENT.md
│   ├── ASSURANCE_CASE.md
│   └── EVIDENCE_INDEX.md
│
├── plugin/                             # The protoc plugins (Java)
│   ├── build.gradle
│   └── src/
│       ├── main/java/dev/protocgen/textcodecs/
│       │   ├── jsonarray/              # JSON array encoding plugin
│       │   │   ├── Main.java           # Entry point: protoc-gen-jsonarray
│       │   │   ├── PluginRunner.java   # Orchestrator: request -> response
│       │   │   ├── ProtoFileProcessor.java
│       │   │   ├── MessageAnalyzer.java
│       │   │   ├── CodeWriter.java
│       │   │   ├── model/              # Language-neutral internal model (shared)
│       │   │   │   ├── ProtoField.java
│       │   │   │   ├── ProtoMessage.java
│       │   │   │   ├── ProtoEnum.java
│       │   │   │   ├── ProtoFile.java
│       │   │   │   ├── TypeRegistry.java
│       │   │   │   └── WellKnownType.java
│       │   │   └── codegen/            # JSON array generators (9 languages)
│       │   │       ├── LanguageGenerator.java
│       │   │       ├── NameResolver.java
│       │   │       ├── TypeMapper.java
│       │   │       ├── KeywordUtil.java
│       │   │       ├── java/           # Each language: Generator, CodeEmitter,
│       │   │       ├── python/         #   SerializerGenerator, DeserializerGenerator,
│       │   │       ├── javascript/     #   NameResolver, TypeMapper
│       │   │       ├── typescript/
│       │   │       ├── c/
│       │   │       ├── cpp/
│       │   │       ├── rust/
│       │   │       ├── zig/
│       │   │       └── go/
│       │   └── pbtkurl/                # pbtk URL encoding plugin
│       │       ├── PbtkMain.java       # Entry point: protoc-gen-pbtkurl
│       │       ├── PbtkPluginRunner.java
│       │       └── codegen/            # pbtk URL generators (9 languages)
│       │           ├── java/
│       │           ├── python/
│       │           ├── javascript/
│       │           ├── typescript/
│       │           ├── c/
│       │           ├── cpp/
│       │           ├── rust/
│       │           ├── zig/
│       │           └── go/
│       └── test/java/...               # Unit and golden-file tests
│
├── runtime/                            # Runtime libraries (JSON array format only)
│   ├── java/                           # Zero-dependency runtime (JsonArrayCodec, JsonArrayReader/Writer, FieldCodecs)
│   ├── c/                              # cJSON-based runtime
│   ├── cpp/                            # nlohmann/json header-only runtime
│   └── rust/                           # serde_json runtime crate
│
├── test-protos/                        # Proto files used in testing
│
└── integration-tests/                  # Per-language integration tests
```

---

## Development

### Running Tests

```bash
# Run all tests
./gradlew test

# Run only plugin unit tests
./gradlew :plugin:test

# Run a specific test class
./gradlew :plugin:test --tests "dev.protocgen.textcodecs.jsonarray.codegen.java.JavaGeneratorTest"
```

### Code Formatting

The project uses [Spotless](https://github.com/diffplug/spotless) with Google Java Format:

```bash
# Check formatting
./gradlew spotlessCheck

# Apply formatting
./gradlew spotlessApply
```

### Golden-File Tests

Generator output is verified against golden files (`*.approved` snapshots). When generator behavior changes intentionally:

1. Run the tests -- they will fail and show a diff.
2. Review the diff to confirm the change is correct.
3. Update the golden files with the `--update-golden` flag.

### Adding a New Language Generator

To add support for a new target language:

1. Create a new package under `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/codegen/<language>/`.
2. Implement the following classes:
   - `<Lang>Generator` -- implements `LanguageGenerator`; the entry point for the language.
   - `<Lang>NameResolver` -- proto names to language-specific names (camelCase, snake_case, PascalCase, etc.).
   - `<Lang>TypeMapper` -- proto types to language types and default values.
   - `<Lang>CodeEmitter` -- generates complete source files (class/struct skeletons, fields, getters/setters).
   - `<Lang>SerializerGenerator` -- generates the serialize method body.
   - `<Lang>DeserializerGenerator` -- generates the deserialize method body.
3. Register the new generator in `PluginRunner.java` so it responds to the appropriate `lang=` value.
4. Add golden-file tests and integration tests.
5. If the language benefits from a shared runtime library, add it under `runtime/<language>/`.

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).
