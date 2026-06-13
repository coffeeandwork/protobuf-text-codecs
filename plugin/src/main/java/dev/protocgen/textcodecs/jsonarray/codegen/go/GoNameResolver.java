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
package dev.protocgen.textcodecs.jsonarray.codegen.go;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Go names. Go uses PascalCase for exported identifiers (fields,
 * methods, types).
 */
public class GoNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // Honor the go_package option when present (its explicit name or last import-path segment),
    // otherwise derive the package from the proto package or the file's base name.
    return GoImportUtil.packageName(
        file.getGoPackage(), file.getProtoPackage(), file.getFileName());
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention, which is
    // exactly what Go wants for exported types.
    return KeywordUtil.escapeGo(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Go exported fields use PascalCase
    return KeywordUtil.escapeGo(snakeToPascal(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    // Go doesn't use getter methods; direct field access via PascalCase field name.
    return KeywordUtil.escapeGo(snakeToPascal(protoFieldName));
  }

  @Override
  public String setterName(String protoFieldName) {
    // Go doesn't use setter methods; direct field assignment.
    return KeywordUtil.escapeGo(snakeToPascal(protoFieldName));
  }

  @Override
  public String enumConstantName(String protoName) {
    // Go enum constants are PascalCase. Proto constants like "STATUS_ACTIVE"
    // are kept as-is for clarity (they're used as Go const names prefixed with type).
    return KeywordUtil.escapeGo(protoName);
  }

  @Override
  public String fileExtension() {
    return ".go";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // For same-package types, use the simple name.
    // Cross-package references would require import management; for now, use simple name.
    if (protoFullName == null) return "interface{}";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    // Go files are typically lowercase. Place in a directory matching the package.
    String pkg = resolvePackage(file);
    // Convert message name to lowercase for file name (Go convention)
    String fileName = camelToSnake(messageName);
    return pkg + "/" + fileName + ".go";
  }

  /** Convert snake_case to PascalCase (Go exported name). */
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

  /** Convert snake_case to camelCase (Go unexported name). */
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

  /** Convert PascalCase/camelCase to snake_case (for file names). */
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
