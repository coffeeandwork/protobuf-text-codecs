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
package dev.protocgen.textcodecs.jsonarray.codegen.rust;

import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates complete Rust source files for proto messages and enums. Produces structs with derive
 * macros, impl blocks with serialize/deserialize, and Rust-idiomatic enums.
 */
public class RustCodeEmitter {

  private final RustTypeMapper typeMapper;
  private final RustNameResolver nameResolver;
  private final RustSerializerGenerator serializerGen;
  private final RustDeserializerGenerator deserializerGen;

  public RustCodeEmitter(RustTypeMapper typeMapper, RustNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.serializerGen = new RustSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new RustDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete Rust source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter();

    // Imports
    emitImports(w, message);

    // Import referenced types from other modules
    emitTypeImports(w, message, file);

    String structName = nameResolver.messageClassName(message.getName());

    // Nested enums (defined before the struct so they can be referenced)
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
    }

    // Nested messages (defined before the parent struct)
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.blankLine();
      emitNestedMessage(w, nested, file);
    }

    // Struct definition
    w.line("#[derive(Debug, Clone, Default)]");
    w.block(
        "pub struct " + structName,
        () -> {
          emitFields(w, message);

          // Oneof case tracking fields
          emitOneofCaseFields(w, message);
        });

    // Impl block with serialize, deserialize, and convenience methods
    w.blankLine();
    w.block(
        "impl " + structName,
        () -> {
          // Constructor
          w.block(
              "pub fn new() -> Self",
              () -> {
                w.line("Self::default()");
              });

          // Getters and setters
          emitGettersSetters(w, message);

          // Serialize method
          serializerGen.generate(w, message);

          // Deserialize method
          deserializerGen.generate(w, message, structName);
        });

    // Display impl
    emitDisplay(w, message, structName);

    return w.toString();
  }

  /** Generate a complete Rust source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();

    // Minimal imports for enum files
    w.line("use serde_json::{Value, json};");
    w.blankLine();

    emitEnum(w, protoEnum);
    return w.toString();
  }

  private void emitImports(CodeWriter w, ProtoMessage message) {
    w.line("use serde_json::{Value, json};");
    // Check if any field uses bytes type
    if (hasFieldOfType(
        message, com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES)) {
      w.line("use base64::{Engine as _, engine::general_purpose};");
    }
    if (hasMapFields(message)) {
      w.line("use std::collections::HashMap;");
    }
    w.blankLine();
  }

  /** Emit use statements for message/enum types referenced from other proto files. */
  private void emitTypeImports(CodeWriter w, ProtoMessage message, ProtoFile file) {
    Set<String> imports = new LinkedHashSet<>();
    collectReferencedTypes(message, file, imports);
    for (String imp : imports) {
      w.line(imp);
    }
    if (!imports.isEmpty()) {
      w.blankLine();
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
      String moduleName = RustNameResolver.toSnakeCase(simpleName);
      imports.add("use super::" + moduleName + "::" + simpleName + ";");
    }
  }

  private void emitFields(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String rustType = typeMapper.languageType(field);
      String rustName = nameResolver.fieldName(field.getName());
      w.line("pub %s: %s,", rustName, rustType);
    }
  }

  private void emitOneofCaseFields(CodeWriter w, ProtoMessage message) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = nameResolver.fieldName(group.name()) + "_case";
      w.line("pub %s: i32, // 0 = not set", caseName);
    }
  }

  private void emitGettersSetters(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String rustType = typeMapper.languageType(field);
      String rustName = nameResolver.fieldName(field.getName());
      String getterName = nameResolver.getterName(field.getName());
      String setterName = nameResolver.setterName(field.getName());

      // Getter - return reference for non-Copy types
      w.blankLine();
      if (isRustCopyType(field)) {
        w.block(
            "pub fn " + getterName + "(&self) -> " + rustType,
            () -> {
              w.line("self.%s", rustName);
            });
      } else {
        // Use &str instead of &String for idiomatic Rust
        String refType = rustType.equals("String") ? "str" : rustType;
        w.block(
            "pub fn " + getterName + "(&self) -> &" + refType,
            () -> {
              w.line("&self.%s", rustName);
            });
      }

      // Setter
      w.blankLine();
      w.block(
          "pub fn " + setterName + "(&mut self, value: " + rustType + ")",
          () -> {
            w.line("self.%s = value;", rustName);
            if (field.isOneofMember()) {
              String caseField = nameResolver.fieldName(field.getOneofName()) + "_case";
              w.line("self.%s = %d;", caseField, field.getFieldNumber());
            }
          });

      // has_* method for optional and message fields
      if (field.isProto3Optional() || field.getKind() == ProtoField.FieldKind.MESSAGE) {
        w.blankLine();
        String hasName = "has_" + nameResolver.fieldName(field.getName());
        w.block(
            "pub fn " + hasName + "(&self) -> bool",
            () -> {
              w.line("self.%s.is_some()", rustName);
            });
      }
    }
  }

  private void emitNestedMessage(CodeWriter w, ProtoMessage nested, ProtoFile file) {
    String structName = nameResolver.messageClassName(nested.getName());

    // Nested enums
    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnum(w, protoEnum);
    }

    // Deeper nested messages
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      w.blankLine();
      emitNestedMessage(w, deepNested, file);
    }

    // Struct
    w.line("#[derive(Debug, Clone, Default)]");
    w.block(
        "pub struct " + structName,
        () -> {
          emitFields(w, nested);
          emitOneofCaseFields(w, nested);
        });

    // Impl block
    w.blankLine();
    w.block(
        "impl " + structName,
        () -> {
          w.block(
              "pub fn new() -> Self",
              () -> {
                w.line("Self::default()");
              });

          emitGettersSetters(w, nested);
          serializerGen.generate(w, nested);
          deserializerGen.generate(w, nested, structName);
        });

    emitDisplay(w, nested, structName);
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.blankLine();
    w.line("#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]");
    w.line("#[repr(i32)]");
    w.block(
        "pub enum " + protoEnum.getName(),
        () -> {
          for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
            String rustName = nameResolver.enumConstantName(val.name());
            w.line("%s = %d,", rustName, val.number());
          }
        });

    // Default impl (first value)
    w.blankLine();
    w.block(
        "impl Default for " + protoEnum.getName(),
        () -> {
          w.block(
              "fn default() -> Self",
              () -> {
                if (!protoEnum.getValues().isEmpty()) {
                  String firstName =
                      nameResolver.enumConstantName(protoEnum.getValues().get(0).name());
                  w.line("%s::%s", protoEnum.getName(), firstName);
                } else {
                  // Should not happen, but handle gracefully
                  w.line("unsafe { std::mem::zeroed() }");
                }
              });
        });

    // From<i32> impl
    w.blankLine();
    w.block(
        "impl From<i32> for " + protoEnum.getName(),
        () -> {
          w.block(
              "fn from(value: i32) -> Self",
              () -> {
                w.block(
                    "match value",
                    () -> {
                      for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
                        String rustName = nameResolver.enumConstantName(val.name());
                        w.line("%d => %s::%s,", val.number(), protoEnum.getName(), rustName);
                      }
                      if (!protoEnum.getValues().isEmpty()) {
                        String firstName =
                            nameResolver.enumConstantName(protoEnum.getValues().get(0).name());
                        w.line("_ => %s::%s,", protoEnum.getName(), firstName);
                      }
                    });
              });
        });
  }

  private void emitDisplay(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "impl std::fmt::Display for " + structName,
        () -> {
          w.block(
              "fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result",
              () -> {
                if (message.getFields().isEmpty()) {
                  w.line("write!(f, \"%s{{}}\")", structName);
                  return;
                }
                // Build the format string and arguments for write!
                StringBuilder formatParts = new StringBuilder();
                StringBuilder argParts = new StringBuilder();
                for (int i = 0; i < message.getFields().size(); i++) {
                  ProtoField field = message.getFields().get(i);
                  String rustName = nameResolver.fieldName(field.getName());
                  if (i > 0) {
                    formatParts.append(", ");
                  }
                  formatParts.append(field.getName()).append(": {:?}");
                  argParts.append(", self.").append(rustName);
                }
                w.line(
                    "write!(f, \"%s{{%s}}\"%s)",
                    structName, formatParts.toString(), argParts.toString());
              });
        });
  }

  /** Check if a field's Rust type is Copy (primitives). */
  private boolean isRustCopyType(ProtoField field) {
    if (field.isMap() || field.isRepeated()) return false;
    if (field.isProto3Optional()) return false;
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) return false;
    if (field.getKind() == ProtoField.FieldKind.ENUM) return true;
    // Scalar types: check if Copy
    return switch (field.getProtoType()) {
      case TYPE_DOUBLE,
          TYPE_FLOAT,
          TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32,
          TYPE_BOOL ->
          true;
      default -> false; // String, bytes are not Copy
    };
  }

  private boolean hasFieldOfType(
      ProtoMessage message, com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type type) {
    for (ProtoField field : message.getFields()) {
      if (field.getProtoType() == type) return true;
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (hasFieldOfType(nested, type)) return true;
    }
    return false;
  }

  private boolean hasMapFields(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.isMap()) return true;
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (hasMapFields(nested)) return true;
    }
    return false;
  }
}
