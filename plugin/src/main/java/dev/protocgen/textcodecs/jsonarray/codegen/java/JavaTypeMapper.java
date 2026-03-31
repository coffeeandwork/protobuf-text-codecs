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
package dev.protocgen.textcodecs.jsonarray.codegen.java;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/** Maps proto types to Java types, boxed types, and default value expressions. */
public class JavaTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = boxedScalarType(field.getMapKeyType());
      String valueType =
          field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
                  || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM
              ? simpleTypeName(field.getMapValueTypeReference())
              : boxedScalarType(field.getMapValueType());
      return "java.util.Map<" + keyType + ", " + valueType + ">";
    }
    if (field.isRepeated()) {
      String elementType;
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        elementType = simpleTypeName(field.getTypeReference());
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        elementType = simpleTypeName(field.getTypeReference());
      } else {
        elementType = boxedScalarType(field.getProtoType());
      }
      return "java.util.List<" + elementType + ">";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference());
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    if (field.isMap()
        || field.isRepeated()
        || field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE
        || field.getKind() == ProtoField.FieldKind.ENUM) {
      return languageType(field);
    }
    return boxedScalarType(field.getProtoType());
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "new java.util.LinkedHashMap<>()";
    if (field.isRepeated()) return "new java.util.ArrayList<>()";
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
   * Format a proto2 schema-specified default value string as a Java expression. The defaultValue is
   * the raw string from the proto file's [default = ...] annotation.
   */
  /** Format a proto2 schema-specified default value string as a Java expression (VULN-003). */
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
        if ("inf".equals(defaultValue)) yield "Double.POSITIVE_INFINITY";
        if ("-inf".equals(defaultValue)) yield "Double.NEGATIVE_INFINITY";
        if ("nan".equals(defaultValue)) yield "Double.NaN";
        // Validate numeric format to prevent code injection (VULN-003)
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
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Float default value '" + defaultValue + "' is not a valid number");
        }
        yield (defaultValue.contains(".") ? defaultValue : defaultValue + ".0") + "f";
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> {
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue + "L";
      }
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "new byte[0]";
        }
        yield "java.util.Base64.getDecoder().decode(\""
            + java.util.Base64.getEncoder()
                .encodeToString(defaultValue.getBytes(StandardCharsets.ISO_8859_1))
            + "\")";
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

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "double";
      case TYPE_FLOAT -> "float";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "long";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 -> "int";
      case TYPE_BOOL -> "boolean";
      case TYPE_STRING -> "String";
      case TYPE_BYTES -> "byte[]";
      default -> "Object";
    };
  }

  public String boxedScalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "Double";
      case TYPE_FLOAT -> "Float";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "Long";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 -> "Integer";
      case TYPE_BOOL -> "Boolean";
      case TYPE_STRING -> "String";
      case TYPE_BYTES -> "byte[]";
      default -> "Object";
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
      case TYPE_BYTES -> "new byte[0]";
      default -> "null";
    };
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Object";
  }
}
