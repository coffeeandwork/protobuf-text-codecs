# Test Traceability Matrix

================================================================================
Generated: 2026-03-24T23:00:00-04:00
Model: Claude Opus 4.6 (1M context)
Review Status: PENDING
Reviewed By: _______________
================================================================================

```
LIMITATIONS:
- This is a DRAFT traceability matrix requiring human validation
- Test counts and coverage are estimates until verified by execution
- Test file references assume all tests pass
```

## Requirements to Test Cases

### Functional Requirements

| Requirement | Test File(s) | Test Case(s) | Type | Status |
|-------------|-------------|--------------|------|--------|
| FR-001: Positional encoding | IndexingAuditTest | testArrayPositionIsFieldNumberMinusOne, testSparseFieldGaps_1_3_5, testSingleFieldAtNumber1 | Unit | Implemented |
| FR-001 | JavaCodeGenTest | testScalarInt32, testScalarString, (15 scalar tests) | Unit | Implemented |
| FR-001 | MultiLanguageCodeGenTest | testScalarFields (x16 langs) | Unit | Implemented |
| FR-002: Gap handling | IndexingAuditTest | testSparseFieldGaps_1_3_5, testSparseFieldGaps_GeneratedJavaSerializerHas5Elements | Unit | Implemented |
| FR-002 | MultiLanguageCodeGenTest | testFieldNumberGaps (x16 langs) | Unit | Implemented |
| FR-003: Scalar types | JavaTypeMapperTest | 61 tests covering all 15 scalar types | Unit | Implemented |
| FR-003 | JavaCodeGenTest | testScalar{Double,Float,Int32,Int64,Uint32,Uint64,Bool,String,Bytes,...} | Unit | Implemented |
| FR-003 | SafetySecurityTest | testSR002_AllScalarTypesConsistent (x17 langs) | Unit | Draft |
| FR-004: Nested messages | JavaCodeGenTest | testNestedMessageSerialize, testNestedMessageAsStaticInnerClass | Unit | Implemented |
| FR-004 | MultiLanguageCodeGenTest | testNestedMessage (x16 langs) | Unit | Implemented |
| FR-005: Repeated fields | JavaCodeGenTest | testRepeatedFieldType, testRepeatedFieldSerialization, testRepeatedMessageSerialization, testRepeatedFieldBoxedTypes | Unit | Implemented |
| FR-005 | MultiLanguageCodeGenTest | testRepeatedField (x16 langs) | Unit | Implemented |
| FR-006: Map fields | JavaCodeGenTest | testMapFieldStringKey, testMapFieldIntKey, testMapFieldMessageValue, testMapFieldDeserialization | Unit | Implemented |
| FR-006 | MultiLanguageCodeGenTest | testMapField (x16 langs) | Unit | Implemented |
| FR-007: Enum encoding | JavaCodeGenTest | testEnumSerialization, testRepeatedEnumSerialization | Unit | Implemented |
| FR-007 | MultiLanguageCodeGenTest | testEnumGeneration (x16 langs) | Unit | Implemented |
| FR-008: Oneof encoding | JavaCodeGenTest | testOneofCaseTracking, testOneofSerializerConditional, testOneofSetterMutatesCase | Unit | Implemented |
| FR-008 | MultiLanguageCodeGenTest | testOneofTracking (x16 langs) | Unit | Implemented |
| FR-009: Proto3 optional | JavaCodeGenTest | testProto3OptionalPresence | Unit | Implemented |
| FR-009 | MultiLanguageCodeGenTest | testOptionalPresence (x16 langs) | Unit | Implemented |
| FR-010: Proto2 support | JavaCodeGenTest | testProto2RequiredField, testProto2OptionalPresence, testProto2StringDefault, testProto2IntDefault, testProto2BoolDefault, testProto2EnumDefault | Unit | Implemented |
| FR-010 | PluginRunnerTest | testProto2Support | Unit | Implemented |
| FR-011: Multi-language | MultiLanguageCodeGenTest | 15 tests x 16 languages = 240 tests | Unit | Implemented |
| FR-011 | CI workflow | 17-language protoc generation smoke test | System | Implemented |
| FR-012: Cross-language interop | cross-language-test.sh | 5 assertions (Java↔Python) | Integration | Implemented |
| FR-013: Cross-file refs | MultiLanguageCodeGenTest | testCrossFileReference (x16 langs) | Unit | Implemented |
| FR-014: Plugin protocol | PluginRunnerTest | 22 tests (parameter parsing, error handling, features) | Unit | Implemented |
| FR-015: Error reporting | PluginRunnerTest | testUnsupportedLanguage, error tests | Unit | Implemented |
| FR-016: Version reporting | SafetySecurityTest | testVersionFlag | Unit | Draft |
| FR-017: Well-known types | WellKnownTypeTest | 3 tests | Unit | Implemented |
| FR-017 | JavaCodeGenTest | testWellKnownTypeTimestamp, testWellKnownTypeAnyRejection | Unit | Implemented |
| FR-018: Any rejection | JavaCodeGenTest | testAnyRejection | Unit | Implemented |
| FR-018 | MessageAnalyzerTest | testAnyRejection | Unit | Implemented |
| FR-018 | SafetySecurityTest | testFault006_AnyFieldRejection | Unit | Draft |

### Safety Requirements

| Requirement | Hazard | Test File(s) | Test Case(s) | Type | Status |
|-------------|--------|-------------|--------------|------|--------|
| SR-001: Position correctness | HAZ-001 | IndexingAuditTest | 12 tests | Unit | Implemented |
| SR-001 | HAZ-001 | SafetySecurityTest | testSR001_fieldPositionInGeneratedCode (x17 langs) | Unit | Implemented |
| SR-002: Type consistency | HAZ-002 | MultiLanguageCodeGenTest | 240 tests (pattern assertions) | Unit | Implemented |
| SR-002 | HAZ-002 | SafetySecurityTest | testSR002_int32EncodingConsistency, testSR002_bytesBase64Consistency, testSR002_enumAsIntegerConsistency (x17 langs) | Unit | Implemented |
| SR-003: int64 precision | HAZ-007 | JavaCodeGenTest | testInt64/Uint64/Sfixed64/Fixed64 (4 tests) | Unit | Implemented |
| SR-003 | HAZ-007 | SafetySecurityTest | testSR003_int64AsString (x17 langs), testSR003_all64BitTypesAsStringInJava (x5 types) | Unit | Implemented |
| SR-004: NaN/Infinity | HAZ-012 | JavaCodeGenTest | testNaNInfinityDouble, testNaNInfinityFloat | Unit | Implemented |
| SR-004 | HAZ-012 | SafetySecurityTest | testSR004_doubleNanCheckInJava, testSR004_floatNanCheckInJava, testSR004_nanEmitsNullInJava | Unit | Implemented |

### Security Requirements

| Requirement | Test File(s) | Test Case(s) | Type | Status |
|-------------|-------------|--------------|------|--------|
| SEC-001: Field name validation | MessageAnalyzerTest | field name validation tests | Unit | Implemented |
| SEC-001 | SafetySecurityTest | testSEC001_validFieldNameAccepted, testSEC001_fieldNameStartingWithDigitRejected, testSEC001_fieldNameWithDashRejected, etc. (5 cases) | Unit | Implemented |
| SEC-002: Path traversal | PluginRunnerTest | path traversal test | Unit | Implemented |
| SEC-002 | SafetySecurityTest | testSEC002_normalPackageProducesValidPath, testSEC002_pathTraversalCheckWorks | Unit | Implemented |
| SEC-003: Keyword escaping | JavaCodeGenTest | testKeywordEscaping | Unit | Implemented |
| SEC-003 | MultiLanguageCodeGenTest | testKeywordEscaping (x16 langs) | Unit | Implemented |
| SEC-003 | SafetySecurityTest | testSEC003_keywordEscapingInGeneratedCode (x17 langs) + language-specific tests | Unit | Implemented |
| SEC-004: Collision detection | JavaCodeGenTest | testNameCollisionDetection | Unit | Implemented |
| SEC-004 | SafetySecurityTest | testSEC004_fooBarCollisionDetected, testSEC004_doubleUnderscoreCollision | Unit | Implemented |

### Performance Requirements

| Requirement | Test File(s) | Test Case(s) | Type | Status |
|-------------|-------------|--------------|------|--------|
| PERF-001: Plugin execution | PerfTest.java (manual) | wall-clock timing | Benchmark | Manual |
| PERF-002: Serialization perf | PerfTest.java (manual) | ops/sec measurement | Benchmark | Manual |

### Interface Requirements

| Requirement | Test File(s) | Test Case(s) | Type | Status |
|-------------|-------------|--------------|------|--------|
| IF-001: Plugin protocol | PluginRunnerTest | all 22 tests | Unit | Implemented |
| IF-002: Language parameter | PluginRunnerTest | testDefaultLanguage, testAll24LanguageEntries | Unit | Implemented |
| IF-003: Generated Java API | JavaCodeGenTest | testConvenienceMethods, testObjectMapperCaching | Unit | Implemented |

## Coverage Summary

| Category | Requirements | Test Cases (implemented) | Test Cases (draft) | Total |
|----------|-------------|----------------------|----------------------|-------|
| Functional (FR) | 18 | ~660 | ~2 (FR-016, FR-018 fault test) | ~662 |
| Safety (SR) | 4 | ~48 | 0 | ~48 |
| Security (SEC) | 4 | ~48 | 0 | ~48 |
| Performance (PERF) | 2 | 0 (manual only) | 0 | 0 |
| Interface (IF) | 3 | ~25 | 0 | ~25 |
| **Total** | **31** | **~911** | **~2** | **1059** |

## Test Files

| File | Purpose | Tests | Status |
|------|---------|-------|--------|
| CodeWriterTest.java | Unit: indentation, blocks, formatting | 14 | Implemented |
| MessageAnalyzerTest.java | Unit: proto analysis, fields, oneofs, maps, WKTs | 37 | Implemented |
| TypeRegistryTest.java | Unit: type catalog, nested types, map entries | 14 | Implemented |
| WellKnownTypeTest.java | Unit: WKT detection and classification | 3 | Implemented |
| JavaNameResolverTest.java | Unit: Java naming conventions | 5 | Implemented |
| JavaTypeMapperTest.java | Unit: all 15 scalar type mappings | 61 | Implemented |
| PluginRunnerTest.java | Unit+Integration: orchestrator, params, errors | 22 | Implemented |
| IndexingAuditTest.java | Unit: position correctness, boundaries | 12 | Implemented |
| JavaCodeGenTest.java | E2E: Java code generation (all constructs) | 71 | Implemented |
| MultiLanguageCodeGenTest.java | E2E: 16 non-Java languages (15 @ParameterizedTest x 16 langs = 240 invocations) | 240 | Implemented |
| PerformanceBenchmarkTest.java | Performance: plugin throughput benchmarks | 8 | Implemented |
| MemoryBenchmarkTest.java | Performance: memory allocation benchmarks | 4 | Implemented |
| PbtkJavaCodeGenTest.java | E2E: pbtk URL format Java code generation | 27 | Implemented |
| PbtkMultiLanguageCodeGenTest.java | E2E: pbtk URL format across 16 non-Java languages | 144 | Implemented |
| PbtkSafetySecurityTest.java | Safety/security tests for pbtk format | 70 | Implemented |
| SafetySecurityTest.java | Safety/security/fault injection (39 @Test + 9 @ParameterizedTest = 180 invocations) | 180 | Implemented |
| GoldenFileTest.java | Snapshot: exact output comparison | 17 | Implemented |
| SchemaEvolutionTest.java | Schema evolution: forward/backward compatibility across languages | 119 | Implemented |
| JavaSchemaEvolutionTest.java | Schema evolution: Java-specific round-trip verification | 11 | Implemented |

## Missing Coverage

| Requirement | Gap | Reason | Mitigation |
|-------------|-----|--------|------------|
| PERF-001/002 | No automated benchmark | Benchmarks are flaky in CI | Manual execution; tracked in BUG_CATALOG.md |
| FR-012 (7 langs) | Only Java↔Python cross-language tested | Requires toolchains for Go, JS, etc. | Pattern tests provide partial coverage |
| HAZ-006 | No C dynamic analysis | Requires Valgrind/ASan | Static review performed |
| HAZ-008 | Proto2 defaults in 16 non-Java langs | Only Java audited | Flagged in HAZARD_ANALYSIS.md |

## Execution Requirements

| Test Type | Command | Dependencies |
|-----------|---------|--------------|
| All unit tests | `./gradlew :plugin:test` | Java 17 |
| With coverage | `./gradlew :plugin:test :plugin:jacocoTestReport` | Java 17 |
| Specific class | `./gradlew :plugin:test --tests "*.SafetySecurityTest"` | Java 17 |
| Integration | `bash integration-tests/cross-language-test.sh` | Java 17, protoc, python3 |
| Schema evolution | `bash integration-tests/schema-evolution-test.sh` | Java 17, protoc, python3 |
| Golden file update | `./gradlew :plugin:test -Dupdate.golden=true` | Java 17 |
| Formatting check | `./gradlew spotlessCheck` | Java 17 |

## Approval

Test traceability requires review:
- [ ] All requirements have corresponding tests
- [ ] Test logic is correct (verified by execution)
- [ ] Coverage gaps are acceptable
- [ ] Traceability is complete and accurate

Reviewer: _________________________ Date: ____________
