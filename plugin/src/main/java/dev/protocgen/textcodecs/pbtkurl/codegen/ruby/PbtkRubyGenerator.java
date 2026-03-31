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
package dev.protocgen.textcodecs.pbtkurl.codegen.ruby;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.ruby.RubyNameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.ruby.RubyTypeMapper;
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
 * Ruby language code generator for pbtk URL encoding. Produces Ruby source files with
 * to_pbtk_url/from_pbtk_url serialization methods.
 *
 * <p>The pbtk URL format encodes protobuf messages as URL strings using the syntax: {@code
 * !<fieldNumber><typeChar><value>} where type chars are: b=bool(0/1), i=integer, f=float, d=double,
 * s=string(URL-encoded), e=enum(int), m=message(count of sub-fields), z=bytes(base64).
 */
public class PbtkRubyGenerator implements LanguageGenerator {

  private final RubyNameResolver nameResolver = new RubyNameResolver();
  private final RubyTypeMapper typeMapper = new RubyTypeMapper();

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

  // ---------------------------------------------------------------------------
  // Top-level emission
  // ---------------------------------------------------------------------------

  private String emitMessage(ProtoMessage message, ProtoFile file, Set<String> lazyImports) {
    CodeWriter w = new CodeWriter("  ");

    // Imports
    emitImports(w);

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

    // Nested enum/message class references as class attributes
    for (ProtoEnum protoEnum : message.getEnums()) {
      w.line("%s = %s", protoEnum.getName(), protoEnum.getName());
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.line("%s = %s", nested.getName(), nested.getName());
    }

    emitAttrAccessors(w, message);
    emitInit(w, message);
    emitHasMethods(w, message);
    emitSerializer(w, message);
    emitDeserializer(w, message, className, lazyImports);
    emitInspect(w, message, className);

    w.dedent();
    w.line("end");

    // Close module hierarchy
    closeModules(w, moduleDepth);

    return w.toString();
  }

  private String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter("  ");
    emitImports(w);
    int moduleDepth = openModules(w, file);
    emitEnum(w, protoEnum);
    closeModules(w, moduleDepth);
    return w.toString();
  }

  private void emitImports(CodeWriter w) {
    w.line("require 'base64'");
    w.line("require 'cgi'");
  }

  private int openModules(CodeWriter w, ProtoFile file) {
    String pkg = file.getProtoPackage();
    if (pkg == null || pkg.isEmpty()) return 0;

    String[] parts = pkg.split("\\.");
    for (String part : parts) {
      w.blankLine();
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

  // ---------------------------------------------------------------------------
  // Nested messages and enums
  // ---------------------------------------------------------------------------

  private void emitNestedMessage(
      CodeWriter w, ProtoMessage nested, ProtoFile file, Set<String> lazyImports) {
    // Recursively emit any of this nested message's own nested types
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

    for (ProtoEnum protoEnum : nested.getEnums()) {
      w.line("%s = %s", protoEnum.getName(), protoEnum.getName());
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      w.line("%s = %s", deepNested.getName(), deepNested.getName());
    }

    emitAttrAccessors(w, nested);
    emitInit(w, nested);
    emitHasMethods(w, nested);
    emitSerializer(w, nested);
    emitDeserializer(w, nested, className, lazyImports);
    emitInspect(w, nested, className);

    w.dedent();
    w.line("end");
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.blankLine();
    w.line("module %s", protoEnum.getName());
    w.indent();
    w.line("# Proto enum %s represented as integer constants.", protoEnum.getName());

    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%s = %d", nameResolver.enumConstantName(val.name()), val.number());
    }

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

  // ---------------------------------------------------------------------------
  // attr_accessor, initialize, has_*, inspect
  // ---------------------------------------------------------------------------

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

    for (ProtoField field : message.getFields()) {
      String rbName = nameResolver.fieldName(field.getName());
      String defaultVal = typeMapper.defaultValue(field);
      if (field.isMap()) {
        w.line("@%s = {}", rbName);
      } else if (field.isRepeated()) {
        w.line("@%s = []", rbName);
      } else {
        w.line("@%s = %s", rbName, defaultVal);
      }
    }

    if (hasOptionalFields(message)) {
      int maxPos = message.getMaxFieldNumber();
      w.line("@present_fields = Array.new(%d, false)", maxPos);
    }

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

  // ---------------------------------------------------------------------------
  // Serializer: to_pbtk_url
  // ---------------------------------------------------------------------------

  private void emitSerializer(CodeWriter w, ProtoMessage message) {
    // _append_pbtk_fields(parts): appends field tokens to an array
    w.blankLine();
    w.line("def _append_pbtk_fields(parts)");
    w.indent();
    if (message.getFields().isEmpty()) {
      w.line("# no fields");
    } else {
      for (ProtoField field : message.getFields()) {
        emitFieldSerialize(w, field);
      }
    }
    w.dedent();
    w.line("end");

    // _count_pbtk_fields: returns number of top-level tokens
    w.blankLine();
    w.line("def _count_pbtk_fields");
    w.indent();
    w.line("count = 0");
    for (ProtoField field : message.getFields()) {
      emitFieldCount(w, field);
    }
    w.line("count");
    w.dedent();
    w.line("end");

    // to_pbtk_url: public serialize method
    w.blankLine();
    w.line("def to_pbtk_url");
    w.indent();
    w.line("parts = []");
    w.line("_append_pbtk_fields(parts)");
    w.line("parts.join");
    w.dedent();
    w.line("end");
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String rbField = "@" + nameResolver.fieldName(field.getName());
    int fieldNum = field.getFieldNumber();

    if (field.isOneofMember()) {
      String caseName = "@" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("if %s == %d", caseName, field.getFieldNumber());
      w.indent();
      emitSingleFieldSerialize(w, field, rbField, fieldNum);
      w.dedent();
      w.line("end");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, rbField, fieldNum);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, rbField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, rbField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, rbField, fieldNum);
    } else {
      emitScalarSerialize(w, field, rbField, fieldNum);
    }
  }

  private void emitSingleFieldSerialize(
      CodeWriter w, ProtoField field, String rbField, int fieldNum) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, rbField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, rbField, fieldNum);
    } else {
      emitScalarSerialize(w, field, rbField, fieldNum);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String rbField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if @present_fields[%d]", field.getArrayPosition());
      w.indent();
      emitScalarAppend(w, field, rbField, fieldNum);
      w.dedent();
      w.line("end");
      return;
    }
    emitScalarAppend(w, field, rbField, fieldNum);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String rbField, int fieldNum) {
    FieldDescriptorProto.Type type = field.getProtoType();
    String typeChar = pbtkTypeChar(type);

    switch (type) {
      case TYPE_BOOL:
        w.line("parts << \"!%d%s\" + (%s ? \"1\" : \"0\")", fieldNum, typeChar, rbField);
        break;
      case TYPE_BYTES:
        w.line("parts << \"!%d%s\" + Base64.strict_encode64(%s)", fieldNum, typeChar, rbField);
        break;
      case TYPE_STRING:
        w.line("parts << \"!%d%s\" + CGI.escape(%s)", fieldNum, typeChar, rbField);
        break;
      case TYPE_DOUBLE:
        w.line("unless %s.nan? || %s.infinite?", rbField, rbField);
        w.indent();
        w.line("parts << \"!%d%s\" + %s.to_s", fieldNum, typeChar, rbField);
        w.dedent();
        w.line("end");
        break;
      case TYPE_FLOAT:
        w.line("unless %s.nan? || %s.infinite?", rbField, rbField);
        w.indent();
        w.line("parts << \"!%d%s\" + %s.to_s", fieldNum, typeChar, rbField);
        w.dedent();
        w.line("end");
        break;
      default:
        w.line("parts << \"!%d%s\" + %s.to_s", fieldNum, typeChar, rbField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String rbField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if @present_fields[%d]", field.getArrayPosition());
      w.indent();
      w.line("parts << \"!%de\" + %s.to_s", fieldNum, rbField);
      w.dedent();
      w.line("end");
    } else {
      w.line("parts << \"!%de\" + %s.to_s", fieldNum, rbField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String rbField, int fieldNum) {
    w.line("unless %s.nil?", rbField);
    w.indent();
    w.line("parts << \"!%dm\" + %s._count_pbtk_fields.to_s", fieldNum, rbField);
    w.line("%s._append_pbtk_fields(parts)", rbField);
    w.dedent();
    w.line("end");
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String rbField, int fieldNum) {
    String elemVar = "__" + nameResolver.fieldName(field.getName()) + "_item";
    w.line("%s.each do |%s|", rbField, elemVar);
    w.indent();

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("unless %s.nil?", elemVar);
      w.indent();
      w.line("parts << \"!%dm\" + %s._count_pbtk_fields.to_s", fieldNum, elemVar);
      w.line("%s._append_pbtk_fields(parts)", elemVar);
      w.dedent();
      w.line("end");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("parts << \"!%de\" + %s.to_s", fieldNum, elemVar);
    } else {
      emitScalarAppend(w, field, elemVar, fieldNum);
    }

    w.dedent();
    w.line("end");
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String rbField, int fieldNum) {
    // Maps serialize as repeated entries: !<fieldNum>m2!1<keyType><key>!2<valType><val>
    String keyVar = "__mk";
    String valVar = "__mv";
    w.line("%s.each do |%s, %s|", rbField, keyVar, valVar);
    w.indent();
    w.line("parts << \"!%dm2\"", fieldNum);

    // Key (field 1)
    String keyTypeChar = pbtkTypeChar(field.getMapKeyType());
    switch (field.getMapKeyType()) {
      case TYPE_STRING:
        w.line("parts << \"!1%s\" + CGI.escape(%s.to_s)", keyTypeChar, keyVar);
        break;
      case TYPE_BOOL:
        w.line("parts << \"!1%s\" + (%s ? \"1\" : \"0\")", keyTypeChar, keyVar);
        break;
      default:
        w.line("parts << \"!1%s\" + %s.to_s", keyTypeChar, keyVar);
        break;
    }

    // Value (field 2)
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("unless %s.nil?", valVar);
      w.indent();
      w.line("parts << \"!2m\" + %s._count_pbtk_fields.to_s", valVar);
      w.line("%s._append_pbtk_fields(parts)", valVar);
      w.dedent();
      w.line("end");
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("parts << \"!2e\" + %s.to_s", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("parts << \"!2s\" + CGI.escape(%s.to_s)", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("parts << \"!2z\" + Base64.strict_encode64(%s)", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("parts << \"!2b\" + (%s ? \"1\" : \"0\")", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_FLOAT
        || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      String valTypeChar = pbtkTypeChar(field.getMapValueType());
      w.line("parts << \"!2%s\" + %s.to_s", valTypeChar, valVar);
    } else {
      String valTypeChar = pbtkTypeChar(field.getMapValueType());
      w.line("parts << \"!2%s\" + %s.to_s", valTypeChar, valVar);
    }

    w.dedent();
    w.line("end");
  }

  private void emitFieldCount(CodeWriter w, ProtoField field) {
    String rbField = "@" + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = "@" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("if %s == %d", caseName, field.getFieldNumber());
      w.indent();
      w.line("count += 1");
      w.dedent();
      w.line("end");
      return;
    }

    if (field.isMap()) {
      w.line("count += %s.length", rbField);
    } else if (field.isRepeated()) {
      if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
        // Only count non-nil elements
        w.line("count += %s.count { |__e| !__e.nil? }", rbField);
      } else {
        w.line("count += %s.length", rbField);
      }
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("count += 1 unless %s.nil?", rbField);
    } else if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("count += 1 if @present_fields[%d]", field.getArrayPosition());
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
      // Skip NaN and Inf
      w.line("count += 1 unless %s.nan? || %s.infinite?", rbField, rbField);
    } else {
      w.line("count += 1");
    }
  }

  // ---------------------------------------------------------------------------
  // Deserializer: from_pbtk_url
  // ---------------------------------------------------------------------------

  private void emitDeserializer(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    // Internal: _parse_pbtk_tokens(tokens, field_count, offset)
    emitParseFromTokens(w, message, className, lazyImports);

    // Public: from_pbtk_url(input_str)
    w.blankLine();
    w.line("def self.from_pbtk_url(input_str)");
    w.indent();
    w.line("return new if input_str.nil? || input_str.empty?");
    w.line("tokens = _tokenize_pbtk(input_str)");
    w.line("offset = [0]");
    w.line("_parse_pbtk_tokens(tokens, tokens.length, offset)");
    w.dedent();
    w.line("end");

    // Tokenizer: _tokenize_pbtk(input_str)
    w.blankLine();
    w.line("def self._tokenize_pbtk(input_str)");
    w.indent();
    w.line("tokens = []");
    w.line("i = input_str[0] == '!' ? 1 : 0");
    w.line("while i < input_str.length");
    w.indent();
    w.line("nxt = input_str.index('!', i)");
    w.line("if nxt.nil?");
    w.indent();
    w.line("tokens << input_str[i..-1]");
    w.line("break");
    w.dedent();
    w.line("end");
    w.line("tokens << input_str[i...nxt]");
    w.line("i = nxt + 1");
    w.dedent();
    w.line("end");
    w.line("tokens");
    w.dedent();
    w.line("end");
  }

  private void emitParseFromTokens(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    w.blankLine();
    w.line("def self._parse_pbtk_tokens(tokens, field_count, offset)");
    w.indent();

    // Lazy requires for cross-file types
    for (String imp : lazyImports) {
      w.line("%s # lazy require to avoid circular dependency", imp);
    }

    w.line("obj = new");
    w.line("consumed = 0");
    w.line("while consumed < field_count && offset[0] < tokens.length");
    w.indent();
    w.line("token = tokens[offset[0]]");
    // Parse field number and type char
    w.line("num_end = 0");
    w.line("num_end += 1 while num_end < token.length && token[num_end] =~ /\\d/");
    w.line("if num_end == 0 || num_end >= token.length");
    w.indent();
    w.line("offset[0] += 1");
    w.line("consumed += 1");
    w.line("next");
    w.dedent();
    w.line("end");
    w.line("field_num = token[0...num_end].to_i");
    w.line("type_char = token[num_end]");
    w.line("value = token[(num_end + 1)..-1]");

    // Dispatch on field number
    boolean first = true;
    for (ProtoField field : message.getFields()) {
      if (first) {
        w.line("if field_num == %d", field.getFieldNumber());
        first = false;
      } else {
        w.line("elsif field_num == %d", field.getFieldNumber());
      }
      w.indent();
      emitFieldDeserialize(w, field);
      w.dedent();
    }

    // Default: skip unknown field
    if (!message.getFields().isEmpty()) {
      w.line("else");
    } else {
      // No fields at all: always skip
      w.line("if true");
    }
    w.indent();
    w.line("offset[0] += 1");
    w.line("consumed += 1");
    w.dedent();
    w.line("end");

    w.dedent(); // end while
    w.line("end");
    w.line("obj");
    w.dedent(); // end method
    w.line("end");
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
    String rbField =
        "obj.instance_variable_set(:@" + nameResolver.fieldName(field.getName()) + ", ";
    String readExpr = scalarReadExpr(field.getProtoType(), "value");
    w.line("%s%s)", rbField, readExpr);
    if (field.isProto3Optional()) {
      w.line("obj.instance_variable_get(:@present_fields)[%d] = true", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("obj.instance_variable_set(:@%s, %d)", caseName, field.getFieldNumber());
    }
    w.line("offset[0] += 1");
    w.line("consumed += 1");
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field) {
    String fieldNameStr = nameResolver.fieldName(field.getName());
    w.line("obj.instance_variable_set(:@%s, value.to_i)", fieldNameStr);
    if (field.isProto3Optional()) {
      w.line("obj.instance_variable_get(:@present_fields)[%d] = true", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("obj.instance_variable_set(:@%s, %d)", caseName, field.getFieldNumber());
    }
    w.line("offset[0] += 1");
    w.line("consumed += 1");
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field) {
    String fieldNameStr = nameResolver.fieldName(field.getName());
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("sub_count = value.to_i");
    w.line("offset[0] += 1");
    w.line(
        "obj.instance_variable_set(:@%s, %s._parse_pbtk_tokens(tokens, sub_count, offset))",
        fieldNameStr, msgType);
    if (field.isOneofMember()) {
      String caseName = nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("obj.instance_variable_set(:@%s, %d)", caseName, field.getFieldNumber());
    }
    // Don't increment offset again -- recursive call consumed sub-tokens,
    // but we need to count this as one consumed field
    w.line("consumed += 1");
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field) {
    String fieldNameStr = nameResolver.fieldName(field.getName());

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("sub_count = value.to_i");
      w.line("offset[0] += 1");
      w.line(
          "obj.instance_variable_get(:@%s) << %s._parse_pbtk_tokens(tokens, sub_count, offset)",
          fieldNameStr, msgType);
      w.line("consumed += 1");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("obj.instance_variable_get(:@%s) << value.to_i", fieldNameStr);
      w.line("offset[0] += 1");
      w.line("consumed += 1");
    } else {
      String readExpr = scalarReadExpr(field.getProtoType(), "value");
      w.line("obj.instance_variable_get(:@%s) << %s", fieldNameStr, readExpr);
      w.line("offset[0] += 1");
      w.line("consumed += 1");
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field) {
    String fieldNameStr = nameResolver.fieldName(field.getName());
    // Map entry: !<num>m2!1<keyType><key>!2<valType><val>
    w.line("entry_count = value.to_i");
    w.line("offset[0] += 1");
    w.line("entry_key = nil");
    w.line("entry_val = nil");
    w.line("entry_count.times do");
    w.indent();
    w.line("break if offset[0] >= tokens.length");
    w.line("map_token = tokens[offset[0]]");
    w.line("mne = 0");
    w.line("mne += 1 while mne < map_token.length && map_token[mne] =~ /\\d/");
    w.line("if mne == 0 || mne >= map_token.length");
    w.indent();
    w.line("offset[0] += 1");
    w.line("next");
    w.dedent();
    w.line("end");
    w.line("mfn = map_token[0...mne].to_i");
    w.line("mval = map_token[(mne + 1)..-1]");

    // Key (field 1)
    w.line("if mfn == 1");
    w.indent();
    String keyRead = scalarReadExpr(field.getMapKeyType(), "mval");
    w.line("entry_key = %s", keyRead);
    w.dedent();
    w.line("end");

    // Value (field 2)
    w.line("if mfn == 2");
    w.indent();
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("val_sub_count = mval.to_i");
      w.line("offset[0] += 1");
      w.line("entry_val = %s._parse_pbtk_tokens(tokens, val_sub_count, offset)", msgType);
      w.line("offset[0] -= 1"); // compensate for the outer offset[0]++ below
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("entry_val = mval.to_i");
    } else {
      String valRead = scalarReadExpr(field.getMapValueType(), "mval");
      w.line("entry_val = %s", valRead);
    }
    w.dedent();
    w.line("end");

    w.line("offset[0] += 1");
    w.dedent(); // end times
    w.line("end");

    // Compensate: the outer loop will call offset[0]+=1 and consumed+=1
    // but we already advanced offset past all the sub-tokens.
    // We need to NOT advance offset again, so decrement once:
    w.line("offset[0] -= 1");
    w.line("unless entry_key.nil?");
    w.indent();
    w.line("obj.instance_variable_get(:@%s)[entry_key] = entry_val", fieldNameStr);
    w.dedent();
    w.line("end");
    w.line("offset[0] += 1");
    w.line("consumed += 1");
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
      case TYPE_DOUBLE, TYPE_FLOAT -> valueVar + ".to_f";
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
          valueVar + ".to_i";
      case TYPE_BOOL -> valueVar + " == '1'";
      case TYPE_STRING -> "CGI.unescape(" + valueVar + ")";
      case TYPE_BYTES -> "Base64.strict_decode64(" + valueVar + ")";
      default -> valueVar;
    };
  }

  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "Object";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  /**
   * Collect cross-file require_relative statements for types referenced by this message. These are
   * emitted as lazy requires inside method bodies to avoid circular require issues.
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

      String simpleName = typeRef.substring(typeRef.lastIndexOf('.') + 1);
      if (typeRef.startsWith(currentPrefix)) {
        String moduleName = pascalToSnake(simpleName);
        imports.add("require_relative '" + moduleName + "'");
      }

      // Map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        String valRef = field.getMapValueTypeReference();
        if (registry != null && registry.isMapEntry(valRef)) continue;
        String valName = valRef.substring(valRef.lastIndexOf('.') + 1);
        if (valRef.startsWith(currentPrefix)) {
          String moduleName = pascalToSnake(valName);
          imports.add("require_relative '" + moduleName + "'");
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

  /** Convert PascalCase to snake_case for Ruby module names. */
  private static String pascalToSnake(String pascal) {
    if (pascal == null || pascal.isEmpty()) return pascal;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pascal.length(); i++) {
      char c = pascal.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          char prev = pascal.charAt(i - 1);
          boolean nextIsLower =
              i + 1 < pascal.length() && Character.isLowerCase(pascal.charAt(i + 1));
          if (Character.isLowerCase(prev) || (Character.isUpperCase(prev) && nextIsLower)) {
            sb.append('_');
          }
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
