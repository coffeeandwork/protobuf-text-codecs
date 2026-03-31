/*
 * Copyright 2026 protobuf-text-codecs contributors
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
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import dev.protocgen.textcodecs.jsonarray.model.WellKnownType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageAnalyzerTest {

  private MessageAnalyzer newAnalyzer() {
    return new MessageAnalyzer(new TypeRegistry());
  }

  private MessageAnalyzer newAnalyzer(TypeRegistry registry) {
    return new MessageAnalyzer(registry);
  }

  // ---- helpers to build FieldDescriptorProto ----

  private FieldDescriptorProto scalarField(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  // ---- tests ----

  @Test
  void testSimpleMessage() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("age", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("active", 3, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    assertEquals("User", msg.getName());
    assertEquals(".example.User", msg.getFullName());

    List<ProtoField> fields = msg.getFields();
    assertEquals(3, fields.size());
    // Fields should be sorted by field number
    assertEquals("name", fields.get(0).getName());
    assertEquals(1, fields.get(0).getFieldNumber());
    assertEquals("age", fields.get(1).getName());
    assertEquals(2, fields.get(1).getFieldNumber());
    assertEquals("active", fields.get(2).getName());
    assertEquals(3, fields.get(2).getFieldNumber());
  }

  @Test
  void testFieldNumberGaps() {
    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Sparse")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 5, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".test.");
    assertEquals(5, msg.getMaxFieldNumber());

    // Position 0 -> field 1 ("a"), position 1 -> gap, position 2 -> field 3 ("b"), etc.
    assertNotNull(msg.fieldAtPosition(0));
    assertEquals("a", msg.fieldAtPosition(0).getName());
    assertNull(msg.fieldAtPosition(1)); // gap (field number 2)
    assertNotNull(msg.fieldAtPosition(2));
    assertEquals("b", msg.fieldAtPosition(2).getName());
    assertNull(msg.fieldAtPosition(3)); // gap (field number 4)
    assertNotNull(msg.fieldAtPosition(4));
    assertEquals("c", msg.fieldAtPosition(4).getName());
  }

  @Test
  void testEnumField() {
    FieldDescriptorProto enumField =
        FieldDescriptorProto.newBuilder()
            .setName("status")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_ENUM)
            .setTypeName(".example.Status")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Order").addField(enumField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    ProtoField field = msg.getFields().get(0);
    assertEquals(ProtoField.FieldKind.ENUM, field.getKind());
    assertEquals(".example.Status", field.getTypeReference());
  }

  @Test
  void testNestedMessage() {
    FieldDescriptorProto messageField =
        FieldDescriptorProto.newBuilder()
            .setName("address")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".example.Address")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Person").addField(messageField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    ProtoField field = msg.getFields().get(0);
    assertEquals(ProtoField.FieldKind.MESSAGE, field.getKind());
    assertEquals(".example.Address", field.getTypeReference());
  }

  @Test
  void testRepeatedField() {
    FieldDescriptorProto repeatedField =
        FieldDescriptorProto.newBuilder()
            .setName("tags")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Item").addField(repeatedField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    ProtoField field = msg.getFields().get(0);
    assertEquals(ProtoField.Cardinality.REPEATED, field.getCardinality());
    assertTrue(field.isRepeated());
  }

  @Test
  void testMapField() {
    // A map<string, int32> field in proto is represented as:
    // 1. A nested message "XxxEntry" with options.map_entry = true, containing
    //    field 1 (key) and field 2 (value)
    // 2. A repeated field of type MESSAGE pointing to that entry type

    DescriptorProto mapEntryDescriptor =
        DescriptorProto.newBuilder()
            .setName("LabelsEntry")
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
            .setName("labels")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".example.Resource.LabelsEntry")
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Resource")
            .addField(mapField)
            .addNestedType(mapEntryDescriptor)
            .build();

    // Register the map entry in a TypeRegistry so isMapEntry works
    TypeRegistry registry = new TypeRegistry();
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("resource.proto")
            .setPackage("example")
            .addMessageType(descriptor)
            .build();
    registry.registerFile(file);

    ProtoMessage msg = newAnalyzer(registry).analyze(descriptor, ".example.");
    ProtoField field = msg.getFields().get(0);

    assertEquals(ProtoField.Cardinality.MAP, field.getCardinality());
    assertTrue(field.isMap());
    assertEquals(FieldDescriptorProto.Type.TYPE_STRING, field.getMapKeyType());
    assertEquals(FieldDescriptorProto.Type.TYPE_INT32, field.getMapValueType());
  }

  @Test
  void testOneofFields() {
    FieldDescriptorProto emailField =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto phoneField =
        FieldDescriptorProto.newBuilder()
            .setName("phone")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Contact")
            .addField(emailField)
            .addField(phoneField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("contact_info").build())
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    List<ProtoField> fields = msg.getFields();

    assertEquals(2, fields.size());
    // Both fields should reference oneof index 0 with name "contact_info"
    for (ProtoField f : fields) {
      assertTrue(f.isOneofMember());
      assertEquals(0, f.getOneofIndex());
      assertEquals("contact_info", f.getOneofName());
    }

    // Verify oneof groups
    assertEquals(1, msg.getOneofGroups().size());
    ProtoMessage.OneofGroup group = msg.getOneofGroups().get(0);
    assertEquals("contact_info", group.name());
    assertEquals(2, group.members().size());
  }

  @Test
  void testProto3Optional() {
    FieldDescriptorProto optionalField =
        FieldDescriptorProto.newBuilder()
            .setName("nickname")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setProto3Optional(true)
            .setOneofIndex(0) // proto3 optional creates a synthetic oneof
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Profile")
            .addField(optionalField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_nickname").build())
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    ProtoField field = msg.getFields().get(0);

    assertTrue(field.isProto3Optional());
    // Proto3 optional should NOT be treated as a real oneof member
    assertFalse(field.isOneofMember());
    assertEquals(-1, field.getOneofIndex());
  }

  @Test
  void testWellKnownType() {
    FieldDescriptorProto tsField =
        FieldDescriptorProto.newBuilder()
            .setName("created_at")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".google.protobuf.Timestamp")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Event").addField(tsField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    ProtoField field = msg.getFields().get(0);

    assertEquals(ProtoField.FieldKind.WELL_KNOWN_TYPE, field.getKind());
    assertTrue(field.isWellKnownType());
    assertEquals(WellKnownType.TIMESTAMP, field.getWellKnownType());
    assertEquals(".google.protobuf.Timestamp", field.getTypeReference());
  }

  // ---- google.protobuf.Any rejection ----

  @Test
  void testAnyFieldThrows() {
    FieldDescriptorProto anyField =
        FieldDescriptorProto.newBuilder()
            .setName("payload")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".google.protobuf.Any")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Envelope").addField(anyField).build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".example."));
    assertTrue(ex.getMessage().contains("google.protobuf.Any"));
    assertTrue(ex.getMessage().contains("not supported"));
  }

  // ---- Positive field number validation ----

  @Test
  void testFieldNumberZeroThrows() {
    FieldDescriptorProto badField =
        FieldDescriptorProto.newBuilder()
            .setName("bad")
            .setNumber(0)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("BadMsg").addField(badField).build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".example."));
    assertTrue(ex.getMessage().contains("invalid field number"));
    assertTrue(ex.getMessage().contains("bad"));
  }

  @Test
  void testFieldNumberNegativeThrows() {
    FieldDescriptorProto badField =
        FieldDescriptorProto.newBuilder()
            .setName("negative")
            .setNumber(-1)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("NegMsg").addField(badField).build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> newAnalyzer().analyze(descriptor, ".example."));
    assertTrue(ex.getMessage().contains("invalid field number"));
  }

  // ---- Well-known type detection for ALL well-known types ----

  @Test
  void testWellKnownTypeDuration() {
    assertWellKnownTypeDetected(".google.protobuf.Duration", WellKnownType.DURATION);
  }

  @Test
  void testWellKnownTypeBoolValue() {
    assertWellKnownTypeDetected(".google.protobuf.BoolValue", WellKnownType.BOOL_VALUE);
  }

  @Test
  void testWellKnownTypeInt32Value() {
    assertWellKnownTypeDetected(".google.protobuf.Int32Value", WellKnownType.INT32_VALUE);
  }

  @Test
  void testWellKnownTypeInt64Value() {
    assertWellKnownTypeDetected(".google.protobuf.Int64Value", WellKnownType.INT64_VALUE);
  }

  @Test
  void testWellKnownTypeUInt32Value() {
    assertWellKnownTypeDetected(".google.protobuf.UInt32Value", WellKnownType.UINT32_VALUE);
  }

  @Test
  void testWellKnownTypeUInt64Value() {
    assertWellKnownTypeDetected(".google.protobuf.UInt64Value", WellKnownType.UINT64_VALUE);
  }

  @Test
  void testWellKnownTypeFloatValue() {
    assertWellKnownTypeDetected(".google.protobuf.FloatValue", WellKnownType.FLOAT_VALUE);
  }

  @Test
  void testWellKnownTypeDoubleValue() {
    assertWellKnownTypeDetected(".google.protobuf.DoubleValue", WellKnownType.DOUBLE_VALUE);
  }

  @Test
  void testWellKnownTypeStringValue() {
    assertWellKnownTypeDetected(".google.protobuf.StringValue", WellKnownType.STRING_VALUE);
  }

  @Test
  void testWellKnownTypeBytesValue() {
    assertWellKnownTypeDetected(".google.protobuf.BytesValue", WellKnownType.BYTES_VALUE);
  }

  @Test
  void testWellKnownTypeStruct() {
    assertWellKnownTypeDetected(".google.protobuf.Struct", WellKnownType.STRUCT);
  }

  @Test
  void testWellKnownTypeValue() {
    assertWellKnownTypeDetected(".google.protobuf.Value", WellKnownType.VALUE);
  }

  @Test
  void testWellKnownTypeListValue() {
    assertWellKnownTypeDetected(".google.protobuf.ListValue", WellKnownType.LIST_VALUE);
  }

  @Test
  void testWellKnownTypeFieldMask() {
    assertWellKnownTypeDetected(".google.protobuf.FieldMask", WellKnownType.FIELD_MASK);
  }

  @Test
  void testWellKnownTypeEmpty() {
    assertWellKnownTypeDetected(".google.protobuf.Empty", WellKnownType.EMPTY);
  }

  /**
   * Note: .google.protobuf.Any is NOT tested here because the analyzer throws an
   * IllegalArgumentException for Any fields (tested in testAnyFieldThrows).
   */
  private void assertWellKnownTypeDetected(String typeName, WellKnownType expected) {
    FieldDescriptorProto field =
        FieldDescriptorProto.newBuilder()
            .setName("wkt_field")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(typeName)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("WktMsg").addField(field).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    ProtoField pf = msg.getFields().get(0);
    assertEquals(ProtoField.FieldKind.WELL_KNOWN_TYPE, pf.getKind());
    assertTrue(pf.isWellKnownType());
    assertEquals(expected, pf.getWellKnownType());
    assertEquals(typeName, pf.getTypeReference());
  }

  // ---- enum analysis ----

  @Test
  void testAnalyzeEnum() {
    com.google.protobuf.DescriptorProtos.EnumDescriptorProto enumProto =
        com.google.protobuf.DescriptorProtos.EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(
                com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto.newBuilder()
                    .setName("UNKNOWN")
                    .setNumber(0)
                    .build())
            .addValue(
                com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto.newBuilder()
                    .setName("ACTIVE")
                    .setNumber(1)
                    .build())
            .build();

    dev.protocgen.textcodecs.jsonarray.model.ProtoEnum result =
        newAnalyzer().analyzeEnum(enumProto, ".example.");

    assertEquals("Status", result.getName());
    assertEquals(".example.Status", result.getFullName());
    assertEquals(2, result.getValues().size());
    assertEquals("UNKNOWN", result.getValues().get(0).name());
    assertEquals(0, result.getValues().get(0).number());
    assertEquals("ACTIVE", result.getValues().get(1).name());
    assertEquals(1, result.getValues().get(1).number());
  }

  // ---- proto2 support ----

  @Test
  void testProto2RequiredField() {
    FieldDescriptorProto requiredField =
        FieldDescriptorProto.newBuilder()
            .setName("name")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Person").addField(requiredField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto2");
    ProtoField field = msg.getFields().get(0);

    assertTrue(field.isRequired());
    assertTrue(field.hasExplicitPresence());
    assertFalse(field.isProto3Optional());
  }

  @Test
  void testProto2OptionalFieldWithDefault() {
    FieldDescriptorProto optionalField =
        FieldDescriptorProto.newBuilder()
            .setName("nickname")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("anonymous")
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Profile").addField(optionalField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto2");
    ProtoField field = msg.getFields().get(0);

    assertFalse(field.isRequired());
    assertTrue(field.hasExplicitPresence());
    assertEquals("anonymous", field.getDefaultValue());
    assertFalse(field.isProto3Optional());
  }

  @Test
  void testProto2OptionalFieldWithoutDefault() {
    FieldDescriptorProto optionalField =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Contact").addField(optionalField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto2");
    ProtoField field = msg.getFields().get(0);

    assertFalse(field.isRequired());
    assertTrue(field.hasExplicitPresence());
    assertNull(field.getDefaultValue());
  }

  @Test
  void testProto2AllSingularFieldsHaveExplicitPresence() {
    FieldDescriptorProto requiredField =
        FieldDescriptorProto.newBuilder()
            .setName("id")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED)
            .build();

    FieldDescriptorProto optionalField =
        FieldDescriptorProto.newBuilder()
            .setName("name")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    FieldDescriptorProto repeatedField =
        FieldDescriptorProto.newBuilder()
            .setName("tags")
            .setNumber(3)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Mixed")
            .addField(requiredField)
            .addField(optionalField)
            .addField(repeatedField)
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto2");

    // Required and optional should have explicit presence
    assertTrue(msg.getFields().get(0).hasExplicitPresence()); // id (required)
    assertTrue(msg.getFields().get(1).hasExplicitPresence()); // name (optional)
    // Repeated should NOT have explicit presence
    assertFalse(msg.getFields().get(2).hasExplicitPresence()); // tags (repeated)
  }

  @Test
  void testProto3FieldsDoNotHaveExplicitPresenceByDefault() {
    FieldDescriptorProto regularField =
        FieldDescriptorProto.newBuilder()
            .setName("name")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Simple").addField(regularField).build();

    // Use proto3 syntax (default)
    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto3");
    ProtoField field = msg.getFields().get(0);

    assertFalse(field.hasExplicitPresence());
    assertFalse(field.isRequired());
    assertNull(field.getDefaultValue());
  }

  @Test
  void testProto2EnumFieldWithDefault() {
    FieldDescriptorProto enumField =
        FieldDescriptorProto.newBuilder()
            .setName("priority")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_ENUM)
            .setTypeName(".example.Priority")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("HIGH")
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Task").addField(enumField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto2");
    ProtoField field = msg.getFields().get(0);

    assertEquals(ProtoField.FieldKind.ENUM, field.getKind());
    assertTrue(field.hasExplicitPresence());
    assertEquals("HIGH", field.getDefaultValue());
  }

  @Test
  void testProto2GroupField() {
    // Groups in proto2 use TYPE_GROUP and reference a nested DescriptorProto
    FieldDescriptorProto groupField =
        FieldDescriptorProto.newBuilder()
            .setName("mygroup")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_GROUP)
            .setTypeName(".example.Container.MyGroup")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto groupDescriptor =
        DescriptorProto.newBuilder()
            .setName("MyGroup")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder()
            .setName("Container")
            .addField(groupField)
            .addNestedType(groupDescriptor)
            .build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto2");
    ProtoField field = msg.getFields().get(0);

    // Group should be treated as MESSAGE
    assertEquals(ProtoField.FieldKind.MESSAGE, field.getKind());
    assertEquals(".example.Container.MyGroup", field.getTypeReference());
    // Proto type should be normalized to TYPE_MESSAGE
    assertEquals(FieldDescriptorProto.Type.TYPE_MESSAGE, field.getProtoType());
  }

  @Test
  void testProto2IntFieldWithDefault() {
    FieldDescriptorProto intField =
        FieldDescriptorProto.newBuilder()
            .setName("count")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("42")
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Counter").addField(intField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.", "proto2");
    ProtoField field = msg.getFields().get(0);

    assertEquals("42", field.getDefaultValue());
    assertTrue(field.hasExplicitPresence());
  }

  // ---- non-WKT message field is NOT marked as well-known type ----

  @Test
  void testRegularMessageNotWellKnownType() {
    FieldDescriptorProto regularField =
        FieldDescriptorProto.newBuilder()
            .setName("address")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".example.Address")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto descriptor =
        DescriptorProto.newBuilder().setName("Person").addField(regularField).build();

    ProtoMessage msg = newAnalyzer().analyze(descriptor, ".example.");
    ProtoField field = msg.getFields().get(0);

    assertEquals(ProtoField.FieldKind.MESSAGE, field.getKind());
    assertFalse(field.isWellKnownType());
    assertNull(field.getWellKnownType());
  }
}
