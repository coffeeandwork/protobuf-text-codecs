# Requirements Specification

================================================================================
Generated: 2026-03-24T21:00:00-04:00
Model: Claude Opus 4.6 (1M context)
Review Status: PENDING
Reviewed By: _______________
================================================================================

```
LIMITATIONS:
- This is a DRAFT artifact requiring human validation
- Requirements MUST be reviewed by domain experts
- Derived requirements require human confirmation
- This is an unqualified tool output per DO-330/IEC 62304
```

---

## 1. Functional Requirements

### Encoding Format

#### FR-001: Positional Array Encoding
- **ID:** FR-001
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** SYSTEM_ANALYSIS.md Section 15; `MessageAnalyzer.java:45`
- **Statement:** The system SHALL serialize each proto message as a JSON array where each field occupies position `field_number - 1` (0-indexed).
- **Rationale:** This is the core encoding invariant that all 9 language generators must implement identically.
- **Verification:** Test
- **Acceptance Criteria:** For a message with fields at numbers 1, 2, 3, the serialized output is a 3-element JSON array with field 1 at index 0, field 2 at index 1, field 3 at index 2.

#### FR-002: Field Number Gap Handling
- **ID:** FR-002
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** `JavaSerializerGenerator.java:34`
- **Statement:** The system SHALL emit JSON `null` at array positions corresponding to field numbers that have no field defined (gaps in field numbering).
- **Verification:** Test
- **Acceptance Criteria:** A message with fields 1 and 3 (no field 2) produces a 3-element array `[val1, null, val3]`.

#### FR-003: Scalar Type Encoding
- **ID:** FR-003
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** SYSTEM_ANALYSIS.md Section 15; `JavaTypeMapper.java`
- **Statement:** The system SHALL encode all 15 proto scalar types using their natural JSON representations: int32/uint32/sint32/fixed32/sfixed32 as JSON numbers; int64/uint64/sint64/fixed64/sfixed64 as JSON strings (to preserve precision beyond 2^53); float/double as JSON numbers (NaN/Infinity as null); bool as JSON boolean; string as JSON string; bytes as Base64-encoded JSON string.
- **Verification:** Test
- **Acceptance Criteria:** Round-trip: `deserialize(serialize(message)) == message` for every scalar type with boundary values (0, max, min, -1).

#### FR-004: Nested Message Encoding
- **ID:** FR-004
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** `JavaSerializerGenerator.java:148-155`
- **Statement:** The system SHALL encode nested message fields as nested JSON arrays. Unset message fields SHALL be encoded as JSON `null`.
- **Verification:** Test
- **Acceptance Criteria:** `User{address: Address{city: "SF"}}` serializes with a nested array at the address position.

#### FR-005: Repeated Field Encoding
- **ID:** FR-005
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** `JavaSerializerGenerator.java:157-184`
- **Statement:** The system SHALL encode repeated fields as JSON arrays within their positional slot. Empty repeated fields SHALL serialize as empty JSON arrays `[]`, not `null`.
- **Verification:** Test
- **Acceptance Criteria:** `repeated string tags = ["a","b"]` produces `["a","b"]` at the field's position; empty list produces `[]`.

#### FR-006: Map Field Encoding
- **ID:** FR-006
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** `JavaSerializerGenerator.java:186-217`
- **Statement:** The system SHALL encode map fields with string keys as JSON objects and map fields with non-string keys as JSON arrays of `[key, value]` pairs.
- **Verification:** Test
- **Acceptance Criteria:** `map<string,int32>{"a":1}` produces `{"a":1}`; `map<int32,string>{42:"x"}` produces `[[42,"x"]]`.

#### FR-007: Enum Encoding
- **ID:** FR-007
- **Type:** Functional
- **Priority:** High
- **Derived From:** `JavaSerializerGenerator.java:134-146`
- **Statement:** The system SHALL encode enum fields as their integer value (not the enum name string).
- **Verification:** Test
- **Acceptance Criteria:** An enum field set to `ACTIVE` (value 1) serializes as `1`.

#### FR-008: Oneof Encoding
- **ID:** FR-008
- **Type:** Functional
- **Priority:** High
- **Derived From:** `JavaSerializerGenerator.java:66-77`
- **Statement:** The system SHALL serialize only the active oneof member's value at its position. All inactive oneof member positions SHALL be `null`.
- **Verification:** Test
- **Acceptance Criteria:** A oneof with `email`(24) and `phone`(25): setting email produces `[..., "test@x.com", null, ...]`; setting phone produces `[..., null, "555-1234", ...]`.

#### FR-009: Proto3 Optional Presence
- **ID:** FR-009
- **Type:** Functional
- **Priority:** High
- **Derived From:** `JavaSerializerGenerator.java:114-123`
- **Statement:** The system SHALL serialize proto3 `optional` scalar fields as JSON `null` when not explicitly set, and as the field value when set (even if set to the default value).
- **Verification:** Test
- **Acceptance Criteria:** `optional int32 x` not set → `null`; set to 0 → `0`.

#### FR-010: Proto2 Support
- **ID:** FR-010
- **Type:** Functional
- **Priority:** High
- **Derived From:** `MessageAnalyzer.java:61`, `PluginRunner.java:84-97`
- **Statement:** The system SHALL accept proto2 syntax files. Required fields SHALL always be serialized. Optional fields SHALL use presence tracking. Schema-specified default values SHALL be applied during deserialization when a field is absent.
- **Verification:** Test
- **Acceptance Criteria:** Proto2 `optional string name = 1 [default = "unknown"]`: absent field deserializes to `"unknown"`, not `""`.

### Language Support

#### FR-011: Multi-Language Code Generation
- **ID:** FR-011
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** `PluginRunner.java:28-41`
- **Statement:** The system SHALL generate serialization/deserialization code for: Java, Python, JavaScript, TypeScript, C, C++, Rust, Zig, and Go, selected via the `lang=` parameter.
- **Verification:** Test
- **Acceptance Criteria:** `protoc --jsonarray_out=lang=X` produces compilable/parseable source files for each of the 9 languages.

#### FR-012: Cross-Language Interoperability
- **ID:** FR-012
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** SYSTEM_ANALYSIS.md Section 15 (cross-language invariant)
- **Statement:** The same proto message with the same field values SHALL produce semantically identical JSON when serialized by any of the 9 language generators. JSON produced by one language's generator SHALL be deserializable by any other language's generated code.
- **Verification:** Test
- **Acceptance Criteria:** Serialize in Java, deserialize in Python — all field values match. And vice versa.

#### FR-013: Cross-File Type References
- **ID:** FR-013
- **Type:** Functional
- **Priority:** High
- **Derived From:** `PythonCodeEmitter.java:99-150`, all `*CodeEmitter.java` cross-file import methods
- **Statement:** When a message in file A references a type defined in file B, the generated code SHALL include the appropriate import/include/require/use statement for the target language.
- **Verification:** Test
- **Acceptance Criteria:** `User` referencing `Address` from a separate file produces `from .address import Address` (Python), `#include "address.h"` (C), `use super::address::Address` (Rust), etc.

### Plugin Protocol

#### FR-014: protoc Plugin Protocol Compliance
- **ID:** FR-014
- **Type:** Functional
- **Priority:** Critical
- **Derived From:** `Main.java:12-31`
- **Statement:** The system SHALL read a `CodeGeneratorRequest` from stdin, process it, and write a `CodeGeneratorResponse` to stdout, per the protoc plugin protocol.
- **Verification:** Test
- **Acceptance Criteria:** `protoc --plugin=protoc-gen-jsonarray=./plugin --jsonarray_out=./out file.proto` succeeds and produces output files.

#### FR-015: Error Reporting
- **ID:** FR-015
- **Type:** Functional
- **Priority:** High
- **Derived From:** `PluginRunner.java:44-52`
- **Statement:** The system SHALL report errors via `CodeGeneratorResponse.error` (not via exit code), per the protoc plugin protocol. Error messages SHALL be prefixed with `protoc-gen-jsonarray:`.
- **Verification:** Test
- **Acceptance Criteria:** An invalid request produces a response with `.hasError() == true` and `.getError()` starting with `protoc-gen-jsonarray:`.

#### FR-016: Version Reporting
- **ID:** FR-016
- **Type:** Functional
- **Priority:** Low
- **Derived From:** `Main.java:14-18`
- **Statement:** When invoked with `--version`, the system SHALL print `protoc-gen-jsonarray <version>` to stdout and exit with code 0.
- **Verification:** Test
- **Acceptance Criteria:** `./protoc-gen-jsonarray --version` outputs `protoc-gen-jsonarray 0.1.0`.

### Well-Known Types

#### FR-017: Well-Known Type Handling
- **ID:** FR-017
- **Type:** Functional
- **Priority:** Medium
- **Derived From:** `WellKnownType.java`, `FieldCodecs.java`
- **Statement:** The system SHALL handle Google well-known types with special encoding: Timestamp as RFC 3339 string, Duration as `"Xs.Yns"` string, wrapper types as unwrapped scalars or null, Struct/Value/ListValue as JSON pass-through, FieldMask as comma-separated string, Empty as empty array.
- **Verification:** Test
- **Acceptance Criteria:** A Timestamp field serializes as `"2024-01-15T10:30:00Z"`, not as a nested array.

#### FR-018: Any Type Rejection
- **ID:** FR-018
- **Type:** Functional
- **Priority:** High
- **Derived From:** `MessageAnalyzer.java:124-129`, HAZ-004
- **Statement:** The system SHALL reject proto files that use `google.protobuf.Any` with a clear error message explaining that positional encoding requires compile-time schema knowledge.
- **Verification:** Test
- **Acceptance Criteria:** A message with an `Any` field produces an error response containing "google.protobuf.Any is not supported".

---

## 2. Safety Requirements

### SR-001: Field Position Correctness
- **ID:** SR-001
- **Type:** Safety
- **Priority:** Critical
- **Derived From:** HAZ-001
- **Statement:** The system SHALL compute array positions using the formula `position = field_number - 1` consistently in all serializers, deserializers, and model classes across all 9 languages.
- **Verification:** Test (IndexingAuditTest — 12 tests)
- **Hazard Mitigated:** HAZ-001 (silent data corruption via wrong field position)

### SR-002: Type Encoding Consistency
- **ID:** SR-002
- **Type:** Safety
- **Priority:** Critical
- **Derived From:** HAZ-002
- **Statement:** All 9 language generators SHALL encode each proto type using the same JSON representation as specified in FR-003 through FR-009. No generator SHALL deviate from the encoding specification.
- **Verification:** Test (MultiLanguageCodeGenTest — 120 tests)
- **Hazard Mitigated:** HAZ-002 (silent data corruption via wrong type encoding)

### SR-003: int64 Precision Preservation
- **ID:** SR-003
- **Type:** Safety
- **Priority:** Critical
- **Derived From:** HAZ-007
- **Statement:** The system SHALL encode int64, uint64, sint64, sfixed64, fixed64 fields as JSON strings (not JSON numbers) to prevent precision loss beyond 2^53. Deserializers SHALL accept both string and number formats for backward compatibility.
- **Verification:** Test
- **Hazard Mitigated:** HAZ-007 (int64 precision loss)

### SR-004: NaN/Infinity Handling
- **ID:** SR-004
- **Type:** Safety
- **Priority:** High
- **Derived From:** HAZ-012
- **Statement:** The system SHALL serialize float/double values of NaN, +Infinity, and -Infinity as JSON `null` (since these are not valid JSON values).
- **Verification:** Test (JavaCodeGenTest verifies isNaN/isInfinite checks)
- **Hazard Mitigated:** HAZ-012 (invalid JSON output)

---

## 3. Security Requirements

### SEC-001: Field Name Validation
- **ID:** SEC-001
- **Type:** Security
- **Priority:** High
- **Derived From:** HAZ-004; `MessageAnalyzer.java:91`
- **Statement:** The system SHALL validate all proto field names against the regex `[a-zA-Z_][a-zA-Z0-9_]*` and reject fields with invalid names via `IllegalArgumentException`.
- **Verification:** Test (MessageAnalyzerTest)

### SEC-002: Output Path Traversal Prevention
- **ID:** SEC-002
- **Type:** Security
- **Priority:** High
- **Derived From:** HAZ-005; `PluginRunner.java:114`
- **Statement:** The system SHALL reject any generated output file path containing `..` to prevent directory traversal attacks via malicious package names.
- **Verification:** Test

### SEC-003: Language Keyword Escaping
- **ID:** SEC-003
- **Type:** Security
- **Priority:** Medium
- **Derived From:** HAZ-004; `KeywordUtil.java`
- **Statement:** The system SHALL escape proto identifiers that collide with target language keywords using language-appropriate escaping: `_` suffix (Java, Python, JS, Go), `_pb` suffix (C, C++), `r#` prefix (Rust), `@"..."` quoting (Zig).
- **Verification:** Test (JavaCodeGenTest, MultiLanguageCodeGenTest keyword tests)

### SEC-004: Field Name Collision Detection
- **ID:** SEC-004
- **Type:** Security
- **Priority:** Medium
- **Derived From:** HAZ-001 (prevents ambiguous field mapping); `NameResolver.java:validateFieldNames()`
- **Statement:** The system SHALL detect when two proto field names map to the same target-language identifier (e.g., `foo_bar` and `fooBar` both becoming `fooBar` in Java) and raise an error before generating code.
- **Verification:** Test (JavaCodeGenTest collision test)

---

## 4. Performance Requirements

### PERF-001: Plugin Execution Time
- **ID:** PERF-001
- **Type:** Performance
- **Priority:** Medium
- **Derived From:** Benchmark results (SYSTEM_ANALYSIS.md Section 12)
- **Statement:** The plugin SHALL complete code generation for a typical proto file (< 100 messages, < 30 fields per message) within 5 seconds on a modern workstation.
- **Verification:** Test (benchmark)
- **Measurement:** Wall-clock time from protoc invocation to completion. Measured: ~1 second for kitchen_sink.proto (29 fields).

### PERF-002: Generated Code Serialization Performance
- **ID:** PERF-002
- **Type:** Performance
- **Priority:** Low
- **Derived From:** Benchmark results
- **Statement:** Generated Java serialization code SHALL serialize a simple 4-field message in under 10 microseconds per operation (excluding ObjectMapper creation, which is cached).
- **Verification:** Benchmark
- **Measurement:** Measured: 1.1μs/op for User message with ObjectMapper cached as static field.

---

## 5. Interface Requirements

### IF-001: protoc Plugin Protocol
- **ID:** IF-001
- **Type:** Interface
- **Priority:** Critical
- **Statement:** The system SHALL communicate with `protoc` via the standard plugin protocol: binary `CodeGeneratorRequest` on stdin, binary `CodeGeneratorResponse` on stdout.
- **Protocol:** Google Protocol Buffer Compiler Plugin Protocol (`plugin.proto`)
- **Format:** Binary protobuf (not text format, not JSON)

### IF-002: Language Parameter
- **ID:** IF-002
- **Type:** Interface
- **Priority:** High
- **Statement:** The system SHALL accept a `lang=<language>` parameter via the `CodeGeneratorRequest.parameter` field. Valid values: `java`, `python`, `javascript` (alias: `js`), `typescript` (alias: `ts`), `c`, `cpp` (alias: `c++`), `rust`, `zig`, `go`. Default: `java`.
- **Format:** Comma-separated key=value pairs in parameter string

### IF-003: Generated Code API (Java)
- **ID:** IF-003
- **Type:** Interface
- **Priority:** High
- **Statement:** Generated Java classes SHALL provide: `serialize(ObjectMapper)` returning `ArrayNode`; `toJsonString()` returning `String`; `toJsonBytes()` returning `byte[]`; `static deserialize(ArrayNode, ObjectMapper)`; `static fromJsonString(String)`; `static fromJsonBytes(byte[])`.
- **Format:** Jackson `ArrayNode` for structured access; `String`/`byte[]` for convenience

---

## 6. Derived Requirements

> [DERIVED_REQUIREMENT]: These requirements were inferred from implementation
> behavior and require human confirmation that they represent intended behavior.

### DR-001: Default Language is Java
- **ID:** DR-001
- **Type:** Derived
- **Inferred From:** `PluginRunner.java:104,112`
- **Observed Behavior:** When no `lang=` parameter is specified, the plugin defaults to Java.
- **Proposed Requirement:** When no `lang=` parameter is provided, the system SHALL default to generating Java code.
- **Confidence:** High
- **Needs Confirmation:** Yes — is this the intended default?

### DR-002: Proto2 Extensions Emit Warning
- **ID:** DR-002
- **Type:** Derived
- **Inferred From:** `PluginRunner.java:89-109`
- **Observed Behavior:** Proto2 extension ranges and extension fields emit a warning to stderr but do not cause generation failure. Only base message fields are included.
- **Proposed Requirement:** The system SHALL emit a diagnostic warning to stderr when processing files with extensions, and SHALL generate code only for base message fields (not extension fields).
- **Confidence:** High
- **Needs Confirmation:** Yes — should extensions cause an error instead?

### DR-003: Sparse Field Numbering Warning
- **ID:** DR-003
- **Type:** Derived
- **Inferred From:** `MessageAnalyzer.java:70-79`
- **Observed Behavior:** When `maxFieldNumber > 2 * fieldCount`, the plugin emits a warning to stderr about sparse numbering producing many null gaps.
- **Proposed Requirement:** The system SHALL warn (not error) when a message has sparse field numbering (max number exceeds 2x field count).
- **Confidence:** Medium
- **Needs Confirmation:** Yes — should there be a hard limit?

### DR-004: ObjectMapper Caching in Generated Code
- **ID:** DR-004
- **Type:** Derived
- **Inferred From:** `JavaCodeEmitter.java` (MAPPER_ static field)
- **Observed Behavior:** Generated Java classes use a `private static final ObjectMapper MAPPER_` to avoid per-call construction overhead.
- **Proposed Requirement:** Generated Java code SHALL cache the Jackson ObjectMapper as a static final field, not create a new instance per serialization/deserialization call.
- **Confidence:** High
- **Needs Confirmation:** Yes — is shared ObjectMapper acceptable? (Thread-safe but not configurable per-call.)

### DR-005: Generated equals/hashCode
- **ID:** DR-005
- **Type:** Derived
- **Inferred From:** `JavaCodeEmitter.java` (emitEquals, emitHashCode methods)
- **Observed Behavior:** Generated Java classes include `equals()` and `hashCode()` methods using `Objects.equals()` for objects, `==` for primitives, `Arrays.equals()` for byte arrays.
- **Proposed Requirement:** Generated Java classes SHALL implement `equals()` and `hashCode()` based on all message fields.
- **Confidence:** High
- **Needs Confirmation:** Yes — should this be opt-in via a parameter?

### DR-006: Proto Comment Propagation
- **ID:** DR-006
- **Type:** Derived
- **Inferred From:** `ProtoFileProcessor.java` (buildCommentMap), `JavaCodeEmitter.java` (emitDocComment)
- **Observed Behavior:** Leading comments from proto source files are extracted via `SourceCodeInfo` and emitted as Javadoc comments on generated Java classes and getter methods.
- **Proposed Requirement:** The system SHALL propagate leading comments from proto source files to generated code as documentation comments (Javadoc for Java).
- **Confidence:** Medium
- **Needs Confirmation:** Yes — should this apply to all 9 languages or only Java?

---

## 7. Requirements Traceability

| Req ID | Derives From | Test Case(s) | Status |
|--------|--------------|-------------|--------|
| FR-001 | Encoding spec | IndexingAuditTest, JavaCodeGenTest.testScalarInt32 | Draft |
| FR-002 | Encoding spec | IndexingAuditTest.testSparseFieldGaps | Draft |
| FR-003 | Encoding spec | JavaTypeMapperTest (61 tests), JavaCodeGenTest (15 scalar tests) | Draft |
| FR-004 | Encoding spec | JavaCodeGenTest.testNestedMessage* | Draft |
| FR-005 | Encoding spec | JavaCodeGenTest.testRepeatedField* | Draft |
| FR-006 | Encoding spec | JavaCodeGenTest.testMapField* | Draft |
| FR-007 | Encoding spec | JavaCodeGenTest.testEnumSerialization | Draft |
| FR-008 | Encoding spec | JavaCodeGenTest.testOneofCaseTracking | Draft |
| FR-009 | Encoding spec | JavaCodeGenTest.testProto3OptionalPresence | Draft |
| FR-010 | Proto2 support | PluginRunnerTest.testProto2Support, JavaCodeGenTest.testProto2* | Draft |
| FR-011 | Architecture | MultiLanguageCodeGenTest (120 tests) | Draft |
| FR-012 | Cross-lang spec | integration-tests/cross-language-test.sh | Draft |
| FR-013 | Architecture | MultiLanguageCodeGenTest.testCrossFileReference | Draft |
| FR-014 | Plugin protocol | PluginRunnerTest (22 tests) | Draft |
| FR-015 | Plugin protocol | PluginRunnerTest.testUnsupportedLanguage | Draft |
| FR-016 | CLI | Manual: `./protoc-gen-jsonarray --version` | Draft |
| FR-017 | WKT spec | WellKnownTypeTest, JavaCodeGenTest.testWellKnownType* | Draft |
| FR-018 | Any rejection | JavaCodeGenTest.testAnyRejection, MessageAnalyzerTest | Draft |
| SR-001 | HAZ-001 | IndexingAuditTest (12 tests) | Draft |
| SR-002 | HAZ-002 | MultiLanguageCodeGenTest (120 tests) | Draft |
| SR-003 | HAZ-007 | JavaCodeGenTest.testInt64* (4 tests) | Draft |
| SR-004 | HAZ-012 | JavaCodeGenTest.testNaN* (2 tests) | Draft |
| SEC-001 | HAZ-004 | MessageAnalyzerTest field name tests | Draft |
| SEC-002 | HAZ-005 | PluginRunnerTest path tests | Draft |
| SEC-003 | HAZ-004 | JavaCodeGenTest.testKeywordEscaping, MultiLanguageCodeGenTest.testKeyword* | Draft |
| SEC-004 | HAZ-001 | JavaCodeGenTest.testNameCollisionDetection | Draft |

## 8. Coverage Gaps

| Area | Gap Description | Resolution Needed |
|------|-----------------|-------------------|
| Proto2 default escaping | Only Java generator escapes `\n`, `\r`, `\t` in string defaults; 8 other generators untested | Audit + fix + test all generators |
| ~~Golden-file testing~~ | ~~No snapshot tests~~ **Resolved**: GoldenFileTest.java with 9 golden files | — |
| Generated C compilation | Generated C code not compiled in CI | Add C compilation step to CI |
| Generated code runtime tests | Only Java and Python generated code is executed in tests | Compile and run generated code for all 9 languages |
| Cross-language round-trip | Only Java↔Python tested | Extend to all language pairs (or at least all→canonical→all) |
| Streaming serialization | No support for streaming to OutputStream | Future requirement (not blocking) |
| Protobuf URL format | Planned second encoding format not yet implemented | Future requirement |

## 9. Approval

Requirements require human review:
- [ ] All requirements are clear and unambiguous
- [ ] All safety requirements trace to hazards
- [ ] Derived requirements confirmed or rejected
- [ ] No critical functionality is unspecified
- [ ] Coverage gaps are acceptable for release

Reviewer: _________________________ Date: ____________
