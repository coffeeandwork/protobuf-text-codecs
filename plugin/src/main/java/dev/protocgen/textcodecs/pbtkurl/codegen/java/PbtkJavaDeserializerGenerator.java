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
package dev.protocgen.textcodecs.pbtkurl.codegen.java;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.java.JavaNameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.java.JavaTypeMapper;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the parseFrom() method body for Java pbtk URL classes. Parses pbtk URL-encoded strings
 * using the {@code !<fieldNumber><typeChar><value>} format.
 *
 * <p>Uses Builder pattern: constructs a Builder, calls setters on it, then calls build() to produce
 * an immutable message instance.
 */
public class PbtkJavaDeserializerGenerator {

  private final JavaTypeMapper typeMapper;
  private final JavaNameResolver nameResolver;

  public PbtkJavaDeserializerGenerator(JavaTypeMapper typeMapper, JavaNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // Internal parser that takes a token list and offset
    emitParseFromTokens(w, message, className);

    // Public: deserialize from byte[]
    w.blankLine();
    w.block(
        "public static " + className + " parseFrom(byte[] data)",
        () -> {
          w.line("String input = new String(data, java.nio.charset.StandardCharsets.UTF_8);");
          w.line("if (input.isEmpty()) return %s.getDefaultInstance();", className);
          w.line("java.util.List<String> tokens = tokenizePbtk(input);");
          w.line("int[] offset = {0};");
          w.line("return parsePbtkTokens(tokens, tokens.size(), offset);");
        });

    // Public: deserialize from InputStream
    w.blankLine();
    w.block(
        "public static "
            + className
            + " parseFrom(java.io.InputStream input) throws java.io.IOException",
        () -> {
          w.line("return parseFrom(input.readAllBytes());");
        });

    // Tokenizer: split on '!' and return list of tokens
    w.blankLine();
    w.block(
        "private static java.util.List<String> tokenizePbtk(String input)",
        () -> {
          w.line("java.util.List<String> tokens = new java.util.ArrayList<>();");
          w.line("int i = 0;");
          w.line("if (input.charAt(0) == '!') i = 1;");
          w.block(
              "while (i < input.length())",
              () -> {
                w.line("int next = input.indexOf('!', i);");
                w.block(
                    "if (next < 0)",
                    () -> {
                      w.line("tokens.add(input.substring(i));");
                      w.line("break;");
                    });
                w.line("tokens.add(input.substring(i, next));");
                w.line("i = next + 1;");
              });
          w.line("return tokens;");
        });
  }

  private void emitParseFromTokens(CodeWriter w, ProtoMessage message, String className) {
    w.blankLine();
    w.block(
        "static "
            + className
            + " parsePbtkTokens(java.util.List<String> tokens, int fieldCount, int[] offset)",
        () -> {
          w.line("%s.Builder builder = %s.newBuilder();", className, className);
          w.line("int consumed = 0;");
          w.block(
              "while (consumed < fieldCount && offset[0] < tokens.size())",
              () -> {
                w.line("String token = tokens.get(offset[0]);");
                // Parse field number and type char
                w.line("int numEnd = 0;");
                w.line(
                    "while (numEnd < token.length() && Character.isDigit(token.charAt(numEnd))) numEnd++;");
                w.line(
                    "if (numEnd == 0 || numEnd >= token.length()) { offset[0]++; consumed++; continue; }");
                w.line("int fieldNum = Integer.parseInt(token.substring(0, numEnd));");
                w.line("char typeChar = token.charAt(numEnd);");
                w.line("String value = token.substring(numEnd + 1);");

                // Switch on field number
                w.block(
                    "switch (fieldNum)",
                    () -> {
                      for (ProtoField field : message.getFields()) {
                        emitFieldCase(w, field);
                      }
                      w.line("default: offset[0]++; consumed++; break;");
                    });
              });
          w.line("return builder.build();");
        });
  }

  private void emitFieldCase(CodeWriter w, ProtoField field) {
    int fieldNum = field.getFieldNumber();
    String setterCall = "builder." + nameResolver.setterName(field.getName());

    w.block(
        "case " + fieldNum + ":",
        () -> {
          if (field.isMap()) {
            emitMapDeserialize(w, field);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            emitMessageDeserialize(w, field, setterCall);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, setterCall);
          } else {
            emitScalarDeserialize(w, field, setterCall);
          }
          w.line("offset[0]++;");
          w.line("consumed++;");
          w.line("break;");
        });
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field, String setterCall) {
    String readExpr = scalarReadExpr(field.getProtoType(), "value");
    w.line("%s(%s);", setterCall, readExpr);
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String setterCall) {
    String enumType = simpleTypeName(field.getTypeReference());
    w.line("%s(%s.forNumber(Integer.parseInt(value)));", setterCall, enumType);
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field, String setterCall) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("int subCount = Integer.parseInt(value);");
    w.line("offset[0]++;");
    w.line("%s(%s.parsePbtkTokens(tokens, subCount, offset));", setterCall, msgType);
    // Don't increment offset again -- the recursive call consumed the sub-tokens,
    // and the outer loop will increment once more, but we need to adjust:
    w.line("offset[0]--;"); // compensate for the outer offset[0]++ after break
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field) {
    String addCall =
        "builder.add"
            + dev.protocgen.textcodecs.jsonarray.codegen.java.JavaNameResolver.snakeToPascal(
                field.getName());
    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("int subCount = Integer.parseInt(value);");
      w.line("offset[0]++;");
      w.line("%s(%s.parsePbtkTokens(tokens, subCount, offset));", addCall, msgType);
      w.line("offset[0]--;");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      String enumType = simpleTypeName(field.getTypeReference());
      w.line("%s(%s.forNumber(Integer.parseInt(value)));", addCall, enumType);
    } else {
      String readExpr = scalarReadExpr(field.getProtoType(), "value");
      w.line("%s(%s);", addCall, readExpr);
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field) {
    String putCall =
        "builder.put"
            + dev.protocgen.textcodecs.jsonarray.codegen.java.JavaNameResolver.snakeToPascal(
                field.getName());
    // Map entries are serialized as !<num>m2!1<keyType><key>!2<valType><val>
    w.line("int entryCount = Integer.parseInt(value);");
    w.line("offset[0]++;");
    // Parse key (field 1) and value (field 2) from the next entryCount tokens
    w.line("Object entryKey = null;");
    w.line("Object entryVal = null;");
    w.block(
        "for (int __mi = 0; __mi < entryCount && offset[0] < tokens.size(); __mi++)",
        () -> {
          w.line("String mapToken = tokens.get(offset[0]);");
          w.line("int mne = 0;");
          w.line(
              "while (mne < mapToken.length() && Character.isDigit(mapToken.charAt(mne))) mne++;");
          w.line("if (mne == 0 || mne >= mapToken.length()) { offset[0]++; continue; }");
          w.line("int mfn = Integer.parseInt(mapToken.substring(0, mne));");
          w.line("String mval = mapToken.substring(mne + 1);");
          w.block(
              "if (mfn == 1)",
              () -> {
                // Key
                emitMapKeyRead(w, field);
              });
          w.block(
              "if (mfn == 2)",
              () -> {
                // Value
                emitMapValueRead(w, field);
              });
          w.line("offset[0]++;");
        });
    w.line("offset[0]--;"); // compensate for outer offset[0]++
    // Put the entry into the map via builder
    emitMapPut(w, field, putCall);
  }

  private void emitMapKeyRead(CodeWriter w, ProtoField field) {
    String keyRead = scalarReadExpr(field.getMapKeyType(), "mval");
    w.line("entryKey = %s;", keyRead);
  }

  private void emitMapValueRead(CodeWriter w, ProtoField field) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.line("int valSubCount = Integer.parseInt(mval);");
      w.line("offset[0]++;");
      w.line("entryVal = %s.parsePbtkTokens(tokens, valSubCount, offset);", msgType);
      w.line("offset[0]--;");
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      w.line("entryVal = %s.forNumber(Integer.parseInt(mval));", enumType);
    } else {
      String valRead = scalarReadExpr(field.getMapValueType(), "mval");
      w.line("entryVal = %s;", valRead);
    }
  }

  private void emitMapPut(CodeWriter w, ProtoField field, String putCall) {
    // Cast the key and value to appropriate types and call builder.putXxx(key, value)
    String keyCast = mapKeyCast(field.getMapKeyType());
    String valCast = mapValueCast(field);
    w.line("if (entryKey != null) %s(%s, %s);", putCall, keyCast, valCast);
  }

  private String mapKeyCast(FieldDescriptorProto.Type keyType) {
    return switch (keyType) {
      case TYPE_STRING -> "(String) entryKey";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 ->
          "(Integer) entryKey";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "(Long) entryKey";
      case TYPE_BOOL -> "(Boolean) entryKey";
      default -> "entryKey";
    };
  }

  private String mapValueCast(ProtoField field) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      return "(" + msgType + ") entryVal";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      return "(" + enumType + ") entryVal";
    }
    return switch (field.getMapValueType()) {
      case TYPE_STRING -> "(String) entryVal";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 ->
          "(Integer) entryVal";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "(Long) entryVal";
      case TYPE_DOUBLE -> "(Double) entryVal";
      case TYPE_FLOAT -> "(Float) entryVal";
      case TYPE_BOOL -> "(Boolean) entryVal";
      case TYPE_BYTES -> "(byte[]) entryVal";
      default -> "entryVal";
    };
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String valueVar) {
    return switch (type) {
      case TYPE_DOUBLE -> "Double.parseDouble(" + valueVar + ")";
      case TYPE_FLOAT -> "Float.parseFloat(" + valueVar + ")";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> "Long.parseLong(" + valueVar + ")";
      case TYPE_UINT64, TYPE_FIXED64 -> "Long.parseUnsignedLong(" + valueVar + ")";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "Integer.parseInt(" + valueVar + ")";
      case TYPE_UINT32, TYPE_FIXED32 -> "Integer.parseUnsignedInt(" + valueVar + ")";
      case TYPE_BOOL -> "\"1\".equals(" + valueVar + ")";
      case TYPE_STRING ->
          "java.net.URLDecoder.decode(" + valueVar + ", java.nio.charset.StandardCharsets.UTF_8)";
      case TYPE_BYTES -> "java.util.Base64.getDecoder().decode(" + valueVar + ")";
      default -> valueVar;
    };
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Object";
  }
}
