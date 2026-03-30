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
package dev.protocgen.textcodecs.jsonarray.codegen.kotlin;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates StringBuilder-based serialization methods for Kotlin classes. Uses StringBuilder
 * directly to produce JSON array strings with fields at positions determined by field number.
 */
public class KotlinSerializerGenerator {

  private final KotlinTypeMapper typeMapper;
  private final KotlinNameResolver nameResolver;

  public KotlinSerializerGenerator(KotlinTypeMapper typeMapper, KotlinNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message) {
    // Internal method: appends the JSON array to a shared StringBuilder
    w.blankLine();
    w.block(
        "internal fun appendJsonArray(sb: StringBuilder)",
        () -> {
          w.line("sb.append('[')");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            if (pos > 0) {
              w.line("sb.append(',')");
            }
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("sb.append(\"null\") // gap (no field number %d)", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("sb.append(']')");
        });

    // Public convenience: serialize to JSON string
    w.blankLine();
    w.block(
        "fun toJsonString(): String",
        () -> {
          w.line(
              "val sb = StringBuilder(%d)",
              Math.max(64, message.getMaxFieldNumber() * 32));
          w.line("appendJsonArray(sb)");
          w.line("return sb.toString()");
        });

    // Public convenience: serialize to ByteArray
    w.blankLine();
    w.block(
        "fun toJsonBytes(): ByteArray",
        () -> {
          w.line("return toJsonString().toByteArray(Charsets.UTF_8)");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String ktField = "this." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = nameResolver.fieldName(field.getOneofName()) + "Case_";
      String enumConst = field.getName().toUpperCase() + "Case_";
      w.block(
          "if (" + caseName + " == " + enumConst + ")",
          () -> {
            emitValueForField(w, field, ktField);
          });
      w.line("else { sb.append(\"null\") }");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, ktField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, ktField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, ktField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, ktField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, ktField);
    } else {
      emitScalarSerialize(w, field, ktField);
    }
  }

  /** Emit the value write for a field (used inside oneof conditional blocks). */
  private void emitValueForField(CodeWriter w, ProtoField field, String ktField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "if (" + ktField + " != null)",
          () -> {
            w.line("%s!!.appendJsonArray(sb)", ktField);
          });
      w.line("else { sb.append(\"null\") }");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("sb.append(%s?.getNumber() ?: 0)", ktField);
    } else {
      emitScalarAppend(w, field, ktField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String ktField) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_.get(" + field.getArrayPosition() + ")";
      w.block(
          "if (" + bitCheck + ")",
          () -> {
            emitScalarAppend(w, field, ktField);
          });
      w.line("else { sb.append(\"null\") }");
      return;
    }
    emitScalarAppend(w, field, ktField);
  }

  /**
   * Emit the correct sb.append(...) call for a scalar field, handling int64/uint64 as strings,
   * uint32 as unsigned, float/double NaN/Infinity, and bytes as base64.
   */
  private void emitScalarAppend(CodeWriter w, ProtoField field, String ktField) {
    FieldDescriptorProto.Type protoType = field.getProtoType();
    switch (protoType) {
      case TYPE_STRING:
        w.line(
            "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, %s)",
            ktField);
        break;
      case TYPE_BYTES:
        w.line(
            "sb.append('\"').append(java.util.Base64.getEncoder().encodeToString(%s)).append('\"')",
            ktField);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        // Serialize signed 64-bit integers as quoted strings to avoid JS precision loss
        w.line("sb.append('\"').append(%s.toString()).append('\"')", ktField);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        // Serialize unsigned 64-bit integers as quoted strings using unsigned representation
        w.line("sb.append('\"').append(java.lang.Long.toUnsignedString(%s)).append('\"')", ktField);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        // Serialize unsigned 32-bit integers as their unsigned long representation
        w.line("sb.append(Integer.toUnsignedLong(%s))", ktField);
        break;
      case TYPE_DOUBLE:
        w.block(
            "if (" + ktField + ".isNaN() || " + ktField + ".isInfinite())",
            () -> {
              w.line("sb.append(\"null\")");
            });
        w.block(
            "else",
            () -> {
              w.line("sb.append(%s)", ktField);
            });
        break;
      case TYPE_FLOAT:
        w.block(
            "if (" + ktField + ".isNaN() || " + ktField + ".isInfinite())",
            () -> {
              w.line("sb.append(\"null\")");
            });
        w.block(
            "else",
            () -> {
              w.line("sb.append(%s)", ktField);
            });
        break;
      default:
        // int32, sint32, sfixed32, bool
        w.line("sb.append(%s)", ktField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String ktField) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_.get(" + field.getArrayPosition() + ")";
      w.block(
          "if (" + bitCheck + ")",
          () -> {
            w.line("sb.append(%s?.getNumber() ?: 0)", ktField);
          });
      w.line("else { sb.append(\"null\") }");
    } else {
      w.line("sb.append(%s?.getNumber() ?: 0)", ktField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String ktField) {
    w.block(
        "if (" + ktField + " != null)",
        () -> {
          w.line("%s!!.appendJsonArray(sb)", ktField);
        });
    w.line("else { sb.append(\"null\") }");
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String ktField) {
    w.block(
        "run",
        () -> {
          w.line("sb.append('[')");
          String indexVar = "__" + nameResolver.fieldName(field.getName()) + "Idx";
          w.line("var %s = 0", indexVar);
          w.block(
              "for (__item in " + ktField + ")",
              () -> {
                w.line("if (%s > 0) sb.append(',')", indexVar);
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.block(
                      "if (__item != null)",
                      () -> {
                        w.line("__item.appendJsonArray(sb)");
                      });
                  w.line("else { sb.append(\"null\") }");
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("sb.append(__item?.getNumber() ?: 0)");
                } else {
                  emitScalarAppend(w, field, "__item");
                }
                w.line("%s++", indexVar);
              });
          w.line("sb.append(']')");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String ktField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "run",
        () -> {
          String indexVar = "__" + nameResolver.fieldName(field.getName()) + "Idx";
          if (stringKey) {
            // String-keyed map: serialize as JSON object {"key":value, ...}
            w.line("sb.append('{')");
            w.line("var %s = 0", indexVar);
            w.block(
                "for ((__key, __value) in " + ktField + ")",
                () -> {
                  w.line("if (%s > 0) sb.append(',')", indexVar);
                  w.line(
                      "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, __key as String)");
                  w.line("sb.append(':')");
                  emitMapValueAppend(w, field, "__value");
                  w.line("%s++", indexVar);
                });
            w.line("sb.append('}')");
          } else {
            // Non-string-keyed map: serialize as array of [key,value] pairs
            w.line("sb.append('[')");
            w.line("var %s = 0", indexVar);
            w.block(
                "for ((__key, __value) in " + ktField + ")",
                () -> {
                  w.line("if (%s > 0) sb.append(',')", indexVar);
                  w.line("sb.append('[')");
                  emitMapKeyAppend(w, field, "__key");
                  w.line("sb.append(',')");
                  emitMapValueAppend(w, field, "__value");
                  w.line("sb.append(']')");
                  w.line("%s++", indexVar);
                });
            w.line("sb.append(']')");
          }
        });
  }

  private void emitMapKeyAppend(CodeWriter w, ProtoField field, String keyExpr) {
    FieldDescriptorProto.Type keyType = field.getMapKeyType();
    switch (keyType) {
      case TYPE_STRING:
        w.line(
            "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, %s as String)",
            keyExpr);
        break;
      case TYPE_BOOL:
        w.line("sb.append(%s as Boolean)", keyExpr);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        w.line("sb.append(Integer.toUnsignedLong(%s as Int))", keyExpr);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("sb.append('\"').append((%s as Long).toString()).append('\"')", keyExpr);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line(
            "sb.append('\"').append(java.lang.Long.toUnsignedString(%s as Long)).append('\"')",
            keyExpr);
        break;
      default:
        w.line("sb.append(%s as Int)", keyExpr);
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
            w.line("(%s as %s).appendJsonArray(sb)", valueExpr, msgType);
          });
      w.line("else { sb.append(\"null\") }");
    } else if (valueType == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      w.line(
          "sb.append(if (%s != null) (%s as %s).getNumber() else 0)",
          valueExpr, valueExpr, enumType);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line(
          "dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayWriter.appendQuotedString(sb, %s as String)",
          valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line(
          "sb.append('\"').append(java.util.Base64.getEncoder().encodeToString(%s as ByteArray)).append('\"')",
          valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_INT64
        || valueType == FieldDescriptorProto.Type.TYPE_SINT64
        || valueType == FieldDescriptorProto.Type.TYPE_SFIXED64) {
      w.line("sb.append('\"').append((%s as Long).toString()).append('\"')", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_UINT64
        || valueType == FieldDescriptorProto.Type.TYPE_FIXED64) {
      w.line(
          "sb.append('\"').append(java.lang.Long.toUnsignedString(%s as Long)).append('\"')",
          valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_UINT32
        || valueType == FieldDescriptorProto.Type.TYPE_FIXED32) {
      w.line("sb.append(Integer.toUnsignedLong(%s as Int))", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      String castExpr = "(" + valueExpr + " as Double)";
      w.block(
          "if (" + castExpr + ".isNaN() || " + castExpr + ".isInfinite())",
          () -> {
            w.line("sb.append(\"null\")");
          });
      w.block(
          "else",
          () -> {
            w.line("sb.append(%s)", castExpr);
          });
    } else if (valueType == FieldDescriptorProto.Type.TYPE_FLOAT) {
      String castExpr = "(" + valueExpr + " as Float)";
      w.block(
          "if (" + castExpr + ".isNaN() || " + castExpr + ".isInfinite())",
          () -> {
            w.line("sb.append(\"null\")");
          });
      w.block(
          "else",
          () -> {
            w.line("sb.append(%s)", castExpr);
          });
    } else if (valueType == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("sb.append(%s as Boolean)", valueExpr);
    } else {
      w.line("sb.append(%s as Int)", valueExpr);
    }
  }

  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "Any";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }
}
