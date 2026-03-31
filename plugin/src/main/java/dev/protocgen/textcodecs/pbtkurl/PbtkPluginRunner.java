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
package dev.protocgen.textcodecs.pbtkurl;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.ProtoFileProcessor;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import dev.protocgen.textcodecs.pbtkurl.codegen.c.PbtkCGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.cpp.PbtkCppGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.csharp.PbtkCSharpGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.dart.PbtkDartGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.go.PbtkGoGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.java.PbtkJavaGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.javascript.PbtkJavaScriptGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.kotlin.PbtkKotlinGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.objc.PbtkObjCGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.perl.PbtkPerlGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.php.PbtkPhpGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.python.PbtkPythonGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.ruby.PbtkRubyGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.rust.PbtkRustGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.swift.PbtkSwiftGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.typescript.PbtkTypeScriptGenerator;
import dev.protocgen.textcodecs.pbtkurl.codegen.zig.PbtkZigGenerator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core orchestrator for the protoc-gen-pbtkurl plugin. Mirrors PluginRunner but dispatches to pbtk
 * URL encoding generators.
 */
public class PbtkPluginRunner {

  private static final Map<String, java.util.function.Supplier<LanguageGenerator>> GENERATORS =
      new HashMap<>();

  static {
    GENERATORS.put("c", PbtkCGenerator::new);
    GENERATORS.put("cpp", PbtkCppGenerator::new);
    GENERATORS.put("c++", PbtkCppGenerator::new);
    GENERATORS.put("csharp", PbtkCSharpGenerator::new);
    GENERATORS.put("c#", PbtkCSharpGenerator::new);
    GENERATORS.put("dart", PbtkDartGenerator::new);
    GENERATORS.put("go", PbtkGoGenerator::new);
    GENERATORS.put("java", PbtkJavaGenerator::new);
    GENERATORS.put("javascript", PbtkJavaScriptGenerator::new);
    GENERATORS.put("js", PbtkJavaScriptGenerator::new);
    GENERATORS.put("kotlin", PbtkKotlinGenerator::new);
    GENERATORS.put("kt", PbtkKotlinGenerator::new);
    GENERATORS.put("objc", PbtkObjCGenerator::new);
    GENERATORS.put("objective-c", PbtkObjCGenerator::new);
    GENERATORS.put("perl", PbtkPerlGenerator::new);
    GENERATORS.put("php", PbtkPhpGenerator::new);
    GENERATORS.put("python", PbtkPythonGenerator::new);
    GENERATORS.put("ruby", PbtkRubyGenerator::new);
    GENERATORS.put("rb", PbtkRubyGenerator::new);
    GENERATORS.put("rust", PbtkRustGenerator::new);
    GENERATORS.put("swift", PbtkSwiftGenerator::new);
    GENERATORS.put("typescript", PbtkTypeScriptGenerator::new);
    GENERATORS.put("ts", PbtkTypeScriptGenerator::new);
    GENERATORS.put("zig", PbtkZigGenerator::new);
  }

  /** Process a CodeGeneratorRequest and produce a CodeGeneratorResponse. */
  public CodeGeneratorResponse run(CodeGeneratorRequest request) {
    try {
      return doRun(request);
    } catch (Exception e) {
      return CodeGeneratorResponse.newBuilder()
          .setError("protoc-gen-pbtkurl: " + e.getMessage())
          .build();
    }
  }

  private CodeGeneratorResponse doRun(CodeGeneratorRequest request) {
    String lang = parseLanguage(request.getParameter());

    var generatorFactory = GENERATORS.get(lang);
    if (generatorFactory == null) {
      return errorResponse(
          "unsupported language: '" + lang + "'. Supported: " + GENERATORS.keySet());
    }
    LanguageGenerator generator = generatorFactory.get();

    TypeRegistry typeRegistry = new TypeRegistry();
    for (FileDescriptorProto file : request.getProtoFileList()) {
      typeRegistry.registerFile(file);
    }

    Set<String> filesToGenerate = new HashSet<>(request.getFileToGenerateList());
    ProtoFileProcessor processor = new ProtoFileProcessor(typeRegistry, generator);

    CodeGeneratorResponse.Builder response = CodeGeneratorResponse.newBuilder();
    response.setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE);

    for (FileDescriptorProto file : request.getProtoFileList()) {
      if (!filesToGenerate.contains(file.getName())) {
        continue;
      }

      if (file.getExtensionCount() > 0) {
        System.err.println(
            "protoc-gen-pbtkurl: warning: file '"
                + file.getName()
                + "' defines extensions. Extensions are incompatible with pbtk URL "
                + "encoding and will be ignored.");
      }
      for (DescriptorProto msgType : file.getMessageTypeList()) {
        if (msgType.getExtensionRangeCount() > 0) {
          System.err.println(
              "protoc-gen-pbtkurl: warning: message '"
                  + msgType.getName()
                  + "' in file '"
                  + file.getName()
                  + "' declares extension ranges. Extensions are incompatible with "
                  + "pbtk URL encoding.");
        }
      }

      List<CodeGeneratorResponse.File> generatedFiles = processor.process(file);

      for (CodeGeneratorResponse.File genFile : generatedFiles) {
        String path = genFile.getName();
        if (path.startsWith("/")) {
          path = path.substring(1);
        }
        if (path.contains("..") || path.startsWith("/") || path.contains("\0")) {
          return errorResponse(
              "Generated output path is unsafe: "
                  + path
                  + ". Paths must be relative, must not contain '..', and must not contain null bytes.");
        }
        response.addFile(genFile.toBuilder().setName(path).build());
      }
    }

    return response.build();
  }

  private String parseLanguage(String parameter) {
    if (parameter == null || parameter.isEmpty()) {
      return "java";
    }
    for (String part : parameter.split(",")) {
      String trimmed = part.trim();
      if (trimmed.startsWith("lang=")) {
        return trimmed.substring("lang=".length()).trim().toLowerCase();
      }
    }
    return "java";
  }

  private CodeGeneratorResponse errorResponse(String message) {
    return CodeGeneratorResponse.newBuilder().setError("protoc-gen-pbtkurl: " + message).build();
  }
}
