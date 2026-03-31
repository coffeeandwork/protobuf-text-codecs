# Implementation Plan: `protoc-gen-jsonarray`

## Overview

A protoc plugin that generates code for serializing Protocol Buffer messages as positional JSON arrays. Supports multiple target languages via a `lang` parameter.

## Encoding Design

### Field Positioning Strategy

Array position is determined by **field number**: `position = field_number - 1`. Gaps in field numbering produce `null` entries in the array. This preserves protobuf's schema evolution guarantees:

- Adding a new field with a higher number than all existing fields appends to the array — old deserializers ignore trailing elements, new deserializers tolerate short arrays.
- Removing a field (and `reserved`-ing its number) leaves a `null` slot — both sides handle `null` correctly.
- Reusing or reordering field numbers is a breaking change (same as standard protobuf).

Example with non-contiguous field numbers:

```protobuf
message Sparse {
  string name = 1;
  int32 age = 5;
}
```

Serializes to: `["Alice", null, null, null, 30]` (positions 0, 1, 2, 3, 4 — gaps filled with `null`).

In practice, most proto files use contiguous field numbers starting at 1, so arrays are compact with no wasted slots.

### Serialization Encoding Rules

| Proto Type | JSON Representation | Example |
|---|---|---|
| `string` | JSON string | `"Alice"` |
| `int32/64`, `sint32/64`, `sfixed32/64` | JSON number | `30` |
| `uint32/64`, `fixed32/64` | JSON number | `30` |
| `float`, `double` | JSON number | `3.14` |
| `bool` | JSON boolean | `true` |
| `bytes` | Base64 string | `"AQID"` |
| `enum` | JSON number (enum value) | `1` |
| Nested `message` | Nested JSON array | `["addr", "city", "IL", 62704]` |
| `repeated` | JSON array in its slot | `[1, 2, 3]` |
| `map<string, V>` | JSON object | `{"a": 1}` |
| `map<non-string, V>` | Array of `[key, value]` pairs | `[[1, "a"], [2, "b"]]` |
| Unset scalar (implicit presence) | Default value (`0`, `""`, `false`) | `0` |
| Unset scalar (`optional` / explicit presence) | JSON `null` | `null` |
| Unset message | JSON `null` | `null` |
| Field-number gap | JSON `null` | `null` |

### Well-Known Types

| Well-Known Type | Encoding | Rationale |
|---|---|---|
| `google.protobuf.Timestamp` | JSON string (RFC 3339) | `"2024-01-15T10:30:00Z"` — human-readable, standard |
| `google.protobuf.Duration` | JSON string (`"${seconds}.${nanos}s"`) | `"1.500s"` — matches protobuf JSON mapping |
| `google.protobuf.BoolValue`, `Int32Value`, `StringValue`, etc. | Unwrapped scalar or `null` | `42` or `null` — the whole point of wrapper types is nullability |
| `google.protobuf.Struct` | JSON object (pass-through) | Already JSON-native |
| `google.protobuf.Value` | JSON value (pass-through) | Already JSON-native |
| `google.protobuf.ListValue` | JSON array (pass-through) | Already JSON-native |
| `google.protobuf.Any` | **Rejected with error** | Type is not known at compile time; positional encoding requires a known schema |
| `google.protobuf.FieldMask` | JSON string (comma-separated) | `"foo,bar.baz"` — matches protobuf JSON mapping |
| `google.protobuf.Empty` | Empty JSON array | `[]` |

### Proto2 vs Proto3

Both **proto2 and proto3** are supported. Proto2 features include required fields, optional fields with schema-specified default values, and groups (treated as nested messages). Extensions are incompatible with positional encoding and emit a warning — only base message fields are included in generated code.

## Project Structure

```
protobuf-text-codecs/
├── build.gradle.kts
├── settings.gradle.kts
├── CLAUDE.md
├── docs/
│   └── PLAN.md
│
├── plugin/                                    # The protoc plugin (Java)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/dev/protocgen/textcodecs/jsonarray/
│       │   ├── Main.java                      # Entry point: stdin → stdout
│       │   ├── PluginRunner.java              # Orchestrator: CodeGeneratorRequest → Response
│       │   ├── ProtoFileProcessor.java        # Iterates messages in a FileDescriptorProto
│       │   ├── MessageAnalyzer.java           # Extracts field model from DescriptorProto
│       │   ├── CodeWriter.java                # Indented StringBuilder wrapper
│       │   │
│       │   ├── model/                         # Language-neutral internal model
│       │   │   ├── ProtoField.java            # Normalized field info (type, cardinality, position)
│       │   │   ├── ProtoMessage.java          # Analyzed message with ordered fields
│       │   │   ├── ProtoEnum.java             # Enum definition
│       │   │   ├── ProtoFile.java             # File-level metadata (package, options)
│       │   │   └── TypeRegistry.java          # Global map of fully-qualified type names → descriptors
│       │   │
│       │   └── codegen/                       # Code generators
│       │       ├── LanguageGenerator.java     # Interface for language-specific generation
│       │       ├── NameResolver.java          # Interface: proto names → language-specific names
│       │       ├── TypeMapper.java            # Interface: proto types → language types + defaults
│       │       ├── AbstractCodeEmitter.java   # Template: class skeleton generation
│       │       ├── AbstractSerializerGenerator.java   # Template: field-iteration algorithm
│       │       ├── AbstractDeserializerGenerator.java # Template: field-reading algorithm
│       │       │
│       │       ├── java/
│       │       │   ├── JavaGenerator.java
│       │       │   ├── JavaNameResolver.java
│       │       │   ├── JavaTypeMapper.java
│       │       │   ├── JavaCodeEmitter.java
│       │       │   ├── JavaSerializerGenerator.java
│       │       │   └── JavaDeserializerGenerator.java
│       │       ├── python/
│       │       │   ├── PythonGenerator.java
│       │       │   ├── PythonNameResolver.java
│       │       │   ├── PythonTypeMapper.java
│       │       │   ├── PythonCodeEmitter.java
│       │       │   ├── PythonSerializerGenerator.java
│       │       │   └── PythonDeserializerGenerator.java
│       │       ├── javascript/
│       │       │   ├── JavaScriptGenerator.java
│       │       │   ├── JavaScriptNameResolver.java
│       │       │   ├── JavaScriptTypeMapper.java
│       │       │   ├── JavaScriptCodeEmitter.java
│       │       │   ├── JavaScriptSerializerGenerator.java
│       │       │   └── JavaScriptDeserializerGenerator.java
│       │       ├── typescript/
│       │       │   ├── TypeScriptGenerator.java    # Extends JS, adds type annotations
│       │       │   └── TypeScriptTypeMapper.java
│       │       ├── c/
│       │       │   ├── CGenerator.java
│       │       │   ├── CNameResolver.java
│       │       │   ├── CTypeMapper.java
│       │       │   ├── CCodeEmitter.java
│       │       │   ├── CSerializerGenerator.java
│       │       │   └── CDeserializerGenerator.java
│       │       ├── cpp/
│       │       │   ├── CppGenerator.java
│       │       │   ├── CppNameResolver.java
│       │       │   ├── CppTypeMapper.java
│       │       │   ├── CppCodeEmitter.java
│       │       │   ├── CppSerializerGenerator.java
│       │       │   └── CppDeserializerGenerator.java
│       │       ├── rust/
│       │       │   ├── RustGenerator.java
│       │       │   ├── RustNameResolver.java
│       │       │   ├── RustTypeMapper.java
│       │       │   ├── RustCodeEmitter.java
│       │       │   ├── RustSerializerGenerator.java
│       │       │   └── RustDeserializerGenerator.java
│       │       ├── zig/
│       │       │   ├── ZigGenerator.java
│       │       │   ├── ZigNameResolver.java
│       │       │   ├── ZigTypeMapper.java
│       │       │   ├── ZigCodeEmitter.java
│       │       │   ├── ZigSerializerGenerator.java
│       │       │   └── ZigDeserializerGenerator.java
│       │       └── go/
│       │           ├── GoGenerator.java
│       │           ├── GoNameResolver.java
│       │           ├── GoTypeMapper.java
│       │           ├── GoCodeEmitter.java
│       │           ├── GoSerializerGenerator.java
│       │           └── GoDeserializerGenerator.java
│       │
│       └── test/java/dev/protocgen/textcodecs/jsonarray/
│           ├── MessageAnalyzerTest.java
│           ├── CodeWriterTest.java
│           ├── codegen/
│           │   ├── java/JavaGeneratorTest.java
│           │   ├── python/PythonGeneratorTest.java
│           │   └── ...per language...
│           └── golden/                        # Golden file snapshots
│               ├── user.proto.java.approved
│               ├── user.proto.py.approved
│               └── ...
│
├── runtime/                                   # Runtime libraries (only for languages that need them)
│   ├── java/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/dev/protocgen/textcodecs/jsonarray/runtime/
│   │       ├── JsonArrayCodec.java            # Interface: serialize/deserialize contract
│   │       └── FieldCodecs.java               # Static helpers for scalar encoding/decoding
│   ├── c/
│   │   ├── CMakeLists.txt
│   │   ├── include/jsonarray/codec.h
│   │   └── src/codec.c
│   ├── cpp/
│   │   └── include/jsonarray/
│   │       ├── codec.hpp
│   │       └── field_codecs.hpp
│   └── rust/
│       └── jsonarray-runtime/
│           ├── Cargo.toml
│           └── src/lib.rs
│
├── test-protos/
│   └── src/main/proto/
│       ├── user.proto
│       ├── address.proto
│       ├── kitchen_sink.proto                 # Exercises every field type
│       └── well_known_types_usage.proto       # Uses Timestamp, Duration, wrappers, etc.
│
└── integration-tests/
    ├── java/
    ├── python/
    └── ...per language...
```

### Why Some Languages Get Runtimes and Others Don't

| Language | Runtime? | Reason |
|---|---|---|
| Java | Yes | Jackson `ArrayNode` API is verbose; `FieldCodecs` reduces generated code size |
| C | Yes | cJSON memory management and helpers warrant shared code |
| C++ | Yes | nlohmann/json helpers, RAII wrappers |
| Rust | Yes | `serde_json` trait impls, error types |
| Python | No | `json.dumps(list)` / `json.loads(s)` — generated code is self-contained |
| JavaScript | No | `JSON.stringify(arr)` / `JSON.parse(s)` — generated code is self-contained |
| TypeScript | No | Same as JS, plus inline type definitions |
| Zig | No | `std.json` is sufficient; generated code is self-contained |
| Go | No | `encoding/json` is sufficient; generated code is self-contained |

## Plugin Architecture

### Core (language-agnostic)

- **`Main.java`** — Reads `CodeGeneratorRequest` from stdin, writes `CodeGeneratorResponse` to stdout. Catches all exceptions and returns them via `CodeGeneratorResponse.error`.
- **`PluginRunner.java`** — Parses the `lang=<language>` parameter, builds a global `TypeRegistry`, delegates to `ProtoFileProcessor`. Supports both proto2 and proto3. Rejects `google.protobuf.Any` usage with a clear error. Warns about extensions.
- **`ProtoFileProcessor.java`** — Walks `FileDescriptorProto`, extracts messages/enums, delegates to the selected `LanguageGenerator`. Skips synthetic map-entry messages.
- **`MessageAnalyzer.java`** — Converts `DescriptorProto` into `ProtoMessage` model. Fields are sorted by field number. Detects: maps (via `getOptions().getMapEntry()`), real oneofs (via `hasOneofIndex() && !getProto3Optional()`), well-known type references, repeated fields, type references.
- **`model/`** — Language-neutral internal representation.

### Shared abstractions for code generation

```java
public interface LanguageGenerator {
    List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry);
    String languageId();
}

public interface NameResolver {
    String protoPackageToLangPackage(String protoPackage, FileOptions options);
    String messageClassName(String protoName);
    String fieldName(String protoFieldName);
    String getterName(String protoFieldName);
    String setterName(String protoFieldName);
    String enumConstantName(String protoName);
    String fileExtension();
}

public interface TypeMapper {
    String javaType(ProtoField field);        // "int", "String", "List<Address>", etc.
    String defaultValue(ProtoField field);    // "0", "\"\"", "null", etc.
    String jsonWriteExpr(ProtoField field, String varName);   // language-specific write
    String jsonReadExpr(ProtoField field, String nodeExpr);   // language-specific read
}
```

**`AbstractSerializerGenerator`** defines the field-iteration algorithm as a template method:

```java
// Pseudocode — each language overrides the syntax-specific methods
public final String generate(ProtoMessage message) {
    beginMethod();
    createArrayNode();
    int maxFieldNumber = message.maxFieldNumber();
    for (int pos = 0; pos < maxFieldNumber; pos++) {
        ProtoField field = message.fieldAtPosition(pos); // null if gap
        if (field == null) {
            emitNullSlot();
        } else if (field.isWellKnownType()) {
            emitWellKnownType(field);
        } else switch (field.kind()) {
            case SCALAR -> emitScalar(field);
            case MESSAGE -> emitNestedMessage(field);
            case ENUM -> emitEnum(field);
            case REPEATED -> emitRepeated(field);
            case MAP -> emitMap(field);
            case ONEOF_MEMBER -> emitOneofMember(field);
        }
    }
    returnArray();
    endMethod();
}
```

Each language only implements the `emit*()` methods with its own syntax. `AbstractDeserializerGenerator` follows the same pattern in reverse.

### Per-language JSON library dependencies

| Language | JSON Library | Serialized Type |
|---|---|---|
| Java | Zero-dependency StringBuilder | `String` / `byte[]` |
| Python | `json` (stdlib) | `list` / `str` |
| JavaScript | Native `JSON` | `Array` / `string` |
| TypeScript | Native `JSON` (with type annotations) | `Array` / `string` |
| C | cJSON | `cJSON*` / `char*` |
| C++ | nlohmann/json | `nlohmann::json` / `std::string` |
| Rust | `serde_json` | `serde_json::Value` / `String` |
| Zig | `std.json` | `std.json.Value` / `[]const u8` |
| Go | `encoding/json` | `[]any` / `[]byte` |

## Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Field positioning | `field_number - 1` (sparse) | Preserves protobuf schema evolution guarantees |
| Plugin language | Java | `protobuf-java` provides `PluginProtos` for the plugin protocol |
| Multi-language | Single plugin, `lang` parameter | One binary to distribute, shared analysis logic |
| Map serialization | JSON object (string keys), array-of-pairs (other keys) | JSON object keys must be strings |
| Unset scalars (implicit) | Serialize as default value | Matches proto3 semantics |
| Unset scalars (optional) | Serialize as JSON `null` | Distinguishes "not set" from "set to default" |
| Oneof fields | Only active field has value; others are `null` | Preserves positional alignment |
| Well-known types | Special-cased in serializer/deserializer | Human-readable, matches protobuf JSON mapping conventions |
| `google.protobuf.Any` | Rejected at generation time | Positional encoding requires compile-time schema knowledge |
| Proto2 | Fully supported | Required fields, optional with defaults, groups, extension warnings |
| Runtimes | Only for languages where they reduce generated code | Python/JS/TS/Go/Zig don't need one; Java/C/C++/Rust do |
| Code gen duplication | Abstract template-method base classes | Field-iteration logic written once, each language provides syntax |

## Edge Cases

- **Field-number gaps**: Positions with no field serialize as JSON `null`
- **Oneof**: Only the active field serializes its value; all other oneof member positions serialize as `null`
- **Maps**: Detected via `getOptions().getMapEntry()`; map-entry messages are NOT generated as standalone classes
- **Proto3 optional presence**: Tracked via `BitSet` (Java) or equivalent; unset optionals serialize as `null`
- **Short arrays on deserialization**: Trailing fields not present in the array use default values (forward compatibility)
- **Enums**: Serialize as integer value; unknown values stored as int on deserialization
- **Bytes**: Base64 encoded/decoded in all languages
- **Nested messages**: Recursive serialize/deserialize calls; `null` check before recursing
- **Recursive message references** (e.g. `TreeNode` with `repeated TreeNode children`): Handled naturally by null checks; no infinite recursion since the data is finite

## Invocation

```bash
# Java output
protoc --plugin=protoc-gen-jsonarray=./plugin \
       --jsonarray_out=lang=java:./gen-java \
       user.proto

# Python output
protoc --plugin=protoc-gen-jsonarray=./plugin \
       --jsonarray_out=lang=python:./gen-python \
       user.proto

# Multiple languages (run protoc once per language)
protoc --plugin=... --jsonarray_out=lang=java:./gen-java user.proto
protoc --plugin=... --jsonarray_out=lang=rust:./gen-rust user.proto
```

## Testing Strategy

### Unit tests (per generator)

- **`MessageAnalyzerTest`**: Hand-constructed `DescriptorProto` objects → verify field ordering, oneof grouping, map detection, well-known type identification, field-number gap handling.
- **`CodeWriterTest`**: Verify indentation, block nesting, output formatting.
- **Per-language `*GeneratorTest`**: Feed a `ProtoMessage` model → verify generated source code for correctness.

### Golden-file / snapshot tests

Each generator has golden files (`*.approved`) containing the expected generated source for reference proto files (`user.proto`, `kitchen_sink.proto`). Tests compare actual output against the golden file. Workflow:

1. Run generator on test proto → produce actual output
2. Compare against `golden/<proto>.<lang>.approved`
3. If different, test fails and shows diff
4. Developer reviews and accepts/rejects via `--update-golden` flag

This catches unintentional changes to generated output and makes reviewing generator changes trivial.

### Integration tests (per language)

- Compile test protos through protoc with the plugin
- Compile/run the generated code in the target language
- Round-trip tests: `deserialize(serialize(obj))` equals original
- Cross-field-type coverage via `kitchen_sink.proto`
- Well-known type tests via `well_known_types_usage.proto`
- Schema evolution tests: deserialize data produced by an older schema version (shorter array) and verify defaults are applied

### Cross-language round-trip tests (Phase 10)

Serialize in language A, deserialize in language B. Verifies encoding compatibility. Implemented as a test harness that shells out to each language's runtime.

## Implementation Phases

### Phase 1: Scaffolding & Core Pipeline
1. Gradle multi-module project setup (`plugin`, `runtime/java`, `test-protos`, `integration-tests/java`)
2. `Main.java` + `PluginRunner.java` — stdin/stdout pipeline, verify protoc can invoke
3. `model/` package — `ProtoField`, `ProtoMessage`, `ProtoEnum`, `ProtoFile`, `TypeRegistry`
4. `MessageAnalyzer.java` — Parse `DescriptorProto` into internal model, field-number-based ordering, gap detection
5. `CodeWriter.java` — Indented string builder utility
6. `codegen/` interfaces — `LanguageGenerator`, `NameResolver`, `TypeMapper`
7. `Abstract*Generator` base classes — template-method field iteration for serialize/deserialize

### Phase 2: Java Generator (reference implementation)
8. `JavaNameResolver` — Package resolution, camelCase fields, PascalCase classes
9. `JavaTypeMapper` — Full proto type → Java type mapping with defaults
10. `JavaCodeEmitter` — Class skeleton: fields, getters, setters, constructors
11. `JavaSerializerGenerator` — Scalar fields, then nested messages
12. `JavaDeserializerGenerator` — Scalar fields, then nested messages
13. Repeated field support
14. Enum support (generate enum classes)
15. Map field support
16. Oneof support
17. Proto3 optional presence tracking (`BitSet`)
18. Well-known type handling (Timestamp, Duration, wrappers, etc.)
19. Java runtime library (`JsonArrayCodec`, `FieldCodecs`)
20. Golden-file tests for Java generator
21. Integration tests with `kitchen_sink.proto` and `well_known_types_usage.proto`

### Phase 3: Python Generator
22. `PythonNameResolver`, `PythonTypeMapper`, `PythonCodeEmitter`, `PythonSerializerGenerator`, `PythonDeserializerGenerator`
23. Self-contained generated code (no runtime library)
24. Golden-file + integration tests

### Phase 4: JavaScript/TypeScript Generator
25. `JavaScriptGenerator` and `TypeScriptGenerator` (TS extends JS, adds type annotations)
26. Self-contained generated code (no runtime library)
27. Golden-file + integration tests

### Phase 5: C Generator
28. `CNameResolver`, `CTypeMapper`, `CCodeEmitter`, `CSerializerGenerator`, `CDeserializerGenerator`
29. C runtime (cJSON-based)
30. Golden-file + integration tests

### Phase 6: C++ Generator
31. `CppNameResolver`, `CppTypeMapper`, `CppCodeEmitter`, `CppSerializerGenerator`, `CppDeserializerGenerator`
32. C++ runtime (header-only, nlohmann/json)
33. Golden-file + integration tests

### Phase 7: Rust Generator
34. `RustNameResolver`, `RustTypeMapper`, `RustCodeEmitter`, `RustSerializerGenerator`, `RustDeserializerGenerator`
35. Rust runtime crate
36. Golden-file + integration tests

### Phase 8: Zig Generator
37. `ZigNameResolver`, `ZigTypeMapper`, `ZigCodeEmitter`, `ZigSerializerGenerator`, `ZigDeserializerGenerator`
38. Self-contained generated code
39. Golden-file + integration tests

### Phase 9: Go Generator
40. `GoNameResolver`, `GoTypeMapper`, `GoCodeEmitter`, `GoSerializerGenerator`, `GoDeserializerGenerator`
41. Self-contained generated code
42. Golden-file + integration tests

### Phase 10: Polish & Cross-cutting
43. Cross-language round-trip test harness
44. Error handling (malformed input, unsupported features, Any rejection, extension warnings)
45. Wrapper shell script for easy invocation
46. Schema evolution test suite (old schema data → new schema deserializer)

### Phase 11: Build System Integration (future)
47. Gradle plugin (`protoc-gen-jsonarray-gradle`) wrapping protoc invocation
48. Maven plugin (`protoc-gen-jsonarray-maven-plugin`)
49. Package runtimes for distribution (Maven Central for Java, crates.io for Rust, etc.)

### Future Work
- Proto2 support (required fields, schema-specified defaults, extensions)
- Streaming serialization (write directly to output stream instead of building in-memory tree)
- `google.protobuf.Any` support (with runtime type registry)
- `protoc --jsonarray_out=lang=java,python:./out` multi-language single invocation
