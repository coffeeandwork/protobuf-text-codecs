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
package dev.protocgen.textcodecs.jsonarray.codegen.c;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates complete C source files (.h and .c) for proto messages and enums. Each message produces
 * two files: a header with declarations and a source with implementations.
 */
public class CCodeEmitter {

  private final CTypeMapper typeMapper;
  private final CNameResolver nameResolver;
  private final CSerializerGenerator serializerGen;
  private final CDeserializerGenerator deserializerGen;

  public CCodeEmitter(CTypeMapper typeMapper, CNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.serializerGen = new CSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new CDeserializerGenerator(typeMapper, nameResolver);
  }

  // ========================================================================
  // Header file (.h) generation
  // ========================================================================

  /** Generate the .h header file for a message. */
  public String emitHeader(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter("  ");
    String pkg = nameResolver.resolvePackage(file);
    String typeName = nameResolver.qualifiedTypeName(pkg, message.getName());
    String funcPrefix = nameResolver.functionPrefix(pkg, message.getName());
    String guard = nameResolver.includeGuard(file, message.getName());

    // Include guard
    w.line("#ifndef %s", guard);
    w.line("#define %s", guard);
    w.blankLine();

    // Standard includes
    emitHeaderIncludes(w);

    // Cross-file includes for referenced message/enum types
    emitCrossFileIncludes(w, message, file);
    w.blankLine();

    // C++ guard
    w.line("#ifdef __cplusplus");
    w.line("extern \"C\" {");
    w.line("#endif");
    w.blankLine();

    // Forward declarations for externally-referenced message types
    emitExternalForwardDeclarations(w, message, file);

    // Forward declarations for nested messages
    for (ProtoMessage nested : message.getNestedMessages()) {
      String nestedType =
          nameResolver.qualifiedTypeName(pkg, message.getName() + "_" + nested.getName());
      w.line("typedef struct %s %s;", nestedType, nestedType);
    }
    if (!message.getNestedMessages().isEmpty()) {
      w.blankLine();
    }

    // Nested enums
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnumDef(w, protoEnum, pkg, message.getName() + "_");
    }

    // Map entry typedefs (must come before the struct)
    emitMapEntryTypedefs(w, message, funcPrefix);

    // Oneof case enums and union types
    emitOneofTypes(w, message, funcPrefix, pkg);

    // Struct definition
    emitStructDef(w, message, typeName, funcPrefix, pkg);

    // Function declarations
    w.blankLine();
    w.line("/* Serialization */");
    serializerGen.generateDeclarations(w, funcPrefix, typeName);
    w.blankLine();
    w.line("/* Deserialization */");
    deserializerGen.generateDeclarations(w, funcPrefix, typeName);
    w.blankLine();
    w.line("/* Memory management */");
    w.line("void %s_free(%s* msg);", funcPrefix, typeName);

    // Nested message declarations
    for (ProtoMessage nested : message.getNestedMessages()) {
      String nestedTypeName =
          nameResolver.qualifiedTypeName(pkg, message.getName() + "_" + nested.getName());
      String nestedFuncPrefix =
          nameResolver.functionPrefix(pkg, message.getName() + "_" + nested.getName());
      w.blankLine();
      w.line("/* Nested message: %s */", nested.getName());
      emitNestedMessageHeader(
          w, nested, file, nestedTypeName, nestedFuncPrefix, pkg, message.getName() + "_");
    }

    w.blankLine();
    w.line("#ifdef __cplusplus");
    w.line("}");
    w.line("#endif");
    w.blankLine();
    w.line("#endif /* %s */", guard);

    return w.toString();
  }

  /** Generate the .h header file for a top-level enum. */
  public String emitTopLevelEnumHeader(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter("  ");
    String pkg = nameResolver.resolvePackage(file);
    String guard = nameResolver.includeGuard(file, protoEnum.getName());

    w.line("#ifndef %s", guard);
    w.line("#define %s", guard);
    w.blankLine();
    w.line("#ifdef __cplusplus");
    w.line("extern \"C\" {");
    w.line("#endif");
    w.blankLine();

    emitEnumDef(w, protoEnum, pkg, "");

    w.line("#ifdef __cplusplus");
    w.line("}");
    w.line("#endif");
    w.blankLine();
    w.line("#endif /* %s */", guard);

    return w.toString();
  }

  // ========================================================================
  // Source file (.c) generation
  // ========================================================================

  /** Generate the .c source file for a message. */
  public String emitSource(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter("  ");
    String pkg = nameResolver.resolvePackage(file);
    String typeName = nameResolver.qualifiedTypeName(pkg, message.getName());
    String funcPrefix = nameResolver.functionPrefix(pkg, message.getName());
    String snakeName = CNameResolver.pascalToSnake(message.getName());

    // Include our own header
    String headerFile =
        pkg.isEmpty() ? snakeName + ".h" : pkg.replace('.', '/') + "/" + snakeName + ".h";
    w.line("#include \"%s\"", headerFile);
    w.blankLine();

    // Nested message implementations first
    for (ProtoMessage nested : message.getNestedMessages()) {
      String nestedTypeName =
          nameResolver.qualifiedTypeName(pkg, message.getName() + "_" + nested.getName());
      String nestedFuncPrefix =
          nameResolver.functionPrefix(pkg, message.getName() + "_" + nested.getName());
      emitNestedMessageSource(
          w, nested, file, nestedTypeName, nestedFuncPrefix, pkg, message.getName() + "_");
    }

    // Serialize
    serializerGen.generate(w, message, funcPrefix, typeName);

    // Deserialize
    deserializerGen.generate(w, message, funcPrefix, typeName);

    // Free
    emitFreeFunction(w, message, funcPrefix, typeName, pkg);

    return w.toString();
  }

  /**
   * Generate a .c source file for a top-level enum (no .c needed for pure enums, but we return an
   * empty string for consistency).
   */
  public String emitTopLevelEnumSource(ProtoEnum protoEnum, ProtoFile file) {
    // Enums in C are defined entirely in the header; no .c file content needed.
    return null;
  }

  // ========================================================================
  // Private helpers
  // ========================================================================

  private void emitHeaderIncludes(CodeWriter w) {
    w.line("#include <stdint.h>");
    w.line("#include <stdbool.h>");
    w.line("#include <stdlib.h>");
    w.line("#include <string.h>");
    w.line("#include <stdio.h>");
    w.line("#include <math.h>");
    w.line("#include \"cjson/cJSON.h\"");
    w.line("#include \"jsonarray/codec.h\"");
  }

  /**
   * Emit forward declarations (typedef struct X X;) for all externally-referenced message types.
   * This ensures the struct type is known even if the #include is guarded out (circular
   * references).
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
      collectTypeForwardDecl(
          field.getTypeReference(), field, message, file, currentPrefix, forwardDecls);
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        collectTypeForwardDecl(
            field.getMapValueTypeReference(), null, message, file, currentPrefix, forwardDecls);
      }
    }

    for (ProtoMessage nested : message.getNestedMessages()) {
      collectExternalForwardDeclarations(nested, file, forwardDecls);
    }
  }

  private void collectTypeForwardDecl(
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
      String simpleName = ProtoTypeUtil.simpleTypeName(typeRef);
      if (simpleName.equals(message.getName())) return;

      String cTypeName = nameResolver.resolveTypeReference(typeRef, null);
      forwardDecls.add("typedef struct " + cTypeName + " " + cTypeName + ";");
    }
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
      collectTypeInclude(
          field.getTypeReference(), field, message, file, currentPrefix, pkg, includes);

      // For map value type references
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        collectTypeInclude(
            field.getMapValueTypeReference(), null, message, file, currentPrefix, pkg, includes);
      }
    }

    // Recurse into nested messages
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectCrossFileIncludes(nested, file, includes);
    }
  }

  private void collectTypeInclude(
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
      String simpleName = ProtoTypeUtil.simpleTypeName(typeRef);
      // Skip if this is the same message we're generating
      if (simpleName.equals(message.getName())) return;

      String snakeName = CNameResolver.pascalToSnake(simpleName);
      String dir = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
      includes.add("#include \"" + dir + snakeName + ".h\"");
    }
  }

  private void emitEnumDef(CodeWriter w, ProtoEnum protoEnum, String pkg, String parentPrefix) {
    String enumTypeName = nameResolver.qualifiedTypeName(pkg, parentPrefix + protoEnum.getName());

    w.line("typedef enum {");
    w.indent();
    for (int i = 0; i < protoEnum.getValues().size(); i++) {
      ProtoEnum.EnumValue val = protoEnum.getValues().get(i);
      String constName = enumTypeName.toUpperCase() + "_" + val.name();
      String suffix = i < protoEnum.getValues().size() - 1 ? "," : "";
      w.line("%s = %d%s", constName, val.number(), suffix);
    }
    w.dedent();
    w.line("} %s;", enumTypeName);
    w.blankLine();
  }

  private void emitMapEntryTypedefs(CodeWriter w, ProtoMessage message, String funcPrefix) {
    for (ProtoField field : message.getFields()) {
      if (field.isMap()) {
        String entryType = typeMapper.qualifiedMapEntryTypeName(funcPrefix, field);
        String keyType = typeMapper.mapKeyType(field);
        String valType = typeMapper.mapValueType(field);

        w.line("typedef struct {");
        w.indent();
        w.line("%s key;", keyType);
        w.line("%s value;", valType);
        if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
          w.line("size_t value_len;");
        }
        w.dedent();
        w.line("} %s;", entryType);
        w.blankLine();
      }
    }
  }

  private void emitOneofTypes(CodeWriter w, ProtoMessage message, String funcPrefix, String pkg) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String enumName = funcPrefix.toUpperCase() + "_" + group.name().toUpperCase() + "_CASE";
      // Case enum
      w.line("typedef enum {");
      w.indent();
      w.line("%s_NOT_SET = 0,", funcPrefix.toUpperCase() + "_" + group.name().toUpperCase());
      for (ProtoField member : group.members()) {
        String constName =
            funcPrefix.toUpperCase()
                + "_"
                + group.name().toUpperCase()
                + "_"
                + member.getName().toUpperCase();
        w.line("%s = %d,", constName, member.getFieldNumber());
      }
      w.dedent();
      w.line("} %s;", enumName);
      w.blankLine();

      // Union type
      String unionName = funcPrefix + "_" + group.name() + "_union";
      w.line("typedef union {");
      w.indent();
      for (ProtoField member : group.members()) {
        String memberField = nameResolver.fieldName(member.getName());
        String memberType = resolveFieldType(member, pkg);
        w.line("%s %s;", memberType, memberField);
      }
      w.dedent();
      w.line("} %s;", unionName);
      w.blankLine();
    }
  }

  private void emitStructDef(
      CodeWriter w, ProtoMessage message, String typeName, String funcPrefix, String pkg) {
    w.line("typedef struct {");
    w.indent();

    for (ProtoField field : message.getFields()) {
      if (field.isOneofMember()) {
        // Oneof members are emitted as part of the union; skip individual fields
        continue;
      }

      String fieldName = nameResolver.fieldName(field.getName());

      if (field.isMap()) {
        String entryType = typeMapper.qualifiedMapEntryTypeName(funcPrefix, field);
        w.line("%s* %s;", entryType, fieldName);
        w.line("size_t %s_count;", fieldName);
      } else if (field.isRepeated()) {
        String elemType = resolveFieldType(field, pkg);
        if (field.getKind() == ProtoField.FieldKind.MESSAGE
            || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
          w.line("%s* %s;", elemType, fieldName);
        } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
          w.line("char** %s;", fieldName);
        } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
          w.line("uint8_t** %s;", fieldName);
          w.line("size_t* %s_lengths;", fieldName);
        } else {
          w.line("%s* %s;", typeMapper.scalarType(field.getProtoType()), fieldName);
        }
        w.line("size_t %s_count;", fieldName);
      } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
        String refType = nameResolver.resolveTypeReference(field.getTypeReference(), null);
        w.line("%s* %s;", refType, fieldName);
      } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
        String enumType = nameResolver.resolveTypeReference(field.getTypeReference(), null);
        w.line("%s %s;", enumType, fieldName);
      } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line("uint8_t* %s;", fieldName);
        w.line("size_t %s_len;", fieldName);
      } else {
        w.line("%s %s;", typeMapper.scalarType(field.getProtoType()), fieldName);
      }

      // Presence tracking for proto3 optional
      if (field.isProto3Optional()) {
        w.line("bool has_%s;", fieldName);
      }
    }

    // Oneof unions and case tracking
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String oneofField = nameResolver.fieldName(group.name());
      String unionType = funcPrefix + "_" + group.name() + "_union";
      String caseEnumType = funcPrefix.toUpperCase() + "_" + group.name().toUpperCase() + "_CASE";
      w.line("%s %s;", unionType, oneofField);
      w.line("%s %s_case;", caseEnumType, oneofField);
    }

    w.dedent();
    w.line("} %s;", typeName);
  }

  private void emitFreeFunction(
      CodeWriter w, ProtoMessage message, String funcPrefix, String typeName, String pkg) {
    w.blankLine();
    w.block(
        "void " + funcPrefix + "_free(" + typeName + "* msg)",
        () -> {
          w.line("if (!msg) return;");

          for (ProtoField field : message.getFields()) {
            if (field.isOneofMember()) {
              continue; // handled below with the oneof group
            }
            emitFieldFree(w, field, funcPrefix, pkg);
          }

          // Free oneof members based on active case
          for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
            emitOneofFree(w, group, funcPrefix, pkg);
          }

          w.line("free(msg);");
        });
  }

  private void emitFieldFree(CodeWriter w, ProtoField field, String funcPrefix, String pkg) {
    String fieldName = nameResolver.fieldName(field.getName());
    String accessor = "msg->" + fieldName;

    if (field.isMap()) {
      w.block(
          "if (" + accessor + ")",
          () -> {
            w.block(
                "for (size_t i = 0; i < msg->" + fieldName + "_count; i++)",
                () -> {
                  // Free string keys
                  if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
                    w.line("free(%s[i].key);", accessor);
                  }
                  // Free string/message values
                  if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
                    w.line("free(%s[i].value);", accessor);
                  } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
                    String refPrefix =
                        nameResolver.resolveTypeFunctionPrefix(field.getMapValueTypeReference());
                    w.line("%s_free(%s[i].value);", refPrefix, accessor);
                  }
                });
            w.line("free(%s);", accessor);
          });
    } else if (field.isRepeated()) {
      w.block(
          "if (" + accessor + ")",
          () -> {
            if (field.getKind() == ProtoField.FieldKind.MESSAGE
                || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
              String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
              w.block(
                  "for (size_t i = 0; i < msg->" + fieldName + "_count; i++)",
                  () -> {
                    w.line("%s_free(%s[i]);", refPrefix, accessor);
                  });
            } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
              w.block(
                  "for (size_t i = 0; i < msg->" + fieldName + "_count; i++)",
                  () -> {
                    w.line("free(%s[i]);", accessor);
                  });
            } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
              w.block(
                  "for (size_t i = 0; i < msg->" + fieldName + "_count; i++)",
                  () -> {
                    w.line("free(%s[i]);", accessor);
                  });
              w.line("free(msg->%s_lengths);", fieldName);
            }
            w.line("free(%s);", accessor);
          });
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
      w.line("%s_free(%s);", refPrefix, accessor);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("free(%s);", accessor);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("free(%s);", accessor);
    }
  }

  private void emitOneofFree(
      CodeWriter w, ProtoMessage.OneofGroup group, String funcPrefix, String pkg) {
    String oneofField = nameResolver.fieldName(group.name());
    String caseField = "msg->" + oneofField + "_case";

    boolean hasFreeable =
        group.members().stream()
            .anyMatch(
                m ->
                    m.getKind() == ProtoField.FieldKind.MESSAGE
                        || m.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE
                        || m.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING
                        || m.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES);

    if (!hasFreeable) return;

    w.block(
        "switch (" + caseField + ")",
        () -> {
          for (ProtoField member : group.members()) {
            String memberName = nameResolver.fieldName(member.getName());
            String caseConst =
                funcPrefix.toUpperCase()
                    + "_"
                    + group.name().toUpperCase()
                    + "_"
                    + member.getName().toUpperCase();
            String unionAccess = "msg->" + oneofField + "." + memberName;

            if (member.getKind() == ProtoField.FieldKind.MESSAGE
                || member.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
              String refPrefix = nameResolver.resolveTypeFunctionPrefix(member.getTypeReference());
              w.line("case %s: %s_free(%s); break;", caseConst, refPrefix, unionAccess);
            } else if (member.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
              w.line("case %s: free(%s); break;", caseConst, unionAccess);
            } else if (member.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
              w.line("case %s: free(%s); break;", caseConst, unionAccess);
            }
          }
          w.line("default: break;");
        });
  }

  private void emitNestedMessageHeader(
      CodeWriter w,
      ProtoMessage nested,
      ProtoFile file,
      String typeName,
      String funcPrefix,
      String pkg,
      String parentPrefix) {
    // Nested enums
    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnumDef(w, protoEnum, pkg, parentPrefix + nested.getName() + "_");
    }

    // Map entry typedefs
    emitMapEntryTypedefs(w, nested, funcPrefix);

    // Oneof types
    emitOneofTypes(w, nested, funcPrefix, pkg);

    // Struct definition
    emitStructDef(w, nested, typeName, funcPrefix, pkg);

    // Function declarations
    w.blankLine();
    serializerGen.generateDeclarations(w, funcPrefix, typeName);
    deserializerGen.generateDeclarations(w, funcPrefix, typeName);
    w.line("void %s_free(%s* msg);", funcPrefix, typeName);

    // Recursively handle deeper nesting
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      String deepTypeName =
          nameResolver.qualifiedTypeName(
              pkg, parentPrefix + nested.getName() + "_" + deepNested.getName());
      String deepFuncPrefix =
          nameResolver.functionPrefix(
              pkg, parentPrefix + nested.getName() + "_" + deepNested.getName());
      w.blankLine();
      emitNestedMessageHeader(
          w,
          deepNested,
          file,
          deepTypeName,
          deepFuncPrefix,
          pkg,
          parentPrefix + nested.getName() + "_");
    }
  }

  private void emitNestedMessageSource(
      CodeWriter w,
      ProtoMessage nested,
      ProtoFile file,
      String typeName,
      String funcPrefix,
      String pkg,
      String parentPrefix) {
    // Recursively emit deeper nested messages first
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      String deepTypeName =
          nameResolver.qualifiedTypeName(
              pkg, parentPrefix + nested.getName() + "_" + deepNested.getName());
      String deepFuncPrefix =
          nameResolver.functionPrefix(
              pkg, parentPrefix + nested.getName() + "_" + deepNested.getName());
      emitNestedMessageSource(
          w,
          deepNested,
          file,
          deepTypeName,
          deepFuncPrefix,
          pkg,
          parentPrefix + nested.getName() + "_");
    }

    serializerGen.generate(w, nested, funcPrefix, typeName);
    deserializerGen.generate(w, nested, funcPrefix, typeName);
    emitFreeFunction(w, nested, funcPrefix, typeName, pkg);
  }

  /** Resolve a field type to its C type (for struct fields). */
  private String resolveFieldType(ProtoField field, String pkg) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null) + "*";
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return nameResolver.resolveTypeReference(field.getTypeReference(), null);
    }
    return typeMapper.scalarType(field.getProtoType());
  }
}
