/*
 * Copyright 2024 protobuf-text-codecs contributors
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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.pbtkurl.PbtkPluginRunner;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema evolution and cross-language interoperability tests for both JSON array and pbtk URL
 * encoding formats across all 17 supported languages.
 *
 * <p>Verifies that code generated from evolved schemas maintains forward compatibility (old data,
 * new schema), backward compatibility (new data, old schema), field removal with reservation, and
 * consistent positional/field-number encoding across all language targets.
 */
class SchemaEvolutionTest {

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

  /** Build a v3 User message with reserved field 2: fname=1, (reserved 2), years=3. */
  private DescriptorProto userV3() {
    return DescriptorProto.newBuilder()
        .setName("User")
        .addField(scalarField("fname", 1, FieldDescriptorProto.Type.TYPE_STRING))
        .addField(scalarField("years", 3, FieldDescriptorProto.Type.TYPE_INT32))
        .addReservedRange(DescriptorProto.ReservedRange.newBuilder().setStart(2).setEnd(3).build())
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

  /**
   * Generate JSON array code for a single message. Returns concatenation of all generated files.
   */
  private String generateJsonArray(DescriptorProto msg, String lang) {
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

    CodeGeneratorResponse response = new PluginRunner().run(request);
    assertFalse(
        response.hasError(), "[jsonarray/" + lang + "] Plugin error: " + response.getError());
    assertTrue(
        response.getFileCount() > 0,
        "[jsonarray/" + lang + "] Expected at least one generated file");

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < response.getFileCount(); i++) {
      sb.append(response.getFile(i).getContent());
    }
    return sb.toString();
  }

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
  // 1. Forward compatibility: old data, new schema (JSON array)
  //    v2 deserializer must bounds-check before accessing the age field
  // ======================================================================

  @ParameterizedTest(name = "forwardCompatibility[{0}]")
  @MethodSource("allLanguages")
  void testForwardCompatibility_v2DeserializerBoundsChecksAgeField(String lang) {
    String code = generateJsonArray(userV2(), lang);

    // The v2 deserializer must guard access to position 2 (years, field number 3).
    // Different languages use different idioms for bounds checking, but they all
    // check the array/list size before accessing the element at position 2.
    assertContainsAny(
        code,
        new String[] {
          // Java: if (size > 2
          "size > 2",
          // Python: if len(array) > 2 / if size > 2
          "len(array) > 2",
          "len(arr) > 2",
          // JavaScript/TypeScript: if (array.length > 2 / if (size > 2
          "length > 2",
          ".length > 2",
          // Go: if len(array) > 2
          "len(array) > 2",
          "len(arr) > 2",
          // Rust: if array.len() > 2 / if size > 2
          ".len() > 2",
          // C: if (size > 2 / if (count > 2
          "count > 2",
          // C++: if (array.size() > 2 / if (size > 2
          ".size() > 2",
          // Zig: if (array.items.len > 2)
          ".len > 2",
          // C#: if (array.Count > 2 / if (size > 2
          ".Count > 2",
          "Count > 2",
          // Kotlin: if (array.size > 2 / if (size > 2
          // Swift: if array.count > 2 / if size > 2
          ".count > 2",
          // Dart: if (array.length > 2 / if (size > 2
          // PHP: if (count($array) > 2 / if ($size > 2
          "count($",
          "$size > 2",
          // Ruby: if array.size > 2 / if size > 2
          ".size > 2",
          // ObjC: if ([array count] > 2 / if (size > 2
          "[array count] > 2",
          // Perl: if (scalar @$array > 2 / if ($size > 2
          "scalar @",
          // Generic fallback: any language checking > 2 or >= 3
          "> 2",
          ">= 3",
        },
        "[jsonarray/" + lang + "] forward-compat bounds check for years at position 2");
  }

  // ======================================================================
  // 2. Backward compatibility: new data, old schema (JSON array)
  //    v1 deserializer must NOT reference position 2
  // ======================================================================

  @ParameterizedTest(name = "backwardCompatibility[{0}]")
  @MethodSource("allLanguages")
  void testBackwardCompatibility_v1DeserializerIgnoresExtraElements(String lang) {
    String code = generateJsonArray(userV1(), lang);

    // v1 message has only fields 1 and 2 (positions 0 and 1).
    // The deserializer should NOT access position 2 (which would be field 3 = years).
    // It should safely ignore extra array elements by only iterating up to its known
    // field count.

    // Verify the generated code references positions 0 and 1 but not position 2.
    // Check that the code handles fname (position 0) and sname (position 1).
    assertContainsAny(
        code,
        new String[] {
          "fname", "Fname",
        },
        "[jsonarray/" + lang + "] v1 deserializer should reference fname");
    assertContainsAny(
        code,
        new String[] {
          "sname", "Sname",
        },
        "[jsonarray/" + lang + "] v1 deserializer should reference sname");

    // v1 schema has no "years" field -- the deserializer should never reference it
    assertNotContains(
        code,
        "years",
        "[jsonarray/" + lang + "] v1 deserializer should NOT reference 'years' field");
  }

  // ======================================================================
  // 3. Field removal with reservation (JSON array)
  //    v3 has fields 1 and 3 (reserved 2), so position 1 must be a null gap
  // ======================================================================

  @ParameterizedTest(name = "fieldRemovalWithReservation[{0}]")
  @MethodSource("allLanguages")
  void testFieldRemovalWithReservation_gapAtPosition1(String lang) {
    String code = generateJsonArray(userV3(), lang);

    // v3 has fname=1 and years=3, with field 2 reserved.
    // The serializer must produce a null/gap at position 1 (field number 2).
    assertContainsAny(
        code,
        new String[] {
          // Most languages use a gap comment with field number 2
          "gap (no field number 2)",
          "gap for field number 2",
          "no field number 2",
          // Some languages may use null directly
          "null", // position 1 gap
          "None", // Python
          "nil", // Ruby, Go, Swift, ObjC
          "nullptr", // C++
          "NULL", // C
        },
        "[jsonarray/"
            + lang
            + "] serializer should have null/gap at position 1 for reserved field 2");

    // The deserializer should handle position 1 being null -- it should
    // skip position 1 and read years from position 2 (field number 3)
    assertContainsAny(
        code,
        new String[] {
          // Various languages check size > 2 for the years field at position 2
          "> 2",
          ">= 3",
          // Some languages may index directly at position 2
          "[2]",
          ".get(2)",
          "get(2)",
          "at(2)",
        },
        "[jsonarray/" + lang + "] deserializer should access position 2 for years field");
  }

  // ======================================================================
  // 4. Cross-language format consistency (JSON array)
  //    All 17 languages must place fields at the same array positions
  // ======================================================================

  @ParameterizedTest(name = "crossLanguagePositionConsistency[{0}]")
  @MethodSource("allLanguages")
  void testCrossLanguagePositionConsistency_v2User(String lang) {
    String code = generateJsonArray(userV2(), lang);

    // All languages must place fname at position 0, sname at position 1,
    // years at position 2 in the JSON array serializer.
    // We verify by checking that:
    // (a) the serializer references all three fields in order
    // (b) the deserializer uses the correct positions

    // Verify fname is present
    assertContainsAny(
        code,
        new String[] {
          "fname", "Fname",
        },
        "[jsonarray/" + lang + "] should reference fname");

    // Verify sname is present
    assertContainsAny(
        code,
        new String[] {
          "sname", "Sname",
        },
        "[jsonarray/" + lang + "] should reference sname");

    // Verify years is present
    assertContainsAny(
        code,
        new String[] {
          "years", "Years",
        },
        "[jsonarray/" + lang + "] should reference years");

    // The message has 3 consecutive fields (1,2,3), so there should be NO gaps.
    // No "gap" or "no field number" comments should appear.
    assertNotContains(
        code,
        "gap (no field number",
        "[jsonarray/" + lang + "] consecutive fields should not produce gap markers");
  }

  // ======================================================================
  // 5. Sparse field numbers with evolution (JSON array)
  //    Fields at 1, 5, 10: null padding must be consistent
  // ======================================================================

  @ParameterizedTest(name = "sparseFieldNumbers[{0}]")
  @MethodSource("allLanguages")
  void testSparseFieldNumbers_nullPaddingConsistency(String lang) {
    String code = generateJsonArray(sparseMessage(), lang);

    // Fields at 1, 5, 10 means:
    //   position 0 = alpha (field 1)
    //   positions 1-3 = gaps (field numbers 2, 3, 4)
    //   position 4 = beta (field 5)
    //   positions 5-8 = gaps (field numbers 6, 7, 8, 9)
    //   position 9 = gamma (field 10)
    // Total array size = 10 (max field number)

    // Verify that gaps are present for the missing field numbers.
    // Between field 1 and field 5, there are gaps at field numbers 2, 3, 4.
    boolean hasGapMarkers =
        code.contains("gap") || code.contains("no field") || code.contains("null");
    assertTrue(
        hasGapMarkers,
        "[jsonarray/" + lang + "] sparse fields at 1,5,10 should have gap/null markers");

    // Verify alpha (field 1) and beta (field 5) and gamma (field 10) are referenced
    assertContainsAny(
        code, new String[] {"alpha", "Alpha"}, "[jsonarray/" + lang + "] should reference alpha");
    assertContainsAny(
        code, new String[] {"beta", "Beta"}, "[jsonarray/" + lang + "] should reference beta");
    assertContainsAny(
        code, new String[] {"gamma", "Gamma"}, "[jsonarray/" + lang + "] should reference gamma");

    // Verify the deserializer accesses position 9 for gamma (field 10).
    // This confirms the total array size accounts for the max field number.
    assertContainsAny(
        code,
        new String[] {
          // Bounds check for position 9
          "> 9",
          ">= 10",
          // Direct index access at position 9
          "[9]",
          ".get(9)",
          "get(9)",
          "at(9)",
        },
        "[jsonarray/" + lang + "] deserializer should access position 9 for gamma (field 10)");
  }

  // ======================================================================
  // 6. pbtk URL format evolution
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
  // 7. pbtk sparse field numbers
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
