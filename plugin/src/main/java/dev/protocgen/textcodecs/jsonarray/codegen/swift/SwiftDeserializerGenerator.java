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
package dev.protocgen.textcodecs.jsonarray.codegen.swift;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the deserialize static method for Swift structs. Reads fields positionally from an
 * [Any?] array. JSONSerialization produces NSNumber (Double) for all JSON numbers.
 */
public class SwiftDeserializerGenerator {

  private final SwiftTypeMapper typeMapper;
  private final SwiftNameResolver nameResolver;

  public SwiftDeserializerGenerator(SwiftTypeMapper typeMapper, SwiftNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String structName) {
    // deserialize static method: returns StructName
    w.blankLine();
    w.block(
        "public static func deserialize(_ arr: [Any?]) -> " + structName,
        () -> {
          w.line("var obj = %s()", structName);
          w.line("let size = arr.count");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("// position %d: gap (no field)", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos, structName);
          }

          w.line("return obj");
        });

    // fromJsonString convenience static method
    w.blankLine();
    w.block(
        "public static func fromJsonString(_ s: String) throws -> " + structName,
        () -> {
          w.line("guard let data = s.data(using: .utf8) else {");
          w.indent();
          w.line(
              "throw NSError(domain: \"%s\", code: -1, userInfo: "
                  + "[NSLocalizedDescriptionKey: \"Invalid UTF-8 string\"])",
              structName);
          w.dedent();
          w.line("}");
          w.line(
              "guard let arr = try JSONSerialization.jsonObject(with: data, options: []) as? [Any]"
                  + " else {");
          w.indent();
          w.line(
              "throw NSError(domain: \"%s\", code: -1, userInfo: "
                  + "[NSLocalizedDescriptionKey: \"Expected JSON array\"])",
              structName);
          w.dedent();
          w.line("}");
          w.line("return deserialize(arr.map { $0 is NSNull ? nil : $0 })");
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos, String structName) {
    String swiftField = "obj." + nameResolver.fieldName(field.getName());

    w.block(
        "if size > " + pos + ", let elem = arr[" + pos + "]",
        () -> {
          if (field.isMap()) {
            emitMapDeserialize(w, field, swiftField, "elem");
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, swiftField, "elem");
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            emitMessageDeserialize(w, field, swiftField, "elem");
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, swiftField, "elem");
          } else {
            emitScalarDeserialize(w, field, swiftField, "elem");
          }

          if (field.isOneofMember()) {
            String caseField =
                "obj." + SwiftNameResolver.snakeToCamel(field.getOneofName()) + "Case";
            w.line("%s = %d", caseField, field.getFieldNumber());
          }
        });
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String swiftField, String elemExpr) {
    FieldDescriptorProto.Type protoType = field.getProtoType();

    if (field.isProto3Optional()) {
      emitOptionalScalarDeserialize(w, protoType, swiftField, elemExpr);
      return;
    }

    switch (protoType) {
      case TYPE_BYTES:
        w.block(
            "if let s = " + elemExpr + " as? String, let decoded = Data(base64Encoded: s)",
            () -> {
              w.line("%s = decoded", swiftField);
            });
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
        emitInt64Deserialize(w, swiftField, elemExpr, "Int64", false);
        break;
      case TYPE_UINT64:
      case TYPE_FIXED64:
        emitInt64Deserialize(w, swiftField, elemExpr, "UInt64", false);
        break;
      default:
        emitSimpleScalarDeserialize(w, protoType, swiftField, elemExpr);
        break;
    }
  }

  private void emitOptionalScalarDeserialize(
      CodeWriter w, FieldDescriptorProto.Type protoType, String swiftField, String elemExpr) {
    switch (protoType) {
      case TYPE_BYTES:
        w.block(
            "if let s = " + elemExpr + " as? String, let decoded = Data(base64Encoded: s)",
            () -> {
              w.line("%s = decoded", swiftField);
            });
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
        emitInt64Deserialize(w, swiftField, elemExpr, "Int64", true);
        break;
      case TYPE_UINT64:
      case TYPE_FIXED64:
        emitInt64Deserialize(w, swiftField, elemExpr, "UInt64", true);
        break;
      default:
        emitSimpleScalarDeserialize(w, protoType, swiftField, elemExpr);
        break;
    }
  }

  private void emitSimpleScalarDeserialize(
      CodeWriter w, FieldDescriptorProto.Type protoType, String swiftField, String elemExpr) {
    String swiftType = typeMapper.scalarType(protoType);
    String castType = swiftAssertType(protoType);

    if (castType.equals(swiftType)) {
      w.block(
          "if let v = " + elemExpr + " as? " + castType,
          () -> {
            w.line("%s = v", swiftField);
          });
    } else {
      w.block(
          "if let v = " + elemExpr + " as? " + castType,
          () -> {
            w.line("%s = %s(v)", swiftField, swiftType);
          });
    }
  }

  /**
   * Emit int64/uint64 deserialization. First tries to parse from String (for precision), then falls
   * back to Double.
   */
  private void emitInt64Deserialize(
      CodeWriter w, String swiftField, String elemExpr, String swiftType, boolean isOptional) {
    w.block(
        "if let s = " + elemExpr + " as? String, let parsed = " + swiftType + "(s)",
        () -> {
          w.line("%s = parsed", swiftField);
        });
    w.block(
        "else if let v = " + elemExpr + " as? Double",
        () -> {
          w.line("%s = %s(v)", swiftField, swiftType);
        });
  }

  private void emitEnumDeserialize(
      CodeWriter w, ProtoField field, String swiftField, String elemExpr) {
    String enumType = typeMapper.simpleTypeName(field.getTypeReference());
    w.block(
        "if let v = " + elemExpr + " as? Double, let e = " + enumType + "(rawValue: Int32(v))",
        () -> {
          w.line("%s = e", swiftField);
        });
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String swiftField, String elemExpr) {
    String msgType = typeMapper.simpleTypeName(field.getTypeReference());
    w.block(
        "if let subArr = " + elemExpr + " as? [Any?]",
        () -> {
          w.line("%s = %s.deserialize(subArr)", swiftField, msgType);
        });
    w.block(
        "else if let subArr = " + elemExpr + " as? [Any]",
        () -> {
          w.line(
              "%s = %s.deserialize(subArr.map { $0 is NSNull ? nil : $0 })", swiftField, msgType);
        });
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String swiftField, String elemExpr) {
    w.block(
        "if let listRaw = " + elemExpr + " as? [Any]",
        () -> {
          String elemType = typeMapper.elementType(field);
          w.line("var list: [%s] = []", elemType);
          w.block(
              "for item in listRaw",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  String msgType = typeMapper.simpleTypeName(field.getTypeReference());
                  w.block(
                      "if let subArr = item as? [Any?]",
                      () -> {
                        w.line("list.append(%s.deserialize(subArr))", msgType);
                      });
                  w.block(
                      "else if let subArr = item as? [Any]",
                      () -> {
                        w.line(
                            "list.append(%s.deserialize(subArr.map { $0 is NSNull ? nil : $0 }))",
                            msgType);
                      });
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  String enumType = typeMapper.simpleTypeName(field.getTypeReference());
                  w.block(
                      "if let v = item as? Double, let e = " + enumType + "(rawValue: Int32(v))",
                      () -> {
                        w.line("list.append(e)");
                      });
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.block(
                      "if let s = item as? String, let decoded = Data(base64Encoded: s)",
                      () -> {
                        w.line("list.append(decoded)");
                      });
                } else if (isInt64Type(field.getProtoType())) {
                  String swiftType = typeMapper.scalarType(field.getProtoType());
                  w.block(
                      "if let s = item as? String, let parsed = " + swiftType + "(s)",
                      () -> {
                        w.line("list.append(parsed)");
                      });
                  w.block(
                      "else if let v = item as? Double",
                      () -> {
                        w.line("list.append(%s(v))", swiftType);
                      });
                } else {
                  String swiftType = typeMapper.scalarType(field.getProtoType());
                  String castType = swiftAssertType(field.getProtoType());
                  if (castType.equals(swiftType)) {
                    w.block(
                        "if let v = item as? " + castType,
                        () -> {
                          w.line("list.append(v)");
                        });
                  } else {
                    w.block(
                        "if let v = item as? " + castType,
                        () -> {
                          w.line("list.append(%s(v))", swiftType);
                        });
                  }
                }
              });
          w.line("%s = list", swiftField);
        });
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String swiftField, String elemExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    if (stringKey) {
      // JSON object: [String: Any] from JSONSerialization
      w.block(
          "if let mapRaw = " + elemExpr + " as? [String: Any]",
          () -> {
            String valType = mapValueSwiftType(field);
            w.line("var m: [String: %s] = [:]", valType);
            w.block(
                "for (k, v) in mapRaw",
                () -> {
                  emitMapValueDeserialize(w, field, "m[k]", "v");
                });
            w.line("%s = m", swiftField);
          });
    } else {
      // Array of [key, value] pairs
      w.block(
          "if let pairsRaw = " + elemExpr + " as? [[Any]]",
          () -> {
            String keyType = typeMapper.scalarType(field.getMapKeyType());
            String valType = mapValueSwiftType(field);
            w.line("var m: [%s: %s] = [:]", keyType, valType);
            w.block(
                "for pair in pairsRaw",
                () -> {
                  w.block(
                      "if pair.count == 2",
                      () -> {
                        emitMapKeyDeserialize(w, field.getMapKeyType(), "pair[0]");
                        emitMapValueDeserialize(w, field, "m[mapKey]", "pair[1]");
                      });
                });
            w.line("%s = m", swiftField);
          });
    }
  }

  private void emitMapKeyDeserialize(CodeWriter w, FieldDescriptorProto.Type keyType, String expr) {
    String swiftType = typeMapper.scalarType(keyType);
    String castType = swiftAssertType(keyType);
    if (castType.equals(swiftType)) {
      w.line("let mapKey = (%s as? %s) ?? %s", expr, castType, scalarZeroLiteral(keyType));
    } else {
      w.line(
          "let mapKey = %s((%s as? %s) ?? %s)",
          swiftType, expr, castType, castZeroLiteral(keyType));
    }
  }

  private void emitMapValueDeserialize(
      CodeWriter w, ProtoField field, String targetExpr, String valueExpr) {
    FieldDescriptorProto.Type valType = field.getMapValueType();

    if (valType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = typeMapper.simpleTypeName(field.getMapValueTypeReference());
      w.block(
          "if let subArr = " + valueExpr + " as? [Any?]",
          () -> {
            w.line("%s = %s.deserialize(subArr)", targetExpr, msgType);
          });
      w.block(
          "else if let subArr = " + valueExpr + " as? [Any]",
          () -> {
            w.line(
                "%s = %s.deserialize(subArr.map { $0 is NSNull ? nil : $0 })", targetExpr, msgType);
          });
    } else if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      String enumType = typeMapper.simpleTypeName(field.getMapValueTypeReference());
      w.block(
          "if let n = " + valueExpr + " as? Double, let e = " + enumType + "(rawValue: Int32(n))",
          () -> {
            w.line("%s = e", targetExpr);
          });
    } else {
      String swiftType = typeMapper.scalarType(valType);
      String castType = swiftAssertType(valType);
      if (castType.equals(swiftType)) {
        w.block(
            "if let v = " + valueExpr + " as? " + castType,
            () -> {
              w.line("%s = v", targetExpr);
            });
      } else {
        w.block(
            "if let v = " + valueExpr + " as? " + castType,
            () -> {
              w.line("%s = %s(v)", targetExpr, swiftType);
            });
      }
    }
  }

  private String mapValueSwiftType(ProtoField field) {
    FieldDescriptorProto.Type valType = field.getMapValueType();
    if (valType == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      return typeMapper.simpleTypeName(field.getMapValueTypeReference());
    }
    if (valType == FieldDescriptorProto.Type.TYPE_ENUM) {
      return typeMapper.simpleTypeName(field.getMapValueTypeReference());
    }
    return typeMapper.scalarType(valType);
  }

  /**
   * Return the Swift type that JSONSerialization produces for this proto type. Numbers are Double,
   * bools are Bool, strings are String.
   */
  private String swiftAssertType(FieldDescriptorProto.Type type) {
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
          "Double";
      case TYPE_BOOL -> "Bool";
      case TYPE_STRING -> "String";
      default -> "Any";
    };
  }

  /** Check if this proto type is a 64-bit integer type. */
  private boolean isInt64Type(FieldDescriptorProto.Type type) {
    return ProtoTypeUtil.isInt64Type(type);
  }

  /** Return the Swift zero-value literal for a scalar type. */
  private String scalarZeroLiteral(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      default -> "0";
    };
  }

  /** Return the zero literal for the cast type (Double for numbers, Bool for bools). */
  private String castZeroLiteral(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_BOOL -> "false";
      case TYPE_STRING -> "\"\"";
      default -> "0.0";
    };
  }
}
