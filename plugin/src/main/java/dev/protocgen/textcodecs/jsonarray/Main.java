package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

/**
 * Entry point for the protoc-gen-jsonarray plugin. Reads a CodeGeneratorRequest from stdin,
 * processes it, and writes a CodeGeneratorResponse to stdout.
 */
public class Main {

  /** Version string, kept in sync with build.gradle.kts. */
  static final String VERSION = "0.1.0";

  public static void main(String[] args) {
    for (String arg : args) {
      if ("--version".equals(arg)) {
        System.out.println("protoc-gen-jsonarray " + VERSION);
        return;
      }
    }

    try {
      CodeGeneratorRequest request = CodeGeneratorRequest.parseFrom(System.in);
      CodeGeneratorResponse response = new PluginRunner().run(request);
      response.writeTo(System.out);
    } catch (Exception e) {
      // Per the plugin protocol, errors should be returned via the response,
      // not via exit code. But if we can't even parse the request, write an
      // error response.
      try {
        CodeGeneratorResponse.newBuilder()
            .setError("protoc-gen-jsonarray: " + e.getMessage())
            .build()
            .writeTo(System.out);
      } catch (Exception fatal) {
        System.err.println("protoc-gen-jsonarray: fatal error: " + fatal.getMessage());
        System.exit(1);
      }
    }
  }
}
