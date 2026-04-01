# System Analysis

================================================================================
Generated: 2026-03-24T20:00:00-04:00
Model: Claude Opus 4.6 (1M context)
Review Status: PENDING
Reviewed By: _______________
================================================================================

```
LIMITATIONS:
- This is a DRAFT artifact requiring human validation
- Cannot execute tests or measure coverage independently
- Cannot provide independent verification
- This is an unqualified tool output per DO-330/IEC 62304
- All outputs require human review before use
- No certification claims - these support readiness only
```

## 1. System Classification

- **Type:** Hybrid — CLI Tool (protoc compiler plugin) + Code Generator (produces secondary systems)
- **Primary Language:** Java 17
- **Framework:** Google Protocol Buffer Compiler Plugin Protocol (stdin/stdout binary protobuf)
- **Execution Model:** Synchronous, single-invocation batch processing
- **Concurrency:** None — entirely single-threaded
- **State:** Mostly stateless — transient per-invocation state in PythonGenerator.emittedInitFiles and RustGenerator.emittedModFiles/modulesByDir (used to deduplicate output files across multiple proto files within a single protoc run); no persistent state across invocations

**Important distinction:** This system produces TWO artifacts:
1. **The plugin itself** — a Java CLI tool (this document's primary focus)
2. **Generated source code** — runs in the user's application with its own trust model, dependencies, and failure characteristics (analyzed in Section 14)

## 2. Project Structure

```
protobuf-text-codecs/
├── plugin/                           # Main protoc plugin (Java)
│   └── src/
│       ├── main/java/.../            # 140 source files, ~47,300 lines
│       │   ├── Main.java             # Entry point (stdin/stdout)
│       │   ├── PluginRunner.java     # Orchestrator
│       │   ├── ProtoFileProcessor.java
│       │   ├── MessageAnalyzer.java
│       │   ├── CodeWriter.java
│       │   ├── model/                # Language-neutral internal model (6 classes)
│       │   └── codegen/              # 17 language generators (102+ classes)
│       │       ├── KeywordUtil.java
│       │       ├── java/ python/ javascript/ typescript/
│       │       ├── c/ cpp/ rust/ zig/ go/
│       │       ├── csharp/ kotlin/ swift/ dart/
│       │       ├── php/ ruby/ objc/ perl/
│       │       └── LanguageGenerator.java (interface)
│       └── test/java/.../            # 19 test files
├── runtime/                          # Per-language runtime libraries
│   ├── java/  (zero-dependency, 2 classes)
│   ├── c/     (cJSON-based, 2 files)
│   ├── cpp/   (nlohmann/json header-only, 1 file)
│   └── rust/  (serde_json-based, 1 crate)
├── test-protos/                      # 5 test .proto files
├── integration-tests/                # Shell-based integration tests (4 files)
├── protoc-gen-jsonarray              # Bash wrapper script
├── protoc-gen-pbtkurl               # Bash wrapper script (pbtk URL format)
├── docs/                             # Documentation
│   ├── archive/                      # Historical documents
│   │   ├── BUG_CATALOG_v0.1.md
│   │   └── PLAN.md
│   └── SYSTEM_ANALYSIS.md           # This file
├── build.gradle  settings.gradle     # Groovy DSL build files
├── SECURITY.md  NOTICE               # Security policy and notices
└── LICENSE  README.md  CHANGELOG.md  CONTRIBUTING.md
```

Key directories:
- `plugin/src/main/`: Plugin source code — the core product
- `plugin/src/test/`: Unit and code generation tests (1,090 tests)
- `runtime/`: Thin runtime libraries for languages that need them (Java, C, C++, Rust)
- `test-protos/`: Proto definitions for testing (user.proto, address.proto, kitchen_sink.proto, edge_cases.proto, proto2_test.proto)
- `integration-tests/`: Cross-language round-trip and schema evolution tests

## 3. Entry Points

| Entry Point | Type | Location | Description |
|-------------|------|----------|-------------|
| `Main.main()` | CLI (protoc plugin) | `Main.java` | Reads CodeGeneratorRequest from stdin, writes CodeGeneratorResponse to stdout |
| `Main.main()` --version | CLI | `Main.java` | Prints `protoc-gen-jsonarray 0.2.0` to stdout and exits |
| `protoc-gen-jsonarray` | Shell wrapper | `protoc-gen-jsonarray` | Validates java/JAR existence, delegates to Main |
| `PluginRunner.run()` | Internal API | `PluginRunner.java` | Core orchestration — can be called programmatically (used by 1,090 unit tests) |
| `LanguageGenerator.generate()` | Internal API | `LanguageGenerator.java` | Per-language code generation interface (17 implementations) |

## 4. Components

### Core Framework (Criticality A — bugs here affect ALL generated output)

| Component | Location | Purpose | Lines | Test Coverage |
|-----------|----------|---------|-------|---------------|
| PluginRunner | `PluginRunner.java` | Orchestration, parameter parsing, language dispatch, path validation | ~198 | 93.3% (22 tests) |
| ProtoFileProcessor | `ProtoFileProcessor.java` | Proto file analysis, comment extraction, model construction | 124 | 95.5% (indirect) |
| MessageAnalyzer | `MessageAnalyzer.java` | Proto descriptor → ProtoMessage conversion, field ordering, validation | ~387 | 96.9% (37 tests) |
| TypeRegistry | `model/TypeRegistry.java` | Global type catalog for cross-file resolution | 93 | 100% (14 tests) |
| ProtoField | `model/ProtoField.java` | Field model with builder, validation | 297 | 100% |
| ProtoMessage | `model/ProtoMessage.java` | Message model, field position mapping | 154 | 91.5% |
| ProtoEnum | `model/ProtoEnum.java` | Enum model | 46 | 100% |
| ProtoFile | `model/ProtoFile.java` | File metadata model | 90 | 70.3% |
| WellKnownType | `model/WellKnownType.java` | Google WKT detection (17 types) | 84 | 100% (3 tests) |
| CodeWriter | `CodeWriter.java` | Indented source code builder | 129 | 100% (14 tests) |
| KeywordUtil | `codegen/KeywordUtil.java` | Language keyword escaping (16 keyword sets: 14 in KeywordUtil, 2 delegated from NameResolvers) | 951 | 100% |

### Language Generators (Criticality A — bugs produce incorrect generated code)

| Generator | Location | Lines | Test Coverage |
|-----------|----------|-------|---------------|
| Java (6 classes) | `codegen/java/` | 1,458 | 87.6% (80 dedicated + shared tests) |
| Python (6 classes) | `codegen/python/` | 1,104 | 72.2% (parameterized tests) |
| JavaScript (6 classes) | `codegen/javascript/` | 1,072 | 78.8% (parameterized tests) |
| TypeScript (6 classes) | `codegen/typescript/` | 932 | 79.2% (parameterized tests) |
| C (6 classes) | `codegen/c/` | 1,832 | 66.4% (parameterized tests) |
| C++ (6 classes) | `codegen/cpp/` | 1,264 | 78.7% (parameterized tests) |
| Rust (6 classes) | `codegen/rust/` | 1,317 | 74.7% (parameterized tests) |
| Zig (6 classes) | `codegen/zig/` | 1,163 | 80.5% (parameterized tests) |
| Go (6 classes) | `codegen/go/` | 1,364 | 68.7% (parameterized tests) |
| C# (6 classes) | `codegen/csharp/` | ~1,200 | parameterized tests |
| Kotlin (6 classes) | `codegen/kotlin/` | ~1,100 | parameterized tests |
| Swift (6 classes) | `codegen/swift/` | ~1,100 | parameterized tests |
| Dart (6 classes) | `codegen/dart/` | ~1,100 | parameterized tests |
| PHP (6 classes) | `codegen/php/` | ~1,100 | parameterized tests |
| Ruby (6 classes) | `codegen/ruby/` | ~1,000 | parameterized tests |
| Objective-C (6 classes) | `codegen/objc/` | ~1,200 | parameterized tests |
| Perl (6 classes) | `codegen/perl/` | ~1,000 | parameterized tests |

### Runtime Libraries (Criticality B — used by generated code, not the plugin)

| Runtime | Location | Lines | Language Dependencies |
|---------|----------|-------|----------------------|
| Java | `runtime/java/` | 146 | None (zero-dependency JsonArrayWriter/JsonArrayReader since v0.2.0) |
| C | `runtime/c/` | 264 | cJSON (user-provided) |
| C++ | `runtime/cpp/` | 113 | nlohmann/json (user-provided) |
| Rust | `runtime/rust/` | 156 | serde_json 1.x, base64 0.22 |
| Python | — | — | None (self-contained, uses stdlib `json`) |
| JavaScript/TypeScript | — | — | None (self-contained, uses native `JSON`) |
| Zig | — | — | None (self-contained, uses `std.json`) |
| Go | — | — | None (self-contained, uses `encoding/json`) |
| C# | — | — | None (self-contained, uses `System.Text.Json`) |
| Kotlin | — | — | None (self-contained, uses `kotlinx.serialization`) |
| Swift | — | — | None (self-contained, uses `Foundation` JSONSerialization) |
| Dart | — | — | None (self-contained, uses `dart:convert`) |
| PHP | — | — | None (self-contained, uses `json_encode`/`json_decode` built-in) |
| Ruby | — | — | None (self-contained, uses stdlib `json`) |
| Objective-C | — | — | None (self-contained, uses `Foundation` NSJSONSerialization) |
| Perl | — | — | None (self-contained, uses `JSON` CPAN module) |

## 5. Dependencies

### Direct Runtime Dependencies (Plugin)
| Package | Version | Purpose | Critical Path | Known CVEs |
|---------|---------|---------|---------------|------------|
| com.google.protobuf:protobuf-java | 4.29.3 | Parse CodeGeneratorRequest/Response | Yes | None known (current release) |

### Direct Runtime Dependencies (Java Runtime Library — shipped separately)
| Package | Version | Purpose | Critical Path | Known CVEs |
|---------|---------|---------|---------------|------------|
| (removed in v0.2.0) | — | Jackson eliminated; replaced with zero-dependency JsonArrayWriter/JsonArrayReader | No | — |

### Transitive Dependencies
| Package | Pulled By | Version | Notes |
|---------|-----------|---------|-------|
| com.google.protobuf:protobuf-java (transitive parts) | protobuf-java | 4.29.3 | [INCOMPLETE_ANALYSIS] — full transitive tree not enumerated |

### Build Dependencies
| Package | Version | Purpose | Ships in JAR |
|---------|---------|---------|--------------|
| com.gradleup.shadow | 8.3.6 | Fat JAR creation | No |
| com.diffplug.spotless | 7.0.2 | Google Java Format enforcement | No |
| org.junit.jupiter | 5.11.4 | Unit testing | No |
| JaCoCo | (Gradle default) | Code coverage | No |

### System Dependencies
| Dependency | Version | Purpose | Required At |
|------------|---------|---------|-------------|
| Java JDK | 17+ | Plugin execution | Runtime |
| protoc | Any (tested with 33.4) | Protocol Buffer compiler | Runtime |
| Gradle | 8.12 (via wrapper) | Build system | Build only |

### External Services
None. The plugin has zero external service dependencies. It is a pure computation: proto descriptors in → source code out.

## 6. Data Flow

### Processing Pipeline
```
protoc (external process)
  │
  ├─ Sends: CodeGeneratorRequest (binary protobuf via stdin)
  │   Contains: FileDescriptorProto[], file_to_generate[], parameter string
  │
  ▼
Main.java
  │ parseFrom(System.in)
  ▼
PluginRunner.run(request)
  │ parseLanguage(parameter) → "java", "python", etc.
  │ buildTypeRegistry(request.getProtoFileList())
  ▼
ProtoFileProcessor.process(fileDescriptor) [called once per proto file]
  │ buildCommentMap(sourceCodeInfo)
  │ analyzeMessages(messageTypeList) → ProtoMessage[]
  │ analyzeEnums(enumTypeList) → ProtoEnum[]
  ▼
MessageAnalyzer.analyze(descriptor, prefix, syntax)
  │ validateFieldName(regex)
  │ validateFieldNumber(> 0)
  │ sortFieldsByNumber()
  │ detectMaps(), detectOneofs(), detectWellKnownTypes()
  │ rejectAny(), warnSparseNumbering(), warnExtensions()
  ▼
LanguageGenerator.generate(protoFile, registry) [language-specific]
  │ NameResolver.validateFieldNames() [collision detection]
  │ CodeEmitter → SerializerGenerator → DeserializerGenerator
  │ TypeMapper + NameResolver + KeywordUtil
  ▼
CodeGeneratorResponse.File[] (name + content pairs)
  │ validatePaths() [reject ".." traversal]
  ▼
Main.java
  │ response.writeTo(System.out)
  ▼
protoc (receives generated files, writes to disk)
```

### Inputs
- **stdin**: CodeGeneratorRequest (binary protobuf) — proto file descriptors, parameters, source code info
- **CLI args**: `--version` flag only

### Outputs
- **stdout**: CodeGeneratorResponse (binary protobuf) — generated source files (name + content)
- **stderr**: Diagnostic warnings (sparse field numbering, extensions, [ASSUMED_BEHAVIOR] not formally enumerated)

### Persistence
None. The plugin is completely stateless across invocations. Within a single invocation, `PythonGenerator.emittedInitFiles` and `RustGenerator.emittedModFiles`/`modulesByDir` track previously emitted files to avoid protoc duplicate-file errors.

## 7. Trust Boundaries

| Boundary | Location | Untrusted Input | Validation | Residual Risk |
|----------|----------|-----------------|------------|---------------|
| stdin (protoc protocol) | `Main.main()` | Binary protobuf from protoc | protobuf-java validates wire format | A hand-crafted binary could bypass protoc's semantic validation |
| Field names | `MessageAnalyzer.validateFieldName()` | Proto field identifiers | Regex `[a-zA-Z_][a-zA-Z0-9_]*` rejects invalid chars | None — regex is strict |
| Message/enum names | `KeywordUtil.escape*()` | Proto type identifiers | Keyword escaping applied per-language | [ASSUMED_BEHAVIOR] Not validated against regex like field names |
| Type references | `MessageAnalyzer.analyze()` | Fully-qualified proto type names | `Any` rejected; `simpleTypeName()` extracts last segment | A crafted type_name like `.evil.Foo; malicious_code()` could inject if not stripped to simple name [ASSUMED_BEHAVIOR — protoc validates type names upstream] |
| Output file paths | `PluginRunner.run()` | Package names → file paths | Rejects paths containing `..` | Does not check for absolute paths or null bytes |
| Language parameter | `PluginRunner.parseLanguage()` | `lang=` from protoc parameter | Validated against GENERATORS map (allowlist) | None |
| Proto2 default values | `JavaDeserializerGenerator.schemaDefaultExpression()` | Schema-specified string/numeric defaults | Escapes `\n`, `\r`, `\t`, `\0`, `\`, `"` ; special-cases `inf`/`nan` | [INCOMPLETE_ANALYSIS] Only Java generator validated; other languages may not escape defaults |

**Key trust assumption:** The plugin trusts that `protoc` provides valid, spec-compliant `CodeGeneratorRequest` messages. All validation is defense-in-depth against a hypothetical attacker who can inject a crafted CodeGeneratorRequest (which requires local code execution to run the plugin directly, at which point the attacker already has arbitrary code execution).

## 8. Concurrency Model

**The plugin is entirely single-threaded.**

- No threading primitives: no `Thread`, `ExecutorService`, `CompletableFuture`, `synchronized`, `volatile`, `AtomicReference`, `ConcurrentHashMap`, `parallelStream()`
- No async operations of any kind
- `Runnable` used in `CodeWriter.block()` as a closure/callback pattern (not threading)

**Model objects are immutable after construction:**
- `ProtoField`, `ProtoMessage`, `ProtoEnum`, `ProtoFile` use `List.copyOf()` for all collections
- `TypeRegistry` is populated completely before any reads (sequential write-then-read pattern)
- `WellKnownType.BY_NAME` uses `Map.copyOf()` (deeply immutable)
- `PluginRunner.GENERATORS` is a `HashMap` populated in a `static {}` block (safe per JLS 12.4.2 class initialization guarantee)

**Transient per-invocation mutable state:**
- `PythonGenerator.emittedInitFiles` (`HashSet<String>`) — tracks emitted `__init__.py` files
- `RustGenerator.emittedModFiles` (`HashSet<String>`) and `modulesByDir` (`LinkedHashMap`) — tracks emitted `mod.rs` files
- Both are instance fields on generator objects created fresh per `PluginRunner.run()` invocation, accessed only from the main thread

**Generated code is NOT thread-safe** (mutable fields, unsynchronized `BitSet` for presence tracking). This matches standard protobuf-generated code behavior and is documented in `docs/archive/BUG_CATALOG_v0.1.md`.

## 9. Error Handling Patterns

| Pattern | Implementation | Scope |
|---------|---------------|-------|
| Exception-to-response | `PluginRunner.run()` catches `Exception`, returns `CodeGeneratorResponse.error` | All plugin errors |
| Fatal fallback | `Main.java` outer try-catch writes error response; inner catch prints to stderr, exits 1 | Protocol-level failures |
| Validation-as-exception | `IllegalArgumentException` for field names, field numbers, Any, collisions | Analysis phase |
| Warnings-to-stderr | `System.err.println` for extensions, sparse numbering | Non-fatal diagnostics |
| Default-on-missing | Short arrays during deserialization use field defaults | Generated code |

- **Retry logic:** None (single invocation)
- **Circuit breakers:** N/A (no external services)
- **Graceful degradation:** Extensions emit warning but proceed; sparse numbering warns but proceeds
- **Error granularity:** Error messages include the proto file name, message name, and field name where applicable

## 10. Configuration

| Config Item | Source | Purpose | Validation |
|-------------|--------|---------|------------|
| `lang=<language>` | protoc parameter string | Selects target language (default: java) | Allowlist: java, python, javascript, js, typescript, ts, c, cpp, c++, rust, zig, go, csharp, c#, kotlin, kt, swift, dart, php, ruby, rb, objc, objective-c, perl (24 entries: 17 languages + 7 aliases) |
| `--version` | CLI argument | Prints version and exits | Exact string match |

No environment variables, no config files, no system properties. The plugin's behavior is fully determined by the `CodeGeneratorRequest` and the `lang=` parameter.

## 11. Uncertainties

| Flag | Location | Description | Information Needed |
|------|----------|-------------|-------------------|
| [UNKNOWN_CRITICALITY] | All 17 generators | Cannot determine which target languages are mission-critical vs. nice-to-have | Which languages will be used in production? |
| [ASSUMED_BEHAVIOR] | Well-known type getters | Assumes well-known types have standard getters (`getSeconds()`, `getNanos()`, `getValue()`) | Confirm protobuf-java maintains these across versions |
| [EXTERNAL_DEPENDENCY] | `protoc` compiler | Plugin correctness depends on protoc providing valid CodeGeneratorRequest | protoc version compatibility range (tested: 33.4, claimed: "any") |
| ~~[EXTERNAL_DEPENDENCY]~~ | ~~Jackson 2.18.2~~ | **Resolved**: Jackson removed in v0.2.0; replaced with built-in JsonArrayWriter/JsonArrayReader | — |
| [ASSUMED_BEHAVIOR] | Zig generator | Generated Zig uses `std.json`/`std.base64` APIs that change across Zig versions | Target Zig version(s) |
| [INCOMPLETE_ANALYSIS] | C runtime (`codec.c`) | Memory safety of base64 encode/decode not verified with dynamic analysis | Valgrind/ASan testing |
| [ASSUMED_BEHAVIOR] | Generated C code | `malloc`/`free` patterns assumed correct from static review | Dynamic analysis would confirm |
| [INCOMPLETE_ANALYSIS] | Proto2 defaults (non-Java) | Only Java generator validates/escapes proto2 default values; Python/JS/C/etc. may not | Review each generator's default value handling |
| [INCOMPLETE_ANALYSIS] | Message name validation | Field names validated via regex; message/enum names are NOT validated | Confirm protoc rejects invalid message names |
| [ASSUMED_BEHAVIOR] | Transitive deps | `protobuf-java:4.29.3` transitive dependency tree not fully enumerated | Run `./gradlew dependencies` and review |

## 12. Recommended Assurance Focus

| Area | Applicability | Rationale |
|------|---------------|-----------|
| Data Integrity | **FULL** | Core value: generated serialization must produce correct, interoperable output. A subtle bug silently produces code that compiles but corrupts data at runtime. |
| Code Generation Correctness | **FULL** | Primary output is source code for 17 languages. Must compile, be syntactically valid, and handle all proto constructs. |
| Security Testing | **ADAPTED** | Narrow trust boundary (stdin from protoc). Defense-in-depth present. Focus on: crafted CodeGeneratorRequest injection, generated code injection via field/type names. |
| Performance Testing | **ADAPTED** | Single invocation per build. Benchmarked: 1.1μs/op small, 0.1ms/op large. Focus on: very large protos, extreme sparse field numbers. |
| Fault Injection | **ADAPTED** | Focus on: malformed CodeGeneratorRequest, extreme field numbers, deeply nested messages, proto files with thousands of fields. |
| Formal Methods | **N/A** | Core algorithm (field_number - 1 = position) is trivial. Code generation is string template expansion. Formal verification would not provide proportional benefit. |
| Concurrency Testing | **N/A** | Entirely single-threaded. No shared mutable state. |

## 13. Failure Domains

| Domain | Components Affected | Trigger | Impact | Recovery |
|--------|-------------------|---------|--------|----------|
| Protobuf parsing | Main.java | Malformed stdin | Plugin returns error response | protoc displays error to user |
| Analysis phase | MessageAnalyzer, TypeRegistry | Invalid field names/numbers, Any usage | `IllegalArgumentException` → error response | User fixes .proto file |
| Code generation | Language generators | Unsupported proto construct, internal bug | Either error response or silently incorrect output | Error: user sees protoc error. Silent: **undetectable until runtime** |
| Path validation | PluginRunner | Malicious package name with `..` | Error response, no files written | User fixes package name |
| JVM | Main.java | OutOfMemoryError, StackOverflowError | JVM crash, protoc reports plugin failure | User increases JVM heap or reduces proto complexity |
| Shell wrapper | protoc-gen-jsonarray | Missing java, missing JAR | Error message to stderr, exit 1 | User installs java / runs gradle build |

**Critical failure mode:** A bug in a code generator that produces syntactically valid but semantically incorrect code (e.g., wrong field position, wrong type mapping). This code compiles without error and may pass basic tests, but corrupts data at runtime. This is the highest-risk failure because it is **silent** — no error is raised at build time or generation time.

## 14. Generated Code as Secondary System

The generated code runs in the user's application with fundamentally different characteristics:

| Property | Plugin | Generated Code |
|----------|--------|---------------|
| Execution context | Build-time CLI tool | User's production application |
| Trust model | Trusted (runs locally) | Must handle untrusted JSON input |
| Dependencies | protobuf-java only | Zero-dependency (Java), cJSON (C), etc. |
| Thread safety | N/A (single-threaded) | NOT thread-safe (documented) |
| Memory model | JVM managed | C: manual malloc/free; Zig: allocator-based |
| Error handling | Exception → error response | Java: RuntimeException; Go: error return; Rust: Result; C: NULL returns |
| State | Stateless | Mutable message instances |

### Generated Code Trust Boundaries

| Boundary | Input | Validation in Generated Code |
|----------|-------|------------------------------|
| `parseFrom(byte[])` | Untrusted serialized data | JSON parsing via built-in reader (Java) or library (json/serde/cJSON); type mismatches caught by parser |
| `fromJsonArray(List<Object>)` | Parsed JSON array | Bounds checking (`size > pos`); null checking (`!= null`) |
| Base64 decoding | String from JSON | Library-provided decoding; malformed base64 → empty bytes or exception |
| int64 string parsing | String or number from JSON | `Long.parseLong` / `strconv.ParseInt` with fallback to numeric |

### Generated Code Failure Modes

| Failure | Trigger | Impact | Detection |
|---------|---------|--------|-----------|
| ClassCastException (Java) | Wrong JSON type at position | RuntimeException | Immediate at deserialization |
| Panic (Go) | [FIXED] Type assertion failure | Process crash | [Was bare assertion, now uses comma-ok pattern] |
| Segfault (C) | NULL dereference on malformed input | Process crash | Runtime crash |
| Silent data loss | int64 parsed as float64 by intermediate JSON library | Precision loss above 2^53 | [MITIGATED] int64 encoded as string |
| Memory leak (C) | [FIXED] Base64 string not freed in oneof path | Growing memory usage | Valgrind/ASan |

## 15. Encoding Specification Summary

The core encoding format that all generated code must implement:

| Rule | Specification |
|------|--------------|
| Array position | `field_number - 1` (0-indexed) |
| Field number gaps | JSON `null` at gap positions |
| Scalars (int32, bool, string, etc.) | Native JSON type |
| int64/uint64 | JSON **string** (preserves precision beyond 2^53) |
| float/double | JSON number; NaN/Infinity → `null` |
| bytes | Base64-encoded JSON string (standard alphabet, with padding) |
| enum | JSON number (enum value integer) |
| nested message | Nested JSON array |
| repeated | JSON array at field position |
| map (string keys) | JSON object |
| map (non-string keys) | JSON array of `[key, value]` pairs |
| unset message | JSON `null` |
| unset scalar (proto3 implicit) | Default value (0, "", false) |
| unset scalar (proto3 optional) | JSON `null` |
| unset scalar (proto2) | JSON `null` (schema default applied on deserialization) |

**Cross-language invariant:** The same proto message with the same field values MUST produce semantically identical JSON when serialized by any of the 17 language generators. Verified by cross-language round-trip tests (Java ↔ Python).

## 16. Test Coverage Summary

| Test Class | Tests | Focus |
|------------|-------|-------|
| JavaCodeGenTest | 80 | Java code generation for every proto construct |
| MultiLanguageCodeGenTest | 240 (15×16) | 15 @ParameterizedTest methods × 16 non-Java languages |
| MessageAnalyzerTest | 37 | Proto analysis: fields, oneofs, maps, WKTs, proto2 |
| IndexingAuditTest | 12 | Off-by-one, sparse gaps, presence bits, oneof case constants |
| JavaTypeMapperTest | 61 | Type mapping for all 15 proto scalar types |
| PluginRunnerTest | 22 | Parameter parsing, language dispatch, proto2 support, features |
| TypeRegistryTest | 14 | Type registration, nested types, map entries |
| CodeWriterTest | 14 | Indentation, blocks, formatting |
| JavaNameResolverTest | 5 | Naming conventions |
| WellKnownTypeTest | 3 | WKT lookup and classification |
| SafetySecurityTest | 180 | Safety (SR-001–004), security (SEC-001–004), fault injection for jsonarray |
| GoldenFileTest | 17 | @ParameterizedTest — exact output comparison against golden files |
| PerformanceBenchmarkTest | 8 | Plugin throughput benchmarks |
| MemoryBenchmarkTest | 4 | Memory allocation benchmarks |
| PbtkJavaCodeGenTest | 29 | pbtk URL format Java code generation |
| PbtkMultiLanguageCodeGenTest | 144 | pbtk URL format across 16 non-Java languages |
| PbtkSafetySecurityTest | 86 | Safety/security tests for pbtk format |
| SchemaEvolutionTest | 119 | Schema evolution across all 17 languages, both formats (parameterized) |
| JavaSchemaEvolutionTest | 15 | Java-specific schema evolution patterns (array sizing, bounds, gaps, oneofs) |

**Total: 1,090 tests (19 test classes). Overall coverage: 73.9% instructions, 76.6% lines.**

Integration tests (not in JUnit):
- Cross-language round-trip (Java ↔ Python): 5 assertions
- Schema evolution (forward/backward compat): 4 assertions
- 17-language generation smoke test (CI workflow)

## 17. Initial Observations

### Strengths
1. **Clean architecture:** Language-neutral model layer cleanly separates proto analysis from code generation. Adding a new language requires implementing 6 classes with no changes to the core.
2. **Thorough testing:** 1,090 tests (73.9% instruction coverage, 76.6% line coverage) + integration tests + 80 end-to-end code generation tests verifying actual generated source code + 134 schema evolution tests across all 17 languages.
3. **Immutable model:** All model objects use `List.copyOf()` — no accidental mutation.
4. **Defense-in-depth:** Field name validation, path traversal, keyword escaping, collision detection — even though protoc validates most inputs upstream.
5. **Zero runtime dependencies:** The plugin itself only needs `protobuf-java`. No network, no disk, no state.
6. **Encoding format preserves schema evolution:** Field-number-based positioning allows adding/removing fields without breaking existing data.

### Concerns
1. **Silent incorrect output is the highest-risk failure.** A bug in any generator produces code that compiles but corrupts data. No build-time or generation-time signal.
2. **17 language generators = 17x testing surface.** Parameterized tests cover common patterns but don't deeply test language-specific edge cases (e.g., C memory management, Zig API compatibility).
3. **C runtime memory safety not dynamically validated.** Valgrind/ASan testing would increase confidence.
4. **Zig standard library instability.** Generated Zig code may break with future Zig releases.
5. **int64 string encoding may surprise users.** Correct per protobuf JSON spec but breaks expectations of tools expecting numeric JSON values.
6. **Proto2 default value escaping only validated for Java.** Other generators may not properly escape special characters in schema-specified defaults.
7. ~~No golden-file/snapshot tests.~~ **Resolved.** GoldenFileTest.java provides exact output comparison for all 17 languages against checked-in golden files.
