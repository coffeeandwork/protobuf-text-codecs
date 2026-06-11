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
      w.line("import (");
      w.indent();
      for (String imp : imports) {
        w.line("\"%s\"", imp);
      }
      w.dedent();
      w.line(")");
      w.blankLine();
    }

    // Struct declaration
    emitStruct(w, message, structName);

    // Nested message structs at any depth (Go doesn't have nested types, so they're
    // top-level with underscore-flattened names)
    emitNestedStructs(w, message, structName);

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

  private void emitNestedStructs(CodeWriter w, ProtoMessage message, String structName) {
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.blankLine();
      String nestedName = structName + "_" + nameResolver.messageClassName(nested.getName());
      emitStruct(w, nested, nestedName);
      serializerGen.generate(w, nested, nestedName);
      deserializerGen.generate(w, nested, nestedName);
      emitNestedStructs(w, nested, nestedName);
      for (ProtoEnum protoEnum : nested.getEnums()) {
        emitEnum(w, protoEnum, nestedName);
      }
    }
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
   * Resolve the Go type for a field. GoTypeMapper.simpleTypeName already flattens nested type
   * references (e.g. KitchenSink_InnerMessage), so no extra prefixing is needed here.
   */
  private String resolveFieldType(
      ProtoField field, String parentStructName, ProtoMessage parentMessage) {
    return typeMapper.languageType(field);
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

    // Always need encoding/json for Marshal and Unmarshal
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

    // Check if we need math for NaN/Infinity checks on float/double fields
    if (needsFloat(message)) {
      imports.add("math");
    }

    return imports;
  }

  private boolean needsInt64(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type t = field.getProtoType();
      if (isInt64Kind(t)) {
        return true;
      }
      if (field.isMap()
          && (isInt64Kind(field.getMapKeyType()) || isInt64Kind(field.getMapValueType()))) {
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

  private static boolean isInt64Kind(
      com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type t) {
    return t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64
        || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64
        || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64
        || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64
        || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64;
  }

  private boolean needsFloat(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type t = field.getProtoType();
      if (t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT
          || t == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE) {
        return true;
      }
      if (field.isMap()) {
        com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type vt = field.getMapValueType();
        if (vt == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT
            || vt == com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE) {
          return true;
        }
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (needsFloat(nested)) {
        return true;
      }
    }
    return false;
  }
}
