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
package dev.protocgen.textcodecs.jsonarray.codegen.rust;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

/** Maps proto types to Rust types, default values, and boxed types. */
public class RustTypeMapper implements TypeMapper {

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
      // Use std::collections::HashMap for map fields
      return "std::collections::HashMap<" + keyType + ", " + valueType + ">";
    }
    if (field.isRepeated()) {
      String elementType;
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        elementType = simpleTypeName(field.getTypeReference());
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        elementType = simpleTypeName(field.getTypeReference());
      } else {
        elementType = scalarType(field.getProtoType());
      }
      return "Vec<" + elementType + ">";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String typeName = simpleTypeName(field.getTypeReference());
      // Message fields are always Option<T> in proto3
      return "Option<" + typeName + ">";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      String typeName = simpleTypeName(field.getTypeReference());
      if (field.isProto3Optional()) {
        return "Option<" + typeName + ">";
      }
      return typeName;
    }
    // Scalar fields
    if (field.isProto3Optional()) {
      return "Option<" + scalarType(field.getProtoType()) + ">";
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // Rust does not have separate boxed/unboxed primitives; same as languageType
    return languageType(field);
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "std::collections::HashMap::new()";
    if (field.isRepeated()) return "Vec::new()";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "None";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      String typeName = simpleTypeName(field.getTypeReference());
      if (field.isProto3Optional()) {
        return "None";
      }
      return typeName + "::from(0)";
    }
    if (field.isProto3Optional()) {
      return "None";
    }
    return scalarDefault(field.getProtoType());
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "f64";
      case TYPE_FLOAT -> "f32";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "i64";
      case TYPE_UINT64, TYPE_FIXED64 -> "u64";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "i32";
      case TYPE_UINT32, TYPE_FIXED32 -> "u32";
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "String";
      case TYPE_BYTES -> "Vec<u8>";
      default -> "String";
    };
  }

  /**
   * Return the element type for a repeated field, or the inner type for an Option field. Used when
   * the wrapping (Vec, Option) is handled separately.
   */
  public String elementType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference());
    }
    return scalarType(field.getProtoType());
  }

  /** Return the Rust type for a map value. */
  public String mapValueRustType(ProtoField field) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
        || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return simpleTypeName(field.getMapValueTypeReference());
    }
    return scalarType(field.getMapValueType());
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "0.0_f64";
      case TYPE_FLOAT -> "0.0_f32";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "0_i64";
      case TYPE_UINT64, TYPE_FIXED64 -> "0_u64";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "0_i32";
      case TYPE_UINT32, TYPE_FIXED32 -> "0_u32";
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "String::new()";
      case TYPE_BYTES -> "Vec::new()";
      default -> "Default::default()";
    };
  }

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Value";
  }
}
