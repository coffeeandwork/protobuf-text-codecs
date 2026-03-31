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
package dev.protocgen.textcodecs.jsonarray.codegen.java;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates static deserialization methods for Java classes. Reads fields positionally from a
 * {@code java.util.List<Object>} produced by {@code JsonArrayReader.parseArray()}.
 */
public class JavaDeserializerGenerator {

  private final JavaTypeMapper typeMapper;
  private final JavaNameResolver nameResolver;

  public JavaDeserializerGenerator(JavaTypeMapper typeMapper, JavaNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // Package-private: deserialize from a pre-parsed List<Object> (used by nested messages)
    w.blankLine();
    w.line("@SuppressWarnings(\"unchecked\")");
    w.block(
        "static " + className + " fromJsonArray(java.util.List<Object> array)",
        () -> {
          w.line("%s.Builder builder = %s.newBuilder();", className, className);
          w.line("int size = array.size();");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              // Gap -- skip
              w.line("// position %d: gap (no field)", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos);
          }

          w.line("return builder.build();");
        });

    // Public: deserialize from JSON string
    w.blankLine();
    w.block(
        "public static " + className + " fromJsonString(String json)",
        () -> {
          w.line(
              "java.util.List<Object> array = dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayReader.parseArray(json);");
          w.line("return fromJsonArray(array);");
        });

    // Public: deserialize from byte[]
    w.blankLine();
    w.block(
        "public static " + className + " fromJsonBytes(byte[] bytes)",
        () -> {
          w.line(
              "return fromJsonString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));");
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String setter = "builder." + nameResolver.setterName(field.getName());

    w.block(
        "if (size > " + pos + " && array.get(" + pos + ") != null)",
        () -> {
          String elemExpr = "array.get(" + pos + ")";

          if (field.isMap()) {
            emitMapDeserialize(w, field, setter, elemExpr, pos);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, setter, elemExpr, pos);
          } else if (field.isWellKnownType()) {
            // Treat well-known types as nested messages for now
            emitMessageDeserialize(w, field, setter, elemExpr);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
            emitMessageDeserialize(w, field, setter, elemExpr);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, setter, elemExpr);
          } else {
            emitScalarDeserialize(w, field, setter, elemExpr);
          }
        });

    // Apply schema-specified default for proto2 fields when absent/null
    if (field.getDefaultValue() != null && !field.isRepeated() && !field.isMap()) {
      w.block(
          "else",
          () -> {
            String defaultExpr = schemaDefaultExpression(field, field.getDefaultValue());
            w.line("%s(%s);", setter, defaultExpr);
          });
    }
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), elemExpr);
    w.line("%s(%s);", setter, readExpr);
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String setter, String elemExpr) {
    String enumType = simpleTypeName(field.getTypeReference());
    w.line("%s(%s.forNumber(((Number) %s).intValue()));", setter, enumType, elemExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s(%s.fromJsonArray((java.util.List<Object>) %s));", setter, msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr, int pos) {
    String listType = typeMapper.languageType(field);
    w.line("java.util.List<Object> __listObj%d = (java.util.List<Object>) %s;", pos, elemExpr);
    w.line("%s list%d = new java.util.ArrayList<>(__listObj%d.size());", listType, pos, pos);
    w.block(
        "for (Object __elem" + pos + " : __listObj" + pos + ")",
        () -> {
          String elemVar = "__elem" + pos;
          String listVar = "list" + pos;
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String msgType = simpleTypeName(field.getTypeReference());
            w.line(
                "%s.add(%s == null ? null : %s.fromJsonArray((java.util.List<Object>) %s));",
                listVar, elemVar, msgType, elemVar);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            String enumType = simpleTypeName(field.getTypeReference());
            w.line("%s.add(%s.forNumber(((Number) %s).intValue()));", listVar, enumType, elemVar);
          } else {
            String readExpr = scalarReadExpr(field.getProtoType(), elemVar);
            w.line("%s.add(%s);", listVar, readExpr);
          }
        });
    w.line("%s(list%d);", setter, pos);
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr, int pos) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    String mapType = typeMapper.languageType(field);
    w.line("%s map%d = new java.util.LinkedHashMap<>();", mapType, pos);

    if (stringKey) {
      // String-keyed maps are JSON objects: Map<String, Object>
      w.line(
          "java.util.Map<String, Object> __mapObj%d = (java.util.Map<String, Object>) %s;",
          pos, elemExpr);
      w.block(
          "for (java.util.Map.Entry<String, Object> __entry"
              + pos
              + " : __mapObj"
              + pos
              + ".entrySet())",
          () -> {
            String entryVar = "__entry" + pos;
            String mapVar = "map" + pos;
            String valueExpr = mapValueReadExpr(field, entryVar + ".getValue()");
            w.line("%s.put(%s.getKey(), %s);", mapVar, entryVar, valueExpr);
          });
    } else {
      // Non-string-keyed maps are serialized as arrays of [key, value] pairs
      w.line("java.util.List<Object> __pairs%d = (java.util.List<Object>) %s;", pos, elemExpr);
      w.block(
          "for (Object __pair" + pos + " : __pairs" + pos + ")",
          () -> {
            String pairVar = "__pair" + pos;
            String mapVar = "map" + pos;
            w.line("java.util.List<Object> __kv%d = (java.util.List<Object>) %s;", pos, pairVar);
            String kvVar = "__kv" + pos;
            String keyRead = scalarReadExpr(field.getMapKeyType(), kvVar + ".get(0)");
            String valueRead = mapValueReadExpr(field, kvVar + ".get(1)");
            w.line("%s.put(%s, %s);", mapVar, keyRead, valueRead);
          });
    }
    w.line("%s(map%d);", setter, pos);
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String elemExpr) {
    return switch (type) {
      case TYPE_DOUBLE -> "((Number) " + elemExpr + ").doubleValue()";
      case TYPE_FLOAT -> "((Number) " + elemExpr + ").floatValue()";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          "("
              + elemExpr
              + " instanceof String ? Long.parseLong((String) "
              + elemExpr
              + ") : ((Number) "
              + elemExpr
              + ").longValue())";
      case TYPE_UINT64, TYPE_FIXED64 ->
          "("
              + elemExpr
              + " instanceof String ? Long.parseUnsignedLong((String) "
              + elemExpr
              + ") : ((Number) "
              + elemExpr
              + ").longValue())";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 ->
          "((Number) " + elemExpr + ").intValue()";
      case TYPE_BOOL -> "(Boolean) " + elemExpr;
      case TYPE_STRING -> "(String) " + elemExpr;
      case TYPE_BYTES -> "java.util.Base64.getDecoder().decode((String) " + elemExpr + ")";
      default -> "(String) " + elemExpr;
    };
  }

  private String mapValueReadExpr(ProtoField field, String elemExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      return elemExpr
          + " == null ? null : "
          + msgType
          + ".fromJsonArray((java.util.List<Object>) "
          + elemExpr
          + ")";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      return enumType + ".forNumber(((Number) " + elemExpr + ").intValue())";
    }
    return scalarReadExpr(field.getMapValueType(), elemExpr);
  }

  /**
   * Convert a proto schema default value string to a Java expression. Delegates to {@link
   * JavaTypeMapper#formatSchemaDefault} for the core logic and adds extra Unicode line/paragraph
   * separator escaping for string defaults.
   */
  private String schemaDefaultExpression(ProtoField field, String defaultValue) {
    if (defaultValue == null || defaultValue.isEmpty()) {
      return typeMapper.defaultValue(field);
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      if (!defaultValue.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
        throw new IllegalArgumentException(
            "Enum default value '" + defaultValue + "' is not a valid identifier");
      }
      String enumType = simpleTypeName(field.getTypeReference());
      return enumType + "." + defaultValue;
    }
    String expr = typeMapper.formatSchemaDefault(field.getProtoType(), defaultValue);
    // Add Unicode line/paragraph separator escaping for string defaults
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
      expr = expr.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
    return expr;
  }

  private String simpleTypeName(String protoFullName) {
    return JavaNameResolver.simpleTypeName(protoFullName);
  }
}
