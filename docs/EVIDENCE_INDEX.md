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
- File paths and line numbers are snapshots from commit 6feba4a
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
| KeywordUtil | `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/codegen/KeywordUtil.java` | Language keyword escaping (465 lines, 8 languages) | SEC-003 |

### Language Generators (9 languages × 6 classes each)

| Language | Package | Key Classes | Requirements Traced |
|----------|---------|-------------|---------------------|
| Java | `codegen/java/` | JavaGenerator, JavaCodeEmitter, JavaSerializerGenerator, JavaDeserializerGenerator, JavaTypeMapper, JavaNameResolver | FR-003–FR-010, SR-001–SR-004 |
| Python | `codegen/python/` | PythonGenerator, PythonCodeEmitter, PythonSerializerGenerator, PythonDeserializerGenerator, PythonTypeMapper, PythonNameResolver | FR-011, FR-013 |
| JavaScript | `codegen/javascript/` | JavaScriptGenerator, JavaScriptCodeEmitter, JavaScriptSerializerGenerator, JavaScriptDeserializerGenerator, JavaScriptTypeMapper, JavaScriptNameResolver | FR-011, FR-013 |
| TypeScript | `codegen/typescript/` | TypeScriptGenerator, TypeScriptCodeEmitter, TypeScriptSerializerGenerator, TypeScriptDeserializerGenerator, TypeScriptTypeMapper, TypeScriptNameResolver | FR-011, FR-013 |
| C | `codegen/c/` | CGenerator, CCodeEmitter, CSerializerGenerator, CDeserializerGenerator, CTypeMapper, CNameResolver | FR-011, FR-013 |
| C++ | `codegen/cpp/` | CppGenerator, CppCodeEmitter, CppSerializerGenerator, CppDeserializerGenerator, CppTypeMapper, CppNameResolver | FR-011, FR-013 |
| Rust | `codegen/rust/` | RustGenerator, RustCodeEmitter, RustSerializerGenerator, RustDeserializerGenerator, RustTypeMapper, RustNameResolver | FR-011, FR-013 |
| Zig | `codegen/zig/` | ZigGenerator, ZigCodeEmitter, ZigSerializerGenerator, ZigDeserializerGenerator, ZigTypeMapper, ZigNameResolver | FR-011, FR-013 |
| Go | `codegen/go/` | GoGenerator, GoCodeEmitter, GoSerializerGenerator, GoDeserializerGenerator, GoTypeMapper, GoNameResolver | FR-011, FR-013 |

### Runtime Libraries

| Language | Path | Purpose |
|----------|------|---------|
| Java | `runtime/java/src/main/java/dev/protocgen/textcodecs/jsonarray/runtime/` | JsonArrayCodec interface, FieldCodecs |
| C | `runtime/c/src/codec.c`, `runtime/c/include/jsonarray/codec.h` | Base64, string utils, cJSON helpers |
| C++ | `runtime/cpp/include/jsonarray/codec.hpp` | Header-only base64 |
| Rust | `runtime/rust/src/lib.rs`, `runtime/rust/Cargo.toml` | serde_json traits, base64 helpers |

---

## 3. Test Artifacts

### Unit Tests

| Test Class | Path | Tests | Requirements Verified |
|------------|------|-------|-----------------------|
| JavaCodeGenTest | `plugin/src/test/java/.../JavaCodeGenTest.java` | 71 | FR-001–FR-010, SR-001–SR-004, SEC-001, SEC-003, SEC-004 |
| JavaTypeMapperTest | `plugin/src/test/java/.../JavaTypeMapperTest.java` | 61 | FR-003, SR-002 |
| SafetySecurityTest | `plugin/src/test/java/.../SafetySecurityTest.java` | 116* | SR-001–SR-004, SEC-001–SEC-004, VULN-001–009 |
| MessageAnalyzerTest | `plugin/src/test/java/.../MessageAnalyzerTest.java` | 37 | SEC-001, FR-010, FR-018 |
| PluginRunnerTest | `plugin/src/test/java/.../PluginRunnerTest.java` | 22 | FR-014, FR-015, FR-011 |
| TypeRegistryTest | `plugin/src/test/java/.../TypeRegistryTest.java` | 14 | SEC-001 (VULN-002) |
| CodeWriterTest | `plugin/src/test/java/.../CodeWriterTest.java` | 14 | — (utility) |
| IndexingAuditTest | `plugin/src/test/java/.../IndexingAuditTest.java` | 12 | SR-001, FR-001, FR-002 |
| GoldenFileTest | `plugin/src/test/java/.../GoldenFileTest.java` | 9 | FR-011, SR-002 |
| JavaNameResolverTest | `plugin/src/test/java/.../JavaNameResolverTest.java` | 5 | SEC-003, SEC-004 |
| WellKnownTypeTest | `plugin/src/test/java/.../WellKnownTypeTest.java` | 3 | FR-017 |
| MultiLanguageCodeGenTest | `plugin/src/test/java/.../MultiLanguageCodeGenTest.java` | 120** | FR-011, FR-013, SR-001, SR-002 |
| | | **Total: 484** | |

\* 39 `@Test` + 9 `@ParameterizedTest` methods = 116 expanded invocations.
\*\* 15 `@ParameterizedTest` methods × 8 languages = 120 expanded invocations.

### Test Proto Files

| Proto File | Path | Purpose |
|------------|------|---------|
| user.proto | `plugin/src/test/resources/user.proto` | Basic message with nested address |
| address.proto | `plugin/src/test/resources/address.proto` | Cross-file reference target |
| kitchen_sink.proto | `plugin/src/test/resources/kitchen_sink.proto` | 29 fields, all types |
| edge_cases.proto | `plugin/src/test/resources/edge_cases.proto` | 17 messages, edge cases |
| proto2_test.proto | `plugin/src/test/resources/proto2_test.proto` | Proto2 syntax features |

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

### Integration Tests

| Test | Path | What It Verifies |
|------|------|------------------|
| Cross-language round-trip | `integration-tests/cross-language-test.sh` | Java ↔ Python serialize/deserialize |
| Schema evolution | `integration-tests/schema-evolution-test.sh` | Forward/backward compatibility |
| Java round-trip | `integration-tests/java/UserRoundTripTest.java` | Java end-to-end |
| Python round-trip | `integration-tests/python/user_round_trip_test.py` | Python end-to-end |

---

## 4. Coverage Reports

| Report | Path | Format |
|--------|------|--------|
| JaCoCo HTML | `plugin/build/reports/jacoco/test/html/index.html` | Interactive HTML |
| JaCoCo CSV | `plugin/build/reports/jacoco/test/jacocoTestReport.csv` | Machine-readable |
| JUnit XML | `plugin/build/test-results/test/` | Per-class XML results |

### Coverage Summary (Commit 6feba4a)

| Metric | Value |
|--------|-------|
| Instruction coverage | 27,217 / 34,617 (78.6%) |
| Branch coverage | 2,137 / 3,189 (67.0%) |
| Line coverage | 5,209 / 6,512 (80.0%) |

---

## 5. Security Evidence

### Vulnerability Tracking

| VULN ID | Severity | Fix Commit | Verification |
|---------|----------|------------|--------------|
| VULN-001 | High | 44a0363 | MessageAnalyzerTest — name validation |
| VULN-002 | High | 44a0363 | TypeRegistryTest — type name validation |
| VULN-003 | High | 44a0363 | SafetySecurityTest — default value validation |
| VULN-004 | Medium | 44a0363 | SafetySecurityTest — path traversal rejection |
| VULN-005 | Low | 44a0363 | JavaCodeGenTest — Javadoc `*/` escaping |
| VULN-006 | Low | N/A (accepted) | Warning emitted; protoc limits practical field numbers |
| VULN-007 | Low | Partial | C runtime has bounds-checked array access; generated code gap remains |
| VULN-008 | Medium | 44a0363 | C runtime base64 validates input length % 4 |
| VULN-009 | Info | 44a0363 | ObjectMapper cached as static field |

### Security Documents

| Document | Path | Content |
|----------|------|---------|
| Security Assessment | `docs/SECURITY_ASSESSMENT.md` | STRIDE analysis, 9 vulnerabilities, SBOM |
| Bug Catalog | `BUG_CATALOG.md` | 36 bugs identified and fixed |

---

## 6. Build and CI Artifacts

| Artifact | Path | Purpose |
|----------|------|---------|
| Root build | `build.gradle` | Spotless + Google Java Format |
| Plugin build | `plugin/build.gradle` | Shadow JAR, JaCoCo, test config |
| Settings | `settings.gradle` | Multi-project structure |
| CI workflow | `.github/workflows/ci.yml` | Automated build, test, coverage |
| Plugin wrapper | `protoc-gen-jsonarray` | Shell script for protoc integration |

---

## 7. Project Documentation

| Document | Path | Purpose |
|----------|------|---------|
| README | `README.md` | Usage, installation, encoding spec |
| CHANGELOG | `CHANGELOG.md` | Version history |
| CONTRIBUTING | `CONTRIBUTING.md` | Contribution guidelines |
| LICENSE | `LICENSE` | Apache 2.0 |
| Bug Catalog | `BUG_CATALOG.md` | 36 bugs found and fixed |
| Implementation Plan | `docs/PLAN.md` | Original implementation plan |

---

## 8. Git History (Key Commits)

| Commit | Description | Phase |
|--------|-------------|-------|
| d3b8c77 | Initial release of protoc-gen-jsonarray v0.1.0 | Implementation |
| 0ee76a2 | Fix null safety and add indexing audit tests | Implementation |
| 600d827 | Add comprehensive bug catalog from systematic audit | Quality |
| cd789ae | Fix all 36 cataloged bugs across 9 language generators | Quality |
| 0bace71 | Add 71 end-to-end code generation tests | Quality |
| 31a5952 | Migrate to Groovy DSL and add multi-language tests | Quality |
| d2e4c84 | Add Phase 1 system analysis document | Phase 1 |
| 8f4da77 | Improve system analysis with deeper coverage | Phase 1 |
| 6fd50ef | Add Phase 2: Hazard Analysis and Requirements | Phase 2 |
| 74c2c34 | Add Phase 3: Test Strategy | Phase 3 |
| 0a1f2f2 | Add Phase 4: Test Matrix and traceability | Phase 4 |
| 7c03c86 | Add Phase 5: Security Assessment | Phase 5 |
| 44a0363 | Fix all 9 security vulnerabilities | Phase 5 |
| 046b8e2 | Fix 22 stale numbers across Phase 1-4 docs | Phase 5 |
| 6feba4a | Update Phase 1-5 docs after security fixes | Phase 5 |
| (pending) | Phase 6: Assurance Case and Evidence Index | Phase 6 |

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
