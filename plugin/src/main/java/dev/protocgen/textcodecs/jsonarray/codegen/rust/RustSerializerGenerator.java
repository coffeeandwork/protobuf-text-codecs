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
package dev.protocgen.textcodecs.jsonarray.codegen.rust;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize() method body for Rust structs. Produces a serde_json::Value::Array with
 * fields at positions determined by field number.
 */
public class RustSerializerGenerator {

  private final RustTypeMapper typeMapper;
  private final RustNameResolver nameResolver;

  public RustSerializerGenerator(RustTypeMapper typeMapper, RustNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message) {
    w.blankLine();
    w.block(
        "pub fn serialize(&self) -> Value",
        () -> {
          int maxPos = message.getMaxFieldNumber();
          w.line("let mut arr: Vec<Value> = Vec::with_capacity(%d);", maxPos);
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("arr.push(Value::Null); // gap (no field number %d)", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("Value::Array(arr)");
        });

    // Convenience: serialize to JSON string
    w.blankLine();
    w.block(
        "pub fn to_json_string(&self) -> Result<String, serde_json::Error>",
        () -> {
          w.line("serde_json::to_string(&self.serialize())");
        });

    // Convenience: serialize to bytes
    w.blankLine();
    w.block(
        "pub fn to_json_bytes(&self) -> Result<Vec<u8>, serde_json::Error>",
        () -> {
          w.line("Ok(self.to_json_string()?.into_bytes())");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String rustField = "self." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseField = "self." + nameResolver.fieldName(field.getOneofName()) + "_case";
      String enumConst = field.getFieldNumber() + "";
      w.block(
          "if " + caseField + " == " + enumConst,
          () -> {
            emitValueForField(w, field, rustField);
          });
      w.line("else { arr.push(Value::Null); }");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, rustField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, rustField);
    } else if (field.isWellKnownType()) {
      emitWellKnownSerialize(w, field, rustField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, rustField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, rustField);
    } else {
      emitScalarSerialize(w, field, rustField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String rustField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "match &" + rustField,
          () -> {
            w.line("Some(v) => arr.push(v.serialize()),");
            w.line("None => arr.push(Value::Null),");
          });
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("arr.push(json!(%s as i32));", rustField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("arr.push(json!(general_purpose::STANDARD.encode(&%s)));", rustField);
    } else if (isInt64Type(field.getProtoType())) {
      w.line("arr.push(json!(%s.to_string()));", rustField);
    } else {
      emitScalarPush(w, field, rustField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String rustField) {
    if (field.isProto3Optional()) {
      w.block(
          "match &" + rustField,
          () -> {
            if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
              w.line("Some(v) => arr.push(json!(general_purpose::STANDARD.encode(v))),");
            } else if (isInt64Type(field.getProtoType())) {
              w.line("Some(v) => arr.push(json!(v.to_string())),");
            } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT
                || field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
              w.line(
                  "Some(v) => arr.push(if v.is_nan() || v.is_infinite() { json!(null) } else { json!(v) }),");
            } else {
              w.line("Some(v) => arr.push(json!(v)),");
            }
            w.line("None => arr.push(Value::Null),");
          });
      return;
    }
    switch (field.getProtoType()) {
      case TYPE_BYTES:
        w.line("arr.push(json!(general_purpose::STANDARD.encode(&%s)));", rustField);
        break;
      default:
        emitScalarPush(w, field, rustField);
        break;
    }
  }

  private void emitScalarPush(CodeWriter w, ProtoField field, String rustField) {
    switch (field.getProtoType()) {
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
      case TYPE_UINT64:
      case TYPE_FIXED64:
        // Serialize 64-bit integers as strings to avoid JSON float64 precision loss
        w.line("arr.push(json!(%s.to_string()));", rustField);
        break;
      case TYPE_FLOAT:
      case TYPE_DOUBLE:
        w.line(
            "arr.push(if %s.is_nan() || %s.is_infinite() { json!(null) } else { json!(%s) });",
            rustField, rustField, rustField);
        break;
      case TYPE_STRING:
        w.line("arr.push(json!(%s));", rustField);
        break;
      case TYPE_BOOL:
        w.line("arr.push(json!(%s));", rustField);
        break;
      default:
        w.line("arr.push(json!(%s));", rustField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String rustField) {
    if (field.isProto3Optional()) {
      w.block(
          "match &" + rustField,
          () -> {
            w.line("Some(v) => arr.push(json!(*v as i32)),");
            w.line("None => arr.push(Value::Null),");
          });
    } else {
      w.line("arr.push(json!(%s as i32));", rustField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String rustField) {
    w.block(
        "match &" + rustField,
        () -> {
          w.line("Some(v) => arr.push(v.serialize()),");
          w.line("None => arr.push(Value::Null),");
        });
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String rustField) {
    w.block(
        "",
        () -> {
          w.line("let mut list_arr: Vec<Value> = Vec::with_capacity(%s.len());", rustField);
          String elemVar = nameResolver.fieldName(field.getName()) + "_item";
          w.block(
              "for " + elemVar + " in &" + rustField,
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.line("list_arr.push(%s.serialize());", elemVar);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("list_arr.push(json!(*%s as i32));", elemVar);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.line("list_arr.push(json!(general_purpose::STANDARD.encode(%s)));", elemVar);
                } else if (isInt64Type(field.getProtoType())) {
                  w.line("list_arr.push(json!(%s.to_string()));", elemVar);
                } else {
                  w.line("list_arr.push(json!(%s));", elemVar);
                }
              });
          w.line("arr.push(Value::Array(list_arr));");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String rustField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "",
        () -> {
          if (stringKey) {
            w.line("let mut map_obj = serde_json::Map::new();");
            String entryVar = nameResolver.fieldName(field.getName()) + "_entry";
            w.block(
                "for (k, v) in &" + rustField,
                () -> {
                  emitMapValueInsert(w, field, "k.clone()", "v", true);
                });
            w.line("arr.push(Value::Object(map_obj));");
          } else {
            w.line("let mut map_arr: Vec<Value> = Vec::with_capacity(%s.len());", rustField);
            w.block(
                "for (k, v) in &" + rustField,
                () -> {
                  w.line("let mut pair: Vec<Value> = Vec::with_capacity(2);");
                  emitMapKeyAdd(w, field, "k");
                  emitMapValueAdd(w, field, "v");
                  w.line("map_arr.push(Value::Array(pair));");
                });
            w.line("arr.push(Value::Array(map_arr));");
          }
        });
  }

  private void emitMapValueInsert(
      CodeWriter w, ProtoField field, String keyExpr, String valueExpr, boolean isObjectMap) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("map_obj.insert(%s, %s.serialize());", keyExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("map_obj.insert(%s, json!(*%s as i32));", keyExpr, valueExpr);
    } else {
      w.line("map_obj.insert(%s, json!(%s));", keyExpr, valueExpr);
    }
  }

  private void emitMapKeyAdd(CodeWriter w, ProtoField field, String keyExpr) {
    w.line("pair.push(json!(%s));", keyExpr);
  }

  private void emitMapValueAdd(CodeWriter w, ProtoField field, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("pair.push(%s.serialize());", valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("pair.push(json!(*%s as i32));", valueExpr);
    } else {
      w.line("pair.push(json!(%s));", valueExpr);
    }
  }

  private void emitWellKnownSerialize(CodeWriter w, ProtoField field, String rustField) {
    // Well-known types are treated as regular message fields for now
    emitMessageSerialize(w, field, rustField);
  }

  /** Check if this proto type is a 64-bit integer type. */
  private boolean isInt64Type(FieldDescriptorProto.Type type) {
    return ProtoTypeUtil.isInt64Type(type);
  }
}
