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
package dev.protocgen.textcodecs.jsonarray.codegen.zig;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the serialize() method body for Zig structs. Produces a json.Value (json array) with
 * fields at positions determined by field number.
 */
public class ZigSerializerGenerator {

  private final ZigTypeMapper typeMapper;
  private final ZigNameResolver nameResolver;

  public ZigSerializerGenerator(ZigTypeMapper typeMapper, ZigNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "pub fn serializeToValue(self: *const "
            + structName
            + ", allocator: std.mem.Allocator) !json.Value",
        () -> {
          int maxPos = message.getMaxFieldNumber();
          w.line("var arr = try allocator.alloc(json.Value, %d);", maxPos);

          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              w.line("arr[%d] = .null; // gap (no field number %d)", pos, pos + 1);
            } else {
              emitFieldSerialize(w, field, pos);
            }
          }

          w.line("return json.Value{ .array = json.Array.fromOwnedSlice(allocator, arr) };");
        });

    // Public serialize method: returns []u8
    w.blankLine();
    w.block(
        "pub fn serialize(self: *const " + structName + ", allocator: std.mem.Allocator) ![]u8",
        () -> {
          w.line("const value = try self.serializeToValue(allocator);");
          w.line("var string = std.ArrayList(u8).init(allocator);");
          w.line("defer string.deinit();");
          w.line("try value.jsonStringify(.{}, string.writer());");
          w.line("return try string.toOwnedSlice();");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field, int pos) {
    String zigField = "self." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      emitOneofSerialize(w, field, pos, zigField);
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, pos, zigField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, pos, zigField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, pos, zigField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, pos, zigField);
    } else {
      emitScalarSerialize(w, field, pos, zigField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, int pos, String zigField) {
    if (field.isProto3Optional()) {
      w.block(
          "if (" + zigField + ") |val|",
          () -> {
            w.line("arr[%d] = %s;", pos, scalarToJson("val", field.getProtoType()));
          });
      w.block(
          "else",
          () -> {
            w.line("arr[%d] = .null;", pos);
          });
      return;
    }
    switch (field.getProtoType()) {
      case TYPE_BYTES:
        w.block(
            "",
            () -> {
              w.line("const b64_src = %s;", zigField);
              w.line("const b64_size = std.base64.standard.Encoder.calcSize(b64_src.len);");
              w.line("const b64_dest = try allocator.alloc(u8, b64_size);");
              w.line("_ = std.base64.standard.Encoder.encode(b64_dest, b64_src);");
              w.line("arr[%d] = json.Value{ .string = b64_dest };", pos);
            });
        break;
      default:
        w.line("arr[%d] = %s;", pos, scalarToJson(zigField, field.getProtoType()));
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, int pos, String zigField) {
    if (field.isProto3Optional()) {
      w.block(
          "if (" + zigField + ") |val|",
          () -> {
            w.line("arr[%d] = json.Value{ .integer = @intFromEnum(val) };", pos);
          });
      w.block(
          "else",
          () -> {
            w.line("arr[%d] = .null;", pos);
          });
    } else {
      w.line("arr[%d] = json.Value{ .integer = @intFromEnum(%s) };", pos, zigField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, int pos, String zigField) {
    w.block(
        "if (" + zigField + ") |val|",
        () -> {
          w.line("arr[%d] = try val.serializeToValue(allocator);", pos);
        });
    w.block(
        "else",
        () -> {
          w.line("arr[%d] = .null;", pos);
        });
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, int pos, String zigField) {
    w.block(
        "",
        () -> {
          w.line("var list_arr = try allocator.alloc(json.Value, %s.items.len);", zigField);
          w.block(
              "for (" + zigField + ".items, 0..) |item, idx|",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.line("list_arr[idx] = try item.serializeToValue(allocator);");
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("list_arr[idx] = json.Value{ .integer = @intFromEnum(item) };");
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.block(
                      "",
                      () -> {
                        w.line("const b64_src = item;");
                        w.line(
                            "const b64_size = std.base64.standard.Encoder.calcSize(b64_src.len);");
                        w.line("const b64_dest = try allocator.alloc(u8, b64_size);");
                        w.line("_ = std.base64.standard.Encoder.encode(b64_dest, b64_src);");
                        w.line("list_arr[idx] = json.Value{ .string = b64_dest };");
                      });
                } else {
                  w.line("list_arr[idx] = %s;", scalarToJson("item", field.getProtoType()));
                }
              });
          w.line(
              "arr[%d] = json.Value{ .array = json.Array.fromOwnedSlice(allocator, list_arr) };",
              pos);
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, int pos, String zigField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "",
        () -> {
          if (stringKey) {
            w.line("var obj = json.ObjectMap.init(allocator);");
            w.block(
                "var it = " + zigField + ".iterator(); while (it.next()) |entry|",
                () -> {
                  String valueExpr = mapValueToJson(field, "entry.value_ptr.*");
                  w.line("try obj.put(entry.key_ptr.*, %s);", valueExpr);
                });
            w.line("arr[%d] = json.Value{ .object = obj };", pos);
          } else {
            w.line("var pairs = try allocator.alloc(json.Value, %s.count());", zigField);
            w.line("var pair_idx: usize = 0;");
            w.block(
                "var it = " + zigField + ".iterator(); while (it.next()) |entry| : (pair_idx += 1)",
                () -> {
                  w.line("var pair = try allocator.alloc(json.Value, 2);");
                  w.line("pair[0] = %s;", scalarToJson("entry.key_ptr.*", field.getMapKeyType()));
                  w.line("pair[1] = %s;", mapValueToJson(field, "entry.value_ptr.*"));
                  w.line(
                      "pairs[pair_idx] = json.Value{ .array = json.Array.fromOwnedSlice(allocator, pair) };");
                });
            w.line(
                "arr[%d] = json.Value{ .array = json.Array.fromOwnedSlice(allocator, pairs) };",
                pos);
          }
        });
  }

  private void emitOneofSerialize(CodeWriter w, ProtoField field, int pos, String zigField) {
    String unionField = "self." + nameResolver.fieldName(field.getOneofName());
    String tagName = nameResolver.fieldName(field.getName());
    w.block(
        "if (" + unionField + ") |oneof_val|",
        () -> {
          w.block(
              "switch (oneof_val)",
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.line(
                      ".%s => |msg| { arr[%d] = try msg.serializeToValue(allocator); },",
                      tagName, pos);
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line(
                      ".%s => |val| { arr[%d] = json.Value{ .integer = @intFromEnum(val) }; },",
                      tagName, pos);
                } else {
                  w.line(
                      ".%s => |val| { arr[%d] = %s; },",
                      tagName, pos, scalarToJson("val", field.getProtoType()));
                }
                w.line("else => { arr[%d] = .null; },", pos);
              });
        });
    w.block(
        "else",
        () -> {
          w.line("arr[%d] = .null;", pos);
        });
  }

  /** Convert a Zig scalar expression to a json.Value expression. */
  private String scalarToJson(String expr, FieldDescriptorProto.Type protoType) {
    return switch (protoType) {
      case TYPE_DOUBLE, TYPE_FLOAT ->
          "if (std.math.isNan(@as(f64, "
              + expr
              + ")) or std.math.isInf(@as(f64, "
              + expr
              + "))) .null else json.Value{ .float = @as(f64, "
              + expr
              + ") }";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 ->
          "json.Value{ .integer = @as(i64, @intCast(" + expr + ")) }";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          "json.Value{ .string = try std.fmt.allocPrint(allocator, \"{d}\", .{" + expr + "}) }";
      case TYPE_UINT32, TYPE_FIXED32 -> "json.Value{ .integer = @as(i64, @intCast(" + expr + ")) }";
      case TYPE_UINT64, TYPE_FIXED64 ->
          "json.Value{ .string = try std.fmt.allocPrint(allocator, \"{d}\", .{" + expr + "}) }";
      case TYPE_BOOL -> "json.Value{ .bool = " + expr + " }";
      case TYPE_STRING -> "json.Value{ .string = " + expr + " }";
      case TYPE_BYTES ->
          "blk: { const src = "
              + expr
              + "; const size = std.base64.standard.Encoder.calcSize(src.len); const dest = try allocator.alloc(u8, size); _ = std.base64.standard.Encoder.encode(dest, src); break :blk json.Value{ .string = dest }; }";
      default -> "json.Value{ .string = " + expr + " }";
    };
  }

  /** Convert a map value expression to a json.Value expression. */
  private String mapValueToJson(ProtoField field, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      return "try " + valueExpr + ".serializeToValue(allocator)";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return "json.Value{ .integer = @intFromEnum(" + valueExpr + ") }";
    }
    return scalarToJson(valueExpr, field.getMapValueType());
  }
}
