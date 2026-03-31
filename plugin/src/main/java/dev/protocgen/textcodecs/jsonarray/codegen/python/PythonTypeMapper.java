/*
 * Copyright 2024 protobuf-text-codecs contributors
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
package dev.protocgen.textcodecs.jsonarray.codegen.python;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

/** Maps proto types to Python types, default values, and type hints. */
public class PythonTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = scalarType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
          || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = simpleTypeName(field.getMapValueTypeReference());
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      return "Dict[" + keyType + ", " + valueType + "]";
    }
    if (field.isRepeated()) {
      String elementType;
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        elementType = simpleTypeName(field.getTypeReference());
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        elementType = "int";
      } else {
        elementType = scalarType(field.getProtoType());
      }
      return "List[" + elementType + "]";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      // Enums are represented as int in Python
      return "int";
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // Python doesn't have boxed vs unboxed distinction — same as languageType
    return languageType(field);
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "{}";
    if (field.isRepeated()) return "[]";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "None";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "0";
    }
    return scalarDefault(field.getProtoType());
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "float";
      case TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32 ->
          "int";
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "str";
      case TYPE_BYTES -> "bytes";
      default -> "object";
    };
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "0.0";
      case TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32 ->
          "0";
      case TYPE_BOOL -> "False";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "b\"\"";
      default -> "None";
    };
  }

  /** Extract the simple type name from a fully-qualified proto type reference. */
  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "object";
  }
}
