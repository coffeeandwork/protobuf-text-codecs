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
package dev.protocgen.textcodecs.jsonarray.codegen.ruby;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Ruby names. Ruby uses PascalCase for classes/modules,
 * snake_case for methods/variables, SCREAMING_SNAKE_CASE for constants, .rb extension.
 */
public class RubyNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // Ruby uses the proto package directly as a dotted module path
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase, which is also Ruby convention
    return KeywordUtil.escapeRuby(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Proto field names are already snake_case, which is Ruby convention
    return KeywordUtil.escapeRuby(protoFieldName);
  }

  @Override
  public String getterName(String protoFieldName) {
    // Ruby uses attr_accessor — the getter name is the same as the field name
    return KeywordUtil.escapeRuby(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    // Ruby uses attr_accessor — the setter name is the same as the field name
    return KeywordUtil.escapeRuby(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE, same as Ruby constant convention
    return KeywordUtil.escapeRuby(protoName);
  }

  @Override
  public String fileExtension() {
    return ".rb";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // Return the simple type name — Ruby imports are handled at file level via require_relative
    if (protoFullName == null) return "Object";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String dir = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
    return dir + pascalToSnake(messageName) + ".rb";
  }

  /** Convert PascalCase to snake_case for Ruby file names. */
  static String pascalToSnake(String pascal) {
    if (pascal == null || pascal.isEmpty()) return pascal;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pascal.length(); i++) {
      char c = pascal.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          // Only add underscore if previous char is lowercase, or next char is lowercase
          // This handles runs of capitals like "HTTPServer" -> "http_server"
          char prev = pascal.charAt(i - 1);
          boolean nextIsLower =
              i + 1 < pascal.length() && Character.isLowerCase(pascal.charAt(i + 1));
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
}
