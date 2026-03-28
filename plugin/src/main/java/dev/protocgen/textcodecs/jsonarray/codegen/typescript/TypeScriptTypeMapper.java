package dev.protocgen.textcodecs.jsonarray.codegen.typescript;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.codegen.TypeMapper;
import dev.protocgen.textcodecs.jsonarray.codegen.javascript.JavaScriptTypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

/**
 * Maps proto types to TypeScript types with full type annotations. Delegates to
 * JavaScriptTypeMapper for base logic, adds TS-specific type expressions.
 */
public class TypeScriptTypeMapper implements TypeMapper {

  private final JavaScriptTypeMapper jsTypeMapper = new JavaScriptTypeMapper();

  @Override
  public String languageType(ProtoField field) {
    if (field.isMap()) {
      return mapTypeAnnotation(field);
    }
    if (field.isRepeated()) {
      return arrayTypeAnnotation(field);
    }
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return jsTypeMapper.simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return "number";
    }
    return scalarType(field.getProtoType());
  }

  @Override
  public String boxedType(ProtoField field) {
    return languageType(field);
  }

  @Override
  public String defaultValue(ProtoField field) {
    return jsTypeMapper.defaultValue(field);
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

  /** Return the nullable type annotation (type | null) for optional/message fields. */
  public String nullableType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE
        || field.isProto3Optional()) {
      return languageType(field) + " | null";
    }
    return languageType(field);
  }

  /** The TS type annotation for a map field. */
  private String mapTypeAnnotation(ProtoField field) {
    String keyType = scalarType(field.getMapKeyType());
    String valueType;
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      valueType = jsTypeMapper.simpleTypeName(field.getMapValueTypeReference());
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      valueType = "number";
    } else {
      valueType = scalarType(field.getMapValueType());
    }
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    if (stringKey) {
      return "Record<" + keyType + ", " + valueType + ">";
    }
    // Non-string keys are stored as Array of [key, value] tuples
    return "[" + keyType + ", " + valueType + "][]";
  }

  /** The TS type annotation for a repeated field. */
  private String arrayTypeAnnotation(ProtoField field) {
    String elementType;
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      elementType = jsTypeMapper.simpleTypeName(field.getTypeReference());
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      elementType = "number";
    } else {
      elementType = scalarType(field.getProtoType());
    }
    return elementType + "[]";
  }

  /** Extract the simple type name from a fully-qualified proto type reference. */
  public String simpleTypeName(String protoFullName) {
    return jsTypeMapper.simpleTypeName(protoFullName);
  }
}
