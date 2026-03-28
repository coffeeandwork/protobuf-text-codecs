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
package dev.protocgen.textcodecs.jsonarray.codegen.javascript;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize() method body for JavaScript classes. Produces a plain JS array with
 * fields at positions determined by field number.
 */
public class JavaScriptSerializerGenerator {

  private final JavaScriptTypeMapper typeMapper;
  private final JavaScriptNameResolver nameResolver;

  public JavaScriptSerializerGenerator(
      JavaScriptTypeMapper typeMapper, JavaScriptNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message) {
    // serialize() -> returns an Array
    w.blankLine();
    w.line("/**");
    w.line(" * Serialize this message to a positional JSON array.");
    w.line(" * @returns {Array} The serialized array.");
    w.line(" */");
    w.block(
        "serialize()",
        () -> {
          w.line("const array = [];");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("array.push(null); // gap (no field number %d)", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("return array;");
        });

    // toJSON() convenience
    w.blankLine();
    w.line("/**");
    w.line(" * Serialize this message to a JSON-compatible array (used by JSON.stringify).");
    w.line(" * @returns {Array} The serialized array.");
    w.line(" */");
    w.block(
        "toJSON()",
        () -> {
          w.line("return this.serialize();");
        });

    // toJsonString() convenience
    w.blankLine();
    w.line("/**");
    w.line(" * Serialize this message to a JSON string.");
    w.line(" * @returns {string} The JSON string.");
    w.line(" */");
    w.block(
        "toJsonString()",
        () -> {
          w.line("return JSON.stringify(this.serialize());");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String jsField = "this." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseProp = "this._" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.block(
          "if (" + caseProp + " === " + field.getFieldNumber() + ")",
          () -> {
            emitValueForField(w, field, jsField);
          });
      w.line("else { array.push(null); }");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, jsField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, jsField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, jsField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, jsField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, jsField);
    } else {
      emitScalarSerialize(w, field, jsField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String jsField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("array.push(%s != null ? %s.serialize() : null);", jsField, jsField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("array.push(%s != null ? %s : 0);", jsField, jsField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      emitBytesEncode(w, jsField, "array.push(%s);");
    } else if (isInt64Type(field.getProtoType())) {
      w.line("array.push(String(%s));", jsField);
    } else {
      w.line("array.push(%s);", jsField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String jsField) {
    if (field.isProto3Optional()) {
      String hasCheck = "this._presentFields[" + field.getArrayPosition() + "]";
      w.block(
          "if (" + hasCheck + ")",
          () -> {
            emitScalarPush(w, field, jsField);
          });
      w.line("else { array.push(null); }");
      return;
    }
    emitScalarPush(w, field, jsField);
  }

  private void emitScalarPush(CodeWriter w, ProtoField field, String jsField) {
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      emitBytesEncode(w, jsField, "array.push(%s);");
    } else if (isInt64Type(field.getProtoType())) {
      w.line("array.push(String(%s));", jsField);
    } else {
      w.line("array.push(%s);", jsField);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String jsField) {
    if (field.isProto3Optional()) {
      String hasCheck = "this._presentFields[" + field.getArrayPosition() + "]";
      w.block(
          "if (" + hasCheck + ")",
          () -> {
            w.line("array.push(%s != null ? %s : 0);", jsField, jsField);
          });
      w.line("else { array.push(null); }");
    } else {
      w.line("array.push(%s != null ? %s : 0);", jsField, jsField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String jsField) {
    w.line("array.push(%s != null ? %s.serialize() : null);", jsField, jsField);
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String jsField) {
    w.block(
        "",
        () -> {
          w.line("const listArr = [];");
          String elemVar = nameResolver.fieldName(field.getName()) + "Item";
          w.block(
              "for (const " + elemVar + " of " + jsField + ")",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.line("listArr.push(%s != null ? %s.serialize() : null);", elemVar, elemVar);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("listArr.push(%s != null ? %s : 0);", elemVar, elemVar);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  emitBytesEncode(w, elemVar, "listArr.push(%s);");
                } else if (isInt64Type(field.getProtoType())) {
                  w.line("listArr.push(String(%s));", elemVar);
                } else {
                  w.line("listArr.push(%s);", elemVar);
                }
              });
          w.line("array.push(listArr);");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String jsField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "",
        () -> {
          if (stringKey) {
            w.line("const mapObj = {};");
            w.block(
                "for (const [key, value] of Object.entries(" + jsField + "))",
                () -> {
                  emitMapValueAssign(w, field, "key", "value");
                });
            w.line("array.push(mapObj);");
          } else {
            w.line("const mapArr = [];");
            w.block(
                "for (const [key, value] of Object.entries(" + jsField + "))",
                () -> {
                  w.line("const pair = [];");
                  w.line("pair.push(Number(key));");
                  emitMapValuePush(w, field, "value", "pair");
                  w.line("mapArr.push(pair);");
                });
            w.line("array.push(mapArr);");
          }
        });
  }

  private void emitMapValueAssign(
      CodeWriter w, ProtoField field, String keyExpr, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("mapObj[%s] = %s != null ? %s.serialize() : null;", keyExpr, valueExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("mapObj[%s] = %s != null ? %s : 0;", keyExpr, valueExpr, valueExpr);
    } else if (isInt64Type(field.getMapValueType())) {
      w.line("mapObj[%s] = String(%s);", keyExpr, valueExpr);
    } else {
      w.line("mapObj[%s] = %s;", keyExpr, valueExpr);
    }
  }

  private void emitMapValuePush(CodeWriter w, ProtoField field, String valueExpr, String arrExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("%s.push(%s != null ? %s.serialize() : null);", arrExpr, valueExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("%s.push(%s != null ? %s : 0);", arrExpr, valueExpr, valueExpr);
    } else if (isInt64Type(field.getMapValueType())) {
      w.line("%s.push(String(%s));", arrExpr, valueExpr);
    } else {
      w.line("%s.push(%s);", arrExpr, valueExpr);
    }
  }

  /**
   * Emit base64 encoding for bytes fields. Uses btoa for browser compatibility, with Buffer
   * fallback for Node.
   */
  private void emitBytesEncode(CodeWriter w, String fieldExpr, String pushTemplate) {
    String encodeExpr =
        String.format(
            "(typeof Buffer !== 'undefined' ? Buffer.from(%s).toString('base64') "
                + ": btoa(String.fromCharCode.apply(null, %s)))",
            fieldExpr, fieldExpr);
    w.line(pushTemplate, encodeExpr);
  }

  /** Check if the given proto type is a 64-bit integer type that needs string encoding. */
  private static boolean isInt64Type(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64;
  }
}
