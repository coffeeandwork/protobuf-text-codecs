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
package dev.protocgen.textcodecs.jsonarray.codegen.go;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

/** Maps proto types to Go types, pointer types, and default value expressions. */
public class GoTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = scalarType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        valueType = "*" + simpleTypeName(field.getMapValueTypeReference());
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = simpleTypeName(field.getMapValueTypeReference());
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      return "map[" + keyType + "]" + valueType;
    }
    if (field.isRepeated()) {
      String elementType;
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        elementType = "*" + simpleTypeName(field.getTypeReference());
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        elementType = simpleTypeName(field.getTypeReference());
      } else {
        elementType = scalarType(field.getProtoType());
      }
      return "[]" + elementType;
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "*" + simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference());
    }
    // Proto3 optional scalars use pointers for presence tracking
    if (field.isProto3Optional()) {
      return "*" + scalarType(field.getProtoType());
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // Go doesn't have boxed types. For optional scalars, we use pointers.
    // This method returns the pointer type for scalars that need nullable representation.
    if (field.isMap()
        || field.isRepeated()
        || field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE
        || field.getKind() == ProtoField.FieldKind.ENUM) {
      return languageType(field);
    }
    return "*" + scalarType(field.getProtoType());
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "make(" + languageType(field) + ")";
    if (field.isRepeated()) return "nil";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "nil";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "0";
    }
    if (field.isProto3Optional()) {
      return "nil";
    }
    return scalarDefault(field.getProtoType());
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "float64";
      case TYPE_FLOAT -> "float32";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "int64";
      case TYPE_UINT64, TYPE_FIXED64 -> "uint64";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "int32";
      case TYPE_UINT32, TYPE_FIXED32 -> "uint32";
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "string";
      case TYPE_BYTES -> "[]byte";
      default -> "interface{}";
    };
  }

  /** The element type for a repeated field (without the [] prefix). */
  public String elementType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "*" + simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference());
    }
    return scalarType(field.getProtoType());
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "0";
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
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "nil";
      default -> "nil";
    };
  }

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "interface{}";
  }
}
