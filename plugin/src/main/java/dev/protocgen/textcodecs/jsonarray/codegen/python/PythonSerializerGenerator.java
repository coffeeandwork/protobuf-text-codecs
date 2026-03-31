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
package dev.protocgen.textcodecs.jsonarray.codegen.python;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the serialize() method body for Python classes. Produces a Python list with fields at
 * positions determined by field number.
 */
public class PythonSerializerGenerator {

  private final PythonTypeMapper typeMapper;
  private final PythonNameResolver nameResolver;

  public PythonSerializerGenerator(PythonTypeMapper typeMapper, PythonNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, Set<String> lazyImports) {
    w.blankLine();
    w.line("def serialize(self):");
    w.indent();
    w.line("\"\"\"Serialize this message to a positional list.\"\"\"");
    w.line("result = []");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("result.append(None)  # gap (no field number %d)", pos + 1);
      } else {
        emitFieldSerialize(w, field);
      }
    }

    w.line("return result");
    w.dedent();

    // toJsonString convenience
    w.blankLine();
    w.line("def to_json_string(self):");
    w.indent();
    w.line("\"\"\"Serialize this message to a JSON string.\"\"\"");
    w.line("return json.dumps(self.serialize())");
    w.dedent();

    // toJsonBytes convenience
    w.blankLine();
    w.line("def to_json_bytes(self):");
    w.indent();
    w.line("\"\"\"Serialize this message to JSON-encoded bytes.\"\"\"");
    w.line("return self.to_json_string().encode(\"utf-8\")");
    w.dedent();
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field) {
    String pyField = "self._" + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      String caseName = "self._" + nameResolver.fieldName(field.getOneofName()) + "_case";
      String caseConst = String.valueOf(field.getFieldNumber());
      w.line("if %s == %s:", caseName, caseConst);
      w.indent();
      emitValueForField(w, field, pyField);
      w.dedent();
      w.line("el" + "se:");
      w.indent();
      w.line("result.append(None)");
      w.dedent();
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, pyField);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, pyField);
    } else if (field.isWellKnownType()) {
      emitMessageSerialize(w, field, pyField);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageSerialize(w, field, pyField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, pyField);
    } else {
      emitScalarSerialize(w, field, pyField);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String pyField) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line("result.append(%s.serialize() if %s is not None else None)", pyField, pyField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("result.append(%s)", pyField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("result.append(base64.b64encode(%s).decode(\"ascii\"))", pyField);
    } else if (INT64_TYPES.contains(field.getProtoType())) {
      w.line("result.append(str(%s))", pyField);
    } else {
      w.line("result.append(%s)", pyField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String pyField) {
    if (field.isProto3Optional()) {
      String bitCheck = "self._present_fields[" + field.getArrayPosition() + "]";
      w.line("if %s:", bitCheck);
      w.indent();
      emitScalarAppend(w, field, pyField);
      w.dedent();
      w.line("el" + "se:");
      w.indent();
      w.line("result.append(None)");
      w.dedent();
      return;
    }
    emitScalarAppend(w, field, pyField);
  }

  private static final Set<FieldDescriptorProto.Type> INT64_TYPES =
      Set.of(
          FieldDescriptorProto.Type.TYPE_INT64,
          FieldDescriptorProto.Type.TYPE_SINT64,
          FieldDescriptorProto.Type.TYPE_SFIXED64,
          FieldDescriptorProto.Type.TYPE_UINT64,
          FieldDescriptorProto.Type.TYPE_FIXED64);

  private void emitScalarAppend(CodeWriter w, ProtoField field, String pyField) {
    if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("result.append(base64.b64encode(%s).decode(\"ascii\"))", pyField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_FLOAT
        || field.getProtoType() == FieldDescriptorProto.Type.TYPE_DOUBLE) {
      w.line(
          "result.append(None if math.isnan(%s) or math.isinf(%s) else %s)",
          pyField, pyField, pyField);
    } else if (INT64_TYPES.contains(field.getProtoType())) {
      w.line("result.append(str(%s))", pyField);
    } else {
      w.line("result.append(%s)", pyField);
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String pyField) {
    if (field.isProto3Optional()) {
      String bitCheck = "self._present_fields[" + field.getArrayPosition() + "]";
      w.line("if %s:", bitCheck);
      w.indent();
      w.line("result.append(%s)", pyField);
      w.dedent();
      w.line("el" + "se:");
      w.indent();
      w.line("result.append(None)");
      w.dedent();
    } else {
      w.line("result.append(%s)", pyField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String pyField) {
    w.line("result.append(%s.serialize() if %s is not None else None)", pyField, pyField);
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String pyField) {
    String itemVar = nameResolver.fieldName(field.getName()) + "_item";
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.line(
          "result.append([%s.serialize() if %s is not None else None for %s in %s])",
          itemVar, itemVar, itemVar, pyField);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("result.append(list(%s))", pyField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line(
          "result.append([base64.b64encode(%s).decode(\"ascii\") for %s in %s])",
          itemVar, itemVar, pyField);
    } else if (INT64_TYPES.contains(field.getProtoType())) {
      w.line("result.append([str(%s) for %s in %s])", itemVar, itemVar, pyField);
    } else {
      w.line("result.append(list(%s))", pyField);
    }
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String pyField) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    if (stringKey) {
      // String-keyed maps serialize as a dict
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line(
            "result.append({k: (v.serialize() if v is not None else None) for k, v in %s.items()})",
            pyField);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("result.append(dict(%s))", pyField);
      } else {
        w.line("result.append(dict(%s))", pyField);
      }
    } else {
      // Non-string-keyed maps serialize as list of [k, v] pairs
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        w.line(
            "result.append([[k, v.serialize() if v is not None else None] for k, v in %s.items()])",
            pyField);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("result.append([[k, v] for k, v in %s.items()])", pyField);
      } else {
        w.line("result.append([[k, v] for k, v in %s.items()])", pyField);
      }
    }
  }
}
