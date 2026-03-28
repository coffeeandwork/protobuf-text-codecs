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
package dev.protocgen.textcodecs.jsonarray.codegen.cpp;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic C++ names. Uses snake_case for methods/fields, PascalCase for
 * classes, and namespaces from proto package.
 */
public class CppNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention
    return KeywordUtil.escapeCpp(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Proto field names are already snake_case by convention
    return KeywordUtil.escapeCpp(toSnakeCase(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    return KeywordUtil.escapeCpp(toSnakeCase(protoFieldName));
  }

  @Override
  public String setterName(String protoFieldName) {
    return "set_" + toSnakeCase(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE, keep as-is
    return KeywordUtil.escapeCpp(protoName);
  }

  @Override
  public String fileExtension() {
    return ".hpp";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // Convert ".example.sub.MessageName" to "example::sub::MessageName"
    if (protoFullName == null) return "void";
    String currentPkg = currentFile.getProtoPackage();
    String currentPrefix = currentPkg.isEmpty() ? "." : "." + currentPkg + ".";

    if (protoFullName.startsWith(currentPrefix)) {
      // Same namespace -- use simple name
      String relativeName = protoFullName.substring(currentPrefix.length());
      return relativeName.replace(".", "::");
    }

    // Cross-namespace: strip leading dot and convert dots to ::
    String withoutDot = protoFullName.substring(1); // remove leading "."
    return withoutDot.replace(".", "::");
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String dir = pkg.replace('.', '/');
    if (dir.isEmpty()) {
      return messageName + ".hpp";
    }
    return dir + "/" + messageName + ".hpp";
  }

  /**
   * Build namespace opening declarations from a proto package. E.g., "example.sub" produces
   * ["namespace example {", "namespace sub {"]
   */
  public String[] namespaceOpen(ProtoFile file) {
    String pkg = resolvePackage(file);
    if (pkg.isEmpty()) {
      return new String[0];
    }
    String[] parts = pkg.split("\\.");
    String[] result = new String[parts.length];
    for (int i = 0; i < parts.length; i++) {
      result[i] = "namespace " + parts[i];
    }
    return result;
  }

  /**
   * Build namespace closing comments. E.g., "example.sub" produces ["} // namespace sub", "} //
   * namespace example"]
   */
  public String[] namespaceClose(ProtoFile file) {
    String pkg = resolvePackage(file);
    if (pkg.isEmpty()) {
      return new String[0];
    }
    String[] parts = pkg.split("\\.");
    String[] result = new String[parts.length];
    for (int i = 0; i < parts.length; i++) {
      result[i] = "} // namespace " + parts[parts.length - 1 - i];
    }
    return result;
  }

  /**
   * Convert camelCase or PascalCase to snake_case. Also handles already-snake_case input (returns
   * as-is).
   */
  static String toSnakeCase(String name) {
    if (name == null || name.isEmpty()) return name;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
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

  /** Convert snake_case to PascalCase. */
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
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
