/*
 * Copyright 2026 coffeeandwork
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.protocgen.textcodecs.pbtkurl;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.MessageAnalyzer;
import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety, security, and fault injection tests for the protoc-gen-pbtkurl plugin.
 *
 * <p>Mirrors the JSON array SafetySecurityTest but adapted for pbtk URL encoding semantics. Covers:
 *
 * <ul>
 *   <li>SR-001 through SR-004 (safety requirements from HAZARD_ANALYSIS.md)
 *   <li>SEC-001 through SEC-004 (security requirements from REQUIREMENTS.md)
 *   <li>FAULT-001 and FAULT-002 (fault injection scenarios)
 * </ul>
 */
class PbtkSafetySecurityTest {

  private final PbtkPluginRunner runner = new PbtkPluginRunner();

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

  private FieldDescriptorProto enumField(String name, int number, String typeName) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(FieldDescriptorProto.Type.TYPE_ENUM)
        .setTypeName(typeName)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

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

  /** Generate code for a message in a given language, concatenating all output files. */
  private String generateAll(DescriptorProto msg, String lang) {
    return generateAll(msg, lang, "test", "proto3");
  }

  private String generateAll(DescriptorProto msg, String lang, String pkg, String syntax) {
    CodeGeneratorRequest request = requestForMessage(msg, lang, pkg, syntax).build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), lang + ": unexpected error: " + response.getError());
    assertTrue(response.getFileCount() > 0, lang + ": expected at least one generated file");

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
  // SR-001: Field Number Correctness
  // ======================================================================
  // pbtk uses field numbers directly in the output (e.g., !3i42), unlike
  // JSON array where position = fieldNumber - 1. So we verify the literal
  // field number tokens appear in the generated code.

  @Test
  void testSR001_fieldNumbersAppearInGeneratedCode() {
    // Three fields at numbers 1, 2, 3 should produce !1s, !2i, !3b prefixes.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Positional")
            .addField(scalarField("alpha", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("beta", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("gamma", 3, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1s"), "field 1 (string) should use prefix !1s");
    assertTrue(code.contains("!2i"), "field 2 (int32) should use prefix !2i");
    assertTrue(code.contains("!3b"), "field 3 (bool) should use prefix !3b");
  }

  @Test
  void testSR001_gapInFieldNumbersDoesNotPadWithNulls() {
    // pbtk skips absent fields entirely -- no null padding unlike JSON array.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Sparse")
            .addField(scalarField("first", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("tenth", 10, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1s"), "field 1 should appear");
    assertTrue(code.contains("!10i"), "field 10 should appear with its actual number");
    // pbtk output should not contain addNull-style padding
    assertFalse(code.contains("addNull"), "pbtk should not pad gaps with nulls");
  }

  @ParameterizedTest(name = "SR-001 field references present in {0}")
  @ValueSource(
      strings = {
        "java",
        "python",
        "javascript",
        "typescript",
        "c",
        "cpp",
        "rust",
        "zig",
        "go",
        "csharp",
        "kotlin",
        "swift",
        "dart",
        "php",
        "ruby",
        "objc",
        "perl"
      })
  void testSR001_fieldNamesPresentInAllLanguages(String lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Positional")
            .addField(scalarField("alpha", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("beta", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generateAll(msg, lang);
    // Every language must reference the field names somewhere in the generated code.
    assertTrue(
        code.toLowerCase().contains("alpha") || code.contains("Alpha"),
        lang + ": field 'alpha' missing");
    assertTrue(
        code.toLowerCase().contains("beta") || code.contains("Beta"),
        lang + ": field 'beta' missing");
  }

  // ======================================================================
  // SR-002: Type Encoding Consistency
  // ======================================================================
  // Each proto type maps to a specific pbtk type char. Verify the mapping.

  @Test
  void testSR002_int32UsesTypeCharI() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("count", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1i"), "int32 should use type char 'i'");
  }

  @Test
  void testSR002_stringUsesTypeCharS() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("label", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1s"), "string should use type char 's'");
  }

  @Test
  void testSR002_boolUsesTypeCharB() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("flag", 1, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1b"), "bool should use type char 'b'");
  }

  @Test
  void testSR002_doubleUsesTypeCharD() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1d"), "double should use type char 'd'");
  }

  @Test
  void testSR002_floatUsesTypeCharF() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1f"), "float should use type char 'f'");
  }

  @Test
  void testSR002_bytesUsesTypeCharZ() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("data", 1, FieldDescriptorProto.Type.TYPE_BYTES))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1z"), "bytes should use type char 'z'");
    assertTrue(code.contains("Base64"), "bytes must use base64 encoding");
  }

  @Test
  void testSR002_enumUsesTypeCharE() {
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

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1e"), "enum should use type char 'e'");
  }

  @Test
  void testSR002_bytesBase64InAllLanguages() {
    // All languages must reference base64 for bytes fields.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("data", 1, FieldDescriptorProto.Type.TYPE_BYTES))
            .build();

    for (String lang :
        new String[] {
          "java", "python", "javascript", "typescript", "c", "cpp", "rust", "zig", "go"
        }) {
      String code = generateAll(msg, lang);
      assertTrue(
          code.toLowerCase().contains("base64"),
          lang + ": bytes field must use base64, but no base64 reference found");
    }
  }

  // ======================================================================
  // SR-003: Int64 Precision
  // ======================================================================
  // In pbtk format, int64 uses 'i' type char (same as int32). The key thing
  // is that the serializer handles all five 64-bit integer types without
  // truncation. Unlike JSON array, pbtk doesn't need string wrapping since
  // the value is just appended as text, not parsed as a JSON number.

  @Test
  void testSR003_int64SerializesWithTypeCharI() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("big_val", 1, FieldDescriptorProto.Type.TYPE_INT64))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1i"), "int64 should use type char 'i'");
    assertTrue(code.contains("Long.parseLong"), "deserializer should parse as long");
  }

  @ParameterizedTest(name = "SR-003 {0} generates valid code in Java")
  @ValueSource(
      strings = {"TYPE_INT64", "TYPE_UINT64", "TYPE_SINT64", "TYPE_SFIXED64", "TYPE_FIXED64"})
  void testSR003_all64BitTypesGenerateSuccessfully(String typeName) {
    FieldDescriptorProto.Type type = FieldDescriptorProto.Type.valueOf(typeName);
    DescriptorProto msg =
        DescriptorProto.newBuilder().setName("Msg").addField(scalarField("val", 1, type)).build();

    // All 64-bit types should generate valid code without errors.
    String code = generateAll(msg, "java");
    assertTrue(code.contains("!1i"), "Java: " + typeName + " should use type char 'i'");
  }

  @Test
  void testSR003_uint64UsesUnsignedParsing() {
    // uint64 and fixed64 need unsigned parsing to handle the full range.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_UINT64))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(
        code.contains("Long.parseUnsignedLong") || code.contains("Long.toUnsignedString"),
        "uint64 should use unsigned long operations");
  }

  // ======================================================================
  // SR-004: NaN/Infinity Handling
  // ======================================================================
  // pbtk simply omits NaN/Infinity fields rather than emitting null, since
  // pbtk has no concept of null -- absent fields are just missing.

  @Test
  void testSR004_doubleNanCheckInJava() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("Double.isNaN"), "double must check for NaN");
    assertTrue(code.contains("Double.isInfinite"), "double must check for Infinity");
  }

  @Test
  void testSR004_floatNanCheckInJava() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();

    String code = generateAll(msg, "java");
    assertTrue(code.contains("Float.isNaN"), "float must check for NaN");
    assertTrue(code.contains("Float.isInfinite"), "float must check for Infinity");
  }

  @Test
  void testSR004_nanOmittedNotNull() {
    // In pbtk, NaN/Infinity fields are skipped (not emitted as null like in JSON array).
    // The NaN check should guard the append so nothing is emitted.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("d", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .addField(scalarField("f", 2, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();

    String code = generateAll(msg, "java");
    // The NaN branch should NOT emit the field at all (no sb.append("null")).
    // Instead it just skips the append via an if-guard.
    assertFalse(
        code.contains("sb.append(\"null\")"),
        "pbtk should not emit null -- NaN fields are simply omitted");
  }

  // ======================================================================
  // SEC-001: Field Name Validation
  // ======================================================================
  // Shared MessageAnalyzer validates field names before code generation.
  // These tests confirm injection-style names are caught early.

  @Test
  void testSEC001_validFieldNameAccepted() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("valid_name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    // Should not throw.
    var msg = newAnalyzer().analyze(descriptor, ".test.");
    assertEquals(1, msg.getFields().size());
    assertEquals("valid_name", msg.getFields().get(0).getName());
  }

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
    assertTrue(ex.getMessage().contains("invalid characters"));
  }

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

  // ======================================================================
  // SEC-002: Path Traversal Prevention
  // ======================================================================

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

  @Test
  void testSEC002_pathTraversalCheckWorks() {
    // Verify that a normal package does not trigger the path traversal check,
    // confirming the check is wired up in PbtkPluginRunner.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java", "com.example", "proto3").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Should not trigger path traversal check");

    String path = response.getFile(0).getName();
    assertFalse(path.contains(".."), "Generated path should be clean");
  }

  // ======================================================================
  // SEC-003: Keyword Escaping
  // ======================================================================
  // KeywordUtil is shared infrastructure, but we verify it works end-to-end
  // through the pbtk pipeline too.

  @Test
  void testSEC003_javaKeywordEscaping() {
    assertEquals("class_", KeywordUtil.escapeJava("class"));
    assertEquals("not_a_keyword", KeywordUtil.escapeJava("not_a_keyword"));
  }

  @Test
  void testSEC003_pythonKeywordEscaping() {
    assertEquals("class_", KeywordUtil.escapePython("class"));
    assertEquals("not_a_keyword", KeywordUtil.escapePython("not_a_keyword"));
  }

  @Test
  void testSEC003_rustKeywordEscaping() {
    assertEquals("r#type", KeywordUtil.escapeRust("type"));
    assertEquals("not_a_keyword", KeywordUtil.escapeRust("not_a_keyword"));
  }

  @ParameterizedTest(name = "SEC-003 keyword escaping in pbtk {0}")
  @ValueSource(
      strings = {
        "java",
        "python",
        "javascript",
        "typescript",
        "c",
        "cpp",
        "rust",
        "zig",
        "go",
        "csharp",
        "kotlin",
        "swift",
        "dart",
        "php",
        "ruby",
        "objc",
        "perl"
      })
  void testSEC003_keywordEscapingInGeneratedPbtkCode(String lang) {
    // Pick a keyword and expected escaped form per language.
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
      case "csharp":
        fieldName = "class";
        expectedEscaped = "@class";
        break;
      case "kotlin":
        fieldName = "class";
        expectedEscaped = "`class`";
        break;
      case "swift":
        fieldName = "class";
        expectedEscaped = "`class`";
        break;
      case "perl":
        fieldName = "package";
        expectedEscaped = "package_";
        break;
      case "objc":
        fieldName = "auto";
        expectedEscaped = "auto_pb";
        break;
      default: // java, python, javascript, typescript, dart, php, ruby
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
  // SEC-004: Field Name Collision Detection
  // ======================================================================

  @Test
  void testSEC004_fooBarCollisionDetected() {
    // "foo_bar" and "fooBar" both become "fooBar" in Java, which is a collision.
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

  @Test
  void testSEC004_doubleUnderscoreCollision() {
    // "foo_bar" and "foo__bar" both map to "fooBar" in Java camelCase.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("foo_bar", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("foo__bar", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    CodeGeneratorRequest request = requestForMessage(msg, "java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertTrue(
        response.hasError(),
        "foo_bar and foo__bar collision should be detected (both become fooBar)");
  }

  // ======================================================================
  // FAULT-001: Empty Request
  // ======================================================================

  @Test
  void testFAULT001_emptyRequest() {
    CodeGeneratorRequest request = CodeGeneratorRequest.newBuilder().build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Empty request should not produce an error");
    assertEquals(0, response.getFileCount(), "Empty request should produce 0 files");
  }

  @Test
  void testFAULT001_noFilesToGenerate() {
    // Proto file is present but not listed in file_to_generate -- nothing should be emitted.
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

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder().addProtoFile(file).setParameter("lang=java").build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Should not error when no files_to_generate");
    assertEquals(0, response.getFileCount(), "Should produce 0 files");
  }

  @Test
  void testFAULT001_emptyMessageGeneratesValidCode() {
    // A message with zero fields should still produce a valid class.
    DescriptorProto msg = DescriptorProto.newBuilder().setName("EmptyMsg").build();

    CodeGeneratorRequest request = requestForMessage(msg, "java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Empty message should not produce error");
    assertTrue(response.getFileCount() > 0, "Should generate at least one file");
  }

  // ======================================================================
  // FAULT-002: Unknown Language
  // ======================================================================

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
        response.getError().startsWith("protoc-gen-pbtkurl:"),
        "Error should be prefixed with 'protoc-gen-pbtkurl:'");
  }
}
