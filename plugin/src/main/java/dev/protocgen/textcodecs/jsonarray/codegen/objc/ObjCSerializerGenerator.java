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
package dev.protocgen.textcodecs.jsonarray.codegen.objc;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize method body for Objective-C code. Produces an NSArray with fields at
 * positions determined by field number, using NSJSONSerialization for JSON output.
 */
public class ObjCSerializerGenerator {

  private final ObjCTypeMapper typeMapper;
  private final ObjCNameResolver nameResolver;

  public ObjCSerializerGenerator(ObjCTypeMapper typeMapper, ObjCNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  /** Generate the serialize method declarations for the header file. */
  public void generateDeclarations(CodeWriter w, String className) {
    w.line("- (NSArray *)toJsonArray;");
    w.line("- (NSData *)data;");
  }

  /** Generate the serialize method implementations for the .m file. */
  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // toJsonArray
    w.blankLine();
    w.block(
        "- (NSArray *)toJsonArray",
        () -> {
          w.line("NSMutableArray *array = [NSMutableArray array];");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("[array addObject:[NSNull null]]; /* gap (no field number %d) */", pos + 1);
            } else {
              emitFieldSerialize(w, field);
            }
          }

          w.line("return [array copy];");
        });

    // data - returns NSData *
    w.blankLine();
    w.block(
        "- (NSData *)data",
        () -> {
          w.line("NSArray *array = [self toJsonArray];");
          w.line("return [NSJSONSerialization dataWithJSONObject:array options:0 error:nil];");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String propName = nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String oneofProp = nameResolver.fieldName(field.getOneofName());
      String caseConst =
          nameResolver.fieldName(field.getOneofName())
              + "Case"
              + capitalize(nameResolver.fieldName(field.getName()));
      w.block(
          "if (self." + oneofProp + "Case == " + className(field) + caseConst + ")",
          () -> {
            emitValueAdd(w, field, "self." + oneofProp + "Value");
          });
      w.block(
          "else",
          () -> {
            w.line("[array addObject:[NSNull null]];");
          });
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, propName);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, propName);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, propName);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, propName);
    } else {
      emitScalarSerialize(w, field, propName);
    }
  }

  private void emitValueAdd(CodeWriter w, ProtoField field, String accessor) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line(
          "[array addObject:(%s != nil) ? [%s toJsonArray] : [NSNull null]];", accessor, accessor);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("[array addObject:@(%s)];", accessor);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("[array addObject:(%s != nil) ? %s : [NSNull null]];", accessor, accessor);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line(
          "[array addObject:(%s != nil) ? [%s base64EncodedStringWithOptions:0] : [NSNull null]];",
          accessor, accessor);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("[array addObject:@(%s)];", accessor);
    } else if (typeMapper.isInt64Type(field.getProtoType())) {
      emitInt64Add(w, field.getProtoType(), accessor);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
      emitFloatAdd(w, field.getProtoType(), accessor);
    } else {
      w.line("[array addObject:@(%s)];", accessor);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String propName) {
    String accessor = "self." + propName;

    if (field.isProto3Optional()) {
      String hasProp = "self.has" + capitalize(propName);
      w.block(
          "if (" + hasProp + ")",
          () -> {
            emitScalarAdd(w, field, accessor);
          });
      w.block(
          "else",
          () -> {
            w.line("[array addObject:[NSNull null]];");
          });
      return;
    }

    switch (field.getProtoType()) {
      case TYPE_STRING:
        w.line("[array addObject:(%s != nil) ? %s : @\"\"];", accessor, accessor);
        break;
      case TYPE_BYTES:
        w.line(
            "[array addObject:(%s != nil) ? [%s base64EncodedStringWithOptions:0] : @\"\"];",
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
        w.line("[array addObject:@(%s)];", accessor);
        break;
      case TYPE_DOUBLE:
      case TYPE_FLOAT:
        emitFloatAdd(w, field.getProtoType(), accessor);
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
      case TYPE_UINT64:
      case TYPE_FIXED64:
        emitInt64Add(w, field.getProtoType(), accessor);
        break;
      case TYPE_STRING:
        w.line("[array addObject:(%s != nil) ? %s : [NSNull null]];", accessor, accessor);
        break;
      case TYPE_BYTES:
        w.line(
            "[array addObject:(%s != nil) ? [%s base64EncodedStringWithOptions:0] : [NSNull null]];",
            accessor, accessor);
        break;
      default:
        w.line("[array addObject:@(%s)];", accessor);
        break;
    }
  }

  private void emitFloatAdd(CodeWriter w, FieldDescriptorProto.Type type, String accessor) {
    // NaN/Infinity -> NSNull
    w.line("if (isnan(%s) || isinf(%s)) {", accessor, accessor);
    w.indent();
    w.line("[array addObject:[NSNull null]];");
    w.dedent();
    w.line("} else {");
    w.indent();
    w.line("[array addObject:@(%s)];", accessor);
    w.dedent();
    w.line("}");
  }

  private void emitInt64Add(CodeWriter w, FieldDescriptorProto.Type type, String accessor) {
    // int64/uint64 as JSON strings to prevent precision loss
    if (typeMapper.isSignedInt64(type)) {
      w.line("[array addObject:[NSString stringWithFormat:@\"%%lld\", (long long)%s]];", accessor);
    } else {
      w.line(
          "[array addObject:[NSString stringWithFormat:@\"%%llu\", (unsigned long long)%s]];",
          accessor);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String propName) {
    String accessor = "self." + propName;
    if (field.isProto3Optional()) {
      String hasProp = "self.has" + capitalize(propName);
      w.block(
          "if (" + hasProp + ")",
          () -> {
            w.line("[array addObject:@(%s)];", accessor);
          });
      w.block(
          "else",
          () -> {
            w.line("[array addObject:[NSNull null]];");
          });
    } else {
      w.line("[array addObject:@(%s)];", accessor);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String propName) {
    String accessor = "self." + propName;
    w.block(
        "if (" + accessor + " != nil)",
        () -> {
          w.line("[array addObject:[%s toJsonArray]];", accessor);
        });
    w.block(
        "else",
        () -> {
          w.line("[array addObject:[NSNull null]];");
        });
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String propName) {
    String accessor = "self." + propName;
    w.block(
        "",
        () -> {
          w.line("NSMutableArray *listNode = [NSMutableArray array];");
          w.block(
              "for (id __elem in " + accessor + ")",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  String refType =
                      nameResolver.resolveTypeReference(field.getTypeReference(), null);
                  w.line(
                      "[listNode addObject:(__elem != nil && __elem != (id)[NSNull null]) ? [(%s *)__elem toJsonArray] : [NSNull null]];",
                      refType);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("[listNode addObject:__elem];");
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.line(
                      "[listNode addObject:(__elem != nil) ? [(NSData *)__elem base64EncodedStringWithOptions:0] : [NSNull null]];");
                } else if (typeMapper.isInt64Type(field.getProtoType())) {
                  if (typeMapper.isSignedInt64(field.getProtoType())) {
                    w.line(
                        "[listNode addObject:[NSString stringWithFormat:@\"%%lld\", [(NSNumber *)__elem longLongValue]]];");
                  } else {
                    w.line(
                        "[listNode addObject:[NSString stringWithFormat:@\"%%llu\", [(NSNumber *)__elem unsignedLongLongValue]]];");
                  }
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE
                    || field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT) {
                  w.line("double __val = [(NSNumber *)__elem doubleValue];");
                  w.line("if (isnan(__val) || isinf(__val)) {");
                  w.indent();
                  w.line("[listNode addObject:[NSNull null]];");
                  w.dedent();
                  w.line("} else {");
                  w.indent();
                  w.line("[listNode addObject:__elem];");
                  w.dedent();
                  w.line("}");
                } else {
                  w.line("[listNode addObject:(__elem != nil) ? __elem : [NSNull null]];");
                }
              });
          w.line("[array addObject:listNode];");
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String propName) {
    String accessor = "self." + propName;
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    w.block(
        "",
        () -> {
          if (stringKey) {
            // String-keyed maps -> JSON object (NSDictionary)
            w.line("NSMutableDictionary *mapNode = [NSMutableDictionary dictionary];");
            w.block(
                "for (NSString *__key in " + accessor + ")",
                () -> {
                  w.line("id __val = %s[__key];", accessor);
                  emitMapValuePut(w, field, "__key", "__val");
                });
            w.line("[array addObject:mapNode];");
          } else {
            // Non-string-keyed maps -> JSON array of [key, value] pairs
            w.line("NSMutableArray *mapNode = [NSMutableArray array];");
            w.block(
                "for (id __key in " + accessor + ")",
                () -> {
                  w.line("id __val = %s[__key];", accessor);
                  w.line("NSArray *pair = @[__key, %s];", mapValueExpr(field, "__val"));
                  w.line("[mapNode addObject:pair];");
                });
            w.line("[array addObject:mapNode];");
          }
        });
  }

  private void emitMapValuePut(CodeWriter w, ProtoField field, String keyExpr, String valExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String refType = nameResolver.resolveTypeReference(field.getMapValueTypeReference(), null);
      w.line(
          "mapNode[%s] = (%s != nil && %s != (id)[NSNull null]) ? [(%s *)%s toJsonArray] : [NSNull null];",
          keyExpr, valExpr, valExpr, refType, valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("mapNode[%s] = %s;", keyExpr, valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line("mapNode[%s] = (%s != nil) ? %s : [NSNull null];", keyExpr, valExpr, valExpr);
    } else {
      w.line("mapNode[%s] = (%s != nil) ? %s : [NSNull null];", keyExpr, valExpr, valExpr);
    }
  }

  private String mapValueExpr(ProtoField field, String valExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String refType = nameResolver.resolveTypeReference(field.getMapValueTypeReference(), null);
      return "("
          + valExpr
          + " != nil) ? [("
          + refType
          + " *)"
          + valExpr
          + " toJsonArray] : [NSNull null]";
    }
    return "(" + valExpr + " != nil) ? " + valExpr + " : [NSNull null]";
  }

  /**
   * Placeholder for extracting class name context -- not needed since we don't use enum-based oneof
   * in ObjC.
   */
  private String className(ProtoField field) {
    return "";
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
