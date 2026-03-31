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
package dev.protocgen.textcodecs.jsonarray.codegen.zig;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the deserialize() function for Zig structs. Reads fields positionally from a json.Value
 * (expected to be a JSON array).
 */
public class ZigDeserializerGenerator {

  private final ZigTypeMapper typeMapper;
  private final ZigNameResolver nameResolver;

  public ZigDeserializerGenerator(ZigTypeMapper typeMapper, ZigNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String structName) {
    w.blankLine();
    w.block(
        "pub fn deserialize(value: json.Value, allocator: std.mem.Allocator) !" + structName,
        () -> {
          w.line("const arr = value.array.items;");
          w.line("const size = arr.len;");
          w.line("var obj: %s = undefined;", structName);

          // Initialize collections and optionals to defaults
          emitFieldDefaults(w, message);
          w.blankLine();

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

    // fromJsonString convenience function
    w.blankLine();
    w.block(
        "pub fn fromJsonString(input: []const u8, allocator: std.mem.Allocator) !" + structName,
        () -> {
          w.line("const parsed = try std.json.parseFromSlice(json.Value, allocator, input, .{});");
          w.line("defer parsed.deinit();");
          w.line("return try deserialize(parsed.value, allocator);");
        });
  }

  private void emitFieldDefaults(CodeWriter w, ProtoMessage message) {
    java.util.Set<String> initializedOneofs = new java.util.HashSet<>();
    for (ProtoField field : message.getFields()) {
      String zigName = nameResolver.fieldName(field.getName());
      String defaultVal = typeMapper.defaultValue(field);

      if (field.isOneofMember()) {
        // Oneofs are handled separately; initialize the union field to null (once per group)
        String oneofFieldName = nameResolver.fieldName(field.getOneofName());
        if (initializedOneofs.add(oneofFieldName)) {
          w.line("obj.%s = null;", oneofFieldName);
        }
        continue;
      }
      w.line("obj.%s = %s;", zigName, defaultVal);
    }
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String zigField = "obj." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      emitOneofDeserialize(w, field, pos);
      return;
    }

    w.block(
        "if (size > " + pos + " and arr[" + pos + "] != .null)",
        () -> {
          String elemExpr = "arr[" + pos + "]";

          if (field.isMap()) {
            emitMapDeserialize(w, field, zigField, elemExpr);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, zigField, elemExpr);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            emitMessageDeserialize(w, field, zigField, elemExpr);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, zigField, elemExpr);
          } else {
            emitScalarDeserialize(w, field, zigField, elemExpr);
          }
        });
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String zigField, String elemExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), elemExpr);
    if (field.isProto3Optional()) {
      w.line("%s = %s;", zigField, readExpr);
    } else {
      w.line("%s = %s;", zigField, readExpr);
    }
  }

  private void emitEnumDeserialize(
      CodeWriter w, ProtoField field, String zigField, String elemExpr) {
    w.line("%s = @enumFromInt(@as(i64, %s.integer));", zigField, elemExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String zigField, String elemExpr) {
    String msgType = ZigNameResolver.simpleTypeName(field.getTypeReference());
    w.line("%s = try %s.deserialize(%s, allocator);", zigField, msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String zigField, String elemExpr) {
    String elementType = typeMapper.elementZigType(field);
    w.line("const list_items = %s.array.items;", elemExpr);
    w.block(
        "for (list_items) |elem|",
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String msgType = ZigNameResolver.simpleTypeName(field.getTypeReference());
            w.line("try %s.append(try %s.deserialize(elem, allocator));", zigField, msgType);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line("try %s.append(@enumFromInt(@as(i64, elem.integer)));", zigField);
          } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
            w.line("const src = elem.string;");
            w.line("const size = std.base64.standard.Decoder.calcSizeForSlice(src.len) catch 0;");
            w.line("const decoded = try allocator.alloc(u8, size);");
            w.line("std.base64.standard.Decoder.decode(decoded, src) catch {};");
            w.line("try %s.append(decoded);", zigField);
          } else {
            String readExpr = scalarReadExpr(field.getProtoType(), "elem");
            w.line("try %s.append(%s);", zigField, readExpr);
          }
        });
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String zigField, String elemExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    if (stringKey) {
      w.line("const obj_map = %s.object;", elemExpr);
      w.block(
          "var it = obj_map.iterator(); while (it.next()) |entry|",
          () -> {
            String valueRead = mapValueReadExpr(field, "entry.value_ptr.*");
            w.line("try %s.put(try allocator.dupe(u8, entry.key_ptr.*), %s);", zigField, valueRead);
          });
    } else {
      w.line("const pairs = %s.array.items;", elemExpr);
      w.block(
          "for (pairs) |pair|",
          () -> {
            w.line("const pair_items = pair.array.items;");
            String keyRead = scalarReadExpr(field.getMapKeyType(), "pair_items[0]");
            String valueRead = mapValueReadExpr(field, "pair_items[1]");
            w.line("try %s.put(%s, %s);", zigField, keyRead, valueRead);
          });
    }
  }

  private void emitOneofDeserialize(CodeWriter w, ProtoField field, int pos) {
    String unionField = "obj." + nameResolver.fieldName(field.getOneofName());
    String tagName = nameResolver.fieldName(field.getName());
    String unionType = ZigNameResolver.simpleTypeName(field.getOneofName());

    w.block(
        "if (size > " + pos + " and arr[" + pos + "] != .null)",
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String msgType = ZigNameResolver.simpleTypeName(field.getTypeReference());
            w.line(
                "%s = .{ .%s = try %s.deserialize(arr[%d], allocator) };",
                unionField, tagName, msgType, pos);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line(
                "%s = .{ .%s = @enumFromInt(@as(i64, arr[%d].integer)) };",
                unionField, tagName, pos);
          } else {
            String readExpr = scalarReadExpr(field.getProtoType(), "arr[" + pos + "]");
            w.line("%s = .{ .%s = %s };", unionField, tagName, readExpr);
          }
        });
  }

  /** Expression to read a scalar value from a json.Value. */
  private String scalarReadExpr(FieldDescriptorProto.Type type, String elemExpr) {
    return switch (type) {
      case TYPE_DOUBLE -> elemExpr + ".float";
      case TYPE_FLOAT -> "@as(f32, @floatCast(" + elemExpr + ".float))";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64 ->
          "try std.fmt.parseInt(i64, " + elemExpr + ".string, 10)";
      case TYPE_UINT64, TYPE_FIXED64 -> "try std.fmt.parseInt(u64, " + elemExpr + ".string, 10)";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32 -> "@as(i32, @intCast(" + elemExpr + ".integer))";
      case TYPE_UINT32, TYPE_FIXED32 -> "@as(u32, @intCast(" + elemExpr + ".integer))";
      case TYPE_BOOL -> elemExpr + ".bool";
      case TYPE_STRING -> "try allocator.dupe(u8, " + elemExpr + ".string)";
      case TYPE_BYTES ->
          "blk: { const src = "
              + elemExpr
              + ".string; const size = std.base64.standard.Decoder.calcSizeForSlice(src.len) catch 0; const dest = try allocator.alloc(u8, size); std.base64.standard.Decoder.decode(dest, src) catch {}; break :blk dest; }";
      default -> "try allocator.dupe(u8, " + elemExpr + ".string)";
    };
  }

  private String mapValueReadExpr(ProtoField field, String elemExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = ZigNameResolver.simpleTypeName(field.getMapValueTypeReference());
      return "try " + msgType + ".deserialize(" + elemExpr + ", allocator)";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return "@enumFromInt(@as(i64, " + elemExpr + ".integer))";
    }
    return scalarReadExpr(field.getMapValueType(), elemExpr);
  }
}
