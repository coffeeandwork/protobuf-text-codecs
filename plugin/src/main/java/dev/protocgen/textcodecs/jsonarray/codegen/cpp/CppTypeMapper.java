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
package dev.protocgen.textcodecs.jsonarray.codegen.cpp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/**
 * Maps proto types to C++ types, default values, and boxed (nullable) types. Uses std::optional for
 * optional fields, std::vector for repeated, std::map for string-keyed maps, std::unordered_map
 * otherwise.
 */
public class CppTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = mapKeyScalarType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
          || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = simpleTypeName(field.getMapValueTypeReference());
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      // Use std::map for string keys (ordered), std::unordered_map otherwise
      if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
        return "std::map<" + keyType + ", " + valueType + ">";
      }
      return "std::unordered_map<" + keyType + ", " + valueType + ">";
    }
    if (field.isRepeated()) {
      String elementType = elementType(field);
      return "std::vector<" + elementType + ">";
    }
    if (field.isProto3Optional()) {
      String innerType = singularType(field);
      return "std::optional<" + innerType + ">";
    }
    // Singular message fields use std::optional for null/presence semantics
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "std::optional<" + singularType(field) + ">";
    }
    return singularType(field);
  }

  @Override
  public String boxedType(ProtoField field) {
    // C++ doesn't have boxed types; for nullable semantics use std::optional
    if (field.isMap() || field.isRepeated()) {
      return languageType(field);
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "std::optional<" + simpleTypeName(field.getTypeReference()) + ">";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "std::optional<" + simpleTypeName(field.getTypeReference()) + ">";
    }
    return "std::optional<" + scalarType(field.getProtoType()) + ">";
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "{}";
    if (field.isRepeated()) return "{}";
    if (field.isProto3Optional()) return "std::nullopt";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "std::nullopt";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "static_cast<" + simpleTypeName(field.getTypeReference()) + ">(0)";
    }
    return scalarDefault(field.getProtoType());
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "double";
      case TYPE_FLOAT -> "float";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "int64_t";
      case TYPE_UINT64, TYPE_FIXED64 -> "uint64_t";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "int32_t";
      case TYPE_UINT32, TYPE_FIXED32 -> "uint32_t";
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "std::string";
      case TYPE_BYTES -> "std::vector<uint8_t>";
      default -> "void*";
    };
  }

  /** Return the C++ scalar type suitable for map keys. */
  public String mapKeyScalarType(FieldDescriptorProto.Type protoType) {
    return scalarType(protoType);
  }

  /** The element type for a repeated field (unwrapped from std::vector). */
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

  /** The singular (non-repeated, non-optional) type for a field. */
  private String singularType(ProtoField field) {
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
      case TYPE_FLOAT -> "0.0f";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "0";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 -> "0";
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "{}";
      default -> "{}";
    };
  }

  /** Format a proto2 schema-specified default value string as a C++ expression (VULN-003). */
  public String formatSchemaDefault(FieldDescriptorProto.Type protoType, String defaultValue) {
    return switch (protoType) {
      case TYPE_STRING ->
          "std::string(\""
              + defaultValue
                  .replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\0", "\\0")
              + "\")";
      case TYPE_BOOL -> {
        // Validate bool default to prevent code injection (VULN-003)
        if (!"true".equals(defaultValue) && !"false".equals(defaultValue)) {
          throw new IllegalArgumentException(
              "Bool default value '" + defaultValue + "' is not 'true' or 'false'");
        }
        yield defaultValue;
      }
      case TYPE_DOUBLE -> {
        if ("inf".equals(defaultValue)) yield "std::numeric_limits<double>::infinity()";
        if ("-inf".equals(defaultValue)) yield "-std::numeric_limits<double>::infinity()";
        if ("nan".equals(defaultValue)) yield "std::numeric_limits<double>::quiet_NaN()";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Double default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "std::numeric_limits<float>::infinity()";
        if ("-inf".equals(defaultValue)) yield "-std::numeric_limits<float>::infinity()";
        if ("nan".equals(defaultValue)) yield "std::numeric_limits<float>::quiet_NaN()";
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
        yield defaultValue;
      }
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "std::vector<uint8_t>()";
        }
        yield "jsonarray::base64_decode(\""
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

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  public String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "void*";
  }
}
