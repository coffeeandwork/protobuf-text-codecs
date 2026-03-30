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
package dev.protocgen.textcodecs.jsonarray.codegen.csharp;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic C# names. C# uses PascalCase for types, properties, and
 * methods, and camelCase for local variables and parameters.
 */
public class CSharpNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // C# uses namespaces. Derive from the proto package, converting to PascalCase segments.
    // E.g., "example.user" -> "Example.User"
    String pkg = file.getProtoPackage();
    if (pkg == null || pkg.isEmpty()) {
      return "";
    }
    String[] parts = pkg.split("\\.");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) sb.append('.');
      sb.append(capitalize(parts[i]));
    }
    return sb.toString();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention
    return KeywordUtil.escapeCSharp(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // C# properties use PascalCase, but for backing fields we use camelCase
    return KeywordUtil.escapeCSharp(snakeToCamel(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    // C# uses properties, not getter methods. Return the PascalCase property name.
    return snakeToPascal(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    // C# uses properties, not setter methods. Return the PascalCase property name
    // prefixed with "Set" for builder methods.
    return "Set" + snakeToPascal(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE, same convention works in C#
    return KeywordUtil.escapeCSharp(protoName);
  }

  @Override
  public String fileExtension() {
    return ".cs";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    if (protoFullName == null) return "object";
    String currentPrefix =
        currentFile.getProtoPackage().isEmpty() ? "." : "." + currentFile.getProtoPackage() + ".";

    if (protoFullName.startsWith(currentPrefix)) {
      // Same package -- use the simple name
      return protoFullName.substring(currentPrefix.length());
    }

    // Cross-package: convert proto full name to C# namespace-qualified name
    String withoutDot = protoFullName.substring(1); // remove leading "."
    return withoutDot;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String ns = resolvePackage(file);
    String dir = ns.replace('.', '/');
    if (dir.isEmpty()) {
      return messageName + ".cs";
    }
    return dir + "/" + messageName + ".cs";
  }

  /** Convert snake_case to camelCase. */
  static String snakeToCamel(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = false;
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

  /** Convert snake_case to PascalCase. */
  public static String snakeToPascal(String snake) {
    String camel = snakeToCamel(snake);
    if (camel == null || camel.isEmpty()) return camel;
    return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
  }

  /** Capitalize the first letter of a string. */
  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
