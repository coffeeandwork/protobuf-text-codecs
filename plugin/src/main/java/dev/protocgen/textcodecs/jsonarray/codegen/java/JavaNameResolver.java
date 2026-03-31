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
package dev.protocgen.textcodecs.jsonarray.codegen.java;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/** Converts proto names to idiomatic Java names. */
public class JavaNameResolver implements NameResolver {

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
    return KeywordUtil.escapeJava(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    return KeywordUtil.escapeJava(snakeToCamel(protoFieldName));
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
    // Proto enum constants are UPPER_SNAKE_CASE, same as Java convention
    return KeywordUtil.escapeJava(protoName);
  }

  @Override
  public String fileExtension() {
    return ".java";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // Convert ".example.sub.MessageName" to "com.example.sub.MessageName"
    // using the java_package of the file that defines the type.
    // For simplicity, if the type is in the same package, use the simple name.
    // Full cross-package resolution would need the TypeRegistry to look up
    // the defining file's java_package. For now, strip the leading dot and
    // use the proto package structure.
    if (protoFullName == null) return "Object";
    String pkg = resolvePackage(currentFile);
    String currentPrefix =
        currentFile.getProtoPackage().isEmpty() ? "." : "." + currentFile.getProtoPackage() + ".";

    if (protoFullName.startsWith(currentPrefix)) {
      // Same package — use the simple name. Nested messages are generated as top-level
      // classes (not inner classes), so no dot-to-$ conversion is needed.
      return protoFullName.substring(currentPrefix.length());
    }

    // Cross-package: convert proto full name to Java qualified name
    // Strip leading dot, the proto package becomes the Java package
    String withoutDot = protoFullName.substring(1); // remove leading "."
    return withoutDot;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String dir = pkg.replace('.', '/');
    return dir + "/" + messageName + ".java";
  }

  /** Extract the simple (unqualified) type name from a proto full name. */
  public static String simpleTypeName(String protoFullName) {
    if (protoFullName == null) return "Object";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
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
