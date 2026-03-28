package dev.protocgen.textcodecs.jsonarray.codegen.javascript;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaScript language code generator. Produces ES6 class files with native JSON array
 * serialization.
 */
public class JavaScriptGenerator implements LanguageGenerator {

  private final JavaScriptNameResolver nameResolver = new JavaScriptNameResolver();
  private final JavaScriptTypeMapper typeMapper = new JavaScriptTypeMapper();
  private final JavaScriptCodeEmitter codeEmitter =
      new JavaScriptCodeEmitter(typeMapper, nameResolver);

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();

    // Generate a JS file for each top-level message
    for (ProtoMessage message : file.getMessages()) {
      nameResolver.validateFieldNames(message.getFields());
      String sourceCode = codeEmitter.emitMessage(message, file);
      String outputPath = nameResolver.outputFilePath(file, message.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());
    }

    // Generate a JS file for each top-level enum
    for (ProtoEnum protoEnum : file.getEnums()) {
      String sourceCode = codeEmitter.emitTopLevelEnum(protoEnum, file);
      String outputPath = nameResolver.outputFilePath(file, protoEnum.getName());

      result.add(
          CodeGeneratorResponse.File.newBuilder()
              .setName(outputPath)
              .setContent(sourceCode)
              .build());
    }

    return result;
  }

  @Override
  public String languageId() {
    return "javascript";
  }
}
