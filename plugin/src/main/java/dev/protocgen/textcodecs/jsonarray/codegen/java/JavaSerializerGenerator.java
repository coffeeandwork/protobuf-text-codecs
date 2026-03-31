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
package dev.protocgen.textcodecs.jsonarray.codegen.java;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates Jackson-free serialization methods for Java classes. Uses StringBuilder directly to
 * produce JSON array strings with fields at positions determined by field number.
 */
public class JavaSerializerGenerator {

  private final JavaTypeMapper typeMapper;
  private final JavaNameResolver nameResolver;

  public JavaSerializerGenerator(JavaTypeMapper typeMapper, JavaNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message) {
    // Internal method: appends the JSON array to a shared StringBuilder
    w.blankLine();
    w.block(
        "void appendJsonArray(StringBuilder sb)",
        () -> {
          w.line("sb.append('[');");

          int maxPos = message.getMaxFieldNumber(); // positions 0..maxPos-1
          for (int pos = 0; pos < maxPos; pos++) {
            if (pos > 0) {
              w.line("sb.append(',');");
            }
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              // Gap in field numbering
              w.line("sb.append(\"null\"); // gap (no field number %d)", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("sb.append(']');");
        });

    // Public convenience: serialize to JSON string
    w.blankLine();
    w.block(
        "public String toJsonString()",
        () -> {
          w.line(
              "StringBuilder sb = new StringBuilder(%d);",
              Math.max(64, message.getMaxFieldNumber() * 32));
          w.line("appendJsonArray(sb);");
          w.line("return sb.toString();");
        });

    // Public convenience: serialize to byte[]
    w.blankLine();
    w.block(
        "public byte[] toJsonBytes()",
        () -> {
          w.line("return toJsonString().getBytes(java.nio.charset.StandardCharsets.UTF_8);");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String javaField = "this." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      // For oneof members: write the value if this is the active case, else null
      String caseName = nameResolver.fieldName(field.getOneofName()) + "Case_";
      String enumConst = field.getName().toUpperCase() + "Case_";
      w.block(
          "if (" + caseName + " == " + enumConst + ")",
          () -> {
            emitValueForField(w, field, javaField);
          });
      w.line("else { sb.append(\"null\"); }");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, javaField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, javaField);
    } else if (field.isWellKnownType()) {
      // For now, serialize WKTs as nested messages (WKT runtime can be updated later)
      emitMessageSerialize(w, field, javaField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, javaField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, javaField);
    } else {
      emitScalarSerialize(w, field, javaField);
    }
  }

  /** Emit the value write for a field (used inside oneof conditional blocks). */
  private void emitValueForField(CodeWriter w, ProtoField field, String javaField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "if (" + javaField + " != null)",
          () -> {
            w.line("%s.appendJsonArray(sb);", javaField);
          });
      w.line("else { sb.append(\"null\"); }");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("sb.append(%s != null ? %s.getNumber() : 0);", javaField, javaField);
    } else {
      emitScalarAppend(w, field, javaField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String javaField) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_.get(" + field.getArrayPosition() + ")";
      w.block(
          "if (" + bitCheck + ")",
          () -> {
            emitScalarAppend(w, field, javaField);
          });
      w.line("else { sb.append(\"null\"); }");
      return;
    }
    emitScalarAppend(w, field, javaField);
  }

  /**
   * Emit the correct sb.append(...) call for a scalar field, handling int64/uint64 as strings,
   * uint32 as unsigned, float/double NaN/Infinity, and bytes as base64.
   */
  private void emitScalarAppend(CodeWriter w, ProtoField field, String javaField) {
    FieldDescriptorProto.Type protoType = field.getProtoType();
    switch (protoType) {
      case TYPE_STRING:
        w.line(
            "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, %s);",
            javaField);
        break;
      case TYPE_BYTES:
        w.line(
            "sb.append('\"').append(java.util.Base64.getEncoder().encodeToString(%s)).append('\"');",
            javaField);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        // Serialize signed 64-bit integers as quoted strings to avoid JS precision loss
        w.line("sb.append('\"').append(String.valueOf(%s)).append('\"');", javaField);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        // Serialize unsigned 64-bit integers as quoted strings using unsigned representation
        w.line("sb.append('\"').append(Long.toUnsignedString(%s)).append('\"');", javaField);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        // Serialize unsigned 32-bit integers as their unsigned long representation
        w.line("sb.append(Integer.toUnsignedLong(%s));", javaField);
        break;
      case TYPE_DOUBLE:
        w.block(
            "if (Double.isNaN(" + javaField + ") || Double.isInfinite(" + javaField + "))",
            () -> {
              w.line("sb.append(\"null\");");
            });
        w.block(
            "else",
            () -> {
              w.line("sb.append(%s);", javaField);
            });
        break;
      case TYPE_FLOAT:
        w.block(
            "if (Float.isNaN(" + javaField + ") || Float.isInfinite(" + javaField + "))",
            () -> {
              w.line("sb.append(\"null\");");
            });
        w.block(
            "else",
            () -> {
              w.line("sb.append(%s);", javaField);
            });
        break;
      default:
        // int32, sint32, sfixed32, bool — sb.append(value) works directly
        w.line("sb.append(%s);", javaField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String javaField) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_.get(" + field.getArrayPosition() + ")";
      w.block(
          "if (" + bitCheck + ")",
          () -> {
            w.line("sb.append(%s != null ? %s.getNumber() : 0);", javaField, javaField);
          });
      w.line("else { sb.append(\"null\"); }");
    } else {
      w.line("sb.append(%s != null ? %s.getNumber() : 0);", javaField, javaField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String javaField) {
    w.block(
        "if (" + javaField + " != null)",
        () -> {
          w.line("%s.appendJsonArray(sb);", javaField);
        });
    w.line("else { sb.append(\"null\"); }");
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String javaField) {
    w.block(
        "",
        () -> {
          w.line("sb.append('[');");
          String elementVar = "__" + nameResolver.fieldName(field.getName()) + "Item";
          String indexVar = "__" + nameResolver.fieldName(field.getName()) + "Idx";
          w.line("int %s = 0;", indexVar);
          w.block(
              "for (" + elementBoxedType(field) + " " + elementVar + " : " + javaField + ")",
              () -> {
                w.line("if (%s > 0) sb.append(',');", indexVar);
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.block(
                      "if (" + elementVar + " != null)",
                      () -> {
                        w.line("%s.appendJsonArray(sb);", elementVar);
                      });
                  w.line("else { sb.append(\"null\"); }");
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("sb.append(%s != null ? %s.getNumber() : 0);", elementVar, elementVar);
                } else {
                  emitScalarAppend(w, field, elementVar);
                }
                w.line("%s++;", indexVar);
              });
          w.line("sb.append(']');");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String javaField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "",
        () -> {
          String entryVar = "__" + nameResolver.fieldName(field.getName()) + "Entry";
          String indexVar = "__" + nameResolver.fieldName(field.getName()) + "Idx";
          if (stringKey) {
            // String-keyed map: serialize as JSON object {"key":value, ...}
            w.line("sb.append('{');");
            w.line("int %s = 0;", indexVar);
            w.block(
                "for (java.util.Map.Entry<?, ?> " + entryVar + " : " + javaField + ".entrySet())",
                () -> {
                  w.line("if (%s > 0) sb.append(',');", indexVar);
                  // Key: always a quoted string
                  w.line(
                      "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, (String) %s.getKey());",
                      entryVar);
                  w.line("sb.append(':');");
                  // Value
                  emitMapValueAppend(w, field, entryVar + ".getValue()");
                  w.line("%s++;", indexVar);
                });
            w.line("sb.append('}');");
          } else {
            // Non-string-keyed map: serialize as array of [key,value] pairs
            w.line("sb.append('[');");
            w.line("int %s = 0;", indexVar);
            w.block(
                "for (java.util.Map.Entry<?, ?> " + entryVar + " : " + javaField + ".entrySet())",
                () -> {
                  w.line("if (%s > 0) sb.append(',');", indexVar);
                  w.line("sb.append('[');");
                  emitMapKeyAppend(w, field, entryVar + ".getKey()");
                  w.line("sb.append(',');");
                  emitMapValueAppend(w, field, entryVar + ".getValue()");
                  w.line("sb.append(']');");
                  w.line("%s++;", indexVar);
                });
            w.line("sb.append(']');");
          }
        });
  }

  private void emitMapKeyAppend(CodeWriter w, ProtoField field, String keyExpr) {
    FieldDescriptorProto.Type keyType = field.getMapKeyType();
    switch (keyType) {
      case TYPE_STRING:
        w.line(
            "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, (String) %s);",
            keyExpr);
        break;
      case TYPE_BOOL:
        w.line("sb.append((Boolean) %s);", keyExpr);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line("sb.append(Integer.toUnsignedLong((Integer) %s));", keyExpr);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("sb.append('\"').append(String.valueOf((Long) %s)).append('\"');", keyExpr);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line("sb.append('\"').append(Long.toUnsignedString((Long) %s)).append('\"');", keyExpr);
        break;
      default:
        // int32, sint32, sfixed32
        w.line("sb.append((Integer) %s);", keyExpr);
        break;
    }
  }

  private void emitMapValueAppend(CodeWriter w, ProtoField field, String valueExpr) {
    FieldDescriptorProto.Type valueType = field.getMapValueType();
    if (valueType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      w.block(
          "if (" + valueExpr + " != null)",
          () -> {
            w.line("((%s) %s).appendJsonArray(sb);", msgType, valueExpr);
          });
      w.line("else { sb.append(\"null\"); }");
    } else if (valueType == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      w.line("sb.append(%s != null ? ((%s) %s).getNumber() : 0);", valueExpr, enumType, valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line(
          "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, (String) %s);",
          valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line(
          "sb.append('\"').append(java.util.Base64.getEncoder().encodeToString((byte[]) %s)).append('\"');",
          valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_INT64
        || valueType == FieldDescriptorProto.Type.TYPE_SINT64
        || valueType == FieldDescriptorProto.Type.TYPE_SFIXED64) {
      w.line("sb.append('\"').append(String.valueOf((Long) %s)).append('\"');", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_UINT64
        || valueType == FieldDescriptorProto.Type.TYPE_FIXED64) {
      w.line("sb.append('\"').append(Long.toUnsignedString((Long) %s)).append('\"');", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_UINT32
        || valueType == FieldDescriptorProto.Type.TYPE_FIXED32) {
      w.line("sb.append(Integer.toUnsignedLong((Integer) %s));", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      w.block(
          "if (Double.isNaN((Double) "
              + valueExpr
              + ") || Double.isInfinite((Double) "
              + valueExpr
              + "))",
          () -> {
            w.line("sb.append(\"null\");");
          });
      w.block(
          "else",
          () -> {
            w.line("sb.append((Double) %s);", valueExpr);
          });
    } else if (valueType == FieldDescriptorProto.Type.TYPE_FLOAT) {
      w.block(
          "if (Float.isNaN((Float) "
              + valueExpr
              + ") || Float.isInfinite((Float) "
              + valueExpr
              + "))",
          () -> {
            w.line("sb.append(\"null\");");
          });
      w.block(
          "else",
          () -> {
            w.line("sb.append((Float) %s);", valueExpr);
          });
    } else if (valueType == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("sb.append((Boolean) %s);", valueExpr);
    } else {
      // int32, sint32, sfixed32
      w.line("sb.append((Integer) %s);", valueExpr);
    }
  }

  private String elementBoxedType(ProtoField field) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      return simpleTypeName(field.getTypeReference());
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      return simpleTypeName(field.getTypeReference());
    }
    return typeMapper.boxedScalarType(field.getProtoType());
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Object";
  }
}
