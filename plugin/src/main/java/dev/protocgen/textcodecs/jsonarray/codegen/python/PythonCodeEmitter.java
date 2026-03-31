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
package dev.protocgen.textcodecs.jsonarray.codegen.python;

import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.LinkedHashSet;
import java.util.Set;

/** Generates complete Python source files for proto messages and enums. */
public class PythonCodeEmitter {

  private final PythonTypeMapper typeMapper;
  private final PythonNameResolver nameResolver;
  private final PythonSerializerGenerator serializerGen;
  private final PythonDeserializerGenerator deserializerGen;
  private final TypeRegistry typeRegistry;

  public PythonCodeEmitter(
      PythonTypeMapper typeMapper, PythonNameResolver nameResolver, TypeRegistry typeRegistry) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.typeRegistry = typeRegistry;
    this.serializerGen = new PythonSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new PythonDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete Python source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter();

    // Imports
    emitImports(w);

    // Collect cross-file imports (emitted lazily inside methods to avoid circular imports)
    Set<String> lazyImports = new LinkedHashSet<>();
    collectReferencedTypes(message, file, lazyImports);

    // Nested enums as module-level IntEnum classes (before the main class)
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
    }

    // Nested message classes (before the main class so they can be referenced)
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitNestedMessage(w, nested, file, lazyImports);
    }

    String className = nameResolver.messageClassName(message.getName());

    w.blankLine();
    w.blankLine();
    w.line("class %s:", className);
    w.indent();
    w.line(
        "\"\"\"Generated from proto message %s.\"\"\"",
        message.getFullName().replace("\"\"\"", "\\\"\\\"\\\""));

    // Nested enum/message class references as class attributes
    for (ProtoEnum protoEnum : message.getEnums()) {
      w.line("%s = %s", protoEnum.getName(), protoEnum.getName());
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.line("%s = %s", nested.getName(), nested.getName());
    }

    // __init__
    emitInit(w, message);

    // Properties (getters and setters)
    emitProperties(w, message);

    // has_* methods for optional and message fields
    emitHasMethods(w, message);

    // Serialize
    serializerGen.generate(w, message, lazyImports);

    // Deserialize
    deserializerGen.generate(w, message, className, lazyImports);

    // __repr__
    emitRepr(w, message, className);

    w.dedent();

    return w.toString();
  }

  /** Generate a complete Python source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();
    emitImports(w);
    emitEnum(w, protoEnum);
    return w.toString();
  }

  private void emitImports(CodeWriter w) {
    w.line("import json");
    w.line("import base64");
    w.line("import math");
  }

  /**
   * Collect cross-file import statements for types referenced by this message. These are emitted as
   * lazy imports inside method bodies to avoid circular import issues between mutually-referencing
   * messages (BUG-IO-01). Also skips nested enums (BUG-GA-01) and synthetic map-entry types
   * (BUG-GA-01).
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
        String moduleName = PythonNameResolver.pascalToSnake(simpleName);
        imports.add("from ." + moduleName + " import " + simpleName);
      }

      // For map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        String valRef = field.getMapValueTypeReference();

        // Skip synthetic map-entry types for map values too
        if (typeRegistry != null && typeRegistry.isMapEntry(valRef)) continue;

        String valName = valRef.substring(valRef.lastIndexOf('.') + 1);
        if (valRef.startsWith(currentPrefix)) {
          String moduleName = PythonNameResolver.pascalToSnake(valName);
          imports.add("from ." + moduleName + " import " + valName);
        }
      }
    }

    // Also check nested messages for their references
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectReferencedTypes(nested, file, imports);
    }
  }

  private void emitInit(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.line("def __init__(self):");
    w.indent();

    if (message.getFields().isEmpty() && message.getOneofGroups().isEmpty()) {
      w.line("pass");
      w.dedent();
      return;
    }

    // Field initializations
    for (ProtoField field : message.getFields()) {
      String pyName = nameResolver.fieldName(field.getName());
      String defaultVal = typeMapper.defaultValue(field);
      // Use mutable default factories for list/dict to avoid shared references
      if (field.isMap()) {
        w.line("self._%s = dict()", pyName);
      } else if (field.isRepeated()) {
        w.line("self._%s = list()", pyName);
      } else {
        w.line("self._%s = %s", pyName, defaultVal);
      }
    }

    // Presence tracking for proto3 optional
    if (hasOptionalFields(message)) {
      int maxPos = message.getMaxFieldNumber();
      w.line("self._present_fields = [False] * %d", maxPos);
    }

    // Oneof case tracking
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = nameResolver.fieldName(group.name()) + "_case";
      w.line("self._%s = 0  # 0 = not set", caseName);
    }

    w.dedent();
  }

  private void emitProperties(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String pyName = nameResolver.fieldName(field.getName());

      // Getter property
      w.blankLine();
      w.line("@property");
      w.line("def %s(self):", pyName);
      w.indent();
      w.line("return self._%s", pyName);
      w.dedent();

      // Setter property
      w.blankLine();
      w.line("@%s.setter", pyName);
      w.line("def %s(self, value):", pyName);
      w.indent();
      w.line("self._%s = value", pyName);

      if (field.isProto3Optional()) {
        w.line("self._present_fields[%d] = True", field.getArrayPosition());
      }

      if (field.isOneofMember()) {
        String caseName = nameResolver.fieldName(field.getOneofName()) + "_case";
        w.line("self._%s = %d", caseName, field.getFieldNumber());
      }

      w.dedent();
    }
  }

  private void emitHasMethods(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.isProto3Optional() || field.getKind() == ProtoField.FieldKind.MESSAGE) {
        String pyName = nameResolver.fieldName(field.getName());
        w.blankLine();
        w.line("def has_%s(self):", pyName);
        w.indent();
        if (field.isProto3Optional()) {
          w.line("return self._present_fields[%d]", field.getArrayPosition());
        } else {
          w.line("return self._%s is not None", pyName);
        }
        w.dedent();
      }
    }
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.blankLine();
    w.blankLine();
    w.line("class %s:", protoEnum.getName());
    w.indent();
    w.line("\"\"\"Proto enum %s represented as integer constants.\"\"\"", protoEnum.getName());

    // Enum constants as class attributes
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%s = %d", nameResolver.enumConstantName(val.name()), val.number());
    }

    // forNumber class method
    w.blankLine();
    w.line("_BY_NUMBER = {");
    w.indent();
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%d: %d,", val.number(), val.number());
    }
    w.dedent();
    w.line("}");

    w.blankLine();
    w.line("@classmethod");
    w.line("def for_number(cls, number):");
    w.indent();
    w.line("return cls._BY_NUMBER.get(number, None)");
    w.dedent();

    w.dedent();
  }

  private void emitNestedMessage(
      CodeWriter w, ProtoMessage nested, ProtoFile file, Set<String> lazyImports) {
    // Nested messages are emitted as top-level classes in Python,
    // then referenced as class attributes from the parent

    // First, recursively emit any of this nested message's own nested types
    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnum(w, protoEnum);
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      emitNestedMessage(w, deepNested, file, lazyImports);
    }

    String className = nameResolver.messageClassName(nested.getName());

    w.blankLine();
    w.blankLine();
    w.line("class %s:", className);
    w.indent();
    w.line("\"\"\"Generated from proto message %s.\"\"\"", nested.getFullName());

    // Class attribute references to nested types
    for (ProtoEnum protoEnum : nested.getEnums()) {
      w.line("%s = %s", protoEnum.getName(), protoEnum.getName());
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      w.line("%s = %s", deepNested.getName(), deepNested.getName());
    }

    emitInit(w, nested);
    emitProperties(w, nested);
    emitHasMethods(w, nested);

    serializerGen.generate(w, nested, lazyImports);
    deserializerGen.generate(w, nested, className, lazyImports);
    emitRepr(w, nested, className);

    w.dedent();
  }

  private void emitRepr(CodeWriter w, ProtoMessage message, String className) {
    w.blankLine();
    w.line("def __repr__(self):");
    w.indent();
    if (message.getFields().isEmpty()) {
      w.line("return \"%s()\"", className);
      w.dedent();
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("return f\"").append(className).append("(");
    for (int i = 0; i < message.getFields().size(); i++) {
      ProtoField field = message.getFields().get(i);
      String pyName = nameResolver.fieldName(field.getName());
      if (i > 0) sb.append(", ");
      sb.append(field.getName()).append("={self._").append(pyName).append("!r}");
    }
    sb.append(")\"");
    w.line(sb.toString());
    w.dedent();
  }

  private boolean hasOptionalFields(ProtoMessage message) {
    return message.getFields().stream().anyMatch(ProtoField::isProto3Optional);
  }
}
