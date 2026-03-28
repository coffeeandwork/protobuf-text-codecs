package dev.protocgen.textcodecs.jsonarray.codegen.zig;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

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
