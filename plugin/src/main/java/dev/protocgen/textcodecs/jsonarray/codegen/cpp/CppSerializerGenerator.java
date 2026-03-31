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
package dev.protocgen.textcodecs.jsonarray.codegen.cpp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize() method body for C++ classes. Produces a nlohmann::json array with
 * fields at positions determined by field number.
 */
public class CppSerializerGenerator {

  private final CppTypeMapper typeMapper;
  private final CppNameResolver nameResolver;

  public CppSerializerGenerator(CppTypeMapper typeMapper, CppNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    w.blankLine();
    w.block(
        "inline nlohmann::json " + className + "::serialize() const",
        () -> {
          w.line("nlohmann::json arr = nlohmann::json::array();");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("arr.push_back(nullptr); // gap (no field number %d)", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("return arr;");
        });

    // to_json_string convenience method
    w.blankLine();
    w.block(
        "inline std::string " + className + "::to_json_string() const",
        () -> {
          w.line("return serialize().dump();");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String cppField = nameResolver.fieldName(field.getName()) + "_";

    if (field.isOneofMember()) {
      String caseField = "__oneof_" + nameResolver.fieldName(field.getOneofName()) + "_case_";
      w.block(
          "if (" + caseField + " == " + field.getFieldNumber() + ")",
          () -> {
            emitValueForField(w, field, cppField);
          });
      w.line("else { arr.push_back(nullptr); }");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, cppField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, cppField);
    } else if (field.isWellKnownType()) {
      emitWellKnownSerialize(w, field, cppField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, cppField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, cppField);
    } else {
      emitScalarSerialize(w, field, cppField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String cppField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line(
          "arr.push_back(%s.has_value() ? %s.value().serialize() : nullptr);", cppField, cppField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("arr.push_back(static_cast<int>(%s));", cppField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("arr.push_back(jsonarray::base64_encode(%s));", cppField);
    } else if (is64BitType(field.getProtoType())) {
      w.line("arr.push_back(std::to_string(%s));", cppField);
    } else {
      w.line("arr.push_back(%s);", cppField);
    }
  }

  private static boolean is64BitType(FieldDescriptorProto.Type type) {
    return ProtoTypeUtil.isInt64Type(type);
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String cppField) {
    if (field.isProto3Optional()) {
      w.block(
          "if (" + cppField + ".has_value())",
          () -> {
            if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
              w.line("arr.push_back(jsonarray::base64_encode(%s.value()));", cppField);
            } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT
                || field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
              w.line(
                  "arr.push_back(std::isnan(%s.value()) || std::isinf(%s.value()) ? nlohmann::json(nullptr) : nlohmann::json(%s.value()));",
                  cppField, cppField, cppField);
            } else if (is64BitType(field.getProtoType())) {
              w.line("arr.push_back(std::to_string(%s.value()));", cppField);
            } else {
              w.line("arr.push_back(%s.value());", cppField);
            }
          });
      w.line("else { arr.push_back(nullptr); }");
      return;
    }
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("arr.push_back(jsonarray::base64_encode(%s));", cppField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      w.line(
          "arr.push_back(std::isnan(%s) || std::isinf(%s) ? nlohmann::json(nullptr) : nlohmann::json(%s));",
          cppField, cppField, cppField);
    } else if (is64BitType(field.getProtoType())) {
      w.line("arr.push_back(std::to_string(%s));", cppField);
    } else {
      w.line("arr.push_back(%s);", cppField);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String cppField) {
    if (field.isProto3Optional()) {
      w.block(
          "if (" + cppField + ".has_value())",
          () -> {
            w.line("arr.push_back(static_cast<int>(%s.value()));", cppField);
          });
      w.line("else { arr.push_back(nullptr); }");
    } else {
      w.line("arr.push_back(static_cast<int>(%s));", cppField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String cppField) {
    // Singular message fields are std::optional<T>; check presence
    w.block(
        "if (" + cppField + ".has_value())",
        () -> {
          w.line("arr.push_back(%s.value().serialize());", cppField);
        });
    w.line("else { arr.push_back(nullptr); }");
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String cppField) {
    w.block(
        "",
        () -> {
          w.line("nlohmann::json list_node = nlohmann::json::array();");
          String elemVar = nameResolver.fieldName(field.getName()) + "_item";
          String elemType = "const auto&";
          w.block(
              "for (" + elemType + " " + elemVar + " : " + cppField + ")",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.line("list_node.push_back(%s.serialize());", elemVar);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("list_node.push_back(static_cast<int>(%s));", elemVar);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.line("list_node.push_back(jsonarray::base64_encode(%s));", elemVar);
                } else if (is64BitType(field.getProtoType())) {
                  w.line("list_node.push_back(std::to_string(%s));", elemVar);
                } else {
                  w.line("list_node.push_back(%s);", elemVar);
                }
              });
          w.line("arr.push_back(std::move(list_node));");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String cppField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "",
        () -> {
          if (stringKey) {
            // String-keyed maps become JSON objects
            w.line("nlohmann::json map_node = nlohmann::json::object();");
            String entryVar = nameResolver.fieldName(field.getName()) + "_entry";
            w.block(
                "for (const auto& " + entryVar + " : " + cppField + ")",
                () -> {
                  emitMapValuePut(w, field, entryVar + ".first", entryVar + ".second");
                });
            w.line("arr.push_back(std::move(map_node));");
          } else {
            // Non-string-keyed maps become JSON array of [key, value] pairs
            w.line("nlohmann::json map_node = nlohmann::json::array();");
            String entryVar = nameResolver.fieldName(field.getName()) + "_entry";
            w.block(
                "for (const auto& " + entryVar + " : " + cppField + ")",
                () -> {
                  w.line("nlohmann::json pair = nlohmann::json::array();");
                  w.line("pair.push_back(%s.first);", entryVar);
                  emitMapValueAdd(w, field, entryVar + ".second");
                  w.line("map_node.push_back(std::move(pair));");
                });
            w.line("arr.push_back(std::move(map_node));");
          }
        });
  }

  private void emitMapValuePut(CodeWriter w, ProtoField field, String keyExpr, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("map_node[%s] = %s.serialize();", keyExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("map_node[%s] = static_cast<int>(%s);", keyExpr, valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("map_node[%s] = jsonarray::base64_encode(%s);", keyExpr, valueExpr);
    } else if (is64BitType(field.getMapValueType())) {
      w.line("map_node[%s] = std::to_string(%s);", keyExpr, valueExpr);
    } else {
      w.line("map_node[%s] = %s;", keyExpr, valueExpr);
    }
  }

  private void emitMapValueAdd(CodeWriter w, ProtoField field, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.line("pair.push_back(%s.serialize());", valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("pair.push_back(static_cast<int>(%s));", valueExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("pair.push_back(jsonarray::base64_encode(%s));", valueExpr);
    } else if (is64BitType(field.getMapValueType())) {
      w.line("pair.push_back(std::to_string(%s));", valueExpr);
    } else {
      w.line("pair.push_back(%s);", valueExpr);
    }
  }

  private void emitWellKnownSerialize(CodeWriter w, ProtoField field, String cppField) {
    // For C++, well-known types are serialized as nested messages
    emitMessageSerialize(w, field, cppField);
  }
}
