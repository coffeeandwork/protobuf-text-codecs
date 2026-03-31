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
package dev.protocgen.textcodecs.jsonarray.codegen.dart;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize() method body for Dart classes. Produces a Dart List with fields at
 * positions determined by field number.
 */
public class DartSerializerGenerator {

  private final DartTypeMapper typeMapper;
  private final DartNameResolver nameResolver;

  public DartSerializerGenerator(DartTypeMapper typeMapper, DartNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message) {
    // serialize() -> returns a List
    w.blankLine();
    w.line("/// Serialize this message to a positional JSON array.");
    w.block(
        "List<dynamic> serialize()",
        () -> {
          w.line("final array = <dynamic>[];");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("array.add(null); // gap (no field number %d)", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("return array;");
        });

    // toJsonString() convenience
    w.blankLine();
    w.line("/// Serialize this message to a JSON string.");
    w.block(
        "String toJsonString()",
        () -> {
          w.line("return jsonEncode(serialize());");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String dartField = nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseProp = "_" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.block(
          "if (" + caseProp + " == " + field.getFieldNumber() + ")",
          () -> {
            emitValueForField(w, field, dartField);
          });
      w.line("else { array.add(null); }");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, dartField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, dartField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, dartField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, dartField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, dartField);
    } else {
      emitScalarSerialize(w, field, dartField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String dartField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("array.add(%s != null ? %s!.serialize() : null);", dartField, dartField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("array.add(%s ?? 0);", dartField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      emitBytesEncode(w, dartField, "array.add(%s);");
    } else if (isInt64Type(field.getProtoType())) {
      w.line("array.add(%s);", dartField);
    } else if (isFloatType(field.getProtoType())) {
      emitFloatNanCheck(w, dartField);
    } else {
      w.line("array.add(%s);", dartField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String dartField) {
    if (field.isProto3Optional()) {
      String hasCheck = "_presentFields[" + field.getArrayPosition() + "]";
      w.block(
          "if (" + hasCheck + " == true)",
          () -> {
            emitScalarPush(w, field, dartField);
          });
      w.line("else { array.add(null); }");
      return;
    }
    emitScalarPush(w, field, dartField);
  }

  private void emitScalarPush(CodeWriter w, ProtoField field, String dartField) {
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      emitBytesEncode(w, dartField, "array.add(%s);");
    } else if (isInt64Type(field.getProtoType())) {
      w.line("array.add(%s);", dartField);
    } else if (isFloatType(field.getProtoType())) {
      emitFloatNanCheck(w, dartField);
    } else {
      w.line("array.add(%s);", dartField);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String dartField) {
    if (field.isProto3Optional()) {
      String hasCheck = "_presentFields[" + field.getArrayPosition() + "]";
      w.block(
          "if (" + hasCheck + " == true)",
          () -> {
            w.line("array.add(%s ?? 0);", dartField);
          });
      w.line("else { array.add(null); }");
    } else {
      w.line("array.add(%s ?? 0);", dartField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String dartField) {
    w.line("array.add(%s != null ? %s!.serialize() : null);", dartField, dartField);
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String dartField) {
    w.block(
        "",
        () -> {
          w.line("final listArr = <dynamic>[];");
          String elemVar = nameResolver.fieldName(field.getName()) + "Item";
          w.block(
              "for (final " + elemVar + " in " + dartField + ")",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.line("listArr.add(%s != null ? %s.serialize() : null);", elemVar, elemVar);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("listArr.add(%s ?? 0);", elemVar);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  emitBytesEncode(w, elemVar, "listArr.add(%s);");
                } else if (isInt64Type(field.getProtoType())) {
                  w.line("listArr.add(%s);", elemVar);
                } else if (isFloatType(field.getProtoType())) {
                  w.line(
                      "listArr.add(%s.isNaN || %s.isInfinite ? null : %s);",
                      elemVar, elemVar, elemVar);
                } else {
                  w.line("listArr.add(%s);", elemVar);
                }
              });
          w.line("array.add(listArr);");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String dartField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "",
        () -> {
          if (stringKey) {
            w.line("final mapObj = <String, dynamic>{};");
            w.block(
                "for (final entry in " + dartField + ".entries)",
                () -> {
                  emitMapValueAssign(w, field, "entry.key", "entry.value");
                });
            w.line("array.add(mapObj);");
          } else {
            w.line("final mapArr = <dynamic>[];");
            w.block(
                "for (final entry in " + dartField + ".entries)",
                () -> {
                  w.line("final pair = <dynamic>[];");
                  w.line("pair.add(entry.key);");
                  emitMapValuePush(w, field, "entry.value", "pair");
                  w.line("mapArr.add(pair);");
                });
            w.line("array.add(mapArr);");
          }
        });
  }

  private void emitMapValueAssign(
      CodeWriter w, ProtoField field, String keyExpr, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("mapObj[%s] = %s != null ? %s.serialize() : null;", keyExpr, valueExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("mapObj[%s] = %s ?? 0;", keyExpr, valueExpr);
    } else if (isInt64Type(field.getMapValueType())) {
      w.line("mapObj[%s] = %s;", keyExpr, valueExpr);
    } else {
      w.line("mapObj[%s] = %s;", keyExpr, valueExpr);
    }
  }

  private void emitMapValuePush(CodeWriter w, ProtoField field, String valueExpr, String arrExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("%s.add(%s != null ? %s.serialize() : null);", arrExpr, valueExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("%s.add(%s ?? 0);", arrExpr, valueExpr);
    } else if (isInt64Type(field.getMapValueType())) {
      w.line("%s.add(%s);", arrExpr, valueExpr);
    } else {
      w.line("%s.add(%s);", arrExpr, valueExpr);
    }
  }

  /** Emit base64 encoding for bytes fields. */
  private void emitBytesEncode(CodeWriter w, String fieldExpr, String pushTemplate) {
    String encodeExpr = "base64Encode(" + fieldExpr + ")";
    w.line(pushTemplate, encodeExpr);
  }

  /** Emit NaN/Infinity check for float/double fields. */
  private void emitFloatNanCheck(CodeWriter w, String dartField) {
    w.line("array.add(%s.isNaN || %s.isInfinite ? null : %s);", dartField, dartField, dartField);
  }

  /** Check if the given proto type is a 64-bit integer type that needs string encoding. */
  static boolean isInt64Type(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64;
  }

  /** Check if the given proto type is a float/double type that needs NaN/Infinity handling. */
  static boolean isFloatType(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_FLOAT
        || type == FieldDescriptorProto.Type.TYPE_DOUBLE;
  }
}
