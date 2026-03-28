package dev.protocgen.textcodecs.jsonarray.codegen.c;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize function body for C code. Produces a cJSON array with fields at positions
 * determined by field number.
 */
public class CSerializerGenerator {

  private final CTypeMapper typeMapper;
  private final CNameResolver nameResolver;

  public CSerializerGenerator(CTypeMapper typeMapper, CNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  /** Generate the serialize function implementation in the .c file. */
  public void generate(CodeWriter w, ProtoMessage message, String funcPrefix, String typeName) {
    // cJSON* prefix_serialize(const TypeName* msg)
    w.blankLine();
    w.block(
        "cJSON* " + funcPrefix + "_serialize(const " + typeName + "* msg)",
        () -> {
          w.line("if (!msg) return cJSON_CreateNull();");
          w.line("cJSON* array = cJSON_CreateArray();");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line(
                  "cJSON_AddItemToArray(array, cJSON_CreateNull()); /* gap (no field number %d) */",
                  pos + 1);
            } else {
              emitFieldSerialize(w, field, funcPrefix);
            }
          }

          w.line("return array;");
        });

    // char* prefix_to_json_string(const TypeName* msg)
    w.blankLine();
    w.block(
        "char* " + funcPrefix + "_to_json_string(const " + typeName + "* msg)",
        () -> {
          w.line("cJSON* json = %s_serialize(msg);", funcPrefix);
          w.line("char* str = cJSON_PrintUnformatted(json);");
          w.line("cJSON_Delete(json);");
          w.line("return str;");
        });
  }

  /** Generate serialize function declarations for the header file. */
  public void generateDeclarations(CodeWriter w, String funcPrefix, String typeName) {
    w.line("cJSON* %s_serialize(const %s* msg);", funcPrefix, typeName);
    w.line("char* %s_to_json_string(const %s* msg);", funcPrefix, typeName);
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field, String funcPrefix) {
    String accessor = "msg->" + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseField = "msg->" + nameResolver.fieldName(field.getOneofName()) + "_case";
      String caseConst =
          funcPrefix.toUpperCase()
              + "_"
              + field.getOneofName().toUpperCase()
              + "_"
              + field.getName().toUpperCase();
      // Access via the union
      String unionField =
          "msg->"
              + nameResolver.fieldName(field.getOneofName())
              + "."
              + nameResolver.fieldName(field.getName());
      w.block(
          "if (" + caseField + " == " + caseConst + ")",
          () -> {
            emitValueAdd(w, field, unionField, funcPrefix);
          });
      w.block(
          "else",
          () -> {
            w.line("cJSON_AddItemToArray(array, cJSON_CreateNull());");
          });
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, accessor, funcPrefix);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, accessor, funcPrefix);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, accessor, funcPrefix);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, accessor);
    } else {
      emitScalarSerialize(w, field, accessor);
    }
  }

  private void emitValueAdd(CodeWriter w, ProtoField field, String accessor, String funcPrefix) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
      w.block(
          "if (" + accessor + ")",
          () -> {
            w.line("cJSON_AddItemToArray(array, %s_serialize(%s));", refPrefix, accessor);
          });
      w.block(
          "else",
          () -> {
            w.line("cJSON_AddItemToArray(array, cJSON_CreateNull());");
          });
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("cJSON_AddItemToArray(array, cJSON_CreateNumber((double)%s));", accessor);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("char* __b64 = jsonarray_base64_encode(%s, %s_len);", accessor, accessor);
      w.line("cJSON_AddItemToArray(array, cJSON_CreateString(__b64));");
      w.line("free(__b64);");
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line(
          "cJSON_AddItemToArray(array, %s ? cJSON_CreateString(%s) : cJSON_CreateNull());",
          accessor, accessor);
    } else {
      emitScalarAdd(w, field, accessor);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String accessor) {
    if (field.isProto3Optional()) {
      String hasField = "msg->has_" + nameResolver.fieldName(field.getName());
      w.block(
          "if (" + hasField + ")",
          () -> {
            emitScalarAdd(w, field, accessor);
          });
      w.block(
          "else",
          () -> {
            w.line("cJSON_AddItemToArray(array, cJSON_CreateNull());");
          });
      return;
    }

    switch (field.getProtoType()) {
      case TYPE_BYTES:
        String lenField = accessor + "_len";
        w.block(
            "if (" + accessor + ")",
            () -> {
              w.line("char* b64 = jsonarray_base64_encode(%s, %s);", accessor, lenField);
              w.line("cJSON_AddItemToArray(array, cJSON_CreateString(b64));");
              w.line("free(b64);");
            });
        w.block(
            "else",
            () -> {
              w.line("cJSON_AddItemToArray(array, cJSON_CreateString(\"\"));");
            });
        break;
      case TYPE_STRING:
        w.line(
            "cJSON_AddItemToArray(array, %s ? cJSON_CreateString(%s) : cJSON_CreateString(\"\"));",
            accessor, accessor);
        break;
      default:
        emitScalarAdd(w, field, accessor);
        break;
    }
  }

  private void emitScalarAdd(CodeWriter w, ProtoField field, String accessor) {
    switch (field.getProtoType()) {
      case TYPE_BOOL:
        w.line("cJSON_AddItemToArray(array, cJSON_CreateBool(%s));", accessor);
        break;
      case TYPE_DOUBLE:
      case TYPE_FLOAT:
        w.line("cJSON_AddItemToArray(array, cJSON_CreateNumber((double)%s));", accessor);
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
        w.line(
            "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%lld\", (long long)%s); cJSON_AddItemToArray(array, cJSON_CreateString(__buf)); }",
            accessor);
        break;
      case TYPE_UINT64:
      case TYPE_FIXED64:
        w.line(
            "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%llu\", (unsigned long long)%s); cJSON_AddItemToArray(array, cJSON_CreateString(__buf)); }",
            accessor);
        break;
      default:
        w.line("cJSON_AddItemToArray(array, cJSON_CreateNumber((double)%s));", accessor);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String accessor) {
    if (field.isProto3Optional()) {
      String hasField = "msg->has_" + nameResolver.fieldName(field.getName());
      w.block(
          "if (" + hasField + ")",
          () -> {
            w.line("cJSON_AddItemToArray(array, cJSON_CreateNumber((double)%s));", accessor);
          });
      w.block(
          "else",
          () -> {
            w.line("cJSON_AddItemToArray(array, cJSON_CreateNull());");
          });
    } else {
      w.line("cJSON_AddItemToArray(array, cJSON_CreateNumber((double)%s));", accessor);
    }
  }

  private void emitMessageSerialize(
      CodeWriter w, ProtoField field, String accessor, String funcPrefix) {
    String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
    w.block(
        "if (" + accessor + ")",
        () -> {
          w.line("cJSON_AddItemToArray(array, %s_serialize(%s));", refPrefix, accessor);
        });
    w.block(
        "else",
        () -> {
          w.line("cJSON_AddItemToArray(array, cJSON_CreateNull());");
        });
  }

  private void emitRepeatedSerialize(
      CodeWriter w, ProtoField field, String accessor, String funcPrefix) {
    String countField = "msg->" + nameResolver.fieldName(field.getName()) + "_count";
    w.block(
        "",
        () -> {
          w.line("cJSON* list_node = cJSON_CreateArray();");
          w.block(
              "for (size_t i = 0; i < " + countField + "; i++)",
              () -> {
                String elemExpr = accessor + "[i]";
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  String refPrefix =
                      nameResolver.resolveTypeFunctionPrefix(field.getTypeReference());
                  w.line(
                      "cJSON_AddItemToArray(list_node, %s ? %s_serialize(%s) : cJSON_CreateNull());",
                      elemExpr, refPrefix, elemExpr);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line(
                      "cJSON_AddItemToArray(list_node, cJSON_CreateNumber((double)%s));", elemExpr);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  // bytes repeated: each element is a uint8_t* with a corresponding length
                  // For simplicity, we use a parallel lengths array
                  String lenArray = "msg->" + nameResolver.fieldName(field.getName()) + "_lengths";
                  w.line("char* b64 = jsonarray_base64_encode(%s, %s[i]);", elemExpr, lenArray);
                  w.line("cJSON_AddItemToArray(list_node, cJSON_CreateString(b64));");
                  w.line("free(b64);");
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
                  w.line(
                      "cJSON_AddItemToArray(list_node, %s ? cJSON_CreateString(%s) : cJSON_CreateNull());",
                      elemExpr, elemExpr);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BOOL) {
                  w.line("cJSON_AddItemToArray(list_node, cJSON_CreateBool(%s));", elemExpr);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_INT64
                    || field.getProtoType() == FieldDescriptorProto.Type.TYPE_SINT64
                    || field.getProtoType() == FieldDescriptorProto.Type.TYPE_SFIXED64) {
                  w.line(
                      "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%lld\", (long long)%s); cJSON_AddItemToArray(list_node, cJSON_CreateString(__buf)); }",
                      elemExpr);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_UINT64
                    || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FIXED64) {
                  w.line(
                      "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%llu\", (unsigned long long)%s); cJSON_AddItemToArray(list_node, cJSON_CreateString(__buf)); }",
                      elemExpr);
                } else {
                  w.line(
                      "cJSON_AddItemToArray(list_node, cJSON_CreateNumber((double)%s));", elemExpr);
                }
              });
          w.line("cJSON_AddItemToArray(array, list_node);");
        });
  }

  private void emitMapSerialize(
      CodeWriter w, ProtoField field, String accessor, String funcPrefix) {
    String countField = "msg->" + nameResolver.fieldName(field.getName()) + "_count";
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    w.block(
        "",
        () -> {
          if (stringKey) {
            // String-keyed maps -> JSON object
            w.line("cJSON* map_node = cJSON_CreateObject();");
            w.block(
                "for (size_t i = 0; i < " + countField + "; i++)",
                () -> {
                  String keyExpr = accessor + "[i].key";
                  String valExpr = accessor + "[i].value";
                  emitMapValuePut(w, field, keyExpr, valExpr, funcPrefix);
                });
          } else {
            // Non-string-keyed maps -> JSON array of [key, value] pairs
            w.line("cJSON* map_node = cJSON_CreateArray();");
            w.block(
                "for (size_t i = 0; i < " + countField + "; i++)",
                () -> {
                  w.line("cJSON* pair = cJSON_CreateArray();");
                  String keyExpr = accessor + "[i].key";
                  String valExpr = accessor + "[i].value";
                  emitMapKeyAdd(w, field, keyExpr);
                  emitMapValueAdd(w, field, valExpr, funcPrefix);
                  w.line("cJSON_AddItemToArray(map_node, pair);");
                });
          }
          w.line("cJSON_AddItemToArray(array, map_node);");
        });
  }

  private void emitMapValuePut(
      CodeWriter w, ProtoField field, String keyExpr, String valExpr, String funcPrefix) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getMapValueTypeReference());
      w.line(
          "cJSON_AddItemToObject(map_node, %s, %s ? %s_serialize(%s) : cJSON_CreateNull());",
          keyExpr, valExpr, refPrefix, valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line(
          "cJSON_AddItemToObject(map_node, %s, cJSON_CreateNumber((double)%s));", keyExpr, valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("cJSON_AddItemToObject(map_node, %s, cJSON_CreateBool(%s));", keyExpr, valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line(
          "cJSON_AddItemToObject(map_node, %s, %s ? cJSON_CreateString(%s) : cJSON_CreateNull());",
          keyExpr, valExpr, valExpr);
    } else if (isSignedInt64(field.getMapValueType())) {
      w.line(
          "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%lld\", (long long)%s); cJSON_AddItemToObject(map_node, %s, cJSON_CreateString(__buf)); }",
          valExpr, keyExpr);
    } else if (isUnsignedInt64(field.getMapValueType())) {
      w.line(
          "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%llu\", (unsigned long long)%s); cJSON_AddItemToObject(map_node, %s, cJSON_CreateString(__buf)); }",
          valExpr, keyExpr);
    } else {
      w.line(
          "cJSON_AddItemToObject(map_node, %s, cJSON_CreateNumber((double)%s));", keyExpr, valExpr);
    }
  }

  private void emitMapKeyAdd(CodeWriter w, ProtoField field, String keyExpr) {
    if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("cJSON_AddItemToArray(pair, cJSON_CreateString(%s));", keyExpr);
    } else if (field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("cJSON_AddItemToArray(pair, cJSON_CreateBool(%s));", keyExpr);
    } else if (isSignedInt64(field.getMapKeyType())) {
      w.line(
          "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%lld\", (long long)%s); cJSON_AddItemToArray(pair, cJSON_CreateString(__buf)); }",
          keyExpr);
    } else if (isUnsignedInt64(field.getMapKeyType())) {
      w.line(
          "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%llu\", (unsigned long long)%s); cJSON_AddItemToArray(pair, cJSON_CreateString(__buf)); }",
          keyExpr);
    } else {
      w.line("cJSON_AddItemToArray(pair, cJSON_CreateNumber((double)%s));", keyExpr);
    }
  }

  private void emitMapValueAdd(CodeWriter w, ProtoField field, String valExpr, String funcPrefix) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String refPrefix = nameResolver.resolveTypeFunctionPrefix(field.getMapValueTypeReference());
      w.line(
          "cJSON_AddItemToArray(pair, %s ? %s_serialize(%s) : cJSON_CreateNull());",
          valExpr, refPrefix, valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("cJSON_AddItemToArray(pair, cJSON_CreateNumber((double)%s));", valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("cJSON_AddItemToArray(pair, cJSON_CreateBool(%s));", valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line(
          "cJSON_AddItemToArray(pair, %s ? cJSON_CreateString(%s) : cJSON_CreateNull());",
          valExpr, valExpr);
    } else if (isSignedInt64(field.getMapValueType())) {
      w.line(
          "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%lld\", (long long)%s); cJSON_AddItemToArray(pair, cJSON_CreateString(__buf)); }",
          valExpr);
    } else if (isUnsignedInt64(field.getMapValueType())) {
      w.line(
          "{ char __buf[32]; snprintf(__buf, sizeof(__buf), \"%%llu\", (unsigned long long)%s); cJSON_AddItemToArray(pair, cJSON_CreateString(__buf)); }",
          valExpr);
    } else {
      w.line("cJSON_AddItemToArray(pair, cJSON_CreateNumber((double)%s));", valExpr);
    }
  }

  private static boolean isSignedInt64(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64;
  }

  private static boolean isUnsignedInt64(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64;
  }
}
