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
package dev.protocgen.textcodecs.pbtkurl.codegen.php;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.php.PhpNameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.php.PhpTypeMapper;
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
 * PHP language code generator for pbtk URL encoding. Produces PHP source files with
 * toPbtkUrl()/fromPbtkUrl() serialization methods.
 *
 * <p>The pbtk URL format encodes protobuf messages as URL strings using the syntax: {@code
 * !<fieldNumber><typeChar><value>} where type chars are: b=bool(0/1), i=integer, f=float, d=double,
 * s=string(URL-encoded), e=enum(int), m=message(count of sub-fields), z=bytes(base64).
 */
public class PbtkPhpGenerator implements LanguageGenerator {

  private final PhpNameResolver nameResolver = new PhpNameResolver();
  private final PhpTypeMapper typeMapper = new PhpTypeMapper();

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();

    for (ProtoMessage message : file.getMessages()) {
      nameResolver.validateFieldNames(message.getFields());

      Set<String> useStatements = new LinkedHashSet<>();
      collectReferencedTypes(message, file, useStatements, registry);

      String sourceCode = emitMessage(message, file, useStatements);
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
    return "php";
  }

  // ---------------------------------------------------------------------------
  // Top-level emission
  // ---------------------------------------------------------------------------

  private String emitMessage(ProtoMessage message, ProtoFile file, Set<String> useStatements) {
    CodeWriter w = new CodeWriter();

    // PHP header
    emitFileHeader(w, file);

    // Nested enums
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

    emitProperties(w, message);

    if (hasOptionalFields(message)) {
      w.blankLine();
      w.line("/** @var array<int, bool> */");
      w.line("public array $presentFields = [];");
    }

    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      w.blankLine();
      String caseName = nameResolver.fieldName(group.name()) + "Case";
      w.line("public int $%s = 0; // 0 = not set", caseName);
    }

    emitConstructor(w, message);
    emitGettersSetters(w, message);
    emitHasMethods(w, message);
    emitSerializer(w, message);
    emitDeserializer(w, message, className, useStatements);
    emitToString(w, message, className);

    w.dedent();
    w.line("}");

    return w.toString();
  }

  private String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
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

  // ---------------------------------------------------------------------------
  // Nested messages and enums
  // ---------------------------------------------------------------------------

  private void emitNestedMessage(
      CodeWriter w, ProtoMessage nested, ProtoFile file, Set<String> useStatements) {
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
    emitSerializer(w, nested);
    emitDeserializer(w, nested, className, useStatements);
    emitToString(w, nested, className);

    w.dedent();
    w.line("}");
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum) {
    w.line("/**");
    w.line(" * Proto enum %s represented as integer constants.", protoEnum.getName());
    w.line(" */");
    w.line("class %s", protoEnum.getName());
    w.line("{");
    w.indent();

    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("public const %s = %d;", nameResolver.enumConstantName(val.name()), val.number());
    }

    w.blankLine();
    w.line("private const BY_NUMBER = [");
    w.indent();
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%d => %d,", val.number(), val.number());
    }
    w.dedent();
    w.line("];");

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

  // ---------------------------------------------------------------------------
  // Properties, constructor, getters/setters, has methods
  // ---------------------------------------------------------------------------

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
      w.dedent();
      w.line("}");
      return;
    }

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

  // ---------------------------------------------------------------------------
  // Serializer: toPbtkUrl()
  // ---------------------------------------------------------------------------

  private void emitSerializer(CodeWriter w, ProtoMessage message) {
    // appendPbtkFields(array &$parts): void
    w.blankLine();
    w.line("/**");
    w.line(" * @param array<int, string> $parts");
    w.line(" */");
    w.line("public function appendPbtkFields(array &$parts): void");
    w.line("{");
    w.indent();
    if (message.getFields().isEmpty()) {
      // empty body
    } else {
      for (ProtoField field : message.getFields()) {
        emitFieldSerialize(w, field);
      }
    }
    w.dedent();
    w.line("}");

    // countPbtkFields(): int
    w.blankLine();
    w.line("public function countPbtkFields(): int");
    w.line("{");
    w.indent();
    w.line("$count = 0;");
    for (ProtoField field : message.getFields()) {
      emitFieldCount(w, field);
    }
    w.line("return $count;");
    w.dedent();
    w.line("}");

    // toPbtkUrl(): string
    w.blankLine();
    w.line("/**");
    w.line(" * Serialize this message to a pbtk URL-encoded string.");
    w.line(" */");
    w.line("public function toPbtkUrl(): string");
    w.line("{");
    w.indent();
    w.line("$parts = [];");
    w.line("$this->appendPbtkFields($parts);");
    w.line("return implode('', $parts);");
    w.dedent();
    w.line("}");
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String phpField = "$this->" + nameResolver.fieldName(field.getName());
    int fieldNum = field.getFieldNumber();

    if (field.isOneofMember()) {
      String caseName = "$this->" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.line("if (%s === %d) {", caseName, field.getFieldNumber());
      w.indent();
      emitSingleFieldSerialize(w, field, phpField, fieldNum);
      w.dedent();
      w.line("}");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, phpField, fieldNum);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, phpField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, phpField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, phpField, fieldNum);
    } else {
      emitScalarSerialize(w, field, phpField, fieldNum);
    }
  }

  private void emitSingleFieldSerialize(
      CodeWriter w, ProtoField field, String phpField, int fieldNum) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, phpField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, phpField, fieldNum);
    } else {
      emitScalarSerialize(w, field, phpField, fieldNum);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String phpField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if ($this->presentFields[%d]) {", field.getArrayPosition());
      w.indent();
      emitScalarAppend(w, field, phpField, fieldNum);
      w.dedent();
      w.line("}");
      return;
    }
    emitScalarAppend(w, field, phpField, fieldNum);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String phpField, int fieldNum) {
    FieldDescriptorProto.Type type = field.getProtoType();
    String typeChar = pbtkTypeChar(type);

    switch (type) {
      case TYPE_BOOL:
        w.line("$parts[] = '!%d%s' . (%s ? '1' : '0');", fieldNum, typeChar, phpField);
        break;
      case TYPE_BYTES:
        w.line("$parts[] = '!%d%s' . base64_encode(%s);", fieldNum, typeChar, phpField);
        break;
      case TYPE_STRING:
        w.line("$parts[] = '!%d%s' . rawurlencode(%s);", fieldNum, typeChar, phpField);
        break;
      case TYPE_DOUBLE:
      case TYPE_FLOAT:
        w.line("if (!is_nan(%s) && !is_infinite(%s)) {", phpField, phpField);
        w.indent();
        w.line("$parts[] = '!%d%s' . %s;", fieldNum, typeChar, phpField);
        w.dedent();
        w.line("}");
        break;
      default:
        w.line("$parts[] = '!%d%s' . %s;", fieldNum, typeChar, phpField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String phpField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if ($this->presentFields[%d]) {", field.getArrayPosition());
      w.indent();
      w.line("$parts[] = '!%de' . %s;", fieldNum, phpField);
      w.dedent();
      w.line("}");
    } else {
      w.line("$parts[] = '!%de' . %s;", fieldNum, phpField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String phpField, int fieldNum) {
    w.line("if (%s !== null) {", phpField);
    w.indent();
    w.line("$parts[] = '!%dm' . %s->countPbtkFields();", fieldNum, phpField);
    w.line("%s->appendPbtkFields($parts);", phpField);
    w.dedent();
    w.line("}");
  }

  private void emitRepeatedSerialize(
      CodeWriter w, ProtoField field, String phpField, int fieldNum) {
    w.line("foreach (%s as $item) {", phpField);
    w.indent();

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("if ($item !== null) {");
      w.indent();
      w.line("$parts[] = '!%dm' . $item->countPbtkFields();", fieldNum);
      w.line("$item->appendPbtkFields($parts);");
      w.dedent();
      w.line("}");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("$parts[] = '!%de' . $item;", fieldNum);
    } else {
      emitScalarAppend(w, field, "$item", fieldNum);
    }

    w.dedent();
    w.line("}");
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String phpField, int fieldNum) {
    // Maps serialize as repeated entries: !<fieldNum>m2!1<keyType><key>!2<valType><val>
    w.line("foreach (%s as $mk => $mv) {", phpField);
    w.indent();
    w.line("$parts[] = '!%dm2';", fieldNum);

    // Key (field 1)
    String keyTypeChar = pbtkTypeChar(field.getMapKeyType());
    switch (field.getMapKeyType()) {
      case TYPE_STRING:
        w.line("$parts[] = '!1%s' . rawurlencode((string) $mk);", keyTypeChar);
        break;
      case TYPE_BOOL:
        w.line("$parts[] = '!1%s' . ($mk ? '1' : '0');", keyTypeChar);
        break;
      default:
        w.line("$parts[] = '!1%s' . $mk;", keyTypeChar);
        break;
    }

    // Value (field 2)
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("if ($mv !== null) {");
      w.indent();
      w.line("$parts[] = '!2m' . $mv->countPbtkFields();");
      w.line("$mv->appendPbtkFields($parts);");
      w.dedent();
      w.line("}");
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("$parts[] = '!2e' . $mv;");
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("$parts[] = '!2s' . rawurlencode((string) $mv);");
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("$parts[] = '!2z' . base64_encode($mv);");
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("$parts[] = '!2b' . ($mv ? '1' : '0');");
    } else {
      String valTypeChar = pbtkTypeChar(field.getMapValueType());
      w.line("$parts[] = '!2%s' . $mv;", valTypeChar);
    }

    w.dedent();
    w.line("}");
  }

  private void emitFieldCount(CodeWriter w, ProtoField field) {
    String phpField = "$this->" + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = "$this->" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.line("if (%s === %d) {", caseName, field.getFieldNumber());
      w.indent();
      w.line("$count++;");
      w.dedent();
      w.line("}");
      return;
    }

    if (field.isMap()) {
      w.line("$count += count(%s);", phpField);
    } else if (field.isRepeated()) {
      if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
        // Only count non-null elements
        w.line("foreach (%s as $e) {", phpField);
        w.indent();
        w.line("if ($e !== null) { $count++; }");
        w.dedent();
        w.line("}");
      } else {
        w.line("$count += count(%s);", phpField);
      }
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("if (%s !== null) {", phpField);
      w.indent();
      w.line("$count++;");
      w.dedent();
      w.line("}");
    } else if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if ($this->presentFields[%d]) {", field.getArrayPosition());
      w.indent();
      w.line("$count++;");
      w.dedent();
      w.line("}");
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
      // Skip NaN and Inf
      w.line("if (!is_nan(%s) && !is_infinite(%s)) {", phpField, phpField);
      w.indent();
      w.line("$count++;");
      w.dedent();
      w.line("}");
    } else {
      w.line("$count++;");
    }
  }

  // ---------------------------------------------------------------------------
  // Deserializer: fromPbtkUrl()
  // ---------------------------------------------------------------------------

  private void emitDeserializer(
      CodeWriter w, ProtoMessage message, String className, Set<String> useStatements) {
    // Internal: parsePbtkTokens(array $tokens, int $fieldCount, array &$offset): self
    emitParseFromTokens(w, message, className, useStatements);

    // Public: fromPbtkUrl(string $inputStr): self
    w.blankLine();
    w.line("/**");
    w.line(" * Deserialize a pbtk URL-encoded string into a %s instance.", className);
    w.line(" */");
    w.line("public static function fromPbtkUrl(string $inputStr): self");
    w.line("{");
    w.indent();
    w.line("if ($inputStr === '') {");
    w.indent();
    w.line("return new self();");
    w.dedent();
    w.line("}");
    w.line("$tokens = self::tokenizePbtk($inputStr);");
    w.line("$offset = [0];");
    w.line("return self::parsePbtkTokens($tokens, count($tokens), $offset);");
    w.dedent();
    w.line("}");

    // Tokenizer: tokenizePbtk(string $inputStr): array
    w.blankLine();
    w.line("/**");
    w.line(" * @return array<int, string>");
    w.line(" */");
    w.line("private static function tokenizePbtk(string $inputStr): array");
    w.line("{");
    w.indent();
    w.line("$tokens = [];");
    w.line("$i = ($inputStr !== '' && $inputStr[0] === '!') ? 1 : 0;");
    w.line("$len = strlen($inputStr);");
    w.line("while ($i < $len) {");
    w.indent();
    w.line("$nxt = strpos($inputStr, '!', $i);");
    w.line("if ($nxt === false) {");
    w.indent();
    w.line("$tokens[] = substr($inputStr, $i);");
    w.line("break;");
    w.dedent();
    w.line("}");
    w.line("$tokens[] = substr($inputStr, $i, $nxt - $i);");
    w.line("$i = $nxt + 1;");
    w.dedent();
    w.line("}");
    w.line("return $tokens;");
    w.dedent();
    w.line("}");
  }

  private void emitParseFromTokens(
      CodeWriter w, ProtoMessage message, String className, Set<String> useStatements) {
    w.blankLine();
    w.line("/**");
    w.line(" * @param array<int, string> $tokens");
    w.line(" * @param int[] $offset");
    w.line(" */");
    w.line(
        "private static function parsePbtkTokens(array $tokens, int $fieldCount, array &$offset): self");
    w.line("{");
    w.indent();

    w.line("$obj = new self();");
    w.line("$consumed = 0;");
    w.line("while ($consumed < $fieldCount && $offset[0] < count($tokens)) {");
    w.indent();
    w.line("$token = $tokens[$offset[0]];");
    // Parse field number and type char
    w.line("$numEnd = 0;");
    w.line("$tokenLen = strlen($token);");
    w.line("while ($numEnd < $tokenLen && ctype_digit($token[$numEnd])) {");
    w.indent();
    w.line("$numEnd++;");
    w.dedent();
    w.line("}");
    w.line("if ($numEnd === 0 || $numEnd >= $tokenLen) {");
    w.indent();
    w.line("$offset[0]++;");
    w.line("$consumed++;");
    w.line("continue;");
    w.dedent();
    w.line("}");
    w.line("$fieldNum = (int) substr($token, 0, $numEnd);");
    w.line("$typeChar = $token[$numEnd];");
    w.line("$value = substr($token, $numEnd + 1);");

    // Dispatch on field number
    boolean first = true;
    for (ProtoField field : message.getFields()) {
      if (first) {
        w.line("if ($fieldNum === %d) {", field.getFieldNumber());
        first = false;
      } else {
        w.line("} elseif ($fieldNum === %d) {", field.getFieldNumber());
      }
      w.indent();
      emitFieldDeserialize(w, field);
      w.dedent();
    }

    // Default: skip unknown field
    if (!message.getFields().isEmpty()) {
      w.line("} else {");
    } else {
      w.line("if (true) {");
    }
    w.indent();
    w.line("$offset[0]++;");
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
    String phpField = "$obj->" + nameResolver.fieldName(field.getName());
    String readExpr = scalarReadExpr(field.getProtoType(), "$value");
    w.line("%s = %s;", phpField, readExpr);
    if (field.isProto3Optional()) {
      w.line("$obj->presentFields[%d] = true;", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = "$obj->" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }
    w.line("$offset[0]++;");
    w.line("$consumed++;");
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field) {
    String phpField = "$obj->" + nameResolver.fieldName(field.getName());
    w.line("%s = (int) $value;", phpField);
    if (field.isProto3Optional()) {
      w.line("$obj->presentFields[%d] = true;", field.getArrayPosition());
    }
    if (field.isOneofMember()) {
      String caseName = "$obj->" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }
    w.line("$offset[0]++;");
    w.line("$consumed++;");
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field) {
    String phpField = "$obj->" + nameResolver.fieldName(field.getName());
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("$subCount = (int) $value;");
    w.line("$offset[0]++;");
    w.line("%s = %s::parsePbtkTokens($tokens, $subCount, $offset);", phpField, msgType);
    if (field.isOneofMember()) {
      String caseName = "$obj->" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }
    w.line("$consumed++;");
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field) {
    String phpField = "$obj->" + nameResolver.fieldName(field.getName());

    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("$subCount = (int) $value;");
      w.line("$offset[0]++;");
      w.line("%s[] = %s::parsePbtkTokens($tokens, $subCount, $offset);", phpField, msgType);
      w.line("$consumed++;");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("%s[] = (int) $value;", phpField);
      w.line("$offset[0]++;");
      w.line("$consumed++;");
    } else {
      String readExpr = scalarReadExpr(field.getProtoType(), "$value");
      w.line("%s[] = %s;", phpField, readExpr);
      w.line("$offset[0]++;");
      w.line("$consumed++;");
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field) {
    String phpField = "$obj->" + nameResolver.fieldName(field.getName());
    // Map entry: !<num>m2!1<keyType><key>!2<valType><val>
    w.line("$entryCount = (int) $value;");
    w.line("$offset[0]++;");
    w.line("$entryKey = null;");
    w.line("$entryVal = null;");
    w.line("for ($mi = 0; $mi < $entryCount; $mi++) {");
    w.indent();
    w.line("if ($offset[0] >= count($tokens)) {");
    w.indent();
    w.line("break;");
    w.dedent();
    w.line("}");
    w.line("$mapToken = $tokens[$offset[0]];");
    w.line("$mne = 0;");
    w.line("$mtLen = strlen($mapToken);");
    w.line("while ($mne < $mtLen && ctype_digit($mapToken[$mne])) {");
    w.indent();
    w.line("$mne++;");
    w.dedent();
    w.line("}");
    w.line("if ($mne === 0 || $mne >= $mtLen) {");
    w.indent();
    w.line("$offset[0]++;");
    w.line("continue;");
    w.dedent();
    w.line("}");
    w.line("$mfn = (int) substr($mapToken, 0, $mne);");
    w.line("$mval = substr($mapToken, $mne + 1);");

    // Key (field 1)
    w.line("if ($mfn === 1) {");
    w.indent();
    String keyRead = scalarReadExpr(field.getMapKeyType(), "$mval");
    w.line("$entryKey = %s;", keyRead);
    w.dedent();
    w.line("}");

    // Value (field 2)
    w.line("if ($mfn === 2) {");
    w.indent();
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("$valSubCount = (int) $mval;");
      w.line("$offset[0]++;");
      w.line("$entryVal = %s::parsePbtkTokens($tokens, $valSubCount, $offset);", msgType);
      w.line("$offset[0]--;"); // compensate for outer offset++ below
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("$entryVal = (int) $mval;");
    } else {
      String valRead = scalarReadExpr(field.getMapValueType(), "$mval");
      w.line("$entryVal = %s;", valRead);
    }
    w.dedent();
    w.line("}");

    w.line("$offset[0]++;");
    w.dedent(); // end for
    w.line("}");

    w.line("$offset[0]--;");
    w.line("if ($entryKey !== null) {");
    w.indent();
    w.line("%s[$entryKey] = $entryVal;", phpField);
    w.dedent();
    w.line("}");
    w.line("$offset[0]++;");
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
      case TYPE_DOUBLE, TYPE_FLOAT -> "(float) " + valueVar;
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
          "(int) " + valueVar;
      case TYPE_BOOL -> valueVar + " === '1'";
      case TYPE_STRING -> "rawurldecode(" + valueVar + ")";
      case TYPE_BYTES -> "base64_decode(" + valueVar + ")";
      default -> valueVar;
    };
  }

  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "mixed";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  private void collectReferencedTypes(
      ProtoMessage message, ProtoFile file, Set<String> uses, TypeRegistry registry) {
    // PHP classes in the same namespace don't need use statements
    // Cross-namespace references would need use statements, but for now
    // the same-file nested messages pattern handles most cases
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectReferencedTypes(nested, file, uses, registry);
    }
  }

  private boolean hasOptionalFields(ProtoMessage message) {
    return message.getFields().stream().anyMatch(ProtoField::isProto3Optional);
  }
}
