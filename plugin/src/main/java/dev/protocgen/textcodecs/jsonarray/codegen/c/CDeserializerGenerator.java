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
package dev.protocgen.textcodecs.jsonarray.codegen.c;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the deserialize function body for C code. Reads fields positionally from a cJSON array.
 */
public class CDeserializerGenerator {

  private final CTypeMapper typeMapper;
  private final CNameResolver nameResolver;

  public CDeserializerGenerator(CTypeMapper typeMapper, CNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  /** Generate the deserialize function implementation in the .c file. */
  public void generate(CodeWriter w, ProtoMessage message, String funcPrefix, String typeName) {
    // TypeName* prefix_deserialize(const cJSON* array)
    w.blankLine();
    w.block(
        typeName + "* " + funcPrefix + "_deserialize(const cJSON* array)",
        () -> {
          w.line("if (!array || !cJSON_IsArray(array)) return NULL;");
          w.line("%s* msg = (%s*)calloc(1, sizeof(%s));", typeName, typeName, typeName);
          w.line("if (!msg) return NULL;");
          w.line("int size = cJSON_GetArraySize(array);");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("/* position %d: gap (no field) */", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos, funcPrefix);
          }

          w.line("return msg;");
        });

    // TypeName* prefix_from_json_string(const char* json)
    w.blankLine();
    w.block(
        typeName + "* " + funcPrefix + "_from_json_string(const char* json)",
        () -> {
          w.line("if (!json) return NULL;");
          w.line("cJSON* parsed = cJSON_Parse(json);");
          w.line("if (!parsed) return NULL;");
          w.line("%s* msg = %s_deserialize(parsed);", typeName, funcPrefix);
          w.line("cJSON_Delete(parsed);");
          w.line("return msg;");
        });
  }

  /** Generate deserialize function declarations for the header file. */
  public void generateDeclarations(CodeWriter w, String funcPrefix, String typeName) {
    w.line("%s* %s_deserialize(const cJSON* array);", typeName, funcPrefix);
    w.line("%s* %s_from_json_string(const char* json);", typeName, funcPrefix);
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos, String funcPrefix) {
    String fieldName = nameResolver.fieldName(field.getName());

    w.block(
        "if (size > " + pos + ")",
        () -> {
          w.line("cJSON* item = cJSON_GetArrayItem(array, %d);", pos);

          if (field.isOneofMember()) {
            emitOneofDeserialize(w, field, fieldName, funcPrefix);
            return;
          }

          w.block(
              "if (item && !cJSON_IsNull(item))",
              () -> {
                if (field.isMap()) {
                  emitMapDeserialize(w, field, fieldName, funcPrefix);
                } else if (field.isRepeated()) {
                  emitRepeatedDeserialize(w, field, fieldName, funcPrefix);
                } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  emitMessageDeserialize(w, field, fieldName, funcPrefix);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  emitEnumDeserialize(w, field, fieldName);
                } else {
                  emitScalarDeserialize(w, field, fieldName);
                }

                if (field.isProto3Optional()) {
                  w.line("msg->has_%s = true;", fieldName);
                }
              });
        });
  }

  private void emitOneofDeserialize(
      CodeWriter w, ProtoField field, String fieldName, String funcPrefix) {
    String oneofName = nameResolver.fieldName(field.getOneofName());
    String caseConst =
        funcPrefix.toUpperCase()
            + "_"
            + field.getOneofName().toUpperCase()
            + "_"
            + field.getName().toUpperCase();
    String unionField = "msg->" + oneofName + "." + fieldName;

    w.block(
        "if (item && !cJSON_IsNull(item))",
        () -> {
          w.line("msg->%s_case = %s;", oneofName, caseConst);

          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
            w.line("%s = %s_deserialize(item);", unionField, refPrefix);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line("%s = (int)cJSON_GetNumberValue(item);", unionField);
          } else {
            w.line("%s = %s;", unionField, scalarReadExpr(field.getProtoType(), "item"));
          }
        });
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field, String fieldName) {
    String readExpr = scalarReadExpr(field.getProtoType(), "item");

    switch (field.getProtoType()) {
      case TYPE_STRING:
        w.block(
            "if (cJSON_IsString(item) && item->valuestring)",
            () -> {
              w.line("msg->%s = strdup(item->valuestring);", fieldName);
            });
        break;
      case TYPE_BYTES:
        w.block(
            "if (cJSON_IsString(item) && item->valuestring)",
            () -> {
              w.line(
                  "msg->%s = jsonarray_base64_decode(item->valuestring, &msg->%s_len);",
                  fieldName, fieldName);
            });
        break;
      default:
        w.line("msg->%s = %s;", fieldName, readExpr);
        break;
    }
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String fieldName) {
    w.line("msg->%s = (int)cJSON_GetNumberValue(item);", fieldName);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String fieldName, String funcPrefix) {
    String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
    w.line("msg->%s = %s_deserialize(item);", fieldName, refPrefix);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String fieldName, String funcPrefix) {
    w.block(
        "if (cJSON_IsArray(item))",
        () -> {
          w.line("int count = cJSON_GetArraySize(item);");
          w.line("msg->%s_count = (size_t)count;", fieldName);

          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String refType = nameResolver.resolveTypeReference(field.getTypeReference(), null);
            String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
            w.line("msg->%s = (%s**)calloc(count, sizeof(%s*));", fieldName, refType, refType);
            w.block(
                "for (int i = 0; i < count; i++)",
                () -> {
                  w.line("cJSON* elem = cJSON_GetArrayItem(item, i);");
                  w.line(
                      "msg->%s[i] = (elem && !cJSON_IsNull(elem)) ? %s_deserialize(elem) : NULL;",
                      fieldName, refPrefix);
                });
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            String enumType = nameResolver.resolveTypeReference(field.getTypeReference(), null);
            w.line("msg->%s = (%s*)calloc(count, sizeof(%s));", fieldName, enumType, enumType);
            w.block(
                "for (int i = 0; i < count; i++)",
                () -> {
                  w.line("cJSON* elem = cJSON_GetArrayItem(item, i);");
                  w.line("msg->%s[i] = (%s)(int)cJSON_GetNumberValue(elem);", fieldName, enumType);
                });
          } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
            w.line("msg->%s = (char**)calloc(count, sizeof(char*));", fieldName);
            w.block(
                "for (int i = 0; i < count; i++)",
                () -> {
                  w.line("cJSON* elem = cJSON_GetArrayItem(item, i);");
                  w.line(
                      "msg->%s[i] = (elem && cJSON_IsString(elem) && elem->valuestring) ? strdup(elem->valuestring) : NULL;",
                      fieldName);
                });
          } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
            w.line("msg->%s = (uint8_t**)calloc(count, sizeof(uint8_t*));", fieldName);
            w.line("msg->%s_lengths = (size_t*)calloc(count, sizeof(size_t));", fieldName);
            w.block(
                "for (int i = 0; i < count; i++)",
                () -> {
                  w.line("cJSON* elem = cJSON_GetArrayItem(item, i);");
                  w.block(
                      "if (elem && cJSON_IsString(elem) && elem->valuestring)",
                      () -> {
                        w.line(
                            "msg->%s[i] = jsonarray_base64_decode(elem->valuestring, &msg->%s_lengths[i]);",
                            fieldName, fieldName);
                      });
                });
          } else {
            String cType = typeMapper.scalarType(field.getProtoType());
            w.line("msg->%s = (%s*)calloc(count, sizeof(%s));", fieldName, cType, cType);
            w.block(
                "for (int i = 0; i < count; i++)",
                () -> {
                  w.line("cJSON* elem = cJSON_GetArrayItem(item, i);");
                  w.line(
                      "msg->%s[i] = %s;", fieldName, scalarReadExpr(field.getProtoType(), "elem"));
                });
          }
        });
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String fieldName, String funcPrefix) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    String entryType = typeMapper.qualifiedMapEntryTypeName(funcPrefix, field);

    if (stringKey) {
      // JSON object -> map entries
      w.block(
          "if (cJSON_IsObject(item))",
          () -> {
            w.line("int count = cJSON_GetArraySize(item);");
            w.line("msg->%s_count = (size_t)count;", fieldName);
            w.line("msg->%s = (%s*)calloc(count, sizeof(%s));", fieldName, entryType, entryType);
            w.line("cJSON* child = item->child;");
            w.block(
                "for (int i = 0; child != NULL; i++, child = child->next)",
                () -> {
                  w.line("msg->%s[i].key = strdup(child->string);", fieldName);
                  emitMapValueRead(w, field, fieldName + "[i].value", "child", funcPrefix);
                });
          });
    } else {
      // JSON array of [key, value] pairs
      w.block(
          "if (cJSON_IsArray(item))",
          () -> {
            w.line("int count = cJSON_GetArraySize(item);");
            w.line("msg->%s_count = (size_t)count;", fieldName);
            w.line("msg->%s = (%s*)calloc(count, sizeof(%s));", fieldName, entryType, entryType);
            w.block(
                "for (int i = 0; i < count; i++)",
                () -> {
                  w.line("cJSON* pair = cJSON_GetArrayItem(item, i);");
                  w.line("cJSON* key_item = cJSON_GetArrayItem(pair, 0);");
                  w.line("cJSON* val_item = cJSON_GetArrayItem(pair, 1);");
                  w.line(
                      "msg->%s[i].key = %s;",
                      fieldName, scalarReadExpr(field.getMapKeyType(), "key_item"));
                  emitMapValueRead(w, field, fieldName + "[i].value", "val_item", funcPrefix);
                });
          });
    }
  }

  private void emitMapValueRead(
      CodeWriter w, ProtoField field, String target, String nodeExpr, String funcPrefix) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getMapValueTypeReference());
      w.line(
          "msg->%s = (%s && !cJSON_IsNull(%s)) ? %s_deserialize(%s) : NULL;",
          target, nodeExpr, nodeExpr, refPrefix, nodeExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("msg->%s = (int)cJSON_GetNumberValue(%s);", target, nodeExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line(
          "msg->%s = (%s && cJSON_IsString(%s) && %s->valuestring) ? strdup(%s->valuestring) : NULL;",
          target, nodeExpr, nodeExpr, nodeExpr, nodeExpr);
    } else {
      w.line("msg->%s = %s;", target, scalarReadExpr(field.getMapValueType(), nodeExpr));
    }
  }

  /** Generate deserialize function declarations for the header. */
  private String scalarReadExpr(FieldDescriptorProto.Type type, String nodeExpr) {
    return switch (type) {
      case TYPE_DOUBLE -> "cJSON_GetNumberValue(" + nodeExpr + ")";
      case TYPE_FLOAT -> "(float)cJSON_GetNumberValue(" + nodeExpr + ")";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          "(cJSON_IsString("
              + nodeExpr
              + ") ? strtoll("
              + nodeExpr
              + "->valuestring, NULL, 10) : (int64_t)cJSON_GetNumberValue("
              + nodeExpr
              + "))";
      case TYPE_UINT64, TYPE_FIXED64 ->
          "(cJSON_IsString("
              + nodeExpr
              + ") ? strtoull("
              + nodeExpr
              + "->valuestring, NULL, 10) : (uint64_t)cJSON_GetNumberValue("
              + nodeExpr
              + "))";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 ->
          "(int32_t)cJSON_GetNumberValue(" + nodeExpr + ")";
      case TYPE_UINT32, TYPE_FIXED32 -> "(uint32_t)cJSON_GetNumberValue(" + nodeExpr + ")";
      case TYPE_BOOL -> "cJSON_IsTrue(" + nodeExpr + ")";
      case TYPE_STRING ->
          "(cJSON_IsString("
              + nodeExpr
              + ") && "
              + nodeExpr
              + "->valuestring) ? strdup("
              + nodeExpr
              + "->valuestring) : NULL";
      case TYPE_BYTES -> "NULL /* bytes decoded separately */";
      default -> "NULL";
    };
  }
}
