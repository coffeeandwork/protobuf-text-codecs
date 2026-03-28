package dev.protocgen.textcodecs.jsonarray.model;

import java.util.List;
import java.util.Map;

/** Language-neutral representation of a proto message, with fields ordered by field number. */
public class ProtoMessage {

  private final String name;
  private final String fullName; // fully-qualified proto name (e.g., ".example.User")
  private final List<ProtoField> fields; // sorted by field number
  private final List<ProtoEnum> enums; // nested enum definitions
  private final List<ProtoMessage> nestedMessages; // nested message definitions
  private final List<OneofGroup> oneofGroups;
  private final int maxFieldNumber;
  private final ProtoField[] fieldsByPosition; // array-indexed O(1) lookup, no boxing
  private final String comment; // leading comment from the proto source, null if none

  public ProtoMessage(
      String name,
      String fullName,
      List<ProtoField> fields,
      List<ProtoEnum> enums,
      List<ProtoMessage> nestedMessages,
      List<OneofGroup> oneofGroups) {
    this(name, fullName, fields, enums, nestedMessages, oneofGroups, null);
  }

  public ProtoMessage(
      String name,
      String fullName,
      List<ProtoField> fields,
      List<ProtoEnum> enums,
      List<ProtoMessage> nestedMessages,
      List<OneofGroup> oneofGroups,
      String comment) {
    this.name = name;
    this.fullName = fullName;
    this.fields = List.copyOf(fields);
    this.enums = List.copyOf(enums);
    this.nestedMessages = List.copyOf(nestedMessages);
    this.oneofGroups = List.copyOf(oneofGroups);
    // Compute max field number without Stream overhead
    int max = 0;
    for (ProtoField f : fields) {
      if (f.getFieldNumber() > max) max = f.getFieldNumber();
    }
    this.maxFieldNumber = max;
    // Array-indexed position lookup: no boxing, no HashMap overhead
    ProtoField[] posArr = new ProtoField[max];
    for (ProtoField field : fields) {
      posArr[field.getArrayPosition()] = field;
    }
    this.fieldsByPosition = posArr;
    this.comment = comment;
  }

  public String getName() {
    return name;
  }

  public String getFullName() {
    return fullName;
  }

  public List<ProtoField> getFields() {
    return fields;
  }

  public List<ProtoEnum> getEnums() {
    return enums;
  }

  public List<ProtoMessage> getNestedMessages() {
    return nestedMessages;
  }

  public List<OneofGroup> getOneofGroups() {
    return oneofGroups;
  }

  public int getMaxFieldNumber() {
    return maxFieldNumber;
  }

  public String getComment() {
    return comment;
  }

  /** Returns the field at a given array position, or null if that position is a gap. O(1). */
  public ProtoField fieldAtPosition(int position) {
    if (position < 0 || position >= fieldsByPosition.length) return null;
    return fieldsByPosition[position];
  }

  /**
   * Returns a map view from array position (0-based) to the field at that position. Positions with
   * no field (gaps in field numbering) are absent from the map. Allocates a new map on each call —
   * prefer {@link #fieldAtPosition(int)} for hot paths.
   */
  public Map<Integer, ProtoField> getFieldsByPosition() {
    java.util.Map<Integer, ProtoField> map = new java.util.HashMap<>();
    for (int i = 0; i < fieldsByPosition.length; i++) {
      if (fieldsByPosition[i] != null) map.put(i, fieldsByPosition[i]);
    }
    return java.util.Collections.unmodifiableMap(map);
  }

  public record OneofGroup(String name, int index, List<ProtoField> members) {
    public OneofGroup(String name, int index, List<ProtoField> members) {
      this.name = name;
      this.index = index;
      this.members = List.copyOf(members);
    }
  }
}
