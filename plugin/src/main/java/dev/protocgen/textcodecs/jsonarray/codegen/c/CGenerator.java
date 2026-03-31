/*
 * Copyright 2026 protobuf-text-codecs contributors
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
package dev.protocgen.textcodecs.jsonarray.codegen.c;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * C language code generator. Produces .h header and .c source files with cJSON-based JSON array
 * serialization.
 *
 * <p>For each message, two CodeGeneratorResponse.File entries are produced: 1. message_name.h -
 * struct definition and function declarations 2. message_name.c - function implementations
 * (serialize, deserialize, free)
 *
 * <p>For top-level enums, only a .h file is produced (enums need no implementation).
 */
public class CGenerator implements LanguageGenerator {

  private final CNameResolver nameResolver = new CNameResolver();
  private final CTypeMapper typeMapper = new CTypeMapper(nameResolver);
  private final CCodeEmitter codeEmitter = new CCodeEmitter(typeMapper, nameResolver);

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();

    // Generate .h and .c for each top-level message
    for (ProtoMessage message : file.getMessages()) {
      nameResolver.validateFieldNames(message.getFields());
      // Header file (.h)
      String headerCode = codeEmitter.emitHeader(message, file);
      String headerPath = nameResolver.outputFilePath(file, message.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(headerPath)
              .setContent(headerCode)
              .build());

      // Source file (.c)
      String sourceCode = codeEmitter.emitSource(message, file);
      String sourcePath = nameResolver.outputSourcePath(file, message.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(sourcePath)
              .setContent(sourceCode)
              .build());
    }

    // Generate .h for each top-level enum (no .c needed)
    for (ProtoEnum protoEnum : file.getEnums()) {
      String headerCode = codeEmitter.emitTopLevelEnumHeader(protoEnum, file);
      String headerPath = nameResolver.outputFilePath(file, protoEnum.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(headerPath)
              .setContent(headerCode)
              .build());
    }

    return result;
  }
}
