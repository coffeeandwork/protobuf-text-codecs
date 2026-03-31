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
package dev.protocgen.textcodecs.jsonarray.codegen.python;

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
 * Python language code generator. Produces Python source files with stdlib json-based JSON array
 * serialization.
 */
public class PythonGenerator implements LanguageGenerator {

  private final PythonNameResolver nameResolver = new PythonNameResolver();
  private final PythonTypeMapper typeMapper = new PythonTypeMapper();

  // Track emitted __init__.py files across multiple generate() calls
  // (protoc calls generate() once per .proto file, but the generator instance is shared)
  private final Set<String> emittedInitFiles = new HashSet<>();

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    // Pass TypeRegistry to PythonCodeEmitter so it can filter synthetic map-entry types
    PythonCodeEmitter codeEmitter = new PythonCodeEmitter(typeMapper, nameResolver, registry);
    List<CodeGeneratorResponse.File> result = new ArrayList<>();
    Set<String> initDirs = new HashSet<>();

    // Generate a Python file for each top-level message
    for (ProtoMessage message : file.getMessages()) {
      nameResolver.validateFieldNames(message.getFields());
      String sourceCode = codeEmitter.emitMessage(message, file);
      String outputPath = nameResolver.outputFilePath(file, message.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());

      collectPackageDirs(outputPath, initDirs);
    }

    // Generate a Python file for each top-level enum
    for (ProtoEnum protoEnum : file.getEnums()) {
      String sourceCode = codeEmitter.emitTopLevelEnum(protoEnum, file);
      String outputPath = nameResolver.outputFilePath(file, protoEnum.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());

      collectPackageDirs(outputPath, initDirs);
    }

    // Generate __init__.py for each package directory (only if not already emitted)
    for (String dir : initDirs) {
      String initPath = dir + "/__init__.py";
      if (emittedInitFiles.add(initPath)) {
        result.add(
            CodeGeneratorResponse.File.newBuilder().setName(initPath).setContent("").build());
      }
    }

    return result;
  }

  private void collectPackageDirs(String filePath, Set<String> dirs) {
    int lastSlash = filePath.lastIndexOf('/');
    if (lastSlash > 0) {
      String dir = filePath.substring(0, lastSlash);
      while (!dir.isEmpty()) {
        dirs.add(dir);
        lastSlash = dir.lastIndexOf('/');
        if (lastSlash > 0) {
          dir = dir.substring(0, lastSlash);
        } else {
          dirs.add(dir);
          break;
        }
      }
    }
  }
}
