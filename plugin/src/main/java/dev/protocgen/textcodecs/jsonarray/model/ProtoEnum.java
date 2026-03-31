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
