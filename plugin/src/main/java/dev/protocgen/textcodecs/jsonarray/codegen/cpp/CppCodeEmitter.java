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
package dev.protocgen.textcodecs.jsonarray.codegen.cpp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates complete C++ header-only (.hpp) files for proto messages and enums. Each message
 * produces a single .hpp with inline implementations.
 */
public class CppCodeEmitter {

  private final CppTypeMapper typeMapper;
  private final CppNameResolver nameResolver;
  private final CppSerializerGenerator serializerGen;
  private final CppDeserializerGenerator deserializerGen;

  public CppCodeEmitter(CppTypeMapper typeMapper, CppNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.serializerGen = new CppSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new CppDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete C++ header file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    typeMapper.setCurrentFile(file);
    CodeWriter w = new CodeWriter("  ");

    emitHeaderPreamble(w, message, file);

    // Open namespaces
    String[] nsOpen = nameResolver.namespaceOpen(file);
    for (String ns : nsOpen) {
      w.blankLine();
      w.line(ns + " {");
    }

    if (nsOpen.length > 0) {
      w.blankLine();
    }

    // Every nested message and enum is flattened to a namespace-level definition; emit them
    // deepest-first so each is complete before the type that uses it.
    List<ProtoMessage> nestedMessages = CppTypeUtil.descendantMessages(message);

    // Forward-declare flattened nested message types.
    for (ProtoMessage nested : nestedMessages) {
      w.line("class %s;", nested.getName());
    }
    if (!nestedMessages.isEmpty()) {
      w.blankLine();
    }

    // Enums first (including those nested at any depth) -- must precede classes that use them.
    for (ProtoEnum protoEnum : collectEnums(message)) {
      emitEnum(w, protoEnum);
      w.blankLine();
    }

    // Nested message class definitions, then the main class.
    for (ProtoMessage nested : nestedMessages) {
      emitClassDeclaration(w, nested);
      w.blankLine();
    }
    emitClassDeclaration(w, message);

    // Inline implementations for nested messages, then the main message.
    for (ProtoMessage nested : nestedMessages) {
      emitInlineImplementations(w, nested, nested.getName(), file);
    }
    emitInlineImplementations(w, message, message.getName(), file);

    // Close namespaces
    String[] nsClose = nameResolver.namespaceClose(file);
    for (String ns : nsClose) {
      w.blankLine();
      w.line(ns);
    }

    return w.toString();
  }

  /** Collect all enums declared in {@code message} or any of its nested messages (any depth). */
  private List<ProtoEnum> collectEnums(ProtoMessage message) {
    List<ProtoEnum> result = new ArrayList<>(message.getEnums());
    for (ProtoMessage nested : CppTypeUtil.descendantMessages(message)) {
      result.addAll(nested.getEnums());
    }
    return result;
  }

  /** Generate a complete C++ header file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    typeMapper.setCurrentFile(file);
    CodeWriter w = new CodeWriter("  ");

    w.line("#pragma once");
    w.blankLine();
    w.line("#include <cstdint>");

    // Open namespaces
    String[] nsOpen = nameResolver.namespaceOpen(file);
    for (String ns : nsOpen) {
      w.blankLine();
      w.line(ns + " {");
    }

    if (nsOpen.length > 0) {
      w.blankLine();
    }

    emitEnum(w, protoEnum);

    // Close namespaces
    String[] nsClose = nameResolver.namespaceClose(file);
    for (String ns : nsClose) {
      w.blankLine();
      w.line(ns);
    }

    return w.toString();
  }

  private void emitHeaderPreamble(CodeWriter w, ProtoMessage message, ProtoFile file) {
    w.line("#pragma once");
    w.blankLine();

    // Collect all needed includes based on field types
    List<String> includes = new ArrayList<>();
    includes.add("#include <string>");
    includes.add("#include <cstdint>");
    includes.add("#include <cmath>");
    includes.add("#include <nlohmann/json.hpp>");
    includes.add("#include <jsonarray/codec.hpp>");

    if (needsInclude(message, "vector")) {
      includes.add("#include <vector>");
    }
    if (needsInclude(message, "optional")) {
      includes.add("#include <optional>");
    }
    if (needsInclude(message, "map")) {
      includes.add("#include <map>");
    }
    if (needsInclude(message, "unordered_map")) {
      includes.add("#include <unordered_map>");
    }
    if (needsInclude(message, "variant")) {
      includes.add("#include <variant>");
    }
    if (hasSelfReference(message)) {
      includes.add("#include <memory>");
    }

    // Sort and deduplicate
    includes.stream().sorted().distinct().forEach(w::line);

    // Cross-file includes for referenced message/enum types
    emitCrossFileIncludes(w, message, file);
  }

  /**
   * True if {@code message} or any nested message has a self-referential singular message field.
   */
  private boolean hasSelfReference(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (CppTypeUtil.isSelfReference(field, message)) return true;
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (hasSelfReference(nested)) return true;
    }
    return false;
  }

  private boolean needsInclude(ProtoMessage message, String header) {
    for (ProtoField field : message.getFields()) {
      switch (header) {
        case "vector":
          if (field.isRepeated() || field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
            return true;
          }
          break;
        case "optional":
          if (field.isProto3Optional()) return true;
          // Singular message fields use std::optional for null/presence semantics
          if (!field.isRepeated()
              && !field.isMap()
              && (field.getKind() == ProtoField.FieldKind.MESSAGE
                  || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE)) return true;
          break;
        case "map":
          if (field.isMap() && field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
            return true;
          }
          break;
        case "unordered_map":
          if (field.isMap() && field.getMapKeyType() != FieldDescriptorProto.Type.TYPE_STRING) {
            return true;
          }
          break;
        case "variant":
          if (field.isOneofMember()) return true;
          break;
      }
    }
    // Check nested messages recursively
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (needsInclude(nested, header)) return true;
    }
    return false;
  }

  /** Emit #include directives for message/enum types referenced from other proto files. */
  private void emitCrossFileIncludes(CodeWriter w, ProtoMessage message, ProtoFile file) {
    Set<String> includes = new LinkedHashSet<>();
    collectCrossFileIncludes(message, file, includes);
    for (String inc : includes) {
      w.line(inc);
    }
  }

  /**
   * Walk all fields (including nested messages) and collect #include directives for MESSAGE/ENUM
   * types defined in another proto file -- whether in the same package (different file) or a
   * different package. Types defined inside this top-level message, and the message itself, are
   * emitted in the same header and need no include.
   */
  private void collectCrossFileIncludes(
      ProtoMessage message, ProtoFile file, Set<String> includes) {
    for (ProtoField field : message.getFields()) {
      // A map field's own type reference is the synthetic *MapEntry message, which is never
      // generated; only the value type may need an include.
      if (field.isMap()) {
        collectCppTypeInclude(field.getMapValueTypeReference(), message, file, includes);
      } else if (!field.isWellKnownType()) {
        collectCppTypeInclude(field.getTypeReference(), message, file, includes);
      }
    }

    // Recurse into nested messages
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectCrossFileIncludes(nested, message, file, includes);
    }
  }

  private void collectCrossFileIncludes(
      ProtoMessage message, ProtoMessage topLevel, ProtoFile file, Set<String> includes) {
    for (ProtoField field : message.getFields()) {
      if (field.isMap()) {
        collectCppTypeInclude(field.getMapValueTypeReference(), topLevel, file, includes);
      } else if (!field.isWellKnownType()) {
        collectCppTypeInclude(field.getTypeReference(), topLevel, file, includes);
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectCrossFileIncludes(nested, topLevel, file, includes);
    }
  }

  private void collectCppTypeInclude(
      String typeRef, ProtoMessage topLevel, ProtoFile file, Set<String> includes) {
    if (typeRef == null) return;

    // Types declared inside this top-level message are emitted in the same header.
    if (typeRef.startsWith(topLevel.getFullName() + ".")) return;
    if (typeRef.equals(topLevel.getFullName())) return;

    String pkg = nameResolver.resolvePackage(file);
    String currentPrefix = pkg.isEmpty() ? "." : "." + pkg + ".";
    if (typeRef.startsWith(currentPrefix)) {
      // Same package, different file.
      String simpleName = ProtoTypeUtil.simpleTypeName(typeRef);
      String dir = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
      includes.add("#include \"" + dir + simpleName + ".hpp\"");
      return;
    }

    // Cross-package reference.
    String inc = CppTypeUtil.crossPackageInclude(typeRef, file);
    if (inc != null) {
      includes.add(inc);
    }
  }

  private void emitClassDeclaration(CodeWriter w, ProtoMessage message) {
    String className = nameResolver.messageClassName(message.getName());

    w.blockContinue(
        "class " + className,
        () -> {
          // Private fields
          w.line("private:");
          w.indent();
          emitFields(w, message);
          w.dedent();

          w.blankLine();
          w.line("public:");
          w.indent();

          // Default constructor
          w.line("%s() = default;", className);
          w.blankLine();

          // Move/copy constructors
          w.line("%s(const %s&) = default;", className, className);
          w.line("%s(%s&&) = default;", className, className);
          w.line("%s& operator=(const %s&) = default;", className, className);
          w.line("%s& operator=(%s&&) = default;", className, className);

          // Getters and setters
          emitGettersSetters(w, message);

          // Oneof case accessors
          emitOneofAccessors(w, message);

          // Serialize/deserialize declarations
          w.blankLine();
          w.line("nlohmann::json serialize() const;");
          w.line("static %s deserialize(const nlohmann::json& arr);", className);
          w.blankLine();
          w.line("bool SerializeToString(std::string* output) const;");
          w.line("bool ParseFromString(const std::string& json);", className);

          w.dedent();
        });
    w.rawLine(";");
  }

  private void emitFields(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String cppType = fieldType(field, message);
      String cppName = nameResolver.fieldName(field.getName()) + "_";
      String defaultVal = fieldDefault(field, message);

      w.line("%s %s = %s;", cppType, cppName, defaultVal);
    }

    // Oneof case tracking fields
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = "pb_oneof_" + nameResolver.fieldName(group.name()) + "_case_";
      w.line("int %s = 0; // 0 = not set", caseName);
    }
  }

  /**
   * The declared C++ type for a field; self-referential message fields are boxed via shared_ptr.
   */
  private String fieldType(ProtoField field, ProtoMessage message) {
    if (CppTypeUtil.isSelfReference(field, message)) {
      return "std::shared_ptr<" + nameResolver.messageClassName(message.getName()) + ">";
    }
    return typeMapper.languageType(field);
  }

  private String fieldDefault(ProtoField field, ProtoMessage message) {
    if (CppTypeUtil.isSelfReference(field, message)) {
      return "nullptr";
    }
    return typeMapper.defaultValue(field);
  }

  private void emitGettersSetters(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String cppType = fieldType(field, message);
      String cppName = nameResolver.fieldName(field.getName()) + "_";
      String getterName = nameResolver.getterName(field.getName());
      String setterName = nameResolver.setterName(field.getName());

      w.blankLine();

      // Getter - return const reference for non-primitives
      if (isComplexType(field)) {
        w.line("const %s& %s() const { return %s; }", cppType, getterName, cppName);
      } else {
        w.line("%s %s() const { return %s; }", cppType, getterName, cppName);
      }

      // Setter - const reference overload
      if (isComplexType(field)) {
        w.line(
            "void %s(const %s& value) { %s = value;%s }",
            setterName, cppType, cppName, oneofSetSuffix(field));
        // Move setter
        w.line(
            "void %s(%s&& value) { %s = std::move(value);%s }",
            setterName, cppType, cppName, oneofSetSuffix(field));
      } else {
        w.line(
            "void %s(%s value) { %s = value;%s }",
            setterName, cppType, cppName, oneofSetSuffix(field));
      }

      // has_* method for optional and singular message fields
      if (field.isProto3Optional()
          || (!field.isRepeated()
              && !field.isMap()
              && (field.getKind() == ProtoField.FieldKind.MESSAGE
                  || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE))) {
        String hasName = "has_" + nameResolver.fieldName(field.getName());
        if (CppTypeUtil.isSelfReference(field, message)) {
          w.line("bool %s() const { return %s != nullptr; }", hasName, cppName);
        } else {
          w.line("bool %s() const { return %s.has_value(); }", hasName, cppName);
        }
      }
    }
  }

  private void emitOneofAccessors(CodeWriter w, ProtoMessage message) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = "pb_oneof_" + nameResolver.fieldName(group.name()) + "_case_";
      String getterName = nameResolver.fieldName(group.name()) + "_case";
      w.blankLine();
      w.line("int %s() const { return %s; }", getterName, caseName);
    }
  }

  private void emitInlineImplementations(
      CodeWriter w, ProtoMessage message, String className, ProtoFile file) {
    // Serialize method
    serializerGen.generate(w, message, className);

    // Deserialize method
    deserializerGen.generate(w, message, className, file);
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    String enumName = protoEnum.getName();
    w.blockContinue(
        "enum class " + enumName,
        () -> {
          for (int i = 0; i < protoEnum.getValues().size(); i++) {
            ProtoEnum.EnumValue val = protoEnum.getValues().get(i);
            String suffix = i < protoEnum.getValues().size() - 1 ? "," : "";
            w.line("%s = %d%s", nameResolver.enumConstantName(val.name()), val.number(), suffix);
          }
        });
    w.rawLine(";");

    // to_number helper
    w.blankLine();
    w.block(
        "inline int " + enumName + "_to_number(" + enumName + " value)",
        () -> {
          w.line("return static_cast<int>(value);");
        });

    // from_number helper
    w.blankLine();
    w.block(
        "inline " + enumName + " " + enumName + "_from_number(int number)",
        () -> {
          w.line("return static_cast<%s>(number);", enumName);
        });
  }

  /** Determine if a field type is "complex" (should be passed by const reference). */
  private boolean isComplexType(ProtoField field) {
    if (field.isMap() || field.isRepeated()) return true;
    if (field.isProto3Optional()) return true;
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return true;
    }
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      return true;
    }
    return false;
  }

  /** Return additional statements for setter body when field is a oneof member. */
  private String oneofSetSuffix(ProtoField field) {
    if (field.isOneofMember()) {
      String caseName = "pb_oneof_" + nameResolver.fieldName(field.getOneofName()) + "_case_";
      return " " + caseName + " = " + field.getFieldNumber() + ";";
    }
    return "";
  }
}
