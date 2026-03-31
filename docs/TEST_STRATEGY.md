# Test Strategy

================================================================================
Generated: 2026-03-24T22:00:00-04:00
Model: Claude Opus 4.6 (1M context)
Review Status: PENDING
Reviewed By: _______________
================================================================================

```
LIMITATIONS:
- This is a DRAFT test strategy requiring human validation
- Cannot predict actual coverage without execution
- Strategy effectiveness depends on human judgment
- Runtime behavior cannot be fully predicted from static analysis
- Integration test strategies may miss environmental factors
```

## 1. Strategy Overview

### 1.1 Objectives

1. **Prevent silent data corruption** (HAZ-001, HAZ-002) — the highest-risk failure mode where generated code compiles but produces wrong serialization at runtime
2. **Ensure cross-language interoperability** (HAZ-003) — JSON produced by any language's generated code must be deserializable by all other languages
3. **Verify complete proto IDL coverage** — all 15 scalar types, enums, nested messages, repeated, maps, oneofs, optional, proto2, well-known types
4. **Validate security boundaries** (HAZ-004, HAZ-005) — field name injection, path traversal, keyword escaping
5. **Achieve high coverage** on critical-path components (MessageAnalyzer, TypeMappers, Serializers, Deserializers)

### 1.2 Scope

- **In Scope:**
  - Plugin core (MessageAnalyzer, TypeRegistry, PluginRunner, ProtoFileProcessor)
  - All 17 language generators (code generation pattern verification)
  - Java generated code (compilation + execution + round-trip)
  - Python generated code (execution + round-trip)
  - Cross-language compatibility (Java ↔ Python)
  - Schema evolution (forward/backward compatibility)
  - Security validations (input sanitization, path traversal)
  - Performance baselines (plugin throughput, generated code throughput)

- **Out of Scope:**
  - Compilation of generated C/C++/Rust/Zig/Go/C#/Kotlin/Swift/Dart/PHP/Ruby/ObjC/Perl code (requires target-language toolchains not guaranteed in CI) — mitigated by code pattern assertions
  - Dynamic memory analysis of generated C code (requires Valgrind/ASan setup) — flagged in HAZARD_ANALYSIS.md
  - Generated code performance benchmarks for non-Java languages
  - protoc version compatibility matrix testing
  - Zig standard library API stability testing

### 1.3 Approach

- **Test Framework:** JUnit 5 (Jupiter) with `@ParameterizedTest` for multi-language coverage
- **Coverage Tool:** JaCoCo (integrated via Gradle plugin)
- **Formatting:** Spotless with Google Java Format (enforced in CI)
- **Test Environment:** Java 17+, protoc on PATH, Python 3 (for integration tests)

## 2. Existing Test Analysis

### 2.1 Current State

| Metric | Value | Assessment |
|--------|-------|------------|
| Test files | 17 classes | Adequate for current scope |
| Total tests | 929 | Good — comprehensive for core + all 17 languages |
| Instruction coverage | 74.0% | Good overall; gaps in non-Java generators |
| Line coverage | 76.5% | Good overall |
| Integration tests | 2 shell scripts (9 assertions) | Minimal but effective |
| Cross-language tests | Java ↔ Python only | Insufficient for 17-language claim |

### 2.2 Coverage by Criticality

| Criticality | Components | Current Coverage | Target | Gap |
|-------------|-----------|-----------------|--------|-----|
| Critical | MessageAnalyzer | 96.9% | 100% | 3.1% |
| Critical | TypeRegistry | 100% | 100% | None |
| Critical | Java Serializer/Deserializer | 87.6% | 95% | 7.4% |
| Critical | Other 16 Serializer/Deserializer generators | 66-81% | 90% | 9-24% |
| High | PluginRunner | 93.3% | 95% | 1.7% |
| High | ProtoFileProcessor | 95.5% | 95% | None |
| High | All NameResolvers | 42-80% | 85% | Variable |
| Medium | KeywordUtil | 98.7% | 90% | None (exceeds) |
| Medium | WellKnownType | 100% | 90% | None (exceeds) |
| Low | CodeWriter | 100% | 80% | None (exceeds) |

### 2.3 Gap Analysis

| Requirement | Current Coverage | Gap |
|-------------|-----------------|-----|
| FR-001 (Positional encoding) | Full — 12 indexing tests + 71 codegen tests | None |
| FR-002 (Gap handling) | Full — IndexingAuditTest | None |
| FR-003 (Scalar types) | Full for Java (61 type mapper + 15 codegen); partial for others | Round-trip execution tests for non-Java |
| FR-004 (Nested messages) | Pattern assertion only | No compilation/execution test |
| FR-005 (Repeated fields) | Pattern assertion only | No empty-list round-trip test for all langs |
| FR-006 (Map fields) | Pattern assertion only | No non-string-key round-trip test |
| FR-007 (Enum encoding) | Pattern assertion | No round-trip test across languages |
| FR-008 (Oneof encoding) | Pattern assertion | No round-trip test |
| FR-009 (Proto3 optional) | Pattern assertion for Java | No cross-language presence test |
| FR-010 (Proto2) | PluginRunnerTest + codegen pattern tests | No proto2 round-trip execution test |
| FR-011 (Multi-language) | 240 parameterized pattern tests | No compilation verification for 15 of 17 languages |
| FR-012 (Cross-language interop) | Java ↔ Python only | Missing 15 other languages |
| FR-013 (Cross-file refs) | Pattern assertion per language | No import resolution verification |
| FR-014 (Plugin protocol) | 22 PluginRunner tests | Adequate |
| FR-015 (Error reporting) | Tested in PluginRunnerTest | Adequate |
| FR-016 (Version) | Manual verification | No automated test |
| FR-017 (Well-known types) | 3 WKT tests + codegen pattern | No WKT round-trip test |
| FR-018 (Any rejection) | MessageAnalyzerTest + codegen test | Adequate |
| SR-001 (Position correctness) | 12 IndexingAuditTest | Adequate |
| SR-002 (Type consistency) | 120 MultiLanguageCodeGenTest | Pattern-only; no execution |
| SR-003 (int64 precision) | 4 codegen pattern tests | No cross-language precision test |
| SR-004 (NaN/Infinity) | 2 codegen pattern tests | No round-trip test |
| SEC-001 (Field name validation) | MessageAnalyzerTest | Adequate |
| SEC-002 (Path traversal) | PluginRunnerTest | Adequate |
| SEC-003 (Keyword escaping) | Codegen tests per language | Adequate |
| SEC-004 (Collision detection) | Codegen test | Adequate |

## 3. Test Levels

### 3.1 Unit Testing

**Scope:** All plugin source classes

**Approach:**
- Build proto descriptors programmatically using protobuf-java builders (no disk I/O, no protoc dependency)
- Assert generated source code contains expected patterns
- Test model classes with direct construction and method calls
- No mocking needed — system has no external dependencies

**Coverage Targets:**

| Component | Criticality | Line Target | Branch Target |
|-----------|-------------|-------------|---------------|
| MessageAnalyzer | Critical | 100% | 95% |
| TypeRegistry | Critical | 100% | 100% |
| All TypeMappers (17) | Critical | 95% | 90% |
| All SerializerGenerators (17) | Critical | 90% | 85% |
| All DeserializerGenerators (17) | Critical | 90% | 85% |
| PluginRunner | High | 95% | 90% |
| ProtoFileProcessor | High | 95% | 90% |
| All NameResolvers (17) | High | 85% | 80% |
| All CodeEmitters (17) | High | 85% | 80% |
| KeywordUtil | Medium | 90% | 80% |
| WellKnownType | Medium | 100% | 100% |
| CodeWriter | Low | 80% | N/A |
| ProtoField/Message/Enum/File | Low | 80% | N/A |

### 3.2 Integration Testing

**Scope:** Plugin-to-protoc protocol; generated code compilation and execution

**Approach:**
- Shell scripts invoking `protoc` with the plugin for all 17 languages
- Java: compile generated code with `javac`, execute round-trip tests
- Python: execute generated code with `python3`, verify deserialization

**Key Integration Points:**

| Interface | Test Approach | Priority |
|-----------|---------------|----------|
| protoc -> plugin (stdin/stdout) | Shell: `protoc --plugin=... --jsonarray_out=...` for all 17 langs | Critical |
| Generated Java compilation | `javac` on generated code + Jackson JARs | Critical |
| Generated Java round-trip | Execute serialize→deserialize→compare in Java | Critical |
| Generated Python round-trip | Execute serialize→deserialize→compare in Python | High |
| Java ↔ Python cross-language | Serialize in Java, deserialize in Python (and vice versa) | High |
| Schema evolution | v1 serialize → v2 deserialize (forward compat) | High |
| Schema evolution | v2 serialize → v1 deserialize (backward compat) | High |

### 3.3 System Testing

**Scope:** End-to-end user workflows

**Approach:**
- Simulate real user workflow: write .proto → run protoc → compile generated code → serialize → deserialize → verify
- Use realistic proto files (kitchen_sink.proto, edge_cases.proto)

**Scenarios:**
1. Simple message (User/Address) full pipeline
2. Complex message (KitchenSink with all field types)
3. Proto2 message with defaults
4. Multi-file proto with cross-references
5. Edge cases (empty message, self-referential, deeply nested)

### 3.4 Acceptance Testing

**Scope:** Verify the motivating example from the project README

**Acceptance Criterion:** Given the User/Address proto from README, serialization produces exactly:
```json
["Alice","Smith",30,["123 Main Street","Springfield","IL",62704]]
```

## 4. Specialized Testing

### 4.1 Concurrency Testing
- **Applicability:** N/A
- **Rationale:** Plugin is entirely single-threaded. No shared mutable state. Generated code is documented as not thread-safe (standard protobuf pattern).

### 4.2 Security Testing
- **Applicability:** ADAPTED
- **Rationale:** Narrow trust boundary (stdin from protoc). Defense-in-depth present.
- **Approach:** Unit tests with crafted proto descriptors containing:
  - Field names with special characters (rejected by regex)
  - Package names with `..` (rejected by path check)
  - Language keywords as identifiers (escaped by KeywordUtil)
  - Duplicate field names after case conversion (detected by collision check)
- **Focus Areas:** HAZ-004 (code injection), HAZ-005 (path traversal)

### 4.3 Performance Testing
- **Applicability:** ADAPTED
- **Rationale:** Plugin invoked once per build; performance not critical. Generated code performance matters more.
- **Approach:** Benchmark tests measuring:
  - Plugin: wall-clock time for kitchen_sink.proto across all 17 languages
  - Generated Java: serialize/deserialize ops/sec for small and large messages
- **Targets:** FR-001/PERF-001: < 5 seconds for typical proto; PERF-002: < 10μs/op for small messages

### 4.4 Fault Injection
- **Applicability:** ADAPTED
- **Rationale:** Plugin should handle malformed input gracefully.
- **Approach:** Unit tests with:
  - Empty CodeGeneratorRequest
  - Proto file with no messages
  - Message with no fields
  - Field number 0 and negative numbers
  - Extremely large field numbers
  - Unknown language parameter
  - Proto2 file with extensions
- **Scenarios:** All should produce clear error responses (not crashes)

### 4.5 Property-Based Testing
- **Applicability:** Consider for future
- **Rationale:** The serialization round-trip invariant (`deserialize(serialize(X)) == X`) is a natural property. Could use frameworks like jqwik.
- **Candidates:**
  - `∀ message M: deserialize(serialize(M)) == M` (for all field types)
  - `∀ fields F1, F2: if F1.number ≠ F2.number, then F1.position ≠ F2.position`
  - `∀ language L1, L2: serialize_L1(M) ≈ serialize_L2(M)` (semantic JSON equality)

## 5. Coverage Strategy

### 5.1 Coverage Targets by Criticality

| Criticality | Components | Line Target | Branch Target | MC/DC |
|-------------|-----------|-------------|---------------|-------|
| Critical | MessageAnalyzer, TypeRegistry, all TypeMappers, all Serializer/DeserializerGenerators | 95% | 90% | Recommended |
| High | PluginRunner, ProtoFileProcessor, all NameResolvers, all CodeEmitters | 90% | 85% | Optional |
| Medium | KeywordUtil, WellKnownType, runtime libraries | 90% | 80% | N/A |
| Low | CodeWriter, model classes | 80% | N/A | N/A |

### 5.2 Coverage Exclusions

| Path/Pattern | Reason |
|--------------|--------|
| `Main.java` (stdin/stdout I/O) | Entry point I/O — tested via integration tests, not unit tests |
| `toString()` methods in model classes | Cosmetic debug output |
| `FieldCodecs.java` reflection paths | Depends on actual protobuf well-known type classes at runtime |

### 5.3 Coverage Gaps (Known)

| Area | Gap | Mitigation |
|------|-----|------------|
| Generated non-Java/Python compilation | Cannot compile in standard Java CI | Code pattern assertions verify syntactic correctness |
| Generated code execution (15 of 17 languages) | Only Java and Python executed | Pattern tests + cross-language round-trip for Java/Python |
| C runtime dynamic memory analysis | No Valgrind/ASan | Static review; future CI enhancement |
| Proto2 defaults in non-Java generators | Only Java audited | [INCOMPLETE_ANALYSIS] — needs audit |

## 6. Requirements Traceability

| Requirement | Test Level | Test Cases | Status |
|-------------|-----------|------------|--------|
| FR-001 | Unit | IndexingAuditTest (12), JavaCodeGenTest.testScalar* | Implemented |
| FR-002 | Unit | IndexingAuditTest.testSparseFieldGaps*, MultiLang.testFieldNumberGaps | Implemented |
| FR-003 | Unit | JavaTypeMapperTest (61), JavaCodeGenTest (15 scalar), MultiLang.testScalar* | Implemented |
| FR-004 | Unit | JavaCodeGenTest.testNestedMessage*, MultiLang.testNestedMessage | Implemented |
| FR-005 | Unit | JavaCodeGenTest.testRepeatedField*, MultiLang.testRepeatedField | Implemented |
| FR-006 | Unit | JavaCodeGenTest.testMapField*, MultiLang.testMapField | Implemented |
| FR-007 | Unit | JavaCodeGenTest.testEnumSerialization, MultiLang.testEnumGeneration | Implemented |
| FR-008 | Unit | JavaCodeGenTest.testOneofCaseTracking, MultiLang.testOneofTracking | Implemented |
| FR-009 | Unit | JavaCodeGenTest.testProto3OptionalPresence, MultiLang.testOptionalPresence | Implemented |
| FR-010 | Unit | JavaCodeGenTest.testProto2*, PluginRunnerTest.testProto2Support | Implemented |
| FR-011 | Unit + Integration | MultiLanguageCodeGenTest (240), CI 17-language smoke test | Implemented |
| FR-012 | Integration | cross-language-test.sh (5 assertions) | Implemented (Java↔Python only) |
| FR-013 | Unit | MultiLang.testCrossFileReference | Implemented |
| FR-014 | Unit | PluginRunnerTest (22 tests) | Implemented |
| FR-015 | Unit | PluginRunnerTest.testUnsupportedLanguage, error tests | Implemented |
| FR-016 | Manual | `./protoc-gen-jsonarray --version` | Not automated |
| FR-017 | Unit | WellKnownTypeTest (3), JavaCodeGenTest.testWellKnownType* | Implemented |
| FR-018 | Unit | JavaCodeGenTest.testAnyRejection, MessageAnalyzerTest | Implemented |
| SR-001 | Unit | IndexingAuditTest (12 tests) | Implemented |
| SR-002 | Unit | MultiLanguageCodeGenTest (240 tests) | Implemented |
| SR-003 | Unit | JavaCodeGenTest.testInt64* (4 tests) | Implemented |
| SR-004 | Unit | JavaCodeGenTest.testNaN* (2 tests) | Implemented |
| SEC-001 | Unit | MessageAnalyzerTest field name tests | Implemented |
| SEC-002 | Unit | PluginRunnerTest path traversal test | Implemented |
| SEC-003 | Unit | JavaCodeGenTest.testKeyword*, MultiLang.testKeyword* | Implemented |
| SEC-004 | Unit | JavaCodeGenTest.testNameCollision* | Implemented |
| PERF-001 | Benchmark | PerfTest.java (manual) | Manual only |
| PERF-002 | Benchmark | PerfTest.java (manual) | Manual only |

## 7. Test Data Strategy

### 7.1 Data Categories

| Category | Examples | Source |
|----------|----------|--------|
| Nominal | User{firstname:"Alice", age:30, address:Address{...}} | Programmatic builders in test code |
| Boundary | int32: 0, 2147483647, -2147483648; string: "", 10KB string; bytes: empty, 1-byte | Programmatic builders |
| Invalid | Field number 0, field number -1, `google.protobuf.Any` field, `..` in package | Programmatic builders |
| Edge cases | Empty message, self-referential, sparse fields (1, 100000), circular refs | Proto builders + test protos |
| Proto2 specific | Required fields, optional with defaults, groups, extensions | proto2_test.proto + builders |
| Cross-language | Identical messages constructed in Java and Python | Test programs in each language |

### 7.2 Data Management

- **Location:** Test data is constructed programmatically in test methods (no external files)
- **Proto fixtures:** `test-protos/src/main/proto/` (5 files for integration tests)
- **Format:** `DescriptorProto.newBuilder()` / `FieldDescriptorProto.newBuilder()` chains
- **Sensitive Data:** None — all test data is synthetic

## 8. Test Architecture

### 8.1 Directory Structure

```
plugin/src/test/java/dev/protocgen/textcodecs/jsonarray/
├── CodeWriterTest.java              # Unit: CodeWriter utility
├── MessageAnalyzerTest.java         # Unit: proto analysis (37 tests)
├── TypeRegistryTest.java            # Unit: type catalog (14 tests)
├── WellKnownTypeTest.java           # Unit: WKT detection (3 tests)
├── JavaNameResolverTest.java        # Unit: Java naming (5 tests)
├── JavaTypeMapperTest.java          # Unit: Java type mapping (61 tests)
├── PluginRunnerTest.java            # Unit+Integration: orchestrator (22 tests)
├── IndexingAuditTest.java           # Unit: position correctness (12 tests)
├── JavaCodeGenTest.java             # E2E: Java code generation (71 tests)
├── MultiLanguageCodeGenTest.java    # E2E: 16 non-Java languages (15 parameterized x 16 = 240 tests)
├── SafetySecurityTest.java          # Safety/security/fault injection (180 tests)
├── GoldenFileTest.java              # Snapshot: exact output comparison
├── PerformanceBenchmarkTest.java    # Plugin throughput benchmarks
├── MemoryBenchmarkTest.java         # Memory allocation benchmarks
└── ../pbtkurl/
    ├── PbtkJavaCodeGenTest.java     # E2E: pbtk Java code generation
    ├── PbtkMultiLanguageCodeGenTest.java  # E2E: pbtk 16 non-Java languages
    └── PbtkSafetySecurityTest.java  # Safety/security tests for pbtk format

integration-tests/
├── cross-language-test.sh           # System: Java ↔ Python round-trip
├── schema-evolution-test.sh         # System: forward/backward compat
├── java/UserRoundTripTest.java      # System: Java serialize/deserialize
└── python/user_round_trip_test.py   # System: Python serialize/deserialize

test-protos/src/main/proto/
├── user.proto                       # Simple message with nested type
├── address.proto                    # Simple 4-field message
├── kitchen_sink.proto               # All proto3 field types (29 fields)
├── edge_cases.proto                 # Edge cases (17 messages)
└── proto2_test.proto                # Proto2 features
```

### 8.2 Naming Conventions

- Test classes: `*Test.java` (JUnit 5 convention)
- Test methods: `test[Feature][Scenario]` (e.g., `testScalarInt64SerializedAsString`)
- Parameterized: `@ParameterizedTest(name = "{0}")` with `@MethodSource`
- Helpers: `private` methods within test class (e.g., `generate(msg, lang)`, `scalarField(name, num, type)`)

### 8.3 Test Patterns

- **Setup:** `private final PluginRunner runner = new PluginRunner();` — shared instance (stateless)
- **Proto construction:** Builder pattern: `DescriptorProto.newBuilder().setName("Msg").addField(...).build()`
- **Generation:** `runner.run(request)` → `response.getFile(0).getContent()` → string assertions
- **Assertions:** `assertTrue(code.contains("expected_pattern"))` with custom `assertContainsAny` for multi-pattern OR logic
- **Mocking:** None — system has no external dependencies to mock
- **Fixtures:** None — all test data constructed inline

## 9. Test Execution

### 9.1 Local Execution

```bash
# Run all unit tests
./gradlew :plugin:test

# Run with coverage report
./gradlew :plugin:test :plugin:jacocoTestReport

# Run specific test class
./gradlew :plugin:test --tests "dev.protocgen.textcodecs.jsonarray.JavaCodeGenTest"
  
# Run integration tests (requires protoc + python3 on PATH)
bash integration-tests/cross-language-test.sh
bash integration-tests/schema-evolution-test.sh

# Format check
./gradlew spotlessCheck
```

### 9.2 CI/CD Integration

- **Trigger:** Push to main, all pull requests
- **Pipeline:** `.github/workflows/ci.yml`
- **Steps:**
  1. Checkout → Setup Java 17 → Setup Gradle
  2. `spotlessCheck` (formatting gate)
  3. `:plugin:shadowJar` (build)
  4. `:plugin:test` (all 929 unit tests)
  5. Install protoc
  6. Generate code for all 17 languages (smoke test)
  7. Upload shadow JAR as artifact
- **Parallelization:** None (single job, tests run fast — ~2 seconds)
- **Failure Handling:** Any test failure blocks merge

## 10. Quality Gates

| Gate | Criteria | Enforced By |
|------|----------|-------------|
| All tests pass | 100% pass rate (929/929) | CI: `./gradlew :plugin:test` |
| Code formatting | Google Java Style | CI: `./gradlew spotlessCheck` |
| Coverage minimum | 74.0% instructions (current baseline) | CI: JaCoCo report + manual review |
| 17-language generation | All 17 `protoc --jsonarray_out=lang=X` succeed | CI: smoke test |
| Cross-language round-trip | Java and Python produce/consume identical JSON | CI: `cross-language-test.sh` |
| Schema evolution | Forward and backward compat pass | CI: `schema-evolution-test.sh` |
| No regressions | No existing test failures | CI: zero-failure policy |

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Generated code correctness only verified by pattern matching, not execution | Silent bugs in 15 of 17 languages undetectable | Extend execution tests to more languages; add golden-file snapshots |
| No non-Java/Python compilation in CI | Generated code may have syntax errors | Pattern tests catch most issues; add optional compilation jobs |
| JaCoCo cannot measure generated code coverage | Generated code paths untested | Integration tests with Java/Python execution |
| Property-based testing not yet implemented | Edge cases in serialization may be missed | Consider jqwik for round-trip properties |
| No mutation testing | Test quality unknown — tests may pass on wrong code | Consider PIT mutation testing for critical components |
| Integration tests depend on external tools | protoc/python3 must be on PATH | CI installs them; documented in CONTRIBUTING.md |

## 12. Recommended Test Improvements (Priority Order)

| Priority | Improvement | Effort | Impact | Addresses |
|----------|------------|--------|--------|-----------|
| ~~1~~ | ~~Golden-file snapshot tests for all 17 languages~~ **IMPLEMENTED** (GoldenFileTest.java) | -- | -- | HAZ-001, HAZ-002 |
| 2 | Automated `--version` test | Low | Low — completes FR-016 coverage | FR-016 |
| 3 | Round-trip execution tests for Go (using `go run`) | Medium | High — Go is the 3rd most popular target | FR-012, HAZ-003 |
| 4 | Property-based testing for round-trip invariant | Medium | High — discovers edge cases automatically | SR-001, SR-002 |
| 5 | Proto2 default escaping audit for all 16 non-Java generators | Medium | Medium — closes HAZ-008 | HAZ-008 |
| 6 | Performance regression benchmarks in CI | Low | Low — prevents accidental slowdowns | PERF-001 |
| 7 | Mutation testing on MessageAnalyzer + TypeMappers | High | High — verifies test quality | HAZ-001, HAZ-002 |
| 8 | C compilation + Valgrind in CI (optional job) | High | Medium — addresses HAZ-006 | HAZ-006 |

## 13. Uncertainties

| Flag | Area | Resolution Needed |
|------|------|-------------------|
| [ASSUMED_BEHAVIOR] | Pattern-matching tests assume string containment implies correctness | Golden-file tests would provide exact output verification |
| [INCOMPLETE_ANALYSIS] | 15 of 17 languages have no compilation or execution tests | Requires target-language toolchains in CI |
| [ASSUMED_BEHAVIOR] | Cross-language compatibility verified only for Java↔Python | Should extend to at least Go and JavaScript |
| [EXTERNAL_DEPENDENCY] | Integration tests require protoc and python3 on PATH | CI workflow installs these; local dev must install manually |

## 14. Approval

Test strategy requires review:
- [ ] Coverage targets are appropriate for criticality levels
- [ ] All requirements have test mapping
- [ ] Specialized testing decisions (N/A for concurrency, ADAPTED for security/performance) are correct
- [ ] Test architecture is practical and maintainable
- [ ] Quality gates are achievable
- [ ] Recommended improvements are prioritized correctly
- [ ] Resources/timeline are feasible

Reviewer: _________________________ Date: ____________
