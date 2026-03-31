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
package dev.protocgen.textcodecs.jsonarray.codegen.python;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.Set;

/**
 * Generates the @classmethod deserialize() method for Python classes. Reads fields positionally
 * from a Python list.
 */
public class PythonDeserializerGenerator {

  private final PythonTypeMapper typeMapper;
  private final PythonNameResolver nameResolver;

  public PythonDeserializerGenerator(PythonTypeMapper typeMapper, PythonNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(
      CodeWriter w, ProtoMessage message, String className, Set<String> lazyImports) {
    w.blankLine();
    w.line("@classmethod");
    w.line("def deserialize(cls, data):");
    w.indent();
    w.line("\"\"\"Deserialize a positional list into a %s instance.\"\"\"", className);
    // Emit cross-file imports lazily inside the method body to avoid circular
    // imports between mutually-referencing messages (BUG-IO-01)
    for (String imp : lazyImports) {
      w.line("%s  # lazy import to avoid circular dependency", imp);
    }
    w.line("obj = cls()");
    w.line("size = len(data)");

    int maxPos = message.getMaxFieldNumber();
    for (int pos = 0; pos < maxPos; pos++) {
      ProtoField field = message.fieldAtPosition(pos);
      if (field == null) {
        w.line("# position %d: gap (no field)", pos);
        continue;
      }
      emitFieldDeserialize(w, field, pos);
    }

    w.line("return obj");
    w.dedent();

    // Convenience: deserialize from JSON string
    w.blankLine();
    w.line("@classmethod");
    w.line("def from_json_string(cls, json_str):");
    w.indent();
    w.line("\"\"\"Deserialize from a JSON string.\"\"\"");
    w.line("return cls.deserialize(json.loads(json_str))");
    w.dedent();

    // Convenience: deserialize from bytes
    w.blankLine();
    w.line("@classmethod");
    w.line("def from_json_bytes(cls, data):");
    w.indent();
    w.line("\"\"\"Deserialize from JSON-encoded bytes.\"\"\"");
    w.line("return cls.from_json_string(data.decode(\"utf-8\"))");
    w.dedent();
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String pyField = "obj._" + nameResolver.fieldName(field.getName());

    w.line("if size > %d and data[%d] is not None:", pos, pos);
    w.indent();

    String elemExpr = "data[" + pos + "]";

    if (field.isMap()) {
      emitMapDeserialize(w, field, pyField, elemExpr);
    } else if (field.isRepeated()) {
      emitRepeatedDeserialize(w, field, pyField, elemExpr);
    } else if (field.isWellKnownType()) {
      emitMessageDeserialize(w, field, pyField, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
      emitMessageDeserialize(w, field, pyField, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumDeserialize(w, field, pyField, elemExpr);
    } else {
      emitScalarDeserialize(w, field, pyField, elemExpr);
    }

    if (field.isProto3Optional()) {
      w.line("obj._present_fields[%d] = True", pos);
    }

    if (field.isOneofMember()) {
      String caseName = "obj._" + nameResolver.fieldName(field.getOneofName()) + "_case";
      w.line("%s = %d", caseName, field.getFieldNumber());
    }

    w.dedent();
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String pyField, String elemExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), elemExpr);
    w.line("%s = %s", pyField, readExpr);
  }

  private void emitEnumDeserialize(
      CodeWriter w, ProtoField field, String pyField, String elemExpr) {
    w.line("%s = int(%s)", pyField, elemExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String pyField, String elemExpr) {
    String msgType = simpleTypeName(field.getTypeReference());
    w.line("%s = %s.deserialize(%s)", pyField, msgType, elemExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String pyField, String elemExpr) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      String msgType = simpleTypeName(field.getTypeReference());
      w.line(
          "%s = [%s.deserialize(elem) if elem is not None else None for elem in %s]",
          pyField, msgType, elemExpr);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("%s = [int(elem) for elem in %s]", pyField, elemExpr);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("%s = [base64.b64decode(elem) for elem in %s]", pyField, elemExpr);
    } else {
      String readExpr = scalarListReadExpr(field.getProtoType());
      if (readExpr != null) {
        w.line("%s = [%s for elem in %s]", pyField, readExpr, elemExpr);
      } else {
        w.line("%s = list(%s)", pyField, elemExpr);
      }
    }
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String pyField, String elemExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    if (stringKey) {
      // String-keyed maps are deserialized from a dict
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line(
            "%s = {k: %s.deserialize(v) if v is not None else None for k, v in %s.items()}",
            pyField, msgType, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("%s = {k: int(v) for k, v in %s.items()}", pyField, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line("%s = {k: base64.b64decode(v) for k, v in %s.items()}", pyField, elemExpr);
      } else {
        w.line("%s = dict(%s)", pyField, elemExpr);
      }
    } else {
      // Non-string-keyed maps are deserialized from a list of [k, v] pairs
      String keyRead = mapKeyReadExpr(field.getMapKeyType(), "pair[0]");
      if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
        String msgType = simpleTypeName(field.getMapValueTypeReference());
        w.line(
            "%s = {%s: %s.deserialize(pair[1]) if pair[1] is not None else None for pair in %s}",
            pyField, keyRead, msgType, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
        w.line("%s = {%s: int(pair[1]) for pair in %s}", pyField, keyRead, elemExpr);
      } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_BYTES) {
        w.line("%s = {%s: base64.b64decode(pair[1]) for pair in %s}", pyField, keyRead, elemExpr);
      } else {
        String valRead = mapValueReadExpr(field.getMapValueType(), "pair[1]");
        w.line("%s = {%s: %s for pair in %s}", pyField, keyRead, valRead, elemExpr);
      }
    }
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String expr) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "float(" + expr + ")";
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
      case TYPE_BOOL -> "bool(" + expr + ")";
      case TYPE_STRING -> "str(" + expr + ")";
      case TYPE_BYTES -> "base64.b64decode(" + expr + ")";
      default -> expr;
    };
  }

  /**
   * Read expression for scalar elements in a list comprehension (uses "elem" as variable). Returns
   * null if list() is sufficient (simple pass-through).
   */
  private String scalarListReadExpr(FieldDescriptorProto.Type type) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "float(elem)";
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
          "int(elem)";
      case TYPE_BOOL -> "bool(elem)";
      case TYPE_STRING -> "str(elem)";
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
      case TYPE_BOOL -> "bool(" + expr + ")";
      default -> expr;
    };
  }

  private String mapValueReadExpr(FieldDescriptorProto.Type type, String expr) {
    return scalarReadExpr(type, expr);
  }

  private String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "object";
  }
}
