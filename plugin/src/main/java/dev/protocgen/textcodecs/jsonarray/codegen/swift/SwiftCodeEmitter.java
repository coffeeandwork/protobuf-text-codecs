/*
 * Copyright 2024 protobuf-text-codecs contributors
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
package dev.protocgen.textcodecs.jsonarray.codegen.swift;

import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates complete Swift source files for proto messages and enums. Swift uses 4-space indentation,
 * structs with properties, and static factory methods.
 */
public class SwiftCodeEmitter {

  private final SwiftTypeMapper typeMapper;
  private final SwiftNameResolver nameResolver;
  private final SwiftSerializerGenerator serializerGen;
  private final SwiftDeserializerGenerator deserializerGen;

  public SwiftCodeEmitter(SwiftTypeMapper typeMapper, SwiftNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.serializerGen = new SwiftSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new SwiftDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete Swift source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter(); // 4-space indentation (default)
    String structName = nameResolver.messageClassName(message.getName());

    // Import
    w.line("import Foundation");
    w.blankLine();

    // Struct declaration
    emitStruct(w, message, structName);

    return w.toString();
  }

  /** Generate a complete Swift source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();

    w.line("import Foundation");
    w.blankLine();

    emitEnum(w, protoEnum, "");
    return w.toString();
  }

  private void emitStruct(CodeWriter w, ProtoMessage message, String structName) {
    w.block(
        "public struct " + structName,
        () -> {
          // Properties
          for (ProtoField field : message.getFields()) {
            String swiftType = resolveFieldType(field, structName, message);
            String swiftName = nameResolver.fieldName(field.getName());
            String defaultVal = resolveDefaultValue(field, structName, message);
            w.line("public var %s: %s = %s", swiftName, swiftType, defaultVal);
          }

          // Oneof case tracking fields
          for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
            String caseName = SwiftNameResolver.snakeToCamel(group.name()) + "Case";
            w.line("public var %s: Int = 0 // 0 = not set, field_number = set", caseName);
          }

          // Nested message structs
          for (ProtoMessage nested : message.getNestedMessages()) {
            w.blankLine();
            String nestedName = structName + "_" + nameResolver.messageClassName(nested.getName());
            emitNestedStruct(w, nested, nestedName);
          }

          // Nested enums
          for (ProtoEnum protoEnum : message.getEnums()) {
            emitEnum(w, protoEnum, structName);
          }

          // Oneof case constants
          emitOneofConstants(w, message, structName);

          // Default initializer
          w.blankLine();
          w.block("public init()", () -> {});

          // Serialize method
          serializerGen.generate(w, message, structName);

          // Deserialize static method
          deserializerGen.generate(w, message, structName);
        });

    // Nested message struct extensions outside the main struct for nested serialize/deserialize
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.blankLine();
      String nestedName = structName + "_" + nameResolver.messageClassName(nested.getName());
      emitNestedStructTopLevel(w, nested, nestedName);
    }
  }

  private void emitNestedStruct(CodeWriter w, ProtoMessage nested, String nestedName) {
    w.block(
        "public struct " + nestedName.substring(nestedName.indexOf('_') + 1),
        () -> {
          // nested struct is actually top-level in Swift, so we emit it as a typealias scenario
          // For simplicity, we emit the fields here but the methods will be on the top-level name
        });
    // We'll actually use top-level structs for nested messages since Swift doesn't
    // support forward references within nested structs easily with self-referencing
  }

  /**
   * Emit a nested struct as a top-level type. Swift doesn't have a built-in flattening convention,
   * so nested messages become top-level structs with a prefix (e.g., User_Address).
   */
  private void emitNestedStructTopLevel(CodeWriter w, ProtoMessage nested, String nestedName) {
    w.block(
        "public struct " + nestedName,
        () -> {
          for (ProtoField field : nested.getFields()) {
            String swiftType = typeMapper.languageType(field);
            String swiftName = nameResolver.fieldName(field.getName());
            String defaultVal = typeMapper.defaultValue(field);
            w.line("public var %s: %s = %s", swiftName, swiftType, defaultVal);
          }

          for (ProtoMessage.OneofGroup group : nested.getOneofGroups()) {
            String caseName = SwiftNameResolver.snakeToCamel(group.name()) + "Case";
            w.line("public var %s: Int = 0", caseName);
          }

          // Nested enums within nested messages
          for (ProtoEnum protoEnum : nested.getEnums()) {
            emitEnum(w, protoEnum, nestedName);
          }

          // Default initializer
          w.blankLine();
          w.block("public init()", () -> {});

          // Serialize
          SwiftSerializerGenerator nestedSerializer =
              new SwiftSerializerGenerator(typeMapper, nameResolver);
          nestedSerializer.generate(w, nested, nestedName);

          // Deserialize
          SwiftDeserializerGenerator nestedDeserializer =
              new SwiftDeserializerGenerator(typeMapper, nameResolver);
          nestedDeserializer.generate(w, nested, nestedName);
        });
  }

  /**
   * Resolve the Swift type for a field, handling nested message references. When a field references
   * a nested message, the Swift type uses the flattened name (e.g., User_Address).
   */
  private String resolveFieldType(
      ProtoField field, String parentStructName, ProtoMessage parentMessage) {
    if (isNestedMessageRef(field, parentMessage)) {
      String nestedSimpleName = typeMapper.simpleTypeName(field.getTypeReference());
      String flattenedName = parentStructName + "_" + nestedSimpleName;
      if (field.isRepeated()) {
        return "[" + flattenedName + "]";
      }
      return flattenedName + "?";
    }
    return typeMapper.languageType(field);
  }

  /**
   * Resolve the default value for a field, handling nested message references.
   */
  private String resolveDefaultValue(
      ProtoField field, String parentStructName, ProtoMessage parentMessage) {
    if (isNestedMessageRef(field, parentMessage)) {
      if (field.isRepeated()) {
        return "[]";
      }
      return "nil";
    }
    return typeMapper.defaultValue(field);
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
    w.block(
        "public enum " + typeName + ": Int32",
        () -> {
          for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
            w.line("case %s = %d", nameResolver.enumConstantName(val.name()), val.number());
          }
        });
  }

  private void emitOneofConstants(CodeWriter w, ProtoMessage message, String structName) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      w.blankLine();
      w.line("// Oneof case constants for %s", SwiftNameResolver.snakeToPascal(group.name()));
      for (ProtoField member : group.members()) {
        String constName =
            "k"
                + SwiftNameResolver.snakeToPascal(group.name())
                + "_"
                + SwiftNameResolver.snakeToPascal(member.getName());
        w.line("public static let %s: Int = %d", constName, member.getFieldNumber());
      }
    }
  }
}
