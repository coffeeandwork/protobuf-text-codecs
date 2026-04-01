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
package dev.protocgen.textcodecs.jsonarray.codegen.swift;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/**
 * Maps proto types to Swift types, optional types, and default value expressions. Swift uses
 * optionals (?) for nullable types and value types (struct) for messages.
 */
public class SwiftTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = scalarType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        valueType = simpleTypeName(field.getMapValueTypeReference());
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = simpleTypeName(field.getMapValueTypeReference());
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      return "[" + keyType + ": " + valueType + "]";
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
      return "[" + elementType + "]";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference()) + "?";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      // Proto3 optional enums need to be nullable
      if (field.isProto3Optional()) {
        return simpleTypeName(field.getTypeReference()) + "?";
      }
      return simpleTypeName(field.getTypeReference());
    }
    // Proto3 optional scalars use optionals for presence tracking
    if (field.isProto3Optional()) {
      return scalarType(field.getProtoType()) + "?";
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // Swift uses optionals for nullable values.
    if (field.isMap()
        || field.isRepeated()
        || field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE
        || field.getKind() == ProtoField.FieldKind.ENUM) {
      return languageType(field);
    }
    return scalarType(field.getProtoType()) + "?";
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "[:]";
    if (field.isRepeated()) return "[]";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "nil";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      if (field.isProto3Optional()) {
        return "nil";
      }
      return simpleTypeName(field.getTypeReference()) + "(rawValue: 0)!";
    }
    if (field.isProto3Optional()) {
      return "nil";
    }
    return scalarDefault(field.getProtoType());
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "Double";
      case TYPE_FLOAT -> "Float";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "Int64";
      case TYPE_UINT64, TYPE_FIXED64 -> "UInt64";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "Int32";
      case TYPE_UINT32, TYPE_FIXED32 -> "UInt32";
      case TYPE_BOOL -> "Bool";
      case TYPE_STRING -> "String";
      case TYPE_BYTES -> "Data";
      default -> "Any";
    };
  }

  /** The element type for a repeated field (without the [] wrapper). */
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

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "0.0";
      case TYPE_FLOAT -> "0.0";
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
      case TYPE_BYTES -> "Data()";
      default -> "nil";
    };
  }

  /** Format a proto2 schema-specified default value string as a Swift expression (VULN-003). */
  public String formatSchemaDefault(FieldDescriptorProto.Type protoType, String defaultValue) {
    return switch (protoType) {
      case TYPE_STRING ->
          "\""
              + defaultValue
                  .replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\0", "\\0")
              + "\"";
      case TYPE_BOOL -> {
        // Validate bool default to prevent code injection (VULN-003)
        if (!"true".equals(defaultValue) && !"false".equals(defaultValue)) {
          throw new IllegalArgumentException(
              "Bool default value '" + defaultValue + "' is not 'true' or 'false'");
        }
        yield defaultValue;
      }
      case TYPE_DOUBLE -> {
        if ("inf".equals(defaultValue)) yield "Double.infinity";
        if ("-inf".equals(defaultValue)) yield "-Double.infinity";
        if ("nan".equals(defaultValue)) yield "Double.nan";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Double default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "Float.infinity";
        if ("-inf".equals(defaultValue)) yield "-Float.infinity";
        if ("nan".equals(defaultValue)) yield "Float.nan";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Float default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> {
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue;
      }
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "Data()";
        }
        yield "Data(base64Encoded: \""
            + java.util.Base64.getEncoder()
                .encodeToString(defaultValue.getBytes(StandardCharsets.ISO_8859_1))
            + "\")!";
      }
      default -> {
        // Validate numeric format for int32 types to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Numeric default value '" + defaultValue + "' is not a valid integer");
        }
        yield defaultValue;
      }
    };
  }

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Any";
  }
}
