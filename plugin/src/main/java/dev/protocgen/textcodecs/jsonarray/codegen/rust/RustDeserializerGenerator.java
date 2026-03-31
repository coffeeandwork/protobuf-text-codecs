/*
 * Copyright 2026 protobuf-text-codecs contributors
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
 * Generates the static deserialize() method for Rust structs. Reads fields positionally from a
 * serde_json::Value array.
 */
public class RustDeserializerGenerator {

  private final RustTypeMapper typeMapper;
  private final RustNameResolver nameResolver;

  public RustDeserializerGenerator(RustTypeMapper typeMapper, RustNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "pub fn deserialize(arr: &Value) -> Result<Self, String>",
        () -> {
          w.line("let arr = arr.as_array().ok_or_else(|| \"expected JSON array\".to_string())?;");
          w.line("let size = arr.len();");
          w.line("let mut obj = %s::default();", structName);

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("// position %d: gap (no field)", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos);
          }

          w.line("Ok(obj)");
        });

    // Convenience: deserialize from JSON string
    w.blankLine();
    w.block(
        "pub fn from_json_string(json: &str) -> Result<Self, String>",
        () -> {
          w.line("let value: Value = serde_json::from_str(json).map_err(|e| e.to_string())?;");
          w.line("Self::deserialize(&value)");
        });

    // Convenience: deserialize from bytes
    w.blankLine();
    w.block(
        "pub fn from_json_bytes(bytes: &[u8]) -> Result<Self, String>",
        () -> {
          w.line("let json = std::str::from_utf8(bytes).map_err(|e| e.to_string())?;");
          w.line("Self::from_json_string(json)");
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String rustField = "obj." + nameResolver.fieldName(field.getName());

    w.block(
        "if size > " + pos + " && !arr[" + pos + "].is_null()",
        () -> {
          String nodeExpr = "arr[" + pos + "]";

          if (field.isMap()) {
            emitMapDeserialize(w, field, rustField, nodeExpr);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, rustField, nodeExpr);
          } else if (field.isWellKnownType()) {
            emitWellKnownDeserialize(w, field, rustField, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
            emitMessageDeserialize(w, field, rustField, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, rustField, nodeExpr);
          } else {
            emitScalarDeserialize(w, field, rustField, nodeExpr);
          }

          if (field.isOneofMember()) {
            String caseField = "obj." + nameResolver.fieldName(field.getOneofName()) + "_case";
            w.line("%s = %d;", caseField, field.getFieldNumber());
          }
        });
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String rustField, String nodeExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), nodeExpr);
    if (field.isProto3Optional()) {
      w.line("%s = Some(%s);", rustField, readExpr);
    } else {
      w.line("%s = %s;", rustField, readExpr);
    }
  }

  private void emitEnumDeserialize(
      CodeWriter w, ProtoField field, String rustField, String nodeExpr) {
    String enumType = simpleTypeName(field.getTypeReference());
    String readExpr = enumType + "::from(" + nodeExpr + ".as_i64().unwrap_or(0) as i32)";
    if (field.isProto3Optional()) {
      w.line("%s = Some(%s);", rustField, readExpr);
    } else {
      w.line("%s = %s;", rustField, readExpr);
    }
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String rustField, String nodeExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s = Some(%s::deserialize(&%s)?);", rustField, msgType, nodeExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String rustField, String nodeExpr) {
    w.block(
        "if let Some(list) = " + nodeExpr + ".as_array()",
        () -> {
          w.line("let mut result = Vec::with_capacity(list.len());");
          w.block(
              "for elem in list",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  String msgType = simpleTypeName(field.getTypeReference());
                  w.block(
                      "if elem.is_null()",
                      () -> {
                        w.line("result.push(%s::default());", msgType);
                      });
                  w.block(
                      "else",
                      () -> {
                        w.line("result.push(%s::deserialize(elem)?);", msgType);
                      });
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  String enumType = simpleTypeName(field.getTypeReference());
                  w.line("result.push(%s::from(elem.as_i64().unwrap_or(0) as i32));", enumType);
                } else {
                  String readExpr = scalarReadExpr(field.getProtoType(), "elem");
                  w.line("result.push(%s);", readExpr);
                }
              });
          w.line("%s = result;", rustField);
        });
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String rustField, String nodeExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.line("let mut map = std::collections::HashMap::new();");

    if (stringKey) {
      w.block(
          "if let Some(obj) = " + nodeExpr + ".as_object()",
          () -> {
            w.block(
                "for (key, val) in obj",
                () -> {
                  String valueExpr = mapValueReadExpr(field, "val");
                  w.line("map.insert(key.clone(), %s);", valueExpr);
                });
          });
    } else {
      w.block(
          "if let Some(pairs) = " + nodeExpr + ".as_array()",
          () -> {
            w.block(
                "for pair in pairs",
                () -> {
                  w.block(
                      "if let Some(pair_arr) = pair.as_array()",
                      () -> {
                        w.block(
                            "if pair_arr.len() >= 2",
                            () -> {
                              String keyRead = scalarReadExpr(field.getMapKeyType(), "pair_arr[0]");
                              String valueRead = mapValueReadExpr(field, "&pair_arr[1]");
                              w.line("map.insert(%s, %s);", keyRead, valueRead);
                            });
                      });
                });
          });
    }
    w.line("%s = map;", rustField);
  }

  private void emitWellKnownDeserialize(
      CodeWriter w, ProtoField field, String rustField, String nodeExpr) {
    // Well-known types are treated as regular message fields for now
    emitMessageDeserialize(w, field, rustField, nodeExpr);
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String nodeExpr) {
    return switch (type) {
      case TYPE_DOUBLE -> nodeExpr + ".as_f64().unwrap_or(0.0)";
      case TYPE_FLOAT -> nodeExpr + ".as_f64().unwrap_or(0.0) as f32";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          nodeExpr
              + ".as_str().and_then(|s| s.parse::<i64>().ok()).or_else(|| "
              + nodeExpr
              + ".as_i64()).unwrap_or(0)";
      case TYPE_UINT64, TYPE_FIXED64 ->
          nodeExpr
              + ".as_str().and_then(|s| s.parse::<u64>().ok()).or_else(|| "
              + nodeExpr
              + ".as_u64()).unwrap_or(0)";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> nodeExpr + ".as_i64().unwrap_or(0) as i32";
      case TYPE_UINT32, TYPE_FIXED32 -> nodeExpr + ".as_u64().unwrap_or(0) as u32";
      case TYPE_BOOL -> nodeExpr + ".as_bool().unwrap_or(false)";
      case TYPE_STRING -> nodeExpr + ".as_str().unwrap_or(\"\").to_string()";
      case TYPE_BYTES ->
          "general_purpose::STANDARD.decode("
              + nodeExpr
              + ".as_str().unwrap_or(\"\")).unwrap_or_default()";
      default -> nodeExpr + ".as_str().unwrap_or(\"\").to_string()";
    };
  }

  private String mapValueReadExpr(ProtoField field, String nodeExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      return msgType + "::deserialize(" + nodeExpr + ").unwrap_or_default()";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      return enumType + "::from(" + nodeExpr + ".as_i64().unwrap_or(0) as i32)";
    }
    return scalarReadExpr(field.getMapValueType(), nodeExpr);
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Value";
  }
}
