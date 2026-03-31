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

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.java.JavaTypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.WellKnownType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaTypeMapperTest {

  private final JavaTypeMapper mapper = new JavaTypeMapper();

  // ---- helper to build a ProtoField ----

  private ProtoField scalarField(FieldDescriptorProto.Type type) {
    return ProtoField.builder()
        .name("f")
        .fieldNumber(1)
        .protoType(type)
        .kind(ProtoField.FieldKind.SCALAR)
        .cardinality(ProtoField.Cardinality.SINGULAR)
        .build();
  }

  private ProtoField messageField(String typeRef) {
    return ProtoField.builder()
        .name("f")
        .fieldNumber(1)
        .protoType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .kind(ProtoField.FieldKind.MESSAGE)
        .cardinality(ProtoField.Cardinality.SINGULAR)
        .typeReference(typeRef)
        .build();
  }

  private ProtoField enumField(String typeRef) {
    return ProtoField.builder()
        .name("f")
        .fieldNumber(1)
        .protoType(FieldDescriptorProto.Type.TYPE_ENUM)
        .kind(ProtoField.FieldKind.ENUM)
        .cardinality(ProtoField.Cardinality.SINGULAR)
        .typeReference(typeRef)
        .build();
  }

  private ProtoField repeatedScalarField(FieldDescriptorProto.Type type) {
    return ProtoField.builder()
        .name("f")
        .fieldNumber(1)
        .protoType(type)
        .kind(ProtoField.FieldKind.SCALAR)
        .cardinality(ProtoField.Cardinality.REPEATED)
        .build();
  }

  private ProtoField repeatedMessageField(String typeRef) {
    return ProtoField.builder()
        .name("f")
        .fieldNumber(1)
        .protoType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .kind(ProtoField.FieldKind.MESSAGE)
        .cardinality(ProtoField.Cardinality.REPEATED)
        .typeReference(typeRef)
        .build();
  }

  private ProtoField repeatedEnumField(String typeRef) {
    return ProtoField.builder()
        .name("f")
        .fieldNumber(1)
        .protoType(FieldDescriptorProto.Type.TYPE_ENUM)
        .kind(ProtoField.FieldKind.ENUM)
        .cardinality(ProtoField.Cardinality.REPEATED)
        .typeReference(typeRef)
        .build();
  }

  private ProtoField mapField(
      FieldDescriptorProto.Type keyType, FieldDescriptorProto.Type valueType, String valueTypeRef) {
    ProtoField.Builder b =
        ProtoField.builder()
            .name("f")
            .fieldNumber(1)
            .protoType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .kind(ProtoField.FieldKind.MESSAGE)
            .cardinality(ProtoField.Cardinality.MAP)
            .mapKeyType(keyType)
            .mapValueType(valueType);
    if (valueTypeRef != null) {
      b.mapValueTypeReference(valueTypeRef);
    }
    return b.build();
  }

  private ProtoField wellKnownTypeField(WellKnownType wkt) {
    return ProtoField.builder()
        .name("f")
        .fieldNumber(1)
        .protoType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .kind(ProtoField.FieldKind.WELL_KNOWN_TYPE)
        .cardinality(ProtoField.Cardinality.SINGULAR)
        .typeReference(wkt.getFullName())
        .wellKnownType(wkt)
        .build();
  }

  // ---- scalarType for ALL proto types ----

  @Test
  void testScalarTypeDouble() {
    assertEquals("double", mapper.scalarType(FieldDescriptorProto.Type.TYPE_DOUBLE));
  }

  @Test
  void testScalarTypeFloat() {
    assertEquals("float", mapper.scalarType(FieldDescriptorProto.Type.TYPE_FLOAT));
  }

  @Test
  void testScalarTypeInt64() {
    assertEquals("long", mapper.scalarType(FieldDescriptorProto.Type.TYPE_INT64));
  }

  @Test
  void testScalarTypeSint64() {
    assertEquals("long", mapper.scalarType(FieldDescriptorProto.Type.TYPE_SINT64));
  }

  @Test
  void testScalarTypeSfixed64() {
    assertEquals("long", mapper.scalarType(FieldDescriptorProto.Type.TYPE_SFIXED64));
  }

  @Test
  void testScalarTypeUint64() {
    assertEquals("long", mapper.scalarType(FieldDescriptorProto.Type.TYPE_UINT64));
  }

  @Test
  void testScalarTypeFixed64() {
    assertEquals("long", mapper.scalarType(FieldDescriptorProto.Type.TYPE_FIXED64));
  }

  @Test
  void testScalarTypeInt32() {
    assertEquals("int", mapper.scalarType(FieldDescriptorProto.Type.TYPE_INT32));
  }

  @Test
  void testScalarTypeSint32() {
    assertEquals("int", mapper.scalarType(FieldDescriptorProto.Type.TYPE_SINT32));
  }

  @Test
  void testScalarTypeSfixed32() {
    assertEquals("int", mapper.scalarType(FieldDescriptorProto.Type.TYPE_SFIXED32));
  }

  @Test
  void testScalarTypeUint32() {
    assertEquals("int", mapper.scalarType(FieldDescriptorProto.Type.TYPE_UINT32));
  }

  @Test
  void testScalarTypeFixed32() {
    assertEquals("int", mapper.scalarType(FieldDescriptorProto.Type.TYPE_FIXED32));
  }

  @Test
  void testScalarTypeBool() {
    assertEquals("boolean", mapper.scalarType(FieldDescriptorProto.Type.TYPE_BOOL));
  }

  @Test
  void testScalarTypeString() {
    assertEquals("String", mapper.scalarType(FieldDescriptorProto.Type.TYPE_STRING));
  }

  @Test
  void testScalarTypeBytes() {
    assertEquals("byte[]", mapper.scalarType(FieldDescriptorProto.Type.TYPE_BYTES));
  }

  // ---- boxedScalarType for all types ----

  @Test
  void testBoxedScalarTypeDouble() {
    assertEquals("Double", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_DOUBLE));
  }

  @Test
  void testBoxedScalarTypeFloat() {
    assertEquals("Float", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_FLOAT));
  }

  @Test
  void testBoxedScalarTypeInt64() {
    assertEquals("Long", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_INT64));
  }

  @Test
  void testBoxedScalarTypeSint64() {
    assertEquals("Long", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_SINT64));
  }

  @Test
  void testBoxedScalarTypeSfixed64() {
    assertEquals("Long", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_SFIXED64));
  }

  @Test
  void testBoxedScalarTypeUint64() {
    assertEquals("Long", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_UINT64));
  }

  @Test
  void testBoxedScalarTypeFixed64() {
    assertEquals("Long", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_FIXED64));
  }

  @Test
  void testBoxedScalarTypeInt32() {
    assertEquals("Integer", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_INT32));
  }

  @Test
  void testBoxedScalarTypeSint32() {
    assertEquals("Integer", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_SINT32));
  }

  @Test
  void testBoxedScalarTypeSfixed32() {
    assertEquals("Integer", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_SFIXED32));
  }

  @Test
  void testBoxedScalarTypeUint32() {
    assertEquals("Integer", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_UINT32));
  }

  @Test
  void testBoxedScalarTypeFixed32() {
    assertEquals("Integer", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_FIXED32));
  }

  @Test
  void testBoxedScalarTypeBool() {
    assertEquals("Boolean", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_BOOL));
  }

  @Test
  void testBoxedScalarTypeString() {
    assertEquals("String", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_STRING));
  }

  @Test
  void testBoxedScalarTypeBytes() {
    assertEquals("byte[]", mapper.boxedScalarType(FieldDescriptorProto.Type.TYPE_BYTES));
  }

  // ---- defaultValue for scalars ----

  @Test
  void testDefaultValueDouble() {
    assertEquals("0.0", mapper.defaultValue(scalarField(FieldDescriptorProto.Type.TYPE_DOUBLE)));
  }

  @Test
  void testDefaultValueFloat() {
    assertEquals("0.0f", mapper.defaultValue(scalarField(FieldDescriptorProto.Type.TYPE_FLOAT)));
  }

  @Test
  void testDefaultValueInt64() {
    assertEquals("0L", mapper.defaultValue(scalarField(FieldDescriptorProto.Type.TYPE_INT64)));
  }

  @Test
  void testDefaultValueInt32() {
    assertEquals("0", mapper.defaultValue(scalarField(FieldDescriptorProto.Type.TYPE_INT32)));
  }

  @Test
  void testDefaultValueBool() {
    assertEquals("false", mapper.defaultValue(scalarField(FieldDescriptorProto.Type.TYPE_BOOL)));
  }

  @Test
  void testDefaultValueString() {
    assertEquals("\"\"", mapper.defaultValue(scalarField(FieldDescriptorProto.Type.TYPE_STRING)));
  }

  @Test
  void testDefaultValueBytes() {
    assertEquals(
        "new byte[0]", mapper.defaultValue(scalarField(FieldDescriptorProto.Type.TYPE_BYTES)));
  }

  // ---- defaultValue for messages, enums, repeated, maps ----

  @Test
  void testDefaultValueMessage() {
    assertEquals("null", mapper.defaultValue(messageField(".example.Address")));
  }

  @Test
  void testDefaultValueWellKnownType() {
    assertEquals("null", mapper.defaultValue(wellKnownTypeField(WellKnownType.TIMESTAMP)));
  }

  @Test
  void testDefaultValueEnum() {
    assertEquals("Status.forNumber(0)", mapper.defaultValue(enumField(".example.Status")));
  }

  @Test
  void testDefaultValueRepeated() {
    assertEquals(
        "new java.util.ArrayList<>()",
        mapper.defaultValue(repeatedScalarField(FieldDescriptorProto.Type.TYPE_STRING)));
  }

  @Test
  void testDefaultValueMap() {
    assertEquals(
        "new java.util.LinkedHashMap<>()",
        mapper.defaultValue(
            mapField(
                FieldDescriptorProto.Type.TYPE_STRING,
                FieldDescriptorProto.Type.TYPE_INT32,
                null)));
  }

  // ---- languageType for map fields ----

  @Test
  void testLanguageTypeMapStringToInt() {
    ProtoField field =
        mapField(FieldDescriptorProto.Type.TYPE_STRING, FieldDescriptorProto.Type.TYPE_INT32, null);
    assertEquals("java.util.Map<String, Integer>", mapper.languageType(field));
  }

  @Test
  void testLanguageTypeMapStringToMessage() {
    ProtoField field =
        mapField(
            FieldDescriptorProto.Type.TYPE_STRING,
            FieldDescriptorProto.Type.TYPE_MESSAGE,
            ".example.Address");
    assertEquals("java.util.Map<String, Address>", mapper.languageType(field));
  }

  @Test
  void testLanguageTypeMapStringToEnum() {
    ProtoField field =
        mapField(
            FieldDescriptorProto.Type.TYPE_STRING,
            FieldDescriptorProto.Type.TYPE_ENUM,
            ".example.Status");
    assertEquals("java.util.Map<String, Status>", mapper.languageType(field));
  }

  @Test
  void testLanguageTypeMapInt64ToString() {
    ProtoField field =
        mapField(FieldDescriptorProto.Type.TYPE_INT64, FieldDescriptorProto.Type.TYPE_STRING, null);
    assertEquals("java.util.Map<Long, String>", mapper.languageType(field));
  }

  // ---- languageType for repeated fields ----

  @Test
  void testLanguageTypeRepeatedScalar() {
    assertEquals(
        "java.util.List<String>",
        mapper.languageType(repeatedScalarField(FieldDescriptorProto.Type.TYPE_STRING)));
  }

  @Test
  void testLanguageTypeRepeatedInt32() {
    assertEquals(
        "java.util.List<Integer>",
        mapper.languageType(repeatedScalarField(FieldDescriptorProto.Type.TYPE_INT32)));
  }

  @Test
  void testLanguageTypeRepeatedMessage() {
    assertEquals(
        "java.util.List<Address>", mapper.languageType(repeatedMessageField(".example.Address")));
  }

  @Test
  void testLanguageTypeRepeatedEnum() {
    assertEquals(
        "java.util.List<Status>", mapper.languageType(repeatedEnumField(".example.Status")));
  }

  // ---- languageType for singular message and enum fields ----

  @Test
  void testLanguageTypeMessage() {
    assertEquals("Address", mapper.languageType(messageField(".example.Address")));
  }

  @Test
  void testLanguageTypeEnum() {
    assertEquals("Status", mapper.languageType(enumField(".example.Status")));
  }

  @Test
  void testLanguageTypeWellKnownType() {
    assertEquals("Timestamp", mapper.languageType(wellKnownTypeField(WellKnownType.TIMESTAMP)));
  }

  // ---- languageType for singular scalars ----

  @Test
  void testLanguageTypeSingularDouble() {
    assertEquals("double", mapper.languageType(scalarField(FieldDescriptorProto.Type.TYPE_DOUBLE)));
  }

  @Test
  void testLanguageTypeSingularString() {
    assertEquals("String", mapper.languageType(scalarField(FieldDescriptorProto.Type.TYPE_STRING)));
  }

  @Test
  void testLanguageTypeSingularBool() {
    assertEquals("boolean", mapper.languageType(scalarField(FieldDescriptorProto.Type.TYPE_BOOL)));
  }

  // ---- boxedType ----

  @Test
  void testBoxedTypeForScalar() {
    ProtoField field = scalarField(FieldDescriptorProto.Type.TYPE_INT32);
    assertEquals("Integer", mapper.boxedType(field));
  }

  @Test
  void testBoxedTypeForMessage() {
    // For message fields, boxedType should return the same as languageType
    ProtoField field = messageField(".example.Address");
    assertEquals("Address", mapper.boxedType(field));
  }

  @Test
  void testBoxedTypeForEnum() {
    ProtoField field = enumField(".example.Status");
    assertEquals("Status", mapper.boxedType(field));
  }

  @Test
  void testBoxedTypeForRepeated() {
    ProtoField field = repeatedScalarField(FieldDescriptorProto.Type.TYPE_INT32);
    assertEquals("java.util.List<Integer>", mapper.boxedType(field));
  }

  @Test
  void testBoxedTypeForMap() {
    ProtoField field =
        mapField(FieldDescriptorProto.Type.TYPE_STRING, FieldDescriptorProto.Type.TYPE_INT32, null);
    assertEquals("java.util.Map<String, Integer>", mapper.boxedType(field));
  }
}
