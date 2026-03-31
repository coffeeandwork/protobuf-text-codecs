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
package dev.protocgen.textcodecs.jsonarray.codegen.objc;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic Objective-C names. Uses PascalCase for classes with a 2-letter
 * prefix derived from the proto package, camelCase for properties/methods.
 */
public class ObjCNameResolver implements NameResolver {

  // Keyword set is maintained in KeywordUtil.OBJC_KEYWORDS

  @Override
  public String resolvePackage(ProtoFile file) {
    return file.getProtoPackage();
  }

  /**
   * Derive a 2-letter class prefix from the proto package. E.g., "example" -> "EX", "com.foo" ->
   * "CF", empty/null -> "PB".
   */
  public String classPrefix(ProtoFile file) {
    return classPrefix(file.getProtoPackage());
  }

  /** Derive a 2-letter class prefix from a proto package string. */
  public String classPrefix(String protoPackage) {
    if (protoPackage == null || protoPackage.isEmpty()) {
      return "PB";
    }
    // Use first two uppercase letters of the package name
    String cleaned = protoPackage.replace('.', ' ').trim();
    String[] parts = cleaned.split("[\\s._]+");
    if (parts.length >= 2) {
      return (""
              + Character.toUpperCase(parts[0].charAt(0))
              + Character.toUpperCase(parts[1].charAt(0)))
          .toUpperCase();
    }
    if (parts.length == 1 && parts[0].length() >= 2) {
      return parts[0].substring(0, 2).toUpperCase();
    }
    return "PB";
  }

  @Override
  public String messageClassName(String protoName) {
    // PascalCase, without prefix (prefix added at call sites)
    return escapeObjC(protoName);
  }

  /**
   * Build the fully-qualified Objective-C class name: PrefixMessageName. E.g., package "example",
   * message "User" -> "EXUser".
   */
  public String qualifiedClassName(ProtoFile file, String protoName) {
    return classPrefix(file) + protoName;
  }

  /** Build the qualified class name from package string and proto name. */
  public String qualifiedClassName(String protoPackage, String protoName) {
    return classPrefix(protoPackage) + protoName;
  }

  @Override
  public String fieldName(String protoFieldName) {
    // Convert snake_case to camelCase
    return escapeObjC(snakeToCamel(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    // Objective-C uses the property name as the getter
    return escapeObjC(snakeToCamel(protoFieldName));
  }

  @Override
  public String setterName(String protoFieldName) {
    // Objective-C uses setPropertyName: pattern
    String camel = snakeToCamel(protoFieldName);
    return "set" + Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Keep UPPER_SNAKE_CASE for enum constants
    return escapeObjC(protoName);
  }

  @Override
  public String fileExtension() {
    return ".h";
  }

  /** Returns the .h file extension for headers. */
  public String headerExtension() {
    return ".h";
  }

  /** Returns the .m file extension for implementations. */
  public String sourceExtension() {
    return ".m";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    if (protoFullName == null) return "NSObject";
    // Extract the package and simple name
    String withoutDot = protoFullName.startsWith(".") ? protoFullName.substring(1) : protoFullName;
    int lastDot = withoutDot.lastIndexOf('.');
    String pkg;
    String simpleName;
    if (lastDot >= 0) {
      pkg = withoutDot.substring(0, lastDot);
      simpleName = withoutDot.substring(lastDot + 1);
    } else {
      pkg = "";
      simpleName = withoutDot;
    }
    return classPrefix(pkg) + simpleName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String prefix = filePrefix(file, messageName);
    return prefix + ".h";
  }

  /** Returns the output path for the .m implementation file. */
  public String outputSourcePath(ProtoFile file, String messageName) {
    String prefix = filePrefix(file, messageName);
    return prefix + ".m";
  }

  /** Returns the path prefix (without extension) for output files. */
  private String filePrefix(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    String className = classPrefix(file) + messageName;
    if (pkg == null || pkg.isEmpty()) {
      return className;
    }
    String dir = pkg.replace('.', '/');
    return dir + "/" + className;
  }

  /** Convert snake_case to camelCase. E.g., "first_name" -> "firstName" */
  public static String snakeToCamel(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean capitalizeNext = false;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        sb.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Escape an Objective-C identifier — delegates to {@link KeywordUtil#escapeObjC(String)}. */
  public static String escapeObjC(String name) {
    return KeywordUtil.escapeObjC(name);
  }
}
