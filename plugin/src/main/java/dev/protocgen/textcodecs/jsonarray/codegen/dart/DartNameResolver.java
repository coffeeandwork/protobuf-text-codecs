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
package dev.protocgen.textcodecs.jsonarray.codegen.dart;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Dart names. Fields use camelCase, classes use PascalCase, files
 * use snake_case with .dart extension.
 */
public class DartNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention
    return KeywordUtil.escapeDart(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    return KeywordUtil.escapeDart(snakeToCamel(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    return "get" + snakeToPascal(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    return "set" + snakeToPascal(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE; keep as-is for Dart
    return KeywordUtil.escapeDart(protoName);
  }

  @Override
  public String fileExtension() {
    return ".dart";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // For Dart, resolve to the simple name since we use relative imports
    if (protoFullName == null) return "Object";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String fileName = pascalToSnake(messageName) + ".dart";
    if (pkg == null || pkg.isEmpty()) {
      return fileName;
    }
    String dir = pkg.replace('.', '/');
    return dir + "/" + fileName;
  }

  /** Convert snake_case to camelCase. */
  public static String snakeToCamel(String snake) {
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

  /** Convert PascalCase to snake_case for Dart file names. */
  public static String pascalToSnake(String pascal) {
    if (pascal == null || pascal.isEmpty()) return pascal;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pascal.length(); i++) {
      char c = pascal.charAt(i);
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
