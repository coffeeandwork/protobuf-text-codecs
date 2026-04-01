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
package dev.protocgen.textcodecs.jsonarray.codegen.go;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the Deserialize function for Go structs. Reads fields positionally from a []any slice.
 */
public class GoDeserializerGenerator {

  private final GoTypeMapper typeMapper;
  private final GoNameResolver nameResolver;

  public GoDeserializerGenerator(GoTypeMapper typeMapper, GoNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String structName) {
    // Deserialize function: func DeserializeXxx(arr []any) (*Xxx, error)
    w.blankLine();
    w.block(
        "func Deserialize" + structName + "(arr []any) (*" + structName + ", error)",
        () -> {
          w.line("obj := &%s{}", structName);
          w.line("size := len(arr)");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("// position %d: gap (no field)", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos, structName);
          }

          w.line("return obj, nil");
        });

    // Unmarshal method on receiver: takes []byte, returns error
    w.blankLine();
    w.block(
        "func (m *" + structName + ") Unmarshal(data []byte) error",
        () -> {
          w.line("var arr []any");
          w.block(
              "if err := json.Unmarshal(data, &arr); err != nil",
              () -> {
                w.line("return fmt.Errorf(\"failed to unmarshal JSON: %s\", err)", "%w");
              });
          w.line("parsed, err := Deserialize%s(arr)", structName);
          w.block(
              "if err != nil",
              () -> {
                w.line("return err");
              });
          w.line("*m = *parsed");
          w.line("return nil");
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos, String structName) {
    String goField = "obj." + nameResolver.fieldName(field.getName());

    w.block(
        "if size > " + pos + " && arr[" + pos + "] != nil",
        () -> {
          String elemExpr = "arr[" + pos + "]";

          if (field.isMap()) {
            emitMapDeserialize(w, field, goField, elemExpr);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, goField, elemExpr);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            emitMessageDeserialize(w, field, goField, elemExpr);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, goField, elemExpr);
          } else {
            emitScalarDeserialize(w, field, goField, elemExpr);
          }

          if (field.isOneofMember()) {
            String caseField = "obj." + GoNameResolver.snakeToPascal(field.getOneofName()) + "Case";
            w.line("%s = %d", caseField, field.getFieldNumber());
          }
        });
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String goField, String elemExpr) {
    if (field.isProto3Optional()) {
      // Pointer assignment for optional scalars
      String goType = typeMapper.scalarType(field.getProtoType());
      if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.block(
            "if s, ok := " + elemExpr + ".(string); ok",
            () -> {
              w.block(
                  "if decoded, err := base64.StdEncoding.DecodeString(s); err == nil",
                  () -> {
                    w.line("%s = decoded", goField);
                  });
            });
      } else if (isInt64Type(field.getProtoType())) {
        // int64/uint64: parse from string for precision
        emitSafeInt64Deserialize(w, field, goField, elemExpr, true);
      } else {
        emitSafeScalarAssert(w, field.getProtoType(), elemExpr, goField, true);
      }
      return;
    }

    switch (field.getProtoType()) {
      case TYPE_BYTES:
        w.block(
            "if s, ok := " + elemExpr + ".(string); ok",
            () -> {
              w.block(
                  "if decoded, err := base64.StdEncoding.DecodeString(s); err == nil",
                  () -> {
                    w.line("%s = decoded", goField);
                  });
            });
        break;
      default:
        if (isInt64Type(field.getProtoType())) {
          // int64/uint64: parse from string for precision
          emitSafeInt64Deserialize(w, field, goField, elemExpr, false);
        } else {
          emitSafeScalarAssert(w, field.getProtoType(), elemExpr, goField, false);
        }
        break;
    }
  }

  private void emitEnumDeserialize(
      CodeWriter w, ProtoField field, String goField, String elemExpr) {
    String enumType = typeMapper.simpleTypeName(field.getTypeReference());
    w.block(
        "if v, ok := " + elemExpr + ".(float64); ok",
        () -> {
          w.line("%s = %s(int32(v))", goField, enumType);
        });
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String goField, String elemExpr) {
    String msgType = typeMapper.simpleTypeName(field.getTypeReference());
    w.block(
        "if subArr, ok := " + elemExpr + ".([]any); ok",
        () -> {
          w.line("sub, err := Deserialize%s(subArr)", msgType);
          w.block(
              "if err == nil",
              () -> {
                w.line("%s = sub", goField);
              });
        });
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String goField, String elemExpr) {
    w.block(
        "if listRaw, ok := " + elemExpr + ".([]any); ok",
        () -> {
          String elemType = typeMapper.elementType(field);
          w.line("list := make([]%s, 0, len(listRaw))", elemType);
          w.block(
              "for _, elem := range listRaw",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  String msgType = typeMapper.simpleTypeName(field.getTypeReference());
                  w.blockContinue(
                      "if elem == nil",
                      () -> {
                        w.line("list = append(list, nil)");
                      });
                  w.raw(" ");
                  w.block(
                      "else if subArr, ok := elem.([]any); ok",
                      () -> {
                        w.line("sub, err := Deserialize%s(subArr)", msgType);
                        w.block(
                            "if err == nil",
                            () -> {
                              w.line("list = append(list, sub)");
                            });
                      });
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  String enumType = typeMapper.simpleTypeName(field.getTypeReference());
                  w.block(
                      "if v, ok := elem.(float64); ok",
                      () -> {
                        w.line("list = append(list, %s(int32(v)))", enumType);
                      });
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.block(
                      "if s, ok := elem.(string); ok",
                      () -> {
                        w.block(
                            "if decoded, err := base64.StdEncoding.DecodeString(s); err == nil",
                            () -> {
                              w.line("list = append(list, decoded)");
                            });
                      });
                } else if (isInt64Type(field.getProtoType())) {
                  // int64/uint64: parse from string for precision
                  String goType = typeMapper.scalarType(field.getProtoType());
                  boolean isUnsigned =
                      field.getProtoType() == FieldDescriptorProto.Type.TYPE_UINT64
                          || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FIXED64;
                  w.block(
                      "if s, ok := elem.(string); ok",
                      () -> {
                        if (isUnsigned) {
                          w.block(
                              "if parsed, err := strconv.ParseUint(s, 10, 64); err == nil",
                              () -> {
                                w.line("list = append(list, parsed)");
                              });
                        } else {
                          w.block(
                              "if parsed, err := strconv.ParseInt(s, 10, 64); err == nil",
                              () -> {
                                w.line("list = append(list, parsed)");
                              });
                        }
                      });
                  w.raw(" ");
                  w.block(
                      "else if v, ok := elem.(float64); ok",
                      () -> {
                        w.line("list = append(list, %s(v))", goType);
                      });
                } else {
                  String goType = typeMapper.scalarType(field.getProtoType());
                  String assertType = goAssertType(field.getProtoType());
                  w.block(
                      "if v, ok := elem.(" + assertType + "); ok",
                      () -> {
                        if (assertType.equals(goType)) {
                          w.line("list = append(list, v)");
                        } else {
                          w.line("list = append(list, %s(v))", goType);
                        }
                      });
                }
              });
          w.line("%s = list", goField);
        });
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String goField, String elemExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    String mapType = typeMapper.languageType(field);
    w.line("m := make(%s)", mapType);

    if (stringKey) {
      // JSON object: map[string]any from json.Unmarshal
      w.block(
          "if mapRaw, ok := " + elemExpr + ".(map[string]any); ok",
          () -> {
            w.block(
                "for k, v := range mapRaw",
                () -> {
                  String valueAssign = mapValueDeserializeExpr(field, "v");
                  w.line("m[k] = %s", valueAssign);
                });
          });
    } else {
      // Array of [key, value] pairs
      w.block(
          "if pairsRaw, ok := " + elemExpr + ".([]any); ok",
          () -> {
            w.block(
                "for _, pairRaw := range pairsRaw",
                () -> {
                  w.block(
                      "if pair, ok := pairRaw.([]any); ok && len(pair) == 2",
                      () -> {
                        String keyType = typeMapper.scalarType(field.getMapKeyType());
                        String keyCast = scalarCastExpr(field.getMapKeyType(), "pair[0]");
                        w.line("k := %s(%s)", keyType, keyCast);
                        String valueAssign = mapValueDeserializeExpr(field, "pair[1]");
                        w.line("m[k] = %s", valueAssign);
                      });
                });
          });
    }
    w.line("%s = m", goField);
  }

  /**
   * Generate a Go expression to safely cast a JSON any value to the appropriate Go scalar type.
   * json.Unmarshal into []any produces float64 for all numbers and string for strings. Uses safe
   * type assertions with ok pattern in the calling context (for maps and inline expressions).
   */
  private String scalarCastExpr(FieldDescriptorProto.Type type, String expr) {
    return switch (type) {
      case TYPE_DOUBLE -> safeFloat64(expr);
      case TYPE_FLOAT -> safeFloat64(expr);
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 -> safeFloat64(expr);
      case TYPE_UINT64, TYPE_FIXED64 -> safeFloat64(expr);
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> safeFloat64(expr);
      case TYPE_UINT32, TYPE_FIXED32 -> safeFloat64(expr);
      case TYPE_BOOL -> safeBool(expr);
      case TYPE_STRING -> safeString(expr);
      default -> expr;
    };
  }

  /**
   * Safe float64 assertion that returns 0 on failure. Used in inline map-value expressions where we
   * cannot use the if-ok block pattern.
   */
  private String safeFloat64(String expr) {
    return "func() float64 { if v, ok := " + expr + ".(float64); ok { return v }; return 0 }()";
  }

  /** Safe bool assertion that returns false on failure. Used in inline map-value expressions. */
  private String safeBool(String expr) {
    return "func() bool { if v, ok := " + expr + ".(bool); ok { return v }; return false }()";
  }

  /** Safe string assertion that returns empty on failure. Used in inline map-value expressions. */
  private String safeString(String expr) {
    return "func() string { if v, ok := " + expr + ".(string); ok { return v }; return \"\" }()";
  }

  /**
   * Return the Go type that json.Unmarshal produces for this proto type. Numbers are float64, bools
   * are bool, strings are string.
   */
  private String goAssertType(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_DOUBLE,
          TYPE_FLOAT,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32,
          TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64 ->
          "float64";
      case TYPE_BOOL -> "bool";
      case TYPE_STRING -> "string";
      default -> "interface{}";
    };
  }

  /** Check if this proto type is a 64-bit integer type. */
  private boolean isInt64Type(FieldDescriptorProto.Type type) {
    return ProtoTypeUtil.isInt64Type(type);
  }

  /**
   * Emit safe scalar deserialization using the ok-pattern type assertion. For non-int64 scalar
   * fields in the top-level emitScalarDeserialize flow.
   */
  private void emitSafeScalarAssert(
      CodeWriter w,
      FieldDescriptorProto.Type protoType,
      String elemExpr,
      String goField,
      boolean isPointer) {
    String goType = typeMapper.scalarType(protoType);
    String assertType = goAssertType(protoType);
    w.block(
        "if v, ok := " + elemExpr + ".(" + assertType + "); ok",
        () -> {
          if (isPointer) {
            if (assertType.equals(goType)) {
              w.line("tmp := v");
            } else {
              w.line("tmp := %s(v)", goType);
            }
            w.line("%s = &tmp", goField);
          } else {
            if (assertType.equals(goType)) {
              w.line("%s = v", goField);
            } else {
              w.line("%s = %s(v)", goField, goType);
            }
          }
        });
  }

  /**
   * Emit safe int64/uint64 deserialization. First tries to parse from string (for precision), then
   * falls back to float64 assertion.
   */
  private void emitSafeInt64Deserialize(
      CodeWriter w, ProtoField field, String goField, String elemExpr, boolean isPointer) {
    String goType = typeMapper.scalarType(field.getProtoType());
    boolean isUnsigned =
        field.getProtoType() == FieldDescriptorProto.Type.TYPE_UINT64
            || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FIXED64;
    w.block(
        "if s, ok := " + elemExpr + ".(string); ok",
        () -> {
          if (isUnsigned) {
            w.block(
                "if parsed, err := strconv.ParseUint(s, 10, 64); err == nil",
                () -> {
                  if (isPointer) {
                    w.line("tmp := parsed");
                    w.line("%s = &tmp", goField);
                  } else {
                    w.line("%s = parsed", goField);
                  }
                });
          } else {
            w.block(
                "if parsed, err := strconv.ParseInt(s, 10, 64); err == nil",
                () -> {
                  if (isPointer) {
                    w.line("tmp := parsed");
                    w.line("%s = &tmp", goField);
                  } else {
                    w.line("%s = parsed", goField);
                  }
                });
          }
        });
    w.raw(" ");
    w.block(
        "else if v, ok := " + elemExpr + ".(float64); ok",
        () -> {
          if (isPointer) {
            w.line("tmp := %s(v)", goType);
            w.line("%s = &tmp", goField);
          } else {
            w.line("%s = %s(v)", goField, goType);
          }
        });
  }

  private String mapValueDeserializeExpr(ProtoField field, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      // This is a simplified inline; complex deserialization would need error handling
      String msgType = typeMapper.simpleTypeName(field.getMapValueTypeReference());
      return "func() *"
          + msgType
          + " { if "
          + valueExpr
          + " == nil { return nil }; "
          + "if subArr, ok := "
          + valueExpr
          + ".([]any); ok { "
          + "sub, _ := Deserialize"
          + msgType
          + "(subArr); return sub }; return nil }()";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = typeMapper.simpleTypeName(field.getMapValueTypeReference());
      return enumType + "(" + scalarCastExpr(FieldDescriptorProto.Type.TYPE_INT32, valueExpr) + ")";
    }
    String goType = typeMapper.scalarType(field.getMapValueType());
    if (goType.equals("[]byte")) {
      return "func() []byte { if s, ok := "
          + valueExpr
          + ".(string); ok { "
          + "d, _ := base64.StdEncoding.DecodeString(s); return d }; return nil }()";
    }
    // int64/uint64 map values: serializer emits strings for precision, so parse from
    // string first and fall back to float64 for interop (matches scalar field handling)
    if (isInt64Type(field.getMapValueType())) {
      boolean isUnsigned =
          field.getMapValueType() == FieldDescriptorProto.Type.TYPE_UINT64
              || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_FIXED64;
      if (isUnsigned) {
        return "func() uint64 { if s, ok := "
            + valueExpr
            + ".(string); ok { if p, err := strconv.ParseUint(s, 10, 64); err == nil { return p } }; "
            + "if v, ok := "
            + valueExpr
            + ".(float64); ok { return uint64(v) }; return 0 }()";
      } else {
        return "func() int64 { if s, ok := "
            + valueExpr
            + ".(string); ok { if p, err := strconv.ParseInt(s, 10, 64); err == nil { return p } }; "
            + "if v, ok := "
            + valueExpr
            + ".(float64); ok { return int64(v) }; return 0 }()";
      }
    }
    String castExpr = scalarCastExpr(field.getMapValueType(), valueExpr);
    return goType + "(" + castExpr + ")";
  }
}
