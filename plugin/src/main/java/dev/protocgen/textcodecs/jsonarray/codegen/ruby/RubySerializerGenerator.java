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
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the serialize method body for Ruby classes. Produces a Ruby array with fields at
 * positions determined by field number.
 */
public class RubySerializerGenerator {

  private final RubyTypeMapper typeMapper;
  private final RubyNameResolver nameResolver;

  public RubySerializerGenerator(RubyTypeMapper typeMapper, RubyNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, Set<String> lazyImports) {
    w.blankLine();
    w.line("def serialize");
    w.indent();
    w.line("result = []");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("result << nil # gap (no field number %d)", pos + 1);
      } else {
        emitFieldSerialize(w, field);
      }
    }

    w.line("result");
    w.dedent();
    w.line("end");

    // to_json_string convenience
    w.blankLine();
    w.line("def to_json_string");
    w.indent();
    w.line("JSON.generate(serialize)");
    w.dedent();
    w.line("end");

    // to_json_bytes convenience
    w.blankLine();
    w.line("def to_json_bytes");
    w.indent();
    w.line("to_json_string.encode('UTF-8')");
    w.dedent();
    w.line("end");
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String rbField = "@" + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = "@" + nameResolver.fieldName(field.getOneofName()) + "_case";
      String caseConst = String.valueOf(field.getFieldNumber());
      w.line("if %s == %s", caseName, caseConst);
      w.indent();
      emitValueForField(w, field, rbField);
      w.dedent();
      w.line("else");
      w.indent();
      w.line("result << nil");
      w.dedent();
      w.line("end");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, rbField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, rbField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, rbField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, rbField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, rbField);
    } else {
      emitScalarSerialize(w, field, rbField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String rbField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("result << (%s.nil? ? nil : %s.serialize)", rbField, rbField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("result << %s", rbField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("result << Base64.strict_encode64(%s)", rbField);
    } else {
      w.line("result << %s", rbField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String rbField) {
    if (field.isProto3Optional()) {
      String bitCheck = "@present_fields[" + field.getArrayPosition() + "]";
      w.line("if %s", bitCheck);
      w.indent();
      emitScalarAppend(w, field, rbField);
      w.dedent();
      w.line("else");
      w.indent();
      w.line("result << nil");
      w.dedent();
      w.line("end");
      return;
    }
    emitScalarAppend(w, field, rbField);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String rbField) {
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("result << Base64.strict_encode64(%s)", rbField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      w.line("result << (%s.nan? || %s.infinite? ? nil : %s)", rbField, rbField, rbField);
    } else {
      w.line("result << %s", rbField);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String rbField) {
    if (field.isProto3Optional()) {
      String bitCheck = "@present_fields[" + field.getArrayPosition() + "]";
      w.line("if %s", bitCheck);
      w.indent();
      w.line("result << %s", rbField);
      w.dedent();
      w.line("else");
      w.indent();
      w.line("result << nil");
      w.dedent();
      w.line("end");
    } else {
      w.line("result << %s", rbField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String rbField) {
    w.line("result << (%s.nil? ? nil : %s.serialize)", rbField, rbField);
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String rbField) {
    String itemVar = nameResolver.fieldName(field.getName()) + "_item";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line(
          "result << %s.map { |%s| %s.nil? ? nil : %s.serialize }",
          rbField, itemVar, itemVar, itemVar);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("result << %s.dup", rbField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("result << %s.map { |%s| Base64.strict_encode64(%s) }", rbField, itemVar, itemVar);
    } else {
      w.line("result << %s.dup", rbField);
    }
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String rbField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    if (stringKey) {
      // String-keyed maps serialize as a hash
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line("result << %s.transform_values { |v| v.nil? ? nil : v.serialize }", rbField);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("result << %s.dup", rbField);
      } else {
        w.line("result << %s.dup", rbField);
      }
    } else {
      // Non-string-keyed maps serialize as list of [k, v] pairs
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line("result << %s.map { |k, v| [k, v.nil? ? nil : v.serialize] }", rbField);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("result << %s.map { |k, v| [k, v] }", rbField);
      } else {
        w.line("result << %s.map { |k, v| [k, v] }", rbField);
      }
    }
  }
}
