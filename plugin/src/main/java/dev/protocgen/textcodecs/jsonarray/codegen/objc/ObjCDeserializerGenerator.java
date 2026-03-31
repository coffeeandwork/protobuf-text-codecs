/*
 * Copyright 2026 protobuf-text-codecs contributors
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
 * Generates the deserialize method body for Objective-C code. Reads fields positionally from an
 * NSArray parsed via NSJSONSerialization.
 */
public class ObjCDeserializerGenerator {

  private final ObjCTypeMapper typeMapper;
  private final ObjCNameResolver nameResolver;

  public ObjCDeserializerGenerator(ObjCTypeMapper typeMapper, ObjCNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  /** Generate deserialize method declarations for the header file. */
  public void generateDeclarations(CodeWriter w, String className) {
    w.line("+ (instancetype)fromJsonArray:(NSArray *)array;");
    w.line("+ (instancetype)fromJsonString:(NSString *)jsonString;");
  }

  /** Generate the deserialize method implementations for the .m file. */
  public void generate(CodeWriter w, ProtoMessage message, String className) {
    // fromJsonArray:
    w.blankLine();
    w.block(
        "+ (instancetype)fromJsonArray:(NSArray *)array",
        () -> {
          w.line("if (!array || ![array isKindOfClass:[NSArray class]]) return nil;");
          w.line("%s *msg = [[%s alloc] init];", className, className);
          w.line("NSUInteger count = array.count;");

          int maxPos = message.getMaxFieldNumber();
          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("/* position %d: gap (no field) */", pos);
              continue;
            }
            emitFieldDeserialize(w, field, pos, className);
          }

          w.line("return msg;");
        });

    // fromJsonString:
    w.blankLine();
    w.block(
        "+ (instancetype)fromJsonString:(NSString *)jsonString",
        () -> {
          w.line("if (!jsonString) return nil;");
          w.line("NSData *data = [jsonString dataUsingEncoding:NSUTF8StringEncoding];");
          w.line("if (!data) return nil;");
          w.line(
              "NSArray *array = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];");
          w.line("if (![array isKindOfClass:[NSArray class]]) return nil;");
          w.line("return [%s fromJsonArray:array];", className);
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos, String className) {
    String propName = nameResolver.fieldName(field.getName());

    w.block(
        "if (count > " + pos + ")",
        () -> {
          w.line("id item = array[%d];", pos);

          if (field.isOneofMember()) {
            emitOneofDeserialize(w, field, propName, className);
            return;
          }

          w.block(
              "if (item && item != (id)[NSNull null])",
              () -> {
                if (field.isMap()) {
                  emitMapDeserialize(w, field, propName);
                } else if (field.isRepeated()) {
                  emitRepeatedDeserialize(w, field, propName);
                } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  emitMessageDeserialize(w, field, propName);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  emitEnumDeserialize(w, field, propName);
                } else {
                  emitScalarDeserialize(w, field, propName);
                }

                if (field.isProto3Optional()) {
                  w.line("msg.has%s = YES;", capitalize(propName));
                }
              });
        });
  }

  private void emitOneofDeserialize(
      CodeWriter w, ProtoField field, String propName, String className) {
    String oneofProp = nameResolver.fieldName(field.getOneofName());
    w.block(
        "if (item && item != (id)[NSNull null])",
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String refType = nameResolver.resolveTypeReference(field.getTypeReference(), null);
            w.line("msg.%s = [%s fromJsonArray:item];", propName, refType);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line("msg.%s = [(NSNumber *)item integerValue];", propName);
          } else {
            emitScalarDeserialize(w, field, propName);
          }
        });
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field, String propName) {
    switch (field.getProtoType()) {
      case TYPE_STRING:
        w.block(
            "if ([item isKindOfClass:[NSString class]])",
            () -> {
              w.line("msg.%s = (NSString *)item;", propName);
            });
        break;
      case TYPE_BYTES:
        w.block(
            "if ([item isKindOfClass:[NSString class]])",
            () -> {
              w.line(
                  "msg.%s = [[NSData alloc] initWithBase64EncodedString:(NSString *)item options:0];",
                  propName);
            });
        break;
      case TYPE_BOOL:
        w.line("msg.%s = [(NSNumber *)item boolValue];", propName);
        break;
      case TYPE_DOUBLE:
        w.line("msg.%s = [(NSNumber *)item doubleValue];", propName);
        break;
      case TYPE_FLOAT:
        w.line("msg.%s = [(NSNumber *)item floatValue];", propName);
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
        // int64 may be encoded as string to prevent precision loss
        w.line(
            "msg.%s = [item isKindOfClass:[NSString class]] ? [(NSString *)item longLongValue] : [(NSNumber *)item longLongValue];",
            propName);
        break;
      case TYPE_UINT64:
      case TYPE_FIXED64:
        w.line(
            "msg.%s = [item isKindOfClass:[NSString class]] ? (uint64_t)strtoull([(NSString *)item UTF8String], NULL, 10) : [(NSNumber *)item unsignedLongLongValue];",
            propName);
        break;
      case TYPE_INT32:
      case TYPE_SINT32:
      case TYPE_SFIXED32:
        w.line("msg.%s = [(NSNumber *)item intValue];", propName);
        break;
      case TYPE_UINT32:
      case TYPE_FIXED32:
        w.line("msg.%s = [(NSNumber *)item unsignedIntValue];", propName);
        break;
      default:
        w.line("msg.%s = [(NSNumber *)item intValue];", propName);
        break;
    }
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String propName) {
    w.line("msg.%s = [(NSNumber *)item integerValue];", propName);
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field, String propName) {
    String refType = nameResolver.resolveTypeReference(field.getTypeReference(), null);
    w.block(
        "if ([item isKindOfClass:[NSArray class]])",
        () -> {
          w.line("msg.%s = [%s fromJsonArray:(NSArray *)item];", propName, refType);
        });
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field, String propName) {
    w.block(
        "if ([item isKindOfClass:[NSArray class]])",
        () -> {
          w.line("NSArray *listArr = (NSArray *)item;");
          w.line("NSMutableArray *result = [NSMutableArray arrayWithCapacity:listArr.count];");
          w.block(
              "for (id elem in listArr)",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  String refType =
                      nameResolver.resolveTypeReference(field.getTypeReference(), null);
                  w.line(
                      "[result addObject:(elem != nil && elem != (id)[NSNull null] && [elem isKindOfClass:[NSArray class]]) ? [%s fromJsonArray:(NSArray *)elem] : [NSNull null]];",
                      refType);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("[result addObject:elem];");
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_STRING) {
                  w.line(
                      "[result addObject:(elem != nil && elem != (id)[NSNull null]) ? elem : [NSNull null]];");
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.line(
                      "[result addObject:(elem != nil && [elem isKindOfClass:[NSString class]]) ? [[NSData alloc] initWithBase64EncodedString:(NSString *)elem options:0] : [NSNull null]];");
                } else {
                  w.line(
                      "[result addObject:(elem != nil && elem != (id)[NSNull null]) ? elem : [NSNull null]];");
                }
              });
          w.line("msg.%s = result;", propName);
        });
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String propName) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    if (stringKey) {
      w.block(
          "if ([item isKindOfClass:[NSDictionary class]])",
          () -> {
            w.line("NSDictionary *dictItem = (NSDictionary *)item;");
            w.line(
                "NSMutableDictionary *result = [NSMutableDictionary dictionaryWithCapacity:dictItem.count];");
            w.block(
                "for (NSString *key in dictItem)",
                () -> {
                  w.line("id val = dictItem[key];");
                  emitMapValueRead(w, field, "result[key]", "val");
                });
            w.line("msg.%s = result;", propName);
          });
    } else {
      w.block(
          "if ([item isKindOfClass:[NSArray class]])",
          () -> {
            w.line("NSArray *pairsArr = (NSArray *)item;");
            w.line(
                "NSMutableDictionary *result = [NSMutableDictionary dictionaryWithCapacity:pairsArr.count];");
            w.block(
                "for (NSArray *pair in pairsArr)",
                () -> {
                  w.line("if ([pair isKindOfClass:[NSArray class]] && pair.count >= 2) {");
                  w.indent();
                  w.line("id key = pair[0];");
                  w.line("id val = pair[1];");
                  emitMapValueRead(w, field, "result[key]", "val");
                  w.dedent();
                  w.line("}");
                });
            w.line("msg.%s = result;", propName);
          });
    }
  }

  private void emitMapValueRead(CodeWriter w, ProtoField field, String target, String valExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String refType = nameResolver.resolveTypeReference(field.getMapValueTypeReference(), null);
      w.line(
          "%s = (%s != nil && %s != (id)[NSNull null] && [%s isKindOfClass:[NSArray class]]) ? [%s fromJsonArray:(NSArray *)%s] : [NSNull null];",
          target, valExpr, valExpr, valExpr, refType, valExpr);
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_STRING) {
      w.line(
          "%s = (%s != nil && %s != (id)[NSNull null]) ? %s : [NSNull null];",
          target, valExpr, valExpr, valExpr);
    } else {
      w.line(
          "%s = (%s != nil && %s != (id)[NSNull null]) ? %s : [NSNull null];",
          target, valExpr, valExpr, valExpr);
    }
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
