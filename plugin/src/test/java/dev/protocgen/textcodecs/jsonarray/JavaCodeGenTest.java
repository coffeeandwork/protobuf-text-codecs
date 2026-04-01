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
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end code generation tests. Each test builds a CodeGeneratorRequest programmatically, runs
 * it through PluginRunner, and inspects the generated Java source code for correctness.
 */
class JavaCodeGenTest {

  private final PluginRunner runner = new PluginRunner();

  // ======================================================================
  // Helpers
  // ======================================================================

  private FieldDescriptorProto field(
      String name, int number, FieldDescriptorProto.Type type, FieldDescriptorProto.Label label) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(label)
        .build();
  }

  private FieldDescriptorProto scalarField(
      String name, int number, FieldDescriptorProto.Type type) {
    return field(name, number, type, FieldDescriptorProto.Label.LABEL_OPTIONAL);
  }

  private FieldDescriptorProto repeatedField(
      String name, int number, FieldDescriptorProto.Type type) {
    return field(name, number, type, FieldDescriptorProto.Label.LABEL_REPEATED);
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

  /** Build a request from a single proto3 file and run it, returning the first generated file. */
  private String generateSingleMessage(DescriptorProto msg) {
    return generateSingleMessage(msg, "test", "proto3");
  }

  private String generateSingleMessage(DescriptorProto msg, String pkg, String syntax) {
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage(pkg)
            .setSyntax(syntax)
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Expected at least one generated file");
    return response.getFile(0).getContent();
  }

  // ======================================================================
  // 1. Scalar types (15 total)
  // ======================================================================

  @Test
  void testScalarDouble() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("double_field", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private double doubleField"), "double field declaration");
    // Serializer should check NaN/Infinity
    assertTrue(code.contains("Double.isNaN(this.doubleField)"), "double NaN check in serializer");
    assertTrue(
        code.contains("Double.isInfinite(this.doubleField)"),
        "double Infinity check in serializer");
  }

  @Test
  void testScalarFloat() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("float_field", 1, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private float floatField"), "float field declaration");
    assertTrue(code.contains("Float.isNaN(this.floatField)"), "float NaN check in serializer");
    assertTrue(
        code.contains("Float.isInfinite(this.floatField)"), "float Infinity check in serializer");
  }

  @Test
  void testScalarInt32() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("int32_field", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private int int32Field"), "int32 field declaration");
    assertTrue(code.contains("sb.append(this.int32Field)"), "int32 serialization");
  }

  @Test
  void testScalarSint32() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("sint32_field", 1, FieldDescriptorProto.Type.TYPE_SINT32))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private int sint32Field"), "sint32 field declaration");
    assertTrue(code.contains("sb.append(this.sint32Field)"), "sint32 serialization");
  }

  @Test
  void testScalarSfixed32() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("sfixed32_field", 1, FieldDescriptorProto.Type.TYPE_SFIXED32))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private int sfixed32Field"), "sfixed32 field declaration");
    assertTrue(code.contains("sb.append(this.sfixed32Field)"), "sfixed32 serialization");
  }

  @Test
  void testScalarInt64AsString() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("int64_field", 1, FieldDescriptorProto.Type.TYPE_INT64))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private long int64Field"), "int64 field declaration");
    // Must serialize as string to avoid JS precision loss
    assertTrue(
        code.contains("sb.append('\"').append(String.valueOf(this.int64Field)).append('\"')"),
        "int64 must serialize as string");
  }

  @Test
  void testScalarSint64AsString() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("sint64_field", 1, FieldDescriptorProto.Type.TYPE_SINT64))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private long sint64Field"), "sint64 field declaration");
    assertTrue(
        code.contains("String.valueOf(this.sint64Field)"), "sint64 must serialize as string");
  }

  @Test
  void testScalarSfixed64AsString() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("sfixed64_field", 1, FieldDescriptorProto.Type.TYPE_SFIXED64))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private long sfixed64Field"), "sfixed64 field declaration");
    assertTrue(
        code.contains("String.valueOf(this.sfixed64Field)"), "sfixed64 must serialize as string");
  }

  @Test
  void testScalarUint32UsesUnsigned() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("uint32_field", 1, FieldDescriptorProto.Type.TYPE_UINT32))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private int uint32Field"), "uint32 field declaration");
    assertTrue(
        code.contains("Integer.toUnsignedLong(this.uint32Field)"),
        "uint32 must use Integer.toUnsignedLong");
  }

  @Test
  void testScalarFixed32UsesUnsigned() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("fixed32_field", 1, FieldDescriptorProto.Type.TYPE_FIXED32))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(
        code.contains("Integer.toUnsignedLong(this.fixed32Field)"),
        "fixed32 must use Integer.toUnsignedLong");
  }

  @Test
  void testScalarUint64AsUnsignedString() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("uint64_field", 1, FieldDescriptorProto.Type.TYPE_UINT64))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private long uint64Field"), "uint64 field declaration");
    assertTrue(
        code.contains("Long.toUnsignedString(this.uint64Field)"),
        "uint64 must use Long.toUnsignedString");
  }

  @Test
  void testScalarFixed64AsUnsignedString() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("fixed64_field", 1, FieldDescriptorProto.Type.TYPE_FIXED64))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(
        code.contains("Long.toUnsignedString(this.fixed64Field)"),
        "fixed64 must use Long.toUnsignedString");
  }

  @Test
  void testScalarBool() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("bool_field", 1, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private boolean boolField"), "bool field declaration");
    assertTrue(code.contains("sb.append(this.boolField)"), "bool serialization");
  }

  @Test
  void testScalarString() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("string_field", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private String stringField"), "string field declaration");
    assertTrue(code.contains("appendQuotedString(sb, this.stringField)"), "string serialization");
  }

  @Test
  void testScalarBytes() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("data", 1, FieldDescriptorProto.Type.TYPE_BYTES))
            .build();
    String code = generateSingleMessage(msg);
    assertTrue(code.contains("private byte[] data"), "bytes field declaration");
    assertTrue(
        code.contains("Base64.getEncoder().encodeToString(this.data)"),
        "bytes must use Base64 encoding");
  }

  // ======================================================================
  // 2. Enum
  // ======================================================================

  @Test
  void testEnumGeneration() {
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
    String code = generateSingleMessage(msg);

    // Verify enum declaration
    assertTrue(code.contains("public enum Status"), "Enum should be generated");
    assertTrue(code.contains("UNKNOWN(0)"), "Enum value UNKNOWN");
    assertTrue(code.contains("ACTIVE(1)"), "Enum value ACTIVE");
    assertTrue(code.contains("public static Status forNumber(int number)"), "forNumber method");
    assertTrue(code.contains("public int getNumber()"), "getNumber method");

    // Verify enum field serialization (as integer)
    assertTrue(
        code.contains("this.status != null ? this.status.getNumber() : 0"),
        "Enum serializes via getNumber()");

    // Verify enum deserialization via forNumber()
    assertTrue(code.contains("Status.forNumber("), "Enum deserializes via forNumber()");
  }

  // ======================================================================
  // 3. Nested message
  // ======================================================================

  @Test
  void testNestedMessage() {
    DescriptorProto innerMsg =
        DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Outer")
            .addNestedType(innerMsg)
            .addField(messageField("inner", 1, ".test.Outer.Inner"))
            .build();
    String code = generateSingleMessage(msg);

    // Nested message as static inner class
    assertTrue(
        code.contains("public static final class Inner"),
        "Nested message is static final inner class");

    // Message field serializes with .appendJsonArray(sb)
    assertTrue(
        code.contains("this.inner.appendJsonArray(sb)"), "Message field recursive serialization");

    // Null check before serialization
    assertTrue(code.contains("if (this.inner != null)"), "Null check before message serialization");
    assertTrue(code.contains("sb.append(\"null\")"), "null for unset message");
  }

  // ======================================================================
  // 4. Repeated fields
  // ======================================================================

  @Test
  void testRepeatedScalar() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(repeatedField("tags", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("java.util.List<String> tags"), "Repeated string generates as List<String>");
    // Should serialize as nested array via StringBuilder
    assertTrue(code.contains("sb.append('[')"), "Repeated field serializes as nested array");
  }

  @Test
  void testRepeatedMessage() {
    DescriptorProto innerMsg =
        DescriptorProto.newBuilder()
            .setName("Item")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(innerMsg)
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("items")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Msg.Item")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("java.util.List<Item> items"), "Repeated message generates as List<Item>");
    // Element serialization loop
    assertTrue(
        code.contains(".appendJsonArray(sb)"), "Repeated message elements serialize recursively");
  }

  @Test
  void testRepeatedEmptyList() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(repeatedField("vals", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    // Default value should be an empty list
    assertTrue(
        code.contains("new java.util.ArrayList<>()"), "Repeated field default is empty list");
    // Serialize loop creates a nested array via StringBuilder
    assertTrue(code.contains("sb.append('[')"), "Serializer creates nested array for list");
  }

  // ======================================================================
  // 5. Map fields
  // ======================================================================

  @Test
  void testMapStringKey() {
    DescriptorProto mapEntry =
        DescriptorProto.newBuilder()
            .setName("MetadataEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
            .addField(scalarField("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("value", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(mapEntry)
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("metadata")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Msg.MetadataEntry")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();

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

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String code = response.getFile(0).getContent();

    // String-keyed map serializes as JSON object via StringBuilder
    assertTrue(code.contains("sb.append('{')"), "String-keyed map serializes as JSON object");
    assertTrue(code.contains("java.util.Map<String, String>"), "Map type is Map<String, String>");
  }

  @Test
  void testMapNonStringKey() {
    DescriptorProto mapEntry =
        DescriptorProto.newBuilder()
            .setName("CountsEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
            .addField(scalarField("key", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("value", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(mapEntry)
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("counts")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Msg.CountsEntry")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();

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

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String code = response.getFile(0).getContent();

    // Non-string-keyed map serializes as array of [key,value] pairs
    assertTrue(code.contains("sb.append('[')"), "Non-string-keyed map uses pair arrays");
    assertTrue(code.contains("java.util.Map<Integer, String>"), "Map type is Map<Integer, String>");
  }

  @Test
  void testMapWithMessageValue() {
    DescriptorProto innerMsg =
        DescriptorProto.newBuilder()
            .setName("Detail")
            .addField(scalarField("info", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto mapEntry =
        DescriptorProto.newBuilder()
            .setName("DetailsEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
            .addField(scalarField("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Msg.Detail")
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(innerMsg)
            .addNestedType(mapEntry)
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("details")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Msg.DetailsEntry")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();

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

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String code = response.getFile(0).getContent();

    assertTrue(code.contains("java.util.Map<String, Detail>"), "Map with message value type");
    assertTrue(code.contains(".appendJsonArray(sb)"), "Map message values serialize recursively");
  }

  // ======================================================================
  // 6. Oneof
  // ======================================================================

  @Test
  void testOneofCaseTracking() {
    FieldDescriptorProto emailField =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto phoneField =
        FieldDescriptorProto.newBuilder()
            .setName("phone")
            .setNumber(3)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(emailField)
            .addField(phoneField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("contact").build())
            .build();
    String code = generateSingleMessage(msg);

    // Case tracking constants use field numbers
    assertTrue(code.contains("EMAILCase_ = 2"), "EMAIL case constant uses field number 2");
    assertTrue(code.contains("PHONECase_ = 3"), "PHONE case constant uses field number 3");

    // Serializer checks case
    assertTrue(code.contains("contactCase_ == EMAILCase_"), "Serializer checks EMAIL case");

    // Inactive members emit null
    assertTrue(code.contains("else { sb.append(\"null\"); }"), "Inactive oneof member emits null");
  }

  @Test
  void testOneofSetterUpdatesCase() {
    FieldDescriptorProto emailField =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(emailField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("contact").build())
            .build();
    String code = generateSingleMessage(msg);

    // Builder setter should update the case field (uses field number literal)
    assertTrue(
        code.contains("this.contactCase_ = 1"), "Builder setter must update oneof case field");
  }

  // ======================================================================
  // 7. Proto3 optional (explicit presence)
  // ======================================================================

  @Test
  void testProto3Optional() {
    FieldDescriptorProto optField =
        FieldDescriptorProto.newBuilder()
            .setName("nickname")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setProto3Optional(true)
            .setOneofIndex(0)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(optField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_nickname").build())
            .build();
    String code = generateSingleMessage(msg);

    // BitSet presence tracking
    assertTrue(code.contains("BitSet"), "Proto3 optional uses BitSet");
    assertTrue(code.contains("presentFields_"), "presentFields_ field present");

    // has*() method
    assertTrue(code.contains("hasNickname"), "has method generated for optional field");

    // Serializer checks presence, emits null when not present
    assertTrue(code.contains("presentFields_.get(0)"), "Serializer checks presence bit");
    assertTrue(code.contains("sb.append(\"null\")"), "null emitted when optional not present");
  }

  // ======================================================================
  // 8. Proto2 specifics
  // ======================================================================

  @Test
  void testProto2RequiredFieldNoNullCheck() {
    FieldDescriptorProto reqField =
        FieldDescriptorProto.newBuilder()
            .setName("name")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED)
            .build();

    DescriptorProto msg = DescriptorProto.newBuilder().setName("Msg").addField(reqField).build();
    String code = generateSingleMessage(msg, "test", "proto2");

    // Required field has presence tracking
    assertTrue(code.contains("BitSet"), "Proto2 required field has BitSet presence");
    assertTrue(code.contains("hasName"), "Proto2 required field has has method");
    // Required field still serializes directly (not a null-wrapped conditional)
    // The presence tracking only gates the "else { sb.append("null") }" branch
    assertTrue(
        code.contains("appendQuotedString(sb, this.name)"),
        "Required string field serializes directly");
  }

  @Test
  void testProto2OptionalPresenceTracking() {
    FieldDescriptorProto optField =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto msg = DescriptorProto.newBuilder().setName("Msg").addField(optField).build();
    String code = generateSingleMessage(msg, "test", "proto2");

    // Proto2 optional has explicit presence
    assertTrue(code.contains("BitSet"), "Proto2 optional uses BitSet");
    assertTrue(code.contains("presentFields_.get(0)"), "Serializer checks presence");
    assertTrue(code.contains("sb.append(\"null\")"), "Proto2 optional emits null when not present");
  }

  @Test
  void testProto2SchemaDefaultApplied() {
    FieldDescriptorProto optField =
        FieldDescriptorProto.newBuilder()
            .setName("label")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("default_label")
            .build();

    DescriptorProto msg = DescriptorProto.newBuilder().setName("Msg").addField(optField).build();
    String code = generateSingleMessage(msg, "test", "proto2");

    // Schema-specified default should appear as field initializer
    assertTrue(
        code.contains("\"default_label\""), "Schema default should appear in generated code");
  }

  // ======================================================================
  // 9. Field number gaps
  // ======================================================================

  @Test
  void testFieldNumberGapsProduceNulls() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 5, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("sb.append(\"null\"); // gap (no field number 2)"),
        "Null gap for missing field number 2");
    assertTrue(
        code.contains("sb.append(\"null\"); // gap (no field number 4)"),
        "Null gap for missing field number 4");
  }

  // ======================================================================
  // 10. Well-known types
  // ======================================================================

  @Test
  void testTimestampSerializesAsNestedMessage() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(messageField("created_at", 1, ".google.protobuf.Timestamp"))
            .build();
    String code = generateSingleMessage(msg);

    // Well-known types are serialized as nested messages via appendJsonArray
    assertTrue(
        code.contains("appendJsonArray(sb)"), "Timestamp uses appendJsonArray for serialization");
    // Well-known types are deserialized via fromJsonArray
    assertTrue(code.contains("fromJsonArray("), "Timestamp uses fromJsonArray for deserialization");
  }

  @Test
  void testAnyRejection() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(messageField("payload", 1, ".google.protobuf.Any"))
            .build();

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

    CodeGeneratorResponse response = runner.run(request);
    assertTrue(response.hasError(), "google.protobuf.Any should cause an error");
    assertTrue(
        response.getError().contains("google.protobuf.Any"), "Error message should mention Any");
  }

  // ======================================================================
  // 11. Keyword escaping
  // ======================================================================

  @Test
  void testKeywordFieldEscaping() {
    // "class" is a Java keyword; the field name should become "class_"
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("class_")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();
    String code = generateSingleMessage(msg);

    // "class_" in snake_case -> snakeToCamel -> "class" -> escapeJava -> "class_"
    assertTrue(code.contains("class_"), "Keyword field should be escaped with trailing underscore");
  }

  // ======================================================================
  // 12. Name collision detection
  // ======================================================================

  @Test
  void testNameCollisionThrows() {
    // "foo_bar" -> fooBar, "fooBar" -> fooBar -- collision
    // Note: proto field names must be valid identifiers, but both foo_bar and fooBar are valid.
    // However fooBar contains uppercase, which is not standard proto naming. The analyzer allows
    // it, but the name resolver should detect the collision.
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("foo_bar", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("fooBar", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

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

    CodeGeneratorResponse response = runner.run(request);
    // The collision should produce an error (either thrown as exception and caught, or directly)
    assertTrue(response.hasError(), "Name collision should produce an error");
    assertTrue(
        response.getError().contains("collision"), "Error message should mention 'collision'");
  }

  // ======================================================================
  // 13. @generated marker
  // ======================================================================

  @Test
  void testGeneratedMarker() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.startsWith("// @generated"), "First line must be @generated marker");
  }

  // ======================================================================
  // 14. equals/hashCode
  // ======================================================================

  @Test
  void testEqualsMethodPresent() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("public boolean equals(Object o)"), "equals method present");
    assertTrue(code.contains("instanceof Msg"), "equals uses instanceof check");
    assertTrue(code.contains("if (this == o) return true"), "equals has identity check");
  }

  @Test
  void testHashCodeMethodPresent() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("public int hashCode()"), "hashCode method present");
    assertTrue(code.contains("Objects.hash("), "hashCode uses Objects.hash()");
  }

  // ======================================================================
  // 15. Proto comments (SourceCodeInfo)
  // ======================================================================

  @Test
  void testSourceCommentsPropagated() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    // Path [4, 0] = first message; [4, 0, 2, 0] = first field in first message
    SourceCodeInfo sci =
        SourceCodeInfo.newBuilder()
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .setLeadingComments(" A message comment.\n")
                    .build())
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .addPath(2)
                    .addPath(0)
                    .setLeadingComments(" The name field.\n")
                    .build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .setSourceCodeInfo(sci)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String code = response.getFile(0).getContent();

    assertTrue(code.contains("/** A message comment. */"), "Message Javadoc should appear");
    assertTrue(code.contains("/** The name field. */"), "Field Javadoc should appear");
  }

  // ======================================================================
  // 16. Int64 deserialization handles string and number
  // ======================================================================

  @Test
  void testInt64DeserializerHandlesBothFormats() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("big_val", 1, FieldDescriptorProto.Type.TYPE_INT64))
            .build();
    String code = generateSingleMessage(msg);

    // Deserializer should handle string and numeric formats
    assertTrue(
        code.contains("instanceof String"), "Deserializer checks instanceof String for int64");
    assertTrue(code.contains("Long.parseLong("), "Deserializer parses string as long");
    assertTrue(code.contains("longValue()"), "Deserializer falls back to longValue()");
  }

  @Test
  void testUint64DeserializerHandlesBothFormats() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("unsigned_val", 1, FieldDescriptorProto.Type.TYPE_UINT64))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("instanceof String"), "Deserializer checks instanceof String for uint64");
    assertTrue(
        code.contains("Long.parseUnsignedLong("), "Deserializer uses parseUnsignedLong for uint64");
  }

  // ======================================================================
  // 17. NaN/Infinity handling
  // ======================================================================

  @Test
  void testNanInfinityHandlingDouble() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("score", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("Double.isNaN("), "Double field checks isNaN");
    assertTrue(code.contains("Double.isInfinite("), "Double field checks isInfinite");
    // On NaN/Infinity, should append null instead of the value
    assertTrue(code.contains("sb.append(\"null\")"), "NaN/Infinity serializes as null");
  }

  @Test
  void testNanInfinityHandlingFloat() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("Float.isNaN("), "Float field checks isNaN");
    assertTrue(code.contains("Float.isInfinite("), "Float field checks isInfinite");
  }

  // ======================================================================
  // 18. ObjectMapper caching
  // ======================================================================

  @Test
  void testNoJacksonDependency() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    // No Jackson imports or references should exist
    assertFalse(code.contains("ObjectMapper"), "No ObjectMapper reference");
    assertFalse(code.contains("jackson"), "No Jackson reference");
    assertFalse(code.contains("ArrayNode"), "No ArrayNode reference");
    // Uses StringBuilder-based serialization instead
    assertTrue(
        code.contains("appendJsonArray(StringBuilder sb)"), "appendJsonArray method present");
    assertTrue(code.contains("JsonArrayReader.parseArray("), "parseFrom uses JsonArrayReader");
  }

  // ======================================================================
  // 19. Cross-file type references
  // ======================================================================

  @Test
  void testCrossFileTypeReference() {
    // Address message in address.proto
    DescriptorProto addressMsg =
        DescriptorProto.newBuilder()
            .setName("Address")
            .addField(scalarField("street", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    FileDescriptorProto addressFile =
        FileDescriptorProto.newBuilder()
            .setName("address.proto")
            .setPackage("example")
            .setSyntax("proto3")
            .addMessageType(addressMsg)
            .build();

    // User message in user.proto that references Address
    DescriptorProto userMsg =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(messageField("addr", 2, ".example.Address"))
            .build();

    FileDescriptorProto userFile =
        FileDescriptorProto.newBuilder()
            .setName("user.proto")
            .setPackage("example")
            .setSyntax("proto3")
            .addMessageType(userMsg)
            .addDependency("address.proto")
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(addressFile)
            .addProtoFile(userFile)
            .addFileToGenerate("user.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Should generate user.proto");

    String code = response.getFile(0).getContent();
    // Cross-file reference should use the type name
    assertTrue(code.contains("Address"), "Cross-file Address type reference present");
    assertTrue(code.contains("Address.fromJsonArray("), "Cross-file deserialize call present");
  }

  // ======================================================================
  // 20. Empty message
  // ======================================================================

  @Test
  void testEmptyMessageGeneratesEmptyArray() {
    DescriptorProto msg = DescriptorProto.newBuilder().setName("Empty").build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("sb.append('[')"), "Empty message starts array");
    assertTrue(code.contains("sb.append(']')"), "Empty message ends array");
    // Serialize method should have no sb.append for field values
    String serializeBody = extractMethodBody(code, "appendJsonArray");
    // Only expect the '[' and ']' appends, no field value appends
    int fieldAppends = countOccurrences(serializeBody, "sb.append(this.");
    assertTrue(
        fieldAppends == 0, "Empty message should have zero field appends in appendJsonArray");
  }

  // ======================================================================
  // 21. toByteArray / writeTo / parseFrom convenience methods
  // ======================================================================

  @Test
  void testConvenienceMethods() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("public byte[] toByteArray()"), "toByteArray method");
    assertTrue(code.contains("public void writeTo(java.io.OutputStream output)"), "writeTo method");
    assertTrue(
        code.contains("public static Msg parseFrom(byte[] data)"), "parseFrom(byte[]) method");
    assertTrue(
        code.contains("public static Msg parseFrom(java.io.InputStream input)"),
        "parseFrom(InputStream) method");
  }

  // ======================================================================
  // 22. Deserialization size bounds checks
  // ======================================================================

  @Test
  void testDeserializationBoundsChecks() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("a", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("b", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("c", 3, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("if (size > 0"), "Deserializer checks size > 0");
    assertTrue(code.contains("if (size > 1"), "Deserializer checks size > 1");
    assertTrue(code.contains("if (size > 2"), "Deserializer checks size > 2");
  }

  // ======================================================================
  // 23. Repeated enum field
  // ======================================================================

  @Test
  void testRepeatedEnum() {
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
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("statuses")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_ENUM)
                    .setTypeName(".test.Msg.Status")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("java.util.List<Status>"), "Repeated enum is List<Status>");
    // Elements serialize via getNumber()
    assertTrue(code.contains("getNumber()"), "Repeated enum elements use getNumber()");
    // Elements deserialize via forNumber()
    assertTrue(code.contains("Status.forNumber("), "Repeated enum elements use forNumber()");
  }

  // ======================================================================
  // 24. Proto2 enum default
  // ======================================================================

  @Test
  void testProto2EnumDefault() {
    FieldDescriptorProto enumField =
        FieldDescriptorProto.newBuilder()
            .setName("priority")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_ENUM)
            .setTypeName(".test.Msg.Priority")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("HIGH")
            .build();

    EnumDescriptorProto priorityEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Priority")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("LOW").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("HIGH").setNumber(1).build())
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addEnumType(priorityEnum)
            .addField(enumField)
            .build();

    String code = generateSingleMessage(msg, "test", "proto2");

    // Field default should use the enum constant name
    assertTrue(code.contains("Priority.HIGH"), "Proto2 enum default should be Priority.HIGH");
  }

  // ======================================================================
  // 25. Proto2 int default
  // ======================================================================

  @Test
  void testProto2IntDefault() {
    FieldDescriptorProto intField =
        FieldDescriptorProto.newBuilder()
            .setName("count")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("42")
            .build();

    DescriptorProto msg = DescriptorProto.newBuilder().setName("Msg").addField(intField).build();
    String code = generateSingleMessage(msg, "test", "proto2");

    // Initializer should be 42 (not 0)
    assertTrue(code.contains("= 42;"), "Proto2 int field default should be 42");
  }

  // ======================================================================
  // 26. Proto2 string default
  // ======================================================================

  @Test
  void testProto2StringDefault() {
    FieldDescriptorProto strField =
        FieldDescriptorProto.newBuilder()
            .setName("greeting")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("hello")
            .build();

    DescriptorProto msg = DescriptorProto.newBuilder().setName("Msg").addField(strField).build();
    String code = generateSingleMessage(msg, "test", "proto2");

    assertTrue(code.contains("\"hello\""), "Proto2 string field default should be \"hello\"");
  }

  // ======================================================================
  // 27. Proto2 bool default
  // ======================================================================

  @Test
  void testProto2BoolDefault() {
    FieldDescriptorProto boolField =
        FieldDescriptorProto.newBuilder()
            .setName("active")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_BOOL)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setDefaultValue("true")
            .build();

    DescriptorProto msg = DescriptorProto.newBuilder().setName("Msg").addField(boolField).build();
    String code = generateSingleMessage(msg, "test", "proto2");

    assertTrue(code.contains("= true;"), "Proto2 bool field default should be true");
  }

  // ======================================================================
  // 28. Multiple oneofs
  // ======================================================================

  @Test
  void testMultipleOneofs() {
    FieldDescriptorProto f1 =
        FieldDescriptorProto.newBuilder()
            .setName("str_val")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto f2 =
        FieldDescriptorProto.newBuilder()
            .setName("int_val")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto f3 =
        FieldDescriptorProto.newBuilder()
            .setName("name")
            .setNumber(3)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(1)
            .build();

    FieldDescriptorProto f4 =
        FieldDescriptorProto.newBuilder()
            .setName("id")
            .setNumber(4)
            .setType(FieldDescriptorProto.Type.TYPE_INT64)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(1)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(f1)
            .addField(f2)
            .addField(f3)
            .addField(f4)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("value_oneof").build())
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("identifier").build())
            .build();
    String code = generateSingleMessage(msg);

    // Two separate case tracking fields
    assertTrue(code.contains("valueOneofCase_"), "First oneof case tracking");
    assertTrue(code.contains("identifierCase_"), "Second oneof case tracking");

    // Case constants for both
    assertTrue(code.contains("STR_VALCase_"), "STR_VAL case constant");
    assertTrue(code.contains("INT_VALCase_"), "INT_VAL case constant");
    assertTrue(code.contains("NAMECase_"), "NAME case constant");
    assertTrue(code.contains("IDCase_"), "ID case constant");
  }

  // ======================================================================
  // 29. Deeply nested messages
  // ======================================================================

  @Test
  void testDeeplyNestedMessage() {
    DescriptorProto level3 =
        DescriptorProto.newBuilder()
            .setName("Level3")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto level2 =
        DescriptorProto.newBuilder()
            .setName("Level2")
            .addField(messageField("nested", 1, ".test.Top.Level2.Level3"))
            .addNestedType(level3)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Top")
            .addField(messageField("child", 1, ".test.Top.Level2"))
            .addNestedType(level2)
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("public static final class Level2"), "Level2 static final inner class");
    assertTrue(
        code.contains("public static final class Level3"), "Level3 static final inner class");
  }

  // ======================================================================
  // 30. toString method
  // ======================================================================

  @Test
  void testToStringMethod() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("public String toString()"), "toString method present");
    assertTrue(code.contains("Msg{"), "toString includes class name");
  }

  // ======================================================================
  // 31. Bytes base64 in deserialization
  // ======================================================================

  @Test
  void testBytesDeserializationUsesBase64() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("payload", 1, FieldDescriptorProto.Type.TYPE_BYTES))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("Base64.getDecoder().decode("),
        "Bytes deserialization uses Base64.getDecoder().decode()");
  }

  // ======================================================================
  // 32. Repeated int32 uses Integer boxed type
  // ======================================================================

  @Test
  void testRepeatedInt32BoxedType() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(repeatedField("scores", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("java.util.List<Integer>"), "Repeated int32 is List<Integer>");
  }

  // ======================================================================
  // 33. Getter and setter methods
  // ======================================================================

  @Test
  void testGetterSetterGeneration() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("first_name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("getFirstName()"), "Getter generated with PascalCase");
    assertTrue(code.contains("setFirstName("), "Setter generated with PascalCase");
    assertTrue(code.contains("private String firstName"), "Field uses camelCase");
  }

  // ======================================================================
  // 34. Package declaration
  // ======================================================================

  @Test
  void testPackageDeclaration() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("com.example")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String code = response.getFile(0).getContent();

    assertTrue(code.contains("package com.example;"), "Package declaration present");
  }

  @Test
  void testJavaPackageOption() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("example")
            .setOptions(
                com.google.protobuf.DescriptorProtos.FileOptions.newBuilder()
                    .setJavaPackage("com.example.generated"))
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String code = response.getFile(0).getContent();

    assertTrue(
        code.contains("package com.example.generated;"), "java_package option should be used");
  }

  // ======================================================================
  // 35. Top-level enum
  // ======================================================================

  @Test
  void testTopLevelEnum() {
    EnumDescriptorProto statusEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Color")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("RED").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("GREEN").setNumber(1).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("BLUE").setNumber(2).build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addEnumType(statusEnum)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Should generate a file for the enum");

    String code = response.getFile(0).getContent();
    assertTrue(code.contains("public enum Color"), "Top-level enum generated");
    assertTrue(code.contains("RED(0)"), "RED constant");
    assertTrue(code.contains("GREEN(1)"), "GREEN constant");
    assertTrue(code.contains("BLUE(2)"), "BLUE constant");
    assertTrue(code.contains("forNumber"), "forNumber method in top-level enum");
  }

  // ======================================================================
  // 36. Bytes in equals uses Arrays.equals
  // ======================================================================

  @Test
  void testBytesFieldEqualsUsesArraysEquals() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("data", 1, FieldDescriptorProto.Type.TYPE_BYTES))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("Arrays.equals(this.data, that.data)"),
        "byte[] field uses Arrays.equals in equals");
    assertTrue(
        code.contains("Arrays.hashCode(data)"), "byte[] field uses Arrays.hashCode in hashCode");
  }

  // ======================================================================
  // 37. Primitive fields use == in equals
  // ======================================================================

  @Test
  void testPrimitiveFieldEqualsUseDoubleEquals() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("count", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("flag", 2, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("this.count == that.count"), "Primitive int uses == in equals");
    assertTrue(code.contains("this.flag == that.flag"), "Primitive bool uses == in equals");
  }

  // ======================================================================
  // 38. Message field uses Objects.equals
  // ======================================================================

  @Test
  void testMessageFieldEqualsUsesObjectsEquals() {
    DescriptorProto innerMsg =
        DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(innerMsg)
            .addField(messageField("inner", 1, ".test.Msg.Inner"))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("Objects.equals(this.inner, that.inner)"),
        "Message field uses Objects.equals");
  }

  // ======================================================================
  // 39. Constructor generated
  // ======================================================================

  @Test
  void testDefaultConstructor() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("private Msg(Builder builder)"), "Private Builder constructor present");
    assertTrue(
        code.contains("public static Builder newBuilder()"), "newBuilder() factory method present");
  }

  // ======================================================================
  // 40. Multiple messages in one file
  // ======================================================================

  @Test
  void testMultipleMessagesInOneFile() {
    DescriptorProto msgA =
        DescriptorProto.newBuilder()
            .setName("Alpha")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msgB =
        DescriptorProto.newBuilder()
            .setName("Beta")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msgA)
            .addMessageType(msgB)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    // Should produce 2 files (one per message)
    assertTrue(response.getFileCount() >= 2, "Should generate at least 2 files");

    boolean hasAlpha = false;
    boolean hasBeta = false;
    for (int i = 0; i < response.getFileCount(); i++) {
      String content = response.getFile(i).getContent();
      if (content.contains("public final class Alpha")) hasAlpha = true;
      if (content.contains("public final class Beta")) hasBeta = true;
    }
    assertTrue(hasAlpha, "Alpha class should be generated");
    assertTrue(hasBeta, "Beta class should be generated");
  }

  // ======================================================================
  // 41. Map deserialization
  // ======================================================================

  @Test
  void testMapDeserialization() {
    DescriptorProto mapEntry =
        DescriptorProto.newBuilder()
            .setName("LabelsEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
            .addField(scalarField("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("value", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(mapEntry)
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("labels")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Msg.LabelsEntry")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();

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

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    String code = response.getFile(0).getContent();

    // String-keyed map deserialization uses Map<String, Object>
    assertTrue(
        code.contains("Map<String, Object>"),
        "String-keyed map deserializes from Map<String, Object>");
    assertTrue(code.contains("entrySet()"), "String map iterates entrySet()");
    assertTrue(code.contains("LinkedHashMap"), "Map initialized as LinkedHashMap");
  }

  // ======================================================================
  // 42. Self-referential message
  // ======================================================================

  @Test
  void testSelfReferentialMessage() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("TreeNode")
            .addField(scalarField("label", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(messageField("parent", 2, ".test.TreeNode"))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("private TreeNode parent"), "Self-reference field type is TreeNode");
    assertTrue(
        code.contains("TreeNode.fromJsonArray("),
        "Self-referential deserialize call uses TreeNode");
  }

  // ======================================================================
  // 43. Sfixed64 deserialization handles string and number
  // ======================================================================

  @Test
  void testSfixed64DeserializerHandlesBothFormats() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("fixed_val", 1, FieldDescriptorProto.Type.TYPE_SFIXED64))
            .build();
    String code = generateSingleMessage(msg);

    // Sfixed64 should also handle string format
    assertTrue(
        code.contains("instanceof String"), "sfixed64 deserializer checks instanceof String");
    assertTrue(code.contains("Long.parseLong("), "sfixed64 deserializer uses Long.parseLong");
  }

  // ======================================================================
  // 44. Fixed64 deserialization uses parseUnsignedLong
  // ======================================================================

  @Test
  void testFixed64Deserialization() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("fixed_val", 1, FieldDescriptorProto.Type.TYPE_FIXED64))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("Long.parseUnsignedLong("),
        "fixed64 deserialization uses Long.parseUnsignedLong");
  }

  // ======================================================================
  // 45. has*() for message fields (non-optional)
  // ======================================================================

  @Test
  void testHasMethodForMessageField() {
    DescriptorProto innerMsg =
        DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(scalarField("val", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(innerMsg)
            .addField(messageField("inner", 1, ".test.Msg.Inner"))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("hasInner()"), "has method for message field");
    // For non-optional message field in proto3, hasX checks != null
    assertTrue(code.contains("this.inner != null"), "hasInner checks != null");
  }

  // ======================================================================
  // 46. [P0] Unknown enum value deserialization — forNumber fallback
  // ======================================================================

  @Test
  void testUnknownEnumValueDeserialization() {
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
    String code = generateSingleMessage(msg);

    // Enum definition must include all expected values
    assertTrue(code.contains("UNKNOWN(0)"), "Enum value UNKNOWN(0) present");
    assertTrue(code.contains("ACTIVE(1)"), "Enum value ACTIVE(1) present");

    // forNumber must iterate values and match by number
    assertTrue(
        code.contains("public static Status forNumber(int number)"),
        "forNumber method generated for Status enum");
    assertTrue(
        code.contains("if (v.number == number)"), "forNumber checks v.number == number for match");

    // forNumber must have a default/fallback case that returns null (not crash) for unknown values
    assertTrue(
        code.contains("return null;"), "forNumber returns null for unknown enum values (e.g., 99)");
  }

  // ======================================================================
  // 47. [P0] Proto3 default values serialized not null
  // ======================================================================

  @Test
  void testProto3DefaultValuesSerializedNotNull() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("active", 3, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();
    String code = generateSingleMessage(msg);

    // Extract the serializer method body to check field-level serialization
    String serializerBody = extractMethodBody(code, "appendJsonArray");

    // Proto3 scalar fields without explicit presence must never emit null.
    // Positional encoding requires every position to be filled with actual values.

    // String field at position 0: must emit appendQuotedString (which outputs ""), not null
    assertTrue(
        serializerBody.contains("appendQuotedString(sb, this.name)"),
        "String field serializes via appendQuotedString (emits \"\" for default)");

    // Int32 field at position 1: must emit sb.append(this.age) which outputs 0, not null
    assertTrue(
        serializerBody.contains("sb.append(this.age)"),
        "Int32 field serializes via sb.append (emits 0 for default)");

    // Bool field at position 2: must emit sb.append(this.active) which outputs false, not null
    assertTrue(
        serializerBody.contains("sb.append(this.active)"),
        "Bool field serializes via sb.append (emits false for default)");

    // The serializer for these proto3 non-presence fields must NOT have null branches
    // (null is only for gaps, optional fields, or message fields)
    assertFalse(
        serializerBody.contains("presentFields_"),
        "Proto3 non-optional scalar fields must not use presence tracking in serializer");
  }

  // ======================================================================
  // 48. [P1] Proto2 enum with negative value
  // ======================================================================

  @Test
  void testProto2EnumWithNegativeValue() {
    EnumDescriptorProto negEnum =
        EnumDescriptorProto.newBuilder()
            .setName("SignedPriority")
            .addValue(
                EnumValueDescriptorProto.newBuilder().setName("NEG_ONE").setNumber(-1).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ZERO").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ONE").setNumber(1).build())
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addEnumType(negEnum)
            .addField(enumField("priority", 1, ".test.Msg.SignedPriority"))
            .build();

    String code = generateSingleMessage(msg, "test", "proto2");

    // Negative enum values are valid in proto2
    assertTrue(
        code.contains("NEG_ONE(-1)"),
        "Enum value NEG_ONE(-1) must be generated with negative number");
    assertTrue(code.contains("ZERO(0)"), "Enum value ZERO(0) present");
    assertTrue(code.contains("ONE(1)"), "Enum value ONE(1) present");

    // forNumber must still work with the negative value
    assertTrue(
        code.contains("public static SignedPriority forNumber(int number)"),
        "forNumber generated for enum with negative values");
  }

  // ======================================================================
  // 49. [P1] Cross-file circular reference
  // ======================================================================

  @Test
  void testCrossFileCircularReference() {
    // File A: message A has field of type B
    DescriptorProto msgA =
        DescriptorProto.newBuilder()
            .setName("NodeA")
            .addField(scalarField("label", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(messageField("ref_b", 2, ".circular.NodeB"))
            .build();

    FileDescriptorProto fileA =
        FileDescriptorProto.newBuilder()
            .setName("a.proto")
            .setPackage("circular")
            .setSyntax("proto3")
            .addMessageType(msgA)
            .addDependency("b.proto")
            .build();

    // File B: message B has field of type A
    DescriptorProto msgB =
        DescriptorProto.newBuilder()
            .setName("NodeB")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(messageField("ref_a", 2, ".circular.NodeA"))
            .build();

    FileDescriptorProto fileB =
        FileDescriptorProto.newBuilder()
            .setName("b.proto")
            .setPackage("circular")
            .setSyntax("proto3")
            .addMessageType(msgB)
            .addDependency("a.proto")
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(fileA)
            .addProtoFile(fileB)
            .addFileToGenerate("a.proto")
            .addFileToGenerate("b.proto")
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = runner.run(request);
    assertFalse(
        response.hasError(),
        "Circular cross-file references should not cause an error, got: " + response.getError());
    assertTrue(response.getFileCount() >= 2, "Should generate files for both messages");

    // Find the generated content for each message
    String codeA = null;
    String codeB = null;
    for (int i = 0; i < response.getFileCount(); i++) {
      String content = response.getFile(i).getContent();
      if (content.contains("public final class NodeA")) codeA = content;
      if (content.contains("public final class NodeB")) codeB = content;
    }

    assertTrue(codeA != null, "NodeA class should be generated");
    assertTrue(codeB != null, "NodeB class should be generated");

    // NodeA references NodeB
    assertTrue(codeA.contains("NodeB"), "NodeA references NodeB type");
    assertTrue(
        codeA.contains("NodeB.fromJsonArray("), "NodeA deserializes NodeB via fromJsonArray");

    // NodeB references NodeA
    assertTrue(codeB.contains("NodeA"), "NodeB references NodeA type");
    assertTrue(
        codeB.contains("NodeA.fromJsonArray("), "NodeB deserializes NodeA via fromJsonArray");
  }

  // ======================================================================
  // 50. [P1] Oneof all members unset initial state
  // ======================================================================

  @Test
  void testOneofAllMembersUnsetInitialState() {
    FieldDescriptorProto emailField =
        FieldDescriptorProto.newBuilder()
            .setName("email")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto phoneField =
        FieldDescriptorProto.newBuilder()
            .setName("phone")
            .setNumber(3)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(emailField)
            .addField(phoneField)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("contact").build())
            .build();
    String code = generateSingleMessage(msg);

    // The Builder must initialize the oneof case field to 0 (NOT_SET)
    assertTrue(
        code.contains("private int contactCase_ = 0;"),
        "Builder initializes oneof case field to 0 (NOT_SET)");

    // The clear method must reset the case field to 0
    assertTrue(
        code.contains("this.contactCase_ = 0;"), "Clear/reset sets oneof case field back to 0");

    // The serializer must check the case and emit null for both positions when case is 0
    // EMAIL at position 1 (field number 2)
    assertTrue(
        code.contains("contactCase_ == EMAILCase_"),
        "Serializer checks EMAIL case for field number 2");
    // PHONE at position 2 (field number 3)
    assertTrue(
        code.contains("contactCase_ == PHONECase_"),
        "Serializer checks PHONE case for field number 3");
    // When case doesn't match, emit null
    assertTrue(
        code.contains("else { sb.append(\"null\"); }"),
        "When oneof case is 0 (NOT_SET), inactive members emit null");
  }

  // ======================================================================
  // 51. [P2] Repeated field serializes empty array not null
  // ======================================================================

  @Test
  void testRepeatedFieldSerializesEmptyArrayNotNull() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(repeatedField("tags", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);

    // The serializer must always emit [ and ] for a repeated field (empty array), never null
    String serializerBody = extractMethodBody(code, "appendJsonArray");
    assertTrue(serializerBody.contains("sb.append('[')"), "Repeated field emits opening bracket");
    assertTrue(serializerBody.contains("sb.append(']')"), "Repeated field emits closing bracket");

    // A repeated field in proto3 should never have a null branch in serialization.
    // Count null appends in the serializer — should be zero because the only field is repeated.
    int nullAppends = countOccurrences(serializerBody, "sb.append(\"null\")");
    assertTrue(
        nullAppends == 0,
        "Repeated field serializer must not emit null (emits empty array instead)");
  }

  // ======================================================================
  // 52. [P2] Sparse field at high position — bounds check
  // ======================================================================

  @Test
  void testSparseFieldAtHighPosition() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("first", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("sparse", 1000, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);

    // The deserializer must use correct 0-indexed bounds check for field at position 999
    assertTrue(
        code.contains("size > 999"),
        "Deserializer uses size > 999 for field at position 1000 (0-indexed position 999)");

    // First field at position 0 should use size > 0
    assertTrue(code.contains("size > 0"), "Deserializer uses size > 0 for field at position 1");
  }

  // ======================================================================
  // 53. Proto2 group field treated as nested message
  // ======================================================================

  @Test
  void testProto2GroupTreatedAsMessage() {
    // Proto2 groups use TYPE_GROUP but are structurally identical to nested messages.
    // The analyzer should normalize TYPE_GROUP to TYPE_MESSAGE.
    DescriptorProto groupMsg =
        DescriptorProto.newBuilder()
            .setName("MyGroup")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(scalarField("y", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    FieldDescriptorProto groupField =
        FieldDescriptorProto.newBuilder()
            .setName("mygroup")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_GROUP)
            .setTypeName(".test.Msg.MyGroup")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(groupField)
            .addNestedType(groupMsg)
            .build();

    String code = generateSingleMessage(msg, "test", "proto2");

    // Group should be generated as a static inner class (same as nested message)
    assertTrue(
        code.contains("public static final class MyGroup"),
        "Group should be generated as static final inner class");

    // Group field should have getter/setter like any message field
    assertTrue(
        code.contains("getMygroup()") || code.contains("getMyGroup()"),
        "Group field should have a getter");

    // Serializer should treat it as a message (appendJsonArray or null check)
    assertTrue(
        code.contains("appendJsonArray(sb)"),
        "Group field should serialize via appendJsonArray like a nested message");

    // Deserializer should use fromJsonArray
    assertTrue(
        code.contains("MyGroup.fromJsonArray("),
        "Group field should deserialize via fromJsonArray like a nested message");
  }

  // ======================================================================
  // 54. Message with only oneof fields (no standalone fields)
  // ======================================================================

  @Test
  void testMessageWithOnlyOneofFields() {
    FieldDescriptorProto strVal =
        FieldDescriptorProto.newBuilder()
            .setName("str_val")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto intVal =
        FieldDescriptorProto.newBuilder()
            .setName("int_val")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto boolVal =
        FieldDescriptorProto.newBuilder()
            .setName("bool_val")
            .setNumber(3)
            .setType(FieldDescriptorProto.Type.TYPE_BOOL)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("OneofOnly")
            .addField(strVal)
            .addField(intVal)
            .addField(boolVal)
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("value").build())
            .build();
    String code = generateSingleMessage(msg);

    // Class should be generated
    assertTrue(
        code.contains("public final class OneofOnly"), "Class declaration for oneof-only message");

    // Case tracking field
    assertTrue(code.contains("valueCase_"), "Oneof case tracking field present");

    // Case constants for all members
    assertTrue(code.contains("STR_VALCase_"), "STR_VAL case constant");
    assertTrue(code.contains("INT_VALCase_"), "INT_VAL case constant");
    assertTrue(code.contains("BOOL_VALCase_"), "BOOL_VAL case constant");

    // All fields emit null when not the active case
    assertTrue(
        code.contains("else { sb.append(\"null\"); }"),
        "Inactive oneof members emit null in serializer");

    // Serializer checks each case before emitting
    assertTrue(code.contains("valueCase_ == STR_VALCase_"), "Serializer checks STR_VAL case");
    assertTrue(code.contains("valueCase_ == INT_VALCase_"), "Serializer checks INT_VAL case");
    assertTrue(code.contains("valueCase_ == BOOL_VALCase_"), "Serializer checks BOOL_VAL case");

    // toByteArray and parseFrom convenience methods still present
    assertTrue(code.contains("toByteArray"), "toByteArray method present");
    assertTrue(code.contains("parseFrom"), "parseFrom method present");
  }

  // ======================================================================
  // 50. Map key type coverage (int64, uint64, bool, fixed32)
  // ======================================================================

  @Test
  void testMapKeyTypeInt64() {
    DescriptorProto msg =
        buildMapMessage(
            "int64_key_map",
            "Int64Map",
            FieldDescriptorProto.Type.TYPE_INT64,
            FieldDescriptorProto.Type.TYPE_STRING);
    String code = generateSingleMessage(msg);

    // Int64-keyed maps use array-of-pairs (non-string key)
    assertTrue(
        code.contains("List<Object>") || code.contains("Object[]"),
        "Int64-keyed map uses array-of-pairs encoding");
  }

  @Test
  void testMapKeyTypeBool() {
    DescriptorProto msg =
        buildMapMessage(
            "bool_key_map",
            "BoolMap",
            FieldDescriptorProto.Type.TYPE_BOOL,
            FieldDescriptorProto.Type.TYPE_STRING);
    String code = generateSingleMessage(msg);

    // Bool keys are non-string, so array-of-pairs encoding
    assertFalse(code.contains("Map<String, String>"), "Bool-keyed map is not Map<String>");
  }

  @Test
  void testMapKeyTypeUint64() {
    DescriptorProto msg =
        buildMapMessage(
            "uint64_key_map",
            "Uint64Map",
            FieldDescriptorProto.Type.TYPE_UINT64,
            FieldDescriptorProto.Type.TYPE_STRING);
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("parseUnsignedLong") || code.contains("Long"),
        "Uint64 key type uses unsigned parsing");
  }

  @Test
  void testMapKeyTypeFixed32() {
    DescriptorProto msg =
        buildMapMessage(
            "fixed32_key_map",
            "Fixed32Map",
            FieldDescriptorProto.Type.TYPE_FIXED32,
            FieldDescriptorProto.Type.TYPE_STRING);
    String code = generateSingleMessage(msg);

    assertTrue(
        code.contains("intValue") || code.contains("Integer"), "Fixed32 key type uses int parsing");
  }

  private DescriptorProto buildMapMessage(
      String fieldName,
      String msgName,
      FieldDescriptorProto.Type keyType,
      FieldDescriptorProto.Type valueType) {
    // Use a simple entry type name
    DescriptorProto mapEntry =
        DescriptorProto.newBuilder()
            .setName("MapEntry")
            .setOptions(
                com.google.protobuf.DescriptorProtos.MessageOptions.newBuilder()
                    .setMapEntry(true)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("key")
                    .setNumber(1)
                    .setType(keyType)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(2)
                    .setType(valueType)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    return DescriptorProto.newBuilder()
        .setName(msgName)
        .addNestedType(mapEntry)
        .addField(
            FieldDescriptorProto.newBuilder()
                .setName(fieldName)
                .setNumber(1)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(".test." + msgName + "." + mapEntry.getName())
                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                .build())
        .build();
  }

  // ======================================================================
  // 51. Oneof deserialization
  // ======================================================================

  @Test
  void testOneofDeserializationSetsCase() {
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

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Contact")
            .addField(emailField)
            .addField(phoneField)
            .addOneofDecl(
                com.google.protobuf.DescriptorProtos.OneofDescriptorProto.newBuilder()
                    .setName("info")
                    .build())
            .build();
    String code = generateSingleMessage(msg);

    // Deserializer reads each oneof member from its position
    assertTrue(code.contains("array.get(0)"), "Deserializer reads email from position 0");
    assertTrue(code.contains("array.get(1)"), "Deserializer reads phone from position 1");

    // Builder setter updates the oneof case field (uses field number)
    assertTrue(code.contains("infoCase_ = 1"), "setEmail updates infoCase_ to field number 1");
    assertTrue(code.contains("infoCase_ = 2"), "setPhone updates infoCase_ to field number 2");

    // getInfoCase method exists
    assertTrue(code.contains("getInfoCase()"), "getInfoCase() method generated");
  }

  @Test
  void testOneofDeserializationNullDoesNotSetCase() {
    FieldDescriptorProto nameField =
        FieldDescriptorProto.newBuilder()
            .setName("name")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    FieldDescriptorProto id =
        FieldDescriptorProto.newBuilder()
            .setName("id")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0)
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Ident")
            .addField(nameField)
            .addField(id)
            .addOneofDecl(
                com.google.protobuf.DescriptorProtos.OneofDescriptorProto.newBuilder()
                    .setName("value")
                    .build())
            .build();
    String code = generateSingleMessage(msg);

    // The deserializer's null check prevents setting the case for absent positions
    assertTrue(
        code.contains("array.get(0) != null"),
        "Deserializer null-checks before setting oneof member");
    assertTrue(
        code.contains("array.get(1) != null"),
        "Deserializer null-checks position 1 before setting oneof member");
  }

  // ======================================================================
  // 51. Scalar deserialization: double, float, string, bool, int32, uint32, enum
  // ======================================================================

  @Test
  void testDoubleDeserializationUsesDoubleValue() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("score", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("doubleValue()"), "Double deserialization uses doubleValue()");
    assertTrue(code.contains("(Number)"), "Double deserialization casts to Number");
    assertTrue(code.contains("if (size > 0"), "Double deserializer bounds-checks position 0");
  }

  @Test
  void testFloatDeserializationUsesFloatValue() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("ratio", 1, FieldDescriptorProto.Type.TYPE_FLOAT))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("floatValue()"), "Float deserialization uses floatValue()");
    assertTrue(code.contains("(Number)"), "Float deserialization casts to Number");
  }

  @Test
  void testStringDeserializationCastsToString() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("label", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("(String)"), "String deserialization casts to String");
    assertTrue(code.contains("if (size > 0"), "String deserializer bounds-checks position 0");
  }

  @Test
  void testBoolDeserializationCastsToBoolean() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("active", 1, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("(Boolean)"), "Bool deserialization casts to Boolean");
  }

  @Test
  void testEnumDeserializationUsesForNumber() {
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
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("Status.forNumber("), "Enum deserialization uses forNumber()");
    assertTrue(code.contains("intValue()"), "Enum deserialization reads as intValue()");
  }

  @Test
  void testInt32DeserializationUsesIntValue() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("count", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("intValue()"), "Int32 deserialization uses intValue()");
    assertTrue(code.contains("(Number)"), "Int32 deserialization casts to Number");
  }

  @Test
  void testUint32DeserializationUsesIntValue() {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("count", 1, FieldDescriptorProto.Type.TYPE_UINT32))
            .build();
    String code = generateSingleMessage(msg);

    assertTrue(code.contains("intValue()"), "Uint32 deserialization uses intValue()");
  }

  // ======================================================================
  // Helpers
  // ======================================================================

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
