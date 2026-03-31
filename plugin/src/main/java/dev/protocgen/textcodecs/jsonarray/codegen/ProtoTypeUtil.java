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
package dev.protocgen.textcodecs.jsonarray.codegen;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;

/**
 * Shared utility methods for proto type introspection, extracted from duplicated private helpers
 * across language-specific generators.
 */
public final class ProtoTypeUtil {

  private ProtoTypeUtil() {}

  /**
   * Extract the simple (unqualified) type name from a fully-qualified proto type reference.
   *
   * <p>Examples: {@code ".example.sub.Address"} &rarr; {@code "Address"}, {@code "User"} &rarr;
   * {@code "User"}.
   *
   * @param protoFullName the fully-qualified proto type name, or {@code null}
   * @return the simple name, or {@code null} if the input is {@code null}
   */
  public static String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return null;
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  /**
   * Returns {@code true} if the given proto type is a 64-bit integer type (int64, sint64, sfixed64,
   * uint64, or fixed64).
   */
  public static boolean isInt64Type(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_INT64
        || type == FieldDescriptorProto.Type.TYPE_SINT64
        || type == FieldDescriptorProto.Type.TYPE_SFIXED64
        || type == FieldDescriptorProto.Type.TYPE_UINT64
        || type == FieldDescriptorProto.Type.TYPE_FIXED64;
  }

  /** Returns {@code true} if the given proto type is a floating-point type (float or double). */
  public static boolean isFloatOrDoubleType(FieldDescriptorProto.Type type) {
    return type == FieldDescriptorProto.Type.TYPE_FLOAT
        || type == FieldDescriptorProto.Type.TYPE_DOUBLE;
  }
}
