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
package dev.protocgen.textcodecs.jsonarray.codegen.perl;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the deserialize() class method for Perl classes. Reads fields positionally from a Perl
 * array ref.
 */
public class PerlDeserializerGenerator {

  private final PerlTypeMapper typeMapper;
  private final PerlNameResolver nameResolver;

  public PerlDeserializerGenerator(PerlTypeMapper typeMapper, PerlNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    w.blankLine();
    w.line("sub deserialize {");
    w.indent();
    w.line("my ($class, $data) = @_;");

    // Emit cross-file imports (require statements) lazily inside the method body
    // to avoid circular import issues between mutually-referencing messages (BUG-IO-01)
    for (String imp : lazyImports) {
      w.line("%s  # lazy import to avoid circular dependency", imp);
    }

    w.line("my $obj = $class->new();");
    w.line("my $size = scalar @{$data};");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("# position %d: gap (no field)", pos);
        continue;
      }
      emitFieldDeserialize(w, field, pos);
    }

    w.line("return $obj;");
    w.dedent();
    w.line("}");

    // Convenience: decode from JSON string
    w.blankLine();
    w.line("sub decode {");
    w.indent();
    w.line("my ($class, $data) = @_;");
    w.line("return $class->deserialize(decode_json($data));");
    w.dedent();
    w.line("}");
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String plField = "$obj->{" + nameResolver.fieldName(field.getName()) + "}";

    w.line("if ($size > %d && defined($data->[%d])) {", pos, pos);
    w.indent();

    String elemExpr = "$data->[" + pos + "]";

    if (field.isMap()) {
      emitMapDeserialize(w, field, plField, elemExpr);
    } else if (field.isRepeated()) {
      emitRepeatedDeserialize(w, field, plField, elemExpr);
    } else if (field.isWellKnownType()) {
      emitMessageDeserialize(w, field, plField, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageDeserialize(w, field, plField, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumDeserialize(w, field, plField, elemExpr);
    } else {
      emitScalarDeserialize(w, field, plField, elemExpr);
    }

    if (field.isProto3Optional()) {
      w.line("$obj->{_present_fields}[%d] = 1;", pos);
    }

    if (field.isOneofMember()) {
      String caseName = "$obj->{_" + nameResolver.fieldName(field.getOneofName()) + "_case}";
      w.line("%s = %d;", caseName, field.getFieldNumber());
    }

    w.dedent();
    // Apply schema-specified default for proto2 fields when absent/null
    if (field.getDefaultValue() != null && !field.isRepeated() && !field.isMap()) {
      w.line("} else {");
      w.indent();
      String defaultExpr = schemaDefaultExpression(field, field.getDefaultValue());
      w.line("%s = %s;", plField, defaultExpr);
      w.dedent();
    }
    w.line("}");
  }

  private String schemaDefaultExpression(ProtoField field, String defaultValue) {
    if (defaultValue == null || defaultValue.isEmpty()) {
      return typeMapper.defaultValue(field);
    }
    if (field.getKind() == ProtoField.FieldKind.ENUM) {
      // Perl deserializes enums as int ordinals; cannot resolve enum name to number at codegen time
      return typeMapper.defaultValue(field);
    }
    return typeMapper.formatSchemaDefault(field.getProtoType(), defaultValue);
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String plField, String elemExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), elemExpr);
    w.line("%s = %s;", plField, readExpr);
  }

  private void emitEnumDeserialize(
      CodeWriter w, ProtoField field, String plField, String elemExpr) {
    w.line("%s = int(%s);", plField, elemExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String plField, String elemExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s = %s->deserialize(%s);", plField, msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String plField, String elemExpr) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line(
          "%s = [map { defined($_) ? %s->deserialize($_) : undef } @{%s}];",
          plField, msgType, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("%s = [map { int($_) } @{%s}];", plField, elemExpr);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("%s = [map { decode_base64($_) } @{%s}];", plField, elemExpr);
    } else {
      String readExpr = scalarListReadExpr(field.getProtoType());
      if (readExpr != null) {
        w.line("%s = [map { %s } @{%s}];", plField, readExpr, elemExpr);
      } else {
        w.line("%s = [@{%s}];", plField, elemExpr);
      }
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String plField, String elemExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    if (stringKey) {
      // String-keyed maps are deserialized from a hash ref
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line(
            "%s = {map { $_ => (defined(%s->{$_}) ? %s->deserialize(%s->{$_}) : undef) } keys %%{%s}};",
            plField, elemExpr, msgType, elemExpr, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("%s = {map { $_ => int(%s->{$_}) } keys %%{%s}};", plField, elemExpr, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line(
            "%s = {map { $_ => decode_base64(%s->{$_}) } keys %%{%s}};",
            plField, elemExpr, elemExpr);
      } else {
        w.line("%s = {%%{%s}};", plField, elemExpr);
      }
    } else {
      // Non-string-keyed maps are deserialized from a list of [k, v] pairs
      String keyRead = mapKeyReadExpr(field.getMapKeyType(), "$_->[0]");
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line(
            "%s = {map { %s => (defined($_->[1]) ? %s->deserialize($_->[1]) : undef) } @{%s}};",
            plField, keyRead, msgType, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("%s = {map { %s => int($_->[1]) } @{%s}};", plField, keyRead, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line("%s = {map { %s => decode_base64($_->[1]) } @{%s}};", plField, keyRead, elemExpr);
      } else {
        String valRead = mapValueReadExpr(field.getMapValueType(), "$_->[1]");
        w.line("%s = {map { %s => %s } @{%s}};", plField, keyRead, valRead, elemExpr);
      }
    }
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String expr) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> expr + " + 0.0";
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
          "int(" + expr + ")";
      case TYPE_BOOL -> expr + " ? 1 : 0";
      case TYPE_STRING -> "\"\" . " + expr;
      case TYPE_BYTES -> "decode_base64(" + expr + ")";
      default -> expr;
    };
  }

  /**
   * Read expression for scalar elements in a map { } block (uses "$_" as variable). Returns null if
   * simple copy is sufficient.
   */
  private String scalarListReadExpr(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "$_ + 0.0";
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
          "int($_)";
      case TYPE_BOOL -> "$_ ? 1 : 0";
      case TYPE_STRING -> "\"\" . $_";
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
          "int(" + expr + ")";
      case TYPE_BOOL -> expr + " ? 1 : 0";
      default -> expr;
    };
  }

  private String mapValueReadExpr(FieldDescriptorProto.Type type, String expr) {
    return scalarReadExpr(type, expr);
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "HASH";
  }
}
