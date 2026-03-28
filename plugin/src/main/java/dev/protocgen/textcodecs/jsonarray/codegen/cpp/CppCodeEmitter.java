package dev.protocgen.textcodecs.jsonarray.codegen.cpp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
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
    CodeWriter w = new CodeWriter();

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

    // Forward declarations for externally-referenced message types
    emitExternalForwardDeclarations(w, message, file);

    // Forward-declare nested types if needed
    emitForwardDeclarations(w, message);

    // Nested enums first (must be defined before the class that uses them)
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
      w.blankLine();
    }

    // Nested message class definitions (forward declarations + full definitions)
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitClassDeclaration(w, nested);
      w.blankLine();
    }

    // Main class declaration
    emitClassDeclaration(w, message);

    // Inline implementations for nested messages
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitInlineImplementations(w, nested, nested.getName());
    }

    // Inline implementations for the main message
    emitInlineImplementations(w, message, message.getName());

    // Close namespaces
    String[] nsClose = nameResolver.namespaceClose(file);
    for (String ns : nsClose) {
      w.blankLine();
      w.line(ns);
    }

    return w.toString();
  }

  /** Generate a complete C++ header file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();

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

    // Sort and deduplicate
    includes.stream().sorted().distinct().forEach(w::line);

    // Cross-file includes for referenced message/enum types
    emitCrossFileIncludes(w, message, file);
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
   * types defined in a different proto file within the same package.
   */
  private void collectCrossFileIncludes(
      ProtoMessage message, ProtoFile file, Set<String> includes) {
    String pkg = nameResolver.resolvePackage(file);
    String currentPrefix =
        file.getProtoPackage().isEmpty() ? "." : "." + file.getProtoPackage() + ".";

    for (ProtoField field : message.getFields()) {
      collectCppTypeInclude(
          field.getTypeReference(), field, message, file, currentPrefix, pkg, includes);

      // For map value type references
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        collectCppTypeInclude(
            field.getMapValueTypeReference(), null, message, file, currentPrefix, pkg, includes);
      }
    }

    // Recurse into nested messages
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectCrossFileIncludes(nested, file, includes);
    }
  }

  private void collectCppTypeInclude(
      String typeRef,
      ProtoField field,
      ProtoMessage message,
      ProtoFile file,
      String currentPrefix,
      String pkg,
      Set<String> includes) {
    if (typeRef == null) return;
    if (field != null && field.isWellKnownType()) return;

    // Check if the type is a nested type within the current message
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (typeRef.equals(message.getFullName() + "." + nested.getName())) {
        return;
      }
    }

    // Check if this is a type in the same package but different file
    if (typeRef.startsWith(currentPrefix)) {
      String simpleName = typeRef.substring(typeRef.lastIndexOf('.') + 1);
      // Skip if this is the same message we're generating
      if (simpleName.equals(message.getName())) return;

      String dir = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
      includes.add("#include \"" + dir + simpleName + ".hpp\"");
    }
  }

  /**
   * Emit forward declarations (class X;) for externally-referenced message types. This prevents
   * issues with circular header includes.
   */
  private void emitExternalForwardDeclarations(CodeWriter w, ProtoMessage message, ProtoFile file) {
    Set<String> forwardDecls = new LinkedHashSet<>();
    collectExternalForwardDeclarations(message, file, forwardDecls);
    for (String decl : forwardDecls) {
      w.line(decl);
    }
    if (!forwardDecls.isEmpty()) {
      w.blankLine();
    }
  }

  private void collectExternalForwardDeclarations(
      ProtoMessage message, ProtoFile file, Set<String> forwardDecls) {
    String currentPrefix =
        file.getProtoPackage().isEmpty() ? "." : "." + file.getProtoPackage() + ".";

    for (ProtoField field : message.getFields()) {
      collectCppTypeForwardDecl(
          field.getTypeReference(), field, message, file, currentPrefix, forwardDecls);
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        collectCppTypeForwardDecl(
            field.getMapValueTypeReference(), null, message, file, currentPrefix, forwardDecls);
      }
    }

    for (ProtoMessage nested : message.getNestedMessages()) {
      collectExternalForwardDeclarations(nested, file, forwardDecls);
    }
  }

  private void collectCppTypeForwardDecl(
      String typeRef,
      ProtoField field,
      ProtoMessage message,
      ProtoFile file,
      String currentPrefix,
      Set<String> forwardDecls) {
    if (typeRef == null) return;
    if (field != null && field.isWellKnownType()) return;
    // Only message types need forward declarations (not enums)
    if (field != null
        && field.getKind() != ProtoField.FieldKind.MESSAGE
        && field.getKind() != ProtoField.FieldKind.WELL_KNOWN_TYPE) return;

    // Check if the type is a nested type within the current message
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (typeRef.equals(message.getFullName() + "." + nested.getName())) {
        return;
      }
    }

    // Check if this is a type in the same package but different file
    if (typeRef.startsWith(currentPrefix)) {
      String simpleName = typeRef.substring(typeRef.lastIndexOf('.') + 1);
      if (simpleName.equals(message.getName())) return;

      forwardDecls.add("class " + simpleName + ";");
    }
  }

  private void emitForwardDeclarations(CodeWriter w, ProtoMessage message) {
    // Forward-declare nested message types that are referenced by the parent
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.line("class %s;", nested.getName());
    }
    if (!message.getNestedMessages().isEmpty()) {
      w.blankLine();
    }
  }

  private void emitClassDeclaration(CodeWriter w, ProtoMessage message) {
    String className = nameResolver.messageClassName(message.getName());

    w.block(
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
          w.line("std::string to_json_string() const;");
          w.line("static %s from_json_string(const std::string& json);", className);

          w.dedent();
        });
    w.line(";");
  }

  private void emitFields(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String cppType = typeMapper.languageType(field);
      String cppName = nameResolver.fieldName(field.getName()) + "_";
      String defaultVal = typeMapper.defaultValue(field);

      if (field.isProto3Optional()
          || field.isMap()
          || field.isRepeated()
          || field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        w.line("%s %s = %s;", cppType, cppName, defaultVal);
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        w.line("%s %s = %s;", cppType, cppName, defaultVal);
      } else {
        w.line("%s %s = %s;", cppType, cppName, defaultVal);
      }
    }

    // Oneof case tracking fields
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = "__oneof_" + nameResolver.fieldName(group.name()) + "_case_";
      w.line("int %s = 0; // 0 = not set", caseName);
    }
  }

  private void emitGettersSetters(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String cppType = typeMapper.languageType(field);
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
        w.line("bool %s() const { return %s.has_value(); }", hasName, cppName);
      }
    }
  }

  private void emitOneofAccessors(CodeWriter w, ProtoMessage message) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = "__oneof_" + nameResolver.fieldName(group.name()) + "_case_";
      String getterName = nameResolver.fieldName(group.name()) + "_case";
      w.blankLine();
      w.line("int %s() const { return %s; }", getterName, caseName);
    }
  }

  private void emitInlineImplementations(CodeWriter w, ProtoMessage message, String className) {
    // Serialize method
    serializerGen.generate(w, message, className);

    // Deserialize method
    deserializerGen.generate(w, message, className);
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    String enumName = protoEnum.getName();
    w.block(
        "enum class " + enumName,
        () -> {
          for (int i = 0; i < protoEnum.getValues().size(); i++) {
            ProtoEnum.EnumValue val = protoEnum.getValues().get(i);
            String suffix = i < protoEnum.getValues().size() - 1 ? "," : "";
            w.line("%s = %d%s", nameResolver.enumConstantName(val.name()), val.number(), suffix);
          }
        });
    w.line(";");

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
      String caseName = "__oneof_" + nameResolver.fieldName(field.getOneofName()) + "_case_";
      return " " + caseName + " = " + field.getFieldNumber() + ";";
    }
    return "";
  }
}
