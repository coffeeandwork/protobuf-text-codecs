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
package dev.protocgen.textcodecs.jsonarray.codegen.php;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the serialize() method body for PHP classes. Produces a PHP array with fields at
 * positions determined by field number.
 */
public class PhpSerializerGenerator {

  private final PhpTypeMapper typeMapper;
  private final PhpNameResolver nameResolver;

  public PhpSerializerGenerator(PhpTypeMapper typeMapper, PhpNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, Set<String> lazyImports) {
    // serialize(): array
    w.blankLine();
    w.line("/**");
    w.line(" * Serialize this message to a positional array.");
    w.line(" *");
    w.line(" * @return array<int, mixed>");
    w.line(" */");
    w.line("public function serialize(): array");
    w.line("{");
    w.indent();
    w.line("$result = [];");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("$result[] = null; // gap (no field number %d)", pos + 1);
      } else {
        emitFieldSerialize(w, field);
      }
    }

    w.line("return $result;");
    w.dedent();
    w.line("}");

    // serializeToString convenience
    w.blankLine();
    w.line("/**");
    w.line(" * Serialize this message to a string.");
    w.line(" */");
    w.line("public function serializeToString(): string");
    w.line("{");
    w.indent();
    w.line("return json_encode($this->serialize(), JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);");
    w.dedent();
    w.line("}");
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String phpField = "$this->" + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = "$this->" + nameResolver.fieldName(field.getOneofName()) + "Case";
      String caseConst = String.valueOf(field.getFieldNumber());
      w.line("if (%s === %s) {", caseName, caseConst);
      w.indent();
      emitValueForField(w, field, phpField);
      w.dedent();
      w.line("} else {");
      w.indent();
      w.line("$result[] = null;");
      w.dedent();
      w.line("}");
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, phpField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, phpField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, phpField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, phpField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, phpField);
    } else {
      emitScalarSerialize(w, field, phpField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String phpField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("$result[] = %s !== null ? %s->serialize() : null;", phpField, phpField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("$result[] = %s;", phpField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("$result[] = base64_encode(%s);", phpField);
    } else if (isFloatOrDouble(field.getProtoType())) {
      w.line(
          "$result[] = (is_nan(%s) || is_infinite(%s)) ? null : %s;", phpField, phpField, phpField);
    } else {
      w.line("$result[] = %s;", phpField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String phpField) {
    if (field.isProto3Optional()) {
      String bitCheck = "$this->presentFields[" + field.getArrayPosition() + "]";
      w.line("if (%s) {", bitCheck);
      w.indent();
      emitScalarAppend(w, field, phpField);
      w.dedent();
      w.line("} else {");
      w.indent();
      w.line("$result[] = null;");
      w.dedent();
      w.line("}");
      return;
    }
    emitScalarAppend(w, field, phpField);
  }

  private void emitScalarAppend(CodeWriter w, ProtoField field, String phpField) {
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("$result[] = base64_encode(%s);", phpField);
    } else if (isFloatOrDouble(field.getProtoType())) {
      w.line(
          "$result[] = (is_nan(%s) || is_infinite(%s)) ? null : %s;", phpField, phpField, phpField);
    } else {
      w.line("$result[] = %s;", phpField);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String phpField) {
    if (field.isProto3Optional()) {
      String bitCheck = "$this->presentFields[" + field.getArrayPosition() + "]";
      w.line("if (%s) {", bitCheck);
      w.indent();
      w.line("$result[] = %s;", phpField);
      w.dedent();
      w.line("} else {");
      w.indent();
      w.line("$result[] = null;");
      w.dedent();
      w.line("}");
    } else {
      w.line("$result[] = %s;", phpField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String phpField) {
    w.line("$result[] = %s !== null ? %s->serialize() : null;", phpField, phpField);
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String phpField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("$arr = [];");
      w.line("foreach (%s as $item) {", phpField);
      w.indent();
      w.line("$arr[] = $item !== null ? $item->serialize() : null;");
      w.dedent();
      w.line("}");
      w.line("$result[] = $arr;");
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("$result[] = array_values(%s);", phpField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("$result[] = array_map('base64_encode', %s);", phpField);
    } else {
      w.line("$result[] = array_values(%s);", phpField);
    }
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String phpField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    if (stringKey) {
      // String-keyed maps serialize as an object (associative array)
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line("$mapArr = [];");
        w.line("foreach (%s as $k => $v) {", phpField);
        w.indent();
        w.line("$mapArr[$k] = $v !== null ? $v->serialize() : null;");
        w.dedent();
        w.line("}");
        w.line("$result[] = (object) $mapArr;");
      } else {
        w.line("$result[] = (object) %s;", phpField);
      }
    } else {
      // Non-string-keyed maps serialize as list of [k, v] pairs
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line("$pairs = [];");
        w.line("foreach (%s as $k => $v) {", phpField);
        w.indent();
        w.line("$pairs[] = [$k, $v !== null ? $v->serialize() : null];");
        w.dedent();
        w.line("}");
        w.line("$result[] = $pairs;");
      } else {
        w.line("$pairs = [];");
        w.line("foreach (%s as $k => $v) {", phpField);
        w.indent();
        w.line("$pairs[] = [$k, $v];");
        w.dedent();
        w.line("}");
        w.line("$result[] = $pairs;");
      }
    }
  }

  private boolean isFloatOrDouble(FieldDescriptorProto.Type type) {
    return ProtoTypeUtil.isFloatOrDoubleType(type);
  }
}
