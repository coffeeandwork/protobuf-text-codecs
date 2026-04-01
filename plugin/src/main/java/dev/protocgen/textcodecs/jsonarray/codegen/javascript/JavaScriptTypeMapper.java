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
package dev.protocgen.textcodecs.jsonarray.codegen.javascript;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/** Maps proto types to JavaScript types, default values, and JS doc type annotations. */
public class JavaScriptTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      return "Object";
    }
    if (field.isRepeated()) {
      return "Array";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "number";
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // JavaScript has no boxed/unboxed distinction
    return languageType(field);
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "{}";
    if (field.isRepeated()) return "[]";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "null";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "0";
    }
    return scalarDefault(field.getProtoType());
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE,
          TYPE_FLOAT,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32 ->
          "number";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "string";
      case TYPE_BOOL -> "boolean";
      case TYPE_STRING -> "string";
      case TYPE_BYTES -> "Uint8Array";
      default -> "any";
    };
  }

  /** Return the JSDoc type annotation for a field. */
  public String jsDocType(ProtoField field) {
    if (field.isMap()) {
      String keyType = scalarType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        valueType = simpleTypeName(field.getMapValueTypeReference());
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = "number";
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
      if (stringKey) {
        return "Object.<" + keyType + ", " + valueType + ">";
      }
      return "Array.<Array>";
    }
    if (field.isRepeated()) {
      String elementType;
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        elementType = simpleTypeName(field.getTypeReference());
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        elementType = "number";
      } else {
        elementType = scalarType(field.getProtoType());
      }
      return "Array.<" + elementType + ">";
    }
    return languageType(field);
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "0";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "\"0\"";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 -> "0";
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "new Uint8Array(0)";
      default -> "null";
    };
  }

  /**
   * Format a proto2 schema-specified default value string as a JavaScript expression (VULN-003).
   */
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
      case TYPE_DOUBLE, TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "Infinity";
        if ("-inf".equals(defaultValue)) yield "-Infinity";
        if ("nan".equals(defaultValue)) yield "NaN";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Float default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue;
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> {
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield "\"" + defaultValue + "\"";
      }
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "new Uint8Array(0)";
        }
        yield "_base64Decode(\""
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

  /** Extract the simple type name from a fully-qualified proto type reference. */
  public String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Object";
  }
}
