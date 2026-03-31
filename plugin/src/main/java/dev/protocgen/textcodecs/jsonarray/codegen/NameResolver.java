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
package dev.protocgen.textcodecs.jsonarray.codegen;

import dev.protocgen.textcodecs.jsonarray.model.ProtoField;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interface for language-specific naming conventions. Each language has different rules for
 * converting proto names to idiomatic target names.
 */
public interface NameResolver {

  /** Convert a proto package (and file options) to the target language's package/module path. */
  String resolvePackage(ProtoFile file);

  /** Convert a proto message name (e.g., "UserProfile") to a class/struct name. */
  String messageClassName(String protoName);

  /** Convert a proto field name (e.g., "first_name") to a field/property name. */
  String fieldName(String protoFieldName);

  /**
   * Convert a proto field name to a getter method name (e.g., "getFirstName"). For languages
   * without getters (Python, Go), may return the field name.
   */
  String getterName(String protoFieldName);

  /** Convert a proto field name to a setter method name (e.g., "setFirstName"). */
  String setterName(String protoFieldName);

  /** Convert a proto enum constant name (e.g., "STATUS_ACTIVE") to the target name. */
  String enumConstantName(String protoName);

  /** The file extension for generated source files (e.g., ".java", ".py"). */
  String fileExtension();

  /**
   * Convert a fully-qualified proto type reference to a target language type reference. E.g.,
   * ".example.Address" → "com.example.Address" in Java.
   */
  String resolveTypeReference(String protoFullName, ProtoFile currentFile);

  /** Determine the output file path for a given message in the given proto file. */
  String outputFilePath(ProtoFile file, String messageName);

  /**
   * Validate that no two proto fields map to the same target-language field name. This detects
   * collisions like foo_bar and fooBar both becoming fooBar in Java/JS/TS, or both becoming FooBar
   * in Go.
   *
   * @throws IllegalArgumentException if a collision is detected
   */
  default void validateFieldNames(List<ProtoField> fields) {
    Set<String> seen = new HashSet<>();
    for (ProtoField f : fields) {
      String resolved = fieldName(f.getName());
      if (!seen.add(resolved)) {
        throw new IllegalArgumentException(
            "Field name collision: multiple proto fields map to the same target name '"
                + resolved
                + "'");
      }
    }
  }
}
