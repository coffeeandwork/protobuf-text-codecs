package dev.protocgen.textcodecs.jsonarray.codegen.javascript;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.CodeWriter;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.List;

/**
 * Generates the static deserialize() method for JavaScript classes. Reads fields positionally from
 * a plain JS array.
 */
public class JavaScriptDeserializerGenerator {

  private final JavaScriptTypeMapper typeMapper;
  private final JavaScriptNameResolver nameResolver;

  public JavaScriptDeserializerGenerator(
      JavaScriptTypeMapper typeMapper, JavaScriptNameResolver nameResolver) {
    this.typeMapper = typeMapper;
    this.nameResolver = nameResolver;
  }

  public void generate(CodeWriter w, ProtoMessage message, String className) {
    generate(w, message, className, List.of(), null);
  }

  public void generate(
      CodeWriter w,
      ProtoMessage message,
      String className,
      List<String> lazyImportNames,
      JavaScriptCodeEmitter emitter) {
    // static deserialize(data) - accepts Array or JSON string
    w.blankLine();
    w.line("/**");
    w.line(" * Deserialize from a positional JSON array or JSON string.");
    w.line(" * @param {Array|string} data - The array or JSON string to deserialize from.");
    w.line(" * @returns {%s} The deserialized message.", className);
    w.line(" */");
    w.block(
        "static deserialize(data)",
        () -> {
          // Emit lazy imports inside the method body to avoid circular requires
          if (emitter != null) {
            for (String name : lazyImportNames) {
              w.line(emitter.formatLazyImport(name));
            }
          }
          w.line("const array = typeof data === 'string' ? JSON.parse(data) : data;");
          w.line("const obj = new %s();", className);
          w.line("const size = array.length;");

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

    // Convenience: fromJsonString
    w.blankLine();
    w.line("/**");
    w.line(" * Deserialize from a JSON string.");
    w.line(" * @param {string} json - The JSON string.");
    w.line(" * @returns {%s} The deserialized message.", className);
    w.line(" */");
    w.block(
        "static fromJsonString(json)",
        () -> {
          w.line("return %s.deserialize(JSON.parse(json));", className);
        });
  }

  private void emitFieldDeserialize(CodeWriter w, ProtoField field, int pos) {
    String setter = "obj." + nameResolver.setterName(field.getName());
    String nodeExpr = "array[" + pos + "]";

    w.block(
        "if (size > " + pos + " && " + nodeExpr + " != null)",
        () -> {
          if (field.isMap()) {
            emitMapDeserialize(w, field, setter, nodeExpr);
          } else if (field.isRepeated()) {
            emitRepeatedDeserialize(w, field, setter, nodeExpr);
          } else if (field.isWellKnownType()) {
            emitMessageDeserialize(w, field, setter, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.MESSAGE) {
            emitMessageDeserialize(w, field, setter, nodeExpr);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            emitEnumDeserialize(w, field, setter, nodeExpr);
          } else {
            emitScalarDeserialize(w, field, setter, nodeExpr);
          }

          if (field.isProto3Optional()) {
            w.line("obj._presentFields[%d] = true;", pos);
          }
        });
  }

  private void emitScalarDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String readExpr = scalarReadExpr(field.getProtoType(), nodeExpr);
    w.line("%s(%s);", setter, readExpr);
  }

  private void emitEnumDeserialize(CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    // Enums stored as numbers in the array
    w.line("%s(%s);", setter, nodeExpr);
  }

  private void emitMessageDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    String msgType = typeMapper.simpleTypeName(field.getTypeReference());
    w.line("%s(%s.deserialize(%s));", setter, msgType, nodeExpr);
  }

  private void emitRepeatedDeserialize(
      CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    w.line("const __list = [];");
    w.block(
        "for (const elem of " + nodeExpr + ")",
        () -> {
          if (field.getKind() == ProtoField.FieldKind.MESSAGE
              || field.getKind() == ProtoField.FieldKind.WELL_KNOWN_TYPE) {
            String msgType = typeMapper.simpleTypeName(field.getTypeReference());
            w.line("__list.push(elem != null ? %s.deserialize(elem) : null);", msgType);
          } else if (field.getKind() == ProtoField.FieldKind.ENUM) {
            w.line("__list.push(elem);");
          } else {
            String readExpr = scalarReadExpr(field.getProtoType(), "elem");
            w.line("__list.push(%s);", readExpr);
          }
        });
    w.line("%s(__list);", setter);
  }

  private void emitMapDeserialize(CodeWriter w, ProtoField field, String setter, String nodeExpr) {
    boolean stringKey = field.getMapKeyType() == FieldDescriptorProto.Type.TYPE_STRING;

    w.line("const __map = {};");
    if (stringKey) {
      w.block(
          "for (const [key, val] of Object.entries(" + nodeExpr + "))",
          () -> {
            String valueExpr = mapValueReadExpr(field, "val");
            w.line("__map[key] = %s;", valueExpr);
          });
    } else {
      w.block(
          "for (const __pairs of " + nodeExpr + ")",
          () -> {
            String keyRead = scalarReadExpr(field.getMapKeyType(), "__pairs[0]");
            String valueRead = mapValueReadExpr(field, "__pairs[1]");
            w.line("__map[%s] = %s;", keyRead, valueRead);
          });
    }
    w.line("%s(__map);", setter);
  }

  private String scalarReadExpr(FieldDescriptorProto.Type type, String nodeExpr) {
    return switch (type) {
      case TYPE_DOUBLE, TYPE_FLOAT -> "Number(" + nodeExpr + ")";
      case TYPE_INT64, TYPE_SINT64, TYPE_SFIXED64, TYPE_UINT64, TYPE_FIXED64 ->
          "String(" + nodeExpr + ")";
      case TYPE_INT32, TYPE_SINT32, TYPE_SFIXED32, TYPE_UINT32, TYPE_FIXED32 ->
          "Number(" + nodeExpr + ")";
      case TYPE_BOOL -> "Boolean(" + nodeExpr + ")";
      case TYPE_STRING -> "String(" + nodeExpr + ")";
      case TYPE_BYTES -> bytesDecodeExpr(nodeExpr);
      default -> nodeExpr;
    };
  }

  private String mapValueReadExpr(ProtoField field, String nodeExpr) {
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
      String msgType = typeMapper.simpleTypeName(field.getMapValueTypeReference());
      return nodeExpr + " != null ? " + msgType + ".deserialize(" + nodeExpr + ") : null";
    }
    if (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      return nodeExpr;
    }
    return scalarReadExpr(field.getMapValueType(), nodeExpr);
  }

  /**
   * Generate a base64 decode expression for bytes fields. Uses Buffer for Node.js, atob for browser
   * environments.
   */
  private String bytesDecodeExpr(String nodeExpr) {
    return "(typeof Buffer !== 'undefined' ? new Uint8Array(Buffer.from("
        + nodeExpr
        + ", 'base64')) : new Uint8Array(atob("
        + nodeExpr
        + ").split('').map(c => c.charCodeAt(0))))";
  }
}
