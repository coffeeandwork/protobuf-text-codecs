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
package dev.protocgen.textcodecs.jsonarray.codegen.swift;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Swift names. Swift uses PascalCase for types and camelCase for
 * properties and functions.
 */
public class SwiftNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // Swift doesn't have packages in the traditional sense; use the proto package
    // to create a directory structure.
    String pkg = file.getProtoPackage();
    if (pkg == null || pkg.isEmpty()) {
      String fileName = file.getFileName();
      int slash = fileName.lastIndexOf('/');
      if (slash >= 0) {
        fileName = fileName.substring(slash + 1);
      }
      int dot = fileName.lastIndexOf('.');
      if (dot >= 0) {
        fileName = fileName.substring(0, dot);
      }
      return fileName.toLowerCase();
    }
    // Use the last segment of the proto package for the directory.
    // e.g., "com.example.user" -> "user"
    int lastDot = pkg.lastIndexOf('.');
    if (lastDot >= 0) {
      return pkg.substring(lastDot + 1).toLowerCase();
    }
    return pkg.toLowerCase();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention, which is
    // exactly what Swift wants for struct/class types.
    return KeywordUtil.escapeSwift(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Swift properties use camelCase
    return KeywordUtil.escapeSwift(snakeToCamel(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    // Swift uses direct property access, no getter methods.
    return KeywordUtil.escapeSwift(snakeToCamel(protoFieldName));
  }

  @Override
  public String setterName(String protoFieldName) {
    // Swift uses direct property assignment, no setter methods.
    return KeywordUtil.escapeSwift(snakeToCamel(protoFieldName));
  }

  @Override
  public String enumConstantName(String protoName) {
    // Swift enum cases use camelCase, but proto enum constants are UPPER_SNAKE_CASE.
    // Keep as-is for clarity, since they're used as static constants.
    return KeywordUtil.escapeSwift(protoName);
  }

  @Override
  public String fileExtension() {
    return ".swift";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // For same-package types, use the simple name.
    if (protoFullName == null) return "Any";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    // Swift files are PascalCase. Place in a directory matching the package.
    String pkg = resolvePackage(file);
    return pkg + "/" + messageName + ".swift";
  }

  /** Convert snake_case to camelCase (Swift property name). */
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

  /** Convert snake_case to PascalCase (Swift type name). */
  static String snakeToPascal(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = true; // first char is uppercase
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

  /** Convert PascalCase/camelCase to snake_case (for file names if needed). */
  static String camelToSnake(String camel) {
    if (camel == null || camel.isEmpty()) return camel;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < camel.length(); i++) {
      char c = camel.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          sb.append('_');
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
