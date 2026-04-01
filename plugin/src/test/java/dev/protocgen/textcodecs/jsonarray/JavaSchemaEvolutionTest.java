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
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.pbtkurl.PbtkPluginRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that generated Java code embodies the correct schema evolution contract. Since generated
 * code cannot be compiled within JUnit, we inspect the generated source for specific behavioral
 * patterns that ensure forward and backward compatibility.
 *
 * <p>Covers: array sizing, bounds-checked deserialization, gap handling, optional field presence,
 * pbtk URL evolution, and cross-format consistency.
 */
class JavaSchemaEvolutionTest {

  private final PluginRunner jsonArrayRunner = new PluginRunner();
  private final PbtkPluginRunner pbtkRunner = new PbtkPluginRunner();

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

  private FieldDescriptorProto messageField(String name, int number, String typeName) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName(typeName)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  /** Generate JSON array Java code for a single proto3 message. */
  private String generateJsonArray(DescriptorProto msg) {
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = jsonArrayRunner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Expected at least one generated file");
    return response.getFile(0).getContent();
  }

  /** Generate pbtk URL Java code for a single proto3 message. */
  private String generatePbtk(DescriptorProto msg) {
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = pbtkRunner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Expected at least one generated file");
    return response.getFile(0).getContent();
  }

  // ======================================================================
  // 1. v2 serializer produces 3-element array
  // ======================================================================

  @Test
  void testV2SerializerProduces3ElementArray() {
    DescriptorProto v2User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generateJsonArray(v2User);

    // The serializer appends exactly 3 elements separated by commas inside brackets
    assertTrue(code.contains("appendJsonArray(StringBuilder sb)"), "serializer method present");

    // Verify it serializes firstname at position 0
    assertTrue(
        code.contains("appendQuotedString(sb, this.firstname)"),
        "v2 serializer references firstname field");
    // Verify it serializes lastname at position 1
    assertTrue(
        code.contains("appendQuotedString(sb, this.lastname)"),
        "v2 serializer references lastname field");
    // Verify it serializes age at position 2
    assertTrue(code.contains("sb.append(this.age)"), "v2 serializer references age field");

    // Verify the array structure: two commas means 3 elements
    // The pattern is sb.append('[') ... sb.append(',') ... sb.append(',') ... sb.append(']')
    String appendMethod = extractMethod(code, "void appendJsonArray(StringBuilder sb)");
    int commaCount = countOccurrences(appendMethod, "sb.append(',')");
    assertTrue(
        commaCount == 2,
        "v2 serializer should emit exactly 2 commas for 3 fields, got " + commaCount);
  }

  // ======================================================================
  // 2. v1 serializer produces 2-element array
  // ======================================================================

  @Test
  void testV1SerializerProduces2ElementArray() {
    DescriptorProto v1User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generateJsonArray(v1User);

    // Verify only 2 fields are serialized
    assertTrue(
        code.contains("appendQuotedString(sb, this.firstname)"),
        "v1 serializer references firstname");
    assertTrue(
        code.contains("appendQuotedString(sb, this.lastname)"),
        "v1 serializer references lastname");
    assertFalse(code.contains("this.age"), "v1 serializer must not reference age field");

    // Verify array size: 1 comma means 2 elements
    String appendMethod = extractMethod(code, "void appendJsonArray(StringBuilder sb)");
    int commaCount = countOccurrences(appendMethod, "sb.append(',')");
    assertTrue(
        commaCount == 1,
        "v1 serializer should emit exactly 1 comma for 2 fields, got " + commaCount);
  }

  // ======================================================================
  // 3. v2 deserializer handles short arrays (forward compat)
  // ======================================================================

  @Test
  void testV2DeserializerHandlesShortArrays() {
    DescriptorProto v2User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generateJsonArray(v2User);

    // The deserializer must check array size before accessing each position.
    // Position 0 -> "if (size > 0 ...)"
    // Position 1 -> "if (size > 1 ...)"
    // Position 2 -> "if (size > 2 ...)"
    assertTrue(code.contains("if (size > 0"), "deserializer checks bounds for position 0");
    assertTrue(code.contains("if (size > 1"), "deserializer checks bounds for position 1");
    assertTrue(code.contains("if (size > 2"), "deserializer checks bounds for position 2");

    // Verify the age field (position 2) is guarded by a size > 2 check
    // and uses a proper cast for int32
    assertTrue(
        code.contains("((Number) array.get(2)).intValue()"),
        "deserializer reads age from position 2 with Number cast");

    // The default value for int32 is 0 -- if position 2 is absent, the builder default applies
    // (the builder initializes age = 0, so no explicit else branch is needed for proto3)
    assertTrue(code.contains("private int age = 0"), "builder initializes age to 0 (default)");
  }

  // ======================================================================
  // 4. v1 deserializer ignores extra elements (backward compat)
  // ======================================================================

  @Test
  void testV1DeserializerIgnoresExtraElements() {
    DescriptorProto v1User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generateJsonArray(v1User);

    // v1 deserializer should only access positions 0 and 1
    assertTrue(code.contains("array.get(0)"), "v1 deserializer accesses position 0");
    assertTrue(code.contains("array.get(1)"), "v1 deserializer accesses position 1");
    assertFalse(code.contains("array.get(2)"), "v1 deserializer must not access position 2");

    // v1 only checks bounds up to its known fields
    assertTrue(code.contains("if (size > 0"), "v1 deserializer checks bounds for position 0");
    assertTrue(code.contains("if (size > 1"), "v1 deserializer checks bounds for position 1");
    assertFalse(code.contains("if (size > 2"), "v1 deserializer has no check for position 2");

    // The deserializer does not throw -- it silently returns builder.build()
    // regardless of how many elements are in the array
    assertTrue(code.contains("return builder.build()"), "v1 deserializer returns built message");
  }

  // ======================================================================
  // 5. Field removal with gap produces null
  // ======================================================================

  @Test
  void testFieldGapProducesNull() {
    // Schema: { string name = 1; reserved 2; int32 value = 3; }
    // We model this by having fields at numbers 1 and 3, with a gap at 2.
    DescriptorProto gapMsg =
        DescriptorProto.newBuilder()
            .setName("Sparse")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("value", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generateJsonArray(gapMsg);

    // The serializer must emit null for the gap at position 1 (field number 2)
    assertTrue(
        code.contains("sb.append(\"null\")"),
        "serializer emits null for gap at reserved field number 2");

    // Verify the gap comment is present
    assertTrue(
        code.contains("gap (no field number 2)"),
        "serializer includes comment about gap at field number 2");

    // The deserializer should skip position 1 (the gap)
    assertTrue(
        code.contains("// position 1: gap (no field)"),
        "deserializer includes comment about gap at position 1");

    // Verify position 0 (name) and position 2 (value) are read, but position 1 is not
    assertTrue(code.contains("array.get(0)"), "deserializer reads name at position 0");
    assertTrue(code.contains("array.get(2)"), "deserializer reads value at position 2");
    assertFalse(code.contains("array.get(1)"), "deserializer does not read gap at position 1");

    // The serializer produces 3-element array: ["name", null, value]
    String appendMethod = extractMethod(code, "void appendJsonArray(StringBuilder sb)");
    int commaCount = countOccurrences(appendMethod, "sb.append(',')");
    assertTrue(
        commaCount == 2,
        "gap message serializer should emit 2 commas for 3 positions, got " + commaCount);
  }

  // ======================================================================
  // 6. Adding optional field to existing schema
  // ======================================================================

  @Test
  void testAddingOptionalFieldToSchema() {
    // v1: { string name = 1; }
    DescriptorProto v1Msg =
        DescriptorProto.newBuilder()
            .setName("Profile")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String v1Code = generateJsonArray(v1Msg);

    // v1 has no presence tracking (proto3 scalar, no optional keyword)
    assertFalse(v1Code.contains("presentFields_"), "v1 should not have presence tracking");
    assertFalse(v1Code.contains("BitSet"), "v1 should not use BitSet");

    // v2: { string name = 1; optional string nickname = 2; }
    FieldDescriptorProto optField =
        FieldDescriptorProto.newBuilder()
            .setName("nickname")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setProto3Optional(true)
            .setOneofIndex(0)
            .build();

    DescriptorProto v2Msg =
        DescriptorProto.newBuilder()
            .setName("Profile")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(optField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_nickname").build())
            .build();

    String v2Code = generateJsonArray(v2Msg);

    // v2 has presence tracking for the optional field
    assertTrue(v2Code.contains("presentFields_"), "v2 should have presence tracking");
    assertTrue(v2Code.contains("BitSet"), "v2 should use BitSet for presence tracking");
    assertTrue(v2Code.contains("hasNickname"), "v2 should generate has method for nickname");

    // v2 serializer checks presence before emitting nickname
    assertTrue(
        v2Code.contains("presentFields_.get(1)"),
        "v2 serializer checks presence bit for nickname (position 1)");

    // v2 serializer emits null when optional nickname is not present
    assertTrue(
        v2Code.contains("sb.append(\"null\")"),
        "v2 serializer emits null when optional field is not set");

    // v2 deserializer checks bounds for both positions
    assertTrue(v2Code.contains("if (size > 0"), "v2 deserializer checks bounds for name");
    assertTrue(v2Code.contains("if (size > 1"), "v2 deserializer checks bounds for nickname");

    // v2 deserializer sets the presence bit when nickname is present in the array
    assertTrue(
        v2Code.contains("presentFields_.set(1)"),
        "v2 deserializer sets presence bit when nickname is present");
  }

  // ======================================================================
  // 7. Pbtk URL schema evolution
  // ======================================================================

  @Test
  void testPbtkUrlV1SerializerOutput() {
    DescriptorProto v1User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generatePbtk(v1User);

    // v1 serializer produces !1s<firstname>!2s<lastname>
    assertTrue(code.contains("\"!1s\""), "v1 pbtk serializer has !1s for firstname");
    assertTrue(code.contains("\"!2s\""), "v1 pbtk serializer has !2s for lastname");
    assertFalse(code.contains("\"!3i\""), "v1 pbtk serializer must not have !3i");

    // v1 serializer uses URLEncoder for string fields
    assertTrue(
        code.contains("URLEncoder.encode(this.firstname"),
        "v1 pbtk serializer URL-encodes firstname");
    assertTrue(
        code.contains("URLEncoder.encode(this.lastname"),
        "v1 pbtk serializer URL-encodes lastname");
  }

  @Test
  void testPbtkUrlV2SerializerOutput() {
    DescriptorProto v2User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generatePbtk(v2User);

    // v2 serializer produces !1s<firstname>!2s<lastname>!3i<age>
    assertTrue(code.contains("\"!1s\""), "v2 pbtk serializer has !1s for firstname");
    assertTrue(code.contains("\"!2s\""), "v2 pbtk serializer has !2s for lastname");
    assertTrue(code.contains("\"!3i\""), "v2 pbtk serializer has !3i for age");

    // v2 serializer appends the age value
    assertTrue(code.contains("append(this.age)"), "v2 pbtk serializer appends age value");
  }

  @Test
  void testPbtkUrlV2DeserializerHandlesMissingAge() {
    DescriptorProto v2User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generatePbtk(v2User);

    // v2 deserializer uses switch on field number -- unknown fields hit the default case
    assertTrue(code.contains("switch (fieldNum)"), "pbtk deserializer uses switch on fieldNum");
    assertTrue(code.contains("case 1:"), "pbtk deserializer handles field 1");
    assertTrue(code.contains("case 2:"), "pbtk deserializer handles field 2");
    assertTrue(code.contains("case 3:"), "pbtk deserializer handles field 3");

    // When age is missing from input, the builder default (0) applies
    assertTrue(code.contains("private int age = 0"), "builder initializes age to 0");

    // The default case skips unknown tokens without error
    assertTrue(code.contains("default:"), "pbtk deserializer has default case for unknown fields");
  }

  @Test
  void testPbtkUrlV1DeserializerIgnoresUnknownTokens() {
    DescriptorProto v1User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generatePbtk(v1User);

    // v1 deserializer has cases only for fields 1 and 2
    assertTrue(code.contains("case 1:"), "v1 pbtk deserializer handles field 1");
    assertTrue(code.contains("case 2:"), "v1 pbtk deserializer handles field 2");
    assertFalse(code.contains("case 3:"), "v1 pbtk deserializer must not have case 3");

    // The default case silently skips unknown field tokens (like !3i30)
    assertTrue(
        code.contains("default:"), "v1 pbtk deserializer has default case to skip unknown fields");

    // The deserializer increments offset and consumed for unknown tokens, then continues
    assertTrue(
        code.contains("offset[0]++; consumed++; break"),
        "v1 pbtk deserializer advances past unknown tokens");
  }

  // ======================================================================
  // 8. Cross-format consistency
  // ======================================================================

  @Test
  void testCrossFormatConsistency() {
    DescriptorProto v2User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String jsonArrayCode = generateJsonArray(v2User);
    String pbtkCode = generatePbtk(v2User);

    // Both formats reference the same field numbers
    // JSON array uses positional encoding: field_number - 1 = position
    assertTrue(
        jsonArrayCode.contains("FIRSTNAME_FIELD_NUMBER = 1"),
        "JSON array code has FIRSTNAME_FIELD_NUMBER = 1");
    assertTrue(
        jsonArrayCode.contains("LASTNAME_FIELD_NUMBER = 2"),
        "JSON array code has LASTNAME_FIELD_NUMBER = 2");
    assertTrue(
        jsonArrayCode.contains("AGE_FIELD_NUMBER = 3"), "JSON array code has AGE_FIELD_NUMBER = 3");

    assertTrue(
        pbtkCode.contains("FIRSTNAME_FIELD_NUMBER = 1"),
        "pbtk code has FIRSTNAME_FIELD_NUMBER = 1");
    assertTrue(
        pbtkCode.contains("LASTNAME_FIELD_NUMBER = 2"), "pbtk code has LASTNAME_FIELD_NUMBER = 2");
    assertTrue(pbtkCode.contains("AGE_FIELD_NUMBER = 3"), "pbtk code has AGE_FIELD_NUMBER = 3");

    // Both formats handle the same set of fields in their serializers
    // JSON array: positional encoding at positions 0, 1, 2
    assertTrue(
        jsonArrayCode.contains("this.firstname"), "JSON array serializer accesses firstname");
    assertTrue(jsonArrayCode.contains("this.lastname"), "JSON array serializer accesses lastname");
    assertTrue(jsonArrayCode.contains("this.age"), "JSON array serializer accesses age");

    // pbtk URL: tag-based encoding with !1s, !2s, !3i
    assertTrue(pbtkCode.contains("this.firstname"), "pbtk serializer accesses firstname");
    assertTrue(pbtkCode.contains("this.lastname"), "pbtk serializer accesses lastname");
    assertTrue(pbtkCode.contains("this.age"), "pbtk serializer accesses age");

    // Both formats handle the same set of fields in their deserializers
    // JSON array: positional access at array.get(0), array.get(1), array.get(2)
    assertTrue(jsonArrayCode.contains("setFirstname"), "JSON array deserializer sets firstname");
    assertTrue(jsonArrayCode.contains("setLastname"), "JSON array deserializer sets lastname");
    assertTrue(jsonArrayCode.contains("setAge"), "JSON array deserializer sets age");

    // pbtk URL: case-based dispatch for fields 1, 2, 3
    assertTrue(pbtkCode.contains("setFirstname"), "pbtk deserializer sets firstname");
    assertTrue(pbtkCode.contains("setLastname"), "pbtk deserializer sets lastname");
    assertTrue(pbtkCode.contains("setAge"), "pbtk deserializer sets age");

    // Both formats use the same builder pattern
    assertTrue(jsonArrayCode.contains("newBuilder()"), "JSON array uses builder pattern");
    assertTrue(pbtkCode.contains("newBuilder()"), "pbtk uses builder pattern");
    assertTrue(jsonArrayCode.contains("builder.build()"), "JSON array builds message");
    assertTrue(pbtkCode.contains("builder.build()"), "pbtk builds message");
  }

  // ======================================================================
  // 9. Oneof evolution — adding new members
  // ======================================================================

  @Test
  void testOneofEvolutionAddingNewMember() {
    // v1: message Contact { oneof info { string email = 1; string phone = 2; } }
    FieldDescriptorProto v1Email =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto v1Phone =
        FieldDescriptorProto.newBuilder()
            .setName("phone")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto v1Contact =
        DescriptorProto.newBuilder()
            .setName("Contact")
            .addField(v1Email)
            .addField(v1Phone)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("info").build())
            .build();

    String v1Code = generateJsonArray(v1Contact);

    // v1 serializer produces 2-element array (positions 0 and 1)
    String v1AppendMethod = extractMethod(v1Code, "void appendJsonArray(StringBuilder sb)");
    int v1CommaCount = countOccurrences(v1AppendMethod, "sb.append(',')");
    assertTrue(
        v1CommaCount == 1,
        "v1 oneof serializer should emit 1 comma for 2 fields, got " + v1CommaCount);

    // v2: message Contact { oneof info { string email = 1; string phone = 2; string fax = 3; } }
    FieldDescriptorProto v2Fax =
        FieldDescriptorProto.newBuilder()
            .setName("fax")
            .setNumber(3)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto v2Contact =
        DescriptorProto.newBuilder()
            .setName("Contact")
            .addField(v1Email)
            .addField(v1Phone)
            .addField(v2Fax)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("info").build())
            .build();

    String v2Code = generateJsonArray(v2Contact);

    // v2 serializer produces 3-element array (positions 0, 1, 2)
    String v2AppendMethod = extractMethod(v2Code, "void appendJsonArray(StringBuilder sb)");
    int v2CommaCount = countOccurrences(v2AppendMethod, "sb.append(',')");
    assertTrue(
        v2CommaCount == 2,
        "v2 oneof serializer should emit 2 commas for 3 fields, got " + v2CommaCount);

    // v2 deserializer handles 2-element input: fax (position 2) defaults via bounds check
    assertTrue(
        v2Code.contains("if (size > 2"), "v2 deserializer checks bounds for fax at position 2");

    // v2 oneof case tracking includes the new fax member
    assertTrue(v2Code.contains("FAXCase_ = 3"), "v2 has FAX case constant using field number 3");

    // All three members are tracked in the same oneof group
    assertTrue(v2Code.contains("EMAILCase_ = 1"), "v2 has EMAIL case constant");
    assertTrue(v2Code.contains("PHONECase_ = 2"), "v2 has PHONE case constant");

    // Serializer checks case for each oneof member
    assertTrue(v2Code.contains("infoCase_ == EMAILCase_"), "v2 serializer checks EMAIL case");
    assertTrue(v2Code.contains("infoCase_ == PHONECase_"), "v2 serializer checks PHONE case");
    assertTrue(v2Code.contains("infoCase_ == FAXCase_"), "v2 serializer checks FAX case");

    // Inactive oneof members emit null
    assertTrue(
        v2Code.contains("else { sb.append(\"null\"); }"), "inactive oneof members emit null in v2");
  }

  // ======================================================================
  // 10. Enum evolution — adding new values
  // ======================================================================

  @Test
  void testEnumEvolutionAddingNewValues() {
    // v1: enum Status { UNKNOWN = 0; ACTIVE = 1; } in a message
    EnumDescriptorProto v1StatusEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1).build())
            .build();

    DescriptorProto v1Msg =
        DescriptorProto.newBuilder()
            .setName("Event")
            .addEnumType(v1StatusEnum)
            .addField(enumField("status", 1, ".test.Event.Status"))
            .build();

    String v1Code = generateJsonArray(v1Msg);

    // v1 enum has exactly 2 values
    assertTrue(v1Code.contains("UNKNOWN(0)"), "v1 enum has UNKNOWN value");
    assertTrue(v1Code.contains("ACTIVE(1)"), "v1 enum has ACTIVE value");
    assertFalse(v1Code.contains("SUSPENDED"), "v1 enum must not have SUSPENDED");

    // v2: enum Status { UNKNOWN = 0; ACTIVE = 1; SUSPENDED = 2; }
    EnumDescriptorProto v2StatusEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1).build())
            .addValue(
                EnumValueDescriptorProto.newBuilder().setName("SUSPENDED").setNumber(2).build())
            .build();

    DescriptorProto v2Msg =
        DescriptorProto.newBuilder()
            .setName("Event")
            .addEnumType(v2StatusEnum)
            .addField(enumField("status", 1, ".test.Event.Status"))
            .build();

    String v2Code = generateJsonArray(v2Msg);

    // v2 enum has 3 values
    assertTrue(v2Code.contains("UNKNOWN(0)"), "v2 enum has UNKNOWN value");
    assertTrue(v2Code.contains("ACTIVE(1)"), "v2 enum has ACTIVE value");
    assertTrue(v2Code.contains("SUSPENDED(2)"), "v2 enum has SUSPENDED value");

    // Deserialization uses forNumber() which returns null for unknown enum values
    assertTrue(
        v2Code.contains("Status.forNumber("),
        "v2 deserializer uses forNumber() for enum deserialization");

    // forNumber() iterates values and returns null if no match -- standard protobuf behavior
    assertTrue(
        v2Code.contains("public static Status forNumber(int number)"),
        "v2 enum has forNumber method");
    assertTrue(v2Code.contains("return null;"), "forNumber returns null for unknown enum values");

    // Serialization uses getNumber() with null-safe ternary
    assertTrue(
        v2Code.contains("this.status != null ? this.status.getNumber() : 0"),
        "v2 serializer handles null enum via ternary with default 0");
  }

  // ======================================================================
  // 11. Empty message evolution
  // ======================================================================

  @Test
  void testEmptyMessageEvolution() {
    // v1: message Empty {} (no fields)
    DescriptorProto v1Empty = DescriptorProto.newBuilder().setName("Empty").build();

    String v1Code = generateJsonArray(v1Empty);

    // v1 serializer produces empty array []
    String v1AppendMethod = extractMethod(v1Code, "void appendJsonArray(StringBuilder sb)");
    int v1CommaCount = countOccurrences(v1AppendMethod, "sb.append(',')");
    assertTrue(
        v1CommaCount == 0, "empty message serializer should emit 0 commas, got " + v1CommaCount);

    // v1 serializer appends '[' and ']' with nothing in between
    assertTrue(
        v1AppendMethod.contains("sb.append('[')"), "empty message serializer opens array bracket");
    assertTrue(
        v1AppendMethod.contains("sb.append(']')"), "empty message serializer closes array bracket");

    // v2: message Evolved { string name = 1; }
    DescriptorProto v2Evolved =
        DescriptorProto.newBuilder()
            .setName("Evolved")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String v2Code = generateJsonArray(v2Evolved);

    // v2 deserializer handles empty array input: name defaults to empty string
    assertTrue(
        v2Code.contains("if (size > 0"),
        "v2 deserializer checks bounds before accessing position 0");

    // The builder initializes name to empty string (proto3 default)
    assertTrue(
        v2Code.contains("private String name = \"\""),
        "v2 builder initializes name to empty string");

    // v2 deserializer returns builder.build() regardless of input size
    assertTrue(
        v2Code.contains("return builder.build()"),
        "v2 deserializer returns built message even for empty input");
  }

  // ======================================================================
  // 12. Nested message evolution
  // ======================================================================

  @Test
  void testNestedMessageEvolution() {
    // v1 Child: { string x = 1; }
    DescriptorProto v1Child =
        DescriptorProto.newBuilder()
            .setName("Child")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    // v1 Parent: { string name = 1; Child child = 2; }
    DescriptorProto v1Parent =
        DescriptorProto.newBuilder()
            .setName("Parent")
            .addNestedType(v1Child)
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(messageField("child", 2, ".test.Parent.Child"))
            .build();

    String v1Code = generateJsonArray(v1Parent);

    // v1 Child serializer produces 1-element nested array
    // Verify the nested Child class exists
    assertTrue(v1Code.contains("public static final class Child"), "v1 has nested Child class");

    // v1 serializes child via recursive appendJsonArray
    assertTrue(
        v1Code.contains("this.child.appendJsonArray(sb)"),
        "v1 serializer recursively serializes child");

    // v2 Child: { string x = 1; string y = 2; }
    DescriptorProto v2Child =
        DescriptorProto.newBuilder()
            .setName("Child")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("y", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    // v2 Parent: { string name = 1; Child child = 2; } (same structure, but Child is larger)
    DescriptorProto v2Parent =
        DescriptorProto.newBuilder()
            .setName("Parent")
            .addNestedType(v2Child)
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(messageField("child", 2, ".test.Parent.Child"))
            .build();

    String v2Code = generateJsonArray(v2Parent);

    // v2 Child serializer produces 2-element nested array
    // Extract the Child's appendJsonArray (it's nested inside the outer class)
    // We look for the Child class's serializer by checking comma count in the full code
    // The Child class has its own appendJsonArray with 1 comma for 2 fields
    assertTrue(v2Code.contains("public static final class Child"), "v2 has nested Child class");

    // v2 Child has both x and y fields
    assertTrue(v2Code.contains("private String x = \"\""), "v2 Child has x field");
    assertTrue(v2Code.contains("private String y = \"\""), "v2 Child has y field");

    // v2 Child deserializer uses bounds checking for both fields
    // Both v2 Parent and v2 Child use bounds checking
    assertTrue(v2Code.contains("if (size > 0"), "v2 deserializer checks bounds for position 0");
    assertTrue(v2Code.contains("if (size > 1"), "v2 deserializer checks bounds for position 1");

    // The nested fromJsonArray call uses bounds checking -- the Child.fromJsonArray
    // method receives a List<Object> and checks its size before accessing elements
    assertTrue(
        v2Code.contains("Child.fromJsonArray("),
        "v2 deserializer calls Child.fromJsonArray for nested message");

    // v1 Parent reading v2 output: v1 Parent accesses positions 0 and 1 (name and child),
    // and v1 Child's fromJsonArray only accesses position 0 (x), ignoring the extra y element.
    // This is structurally safe because Child.fromJsonArray bounds-checks with "if (size > 0)".
    // v1 Parent does not access position 2 since it only has 2 fields:
    assertFalse(
        v1Code.contains("if (size > 2"),
        "v1 Parent does not check size > 2 (only 2 fields: name and child)");

    // Both versions use null check before serializing the child message
    assertTrue(
        v1Code.contains("if (this.child != null)"), "v1 null-checks child before serialization");
    assertTrue(
        v2Code.contains("if (this.child != null)"), "v2 null-checks child before serialization");
  }

  // ======================================================================
  // Utility methods
  // ======================================================================

  /**
   * Extracts the body of a method from the generated source code. Finds the method signature and
   * returns everything up to the matching closing brace.
   */
  private static String extractMethod(String code, String methodSignature) {
    int start = code.indexOf(methodSignature);
    if (start < 0) {
      return "";
    }
    // Find the opening brace
    int braceStart = code.indexOf('{', start);
    if (braceStart < 0) {
      return "";
    }
    // Track brace depth to find the matching close
    int depth = 1;
    int i = braceStart + 1;
    while (i < code.length() && depth > 0) {
      char c = code.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
      }
      i++;
    }
    return code.substring(start, i);
  }

  /** Counts the number of non-overlapping occurrences of a substring. */
  private static int countOccurrences(String text, String sub) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(sub, idx)) >= 0) {
      count++;
      idx += sub.length();
    }
    return count;
  }
}
