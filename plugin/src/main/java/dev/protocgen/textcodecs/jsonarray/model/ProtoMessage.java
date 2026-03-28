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

  // Field numbers above this limit use a HashMap fallback instead of a position array,
  // preventing excessive memory allocation from sparse or malicious field numbering.
  private static final int MAX_POSITION_ARRAY_SIZE = 10_000;

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
    // Array-indexed position lookup: no boxing, no HashMap overhead.
    // Cap array size to prevent OOM from extreme field numbers (protobuf allows up to 2^29 - 1).
    if (max <= MAX_POSITION_ARRAY_SIZE) {
      ProtoField[] posArr = new ProtoField[max];
      for (ProtoField field : fields) {
        posArr[field.getArrayPosition()] = field;
      }
      this.fieldsByPosition = posArr;
    } else {
      this.fieldsByPosition = null;
    }
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
    if (position < 0) return null;
    if (fieldsByPosition != null) {
      if (position >= fieldsByPosition.length) return null;
      return fieldsByPosition[position];
    }
    // Fallback for large field numbers: linear scan
    for (ProtoField f : fields) {
      if (f.getArrayPosition() == position) return f;
    }
    return null;
  }

  /**
   * Returns a map view from array position (0-based) to the field at that position. Positions with
   * no field (gaps in field numbering) are absent from the map. Allocates a new map on each call —
   * prefer {@link #fieldAtPosition(int)} for hot paths.
   */
  public Map<Integer, ProtoField> getFieldsByPosition() {
    Map<Integer, ProtoField> map = new java.util.HashMap<>();
    if (fieldsByPosition != null) {
      for (int i = 0; i < fieldsByPosition.length; i++) {
        if (fieldsByPosition[i] != null) map.put(i, fieldsByPosition[i]);
      }
    } else {
      for (ProtoField f : fields) {
        map.put(f.getArrayPosition(), f);
      }
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
