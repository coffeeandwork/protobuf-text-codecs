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
package dev.protocgen.textcodecs.jsonarray.codegen.ruby;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the self.deserialize class method for Ruby classes. Reads fields positionally from a
 * Ruby array.
 */
public class RubyDeserializerGenerator {

  private final RubyTypeMapper typeMapper;
  private final RubyNameResolver nameResolver;

  public RubyDeserializerGenerator(RubyTypeMapper typeMapper, RubyNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    w.blankLine();
    w.line("def self.deserialize(data)");
    w.indent();
    // Emit cross-file requires lazily inside the method body to avoid circular
    // requires between mutually-referencing messages (BUG-IO-01)
    for (String imp : lazyImports) {
      w.line("%s # lazy require to avoid circular dependency", imp);
    }
    w.line("obj = new");
    w.line("size = data.length");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("# position %d: gap (no field)", pos);
        continue;
      }
      emitFieldDeserialize(w, field, pos);
    }

    w.line("obj");
    w.dedent();
    w.line("end");

    // Convenience: deserialize from JSON string
    w.blankLine();
    w.line("def self.from_json_string(json_str)");
    w.indent();
    w.line("deserialize(JSON.parse(json_str))");
    w.dedent();
    w.line("end");

    // Convenience: deserialize from bytes
    w.blankLine();
    w.line("def self.from_json_bytes(data)");
    w.indent();
    w.line("from_json_string(data.force_encoding('UTF-8'))");
    w.dedent();
    w.line("end");
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    w.line("if size > %d && !data[%d].nil?", pos, pos);
    w.indent();

    String elemExpr = "data[" + pos + "]";

    if (field.isMap()) {
      emitMapDeserialize(w, field, elemExpr);
    } else if (field.isRepeated()) {
      emitRepeatedDeserialize(w, field, elemExpr);
    } else if (field.isWellKnownType()) {
      emitMessageDeserialize(w, field, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageDeserialize(w, field, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumDeserialize(w, field, elemExpr);
    } else {
      emitScalarDeserialize(w, field, elemExpr);
    }

    if (field.isProto3Optional()) {
      w.line("obj.instance_variable_get(:@present_fields)[%d] = true", pos);
    }

    if (field.isOneofMember()) {
      String caseName = nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("obj.instance_variable_set(:@%s, %d)", caseName, field.getFieldNumber());
    }

    w.dedent();
    w.line("end");
  }

  private void emitScalarDeserialize(CodeWriter w, ProtoField field, String elemExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), elemExpr);
    w.line("obj.%s = %s", nameResolver.fieldName(field.getName()), readExpr);
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String elemExpr) {
    w.line("obj.%s = %s.to_i", nameResolver.fieldName(field.getName()), elemExpr);
  }

  private void emitMessageDeserialize(CodeWriter w, ProtoField field, String elemExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line(
        "obj.%s = %s.deserialize(%s)", nameResolver.fieldName(field.getName()), msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(CodeWriter w, ProtoField field, String elemExpr) {
    String fieldNameStr = nameResolver.fieldName(field.getName());
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line(
          "obj.%s = %s.map { |elem| elem.nil? ? nil : %s.deserialize(elem) }",
          fieldNameStr, elemExpr, msgType);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("obj.%s = %s.map { |elem| elem.to_i }", fieldNameStr, elemExpr);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("obj.%s = %s.map { |elem| Base64.strict_decode64(elem) }", fieldNameStr, elemExpr);
    } else {
      String readExpr = scalarListReadExpr(field.getProtoType());
      if (readExpr != null) {
        w.line("obj.%s = %s.map { |elem| %s }", fieldNameStr, elemExpr, readExpr);
      } else {
        w.line("obj.%s = Array(%s)", fieldNameStr, elemExpr);
      }
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String elemExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    String fieldNameStr = nameResolver.fieldName(field.getName());

    if (stringKey) {
      // String-keyed maps are deserialized from a hash
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line(
            "obj.%s = %s.transform_values { |v| v.nil? ? nil : %s.deserialize(v) }",
            fieldNameStr, elemExpr, msgType);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("obj.%s = %s.transform_values { |v| v.to_i }", fieldNameStr, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line(
            "obj.%s = %s.transform_values { |v| Base64.strict_decode64(v) }",
            fieldNameStr, elemExpr);
      } else {
        w.line("obj.%s = Hash[%s]", fieldNameStr, elemExpr);
      }
    } else {
      // Non-string-keyed maps are deserialized from a list of [k, v] pairs
      String keyRead = mapKeyReadExpr(field.getMapKeyType(), "pair[0]");
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line(
            "obj.%s = %s.each_with_object({}) { |pair, h| h[%s] = pair[1].nil? ? nil : %s.deserialize(pair[1]) }",
            fieldNameStr, elemExpr, keyRead, msgType);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line(
            "obj.%s = %s.each_with_object({}) { |pair, h| h[%s] = pair[1].to_i }",
            fieldNameStr, elemExpr, keyRead);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line(
            "obj.%s = %s.each_with_object({}) { |pair, h| h[%s] = Base64.strict_decode64(pair[1]) }",
            fieldNameStr, elemExpr, keyRead);
      } else {
        String valRead = mapValueReadExpr(field.getMapValueType(), "pair[1]");
        w.line(
            "obj.%s = %s.each_with_object({}) { |pair, h| h[%s] = %s }",
            fieldNameStr, elemExpr, keyRead, valRead);
      }
    }
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String expr) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> expr + ".to_f";
      case TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32 ->
          expr + ".to_i";
      case TYPE_BOOL -> "!!(" + expr + ")";
      case TYPE_STRING -> expr + ".to_s";
      case TYPE_BYTES -> "Base64.strict_decode64(" + expr + ")";
      default -> expr;
    };
  }

  /**
   * Read expression for scalar elements in a map/list block (uses "elem" as variable). Returns null
   * if Array() is sufficient (simple pass-through).
   */
  private String scalarListReadExpr(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "elem.to_f";
      case TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32 ->
          "elem.to_i";
      case TYPE_BOOL -> "!!(elem)";
      case TYPE_STRING -> "elem.to_s";
      default -> null;
    };
  }

  private String mapKeyReadExpr(FieldDescriptorProto.Type type, String expr) {
    return switch (type) {
      case TYPE_INT64,
          TYPE_SINT64,
          TYPE_SFIXED64,
          TYPE_UINT64,
          TYPE_FIXED64,
          TYPE_INT32,
          TYPE_SINT32,
          TYPE_SFIXED32,
          TYPE_UINT32,
          TYPE_FIXED32 ->
          expr + ".to_i";
      case TYPE_BOOL -> "!!(" + expr + ")";
      default -> expr;
    };
  }

  private String mapValueReadExpr(FieldDescriptorProto.Type type, String expr) {
    return scalarReadExpr(type, expr);
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "Object";
  }
}
