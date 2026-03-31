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
package dev.protocgen.textcodecs.jsonarray.codegen.swift;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize() method body for Swift structs. Produces an [Any?] array with fields at
 * positions determined by field number. int64/uint64 values are serialized as strings to avoid
 * precision loss in JSON. NaN/Infinity are serialized as NSNull().
 */
public class SwiftSerializerGenerator {

  private final SwiftTypeMapper typeMapper;
  private final SwiftNameResolver nameResolver;

  public SwiftSerializerGenerator(SwiftTypeMapper typeMapper, SwiftNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String structName) {
    // serialize method: returns [Any?]
    w.blankLine();
    w.block(
        "public func serialize() -> [Any?]",
        () -> {
          int maxPos = message.getMaxFieldNumber();
          w.line("var arr: [Any?] = Array(repeating: nil, count: %d)", maxPos);

          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("// position %d: gap (no field number %d)", pos, pos + 1);
            } else {
              emitFieldSerialize(w, field, pos);
            }
          }

          w.line("return arr");
        });

    // toJsonString convenience method
    w.blankLine();
    w.block(
        "public func toJsonString() throws -> String",
        () -> {
          w.line("let arr = serialize()");
          w.line(
              "let data = try JSONSerialization.data(withJSONObject: arr.map { $0 ?? NSNull() "
                  + "}, options: [])");
          w.line("return String(data: data, encoding: .utf8)!");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field, int pos) {
    String swiftField = nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      // For oneof members: write the value if this is the active case, else nil
      String caseField = SwiftNameResolver.snakeToCamel(field.getOneofName()) + "Case";
      int fieldNum = field.getFieldNumber();
      w.block(
          "if " + caseField + " == " + fieldNum,
          () -> {
            emitValueForField(w, field, swiftField, pos);
          });
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, swiftField, pos);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, swiftField, pos);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, swiftField, pos);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, swiftField, pos);
    } else {
      emitScalarSerialize(w, field, swiftField, pos);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String swiftField, int pos) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "if let msg = " + swiftField,
          () -> {
            w.line("arr[%d] = msg.serialize()", pos);
          });
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("arr[%d] = %s.rawValue", pos, swiftField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("arr[%d] = %s.base64EncodedString()", pos, swiftField);
    } else if (isInt64Type(field.getProtoType())) {
      w.line("arr[%d] = String(%s)", pos, swiftField);
    } else if (isFloatType(field.getProtoType())) {
      emitFloatSerialize(w, swiftField, pos);
    } else {
      w.line("arr[%d] = %s", pos, swiftField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String swiftField, int pos) {
    if (field.isProto3Optional()) {
      // Optional field: check for nil
      w.block(
          "if let val = " + swiftField,
          () -> {
            if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
              w.line("arr[%d] = val.base64EncodedString()", pos);
            } else if (isInt64Type(field.getProtoType())) {
              w.line("arr[%d] = String(val)", pos);
            } else if (isFloatType(field.getProtoType())) {
              emitFloatSerializeFromVal(w, "val", pos);
            } else {
              w.line("arr[%d] = val", pos);
            }
          });
      return;
    }
    switch (field.getProtoType()) {
      case TYPE_BYTES:
        w.line("arr[%d] = %s.base64EncodedString()", pos, swiftField);
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
      case TYPE_UINT64:
      case TYPE_FIXED64:
        w.line("arr[%d] = String(%s)", pos, swiftField);
        break;
      case TYPE_DOUBLE:
      case TYPE_FLOAT:
        emitFloatSerialize(w, swiftField, pos);
        break;
      default:
        w.line("arr[%d] = %s", pos, swiftField);
        break;
    }
  }

  private void emitFloatSerialize(CodeWriter w, String fieldExpr, int pos) {
    w.block(
        "if " + fieldExpr + ".isNaN || " + fieldExpr + ".isInfinite",
        () -> {
          w.line("arr[%d] = NSNull()", pos);
        });
    w.block(
        "else",
        () -> {
          w.line("arr[%d] = %s", pos, fieldExpr);
        });
  }

  private void emitFloatSerializeFromVal(CodeWriter w, String valExpr, int pos) {
    w.block(
        "if " + valExpr + ".isNaN || " + valExpr + ".isInfinite",
        () -> {
          w.line("arr[%d] = NSNull()", pos);
        });
    w.block(
        "else",
        () -> {
          w.line("arr[%d] = %s", pos, valExpr);
        });
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String swiftField, int pos) {
    if (field.isProto3Optional()) {
      w.block(
          "if let val = " + swiftField,
          () -> {
            w.line("arr[%d] = val.rawValue", pos);
          });
    } else {
      w.line("arr[%d] = %s.rawValue", pos, swiftField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String swiftField, int pos) {
    w.block(
        "if let msg = " + swiftField,
        () -> {
          w.line("arr[%d] = msg.serialize()", pos);
        });
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String swiftField, int pos) {
    w.line("var listArr: [Any] = []");
    w.block(
        "for item in " + swiftField,
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            w.line("listArr.append(item.serialize())");
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line("listArr.append(item.rawValue)");
          } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
            w.line("listArr.append(item.base64EncodedString())");
          } else if (isInt64Type(field.getProtoType())) {
            w.line("listArr.append(String(item))");
          } else if (isFloatType(field.getProtoType())) {
            w.block(
                "if item.isNaN || item.isInfinite",
                () -> {
                  w.line("listArr.append(NSNull())");
                });
            w.block(
                "else",
                () -> {
                  w.line("listArr.append(item)");
                });
          } else {
            w.line("listArr.append(item)");
          }
        });
    w.line("arr[%d] = listArr", pos);
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String swiftField, int pos) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    if (stringKey) {
      // String keys: serialize as JSON object (dictionary)
      w.line("var mapObj: [String: Any] = [:]");
      w.block(
          "for (key, val) in " + swiftField,
          () -> {
            emitMapValueAssign(w, field, "key", "val", "mapObj");
          });
      w.line("arr[%d] = mapObj", pos);
    } else {
      // Non-string keys: serialize as array of [key, value] pairs
      w.line("var pairs: [[Any]] = []");
      w.block(
          "for (key, val) in " + swiftField,
          () -> {
            w.line("pairs.append([key, %s] as [Any])", mapValueExpr(field, "val"));
          });
      w.line("arr[%d] = pairs", pos);
    }
  }

  private void emitMapValueAssign(
      CodeWriter w, ProtoField field, String keyExpr, String valueExpr, String mapVar) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("%s[%s] = %s.serialize()", mapVar, keyExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("%s[%s] = %s.rawValue", mapVar, keyExpr, valueExpr);
    } else {
      w.line("%s[%s] = %s", mapVar, keyExpr, valueExpr);
    }
  }

  private String mapValueExpr(ProtoField field, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      return valueExpr + ".serialize()";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return valueExpr + ".rawValue";
    }
    return valueExpr;
  }

  /** Check if this proto type is a 64-bit integer type. */
  private boolean isInt64Type(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64
        || type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64;
  }

  /** Check if this proto type is a floating-point type. */
  private boolean isFloatType(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_FLOAT
        || type == FieldDescriptorProto.Type.TYPE_DOUBLE;
  }
}
