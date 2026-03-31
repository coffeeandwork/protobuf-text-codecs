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
package dev.protocgen.textcodecs.jsonarray.codegen.cpp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the static deserialize() method for C++ classes. Reads fields positionally from a
 * nlohmann::json array.
 */
public class CppDeserializerGenerator {

  private final CppTypeMapper typeMapper;
  private final CppNameResolver nameResolver;

  public CppDeserializerGenerator(CppTypeMapper typeMapper, CppNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    w.blankLine();
    w.block(
        "inline " + className + " " + className + "::deserialize(const nlohmann::json& arr)",
        () -> {
          w.line("%s obj;", className);
          w.line("const auto size = arr.size();");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("// position %d: gap (no field)", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos);
          }

          w.line("return obj;");
        });

    // from_json_string convenience method
    w.blankLine();
    w.block(
        "inline " + className + " " + className + "::from_json_string(const std::string& json)",
        () -> {
          w.line("return deserialize(nlohmann::json::parse(json));");
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String setter = "obj." + nameResolver.setterName(field.getName());

    w.block(
        "if (size > " + pos + " && !arr[" + pos + "].is_null())",
        () -> {
          String nodeExpr = "arr[" + pos + "]";

          if (field.isMap()) {
            emitMapDeserialize(w, field, setter, nodeExpr);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, setter, nodeExpr);
          } else if (field.isWellKnownType()) {
            emitWellKnownDeserialize(w, field, setter, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
            emitMessageDeserialize(w, field, setter, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, setter, nodeExpr);
          } else {
            emitScalarDeserialize(w, field, setter, nodeExpr);
          }
        });
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), nodeExpr);
    w.line("%s(%s);", setter, readExpr);
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String enumType = simpleTypeName(field.getTypeReference());
    w.line("%s(static_cast<%s>(%s.get<int>()));", setter, enumType, nodeExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s(std::make_optional(%s::deserialize(%s)));", setter, msgType, nodeExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String vecType = typeMapper.languageType(field);
    w.line("%s list;", vecType);
    w.block(
        "for (const auto& elem : " + nodeExpr + ")",
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String msgType = simpleTypeName(field.getTypeReference());
            w.line("list.push_back(%s::deserialize(elem));", msgType);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            String enumType = simpleTypeName(field.getTypeReference());
            w.line("list.push_back(static_cast<%s>(elem.get<int>()));", enumType);
          } else {
            String readExpr = scalarReadExpr(field.getProtoType(), "elem");
            w.line("list.push_back(%s);", readExpr);
          }
        });
    w.line("%s(std::move(list));", setter);
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    String mapType = typeMapper.languageType(field);
    w.line("%s map;", mapType);

    if (stringKey) {
      w.block(
          "for (auto it = " + nodeExpr + ".begin(); it != " + nodeExpr + ".end(); ++it)",
          () -> {
            String valueExpr = mapValueReadExpr(field, "it.value()");
            w.line("map[it.key()] = %s;", valueExpr);
          });
    } else {
      w.block(
          "for (const auto& pair : " + nodeExpr + ")",
          () -> {
            String keyRead = scalarReadExpr(field.getMapKeyType(), "pair[0]");
            String valueRead = mapValueReadExpr(field, "pair[1]");
            w.line("map[%s] = %s;", keyRead, valueRead);
          });
    }
    w.line("%s(std::move(map));", setter);
  }

  private void emitWellKnownDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    emitMessageDeserialize(w, field, setter, nodeExpr);
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String nodeExpr) {
    return switch (type) {
      case TYPE_DOUBLE -> nodeExpr + ".get<double>()";
      case TYPE_FLOAT -> nodeExpr + ".get<float>()";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          "("
              + nodeExpr
              + ".is_string() ? std::stoll("
              + nodeExpr
              + ".get_ref<const std::string&>()) : "
              + nodeExpr
              + ".get<int64_t>())";
      case TYPE_UINT64, TYPE_FIXED64 ->
          "("
              + nodeExpr
              + ".is_string() ? std::stoull("
              + nodeExpr
              + ".get_ref<const std::string&>()) : "
              + nodeExpr
              + ".get<uint64_t>())";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> nodeExpr + ".get<int32_t>()";
      case TYPE_UINT32, TYPE_FIXED32 -> nodeExpr + ".get<uint32_t>()";
      case TYPE_BOOL -> nodeExpr + ".get<bool>()";
      case TYPE_STRING -> nodeExpr + ".get<std::string>()";
      case TYPE_BYTES -> "jsonarray::base64_decode(" + nodeExpr + ".get<std::string>())";
      default -> nodeExpr + ".get<std::string>()";
    };
  }

  private String mapValueReadExpr(ProtoField field, String nodeExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      return msgType + "::deserialize(" + nodeExpr + ")";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      return "static_cast<" + enumType + ">(" + nodeExpr + ".get<int>())";
    }
    return scalarReadExpr(field.getMapValueType(), nodeExpr);
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "void*";
  }
}
