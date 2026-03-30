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
package dev.protocgen.textcodecs.jsonarray.codegen.php;

import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.LinkedHashSet;
import java.util.Set;

/** Generates complete PHP source files for proto messages and enums. */
public class PhpCodeEmitter {

  private final PhpTypeMapper typeMapper;
  private final PhpNameResolver nameResolver;
  private final PhpSerializerGenerator serializerGen;
  private final PhpDeserializerGenerator deserializerGen;
  private final TypeRegistry typeRegistry;

  public PhpCodeEmitter(
      PhpTypeMapper typeMapper, PhpNameResolver nameResolver, TypeRegistry typeRegistry) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.typeRegistry = typeRegistry;
    this.serializerGen = new PhpSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new PhpDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete PHP source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter();

    // PHP header
    emitFileHeader(w, file);

    // Collect cross-file imports
    Set<String> useStatements = new LinkedHashSet<>();
    collectReferencedTypes(message, file, useStatements);

    // Use statements for cross-file types
    for (String use : useStatements) {
      w.line(use);
    }
    if (!useStatements.isEmpty()) {
      w.blankLine();
    }

    // Nested enums as separate classes are not supported inline in PHP;
    // they are defined before the main class
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
      w.blankLine();
    }

    // Nested message classes
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitNestedMessage(w, nested, file, useStatements);
      w.blankLine();
    }

    String className = nameResolver.messageClassName(message.getName());

    w.line("/**");
    w.line(" * Generated from proto message %s.", message.getFullName());
    w.line(" */");
    w.line("class %s", className);
    w.line("{");
    w.indent();

    // Properties
    emitProperties(w, message);

    // Presence tracking for proto3 optional
    if (hasOptionalFields(message)) {
      w.blankLine();
      int maxPos = message.getMaxFieldNumber();
      w.line("/** @var array<int, bool> */");
      w.line("public array $presentFields = [];");
    }

    // Oneof case tracking
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      w.blankLine();
      String caseName = nameResolver.fieldName(group.name()) + "Case";
      w.line("public int $%s = 0; // 0 = not set", caseName);
    }

    // Constructor
    emitConstructor(w, message);

    // Getters and Setters
    emitGettersSetters(w, message);

    // has* methods
    emitHasMethods(w, message);

    // Serialize
    serializerGen.generate(w, message, useStatements);

    // Deserialize
    deserializerGen.generate(w, message, className, useStatements);

    // __toString
    emitToString(w, message, className);

    w.dedent();
    w.line("}");

    return w.toString();
  }

  /** Generate a complete PHP source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();
    emitFileHeader(w, file);
    emitEnum(w, protoEnum);
    return w.toString();
  }

  private void emitFileHeader(CodeWriter w, ProtoFile file) {
    w.line("<?php");
    w.blankLine();
    w.line("declare(strict_types=1);");
    w.blankLine();

    String ns = nameResolver.resolvePackage(file);
    if (!ns.isEmpty()) {
      w.line("namespace %s;", ns);
      w.blankLine();
    }
  }

  /**
   * Collect cross-file use statements for types referenced by this message. Also skips nested
   * enums/messages and synthetic map-entry types.
   */
  private void collectReferencedTypes(ProtoMessage message, ProtoFile file, Set<String> uses) {
    String currentPrefix =
        file.getProtoPackage().isEmpty() ? "." : "." + file.getProtoPackage() + ".";
    String phpNamespace = nameResolver.resolvePackage(file);

    for (ProtoField field : message.getFields()) {
      addFieldTypeUse(
          field, field.getTypeReference(), message, file, currentPrefix, phpNamespace, uses);

      // For map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        addFieldTypeUse(
            field,
            field.getMapValueTypeReference(),
            message,
            file,
            currentPrefix,
            phpNamespace,
            uses);
      }
    }

    // Also check nested messages for their references
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectReferencedTypes(nested, file, uses);
    }
  }

  private void addFieldTypeUse(
      ProtoField field,
      String typeRef,
      ProtoMessage message,
      ProtoFile file,
      String currentPrefix,
      String phpNamespace,
      Set<String> uses) {
    if (typeRef == null) return;
    if (field.isWellKnownType()) return;

    // Skip synthetic map-entry types
    if (typeRegistry != null && typeRegistry.isMapEntry(typeRef)) return;

    // Check if the type is defined in the current message (nested)
    boolean isNested = false;
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (typeRef.equals(message.getFullName() + "." + nested.getName())) {
        isNested = true;
        break;
      }
    }
    if (!isNested) {
      for (ProtoEnum e : message.getEnums()) {
        if (typeRef.equals(message.getFullName() + "." + e.getName())) {
          isNested = true;
          break;
        }
      }
    }
    if (isNested) return;

    // Types in the same package don't need use statements (same namespace)
    // Types in different packages would need full qualification
    // For now, types in the same package are in the same namespace
  }

  private void emitProperties(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String phpName = nameResolver.fieldName(field.getName());
      String phpType = typeMapper.boxedType(field);
      String defaultVal = typeMapper.defaultValue(field);

      if (field.isMap() || field.isRepeated()) {
        w.line("/** @var array */");
        w.line("public array $%s = [];", phpName);
      } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        String simpleType = simpleTypeName(field.getTypeReference());
        w.line("public ?%s $%s = null;", simpleType, phpName);
      } else if (field.isProto3Optional()) {
        w.line("public %s $%s = %s;", phpType, phpName, defaultVal);
      } else {
        w.line("public %s $%s = %s;", phpType, phpName, defaultVal);
      }
    }
  }

  private void emitConstructor(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.line("public function __construct()");
    w.line("{");
    w.indent();

    if (message.getFields().isEmpty() && message.getOneofGroups().isEmpty()) {
      // Empty constructor
      w.dedent();
      w.line("}");
      return;
    }

    // Initialize presence tracking for proto3 optional
    if (hasOptionalFields(message)) {
      int maxPos = message.getMaxFieldNumber();
      w.line("$this->presentFields = array_fill(0, %d, false);", maxPos);
    }

    w.dedent();
    w.line("}");
  }

  private void emitGettersSetters(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String phpName = nameResolver.fieldName(field.getName());
      String phpType = typeMapper.boxedType(field);
      String getterName = nameResolver.getterName(field.getName());
      String setterName = nameResolver.setterName(field.getName());

      // Getter
      w.blankLine();
      if (field.isMap() || field.isRepeated()) {
        w.line("public function %s(): array", getterName);
      } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        String simpleType = simpleTypeName(field.getTypeReference());
        w.line("public function %s(): ?%s", getterName, simpleType);
      } else {
        w.line("public function %s(): %s", getterName, phpType);
      }
      w.line("{");
      w.indent();
      w.line("return $this->%s;", phpName);
      w.dedent();
      w.line("}");

      // Setter
      w.blankLine();
      if (field.isMap() || field.isRepeated()) {
        w.line("public function %s(array $value): self", setterName);
      } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        String simpleType = simpleTypeName(field.getTypeReference());
        w.line("public function %s(?%s $value): self", setterName, simpleType);
      } else {
        w.line("public function %s(%s $value): self", setterName, phpType);
      }
      w.line("{");
      w.indent();
      w.line("$this->%s = $value;", phpName);

      if (field.isProto3Optional()) {
        w.line("$this->presentFields[%d] = true;", field.getArrayPosition());
      }

      if (field.isOneofMember()) {
        String caseName = nameResolver.fieldName(field.getOneofName()) + "Case";
        w.line("$this->%s = %d;", caseName, field.getFieldNumber());
      }

      w.line("return $this;");
      w.dedent();
      w.line("}");
    }
  }

  private void emitHasMethods(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.isProto3Optional() || field.getKind() == ProtoField.FieldKind.MESSAGE) {
        String phpName = nameResolver.fieldName(field.getName());
        w.blankLine();
        w.line("public function has%s(): bool", PhpNameResolver.capitalize(phpName));
        w.line("{");
        w.indent();
        if (field.isProto3Optional()) {
          w.line("return $this->presentFields[%d];", field.getArrayPosition());
        } else {
          w.line("return $this->%s !== null;", phpName);
        }
        w.dedent();
        w.line("}");
      }
    }
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.line("/**");
    w.line(" * Proto enum %s represented as integer constants.", protoEnum.getName());
    w.line(" */");
    w.line("class %s", protoEnum.getName());
    w.line("{");
    w.indent();

    // Enum constants as class constants
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("public const %s = %d;", nameResolver.enumConstantName(val.name()), val.number());
    }

    // BY_NUMBER lookup
    w.blankLine();
    w.line("private const BY_NUMBER = [");
    w.indent();
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%d => %d,", val.number(), val.number());
    }
    w.dedent();
    w.line("];");

    // forNumber static method
    w.blankLine();
    w.line("public static function forNumber(int $number): ?int");
    w.line("{");
    w.indent();
    w.line("return self::BY_NUMBER[$number] ?? null;");
    w.dedent();
    w.line("}");

    w.dedent();
    w.line("}");
  }

  private void emitNestedMessage(
      CodeWriter w, ProtoMessage nested, ProtoFile file, Set<String> useStatements) {
    // In PHP, nested classes are not natively supported, so we emit them as
    // separate top-level classes in the same file

    // Recursively emit any of this nested message's own nested types
    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnum(w, protoEnum);
      w.blankLine();
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      emitNestedMessage(w, deepNested, file, useStatements);
      w.blankLine();
    }

    String className = nameResolver.messageClassName(nested.getName());

    w.line("/**");
    w.line(" * Generated from proto message %s.", nested.getFullName());
    w.line(" */");
    w.line("class %s", className);
    w.line("{");
    w.indent();

    emitProperties(w, nested);

    if (hasOptionalFields(nested)) {
      w.blankLine();
      int maxPos = nested.getMaxFieldNumber();
      w.line("/** @var array<int, bool> */");
      w.line("public array $presentFields = [];");
    }

    for (ProtoMessage.OneofGroup group : nested.getOneofGroups()) {
      w.blankLine();
      String caseName = nameResolver.fieldName(group.name()) + "Case";
      w.line("public int $%s = 0; // 0 = not set", caseName);
    }

    emitConstructor(w, nested);
    emitGettersSetters(w, nested);
    emitHasMethods(w, nested);

    serializerGen.generate(w, nested, useStatements);
    deserializerGen.generate(w, nested, className, useStatements);
    emitToString(w, nested, className);

    w.dedent();
    w.line("}");
  }

  private void emitToString(CodeWriter w, ProtoMessage message, String className) {
    w.blankLine();
    w.line("public function __toString(): string");
    w.line("{");
    w.indent();
    if (message.getFields().isEmpty()) {
      w.line("return '%s()';", className);
      w.dedent();
      w.line("}");
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("return '").append(className).append("('");
    for (int i = 0; i < message.getFields().size(); i++) {
      ProtoField field = message.getFields().get(i);
      String phpName = nameResolver.fieldName(field.getName());
      if (i > 0) {
        sb.append(" . ', '");
      }
      sb.append(" . '").append(field.getName()).append("=' . ");
      sb.append("var_export($this->").append(phpName).append(", true)");
    }
    sb.append(" . ')';");
    w.line(sb.toString());
    w.dedent();
    w.line("}");
  }

  private boolean hasOptionalFields(ProtoMessage message) {
    return message.getFields().stream().anyMatch(ProtoField::isProto3Optional);
  }

  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "mixed";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }
}
