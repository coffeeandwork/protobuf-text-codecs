package dev.protocgen.textcodecs.jsonarray.codegen.typescript;

import dev.protocgen.textcodecs.jsonarray.codegen.javascript.JavaScriptNameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to TypeScript names. Inherits all JS naming conventions (camelCase fields,
 * PascalCase classes) but produces .ts files instead of .js.
 */
public class TypeScriptNameResolver extends JavaScriptNameResolver {

  @Override
  public String fileExtension() {
    return ".ts";
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    if (pkg == null || pkg.isEmpty()) {
      return messageName + ".ts";
    }
    String dir = pkg.replace('.', '/');
    return dir + "/" + messageName + ".ts";
  }
}
