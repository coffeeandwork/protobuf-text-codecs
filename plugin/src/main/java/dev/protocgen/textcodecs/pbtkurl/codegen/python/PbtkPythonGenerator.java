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
package dev.protocgen.textcodecs.pbtkurl.codegen.python;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.python.PythonNameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.python.PythonTypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Python language code generator for pbtk URL encoding. Produces Python source files with
 * to_pbtk_url()/from_pbtk_url() serialization methods.
 *
 * <p>The pbtk URL format encodes protobuf messages as URL strings using the syntax: {@code
 * !<fieldNumber><typeChar><value>} where type chars are: b=bool(0/1), i=integer, f=float, d=double,
 * s=string(URL-encoded), e=enum(int), m=message(count of sub-fields), z=bytes(base64).
 */
public class PbtkPythonGenerator implements LanguageGenerator {

  private final PythonNameResolver nameResolver = new PythonNameResolver();
  private final PythonTypeMapper typeMapper = new PythonTypeMapper();

  // Track emitted __init__.py files across multiple generate() calls
  private final Set<String> emittedInitFiles = new HashSet<>();

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();
    Set<String> initDirs = new HashSet<>();

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

      collectPackageDirs(outputPath, initDirs);
    }

    for (ProtoEnum protoEnum : file.getEnums()) {
      String sourceCode = emitTopLevelEnum(protoEnum);
      String outputPath = nameResolver.outputFilePath(file, protoEnum.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());

      collectPackageDirs(outputPath, initDirs);
    }

    // Generate __init__.py for each package directory
    for (String dir : initDirs) {
      String initPath = dir + "/__init__.py";
      if (emittedInitFiles.add(initPath)) {
        result.add(
            CodeGeneratorResponse.File.newBuilder().setName(initPath).setContent("").build());
      }
    }

    return result;
  }

  // ---------------------------------------------------------------------------
  // Top-level emission
  // ---------------------------------------------------------------------------

  private String emitMessage(ProtoMessage message, ProtoFile file, Set<String> lazyImports) {
    CodeWriter w = new CodeWriter();

    // Imports
    emitImports(w);

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

    emitInit(w, message);
    emitProperties(w, message);
    emitHasMethods(w, message);
    emitSerializer(w, message);
    emitDeserializer(w, message, className, lazyImports);
    emitRepr(w, message, className);

    w.dedent();

    return w.toString();
  }

  private String emitTopLevelEnum(ProtoEnum protoEnum) {
    CodeWriter w = new CodeWriter();
    emitImports(w);
    emitEnum(w, protoEnum);
    return w.toString();
  }

  private void emitImports(CodeWriter w) {
    w.line("import base64");
    w.line("import urllib.parse");
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
    w.blankLine();
    w.line("class %s:", className);
    w.indent();
    w.line("\"\"\"Generated from proto message %s.\"\"\"", nested.getFullName());

    for (ProtoEnum protoEnum : nested.getEnums()) {
      w.line("%s = %s", protoEnum.getName(), protoEnum.getName());
    }
    for (ProtoMessage deepNested : nested.getNestedMessages()) {
      w.line("%s = %s", deepNested.getName(), deepNested.getName());
    }

    emitInit(w, nested);
    emitProperties(w, nested);
    emitHasMethods(w, nested);
    emitSerializer(w, nested);
    emitDeserializer(w, nested, className, lazyImports);
    emitRepr(w, nested, className);

    w.dedent();
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.blankLine();
    w.blankLine();
    w.line("class %s:", protoEnum.getName());
    w.indent();
    w.line("\"\"\"Proto enum %s represented as integer constants.\"\"\"", protoEnum.getName());

    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%s = %d", nameResolver.enumConstantName(val.name()), val.number());
    }

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

  // ---------------------------------------------------------------------------
  // __init__, properties, has_*, __repr__
  // ---------------------------------------------------------------------------

  private void emitInit(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.line("def __init__(self):");
    w.indent();

    if (message.getFields().isEmpty() && message.getOneofGroups().isEmpty()) {
      w.line("pass");
      w.dedent();
      return;
    }

    for (ProtoField field : message.getFields()) {
      String pyName = nameResolver.fieldName(field.getName());
      String defaultVal = typeMapper.defaultValue(field);
      if (field.isMap()) {
        w.line("self._%s = dict()", pyName);
      } else if (field.isRepeated()) {
        w.line("self._%s = list()", pyName);
      } else {
        w.line("self._%s = %s", pyName, defaultVal);
      }
    }

    if (hasOptionalFields(message)) {
      int maxPos = message.getMaxFieldNumber();
      w.line("self._present_fields = [False] * %d", maxPos);
    }

    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      String caseName = nameResolver.fieldName(group.name()) + "_case";
      w.line("self._%s = 0  # 0 = not set", caseName);
    }

    w.dedent();
  }

  private void emitProperties(CodeWriter w, ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      String pyName = nameResolver.fieldName(field.getName());

      // Getter
      w.blankLine();
      w.line("@property");
      w.line("def %s(self):", pyName);
      w.indent();
      w.line("return self._%s", pyName);
      w.dedent();

      // Setter
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

  // ---------------------------------------------------------------------------
  // Serializer: to_pbtk_url()
  // ---------------------------------------------------------------------------

  private void emitSerializer(CodeWriter w, ProtoMessage message) {
    // _append_pbtk_fields(self, parts): appends field tokens to a list
    w.blankLine();
    w.line("def _append_pbtk_fields(self, parts):");
    w.indent();
    if (message.getFields().isEmpty()) {
      w.line("pass");
    } else {
      for (ProtoField field : message.getFields()) {
        emitFieldSerialize(w, field);
      }
    }
    w.dedent();

    // _count_pbtk_fields(self): returns number of top-level tokens
    w.blankLine();
    w.line("def _count_pbtk_fields(self):");
    w.indent();
    w.line("count = 0");
    for (ProtoField field : message.getFields()) {
      emitFieldCount(w, field);
    }
    w.line("return count");
    w.dedent();

    // to_pbtk_url(self): public serialize method
    w.blankLine();
    w.line("def to_pbtk_url(self):");
    w.indent();
    w.line("\"\"\"Serialize this message to a pbtk URL-encoded string.\"\"\"");
    w.line("parts = []");
    w.line("self._append_pbtk_fields(parts)");
    w.line("return \"\".join(parts)");
    w.dedent();
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String pyField = "self._" + nameResolver.fieldName(field.getName());
    int fieldNum = field.getFieldNumber();

    if (field.isOneofMember()) {
      String caseName = "self._" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("if %s == %d:", caseName, field.getFieldNumber());
      w.indent();
      emitSingleFieldSerialize(w, field, pyField, fieldNum);
      w.dedent();
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, pyField, fieldNum);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, pyField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, pyField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, pyField, fieldNum);
    } else {
      emitScalarSerialize(w, field, pyField, fieldNum);
    }
  }

  private void emitSingleFieldSerialize(
      CodeWriter w, ProtoField field, String pyField, int fieldNum) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, pyField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, pyField, fieldNum);
    } else {
      emitScalarSerialize(w, field, pyField, fieldNum);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String pyField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if self._present_fields[%d]:", field.getArrayPosition());
      w.indent();
      emitScalarAppend(w, field, pyField, fieldNum);
      w.dedent();
      return;
    }
    emitScalarAppend(w, field, pyField, fieldNum);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String pyField, int fieldNum) {
    FieldDescriptorProto.Type type = field.getProtoType();
    String typeChar = pbtkTypeChar(type);

    switch (type) {
      case TYPE_BOOL:
        w.line("parts.append(\"!%d%s\" + (\"1\" if %s else \"0\"))", fieldNum, typeChar, pyField);
        break;
      case TYPE_BYTES:
        w.line(
            "parts.append(\"!%d%s\" + base64.b64encode(%s).decode(\"ascii\"))",
            fieldNum, typeChar, pyField);
        break;
      case TYPE_STRING:
        w.line(
            "parts.append(\"!%d%s\" + urllib.parse.quote(%s, safe=\"\"))",
            fieldNum, typeChar, pyField);
        break;
      case TYPE_DOUBLE:
        w.line(
            "if not (%s != %s or %s == float(\"inf\") or %s == float(\"-inf\")):",
            pyField, pyField, pyField, pyField);
        w.indent();
        w.line("parts.append(\"!%d%s\" + str(%s))", fieldNum, typeChar, pyField);
        w.dedent();
        break;
      case TYPE_FLOAT:
        w.line(
            "if not (%s != %s or %s == float(\"inf\") or %s == float(\"-inf\")):",
            pyField, pyField, pyField, pyField);
        w.indent();
        w.line("parts.append(\"!%d%s\" + str(%s))", fieldNum, typeChar, pyField);
        w.dedent();
        break;
      default:
        w.line("parts.append(\"!%d%s\" + str(%s))", fieldNum, typeChar, pyField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String pyField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if self._present_fields[%d]:", field.getArrayPosition());
      w.indent();
      w.line("parts.append(\"!%de\" + str(%s))", fieldNum, pyField);
      w.dedent();
    } else {
      w.line("parts.append(\"!%de\" + str(%s))", fieldNum, pyField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String pyField, int fieldNum) {
    w.line("if %s is not None:", pyField);
    w.indent();
    w.line("parts.append(\"!%dm\" + str(%s._count_pbtk_fields()))", fieldNum, pyField);
    w.line("%s._append_pbtk_fields(parts)", pyField);
    w.dedent();
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String pyField, int fieldNum) {
    String elemVar = "__" + nameResolver.fieldName(field.getName()) + "_item";
    w.line("for %s in %s:", elemVar, pyField);
    w.indent();

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("if %s is not None:", elemVar);
      w.indent();
      w.line("parts.append(\"!%dm\" + str(%s._count_pbtk_fields()))", fieldNum, elemVar);
      w.line("%s._append_pbtk_fields(parts)", elemVar);
      w.dedent();
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("parts.append(\"!%de\" + str(%s))", fieldNum, elemVar);
    } else {
      emitScalarAppend(w, field, elemVar, fieldNum);
    }

    w.dedent();
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String pyField, int fieldNum) {
    // Maps serialize as repeated entries: !<fieldNum>m2!1<keyType><key>!2<valType><val>
    String keyVar = "__mk";
    String valVar = "__mv";
    w.line("for %s, %s in %s.items():", keyVar, valVar, pyField);
    w.indent();
    w.line("parts.append(\"!%dm2\")", fieldNum);

    // Key (field 1)
    String keyTypeChar = pbtkTypeChar(field.getMapKeyType());
    switch (field.getMapKeyType()) {
      case TYPE_STRING:
        w.line(
            "parts.append(\"!1%s\" + urllib.parse.quote(str(%s), safe=\"\"))", keyTypeChar, keyVar);
        break;
      case TYPE_BOOL:
        w.line("parts.append(\"!1%s\" + (\"1\" if %s else \"0\"))", keyTypeChar, keyVar);
        break;
      default:
        w.line("parts.append(\"!1%s\" + str(%s))", keyTypeChar, keyVar);
        break;
    }

    // Value (field 2)
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("if %s is not None:", valVar);
      w.indent();
      w.line("parts.append(\"!2m\" + str(%s._count_pbtk_fields()))", valVar);
      w.line("%s._append_pbtk_fields(parts)", valVar);
      w.dedent();
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("parts.append(\"!2e\" + str(%s))", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("parts.append(\"!2s\" + urllib.parse.quote(str(%s), safe=\"\"))", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("parts.append(\"!2z\" + base64.b64encode(%s).decode(\"ascii\"))", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("parts.append(\"!2b\" + (\"1\" if %s else \"0\"))", valVar);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_FLOAT
        || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      String valTypeChar = pbtkTypeChar(field.getMapValueType());
      w.line("parts.append(\"!2%s\" + str(%s))", valTypeChar, valVar);
    } else {
      String valTypeChar = pbtkTypeChar(field.getMapValueType());
      w.line("parts.append(\"!2%s\" + str(%s))", valTypeChar, valVar);
    }

    w.dedent();
  }

  private void emitFieldCount(CodeWriter w, ProtoField field) {
    String pyField = "self._" + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = "self._" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("if %s == %d:", caseName, field.getFieldNumber());
      w.indent();
      w.line("count += 1");
      w.dedent();
      return;
    }

    if (field.isMap()) {
      w.line("count += len(%s)", pyField);
    } else if (field.isRepeated()) {
      if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
        // Only count non-None elements
        w.line("count += sum(1 for __e in %s if __e is not None)", pyField);
      } else {
        w.line("count += len(%s)", pyField);
      }
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("if %s is not None:", pyField);
      w.indent();
      w.line("count += 1");
      w.dedent();
    } else if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if self._present_fields[%d]:", field.getArrayPosition());
      w.indent();
      w.line("count += 1");
      w.dedent();
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
      // Skip NaN and Inf
      w.line(
          "if not (%s != %s or %s == float(\"inf\") or %s == float(\"-inf\")):",
          pyField, pyField, pyField, pyField);
      w.indent();
      w.line("count += 1");
      w.dedent();
    } else {
      w.line("count += 1");
    }
  }

  // ---------------------------------------------------------------------------
  // Deserializer: from_pbtk_url()
  // ---------------------------------------------------------------------------

  private void emitDeserializer(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    // Internal: _parse_pbtk_tokens(cls, tokens, field_count, offset)
    emitParseFromTokens(w, message, className, lazyImports);

    // Public: from_pbtk_url(cls, input_str)
    w.blankLine();
    w.line("@classmethod");
    w.line("def from_pbtk_url(cls, input_str):");
    w.indent();
    w.line("\"\"\"Deserialize a pbtk URL-encoded string into a %s instance.\"\"\"", className);
    w.line("if not input_str:");
    w.indent();
    w.line("return cls()");
    w.dedent();
    w.line("tokens = cls._tokenize_pbtk(input_str)");
    w.line("offset = [0]");
    w.line("return cls._parse_pbtk_tokens(tokens, len(tokens), offset)");
    w.dedent();

    // Tokenizer: _tokenize_pbtk(cls, input_str)
    w.blankLine();
    w.line("@staticmethod");
    w.line("def _tokenize_pbtk(input_str):");
    w.indent();
    w.line("tokens = []");
    w.line("i = 1 if input_str and input_str[0] == \"!\" else 0");
    w.line("while i < len(input_str):");
    w.indent();
    w.line("nxt = input_str.find(\"!\", i)");
    w.line("if nxt < 0:");
    w.indent();
    w.line("tokens.append(input_str[i:])");
    w.line("break");
    w.dedent();
    w.line("tokens.append(input_str[i:nxt])");
    w.line("i = nxt + 1");
    w.dedent();
    w.line("return tokens");
    w.dedent();
  }

  private void emitParseFromTokens(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    w.blankLine();
    w.line("@classmethod");
    w.line("def _parse_pbtk_tokens(cls, tokens, field_count, offset):");
    w.indent();

    // Lazy imports for cross-file types
    for (String imp : lazyImports) {
      w.line("%s  # lazy import to avoid circular dependency", imp);
    }

    w.line("obj = cls()");
    w.line("consumed = 0");
    w.line("while consumed < field_count and offset[0] < len(tokens):");
    w.indent();
    w.line("token = tokens[offset[0]]");
    // Parse field number and type char
    w.line("num_end = 0");
    w.line("while num_end < len(token) and token[num_end].isdigit():");
    w.indent();
    w.line("num_end += 1");
    w.dedent();
    w.line("if num_end == 0 or num_end >= len(token):");
    w.indent();
    w.line("offset[0] += 1");
    w.line("consumed += 1");
    w.line("continue");
    w.dedent();
    w.line("field_num = int(token[:num_end])");
    w.line("type_char = token[num_end]");
    w.line("value = token[num_end + 1:]");

    // Dispatch on field number
    boolean first = true;
    for (ProtoField field : message.getFields()) {
      if (first) {
        w.line("if field_num == %d:", field.getFieldNumber());
        first = false;
      } else {
        w.line("elif field_num == %d:", field.getFieldNumber());
      }
      w.indent();
      emitFieldDeserialize(w, field);
      w.dedent();
    }

    // Default: skip unknown field
    if (!message.getFields().isEmpty()) {
      w.line("el" + "se:");
    } else {
      // No fields at all: always skip
      w.line("if True:");
    }
    w.indent();
    w.line("offset[0] += 1");
    w.line("consumed += 1");
    w.dedent();

    w.dedent(); // end while
    w.line("return obj");
    w.dedent(); // end method
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

    // All non-message/non-map single paths end with offset[0]+=1; consumed+=1
    // (message and map paths handle offset internally)
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field) {
    String pyField = "obj._" + nameResolver.fieldName(field.getName());
    String readExpr = scalarReadExpr(field.getProtoType(), "value");
    w.line("%s = %s", pyField, readExpr);
    if (field.isProto3Optional()) {
      w.line("obj._present_fields[%d] = True", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = "obj._" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("%s = %d", caseName, field.getFieldNumber());
    }
    w.line("offset[0] += 1");
    w.line("consumed += 1");
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field) {
    String pyField = "obj._" + nameResolver.fieldName(field.getName());
    w.line("%s = int(value)", pyField);
    if (field.isProto3Optional()) {
      w.line("obj._present_fields[%d] = True", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = "obj._" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("%s = %d", caseName, field.getFieldNumber());
    }
    w.line("offset[0] += 1");
    w.line("consumed += 1");
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field) {
    String pyField = "obj._" + nameResolver.fieldName(field.getName());
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("sub_count = int(value)");
    w.line("offset[0] += 1");
    w.line("%s = %s._parse_pbtk_tokens(tokens, sub_count, offset)", pyField, msgType);
    if (field.isOneofMember()) {
      String caseName = "obj._" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("%s = %d", caseName, field.getFieldNumber());
    }
    // Don't increment offset again -- recursive call consumed sub-tokens,
    // but we need to count this as one consumed field
    w.line("consumed += 1");
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field) {
    String pyField = "obj._" + nameResolver.fieldName(field.getName());

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("sub_count = int(value)");
      w.line("offset[0] += 1");
      w.line("%s.append(%s._parse_pbtk_tokens(tokens, sub_count, offset))", pyField, msgType);
      w.line("consumed += 1");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("%s.append(int(value))", pyField);
      w.line("offset[0] += 1");
      w.line("consumed += 1");
    } else {
      String readExpr = scalarReadExpr(field.getProtoType(), "value");
      w.line("%s.append(%s)", pyField, readExpr);
      w.line("offset[0] += 1");
      w.line("consumed += 1");
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field) {
    String pyField = "obj._" + nameResolver.fieldName(field.getName());
    // Map entry: !<num>m2!1<keyType><key>!2<valType><val>
    w.line("entry_count = int(value)");
    w.line("offset[0] += 1");
    w.line("entry_key = None");
    w.line("entry_val = None");
    w.line("for __mi in range(entry_count):");
    w.indent();
    w.line("if offset[0] >= len(tokens):");
    w.indent();
    w.line("break");
    w.dedent();
    w.line("map_token = tokens[offset[0]]");
    w.line("mne = 0");
    w.line("while mne < len(map_token) and map_token[mne].isdigit():");
    w.indent();
    w.line("mne += 1");
    w.dedent();
    w.line("if mne == 0 or mne >= len(map_token):");
    w.indent();
    w.line("offset[0] += 1");
    w.line("continue");
    w.dedent();
    w.line("mfn = int(map_token[:mne])");
    w.line("mval = map_token[mne + 1:]");

    // Key (field 1)
    w.line("if mfn == 1:");
    w.indent();
    String keyRead = scalarReadExpr(field.getMapKeyType(), "mval");
    w.line("entry_key = %s", keyRead);
    w.dedent();

    // Value (field 2)
    w.line("if mfn == 2:");
    w.indent();
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("val_sub_count = int(mval)");
      w.line("offset[0] += 1");
      w.line("entry_val = %s._parse_pbtk_tokens(tokens, val_sub_count, offset)", msgType);
      w.line("offset[0] -= 1"); // compensate for the outer offset[0]++ below
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("entry_val = int(mval)");
    } else {
      String valRead = scalarReadExpr(field.getMapValueType(), "mval");
      w.line("entry_val = %s", valRead);
    }
    w.dedent();

    w.line("offset[0] += 1");
    w.dedent(); // end for

    // Compensate: the outer loop will call offset[0]+=1 and consumed+=1
    // but we already advanced offset past all the sub-tokens.
    // We need to NOT advance offset again, so decrement once:
    w.line("offset[0] -= 1");
    w.line("if entry_key is not None:");
    w.indent();
    w.line("%s[entry_key] = entry_val", pyField);
    w.dedent();
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
      case TYPE_DOUBLE, TYPE_FLOAT -> "float(" + valueVar + ")";
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
      case TYPE_BOOL -> valueVar + " == \"1\"";
      case TYPE_STRING -> "urllib.parse.unquote(" + valueVar + ")";
      case TYPE_BYTES -> "base64.b64decode(" + valueVar + ")";
      default -> valueVar;
    };
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "object";
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

      String simpleName = ProtoTypeUtil.simpleTypeName(typeRef);
      if (typeRef.startsWith(currentPrefix)) {
        String moduleName = pascalToSnake(simpleName);
        imports.add("from ." + moduleName + " import " + simpleName);
      }

      // Map value types
      if (field.isMap() && field.getMapValueTypeReference() != null) {
        String valRef = field.getMapValueTypeReference();
        if (registry != null && registry.isMapEntry(valRef)) continue;
        String valName = ProtoTypeUtil.simpleTypeName(valRef);
        if (valRef.startsWith(currentPrefix)) {
          String moduleName = pascalToSnake(valName);
          imports.add("from ." + moduleName + " import " + valName);
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

  /** Convert PascalCase to snake_case for Python module names. */
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

  private void collectPackageDirs(String filePath, Set<String> dirs) {
    int lastSlash = filePath.lastIndexOf('/');
    if (lastSlash > 0) {
      String dir = filePath.substring(0, lastSlash);
      while (!dir.isEmpty()) {
        dirs.add(dir);
        lastSlash = dir.lastIndexOf('/');
        if (lastSlash > 0) {
          dir = dir.substring(0, lastSlash);
        } else {
          dirs.add(dir);
          break;
        }
      }
    }
  }
}
