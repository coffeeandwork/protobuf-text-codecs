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
package dev.protocgen.textcodecs.jsonarray.codegen.kotlin;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.Set;
import java.util.TreeSet;

/**
 * Collects {@code import} statements for Kotlin message files. Kotlin resolves same-package
 * top-level classes without imports, so only cross-package type references (and the top-level
 * container that owns a referenced nested type) need an explicit import.
 */
public final class KotlinImportUtil {

  private KotlinImportUtil() {}

  /** Returns the sorted set of {@code import …} statements required by {@code message}. */
  public static Set<String> collectImports(
      ProtoMessage message, ProtoFile file, TypeRegistry registry) {
    Set<String> imports = new TreeSet<>();
    if (registry == null) {
      return imports;
    }
    String currentPackage = resolvePackage(file);
    collect(message, currentPackage, registry, imports);
    return imports;
  }

  private static void collect(
      ProtoMessage message, String currentPackage, TypeRegistry registry, Set<String> imports) {
    for (ProtoField field : message.getFields()) {
      if (field.isMap()) {
        // A map field's own type reference is the synthetic *MapEntry message, which is never
        // generated; only the value type may need an import.
        addImport(field.getMapValueTypeReference(), false, currentPackage, registry, imports);
      } else {
        addImport(
            field.getTypeReference(), field.isWellKnownType(), currentPackage, registry, imports);
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      collect(nested, currentPackage, registry, imports);
    }
  }

  private static void addImport(
      String typeRef,
      boolean isWellKnown,
      String currentPackage,
      TypeRegistry registry,
      Set<String> imports) {
    if (typeRef == null || isWellKnown) {
      return;
    }
    FileDescriptorProto definingFile = registry.getDefiningFile(typeRef);
    if (definingFile == null) {
      return;
    }
    String definingPackage = javaPackageOf(definingFile);
    if (definingPackage.equals(currentPackage)) {
      // Same Kotlin package — no import needed.
      return;
    }
    String topLevel = topLevelTypeName(typeRef, definingFile.getPackage());
    if (topLevel.isEmpty()) {
      return;
    }
    String fqName = definingPackage.isEmpty() ? topLevel : definingPackage + "." + topLevel;
    imports.add("import " + fqName);
  }

  /**
   * Returns the top-level container name for a proto type reference. A reference to a nested type
   * such as {@code .pkg.Outer.Inner} is imported via its top-level owner {@code Outer}.
   */
  private static String topLevelTypeName(String typeRef, String protoPackage) {
    String prefix = protoPackage.isEmpty() ? "." : "." + protoPackage + ".";
    String relative =
        typeRef.startsWith(prefix)
            ? typeRef.substring(prefix.length())
            : (typeRef.startsWith(".") ? typeRef.substring(1) : typeRef);
    int dot = relative.indexOf('.');
    return dot >= 0 ? relative.substring(0, dot) : relative;
  }

  private static String javaPackageOf(FileDescriptorProto file) {
    if (file.getOptions().hasJavaPackage() && !file.getOptions().getJavaPackage().isEmpty()) {
      return file.getOptions().getJavaPackage();
    }
    return file.getPackage();
  }

  private static String resolvePackage(ProtoFile file) {
    if (file.getJavaPackage() != null && !file.getJavaPackage().isEmpty()) {
      return file.getJavaPackage();
    }
    return file.getProtoPackage();
  }
}
