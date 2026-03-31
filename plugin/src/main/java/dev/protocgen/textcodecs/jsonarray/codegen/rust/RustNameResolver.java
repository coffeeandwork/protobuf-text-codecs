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
package dev.protocgen.textcodecs.jsonarray.codegen.rust;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Rust names. Fields/methods use snake_case, types use
 * PascalCase, files use .rs extension.
 */
public class RustNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // Rust uses the proto package as a module path
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention, which is also Rust convention
    return KeywordUtil.escapeRust(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Proto field names are already snake_case by convention, which is also Rust convention
    return KeywordUtil.escapeRust(toSnakeCase(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    // Rust getters are just the field name (no get_ prefix by convention)
    return KeywordUtil.escapeRust(toSnakeCase(protoFieldName));
  }

  @Override
  public String setterName(String protoFieldName) {
    return "set_" + toSnakeCase(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Convert UPPER_SNAKE_CASE to PascalCase for Rust enum variants
    return KeywordUtil.escapeRust(snakeToPascal(protoName));
  }

  @Override
  public String fileExtension() {
    return ".rs";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    if (protoFullName == null) return "()";
    // For types in the same package, use the simple name
    String currentPrefix =
        currentFile.getProtoPackage().isEmpty() ? "." : "." + currentFile.getProtoPackage() + ".";

    if (protoFullName.startsWith(currentPrefix)) {
      String relativeName = protoFullName.substring(currentPrefix.length());
      return relativeName;
    }

    // Cross-package: convert proto full name to Rust module::Type path
    // Strip leading dot, replace dots with ::
    String withoutDot = protoFullName.substring(1);
    int lastDot = withoutDot.lastIndexOf('.');
    if (lastDot >= 0) {
      String modulePath = withoutDot.substring(0, lastDot).replace('.', ':').replaceAll(":", "::");
      String typeName = withoutDot.substring(lastDot + 1);
      return modulePath + "::" + typeName;
    }
    return withoutDot;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    if (pkg.isEmpty()) {
      return toSnakeCase(messageName) + ".rs";
    }
    String dir = pkg.replace('.', '/');
    return dir + "/" + toSnakeCase(messageName) + ".rs";
  }

  /**
   * Convert a string to snake_case. Handles PascalCase, camelCase, and already-snake_case inputs.
   */
  public static String toSnakeCase(String name) {
    if (name == null || name.isEmpty()) return name;
    // If it's already snake_case (contains underscores and is lowercase), return as-is
    if (name.contains("_") && name.equals(name.toLowerCase())) {
      return name;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_') {
        sb.append('_');
      } else if (Character.isUpperCase(c)) {
        if (i > 0 && name.charAt(i - 1) != '_') {
          sb.append('_');
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Convert UPPER_SNAKE_CASE or snake_case to PascalCase. */
  static String snakeToPascal(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = true;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_') {
        nextUpper = true;
      } else if (nextUpper) {
        sb.append(Character.toUpperCase(c));
        nextUpper = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }
}
