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
package dev.protocgen.textcodecs.jsonarray.codegen.c;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic C names. Uses snake_case for everything and prefixes with the
 * proto package to avoid collisions.
 */
public class CNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // PascalCase message name stays PascalCase for the typedef name.
    // The package prefix is added at the call site when building the full qualified C type.
    return KeywordUtil.escapeC(protoName);
  }

  /**
   * Build the fully-qualified C struct/typedef name: package_MessageName. E.g., package "example",
   * message "User" -> "example_User".
   */
  public String qualifiedTypeName(String protoPackage, String protoName) {
    if (protoPackage == null || protoPackage.isEmpty()) {
      return protoName;
    }
    return protoPackage.replace('.', '_') + "_" + protoName;
  }

  /**
   * Build the snake_case function prefix for a message. E.g., package "example", message "User" ->
   * "example_user".
   */
  public String functionPrefix(String protoPackage, String messageName) {
    String snake = pascalToSnake(messageName);
    if (protoPackage == null || protoPackage.isEmpty()) {
      return snake;
    }
    return protoPackage.replace('.', '_') + "_" + snake;
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Proto field names are already snake_case by convention
    return KeywordUtil.escapeC(protoFieldName);
  }

  @Override
  public String getterName(String protoFieldName) {
    // C does not use getters; direct field access
    return KeywordUtil.escapeC(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    // C does not use setters; direct field access
    return KeywordUtil.escapeC(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are already UPPER_SNAKE_CASE
    return KeywordUtil.escapeC(protoName);
  }

  @Override
  public String fileExtension() {
    return ".h";
  }

  /** Returns the .h file extension for headers. */
  public String headerExtension() {
    return ".h";
  }

  /** Returns the .c file extension for implementations. */
  public String sourceExtension() {
    return ".c";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // Convert ".example.sub.MessageName" to "example_sub_MessageName"
    if (protoFullName == null) return "void*";
    String withoutDot = protoFullName.startsWith(".") ? protoFullName.substring(1) : protoFullName;
    return withoutDot.replace('.', '_');
  }

  /**
   * Resolve a type reference to a snake_case function prefix. E.g., ".example.User" ->
   * "example_user"
   */
  public String resolveTypeFunctionPrefix(String protoFullName) {
    if (protoFullName == null) return "unknown";
    String withoutDot = protoFullName.startsWith(".") ? protoFullName.substring(1) : protoFullName;
    // Split by '.', snake_case the last component (message name), keep package parts as-is
    int lastDot = withoutDot.lastIndexOf('.');
    if (lastDot < 0) {
      return pascalToSnake(withoutDot);
    }
    String pkg = withoutDot.substring(0, lastDot).replace('.', '_');
    String name = pascalToSnake(withoutDot.substring(lastDot + 1));
    return pkg + "_" + name;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String prefix = filePrefix(file, messageName);
    return prefix + ".h";
  }

  /** Returns the output path for the .c implementation file. */
  public String outputSourcePath(ProtoFile file, String messageName) {
    String prefix = filePrefix(file, messageName);
    return prefix + ".c";
  }

  /** Returns the path prefix (without extension) for output files. */
  private String filePrefix(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String snakeName = pascalToSnake(messageName);
    if (pkg == null || pkg.isEmpty()) {
      return snakeName;
    }
    String dir = pkg.replace('.', '/');
    return dir + "/" + snakeName;
  }

  /** Build the include guard macro name. E.g., "example/user.h" -> "EXAMPLE_USER_H" */
  public String includeGuard(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String snakeName = pascalToSnake(messageName).toUpperCase();
    if (pkg == null || pkg.isEmpty()) {
      return snakeName + "_H";
    }
    return pkg.replace('.', '_').toUpperCase() + "_" + snakeName + "_H";
  }

  /** Convert PascalCase to snake_case. E.g., "UserProfile" -> "user_profile" */
  public static String pascalToSnake(String pascal) {
    if (pascal == null || pascal.isEmpty()) return pascal;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pascal.length(); i++) {
      char c = pascal.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          // Don't insert underscore between consecutive uppercase (e.g., "ID" stays "id")
          char prev = pascal.charAt(i - 1);
          if (!Character.isUpperCase(prev)) {
            sb.append('_');
          } else if (i + 1 < pascal.length() && !Character.isUpperCase(pascal.charAt(i + 1))) {
            // Handle "USAUser" -> "usa_user": insert before U when next is lowercase
            sb.append('_');
          }
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
