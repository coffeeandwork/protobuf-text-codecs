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
package dev.protocgen.textcodecs.jsonarray.codegen.zig;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Zig names. Fields and functions use snake_case; types use
 * PascalCase.
 */
public class ZigNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // Zig doesn't have a package system like Java; use the proto package
    // to namespace output directories.
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention, which matches Zig types.
    return KeywordUtil.escapeZig(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Proto field names are already snake_case, which is Zig convention.
    return KeywordUtil.escapeZig(protoFieldName);
  }

  @Override
  public String getterName(String protoFieldName) {
    // Zig structs use direct field access; no getters needed.
    return KeywordUtil.escapeZig(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    // Zig structs use direct field access; no setters needed.
    return KeywordUtil.escapeZig(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Convert UPPER_SNAKE_CASE to snake_case for idiomatic Zig enum literals.
    return KeywordUtil.escapeZig(toSnakeCase(protoName));
  }

  @Override
  public String fileExtension() {
    return ".zig";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // For Zig, since all types are generated as top-level structs in the same
    // output directory, we use the simple type name. Cross-file imports would
    // use @import, but within a single generated file we use direct names.
    return simpleTypeName(protoFullName);
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String dir = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
    return dir + toSnakeCase(messageName) + ".zig";
  }

  /** Convert PascalCase or UPPER_SNAKE_CASE to snake_case. */
  public static String toSnakeCase(String name) {
    if (name == null || name.isEmpty()) return name;

    // If already UPPER_SNAKE_CASE (contains underscores and is all uppercase),
    // simply lowercase it.
    if (name.contains("_")) {
      return name.toLowerCase();
    }

    // PascalCase to snake_case
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          // Check if previous char is lowercase or next char is lowercase
          // to handle acronyms like "HTTPServer" -> "http_server"
          char prev = name.charAt(i - 1);
          boolean nextIsLower = i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
          if (Character.isLowerCase(prev) || (Character.isUpperCase(prev) && nextIsLower)) {
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

  /** Extract the simple type name from a fully-qualified proto type reference. */
  public static String simpleTypeName(String protoFullName) {
    String simple = ProtoTypeUtil.simpleTypeName(protoFullName);
    return simple != null ? simple : "void";
  }
}
