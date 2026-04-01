# protobuf-text-codecs

A suite of `protoc` compiler plugins that generate code for serializing Protocol Buffer messages using compact text-based formats. Two encoding formats are supported, each as a separate protoc plugin:

## Encoding Formats

### 1. JSON Array (`protoc-gen-jsonarray`)

Positional JSON array encoding where `position = field_number - 1`.

```
["Alice", "Smith", 30, ["123 Main Street", "Springfield", "IL", 62704]]
```

- Zero-dependency Java (StringBuilder-based), stdlib JSON (Python/JS/Go/Ruby), cJSON (C), nlohmann/json (C++), serde_json (Rust), std.json (Zig), System.Text.Json (C#), kotlinx.serialization (Kotlin), Foundation JSON (Swift/Objective-C), dart:convert (Dart), json built-in (PHP), JSON CPAN (Perl)
- Gaps in field numbering produce `null` entries
- Well-known types have special encoding (Timestamp → RFC 3339, etc.)

### 2. pbtk URL (`protoc-gen-pbtkurl`)

Google Maps-style protobuf URL encoding: `!<field_number><type_char><value>`.

```
!1sAlice!2sSmith!3i30!4m4!1s123+Main+Street!2sSpringfield!3sIL!4i62704
```

- Type chars: `s`=string, `i`=integer, `d`=double, `f`=float, `b`=bool(0/1), `e`=enum, `m`=message, `z`=bytes
- No JSON library dependency — pure string manipulation
- URL-safe output; strings are percent-encoded, bytes are base64-encoded
- Absent fields are simply omitted (no null padding)

## Supported Languages

Java, Python, JavaScript, TypeScript, C, C++, Rust, Zig, Go, C#, Kotlin, Swift, Dart, PHP, Ruby, Objective-C, Perl (17 languages, both formats).

## Build & Usage

```bash
# Build both plugins
./gradlew :plugin:shadowJar :plugin:pbtkShadowJar

# JSON Array format
protoc \
  --plugin=protoc-gen-jsonarray=/path/to/protoc-gen-jsonarray \
  --jsonarray_out=lang=java:./generated-sources \
  src/main/proto/user.proto

# pbtk URL format
protoc \
  --plugin=protoc-gen-pbtkurl=/path/to/protoc-gen-pbtkurl \
  --pbtkurl_out=lang=java:./generated-sources \
  src/main/proto/user.proto
```

Both plugins accept `lang=<language>` parameter. Default is `java`. Aliases: `js`=javascript, `ts`=typescript, `c++`=cpp, `c#`=csharp, `kt`=kotlin, `rb`=ruby, `objective-c`=objc (24 entries: 17 languages + 7 aliases).

## Architecture

The plugin is implemented in Java 17 and uses the standard protoc plugin protocol (CodeGeneratorRequest/CodeGeneratorResponse via stdin/stdout binary protobuf).

### Shared Infrastructure (`dev.protocgen.textcodecs.jsonarray`)

- `Main.java` / `PluginRunner.java` — JSON array plugin entry point and orchestrator
- `MessageAnalyzer.java` — converts proto descriptors to internal model with validation
- `ProtoFileProcessor.java` — per-file code generation dispatch
- `CodeWriter.java` — indented source code output utility
- `model/` — language-neutral model: ProtoField, ProtoMessage, ProtoEnum, ProtoFile, TypeRegistry, WellKnownType
- `codegen/` — shared interfaces (LanguageGenerator, NameResolver, TypeMapper) and KeywordUtil (951 lines, 16 keyword sets: 14 in KeywordUtil, 2 delegated from NameResolvers)

### JSON Array Generators (`dev.protocgen.textcodecs.jsonarray.codegen.<lang>`)

Each language has 6 classes: Generator, CodeEmitter, SerializerGenerator, DeserializerGenerator, NameResolver, TypeMapper.

### pbtk URL Generators (`dev.protocgen.textcodecs.pbtkurl`)

- `PbtkMain.java` / `PbtkPluginRunner.java` — pbtk plugin entry point and orchestrator
- `codegen/<lang>/` — per-language pbtk generators, reusing NameResolver and TypeMapper from the JSON array generators

## Key Design Decisions

- **Field-number-based positioning** (not declaration order) preserves schema evolution compatibility
- **int64/uint64 as JSON strings** in JSON array format to prevent precision loss beyond 2^53
- **NaN/Infinity → null** since these are not valid JSON values
- **Cross-file imports** generated for all 17 languages using language-appropriate patterns
- **Lazy imports** in Python/JS/TS to prevent circular import issues
- **Defense-in-depth validation**: field name regex, message/enum name validation, type reference validation, default value validation, path traversal prevention
- **Proto2 support**: required fields, optional with schema defaults, groups (→ messages)
- **google.protobuf.Any rejected** at generation time (positional encoding requires compile-time schema)

## Testing

```bash
./gradlew :plugin:test                    # All tests (1,090 tests)
./gradlew :plugin:test --tests "*.PbtkJavaCodeGenTest"  # pbtk tests only
./gradlew :plugin:jacocoTestReport        # Coverage report
./gradlew spotlessCheck                   # Code formatting
```

- 19 test classes total (16 in jsonarray package including 2 schema evolution, 3 in pbtkurl package)
- Parameterized tests across 16 non-Java languages
- Golden-file snapshot tests for all 17 languages
- Schema evolution tests: 134 tests across all 17 languages (forward/backward compat, field removal, gap handling)
- Safety/security tests (SR-001–004, SEC-001–004) for both JSON array and pbtk formats
- Integration tests (Java ↔ Python cross-language round-trip)

## Documentation

Formal assurance documentation (DO-330/IEC 62304-inspired, 6 phases) in `docs/`:
- Phase 1: System Analysis
- Phase 2: Hazard Analysis + Requirements (18 FR, 4 SR, 4 SEC)
- Phase 3: Test Strategy
- Phase 4: Test Traceability Matrix
- Phase 5: Security Assessment (9 vulnerabilities identified and fixed)
- Phase 6: Assurance Case + Evidence Index
