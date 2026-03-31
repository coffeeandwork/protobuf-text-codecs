/*
 * Copyright 2026 protobuf-text-codecs contributors
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
package dev.protocgen.textcodecs.jsonarray.codegen.ruby;

import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.LinkedHashSet;
import java.util.Set;

/** Generates complete Ruby source files for proto messages and enums. */
public class RubyCodeEmitter {

  private final RubyTypeMapper typeMapper;
  private final RubyNameResolver nameResolver;
  private final RubySerializerGenerator serializerGen;
  private final RubyDeserializerGenerator deserializerGen;
  private final TypeRegistry typeRegistry;

  public RubyCodeEmitter(
      RubyTypeMapper typeMapper, RubyNameResolver nameResolver, TypeRegistry typeRegistry) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.typeRegistry = typeRegistry;
    this.serializerGen = new RubySerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new RubyDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete Ruby source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter("  "); // Ruby uses 2-space indentation

    // Imports
    emitImports(w);

    // Collect cross-file requires (emitted lazily inside methods to avoid circular requires)
    Set<String> lazyImports = new LinkedHashSet<>();
    collectReferencedTypes(message, file, lazyImports);

    // Open module hierarchy from proto package
    int moduleDepth = openModules(w, file);

    // Nested enums as module-level classes (before the main class)
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
    }

    // Nested message classes (before the main class so they can be referenced)
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitNestedMessage(w, nested, file, lazyImports);
    }

    String className = nameResolver.messageClassName(message.getName());

    w.blankLine();
    w.line("class %s", className);
    w.indent();
    w.line("# Generated from proto message %s.", message.getFullName().replace("\"", "\\\""));

    // attr_accessor for fields
    emitAttrAccessors(w, message);

    // initialize
    emitInit(w, message);

    // has_* methods for optional and message fields
    emitHasMethods(w, message);

    // Serialize
    serializerGen.generate(w, message, lazyImports);

    // Deserialize
    deserializerGen.generate(w, message, className, lazyImports);

    // inspect
    emitInspect(w, message, className);

    w.dedent();
    w.line("end");

    // Close module hierarchy
    closeModules(w, moduleDepth);

    return w.toString();
  }

  /** Generate a complete Ruby source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter("  ");
    emitImports(w);

    int moduleDepth = openModules(w, file);
    emitEnum(w, protoEnum);
    closeModules(w, moduleDepth);

    return w.toString();
  }

  private void emitImports(CodeWriter w) {
    w.line("# frozen_string_literal: true");
    w.blankLine();
    w.line("require 'json'");
    w.line("require 'base64'");
  }

  /**
   * Open nested module declarations from the proto package.
   *
   * @return the number of module levels opened
   */
  private int openModules(CodeWriter w, ProtoFile file) {
    String pkg = file.getProtoPackage();
    if (pkg == null || pkg.isEmpty()) return 0;

    String[] parts = pkg.split("\\.");
    for (String part : parts) {
      w.blankLine();
      // Capitalize first letter for Ruby module naming
      String moduleName = part.substring(0, 1).toUpperCase() + part.substring(1);
      w.line("module %s", moduleName);
      w.indent();
    }
    return parts.length;
  }

  private void closeModules(CodeWriter w, int depth) {
    for (int i = 0; i < depth; i++) {
      w.dedent();
      w.line("end");
    }
  }

  /**
   * Collect cross-file require_relative statements for types referenced by this message. These are
   * emitted as lazy requires inside method bodies to avoid circular require issues between
   * mutually-referencing messages (BUG-IO-01). Also skips nested enums (BUG-GA-01) and synthetic
   * map-entry types (BUG-GA-01).
   */
  private void collectReferencedTypes(ProtoMessage message, ProtoFile file, Set<String> imports) {
    String currentPrefix =
        file.getProtoPackage().isEmpty() ? "." : "." + file.getProtoPackage() + ".";

    for (ProtoField field : message.getFields()) {
      String typeRef = field.getTypeReference();
      if (typeRef == null) continue;
      if (field.isWellKnownType()) continue;

      // Skip synthetic map-entry types (BUG-GA-01)
      if (typeRegistry != null && typeRegistry.isMapEntry(typeRef)) continue;

      // Check if the type is defined in the current message (nested message or nested enum)
      boolean isNested = false;
      for (ProtoMessage nested : message.getNestedMessages()) {
        if (typeRef.equals(message.getFullName() + "." + nested.getName())) {
          isNested = true;
          break;
        }
      }
      if (!isNested) {
        // Also check nested enums (BUG-GA-01: previously only checked nested messages)
        for (ProtoEnum e : message.getEnums()) {
          if (typeRef.equals(message.getFullName() + "." + e.getName())) {
            isNested = true;
            break;
          }
        }
      }
      if (isNested) continue;

      // Extract the simple name and module name
      String simpleName = typeRef.substring(typeRef.lastIndexOf('.') + 1);

      // Check if this is a type in the same package but different file
      if (typeRef.startsWith(currentPrefix)) {
        String moduleName = RubyNameResolver.pascalToSnake(simpleName);
        imports.add("require_relative '" + moduleName + "'");
      }

      // For map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        String valRef = field.getMapValueTypeReference();

        // Skip synthetic map-entry types for map values too
        if (typeRegistry != null && typeRegistry.isMapEntry(valRef)) continue;

        String valName = valRef.substring(valRef.lastIndexOf('.') + 1);
        if (valRef.startsWith(currentPrefix)) {
          String moduleName = RubyNameResolver.pascalToSnake(valName);
          imports.add("require_relative '" + moduleName + "'");
        }
      }
    }

    // Also check nested messages for their references
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectReferencedTypes(nested, file, imports);
    }
  }

  private void emitAttrAccessors(CodeWriter w, ProtoMessage message) {
    if (message.getFields().isEmpty()) return;

    w.blankLine();
    for (ProtoField field : message.getFields()) {
      String rbName = nameResolver.fieldName(field.getName());
      w.line("attr_accessor :%s", rbName);
    }
  }

  private void emitInit(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.line("def initialize");
    w.indent();

    if (message.getFields().isEmpty() && message.getOneofGroups().isEmpty()) {
      w.line("# no fields");
      w.dedent();
      w.line("end");
      return;
    }

    // Field initializations
    for (ProtoField field : message.getFields()) {
      String rbName = nameResolver.fieldName(field.getName());
      String defaultVal = typeMapper.defaultValue(field);
      // Use fresh mutable instances for array/hash to avoid shared references
      if (field.isMap()) {
        w.line("@%s = {}", rbName);
      } else if (field.isRepeated()) {
        w.line("@%s = []", rbName);
      } else {
        w.line("@%s = %s", rbName, defaultVal);
      }
    }

    // Presence tracking for proto3 optional
    if (hasOptionalFields(message)) {
      int maxPos = message.getMaxFieldNumber();
      w.line("@present_fields = Array.new(%d, false)", maxPos);
    }

    // Oneof case tracking
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = nameResolver.fieldName(group.name()) + "_case";
      w.line("@%s = 0 # 0 = not set", caseName);
    }

    w.dedent();
    w.line("end");
  }

  private void emitHasMethods(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.isProto3Optional() || field.getKind() == ProtoField.FieldKind.MESSAGE) {
        String rbName = nameResolver.fieldName(field.getName());
        w.blankLine();
        w.line("def has_%s?", rbName);
        w.indent();
        if (field.isProto3Optional()) {
          w.line("@present_fields[%d]", field.getArrayPosition());
        } else {
          w.line("!@%s.nil?", rbName);
        }
        w.dedent();
        w.line("end");
      }
    }
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.blankLine();
    w.line("module %s", protoEnum.getName());
    w.indent();
    w.line("# Proto enum %s represented as integer constants.", protoEnum.getName());

    // Enum constants as module constants
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%s = %d", nameResolver.enumConstantName(val.name()), val.number());
    }

    // for_number class method
    w.blankLine();
    w.line("BY_NUMBER = {");
    w.indent();
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%d => %d,", val.number(), val.number());
    }
    w.dedent();
    w.line("}.freeze");

    w.blankLine();
    w.line("def self.for_number(number)");
    w.indent();
    w.line("BY_NUMBER[number]");
    w.dedent();
    w.line("end");

    w.dedent();
    w.line("end");
  }

  private void emitNestedMessage(
      CodeWriter w, ProtoMessage nested, ProtoFile file, Set<String> lazyImports) {
    // Nested messages are emitted as classes inside the parent module/class in Ruby

    // First, recursively emit any of this nested message's own nested types
    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnum(w, protoEnum);
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      emitNestedMessage(w, deepNested, file, lazyImports);
    }

    String className = nameResolver.messageClassName(nested.getName());

    w.blankLine();
    w.line("class %s", className);
    w.indent();
    w.line("# Generated from proto message %s.", nested.getFullName());

    // Class attribute references to nested types
    for (ProtoEnum protoEnum : nested.getEnums()) {
      w.line("%s = %s", protoEnum.getName(), protoEnum.getName());
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      w.line("%s = %s", deepNested.getName(), deepNested.getName());
    }

    emitAttrAccessors(w, nested);
    emitInit(w, nested);
    emitHasMethods(w, nested);

    serializerGen.generate(w, nested, lazyImports);
    deserializerGen.generate(w, nested, className, lazyImports);
    emitInspect(w, nested, className);

    w.dedent();
    w.line("end");
  }

  private void emitInspect(CodeWriter w, ProtoMessage message, String className) {
    w.blankLine();
    w.line("def inspect");
    w.indent();
    if (message.getFields().isEmpty()) {
      w.line("\"%s()\"", className);
      w.dedent();
      w.line("end");
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("\"").append(className).append("(");
    for (int i = 0; i < message.getFields().size(); i++) {
      ProtoField field = message.getFields().get(i);
      String rbName = nameResolver.fieldName(field.getName());
      if (i > 0) sb.append(", ");
      sb.append(field.getName()).append("=#{@").append(rbName).append(".inspect}");
    }
    sb.append(")\"");
    w.line(sb.toString());
    w.dedent();
    w.line("end");
  }

  private boolean hasOptionalFields(ProtoMessage message) {
    return message.getFields().stream().anyMatch(ProtoField::isProto3Optional);
  }
}
