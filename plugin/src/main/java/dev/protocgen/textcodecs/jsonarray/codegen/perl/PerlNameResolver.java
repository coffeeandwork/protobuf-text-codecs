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
package dev.protocgen.textcodecs.jsonarray.codegen.perl;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Perl names. Perl uses snake_case for fields/methods, PascalCase
 * for packages, .pm extension.
 */
public class PerlNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    // Perl uses :: as package separator; convert proto dotted package to Perl double-colon
    String pkg = file.getProtoPackage();
    if (pkg == null || pkg.isEmpty()) return "";
    // Capitalize each segment for PascalCase Perl packages
    StringBuilder sb = new StringBuilder();
    for (String segment : pkg.split("\\.")) {
      if (sb.length() > 0) sb.append("::");
      sb.append(capitalize(segment));
    }
    return sb.toString();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase, which is also Perl convention
    return KeywordUtil.escapePerl(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Proto field names are already snake_case, which is Perl convention
    return KeywordUtil.escapePerl(protoFieldName);
  }

  @Override
  public String getterName(String protoFieldName) {
    // Perl uses the same name for getter/setter (accessor method)
    return KeywordUtil.escapePerl(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    // Perl uses the same name for getter/setter (accessor method)
    return KeywordUtil.escapePerl(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE, same as Perl convention
    return KeywordUtil.escapePerl(protoName);
  }

  @Override
  public String fileExtension() {
    return ".pm";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // Return the simple type name -- Perl imports are handled at file level
    if (protoFullName == null) return "HASH";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    // Perl modules use PascalCase directory structure matching the package
    String dir = pkg.isEmpty() ? "" : pkg.replace("::", "/") + "/";
    return dir + messageName + ".pm";
  }

  /**
   * Resolve a fully-qualified proto type reference to a Perl package name. E.g., ".example.Address"
   * with package "example" becomes "Example::Address".
   */
  public String resolveFullPackageName(String protoFullName, ProtoFile currentFile) {
    if (protoFullName == null) return "HASH";
    // Strip leading dot
    String stripped = protoFullName.startsWith(".") ? protoFullName.substring(1) : protoFullName;
    // Convert dotted name to PascalCase Perl package
    StringBuilder sb = new StringBuilder();
    for (String segment : stripped.split("\\.")) {
      if (sb.length() > 0) sb.append("::");
      sb.append(capitalize(segment));
    }
    return sb.toString();
  }

  /** Capitalize the first letter of a string. */
  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
