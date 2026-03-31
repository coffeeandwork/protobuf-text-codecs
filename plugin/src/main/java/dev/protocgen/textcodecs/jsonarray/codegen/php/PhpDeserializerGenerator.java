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
package dev.protocgen.textcodecs.jsonarray.codegen.php;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the static deserialize() method for PHP classes. Reads fields positionally from a PHP
 * array.
 */
public class PhpDeserializerGenerator {

  private final PhpTypeMapper typeMapper;
  private final PhpNameResolver nameResolver;

  public PhpDeserializerGenerator(PhpTypeMapper typeMapper, PhpNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    // deserialize(array $data): self
    w.blankLine();
    w.line("/**");
    w.line(" * Deserialize a positional array into a %s instance.", className);
    w.line(" *");
    w.line(" * @param array<int, mixed> $data");
    w.line(" * @return self");
    w.line(" */");
    w.line("public static function deserialize(array $data): self");
    w.line("{");
    w.indent();
    w.line("$obj = new self();");
    w.line("$size = count($data);");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("// position %d: gap (no field)", pos);
        continue;
      }
      emitFieldDeserialize(w, field, pos);
    }

    w.line("return $obj;");
    w.dedent();
    w.line("}");

    // fromJsonString convenience
    w.blankLine();
    w.line("/**");
    w.line(" * Deserialize from a JSON string.");
    w.line(" */");
    w.line("public static function fromJsonString(string $jsonStr): self");
    w.line("{");
    w.indent();
    w.line("$data = json_decode($jsonStr, true, 512, JSON_THROW_ON_ERROR);");
    w.line("return self::deserialize($data);");
    w.dedent();
    w.line("}");

    // fromJsonBytes convenience
    w.blankLine();
    w.line("/**");
    w.line(" * Deserialize from JSON-encoded bytes.");
    w.line(" */");
    w.line("public static function fromJsonBytes(string $data): self");
    w.line("{");
    w.indent();
    w.line("return self::fromJsonString($data);");
    w.dedent();
    w.line("}");
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String phpField = "$obj->" + nameResolver.fieldName(field.getName());

    w.line("if ($size > %d && $data[%d] !== null) {", pos, pos);
    w.indent();

    String elemExpr = "$data[" + pos + "]";

    if (field.isMap()) {
      emitMapDeserialize(w, field, phpField, elemExpr);
    } else if (field.isRepeated()) {
      emitRepeatedDeserialize(w, field, phpField, elemExpr);
    } else if (field.isWellKnownType()) {
      emitMessageDeserialize(w, field, phpField, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageDeserialize(w, field, phpField, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumDeserialize(w, field, phpField, elemExpr);
    } else {
      emitScalarDeserialize(w, field, phpField, elemExpr);
    }

    if (field.isProto3Optional()) {
      w.line("$obj->presentFields[%d] = true;", pos);
    }

    if (field.isOneofMember()) {
      String caseName = "$obj->" + nameResolver.fieldName(field.getOneofName()) + "Case";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }

    w.dedent();
    w.line("}");
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String phpField, String elemExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), elemExpr);
    w.line("%s = %s;", phpField, readExpr);
  }

  private void emitEnumDeserialize(
      CodeWriter w, ProtoField field, String phpField, String elemExpr) {
    w.line("%s = (int) %s;", phpField, elemExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String phpField, String elemExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s = %s::deserialize(%s);", phpField, msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String phpField, String elemExpr) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line("%s = [];", phpField);
      w.line("foreach (%s as $elem) {", elemExpr);
      w.indent();
      w.line("%s[] = $elem !== null ? %s::deserialize($elem) : null;", phpField, msgType);
      w.dedent();
      w.line("}");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("%s = array_map(fn($e) => (int) $e, %s);", phpField, elemExpr);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("%s = array_map('base64_decode', %s);", phpField, elemExpr);
    } else {
      String castFn = scalarArrayCastFn(field.getProtoType());
      if (castFn != null) {
        w.line("%s = array_map(%s, %s);", phpField, castFn, elemExpr);
      } else {
        w.line("%s = array_values(%s);", phpField, elemExpr);
      }
    }
  }

  private void emitMapDeserialize(
      CodeWriter w, ProtoField field, String phpField, String elemExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    if (stringKey) {
      // String-keyed maps are deserialized from an object/assoc array
      w.line("$raw = (array) %s;", elemExpr);
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line("%s = [];", phpField);
        w.line("foreach ($raw as $k => $v) {");
        w.indent();
        w.line("%s[$k] = $v !== null ? %s::deserialize($v) : null;", phpField, msgType);
        w.dedent();
        w.line("}");
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("%s = [];", phpField);
        w.line("foreach ($raw as $k => $v) {");
        w.indent();
        w.line("%s[$k] = (int) $v;", phpField);
        w.dedent();
        w.line("}");
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line("%s = [];", phpField);
        w.line("foreach ($raw as $k => $v) {");
        w.indent();
        w.line("%s[$k] = base64_decode($v);", phpField);
        w.dedent();
        w.line("}");
      } else {
        w.line("%s = $raw;", phpField);
      }
    } else {
      // Non-string-keyed maps are deserialized from a list of [k, v] pairs
      w.line("%s = [];", phpField);
      w.line("foreach (%s as $pair) {", elemExpr);
      w.indent();
      String keyRead = mapKeyReadExpr(field.getMapKeyType(), "$pair[0]");
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line(
            "%s[%s] = $pair[1] !== null ? %s::deserialize($pair[1]) : null;",
            phpField, keyRead, msgType);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("%s[%s] = (int) $pair[1];", phpField, keyRead);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line("%s[%s] = base64_decode($pair[1]);", phpField, keyRead);
      } else {
        String valRead = scalarReadExpr(field.getMapValueType(), "$pair[1]");
        w.line("%s[%s] = %s;", phpField, keyRead, valRead);
      }
      w.dedent();
      w.line("}");
    }
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String expr) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "(float) " + expr;
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
          "(int) " + expr;
      case TYPE_BOOL -> "(bool) " + expr;
      case TYPE_STRING -> "(string) " + expr;
      case TYPE_BYTES -> "base64_decode(" + expr + ")";
      default -> expr;
    };
  }

  /**
   * Returns a PHP callable string for array_map when casting repeated scalar elements. Returns null
   * if no cast is needed (simple pass-through).
   */
  private String scalarArrayCastFn(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "fn($e) => (float) $e";
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
          "fn($e) => (int) $e";
      case TYPE_BOOL -> "fn($e) => (bool) $e";
      case TYPE_STRING -> "fn($e) => (string) $e";
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
          "(int) " + expr;
      case TYPE_BOOL -> "(bool) " + expr;
      default -> expr;
    };
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "mixed";
  }
}
