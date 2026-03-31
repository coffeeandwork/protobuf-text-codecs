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
package dev.protocgen.textcodecs.jsonarray.codegen.kotlin;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/**
 * Maps proto types to Kotlin types, nullable types, and default value expressions. Kotlin has no
 * primitive/boxed distinction -- Int, Long, Double etc. are always the same type but can be
 * nullable (Int?).
 */
public class KotlinTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = scalarType(field.getMapKeyType());
      String valueType =
          field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
                  || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM
              ? simpleTypeName(field.getMapValueTypeReference())
              : scalarType(field.getMapValueType());
      return "MutableMap<" + keyType + ", " + valueType + ">";
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
      return "MutableList<" + elementType + ">";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference()) + "?";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference()) + "?";
    }
    return scalarType(field.getProtoType());
  }

  /**
   * Returns the immutable version of collection types for use in the message class fields. Maps
   * become Map, MutableList becomes List. Nullable types remain unchanged.
   */
  public String immutableType(ProtoField field) {
    if (field.isMap()) {
      String keyType = scalarType(field.getMapKeyType());
      String valueType =
          field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
                  || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM
              ? simpleTypeName(field.getMapValueTypeReference())
              : scalarType(field.getMapValueType());
      return "Map<" + keyType + ", " + valueType + ">";
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
      return "List<" + elementType + ">";
    }
    return languageType(field);
  }

  @Override
  public String boxedType(ProtoField field) {
    // Kotlin has no primitive/boxed distinction; nullable types use ? suffix
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
    if (field.isMap()) return "mutableMapOf()";
    if (field.isRepeated()) return "mutableListOf()";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "null";
    }
    // Use schema-specified default if available (proto2)
    String schemaDefault = field.getDefaultValue();
    if (schemaDefault != null && !schemaDefault.isEmpty()) {
      if (field.getKind() == ProtoField.FieldKind.ENUM) {
        // Validate enum constant name to prevent code injection (VULN-003)
        if (!schemaDefault.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
          throw new IllegalArgumentException(
              "Enum default value '" + schemaDefault + "' is not a valid identifier");
        }
        return simpleTypeName(field.getTypeReference()) + "." + schemaDefault;
      }
      return formatSchemaDefault(field.getProtoType(), schemaDefault);
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference()) + ".forNumber(0)";
    }
    return scalarDefault(field.getProtoType());
  }

  /**
   * Format a proto2 schema-specified default value string as a Kotlin expression. The defaultValue
   * is the raw string from the proto file's [default = ...] annotation.
   */
  private String formatSchemaDefault(FieldDescriptorProto.Type protoType, String defaultValue) {
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
        if (!"true".equals(defaultValue) && !"false".equals(defaultValue)) {
          throw new IllegalArgumentException(
              "Bool default value '" + defaultValue + "' is not 'true' or 'false'");
        }
        yield defaultValue;
      }
      case TYPE_DOUBLE -> {
        if ("inf".equals(defaultValue)) yield "Double.POSITIVE_INFINITY";
        if ("-inf".equals(defaultValue)) yield "Double.NEGATIVE_INFINITY";
        if ("nan".equals(defaultValue)) yield "Double.NaN";
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Double default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "Float.POSITIVE_INFINITY";
        if ("-inf".equals(defaultValue)) yield "Float.NEGATIVE_INFINITY";
        if ("nan".equals(defaultValue)) yield "Float.NaN";
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Float default value '" + defaultValue + "' is not a valid number");
        }
        yield (defaultValue.contains(".") ? defaultValue : defaultValue + ".0") + "f";
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> {
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue + "L";
      }
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "byteArrayOf()";
        }
        yield "java.util.Base64.getDecoder().decode(\""
            + java.util.Base64.getEncoder()
                .encodeToString(defaultValue.getBytes(StandardCharsets.ISO_8859_1))
            + "\")";
      }
      default -> {
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Numeric default value '" + defaultValue + "' is not a valid integer");
        }
        yield defaultValue;
      }
    };
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "Double";
      case TYPE_FLOAT -> "Float";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "Long";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 -> "Int";
      case TYPE_BOOL -> "Boolean";
      case TYPE_STRING -> "String";
      case TYPE_BYTES -> "ByteArray";
      default -> "Any";
    };
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "0.0";
      case TYPE_FLOAT -> "0.0f";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "0L";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 -> "0";
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "byteArrayOf()";
      default -> "null";
    };
  }

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  public String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Any";
  }
}
