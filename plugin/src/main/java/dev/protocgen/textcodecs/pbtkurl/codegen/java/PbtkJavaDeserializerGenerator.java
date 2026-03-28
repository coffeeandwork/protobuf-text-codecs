package dev.protocgen.textcodecs.pbtkurl.codegen.java;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.nio.charset.StandardCharsets;

/**
 * Generates the fromPbtkUrl() method body for Java classes. Parses pbtk URL-encoded strings using
 * the {@code !<fieldNumber><typeChar><value>} format.
 */
public class PbtkJavaDeserializerGenerator {

  private final dev.protocgen.textcodecs.jsonarray.codegen.java.JavaTypeMapper typeMapper;
  private final dev.protocgen.textcodecs.jsonarray.codegen.java.JavaNameResolver nameResolver;

  public PbtkJavaDeserializerGenerator(
      dev.protocgen.textcodecs.jsonarray.codegen.java.JavaTypeMapper typeMapper,
      dev.protocgen.textcodecs.jsonarray.codegen.java.JavaNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // Internal parser that takes a token list and offset
    emitParseFromTokens(w, message, className);

    // Public convenience: parse from string
    w.blankLine();
    w.block(
        "public static " + className + " fromPbtkUrl(String input)",
        () -> {
          w.line("if (input == null || input.isEmpty()) return new %s();", className);
          w.line("java.util.List<String> tokens = tokenizePbtk(input);");
          w.line("int[] offset = {0};");
          w.line("return parsePbtkTokens(tokens, tokens.size(), offset);");
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
        "private static "
            + className
            + " parsePbtkTokens(java.util.List<String> tokens, int fieldCount, int[] offset)",
        () -> {
          w.line("%s obj = new %s();", className, className);
          w.line("int consumed = 0;");
          w.block(
              "while (consumed < fieldCount && offset[0] < tokens.size())",
              () -> {
                w.line("String token = tokens.get(offset[0]);");
                // Parse field number and type char
                w.line("int numEnd = 0;");
                w.line(
                    "while (numEnd < token.length() && Character.isDigit(token.charAt(numEnd))) numEnd++;");
                w.line("if (numEnd == 0 || numEnd >= token.length()) { offset[0]++; consumed++; continue; }");
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
          w.line("return obj;");
        });
  }

  private void emitFieldCase(CodeWriter w, ProtoField field) {
    int fieldNum = field.getFieldNumber();
    String setter = "obj." + nameResolver.setterName(field.getName());
    String getter = "obj." + nameResolver.getterName(field.getName()) + "()";
    String javaField = "obj." + nameResolver.fieldName(field.getName());

    w.block(
        "case " + fieldNum + ":",
        () -> {
          if (field.isMap()) {
            emitMapDeserialize(w, field, javaField);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, getter);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
            emitMessageDeserialize(w, field, setter);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, setter);
          } else {
            emitScalarDeserialize(w, field, setter);
          }
          w.line("offset[0]++;");
          w.line("consumed++;");
          w.line("break;");
        });
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field, String setter) {
    String readExpr = scalarReadExpr(field.getProtoType(), "value");
    w.line("%s(%s);", setter, readExpr);
    if (field.hasExplicitPresence()) {
      w.line("obj.presentFields_.set(%d);", field.getArrayPosition());
    }
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String setter) {
    String enumType = simpleTypeName(field.getTypeReference());
    w.line("%s(%s.forNumber(Integer.parseInt(value)));", setter, enumType);
    if (field.hasExplicitPresence()) {
      w.line("obj.presentFields_.set(%d);", field.getArrayPosition());
    }
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field, String setter) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("int subCount = Integer.parseInt(value);");
    w.line("offset[0]++;");
    w.line("%s(%s.parsePbtkTokens(tokens, subCount, offset));", setter, msgType);
    // Don't increment offset again — the recursive call consumed the sub-tokens,
    // and the outer loop will increment once more, but we need to adjust:
    w.line("offset[0]--;"); // compensate for the outer offset[0]++ after break
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field, String getter) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("int subCount = Integer.parseInt(value);");
      w.line("offset[0]++;");
      w.line("%s.add(%s.parsePbtkTokens(tokens, subCount, offset));", getter, msgType);
      w.line("offset[0]--;");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      String enumType = simpleTypeName(field.getTypeReference());
      w.line("%s.add(%s.forNumber(Integer.parseInt(value)));", getter, enumType);
    } else {
      String readExpr = scalarReadExpr(field.getProtoType(), "value");
      w.line("%s.add(%s);", getter, readExpr);
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String javaField) {
    // Map entries are serialized as !<num>m2!1<keyType><key>!2<valType><val>
    w.line("int entryCount = Integer.parseInt(value);");
    w.line("offset[0]++;");
    // Parse key (field 1) and value (field 2) from the next entryCount tokens
    w.line("String entryKey = null;");
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
                String keyRead = scalarReadExpr(field.getMapKeyType(), "mval");
                w.line("entryKey = String.valueOf(%s);", keyRead);
              });
          w.block(
              "if (mfn == 2)",
              () -> {
                // Value
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
              });
          w.line("offset[0]++;");
        });
    w.line("offset[0]--;"); // compensate for outer offset[0]++
    // Put the entry into the map
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    if (stringKey) {
      w.line("if (entryKey != null) %s.put(entryKey, entryVal);", javaField);
    } else {
      String keyType =
          switch (field.getMapKeyType()) {
            case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 -> "Integer";
            case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "Long";
            case TYPE_BOOL -> "Boolean";
            default -> "Object";
          };
      w.line(
          "if (entryKey != null) %s.put(%s.valueOf(entryKey), entryVal);", javaField, keyType);
    }
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
          "java.net.URLDecoder.decode("
              + valueVar
              + ", java.nio.charset.StandardCharsets.UTF_8)";
      case TYPE_BYTES -> "java.util.Base64.getDecoder().decode(" + valueVar + ")";
      default -> valueVar;
    };
  }

  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "Object";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }
}
