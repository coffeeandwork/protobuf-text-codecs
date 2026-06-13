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
package dev.protocgen.textcodecs.jsonarray.codegen.cpp;

import dev.protocgen.textcodecs.jsonarray.codegen.ProtoTypeUtil;
import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared C++ name-resolution helpers used by both the JSON array and pbtk URL generators.
 *
 * <p>Every proto message and enum (including those nested inside another message) is emitted as a
 * namespace-level class/enum named by its simple name, so all same-package references use the
 * simple name and cross-package references are qualified with the defining package's namespace.
 */
public final class CppTypeUtil {

  private CppTypeUtil() {}

  /**
   * Resolve a fully-qualified proto type reference (e.g. {@code .example.Address}) to the C++ type
   * name to use from within {@code currentFile}. Same-package types — including nested types, which
   * are flattened to namespace level — use the simple name; cross-package types are prefixed with
   * the defining package rendered as a C++ namespace (e.g. {@code example::Address}).
   */
  public static String qualifiedTypeName(String typeRef, ProtoFile currentFile) {
    if (typeRef == null) return "void*";
    String simple = ProtoTypeUtil.simpleTypeName(typeRef);
    if (simple == null) return "void*";

    String pkg = currentFile.getProtoPackage();
    String currentPrefix = pkg.isEmpty() ? "." : "." + pkg + ".";
    if (typeRef.startsWith(currentPrefix)) {
      // Same package (possibly a nested type, which we flatten to namespace level).
      return simple;
    }

    // Cross-package: qualify with the defining package. The cross-referenced type is assumed to be
    // top-level, matching the file layout (<pkg>/<TypeName>.hpp).
    String withoutDot = typeRef.startsWith(".") ? typeRef.substring(1) : typeRef;
    int lastDot = withoutDot.lastIndexOf('.');
    if (lastDot <= 0) return simple;
    String pkgPath = withoutDot.substring(0, lastDot).replace(".", "::");
    return pkgPath + "::" + simple;
  }

  /**
   * The {@code #include} path for a cross-package type reference, or {@code null} when the type is
   * in the current package (handled by same-package includes) or is internal to {@code message}.
   */
  public static String crossPackageInclude(String typeRef, ProtoFile currentFile) {
    if (typeRef == null) return null;
    String pkg = currentFile.getProtoPackage();
    String currentPrefix = pkg.isEmpty() ? "." : "." + pkg + ".";
    if (typeRef.startsWith(currentPrefix)) return null;

    String withoutDot = typeRef.startsWith(".") ? typeRef.substring(1) : typeRef;
    int lastDot = withoutDot.lastIndexOf('.');
    if (lastDot <= 0) return null;
    String dir = withoutDot.substring(0, lastDot).replace('.', '/');
    String simple = withoutDot.substring(lastDot + 1);
    return "#include \"" + dir + "/" + simple + ".hpp\"";
  }

  /**
   * True when {@code field} is a singular (non-repeated, non-map) message field whose type is the
   * message that declares it. Such fields need pointer indirection ({@code std::shared_ptr})
   * because a class cannot contain a value member of its own incomplete type.
   */
  public static boolean isSelfReference(ProtoField field, ProtoMessage message) {
    return field.getKind() == ProtoField.FieldKind.MESSAGE
        && !field.isRepeated()
        && !field.isMap()
        && field.getTypeReference() != null
        && field.getTypeReference().equals(message.getFullName());
  }

  /** All messages nested under {@code message} (recursively), deepest first (post-order). */
  public static List<ProtoMessage> descendantMessages(ProtoMessage message) {
    List<ProtoMessage> result = new ArrayList<>();
    collectDescendants(message, result);
    return result;
  }

  private static void collectDescendants(ProtoMessage message, List<ProtoMessage> out) {
    for (ProtoMessage nested : message.getNestedMessages()) {
      collectDescendants(nested, out);
      out.add(nested);
    }
  }
}
