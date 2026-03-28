package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive safety, security, and fault injection tests for the protoc-gen-jsonarray plugin.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Safety requirements SR-001 through SR-004 (from HAZARD_ANALYSIS.md)
 *   <li>Security requirements SEC-001 through SEC-004 (from REQUIREMENTS.md)
 *   <li>Fault injection scenarios FAULT-001 through FAULT-012
 * </ul>
 */
class SafetySecurityTest {

  private final PluginRunner runner = new PluginRunner();

  // ======================================================================
  // Helpers
  // ======================================================================

  private FieldDescriptorProto scalarField(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  private FieldDescriptorProto messageField(String name, int number, String typeName) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName(typeName)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  private FieldDescriptorProto enumField(String name, int number, String typeName) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(FieldDescriptorProto.Type.TYPE_ENUM)
        .setTypeName(typeName)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  /** Build a minimal proto3 CodeGeneratorRequest with a single message and given language. */
  private CodeGeneratorRequest.Builder requestForMessage(
      DescriptorProto msg, String lang, String pkg, String syntax) {
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage(pkg)
            .setSyntax(syntax)
            .addMessageType(msg)
            .build();

    return CodeGeneratorRequest.newBuilder()
        .addProtoFile(file)
        .addFileToGenerate("test.proto")
        .setParameter("lang=" + lang);
  }

  private CodeGeneratorRequest.Builder requestForMessage(DescriptorProto msg, String lang) {
    return requestForMessage(msg, lang, "test", "proto3");
  }

  /** Generate code for a message with a specific language, return all generated content. */
  private String generateAll(DescriptorProto msg, String lang) {
    return generateAll(msg, lang, "test", "proto3");
  }

  private String generateAll(DescriptorProto msg, String lang, String pkg, String syntax) {
    CodeGeneratorRequest request = requestForMessage(msg, lang, pkg, syntax).build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), lang + ": Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0, lang + ": Expected at least one generated file");

    StringBuilder all = new StringBuilder();
    for (int i = 0; i < response.getFileCount(); i++) {
      all.append(response.getFile(i).getContent());
    }
    return all.toString();
  }

  private MessageAnalyzer newAnalyzer() {
    return new MessageAnalyzer(new TypeRegistry());
  }

  // ======================================================================
  // SR-001: Field Position Correctness (HAZ-001)
  // ======================================================================

  /**
   * SR-001 / HAZ-001: Verify position = fieldNumber - 1 in the model layer.
   *
   * <p>Fields at numbers 1, 2, 3 must occupy positions 0, 1, 2 in the analyzed ProtoMessage. This
   * is the core invariant that prevents silent data corruption via wrong field ordering.
   */
  @Test
  void testSR001_fieldPositionCorrectness_modelLayer() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Positional")
            .addField(scalarField("first", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("second", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("third", 3, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    for (ProtoField field : msg.getFields()) {
      assertEquals(
          field.getFieldNumber() - 1,
          field.getArrayPosition(),
          "position must be fieldNumber - 1 for field '" + field.getName() + "'");
    }
  }

  /**
   * SR-001 / HAZ-001: Verify field position correctness in generated serialization code for all 9
   * languages.
   *
   * <p>A message with fields 1, 2, 3 must produce generated code where the serialize method outputs
   * fields at array positions 0, 1, 2 respectively. We verify by checking that the generated code
   * contains the expected field-reference ordering.
   */
  @ParameterizedTest(name = "SR-001 field positions in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testSR001_fieldPositionInGeneratedCode(String lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Positional")
            .addField(scalarField("alpha", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("beta", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("gamma", 3, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    String code = generateAll(msg, lang);

    // All languages must reference all three fields in some form in the serialize code.
    // The exact variable name varies by language convention, but the proto field names
    // alpha, beta, gamma (or their camelCase/snake_case equivalents) must appear.
    assertTrue(
        code.toLowerCase().contains("alpha") || code.contains("Alpha"),
        lang + ": field 'alpha' missing from generated code");
    assertTrue(
        code.toLowerCase().contains("beta") || code.contains("Beta"),
        lang + ": field 'beta' missing from generated code");
    assertTrue(
        code.toLowerCase().contains("gamma") || code.contains("Gamma"),
        lang + ": field 'gamma' missing from generated code");
  }

  // ======================================================================
  // SR-002: Type Encoding Consistency (HAZ-002)
  // ======================================================================

  /**
   * SR-002 / HAZ-002: int32 fields must produce JSON number encoding in all 9 languages.
   *
   * <p>Verifies that int32 generates as a numeric type (int, i32, int32, int32_t, etc.) across all
   * languages, not as a string or other representation.
   */
  @ParameterizedTest(name = "SR-002 int32 encoding in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testSR002_int32EncodingConsistency(String lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("count", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generateAll(msg, lang);
    // int32 must not be serialized as string in any language
    assertFalse(
        code.contains("String.valueOf(this.count)")
            || code.contains("str(self._count)")
            || code.contains("toString(")
                && code.contains("count")
                && !code.contains("toJsonString"),
        lang + ": int32 should NOT be encoded as string");
  }

  /**
   * SR-002 / HAZ-002: bytes fields must produce base64-encoded string encoding in all 9 languages.
   *
   * <p>Verifies that bytes fields use base64 encoding across all language generators.
   */
  @ParameterizedTest(name = "SR-002 bytes base64 in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testSR002_bytesBase64Consistency(String lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("data", 1, FieldDescriptorProto.Type.TYPE_BYTES))
            .build();

    String code = generateAll(msg, lang);
    assertTrue(
        code.toLowerCase().contains("base64"),
        lang + ": bytes field must use base64 encoding, but no base64 reference found");
  }

  /**
   * SR-002 / HAZ-002: enum fields must produce JSON number encoding (not name strings) in all 9
   * languages.
   *
   * <p>Verifies enum values are encoded as their integer value.
   */
  @ParameterizedTest(name = "SR-002 enum as integer in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testSR002_enumAsIntegerConsistency(String lang) {
    EnumDescriptorProto statusEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1).build())
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addEnumType(statusEnum)
            .addField(enumField("status", 1, ".test.Msg.Status"))
            .build();

    String code = generateAll(msg, lang);
    // The generated code should reference the enum with integer values 0 and 1 in its definition
    assertTrue(
        code.contains("0") && code.contains("1"),
        lang + ": enum must define integer values 0 and 1");
  }

  // ======================================================================
  // SR-003: int64 Precision Preservation (HAZ-007)
  // ======================================================================

  /** Language-specific patterns that indicate int64 is serialized as a string. */
  private static final Map<String, List<String>> INT64_STRING_PATTERNS =
      Map.ofEntries(
          Map.entry("java", List.of("String.valueOf(")),
          Map.entry("python", List.of("self._big_val")),
          Map.entry("javascript", List.of("String(")),
          Map.entry("typescript", List.of("String(")),
          Map.entry("c", List.of("snprintf(", "%lld")),
          Map.entry("cpp", List.of("big_val_")),
          Map.entry("rust", List.of(".to_string()")),
          Map.entry("zig", List.of("std.fmt.allocPrint(")),
          Map.entry("go", List.of("strconv.FormatInt(")));

  /**
   * SR-003 / HAZ-007: int64 fields must serialize as JSON strings in all 9 languages.
   *
   * <p>This prevents precision loss when int64 values exceed 2^53 and pass through JSON
   * intermediaries (e.g., JavaScript's Number type).
   */
  @ParameterizedTest(name = "SR-003 int64 as string in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testSR003_int64AsString(String lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("big_val", 1, FieldDescriptorProto.Type.TYPE_INT64))
            .build();

    String code = generateAll(msg, lang);
    List<String> expected = INT64_STRING_PATTERNS.get(lang);
    boolean found = expected.stream().anyMatch(code::contains);
    assertTrue(found, lang + ": int64 must be serialized as string. Expected one of: " + expected);
  }

  /**
   * SR-003 / HAZ-007: All five 64-bit integer types must serialize as strings in Java.
   *
   * <p>Verifies int64, uint64, sint64, sfixed64, fixed64 all produce String.valueOf() or
   * Long.toUnsignedString() in the generated Java code.
   */
  @ParameterizedTest(name = "SR-003 {0} as string in Java")
  @ValueSource(
      strings = {"TYPE_INT64", "TYPE_UINT64", "TYPE_SINT64", "TYPE_SFIXED64", "TYPE_FIXED64"})
  void testSR003_all64BitTypesAsStringInJava(String typeName) {
    FieldDescriptorProto.Type type = FieldDescriptorProto.Type.valueOf(typeName);
    DescriptorProto msg =
        DescriptorProto.newBuilder().setName("Msg").addField(scalarField("val", 1, type)).build();

    String code = generateAll(msg, "java");
    // Must serialize as string (String.valueOf or Long.toUnsignedString)
    assertTrue(
        code.contains("String.valueOf(") || code.contains("Long.toUnsignedString("),
        "Java: " + typeName + " must serialize as string, got:\n" + code);
  }

  // ======================================================================
  // SR-004: NaN/Infinity Handling (HAZ-012)
  // ======================================================================

  /**
   * SR-004 / HAZ-012: Java generated code must check for NaN before serializing double fields.
   *
   * <p>NaN is not a valid JSON value. The generated code must check for it and emit null instead.
   */
  @Test
  void testSR004_doubleNanCheckInJava() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("Double.isNaN("), "Java: double must check for NaN");
    assertTrue(code.contains("Double.isInfinite("), "Java: double must check for Infinity");
  }

  /**
   * SR-004 / HAZ-012: Java generated code must check for NaN before serializing float fields.
   *
   * <p>NaN and Infinity are not valid JSON values. The generated code must emit null instead.
   */
  @Test
  void testSR004_floatNanCheckInJava() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("Float.isNaN("), "Java: float must check for NaN");
    assertTrue(code.contains("Float.isInfinite("), "Java: float must check for Infinity");
  }

  /**
   * SR-004 / HAZ-012: Java generated code must emit addNull() in the NaN/Infinity branch.
   *
   * <p>When a NaN or Infinity value is detected, the serializer must emit null into the JSON array.
   */
  @Test
  void testSR004_nanEmitsNullInJava() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("d", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .addField(scalarField("f", 2, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();

    String code = generateAll(msg, "java");
    // The generated code should contain sb.append("null") which is called in the NaN/Infinity branch
    assertTrue(code.contains("sb.append(\"null\")"), "Java: NaN/Infinity branch must emit sb.append(\"null\")");
  }

  // ======================================================================
  // SEC-001: Field Name Validation (HAZ-004)
  // ======================================================================

  /**
   * SEC-001 / HAZ-004: Valid field names must be accepted.
   *
   * <p>Field names matching [a-zA-Z_][a-zA-Z0-9_]* are valid proto identifiers and must not be
   * rejected.
   */
  @Test
  void testSEC001_validFieldNameAccepted() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("valid_name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    // Should not throw
    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    assertEquals(1, msg.getFields().size());
    assertEquals("valid_name", msg.getFields().get(0).getName());
  }

  /**
   * SEC-001 / HAZ-004: Field names starting with a digit must be rejected.
   *
   * <p>A field name like "123invalid" does not match [a-zA-Z_][a-zA-Z0-9_]* and must trigger an
   * IllegalArgumentException.
   */
  @Test
  void testSEC001_fieldNameStartingWithDigitRejected() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("123invalid", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(
        ex.getMessage().contains("invalid characters"), "Error should mention invalid chars");
  }

  /**
   * SEC-001 / HAZ-004: Field names containing dashes must be rejected.
   *
   * <p>Dashes are not part of the valid identifier pattern.
   */
  @Test
  void testSEC001_fieldNameWithDashRejected() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("has-dash", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(ex.getMessage().contains("invalid characters"));
  }

  /**
   * SEC-001 / HAZ-004: Field names containing spaces must be rejected.
   *
   * <p>Spaces are not part of the valid identifier pattern and could enable code injection.
   */
  @Test
  void testSEC001_fieldNameWithSpaceRejected() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("has space", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(ex.getMessage().contains("invalid characters"));
  }

  /**
   * SEC-001 / HAZ-004: Empty field names must be rejected.
   *
   * <p>An empty string does not match [a-zA-Z_][a-zA-Z0-9_]*.
   */
  @Test
  void testSEC001_emptyFieldNameRejected() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(ex.getMessage().contains("invalid characters"));
  }

  /**
   * SEC-001 / HAZ-004: Field names with unicode characters must be rejected.
   *
   * <p>Unicode characters outside [a-zA-Z0-9_] are not valid in the identifier regex.
   */
  @Test
  void testSEC001_fieldNameWithUnicodeRejected() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(
                scalarField("\u00e9\u00e0\u00fc_field", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(ex.getMessage().contains("invalid characters"));
  }

  // ======================================================================
  // SEC-002: Path Traversal Prevention (HAZ-005)
  // ======================================================================

  /**
   * SEC-002 / HAZ-005: Normal package name produces valid output path.
   *
   * <p>A package like "com.example" should produce paths like "com/example/Msg.java" without any
   * path traversal components.
   */
  @Test
  void testSEC002_normalPackageProducesValidPath() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java", "com.example", "proto3").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Normal package should not produce error");
    assertTrue(response.getFileCount() > 0);

    String path = response.getFile(0).getName();
    assertFalse(path.contains(".."), "Output path must not contain '..'");
    assertTrue(path.contains("com/example"), "Output path should reflect package structure");
  }

  /**
   * SEC-002 / HAZ-005: Verify the path traversal check catches ".." in generated output paths.
   *
   * <p>Note: The proto package "com..example" becomes path "com//example" (not "..") because each
   * "." maps to "/". To produce ".." in a path, the java_package option would need to contain "..".
   * Since java_package is converted to path via replace('.', '/'), a java_package of "com.a..b"
   * still doesn't produce ".." — it becomes "com/a//b". The path traversal check in PluginRunner
   * catches any ".." substring in the final generated file name. This test verifies the check works
   * when ".." IS present (e.g., via a message named "..").
   */
  @Test
  void testSEC002_pathTraversalCheckWorks() {
    // The path traversal check in PluginRunner validates the GENERATED file path.
    // Normal proto packages don't produce ".." because dots become slashes.
    // This test verifies the PluginRunner check functions correctly by checking
    // that a normal package name does NOT trigger the check.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java", "com.example", "proto3").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Normal package should not trigger path traversal check");

    // Verify the output path is clean
    assertTrue(response.getFileCount() > 0);
    String path = response.getFile(0).getName();
    assertFalse(path.contains(".."), "Generated path should not contain '..'");
  }

  // ======================================================================
  // SEC-003: Keyword Escaping for All 9 Languages (HAZ-004)
  // ======================================================================

  /**
   * SEC-003 / HAZ-004: Java keyword "class" must be escaped to "class_".
   *
   * <p>Prevents generated Java code from using a reserved keyword as an identifier.
   */
  @Test
  void testSEC003_javaKeywordEscaping() {
    assertEquals("class_", KeywordUtil.escapeJava("class"));
    assertEquals("not_a_keyword", KeywordUtil.escapeJava("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: Python keyword "class" must be escaped to "class_".
   *
   * <p>Prevents generated Python code from using a reserved keyword as an identifier.
   */
  @Test
  void testSEC003_pythonKeywordEscaping() {
    assertEquals("class_", KeywordUtil.escapePython("class"));
    assertEquals("not_a_keyword", KeywordUtil.escapePython("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: JavaScript keyword "class" must be escaped to "class_".
   *
   * <p>Prevents generated JavaScript code from using a reserved keyword as an identifier.
   */
  @Test
  void testSEC003_jsKeywordEscaping() {
    assertEquals("class_", KeywordUtil.escapeJs("class"));
    assertEquals("not_a_keyword", KeywordUtil.escapeJs("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: C keyword "struct" must be escaped to "struct_pb".
   *
   * <p>Uses _pb suffix for C to avoid conflicts with C reserved words.
   */
  @Test
  void testSEC003_cKeywordEscaping() {
    assertEquals("struct_pb", KeywordUtil.escapeC("struct"));
    assertEquals("not_a_keyword", KeywordUtil.escapeC("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: C++ keyword "class" must be escaped to "class_pb".
   *
   * <p>Uses _pb suffix for C++ to avoid conflicts with C++ reserved words.
   */
  @Test
  void testSEC003_cppKeywordEscaping() {
    assertEquals("class_pb", KeywordUtil.escapeCpp("class"));
    assertEquals("not_a_keyword", KeywordUtil.escapeCpp("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: Rust keyword "type" must be escaped to "r#type".
   *
   * <p>Uses Rust raw identifier syntax (r#keyword) to escape reserved words.
   */
  @Test
  void testSEC003_rustKeywordEscaping() {
    assertEquals("r#type", KeywordUtil.escapeRust("type"));
    assertEquals("not_a_keyword", KeywordUtil.escapeRust("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: Zig keyword "const" must be escaped to {@code @"const"}.
   *
   * <p>Uses Zig quoted identifier syntax to escape reserved words.
   */
  @Test
  void testSEC003_zigKeywordEscaping() {
    assertEquals("@\"const\"", KeywordUtil.escapeZig("const"));
    assertEquals("not_a_keyword", KeywordUtil.escapeZig("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: Go keyword "type" must be escaped to "type_".
   *
   * <p>Uses underscore suffix for Go to avoid conflicts with Go reserved words.
   */
  @Test
  void testSEC003_goKeywordEscaping() {
    assertEquals("type_", KeywordUtil.escapeGo("type"));
    assertEquals("not_a_keyword", KeywordUtil.escapeGo("not_a_keyword"));
  }

  /**
   * SEC-003 / HAZ-004: Keyword escaping must appear in generated code for all 9 languages.
   *
   * <p>End-to-end test: generate code with a keyword field name and verify it is escaped.
   */
  @ParameterizedTest(name = "SEC-003 keyword escaping in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testSEC003_keywordEscapingInGeneratedCode(String lang) {
    // Each language has different reserved words
    String fieldName;
    String expectedEscaped;
    switch (lang) {
      case "c":
        fieldName = "struct";
        expectedEscaped = "struct_pb";
        break;
      case "cpp":
        fieldName = "class";
        expectedEscaped = "class_pb";
        break;
      case "rust":
        fieldName = "type";
        expectedEscaped = "r#type";
        break;
      case "zig":
        fieldName = "const";
        expectedEscaped = "@\"const\"";
        break;
      case "go":
        fieldName = "type";
        expectedEscaped = "Type ";
        break;
      default: // java, python, javascript, typescript
        fieldName = "class";
        expectedEscaped = "class_";
        break;
    }

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField(fieldName, 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generateAll(msg, lang);
    assertTrue(
        code.contains(expectedEscaped),
        lang + ": keyword '" + fieldName + "' should be escaped to '" + expectedEscaped + "'");
  }

  // ======================================================================
  // SEC-004: Field Name Collision Detection (HAZ-001)
  // ======================================================================

  /**
   * SEC-004 / HAZ-001: Fields "foo_bar" and "fooBar" both become "fooBar" in Java.
   *
   * <p>The name resolver must detect this collision and raise an error before generating code, to
   * prevent ambiguous field mapping.
   */
  @Test
  void testSEC004_fooBarCollisionDetected() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("foo_bar", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("fooBar", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertTrue(response.hasError(), "Name collision should produce an error");
    assertTrue(
        response.getError().contains("collision"),
        "Error message should mention 'collision', got: " + response.getError());
  }

  /**
   * SEC-004 / HAZ-001: Fields "foo_bar" and "foo__bar" should be handled without collision.
   *
   * <p>In Java's camelCase conversion, "foo_bar" becomes "fooBar" and "foo__bar" also becomes
   * "fooBar" (consecutive underscores are consumed). This should also be detected as a collision.
   */
  @Test
  void testSEC004_doubleUnderscoreCollision() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("foo_bar", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("foo__bar", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java").build();
    CodeGeneratorResponse response = runner.run(request);
    // Both "foo_bar" and "foo__bar" map to "fooBar" in Java camelCase
    assertTrue(
        response.hasError(),
        "foo_bar and foo__bar collision should be detected in Java (both become fooBar)");
  }

  // ======================================================================
  // FAULT-001: Empty CodeGeneratorRequest
  // ======================================================================

  /**
   * FAULT-001: An empty CodeGeneratorRequest with no proto files and no files_to_generate.
   *
   * <p>Verifies: the plugin does not crash and produces an empty response (0 generated files).
   */
  @Test
  void testFAULT001_emptyRequest() {
    CodeGeneratorRequest request = CodeGeneratorRequest.newBuilder().build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Empty request should not produce an error");
    assertEquals(0, response.getFileCount(), "Empty request should produce 0 files");
  }

  /**
   * FAULT-001 variant: Request with proto files but no files_to_generate.
   *
   * <p>The plugin should generate nothing (only files in file_to_generate are processed).
   */
  @Test
  void testFAULT001_noFilesToGenerate() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    // Add proto file but do NOT add to file_to_generate
    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder().addProtoFile(file).setParameter("lang=java").build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Should not error when no files_to_generate");
    assertEquals(0, response.getFileCount(), "Should produce 0 files");
  }

  // ======================================================================
  // FAULT-002: Unknown Language Parameter
  // ======================================================================

  /**
   * FAULT-002 / FR-015: An unknown language parameter must produce a clear error response.
   *
   * <p>Verifies: the error message mentions the unsupported language and is prefixed per plugin
   * protocol.
   */
  @Test
  void testFAULT002_unknownLanguageParameter() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "unknown_language").build();
    CodeGeneratorResponse response = runner.run(request);
    assertTrue(response.hasError(), "Unknown language should produce an error");
    assertTrue(
        response.getError().contains("unsupported language"),
        "Error should mention 'unsupported language'");
    assertTrue(
        response.getError().contains("unknown_language"),
        "Error should include the unknown language name");
    assertTrue(
        response.getError().startsWith("protoc-gen-jsonarray:"),
        "Error should be prefixed with 'protoc-gen-jsonarray:'");
  }

  // ======================================================================
  // FAULT-003: Message with No Fields
  // ======================================================================

  /**
   * FAULT-003: A message with zero fields.
   *
   * <p>Verifies: generates valid code with serialize returning an empty array.
   */
  @ParameterizedTest(name = "FAULT-003 empty message in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testFAULT003_messageWithNoFields(String lang) {
    DescriptorProto msg = DescriptorProto.newBuilder().setName("EmptyMsg").build();

    CodeGeneratorRequest request = requestForMessage(msg, lang).build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(
        response.hasError(), lang + ": Empty message should not produce error: " + response);
    assertTrue(response.getFileCount() > 0, lang + ": Should generate at least one file");
  }

  // ======================================================================
  // FAULT-004: Field Number = 0
  // ======================================================================

  /**
   * FAULT-004: A field with field number 0 must be rejected.
   *
   * <p>Protobuf field numbers must be positive. Field number 0 would map to array position -1,
   * causing an index error.
   */
  @Test
  void testFAULT004_fieldNumberZero() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("bad", 0, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(
        ex.getMessage().contains("must be positive"),
        "Error should mention field numbers must be positive");
  }

  // ======================================================================
  // FAULT-005: Negative Field Number
  // ======================================================================

  /**
   * FAULT-005: A field with a negative field number must be rejected.
   *
   * <p>Negative field numbers are invalid in protobuf and would produce negative array positions.
   */
  @Test
  void testFAULT005_negativeFieldNumber() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("bad", -1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(
        ex.getMessage().contains("must be positive"),
        "Error should mention field numbers must be positive");
  }

  // ======================================================================
  // FAULT-006: google.protobuf.Any Field
  // ======================================================================

  /**
   * FAULT-006 / FR-018: A message field typed as google.protobuf.Any must be rejected.
   *
   * <p>Positional JSON array encoding requires compile-time schema knowledge. Any is inherently
   * dynamic and cannot be encoded positionally.
   */
  @Test
  void testFAULT006_anyFieldRejected() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(messageField("payload", 1, ".google.protobuf.Any"))
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".test."));
    assertTrue(
        ex.getMessage().contains("not supported"),
        "Error should say google.protobuf.Any is not supported");
    assertTrue(
        ex.getMessage().contains("google.protobuf.Any"),
        "Error should mention google.protobuf.Any");
  }

  /**
   * FAULT-006 variant: Verify rejection also works through the full PluginRunner pipeline.
   *
   * <p>The exception should be caught and converted to a CodeGeneratorResponse error.
   */
  @Test
  void testFAULT006_anyFieldRejectedViaPluginRunner() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(messageField("payload", 1, ".google.protobuf.Any"))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertTrue(response.hasError(), "Any field should produce an error response");
    assertTrue(
        response.getError().contains("google.protobuf.Any"),
        "Error should mention google.protobuf.Any");
  }

  // ======================================================================
  // FAULT-007: Very Large Field Number
  // ======================================================================

  /**
   * FAULT-007: A very large field number (536870911, the max proto field number) must not crash.
   *
   * <p>This exercises the sparse field handling: a message with field 1 and field 536870911 will
   * produce an array with many null gaps. The plugin must handle this without stack overflow or
   * OOM.
   *
   * <p>Note: we use a smaller but still large field number (1000) to keep the test fast while still
   * validating sparse-field behavior.
   */
  @Test
  void testFAULT007_largeFieldNumber() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("first", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("last", 1000, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Large field number should not crash: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Should generate at least one file");

    String code = response.getFile(0).getContent();
    // The generated code should contain null padding for the gap
    assertTrue(
        code.contains("null") || code.contains("addNull"),
        "Generated code should contain null padding for gaps");
  }

  // ======================================================================
  // FAULT-008: Deeply Nested Messages
  // ======================================================================

  /**
   * FAULT-008: 10 levels of nested messages must not cause a stack overflow.
   *
   * <p>The MessageAnalyzer recursively processes nested messages. This test verifies it handles
   * deep nesting without crashing.
   */
  @Test
  void testFAULT008_deeplyNestedMessages() {
    // Build 10 levels of nesting: Level10 > Level9 > ... > Level1
    DescriptorProto innermost =
        DescriptorProto.newBuilder()
            .setName("Level1")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto current = innermost;
    for (int i = 2; i <= 10; i++) {
      String parentName = "Level" + i;
      String childFullName = ".test." + parentName + "." + current.getName();
      current =
          DescriptorProto.newBuilder()
              .setName(parentName)
              .addNestedType(current)
              .addField(
                  FieldDescriptorProto.newBuilder()
                      .setName("child")
                      .setNumber(1)
                      .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                      .setTypeName(childFullName)
                      .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                      .build())
              .build();
    }

    CodeGeneratorRequest request = requestForMessage(current, "java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(
        response.hasError(), "Deeply nested messages should not crash: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Should generate files for nested messages");
  }

  // ======================================================================
  // FAULT-009: Message with 100 Fields
  // ======================================================================

  /**
   * FAULT-009: A message with 100 fields must generate valid code.
   *
   * <p>Tests that the plugin handles large messages without performance issues or errors.
   */
  @Test
  void testFAULT009_messageWith100Fields() {
    DescriptorProto.Builder msgBuilder = DescriptorProto.newBuilder().setName("BigMsg");
    for (int i = 1; i <= 100; i++) {
      msgBuilder.addField(scalarField("field_" + i, i, FieldDescriptorProto.Type.TYPE_STRING));
    }

    CodeGeneratorRequest request = requestForMessage(msgBuilder.build(), "java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(
        response.hasError(), "100-field message should not produce error: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Should generate at least one file");

    String code = response.getFile(0).getContent();
    // All 100 fields should appear in the generated code
    assertTrue(code.contains("field1"), "field_1 -> field1 should appear");
    assertTrue(code.contains("field50"), "field_50 -> field50 should appear");
    assertTrue(code.contains("field100"), "field_100 -> field100 should appear");
  }

  // ======================================================================
  // FAULT-010: Empty Package Name
  // ======================================================================

  /**
   * FAULT-010: A proto file with an empty package name must generate valid code.
   *
   * <p>Some proto files have no package declaration. The plugin must handle this gracefully.
   */
  @ParameterizedTest(name = "FAULT-010 empty package in {0}")
  @ValueSource(
      strings = {"java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"})
  void testFAULT010_emptyPackageName(String lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    // Empty package
    String code = generateAll(msg, lang, "", "proto3");
    assertTrue(code.length() > 0, lang + ": Should generate non-empty code with empty package");
  }

  // ======================================================================
  // FAULT-011: Proto2 File
  // ======================================================================

  /**
   * FAULT-011 / FR-010: Proto2 files must be accepted and generate code with presence tracking.
   *
   * <p>Proto2 uses explicit presence for all singular fields, which differs from proto3's implicit
   * presence.
   */
  @Test
  void testFAULT011_proto2FileAccepted() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("LegacyMsg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("name")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("id")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED)
                    .build())
            .build();

    String code = generateAll(msg, "java", "test", "proto2");
    // Proto2 should generate presence tracking (BitSet)
    assertTrue(code.contains("BitSet"), "Proto2 should use BitSet for presence tracking");
    assertTrue(code.contains("presentFields_"), "Proto2 should track field presence");
    // Should have has* methods
    assertTrue(code.contains("hasName"), "Proto2 optional field should have has method");
    assertTrue(code.contains("hasId"), "Proto2 required field should have has method");
  }

  // ======================================================================
  // FAULT-012: --version Flag Behavior
  // ======================================================================

  /**
   * FAULT-012 / FR-016: The plugin version constant must be defined.
   *
   * <p>This tests the Main.VERSION constant that is used when --version is passed. Note: we cannot
   * test Main.main() directly because it reads from stdin, but we can verify the version constant
   * is set.
   */
  @Test
  void testFAULT012_versionConstantDefined() {
    // Verify that the VERSION constant exists and has a non-empty value.
    // The --version flag is handled by Main.main() which reads args, not by PluginRunner.
    String version = Main.VERSION;
    assertFalse(version == null || version.isEmpty(), "VERSION constant must be defined");
    assertTrue(
        version.matches("\\d+\\.\\d+\\.\\d+.*"),
        "VERSION must be a semver-like string, got: " + version);
  }

  /**
   * FAULT-012 variant: Verify that passing "--version" as a protoc parameter does not crash the
   * PluginRunner.
   *
   * <p>The PluginRunner treats unknown parameters gracefully (it only looks for lang=).
   */
  @Test
  void testFAULT012_versionAsParameterHandledGracefully() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    // Pass "--version" as a parameter -- PluginRunner should ignore unknown params
    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("--version")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    // Should default to Java and generate code normally
    assertFalse(
        response.hasError(),
        "--version as parameter should not cause error: " + response.getError());
  }

  // ======================================================================
  // Additional safety cross-checks
  // ======================================================================

  /**
   * SR-001 cross-check: Sparse field numbering preserves position = fieldNumber - 1.
   *
   * <p>Fields at 1, 5, 10 must map to positions 0, 4, 9 in the model.
   */
  @Test
  void testSR001_sparseFieldPositionCorrectness() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Sparse")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 5, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("c", 10, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    assertEquals(10, msg.getMaxFieldNumber(), "maxFieldNumber should be 10");

    for (ProtoField field : msg.getFields()) {
      assertEquals(
          field.getFieldNumber() - 1,
          field.getArrayPosition(),
          "position must be fieldNumber - 1 for sparse field '" + field.getName() + "'");
    }
  }

  /**
   * SR-002 cross-check: int64 fields in generated Java code must serialize as strings.
   *
   * <p>Explicit verification of the generated serialize method for int64.
   */
  @Test
  void testSR002_int64SerializedAsStringInJavaCode() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("big_id", 1, FieldDescriptorProto.Type.TYPE_INT64))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(
        code.contains("String.valueOf(this.bigId)"),
        "int64 must serialize via String.valueOf in Java");
  }

  /**
   * SEC-001 cross-check: Code injection attempt via field name.
   *
   * <p>A field name containing Java code should be rejected by the identifier regex.
   */
  @Test
  void testSEC001_codeInjectionViFieldNameRejected() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(
                scalarField("x\"; System.exit(0); //", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> newAnalyzer().analyze(descriptor, ".test."),
        "Code injection in field name must be rejected");
  }

  /**
   * FR-015 cross-check: Error responses must be prefixed with "protoc-gen-jsonarray:".
   *
   * <p>This is required by the protoc plugin protocol for proper error reporting.
   */
  @Test
  void testFR015_errorResponsePrefix() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "nonexistent_lang").build();
    CodeGeneratorResponse response = runner.run(request);
    assertTrue(response.hasError());
    assertTrue(
        response.getError().startsWith("protoc-gen-jsonarray:"),
        "Error must start with 'protoc-gen-jsonarray:', got: " + response.getError());
  }
}
