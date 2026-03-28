package dev.protocgen.textcodecs.pbtkurl;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for pbtk URL encoding Java code generation. */
class PbtkJavaCodeGenTest {

  private CodeGeneratorResponse generate(FileDescriptorProto file) {
    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate(file.getName())
            .setParameter("lang=java")
            .build();
    return new PbtkPluginRunner().run(request);
  }

  private String generatedCode(FileDescriptorProto file) {
    CodeGeneratorResponse response = generate(file);
    assertFalse(response.hasError(), "Plugin error: " + response.getError());
    assertTrue(response.getFileCount() > 0, "No files generated");
    return response.getFile(0).getContent();
  }

  // --- Helper to build a proto file ---

  private FileDescriptorProto.Builder protoFile() {
    return FileDescriptorProto.newBuilder()
        .setName("test.proto")
        .setSyntax("proto3")
        .setPackage("com.example");
  }

  private FieldDescriptorProto.Builder field(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
  }

  // --- Tests ---

  @Test
  void testScalarStringField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("User")
                    .addField(
                        field("name", 1, FieldDescriptorProto.Type.TYPE_STRING)))
            .build();

    String code = generatedCode(file);

    // Should generate toPbtkUrl method
    assertTrue(code.contains("toPbtkUrl"), "Missing toPbtkUrl method");
    assertTrue(code.contains("fromPbtkUrl"), "Missing fromPbtkUrl method");

    // Serializer should use !1s prefix and URL encoding
    assertTrue(code.contains("!1s"), "Missing field 1 string prefix");
    assertTrue(code.contains("URLEncoder.encode"), "Missing URL encoding for string");

    // Deserializer should use URLDecoder
    assertTrue(code.contains("URLDecoder.decode"), "Missing URL decoding for string");

    // Should NOT have Jackson/ObjectMapper references
    assertFalse(code.contains("ObjectMapper"), "Should not reference ObjectMapper");
    assertFalse(code.contains("ArrayNode"), "Should not reference ArrayNode");
  }

  @Test
  void testScalarInt32Field() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Counter")
                    .addField(field("count", 1, FieldDescriptorProto.Type.TYPE_INT32)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1i"), "Missing field 1 integer prefix");
    assertTrue(code.contains("Integer.parseInt"), "Missing int parsing in deserializer");
  }

  @Test
  void testScalarBoolField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Flag")
                    .addField(field("active", 1, FieldDescriptorProto.Type.TYPE_BOOL)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1b"), "Missing field 1 bool prefix");
    assertTrue(code.contains("\"1\" : \"0\""), "Missing bool 1/0 encoding");
    assertTrue(code.contains("\"1\".equals"), "Missing bool 1 decoding");
  }

  @Test
  void testScalarDoubleField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Measurement")
                    .addField(field("value", 1, FieldDescriptorProto.Type.TYPE_DOUBLE)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1d"), "Missing field 1 double prefix");
    assertTrue(code.contains("Double.isNaN"), "Missing NaN check");
    assertTrue(code.contains("Double.parseDouble"), "Missing double parsing");
  }

  @Test
  void testScalarFloatField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Measurement")
                    .addField(field("value", 1, FieldDescriptorProto.Type.TYPE_FLOAT)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1f"), "Missing field 1 float prefix");
    assertTrue(code.contains("Float.isNaN"), "Missing NaN check");
  }

  @Test
  void testScalarBytesField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Blob")
                    .addField(field("data", 1, FieldDescriptorProto.Type.TYPE_BYTES)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1z"), "Missing field 1 bytes/z prefix");
    assertTrue(code.contains("Base64.getEncoder"), "Missing base64 encoding");
    assertTrue(code.contains("Base64.getDecoder"), "Missing base64 decoding");
  }

  @Test
  void testNestedMessage() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Outer")
                    .addField(
                        field("inner", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".com.example.Outer.Inner"))
                    .addNestedType(
                        DescriptorProto.newBuilder()
                            .setName("Inner")
                            .addField(
                                field("value", 1, FieldDescriptorProto.Type.TYPE_INT32))))
            .build();

    String code = generatedCode(file);
    // Nested message should use m type char with count
    assertTrue(code.contains("!1m"), "Missing message field prefix");
    assertTrue(code.contains("countPbtkFields"), "Missing countPbtkFields");
    assertTrue(code.contains("appendPbtkFields"), "Missing appendPbtkFields");
    assertTrue(code.contains("parsePbtkTokens"), "Missing parsePbtkTokens in deserializer");
  }

  @Test
  void testEnumField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Msg")
                    .addField(
                        field("status", 1, FieldDescriptorProto.Type.TYPE_ENUM)
                            .setTypeName(".com.example.Msg.Status"))
                    .addEnumType(
                        EnumDescriptorProto.newBuilder()
                            .setName("Status")
                            .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                    .setName("UNKNOWN")
                                    .setNumber(0))
                            .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                    .setName("ACTIVE")
                                    .setNumber(1))))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1e"), "Missing enum field prefix");
    assertTrue(code.contains("getNumber()"), "Missing getNumber in serializer");
    assertTrue(code.contains("forNumber"), "Missing forNumber in deserializer");
  }

  @Test
  void testRepeatedField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Tags")
                    .addField(
                        field("tag", 1, FieldDescriptorProto.Type.TYPE_STRING)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)))
            .build();

    String code = generatedCode(file);
    // Repeated fields emit one !<num><type><value> per element
    assertTrue(code.contains("for ("), "Missing loop for repeated field");
    assertTrue(code.contains("!1s"), "Missing repeated string prefix");
  }

  @Test
  void testInt64AsString() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("BigNum")
                    .addField(field("value", 1, FieldDescriptorProto.Type.TYPE_INT64)))
            .build();

    String code = generatedCode(file);
    // int64 uses 'i' type char in pbtk format (unlike JSON array which uses strings)
    assertTrue(code.contains("!1i"), "Missing int64 prefix");
    assertTrue(code.contains("Long.parseLong"), "Missing long parsing");
  }

  @Test
  void testMultipleFields() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Person")
                    .addField(field("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(field("age", 2, FieldDescriptorProto.Type.TYPE_INT32))
                    .addField(field("active", 3, FieldDescriptorProto.Type.TYPE_BOOL)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1s"), "Missing name field prefix");
    assertTrue(code.contains("!2i"), "Missing age field prefix");
    assertTrue(code.contains("!3b"), "Missing active field prefix");
  }

  @Test
  void testGeneratedMarker() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Msg")
                    .addField(field("x", 1, FieldDescriptorProto.Type.TYPE_INT32)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("@generated by protoc-gen-pbtkurl"), "Missing generated marker");
  }

  @Test
  void testNoObjectMapperDependency() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Simple")
                    .addField(field("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
                    .addField(field("y", 2, FieldDescriptorProto.Type.TYPE_STRING)))
            .build();

    String code = generatedCode(file);
    assertFalse(code.contains("jackson"), "Should not reference Jackson");
    assertFalse(code.contains("ObjectMapper"), "Should not reference ObjectMapper");
    assertFalse(code.contains("ArrayNode"), "Should not reference ArrayNode");
    assertFalse(code.contains("JsonNode"), "Should not reference JsonNode");
  }

  @Test
  void testMapField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Config")
                    .addField(
                        field("settings", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".com.example.Config.SettingsEntry")
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED))
                    .addNestedType(
                        DescriptorProto.newBuilder()
                            .setName("SettingsEntry")
                            .setOptions(
                                MessageOptions.newBuilder().setMapEntry(true))
                            .addField(
                                field("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
                            .addField(
                                field("value", 2, FieldDescriptorProto.Type.TYPE_STRING))))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("!1m2"), "Missing map entry m2 prefix");
    assertTrue(code.contains("entrySet"), "Missing map iteration");
  }

  @Test
  void testEqualsAndHashCode() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Msg")
                    .addField(field("x", 1, FieldDescriptorProto.Type.TYPE_INT32)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("equals(Object o)"), "Missing equals method");
    assertTrue(code.contains("hashCode()"), "Missing hashCode method");
    assertTrue(code.contains("toString()"), "Missing toString method");
  }

  @Test
  void testClassStructure() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("User")
                    .addField(field("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(field("age", 2, FieldDescriptorProto.Type.TYPE_INT32)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("package com.example;"), "Missing package declaration");
    assertTrue(code.contains("public class User"), "Missing class declaration");
    assertTrue(code.contains("getName()"), "Missing getter");
    assertTrue(code.contains("setName("), "Missing setter");
    assertTrue(code.contains("getAge()"), "Missing getter");
    assertTrue(code.contains("setAge("), "Missing setter");
  }

  @Test
  void testUint32UnsignedEncoding() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Msg")
                    .addField(field("x", 1, FieldDescriptorProto.Type.TYPE_UINT32)))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("Integer.toUnsignedLong"), "Missing unsigned int encoding");
    assertTrue(code.contains("Integer.parseUnsignedInt"), "Missing unsigned int parsing");
  }

  // --- Edge case tests ---

  @Test
  void testEmptyMessage() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(DescriptorProto.newBuilder().setName("Empty"))
            .build();

    String code = generatedCode(file);
    assertTrue(code.contains("public class Empty"), "Missing class declaration");
    assertTrue(code.contains("toPbtkUrl"), "Missing toPbtkUrl method");
    assertTrue(code.contains("fromPbtkUrl"), "Missing fromPbtkUrl method");
    assertTrue(code.contains("appendPbtkFields"), "Missing appendPbtkFields method");
    assertTrue(code.contains("countPbtkFields"), "Missing countPbtkFields method");
  }

  @Test
  void testOneofFields() {
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

    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Contact")
                    .addField(field("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(emailField)
                    .addField(phoneField)
                    .addOneofDecl(
                        OneofDescriptorProto.newBuilder().setName("contact").build()))
            .build();

    String code = generatedCode(file);

    // Case tracking constants
    assertTrue(code.contains("EMAILCase_"), "Missing EMAIL case constant");
    assertTrue(code.contains("PHONECase_"), "Missing PHONE case constant");
    assertTrue(code.contains("contactCase_"), "Missing contact case tracking field");

    // Serializer checks case before emitting oneof field
    assertTrue(
        code.contains("contactCase_ == EMAILCase_"),
        "Missing conditional serialization for email oneof");
  }

  @Test
  void testDeeplyNestedMessage() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Outer")
                    .addField(
                        field("inner", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".com.example.Outer.Inner"))
                    .addNestedType(
                        DescriptorProto.newBuilder()
                            .setName("Inner")
                            .addField(
                                field("inner_inner", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                                    .setTypeName(".com.example.Outer.Inner.InnerInner"))
                            .addNestedType(
                                DescriptorProto.newBuilder()
                                    .setName("InnerInner")
                                    .addField(
                                        field(
                                            "value",
                                            1,
                                            FieldDescriptorProto.Type.TYPE_INT32)))))
            .build();

    String code = generatedCode(file);

    // Outer has nested Inner which has nested InnerInner
    assertTrue(code.contains("public class Outer"), "Missing Outer class");
    assertTrue(code.contains("public static class Inner"), "Missing Inner nested class");
    assertTrue(
        code.contains("public static class InnerInner"), "Missing InnerInner nested class");

    // Each level should use 'm' type for message fields
    assertTrue(code.contains("!1m"), "Missing message field prefix at some level");

    // InnerInner should have its own countPbtkFields/appendPbtkFields
    // Count occurrences — should appear at least 3 times (Outer, Inner, InnerInner)
    int count = 0;
    int idx = 0;
    while ((idx = code.indexOf("countPbtkFields()", idx)) >= 0) {
      count++;
      idx++;
    }
    assertTrue(count >= 3, "Expected countPbtkFields in all 3 levels, found " + count);
  }

  @Test
  void testRepeatedMessageField() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Conversation")
                    .addField(
                        field("messages", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".com.example.Conversation.ChatMessage")
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED))
                    .addNestedType(
                        DescriptorProto.newBuilder()
                            .setName("ChatMessage")
                            .addField(
                                field("text", 1, FieldDescriptorProto.Type.TYPE_STRING))))
            .build();

    String code = generatedCode(file);

    // Should iterate over repeated message field
    assertTrue(code.contains("for ("), "Missing loop for repeated message field");
    assertTrue(code.contains("!1m"), "Missing message prefix for repeated messages");
    assertTrue(
        code.contains("countPbtkFields()"),
        "Missing countPbtkFields call for repeated message elements");
    assertTrue(
        code.contains("appendPbtkFields(sb)"),
        "Missing appendPbtkFields call for repeated message elements");

    // Deserializer should add parsed messages to the list
    assertTrue(
        code.contains("parsePbtkTokens"), "Missing parsePbtkTokens in repeated message deserializer");
  }

  @Test
  void testMapWithMessageValue() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Registry")
                    .addField(
                        field("entries", 1, FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".com.example.Registry.EntriesEntry")
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED))
                    .addNestedType(
                        DescriptorProto.newBuilder()
                            .setName("EntriesEntry")
                            .setOptions(MessageOptions.newBuilder().setMapEntry(true))
                            .addField(
                                field("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
                            .addField(
                                field("value", 2, FieldDescriptorProto.Type.TYPE_MESSAGE)
                                    .setTypeName(".com.example.Registry.Detail")))
                    .addNestedType(
                        DescriptorProto.newBuilder()
                            .setName("Detail")
                            .addField(
                                field("info", 1, FieldDescriptorProto.Type.TYPE_STRING))))
            .build();

    String code = generatedCode(file);

    // Map serialization uses m prefix with count for the entry
    assertTrue(code.contains("entrySet"), "Missing map iteration via entrySet");
    // Value is a message, so serializer should use countPbtkFields on it
    assertTrue(
        code.contains("!2m"),
        "Missing !2m prefix for message-typed map value");
    assertTrue(
        code.contains("countPbtkFields"),
        "Missing countPbtkFields for message map value");
  }

  @Test
  void testProto3OptionalPresence() {
    FieldDescriptorProto optField =
        FieldDescriptorProto.newBuilder()
            .setName("nickname")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setProto3Optional(true)
            .setOneofIndex(0)
            .build();

    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Profile")
                    .addField(optField)
                    .addOneofDecl(
                        OneofDescriptorProto.newBuilder().setName("_nickname").build()))
            .build();

    String code = generatedCode(file);

    // Presence tracking with BitSet
    assertTrue(code.contains("BitSet"), "Missing BitSet for proto3 optional presence");
    assertTrue(code.contains("presentFields_"), "Missing presentFields_ field");

    // has*() method
    assertTrue(code.contains("hasNickname"), "Missing has method for optional field");

    // Serializer should check presence bit before emitting
    assertTrue(
        code.contains("presentFields_.get(0)"),
        "Missing presence check in serializer for proto3 optional");
  }

  @Test
  void testFieldNumberGap() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Sparse")
                    .addField(field("first", 1, FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(field("fifth", 5, FieldDescriptorProto.Type.TYPE_INT32)))
            .build();

    String code = generatedCode(file);

    // Should have both fields with correct field numbers
    assertTrue(code.contains("!1s"), "Missing field 1 string prefix");
    assertTrue(code.contains("!5i"), "Missing field 5 int prefix");

    // Should NOT have any null padding for fields 2, 3, 4
    // (unlike JSON array format, pbtk URL uses field numbers directly)
    assertFalse(code.contains("null, null"), "Should not have null padding for gaps");
    assertFalse(code.contains("!2"), "Should not reference field 2");
    assertFalse(code.contains("!3"), "Should not reference field 3");
    assertFalse(code.contains("!4"), "Should not reference field 4");
  }

  @Test
  void testNaNInfinityOmitted() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Coords")
                    .addField(field("lat", 1, FieldDescriptorProto.Type.TYPE_DOUBLE))
                    .addField(field("lng", 2, FieldDescriptorProto.Type.TYPE_DOUBLE))
                    .addField(field("altitude", 3, FieldDescriptorProto.Type.TYPE_FLOAT)))
            .build();

    String code = generatedCode(file);

    // Double fields should have NaN and Infinity checks
    assertTrue(code.contains("Double.isNaN"), "Missing Double.isNaN check");
    assertTrue(code.contains("Double.isInfinite"), "Missing Double.isInfinite check");

    // Float field should have NaN and Infinity checks
    assertTrue(code.contains("Float.isNaN"), "Missing Float.isNaN check");
    assertTrue(code.contains("Float.isInfinite"), "Missing Float.isInfinite check");

    // Both countPbtkFields and appendPbtkFields should guard against NaN/Infinity
    // (NaN/Infinity fields are omitted, so count should also skip them)
    int nanCheckCount = 0;
    int searchIdx = 0;
    while ((searchIdx = code.indexOf("isNaN", searchIdx)) >= 0) {
      nanCheckCount++;
      searchIdx++;
    }
    // At least 2 checks for double (serialize + count), plus float checks
    assertTrue(nanCheckCount >= 4, "Expected NaN checks in both serializer and counter, found " + nanCheckCount);
  }

  @Test
  void testSfixed64Field() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("Timestamps")
                    .addField(field("ts", 1, FieldDescriptorProto.Type.TYPE_SFIXED64)))
            .build();

    String code = generatedCode(file);

    // sfixed64 should use 'i' type char just like all integer types
    assertTrue(code.contains("!1i"), "sfixed64 should use 'i' type char");
    assertTrue(code.contains("Long.parseLong"), "sfixed64 should parse as long");
  }

  @Test
  void testMultipleRepeatedFields() {
    FileDescriptorProto file =
        protoFile()
            .addMessageType(
                DescriptorProto.newBuilder()
                    .setName("MultiList")
                    .addField(
                        field("names", 1, FieldDescriptorProto.Type.TYPE_STRING)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED))
                    .addField(
                        field("scores", 2, FieldDescriptorProto.Type.TYPE_INT32)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)))
            .build();

    String code = generatedCode(file);

    // Both repeated fields should use loops
    assertTrue(code.contains("!1s"), "Missing repeated string prefix for names");
    assertTrue(code.contains("!2i"), "Missing repeated int prefix for scores");

    // Count should add sizes of both lists
    assertTrue(code.contains("size()"), "Missing size() calls for repeated field counting");

    // Two for loops for two repeated fields
    int forCount = 0;
    int forIdx = 0;
    while ((forIdx = code.indexOf("for (", forIdx)) >= 0) {
      forCount++;
      forIdx++;
    }
    assertTrue(forCount >= 2, "Expected at least 2 for loops for 2 repeated fields, found " + forCount);
  }
}
