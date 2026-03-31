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
package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRunnerTest {

  private final PluginRunner runner = new PluginRunner();

  /**
   * Build a minimal valid proto3 CodeGeneratorRequest with a single message containing one field.
   */
  private CodeGeneratorRequest.Builder minimalProto3Request() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("TestMsg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    return CodeGeneratorRequest.newBuilder().addProtoFile(file).addFileToGenerate("test.proto");
  }

  // ---- parseLanguage tests (exercised through run()) ----

  @Test
  void testDefaultLanguageWhenNoParameter() {
    // When no parameter is provided, the default language is java.
    // A successful Java generation confirms java was selected.
    CodeGeneratorRequest request = minimalProto3Request().build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    // Java generator should produce at least one file
    assertTrue(response.getFileCount() > 0, "Expected generated files for java");
  }

  @Test
  void testLangJava() {
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0);
  }

  @Test
  void testLangPython() {
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=python").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0);
  }

  @Test
  void testLangWithMixedCase() {
    // lang= values are lowercased internally, so "Java" should work
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=Java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
  }

  @Test
  void testEmptyParameter() {
    // Empty parameter should default to java
    CodeGeneratorRequest request = minimalProto3Request().setParameter("").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0);
  }

  @Test
  void testParameterWithoutLang() {
    // Parameter present but no lang= key should default to java
    CodeGeneratorRequest request =
        minimalProto3Request().setParameter("some_other_param=value").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
  }

  @Test
  void testLangUnknown() {
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=unknown").build();
    CodeGeneratorResponse response = runner.run(request);
    assertTrue(response.hasError());
    assertTrue(response.getError().contains("unsupported language"));
    assertTrue(response.getError().contains("unknown"));
  }

  @Test
  void testLangWithWhitespace() {
    // Extra whitespace around the lang= value should be trimmed
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang= java ").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
  }

  @Test
  void testLangInCommaDelimitedParams() {
    // lang= among other comma-separated parameters
    CodeGeneratorRequest request =
        minimalProto3Request().setParameter("other=foo,lang=python,bar=baz").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
  }

  // ---- proto2 support ----

  @Test
  void testProto2Support() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("LegacyMsg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("id")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    FileDescriptorProto proto2File =
        FileDescriptorProto.newBuilder()
            .setName("legacy.proto")
            .setPackage("legacy")
            .setSyntax("proto2")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(proto2File)
            .addFileToGenerate("legacy.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    // Proto2 is now supported — should not produce an error
    assertFalse(
        response.hasError(), "Proto2 should be supported but got error: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Should generate files for proto2 messages");
  }

  @Test
  void testProto2RequiredAndOptionalFields() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Proto2Msg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("name")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("age")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setDefaultValue("25")
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("tags")
                    .setNumber(3)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();

    FileDescriptorProto proto2File =
        FileDescriptorProto.newBuilder()
            .setName("proto2_msg.proto")
            .setPackage("test")
            .setSyntax("proto2")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(proto2File)
            .addFileToGenerate("proto2_msg.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0);

    // Verify the generated code contains presence tracking (BitSet)
    String content = response.getFile(0).getContent();
    assertTrue(content.contains("BitSet"), "Proto2 should have BitSet for presence tracking");
    assertTrue(content.contains("presentFields_"), "Proto2 should track field presence");
    // Should contain has* methods for all fields
    assertTrue(content.contains("hasName"), "Required field should have has method");
    assertTrue(content.contains("hasAge"), "Optional field should have has method");
  }

  @Test
  void testProto2WithSchemaDefaults() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("DefaultsMsg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("label")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setDefaultValue("unknown")
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("count")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setDefaultValue("10")
                    .build())
            .build();

    FileDescriptorProto proto2File =
        FileDescriptorProto.newBuilder()
            .setName("defaults.proto")
            .setPackage("test")
            .setSyntax("proto2")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(proto2File)
            .addFileToGenerate("defaults.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());

    String content = response.getFile(0).getContent();
    // Schema defaults should appear as field initializers
    assertTrue(content.contains("\"unknown\""), "String default should appear in generated code");
    assertTrue(content.contains("10"), "Int default should appear in generated code");
  }

  @Test
  void testProto2FileNotInFileToGenerate() {
    // A proto2 file that is a dependency but NOT in file_to_generate should not cause rejection
    DescriptorProto depMsg =
        DescriptorProto.newBuilder()
            .setName("DepMsg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("x")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    FileDescriptorProto proto2Dep =
        FileDescriptorProto.newBuilder()
            .setName("dep.proto")
            .setPackage("dep")
            .setSyntax("proto2")
            .addMessageType(depMsg)
            .build();

    CodeGeneratorRequest request =
        minimalProto3Request()
            .addProtoFile(proto2Dep)
            // Only "test.proto" is in file_to_generate, not "dep.proto"
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
  }

  // ---- supported features ----

  @Test
  void testSupportedFeaturesFlag() {
    CodeGeneratorRequest request = minimalProto3Request().build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertEquals(
        CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE,
        response.getSupportedFeatures());
  }

  // ---- all supported language aliases ----

  @Test
  void testAllLanguageAliases() {
    String[] languages = {
      "java",
      "python",
      "javascript",
      "js",
      "typescript",
      "ts",
      "c",
      "cpp",
      "c++",
      "csharp",
      "c#",
      "dart",
      "go",
      "kotlin",
      "kt",
      "objc",
      "objective-c",
      "perl",
      "php",
      "ruby",
      "rb",
      "rust",
      "swift",
      "zig"
    };
    for (String lang : languages) {
      CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=" + lang).build();
      CodeGeneratorResponse response = runner.run(request);
      assertFalse(
          response.hasError(),
          "Expected no error for lang=" + lang + ", got: " + response.getError());
      assertTrue(response.getFileCount() > 0, "Expected generated files for lang=" + lang);
    }
  }

  // ---- @generated marker ----

  @Test
  void testGeneratedMarkerInJavaOutput() {
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String content = response.getFile(0).getContent();
    assertTrue(
        content.startsWith("// @generated by protoc-gen-jsonarray"),
        "Java output should start with @generated marker");
  }

  // ---- equals() and hashCode() ----

  @Test
  void testEqualsMethodInJavaOutput() {
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String content = response.getFile(0).getContent();
    assertTrue(
        content.contains("public boolean equals(Object o)"),
        "Java output should contain equals method");
    assertTrue(
        content.contains("if (this == o) return true;"), "equals should have identity check");
    assertTrue(content.contains("instanceof TestMsg"), "equals should have instanceof check");
  }

  @Test
  void testHashCodeMethodInJavaOutput() {
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String content = response.getFile(0).getContent();
    assertTrue(
        content.contains("public int hashCode()"), "Java output should contain hashCode method");
    assertTrue(content.contains("Objects.hash("), "hashCode should use Objects.hash()");
  }

  @Test
  void testEqualsWithPrimitiveFields() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("PrimMsg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("count")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("active")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_BOOL)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("prim.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("prim.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String content = response.getFile(0).getContent();
    // Primitive fields should use == comparison
    assertTrue(
        content.contains("this.count == that.count"), "Primitive int should use == in equals");
    assertTrue(
        content.contains("this.active == that.active"), "Primitive bool should use == in equals");
  }

  @Test
  void testEqualsWithByteArrayField() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("BytesMsg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("data")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_BYTES)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("bytes.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("bytes.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String content = response.getFile(0).getContent();
    // byte[] fields should use Arrays.equals in equals()
    assertTrue(
        content.contains("Arrays.equals(this.data, that.data)"),
        "byte[] should use Arrays.equals in equals");
    // byte[] fields should use Arrays.hashCode in hashCode()
    assertTrue(
        content.contains("Arrays.hashCode(data)"), "byte[] should use Arrays.hashCode in hashCode");
  }

  // ---- source comment propagation ----

  @Test
  void testSourceCommentsInJavaOutput() {
    // Build a request with SourceCodeInfo containing comments
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Commented")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("name")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    // Path [4, 0] = first top-level message
    // Path [4, 0, 2, 0] = first field in first message
    SourceCodeInfo sourceCodeInfo =
        SourceCodeInfo.newBuilder()
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .setLeadingComments(" A test message.\n")
                    .build())
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .addPath(2)
                    .addPath(0)
                    .setLeadingComments(" The person's name.\n")
                    .build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("commented.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .setSourceCodeInfo(sourceCodeInfo)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("commented.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String content = response.getFile(0).getContent();
    // Message comment should appear as Javadoc before class
    assertTrue(
        content.contains("/** A test message. */"),
        "Message comment should appear as Javadoc, got:\n" + content);
    // Field comment should appear as Javadoc before getter
    assertTrue(
        content.contains("/** The person's name. */"),
        "Field comment should appear as Javadoc, got:\n" + content);
  }

  @Test
  void testNoCommentsWhenSourceCodeInfoAbsent() {
    // Standard request without SourceCodeInfo should still work
    CodeGeneratorRequest request = minimalProto3Request().setParameter("lang=java").build();
    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String content = response.getFile(0).getContent();
    // Should not contain any Javadoc (no /** ... */)
    assertFalse(content.contains("/**"), "No Javadoc should appear when SourceCodeInfo is absent");
  }
}
