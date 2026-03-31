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
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive audit tests for off-by-one errors, indexing bugs, and boundary conditions in the
 * protoc-gen-jsonarray plugin.
 *
 * <p>These tests verify the core invariant: position = fieldNumber - 1.
 */
class IndexingAuditTest {

  private MessageAnalyzer newAnalyzer() {
    return new MessageAnalyzer(new TypeRegistry());
  }

  private MessageAnalyzer newAnalyzer(TypeRegistry registry) {
    return new MessageAnalyzer(registry);
  }

  private FieldDescriptorProto scalarField(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  // ======================================================================
  // 1. ProtoField: arrayPosition = fieldNumber - 1
  // ======================================================================

  @Test
  void testArrayPositionIsFieldNumberMinusOne() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Test")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 5, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("c", 10, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    for (ProtoField field : msg.getFields()) {
      assertEquals(
          field.getFieldNumber() - 1,
          field.getArrayPosition(),
          "arrayPosition must be fieldNumber - 1 for field " + field.getName());
    }
  }

  // ======================================================================
  // 2. Sparse field gaps: fields at 1, 3, 5
  // ======================================================================

  @Test
  void testSparseFieldGaps_1_3_5() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Sparse")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 5, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");

    // maxFieldNumber = 5
    assertEquals(5, msg.getMaxFieldNumber());

    // 5 positions: 0..4
    // pos 0 -> field 1 ("a")
    assertNotNull(msg.fieldAtPosition(0));
    assertEquals("a", msg.fieldAtPosition(0).getName());
    assertEquals(0, msg.fieldAtPosition(0).getArrayPosition());

    // pos 1 -> null (gap at field number 2)
    assertNull(msg.fieldAtPosition(1));

    // pos 2 -> field 3 ("b")
    assertNotNull(msg.fieldAtPosition(2));
    assertEquals("b", msg.fieldAtPosition(2).getName());
    assertEquals(2, msg.fieldAtPosition(2).getArrayPosition());

    // pos 3 -> null (gap at field number 4)
    assertNull(msg.fieldAtPosition(3));

    // pos 4 -> field 5 ("c")
    assertNotNull(msg.fieldAtPosition(4));
    assertEquals("c", msg.fieldAtPosition(4).getName());
    assertEquals(4, msg.fieldAtPosition(4).getArrayPosition());

    // getFieldsByPosition should only have 3 entries
    Map<Integer, ProtoField> byPos = msg.getFieldsByPosition();
    assertEquals(3, byPos.size());
    assertTrue(byPos.containsKey(0));
    assertTrue(byPos.containsKey(2));
    assertTrue(byPos.containsKey(4));
  }

  @Test
  void testSparseFieldGaps_GeneratedJavaSerializerHas5Elements() {
    // Verify the generated Java code produces exactly 5 array elements for fields 1,3,5
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("SparseMsg")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 5, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("sparse.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("sparse.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = new PluginRunner().run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0);

    String content = response.getFile(0).getContent();

    // The appendJsonArray method should produce exactly 5 entries:
    // - sb.append(this.a) at pos 0 (field 1)
    // - sb.append("null") at pos 1 (gap for field number 2)
    // - sb.append(this.b) at pos 2 (field 3)
    // - sb.append("null") at pos 3 (gap for field number 4)
    // - sb.append(this.c) at pos 4 (field 5)
    assertTrue(
        content.contains("sb.append(\"null\"); // gap (no field number 2)"),
        "Should have null gap for field number 2");
    assertTrue(
        content.contains("sb.append(\"null\"); // gap (no field number 4)"),
        "Should have null gap for field number 4");

    // Count the total number of sb.append calls for field values and gaps in the appendJsonArray
    // method
    // There should be 3 field appends + 2 gap appends = 5 value appends
    String serializeBody = extractMethodBody(content, "appendJsonArray");
    int gapCount = countOccurrences(serializeBody, "sb.append(\"null\"); // gap");
    assertEquals(2, gapCount, "Should have exactly 2 gap null appends for fields 1,3,5");
  }

  // ======================================================================
  // 3. Empty message: zero fields
  // ======================================================================

  @Test
  void testEmptyMessage() {
    DescriptorProto descriptor = DescriptorProto.newBuilder().setName("Empty").build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    assertEquals(0, msg.getMaxFieldNumber());
    assertEquals(0, msg.getFields().size());
    assertTrue(msg.getFieldsByPosition().isEmpty());
  }

  @Test
  void testEmptyMessageGeneratesEmptyArray() {
    DescriptorProto msgDesc = DescriptorProto.newBuilder().setName("EmptyMsg").build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("empty.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msgDesc)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("empty.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = new PluginRunner().run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());

    String content = response.getFile(0).getContent();
    // The appendJsonArray method should just open and close the array with no field appends
    assertTrue(content.contains("sb.append('[')"));
    assertTrue(content.contains("sb.append(']')"));
    // No field value appends in appendJsonArray
    String serializeBody = extractMethodBody(content, "appendJsonArray");
    int fieldAppends = countOccurrences(serializeBody, "sb.append(this.");
    assertEquals(0, fieldAppends, "Empty message should produce zero field appends");
  }

  // ======================================================================
  // 4. Single field at field number 1
  // ======================================================================

  @Test
  void testSingleFieldAtNumber1() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Single")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    assertEquals(1, msg.getMaxFieldNumber());
    assertNotNull(msg.fieldAtPosition(0));
    assertEquals("val", msg.fieldAtPosition(0).getName());
    assertEquals(0, msg.fieldAtPosition(0).getArrayPosition());
  }

  // ======================================================================
  // 5. Deserialization: short array (size check)
  // ======================================================================

  @Test
  void testDeserializationShortArrayBoundsCheck() {
    // Message with 5 fields, receiving a 3-element array
    // Verify the generated code uses `size > pos` correctly
    DescriptorProto msgDesc =
        DescriptorProto.newBuilder()
            .setName("FiveFields")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("d", 4, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("e", 5, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("five.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msgDesc)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("five.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = new PluginRunner().run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());

    String content = response.getFile(0).getContent();
    // Should check "size > 0", "size > 1", "size > 2", "size > 3", "size > 4"
    assertTrue(content.contains("if (size > 0"), "Should check size > 0 for position 0");
    assertTrue(content.contains("if (size > 1"), "Should check size > 1 for position 1");
    assertTrue(content.contains("if (size > 2"), "Should check size > 2 for position 2");
    assertTrue(content.contains("if (size > 3"), "Should check size > 3 for position 3");
    assertTrue(content.contains("if (size > 4"), "Should check size > 4 for position 4");
  }

  // ======================================================================
  // 6. Oneof case tracking uses field numbers consistently
  // ======================================================================

  @Test
  void testOneofCaseUsesFieldNumber() {
    FieldDescriptorProto emailField =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(24)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto phoneField =
        FieldDescriptorProto.newBuilder()
            .setName("phone")
            .setNumber(25)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto msgDesc =
        DescriptorProto.newBuilder()
            .setName("ContactMsg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(emailField)
            .addField(phoneField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("contact_info").build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("contact.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msgDesc)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("contact.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = new PluginRunner().run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());

    String content = response.getFile(0).getContent();

    // The case constants should use field numbers (24, 25), not array positions (23, 24)
    assertTrue(
        content.contains("EMAILCase_ = 24"), "EMAIL case constant should use field number 24");
    assertTrue(
        content.contains("PHONECase_ = 25"), "PHONE case constant should use field number 25");

    // Serializer should compare against the same constants
    assertTrue(
        content.contains("contactInfoCase_ == EMAILCase_"),
        "Serializer should compare against EMAILCase_");
  }

  // ======================================================================
  // 7. Presence tracking uses arrayPosition consistently
  // ======================================================================

  @Test
  void testPresenceTrackingUsesArrayPosition() {
    // proto3 optional field at field number 5 -> arrayPosition 4
    FieldDescriptorProto optField =
        FieldDescriptorProto.newBuilder()
            .setName("opt_val")
            .setNumber(5)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setProto3Optional(true)
            .setOneofIndex(0)
            .build();

    DescriptorProto msgDesc =
        DescriptorProto.newBuilder()
            .setName("PresenceMsg")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("d", 4, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(optField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_opt_val").build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("presence.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msgDesc)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("presence.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = new PluginRunner().run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());

    String content = response.getFile(0).getContent();

    // Presence bit for field number 5 should use arrayPosition 4
    // Setter: this.presentFields_.set(4)
    assertTrue(
        content.contains("this.presentFields_.set(4)"),
        "Setter should use arrayPosition 4 for field number 5");

    // Serializer: presentFields_.get(4)
    assertTrue(
        content.contains("presentFields_.get(4)"),
        "Serializer should check presentFields_.get(4) for field number 5");

    // Deserializer uses Builder pattern: presence is set by Builder.setXxx() internally
    // Verify the deserializer uses Builder
    assertTrue(content.contains("builder.build()"), "Deserializer should use Builder pattern");
  }

  // ======================================================================
  // 8. Map field: field 1 is key, field 2 is value
  // ======================================================================

  @Test
  void testMapEntryFieldNumbers() {
    DescriptorProto mapEntryDescriptor =
        DescriptorProto.newBuilder()
            .setName("ThingsEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("key")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    FieldDescriptorProto mapField =
        FieldDescriptorProto.newBuilder()
            .setName("things")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".test.Container.ThingsEntry")
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Container")
            .addField(mapField)
            .addNestedType(mapEntryDescriptor)
            .build();

    TypeRegistry registry = new TypeRegistry();
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("container.proto")
            .setPackage("test")
            .addMessageType(descriptor)
            .build();
    registry.registerFile(file);

    ProtoMessage msg = newAnalyzer(registry).analyze(descriptor, ".test.");
    ProtoField field = msg.getFields().get(0);

    assertTrue(field.isMap());
    assertEquals(FieldDescriptorProto.Type.TYPE_STRING, field.getMapKeyType());
    assertEquals(FieldDescriptorProto.Type.TYPE_INT32, field.getMapValueType());
  }

  // ======================================================================
  // 9. All language generators: verify loop pattern consistency
  // ======================================================================

  @Test
  void testAllLanguagesSparseFieldGeneration() {
    // Generate code for all supported languages with sparse fields at 1,3,5
    // and verify each handles gaps correctly
    DescriptorProto msgDesc =
        DescriptorProto.newBuilder()
            .setName("LangTest")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 5, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("langtest.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msgDesc)
            .build();

    String[] languages = {
      "java",
      "python",
      "javascript",
      "typescript",
      "go",
      "rust",
      "cpp",
      "c",
      "zig",
      "csharp",
      "kotlin",
      "swift",
      "dart",
      "php",
      "ruby",
      "objc",
      "perl"
    };

    for (String lang : languages) {
      CodeGeneratorRequest request =
          CodeGeneratorRequest.newBuilder()
              .addProtoFile(file)
              .addFileToGenerate("langtest.proto")
              .setParameter("lang=" + lang)
              .build();

      CodeGeneratorResponse response = new PluginRunner().run(request);
      assertFalse(
          response.hasError(),
          "Expected no error for lang=" + lang + ", got: " + response.getError());
      assertTrue(response.getFileCount() > 0, "Expected generated files for lang=" + lang);

      // Verify at least one file has gap markers (some languages produce multiple files,
      // e.g. C produces .h and .c — the gap markers appear in the .c file)
      boolean hasContent = false;
      boolean hasGapMarker = false;
      for (int i = 0; i < response.getFileCount(); i++) {
        if (response.getFile(i).getContent() != null
            && !response.getFile(i).getContent().isEmpty()) {
          hasContent = true;
          String content = response.getFile(i).getContent();
          if (content.contains("gap") || content.contains("no field")) {
            hasGapMarker = true;
          }
        }
      }
      assertTrue(hasContent, "Language " + lang + " should produce non-empty content");
      assertTrue(
          hasGapMarker,
          "Language " + lang + " should have gap markers in at least one generated file");
    }
  }

  // ======================================================================
  // 10. Consecutive fields (no gaps): verify no null padding
  // ======================================================================

  @Test
  void testConsecutiveFieldsNoGaps() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("NoGaps")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("c", 3, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    assertEquals(3, msg.getMaxFieldNumber());

    // All positions should have fields, no gaps
    for (int pos = 0; pos < msg.getMaxFieldNumber(); pos++) {
      assertNotNull(msg.fieldAtPosition(pos), "Position " + pos + " should have a field (no gaps)");
    }
  }

  // ======================================================================
  // Helpers
  // ======================================================================

  /**
   * Extract the first method body matching the given method name from generated Java code. This is
   * a rough extraction for testing purposes only.
   */
  private String extractMethodBody(String code, String methodName) {
    int idx = code.indexOf(methodName + "(");
    if (idx < 0) return "";
    int braceStart = code.indexOf('{', idx);
    if (braceStart < 0) return "";
    int depth = 1;
    int i = braceStart + 1;
    while (i < code.length() && depth > 0) {
      if (code.charAt(i) == '{') depth++;
      else if (code.charAt(i) == '}') depth--;
      i++;
    }
    return code.substring(braceStart, i);
  }

  /** Count occurrences of a substring in a string. */
  private int countOccurrences(String text, String sub) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(sub, idx)) >= 0) {
      count++;
      idx += sub.length();
    }
    return count;
  }
}
