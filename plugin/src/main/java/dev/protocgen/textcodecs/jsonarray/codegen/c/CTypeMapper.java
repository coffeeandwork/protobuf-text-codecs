package dev.protocgen.textcodecs.jsonarray.codegen.c;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

/** Maps proto types to C types, default values, and related expressions. */
public class CTypeMapper implements TypeMapper {

  private final CNameResolver nameResolver;

  public CTypeMapper(CNameResolver nameResolver) {
    this.nameResolver = nameResolver;
  }

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      // Maps are represented as an array of key-value pair structs + count.
      // The struct type is generated inline during code emission.
      // Here we return the entry pointer type.
      return mapEntryTypeName(field) + "*";
    }
    if (field.isRepeated()) {
      return elementType(field) + "*";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null) + "*";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null);
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // C has no boxed types; same as languageType
    return languageType(field);
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap() || field.isRepeated()) return "NULL";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "NULL";
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
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "char*";
      case TYPE_BYTES -> "uint8_t*";
      default -> "void*";
    };
  }

  /** Returns the element type for repeated fields (without the pointer). */
  public String elementType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null) + "*";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null);
    }
    return scalarType(field.getProtoType());
  }

  /**
   * Returns the C type name for a map entry struct. E.g., for field "tags_map" in message with
   * prefix "example_User": "example_User_tags_map_entry"
   */
  public String mapEntryTypeName(ProtoField field) {
    // The actual prefix is determined at emit time; we use a placeholder pattern
    // The code emitter will generate the struct with the proper prefix
    return "__MAP_ENTRY_" + field.getName();
  }

  /** Build the actual map entry type name with the message prefix. */
  public String qualifiedMapEntryTypeName(String funcPrefix, ProtoField field) {
    return funcPrefix + "_" + field.getName() + "_entry_t";
  }

  /** The C type for a map key. */
  public String mapKeyType(ProtoField field) {
    return scalarType(field.getMapKeyType());
  }

  /** The C type for a map value. */
  public String mapValueType(ProtoField field) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      return nameResolver.resolveTypeReference(field.getMapValueTypeReference(), null) + "*";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return nameResolver.resolveTypeReference(field.getMapValueTypeReference(), null);
    }
    return scalarType(field.getMapValueType());
  }

  /** Whether the type needs a separate length/count field (bytes, repeated, map). */
  public boolean needsLengthField(ProtoField field) {
    return field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES
        || field.isRepeated()
        || field.isMap();
  }

  /** Whether the scalar type is a pointer type that needs NULL checks and strdup. */
  public boolean isPointerScalar(FieldDescriptorProto.Type protoType) {
    return protoType == FieldDescriptorProto.Type.TYPE_STRING
        || protoType == FieldDescriptorProto.Type.TYPE_BYTES;
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE -> "0.0";
      case TYPE_FLOAT -> "0.0f";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "0";
      case TYPE_UINT64, TYPE_FIXED64 -> "0";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "0";
      case TYPE_UINT32, TYPE_FIXED32 -> "0";
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "NULL";
      case TYPE_BYTES -> "NULL";
      default -> "NULL";
    };
  }
}
