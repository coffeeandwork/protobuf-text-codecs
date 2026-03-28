package dev.protocgen.textcodecs.jsonarray.model;

import java.util.Map;

/** Enum of well-known protobuf types that receive special serialization treatment. */
public enum WellKnownType {
  TIMESTAMP(".google.protobuf.Timestamp"),
  DURATION(".google.protobuf.Duration"),
  BOOL_VALUE(".google.protobuf.BoolValue"),
  INT32_VALUE(".google.protobuf.Int32Value"),
  INT64_VALUE(".google.protobuf.Int64Value"),
  UINT32_VALUE(".google.protobuf.UInt32Value"),
  UINT64_VALUE(".google.protobuf.UInt64Value"),
  FLOAT_VALUE(".google.protobuf.FloatValue"),
  DOUBLE_VALUE(".google.protobuf.DoubleValue"),
  STRING_VALUE(".google.protobuf.StringValue"),
  BYTES_VALUE(".google.protobuf.BytesValue"),
  STRUCT(".google.protobuf.Struct"),
  VALUE(".google.protobuf.Value"),
  LIST_VALUE(".google.protobuf.ListValue"),
  FIELD_MASK(".google.protobuf.FieldMask"),
  EMPTY(".google.protobuf.Empty"),
  ANY(".google.protobuf.Any");

  private final String fullName;

  WellKnownType(String fullName) {
    this.fullName = fullName;
  }

  public String getFullName() {
    return fullName;
  }

  private static final Map<String, WellKnownType> BY_NAME;

  static {
    var map = new java.util.HashMap<String, WellKnownType>();
    for (WellKnownType wkt : values()) {
      map.put(wkt.fullName, wkt);
    }
    BY_NAME = Map.copyOf(map);
  }

  /**
   * Look up a well-known type by its fully-qualified proto type name.
   *
   * @return the WellKnownType, or null if not a well-known type
   */
  public static WellKnownType fromTypeName(String typeName) {
    return BY_NAME.get(typeName);
  }

  public boolean isWrapperType() {
    return switch (this) {
      case BOOL_VALUE,
          INT32_VALUE,
          INT64_VALUE,
          UINT32_VALUE,
          UINT64_VALUE,
          FLOAT_VALUE,
          DOUBLE_VALUE,
          STRING_VALUE,
          BYTES_VALUE ->
          true;
      default -> false;
    };
  }
}
