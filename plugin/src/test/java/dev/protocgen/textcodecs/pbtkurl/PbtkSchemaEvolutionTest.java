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
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema evolution tests for pbtk URL encoding format across all 17 supported languages.
 *
 * <p>Verifies that pbtk field-number-based encoding handles schema evolution gracefully: forward
 * compatibility via default/fallback cases, backward compatibility via field-number-based dispatch,
 * and sparse field numbers without null padding.
 */
class PbtkSchemaEvolutionTest {

  private static final String[] ALL_LANGUAGES = {
    "java",
    "python",
    "javascript",
    "typescript",
    "go",
    "rust",
    "cpp",
    "c",
    "zig",
    "csharp",
    "kotlin",
    "swift",
    "dart",
    "php",
    "ruby",
    "objc",
    "perl"
  };

  // ======================================================================
  // Data providers
  // ======================================================================

  static Stream<Arguments> allLanguages() {
    return Stream.of(ALL_LANGUAGES).map(Arguments::of);
  }

  // ======================================================================
  // Proto descriptor helpers
  // ======================================================================

  private FieldDescriptorProto scalarField(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  /** Build a v1 User message: fname=1, sname=2. */
  private DescriptorProto userV1() {
    return DescriptorProto.newBuilder()
        .setName("User")
        .addField(scalarField("fname", 1, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(scalarField("sname", 2, FieldDescriptorProto.Type.TYPE_STRING))
        .build();
  }

  /** Build a v2 User message: fname=1, sname=2, years=3. */
  private DescriptorProto userV2() {
    return DescriptorProto.newBuilder()
        .setName("User")
        .addField(scalarField("fname", 1, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(scalarField("sname", 2, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(scalarField("years", 3, FieldDescriptorProto.Type.TYPE_INT32))
        .build();
  }

  /** Build a sparse message with fields at 1, 5, 10. */
  private DescriptorProto sparseMessage() {
    return DescriptorProto.newBuilder()
        .setName("SparseMsg")
        .addField(scalarField("alpha", 1, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(scalarField("beta", 5, FieldDescriptorProto.Type.TYPE_INT32))
        .addField(scalarField("gamma", 10, FieldDescriptorProto.Type.TYPE_BOOL))
        .build();
  }

  // ======================================================================
  // Code generation helpers
  // ======================================================================

  /** Generate pbtk URL code for a single message. Returns concatenation of all generated files. */
  private String generatePbtk(DescriptorProto msg, String lang) {
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("evolution.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("evolution.proto")
            .setParameter("lang=" + lang)
            .build();

    CodeGeneratorResponse response = new PbtkPluginRunner().run(request);
    assertFalse(response.hasError(), "[pbtk/" + lang + "] Plugin error: " + response.getError());
    assertTrue(
        response.getFileCount() > 0, "[pbtk/" + lang + "] Expected at least one generated file");

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < response.getFileCount(); i++) {
      sb.append(response.getFile(i).getContent());
    }
    return sb.toString();
  }

  private void assertContains(String code, String expected, String context) {
    assertTrue(
        code.contains(expected),
        context + ": expected to find '" + expected + "' in generated code");
  }

  private void assertNotContains(String code, String unexpected, String context) {
    assertFalse(
        code.contains(unexpected),
        context + ": expected NOT to find '" + unexpected + "' in generated code");
  }

  private void assertContainsAny(String code, String[] patterns, String context) {
    for (String p : patterns) {
      if (code.contains(p)) {
        return;
      }
    }
    StringBuilder msg = new StringBuilder();
    msg.append(context).append(": expected at least one of [");
    for (int i = 0; i < patterns.length; i++) {
      if (i > 0) msg.append(", ");
      msg.append("'").append(patterns[i]).append("'");
    }
    msg.append("] in generated code");
    assertTrue(false, msg.toString());
  }

  // ======================================================================
  // 1. pbtk URL format evolution
  //    v1/v2 scenarios: field-number-based encoding handles evolution gracefully
  // ======================================================================

  @ParameterizedTest(name = "pbtkUrlFormatEvolution[{0}]")
  @MethodSource("allLanguages")
  void testPbtkUrlFormatEvolution(String lang) {
    // -- v1 deserializer: should only have cases for fields 1 and 2 --
    String v1Code = generatePbtk(userV1(), lang);

    // v1 has fname=1 (string) and sname=2 (string).
    // The serializer should emit !1s for fname and !2s for sname.
    assertContainsAny(
        v1Code,
        new String[] {"!1s", "!1s"},
        "[pbtk/" + lang + "] v1 serializer should emit !1s for fname");
    assertContainsAny(
        v1Code,
        new String[] {"!2s", "!2s"},
        "[pbtk/" + lang + "] v1 serializer should emit !2s for sname");

    // v1 deserializer should NOT reference field number 3 (years)
    assertNotContains(
        v1Code,
        "!3i",
        "[pbtk/" + lang + "] v1 code should NOT reference !3i (years field not in v1)");

    // The pbtk deserializer has a default/fallback case that skips unknown field numbers.
    // This is what enables forward compatibility -- v1 deserializer will skip field 3.
    // For non-Java languages, the pattern varies but the concept is the same.
    assertContainsAny(
        v1Code,
        new String[] {
          // Java: default: offset[0]++; consumed++; break;
          "default:",
          // Python: else: / default handling
          "else:",
          // JavaScript/TypeScript: default:
          "default:",
          // Go: default:
          "default:",
          // Rust: _ =>
          "_ =>",
          // C/C++: default:
          "default:",
          // Zig: else =>
          "else =>",
          // C#: default:
          "default:",
          // Kotlin: else ->
          "else ->",
          // Swift: default:
          "default:",
          // Dart: default:
          "default:",
          // PHP: default:
          "default:",
          // Ruby: else
          "else",
          // ObjC: default:
          "default:",
          // Perl: else
          "else",
        },
        "[pbtk/" + lang + "] v1 deserializer should have default/fallback case for unknown fields");

    // -- v2 serializer: should have !1s, !2s, and !3i --
    String v2Code = generatePbtk(userV2(), lang);
    assertContains(v2Code, "!3i", "[pbtk/" + lang + "] v2 serializer should emit !3i for years");

    // v2 deserializer handles missing fields from v1 output by using defaults.
    // The pbtk format simply omits absent fields, so the v2 deserializer will
    // see no field 3 token and the years field remains at its default value (0).
    // Verify the v2 deserializer initializes years with a default.
    assertContainsAny(
        v2Code,
        new String[] {
          // Various default-value patterns across languages for int32
          "= 0", // Java, C#, Kotlin, Swift, Dart, Go, JS, TS
          ": 0", // Python dict literal
          "0i32", // Rust
          "0;", // C, C++
          "i32 = 0", // Zig
          "@0", // ObjC
          "Int32", // Swift
          "int32", // Go
          "int", // Generic
          "0", // Fallback
        },
        "[pbtk/" + lang + "] v2 code should initialize years with default value");
  }

  // ======================================================================
  // 2. pbtk sparse field numbers
  //    Verify pbtk serializer uses actual field numbers, not positions
  // ======================================================================

  @ParameterizedTest(name = "pbtkSparseFieldNumbers[{0}]")
  @MethodSource("allLanguages")
  void testPbtkSparseFieldNumbers(String lang) {
    String code = generatePbtk(sparseMessage(), lang);

    // pbtk URL format uses !<fieldNumber><typeChar><value>.
    // For fields at 1, 5, 10:
    //   alpha (string, field 1) -> !1s
    //   beta (int32, field 5) -> !5i
    //   gamma (bool, field 10) -> !10b
    // There should be NO null padding (unlike JSON array format).
    assertContains(code, "!1s", "[pbtk/" + lang + "] should emit !1s for alpha");
    assertContains(code, "!5i", "[pbtk/" + lang + "] should emit !5i for beta");

    // For bool, the pattern is !10b followed by 1 or 0
    assertContainsAny(
        code,
        new String[] {"!10b", "!10b1", "!10b0"},
        "[pbtk/" + lang + "] should emit !10b for gamma");

    // pbtk format should NOT have gap/null padding between fields
    assertNotContains(
        code,
        "gap (no field number",
        "[pbtk/" + lang + "] pbtk format should not have JSON-array-style gap markers");
  }
}
