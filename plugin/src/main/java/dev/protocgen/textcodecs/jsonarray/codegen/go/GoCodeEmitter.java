package dev.protocgen.textcodecs.jsonarray.codegen.go;

import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates complete Go source files for proto messages and enums. Go uses tabs for indentation,
 * structs with exported fields, and standalone functions.
 */
public class GoCodeEmitter {

  private final GoTypeMapper typeMapper;
  private final GoNameResolver nameResolver;
  private final GoSerializerGenerator serializerGen;
  private final GoDeserializerGenerator deserializerGen;

  public GoCodeEmitter(GoTypeMapper typeMapper, GoNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.serializerGen = new GoSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new GoDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete Go source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter("\t"); // Go uses tabs
    String pkg = nameResolver.resolvePackage(file);
    String structName = nameResolver.messageClassName(message.getName());

    // Package declaration
    w.line("package %s", pkg);
    w.blankLine();

    // Imports - collect needed imports
    Set<String> imports = collectImports(message);
    if (!imports.isEmpty()) {
      w.block(
          "import",
          () -> {
            for (String imp : imports) {
              w.line("\"%s\"", imp);
            }
          });
      w.blankLine();
    }

    // Struct declaration
    emitStruct(w, message, structName);

    // Nested message structs (Go doesn't have nested types, so they're top-level)
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.blankLine();
      String nestedName = structName + "_" + nameResolver.messageClassName(nested.getName());
      emitStruct(w, nested, nestedName);
      serializerGen.generate(w, nested, nestedName);
      deserializerGen.generate(w, nested, nestedName);
    }

    // Nested enums
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum, structName);
    }

    // Oneof case tracking fields are in the struct; emit constants
    emitOneofConstants(w, message, structName);

    // Serialize method
    serializerGen.generate(w, message, structName);

    // Deserialize function
    deserializerGen.generate(w, message, structName);

    return w.toString();
  }

  /** Generate a complete Go source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter("\t");
    String pkg = nameResolver.resolvePackage(file);

    w.line("package %s", pkg);
    w.blankLine();

    emitEnum(w, protoEnum, "");
    return w.toString();
  }

  private void emitStruct(CodeWriter w, ProtoMessage message, String structName) {
    w.block(
        "type " + structName + " struct",
        () -> {
          // Regular fields
          for (ProtoField field : message.getFields()) {
            String goType = resolveFieldType(field, structName, message);
            String goName = nameResolver.fieldName(field.getName());
            w.line("%s %s", goName, goType);
          }

          // Oneof case tracking fields
          for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
            String caseName = GoNameResolver.snakeToPascal(group.name()) + "Case";
            w.line("%s int // 0 = not set, field_number = set", caseName);
          }
        });
  }

  /**
   * Resolve the Go type for a field, handling nested message references. When a field references a
   * nested message, the Go type uses the flattened name (e.g., User_Address instead of Address).
   */
  private String resolveFieldType(
      ProtoField field, String parentStructName, ProtoMessage parentMessage) {
    if (isNestedMessageRef(field, parentMessage)) {
      String nestedSimpleName = typeMapper.simpleTypeName(field.getTypeReference());
      String flattenedName = parentStructName + "_" + nestedSimpleName;
      if (field.isRepeated()) {
        return "[]*" + flattenedName;
      }
      return "*" + flattenedName;
    }
    return typeMapper.languageType(field);
  }

  /** Check whether a field references one of the parent message's nested message types. */
  private boolean isNestedMessageRef(ProtoField field, ProtoMessage parentMessage) {
    if (field.getKind() != ProtoField.FieldKind.MESSAGE
        && field.getKind() != ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return false;
    }
    if (field.getTypeReference() == null) return false;
    for (ProtoMessage nested : parentMessage.getNestedMessages()) {
      if (field.getTypeReference().endsWith("." + nested.getName())) {
        return true;
      }
    }
    return false;
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum, String parentPrefix) {
    String typeName;
    if (parentPrefix != null && !parentPrefix.isEmpty()) {
      typeName = parentPrefix + "_" + protoEnum.getName();
    } else {
      typeName = protoEnum.getName();
    }

    w.blankLine();
    w.line("type %s int32", typeName);
    w.blankLine();

    // Constants
    w.line("const (");
    w.indent();
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%s_%s %s = %d", typeName, val.name(), typeName, val.number());
    }
    w.dedent();
    w.line(")");

    // ForNumber helper function
    w.blankLine();
    w.block(
        "func " + typeName + "ForNumber(n int32) " + typeName,
        () -> {
          w.line("return %s(n)", typeName);
        });
  }

  private void emitOneofConstants(CodeWriter w, ProtoMessage message, String structName) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      w.blankLine();
      w.line(
          "// Oneof case constants for %s.%s",
          structName, GoNameResolver.snakeToPascal(group.name()));
      w.line("const (");
      w.indent();
      for (ProtoField member : group.members()) {
        String constName =
            structName
                + "_"
                + GoNameResolver.snakeToPascal(group.name())
                + "_"
                + GoNameResolver.snakeToPascal(member.getName());
        w.line("%s = %d", constName, member.getFieldNumber());
      }
      w.dedent();
      w.line(")");
    }
  }

  /** Collect the set of Go imports needed by a message and its nested types. */
  private Set<String> collectImports(ProtoMessage message) {
    Set<String> imports = new LinkedHashSet<>();

    // Always need encoding/json for ToJsonString and FromJsonString
    imports.add("encoding/json");
    imports.add("fmt");

    // Check if we need encoding/base64
    if (needsBase64(message)) {
      imports.add("encoding/base64");
    }

    // Check if we need strconv and fmt for int64/uint64 string serialization
    if (needsInt64(message)) {
      imports.add("strconv");
    }

    return imports;
  }

  private boolean needsInt64(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type t = field.getProtoType();
      if (t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64
          || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64
          || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64
          || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64
          || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64) {
        return true;
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (needsInt64(nested)) {
        return true;
      }
    }
    return false;
  }

  private boolean needsBase64(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.getProtoType()
          == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES) {
        return true;
      }
      if (field.isMap()
          && field.getMapValueType()
              == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES) {
        return true;
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (needsBase64(nested)) {
        return true;
      }
    }
    return false;
  }
}
