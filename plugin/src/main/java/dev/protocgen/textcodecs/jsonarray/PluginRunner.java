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
package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.c.CGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.cpp.CppGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.csharp.CSharpGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.dart.DartGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.go.GoGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.java.JavaGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.javascript.JavaScriptGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.kotlin.KotlinGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.objc.ObjCGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.perl.PerlGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.php.PhpGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.python.PythonGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.ruby.RubyGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.rust.RustGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.swift.SwiftGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.typescript.TypeScriptGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.zig.ZigGenerator;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Core orchestrator for the protoc plugin. Parses the {@code lang=} parameter from the {@link
 * com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest#getParameter() request parameter},
 * builds the {@link dev.protocgen.textcodecs.jsonarray.model.TypeRegistry TypeRegistry} from the
 * request's {@link com.google.protobuf.DescriptorProtos.FileDescriptorProto FileDescriptorProto}
 * list, and delegates to the appropriate {@link LanguageGenerator}.
 *
 * <p>The request/response types are defined in <a
 * href="https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/compiler/plugin.proto">plugin.proto</a>;
 * the file descriptors that describe each .proto file are defined in <a
 * href="https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/descriptor.proto">descriptor.proto</a>
 * .
 */
public class PluginRunner {

  private static final Map<String, Supplier<LanguageGenerator>> GENERATORS;

  static {
    Map<String, Supplier<LanguageGenerator>> map = new HashMap<>();
    map.put("java", JavaGenerator::new);
    map.put("python", PythonGenerator::new);
    map.put("javascript", JavaScriptGenerator::new);
    map.put("js", JavaScriptGenerator::new);
    map.put("typescript", TypeScriptGenerator::new);
    map.put("ts", TypeScriptGenerator::new);
    map.put("c", CGenerator::new);
    map.put("cpp", CppGenerator::new);
    map.put("c++", CppGenerator::new);
    map.put("csharp", CSharpGenerator::new);
    map.put("c#", CSharpGenerator::new);
    map.put("dart", DartGenerator::new);
    map.put("go", GoGenerator::new);
    map.put("kotlin", KotlinGenerator::new);
    map.put("kt", KotlinGenerator::new);
    map.put("objc", ObjCGenerator::new);
    map.put("objective-c", ObjCGenerator::new);
    map.put("perl", PerlGenerator::new);
    map.put("php", PhpGenerator::new);
    map.put("ruby", RubyGenerator::new);
    map.put("rb", RubyGenerator::new);
    map.put("rust", RustGenerator::new);
    map.put("swift", SwiftGenerator::new);
    map.put("zig", ZigGenerator::new);
    GENERATORS = Collections.unmodifiableMap(map);
  }

  /** Process a CodeGeneratorRequest and produce a CodeGeneratorResponse. */
  public CodeGeneratorResponse run(CodeGeneratorRequest request) {
    try {
      return doRun(request);
    } catch (Exception e) {
      return CodeGeneratorResponse.newBuilder()
          .setError("protoc-gen-jsonarray: " + e.getMessage())
          .build();
    }
  }

  private CodeGeneratorResponse doRun(CodeGeneratorRequest request) {
    // Parse the lang= parameter
    String lang = parseLanguage(request.getParameter());

    // Look up the generator
    var generatorFactory = GENERATORS.get(lang);
    if (generatorFactory == null) {
      return errorResponse(
          "unsupported language: '" + lang + "'. Supported: " + GENERATORS.keySet());
    }
    LanguageGenerator generator = generatorFactory.get();

    // Build global type registry from all proto files
    TypeRegistry typeRegistry = new TypeRegistry();
    for (FileDescriptorProto file : request.getProtoFileList()) {
      typeRegistry.registerFile(file);
    }

    // Process only the files the user explicitly asked to compile
    Set<String> filesToGenerate = new HashSet<>(request.getFileToGenerateList());
    ProtoFileProcessor processor = new ProtoFileProcessor(typeRegistry, generator);

    CodeGeneratorResponse.Builder response = CodeGeneratorResponse.newBuilder();
    // Indicate we support proto3 optional
    response.setSupportedFeatures(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL_VALUE);

    for (FileDescriptorProto file : request.getProtoFileList()) {
      if (!filesToGenerate.contains(file.getName())) {
        continue;
      }

      // Warn about extensions (incompatible with positional encoding)
      if (file.getExtensionCount() > 0) {
        System.err.println(
            "protoc-gen-jsonarray: warning: file '"
                + file.getName()
                + "' defines extensions. Extensions are incompatible with positional "
                + "JSON array encoding and will be ignored. Only base message fields "
                + "will be included in generated code.");
      }
      for (DescriptorProto msgType : file.getMessageTypeList()) {
        if (msgType.getExtensionRangeCount() > 0) {
          System.err.println(
              "protoc-gen-jsonarray: warning: message '"
                  + msgType.getName()
                  + "' in file '"
                  + file.getName()
                  + "' declares extension ranges. Extensions are incompatible with "
                  + "positional JSON array encoding. Only base message fields will be "
                  + "included in generated code.");
        }
      }

      List<CodeGeneratorResponse.File> generatedFiles = processor.process(file);

      // Validate and normalize output paths against path traversal
      for (CodeGeneratorResponse.File genFile : generatedFiles) {
        String path = genFile.getName();
        // Normalize leading slash from empty package (e.g., "/Msg.java" → "Msg.java")
        if (path.startsWith("/")) {
          path = path.substring(1);
        }
        if (path.contains("..") || path.startsWith("/") || path.contains("\0")) {
          return errorResponse(
              "Generated output path is unsafe: "
                  + path
                  + ". Paths must be relative, must not contain '..', and must not contain null bytes.");
        }
        // Use the normalized path, not the original
        response.addFile(genFile.toBuilder().setName(path).build());
      }
    }

    return response.build();
  }

  private String parseLanguage(String parameter) {
    if (parameter == null || parameter.isEmpty()) {
      return "java"; // default
    }
    for (String part : parameter.split(",")) {
      String trimmed = part.trim();
      if (trimmed.startsWith("lang=")) {
        return trimmed.substring("lang=".length()).trim().toLowerCase();
      }
    }
    return "java"; // default if no lang= found
  }

  private CodeGeneratorResponse errorResponse(String message) {
    return CodeGeneratorResponse.newBuilder().setError("protoc-gen-jsonarray: " + message).build();
  }
}
