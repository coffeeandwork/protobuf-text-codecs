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
package dev.protocgen.textcodecs.pbtkurl.codegen.swift;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.swift.SwiftNameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.swift.SwiftTypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Swift language code generator for pbtk URL encoding. Produces Swift source files with toPbtkUrl()
 * and fromPbtkUrl() methods. Generated code uses only Foundation (String manipulation, percent
 * encoding, Data for base64).
 *
 * <p>The pbtk format encodes protobuf messages as URL strings: {@code
 * !<fieldNumber><typeChar><value>} where type chars are: b=bool(0/1), i=integer, f=float, d=double,
 * s=string(URL-encoded), e=enum(int), m=message(count of sub-fields), z=bytes(base64).
 */
public class PbtkSwiftGenerator implements LanguageGenerator {

  private final SwiftNameResolver nameResolver = new SwiftNameResolver();
  private final SwiftTypeMapper typeMapper = new SwiftTypeMapper();

  // ---------------------------------------------------------------------------
  // Local helpers
  // ---------------------------------------------------------------------------

  /** Convert snake_case to camelCase (Swift property name). */
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

  /** Convert snake_case to PascalCase (Swift type name). */
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

  /**
   * Extract the simple type name from a fully-qualified proto type reference. E.g.,
   * ".example.sub.Address" -> "Address"
   */
  private static String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "Any";
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

  // ---------------------------------------------------------------------------
  // Top-level emitters
  // ---------------------------------------------------------------------------

  /** Generate a complete Swift source file for a message. */
  private String emitMessage(ProtoMessage message, ProtoFile file) {
    CodeWriter w = new CodeWriter(); // 4-space indentation
    String structName = nameResolver.messageClassName(message.getName());

    // Import
    w.line("import Foundation");
    w.blankLine();

    // Struct declaration
    w.block(
        "public struct " + structName,
        () -> {
          // Properties
          for (ProtoField field : message.getFields()) {
            String swiftType = resolveFieldType(field, structName, message);
            String swiftName = nameResolver.fieldName(field.getName());
            String defaultVal = resolveDefaultValue(field, structName, message);
            w.line("public var %s: %s = %s", swiftName, swiftType, defaultVal);
          }

          // Oneof case tracking fields
          for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
            String caseName = snakeToCamel(group.name()) + "Case";
            w.line("public var %s: Int = 0 // 0 = not set, field_number = set", caseName);
          }

          // Nested message structs
          for (ProtoMessage nested : message.getNestedMessages()) {
            w.blankLine();
            String nestedName = structName + "_" + nameResolver.messageClassName(nested.getName());
            // Emit placeholder for nested struct (actual struct is top-level)
          }

          // Nested enums
          for (ProtoEnum protoEnum : message.getEnums()) {
            emitEnum(w, protoEnum, structName);
          }

          // Oneof case constants
          emitOneofConstants(w, message, structName);

          // Default initializer
          w.blankLine();
          w.block("public init()", () -> {});

          // Serialization methods
          emitCountPbtkFields(w, message);
          emitAppendPbtkFields(w, message);
          emitToPbtkUrl(w);

          // Deserialization methods
          emitParsePbtkTokens(w, message, structName);
          emitDeserializeFromPbtkUrl(w, structName);
        });

    // Nested message structs as top-level types
    for (ProtoMessage nested : message.getNestedMessages()) {
      w.blankLine();
      String nestedName = structName + "_" + nameResolver.messageClassName(nested.getName());
      emitNestedStructTopLevel(w, nested, nestedName);
    }

    // Tokenizer helper as a free function
    emitTokenizer(w);

    return w.toString();
  }

  /** Generate a complete Swift source file for a top-level enum. */
  private String emitTopLevelEnum(ProtoEnum protoEnum, ProtoFile file) {
    CodeWriter w = new CodeWriter();

    w.line("import Foundation");
    w.blankLine();

    emitEnum(w, protoEnum, "");
    return w.toString();
  }

  // ---------------------------------------------------------------------------
  // Struct and enum emission
  // ---------------------------------------------------------------------------

  private void emitNestedStructTopLevel(CodeWriter w, ProtoMessage nested, String nestedName) {
    w.block(
        "public struct " + nestedName,
        () -> {
          for (ProtoField field : nested.getFields()) {
            String swiftType = typeMapper.languageType(field);
            String swiftName = nameResolver.fieldName(field.getName());
            String defaultVal = typeMapper.defaultValue(field);
            w.line("public var %s: %s = %s", swiftName, swiftType, defaultVal);
          }

          for (ProtoMessage.OneofGroup group : nested.getOneofGroups()) {
            String caseName = snakeToCamel(group.name()) + "Case";
            w.line("public var %s: Int = 0", caseName);
          }

          for (ProtoEnum protoEnum : nested.getEnums()) {
            emitEnum(w, protoEnum, nestedName);
          }

          w.blankLine();
          w.block("public init()", () -> {});

          emitCountPbtkFields(w, nested);
          emitAppendPbtkFields(w, nested);
          emitToPbtkUrl(w);
          emitParsePbtkTokens(w, nested, nestedName);
          emitDeserializeFromPbtkUrl(w, nestedName);
        });
  }

  private String resolveFieldType(
      ProtoField field, String parentStructName, ProtoMessage parentMessage) {
    if (isNestedMessageRef(field, parentMessage)) {
      String nestedSimpleName = simpleTypeName(field.getTypeReference());
      String flattenedName = parentStructName + "_" + nestedSimpleName;
      if (field.isRepeated()) {
        return "[" + flattenedName + "]";
      }
      return flattenedName + "?";
    }
    return typeMapper.languageType(field);
  }

  private String resolveDefaultValue(
      ProtoField field, String parentStructName, ProtoMessage parentMessage) {
    if (isNestedMessageRef(field, parentMessage)) {
      if (field.isRepeated()) {
        return "[]";
      }
      return "nil";
    }
    return typeMapper.defaultValue(field);
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
    w.block(
        "public enum " + typeName + ": Int32",
        () -> {
          for (ProtoEnum.EnumValue val : protoEnum.getValues()) {
            w.line("case %s = %d", nameResolver.enumConstantName(val.name()), val.number());
          }
        });
  }

  private void emitOneofConstants(CodeWriter w, ProtoMessage message, String structName) {
    for (ProtoMessage.OneofGroup group : message.getOneofGroups()) {
      w.blankLine();
      w.line("// Oneof case constants for %s", snakeToPascal(group.name()));
      for (ProtoField member : group.members()) {
        String constName =
            "k" + snakeToPascal(group.name()) + "_" + snakeToPascal(member.getName());
        w.line("public static let %s: Int = %d", constName, member.getFieldNumber());
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Serialization: ToPbtkUrl
  // ---------------------------------------------------------------------------

  /** Emit countPbtkFields method: counts how many tokens this message will produce. */
  private void emitCountPbtkFields(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.block(
        "func countPbtkFields() -> Int",
        () -> {
          w.line("var count = 0");
          for (ProtoField field : message.getFields()) {
            emitFieldCount(w, field);
          }
          w.line("return count");
        });
  }

  private void emitFieldCount(CodeWriter w, ProtoField field) {
    String swiftField = nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseField = snakeToCamel(field.getOneofName()) + "Case";
      w.block(
          "if " + caseField + " == " + field.getFieldNumber(),
          () -> {
            if (field.getKind() == ProtoField.FieldKind.MESSAGE
                || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
              w.block(
                  "if let msg = " + swiftField,
                  () -> {
                    w.line("count += 1 + msg.countPbtkFields()");
                  });
            } else {
              w.line("count += 1");
            }
          });
      return;
    }

    if (field.isMap()) {
      w.line("count += %s.count", swiftField);
    } else if (field.isRepeated()) {
      w.line("count += %s.count", swiftField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "if " + swiftField + " != nil",
          () -> {
            w.line("count += 1");
          });
    } else if (field.isProto3Optional()) {
      w.block(
          "if " + swiftField + " != nil",
          () -> {
            w.line("count += 1");
          });
    } else {
      w.line("count += 1");
    }
  }

  /** Emit appendPbtkFields method: appends all field tokens to a String. */
  private void emitAppendPbtkFields(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.block(
        "func appendPbtkFields(_ sb: inout String)",
        () -> {
          for (ProtoField field : message.getFields()) {
            emitFieldSerialize(w, field);
          }
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String swiftField = nameResolver.fieldName(field.getName());
    int fieldNum = field.getFieldNumber();

    if (field.isOneofMember()) {
      String caseField = snakeToCamel(field.getOneofName()) + "Case";
      w.block(
          "if " + caseField + " == " + fieldNum,
          () -> {
            emitSingleFieldSerialize(w, field, swiftField, fieldNum);
          });
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, swiftField, fieldNum);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, swiftField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, swiftField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, swiftField, fieldNum);
    } else {
      emitScalarSerialize(w, field, swiftField, fieldNum);
    }
  }

  private void emitSingleFieldSerialize(
      CodeWriter w, ProtoField field, String swiftField, int fieldNum) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, swiftField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, swiftField, fieldNum);
    } else {
      emitScalarSerialize(w, field, swiftField, fieldNum);
    }
  }

  private void emitScalarSerialize(
      CodeWriter w, ProtoField field, String swiftField, int fieldNum) {
    FieldDescriptorProto.Type type = field.getProtoType();
    String typeChar = pbtkTypeChar(type);

    if (field.isProto3Optional()) {
      w.block(
          "if let val = " + swiftField,
          () -> {
            emitScalarAppend(w, type, "val", fieldNum, typeChar);
          });
      return;
    }

    emitScalarAppend(w, type, swiftField, fieldNum, typeChar);
  }

  private void emitScalarAppend(
      CodeWriter w,
      FieldDescriptorProto.Type type,
      String swiftField,
      int fieldNum,
      String typeChar) {
    switch (type) {
      case TYPE_BOOL:
        w.line("sb += \"!%d%s\\(%s ? \"1\" : \"0\")\"", fieldNum, typeChar, swiftField);
        break;
      case TYPE_BYTES:
        w.line("sb += \"!%d%s\"", fieldNum, typeChar);
        w.line("sb += %s.base64EncodedString()", swiftField);
        break;
      case TYPE_STRING:
        w.line("sb += \"!%d%s\"", fieldNum, typeChar);
        w.line(
            "sb += %s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? %s",
            swiftField, swiftField);
        break;
      case TYPE_DOUBLE:
        w.line("sb += \"!%d%s\"", fieldNum, typeChar);
        w.line("sb += String(%s)", swiftField);
        break;
      case TYPE_FLOAT:
        w.line("sb += \"!%d%s\"", fieldNum, typeChar);
        w.line("sb += String(%s)", swiftField);
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
      case TYPE_UINT64:
      case TYPE_FIXED64:
      case TYPE_INT32:
      case TYPE_SINT32:
      case TYPE_SFIXED32:
      case TYPE_UINT32:
      case TYPE_FIXED32:
        w.line("sb += \"!%d%s\"", fieldNum, typeChar);
        w.line("sb += String(%s)", swiftField);
        break;
      default:
        w.line("sb += \"!%d%s\"", fieldNum, typeChar);
        w.line("sb += String(describing: %s)", swiftField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String swiftField, int fieldNum) {
    if (field.isProto3Optional()) {
      w.block(
          "if let val = " + swiftField,
          () -> {
            w.line("sb += \"!%de\"", fieldNum);
            w.line("sb += String(val.rawValue)");
          });
    } else {
      w.line("sb += \"!%de\"", fieldNum);
      w.line("sb += String(%s.rawValue)", swiftField);
    }
  }

  private void emitMessageSerialize(
      CodeWriter w, ProtoField field, String swiftField, int fieldNum) {
    w.block(
        "if let msg = " + swiftField,
        () -> {
          w.line("sb += \"!%dm\"", fieldNum);
          w.line("sb += String(msg.countPbtkFields())");
          w.line("msg.appendPbtkFields(&sb)");
        });
  }

  private void emitRepeatedSerialize(
      CodeWriter w, ProtoField field, String swiftField, int fieldNum) {
    w.block(
        "for item in " + swiftField,
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            w.line("sb += \"!%dm\"", fieldNum);
            w.line("sb += String(item.countPbtkFields())");
            w.line("item.appendPbtkFields(&sb)");
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line("sb += \"!%de\"", fieldNum);
            w.line("sb += String(item.rawValue)");
          } else {
            emitScalarAppend(
                w, field.getProtoType(), "item", fieldNum, pbtkTypeChar(field.getProtoType()));
          }
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String swiftField, int fieldNum) {
    w.block(
        "for (key, val) in " + swiftField,
        () -> {
          // Each map entry is a synthetic message with 2 sub-fields
          w.line("sb += \"!%dm2\"", fieldNum);

          // Key (field 1)
          emitMapKeySerialize(w, field.getMapKeyType(), "key");

          // Value (field 2)
          emitMapValueSerialize(w, field, "val");
        });
  }

  private void emitMapKeySerialize(
      CodeWriter w, FieldDescriptorProto.Type keyType, String keyExpr) {
    String typeChar = pbtkTypeChar(keyType);
    switch (keyType) {
      case TYPE_STRING:
        w.line("sb += \"!1%s\"", typeChar);
        w.line(
            "sb += %s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? %s",
            keyExpr, keyExpr);
        break;
      case TYPE_BOOL:
        w.line("sb += \"!1%s\\(%s ? \"1\" : \"0\")\"", typeChar, keyExpr);
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
      case TYPE_UINT64:
      case TYPE_FIXED64:
      case TYPE_INT32:
      case TYPE_SINT32:
      case TYPE_SFIXED32:
      case TYPE_UINT32:
      case TYPE_FIXED32:
        w.line("sb += \"!1%s\"", typeChar);
        w.line("sb += String(%s)", keyExpr);
        break;
      default:
        w.line("sb += \"!1%s\"", typeChar);
        w.line("sb += String(describing: %s)", keyExpr);
        break;
    }
  }

  private void emitMapValueSerialize(CodeWriter w, ProtoField field, String valExpr) {
    FieldDescriptorProto.Type valType = field.getMapValueType();

    if (valType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("sb += \"!2m\"");
      w.line("sb += String(%s.countPbtkFields())", valExpr);
      w.line("%s.appendPbtkFields(&sb)", valExpr);
      return;
    }

    if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("sb += \"!2e\"");
      w.line("sb += String(%s.rawValue)", valExpr);
      return;
    }

    String typeChar = pbtkTypeChar(valType);
    switch (valType) {
      case TYPE_STRING:
        w.line("sb += \"!2%s\"", typeChar);
        w.line(
            "sb += %s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? %s",
            valExpr, valExpr);
        break;
      case TYPE_BYTES:
        w.line("sb += \"!2%s\"", typeChar);
        w.line("sb += %s.base64EncodedString()", valExpr);
        break;
      case TYPE_BOOL:
        w.line("sb += \"!2%s\\(%s ? \"1\" : \"0\")\"", typeChar, valExpr);
        break;
      default:
        w.line("sb += \"!2%s\"", typeChar);
        w.line("sb += String(%s)", valExpr);
        break;
    }
  }

  /** Emit the public toPbtkUrl convenience method. */
  private void emitToPbtkUrl(CodeWriter w) {
    w.blankLine();
    w.block(
        "public func toPbtkUrl() -> String",
        () -> {
          w.line("var sb = \"\"");
          w.line("appendPbtkFields(&sb)");
          w.line("return sb");
        });
  }

  // ---------------------------------------------------------------------------
  // Deserialization: FromPbtkUrl
  // ---------------------------------------------------------------------------

  /** Emit the internal parsePbtkTokens function that processes a token slice. */
  private void emitParsePbtkTokens(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "static func parsePbtkTokens(_ tokens: [String], fieldCount: Int, offset: inout Int) -> "
            + structName,
        () -> {
          w.line("var obj = %s()", structName);
          w.line("var consumed = 0");
          w.block(
              "while consumed < fieldCount && offset < tokens.count",
              () -> {
                w.line("let token = tokens[offset]");
                // Parse field number and type char
                w.line("var numEnd = token.startIndex");
                w.block(
                    "while numEnd < token.endIndex && token[numEnd].isNumber",
                    () -> w.line("numEnd = token.index(after: numEnd)"));
                w.block(
                    "if numEnd == token.startIndex || numEnd >= token.endIndex",
                    () -> {
                      w.line("offset += 1");
                      w.line("consumed += 1");
                      w.line("continue");
                    });
                w.line("guard let fieldNum = Int(token[token.startIndex..<numEnd]) else {");
                w.indent();
                w.line("offset += 1");
                w.line("consumed += 1");
                w.line("continue");
                w.dedent();
                w.line("}");
                w.line("let valueStart = token.index(after: numEnd)");
                w.line("let value = String(token[valueStart..<token.endIndex])");

                // Switch on field number
                w.line("switch fieldNum {");
                for (ProtoField field : message.getFields()) {
                  emitFieldCase(w, field, structName);
                }
                w.line("default:");
                w.indent();
                w.line("offset += 1");
                w.line("consumed += 1");
                w.dedent();
                w.line("}");
              });
          w.line("return obj");
        });
  }

  private void emitFieldCase(CodeWriter w, ProtoField field, String structName) {
    int fieldNum = field.getFieldNumber();
    String swiftField = "obj." + nameResolver.fieldName(field.getName());

    w.line("case %d:", fieldNum);
    w.indent();

    if (field.isMap()) {
      emitMapDeserialize(w, field, swiftField);
    } else if (field.isRepeated()) {
      emitRepeatedDeserialize(w, field, swiftField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageDeserialize(w, field, swiftField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumDeserialize(w, field, swiftField);
    } else {
      emitScalarDeserialize(w, field, swiftField);
    }

    if (field.isOneofMember()) {
      String caseField = "obj." + snakeToCamel(field.getOneofName()) + "Case";
      w.line("%s = %d", caseField, fieldNum);
    }

    w.line("offset += 1");
    w.line("consumed += 1");
    w.dedent();
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field, String swiftField) {
    FieldDescriptorProto.Type type = field.getProtoType();
    boolean isOptional = field.isProto3Optional();

    switch (type) {
      case TYPE_BOOL:
        w.line("%s = value == \"1\"", swiftField);
        break;
      case TYPE_STRING:
        w.line("%s = value.removingPercentEncoding ?? value", swiftField);
        break;
      case TYPE_BYTES:
        w.block(
            "if let decoded = Data(base64Encoded: value)",
            () -> {
              w.line("%s = decoded", swiftField);
            });
        break;
      case TYPE_DOUBLE:
        if (isOptional) {
          w.line("%s = Double(value)", swiftField);
        } else {
          w.line("%s = Double(value) ?? 0.0", swiftField);
        }
        break;
      case TYPE_FLOAT:
        if (isOptional) {
          w.line("%s = Float(value).map { Float($0) }", swiftField);
        } else {
          w.line("%s = Float(value) ?? 0.0", swiftField);
        }
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
        if (isOptional) {
          w.line("%s = Int64(value)", swiftField);
        } else {
          w.line("%s = Int64(value) ?? 0", swiftField);
        }
        break;
      case TYPE_UINT64:
      case TYPE_FIXED64:
        if (isOptional) {
          w.line("%s = UInt64(value)", swiftField);
        } else {
          w.line("%s = UInt64(value) ?? 0", swiftField);
        }
        break;
      case TYPE_INT32:
      case TYPE_SINT32:
      case TYPE_SFIXED32:
        if (isOptional) {
          w.line("%s = Int32(value)", swiftField);
        } else {
          w.line("%s = Int32(value) ?? 0", swiftField);
        }
        break;
      case TYPE_UINT32:
      case TYPE_FIXED32:
        if (isOptional) {
          w.line("%s = UInt32(value)", swiftField);
        } else {
          w.line("%s = UInt32(value) ?? 0", swiftField);
        }
        break;
      default:
        w.line("// unsupported type");
        break;
    }
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String swiftField) {
    String enumType = simpleTypeName(field.getTypeReference());
    w.block(
        "if let rawVal = Int32(value)",
        () -> {
          w.line("%s = %s(rawValue: rawVal) ?? %s(rawValue: 0)!", swiftField, enumType, enumType);
        });
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field, String swiftField) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.block(
        "if let subCount = Int(value)",
        () -> {
          w.line("offset += 1");
          w.line(
              "%s = %s.parsePbtkTokens(tokens, fieldCount: subCount, offset: &offset)",
              swiftField, msgType);
          w.line("offset -= 1"); // compensate for the outer offset += 1
        });
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field, String swiftField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.block(
          "if let subCount = Int(value)",
          () -> {
            w.line("offset += 1");
            w.line(
                "%s.append(%s.parsePbtkTokens(tokens, fieldCount: subCount, offset: &offset))",
                swiftField, msgType);
            w.line("offset -= 1");
          });
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      String enumType = simpleTypeName(field.getTypeReference());
      w.block(
          "if let rawVal = Int32(value), let e = " + enumType + "(rawValue: rawVal)",
          () -> {
            w.line("%s.append(e)", swiftField);
          });
    } else {
      emitRepeatedScalarDeserialize(w, field, swiftField);
    }
  }

  private void emitRepeatedScalarDeserialize(CodeWriter w, ProtoField field, String swiftField) {
    FieldDescriptorProto.Type type = field.getProtoType();
    switch (type) {
      case TYPE_BOOL:
        w.line("%s.append(value == \"1\")", swiftField);
        break;
      case TYPE_STRING:
        w.line("%s.append(value.removingPercentEncoding ?? value)", swiftField);
        break;
      case TYPE_BYTES:
        w.block(
            "if let decoded = Data(base64Encoded: value)",
            () -> {
              w.line("%s.append(decoded)", swiftField);
            });
        break;
      case TYPE_DOUBLE:
        w.block(
            "if let v = Double(value)",
            () -> {
              w.line("%s.append(v)", swiftField);
            });
        break;
      case TYPE_FLOAT:
        w.block(
            "if let v = Float(value)",
            () -> {
              w.line("%s.append(v)", swiftField);
            });
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
        w.block(
            "if let v = Int64(value)",
            () -> {
              w.line("%s.append(v)", swiftField);
            });
        break;
      case TYPE_UINT64:
      case TYPE_FIXED64:
        w.block(
            "if let v = UInt64(value)",
            () -> {
              w.line("%s.append(v)", swiftField);
            });
        break;
      case TYPE_INT32:
      case TYPE_SINT32:
      case TYPE_SFIXED32:
        w.block(
            "if let v = Int32(value)",
            () -> {
              w.line("%s.append(v)", swiftField);
            });
        break;
      case TYPE_UINT32:
      case TYPE_FIXED32:
        w.block(
            "if let v = UInt32(value)",
            () -> {
              w.line("%s.append(v)", swiftField);
            });
        break;
      default:
        w.line("// unsupported repeated scalar type");
        break;
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String swiftField) {
    String mapType = typeMapper.languageType(field);
    // Initialize map if empty
    w.block("if " + swiftField + ".isEmpty", () -> w.line("// map already initialized as empty"));

    w.block(
        "if let entryCount = Int(value)",
        () -> {
          w.line("offset += 1");

          w.line(
              "var mapKey: %s = %s",
              typeMapper.scalarType(field.getMapKeyType()),
              scalarZeroLiteral(field.getMapKeyType()));
          emitMapValueVarDecl(w, field);

          w.block(
              "for _ in 0..<entryCount",
              () -> {
                w.block("if offset >= tokens.count", () -> w.line("break"));
                w.line("let mapToken = tokens[offset]");
                w.line("var mne = mapToken.startIndex");
                w.block(
                    "while mne < mapToken.endIndex && mapToken[mne].isNumber",
                    () -> w.line("mne = mapToken.index(after: mne)"));
                w.block(
                    "if mne == mapToken.startIndex || mne >= mapToken.endIndex",
                    () -> {
                      w.line("offset += 1");
                      w.line("continue");
                    });
                w.line(
                    "guard let mfn = Int(mapToken[mapToken.startIndex..<mne]) else { "
                        + "offset += 1; continue }");
                w.line("let mvalStart = mapToken.index(after: mne)");
                w.line("let mval = String(mapToken[mvalStart..<mapToken.endIndex])");
                w.block("if mfn == 1", () -> emitMapKeyParse(w, field.getMapKeyType(), "mval"));
                w.block("if mfn == 2", () -> emitMapValueParse(w, field, "mval"));
                w.line("offset += 1");
              });

          w.line("offset -= 1"); // compensate for outer offset += 1
          w.line("%s[mapKey] = mapVal", swiftField);
        });
  }

  private void emitMapValueVarDecl(CodeWriter w, ProtoField field) {
    FieldDescriptorProto.Type valType = field.getMapValueType();
    if (valType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("var mapVal = %s()", msgType);
    } else if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      w.line("var mapVal = %s(rawValue: 0)!", enumType);
    } else {
      String swiftType = typeMapper.scalarType(valType);
      w.line("var mapVal: %s = %s", swiftType, scalarZeroLiteral(valType));
    }
  }

  private void emitMapKeyParse(CodeWriter w, FieldDescriptorProto.Type keyType, String valExpr) {
    switch (keyType) {
      case TYPE_STRING:
        w.line("mapKey = %s.removingPercentEncoding ?? %s", valExpr, valExpr);
        break;
      case TYPE_BOOL:
        w.line("mapKey = %s == \"1\"", valExpr);
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
        w.line("mapKey = Int64(%s) ?? 0", valExpr);
        break;
      case TYPE_UINT64:
      case TYPE_FIXED64:
        w.line("mapKey = UInt64(%s) ?? 0", valExpr);
        break;
      case TYPE_INT32:
      case TYPE_SINT32:
      case TYPE_SFIXED32:
        w.line("mapKey = Int32(%s) ?? 0", valExpr);
        break;
      case TYPE_UINT32:
      case TYPE_FIXED32:
        w.line("mapKey = UInt32(%s) ?? 0", valExpr);
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
      w.block(
          "if let valSubCount = Int(" + valExpr + ")",
          () -> {
            w.line("offset += 1");
            w.line(
                "mapVal = %s.parsePbtkTokens(tokens, fieldCount: valSubCount, offset: &offset)",
                msgType);
            w.line("offset -= 1");
          });
    } else if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      w.block(
          "if let rawVal = Int32(" + valExpr + ")",
          () -> {
            w.line("mapVal = %s(rawValue: rawVal) ?? %s(rawValue: 0)!", enumType, enumType);
          });
    } else {
      switch (valType) {
        case TYPE_STRING:
          w.line("mapVal = %s.removingPercentEncoding ?? %s", valExpr, valExpr);
          break;
        case TYPE_BYTES:
          w.block(
              "if let decoded = Data(base64Encoded: " + valExpr + ")",
              () -> {
                w.line("mapVal = decoded");
              });
          break;
        case TYPE_BOOL:
          w.line("mapVal = %s == \"1\"", valExpr);
          break;
        case TYPE_DOUBLE:
          w.line("mapVal = Double(%s) ?? 0.0", valExpr);
          break;
        case TYPE_FLOAT:
          w.line("mapVal = Float(%s) ?? 0.0", valExpr);
          break;
        case TYPE_INT64:
        case TYPE_SINT64:
        case TYPE_SFIXED64:
          w.line("mapVal = Int64(%s) ?? 0", valExpr);
          break;
        case TYPE_UINT64:
        case TYPE_FIXED64:
          w.line("mapVal = UInt64(%s) ?? 0", valExpr);
          break;
        case TYPE_INT32:
        case TYPE_SINT32:
        case TYPE_SFIXED32:
          w.line("mapVal = Int32(%s) ?? 0", valExpr);
          break;
        case TYPE_UINT32:
        case TYPE_FIXED32:
          w.line("mapVal = UInt32(%s) ?? 0", valExpr);
          break;
        default:
          w.line("// unsupported map value type");
          break;
      }
    }
  }

  /** Emit the public fromPbtkUrl static method. */
  private void emitDeserializeFromPbtkUrl(CodeWriter w, String structName) {
    w.blankLine();
    w.block(
        "public static func fromPbtkUrl(_ input: String) -> " + structName,
        () -> {
          w.block("if input.isEmpty", () -> w.line("return %s()", structName));
          w.line("let tokens = pbtkTokenize(input)");
          w.line("var offset = 0");
          w.line("return parsePbtkTokens(tokens, fieldCount: tokens.count, offset: &offset)");
        });
  }

  /** Emit the pbtkTokenize helper function. */
  private void emitTokenizer(CodeWriter w) {
    w.blankLine();
    w.block(
        "private func pbtkTokenize(_ input: String) -> [String]",
        () -> {
          w.line("var tokens: [String] = []");
          w.line("var s = input");
          w.block("if s.hasPrefix(\"!\")", () -> w.line("s = String(s.dropFirst())"));
          w.block(
              "while !s.isEmpty",
              () -> {
                w.block(
                    "if let range = s.range(of: \"!\")",
                    () -> {
                      w.line("tokens.append(String(s[s.startIndex..<range.lowerBound]))");
                      w.line("s = String(s[range.upperBound...])");
                    });
                w.block(
                    "else",
                    () -> {
                      w.line("tokens.append(s)");
                      w.line("break");
                    });
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

  /** Return the Swift zero-value literal for a scalar type. */
  private String scalarZeroLiteral(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      case TYPE_BYTES -> "Data()";
      case TYPE_DOUBLE, TYPE_FLOAT -> "0.0";
      default -> "0";
    };
  }
}
