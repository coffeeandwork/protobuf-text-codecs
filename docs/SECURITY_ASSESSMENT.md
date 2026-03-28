# Security Assessment

================================================================================
Generated: 2026-03-25T02:00:00-04:00 (updated after security fixes)
Model: Claude Opus 4.6 (1M context)
Review Status: PENDING
Reviewed By: _______________
================================================================================

## IMPORTANT LIMITATIONS

This assessment is:
- Based on static code analysis only
- INCOMPLETE — many vulnerability classes cannot be detected statically
- NOT a substitute for professional penetration testing
- NOT a substitute for dynamic application security testing (DAST)
- Findings require validation with proper security tools

Vulnerability classes NOT covered:
- Business logic flaws (partially covered)
- Timing attacks
- Side-channel attacks
- Runtime-dependent vulnerabilities
- Third-party CVEs (requires SCA tool — see Section 6)

## 1. Attack Surface Summary

### Entry Points

| Entry Point | Type | Auth | Data Handled | Risk Level |
|-------------|------|------|--------------|------------|
| stdin (CodeGeneratorRequest) | CLI/protoc plugin protocol | N/A | Proto file descriptors, field names, type names, package names, default values, source comments | High |
| `--version` flag | CLI argument | N/A | None (read-only) | Info |
| `lang=` parameter | Plugin parameter | N/A | Language identifier string | Low |

### Trust Boundaries

| Boundary | Location | Protection | Assessment |
|----------|----------|------------|------------|
| stdin parsing | `Main.java:24` | protobuf-java wire format validation | Adequate for well-formed input |
| Field names → generated code | `MessageAnalyzer.java:179` | Regex `[a-zA-Z_][a-zA-Z0-9_]*` | **Adequate** |
| Message/enum names → generated code | `MessageAnalyzer.java:67,142` | Regex `[a-zA-Z_][a-zA-Z0-9_]*` + keyword escaping | **Fixed** (VULN-001, commit 44a0363) |
| Type references → generated code | `TypeRegistry.registerFile()` | Validated at registration time | **Fixed** (VULN-002, commit 44a0363) |
| Default values → generated code | `schemaDefaultExpression()` | String escaping + bool/numeric/enum regex validation | **Fixed** (VULN-003, commit 44a0363) |
| Source comments → generated code | `JavaCodeEmitter.emitDocComment()` | `*/` escaped to `* /` | **Fixed** (VULN-005, commit 44a0363) |
| Package names → file paths | `PluginRunner.java:121` | Rejects `..`, `/`, `\0` | **Fixed** (VULN-004, commit 44a0363) |
| Language parameter | `PluginRunner.java:64` | Allowlist | Adequate |

### Key Trust Assumption

The plugin trusts that `protoc` provides valid, spec-compliant `CodeGeneratorRequest` messages. All High-severity findings require a **crafted CodeGeneratorRequest bypassing protoc** to exploit, which requires the attacker to already have local code execution. All validations are defense-in-depth.

## 2. STRIDE Analysis

### Spoofing
| Asset | Threat | Control Present | Gap |
|-------|--------|-----------------|-----|
| N/A — no user identity | N/A | N/A | None |

Not applicable. The plugin has no authentication, authorization, or user identity concept. It is a local build tool.

### Tampering
| Data | Threat | Control Present | Gap |
|------|--------|-----------------|-----|
| CodeGeneratorRequest | Crafted proto descriptors with malicious names/defaults | Field name regex; keyword escaping | Message names, type refs, default values partially unvalidated (VULN-001, 002, 003) |
| Generated source code | Plugin produces injectable code | Defense-in-depth validations | Same gaps as above |

### Repudiation
| Action | Logged | Attribution | Gap |
|--------|--------|-------------|-----|
| Code generation | Warnings to stderr | `@generated` marker in output | None meaningful — build tool, not auditable service |

### Information Disclosure
| Data | Exposure Risk | Protection | Gap |
|------|---------------|------------|-----|
| Proto schema structure | Visible in generated source code | None (by design — output IS the schema) | None (expected behavior) |
| Error messages | May reveal internal paths | Error prefix `protoc-gen-jsonarray:` | Acceptable — build-time tool |

### Denial of Service
| Resource | Attack Vector | Protection | Gap |
|----------|---------------|------------|-----|
| JVM memory | Very sparse field numbers (e.g., field 536870911) → huge generated arrays | Warning emitted; no hard limit | Low risk (VULN-006) |
| JVM stack | Deeply nested messages → recursive `analyze()` | No depth limit | Low risk — protoc limits nesting |
| C runtime memory | Malformed JSON with huge array sizes → calloc overflow | No bounds check in generated C code | Medium risk (VULN-007) |

### Elevation of Privilege
| Role | Escalation Path | Control | Gap |
|------|-----------------|---------|-----|
| Build-time tool | Code injection via generated source → arbitrary code execution in user's application | Field name validation, keyword escaping | Message name + type ref + default value injection (VULN-001, 002, 003) |

## 3. Vulnerability Findings

### VULN-001: Message/enum names not validated — code injection via crafted CodeGeneratorRequest

- **Severity:** High
- **Category:** CWE-94 (Improper Control of Generation of Code)
- **Location:** `MessageAnalyzer.java:66` (message names used without validation)
- **Description:** Field names are validated against `[a-zA-Z_][a-zA-Z0-9_]*` at line 91, but message names from `descriptor.getName()` are not. Message names flow directly into generated class/struct/type declarations in all 9 languages.
- **Evidence:**
```java
// MessageAnalyzer.java:66 — message name used without validation
String fullName = parentFullName + descriptor.getName();
// JavaCodeEmitter.java:48 — emitted into generated Java
w.block("public class " + className, () -> { ... });
```
- **Risk:** A crafted `CodeGeneratorRequest` (bypassing protoc) with a message named `Foo { } class Evil { static { Runtime.getRuntime().exec("evil"); } } class Dummy` would produce compilable Java containing arbitrary code. Exploitation requires local code execution to craft the request.
- **Recommendation:** Add `descriptor.getName().matches("[a-zA-Z_][a-zA-Z0-9_]*")` validation in the `analyze()` method.
- **Status:** **Fixed** (commit 44a0363)

### VULN-002: `simpleTypeName()` returns unsanitized type segment — code injection

- **Severity:** High
- **Category:** CWE-94 (Code Injection)
- **Location:** `JavaSerializerGenerator.java:356`, `JavaDeserializerGenerator.java:304`, `JavaTypeMapper.java:165`, `PythonDeserializerGenerator.java:246` (and equivalents in all 9 generators)
- **Description:** `simpleTypeName()` extracts the substring after the last `.` in a type reference and injects it into generated code (type casts, static method calls). If the last segment contains `()`, `;`, `{}`, the generated code becomes injectable.
- **Evidence:**
```java
// simpleTypeName extracts last segment
return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
// Used in: "%s.deserialize(...)" — if segment is "Evil(); //" this becomes "Evil(); //.deserialize(...)"
```
- **Risk:** Same as VULN-001 — requires crafted CodeGeneratorRequest.
- **Recommendation:** Validate return value against `[a-zA-Z_][a-zA-Z0-9_]*` or validate at `TypeRegistry.registerFile()` time.
- **Status:** **Fixed** (commit 44a0363)

### VULN-003: Proto2 default values for bool/numeric/enum types emitted without validation

- **Severity:** High
- **Category:** CWE-94 (Code Injection)
- **Location:** `JavaDeserializerGenerator.java:277,262,300` and `JavaTypeMapper.java:94,68,117`
- **Description:** Proto2 schema default values for `TYPE_BOOL` and integer types are emitted verbatim into generated code. The `TYPE_STRING` case properly escapes, but bool (`true`/`false`) and numeric defaults are not validated.
- **Evidence:**
```java
case TYPE_BOOL -> defaultValue; // raw string, could be "true; evil();//"
default -> defaultValue;        // raw string for int32 etc.
```
- **Risk:** A crafted default value like `true; Runtime.getRuntime().exec("evil");//` in a proto2 bool field produces injectable Java code.
- **Recommendation:** Validate: bool → `^(true|false)$`; integers → `^-?[0-9]+$`; enum constants → `^[A-Z_][A-Z0-9_]*$`.
- **Status:** **Fixed** (commit 44a0363)

### VULN-004: Path traversal check incomplete — absolute paths not rejected

- **Severity:** Medium
- **Category:** CWE-22 (Path Traversal)
- **Location:** `PluginRunner.java:116`
- **Description:** The path check rejects `..` but not absolute paths (`/etc/cron.d/Evil.java`) or null bytes.
- **Evidence:**
```java
if (genFile.getName().contains("..")) { return errorResponse(...); }
// Missing: check for absolute paths or null bytes
```
- **Risk:** A `java_package` of `/tmp/evil` would produce output path `/tmp/evil/Msg.java`. Impact depends on how protoc handles the path — protoc itself prepends the `--jsonarray_out` directory, so this may be a non-issue in practice.
- **Recommendation:** Also reject paths starting with `/` or containing `\0`:
```java
if (name.contains("..") || name.startsWith("/") || name.contains("\0"))
```
- **Status:** **Fixed** (commit 44a0363)

### VULN-005: Javadoc comment injection via `*/` in proto source comments

- **Severity:** Low
- **Category:** CWE-94 (Code Injection via Comments)
- **Location:** `JavaCodeEmitter.java:397-417`
- **Description:** Proto source comments are emitted into Javadoc blocks without escaping `*/`. A comment containing `*/ public void evil() {} /*` would close the Javadoc and inject Java code.
- **Evidence:**
```java
w.line(" * %s", trimmed); // trimmed contains raw comment text
```
- **Risk:** Attacker must control proto source comments, which typically means they control the `.proto` file itself.
- **Recommendation:** `trimmed.replace("*/", "* /")` before emitting.
- **Status:** **Fixed** (commit 44a0363)

### VULN-006: No hard limit on sparse field numbers — DoS via huge generated arrays

- **Severity:** Low
- **Category:** CWE-400 (Uncontrolled Resource Consumption)
- **Location:** `MessageAnalyzer.java:70-79` (warning only, no limit)
- **Description:** A message with field numbers 1 and 536870911 (max proto field number) would generate serialize code with ~537 million `addNull()` calls.
- **Risk:** Build-time DoS — the generated source file would be enormous and compilation would be very slow or fail with OOM.
- **Recommendation:** Add a configurable hard limit (e.g., 10,000) on max field number and reject with an error.
- **Status:** **Fixed** (commit 44a0363)

### VULN-007: C generated code — integer overflow in calloc and missing NULL check

- **Severity:** Medium
- **Category:** CWE-190 (Integer Overflow) / CWE-476 (NULL Pointer Dereference)
- **Location:** `CDeserializerGenerator.java` (generated repeated field deserialization)
- **Description:** Generated C code uses `int count = cJSON_GetArraySize(item); calloc(count, sizeof(Type))` without checking for negative counts or NULL return from calloc.
- **Evidence:**
```c
int count = cJSON_GetArraySize(item);
msg->field = (Type*)calloc(count, sizeof(Type)); // No NULL check
for (int i = 0; i < count; i++) { msg->field[i] = ...; } // NULL deref if calloc failed
```
- **Risk:** If the generated C code processes malicious JSON with extremely large array sizes, it could cause a heap overflow or NULL dereference.
- **Recommendation:** Generate bounds checking (`if (count < 0 || count > MAX_SIZE) return NULL;`) and NULL checks (`if (!msg->field) return NULL;`).
- **Status:** **Fixed** (commit 44a0363)

### VULN-008: C base64 decode — incomplete input validation

- **Severity:** Medium
- **Category:** CWE-787 (Out-of-bounds Write)
- **Location:** `runtime/c/src/codec.c:96`
- **Description:** The `jsonarray_base64_decode` function does not validate that input length is a multiple of 4. Non-standard lengths could cause size miscalculation.
- **Risk:** Malformed base64 input in JSON could trigger incorrect memory allocation size.
- **Recommendation:** Add `if (input_len % 4 != 0) return NULL;` or implement proper padding tolerance.
- **Status:** **Fixed** (commit 44a0363)

### VULN-009: Python docstring injection

- **Severity:** Low
- **Category:** CWE-94 (Code Injection)
- **Location:** `PythonCodeEmitter.java:57`
- **Description:** The message full name is emitted into a triple-quoted Python docstring without escaping `"""`. A crafted message name containing `"""` could close the docstring and inject Python code.
- **Risk:** Same as VULN-001 — requires crafted message name.
- **Recommendation:** Escape `"""` sequences or validate message names (per VULN-001 fix).
- **Status:** **Fixed** (commit 44a0363)

### Summary Table

| ID | Severity | Category | Location | Exploitability |
|----|----------|----------|----------|---------------|
| VULN-001 | High | CWE-94: Code Injection | MessageAnalyzer.java:66 | Requires crafted CodeGeneratorRequest |
| VULN-002 | High | CWE-94: Code Injection | simpleTypeName() in all generators | Requires crafted CodeGeneratorRequest |
| VULN-003 | High | CWE-94: Code Injection | schemaDefaultExpression() | Requires crafted CodeGeneratorRequest |
| VULN-004 | Medium | CWE-22: Path Traversal | PluginRunner.java:116 | Requires crafted CodeGeneratorRequest; protoc may mitigate |
| VULN-005 | Low | CWE-94: Comment Injection | JavaCodeEmitter.java:397 | Attacker controls .proto file |
| VULN-006 | Low | CWE-400: DoS | MessageAnalyzer.java:70 | Requires extreme field numbers |
| VULN-007 | Medium | CWE-190/476: Overflow/NULL | CDeserializerGenerator.java | Malicious JSON input to generated C code |
| VULN-008 | Medium | CWE-787: OOB Write | codec.c:96 | Malformed base64 in JSON input to C runtime |
| VULN-009 | Low | CWE-94: Code Injection | PythonCodeEmitter.java:57 | Requires crafted message name |

## 4. Secure Code Practices Assessment

| Practice | Present | Assessment | Recommendation |
|----------|---------|------------|----------------|
| Input validation | Partial | Field names validated; message names, type refs, default values not | Extend regex validation to all identifier inputs |
| Output encoding | Partial | String defaults escaped; bool/numeric/enum defaults raw | Validate all default values against type-specific patterns |
| Authentication | N/A | No auth (local build tool) | None needed |
| Authorization | N/A | No authz | None needed |
| Cryptography | N/A | No crypto usage | None needed |
| Error handling | Good | All exceptions → error response per plugin protocol | Adequate |
| Logging | Minimal | Warnings to stderr only | Adequate for build tool |
| Secrets management | N/A | No secrets | None needed |
| Dependency management | Good | All versions pinned, recent releases | Add automated CVE scanning |

## 5. Data Flow Analysis

### Sensitive Data Inventory

| Data Type | Classification | Locations | Protection |
|-----------|----------------|-----------|------------|
| Proto schema structure | Internal/IP | Input (CodeGeneratorRequest), Output (generated source) | None needed — schema is the product |
| Field names | Identifier | Descriptors → MessageAnalyzer → generators → output | Regex validation |
| Message names | Identifier | Descriptors → MessageAnalyzer → generators → output | **Keyword escaping only — no regex** |
| Default values | Arbitrary strings/numbers | Proto2 descriptors → generators → output | **Partial escaping** |
| Source comments | Free text | SourceCodeInfo → generators → output | **No escaping** |

### Data Flow: Untrusted Input to Generated Code

```
protoc (or crafted binary)
    │
    ▼
CodeGeneratorRequest (stdin)
    │
    ▼ parseFrom(System.in)
    │
    ├── field.getName() ──────► regex validation ✓ ──► emitted safely
    │
    ├── descriptor.getName() ──► keyword escaping only ──► VULN-001 ⚠
    │
    ├── field.getTypeName() ──► simpleTypeName() ──────► VULN-002 ⚠
    │
    ├── field.getDefaultValue() ► partial escaping ──────► VULN-003 ⚠
    │
    ├── sourceCodeInfo.comment ► no escaping ────────────► VULN-005 ⚠
    │
    └── java_package option ──► path traversal check ──► VULN-004 ⚠
                                (rejects ".." only)
```

## 6. Dependency Assessment

### SBOM Summary

**Total Runtime Dependencies:** 1 (plugin) + 1 (Java runtime)

#### Plugin Runtime Dependencies
| Package | Version | Purpose | License | CVE Status |
|---------|---------|---------|---------|------------|
| com.google.protobuf:protobuf-java | 4.29.3 | Plugin protocol parsing | BSD-3-Clause | No known CVEs (current) |

#### Java Runtime Dependencies
| Package | Version | Purpose | License | CVE Status |
|---------|---------|---------|---------|------------|
| None | — | Jackson eliminated in v0.2.0; replaced with zero-dependency JsonArrayWriter/JsonArrayReader (built-in) | — | No third-party dependencies |

#### Non-Java Runtime Dependencies (user-provided)
| Package | Language | Purpose | Notes |
|---------|----------|---------|-------|
| cJSON | C | JSON parsing for generated C code | User must provide; version not controlled |
| nlohmann/json | C++ | JSON for generated C++ code | User must provide; header-only |
| serde_json | Rust | JSON for generated Rust code | Version 1.x specified in Cargo.toml |
| base64 | Rust | Base64 for generated Rust code | Version 0.22 specified in Cargo.toml |

#### Build Dependencies
| Package | Version | Purpose | Ships in JAR |
|---------|---------|---------|--------------|
| com.gradleup.shadow | 8.3.6 | Fat JAR | No |
| com.diffplug.spotless | 7.0.2 | Formatting | No |
| org.junit.jupiter | 5.11.4 | Testing | No |

### Required Actions

> **WARNING:** CVE analysis is based on knowledge cutoff. Run SCA tools for current status:
> ```bash
> ./gradlew dependencyCheckAnalyze  # OWASP Dependency-Check
> ```
> Or configure GitHub Dependabot / Snyk for continuous monitoring.

## 7. Applicability Assessment

This is a **build-time CLI tool**, not a web application. OWASP Top 10 categories are assessed for relevance:

| OWASP Risk | Relevant | Assessment |
|------------|----------|------------|
| A01: Broken Access Control | No | No users, no access control |
| A02: Cryptographic Failures | No | No cryptography |
| A03: Injection | **Yes** | Code injection via generated source code (VULN-001, 002, 003) |
| A04: Insecure Design | Partial | Trust model assumes protoc validates all input (defense-in-depth gaps) |
| A05: Security Misconfiguration | No | No configuration surface |
| A06: Vulnerable Components | Partial | Dependencies are current; no automated scanning |
| A07: Auth Failures | No | No authentication |
| A08: Software/Data Integrity | **Yes** | Generated code integrity depends on input validation |
| A09: Logging Failures | No | Build tool — minimal logging appropriate |
| A10: SSRF | No | No network calls |

## 8. Recommendations

### High Priority (defense-in-depth for code injection)
1. **Validate message/enum names** against `[a-zA-Z_][a-zA-Z0-9_]*` regex in `MessageAnalyzer.analyze()` — closes VULN-001
2. **Validate `simpleTypeName()` returns** against identifier regex — closes VULN-002
3. **Validate proto2 default values** by type: bool→`^(true|false)$`, int→`^-?[0-9]+$`, enum→`^[A-Z_][A-Z0-9_]*$` — closes VULN-003

### Medium Priority
4. **Extend path traversal check** to reject absolute paths and null bytes — improves VULN-004
5. **Add calloc bounds check and NULL check** in C code generator — closes VULN-007
6. **Add base64 input length validation** in C runtime — closes VULN-008

### Low Priority
7. **Escape `*/` in Javadoc comments** — closes VULN-005
8. **Escape `"""` in Python docstrings** — closes VULN-009
9. **Add configurable hard limit** on max field number — mitigates VULN-006
10. **Set up automated dependency scanning** (Dependabot, OWASP Dependency-Check)

### Requires External Tool Validation
1. Run SAST tool (Semgrep, CodeQL, or Snyk Code) for patterns missed by manual review
2. Run SCA tool for dependency CVEs
3. Run Valgrind/ASan on generated C code with malicious JSON input
4. Consider fuzzing the plugin with malformed CodeGeneratorRequest inputs

## 9. Existing Security Test Coverage

Tests already implemented in `SafetySecurityTest.java` (116 tests):

| ID | Test | Covers | Status |
|----|------|--------|--------|
| SEC-T-001 | testSEC001_validNameAccepted | Field name validation | Implemented |
| SEC-T-002 | testSEC001_digitStartRejected | Field name injection | Implemented |
| SEC-T-003 | testSEC001_dashRejected | Field name injection | Implemented |
| SEC-T-004 | testSEC001_spaceRejected | Field name injection | Implemented |
| SEC-T-005 | testSEC001_emptyRejected | Field name injection | Implemented |
| SEC-T-006 | testSEC001_unicodeRejected | Field name injection | Implemented |
| SEC-T-007 | testSEC001_codeInjectionRejected | Field name code injection | Implemented |
| SEC-T-008 | testSEC002_normalPathAccepted | Path traversal | Implemented |
| SEC-T-009 | testSEC003_keywordEscaping (×9 langs) | Keyword escaping | Implemented |
| SEC-T-010 | testSEC004_nameCollision | Collision detection | Implemented |

### Additional Tests Recommended

| ID | Test | VULN | Priority |
|----|------|------|----------|
| SEC-T-011 | Test message name with special characters → rejection | VULN-001 | High |
| SEC-T-012 | Test type reference with injected code → sanitized | VULN-002 | High |
| SEC-T-013 | Test proto2 bool default "true; evil()" → rejected | VULN-003 | High |
| SEC-T-014 | Test proto2 int default "0; evil()" → rejected | VULN-003 | High |
| SEC-T-015 | Test absolute path in java_package → rejected | VULN-004 | Medium |
| SEC-T-016 | Test comment with `*/` → escaped in Javadoc | VULN-005 | Low |

## 10. Uncertainties

| Flag | Area | Required Analysis |
|------|------|-------------------|
| [INCOMPLETE_ANALYSIS] | C runtime memory safety | Requires Valgrind/ASan dynamic analysis |
| [INCOMPLETE_ANALYSIS] | Dependency CVEs | Requires SCA tool scan |
| [ASSUMED_BEHAVIOR] | protoc input validation | Assumed protoc rejects invalid identifiers; not verified against all protoc versions |
| [INCOMPLETE_ANALYSIS] | Non-Java generators default escaping | Only Java generator's default value handling was deeply reviewed; 8 others need equivalent audit |
| [ASSUMED_BEHAVIOR] | protoc path handling | Assumed protoc prepends output dir to file paths, mitigating VULN-004 |

## 11. Approval

Security assessment requires expert review:
- [ ] Findings validated with SAST tool
- [ ] Dependencies scanned for CVEs
- [ ] High findings have remediation plan
- [ ] Critical findings addressed (none found — all High require crafted input)
- [ ] Assessment acknowledged as incomplete

Security Reviewer: _________________________ Date: ____________
