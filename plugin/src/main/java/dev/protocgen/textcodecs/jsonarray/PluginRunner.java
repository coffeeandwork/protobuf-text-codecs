package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.c.CGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.cpp.CppGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.go.GoGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.java.JavaGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.javascript.JavaScriptGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.python.PythonGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.rust.RustGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.typescript.TypeScriptGenerator;
import dev.protocgen.textcodecs.jsonarray.codegen.zig.ZigGenerator;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core orchestrator for the protoc plugin. Parses parameters, builds the type registry, and
 * delegates to the appropriate language generator.
 */
public class PluginRunner {

  private static final Map<String, java.util.function.Supplier<LanguageGenerator>> GENERATORS =
      new HashMap<>();

  static {
    GENERATORS.put("java", JavaGenerator::new);
    GENERATORS.put("python", PythonGenerator::new);
    GENERATORS.put("javascript", JavaScriptGenerator::new);
    GENERATORS.put("js", JavaScriptGenerator::new);
    GENERATORS.put("typescript", TypeScriptGenerator::new);
    GENERATORS.put("ts", TypeScriptGenerator::new);
    GENERATORS.put("c", CGenerator::new);
    GENERATORS.put("cpp", CppGenerator::new);
    GENERATORS.put("c++", CppGenerator::new);
    GENERATORS.put("rust", RustGenerator::new);
    GENERATORS.put("zig", ZigGenerator::new);
    GENERATORS.put("go", GoGenerator::new);
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

      // Validate output paths against path traversal
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
      }

      response.addAllFile(generatedFiles);
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
