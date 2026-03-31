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
package dev.protocgen.textcodecs.jsonarray.codegen.csharp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates JSON array serialization methods for C# classes. Uses System.Text.Json via a
 * StringBuilder approach: fields are written at positions determined by field number.
 */
public class CSharpSerializerGenerator {

  private final CSharpTypeMapper typeMapper;
  private final CSharpNameResolver nameResolver;

  public CSharpSerializerGenerator(CSharpTypeMapper typeMapper, CSharpNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message) {
    // Internal method: appends the JSON array to a shared StringBuilder
    w.blankLine();
    w.block(
        "internal void AppendJsonArray(StringBuilder sb)",
        () -> {
          w.line("sb.Append('[');");

          int maxPos = message.getMaxFieldNumber(); // positions 0..maxPos-1
          for (int pos = 0; pos < maxPos; pos++) {
            if (pos > 0) {
              w.line("sb.Append(',');");
            }
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              // Gap in field numbering
              w.line("sb.Append(\"null\"); // gap (no field number %d)", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("sb.Append(']');");
        });

    // Public convenience: serialize to JSON string
    w.blankLine();
    w.block(
        "public string ToJsonString()",
        () -> {
          w.line("var sb = new StringBuilder(%d);", Math.max(64, message.getMaxFieldNumber() * 32));
          w.line("AppendJsonArray(sb);");
          w.line("return sb.ToString();");
        });

    // Public convenience: serialize to byte[]
    w.blankLine();
    w.block(
        "public byte[] ToJsonBytes()",
        () -> {
          w.line("return System.Text.Encoding.UTF8.GetBytes(ToJsonString());");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String csField = "this." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      // For oneof members: write the value if this is the active case, else null
      String caseName = nameResolver.fieldName(field.getOneofName()) + "Case_";
      String enumConst = field.getName().toUpperCase() + "Case_";
      w.block(
          "if (" + caseName + " == " + enumConst + ")",
          () -> {
            emitValueForField(w, field, csField);
          });
      w.line("else { sb.Append(\"null\"); }");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, csField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, csField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, csField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, csField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, csField);
    } else {
      emitScalarSerialize(w, field, csField);
    }
  }

  /** Emit the value write for a field (used inside oneof conditional blocks). */
  private void emitValueForField(CodeWriter w, ProtoField field, String csField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "if (" + csField + " != null)",
          () -> {
            w.line("%s.AppendJsonArray(sb);", csField);
          });
      w.line("else { sb.Append(\"null\"); }");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("sb.Append(%s != null ? (int)%s : 0);", csField, csField);
    } else {
      emitScalarAppend(w, field, csField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String csField) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_[" + field.getArrayPosition() + "]";
      w.block(
          "if (" + bitCheck + ")",
          () -> {
            emitScalarAppend(w, field, csField);
          });
      w.line("else { sb.Append(\"null\"); }");
      return;
    }
    emitScalarAppend(w, field, csField);
  }

  /**
   * Emit the correct sb.Append(...) call for a scalar field, handling int64/uint64 as strings,
   * float/double NaN/Infinity, and bytes as base64.
   */
  private void emitScalarAppend(CodeWriter w, ProtoField field, String csField) {
    FieldDescriptorProto.Type protoType = field.getProtoType();
    switch (protoType) {
      case TYPE_STRING:
        w.line("sb.Append('\"');");
        w.line("sb.Append(JsonEncodedText.Encode(%s).ToString());", csField);
        w.line("sb.Append('\"');");
        break;
      case TYPE_BYTES:
        w.line("sb.Append('\"').Append(Convert.ToBase64String(%s)).Append('\"');", csField);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        // Serialize signed 64-bit integers as quoted strings to avoid JS precision loss
        w.line("sb.Append('\"').Append(%s).Append('\"');", csField);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        // Serialize unsigned 64-bit integers as quoted strings
        w.line("sb.Append('\"').Append(%s).Append('\"');", csField);
        break;
      case TYPE_UINT32, TYPE_FIXED32:
        // Serialize unsigned 32-bit integers
        w.line("sb.Append(%s);", csField);
        break;
      case TYPE_DOUBLE:
        w.block(
            "if (double.IsNaN(" + csField + ") || double.IsInfinity(" + csField + "))",
            () -> {
              w.line("sb.Append(\"null\");");
            });
        w.block(
            "else",
            () -> {
              w.line("sb.Append(%s);", csField);
            });
        break;
      case TYPE_FLOAT:
        w.block(
            "if (float.IsNaN(" + csField + ") || float.IsInfinity(" + csField + "))",
            () -> {
              w.line("sb.Append(\"null\");");
            });
        w.block(
            "else",
            () -> {
              w.line("sb.Append(%s);", csField);
            });
        break;
      case TYPE_BOOL:
        w.line("sb.Append(%s ? \"true\" : \"false\");", csField);
        break;
      default:
        // int32, sint32, sfixed32 -- sb.Append(value) works directly
        w.line("sb.Append(%s);", csField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String csField) {
    if (field.hasExplicitPresence() && !field.isRequired()) {
      String bitCheck = "presentFields_[" + field.getArrayPosition() + "]";
      w.block(
          "if (" + bitCheck + ")",
          () -> {
            w.line("sb.Append(%s != null ? (int)%s : 0);", csField, csField);
          });
      w.line("else { sb.Append(\"null\"); }");
    } else {
      w.line("sb.Append(%s != null ? (int)%s : 0);", csField, csField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String csField) {
    w.block(
        "if (" + csField + " != null)",
        () -> {
          w.line("%s.AppendJsonArray(sb);", csField);
        });
    w.line("else { sb.Append(\"null\"); }");
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String csField) {
    w.block(
        "",
        () -> {
          w.line("sb.Append('[');");
          String elementVar = "__" + nameResolver.fieldName(field.getName()) + "Item";
          String indexVar = "__" + nameResolver.fieldName(field.getName()) + "Idx";
          w.line("int %s = 0;", indexVar);
          w.block(
              "foreach (var " + elementVar + " in " + csField + ")",
              () -> {
                w.line("if (%s > 0) sb.Append(',');", indexVar);
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.block(
                      "if (" + elementVar + " != null)",
                      () -> {
                        w.line("%s.AppendJsonArray(sb);", elementVar);
                      });
                  w.line("else { sb.Append(\"null\"); }");
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("sb.Append(%s != null ? (int)%s : 0);", elementVar, elementVar);
                } else {
                  emitScalarAppend(w, field, elementVar);
                }
                w.line("%s++;", indexVar);
              });
          w.line("sb.Append(']');");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String csField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "",
        () -> {
          String entryVar = "__" + nameResolver.fieldName(field.getName()) + "Entry";
          String indexVar = "__" + nameResolver.fieldName(field.getName()) + "Idx";
          if (stringKey) {
            // String-keyed map: serialize as JSON object {"key":value, ...}
            w.line("sb.Append('{');");
            w.line("int %s = 0;", indexVar);
            w.block(
                "foreach (var " + entryVar + " in " + csField + ")",
                () -> {
                  w.line("if (%s > 0) sb.Append(',');", indexVar);
                  // Key: always a quoted string
                  w.line("sb.Append('\"');");
                  w.line("sb.Append(JsonEncodedText.Encode(%s.Key).ToString());", entryVar);
                  w.line("sb.Append('\"');");
                  w.line("sb.Append(':');");
                  // Value
                  emitMapValueAppend(w, field, entryVar + ".Value");
                  w.line("%s++;", indexVar);
                });
            w.line("sb.Append('}');");
          } else {
            // Non-string-keyed map: serialize as array of [key,value] pairs
            w.line("sb.Append('[');");
            w.line("int %s = 0;", indexVar);
            w.block(
                "foreach (var " + entryVar + " in " + csField + ")",
                () -> {
                  w.line("if (%s > 0) sb.Append(',');", indexVar);
                  w.line("sb.Append('[');");
                  emitMapKeyAppend(w, field, entryVar + ".Key");
                  w.line("sb.Append(',');");
                  emitMapValueAppend(w, field, entryVar + ".Value");
                  w.line("sb.Append(']');");
                  w.line("%s++;", indexVar);
                });
            w.line("sb.Append(']');");
          }
        });
  }

  private void emitMapKeyAppend(CodeWriter w, ProtoField field, String keyExpr) {
    FieldDescriptorProto.Type keyType = field.getMapKeyType();
    switch (keyType) {
      case TYPE_STRING:
        w.line("sb.Append('\"');");
        w.line("sb.Append(JsonEncodedText.Encode(%s).ToString());", keyExpr);
        w.line("sb.Append('\"');");
        break;
      case TYPE_BOOL:
        w.line("sb.Append(%s ? \"true\" : \"false\");", keyExpr);
        break;
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64:
        w.line("sb.Append('\"').Append(%s).Append('\"');", keyExpr);
        break;
      case TYPE_UINT64, TYPE_FIXED64:
        w.line("sb.Append('\"').Append(%s).Append('\"');", keyExpr);
        break;
      default:
        // int32, uint32, sint32, sfixed32, fixed32
        w.line("sb.Append(%s);", keyExpr);
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
            w.line("%s.AppendJsonArray(sb);", valueExpr);
          });
      w.line("else { sb.Append(\"null\"); }");
    } else if (valueType == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("sb.Append(%s != null ? (int)%s : 0);", valueExpr, valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("sb.Append('\"');");
      w.line("sb.Append(JsonEncodedText.Encode(%s).ToString());", valueExpr);
      w.line("sb.Append('\"');");
    } else if (valueType == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("sb.Append('\"').Append(Convert.ToBase64String(%s)).Append('\"');", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_INT64
        || valueType == FieldDescriptorProto.Type.TYPE_SINT64
        || valueType == FieldDescriptorProto.Type.TYPE_SFIXED64) {
      w.line("sb.Append('\"').Append(%s).Append('\"');", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_UINT64
        || valueType == FieldDescriptorProto.Type.TYPE_FIXED64) {
      w.line("sb.Append('\"').Append(%s).Append('\"');", valueExpr);
    } else if (valueType == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      w.block(
          "if (double.IsNaN(" + valueExpr + ") || double.IsInfinity(" + valueExpr + "))",
          () -> {
            w.line("sb.Append(\"null\");");
          });
      w.block(
          "else",
          () -> {
            w.line("sb.Append(%s);", valueExpr);
          });
    } else if (valueType == FieldDescriptorProto.Type.TYPE_FLOAT) {
      w.block(
          "if (float.IsNaN(" + valueExpr + ") || float.IsInfinity(" + valueExpr + "))",
          () -> {
            w.line("sb.Append(\"null\");");
          });
      w.block(
          "else",
          () -> {
            w.line("sb.Append(%s);", valueExpr);
          });
    } else if (valueType == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("sb.Append(%s ? \"true\" : \"false\");", valueExpr);
    } else {
      // int32, uint32, sint32, sfixed32, fixed32
      w.line("sb.Append(%s);", valueExpr);
    }
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "object";
  }
}
