package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.codegen.LanguageGenerator;
import dev.protocgen.textcodecs.jsonarray.model.ProtoEnum;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.ProtoMessage;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes a single FileDescriptorProto: extracts messages and enums, analyzes them into the
 * internal model, and delegates to the language generator.
 */
public class ProtoFileProcessor {

  private final TypeRegistry typeRegistry;
  private final LanguageGenerator generator;
  private final MessageAnalyzer analyzer;

  public ProtoFileProcessor(TypeRegistry typeRegistry, LanguageGenerator generator) {
    this.typeRegistry = typeRegistry;
    this.generator = generator;
    this.analyzer = new MessageAnalyzer(typeRegistry);
  }

  /** Process a proto file and produce generated source files. */
  public List<CodeGeneratorResponse.File> process(FileDescriptorProto fileDescriptor) {
    String protoPackage = fileDescriptor.getPackage();
    String prefix = protoPackage.isEmpty() ? "." : "." + protoPackage + ".";

    // Build a map from source path to leading comment
    Map<List<Integer>, String> commentMap = buildCommentMap(fileDescriptor);

    // Analyze top-level messages (nested messages are handled recursively by MessageAnalyzer)
    List<ProtoMessage> messages = new ArrayList<>();
    int messageIndex = 0;
    for (DescriptorProto messageType : fileDescriptor.getMessageTypeList()) {
      // Skip synthetic map-entry messages at the top level
      if (messageType.getOptions().getMapEntry()) {
        messageIndex++;
        continue;
      }
      messages.add(
          analyzer.analyze(
              messageType,
              prefix,
              fileDescriptor.getSyntax(),
              commentMap,
              List.of(4, messageIndex)));
      messageIndex++;
    }

    // Analyze top-level enums
    List<ProtoEnum> enums = new ArrayList<>();
    for (EnumDescriptorProto enumType : fileDescriptor.getEnumTypeList()) {
      enums.add(analyzer.analyzeEnum(enumType, prefix));
    }

    // Build ProtoFile model
    String javaPackage =
        fileDescriptor.getOptions().hasJavaPackage()
            ? fileDescriptor.getOptions().getJavaPackage()
            : null;
    String javaOuterClassname =
        fileDescriptor.getOptions().hasJavaOuterClassname()
            ? fileDescriptor.getOptions().getJavaOuterClassname()
            : null;

    ProtoFile protoFile =
        new ProtoFile(
            fileDescriptor.getName(),
            protoPackage,
            javaPackage,
            javaOuterClassname,
            fileDescriptor.getSyntax(),
            messages,
            enums,
            fileDescriptor.getDependencyList());

    // Delegate to the language generator
    return generator.generate(protoFile, typeRegistry);
  }

  /**
   * Build a map from source location path to leading comment. The path is a list of field numbers
   * identifying the location in the proto file AST as defined by SourceCodeInfo.Location.
   */
  private Map<List<Integer>, String> buildCommentMap(FileDescriptorProto fileDescriptor) {
    Map<List<Integer>, String> map = new HashMap<>();
    if (!fileDescriptor.hasSourceCodeInfo()) {
      return map;
    }
    for (SourceCodeInfo.Location location : fileDescriptor.getSourceCodeInfo().getLocationList()) {
      String comment = location.getLeadingComments();
      if (comment != null && !comment.isEmpty()) {
        map.put(location.getPathList(), comment.strip());
      }
    }
    return map;
  }
}
