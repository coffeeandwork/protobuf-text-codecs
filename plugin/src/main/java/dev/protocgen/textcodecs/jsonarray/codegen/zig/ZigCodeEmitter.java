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
package dev.protocgen.textcodecs.jsonarray.codegen.zig;

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
 * Generates complete Zig source files for proto messages and enums. Each message becomes a Zig
 * struct with pub fields and serialize/deserialize methods.
 */
public class ZigCodeEmitter {

  private final ZigTypeMapper typeMapper;
  private final ZigNameResolver nameResolver;
  private final ZigSerializerGenerator serializerGen;
  private final ZigDeserializerGenerator deserializerGen;

  public ZigCodeEmitter(ZigTypeMapper typeMapper, ZigNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.serializerGen = new ZigSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new ZigDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete Zig source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter();
    String structName = nameResolver.messageClassName(message.getName());

    emitImports(w);

    // Import referenced types from other modules
    emitTypeImports(w, message, file);

    w.blankLine();

    // Nested enums as top-level Zig types (Zig doesn't have true nesting for enums
    // used as field types, so we declare them before the struct)
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
      w.blankLine();
    }

    // Nested messages as top-level structs
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitNestedStruct(w, nested, file);
      w.blankLine();
    }

    // Oneof tagged unions
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      emitOneofUnion(w, group);
      w.blankLine();
    }

    // Main struct: use constBlock for `pub const X = struct { ... };`
    constBlock(
        w,
        "pub const " + structName + " = struct",
        () -> {
          // Fields
          emitFields(w, message);

          // serialize
          serializerGen.generate(w, message, structName);

          // deserialize
          deserializerGen.generate(w, message, structName);

          // deinit
          emitDeinit(w, message, structName);
        });

    return w.toString();
  }

  /** Generate a complete Zig source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();
    emitImports(w);
    w.blankLine();
    emitEnum(w, protoEnum);
    return w.toString();
  }

  private void emitImports(CodeWriter w) {
    w.line("const std = @import(\"std\");");
    w.line("const json = std.json;");
  }

  /** Emit const declarations for message/enum types referenced from other proto files. */
  private void emitTypeImports(CodeWriter w, ProtoMessage message, ProtoFile file) {
    Set<String> imports = new LinkedHashSet<>();
    collectReferencedTypes(message, file, imports);
    for (String imp : imports) {
      w.line(imp);
    }
  }

  private void collectReferencedTypes(ProtoMessage message, ProtoFile file, Set<String> imports) {
    String currentPrefix =
        file.getProtoPackage().isEmpty() ? "." : "." + file.getProtoPackage() + ".";

    for (ProtoField field : message.getFields()) {
      addTypeImportIfNeeded(
          field.getTypeReference(), field.isWellKnownType(), message, currentPrefix, imports);

      // For map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        addTypeImportIfNeeded(
            field.getMapValueTypeReference(), false, message, currentPrefix, imports);
      }
    }

    // Also check nested messages for their references
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectReferencedTypes(nested, file, imports);
    }
  }

  private void addTypeImportIfNeeded(
      String typeRef,
      boolean isWellKnown,
      ProtoMessage message,
      String currentPrefix,
      Set<String> imports) {
    if (typeRef == null) return;
    if (isWellKnown) return;

    // Check if the type is defined in the current message (nested type)
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (typeRef.equals(message.getFullName() + "." + nested.getName())) {
        return;
      }
    }
    for (ProtoEnum nestedEnum : message.getEnums()) {
      if (typeRef.equals(message.getFullName() + "." + nestedEnum.getName())) {
        return;
      }
    }

    // Check if this is a type in the same package but different file
    if (typeRef.startsWith(currentPrefix)) {
      String simpleName = ProtoTypeUtil.simpleTypeName(typeRef);
      String moduleName = ZigNameResolver.toSnakeCase(simpleName);
      imports.add(
          "const " + simpleName + " = @import(\"" + moduleName + ".zig\")." + simpleName + ";");
    }
  }

  private void emitFields(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.isOneofMember()) {
        // Oneof members are part of the tagged union, not direct fields.
        // We emit the union field once per oneof group.
        continue;
      }
      String zigType = typeMapper.languageType(field);
      String zigName = nameResolver.fieldName(field.getName());
      String defaultVal = structFieldDefault(field);
      w.line("%s: %s = %s,", zigName, zigType, defaultVal);
    }

    // Emit one field per oneof group (the tagged union)
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String unionTypeName = pascalCase(group.name());
      String fieldName = nameResolver.fieldName(group.name());
      w.line("%s: ?%s = null,", fieldName, unionTypeName);
    }
  }

  /**
   * Return the default value for a struct field declaration. Unlike runtime defaults (used in the
   * deserializer), struct defaults must be comptime-known. Collections require an allocator at
   * init, so we use `undefined` for those.
   */
  private String structFieldDefault(ProtoField field) {
    if (field.isMap() || field.isRepeated()) {
      return "undefined";
    }
    return typeMapper.defaultValue(field);
  }

  void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    String enumName = nameResolver.messageClassName(protoEnum.getName());

    constBlock(
        w,
        "pub const " + enumName + " = enum(i32)",
        () -> {
          for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
            String constName = nameResolver.enumConstantName(val.name());
            w.line("%s = %d,", constName, val.number());
          }

          // fromInt helper
          w.blankLine();
          w.block(
              "pub fn fromInt(value: i32) " + enumName,
              () -> {
                w.line("return @enumFromInt(value);");
              });

          // toInt helper
          w.blankLine();
          w.block(
              "pub fn toInt(self: " + enumName + ") i32",
              () -> {
                w.line("return @intFromEnum(self);");
              });
        });
  }

  private void emitOneofUnion(CodeWriter w, ProtoMessage.OneofGroup group) {
    String unionName = pascalCase(group.name());
    constBlock(
        w,
        "pub const " + unionName + " = union(enum)",
        () -> {
          for (ProtoField member : group.members()) {
            String tagName = nameResolver.fieldName(member.getName());
            String tagType = oneofMemberType(member);
            w.line("%s: %s,", tagName, tagType);
          }
        });
  }

  private String oneofMemberType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return ZigNameResolver.simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return ZigNameResolver.simpleTypeName(field.getTypeReference());
    }
    return typeMapper.scalarType(field.getProtoType());
  }

  private void emitNestedStruct(CodeWriter w, ProtoMessage nested, ProtoFile file) {
    String structName = nameResolver.messageClassName(nested.getName());

    // Nested enums
    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnum(w, protoEnum);
      w.blankLine();
    }

    // Recursively emit deeply-nested messages
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      emitNestedStruct(w, deepNested, file);
      w.blankLine();
    }

    // Oneof unions for the nested message
    for (ProtoMessage.OneofGroup group : nested.getOneofGroups()) {
      emitOneofUnion(w, group);
      w.blankLine();
    }

    constBlock(
        w,
        "pub const " + structName + " = struct",
        () -> {
          emitFields(w, nested);
          serializerGen.generate(w, nested, structName);
          deserializerGen.generate(w, nested, structName);
          emitDeinit(w, nested, structName);
        });
  }

  private void emitDeinit(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "pub fn deinit(self: *" + structName + ", allocator: std.mem.Allocator) void",
        () -> {
          boolean hasCleanup = false;
          for (ProtoField field : message.getFields()) {
            if (field.isOneofMember()) continue;
            String zigName = "self." + nameResolver.fieldName(field.getName());

            if (field.isMap()) {
              // Free allocator-owned string keys
              if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
                w.block(
                    "var key_it = " + zigName + ".keyIterator(); while (key_it.next()) |key|",
                    () -> {
                      w.line("allocator.free(key.*);");
                    });
              }
              // Free allocator-owned string values
              if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
                w.block(
                    "var val_it = " + zigName + ".valueIterator(); while (val_it.next()) |val|",
                    () -> {
                      w.line("allocator.free(val.*);");
                    });
              }
              w.line("%s.deinit();", zigName);
              hasCleanup = true;
            } else if (field.isRepeated()) {
              // Free allocator-owned elements in the list
              if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
                w.block(
                    "for (" + zigName + ".items) |item|",
                    () -> {
                      w.line("allocator.free(item);");
                    });
              } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
                  || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                w.block(
                    "for (" + zigName + ".items) |*item|",
                    () -> {
                      w.line("item.deinit(allocator);");
                    });
              } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                w.block(
                    "for (" + zigName + ".items) |item|",
                    () -> {
                      w.line("allocator.free(item);");
                    });
              }
              w.line("%s.deinit();", zigName);
              hasCleanup = true;
            } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
                || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
              w.block(
                  "if (" + zigName + ") |*val|",
                  () -> {
                    w.line("val.deinit(allocator);");
                  });
              hasCleanup = true;
            } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
              // Free allocator-owned string slice; skip if it is the default empty literal
              w.block(
                  "if (" + zigName + ".len > 0)",
                  () -> {
                    w.line("allocator.free(%s);", zigName);
                  });
              hasCleanup = true;
            } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
              // Free allocator-owned bytes slice; skip if it is the default empty literal
              w.block(
                  "if (" + zigName + ".len > 0)",
                  () -> {
                    w.line("allocator.free(%s);", zigName);
                  });
              hasCleanup = true;
            }
          }

          // Deinit oneof fields
          for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
            String unionField = "self." + nameResolver.fieldName(group.name());
            w.block(
                "if (" + unionField + ") |*oneof_val|",
                () -> {
                  w.block(
                      "switch (oneof_val.*)",
                      () -> {
                        for (ProtoField member : group.members()) {
                          String tagName = nameResolver.fieldName(member.getName());
                          if (member.getKind() == ProtoField.FieldKind.MESSAGE
                              || member.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                            w.line(".%s => |*msg| msg.deinit(allocator),", tagName);
                          } else if (member.getProtoType()
                              == FieldDescriptorProto.Type.TYPE_STRING) {
                            w.line(".%s => |str| allocator.free(str),", tagName);
                          } else {
                            w.line(".%s => {},", tagName);
                          }
                        }
                      });
                });
            hasCleanup = true;
          }

          if (!hasCleanup) {
            w.line("_ = allocator;");
          }
        });
  }

  /**
   * Emit a `pub const X = struct/enum/union { ... };` block. Zig requires a trailing semicolon
   * after the closing brace of const declarations, which differs from function/control-flow blocks.
   * This method handles that by manually writing the header, running the body indented, and closing
   * with `};`.
   */
  private void constBlock(CodeWriter w, String header, Runnable body) {
    w.line(header + " {");
    w.indent();
    body.run();
    w.dedent();
    w.line("};");
  }

  /** Convert snake_case to PascalCase for type names. */
  static String pascalCase(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = true;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_') {
        nextUpper = true;
      } else if (nextUpper) {
        sb.append(Character.toUpperCase(c));
        nextUpper = false;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
