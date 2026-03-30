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
package dev.protocgen.textcodecs.jsonarray.codegen.dart;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the static deserialize() factory method for Dart classes. Reads fields positionally
 * from a Dart List.
 */
public class DartDeserializerGenerator {

  private final DartTypeMapper typeMapper;
  private final DartNameResolver nameResolver;

  public DartDeserializerGenerator(DartTypeMapper typeMapper, DartNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // static deserialize(data) - accepts List or dynamic
    w.blankLine();
    w.line("/// Deserialize from a positional JSON array.");
    w.block(
        "static " + className + " deserialize(List<dynamic> array)",
        () -> {
          w.line("final obj = %s();", className);
          w.line("final size = array.length;");

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

    // Convenience: fromJsonString
    w.blankLine();
    w.line("/// Deserialize from a JSON string.");
    w.block(
        "factory " + className + ".fromJsonString(String json)",
        () -> {
          w.line("return %s.deserialize(jsonDecode(json) as List<dynamic>);", className);
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String setter = "obj." + nameResolver.setterName(field.getName());
    String nodeExpr = "array[" + pos + "]";

    w.block(
        "if (size > " + pos + " && " + nodeExpr + " != null)",
        () -> {
          if (field.isMap()) {
            emitMapDeserialize(w, field, setter, nodeExpr);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, setter, nodeExpr);
          } else if (field.isWellKnownType()) {
            emitMessageDeserialize(w, field, setter, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
            emitMessageDeserialize(w, field, setter, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, setter, nodeExpr);
          } else {
            emitScalarDeserialize(w, field, setter, nodeExpr);
          }

          if (field.isProto3Optional()) {
            w.line("obj._presentFields[%d] = true;", pos);
          }
        });
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), nodeExpr);
    w.line("%s(%s);", setter, readExpr);
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    // Enums stored as numbers in the array
    w.line("%s((%s as num).toInt());", setter, nodeExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String msgType = typeMapper.simpleTypeName(field.getTypeReference());
    w.line("%s(%s.deserialize(%s as List<dynamic>));", setter, msgType, nodeExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String msgType = typeMapper.simpleTypeName(field.getTypeReference());
      w.line(
          "final list = (%s as List).map((elem) => elem != null ? %s.deserialize(elem as List<dynamic>) : null).toList();",
          nodeExpr, msgType);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("final list = (%s as List).map((elem) => (elem as num).toInt()).toList();", nodeExpr);
    } else {
      String mapExpr = scalarMapExpr(field.getProtoType());
      w.line("final list = (%s as List).map((elem) => %s).toList();", nodeExpr, mapExpr);
    }
    w.line("%s(list);", setter);
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    if (stringKey) {
      w.line("final map = <String, dynamic>{};");
      w.block(
          "for (final entry in (" + nodeExpr + " as Map).entries)",
          () -> {
            String valueExpr = mapValueReadExpr(field, "entry.value");
            w.line("map[entry.key as String] = %s;", valueExpr);
          });
    } else {
      w.line("final map = <dynamic, dynamic>{};");
      w.block(
          "for (final pair in (" + nodeExpr + " as List))",
          () -> {
            String keyRead = scalarReadExpr(field.getMapKeyType(), "(pair as List)[0]");
            String valueRead = mapValueReadExpr(field, "(pair as List)[1]");
            w.line("map[%s] = %s;", keyRead, valueRead);
          });
    }
    w.line("%s(map);", setter);
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String nodeExpr) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "(" + nodeExpr + " as num).toDouble()";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 ->
          nodeExpr + ".toString()";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 ->
          "(" + nodeExpr + " as num).toInt()";
      case TYPE_BOOL -> nodeExpr + " as bool";
      case TYPE_STRING -> nodeExpr + " as String";
      case TYPE_BYTES -> "base64Decode(" + nodeExpr + " as String)";
      default -> nodeExpr;
    };
  }

  private String scalarMapExpr(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "(elem as num).toDouble()";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> "elem.toString()";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 ->
          "(elem as num).toInt()";
      case TYPE_BOOL -> "elem as bool";
      case TYPE_STRING -> "elem as String";
      case TYPE_BYTES -> "base64Decode(elem as String)";
      default -> "elem";
    };
  }

  private String mapValueReadExpr(ProtoField field, String nodeExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = typeMapper.simpleTypeName(field.getMapValueTypeReference());
      return nodeExpr
          + " != null ? "
          + msgType
          + ".deserialize("
          + nodeExpr
          + " as List<dynamic>) : null";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return "(" + nodeExpr + " as num).toInt()";
    }
    return scalarReadExpr(field.getMapValueType(), nodeExpr);
  }
}
