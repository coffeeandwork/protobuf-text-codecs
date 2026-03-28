package dev.protocgen.textcodecs.jsonarray.codegen;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.List;

/**
 * Interface for language-specific code generators. Each target language implements this to produce
 * source files from analyzed proto definitions.
 */
public interface LanguageGenerator {

  /** Generate source files for all messages and enums in the given proto file. */
  List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry);

  /** The language identifier used in the lang= parameter (e.g., "java", "python"). */
  String languageId();
}
