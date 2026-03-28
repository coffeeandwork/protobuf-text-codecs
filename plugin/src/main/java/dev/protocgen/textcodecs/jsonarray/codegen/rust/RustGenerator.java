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
package dev.protocgen.textcodecs.jsonarray.codegen.rust;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rust language code generator. Produces Rust source files with serde_json-based JSON array
 * serialization.
 */
public class RustGenerator implements LanguageGenerator {

  private final RustNameResolver nameResolver = new RustNameResolver();
  private final RustTypeMapper typeMapper = new RustTypeMapper();
  private final RustCodeEmitter codeEmitter = new RustCodeEmitter(typeMapper, nameResolver);

  // Track emitted mod.rs files to avoid duplicates across multiple proto files in the same package
  private final Set<String> emittedModFiles = new HashSet<>();
  // Accumulate module names per package directory for mod.rs generation
  private final java.util.Map<String, Set<String>> modulesByDir = new java.util.LinkedHashMap<>();

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();
    List<String> moduleNames = new ArrayList<>();

    // Generate a Rust source file for each top-level message
    for (ProtoMessage message : file.getMessages()) {
      nameResolver.validateFieldNames(message.getFields());
      String sourceCode = codeEmitter.emitMessage(message, file);
      String outputPath = nameResolver.outputFilePath(file, message.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());

      moduleNames.add(RustNameResolver.toSnakeCase(message.getName()));
    }

    // Generate a Rust source file for each top-level enum
    for (ProtoEnum protoEnum : file.getEnums()) {
      String sourceCode = codeEmitter.emitTopLevelEnum(protoEnum, file);
      String outputPath = nameResolver.outputFilePath(file, protoEnum.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());

      moduleNames.add(RustNameResolver.toSnakeCase(protoEnum.getName()));
    }

    // Accumulate module names per directory for mod.rs generation
    String pkg = nameResolver.resolvePackage(file);
    String dir = pkg.isEmpty() ? "" : pkg.replace('.', '/');
    modulesByDir.computeIfAbsent(dir, k -> new java.util.LinkedHashSet<>()).addAll(moduleNames);

    // Generate mod.rs only if we haven't emitted one for this directory yet.
    // Since protoc rejects duplicate file names, we emit mod.rs on the first
    // proto file only. For subsequent files in the same package, we use the
    // insertion_point mechanism to append module declarations.
    String modPath = dir.isEmpty() ? "mod.rs" : dir + "/mod.rs";
    if (!moduleNames.isEmpty() && emittedModFiles.add(modPath)) {
      // First time for this directory — emit mod.rs with an insertion point
      StringBuilder modContent = new StringBuilder();
      for (String modName : modulesByDir.get(dir)) {
        modContent.append("pub mod ").append(modName).append(";\n");
      }
      modContent.append("// @@protoc_insertion_point(module_declarations)\n");
      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(modPath)
              .setContent(modContent.toString())
              .build());
    } else if (!moduleNames.isEmpty() && !emittedModFiles.add(modPath)) {
      // Subsequent proto file in same package — append via insertion point
      StringBuilder newMods = new StringBuilder();
      for (String modName : moduleNames) {
        newMods.append("pub mod ").append(modName).append(";\n");
      }
      newMods.append("// @@protoc_insertion_point(module_declarations)\n");
      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(modPath)
              .setInsertionPoint("module_declarations")
              .setContent(newMods.toString())
              .build());
    }

    return result;
  }

  @Override
  public String languageId() {
    return "rust";
  }
}
