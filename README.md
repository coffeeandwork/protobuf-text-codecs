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

---

## Supported Languages

| Language | `lang=` | Dependencies |
|---|---|---|
| Java | `java` | None (zero-dependency) |
| Python | `python` | `json` (stdlib) |
| JavaScript | `javascript` (`js`) | None |
| TypeScript | `typescript` (`ts`) | None |
| C | `c` | cJSON + runtime |
| C++ | `cpp` (`c++`) | nlohmann/json + runtime |
| Rust | `rust` | `serde_json` + runtime |
| Zig | `zig` | `std.json` |
| Go | `go` | `encoding/json` |
| C# | `csharp` (`c#`) | `System.Text.Json` |
| Kotlin | `kotlin` (`kt`) | `kotlinx.serialization.json` |
| Swift | `swift` | Foundation (`JSONSerialization`) |
| Dart | `dart` | `dart:convert` |
| PHP | `php` | `json_encode`/`json_decode` (built-in) |
| Ruby | `ruby` (`rb`) | `json` (stdlib) |
| Objective-C | `objc` (`objective-c`) | Foundation (`NSJSONSerialization`) |
| Perl | `perl` | `JSON` (CPAN) |

Languages with "+ runtime" ship a small runtime library under `runtime/`. All others produce fully self-contained code.

New to Protocol Buffers? See the official tutorials for [Java](https://protobuf.dev/getting-started/javatutorial/), [C++](https://protobuf.dev/getting-started/cpptutorial/), [Python](https://protobuf.dev/getting-started/pythontutorial/), [Go](https://protobuf.dev/getting-started/gotutorial/), [C#](https://protobuf.dev/getting-started/csharptutorial/), [Dart](https://protobuf.dev/getting-started/darttutorial/), [Kotlin](https://protobuf.dev/getting-started/kotlintutorial/), or the reference pages for [Rust](https://protobuf.dev/reference/rust/), [PHP](https://protobuf.dev/reference/php/), [Ruby](https://protobuf.dev/reference/ruby/), and [Objective-C](https://protobuf.dev/reference/objective-c/).

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

Invoke `protoc` with the plugin and specify the target language via the `lang=` parameter:

```bash
# JSON Array format
protoc --plugin=protoc-gen-jsonarray=./protoc-gen-jsonarray \
       --jsonarray_out=lang=java:./gen-java user.proto

# pbtk URL format
protoc --plugin=protoc-gen-pbtkurl=./protoc-gen-pbtkurl \
       --pbtkurl_out=lang=python:./gen-python user.proto
```

Substitute any `lang=` value from the Supported Languages table. Each `protoc` invocation produces one language; run it multiple times for multiple languages.

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

Each generated class/struct provides serialization and deserialization methods. Method names follow each language's conventions.

### JSON Array API

| Language | Serialize | Deserialize |
|---|---|---|
| Java | `user.toJsonString()` / `user.toJsonBytes()` | `User.fromJsonString(s)` / `User.fromJsonBytes(b)` |
| Python | `user.to_json_string()` | `User.from_json_string(s)` |
| Go | `user.ToJsonString()` | `UserFromJsonString(s)` |
| JS / TS | `user.toJsonString()` | `User.fromJsonString(s)` |
| C | `user_to_json_string(user)` | `user_from_json_string(s)` |
| C++ | `user.toJsonString()` | `User::fromJsonString(s)` |
| Rust | `user.to_json_string()` | `User::from_json_string(s)?` |
| Zig | `user.toJsonString(alloc)` | `User.fromJsonString(s, alloc)` |

All languages also expose lower-level `serialize()`/`deserialize()` methods that work with the language's native JSON type (e.g., `serde_json::Value` in Rust, `cJSON*` in C). C requires explicit memory management via `user_free()` and `free()`.

### pbtk URL API

| Language | Serialize | Deserialize |
|---|---|---|
| Java | `user.toPbtkUrl()` | `User.fromPbtkUrl(s)` |
| Python | `user.to_pbtk_url()` | `User.from_pbtk_url(s)` |
| Go | `user.ToPbtkUrl()` | `UserFromPbtkUrl(s)` |
| JS / TS | `user.toPbtkUrl()` | `User.fromPbtkUrl(s)` |
| C | `user_to_pbtk_url(user)` | `user_from_pbtk_url(s)` |
| C++ | `user.to_pbtk_url()` | `User::from_pbtk_url(s)` |
| Rust | `user.to_pbtk_url()` | `User::from_pbtk_url(s)?` |
| Zig | `user.toPbtkUrl(alloc)` | `User.fromPbtkUrl(s, alloc)` |

No JSON library dependency — pure string manipulation. Generated classes have the same field accessors as the JSON array versions.

---

## Limitations

- **Both proto2 and proto3 are supported.** Proto2 features including required fields, optional fields with schema-specified defaults, and groups are handled. Extensions are incompatible with positional encoding and emit a warning (only base message fields are included).
- **No `google.protobuf.Any` support.** Positional encoding requires knowing the message schema at compile time. `Any` embeds an arbitrary message whose type is only known at runtime, making positional encoding impossible. The plugin rejects files that use `Any` with a clear error.
- **No streaming serialization.** Serialization builds a complete in-memory representation (array/list/slice) before encoding to JSON. Streaming directly to an output stream is not currently supported.
- **Single language per invocation.** Each `protoc` run produces output for one target language. To generate multiple languages, invoke `protoc` multiple times with different `lang=` values.

---

## How It Works

Both plugins implement the standard [protoc plugin protocol](https://protobuf.dev/reference/other/) (links verified 2026-03-30). The protocol is defined in [`plugin.proto`](https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/compiler/plugin.proto) and [`descriptor.proto`](https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/descriptor.proto).

**Plugin lifecycle:**

1. **`protoc` invokes the plugin** as a subprocess, sending a binary [`CodeGeneratorRequest`](https://protobuf.dev/reference/cpp/api-docs/google.protobuf.compiler.plugin.pb/#CodeGeneratorRequest) on stdin. The request contains the parsed [`FileDescriptorProto`](https://protobuf.dev/reference/cpp/api-docs/google.protobuf.descriptor.pb/#FileDescriptorProto) for each `.proto` file plus the `lang=` parameter string.
   - **Our entry points:** `Main.java` (jsonarray) / `PbtkMain.java` (pbtkurl) read the binary request from stdin.

2. **Parameter parsing and dispatch.** The `lang=` parameter selects which language generator to instantiate from a registry of 17 generators.
   - **Our orchestrators:** `PluginRunner.java` / `PbtkPluginRunner.java` parse parameters, build the `TypeRegistry`, and dispatch to the selected `LanguageGenerator`.

3. **Proto descriptors are converted to an internal model.** The raw protobuf [`DescriptorProto`](https://protobuf.dev/reference/cpp/api-docs/google.protobuf.descriptor.pb/#DescriptorProto) and [`FieldDescriptorProto`](https://protobuf.dev/reference/cpp/api-docs/google.protobuf.descriptor.pb/#FieldDescriptorProto) messages are analyzed and converted to a language-neutral model with validation.
   - **Our analyzer:** `MessageAnalyzer.java` converts descriptors to `ProtoMessage`, `ProtoField`, `ProtoEnum`, and `ProtoFile` model objects. `TypeRegistry.java` resolves cross-file type references.

4. **Code generation.** Each language generator implements `LanguageGenerator.generate()` which receives the model and emits source code using `CodeWriter`.
   - **Per-language generators:** `codegen/<lang>/` (6 classes each: Generator, NameResolver, TypeMapper, CodeEmitter, SerializerGenerator, DeserializerGenerator).
   - **Keyword escaping:** `KeywordUtil.java` prevents generated identifiers from colliding with reserved words in 17 languages.

5. **Response.** The plugin writes a binary [`CodeGeneratorResponse`](https://protobuf.dev/reference/cpp/api-docs/google.protobuf.compiler.plugin.pb/#CodeGeneratorResponse) to stdout containing the generated source files.

For the full C++ API reference of the plugin protocol types, see the [plugin.pb.h docs](https://protobuf.dev/reference/cpp/api-docs/google.protobuf.compiler.plugin.pb/) and [descriptor.pb.h docs](https://protobuf.dev/reference/cpp/api-docs/google.protobuf.descriptor.pb/) (links verified 2026-03-30).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions, testing, code formatting, and how to add a new language generator.

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).
