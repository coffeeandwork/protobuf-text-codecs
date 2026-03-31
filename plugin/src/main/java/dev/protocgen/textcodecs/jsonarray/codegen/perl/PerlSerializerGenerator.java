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
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the serialize() method body for Perl classes. Produces a Perl array ref with fields at
 * positions determined by field number.
 */
public class PerlSerializerGenerator {

  private final PerlTypeMapper typeMapper;
  private final PerlNameResolver nameResolver;

  public PerlSerializerGenerator(PerlTypeMapper typeMapper, PerlNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, Set<String> lazyImports) {
    // serialize method
    w.blankLine();
    w.line("sub serialize {");
    w.indent();
    w.line("my ($self) = @_;");
    w.line("my @result;");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("push @result, undef;  # gap (no field number %d)", pos + 1);
      } else {
        emitFieldSerialize(w, field);
      }
    }

    w.line("return \\@result;");
    w.dedent();
    w.line("}");

    // to_json_string convenience
    w.blankLine();
    w.line("sub to_json_string {");
    w.indent();
    w.line("my ($self) = @_;");
    w.line("return encode_json($self->serialize());");
    w.dedent();
    w.line("}");

    // to_json_bytes convenience (same as to_json_string in Perl, bytes are strings)
    w.blankLine();
    w.line("sub to_json_bytes {");
    w.indent();
    w.line("my ($self) = @_;");
    w.line("return $self->to_json_string();");
    w.dedent();
    w.line("}");
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String plField = "$self->{" + nameResolver.fieldName(field.getName()) + "}";

    if (field.isOneofMember()) {
      String caseName = "$self->{_" + nameResolver.fieldName(field.getOneofName()) + "_case}";
      String caseConst = String.valueOf(field.getFieldNumber());
      w.line("if (%s == %s) {", caseName, caseConst);
      w.indent();
      emitValueForField(w, field, plField);
      w.dedent();
      w.line("} else {");
      w.indent();
      w.line("push @result, undef;");
      w.dedent();
      w.line("}");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, plField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, plField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, plField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, plField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, plField);
    } else {
      emitScalarSerialize(w, field, plField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String plField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("push @result, defined(%s) ? %s->serialize() : undef;", plField, plField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("push @result, %s;", plField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("push @result, encode_base64(%s, \"\");", plField);
    } else {
      w.line("push @result, %s;", plField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String plField) {
    if (field.isProto3Optional()) {
      String bitCheck = "$self->{_present_fields}[" + field.getArrayPosition() + "]";
      w.line("if (%s) {", bitCheck);
      w.indent();
      emitScalarAppend(w, field, plField);
      w.dedent();
      w.line("} else {");
      w.indent();
      w.line("push @result, undef;");
      w.dedent();
      w.line("}");
      return;
    }
    emitScalarAppend(w, field, plField);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String plField) {
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("push @result, encode_base64(%s, \"\");", plField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("push @result, %s ? JSON::true : JSON::false;", plField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      w.line(
          "push @result, (%s != %s || %s == 9**9**9 || %s == -(9**9**9)) ? undef : %s;",
          plField, plField, plField, plField, plField);
    } else {
      w.line("push @result, %s;", plField);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String plField) {
    if (field.isProto3Optional()) {
      String bitCheck = "$self->{_present_fields}[" + field.getArrayPosition() + "]";
      w.line("if (%s) {", bitCheck);
      w.indent();
      w.line("push @result, %s;", plField);
      w.dedent();
      w.line("} else {");
      w.indent();
      w.line("push @result, undef;");
      w.dedent();
      w.line("}");
    } else {
      w.line("push @result, %s;", plField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String plField) {
    w.line("push @result, defined(%s) ? %s->serialize() : undef;", plField, plField);
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String plField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("push @result, [map { defined($_) ? $_->serialize() : undef } @{%s}];", plField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("push @result, [@{%s}];", plField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("push @result, [map { encode_base64($_, \"\") } @{%s}];", plField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BOOL) {
      w.line("push @result, [map { $_ ? JSON::true : JSON::false } @{%s}];", plField);
    } else {
      w.line("push @result, [@{%s}];", plField);
    }
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String plField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    if (stringKey) {
      // String-keyed maps serialize as a hash ref
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line(
            "push @result, {map { $_ => (defined(%s->{$_}) ? %s->{$_}->serialize() : undef) } keys %%{%s}};",
            plField, plField, plField);
      } else {
        w.line("push @result, {%%{%s}};", plField);
      }
    } else {
      // Non-string-keyed maps serialize as list of [k, v] pairs
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line(
            "push @result, [map { [$_, defined(%s->{$_}) ? %s->{$_}->serialize() : undef] } keys %%{%s}];",
            plField, plField, plField);
      } else {
        w.line("push @result, [map { [$_, %s->{$_}] } keys %%{%s}];", plField, plField);
      }
    }
  }
}
