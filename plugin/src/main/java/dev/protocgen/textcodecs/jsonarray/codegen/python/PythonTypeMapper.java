package dev.protocgen.textcodecs.jsonarray.codegen.python;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

/** Maps proto types to Python types, default values, and type hints. */
public class PythonTypeMapper implements TypeMapper {

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      String keyType = scalarType(field.getMapKeyType());
      String valueType;
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
          || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        valueType = simpleTypeName(field.getMapValueTypeReference());
      } else {
        valueType = scalarType(field.getMapValueType());
      }
      return "Dict[" + keyType + ", " + valueType + "]";
    }
    if (field.isRepeated()) {
      String elementType;
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        elementType = simpleTypeName(field.getTypeReference());
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        elementType = "int";
      } else {
        elementType = scalarType(field.getProtoType());
      }
      return "List[" + elementType + "]";
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      // Enums are represented as int in Python
      return "int";
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    // Python doesn't have boxed vs unboxed distinction — same as languageType
    return languageType(field);
  }

  @Override
  public String defaultValue(ProtoField field) {
    if (field.isMap()) return "{}";
    if (field.isRepeated()) return "[]";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return "None";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "0";
    }
    return scalarDefault(field.getProtoType());
  }

  @Override
  public String scalarType(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "float";
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
          "int";
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "str";
      case TYPE_BYTES -> "bytes";
      default -> "object";
    };
  }

  private String scalarDefault(FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "0.0";
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
      case TYPE_BOOL -> "False";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "b\"\"";
      default -> "None";
    };
  }

  /** Extract the simple type name from a fully-qualified proto type reference. */
  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "object";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }
}
