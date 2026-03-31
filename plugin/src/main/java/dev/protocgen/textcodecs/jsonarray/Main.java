/*
 * Copyright 2026 protobuf-text-codecs contributors
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

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

/**
 * Entry point for the protoc-gen-jsonarray plugin. Reads a CodeGeneratorRequest from stdin,
 * processes it, and writes a CodeGeneratorResponse to stdout.
 *
 * <p>This implements the standard protoc plugin protocol defined in <a
 * href="https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/compiler/plugin.proto">plugin.proto</a>.
 * See also <a href="https://protobuf.dev/reference/other/">protobuf.dev plugin reference</a> (links
 * verified 2026-03-30).
 */
public class Main {

  static final String VERSION = "0.2.0";

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
