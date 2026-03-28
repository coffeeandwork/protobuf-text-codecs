package dev.protocgen.textcodecs.jsonarray.codegen;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;

/**
 * Interface for mapping proto types to target language types and JSON encoding expressions. Each
 * language provides its own implementation.
 */
public interface TypeMapper {

  /** The target language type for a proto field (e.g., "int", "String", "List<Address>"). */
  String languageType(ProtoField field);

  /**
   * The boxed/nullable version of the type (e.g., "Integer" instead of "int" in Java). For
   * languages without boxed types, returns the same as languageType().
   */
  String boxedType(ProtoField field);

  /**
   * The default value expression for a field in the target language. E.g., "0", "\"\"", "null",
   * "[]", "{}".
   */
  String defaultValue(ProtoField field);

  /** The target language type for a proto scalar type (e.g., TYPE_INT32 → "int"). */
  String scalarType(FieldDescriptorProto.Type protoType);
}
