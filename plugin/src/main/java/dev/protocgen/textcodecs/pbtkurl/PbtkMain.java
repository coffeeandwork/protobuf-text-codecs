package dev.protocgen.textcodecs.pbtkurl;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

/**
 * Entry point for the protoc-gen-pbtkurl plugin. Reads a CodeGeneratorRequest from stdin, processes
 * it, and writes a CodeGeneratorResponse to stdout.
 */
public class PbtkMain {

  static final String VERSION = "0.1.0";

  public static void main(String[] args) {
    for (String arg : args) {
      if ("--version".equals(arg)) {
        System.out.println("protoc-gen-pbtkurl " + VERSION);
        return;
      }
    }

    try {
      CodeGeneratorRequest request = CodeGeneratorRequest.parseFrom(System.in);
      CodeGeneratorResponse response = new PbtkPluginRunner().run(request);
      response.writeTo(System.out);
    } catch (Exception e) {
      try {
        CodeGeneratorResponse.newBuilder()
            .setError("protoc-gen-pbtkurl: " + e.getMessage())
            .build()
            .writeTo(System.out);
      } catch (Exception fatal) {
        System.err.println("protoc-gen-pbtkurl: fatal error: " + fatal.getMessage());
        System.exit(1);
      }
    }
  }
}
