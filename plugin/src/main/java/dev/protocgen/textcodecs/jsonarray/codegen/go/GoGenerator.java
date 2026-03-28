package dev.protocgen.textcodecs.jsonarray.codegen.go;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * Go language code generator. Produces Go source files with encoding/json-based JSON array
 * serialization. Generated code uses only Go stdlib (encoding/json, encoding/base64, fmt).
 */
public class GoGenerator implements LanguageGenerator {

  private final GoNameResolver nameResolver = new GoNameResolver();
  private final GoTypeMapper typeMapper = new GoTypeMapper();
  private final GoCodeEmitter codeEmitter = new GoCodeEmitter(typeMapper, nameResolver);

  @Override
  public List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry) {
    List<CodeGeneratorResponse.File> result = new ArrayList<>();

    // Generate a Go file for each top-level message
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

    // Generate a Go file for each top-level enum
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
    return "go";
  }
}
