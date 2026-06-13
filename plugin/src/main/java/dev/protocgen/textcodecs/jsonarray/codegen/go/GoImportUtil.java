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

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves Go package names, import paths, and cross-package qualifiers from the {@code go_package}
 * file option. Go requires a module import path to reference a type defined in another package, so
 * cross-package references are only resolvable when the defining file declares {@code go_package}.
 * Shared by the jsonarray and pbtkurl Go emitters.
 */
public final class GoImportUtil {

  private GoImportUtil() {}

  /** The Go package coordinates of a proto file: the package name and its module import path. */
  record GoPackage(String name, String importPath) {}

  /**
   * Derive the Go package name from a {@code go_package} option, falling back to the proto package
   * (last dotted segment) and finally the file's base name. The package name is the part after a
   * {@code ;} in the option, or the last path segment of the import path.
   */
  public static String packageName(String goPackage, String protoPackage, String fileName) {
    String option = goPackage == null ? "" : goPackage.trim();
    if (!option.isEmpty()) {
      int semi = option.indexOf(';');
      if (semi >= 0) {
        String explicit = option.substring(semi + 1).trim();
        if (!explicit.isEmpty()) {
          return explicit;
        }
        option = option.substring(0, semi);
      }
      int slash = option.lastIndexOf('/');
      String last = slash >= 0 ? option.substring(slash + 1) : option;
      if (!last.isEmpty()) {
        return last.toLowerCase();
      }
    }
    if (protoPackage != null && !protoPackage.isEmpty()) {
      int lastDot = protoPackage.lastIndexOf('.');
      return (lastDot >= 0 ? protoPackage.substring(lastDot + 1) : protoPackage).toLowerCase();
    }
    String base = fileName == null ? "" : fileName;
    int slash = base.lastIndexOf('/');
    if (slash >= 0) {
      base = base.substring(slash + 1);
    }
    int dot = base.lastIndexOf('.');
    if (dot >= 0) {
      base = base.substring(0, dot);
    }
    return base.toLowerCase();
  }

  /** The module import path from a {@code go_package} option, or null when it is not declared. */
  public static String importPath(String goPackage) {
    if (goPackage == null || goPackage.trim().isEmpty()) {
      return null;
    }
    String option = goPackage.trim();
    int semi = option.indexOf(';');
    String path = semi >= 0 ? option.substring(0, semi) : option;
    return path.isEmpty() ? null : path;
  }

  private static GoPackage packageOf(ProtoFile file) {
    return new GoPackage(
        packageName(file.getGoPackage(), file.getProtoPackage(), file.getFileName()),
        importPath(file.getGoPackage()));
  }

  private static GoPackage packageOf(FileDescriptorProto file) {
    String goPackage = file.getOptions().hasGoPackage() ? file.getOptions().getGoPackage() : null;
    return new GoPackage(
        packageName(goPackage, file.getPackage(), file.getName()), importPath(goPackage));
  }

  private static boolean samePackage(GoPackage a, GoPackage b) {
    if (a.importPath() != null && b.importPath() != null) {
      return a.importPath().equals(b.importPath());
    }
    return a.name().equals(b.name());
  }

  /**
   * Return the package qualifier prefix (e.g. {@code "example."}) needed to reference the given
   * proto type from {@code currentFile}, or an empty string when the type is in the same package,
   * is a well-known type, or cannot be resolved.
   */
  public static String qualifier(String typeRef, ProtoFile currentFile, TypeRegistry registry) {
    if (typeRef == null || typeRef.startsWith(".google.protobuf.") || registry == null) {
      return "";
    }
    FileDescriptorProto definingFile = registry.getDefiningFile(typeRef);
    if (definingFile == null) {
      return "";
    }
    GoPackage defPkg = packageOf(definingFile);
    GoPackage curPkg = packageOf(currentFile);
    if (samePackage(curPkg, defPkg)) {
      return "";
    }
    return defPkg.name() + ".";
  }

  /**
   * Collect formatted Go import specs (e.g. {@code "smoke/example"} or {@code alias
   * "smoke/example"}) for message/enum types referenced by this message and its nested messages
   * that live in a different package. Returns an empty set when no cross-package references resolve
   * to an import path.
   */
  public static Set<String> collectCrossPackageImports(
      ProtoMessage message, ProtoFile currentFile, TypeRegistry registry) {
    Set<String> imports = new TreeSet<>();
    if (registry == null) {
      return imports;
    }
    collect(message, currentFile, registry, imports);
    return imports;
  }

  private static void collect(
      ProtoMessage message, ProtoFile currentFile, TypeRegistry registry, Set<String> imports) {
    for (ProtoField field : message.getFields()) {
      if (field.getKind() == ProtoField.FieldKind.MESSAGE
          || field.getKind() == ProtoField.FieldKind.ENUM) {
        addImport(field.getTypeReference(), currentFile, registry, imports);
      }
      if (field.isMap()
          && (field.getMapValueType() == FieldDescriptorProto.Type.TYPE_MESSAGE
              || field.getMapValueType() == FieldDescriptorProto.Type.TYPE_ENUM)) {
        addImport(field.getMapValueTypeReference(), currentFile, registry, imports);
      }
    }
    for (ProtoMessage nested : message.getNestedMessages()) {
      collect(nested, currentFile, registry, imports);
    }
  }

  private static void addImport(
      String typeRef, ProtoFile currentFile, TypeRegistry registry, Set<String> imports) {
    if (typeRef == null || typeRef.startsWith(".google.protobuf.")) {
      return;
    }
    FileDescriptorProto definingFile = registry.getDefiningFile(typeRef);
    if (definingFile == null) {
      return;
    }
    GoPackage defPkg = packageOf(definingFile);
    GoPackage curPkg = packageOf(currentFile);
    if (samePackage(curPkg, defPkg) || defPkg.importPath() == null) {
      return;
    }
    int slash = defPkg.importPath().lastIndexOf('/');
    String lastSegment =
        slash >= 0 ? defPkg.importPath().substring(slash + 1) : defPkg.importPath();
    if (defPkg.name().equals(lastSegment)) {
      imports.add("\"" + defPkg.importPath() + "\"");
    } else {
      imports.add(defPkg.name() + " \"" + defPkg.importPath() + "\"");
    }
  }
}
