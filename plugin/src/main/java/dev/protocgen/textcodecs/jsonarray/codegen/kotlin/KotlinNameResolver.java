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
package dev.protocgen.textcodecs.jsonarray.codegen.kotlin;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/** Converts proto names to idiomatic Kotlin names. */
public class KotlinNameResolver implements NameResolver {

  /** Escape a Kotlin identifier — delegates to {@link KeywordUtil#escapeKotlin(String)}. */
  public static String escapeKotlin(String name) {
    return KeywordUtil.escapeKotlin(name);
  }

  @Override
  public String resolvePackage(ProtoFile file) {
    if (file.getJavaPackage() != null && !file.getJavaPackage().isEmpty()) {
      return file.getJavaPackage();
    }
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention
    return escapeKotlin(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    return escapeKotlin(snakeToCamel(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    // Kotlin uses property syntax, but we use get* for compatibility with the builder pattern
    return "get" + snakeToPascal(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    return "set" + snakeToPascal(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE, same as Kotlin convention
    return escapeKotlin(protoName);
  }

  @Override
  public String fileExtension() {
    return ".kt";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    if (protoFullName == null) return "Any";
    String currentPrefix =
        currentFile.getProtoPackage().isEmpty() ? "." : "." + currentFile.getProtoPackage() + ".";

    if (protoFullName.startsWith(currentPrefix)) {
      // Same package -- use simple name
      return protoFullName.substring(currentPrefix.length());
    }

    // Cross-package: strip leading dot
    return protoFullName.substring(1);
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String dir = pkg.replace('.', '/');
    return dir + "/" + messageName + ".kt";
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
    if (camel.isEmpty()) return camel;
    return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
  }
}
