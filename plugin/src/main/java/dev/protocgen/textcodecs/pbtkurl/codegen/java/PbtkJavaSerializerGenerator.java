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
 * Generates the toByteArray() method body for Java pbtk URL classes. Produces pbtk URL-encoded
 * strings using the Google Maps protobuf text format: {@code !<fieldNumber><typeChar><value>}.
 *
 * <p>Works with immutable message classes: reads fields via getters on the message instance.
 */
public class PbtkJavaSerializerGenerator {

  private final JavaNameResolver nameResolver;
  private final JavaTypeMapper typeMapper;

  public PbtkJavaSerializerGenerator(JavaTypeMapper typeMapper, JavaNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message) {
    // Inner method that produces the field tokens without leading context
    w.blankLine();
    w.block(
        "void appendPbtkFields(StringBuilder sb)",
        () -> {
          for (ProtoField field : message.getFields()) {
            emitFieldSerialize(w, field);
          }
        });

    // Count how many top-level fields this message serializes (for m<count> prefix)
    w.blankLine();
    w.block(
        "int countPbtkFields()",
        () -> {
          w.line("int count = 0;");
          for (ProtoField field : message.getFields()) {
            emitFieldCount(w, field);
          }
          w.line("return count;");
        });

    // Public: serialize to byte[]
    w.blankLine();
    w.block(
        "public byte[] toByteArray()",
        () -> {
          w.line("StringBuilder sb = new StringBuilder();");
          w.line("appendPbtkFields(sb);");
          w.line("return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);");
        });

    // Public: serialize to OutputStream
    w.blankLine();
    w.block(
        "public void writeTo(java.io.OutputStream output) throws java.io.IOException",
        () -> {
          w.line("output.write(toByteArray());");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String javaField = "this." + nameResolver.fieldName(field.getName());
    int fieldNum = field.getFieldNumber();

    if (field.isOneofMember()) {
      String caseName = nameResolver.fieldName(field.getOneofName()) + "Case_";
      String enumConst = field.getName().toUpperCase() + "Case_";
      w.block(
          "if (" + caseName + " == " + enumConst + ")",
          () -> {
            emitSingleFieldSerialize(w, field, javaField, fieldNum);
          });
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, javaField, fieldNum);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, javaField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, javaField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, javaField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, javaField, fieldNum);
    } else {
      emitScalarSerialize(w, field, javaField, fieldNum);
    }
  }

  private void emitSingleFieldSerialize(
      CodeWriter w, ProtoField field, String javaField, int fieldNum) {
    if (field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, javaField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, javaField, fieldNum);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, javaField, fieldNum);
    } else {
      emitScalarSerialize(w, field, javaField, fieldNum);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String javaField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_.get(" + field.getArrayPosition() + ")";
      w.block("if (" + bitCheck + ")", () -> emitScalarAppend(w, field, javaField, fieldNum));
      return;
    }
    emitScalarAppend(w, field, javaField, fieldNum);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String javaField, int fieldNum) {
    FieldDescriptorProto.Type type = field.getProtoType();
    String typeChar = pbtkTypeChar(type);

    switch (type) {
      case TYPE_BOOL:
        w.line("sb.append(\"!%d%s\").append(%s ? \"1\" : \"0\");", fieldNum, typeChar, javaField);
        break;
      case TYPE_BYTES:
        w.line(
            "sb.append(\"!%d%s\").append(java.util.Base64.getEncoder().encodeToString(%s));",
            fieldNum, typeChar, javaField);
        break;
      case TYPE_STRING:
        w.line(
            "sb.append(\"!%d%s\").append(java.net.URLEncoder.encode(%s, java.nio.charset.StandardCharsets.UTF_8));",
            fieldNum, typeChar, javaField);
        break;
      case TYPE_DOUBLE:
        w.block(
            "if (!Double.isNaN(" + javaField + ") && !Double.isInfinite(" + javaField + "))",
            () -> w.line("sb.append(\"!%d%s\").append(%s);", fieldNum, typeChar, javaField));
        break;
      case TYPE_FLOAT:
        w.block(
            "if (!Float.isNaN(" + javaField + ") && !Float.isInfinite(" + javaField + "))",
            () -> w.line("sb.append(\"!%d%s\").append(%s);", fieldNum, typeChar, javaField));
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line(
            "sb.append(\"!%d%s\").append(Integer.toUnsignedLong(%s));",
            fieldNum, typeChar, javaField);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line(
            "sb.append(\"!%d%s\").append(Long.toUnsignedString(%s));",
            fieldNum, typeChar, javaField);
        break;
      default:
        w.line("sb.append(\"!%d%s\").append(%s);", fieldNum, typeChar, javaField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String javaField, int fieldNum) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_.get(" + field.getArrayPosition() + ")";
      w.block(
          "if (" + bitCheck + ")",
          () ->
              w.line(
                  "sb.append(\"!%de\").append(%s != null ? %s.getNumber() : 0);",
                  fieldNum, javaField, javaField));
    } else {
      w.line(
          "sb.append(\"!%de\").append(%s != null ? %s.getNumber() : 0);",
          fieldNum, javaField, javaField);
    }
  }

  private void emitMessageSerialize(
      CodeWriter w, ProtoField field, String javaField, int fieldNum) {
    w.block(
        "if (" + javaField + " != null)",
        () -> {
          w.line("sb.append(\"!%dm\").append(%s.countPbtkFields());", fieldNum, javaField);
          w.line("%s.appendPbtkFields(sb);", javaField);
        });
  }

  private void emitRepeatedSerialize(
      CodeWriter w, ProtoField field, String javaField, int fieldNum) {
    String elementVar = "__" + nameResolver.fieldName(field.getName()) + "Item";
    String boxedType = elementBoxedType(field);
    w.block(
        "for (" + boxedType + " " + elementVar + " : " + javaField + ")",
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
            w.block(
                "if (" + elementVar + " != null)",
                () -> {
                  w.line("sb.append(\"!%dm\").append(%s.countPbtkFields());", fieldNum, elementVar);
                  w.line("%s.appendPbtkFields(sb);", elementVar);
                });
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line(
                "sb.append(\"!%de\").append(%s != null ? %s.getNumber() : 0);",
                fieldNum, elementVar, elementVar);
          } else {
            emitScalarAppend(w, field, elementVar, fieldNum);
          }
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String javaField, int fieldNum) {
    // Maps are serialized as repeated message entries:
    // !<fieldNum>m2!1<keyType><key>!2<valType><val>
    String entryVar = "__" + nameResolver.fieldName(field.getName()) + "Entry";
    w.block(
        "for (java.util.Map.Entry<?, ?> " + entryVar + " : " + javaField + ".entrySet())",
        () -> {
          // Each map entry is a synthetic message with field 1 = key, field 2 = value
          w.line("sb.append(\"!%dm2\");", fieldNum);

          // Key (field 1)
          String keyTypeChar = pbtkTypeChar(field.getMapKeyType());
          if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
            w.line(
                "sb.append(\"!1%s\").append(java.net.URLEncoder.encode((String) %s.getKey(), java.nio.charset.StandardCharsets.UTF_8));",
                keyTypeChar, entryVar);
          } else if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_BOOL) {
            w.line(
                "sb.append(\"!1%s\").append(((Boolean) %s.getKey()) ? \"1\" : \"0\");",
                keyTypeChar, entryVar);
          } else if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_UINT32
              || field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_FIXED32) {
            w.line(
                "sb.append(\"!1%s\").append(Integer.toUnsignedLong((Integer) %s.getKey()));",
                keyTypeChar, entryVar);
          } else if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_UINT64
              || field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_FIXED64) {
            w.line(
                "sb.append(\"!1%s\").append(Long.toUnsignedString((Long) %s.getKey()));",
                keyTypeChar, entryVar);
          } else if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_INT64
              || field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_SINT64
              || field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_SFIXED64) {
            w.line("sb.append(\"!1%s\").append((Long) %s.getKey());", keyTypeChar, entryVar);
          } else {
            w.line("sb.append(\"!1%s\").append(%s.getKey());", keyTypeChar, entryVar);
          }

          // Value (field 2)
          if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
            String msgType = simpleTypeName(field.getMapValueTypeReference());
            w.line(
                "if (%s.getValue() != null) { sb.append(\"!2m\").append(((%s) %s.getValue()).countPbtkFields()); ((%s) %s.getValue()).appendPbtkFields(sb); }",
                entryVar, msgType, entryVar, msgType, entryVar);
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
            String enumType = simpleTypeName(field.getMapValueTypeReference());
            w.line(
                "sb.append(\"!2e\").append(%s.getValue() != null ? ((%s) %s.getValue()).getNumber() : 0);",
                entryVar, enumType, entryVar);
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
            w.line(
                "sb.append(\"!2s\").append(java.net.URLEncoder.encode((String) %s.getValue(), java.nio.charset.StandardCharsets.UTF_8));",
                entryVar);
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
            w.line(
                "sb.append(\"!2z\").append(java.util.Base64.getEncoder().encodeToString((byte[]) %s.getValue()));",
                entryVar);
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BOOL) {
            w.line(
                "sb.append(\"!2b\").append(((Boolean) %s.getValue()) ? \"1\" : \"0\");", entryVar);
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
            w.line("{ Double __dv = ((Number) %s.getValue()).doubleValue();", entryVar);
            w.line("if (!__dv.isNaN() && !__dv.isInfinite()) sb.append(\"!2d\").append(__dv); }");
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
            w.line("{ Float __fv = ((Number) %s.getValue()).floatValue();", entryVar);
            w.line("if (!__fv.isNaN() && !__fv.isInfinite()) sb.append(\"!2f\").append(__fv); }");
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_UINT32
              || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_FIXED32) {
            w.line(
                "sb.append(\"!2i\").append(Integer.toUnsignedLong((Integer) %s.getValue()));",
                entryVar);
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_UINT64
              || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_FIXED64) {
            w.line(
                "sb.append(\"!2i\").append(Long.toUnsignedString((Long) %s.getValue()));",
                entryVar);
          } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_INT64
              || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_SINT64
              || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_SFIXED64) {
            w.line("sb.append(\"!2i\").append((Long) %s.getValue());", entryVar);
          } else {
            String valTypeChar = pbtkTypeChar(field.getMapValueType());
            w.line("sb.append(\"!2%s\").append(%s.getValue());", valTypeChar, entryVar);
          }
        });
  }

  private void emitFieldCount(CodeWriter w, ProtoField field) {
    String javaField = "this." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = nameResolver.fieldName(field.getOneofName()) + "Case_";
      String enumConst = field.getName().toUpperCase() + "Case_";
      w.line("if (%s == %s) count++;", caseName, enumConst);
      return;
    }

    if (field.isMap()) {
      w.line("count += %s.size();", javaField);
    } else if (field.isRepeated()) {
      if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
        // Filter null elements — serializer skips them, so count must match
        w.line("count += (int) %s.stream().filter(java.util.Objects::nonNull).count();", javaField);
      } else {
        w.line("count += %s.size();", javaField);
      }
    } else if (field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("if (%s != null) count++;", javaField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      w.line("if (%s != null) count++;", javaField);
    } else if (field.hasExplicitPresence() && !field.isRequired()) {
      w.line("if (presentFields_.get(%d)) count++;", field.getArrayPosition());
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      w.line("if (!Double.isNaN(%s) && !Double.isInfinite(%s)) count++;", javaField, javaField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
      w.line("if (!Float.isNaN(%s) && !Float.isInfinite(%s)) count++;", javaField, javaField);
    } else {
      w.line("count++;");
    }
  }

  private String elementBoxedType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference());
    }
    return typeMapper.boxedScalarType(field.getProtoType());
  }

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

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Object";
  }
}
