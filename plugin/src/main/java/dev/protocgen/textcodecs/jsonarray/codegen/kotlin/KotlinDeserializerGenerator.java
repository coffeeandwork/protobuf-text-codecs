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
package dev.protocgen.textcodecs.jsonarray.codegen.kotlin;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.nio.charset.StandardCharsets;

/**
 * Generates static deserialization methods for Kotlin classes. Reads fields positionally from a
 * List<Any?> produced by the JSON array reader.
 */
public class KotlinDeserializerGenerator {

  private final KotlinTypeMapper typeMapper;
  private final KotlinNameResolver nameResolver;

  public KotlinDeserializerGenerator(KotlinTypeMapper typeMapper, KotlinNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // Internal: deserialize from a pre-parsed List<Any?>
    w.blankLine();
    w.line("@Suppress(\"UNCHECKED_CAST\")");
    w.block(
        "internal fun fromJsonArray(array: List<Any?>): " + className,
        () -> {
          w.line("val builder = %s.newBuilder()", className);
          w.line("val size = array.size");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("// position %d: gap (no field)", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos);
          }

          w.line("return builder.build()");
        });

    // Public: deserialize from JSON string
    w.blankLine();
    w.block(
        "fun fromJsonString(json: String): " + className,
        () -> {
          w.line(
              "val array = dev.protocgen.textcodecs.jsonarray.runtime.JsonArrayReader.parseArray(json)");
          w.line("return fromJsonArray(array)");
        });

    // Public: deserialize from ByteArray
    w.blankLine();
    w.block(
        "fun fromJsonBytes(bytes: ByteArray): " + className,
        () -> {
          w.line("return fromJsonString(String(bytes, Charsets.UTF_8))");
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String setter = "builder." + nameResolver.setterName(field.getName());

    w.block(
        "if (size > " + pos + " && array[" + pos + "] != null)",
        () -> {
          String elemExpr = "array[" + pos + "]";

          if (field.isMap()) {
            emitMapDeserialize(w, field, setter, elemExpr, pos);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, setter, elemExpr, pos);
          } else if (field.isWellKnownType()) {
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
            w.line("%s(%s)", setter, defaultExpr);
          });
    }
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), elemExpr);
    w.line("%s(%s)", setter, readExpr);
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String setter, String elemExpr) {
    String enumType = simpleTypeName(field.getTypeReference());
    w.line("%s(%s.forNumber((%s as Number).toInt()))", setter, enumType, elemExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s(%s.fromJsonArray(%s as List<Any?>))", setter, msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr, int pos) {
    String listType = typeMapper.languageType(field);
    w.line("val __listObj%d = %s as List<Any?>", pos, elemExpr);
    w.line("val list%d: %s = mutableListOf()", pos, listType);
    w.block(
        "for (__elem" + pos + " in __listObj" + pos + ")",
        () -> {
          String elemVar = "__elem" + pos;
          String listVar = "list" + pos;
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String msgType = simpleTypeName(field.getTypeReference());
            w.line(
                "%s.add(if (%s == null) null else %s.fromJsonArray(%s as List<Any?>))",
                listVar, elemVar, msgType, elemVar);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            String enumType = simpleTypeName(field.getTypeReference());
            w.line("%s.add(%s.forNumber((%s as Number).toInt()))", listVar, enumType, elemVar);
          } else {
            String readExpr = scalarReadExpr(field.getProtoType(), elemVar);
            w.line("%s.add(%s)", listVar, readExpr);
          }
        });
    w.line("%s(list%d)", setter, pos);
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr, int pos) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    String mapType = typeMapper.languageType(field);
    w.line("val map%d: %s = mutableMapOf()", pos, mapType);

    if (stringKey) {
      w.line("val __mapObj%d = %s as Map<String, Any?>", pos, elemExpr);
      w.block(
          "for ((__key" + pos + ", __val" + pos + ") in __mapObj" + pos + ")",
          () -> {
            String valueExpr = mapValueReadExpr(field, "__val" + pos);
            w.line("map%d[__key%d] = %s", pos, pos, valueExpr);
          });
    } else {
      w.line("val __pairs%d = %s as List<Any?>", pos, elemExpr);
      w.block(
          "for (__pair" + pos + " in __pairs" + pos + ")",
          () -> {
            w.line("val __kv%d = %s as List<Any?>", pos, "__pair" + pos);
            String keyRead = scalarReadExpr(field.getMapKeyType(), "__kv" + pos + "[0]");
            String valueRead = mapValueReadExpr(field, "__kv" + pos + "[1]");
            w.line("map%d[%s] = %s", pos, keyRead, valueRead);
          });
    }
    w.line("%s(map%d)", setter, pos);
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String elemExpr) {
    return switch (type) {
      case TYPE_DOUBLE -> "(" + elemExpr + " as Number).toDouble()";
      case TYPE_FLOAT -> "(" + elemExpr + " as Number).toFloat()";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          "(if ("
              + elemExpr
              + " is String) ("
              + elemExpr
              + " as String).toLong() else ("
              + elemExpr
              + " as Number).toLong())";
      case TYPE_UINT64, TYPE_FIXED64 ->
          "(if ("
              + elemExpr
              + " is String) java.lang.Long.parseUnsignedLong("
              + elemExpr
              + " as String) else ("
              + elemExpr
              + " as Number).toLong())";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 ->
          "(" + elemExpr + " as Number).toInt()";
      case TYPE_BOOL -> elemExpr + " as Boolean";
      case TYPE_STRING -> elemExpr + " as String";
      case TYPE_BYTES -> "java.util.Base64.getDecoder().decode(" + elemExpr + " as String)";
      default -> elemExpr + " as String";
    };
  }

  private String mapValueReadExpr(ProtoField field, String elemExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      return "if ("
          + elemExpr
          + " == null) null else "
          + msgType
          + ".fromJsonArray("
          + elemExpr
          + " as List<Any?>)";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      return enumType + ".forNumber((" + elemExpr + " as Number).toInt())";
    }
    return scalarReadExpr(field.getMapValueType(), elemExpr);
  }

  /**
   * Convert a proto schema default value string to a Kotlin expression. Proto2 fields can specify
   * default values like [default = "hello"] or [default = 42].
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
    return switch (field.getProtoType()) {
      case TYPE_STRING ->
          "\""
              + defaultValue
                  .replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\0", "\\0")
                  .replace("\u2028", "\\u2028")
                  .replace("\u2029", "\\u2029")
              + "\"";
      case TYPE_BOOL -> {
        if (!"true".equals(defaultValue) && !"false".equals(defaultValue)) {
          throw new IllegalArgumentException(
              "Bool default value '" + defaultValue + "' is not 'true' or 'false'");
        }
        yield defaultValue;
      }
      case TYPE_DOUBLE -> {
        if ("inf".equals(defaultValue)) yield "Double.POSITIVE_INFINITY";
        if ("-inf".equals(defaultValue)) yield "Double.NEGATIVE_INFINITY";
        if ("nan".equals(defaultValue)) yield "Double.NaN";
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "Float.POSITIVE_INFINITY";
        if ("-inf".equals(defaultValue)) yield "Float.NEGATIVE_INFINITY";
        if ("nan".equals(defaultValue)) yield "Float.NaN";
        yield (defaultValue.contains(".") ? defaultValue : defaultValue + ".0") + "f";
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> defaultValue + "L";
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "byteArrayOf()";
        }
        yield "java.util.Base64.getDecoder().decode(\""
            + java.util.Base64.getEncoder()
                .encodeToString(defaultValue.getBytes(StandardCharsets.ISO_8859_1))
            + "\")";
      }
      default -> {
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Numeric default value '" + defaultValue + "' is not a valid integer");
        }
        yield defaultValue;
      }
    };
  }

  private String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "Any";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }
}
