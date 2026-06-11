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
package dev.protocgen.textcodecs.jsonarray.codegen.java;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes Java import statements for cross-package type references. Generated code references
 * message/enum types by simple name, which only resolves across Java packages via an import. Shared
 * by the jsonarray and pbtkurl Java emitters.
 */
public final class JavaImportUtil {

  private JavaImportUtil() {}

  /**
   * Collect import statements for message/enum types referenced by this message (including its
   * nested messages) that are defined in a different Java package. Well-known types are excluded
   * (they use special encodings, never a generated class). Returns an empty set when registry is
   * null.
   */
  public static Set<String> collectCrossPackageImports(
      ProtoMessage message, ProtoFile file, TypeRegistry registry, JavaNameResolver nameResolver) {
    Set<String> imports = new TreeSet<>();
    if (registry == null) {
      return imports;
    }
    String currentPkg = nameResolver.resolvePackage(file);
    collectTypeRefImports(message, registry, currentPkg, nameResolver, imports);
    return imports;
  }

  private static void collectTypeRefImports(
      ProtoMessage message,
      TypeRegistry registry,
      String currentPkg,
      JavaNameResolver nameResolver,
      Set<String> imports) {
    for (ProtoField field : message.getFields()) {
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.ENUM) {
        addCrossPackageImport(
            field.getTypeReference(), registry, currentPkg, nameResolver, imports);
      }
      if (field.isMap()
          && (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
              || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM)) {
        addCrossPackageImport(
            field.getMapValueTypeReference(), registry, currentPkg, nameResolver, imports);
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectTypeRefImports(nested, registry, currentPkg, nameResolver, imports);
    }
  }

  private static void addCrossPackageImport(
      String typeRef,
      TypeRegistry registry,
      String currentPkg,
      JavaNameResolver nameResolver,
      Set<String> imports) {
    if (typeRef == null || typeRef.startsWith(".google.protobuf.")) {
      return;
    }
    FileDescriptorProto definingFile = registry.getDefiningFile(typeRef);
    if (definingFile == null) {
      return;
    }
    String defPkg =
        definingFile.getOptions().getJavaPackage().isEmpty()
            ? definingFile.getPackage()
            : definingFile.getOptions().getJavaPackage();
    if (defPkg.isEmpty() || defPkg.equals(currentPkg)) {
      return;
    }
    // Import the top-level class: the first name segment after the proto package. Nested types
    // are inner classes of it.
    String protoPkgPrefix =
        definingFile.getPackage().isEmpty() ? "." : "." + definingFile.getPackage() + ".";
    String path =
        typeRef.startsWith(protoPkgPrefix)
            ? typeRef.substring(protoPkgPrefix.length())
            : typeRef.substring(1);
    int dot = path.indexOf('.');
    String topLevel = dot >= 0 ? path.substring(0, dot) : path;
    imports.add(defPkg + "." + nameResolver.messageClassName(topLevel));
  }
}
