# Evidence Index

================================================================================
Generated: 2026-03-25T02:00:00-04:00
Model: Claude Opus 4.6 (1M context)
Review Status: PENDING
Reviewed By: _______________
================================================================================

```
LIMITATIONS:
- This index is auto-generated and requires human validation
- File paths and line numbers are snapshots from current HEAD
- All evidence items must be verified against the actual codebase
```

---

## 1. Document Cross-References

| Phase | Document | Location | Status |
|-------|----------|----------|--------|
| 1 | System Analysis | `docs/SYSTEM_ANALYSIS.md` | Complete |
| 2 | Hazard Analysis | `docs/HAZARD_ANALYSIS.md` | Complete |
| 2 | Requirements Specification | `docs/REQUIREMENTS.md` | Complete |
| 3 | Test Strategy | `docs/TEST_STRATEGY.md` | Complete |
| 4 | Test Traceability Matrix | `docs/TEST_MATRIX.md` | Complete |
| 5 | Security Assessment | `docs/SECURITY_ASSESSMENT.md` | Complete |
| 6 | Assurance Case | `docs/ASSURANCE_CASE.md` | Complete |
| 6 | Evidence Index | `docs/EVIDENCE_INDEX.md` (this file) | Complete |

---

## 2. Source Code Artifacts

### Plugin Core

| Artifact | Path | Purpose | Requirements Traced |
|----------|------|---------|---------------------|
| Entry point | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/Main.java` | stdin/stdout protoc protocol | FR-014, FR-016 |
| Orchestrator | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/PluginRunner.java` | Parameter parsing, language dispatch, path validation | FR-011, FR-014, FR-015, SEC-002 |
| Descriptor analysis | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/MessageAnalyzer.java` | Proto descriptor → internal model, validation | SEC-001, FR-010, FR-018 |
| File processor | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/ProtoFileProcessor.java` | Per-file code generation dispatch | FR-014 |
| Code writer | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/CodeWriter.java` | Indented source output utility | — |

### Internal Model

| Artifact | Path | Purpose | Requirements Traced |
|----------|------|---------|---------------------|
| ProtoField | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/model/ProtoField.java` | Field metadata, position, presence | SR-001, FR-001 |
| ProtoMessage | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/model/ProtoMessage.java` | Message structure, max field number | FR-002 |
| ProtoEnum | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/model/ProtoEnum.java` | Enum values | FR-007 |
| ProtoFile | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/model/ProtoFile.java` | File metadata, proto2/proto3 | FR-010 |
| TypeRegistry | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/model/TypeRegistry.java` | Global type catalog, name validation | SEC-001 (VULN-002 fix) |
| WellKnownType | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/model/WellKnownType.java` | Well-known type detection | FR-017 |

### Code Generation Framework

| Artifact | Path | Purpose | Requirements Traced |
|----------|------|---------|---------------------|
| LanguageGenerator | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/codegen/LanguageGenerator.java` | Generator interface | FR-011 |
| NameResolver | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/codegen/NameResolver.java` | Name mapping + collision detection | SEC-003, SEC-004 |
| TypeMapper | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/codegen/TypeMapper.java` | Type mapping interface | FR-003 |
| KeywordUtil | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/codegen/KeywordUtil.java` | Language keyword escaping (~950 lines, 16 keyword sets) | SEC-003 |

### Language Generators (17 languages x 6 classes each)

| Language | Package | Key Classes | Requirements Traced |
|----------|---------|-------------|---------------------|
| Java | `codegen/java/` | JavaGenerator, JavaCodeEmitter, JavaSerializerGenerator, JavaDeserializerGenerator, JavaTypeMapper, JavaNameResolver | FR-003-FR-010, SR-001-SR-004 |
| Python | `codegen/python/` | PythonGenerator, PythonCodeEmitter, PythonSerializerGenerator, PythonDeserializerGenerator, PythonTypeMapper, PythonNameResolver | FR-011, FR-013 |
| JavaScript | `codegen/javascript/` | JavaScriptGenerator, JavaScriptCodeEmitter, JavaScriptSerializerGenerator, JavaScriptDeserializerGenerator, JavaScriptTypeMapper, JavaScriptNameResolver | FR-011, FR-013 |
| TypeScript | `codegen/typescript/` | TypeScriptGenerator, TypeScriptCodeEmitter, TypeScriptSerializerGenerator, TypeScriptDeserializerGenerator, TypeScriptTypeMapper, TypeScriptNameResolver | FR-011, FR-013 |
| C | `codegen/c/` | CGenerator, CCodeEmitter, CSerializerGenerator, CDeserializerGenerator, CTypeMapper, CNameResolver | FR-006, FR-011, FR-013 (includes `size_t value_len` fix for bytes-valued map entries) |
| C++ | `codegen/cpp/` | CppGenerator, CppCodeEmitter, CppSerializerGenerator, CppDeserializerGenerator, CppTypeMapper, CppNameResolver | FR-011, FR-013 |
| Rust | `codegen/rust/` | RustGenerator, RustCodeEmitter, RustSerializerGenerator, RustDeserializerGenerator, RustTypeMapper, RustNameResolver | FR-011, FR-013 |
| Zig | `codegen/zig/` | ZigGenerator, ZigCodeEmitter, ZigSerializerGenerator, ZigDeserializerGenerator, ZigTypeMapper, ZigNameResolver | FR-011, FR-013 |
| Go | `codegen/go/` | GoGenerator, GoCodeEmitter, GoSerializerGenerator, GoDeserializerGenerator, GoTypeMapper, GoNameResolver | FR-011, FR-013 |
| C# | `codegen/csharp/` | CSharpGenerator, CSharpCodeEmitter, CSharpSerializerGenerator, CSharpDeserializerGenerator, CSharpTypeMapper, CSharpNameResolver | FR-011, FR-013 |
| Kotlin | `codegen/kotlin/` | KotlinGenerator, KotlinCodeEmitter, KotlinSerializerGenerator, KotlinDeserializerGenerator, KotlinTypeMapper, KotlinNameResolver | FR-011, FR-013 |
| Swift | `codegen/swift/` | SwiftGenerator, SwiftCodeEmitter, SwiftSerializerGenerator, SwiftDeserializerGenerator, SwiftTypeMapper, SwiftNameResolver | FR-011, FR-013 |
| Dart | `codegen/dart/` | DartGenerator, DartCodeEmitter, DartSerializerGenerator, DartDeserializerGenerator, DartTypeMapper, DartNameResolver | FR-011, FR-013 |
| PHP | `codegen/php/` | PhpGenerator, PhpCodeEmitter, PhpSerializerGenerator, PhpDeserializerGenerator, PhpTypeMapper, PhpNameResolver | FR-011, FR-013 |
| Ruby | `codegen/ruby/` | RubyGenerator, RubyCodeEmitter, RubySerializerGenerator, RubyDeserializerGenerator, RubyTypeMapper, RubyNameResolver | FR-011, FR-013 |
| Objective-C | `codegen/objc/` | ObjCGenerator, ObjCCodeEmitter, ObjCSerializerGenerator, ObjCDeserializerGenerator, ObjCTypeMapper, ObjCNameResolver | FR-011, FR-013 |
| Perl | `codegen/perl/` | PerlGenerator, PerlCodeEmitter, PerlSerializerGenerator, PerlDeserializerGenerator, PerlTypeMapper, PerlNameResolver | FR-011, FR-013 |

### Runtime Libraries

| Language | Path | Purpose |
|----------|------|---------|
| Java | `runtime/java/src/main/java/dev/protocgen/textcodecs/jsonarray/runtime/` | JsonArrayWriter, JsonArrayReader (zero-dependency) |
| C | `runtime/c/src/codec.c`, `runtime/c/include/jsonarray/codec.h` | Base64, string utils, cJSON helpers |
| C++ | `runtime/cpp/include/jsonarray/codec.hpp` | Header-only base64 |
| Rust | `runtime/rust/src/lib.rs`, `runtime/rust/Cargo.toml` | serde_json traits, base64 helpers |

---

## 3. Test Artifacts

### Unit Tests

| Test Class | Path | Tests | Requirements Verified |
|------------|------|-------|-----------------------|
| JavaCodeGenTest | `plugin/src/test/java/.../jsonarray/JavaCodeGenTest.java` | 80 | FR-001-FR-010, SR-001-SR-004, SEC-001, SEC-003, SEC-004 |
| JavaTypeMapperTest | `plugin/src/test/java/.../jsonarray/JavaTypeMapperTest.java` | 61 | FR-003, SR-002 |
| SafetySecurityTest | `plugin/src/test/java/.../jsonarray/SafetySecurityTest.java` | 180 | SR-001-SR-004, SEC-001-SEC-004, VULN-001-009 |
| MessageAnalyzerTest | `plugin/src/test/java/.../jsonarray/MessageAnalyzerTest.java` | 37 | SEC-001, FR-010, FR-018 |
| PluginRunnerTest | `plugin/src/test/java/.../jsonarray/PluginRunnerTest.java` | 22 | FR-014, FR-015, FR-011 |
| TypeRegistryTest | `plugin/src/test/java/.../jsonarray/TypeRegistryTest.java` | 14 | SEC-001 (VULN-002) |
| CodeWriterTest | `plugin/src/test/java/.../jsonarray/CodeWriterTest.java` | 14 | -- (utility) |
| IndexingAuditTest | `plugin/src/test/java/.../jsonarray/IndexingAuditTest.java` | 12 | SR-001, FR-001, FR-002 |
| GoldenFileTest | `plugin/src/test/java/.../jsonarray/GoldenFileTest.java` | 17 | FR-011, SR-002 |
| JavaNameResolverTest | `plugin/src/test/java/.../jsonarray/JavaNameResolverTest.java` | 5 | SEC-003, SEC-004 |
| WellKnownTypeTest | `plugin/src/test/java/.../jsonarray/WellKnownTypeTest.java` | 3 | FR-017 |
| MultiLanguageCodeGenTest | `plugin/src/test/java/.../jsonarray/MultiLanguageCodeGenTest.java` | 240 | FR-011, FR-013, SR-001, SR-002 |
| PerformanceBenchmarkTest | `plugin/src/test/java/.../jsonarray/PerformanceBenchmarkTest.java` | 8 | PERF-001, PERF-002 |
| MemoryBenchmarkTest | `plugin/src/test/java/.../jsonarray/MemoryBenchmarkTest.java` | 4 | PERF-001 |
| PbtkJavaCodeGenTest | `plugin/src/test/java/.../pbtkurl/PbtkJavaCodeGenTest.java` | 29 | FR-011 (pbtk) |
| PbtkMultiLanguageCodeGenTest | `plugin/src/test/java/.../pbtkurl/PbtkMultiLanguageCodeGenTest.java` | 144 | FR-011, FR-013 (pbtk) |
| PbtkSafetySecurityTest | `plugin/src/test/java/.../pbtkurl/PbtkSafetySecurityTest.java` | 86 | SR-001-SR-004, SEC-001-SEC-004 (pbtk) |
| PbtkSchemaEvolutionTest | `plugin/src/test/java/.../pbtkurl/PbtkSchemaEvolutionTest.java` | 34 (2 parameterized x 17 langs) | FR-001, FR-002, SR-001 (pbtk) |
| PbtkJavaSchemaEvolutionTest | `plugin/src/test/java/.../pbtkurl/PbtkJavaSchemaEvolutionTest.java` | 4 | FR-001, FR-002, SR-001 (pbtk) |
| SchemaEvolutionTest | `plugin/src/test/java/.../jsonarray/SchemaEvolutionTest.java` | 119 | FR-001, FR-002, SR-001 |
| JavaSchemaEvolutionTest | `plugin/src/test/java/.../jsonarray/JavaSchemaEvolutionTest.java` | 15 | FR-001, FR-002, SR-001 |
| | | **Total: 1,107** | |

Note: 15 `@ParameterizedTest` methods x 16 languages = 240 expanded invocations for MultiLanguageCodeGenTest.

### Test Proto Files

| Proto File | Path | Purpose |
|------------|------|---------|
| user.proto | `test-protos/src/main/proto/user.proto` | Basic message with nested address |
| address.proto | `test-protos/src/main/proto/address.proto` | Cross-file reference target |
| kitchen_sink.proto | `test-protos/src/main/proto/kitchen_sink.proto` | 29 fields, all types |
| edge_cases.proto | `test-protos/src/main/proto/edge_cases.proto` | 17 messages, edge cases |
| proto2_test.proto | `test-protos/src/main/proto/proto2_test.proto` | Proto2 syntax features |

### Golden Files

| Language | Path | Snapshot Content |
|----------|------|-----------------|
| Java | `plugin/src/test/resources/golden/java/` | Generated Java source |
| Python | `plugin/src/test/resources/golden/python/` | Generated Python source |
| JavaScript | `plugin/src/test/resources/golden/javascript/` | Generated JS source |
| TypeScript | `plugin/src/test/resources/golden/typescript/` | Generated TS source |
| C | `plugin/src/test/resources/golden/c/` | Generated .h/.c source |
| C++ | `plugin/src/test/resources/golden/cpp/` | Generated C++ source |
| Rust | `plugin/src/test/resources/golden/rust/` | Generated Rust source |
| Zig | `plugin/src/test/resources/golden/zig/` | Generated Zig source |
| Go | `plugin/src/test/resources/golden/go/` | Generated Go source |
| C# | `plugin/src/test/resources/golden/csharp/` | Generated C# source |
| Kotlin | `plugin/src/test/resources/golden/kotlin/` | Generated Kotlin source |
| Swift | `plugin/src/test/resources/golden/swift/` | Generated Swift source |
| Dart | `plugin/src/test/resources/golden/dart/` | Generated Dart source |
| PHP | `plugin/src/test/resources/golden/php/` | Generated PHP source |
| Ruby | `plugin/src/test/resources/golden/ruby/` | Generated Ruby source |
| Objective-C | `plugin/src/test/resources/golden/objc/` | Generated Objective-C source |
| Perl | `plugin/src/test/resources/golden/perl/` | Generated Perl source |

### Integration Tests

| Test | Path | What It Verifies |
|------|------|------------------|
| Cross-language round-trip | `integration-tests/cross-language-test.sh` | Java ↔ Python serialize/deserialize |
| Schema evolution | `integration-tests/schema-evolution-test.sh` | Forward/backward compatibility |
| Proto2-proto3 migration | `integration-tests/proto2-proto3-migration-test.sh` | Proto2/proto3 migration compatibility (5 scenarios: proto2→proto3, short array defaults, proto3→proto2, zero-value preservation, cross-syntax round-trip) |
| Java round-trip | `integration-tests/java/UserRoundTripTest.java` | Java end-to-end |
| Python round-trip | `integration-tests/python/user_round_trip_test.py` | Python end-to-end |

---

## 4. Coverage Reports

| Report | Path | Format |
|--------|------|--------|
| JaCoCo HTML | `plugin/build/reports/jacoco/test/html/index.html` | Interactive HTML |
| JaCoCo CSV | `plugin/build/reports/jacoco/test/jacocoTestReport.csv` | Machine-readable |
| JUnit XML | `plugin/build/test-results/test/` | Per-class XML results |

### Coverage Summary (current HEAD)

| Metric | Value |
|--------|-------|
| Instruction coverage | 73.9% |
| Line coverage | 76.6% |

---

## 5. Security Evidence

### Vulnerability Tracking

| VULN ID | Severity | Fix Commit | Verification |
|---------|----------|------------|--------------|
| VULN-001 | High | Fixed | MessageAnalyzerTest -- name validation |
| VULN-002 | High | Fixed | TypeRegistryTest -- type name validation |
| VULN-003 | High | Fixed | SafetySecurityTest -- default value validation |
| VULN-004 | Medium | Fixed | SafetySecurityTest -- path traversal rejection |
| VULN-005 | Low | Fixed | JavaCodeGenTest -- Javadoc `*/` escaping |
| VULN-006 | Low | Fixed | Warning emitted; protoc limits practical field numbers |
| VULN-007 | Low | Fixed | Bounds checking and NULL checks added to generated C deserializer code |
| VULN-008 | Medium | Fixed | C runtime base64 validates input length % 4 |
| VULN-009 | Info | Fixed | Resolved in v0.2.0 |

### Security Documents

| Document | Path | Content |
|----------|------|---------|
| Security Assessment | `docs/SECURITY_ASSESSMENT.md` | STRIDE analysis, 9 vulnerabilities, SBOM |
| Bug Catalog | `docs/archive/BUG_CATALOG_v0.1.md` | 36 bugs identified and fixed |

---

## 6. Build and CI Artifacts

| Artifact | Path | Purpose |
|----------|------|---------|
| Root build | `build.gradle` | Spotless + Google Java Format |
| Plugin build | `plugin/build.gradle` | Shadow JAR, JaCoCo, test config |
| Settings | `settings.gradle` | Multi-project structure |
| CI workflow | GitHub Actions (user-configured) | Automated build, test, coverage |
| Plugin wrapper (jsonarray) | `protoc-gen-jsonarray` | Shell script for protoc integration |
| Plugin wrapper (pbtkurl) | `protoc-gen-pbtkurl` | Shell script for protoc integration |

---

## 7. Project Documentation

| Document | Path | Purpose |
|----------|------|---------|
| README | `README.md` | Usage, installation, encoding spec |
| CHANGELOG | `CHANGELOG.md` | Version history |
| CONTRIBUTING | `CONTRIBUTING.md` | Contribution guidelines |
| LICENSE | `LICENSE` | Apache 2.0 |
| Bug Catalog | `docs/archive/BUG_CATALOG_v0.1.md` | 36 bugs found and fixed |
| Implementation Plan | `docs/archive/PLAN.md` | Original implementation plan |

---

## 8. Git History (Key Commits)

| Commit | Description | Phase |
|--------|-------------|-------|
| 1a8d60d | Initial release of protobuf-text-codecs v0.2.0 | Implementation |
| a893b9a | Eliminate Jackson dependency, add immutable builder pattern, and prepare for OSS release | Implementation |
| a806b9b | Trim README from 783 to 434 lines | Documentation |
| f861f31 | Add protobuf tutorial links and protoc plugin protocol reference | Documentation |
| 376655e | Add code generation for 8 new languages: C#, Kotlin, Swift, Dart, PHP, Ruby, Objective-C, Perl | Languages |
| 1c7c605 | Add keyword escaping for 5 new languages and inline plugin protocol docs | Languages |
| 549f016 | Update Supported Languages table to 17 languages | Documentation |
| 8fdae73 | Expand test coverage and CI to all 17 languages | Testing |
| 0466fbe | Add 8 new languages to parameterized multi-language tests | Testing |

---

## 9. Traceability Summary

### Requirements → Evidence Path

```
FR/SR/SEC Requirement
  → TEST_MATRIX.md (maps requirement → test cases)
  → Test Source Files (implement the test cases)
  → JaCoCo Reports (measure coverage)
  → ASSURANCE_CASE.md (claims + evidence linkage)
  → EVIDENCE_INDEX.md (this file — artifact locations)
```

### Hazard → Mitigation Path

```
HAZARD_ANALYSIS.md (HAZ-001 through HAZ-012)
  → REQUIREMENTS.md (SR/SEC requirements derived from hazards)
  → SECURITY_ASSESSMENT.md (VULN findings + fixes)
  → Test files (verification of mitigations)
  → ASSURANCE_CASE.md (gap analysis)
```
