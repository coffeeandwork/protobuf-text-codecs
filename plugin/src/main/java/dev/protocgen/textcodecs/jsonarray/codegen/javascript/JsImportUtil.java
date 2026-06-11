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
package dev.protocgen.textcodecs.jsonarray.codegen.javascript;

import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Computes ES-module import specs ("Symbol|relative/module/path", extensionless) for types
 * referenced from other generated files, including other proto packages. Nested types are imported
 * from their top-level container's file, where they are emitted (and exported) as additional
 * classes. Shared by the jsonarray and pbtkurl JavaScript/TypeScript emitters.
 */
public final class JsImportUtil {

  private JsImportUtil() {}

  /** Collect import specs for all types referenced by a message tree. */
  public static Set<String> collectImportSpecs(ProtoMessage message, ProtoFile file) {
    Set<String> specs = new LinkedHashSet<>();
    collect(message, file, specs);
    return specs;
  }

  private static void collect(ProtoMessage message, ProtoFile file, Set<String> specs) {
    String currentPrefix =
        file.getProtoPackage().isEmpty() ? "." : "." + file.getProtoPackage() + ".";
    for (ProtoField field : message.getFields()) {
      // A map field's own type reference is the synthetic *MapEntry message, which is
      // never generated as a file; only the value type may need an import.
      if (field.isMap()) {
        addImportSpec(field.getMapValueTypeReference(), message, file, currentPrefix, specs);
        continue;
      }
      if (field.isWellKnownType()) {
        continue;
      }
      addImportSpec(field.getTypeReference(), message, file, currentPrefix, specs);
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      collect(nested, file, specs);
    }
  }

  private static void addImportSpec(
      String typeRef,
      ProtoMessage message,
      ProtoFile file,
      String currentPrefix,
      Set<String> specs) {
    if (typeRef == null || typeRef.startsWith(".google.protobuf.")) {
      return;
    }
    // A message that references itself (recursive type) needs no import
    if (typeRef.equals(message.getFullName())) {
      return;
    }
    // A type nested anywhere under this message lives in the same file
    if (typeRef.startsWith(message.getFullName() + ".")) {
      return;
    }

    // Split the reference into package segments (lowercase by proto convention) and type
    // segments; the first type segment is the top-level container file, the last is the
    // emitted (and exported) class name.
    String withoutDot = typeRef.startsWith(".") ? typeRef.substring(1) : typeRef;
    String[] segments = withoutDot.split("\\.");
    int firstType = 0;
    while (firstType < segments.length
        && !segments[firstType].isEmpty()
        && Character.isLowerCase(segments[firstType].charAt(0))) {
      firstType++;
    }
    if (firstType >= segments.length) {
      return;
    }
    String topLevel = segments[firstType];
    String symbol = segments[segments.length - 1];

    if (typeRef.startsWith(currentPrefix)) {
      // Same package: sibling file named after the top-level container
      specs.add(symbol + "|./" + topLevel);
      return;
    }

    // Cross-package: path relative to this file's package directory
    int currentDepth =
        file.getProtoPackage().isEmpty() ? 0 : file.getProtoPackage().split("\\.").length;
    StringBuilder path = new StringBuilder();
    for (int i = 0; i < currentDepth; i++) {
      path.append("../");
    }
    for (int i = 0; i < firstType; i++) {
      path.append(segments[i]).append('/');
    }
    path.append(topLevel);
    specs.add(
        symbol + "|" + (firstType > 0 || currentDepth > 0 ? path.toString() : "./" + topLevel));
  }

  /** Format an import spec as an ES-module import statement. */
  public static String formatImport(String importSpec) {
    int sep = importSpec.indexOf('|');
    String symbol = sep < 0 ? importSpec : importSpec.substring(0, sep);
    String path = sep < 0 ? "./" + importSpec : importSpec.substring(sep + 1);
    return String.format("import { %s } from '%s.js';", symbol, path);
  }
}
