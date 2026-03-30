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
package dev.protocgen.textcodecs.pbtkurl.codegen.perl;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.perl.PerlNameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.perl.PerlTypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Perl language code generator for pbtk URL encoding. Produces Perl module (.pm) files with
 * to_pbtk_url()/from_pbtk_url() serialization methods.
 *
 * <p>The pbtk URL format encodes protobuf messages as URL strings using the syntax: {@code
 * !<fieldNumber><typeChar><value>} where type chars are: b=bool(0/1), i=integer, f=float, d=double,
 * s=string(URL-encoded), e=enum(int), m=message(count of sub-fields), z=bytes(base64).
 */
public class PbtkPerlGenerator implements LanguageGenerator {

  private final PerlNameResolver nameResolver = new PerlNameResolver();
  private final PerlTypeMapper typeMapper = new PerlTypeMapper();

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();

    for (ProtoMessage message : file.getMessages()) {
      nameResolver.validateFieldNames(message.getFields());

      Set<String> lazyImports = new LinkedHashSet<>();
      collectReferencedTypes(message, file, lazyImports, registry);

      String sourceCode = emitMessage(message, file, lazyImports);
      String outputPath = nameResolver.outputFilePath(file, message.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());
    }

    for (ProtoEnum protoEnum : file.getEnums()) {
      String sourceCode = emitTopLevelEnum(protoEnum, file);
      String outputPath = nameResolver.outputFilePath(file, protoEnum.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());
    }

    return result;
  }

  @Override
  public String languageId() {
    return "perl";
  }

  // ---------------------------------------------------------------------------
  // Top-level emission
  // ---------------------------------------------------------------------------

  private String emitMessage(ProtoMessage message, ProtoFile file, Set<String> lazyImports) {
    CodeWriter w = new CodeWriter();

    String perlPackage = buildPackageName(file, message.getName());

    // Package declaration and imports
    emitImports(w, perlPackage);

    // Nested enums as constant subs
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum);
    }

    // Nested message packages
    for (ProtoMessage nested : message.getNestedMessages()) {
      emitNestedMessage(w, nested, file, perlPackage, lazyImports);
    }

    emitNew(w, message);
    emitAccessors(w, message);
    emitHasMethods(w, message);
    emitSerializer(w, message);
    emitDeserializer(w, message, lazyImports);
    emitRepr(w, message);

    w.blankLine();
    w.line("1;");

    return w.toString();
  }

  private String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();
    String perlPackage = buildPackageName(file, protoEnum.getName());
    emitImports(w, perlPackage);
    emitEnum(w, protoEnum);
    w.blankLine();
    w.line("1;");
    return w.toString();
  }

  private void emitImports(CodeWriter w, String perlPackage) {
    w.line("package %s;", perlPackage);
    w.blankLine();
    w.line("use strict;");
    w.line("use warnings;");
    w.line("use MIME::Base64 qw(encode_base64 decode_base64);");
    w.line("use URI::Escape qw(uri_escape_utf8 uri_unescape);");
  }

  // ---------------------------------------------------------------------------
  // Nested messages and enums
  // ---------------------------------------------------------------------------

  private void emitNestedMessage(
      CodeWriter w,
      ProtoMessage nested,
      ProtoFile file,
      String parentPackage,
      Set<String> lazyImports) {
    String nestedPackage = parentPackage + "::" + nested.getName();

    w.blankLine();
    w.line("# Nested message: %s", nested.getName());
    w.line("{");
    w.indent();
    w.line("package %s;", nestedPackage);
    w.blankLine();
    w.line("use strict;");
    w.line("use warnings;");
    w.line("use MIME::Base64 qw(encode_base64 decode_base64);");
    w.line("use URI::Escape qw(uri_escape_utf8 uri_unescape);");

    for (ProtoEnum protoEnum : nested.getEnums()) {
      emitEnum(w, protoEnum);
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      emitNestedMessage(w, deepNested, file, nestedPackage, lazyImports);
    }

    emitNew(w, nested);
    emitAccessors(w, nested);
    emitHasMethods(w, nested);
    emitSerializer(w, nested);
    emitDeserializer(w, nested, lazyImports);
    emitRepr(w, nested);

    w.dedent();
    w.line("}");
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.blankLine();
    w.line("# Enum: %s", protoEnum.getName());
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("use constant %s => %d;", nameResolver.enumConstantName(val.name()), val.number());
    }

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

  // ---------------------------------------------------------------------------
  // new, accessors, has_*, repr
  // ---------------------------------------------------------------------------

  private void emitNew(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.line("sub new {");
    w.indent();
    w.line("my ($class, %%args) = @_;");
    w.line("my $self = bless {}, $class;");

    for (ProtoField field : message.getFields()) {
      String plName = nameResolver.fieldName(field.getName());
      String defaultVal = typeMapper.defaultValue(field);
      w.line("$self->{%s} = %s;", plName, defaultVal);
    }

    if (hasOptionalFields(message)) {
      int maxPos = message.getMaxFieldNumber();
      w.line("$self->{_present_fields} = [(0) x %d];", maxPos);
    }

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

  private void emitRepr(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.line("sub to_string {");
    w.indent();
    w.line("my ($self) = @_;");
    if (message.getFields().isEmpty()) {
      w.line("return ref($self) . \"()\";");
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append("return ref($self) . \"(\"");
      for (int i = 0; i < message.getFields().size(); i++) {
        ProtoField field = message.getFields().get(i);
        String plName = nameResolver.fieldName(field.getName());
        if (i > 0) {
          sb.append(" . \", \"");
        }
        sb.append(" . \"").append(field.getName()).append("=\" . (defined($self->{").append(plName);
        sb.append("}) ? $self->{").append(plName).append("} : \"undef\")");
      }
      sb.append(" . \")\";");
      w.line(sb.toString());
    }
    w.dedent();
    w.line("}");
  }

  // ---------------------------------------------------------------------------
  // Serializer: to_pbtk_url()
  // ---------------------------------------------------------------------------

  private void emitSerializer(CodeWriter w, ProtoMessage message) {
    // _append_pbtk_fields: appends field tokens to an array ref
    w.blankLine();
    w.line("sub _append_pbtk_fields {");
    w.indent();
    w.line("my ($self, $parts) = @_;");
    if (message.getFields().isEmpty()) {
      w.line("# no fields");
    } else {
      for (ProtoField field : message.getFields()) {
        emitFieldSerialize(w, field);
      }
    }
    w.dedent();
    w.line("}");

    // _count_pbtk_fields: returns number of top-level tokens
    w.blankLine();
    w.line("sub _count_pbtk_fields {");
    w.indent();
    w.line("my ($self) = @_;");
    w.line("my $count = 0;");
    for (ProtoField field : message.getFields()) {
      emitFieldCount(w, field);
    }
    w.line("return $count;");
    w.dedent();
    w.line("}");

    // to_pbtk_url: public serialize method
    w.blankLine();
    w.line("sub to_pbtk_url {");
    w.indent();
    w.line("my ($self) = @_;");
    w.line("my @parts;");
    w.line("$self->_append_pbtk_fields(\\@parts);");
    w.line("return join(\"\", @parts);");
    w.dedent();
    w.line("}");
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String plField = "$self->{" + nameResolver.fieldName(field.getName()) + "}";
    int fieldNum = field.getFieldNumber();

    if (field.isOneofMember()) {
      String caseName = "$self->{_" + nameResolver.fieldName(field.getOneofName()) + "_case}";
      w.line("if (%s == %d) {", caseName, field.getFieldNumber());
      w.indent();
      emitSingleFieldSerialize(w, field, plField, fieldNum);
      w.dedent();
      w.line("}");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, plField, fieldNum);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, plField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, plField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, plField, fieldNum);
    } else {
      emitScalarSerialize(w, field, plField, fieldNum);
    }
  }

  private void emitSingleFieldSerialize(
      CodeWriter w, ProtoField field, String plField, int fieldNum) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, plField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, plField, fieldNum);
    } else {
      emitScalarSerialize(w, field, plField, fieldNum);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String plField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if ($self->{_present_fields}[%d]) {", field.getArrayPosition());
      w.indent();
      emitScalarAppend(w, field, plField, fieldNum);
      w.dedent();
      w.line("}");
      return;
    }
    emitScalarAppend(w, field, plField, fieldNum);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String plField, int fieldNum) {
    FieldDescriptorProto.Type type = field.getProtoType();
    String typeChar = pbtkTypeChar(type);

    switch (type) {
      case TYPE_BOOL:
        w.line("push @{$parts}, \"!%d%s\" . (%s ? \"1\" : \"0\");", fieldNum, typeChar, plField);
        break;
      case TYPE_BYTES:
        w.line("push @{$parts}, \"!%d%s\" . encode_base64(%s, \"\");", fieldNum, typeChar, plField);
        break;
      case TYPE_STRING:
        w.line("push @{$parts}, \"!%d%s\" . uri_escape_utf8(%s);", fieldNum, typeChar, plField);
        break;
      case TYPE_DOUBLE:
        w.line(
            "if (%s == %s && %s != 9**9**9 && %s != -9**9**9) {",
            plField, plField, plField, plField);
        w.indent();
        w.line("push @{$parts}, \"!%d%s\" . %s;", fieldNum, typeChar, plField);
        w.dedent();
        w.line("}");
        break;
      case TYPE_FLOAT:
        w.line(
            "if (%s == %s && %s != 9**9**9 && %s != -9**9**9) {",
            plField, plField, plField, plField);
        w.indent();
        w.line("push @{$parts}, \"!%d%s\" . %s;", fieldNum, typeChar, plField);
        w.dedent();
        w.line("}");
        break;
      default:
        w.line("push @{$parts}, \"!%d%s\" . %s;", fieldNum, typeChar, plField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String plField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if ($self->{_present_fields}[%d]) {", field.getArrayPosition());
      w.indent();
      w.line("push @{$parts}, \"!%de\" . %s;", fieldNum, plField);
      w.dedent();
      w.line("}");
    } else {
      w.line("push @{$parts}, \"!%de\" . %s;", fieldNum, plField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String plField, int fieldNum) {
    w.line("if (defined(%s)) {", plField);
    w.indent();
    w.line("push @{$parts}, \"!%dm\" . %s->_count_pbtk_fields();", fieldNum, plField);
    w.line("%s->_append_pbtk_fields($parts);", plField);
    w.dedent();
    w.line("}");
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String plField, int fieldNum) {
    String elemVar = "$__" + nameResolver.fieldName(field.getName()) + "_item";
    w.line("for my %s (@{%s}) {", elemVar, plField);
    w.indent();

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("if (defined(%s)) {", elemVar);
      w.indent();
      w.line("push @{$parts}, \"!%dm\" . %s->_count_pbtk_fields();", fieldNum, elemVar);
      w.line("%s->_append_pbtk_fields($parts);", elemVar);
      w.dedent();
      w.line("}");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("push @{$parts}, \"!%de\" . %s;", fieldNum, elemVar);
    } else {
      emitScalarAppend(w, field, elemVar, fieldNum);
    }

    w.dedent();
    w.line("}");
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String plField, int fieldNum) {
    // Maps serialize as repeated entries: !<fieldNum>m2!1<keyType><key>!2<valType><val>
    String keyVar = "$__mk";
    String valVar = "$__mv";
    w.line("for my %s (keys %%{%s}) {", keyVar, plField);
    w.indent();
    w.line("my %s = %s->{%s};", valVar, plField, keyVar);
    w.line("push @{$parts}, \"!%dm2\";", fieldNum);

    // Key (field 1)
    String keyTypeChar = pbtkTypeChar(field.getMapKeyType());
    switch (field.getMapKeyType()) {
      case TYPE_STRING:
        w.line("push @{$parts}, \"!1%s\" . uri_escape_utf8(%s);", keyTypeChar, keyVar);
        break;
      case TYPE_BOOL:
        w.line("push @{$parts}, \"!1%s\" . (%s ? \"1\" : \"0\");", keyTypeChar, keyVar);
        break;
      default:
        w.line("push @{$parts}, \"!1%s\" . %s;", keyTypeChar, keyVar);
        break;
    }

    // Value (field 2)
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("if (defined(%s)) {", valVar);
      w.indent();
      w.line("push @{$parts}, \"!2m\" . %s->_count_pbtk_fields();", valVar);
      w.line("%s->_append_pbtk_fields($parts);", valVar);
      w.dedent();
      w.line("}");
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("push @{$parts}, \"!2e\" . %s;", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("push @{$parts}, \"!2s\" . uri_escape_utf8(%s);", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("push @{$parts}, \"!2z\" . encode_base64(%s, \"\");", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("push @{$parts}, \"!2b\" . (%s ? \"1\" : \"0\");", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_FLOAT
        || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      String valTypeChar = pbtkTypeChar(field.getMapValueType());
      w.line("push @{$parts}, \"!2%s\" . %s;", valTypeChar, valVar);
    } else {
      String valTypeChar = pbtkTypeChar(field.getMapValueType());
      w.line("push @{$parts}, \"!2%s\" . %s;", valTypeChar, valVar);
    }

    w.dedent();
    w.line("}");
  }

  private void emitFieldCount(CodeWriter w, ProtoField field) {
    String plField = "$self->{" + nameResolver.fieldName(field.getName()) + "}";

    if (field.isOneofMember()) {
      String caseName = "$self->{_" + nameResolver.fieldName(field.getOneofName()) + "_case}";
      w.line("if (%s == %d) {", caseName, field.getFieldNumber());
      w.indent();
      w.line("$count += 1;");
      w.dedent();
      w.line("}");
      return;
    }

    if (field.isMap()) {
      w.line("$count += scalar keys %%{%s};", plField);
    } else if (field.isRepeated()) {
      if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
        // Only count defined elements
        w.line("$count += scalar grep { defined($_) } @{%s};", plField);
      } else {
        w.line("$count += scalar @{%s};", plField);
      }
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("if (defined(%s)) {", plField);
      w.indent();
      w.line("$count += 1;");
      w.dedent();
      w.line("}");
    } else if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if ($self->{_present_fields}[%d]) {", field.getArrayPosition());
      w.indent();
      w.line("$count += 1;");
      w.dedent();
      w.line("}");
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
      // Skip NaN and Inf
      w.line(
          "if (%s == %s && %s != 9**9**9 && %s != -9**9**9) {", plField, plField, plField, plField);
      w.indent();
      w.line("$count += 1;");
      w.dedent();
      w.line("}");
    } else {
      w.line("$count += 1;");
    }
  }

  // ---------------------------------------------------------------------------
  // Deserializer: from_pbtk_url()
  // ---------------------------------------------------------------------------

  private void emitDeserializer(CodeWriter w, ProtoMessage message, Set<String> lazyImports) {
    String className = nameResolver.messageClassName(message.getName());

    // Internal: _parse_pbtk_tokens
    emitParseFromTokens(w, message, className, lazyImports);

    // Public: from_pbtk_url
    w.blankLine();
    w.line("sub from_pbtk_url {");
    w.indent();
    w.line("my ($class, $input_str) = @_;");
    w.line("if (!defined($input_str) || $input_str eq \"\") {");
    w.indent();
    w.line("return $class->new();");
    w.dedent();
    w.line("}");
    w.line("my $tokens = $class->_tokenize_pbtk($input_str);");
    w.line("my $offset = [0];");
    w.line("return $class->_parse_pbtk_tokens($tokens, scalar @{$tokens}, $offset);");
    w.dedent();
    w.line("}");

    // Tokenizer: _tokenize_pbtk
    w.blankLine();
    w.line("sub _tokenize_pbtk {");
    w.indent();
    w.line("my ($class, $input_str) = @_;");
    w.line("my @tokens;");
    w.line("my $i = (length($input_str) > 0 && substr($input_str, 0, 1) eq \"!\") ? 1 : 0;");
    w.line("while ($i < length($input_str)) {");
    w.indent();
    w.line("my $nxt = index($input_str, \"!\", $i);");
    w.line("if ($nxt < 0) {");
    w.indent();
    w.line("push @tokens, substr($input_str, $i);");
    w.line("last;");
    w.dedent();
    w.line("}");
    w.line("push @tokens, substr($input_str, $i, $nxt - $i);");
    w.line("$i = $nxt + 1;");
    w.dedent();
    w.line("}");
    w.line("return \\@tokens;");
    w.dedent();
    w.line("}");
  }

  private void emitParseFromTokens(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    w.blankLine();
    w.line("sub _parse_pbtk_tokens {");
    w.indent();
    w.line("my ($class, $tokens, $field_count, $offset) = @_;");

    // Lazy imports for cross-file types
    for (String imp : lazyImports) {
      w.line("%s  # lazy import to avoid circular dependency", imp);
    }

    w.line("my $obj = $class->new();");
    w.line("my $consumed = 0;");
    w.line("while ($consumed < $field_count && $offset->[0] < scalar @{$tokens}) {");
    w.indent();
    w.line("my $token = $tokens->[$offset->[0]];");
    // Parse field number and type char
    w.line("my $num_end = 0;");
    w.line("$num_end++ while ($num_end < length($token) && substr($token, $num_end, 1) =~ /\\d/);");
    w.line("if ($num_end == 0 || $num_end >= length($token)) {");
    w.indent();
    w.line("$offset->[0]++;");
    w.line("$consumed++;");
    w.line("next;");
    w.dedent();
    w.line("}");
    w.line("my $field_num = int(substr($token, 0, $num_end));");
    w.line("my $type_char = substr($token, $num_end, 1);");
    w.line("my $value = substr($token, $num_end + 1);");

    // Dispatch on field number
    boolean first = true;
    for (ProtoField field : message.getFields()) {
      if (first) {
        w.line("if ($field_num == %d) {", field.getFieldNumber());
        first = false;
      } else {
        w.line("} elsif ($field_num == %d) {", field.getFieldNumber());
      }
      w.indent();
      emitFieldDeserialize(w, field);
      w.dedent();
    }

    // Default: skip unknown field
    if (!message.getFields().isEmpty()) {
      w.line("} else {");
    } else {
      w.line("if (1) {");
    }
    w.indent();
    w.line("$offset->[0]++;");
    w.line("$consumed++;");
    w.dedent();
    w.line("}");

    w.dedent(); // end while
    w.line("}");
    w.line("return $obj;");
    w.dedent(); // end method
    w.line("}");
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field) {
    if (field.isMap()) {
      emitMapDeserialize(w, field);
    } else if (field.isRepeated()) {
      emitRepeatedDeserialize(w, field);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageDeserialize(w, field);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumDeserialize(w, field);
    } else {
      emitScalarDeserialize(w, field);
    }
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field) {
    String plField = "$obj->{" + nameResolver.fieldName(field.getName()) + "}";
    String readExpr = scalarReadExpr(field.getProtoType(), "$value");
    w.line("%s = %s;", plField, readExpr);
    if (field.isProto3Optional()) {
      w.line("$obj->{_present_fields}[%d] = 1;", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = "$obj->{_" + nameResolver.fieldName(field.getOneofName()) + "_case}";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }
    w.line("$offset->[0]++;");
    w.line("$consumed++;");
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field) {
    String plField = "$obj->{" + nameResolver.fieldName(field.getName()) + "}";
    w.line("%s = int($value);", plField);
    if (field.isProto3Optional()) {
      w.line("$obj->{_present_fields}[%d] = 1;", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = "$obj->{_" + nameResolver.fieldName(field.getOneofName()) + "_case}";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }
    w.line("$offset->[0]++;");
    w.line("$consumed++;");
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field) {
    String plField = "$obj->{" + nameResolver.fieldName(field.getName()) + "}";
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("my $sub_count = int($value);");
    w.line("$offset->[0]++;");
    w.line("%s = %s->_parse_pbtk_tokens($tokens, $sub_count, $offset);", plField, msgType);
    if (field.isOneofMember()) {
      String caseName = "$obj->{_" + nameResolver.fieldName(field.getOneofName()) + "_case}";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }
    w.line("$consumed++;");
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field) {
    String plField = "$obj->{" + nameResolver.fieldName(field.getName()) + "}";

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("my $sub_count = int($value);");
      w.line("$offset->[0]++;");
      w.line("push @{%s}, %s->_parse_pbtk_tokens($tokens, $sub_count, $offset);", plField, msgType);
      w.line("$consumed++;");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("push @{%s}, int($value);", plField);
      w.line("$offset->[0]++;");
      w.line("$consumed++;");
    } else {
      String readExpr = scalarReadExpr(field.getProtoType(), "$value");
      w.line("push @{%s}, %s;", plField, readExpr);
      w.line("$offset->[0]++;");
      w.line("$consumed++;");
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field) {
    String plField = "$obj->{" + nameResolver.fieldName(field.getName()) + "}";
    // Map entry: !<num>m2!1<keyType><key>!2<valType><val>
    w.line("my $entry_count = int($value);");
    w.line("$offset->[0]++;");
    w.line("my $entry_key = undef;");
    w.line("my $entry_val = undef;");
    w.line("for my $__mi (0 .. $entry_count - 1) {");
    w.indent();
    w.line("last if ($offset->[0] >= scalar @{$tokens});");
    w.line("my $map_token = $tokens->[$offset->[0]];");
    w.line("my $mne = 0;");
    w.line("$mne++ while ($mne < length($map_token) && substr($map_token, $mne, 1) =~ /\\d/);");
    w.line("if ($mne == 0 || $mne >= length($map_token)) {");
    w.indent();
    w.line("$offset->[0]++;");
    w.line("next;");
    w.dedent();
    w.line("}");
    w.line("my $mfn = int(substr($map_token, 0, $mne));");
    w.line("my $mval = substr($map_token, $mne + 1);");

    // Key (field 1)
    w.line("if ($mfn == 1) {");
    w.indent();
    String keyRead = scalarReadExpr(field.getMapKeyType(), "$mval");
    w.line("$entry_key = %s;", keyRead);
    w.dedent();
    w.line("}");

    // Value (field 2)
    w.line("if ($mfn == 2) {");
    w.indent();
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("my $val_sub_count = int($mval);");
      w.line("$offset->[0]++;");
      w.line("$entry_val = %s->_parse_pbtk_tokens($tokens, $val_sub_count, $offset);", msgType);
      w.line("$offset->[0]--;"); // compensate for the outer offset++ below
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("$entry_val = int($mval);");
    } else {
      String valRead = scalarReadExpr(field.getMapValueType(), "$mval");
      w.line("$entry_val = %s;", valRead);
    }
    w.dedent();
    w.line("}");

    w.line("$offset->[0]++;");
    w.dedent(); // end for
    w.line("}");

    // Compensate
    w.line("$offset->[0]--;");
    w.line("if (defined($entry_key)) {");
    w.indent();
    w.line("%s->{$entry_key} = $entry_val;", plField);
    w.dedent();
    w.line("}");
    w.line("$offset->[0]++;");
    w.line("$consumed++;");
  }

  // ---------------------------------------------------------------------------
  // Type mapping helpers
  // ---------------------------------------------------------------------------

  private static String pbtkTypeChar(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_BOOL -> "b";
      case TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32,
          TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64 ->
          "i";
      case TYPE_FLOAT -> "f";
      case TYPE_DOUBLE -> "d";
      case TYPE_STRING -> "s";
      case TYPE_ENUM -> "e";
      case TYPE_MESSAGE -> "m";
      case TYPE_BYTES -> "z";
      default -> "s";
    };
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String valueVar) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> valueVar + " + 0.0";
      case TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32 ->
          "int(" + valueVar + ")";
      case TYPE_BOOL -> valueVar + " eq \"1\" ? 1 : 0";
      case TYPE_STRING -> "uri_unescape(" + valueVar + ")";
      case TYPE_BYTES -> "decode_base64(" + valueVar + ")";
      default -> valueVar;
    };
  }

  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "HASH";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  /**
   * Collect cross-file import statements for types referenced by this message. These are emitted as
   * lazy imports inside method bodies to avoid circular import issues.
   */
  private void collectReferencedTypes(
      ProtoMessage message, ProtoFile file, Set<String> imports, TypeRegistry registry) {
    String currentPrefix =
        file.getProtoPackage().isEmpty() ? "." : "." + file.getProtoPackage() + ".";

    for (ProtoField field : message.getFields()) {
      String typeRef = field.getTypeReference();
      if (typeRef == null) continue;
      if (field.isWellKnownType()) continue;

      // Skip synthetic map-entry types
      if (registry != null && registry.isMapEntry(typeRef)) continue;

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
      if (isNested) continue;

      if (typeRef.startsWith(currentPrefix)) {
        String perlPkg = nameResolver.resolveFullPackageName(typeRef, file);
        imports.add("require " + perlPkg + ";");
      }

      // Map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        String valRef = field.getMapValueTypeReference();
        if (registry != null && registry.isMapEntry(valRef)) continue;
        if (valRef.startsWith(currentPrefix)) {
          String perlPkg = nameResolver.resolveFullPackageName(valRef, file);
          imports.add("require " + perlPkg + ";");
        }
      }
    }

    for (ProtoMessage nested : message.getNestedMessages()) {
      collectReferencedTypes(nested, file, imports, registry);
    }
  }

  private boolean hasOptionalFields(ProtoMessage message) {
    return message.getFields().stream().anyMatch(ProtoField::isProto3Optional);
  }

  private String buildPackageName(ProtoFile file, String messageName) {
    String pkg = nameResolver.resolvePackage(file);
    if (pkg.isEmpty()) {
      return messageName;
    }
    return pkg + "::" + messageName;
  }
}
