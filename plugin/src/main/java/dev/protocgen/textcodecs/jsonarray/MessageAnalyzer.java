package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import dev.protocgen.textcodecs.jsonarray.model.WellKnownType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analyzes a DescriptorProto and converts it into the language-neutral ProtoMessage model. Fields
 * are sorted by field number for positional array encoding.
 */
public class MessageAnalyzer {

  /** Regex for valid proto identifiers (field names, message names, enum names). */
  private static final String SAFE_IDENTIFIER = "[a-zA-Z_][a-zA-Z0-9_]*";

  /** Pre-compiled pattern to avoid per-call regex compilation overhead. */
  private static final java.util.regex.Pattern SAFE_IDENTIFIER_PATTERN =
      java.util.regex.Pattern.compile(SAFE_IDENTIFIER);

  private final TypeRegistry typeRegistry;

  public MessageAnalyzer(TypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
  }

  /**
   * Analyze a message descriptor and produce a ProtoMessage model. Assumes proto3 syntax.
   *
   * @param descriptor the protobuf message descriptor
   * @param parentFullName the fully-qualified name prefix (e.g., ".example.")
   */
  public ProtoMessage analyze(DescriptorProto descriptor, String parentFullName) {
    return analyze(descriptor, parentFullName, "proto3");
  }

  /**
   * Analyze a message descriptor and produce a ProtoMessage model.
   *
   * @param descriptor the protobuf message descriptor
   * @param parentFullName the fully-qualified name prefix (e.g., ".example.")
   * @param syntax the proto file syntax ("proto2" or "proto3")
   */
  public ProtoMessage analyze(DescriptorProto descriptor, String parentFullName, String syntax) {
    return analyze(descriptor, parentFullName, syntax, Collections.emptyMap(), List.of());
  }

  /**
   * Analyze a message descriptor and produce a ProtoMessage model, with source comments.
   *
   * @param descriptor the protobuf message descriptor
   * @param parentFullName the fully-qualified name prefix (e.g., ".example.")
   * @param syntax the proto file syntax ("proto2" or "proto3")
   * @param commentMap map from source path to leading comment text
   * @param messagePath the source path to this message in the FileDescriptorProto
   */
  public ProtoMessage analyze(
      DescriptorProto descriptor,
      String parentFullName,
      String syntax,
      Map<List<Integer>, String> commentMap,
      List<Integer> messagePath) {
    // Validate message name is a safe identifier (defense-in-depth, VULN-001)
    if (!SAFE_IDENTIFIER_PATTERN.matcher(descriptor.getName()).matches()) {
      throw new IllegalArgumentException(
          "Message name '"
              + descriptor.getName()
              + "' contains invalid characters. "
              + "Message names must match " + SAFE_IDENTIFIER + ".");
    }

    String fullName = parentFullName + descriptor.getName();

    // Analyze nested enums
    List<ProtoEnum> enums =
        descriptor.getEnumTypeList().stream().map(e -> analyzeEnum(e, fullName + ".")).toList();

    // Analyze nested messages (skip synthetic map-entry messages)
    List<ProtoMessage> nestedMessages = new ArrayList<>();
    int nestedIndex = 0;
    for (DescriptorProto nested : descriptor.getNestedTypeList()) {
      if (!nested.getOptions().getMapEntry()) {
        // Path for nested message: parentPath + [3, nestedIndex]
        // (3 = DescriptorProto.nested_type field number)
        List<Integer> nestedPath = new ArrayList<>(messagePath);
        nestedPath.add(3);
        nestedPath.add(nestedIndex);
        nestedMessages.add(analyze(nested, fullName + ".", syntax, commentMap, nestedPath));
      }
      nestedIndex++;
    }

    // Analyze fields, sorted by field number
    boolean isProto2 = "proto2".equals(syntax);
    List<FieldDescriptorProto> fieldList = descriptor.getFieldList();
    List<ProtoField> unsorted = new ArrayList<>(fieldList.size());
    for (int fieldIndex = 0; fieldIndex < fieldList.size(); fieldIndex++) {
      FieldDescriptorProto field = fieldList.get(fieldIndex);
      // Path for field: parentPath + [2, fieldIndex]
      // (2 = DescriptorProto.field field number)
      List<Integer> fieldPath = new ArrayList<>(messagePath);
      fieldPath.add(2);
      fieldPath.add(fieldIndex);
      String fieldComment = commentMap.get(fieldPath);
      unsorted.add(analyzeField(field, descriptor, fullName, isProto2, fieldComment));
    }
    unsorted.sort(Comparator.comparingInt(ProtoField::getFieldNumber));
    List<ProtoField> fields = List.copyOf(unsorted);

    // Warn about very sparse field numbering
    if (!fields.isEmpty()) {
      int maxFieldNumber = fields.stream().mapToInt(ProtoField::getFieldNumber).max().orElse(0);
      if (maxFieldNumber > 2 * fields.size()) {
        System.err.println(
            "protoc-gen-jsonarray: warning: message '"
                + fullName
                + "' has sparse field numbering (max field number "
                + maxFieldNumber
                + " with only "
                + fields.size()
                + " fields). "
                + "The generated JSON arrays will contain many null gaps.");
      }
    }

    // Build oneof groups (exclude synthetic oneofs from proto3 optional)
    List<ProtoMessage.OneofGroup> oneofGroups = buildOneofGroups(descriptor, fields);

    // Look up leading comment for this message
    String messageComment = commentMap.get(messagePath);

    return new ProtoMessage(
        descriptor.getName(), fullName, fields, enums, nestedMessages, oneofGroups, messageComment);
  }

  public ProtoEnum analyzeEnum(EnumDescriptorProto descriptor, String parentFullName) {
    // Validate enum name (defense-in-depth, VULN-001)
    if (!SAFE_IDENTIFIER_PATTERN.matcher(descriptor.getName()).matches()) {
      throw new IllegalArgumentException(
          "Enum name '"
              + descriptor.getName()
              + "' contains invalid characters. "
              + "Enum names must match " + SAFE_IDENTIFIER + ".");
    }
    // Validate enum value names
    for (var v : descriptor.getValueList()) {
      if (!SAFE_IDENTIFIER_PATTERN.matcher(v.getName()).matches()) {
        throw new IllegalArgumentException(
            "Enum value name '"
                + v.getName()
                + "' contains invalid characters. "
                + "Enum value names must match " + SAFE_IDENTIFIER + ".");
      }
    }
    String fullName = parentFullName + descriptor.getName();
    List<ProtoEnum.EnumValue> values =
        descriptor.getValueList().stream()
            .map(v -> new ProtoEnum.EnumValue(v.getName(), v.getNumber()))
            .toList();
    return new ProtoEnum(descriptor.getName(), fullName, values);
  }

  private ProtoField analyzeField(
      FieldDescriptorProto field, DescriptorProto parent, String parentFullName, boolean isProto2) {
    return analyzeField(field, parent, parentFullName, isProto2, null);
  }

  private ProtoField analyzeField(
      FieldDescriptorProto field,
      DescriptorProto parent,
      String parentFullName,
      boolean isProto2,
      String comment) {
    // Validate field name is a safe identifier (defense-in-depth against crafted requests)
    if (!SAFE_IDENTIFIER_PATTERN.matcher(field.getName()).matches()) {
      throw new IllegalArgumentException(
          "Field name '"
              + field.getName()
              + "' in message '"
              + parentFullName
              + "' contains invalid characters. Field names must match " + SAFE_IDENTIFIER + ".");
    }

    // Validate field number is positive
    if (field.getNumber() <= 0) {
      throw new IllegalArgumentException(
          "Field '"
              + field.getName()
              + "' in message '"
              + parentFullName
              + "' has invalid field number "
              + field.getNumber()
              + ". Field numbers must be positive.");
    }

    ProtoField.Builder builder =
        ProtoField.builder()
            .name(field.getName())
            .fieldNumber(field.getNumber())
            .protoType(field.getType());

    // Determine kind and type reference
    if (field.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE
        || field.getType() == FieldDescriptorProto.Type.TYPE_GROUP) {
      String typeRef = field.getTypeName();

      // Reject google.protobuf.Any
      if (".google.protobuf.Any".equals(typeRef)) {
        throw new IllegalArgumentException(
            "google.protobuf.Any is not supported by protoc-gen-jsonarray "
                + "because positional JSON array encoding requires compile-time "
                + "schema knowledge. Consider using a concrete message type instead.");
      }

      WellKnownType wkt = WellKnownType.fromTypeName(typeRef);
      if (wkt != null) {
        builder.kind(ProtoField.FieldKind.WELL_KNOWN_TYPE).wellKnownType(wkt);
      } else {
        builder.kind(ProtoField.FieldKind.MESSAGE);
      }
      // Groups use TYPE_GROUP but are structurally identical to nested messages.
      // Normalize to TYPE_MESSAGE for code generation purposes.
      if (field.getType() == FieldDescriptorProto.Type.TYPE_GROUP) {
        builder.protoType(FieldDescriptorProto.Type.TYPE_MESSAGE);
      }
      builder.typeReference(typeRef);
    } else if (field.getType() == FieldDescriptorProto.Type.TYPE_ENUM) {
      builder.kind(ProtoField.FieldKind.ENUM).typeReference(field.getTypeName());
    } else {
      builder.kind(ProtoField.FieldKind.SCALAR);
    }

    // Determine cardinality
    if (field.getLabel() == FieldDescriptorProto.Label.LABEL_REPEATED) {
      if (isMapField(field)) {
        builder.cardinality(ProtoField.Cardinality.MAP);
        populateMapTypes(builder, field);
      } else {
        builder.cardinality(ProtoField.Cardinality.REPEATED);
      }
    } else {
      builder.cardinality(ProtoField.Cardinality.SINGULAR);
    }

    // Proto2 required label
    if (field.getLabel() == FieldDescriptorProto.Label.LABEL_REQUIRED) {
      builder.required(true);
    }

    // Proto2 schema-specified default value
    if (isProto2 && field.hasDefaultValue() && !field.getDefaultValue().isEmpty()) {
      builder.defaultValue(field.getDefaultValue());
    }

    // Determine explicit presence:
    // - Proto2: ALL singular fields have explicit presence
    // - Proto3: only fields with the 'optional' keyword have explicit presence
    if (isProto2) {
      if (field.getLabel() != FieldDescriptorProto.Label.LABEL_REPEATED) {
        builder.hasExplicitPresence(true);
      }
    }

    // Proto3 optional (explicit presence)
    if (field.hasProto3Optional() && field.getProto3Optional()) {
      builder.proto3Optional(true);
      builder.hasExplicitPresence(true);
    }

    // Oneof membership (real oneofs only, not synthetic proto3 optional)
    if (field.hasOneofIndex() && !(field.hasProto3Optional() && field.getProto3Optional())) {
      int oneofIdx = field.getOneofIndex();
      String oneofName = parent.getOneofDecl(oneofIdx).getName();
      builder.oneofIndex(oneofIdx).oneofName(oneofName);
    }

    // Attach source comment if available
    if (comment != null) {
      builder.comment(comment);
    }

    return builder.build();
  }

  private boolean isMapField(FieldDescriptorProto field) {
    if (field.getType() != FieldDescriptorProto.Type.TYPE_MESSAGE) {
      return false;
    }
    return typeRegistry.isMapEntry(field.getTypeName());
  }

  private void populateMapTypes(ProtoField.Builder builder, FieldDescriptorProto field) {
    DescriptorProto mapEntry = typeRegistry.getMessage(field.getTypeName());
    if (mapEntry == null) {
      return;
    }
    for (FieldDescriptorProto entryField : mapEntry.getFieldList()) {
      if (entryField.getNumber() == 1) { // key
        builder.mapKeyType(entryField.getType());
      } else if (entryField.getNumber() == 2) { // value
        builder.mapValueType(entryField.getType());
        if (entryField.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE
            || entryField.getType() == FieldDescriptorProto.Type.TYPE_ENUM) {
          builder.mapValueTypeReference(entryField.getTypeName());
        }
      }
    }
  }

  private List<ProtoMessage.OneofGroup> buildOneofGroups(
      DescriptorProto descriptor, List<ProtoField> fields) {
    if (descriptor.getOneofDeclCount() == 0) {
      return List.of();
    }

    // Group fields by oneof index, excluding synthetic oneofs
    Map<Integer, List<ProtoField>> grouped =
        fields.stream()
            .filter(ProtoField::isOneofMember)
            .collect(Collectors.groupingBy(ProtoField::getOneofIndex));

    List<ProtoMessage.OneofGroup> groups = new ArrayList<>();
    for (var entry : grouped.entrySet()) {
      int idx = entry.getKey();
      String name = descriptor.getOneofDecl(idx).getName();
      groups.add(new ProtoMessage.OneofGroup(name, idx, entry.getValue()));
    }

    groups.sort(Comparator.comparingInt(ProtoMessage.OneofGroup::index));
    return groups;
  }
}
