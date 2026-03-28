package dev.protocgen.textcodecs.jsonarray.codegen.go;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;

/**
 * Generates the Serialize() method body for Go structs. Produces a []any slice with fields at
 * positions determined by field number. int64/uint64 values are serialized as strings to avoid
 * float64 precision loss in JSON.
 */
public class GoSerializerGenerator {

  private final GoTypeMapper typeMapper;
  private final GoNameResolver nameResolver;

  public GoSerializerGenerator(GoTypeMapper typeMapper, GoNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String structName) {
    // Serialize method: returns []any
    w.blankLine();
    w.block(
        "func (m *" + structName + ") Serialize() []any",
        () -> {
          int maxPos = message.getMaxFieldNumber();
          w.line("arr := make([]any, %d)", maxPos);

          for (int pos = 0; pos < maxPos; pos++) {
            ProtoField field = message.fieldAtPosition(pos);
            if (field == null) {
              // Gap in field numbering - stays nil (null in JSON)
              w.line("// position %d: gap (no field number %d)", pos, pos + 1);
            } else {
              emitFieldSerialize(w, field, pos);
            }
          }

          w.line("return arr");
        });

    // ToJsonString convenience method
    w.blankLine();
    w.block(
        "func (m *" + structName + ") ToJsonString() (string, error)",
        () -> {
          w.line("arr := m.Serialize()");
          w.line("data, err := json.Marshal(arr)");
          w.block(
              "if err != nil",
              () -> {
                w.line("return \"\", err");
              });
          w.line("return string(data), nil");
        });
  }

  private void emitFieldSerialize(CodeWriter w, ProtoField field, int pos) {
    String goField = "m." + nameResolver.fieldName(field.getName());

    if (field.isOneofMember()) {
      // For oneof members: write the value if this is the active case, else nil
      String caseField = "m." + GoNameResolver.snakeToPascal(field.getOneofName()) + "Case";
      int fieldNum = field.getFieldNumber();
      w.block(
          "if " + caseField + " == " + fieldNum,
          () -> {
            emitValueForField(w, field, goField, pos);
          });
      // else: arr[pos] stays nil
      return;
    }

    if (field.isMap()) {
      emitMapSerialize(w, field, goField, pos);
    } else if (field.isRepeated()) {
      emitRepeatedSerialize(w, field, goField, pos);
    } else if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      emitMessageSerialize(w, field, goField, pos);
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      emitEnumSerialize(w, field, goField, pos);
    } else {
      emitScalarSerialize(w, field, goField, pos);
    }
  }

  private void emitValueForField(CodeWriter w, ProtoField field, String goField, int pos) {
    if (field.getKind() == ProtoField.FieldKind.MESSAGE
        || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
      w.block(
          "if " + goField + " != nil",
          () -> {
            w.line("arr[%d] = %s.Serialize()", pos, goField);
          });
    } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
      w.line("arr[%d] = int32(%s)", pos, goField);
    } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
      w.line("arr[%d] = base64.StdEncoding.EncodeToString(%s)", pos, goField);
    } else if (isInt64Type(field.getProtoType())) {
      w.line("arr[%d] = %s", pos, int64ToStringExpr(goField, field.getProtoType()));
    } else {
      w.line("arr[%d] = %s", pos, goField);
    }
  }

  private void emitScalarSerialize(CodeWriter w, ProtoField field, String goField, int pos) {
    if (field.isProto3Optional()) {
      // Pointer field: check for nil
      w.block(
          "if " + goField + " != nil",
          () -> {
            if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
              w.line("arr[%d] = base64.StdEncoding.EncodeToString(%s)", pos, goField);
            } else if (isInt64Type(field.getProtoType())) {
              w.line("arr[%d] = %s", pos, int64ToStringExpr("*" + goField, field.getProtoType()));
            } else {
              w.line("arr[%d] = *%s", pos, goField);
            }
          });
      return;
    }
    switch (field.getProtoType()) {
      case TYPE_BYTES:
        w.block(
            "if " + goField + " != nil",
            () -> {
              w.line("arr[%d] = base64.StdEncoding.EncodeToString(%s)", pos, goField);
            });
        break;
      case TYPE_INT64:
      case TYPE_SINT64:
      case TYPE_SFIXED64:
      case TYPE_UINT64:
      case TYPE_FIXED64:
        w.line("arr[%d] = %s", pos, int64ToStringExpr(goField, field.getProtoType()));
        break;
      default:
        w.line("arr[%d] = %s", pos, goField);
        break;
    }
  }

  private void emitEnumSerialize(CodeWriter w, ProtoField field, String goField, int pos) {
    if (field.isProto3Optional()) {
      w.block(
          "if " + goField + " != nil",
          () -> {
            w.line("arr[%d] = int32(*%s)", pos, goField);
          });
    } else {
      w.line("arr[%d] = int32(%s)", pos, goField);
    }
  }

  private void emitMessageSerialize(CodeWriter w, ProtoField field, String goField, int pos) {
    w.block(
        "if " + goField + " != nil",
        () -> {
          w.line("arr[%d] = %s.Serialize()", pos, goField);
        });
  }

  private void emitRepeatedSerialize(CodeWriter w, ProtoField field, String goField, int pos) {
    // Always emit an array (even if empty) to avoid null in JSON output
    w.blockContinue(
        "if " + goField + " == nil",
        () -> {
          w.line("arr[%d] = []any{}", pos);
        });
    w.raw(" ");
    w.block(
        "else",
        () -> {
          String itemVar = GoNameResolver.snakeToCamel(field.getName()) + "Item";
          w.line("listArr := make([]any, len(%s))", goField);
          w.block(
              "for i, " + itemVar + " := range " + goField,
              () -> {
                if (field.getKind() == ProtoField.FieldKind.MESSAGE
                    || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
                  w.block(
                      "if " + itemVar + " != nil",
                      () -> {
                        w.line("listArr[i] = %s.Serialize()", itemVar);
                      });
                } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
                  w.line("listArr[i] = int32(%s)", itemVar);
                } else if (field.getProtoType() == FieldDescriptorProto.Type.TYPE_BYTES) {
                  w.line("listArr[i] = base64.StdEncoding.EncodeToString(%s)", itemVar);
                } else if (isInt64Type(field.getProtoType())) {
                  w.line("listArr[i] = %s", int64ToStringExpr(itemVar, field.getProtoType()));
                } else {
                  w.line("listArr[i] = %s", itemVar);
                }
              });
          w.line("arr[%d] = listArr", pos);
        });
  }

  private void emitMapSerialize(CodeWriter w, ProtoField field, String goField, int pos) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;
    w.block(
        "if " + goField + " != nil",
        () -> {
          if (stringKey) {
            // String keys: serialize as JSON object (map[string]any)
            w.line("mapObj := make(map[string]any, len(%s))", goField);
            String entryVar = GoNameResolver.snakeToCamel(field.getName()) + "Key";
            String valVar = GoNameResolver.snakeToCamel(field.getName()) + "Val";
            w.block(
                "for " + entryVar + ", " + valVar + " := range " + goField,
                () -> {
                  emitMapValueAssign(w, field, entryVar, valVar, "mapObj");
                });
            w.line("arr[%d] = mapObj", pos);
          } else {
            // Non-string keys: serialize as array of [key, value] pairs
            w.line("var pairs []any");
            String entryVar = GoNameResolver.snakeToCamel(field.getName()) + "Key";
            String valVar = GoNameResolver.snakeToCamel(field.getName()) + "Val";
            w.block(
                "for " + entryVar + ", " + valVar + " := range " + goField,
                () -> {
                  w.line("pair := []any{%s, %s}", entryVar, mapValueExpr(field, valVar));
                  w.line("pairs = append(pairs, pair)");
                });
            w.line("arr[%d] = pairs", pos);
          }
        });
  }

  private void emitMapValueAssign(
      CodeWriter w, ProtoField field, String keyExpr, String valueExpr, String mapVar) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      w.blockContinue(
          "if " + valueExpr + " != nil",
          () -> {
            w.line("%s[%s] = %s.Serialize()", mapVar, keyExpr, valueExpr);
          });
      w.raw(" ");
      w.block(
          "else",
          () -> {
            w.line("%s[%s] = nil", mapVar, keyExpr);
          });
    } else if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      w.line("%s[%s] = int32(%s)", mapVar, keyExpr, valueExpr);
    } else {
      w.line("%s[%s] = %s", mapVar, keyExpr, valueExpr);
    }
  }

  private String mapValueExpr(ProtoField field, String valueExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      return valueExpr + ".Serialize()";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return "int32(" + valueExpr + ")";
    }
    return valueExpr;
  }

  /** Check if this proto type is a 64-bit integer type. */
  private boolean isInt64Type(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64
        || type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64;
  }

  /**
   * Return a Go expression that serializes an int64/uint64 field as a string. Uses
   * strconv.FormatInt/FormatUint to avoid JSON float64 precision loss.
   */
  private String int64ToStringExpr(String fieldExpr, FieldDescriptorProto.Type type) {
    if (type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64) {
      return "strconv.FormatUint(" + fieldExpr + ", 10)";
    }
    return "strconv.FormatInt(" + fieldExpr + ", 10)";
  }
}
