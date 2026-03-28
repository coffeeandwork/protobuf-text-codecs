package dev.protocgen.textcodecs.jsonarray.model;

import java.util.List;

/** Language-neutral representation of a proto enum definition. */
public class ProtoEnum {

  private final String name;
  private final String fullName; // fully-qualified proto name
  private final List<EnumValue> values;

  public ProtoEnum(String name, String fullName, List<EnumValue> values) {
    this.name = name;
    this.fullName = fullName;
    this.values = List.copyOf(values);
  }

  public String getName() {
    return name;
  }

  public String getFullName() {
    return fullName;
  }

  public List<EnumValue> getValues() {
    return values;
  }

  public record EnumValue(String name, int number) {}
}
