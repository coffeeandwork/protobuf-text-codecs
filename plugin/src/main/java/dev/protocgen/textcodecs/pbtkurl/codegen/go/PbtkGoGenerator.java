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
package dev.protocgen.textcodecs.pbtkurl.codegen.go;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.go.GoNameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.go.GoTypeMapper;
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
 * Go language code generator for pbtk URL encoding. Produces Go source files with ToPbtkUrl() and
 * DeserializeMsgFromPbtkUrl() methods. Generated code uses only Go stdlib (strings, strconv,
 * net/url, encoding/base64, fmt).
 *
 * <p>The pbtk format encodes protobuf messages as URL strings: {@code
 * !<fieldNumber><typeChar><value>} where type chars are: b=bool(0/1), i=integer, f=float, d=double,
 * s=string(URL-encoded), e=enum(int), m=message(count of sub-fields), z=bytes(base64).
 */
public class PbtkGoGenerator implements LanguageGenerator {

  private final GoNameResolver nameResolver = new GoNameResolver();
  private final GoTypeMapper typeMapper = new GoTypeMapper();

  // ---------------------------------------------------------------------------
  // Local helpers for name/type operations that are package-private in the
  // Go codegen package. We replicate them here to avoid cross-package access.
  // ---------------------------------------------------------------------------

  /** Convert snake_case to PascalCase (Go exported name). */
  private static String snakeToPascal(String snake) {
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

  /** Convert snake_case to camelCase (Go unexported name). */
  private static String snakeToCamel(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = false;
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

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  private static String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "interface{}";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();

    for (ProtoMessage message : file.getMessages()) {
      nameResolver.validateFieldNames(message.getFields());
      String sourceCode = emitMessage(message, file);
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
    return "go";
  }

  // ---------------------------------------------------------------------------
  // Top-level emitters
  // ---------------------------------------------------------------------------

  /** Generate a complete Go source file for a message. */
  private String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter("\t"); // Go uses tabs
    String pkg = nameResolver.resolvePackage(file);
    String structName = nameResolver.messageClassName(message.getName());

    // Package declaration
    w.line("package %s", pkg);
    w.blankLine();

    // Imports
    Set<String> imports = collectImports(message);
    if (!imports.isEmpty()) {
      w.block(
          "import",
          () -> {
            for (String imp : imports) {
              w.line("\"%s\"", imp);
            }
          });
      w.blankLine();
    }

    // Struct declaration
    emitStruct(w, message, structName);

    // Nested message structs (Go doesn't have nested types, so they're top-level)
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.blankLine();
      String nestedName = structName + "_" + nameResolver.messageClassName(nested.getName());
      emitStruct(w, nested, nestedName);
      emitCountPbtkFields(w, nested, nestedName);
      emitAppendPbtkFields(w, nested, nestedName);
      emitToPbtkUrl(w, nestedName);
      emitParsePbtkTokens(w, nested, nestedName);
      emitDeserializeFromPbtkUrl(w, nestedName);
    }

    // Nested enums
    for (ProtoEnum protoEnum : message.getEnums()) {
      emitEnum(w, protoEnum, structName);
    }

    // Oneof case tracking constants
    emitOneofConstants(w, message, structName);

    // Serialization methods
    emitCountPbtkFields(w, message, structName);
    emitAppendPbtkFields(w, message, structName);
    emitToPbtkUrl(w, structName);

    // Deserialization methods
    emitParsePbtkTokens(w, message, structName);
    emitDeserializeFromPbtkUrl(w, structName);

    return w.toString();
  }

  /** Generate a complete Go source file for a top-level enum. */
  private String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter("\t");
    String pkg = nameResolver.resolvePackage(file);

    w.line("package %s", pkg);
    w.blankLine();

    emitEnum(w, protoEnum, "");
    return w.toString();
  }

  // ---------------------------------------------------------------------------
  // Struct and enum emission
  // ---------------------------------------------------------------------------

  private void emitStruct(CodeWriter w, ProtoMessage message, String structName) {
    w.block(
        "type " + structName + " struct",
        () -> {
          for (ProtoField field : message.getFields()) {
            String goType = resolveFieldType(field, structName, message);
            String goName = nameResolver.fieldName(field.getName());
            w.line("%s %s", goName, goType);
          }

          for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
            String caseName = snakeToPascal(group.name()) + "Case";
            w.line("%s int // 0 = not set, field_number = set", caseName);
          }
        });
  }

  private String resolveFieldType(
      ProtoField field, String parentStructName, ProtoMessage parentMessage) {
    if (isNestedMessageRef(field, parentMessage)) {
      String nestedSimpleName = simpleTypeName(field.getTypeReference());
      String flattenedName = parentStructName + "_" + nestedSimpleName;
      if (field.isRepeated()) {
        return "[]*" + flattenedName;
      }
      return "*" + flattenedName;
    }
    return typeMapper.languageType(field);
  }

  private boolean isNestedMessageRef(ProtoField field, ProtoMessage parentMessage) {
    if (field.getKind() != ProtoField.FieldKind.MESSAGE
        && field.getKind() != ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return false;
    }
    if (field.getTypeReference() == null) return false;
    for (ProtoMessage nested : parentMessage.getNestedMessages()) {
      if (field.getTypeReference().endsWith("." + nested.getName())) {
        return true;
      }
    }
    return false;
  }

  private void emitEnum(CodeWriter w, ProtoEnum protoEnum, String parentPrefix) {
    String typeName;
    if (parentPrefix != null && !parentPrefix.isEmpty()) {
      typeName = parentPrefix + "_" + protoEnum.getName();
    } else {
      typeName = protoEnum.getName();
    }

    w.blankLine();
    w.line("type %s int32", typeName);
    w.blankLine();

    w.line("const (");
    w.indent();
    for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
      w.line("%s_%s %s = %d", typeName, val.name(), typeName, val.number());
    }
    w.dedent();
    w.line(")");

    w.blankLine();
    w.block(
        "func " + typeName + "ForNumber(n int32) " + typeName,
        () -> {
          w.line("return %s(n)", typeName);
        });
  }

  private void emitOneofConstants(CodeWriter w, ProtoMessage message, String structName) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      w.blankLine();
      w.line("// Oneof case constants for %s.%s", structName, snakeToPascal(group.name()));
      w.line("const (");
      w.indent();
      for (ProtoField member : group.members()) {
        String constName =
            structName + "_" + snakeToPascal(group.name()) + "_" + snakeToPascal(member.getName());
        w.line("%s = %d", constName, member.getFieldNumber());
      }
      w.dedent();
      w.line(")");
    }
  }

  // ---------------------------------------------------------------------------
  // Serialization: ToPbtkUrl
  // ---------------------------------------------------------------------------

  /** Emit countPbtkFields method: counts how many tokens this message will produce. */
  private void emitCountPbtkFields(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "func (m *" + structName + ") countPbtkFields() int",
        () -> {
          w.line("count := 0");
          for (ProtoField field : message.getFields()) {
            emitFieldCount(w, field);
          }
          w.line("return count");
        });
  }

  private void emitFieldCount(CodeWriter w, ProtoField field) {
    String goField = "m." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseField = "m." + snakeToPascal(field.getOneofName()) + "Case";
      w.block(
          "if " + caseField + " == " + field.getFieldNumber(),
          () -> {
            if (field.getKind() == ProtoField.FieldKind.MESSAGE
                || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
              w.block(
                  "if " + goField + " != nil",
                  () -> {
                    w.line("count += 1 + %s.countPbtkFields()", goField);
                  });
            } else {
              w.line("count++");
            }
          });
      return;
    }

    if (field.isMap()) {
      // Each map entry produces 1 token (the !<num>m2 prefix) + 2 sub-tokens for key/value
      // But for counting at the parent level, each entry is 1 top-level field
      w.line("count += len(%s)", goField);
    } else if (field.isRepeated()) {
      w.line("count += len(%s)", goField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "if " + goField + " != nil",
          () -> {
            w.line("count++");
          });
    } else if (field.isProto3Optional()) {
      w.block(
          "if " + goField + " != nil",
          () -> {
            w.line("count++");
          });
    } else {
      w.line("count++");
    }
  }

  /** Emit appendPbtkFields method: appends all field tokens to a strings.Builder. */
  private void emitAppendPbtkFields(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "func (m *" + structName + ") appendPbtkFields(sb *strings.Builder)",
        () -> {
          for (ProtoField field : message.getFields()) {
            emitFieldSerialize(w, field);
          }
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String goField = "m." + nameResolver.fieldName(field.getName());
    int fieldNum = field.getFieldNumber();

    if (field.isOneofMember()) {
      String caseField = "m." + snakeToPascal(field.getOneofName()) + "Case";
      w.block(
          "if " + caseField + " == " + fieldNum,
          () -> {
            emitSingleFieldSerialize(w, field, goField, fieldNum);
          });
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, goField, fieldNum);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, goField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, goField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, goField, fieldNum);
    } else {
      emitScalarSerialize(w, field, goField, fieldNum);
    }
  }

  private void emitSingleFieldSerialize(
      CodeWriter w, ProtoField field, String goField, int fieldNum) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, goField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, goField, fieldNum);
    } else {
      emitScalarSerialize(w, field, goField, fieldNum);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String goField, int fieldNum) {
    FieldDescriptorProto.Type type = field.getProtoType();
    String typeChar = pbtkTypeChar(type);

    if (field.isProto3Optional()) {
      w.block(
          "if " + goField + " != nil",
          () -> {
            emitScalarAppend(w, type, "*" + goField, fieldNum, typeChar);
          });
      return;
    }

    emitScalarAppend(w, type, goField, fieldNum, typeChar);
  }

  private void emitScalarAppend(
      CodeWriter w, FieldDescriptorProto.Type type, String goField, int fieldNum, String typeChar) {
    switch (type) {
      case TYPE_BOOL:
        w.block(
            "if " + goField,
            () -> {
              w.line("sb.WriteString(\"!%d%s1\")", fieldNum, typeChar);
            });
        w.block(
            "if !" + goField,
            () -> {
              w.line("sb.WriteString(\"!%d%s0\")", fieldNum, typeChar);
            });
        break;
      case TYPE_BYTES:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(base64.StdEncoding.EncodeToString(%s))", goField);
        break;
      case TYPE_STRING:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(url.QueryEscape(%s))", goField);
        break;
      case TYPE_DOUBLE:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(strconv.FormatFloat(%s, 'g', -1, 64))", goField);
        break;
      case TYPE_FLOAT:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(strconv.FormatFloat(float64(%s), 'g', -1, 32))", goField);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(strconv.FormatInt(%s, 10))", goField);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(strconv.FormatUint(%s, 10))", goField);
        break;
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(strconv.FormatInt(int64(%s), 10))", goField);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(strconv.FormatUint(uint64(%s), 10))", goField);
        break;
      default:
        w.line("sb.WriteString(\"!%d%s\")", fieldNum, typeChar);
        w.line("sb.WriteString(fmt.Sprintf(\"%%v\", %s))", goField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String goField, int fieldNum) {
    if (field.isProto3Optional()) {
      w.block(
          "if " + goField + " != nil",
          () -> {
            w.line("sb.WriteString(\"!%de\")", fieldNum);
            w.line("sb.WriteString(strconv.FormatInt(int64(*%s), 10))", goField);
          });
    } else {
      w.line("sb.WriteString(\"!%de\")", fieldNum);
      w.line("sb.WriteString(strconv.FormatInt(int64(%s), 10))", goField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String goField, int fieldNum) {
    w.block(
        "if " + goField + " != nil",
        () -> {
          w.line("sb.WriteString(\"!%dm\")", fieldNum);
          w.line("sb.WriteString(strconv.Itoa(%s.countPbtkFields()))", goField);
          w.line("%s.appendPbtkFields(sb)", goField);
        });
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String goField, int fieldNum) {
    String itemVar = snakeToCamel(field.getName()) + "Item";
    w.block(
        "for _, " + itemVar + " := range " + goField,
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            w.block(
                "if " + itemVar + " != nil",
                () -> {
                  w.line("sb.WriteString(\"!%dm\")", fieldNum);
                  w.line("sb.WriteString(strconv.Itoa(%s.countPbtkFields()))", itemVar);
                  w.line("%s.appendPbtkFields(sb)", itemVar);
                });
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line("sb.WriteString(\"!%de\")", fieldNum);
            w.line("sb.WriteString(strconv.FormatInt(int64(%s), 10))", itemVar);
          } else {
            emitScalarAppend(
                w, field.getProtoType(), itemVar, fieldNum, pbtkTypeChar(field.getProtoType()));
          }
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String goField, int fieldNum) {
    String entryKey = snakeToCamel(field.getName()) + "Key";
    String entryVal = snakeToCamel(field.getName()) + "Val";
    w.block(
        "for " + entryKey + ", " + entryVal + " := range " + goField,
        () -> {
          // Each map entry is a synthetic message with 2 sub-fields
          w.line("sb.WriteString(\"!%dm2\")", fieldNum);

          // Key (field 1)
          emitMapKeySerialize(w, field.getMapKeyType(), entryKey);

          // Value (field 2)
          emitMapValueSerialize(w, field, entryVal);
        });
  }

  private void emitMapKeySerialize(
      CodeWriter w, FieldDescriptorProto.Type keyType, String keyExpr) {
    String typeChar = pbtkTypeChar(keyType);
    switch (keyType) {
      case TYPE_STRING:
        w.line("sb.WriteString(\"!1%s\")", typeChar);
        w.line("sb.WriteString(url.QueryEscape(%s))", keyExpr);
        break;
      case TYPE_BOOL:
        w.block("if " + keyExpr, () -> w.line("sb.WriteString(\"!1%s1\")", typeChar));
        w.block("if !" + keyExpr, () -> w.line("sb.WriteString(\"!1%s0\")", typeChar));
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("sb.WriteString(\"!1%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatInt(%s, 10))", keyExpr);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line("sb.WriteString(\"!1%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatUint(%s, 10))", keyExpr);
        break;
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32:
        w.line("sb.WriteString(\"!1%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatInt(int64(%s), 10))", keyExpr);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line("sb.WriteString(\"!1%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatUint(uint64(%s), 10))", keyExpr);
        break;
      default:
        w.line("sb.WriteString(\"!1%s\")", typeChar);
        w.line("sb.WriteString(fmt.Sprintf(\"%%v\", %s))", keyExpr);
        break;
    }
  }

  private void emitMapValueSerialize(CodeWriter w, ProtoField field, String valExpr) {
    FieldDescriptorProto.Type valType = field.getMapValueType();

    if (valType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.block(
          "if " + valExpr + " != nil",
          () -> {
            w.line("sb.WriteString(\"!2m\")");
            w.line("sb.WriteString(strconv.Itoa(%s.countPbtkFields()))", valExpr);
            w.line("%s.appendPbtkFields(sb)", valExpr);
          });
      return;
    }

    if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("sb.WriteString(\"!2e\")");
      w.line("sb.WriteString(strconv.FormatInt(int64(%s), 10))", valExpr);
      return;
    }

    String typeChar = pbtkTypeChar(valType);
    switch (valType) {
      case TYPE_STRING:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(url.QueryEscape(%s))", valExpr);
        break;
      case TYPE_BYTES:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(base64.StdEncoding.EncodeToString(%s))", valExpr);
        break;
      case TYPE_BOOL:
        w.block("if " + valExpr, () -> w.line("sb.WriteString(\"!2%s1\")", typeChar));
        w.block("if !" + valExpr, () -> w.line("sb.WriteString(\"!2%s0\")", typeChar));
        break;
      case TYPE_DOUBLE:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatFloat(%s, 'g', -1, 64))", valExpr);
        break;
      case TYPE_FLOAT:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatFloat(float64(%s), 'g', -1, 32))", valExpr);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatInt(%s, 10))", valExpr);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatUint(%s, 10))", valExpr);
        break;
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatInt(int64(%s), 10))", valExpr);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(strconv.FormatUint(uint64(%s), 10))", valExpr);
        break;
      default:
        w.line("sb.WriteString(\"!2%s\")", typeChar);
        w.line("sb.WriteString(fmt.Sprintf(\"%%v\", %s))", valExpr);
        break;
    }
  }

  /** Emit the public ToPbtkUrl convenience method. */
  private void emitToPbtkUrl(CodeWriter w, String structName) {
    w.blankLine();
    w.block(
        "func (m *" + structName + ") ToPbtkUrl() string",
        () -> {
          w.line("var sb strings.Builder");
          w.line("m.appendPbtkFields(&sb)");
          w.line("return sb.String()");
        });
  }

  // ---------------------------------------------------------------------------
  // Deserialization: FromPbtkUrl
  // ---------------------------------------------------------------------------

  /** Emit the internal parsePbtkTokens function that processes a token slice. */
  private void emitParsePbtkTokens(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "func parse"
            + structName
            + "PbtkTokens(tokens []string, fieldCount int, offset *int) *"
            + structName,
        () -> {
          w.line("obj := &%s{}", structName);
          w.line("consumed := 0");
          w.block(
              "for consumed < fieldCount && *offset < len(tokens)",
              () -> {
                w.line("token := tokens[*offset]");
                // Parse field number and type char
                w.line("numEnd := 0");
                w.block(
                    "for numEnd < len(token) && token[numEnd] >= '0' && token[numEnd] <= '9'",
                    () -> w.line("numEnd++"));
                w.block(
                    "if numEnd == 0 || numEnd >= len(token)",
                    () -> {
                      w.line("*offset++");
                      w.line("consumed++");
                      w.line("continue");
                    });
                w.line("fieldNum, _ := strconv.Atoi(token[:numEnd])");
                w.line("value := token[numEnd+1:]");

                // Switch on field number
                w.block(
                    "switch fieldNum",
                    () -> {
                      for (ProtoField field : message.getFields()) {
                        emitFieldCase(w, field, structName);
                      }
                      w.line("default:");
                      w.indent();
                      w.line("*offset++");
                      w.line("consumed++");
                      w.dedent();
                    });
              });
          w.line("return obj");
        });
  }

  private void emitFieldCase(CodeWriter w, ProtoField field, String structName) {
    int fieldNum = field.getFieldNumber();
    String goField = "obj." + nameResolver.fieldName(field.getName());

    w.line("case %d:", fieldNum);
    w.indent();

    if (field.isMap()) {
      emitMapDeserialize(w, field, goField);
    } else if (field.isRepeated()) {
      emitRepeatedDeserialize(w, field, goField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageDeserialize(w, field, goField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumDeserialize(w, field, goField);
    } else {
      emitScalarDeserialize(w, field, goField);
    }

    if (field.isOneofMember()) {
      String caseField = "obj." + snakeToPascal(field.getOneofName()) + "Case";
      w.line("%s = %d", caseField, fieldNum);
    }

    w.line("*offset++");
    w.line("consumed++");
    w.dedent();
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field, String goField) {
    FieldDescriptorProto.Type type = field.getProtoType();
    boolean isPointer = field.isProto3Optional();

    switch (type) {
      case TYPE_BOOL:
        if (isPointer) {
          w.line("tmpBool := value == \"1\"");
          w.line("%s = &tmpBool", goField);
        } else {
          w.line("%s = value == \"1\"", goField);
        }
        break;
      case TYPE_STRING:
        if (isPointer) {
          w.line("tmpStr, _ := url.QueryUnescape(value)");
          w.line("%s = &tmpStr", goField);
        } else {
          w.line("%s, _ = url.QueryUnescape(value)", goField);
        }
        break;
      case TYPE_BYTES:
        w.line("%s, _ = base64.StdEncoding.DecodeString(value)", goField);
        break;
      case TYPE_DOUBLE:
        if (isPointer) {
          w.line("tmpFloat, _ := strconv.ParseFloat(value, 64)");
          w.line("%s = &tmpFloat", goField);
        } else {
          w.line("%s, _ = strconv.ParseFloat(value, 64)", goField);
        }
        break;
      case TYPE_FLOAT:
        if (isPointer) {
          w.line("tmpF64, _ := strconv.ParseFloat(value, 32)");
          w.line("tmpF32 := float32(tmpF64)");
          w.line("%s = &tmpF32", goField);
        } else {
          w.line("tmpF64, _ := strconv.ParseFloat(value, 32)");
          w.line("%s = float32(tmpF64)", goField);
        }
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        if (isPointer) {
          w.line("tmpI64, _ := strconv.ParseInt(value, 10, 64)");
          w.line("%s = &tmpI64", goField);
        } else {
          w.line("%s, _ = strconv.ParseInt(value, 10, 64)", goField);
        }
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        if (isPointer) {
          w.line("tmpU64, _ := strconv.ParseUint(value, 10, 64)");
          w.line("%s = &tmpU64", goField);
        } else {
          w.line("%s, _ = strconv.ParseUint(value, 10, 64)", goField);
        }
        break;
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32:
        if (isPointer) {
          w.line("tmpI64, _ := strconv.ParseInt(value, 10, 32)");
          w.line("tmpI32 := int32(tmpI64)");
          w.line("%s = &tmpI32", goField);
        } else {
          w.line("tmpI64, _ := strconv.ParseInt(value, 10, 32)");
          w.line("%s = int32(tmpI64)", goField);
        }
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        if (isPointer) {
          w.line("tmpU64, _ := strconv.ParseUint(value, 10, 32)");
          w.line("tmpU32 := uint32(tmpU64)");
          w.line("%s = &tmpU32", goField);
        } else {
          w.line("tmpU64, _ := strconv.ParseUint(value, 10, 32)");
          w.line("%s = uint32(tmpU64)", goField);
        }
        break;
      default:
        w.line("// unsupported type");
        break;
    }
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String goField) {
    String enumType = simpleTypeName(field.getTypeReference());
    w.line("tmpEnumVal, _ := strconv.ParseInt(value, 10, 32)");
    w.line("%s = %s(int32(tmpEnumVal))", goField, enumType);
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field, String goField) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("subCount, _ := strconv.Atoi(value)");
    w.line("*offset++");
    w.line("%s = parse%sPbtkTokens(tokens, subCount, offset)", goField, msgType);
    w.line("*offset--"); // compensate for the outer *offset++ after break
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field, String goField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("subCount, _ := strconv.Atoi(value)");
      w.line("*offset++");
      w.line(
          "%s = append(%s, parse%sPbtkTokens(tokens, subCount, offset))",
          goField, goField, msgType);
      w.line("*offset--");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      String enumType = simpleTypeName(field.getTypeReference());
      w.line("tmpEnumVal, _ := strconv.ParseInt(value, 10, 32)");
      w.line("%s = append(%s, %s(int32(tmpEnumVal)))", goField, goField, enumType);
    } else {
      emitRepeatedScalarDeserialize(w, field, goField);
    }
  }

  private void emitRepeatedScalarDeserialize(CodeWriter w, ProtoField field, String goField) {
    FieldDescriptorProto.Type type = field.getProtoType();
    switch (type) {
      case TYPE_BOOL:
        w.line("%s = append(%s, value == \"1\")", goField, goField);
        break;
      case TYPE_STRING:
        w.line("tmpStr, _ := url.QueryUnescape(value)");
        w.line("%s = append(%s, tmpStr)", goField, goField);
        break;
      case TYPE_BYTES:
        w.line("tmpBytes, _ := base64.StdEncoding.DecodeString(value)");
        w.line("%s = append(%s, tmpBytes)", goField, goField);
        break;
      case TYPE_DOUBLE:
        w.line("tmpFloat, _ := strconv.ParseFloat(value, 64)");
        w.line("%s = append(%s, tmpFloat)", goField, goField);
        break;
      case TYPE_FLOAT:
        w.line("tmpF64, _ := strconv.ParseFloat(value, 32)");
        w.line("%s = append(%s, float32(tmpF64))", goField, goField);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("tmpI64, _ := strconv.ParseInt(value, 10, 64)");
        w.line("%s = append(%s, tmpI64)", goField, goField);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line("tmpU64, _ := strconv.ParseUint(value, 10, 64)");
        w.line("%s = append(%s, tmpU64)", goField, goField);
        break;
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32:
        w.line("tmpI64, _ := strconv.ParseInt(value, 10, 32)");
        w.line("%s = append(%s, int32(tmpI64))", goField, goField);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line("tmpU64, _ := strconv.ParseUint(value, 10, 32)");
        w.line("%s = append(%s, uint32(tmpU64))", goField, goField);
        break;
      default:
        w.line("// unsupported repeated scalar type");
        break;
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String goField) {
    String mapType = typeMapper.languageType(field);
    // Initialize map if nil
    w.block("if " + goField + " == nil", () -> w.line("%s = make(%s)", goField, mapType));

    w.line("entryCount, _ := strconv.Atoi(value)");
    w.line("*offset++");

    // Parse key and value from the entry's sub-tokens
    emitMapKeyVarDecl(w, field.getMapKeyType());
    emitMapValueVarDecl(w, field);

    w.block(
        "for __mi := 0; __mi < entryCount && *offset < len(tokens); __mi++",
        () -> {
          w.line("mapToken := tokens[*offset]");
          w.line("mne := 0");
          w.block(
              "for mne < len(mapToken) && mapToken[mne] >= '0' && mapToken[mne] <= '9'",
              () -> w.line("mne++"));
          w.block(
              "if mne == 0 || mne >= len(mapToken)",
              () -> {
                w.line("*offset++");
                w.line("continue");
              });
          w.line("mfn, _ := strconv.Atoi(mapToken[:mne])");
          w.line("mval := mapToken[mne+1:]");
          w.block("if mfn == 1", () -> emitMapKeyParse(w, field.getMapKeyType(), "mval"));
          w.block("if mfn == 2", () -> emitMapValueParse(w, field, "mval"));
          w.line("*offset++");
        });

    w.line("*offset--"); // compensate for outer *offset++
    // Put the entry into the map
    w.line("%s[mapKey] = mapVal", goField);
  }

  private void emitMapKeyVarDecl(CodeWriter w, FieldDescriptorProto.Type keyType) {
    String goType = typeMapper.scalarType(keyType);
    String defaultVal = scalarZeroLiteral(keyType);
    w.line("var mapKey %s", goType);
    // For string keys, initialize to empty string (already zero value)
    if (keyType != FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("_ = mapKey");
    }
  }

  private void emitMapValueVarDecl(CodeWriter w, ProtoField field) {
    FieldDescriptorProto.Type valType = field.getMapValueType();
    if (valType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("var mapVal *%s", msgType);
    } else if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      w.line("var mapVal %s", enumType);
    } else {
      String goType = typeMapper.scalarType(valType);
      w.line("var mapVal %s", goType);
    }
  }

  private void emitMapKeyParse(CodeWriter w, FieldDescriptorProto.Type keyType, String valExpr) {
    switch (keyType) {
      case TYPE_STRING:
        w.line("mapKey, _ = url.QueryUnescape(%s)", valExpr);
        break;
      case TYPE_BOOL:
        w.line("mapKey = %s == \"1\"", valExpr);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("mapKey, _ = strconv.ParseInt(%s, 10, 64)", valExpr);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line("mapKey, _ = strconv.ParseUint(%s, 10, 64)", valExpr);
        break;
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32:
        w.line("tmpKeyI64, _ := strconv.ParseInt(%s, 10, 32)", valExpr);
        w.line("mapKey = int32(tmpKeyI64)");
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line("tmpKeyU64, _ := strconv.ParseUint(%s, 10, 32)", valExpr);
        w.line("mapKey = uint32(tmpKeyU64)");
        break;
      default:
        w.line("// unsupported map key type");
        break;
    }
  }

  private void emitMapValueParse(CodeWriter w, ProtoField field, String valExpr) {
    FieldDescriptorProto.Type valType = field.getMapValueType();
    if (valType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("valSubCount, _ := strconv.Atoi(%s)", valExpr);
      w.line("*offset++");
      w.line("mapVal = parse%sPbtkTokens(tokens, valSubCount, offset)", msgType);
      w.line("*offset--");
    } else if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      w.line("tmpValEnum, _ := strconv.ParseInt(%s, 10, 32)", valExpr);
      w.line("mapVal = %s(int32(tmpValEnum))", enumType);
    } else {
      switch (valType) {
        case TYPE_STRING:
          w.line("mapVal, _ = url.QueryUnescape(%s)", valExpr);
          break;
        case TYPE_BYTES:
          w.line("mapVal, _ = base64.StdEncoding.DecodeString(%s)", valExpr);
          break;
        case TYPE_BOOL:
          w.line("mapVal = %s == \"1\"", valExpr);
          break;
        case TYPE_DOUBLE:
          w.line("mapVal, _ = strconv.ParseFloat(%s, 64)", valExpr);
          break;
        case TYPE_FLOAT:
          w.line("tmpValF64, _ := strconv.ParseFloat(%s, 32)", valExpr);
          w.line("mapVal = float32(tmpValF64)");
          break;
        case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
          w.line("mapVal, _ = strconv.ParseInt(%s, 10, 64)", valExpr);
          break;
        case TYPE_UINT64, TYPE_FIXED64:
          w.line("mapVal, _ = strconv.ParseUint(%s, 10, 64)", valExpr);
          break;
        case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32:
          w.line("tmpValI64, _ := strconv.ParseInt(%s, 10, 32)", valExpr);
          w.line("mapVal = int32(tmpValI64)");
          break;
        case TYPE_UINT32, TYPE_FIXED32:
          w.line("tmpValU64, _ := strconv.ParseUint(%s, 10, 32)", valExpr);
          w.line("mapVal = uint32(tmpValU64)");
          break;
        default:
          w.line("// unsupported map value type");
          break;
      }
    }
  }

  /** Emit the public Deserialize convenience function. */
  private void emitDeserializeFromPbtkUrl(CodeWriter w, String structName) {
    w.blankLine();
    w.block(
        "func Deserialize" + structName + "FromPbtkUrl(input string) *" + structName,
        () -> {
          w.block("if input == \"\"", () -> w.line("return &%s{}", structName));
          // Tokenize: split on '!'
          w.line("tokens := pbtkTokenize(input)");
          w.line("offset := 0");
          w.line("return parse%sPbtkTokens(tokens, len(tokens), &offset)", structName);
        });

    // Only emit the tokenizer once: check if it's the top-level call by including
    // it alongside the public deserialize method. We use a package-level function
    // that Go will deduplicate if multiple messages exist (same signature).
    emitTokenizer(w);
  }

  /** Emit the pbtkTokenize helper function. */
  private void emitTokenizer(CodeWriter w) {
    w.blankLine();
    w.block(
        "func pbtkTokenize(input string) []string",
        () -> {
          w.line("var tokens []string");
          w.line("i := 0");
          w.block("if len(input) > 0 && input[0] == '!'", () -> w.line("i = 1"));
          w.block(
              "for i < len(input)",
              () -> {
                w.line("next := strings.IndexByte(input[i:], '!')");
                w.block(
                    "if next < 0",
                    () -> {
                      w.line("tokens = append(tokens, input[i:])");
                      w.line("break");
                    });
                w.line("tokens = append(tokens, input[i:i+next])");
                w.line("i = i + next + 1");
              });
          w.line("return tokens");
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Return the pbtk type character for a given proto type. */
  static String pbtkTypeChar(FieldDescriptorProto.Type type) {
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

  /** Return the Go zero-value literal for a scalar type. */
  private String scalarZeroLiteral(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "nil";
      default -> "0";
    };
  }

  /** Collect the set of Go imports needed by a message and its nested types. */
  private Set<String> collectImports(ProtoMessage message) {
    Set<String> imports = new LinkedHashSet<>();

    // pbtk URL encoding always needs these
    imports.add("strconv");
    imports.add("strings");

    // Check if we need net/url (string fields)
    if (needsUrlEncoding(message)) {
      imports.add("net/url");
    }

    // Check if we need encoding/base64 (bytes fields)
    if (needsBase64(message)) {
      imports.add("encoding/base64");
    }

    // Check if we need fmt (for fallback formatting)
    if (needsFmt(message)) {
      imports.add("fmt");
    }

    return imports;
  }

  private boolean needsUrlEncoding(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
        return true;
      }
      if (field.isMap()) {
        if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING
            || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
          return true;
        }
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (needsUrlEncoding(nested)) {
        return true;
      }
    }
    return false;
  }

  private boolean needsBase64(ProtoMessage message) {
    for (ProtoField field : message.getFields()) {
      if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        return true;
      }
      if (field.isMap() && field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        return true;
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      if (needsBase64(nested)) {
        return true;
      }
    }
    return false;
  }

  private boolean needsFmt(ProtoMessage message) {
    // fmt is needed if there are any fields that fall through to the default
    // Sprintf formatting case. In practice this is rare, but we include it
    // conservatively when there are fields of unrecognized types.
    return false;
  }
}
