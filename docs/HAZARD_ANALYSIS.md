# Hazard Analysis

================================================================================
Generated: 2026-03-24T21:00:00-04:00
Model: Claude Opus 4.6 (1M context)
Review Status: PENDING
Reviewed By: _______________
================================================================================

```
LIMITATIONS:
- This is a DRAFT artifact requiring human validation
- Risk classifications MUST be validated by domain experts
- Safety requirements require qualified human review
- This is an unqualified tool output per DO-330/IEC 62304
- Hazard analysis outputs are starting points, not completions
```

## 1. Domain Classification

- **Primary Domain:** General Software (developer tooling / serialization library)
- **Applicable Standard:** Internal risk-based (no regulatory requirement identified)
- **Overall System Criticality:** **High** — the plugin generates serialization code that runs in users' production applications. Silent bugs cause data corruption at runtime.

**Rationale:** This is not safety-critical, medical, or aerospace software. However, the system's output (generated serialization code) runs in arbitrary user applications where data integrity matters. A subtle code generation bug can cause silent data corruption in production, making the "data integrity" risk high despite the tool itself being a build-time utility.

## 2. Component Criticality

### Plugin Components

| Component | Criticality | Failure Mode | Effect | Detection | Rationale |
|-----------|-------------|--------------|--------|-----------|-----------|
| MessageAnalyzer | **Critical** | Incorrect field ordering, wrong cardinality detection, missed oneof grouping | All generators produce wrong code for affected messages | Unit tests (37), pattern assertions | Foundational — every generator depends on correct analysis |
| TypeRegistry | **Critical** | Missing type, wrong map-entry detection | Cross-file references fail; map fields treated as repeated messages | Unit tests (14) | Required for resolving cross-file and nested types |
| PluginRunner | **High** | Wrong language selected, proto2 misidentified, path traversal bypass | Wrong language output; proto2 fields get proto3 defaults; files written outside output dir | Unit tests (22) | Central coordinator; security boundary |
| ProtoFileProcessor | **High** | Comment map wrong, syntax not propagated | Missing Javadoc; proto2 presence semantics lost | Indirect tests (95.5%) | Bridges raw descriptors to typed model |
| CodeWriter | **Low** | Wrong indentation | Generated code has formatting issues but still compiles | Unit tests (14), 100% coverage | Cosmetic only — incorrect indent doesn't affect semantics |
| KeywordUtil | **Medium** | Missing keyword, wrong escape | Generated code won't compile in target language | Unit tests, 100% coverage | Compilation error is detectable (not silent) |
| WellKnownType | **Medium** | Missing WKT, wrong classification | Timestamp/Duration serialized as raw arrays instead of formatted strings | Unit tests (3), 100% coverage | Incorrect but detectable by user |

### Per-Language Generators

| Generator | Criticality | Silent Failure Mode | Detectable Failure Mode |
|-----------|-------------|--------------------|-----------------------|
| Serializer | **Critical** | Wrong field position, wrong type encoding, missing null for gaps | — |
| Deserializer | **Critical** | Wrong position read, type mismatch, missing default | ClassCastException / runtime crash |
| CodeEmitter | **High** | Wrong class structure, missing equals/hashCode | Compilation error (detectable) |
| TypeMapper | **Critical** | Wrong Java/Python/etc. type for a proto type | Compilation error OR silent wrong cast |
| NameResolver | **High** | Wrong identifier, missing keyword escape | Compilation error (detectable) |

### Runtime Libraries

| Runtime | Criticality | Failure Mode | Effect |
|---------|-------------|--------------|--------|
| Java JsonArrayWriter/Reader | **Low** | Incorrect JSON output | Malformed JSON array serialization or deserialization failure |
| C codec.c | **High** | Buffer overflow in base64, use-after-free | Memory corruption in user's C application |
| C++ codec.hpp | **Low** | Wrong base64 output | Data corruption for bytes fields |
| Rust lib.rs | **Low** | Wrong trait implementation | Compilation error (detectable) |

## 3. Identified Hazards

| ID | Hazard | Severity | Likelihood | Risk Level | Mitigation |
|----|--------|----------|------------|------------|------------|
| HAZ-001 | Silent data corruption via wrong field position | Major | Unlikely | **High** | 12 indexing audit tests, 80 Java codegen tests, 240 multi-lang tests |
| HAZ-002 | Silent data corruption via wrong type encoding | Major | Unlikely | **High** | Type mapper tests (61), codegen pattern assertions |
| HAZ-003 | Cross-language encoding incompatibility | Moderate | Possible | **Medium** | Cross-language round-trip tests (Java↔Python), encoding spec |
| HAZ-004 | Code injection via crafted proto field/type names | Major | Rare | **Medium** | Field name regex validation, keyword escaping, protoc upstream validation |
| HAZ-005 | Path traversal writing files outside output directory | Moderate | Rare | **Low** | `..` path rejection in PluginRunner |
| HAZ-006 | Memory corruption in generated C code | Major | Unlikely | **Medium** | Static review only; no dynamic analysis (Valgrind/ASan) |
| HAZ-007 | int64/uint64 precision loss across JSON intermediaries | Moderate | Possible | **Medium** | Mitigated: int64 encoded as JSON string; backward-compat parsing |
| HAZ-008 | Proto2 schema defaults not properly escaped in non-Java generators | Moderate | Possible | **Medium** | Only Java generator validated; 16 others [INCOMPLETE_ANALYSIS] |
| HAZ-009 | Generated code breaks with future language/library versions | Minor | Likely | **Medium** | Zig stdlib unstable; Go generics evolution; new language dependencies: System.Text.Json (C#), Foundation (ObjC/Swift), kotlinx.serialization (Kotlin), dart:convert (Dart), JSON CPAN (Perl), json stdlib (Ruby), json built-in (PHP) |
| HAZ-010 | Schema evolution breaks positional encoding | Major | Unlikely | **Medium** | Field-number-based positioning; forward/backward compat tests |
| HAZ-011 | Denial of service via extremely sparse field numbers | Minor | Rare | **Low** | Warning emitted for sparse numbering; no hard limit |
| HAZ-012 | NaN/Infinity produce invalid JSON | Moderate | Unlikely | **Low** | Mitigated: NaN/Infinity serialize as null; tested |

### Hazard Details

#### HAZ-001: Silent data corruption via wrong field position
- **Description:** A bug in any SerializerGenerator or DeserializerGenerator that places a field value at the wrong array position. The generated code compiles and runs, but serialized data has fields swapped or shifted.
- **Causal Chain:** Bug in field-number-to-position mapping → serializer emits `array.add(field)` at wrong index → deserialized message has field A's value in field B's slot → user application reads wrong data
- **Affected Components:** All 17 SerializerGenerators, all 17 DeserializerGenerators, MessageAnalyzer (position calculation)
- **Current Safeguards:** 12 IndexingAuditTest cases verify position = fieldNumber - 1; 80 JavaCodeGenTest cases verify generated patterns; 240 MultiLanguageCodeGenTest cases verify all 16 other languages; cross-language round-trip tests verify Java↔Python produce identical JSON
- **Additional Mitigation:** Golden-file snapshot tests would catch regressions. Per-language compilation + execution tests (not just pattern matching) would catch runtime issues.

#### HAZ-002: Silent data corruption via wrong type encoding
- **Description:** A TypeMapper bug maps a proto type to the wrong target-language type, or a SerializerGenerator encodes a value incorrectly (e.g., int64 as numeric instead of string, bytes without base64).
- **Causal Chain:** Wrong type mapping → generated code compiles (types compatible enough) → serialized JSON has wrong representation → deserializer misinterprets or loses precision
- **Affected Components:** All 17 TypeMappers, all 17 SerializerGenerators/DeserializerGenerators
- **Current Safeguards:** 61 JavaTypeMapperTest cases cover all 15 scalar types; codegen tests verify int64→string, bytes→base64, enum→integer patterns across all languages
- **Additional Mitigation:** Round-trip tests that verify `deserialize(serialize(X)) == X` for all field types in all languages.

#### HAZ-003: Cross-language encoding incompatibility
- **Description:** Java serializer and Python serializer produce semantically different JSON for the same proto message, breaking interoperability.
- **Causal Chain:** Different number formatting (`3.0` vs `3`), different base64 padding, different null handling → JSON differs → deserialization in language B fails or produces wrong values
- **Affected Components:** All 17 SerializerGenerators
- **Current Safeguards:** Cross-language round-trip test (Java→Python→compare); encoding specification in SYSTEM_ANALYSIS.md Section 15
- **Additional Mitigation:** Extend round-trip tests to all language pairs (currently only Java↔Python). Define canonical JSON output format.

#### HAZ-004: Code injection via crafted proto identifiers
- **Description:** A malicious proto file (or crafted CodeGeneratorRequest) contains field names or type names that, when emitted into generated source code, form executable code in the target language.
- **Causal Chain:** Attacker crafts proto with field name `class"; System.exit(0); //` → plugin emits this verbatim into generated Java → generated code contains injected code
- **Affected Components:** All NameResolvers, all CodeEmitters
- **Current Safeguards:** Field name validation regex `[a-zA-Z_][a-zA-Z0-9_]*`; keyword escaping; protoc validates identifiers upstream
- **Residual Risk:** Message names and type names not validated by regex (only by keyword escaping). [ASSUMED_BEHAVIOR] protoc rejects invalid identifiers before plugin receives them.

#### HAZ-006: Memory corruption in generated C code
- **Description:** Generated C code has buffer overflows, use-after-free, or memory leaks that corrupt the user's application memory.
- **Causal Chain:** C generator emits incorrect malloc/free sequence → generated code has memory bug → user's C application crashes or corrupts data
- **Affected Components:** CSerializerGenerator, CDeserializerGenerator, CCodeEmitter, C runtime (codec.c)
- **Current Safeguards:** Static code review; strdup null-safety check (BUG fix); oneof bytes memory leak fixed; free() function generated for every message
- **Additional Mitigation:** Compile generated C code and run under Valgrind/AddressSanitizer with test data.

#### HAZ-008: Proto2 defaults not escaped in non-Java generators
- **Description:** A proto2 file with `string name = 1 [default = "hello\nworld"]` generates code in Python/JS/C/etc. where the newline is not properly escaped, causing syntax errors or wrong default values.
- **Causal Chain:** Proto2 default value with special chars → generator emits raw string into target language source → generated code has syntax error or interprets escape differently
- **Affected Components:** Python, JS, TS, C, C++, Rust, Zig, Go, C#, Kotlin, Swift, Dart, PHP, Ruby, Objective-C, Perl SerializerGenerators/DeserializerGenerators
- **Current Safeguards:** Java generator escapes `\n`, `\r`, `\t`, `\0`, `\`, `"` and handles `inf`/`nan`. Other generators: [INCOMPLETE_ANALYSIS]
- **Additional Mitigation:** Audit and fix proto2 default escaping in all 16 non-Java generators.

#### HAZ-010: Schema evolution breaks positional encoding
- **Description:** A user removes a field and later reuses the same field number for a different type, causing old serialized data to be misinterpreted at that position.
- **Causal Chain:** User removes `string name = 2` and later adds `int32 age = 2` → position 1 now expects an integer → old serialized data has a string at position 1 → deserialization fails or produces wrong data
- **Affected Components:** Encoding design (not a code bug)
- **Current Safeguards:** Encoding uses `field_number - 1` as position (not declaration order), so adding a new field number between existing ones does NOT shift any existing positions. Field 3 is always at index 2 regardless of whether field 2 exists. The real risk is field number reuse with a different type, which violates standard protobuf schema evolution rules.
- **Additional Mitigation:** Document clearly: "never reuse a field number for a different type; use `reserved` to retire old numbers." The schema evolution test suite verifies forward/backward compat for append-only changes.

## 4. Risk Matrix

|              | Rare | Unlikely | Possible | Likely | Certain |
|--------------|------|----------|----------|--------|---------|
| **Major**    | HAZ-004 | HAZ-001, HAZ-002, HAZ-006, HAZ-010 | | | |
| **Moderate** | HAZ-005 | HAZ-007, HAZ-012 | HAZ-003, HAZ-008 | | |
| **Minor**    | HAZ-011 | | | HAZ-009 | |

## 5. Assurance Level Determination

| Component Group | Required Assurance | Testing Rigor | Analysis Required |
|-----------------|-------------------|---------------|-------------------|
| MessageAnalyzer + TypeRegistry | **High** | Exhaustive (all field types, all cardinalities, all edge cases) | Yes — field position correctness proof |
| Java Generator (reference impl) | **High** | Exhaustive (80 dedicated tests + shared) | Yes — generated code compilation + round-trip |
| Other 16 Generators | **High** | Thorough (parameterized tests + language-specific edge cases) | Yes — generated code compilation |
| Runtime Libraries | **Medium** | Thorough (unit tests for each helper function) | Yes — C memory safety dynamic analysis |
| PluginRunner / Main | **Medium** | Basic (parameter parsing, error handling) | No |
| CodeWriter / KeywordUtil | **Low** | Basic (correctness of output formatting) | No |

## 6. Uncertainties

| Flag | Item | Required Information |
|------|------|---------------------|
| [UNKNOWN_CRITICALITY] | All 17 language generators | Which languages will be used in production vs. experimental? This affects testing rigor allocation. |
| [UNKNOWN_CRITICALITY] | Generated code deployment context | Will generated code handle sensitive data (PII, financial, medical)? This affects severity of data corruption hazards. |
| [INCOMPLETE_ANALYSIS] | C/C++ memory safety | No dynamic analysis performed. Severity of HAZ-006 may be higher if generated C code is used in security-sensitive contexts. |
| [INCOMPLETE_ANALYSIS] | Proto2 default escaping (16 languages) | HAZ-008 scope unknown — only Java generator audited. |
| [ASSUMED_BEHAVIOR] | protoc input validation | Severity of HAZ-004 depends on whether protoc can be bypassed. Assumed it cannot in normal usage. |

## 7. Approval

Hazard analysis requires domain expert review:
- [ ] All hazards identified are complete
- [ ] Severity ratings are accurate
- [ ] Criticality assignments are appropriate
- [ ] Mitigations are adequate
- [ ] Residual risks are acceptable

Expert: _________________________ Date: ____________
