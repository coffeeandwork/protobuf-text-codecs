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
package dev.protocgen.textcodecs.jsonarray.codegen.perl;

import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.LinkedHashSet;
import java.util.Set;

/** Generates complete Perl module (.pm) source files for proto messages and enums. */
public class PerlCodeEmitter {

  private final PerlTypeMapper typeMapper;
  private final PerlNameResolver nameResolver;
  private final PerlSerializerGenerator serializerGen;
  private final PerlDeserializerGenerator deserializerGen;
  private final TypeRegistry typeRegistry;

  public PerlCodeEmitter(
      PerlTypeMapper typeMapper, PerlNameResolver nameResolver, TypeRegistry typeRegistry) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
    this.typeRegistry = typeRegistry;
    this.serializerGen = new PerlSerializerGenerator(typeMapper, nameResolver);
    this.deserializerGen = new PerlDeserializerGenerator(typeMapper, nameResolver);
  }

  /** Generate a complete Perl module source file for a message. */
  public String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter();

    String perlPackage = buildPackageName(file, message.getName());

    // Package declaration and imports
    emitPackageHeader(w, perlPackage);

    // Collect cross-file imports (emitted lazily inside methods to avoid circular imports)
    Set<String> lazyImports = new LinkedHashSet<>();
    collectReferencedTypes(message, file, lazyImports);

    // Nested enums as constant subs
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
    }

    // Nested message packages
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitNestedMessage(w, nested, file, perlPackage, lazyImports);
    }

    // Constructor
    emitNew(w, message);

    // Accessors
    emitAccessors(w, message);

    // has_* methods for optional and message fields
    emitHasMethods(w, message);

    // Serialize
    serializerGen.generate(w, message, lazyImports);

    // Deserialize
    String className = nameResolver.messageClassName(message.getName());
    deserializerGen.generate(w, message, className, lazyImports);

    // Module end
    w.blankLine();
    w.line("1;");

    return w.toString();
  }

  /** Generate a complete Perl module source file for a top-level enum. */
  public String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();
    String perlPackage = buildPackageName(file, protoEnum.getName());
    emitPackageHeader(w, perlPackage);
    emitEnum(w, protoEnum);
    w.blankLine();
    w.line("1;");
    return w.toString();
  }

  private void emitPackageHeader(CodeWriter w, String perlPackage) {
    w.line("package %s;", perlPackage);
    w.blankLine();
    w.line("use strict;");
    w.line("use warnings;");
    w.line("use JSON qw(encode_json decode_json);");
    w.line("use MIME::Base64 qw(encode_base64 decode_base64);");
  }

  /**
   * Collect cross-file import statements for types referenced by this message. These are emitted as
   * lazy imports (require) inside method bodies to avoid circular import issues between
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

      // Extract the simple name
      String simpleName = typeRef.substring(typeRef.lastIndexOf('.') + 1);

      // Check if this is a type in the same package but different file
      if (typeRef.startsWith(currentPrefix)) {
        String perlPkg = nameResolver.resolveFullPackageName(typeRef, file);
        String modulePath = perlPkg.replace("::", "/") + ".pm";
        imports.add("require " + perlPkg + ";");
      }

      // For map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        String valRef = field.getMapValueTypeReference();

        // Skip synthetic map-entry types for map values too
        if (typeRegistry != null && typeRegistry.isMapEntry(valRef)) continue;

        if (valRef.startsWith(currentPrefix)) {
          String perlPkg = nameResolver.resolveFullPackageName(valRef, file);
          imports.add("require " + perlPkg + ";");
        }
      }
    }

    // Also check nested messages for their references
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectReferencedTypes(nested, file, imports);
    }
  }

  private void emitNew(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.line("sub new {");
    w.indent();
    w.line("my ($class, %%args) = @_;");
    w.line("my $self = bless {}, $class;");

    // Field initializations
    for (ProtoField field : message.getFields()) {
      String plName = nameResolver.fieldName(field.getName());
      String defaultVal = typeMapper.defaultValue(field);
      w.line("$self->{%s} = %s;", plName, defaultVal);
    }

    // Presence tracking for proto3 optional
    if (hasOptionalFields(message)) {
      int maxPos = message.getMaxFieldNumber();
      w.line("$self->{_present_fields} = [(0) x %d];", maxPos);
    }

    // Oneof case tracking
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = "_" + nameResolver.fieldName(group.name()) + "_case";
      w.line("$self->{%s} = 0;  # 0 = not set", caseName);
    }

    w.line("return $self;");
    w.dedent();
    w.line("}");
  }

  private void emitAccessors(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String plName = nameResolver.fieldName(field.getName());

      // Combined getter/setter
      w.blankLine();
      w.line("sub %s {", plName);
      w.indent();
      w.line("my ($self, $value) = @_;");
      w.line("if (defined $value) {");
      w.indent();
      w.line("$self->{%s} = $value;", plName);

      if (field.isProto3Optional()) {
        w.line("$self->{_present_fields}[%d] = 1;", field.getArrayPosition());
      }

      if (field.isOneofMember()) {
        String caseName = "_" + nameResolver.fieldName(field.getOneofName()) + "_case";
        w.line("$self->{%s} = %d;", caseName, field.getFieldNumber());
      }

      w.dedent();
      w.line("}");
      w.line("return $self->{%s};", plName);
      w.dedent();
      w.line("}");
    }
  }

  private void emitHasMethods(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.isProto3Optional() || field.getKind() == ProtoField.FieldKind.MESSAGE) {
        String plName = nameResolver.fieldName(field.getName());
        w.blankLine();
        w.line("sub has_%s {", plName);
        w.indent();
        w.line("my ($self) = @_;");
        if (field.isProto3Optional()) {
          w.line("return $self->{_present_fields}[%d];", field.getArrayPosition());
        } else {
          w.line("return defined($self->{%s});", plName);
        }
        w.dedent();
        w.line("}");
      }
    }
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.blankLine();
    w.line("# Enum: %s", protoEnum.getName());
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("use constant %s => %d;", nameResolver.enumConstantName(val.name()), val.number());
    }

    // for_number lookup
    w.blankLine();
    w.line("my %%_%s_BY_NUMBER = (", protoEnum.getName());
    w.indent();
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%d => %d,", val.number(), val.number());
    }
    w.dedent();
    w.line(");");
    w.blankLine();
    w.line("sub %s_for_number {", protoEnum.getName());
    w.indent();
    w.line("my ($class, $number) = @_;");
    w.line("return $_%s_BY_NUMBER{$number};", protoEnum.getName());
    w.dedent();
    w.line("}");
  }

  private void emitNestedMessage(
      CodeWriter w,
      ProtoMessage nested,
      ProtoFile file,
      String parentPackage,
      Set<String> lazyImports) {
    String nestedPackage = parentPackage + "::" + nested.getName();

    w.blankLine();
    w.line("# Nested message: %s", nested.getName());

    // Open a new package block for the nested message
    w.line("{");
    w.indent();
    w.line("package %s;", nestedPackage);
    w.blankLine();
    w.line("use strict;");
    w.line("use warnings;");
    w.line("use JSON qw(encode_json decode_json);");
    w.line("use MIME::Base64 qw(encode_base64 decode_base64);");

    // Nested enums
    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnum(w, protoEnum);
    }

    // Recursively emit nested messages
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      emitNestedMessage(w, deepNested, file, nestedPackage, lazyImports);
    }

    // Constructor
    emitNew(w, nested);

    // Accessors
    emitAccessors(w, nested);

    // has_* methods
    emitHasMethods(w, nested);

    // Serialize / Deserialize
    serializerGen.generate(w, nested, lazyImports);
    String nestedClassName = nameResolver.messageClassName(nested.getName());
    deserializerGen.generate(w, nested, nestedClassName, lazyImports);

    w.dedent();
    w.line("}");
  }

  private String buildPackageName(ProtoFile file, String messageName) {
    String pkg = nameResolver.resolvePackage(file);
    if (pkg.isEmpty()) {
      return messageName;
    }
    return pkg + "::" + messageName;
  }

  private boolean hasOptionalFields(ProtoMessage message) {
    return message.getFields().stream().anyMatch(ProtoField::isProto3Optional);
  }
}
