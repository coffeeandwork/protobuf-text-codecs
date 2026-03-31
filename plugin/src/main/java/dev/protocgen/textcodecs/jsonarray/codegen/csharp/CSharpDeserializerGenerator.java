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
package dev.protocgen.textcodecs.jsonarray.codegen.csharp;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.nio.charset.StandardCharsets;

/**
 * Generates static deserialization methods for C# classes. Uses System.Text.Json (JsonDocument) to
 * parse JSON arrays and reads fields positionally.
 */
public class CSharpDeserializerGenerator {

  private final CSharpTypeMapper typeMapper;
  private final CSharpNameResolver nameResolver;

  public CSharpDeserializerGenerator(CSharpTypeMapper typeMapper, CSharpNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // Internal: deserialize from a JsonElement (a JSON array element)
    w.blankLine();
    w.block(
        "internal static " + className + " FromJsonArray(JsonElement array)",
        () -> {
          w.line("var builder = %s.NewBuilder();", className);
          w.line("int size = array.GetArrayLength();");

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

          w.line("return builder.Build();");
        });

    // Public: deserialize from JSON string
    w.blankLine();
    w.block(
        "public static " + className + " FromJsonString(string json)",
        () -> {
          w.line("using var doc = JsonDocument.Parse(json);");
          w.line("return FromJsonArray(doc.RootElement);");
        });

    // Public: deserialize from byte[]
    w.blankLine();
    w.block(
        "public static " + className + " FromJsonBytes(byte[] bytes)",
        () -> {
          w.line("return FromJsonString(System.Text.Encoding.UTF8.GetString(bytes));");
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String setter = "builder.Set" + CSharpNameResolver.snakeToPascal(field.getName());

    w.block(
        "if (size > " + pos + " && array[" + pos + "].ValueKind != JsonValueKind.Null)",
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
    w.line("%s((%s)%s.GetInt32());", setter, enumType, elemExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s(%s.FromJsonArray(%s));", setter, msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr, int pos) {
    String listType = typeMapper.languageType(field);
    w.line("var __list%d = new %s();", pos, listType);
    w.block(
        "foreach (var __elem" + pos + " in " + elemExpr + ".EnumerateArray())",
        () -> {
          String elemVar = "__elem" + pos;
          String listVar = "__list" + pos;
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String msgType = simpleTypeName(field.getTypeReference());
            w.line(
                "%s.Add(%s.ValueKind == JsonValueKind.Null ? null : %s.FromJsonArray(%s));",
                listVar, elemVar, msgType, elemVar);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            String enumType = simpleTypeName(field.getTypeReference());
            w.line("%s.Add((%s)%s.GetInt32());", listVar, enumType, elemVar);
          } else {
            String readExpr = scalarReadExpr(field.getProtoType(), elemVar);
            w.line("%s.Add(%s);", listVar, readExpr);
          }
        });
    w.line("%s(__list%d);", setter, pos);
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String setter, String elemExpr, int pos) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    String mapType = typeMapper.languageType(field);
    w.line("var __map%d = new %s();", pos, mapType);

    if (stringKey) {
      // String-keyed maps are JSON objects
      w.block(
          "foreach (var __prop" + pos + " in " + elemExpr + ".EnumerateObject())",
          () -> {
            String propVar = "__prop" + pos;
            String mapVar = "__map" + pos;
            String valueExpr = mapValueReadExpr(field, propVar + ".Value");
            w.line("%s[%s.Name] = %s;", mapVar, propVar, valueExpr);
          });
    } else {
      // Non-string-keyed maps are serialized as arrays of [key, value] pairs
      w.block(
          "foreach (var __pair" + pos + " in " + elemExpr + ".EnumerateArray())",
          () -> {
            String pairVar = "__pair" + pos;
            String mapVar = "__map" + pos;
            String keyRead = scalarReadExpr(field.getMapKeyType(), pairVar + "[0]");
            String valueRead = mapValueReadExpr(field, pairVar + "[1]");
            w.line("%s[%s] = %s;", mapVar, keyRead, valueRead);
          });
    }
    w.line("%s(__map%d);", setter, pos);
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String elemExpr) {
    return switch (type) {
      case TYPE_DOUBLE -> elemExpr + ".GetDouble()";
      case TYPE_FLOAT -> "(float)" + elemExpr + ".GetDouble()";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          "("
              + elemExpr
              + ".ValueKind == JsonValueKind.String ? long.Parse("
              + elemExpr
              + ".GetString()) : "
              + elemExpr
              + ".GetInt64())";
      case TYPE_UINT64, TYPE_FIXED64 ->
          "("
              + elemExpr
              + ".ValueKind == JsonValueKind.String ? ulong.Parse("
              + elemExpr
              + ".GetString()) : "
              + elemExpr
              + ".GetUInt64())";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> elemExpr + ".GetInt32()";
      case TYPE_UINT32, TYPE_FIXED32 -> elemExpr + ".GetUInt32()";
      case TYPE_BOOL -> elemExpr + ".GetBoolean()";
      case TYPE_STRING -> elemExpr + ".GetString()";
      case TYPE_BYTES -> "Convert.FromBase64String(" + elemExpr + ".GetString())";
      default -> elemExpr + ".GetString()";
    };
  }

  private String mapValueReadExpr(ProtoField field, String elemExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = simpleTypeName(field.getMapValueTypeReference());
      return elemExpr
          + ".ValueKind == JsonValueKind.Null ? null : "
          + msgType
          + ".FromJsonArray("
          + elemExpr
          + ")";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = simpleTypeName(field.getMapValueTypeReference());
      return "(" + enumType + ")" + elemExpr + ".GetInt32()";
    }
    return scalarReadExpr(field.getMapValueType(), elemExpr);
  }

  /**
   * Convert a proto schema default value string to a C# expression. Proto2 fields can specify
   * default values like [default = "hello"] or [default = 42].
   */
  private String schemaDefaultExpression(ProtoField field, String defaultValue) {
    if (defaultValue == null || defaultValue.isEmpty()) {
      return typeMapper.defaultValue(field);
    }
    // Enum defaults: validate the constant name is a safe identifier (VULN-003)
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
              + "\"";
      case TYPE_BOOL -> {
        if (!"true".equals(defaultValue) && !"false".equals(defaultValue)) {
          throw new IllegalArgumentException(
              "Bool default value '" + defaultValue + "' is not 'true' or 'false'");
        }
        yield defaultValue;
      }
      case TYPE_DOUBLE -> {
        if ("inf".equals(defaultValue)) yield "double.PositiveInfinity";
        if ("-inf".equals(defaultValue)) yield "double.NegativeInfinity";
        if ("nan".equals(defaultValue)) yield "double.NaN";
        yield defaultValue.contains(".") ? defaultValue : defaultValue + ".0";
      }
      case TYPE_FLOAT -> {
        if ("inf".equals(defaultValue)) yield "float.PositiveInfinity";
        if ("-inf".equals(defaultValue)) yield "float.NegativeInfinity";
        if ("nan".equals(defaultValue)) yield "float.NaN";
        yield (defaultValue.contains(".") ? defaultValue : defaultValue + ".0") + "f";
      }
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 -> defaultValue + "L";
      case TYPE_BYTES -> {
        if (defaultValue.isEmpty()) {
          yield "Array.Empty<byte>()";
        }
        yield "Convert.FromBase64String(\""
            + java.util.Base64.getEncoder()
                .encodeToString(defaultValue.getBytes(StandardCharsets.ISO_8859_1))
            + "\")";
      }
      default -> {
        // Validate numeric defaults are actually numbers (VULN-003)
        if (!defaultValue.matches("-?[0-9]+")) {
          throw new IllegalArgumentException(
              "Numeric default value '" + defaultValue + "' is not a valid integer");
        }
        yield defaultValue;
      }
    };
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "object";
  }
}
