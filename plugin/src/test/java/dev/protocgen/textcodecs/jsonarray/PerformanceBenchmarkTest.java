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
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import dev.protocgen.textcodecs.pbtkurl.PbtkPluginRunner;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance benchmark tests for the protoc-gen-jsonarray and protoc-gen-pbtkurl code generators.
 *
 * <p>Measures code generation throughput, security validation overhead, and multi-language
 * generation performance. Uses manual warm-up/measurement phases with statistical reporting.
 *
 * <p>Run separately with: {@code ./gradlew :plugin:test --tests "*.PerformanceBenchmarkTest"}
 */
@Tag("benchmark")
class PerformanceBenchmarkTest {

  private static final int WARMUP_ITERATIONS = 10;
  private static final int MEASUREMENT_ITERATIONS = 100;

  // -- Proto descriptors built once for all benchmarks --

  private static FileDescriptorProto simpleProto;
  private static FileDescriptorProto complexProto;
  private static CodeGeneratorRequest simpleJavaRequest;
  private static CodeGeneratorRequest complexJavaRequest;
  private static CodeGeneratorRequest simplePbtkJavaRequest;
  private static CodeGeneratorRequest complexPbtkJavaRequest;

  // -- Language names (17 canonical languages, no aliases) --

  private static final String[] ALL_LANGUAGES = {
    "java",
    "python",
    "javascript",
    "typescript",
    "c",
    "cpp",
    "rust",
    "zig",
    "go",
    "csharp",
    "kotlin",
    "swift",
    "dart",
    "php",
    "ruby",
    "objc",
    "perl"
  };

  @BeforeAll
  static void buildDescriptors() {
    simpleProto = buildSimpleProto();
    complexProto = buildComplexProto();

    simpleJavaRequest = buildRequest(simpleProto, "lang=java");
    complexJavaRequest = buildRequest(complexProto, "lang=java");
    simplePbtkJavaRequest = buildRequest(simpleProto, "lang=java");
    complexPbtkJavaRequest = buildRequest(complexProto, "lang=java");
  }

  // ======================================================================
  // 1. Code generation throughput -- JSON array format
  // ======================================================================

  @Test
  void benchmarkJsonArraySimpleMessageJava() {
    PluginRunner runner = new PluginRunner();
    long[] timings =
        benchmarkRunner(
            "jsonarray/simple/java",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            () -> {
              CodeGeneratorResponse resp = runner.run(simpleJavaRequest);
              return resp.getFileCount();
            });
    reportAndAssert(timings, "jsonarray: simple msg (Java)", 1_000, 5_000);
  }

  @Test
  void benchmarkJsonArrayComplexMessageJava() {
    PluginRunner runner = new PluginRunner();
    long[] timings =
        benchmarkRunner(
            "jsonarray/complex/java",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            () -> {
              CodeGeneratorResponse resp = runner.run(complexJavaRequest);
              return resp.getFileCount();
            });
    reportAndAssert(timings, "jsonarray: complex msg (Java)", 5_000, 25_000);
  }

  @Test
  void benchmarkJsonArrayAllLanguagesSimpleMessage() {
    PluginRunner runner = new PluginRunner();
    for (String lang : ALL_LANGUAGES) {
      CodeGeneratorRequest req = buildRequest(simpleProto, "lang=" + lang);
      long[] timings =
          benchmarkRunner(
              "jsonarray/simple/" + lang,
              WARMUP_ITERATIONS,
              MEASUREMENT_ITERATIONS,
              () -> {
                CodeGeneratorResponse resp = runner.run(req);
                return resp.getFileCount();
              });
      reportAndAssert(timings, "jsonarray: simple msg (" + lang + ")", 1_000, 5_000);
    }
  }

  // ======================================================================
  // 2. Code generation throughput -- pbtk URL format
  // ======================================================================

  @Test
  void benchmarkPbtkUrlSimpleMessageJava() {
    PbtkPluginRunner runner = new PbtkPluginRunner();
    long[] timings =
        benchmarkRunner(
            "pbtkurl/simple/java",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            () -> {
              CodeGeneratorResponse resp = runner.run(simplePbtkJavaRequest);
              return resp.getFileCount();
            });
    reportAndAssert(timings, "pbtkurl: simple msg (Java)", 1_000, 5_000);
  }

  @Test
  void benchmarkPbtkUrlComplexMessageJava() {
    PbtkPluginRunner runner = new PbtkPluginRunner();
    long[] timings =
        benchmarkRunner(
            "pbtkurl/complex/java",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            () -> {
              CodeGeneratorResponse resp = runner.run(complexPbtkJavaRequest);
              return resp.getFileCount();
            });
    reportAndAssert(timings, "pbtkurl: complex msg (Java)", 5_000, 25_000);
  }

  // ======================================================================
  // 3. Security validation overhead -- MessageAnalyzer
  // ======================================================================

  @Test
  void benchmarkMessageAnalyzerSimple() {
    TypeRegistry registry = new TypeRegistry();
    registry.registerFile(simpleProto);
    MessageAnalyzer analyzer = new MessageAnalyzer(registry);
    DescriptorProto descriptor = simpleProto.getMessageType(0);

    long[] timings =
        benchmarkRunner(
            "analyzer/simple",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            () -> {
              var msg = analyzer.analyze(descriptor, ".bench.");
              return msg.getFields().size();
            });
    reportAndAssert(timings, "MessageAnalyzer.analyze (simple)", 100, 1_000);
  }

  @Test
  void benchmarkMessageAnalyzerComplex() {
    TypeRegistry registry = new TypeRegistry();
    registry.registerFile(complexProto);
    MessageAnalyzer analyzer = new MessageAnalyzer(registry);
    DescriptorProto descriptor = complexProto.getMessageType(0);

    long[] timings =
        benchmarkRunner(
            "analyzer/complex",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            () -> {
              var msg = analyzer.analyze(descriptor, ".bench.");
              return msg.getFields().size();
            });
    reportAndAssert(timings, "MessageAnalyzer.analyze (complex)", 500, 2_000);
  }

  @Test
  void benchmarkTypeRegistryRegisterFile() {
    long[] timings =
        benchmarkRunner(
            "registry/registerFile",
            WARMUP_ITERATIONS,
            MEASUREMENT_ITERATIONS,
            () -> {
              TypeRegistry registry = new TypeRegistry();
              registry.registerFile(complexProto);
              return 1;
            });
    reportAndAssert(timings, "TypeRegistry.registerFile (complex)", 50, 500);
  }

  // ======================================================================
  // Benchmark harness
  // ======================================================================

  @FunctionalInterface
  interface BenchmarkBody {
    /** Returns an int result to prevent dead-code elimination by the JIT. */
    int run();
  }

  /**
   * Runs warm-up iterations (untimed), then measurement iterations (timed).
   *
   * @return array of nanosecond timings for each measurement iteration
   */
  private long[] benchmarkRunner(
      String label, int warmupCount, int measureCount, BenchmarkBody body) {
    // Accumulator prevents the JIT from optimizing away the benchmark body.
    int blackhole = 0;

    // Warm-up phase
    for (int i = 0; i < warmupCount; i++) {
      blackhole += body.run();
    }

    // Measurement phase
    long[] timings = new long[measureCount];
    for (int i = 0; i < measureCount; i++) {
      long start = System.nanoTime();
      blackhole += body.run();
      timings[i] = System.nanoTime() - start;
    }

    // Use blackhole to prevent dead-code elimination
    if (blackhole == Integer.MIN_VALUE) {
      // This branch is never taken but the JIT cannot prove it,
      // so it must keep the computation alive.
      System.err.println("blackhole: " + blackhole);
    }

    return timings;
  }

  // ======================================================================
  // Reporting and soft assertions
  // ======================================================================

  /**
   * Reports benchmark statistics to stderr and asserts a hard regression bound. The upper bound is
   * generous (milliseconds) to avoid flaky failures in CI, while still catching order-of-magnitude
   * regressions. A separate soft bound warns on smaller regressions without failing.
   *
   * @param timingsNs raw nanosecond timings
   * @param name human-readable benchmark name
   * @param softBoundUs soft bound in microseconds (warn only)
   * @param hardBoundUs hard bound in microseconds (test fails if p95 exceeds this)
   */
  private void reportAndAssert(long[] timingsNs, String name, long softBoundUs, long hardBoundUs) {
    Arrays.sort(timingsNs);
    long minNs = timingsNs[0];
    long maxNs = timingsNs[timingsNs.length - 1];
    long p50Ns = timingsNs[timingsNs.length / 2];
    long p95Ns = timingsNs[(int) (timingsNs.length * 0.95)];
    double meanNs = Arrays.stream(timingsNs).average().orElse(0);

    double minUs = minNs / 1_000.0;
    double maxUs = maxNs / 1_000.0;
    double meanUs = meanNs / 1_000.0;
    double p50Us = p50Ns / 1_000.0;
    double p95Us = p95Ns / 1_000.0;

    System.err.printf(
        "  %-40s  mean=%6.0f us  p50=%6.0f us  p95=%6.0f us  min=%6.0f us  max=%6.0f us%n",
        name, meanUs, p50Us, p95Us, minUs, maxUs);

    if (meanUs > softBoundUs) {
      System.err.printf(
          "  WARNING: %s exceeded soft bound of %d us (actual mean=%.0f us)%n",
          name, softBoundUs, meanUs);
    }

    // Hard regression guard — p95 must stay under the hard bound.
    // Bounds are generous (5-10x observed values) to avoid CI flakiness.
    assertTrue(
        p95Us < hardBoundUs,
        String.format(
            "PERFORMANCE REGRESSION: %s p95=%.0f us exceeds hard bound of %d us",
            name, p95Us, hardBoundUs));
  }

  // ======================================================================
  // Proto descriptor builders
  // ======================================================================

  /** Simple message: 2 scalar fields (string + int32). */
  private static FileDescriptorProto buildSimpleProto() {
    return FileDescriptorProto.newBuilder()
        .setName("bench.proto")
        .setPackage("bench")
        .setSyntax("proto3")
        .addMessageType(
            DescriptorProto.newBuilder()
                .setName("SimpleMsg")
                .addField(
                    FieldDescriptorProto.newBuilder()
                        .setName("name")
                        .setNumber(1)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
                .addField(
                    FieldDescriptorProto.newBuilder()
                        .setName("age")
                        .setNumber(2)
                        .setType(FieldDescriptorProto.Type.TYPE_INT32)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)))
        .build();
  }

  /**
   * Complex "kitchen sink" message with all field types: string, int32, int64, double, float, bool,
   * bytes, enum, nested message, repeated string, repeated message, map(string,string),
   * map(int32,string), oneof (2 fields), proto3 optional.
   */
  private static FileDescriptorProto buildComplexProto() {
    // Nested enum: Status
    EnumDescriptorProto statusEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("STATUS_UNKNOWN").setNumber(0))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("STATUS_ACTIVE").setNumber(1))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("STATUS_INACTIVE").setNumber(2))
            .build();

    // Nested message: Inner
    DescriptorProto innerMsg =
        DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("score")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
            .build();

    // Map entry: map<string, string>
    DescriptorProto stringMapEntry =
        DescriptorProto.newBuilder()
            .setName("MetadataEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("key")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
            .build();

    // Map entry: map<int32, string>
    DescriptorProto intMapEntry =
        DescriptorProto.newBuilder()
            .setName("LookupEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("key")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
            .build();

    // Build the kitchen-sink message
    DescriptorProto.Builder kitchenSink =
        DescriptorProto.newBuilder()
            .setName("KitchenSink")
            .addEnumType(statusEnum)
            .addNestedType(innerMsg)
            .addNestedType(stringMapEntry)
            .addNestedType(intMapEntry);

    // Oneof declaration
    kitchenSink.addOneofDecl(OneofDescriptorProto.newBuilder().setName("result"));
    // Synthetic oneof for proto3 optional
    kitchenSink.addOneofDecl(OneofDescriptorProto.newBuilder().setName("_opt_tag"));

    // Field 1: string
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("str_field")
            .setNumber(1)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 2: int32
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("int_field")
            .setNumber(2)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 3: int64
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("long_field")
            .setNumber(3)
            .setType(FieldDescriptorProto.Type.TYPE_INT64)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 4: double
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("double_field")
            .setNumber(4)
            .setType(FieldDescriptorProto.Type.TYPE_DOUBLE)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 5: float
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("float_field")
            .setNumber(5)
            .setType(FieldDescriptorProto.Type.TYPE_FLOAT)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 6: bool
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("bool_field")
            .setNumber(6)
            .setType(FieldDescriptorProto.Type.TYPE_BOOL)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 7: bytes
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("bytes_field")
            .setNumber(7)
            .setType(FieldDescriptorProto.Type.TYPE_BYTES)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 8: enum
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("status")
            .setNumber(8)
            .setType(FieldDescriptorProto.Type.TYPE_ENUM)
            .setTypeName(".bench.KitchenSink.Status")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 9: nested message
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("inner")
            .setNumber(9)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".bench.KitchenSink.Inner")
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

    // Field 10: repeated string
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("tags")
            .setNumber(10)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));

    // Field 11: repeated message
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("items")
            .setNumber(11)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".bench.KitchenSink.Inner")
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));

    // Field 12: map<string, string>
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("metadata")
            .setNumber(12)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".bench.KitchenSink.MetadataEntry")
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));

    // Field 13: map<int32, string>
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("lookup")
            .setNumber(13)
            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".bench.KitchenSink.LookupEntry")
            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));

    // Field 14: oneof member -- string success_msg (oneof_index=0)
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("success_msg")
            .setNumber(14)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0));

    // Field 15: oneof member -- int32 error_code (oneof_index=0)
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("error_code")
            .setNumber(15)
            .setType(FieldDescriptorProto.Type.TYPE_INT32)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setOneofIndex(0));

    // Field 16: proto3 optional string
    kitchenSink.addField(
        FieldDescriptorProto.newBuilder()
            .setName("opt_tag")
            .setNumber(16)
            .setType(FieldDescriptorProto.Type.TYPE_STRING)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setProto3Optional(true)
            .setOneofIndex(1));

    return FileDescriptorProto.newBuilder()
        .setName("bench.proto")
        .setPackage("bench")
        .setSyntax("proto3")
        .addMessageType(kitchenSink)
        .build();
  }

  private static CodeGeneratorRequest buildRequest(FileDescriptorProto proto, String parameter) {
    return CodeGeneratorRequest.newBuilder()
        .addProtoFile(proto)
        .addFileToGenerate(proto.getName())
        .setParameter(parameter)
        .build();
  }
}
