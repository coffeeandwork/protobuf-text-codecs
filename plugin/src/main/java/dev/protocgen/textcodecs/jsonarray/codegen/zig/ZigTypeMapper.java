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
package dev.protocgen.textcodecs.jsonarray.codegen.zig;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/** Maps proto types to Zig types, optional wrappers, and default value expressions. */
public class ZigTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = mapKeyZigType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
          || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = ZigNameResolver.simpleTypeName(field.getMapValueTypeReference());
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
        return "std.StringHashMap(" + valueType + ")";
      }
      return "std.AutoHashMap(" + keyType + ", " + valueType + ")";
    }
    if (field.isRepeated()) {
      String elementType = elementZigType(field);
      return "std.ArrayList(" + elementType + ")";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String typeName = ZigNameResolver.simpleTypeName(field.getTypeReference());
      return "?" + typeName;
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return ZigNameResolver.simpleTypeName(field.getTypeReference());
    }
    if (field.isProto3Optional()) {
      return "?" + scalarType(field.getProtoType());
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // Zig has no boxed/unboxed distinction; optional is expressed with ?T.
    return languageType(field);
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) {
      String keyType = mapKeyZigType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
          || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = ZigNameResolver.simpleTypeName(field.getMapValueTypeReference());
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
        return "std.StringHashMap(" + valueType + ").init(allocator)";
      }
      return "std.AutoHashMap(" + keyType + ", " + valueType + ").init(allocator)";
    }
    if (field.isRepeated()) {
      String elementType = elementZigType(field);
      return "std.ArrayList(" + elementType + ").init(allocator)";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "null";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "@enumFromInt(0)";
    }
    if (field.isProto3Optional()) {
      return "null";
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
      case TYPE_STRING -> "[]const u8";
      case TYPE_BYTES -> "[]u8";
      default -> "[]const u8";
    };
  }

  /** Return the Zig type for a map key. Proto map keys can only be integral types or string. */
  String mapKeyZigType(FieldDescriptorProto.Type keyType) {
    return scalarType(keyType);
  }

  /** Return the Zig element type for a repeated field. */
  String elementZigType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return ZigNameResolver.simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return ZigNameResolver.simpleTypeName(field.getTypeReference());
    }
    return scalarType(field.getProtoType());
  }

  /** Format a proto2 schema-specified default value string as a Zig expression (VULN-003). */
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
        if ("inf".equals(defaultValue)) yield "std.math.inf(f64)";
        if ("-inf".equals(defaultValue)) yield "-std.math.inf(f64)";
        if ("nan".equals(defaultValue)) yield "std.math.nan(f64)";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Double default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "std.math.inf(f32)";
        if ("-inf".equals(defaultValue)) yield "-std.math.inf(f32)";
        if ("nan".equals(defaultValue)) yield "std.math.nan(f32)";
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
          yield "\"\"";
        }
        // Zig bytes default: embed base64 string and decode at runtime
        yield "blk: { const src = \""
            + java.util.Base64.getEncoder()
                .encodeToString(defaultValue.getBytes(StandardCharsets.ISO_8859_1))
            + "\"; const size = std.base64.standard.Decoder.calcSizeForSlice(src.len) catch 0;"
            + " const dest = try allocator.alloc(u8, size);"
            + " std.base64.standard.Decoder.decode(dest, src) catch {}; break :blk dest; }";
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

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "0.0";
      case TYPE_FLOAT -> "0.0";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "0";
      case TYPE_UINT64, TYPE_FIXED64 -> "0";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "0";
      case TYPE_UINT32, TYPE_FIXED32 -> "0";
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "\"\"";
      default -> "\"\"";
    };
  }
}
