package dev.protocgen.textcodecs.jsonarray.codegen.javascript;

import dev.protocgen.textcodecs.jsonarray.codegen.KeywordUtil;
import dev.protocgen.textcodecs.jsonarray.codegen.NameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;

/**
 * Converts proto names to idiomatic JavaScript names. Fields use camelCase, classes use PascalCase,
 * files use .js extension.
 */
public class JavaScriptNameResolver implements NameResolver {

  @Override
  public String resolvePackage(ProtoFile file) {
    return file.getProtoPackage();
  }

  @Override
  public String messageClassName(String protoName) {
    // Proto message names are already PascalCase by convention
    return KeywordUtil.escapeJs(protoName);
  }

  @Override
  public String fieldName(String protoFieldName) {
    return KeywordUtil.escapeJs(snakeToCamel(protoFieldName));
  }

  @Override
  public String getterName(String protoFieldName) {
    return "get" + snakeToPascal(protoFieldName);
  }

  @Override
  public String setterName(String protoFieldName) {
    return "set" + snakeToPascal(protoFieldName);
  }

  @Override
  public String enumConstantName(String protoName) {
    // Proto enum constants are UPPER_SNAKE_CASE; keep as-is for JS const objects
    return KeywordUtil.escapeJs(protoName);
  }

  @Override
  public String fileExtension() {
    return ".js";
  }

  @Override
  public String resolveTypeReference(String protoFullName, ProtoFile currentFile) {
    // For JS, resolve to the simple name since we use relative imports
    if (protoFullName == null) return "Object";
    int lastDot = protoFullName.lastIndexOf('.');
    return lastDot >= 0 ? protoFullName.substring(lastDot + 1) : protoFullName;
  }

  @Override
  public String outputFilePath(ProtoFile file, String messageName) {
    String pkg = resolvePackage(file);
    if (pkg == null || pkg.isEmpty()) {
      return messageName + ".js";
    }
    String dir = pkg.replace('.', '/');
    return dir + "/" + messageName + ".js";
  }

  /** Convert snake_case to camelCase. */
  public static String snakeToCamel(String snake) {
    if (snake == null || snake.isEmpty()) return snake;
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = false;
    for (int i = 0; i < snake.length(); i++) {
      char c = snake.charAt(i);
      if (c == '_') {
        nextUpper = true;
      } else if (nextUpper) {
        sb.append(Character.toUpperCase(c));
        nextUpper = false;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Convert snake_case to PascalCase. */
  public static String snakeToPascal(String snake) {
    String camel = snakeToCamel(snake);
    if (camel == null || camel.isEmpty()) return camel;
    return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
  }
}
