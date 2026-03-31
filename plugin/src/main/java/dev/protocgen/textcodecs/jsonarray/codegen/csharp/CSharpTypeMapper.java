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
package dev.protocgen.textcodecs.jsonarray.codegen.csharp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/**
 * Maps proto types to C# types, nullable types, and default value expressions. C# has value types
 * (int, long, double, etc.) and reference types (string, byte[]). Nullable value types use the ?
 * suffix (int?, long?).
 */
public class CSharpTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = boxedScalarType(field.getMapKeyType());
      String valueType =
          field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
                  || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM
              ? simpleTypeName(field.getMapValueTypeReference())
              : boxedScalarType(field.getMapValueType());
      return "Dictionary<" + keyType + ", " + valueType + ">";
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
      return "List<" + elementType + ">";
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
    if (field.isMap()) return "new Dictionary<" + mapGenericArgs(field) + ">()";
    if (field.isRepeated()) return "new List<" + repeatedElementType(field) + ">()";
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
      return "(" + simpleTypeName(field.getTypeReference()) + ")0";
    }
    return scalarDefault(field.getProtoType());
  }

  /**
   * Format a proto2 schema-specified default value string as a C# expression. The defaultValue is
   * the raw string from the proto file's [default = ...] annotation.
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
        // Validate bool default to prevent code injection (VULN-003)
        if (!"true".equals(defaultValue) && !"false".equals(defaultValue)) {
          throw new IllegalArgumentException(
              "Bool default value '" + defaultValue + "' is not 'true' or 'false'");
        }
        yield defaultValue;
      }
      case TYPE_DOUBLE -> {
        if ("inf".equals(defaultValue)) yield "double.PositiveInfinity";
        if ("-inf".equals(defaultValue)) yield "double.NegativeInfinity";
        if ("nan".equals(defaultValue)) yield "double.NaN";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Double default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "float.PositiveInfinity";
        if ("-inf".equals(defaultValue)) yield "float.NegativeInfinity";
        if ("nan".equals(defaultValue)) yield "float.NaN";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Float default value '" + defaultValue + "' is not a valid number");
        }
        yield (defaultValue.contains(".") ? defaultValue : defaultValue + ".0") + "f";
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> {
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue + "L";
      }
      case TYPE_UINT64, TYPE_FIXED64 -> {
        if (!defaultValue.matches("[0-9]+")) {
          throw new IllegalArgumentException(
              "Unsigned integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue + "UL";
      }
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "Array.Empty<byte>()";
        }
        yield "Convert.FromBase64String(\""
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
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "long";
      case TYPE_UINT64, TYPE_FIXED64 -> "ulong";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "int";
      case TYPE_UINT32, TYPE_FIXED32 -> "uint";
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "string";
      case TYPE_BYTES -> "byte[]";
      default -> "object";
    };
  }

  /** Returns the boxed/nullable version of a scalar type for use in generics and collections. */
  public String boxedScalarType(FieldDescriptorProto.Type protoType) {
    // C# generics work with value types directly (no boxing needed for generic type args).
    // However, we use the same names as scalarType since C# doesn't distinguish like Java.
    return scalarType(protoType);
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "0.0";
      case TYPE_FLOAT -> "0.0f";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "0L";
      case TYPE_UINT64, TYPE_FIXED64 -> "0UL";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "0";
      case TYPE_UINT32, TYPE_FIXED32 -> "0U";
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "Array.Empty<byte>()";
      default -> "null";
    };
  }

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "object";
  }

  /** Get the generic type arguments string for a map field's Dictionary. */
  private String mapGenericArgs(ProtoField field) {
    String keyType = boxedScalarType(field.getMapKeyType());
    String valueType =
        field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
                || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM
            ? simpleTypeName(field.getMapValueTypeReference())
            : boxedScalarType(field.getMapValueType());
    return keyType + ", " + valueType;
  }

  /** Get the element type for a repeated field's List. */
  private String repeatedElementType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference());
    }
    return boxedScalarType(field.getProtoType());
  }
}
