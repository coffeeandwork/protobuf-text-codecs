/*
 * Copyright 2026 coffeeandwork
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
package dev.protocgen.textcodecs.pbtkurl;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Golden-file snapshot tests for pbtk URL format. Generates code for the Address proto message
 * through PbtkPluginRunner for each language and compares the output byte-for-byte against
 * checked-in golden files.
 *
 * <p>Golden files are stored in {@code plugin/src/test/resources/golden-pbtk/<lang>/}. On first
 * run, if golden files do not exist, they are automatically generated and the test passes with a
 * warning. Subsequent runs compare against the established baseline.
 *
 * <p>To regenerate golden files after an intentional change to generated output:
 *
 * <pre>
 *   UPDATE_GOLDEN=true ./gradlew :plugin:test --tests '*PbtkGoldenFileTest*'
 * </pre>
 */
class PbtkGoldenFileTest {

  private static final String GOLDEN_DIR = "golden-pbtk";
  private final PbtkPluginRunner runner = new PbtkPluginRunner();

  /**
   * Build a CodeGeneratorRequest that mirrors {@code address.proto}:
   *
   * <pre>
   * syntax = "proto3";
   * package example;
   * option java_package = "com.example";
   * // A physical mailing address.
   * message Address {
   *   // The street address line.
   *   string street = 1;
   *   // The city name.
   *   string city = 2;
   *   // The state or province code.
   *   string state = 3;
   *   // The postal/ZIP code.
   *   int32 zip = 4;
   * }
   * </pre>
   */
  static CodeGeneratorRequest buildAddressRequest(String lang) {
    DescriptorProto addressMsg =
        DescriptorProto.newBuilder()
            .setName("Address")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("street")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("city")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("state")
                    .setNumber(3)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("zip")
                    .setNumber(4)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    // SourceCodeInfo paths: 4 = message_type, 0 = first message, 2 = field, N = field index
    SourceCodeInfo sourceCodeInfo =
        SourceCodeInfo.newBuilder()
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .setLeadingComments(" A physical mailing address.\n")
                    .build())
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .addPath(2)
                    .addPath(0)
                    .setLeadingComments(" The street address line.\n")
                    .build())
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .addPath(2)
                    .addPath(1)
                    .setLeadingComments(" The city name.\n")
                    .build())
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .addPath(2)
                    .addPath(2)
                    .setLeadingComments(" The state or province code.\n")
                    .build())
            .addLocation(
                SourceCodeInfo.Location.newBuilder()
                    .addPath(4)
                    .addPath(0)
                    .addPath(2)
                    .addPath(3)
                    .setLeadingComments(" The postal/ZIP code.\n")
                    .build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("address.proto")
            .setPackage("example")
            .setSyntax("proto3")
            .setOptions(FileOptions.newBuilder().setJavaPackage("com.example").build())
            .addMessageType(addressMsg)
            .setSourceCodeInfo(sourceCodeInfo)
            .build();

    return CodeGeneratorRequest.newBuilder()
        .addProtoFile(file)
        .addFileToGenerate("address.proto")
        .setParameter("lang=" + lang)
        .build();
  }

  static Stream<Arguments> languages() {
    return Stream.of(
        Arguments.of("java"),
        Arguments.of("python"),
        Arguments.of("javascript"),
        Arguments.of("typescript"),
        Arguments.of("c"),
        Arguments.of("cpp"),
        Arguments.of("rust"),
        Arguments.of("zig"),
        Arguments.of("go"),
        Arguments.of("csharp"),
        Arguments.of("kotlin"),
        Arguments.of("swift"),
        Arguments.of("dart"),
        Arguments.of("php"),
        Arguments.of("ruby"),
        Arguments.of("objc"),
        Arguments.of("perl"));
  }

  @ParameterizedTest(name = "golden-pbtk-{0}")
  @MethodSource("languages")
  void testGoldenFile(String lang) throws IOException {
    CodeGeneratorRequest request = buildAddressRequest(lang);
    CodeGeneratorResponse response = runner.run(request);

    assertFalse(
        response.hasError(),
        "Code generation failed for lang=" + lang + ": " + response.getError());
    assertTrue(
        response.getFileCount() > 0, "Expected at least one generated file for lang=" + lang);

    boolean updateMode =
        Boolean.getBoolean("update.golden")
            || "true".equalsIgnoreCase(System.getenv("UPDATE_GOLDEN"));

    for (int i = 0; i < response.getFileCount(); i++) {
      CodeGeneratorResponse.File generatedFile = response.getFile(i);
      String generatedContent = generatedFile.getContent();
      // Use the basename of the generated file path as the golden file name
      String outputFileName = Path.of(generatedFile.getName()).getFileName().toString();
      String goldenResourcePath = GOLDEN_DIR + "/" + lang + "/" + outputFileName;

      if (updateMode) {
        writeGoldenFile(lang, outputFileName, generatedContent);
      } else {
        // Load golden file from classpath
        String goldenContent = loadGoldenFile(goldenResourcePath);

        if (goldenContent == null) {
          // In CI, golden files must already exist
          if (System.getenv("CI") != null) {
            fail(
                "Golden file missing in CI: "
                    + goldenResourcePath
                    + ". Run locally with UPDATE_GOLDEN=true to generate.");
          }
          // Local dev: auto-generate the golden file and warn
          writeGoldenFile(lang, outputFileName, generatedContent);
          System.err.println(
              "[PbtkGoldenFileTest] WARNING: golden file created for "
                  + lang
                  + "/"
                  + outputFileName
                  + ". Commit the file and re-run to validate.");
        } else {
          assertEquals(
              goldenContent,
              generatedContent,
              "Generated output for "
                  + lang
                  + "/"
                  + outputFileName
                  + " differs from golden file.\n"
                  + "If the change is intentional, run:\n"
                  + "  UPDATE_GOLDEN=true ./gradlew :plugin:test --tests '*PbtkGoldenFileTest*'");
        }
      }
    }
  }

  /**
   * Load a golden file from the test classpath.
   *
   * @return file contents, or null if not found
   */
  private String loadGoldenFile(String resourcePath) throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        return null;
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** Write a golden file to the source tree. */
  private void writeGoldenFile(String lang, String fileName, String content) throws IOException {
    Path goldenPath = resolveGoldenSourcePath(lang, fileName);
    Files.createDirectories(goldenPath.getParent());
    Files.writeString(goldenPath, content, StandardCharsets.UTF_8);
    System.out.println("[PbtkGoldenFileTest] Wrote golden file: " + goldenPath);
  }

  /**
   * Resolve the path to the golden file in the source tree. The golden files live under {@code
   * plugin/src/test/resources/golden-pbtk/<lang>/}.
   */
  private Path resolveGoldenSourcePath(String lang, String fileName) {
    // Gradle sets user.dir to the project root (protobuf-text-codecs)
    Path cwd = Path.of(System.getProperty("user.dir"));
    Path candidate = cwd.resolve("plugin/src/test/resources/" + GOLDEN_DIR);
    if (!Files.isDirectory(candidate.getParent())) {
      // Fallback: we might be inside the plugin subproject
      candidate = cwd.resolve("src/test/resources/" + GOLDEN_DIR);
    }
    return candidate.resolve(lang).resolve(fileName);
  }
}
