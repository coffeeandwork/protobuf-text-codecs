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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Global registry mapping fully-qualified proto type names to their descriptors. Built from all
 * FileDescriptorProto entries in the CodeGeneratorRequest.
 */
public class TypeRegistry {

  /** Maximum allowed nesting depth for recursive message registration. */
  private static final int MAX_NESTING_DEPTH = 64;

  private final Map<String, DescriptorProto> messageTypes = new HashMap<>();
  private final Map<String, EnumDescriptorProto> enumTypes = new HashMap<>();
  private final Map<String, FileDescriptorProto> filesByName = new HashMap<>();

  /** Register all types from a FileDescriptorProto. */
  public void registerFile(FileDescriptorProto file) {
    filesByName.put(file.getName(), file);
    String prefix = file.getPackage().isEmpty() ? "." : "." + file.getPackage() + ".";

    for (DescriptorProto message : file.getMessageTypeList()) {
      registerMessage(prefix, message, 0);
    }
    for (EnumDescriptorProto enumType : file.getEnumTypeList()) {
      enumTypes.put(prefix + enumType.getName(), enumType);
    }
  }

  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

  private void registerMessage(String prefix, DescriptorProto message, int depth) {
    if (depth > MAX_NESTING_DEPTH) {
      throw new IllegalArgumentException(
          "Message nesting depth exceeds maximum of "
              + MAX_NESTING_DEPTH
              + " at '"
              + prefix
              + message.getName()
              + "'. This may indicate a circular or excessively deep message definition.");
    }

    // Validate message name at registration time (defense-in-depth, VULN-002)
    validateIdentifier(message.getName(), "message");
    String fullName = prefix + message.getName();
    messageTypes.put(fullName, message);

    // Register nested types
    for (DescriptorProto nested : message.getNestedTypeList()) {
      registerMessage(fullName + ".", nested, depth + 1);
    }
    for (EnumDescriptorProto enumType : message.getEnumTypeList()) {
      validateIdentifier(enumType.getName(), "enum");
      enumTypes.put(fullName + "." + enumType.getName(), enumType);
    }
  }

  private void validateIdentifier(String name, String kind) {
    if (name != null && !SAFE_IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException(
          kind
              + " name '"
              + name
              + "' contains invalid characters. Names must match [a-zA-Z_][a-zA-Z0-9_]*.");
    }
  }

  public DescriptorProto getMessage(String fullName) {
    return messageTypes.get(fullName);
  }

  public EnumDescriptorProto getEnum(String fullName) {
    return enumTypes.get(fullName);
  }

  public FileDescriptorProto getFile(String fileName) {
    return filesByName.get(fileName);
  }

  /** Check if a message type is a synthetic map-entry message. */
  public boolean isMapEntry(String fullName) {
    DescriptorProto msg = messageTypes.get(fullName);
    return msg != null && msg.getOptions().getMapEntry();
  }
}
