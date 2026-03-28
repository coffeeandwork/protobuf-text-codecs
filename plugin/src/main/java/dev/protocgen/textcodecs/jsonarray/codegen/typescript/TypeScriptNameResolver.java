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
package dev.protocgen.textcodecs.jsonarray.codegen.typescript;

import dev.protocgen.textcodecs.jsonarray.codegen.javascript.JavaScriptNameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to TypeScript names. Inherits all JS naming conventions (camelCase fields,
 * PascalCase classes) but produces .ts files instead of .js.
 */
public class TypeScriptNameResolver extends JavaScriptNameResolver {

  @Override
  public String fileExtension() {
    return ".ts";
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    if (pkg == null || pkg.isEmpty()) {
      return messageName + ".ts";
    }
    String dir = pkg.replace('.', '/');
    return dir + "/" + messageName + ".ts";
  }
}
