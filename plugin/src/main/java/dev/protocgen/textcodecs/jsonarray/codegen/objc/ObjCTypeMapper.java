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
package dev.protocgen.textcodecs.jsonarray.codegen.objc;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import java.nio.charset.StandardCharsets;

/**
 * Maps proto types to Objective-C types, default values, and related expressions. Uses Foundation
 * types (NSString, NSNumber, NSData, NSArray, NSDictionary) for ARC compatibility.
 */
public class ObjCTypeMapper implements TypeMapper {

  private final ObjCNameResolver nameResolver;

  public ObjCTypeMapper(ObjCNameResolver nameResolver) {
    this.nameResolver = nameResolver;
  }

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = mapKeyObjCType(field.getMapKeyType());
      String valType = mapValueObjCType(field);
      return "NSMutableDictionary<" + keyType + ", " + valType + "> *";
    }
    if (field.isRepeated()) {
      String elemType = elementType(field);
      return "NSMutableArray<" + elemType + "> *";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null) + " *";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      // Enums are NSInteger typedef
      return "NSInteger";
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    if (field.isMap() || field.isRepeated()) {
      return languageType(field);
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return languageType(field);
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "NSNumber *";
    }
    return boxedScalarType(field.getProtoType());
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "nil";
    if (field.isRepeated()) return "nil";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "nil";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "0";
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
      case TYPE_BOOL -> "BOOL";
      case TYPE_STRING -> "NSString *";
      case TYPE_BYTES -> "NSData *";
      default -> "id";
    };
  }

  /** Returns the Objective-C property attribute for a given proto type. */
  public String propertyAttributes(ProtoField field) {
    if (field.isMap() || field.isRepeated()) {
      return "nonatomic, strong";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "nonatomic, strong";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "nonatomic, assign";
    }
    return switch (field.getProtoType()) {
      case TYPE_STRING -> "nonatomic, copy";
      case TYPE_BYTES -> "nonatomic, strong";
      default -> "nonatomic, assign";
    };
  }

  /** Whether the field is an object type (needs nullable annotation for optional). */
  public boolean isObjectType(ProtoField field) {
    if (field.isMap() || field.isRepeated()) return true;
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return true;
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) return false;
    return field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES;
  }

  /** Returns the element type for repeated fields (for generics). */
  public String elementType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null) + " *";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "NSNumber *";
    }
    return boxedScalarType(field.getProtoType());
  }

  /** The Objective-C type for a map key (must be an object type for NSDictionary). */
  public String mapKeyObjCType(FieldDescriptorProto.Type keyType) {
    return switch (keyType) {
      case TYPE_STRING -> "NSString *";
      case TYPE_BOOL -> "NSNumber *";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "NSNumber *";
      case TYPE_UINT32, TYPE_FIXED32 -> "NSNumber *";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "NSNumber *";
      case TYPE_UINT64, TYPE_FIXED64 -> "NSNumber *";
      default -> "NSString *";
    };
  }

  /** The Objective-C type for a map value. */
  public String mapValueObjCType(ProtoField field) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      return nameResolver.resolveTypeReference(field.getMapValueTypeReference(), null) + " *";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return "NSNumber *";
    }
    return boxedScalarType(field.getMapValueType());
  }

  /** Returns the boxed (Foundation) type for a scalar proto type. */
  public String boxedScalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_STRING -> "NSString *";
      case TYPE_BYTES -> "NSData *";
      default -> "NSNumber *";
    };
  }

  /** Whether a proto type is an integer type that needs int64 as JSON strings. */
  public boolean isInt64Type(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64
        || type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64;
  }

  /** Whether a proto type is a signed 64-bit type. */
  public boolean isSignedInt64(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64;
  }

  /** Whether a proto type is an unsigned 64-bit type. */
  public boolean isUnsignedInt64(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64;
  }

  /**
   * Format a proto2 schema-specified default value string as an Objective-C expression (VULN-003).
   */
  public String formatSchemaDefault(FieldDescriptorProto.Type protoType, String defaultValue) {
    return switch (protoType) {
      case TYPE_STRING ->
          "@\""
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
        yield "true".equals(defaultValue) ? "YES" : "NO";
      }
      case TYPE_DOUBLE -> {
        if ("inf".equals(defaultValue)) yield "INFINITY";
        if ("-inf".equals(defaultValue)) yield "-INFINITY";
        if ("nan".equals(defaultValue)) yield "NAN";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Double default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "INFINITY";
        if ("-inf".equals(defaultValue)) yield "-INFINITY";
        if ("nan".equals(defaultValue)) yield "NAN";
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
          throw new IllegalArgumentException(
              "Float default value '" + defaultValue + "' is not a valid number");
        }
        yield (defaultValue.contains(".") ? defaultValue : defaultValue + ".0") + "f";
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> {
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue + "LL";
      }
      case TYPE_UINT64, TYPE_FIXED64 -> {
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue + "ULL";
      }
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> {
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue;
      }
      case TYPE_UINT32, TYPE_FIXED32 -> {
        // Validate numeric format to prevent code injection (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Integer default value '" + defaultValue + "' is not a valid number");
        }
        yield defaultValue + "U";
      }
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "[NSData data]";
        }
        yield "[[NSData alloc] initWithBase64EncodedString:@\""
            + java.util.Base64.getEncoder()
                .encodeToString(defaultValue.getBytes(StandardCharsets.ISO_8859_1))
            + "\" options:0]";
      }
      default -> {
        // Validate numeric format for remaining types to prevent code injection (VULN-003)
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
      case TYPE_FLOAT -> "0.0f";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "0";
      case TYPE_UINT64, TYPE_FIXED64 -> "0";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "0";
      case TYPE_UINT32, TYPE_FIXED32 -> "0";
      case TYPE_BOOL -> "NO";
      case TYPE_STRING -> "nil";
      case TYPE_BYTES -> "nil";
      default -> "nil";
    };
  }
}
