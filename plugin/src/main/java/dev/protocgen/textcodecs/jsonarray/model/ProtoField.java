/*
 * Copyright 2026 protobuf-text-codecs contributors
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

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;

/**
 * Language-neutral representation of a single proto field, normalized for code generation. Fields
 * are positioned by field number (position = fieldNumber - 1).
 */
public class ProtoField {

  public enum Cardinality {
    SINGULAR,
    REPEATED,
    MAP
  }

  public enum FieldKind {
    SCALAR,
    MESSAGE,
    ENUM,
    WELL_KNOWN_TYPE
  }

  private final String name;
  private final int fieldNumber;
  private final int arrayPosition; // fieldNumber - 1
  private final FieldDescriptorProto.Type protoType;
  private final FieldKind kind;
  private final Cardinality cardinality;
  private final String
      typeReference; // fully-qualified type name for MESSAGE/ENUM, null for scalars
  private final boolean proto3Optional; // explicit presence tracking
  private final boolean required; // proto2 required label
  private final String defaultValue; // schema-specified default value (proto2), null if none
  private final boolean
      hasExplicitPresence; // true for all proto2 fields and proto3 optional fields
  private final int oneofIndex; // -1 if not part of a oneof
  private final String oneofName; // null if not part of a oneof
  private final WellKnownType wellKnownType; // null if not a well-known type
  private final String comment; // leading comment from the proto source, null if none

  // For map fields: key and value types
  private final FieldDescriptorProto.Type mapKeyType;
  private final FieldDescriptorProto.Type mapValueType;
  private final String mapValueTypeReference; // for MESSAGE/ENUM map values

  private ProtoField(Builder builder) {
    this.name = builder.name;
    this.fieldNumber = builder.fieldNumber;
    this.arrayPosition = builder.fieldNumber - 1;
    this.protoType = builder.protoType;
    this.kind = builder.kind;
    this.cardinality = builder.cardinality;
    this.typeReference = builder.typeReference;
    this.proto3Optional = builder.proto3Optional;
    this.required = builder.required;
    this.defaultValue = builder.defaultValue;
    this.hasExplicitPresence = builder.hasExplicitPresence;
    this.oneofIndex = builder.oneofIndex;
    this.oneofName = builder.oneofName;
    this.wellKnownType = builder.wellKnownType;
    this.comment = builder.comment;
    this.mapKeyType = builder.mapKeyType;
    this.mapValueType = builder.mapValueType;
    this.mapValueTypeReference = builder.mapValueTypeReference;
  }

  public String getName() {
    return name;
  }

  public int getFieldNumber() {
    return fieldNumber;
  }

  public int getArrayPosition() {
    return arrayPosition;
  }

  public FieldDescriptorProto.Type getProtoType() {
    return protoType;
  }

  public FieldKind getKind() {
    return kind;
  }

  public Cardinality getCardinality() {
    return cardinality;
  }

  public String getTypeReference() {
    return typeReference;
  }

  public boolean isProto3Optional() {
    return proto3Optional;
  }

  public boolean isRequired() {
    return required;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public boolean hasExplicitPresence() {
    return hasExplicitPresence;
  }

  public int getOneofIndex() {
    return oneofIndex;
  }

  public String getOneofName() {
    return oneofName;
  }

  public WellKnownType getWellKnownType() {
    return wellKnownType;
  }

  public String getComment() {
    return comment;
  }

  public FieldDescriptorProto.Type getMapKeyType() {
    return mapKeyType;
  }

  public FieldDescriptorProto.Type getMapValueType() {
    return mapValueType;
  }

  public String getMapValueTypeReference() {
    return mapValueTypeReference;
  }

  public boolean isMap() {
    return cardinality == Cardinality.MAP;
  }

  public boolean isRepeated() {
    return cardinality == Cardinality.REPEATED;
  }

  public boolean isSingular() {
    return cardinality == Cardinality.SINGULAR;
  }

  public boolean isOneofMember() {
    return oneofIndex >= 0;
  }

  public boolean isWellKnownType() {
    return wellKnownType != null;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String name;
    private int fieldNumber;
    private FieldDescriptorProto.Type protoType;
    private FieldKind kind = FieldKind.SCALAR;
    private Cardinality cardinality = Cardinality.SINGULAR;
    private String typeReference;
    private boolean proto3Optional;
    private boolean required;
    private String defaultValue;
    private boolean hasExplicitPresence;
    private int oneofIndex = -1;
    private String oneofName;
    private WellKnownType wellKnownType;
    private String comment;
    private FieldDescriptorProto.Type mapKeyType;
    private FieldDescriptorProto.Type mapValueType;
    private String mapValueTypeReference;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder fieldNumber(int fieldNumber) {
      this.fieldNumber = fieldNumber;
      return this;
    }

    public Builder protoType(FieldDescriptorProto.Type protoType) {
      this.protoType = protoType;
      return this;
    }

    public Builder kind(FieldKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder cardinality(Cardinality cardinality) {
      this.cardinality = cardinality;
      return this;
    }

    public Builder typeReference(String typeReference) {
      this.typeReference = typeReference;
      return this;
    }

    public Builder proto3Optional(boolean proto3Optional) {
      this.proto3Optional = proto3Optional;
      return this;
    }

    public Builder required(boolean required) {
      this.required = required;
      return this;
    }

    public Builder defaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Builder hasExplicitPresence(boolean hasExplicitPresence) {
      this.hasExplicitPresence = hasExplicitPresence;
      return this;
    }

    public Builder oneofIndex(int oneofIndex) {
      this.oneofIndex = oneofIndex;
      return this;
    }

    public Builder oneofName(String oneofName) {
      this.oneofName = oneofName;
      return this;
    }

    public Builder wellKnownType(WellKnownType wellKnownType) {
      this.wellKnownType = wellKnownType;
      return this;
    }

    public Builder comment(String comment) {
      this.comment = comment;
      return this;
    }

    public Builder mapKeyType(FieldDescriptorProto.Type mapKeyType) {
      this.mapKeyType = mapKeyType;
      return this;
    }

    public Builder mapValueType(FieldDescriptorProto.Type mapValueType) {
      this.mapValueType = mapValueType;
      return this;
    }

    public Builder mapValueTypeReference(String ref) {
      this.mapValueTypeReference = ref;
      return this;
    }

    public ProtoField build() {
      if (name == null) {
        throw new IllegalStateException("ProtoField.Builder: name is required");
      }
      if (fieldNumber <= 0) {
        throw new IllegalStateException(
            "ProtoField.Builder: fieldNumber must be positive, got " + fieldNumber);
      }
      if (protoType == null) {
        throw new IllegalStateException("ProtoField.Builder: protoType is required");
      }
      return new ProtoField(this);
    }
  }
}
