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
package dev.protocgen.textcodecs.jsonarray.codegen.php;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic PHP names. PHP uses PascalCase for classes, camelCase for
 * methods and properties, .php extension. Namespaces use backslash separators derived from the
 * proto package.
 */
public class PhpNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // PHP uses backslash-separated namespaces derived from the proto package
    // e.g., "example.user" -> "Example\User"
    String pkg = file.getProtoPackage();
    if (pkg == null || pkg.isEmpty()) return "";
    String[] parts = pkg.split("\\.");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) sb.append('\\');
      sb.append(capitalize(parts[i]));
    }
    return sb.toString();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase, which is also PHP convention
    return KeywordUtil.escapePhp(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Convert snake_case to camelCase for PHP properties
    return KeywordUtil.escapePhp(snakeToCamel(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    // PHP uses getFieldName() style getters
    return "get" + capitalize(snakeToCamel(protoFieldName));
  }

  @Override
  public String setterName(String protoFieldName) {
    // PHP uses setFieldName() style setters
    return "set" + capitalize(snakeToCamel(protoFieldName));
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE, same convention in PHP
    return KeywordUtil.escapePhp(protoName);
  }

  @Override
  public String fileExtension() {
    return ".php";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // Return the simple type name — PHP imports/use statements handle namespacing
    if (protoFullName == null) return "mixed";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String dir = pkg.isEmpty() ? "" : pkg.replace('\\', '/') + "/";
    return dir + messageName + ".php";
  }

  /**
   * Convert a proto package segment to a PHP namespace segment by capitalizing the first letter.
   * E.g., "example" -> "Example".
   */
  public static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * Convert snake_case to camelCase for PHP property/method names. E.g., "first_name" ->
   * "firstName".
   */
  static String snakeToCamel(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = false;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_') {
        nextUpper = true;
      } else {
        sb.append(nextUpper ? Character.toUpperCase(c) : c);
        nextUpper = false;
      }
    }
    return sb.toString();
  }
}
