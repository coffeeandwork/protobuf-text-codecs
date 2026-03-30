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
package dev.protocgen.textcodecs.jsonarray.codegen.objc;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Objective-C language code generator. Produces .h header and .m implementation files with
 * NSJSONSerialization-based JSON array serialization.
 *
 * <p>For each message, two CodeGeneratorResponse.File entries are produced:
 *
 * <ol>
 *   <li>ClassName.h - @interface with properties and method declarations
 *   <li>ClassName.m - @implementation with serialization/deserialization logic
 * </ol>
 *
 * <p>For top-level enums, only a .h file is produced (enums need no implementation). Uses ARC
 * (Automatic Reference Counting) -- no manual retain/release.
 */
public class ObjCGenerator implements LanguageGenerator {

  private final ObjCNameResolver nameResolver = new ObjCNameResolver();
  private final ObjCTypeMapper typeMapper = new ObjCTypeMapper(nameResolver);
  private final ObjCCodeEmitter codeEmitter = new ObjCCodeEmitter(typeMapper, nameResolver);

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();

    // Generate .h and .m for each top-level message
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

      // Implementation file (.m)
      String sourceCode = codeEmitter.emitSource(message, file);
      String sourcePath = nameResolver.outputSourcePath(file, message.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(sourcePath)
              .setContent(sourceCode)
              .build());
    }

    // Generate .h for each top-level enum (no .m needed)
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

  @Override
  public String languageId() {
    return "objc";
  }
}
