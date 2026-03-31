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
package dev.protocgen.textcodecs.pbtkurl;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end parameterized code generation tests for all 16 non-Java language targets of the pbtk
 * URL encoding plugin. Verifies the generated source contains expected language-specific pbtk URL
 * encoding patterns.
 */
class PbtkMultiLanguageCodeGenTest {

  // ======================================================================
  // Language expectation record and data provider
  // ======================================================================

  record LangExpectation(String lang, Map<String, List<String>> patterns) {

    @Override
    public String toString() {
      return lang;
    }
  }

  static Stream<Arguments> languages() {
    return Stream.of(
        Arguments.of(
            new LangExpectation(
                "python",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("self._name", "self._age")),
                    Map.entry("pbtk_serialize", List.of("def to_pbtk_url(self):")),
                    Map.entry("pbtk_deserialize", List.of("def from_pbtk_url(cls,")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("urllib.parse.quote(")),
                    Map.entry("bytes_base64", List.of("base64.b64encode(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("\"1\" if", "\"0\""))))),
        Arguments.of(
            new LangExpectation(
                "javascript",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("this.name = \"\"", "this.age = 0")),
                    Map.entry("pbtk_serialize", List.of("toPbtkUrl()")),
                    Map.entry("pbtk_deserialize", List.of("static fromPbtkUrl(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("encodeURIComponent(")),
                    Map.entry("bytes_base64", List.of("toString('base64')", "btoa(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? '1' : '0'"))))),
        Arguments.of(
            new LangExpectation(
                "typescript",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("this.name = \"\"", "this.age = 0")),
                    Map.entry("pbtk_serialize", List.of("toPbtkUrl()")),
                    Map.entry("pbtk_deserialize", List.of("static fromPbtkUrl(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("encodeURIComponent(")),
                    Map.entry("bytes_base64", List.of("toString('base64')", "btoa(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? '1' : '0'"))))),
        Arguments.of(
            new LangExpectation(
                "c",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("char*", "int32_t")),
                    Map.entry("pbtk_serialize", List.of("_to_pbtk_url(")),
                    Map.entry("pbtk_deserialize", List.of("_from_pbtk_url(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("pbtk_url_encode(")),
                    Map.entry("bytes_base64", List.of("pbtk_base64_encode(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? \"1\" : \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "cpp",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("std::string", "int32_t")),
                    Map.entry("pbtk_serialize", List.of("to_pbtk_url() const")),
                    Map.entry("pbtk_deserialize", List.of("from_pbtk_url(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("url_encode(")),
                    Map.entry("bytes_base64", List.of("base64_encode(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? \"1\" : \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "rust",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("String", "i32")),
                    Map.entry("pbtk_serialize", List.of("pub fn to_pbtk_url(&self)")),
                    Map.entry("pbtk_deserialize", List.of("pub fn from_pbtk_url(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("urlencoding::encode(")),
                    Map.entry("bytes_base64", List.of("general_purpose::STANDARD.encode(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("\"1\" } else { \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "zig",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("[]const u8", "i32")),
                    Map.entry("pbtk_serialize", List.of("pub fn toPbtkUrl(")),
                    Map.entry("pbtk_deserialize", List.of("pub fn fromPbtkUrl(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("std.Uri.percentEncode(")),
                    Map.entry("bytes_base64", List.of("std.base64")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("\"1\" else \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "go",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("string", "int32")),
                    Map.entry("pbtk_serialize", List.of("ToPbtkUrl()")),
                    Map.entry("pbtk_deserialize", List.of("DeserializeMsgFromPbtkUrl(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("url.QueryEscape(")),
                    Map.entry("bytes_base64", List.of("base64.StdEncoding.EncodeToString(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("!1b1", "!1b0"))))),
        Arguments.of(
            new LangExpectation(
                "csharp",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("string", "int")),
                    Map.entry("pbtk_serialize", List.of("public string ToPbtkUrl()")),
                    Map.entry(
                        "pbtk_deserialize", List.of("public static", "FromPbtkUrl(string input)")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("Uri.EscapeDataString(")),
                    Map.entry("bytes_base64", List.of("Convert.ToBase64String(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? \"1\" : \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "kotlin",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("val", "String", "Int")),
                    Map.entry("pbtk_serialize", List.of("fun toPbtkUrl(): String")),
                    Map.entry("pbtk_deserialize", List.of("fun fromPbtkUrl(input: String)")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("java.net.URLEncoder.encode(")),
                    Map.entry(
                        "bytes_base64", List.of("java.util.Base64.getEncoder().encodeToString(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("if (", "\"1\" else \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "swift",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("public var", "String", "Int32")),
                    Map.entry("pbtk_serialize", List.of("func toPbtkUrl() -> String")),
                    Map.entry("pbtk_deserialize", List.of("static func fromPbtkUrl(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry(
                        "string_url_encode",
                        List.of("addingPercentEncoding(withAllowedCharacters:")),
                    Map.entry("bytes_base64", List.of("base64EncodedString()")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? \"1\" : \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "dart",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("String", "int")),
                    Map.entry("pbtk_serialize", List.of("String toPbtkUrl()")),
                    Map.entry("pbtk_deserialize", List.of("static", "fromPbtkUrl(String")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("Uri.encodeComponent(")),
                    Map.entry("bytes_base64", List.of("base64Encode(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? '1' : '0'"))))),
        Arguments.of(
            new LangExpectation(
                "php",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("string $", "int $")),
                    Map.entry("pbtk_serialize", List.of("public function toPbtkUrl(): string")),
                    Map.entry("pbtk_deserialize", List.of("public static function fromPbtkUrl(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("rawurlencode(")),
                    Map.entry("bytes_base64", List.of("base64_encode(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? '1' : '0'"))))),
        Arguments.of(
            new LangExpectation(
                "ruby",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("attr_accessor")),
                    Map.entry("pbtk_serialize", List.of("def to_pbtk_url")),
                    Map.entry("pbtk_deserialize", List.of("def self.from_pbtk_url(")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("CGI.escape(")),
                    Map.entry("bytes_base64", List.of("Base64.strict_encode64(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? \"1\" : \"0\""))))),
        Arguments.of(
            new LangExpectation(
                "objc",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("@property", "NSString", "int32_t")),
                    Map.entry("pbtk_serialize", List.of("- (NSString *)toPbtkUrl")),
                    Map.entry(
                        "pbtk_deserialize",
                        List.of("+ (instancetype)fromPbtkUrl:(NSString *)input")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("pbtk_url_encode(")),
                    Map.entry("bytes_base64", List.of("pbtk_base64_encode(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? @\"1\" : @\"0\""))))),
        Arguments.of(
            new LangExpectation(
                "perl",
                Map.ofEntries(
                    Map.entry("scalar_field_decl", List.of("$self->{")),
                    Map.entry("pbtk_serialize", List.of("sub to_pbtk_url {")),
                    Map.entry("pbtk_deserialize", List.of("sub from_pbtk_url {")),
                    Map.entry("pbtk_prefix", List.of("!1s")),
                    Map.entry("string_url_encode", List.of("uri_escape_utf8(")),
                    Map.entry("bytes_base64", List.of("encode_base64(")),
                    Map.entry("nested_message", List.of("!1m")),
                    Map.entry("enum_field", List.of("!1e")),
                    Map.entry("bool_encoding", List.of("? \"1\" : \"0\""))))));
  }

  // ======================================================================
  // Helpers
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

  private FieldDescriptorProto repeatedField(
      String name, int number, FieldDescriptorProto.Type type) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(type)
        .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
        .build();
  }

  private FieldDescriptorProto messageField(String name, int number, String typeName) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
        .setTypeName(typeName)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  private FieldDescriptorProto enumField(String name, int number, String typeName) {
    return FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setType(FieldDescriptorProto.Type.TYPE_ENUM)
        .setTypeName(typeName)
        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .build();
  }

  /** Generate code for a single message with the given language. */
  private String generate(DescriptorProto msg, String lang) {
    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("test.proto")
            .setPackage("test")
            .setSyntax("proto3")
            .addMessageType(msg)
            .build();

    CodeGeneratorRequest request =
        CodeGeneratorRequest.newBuilder()
            .addProtoFile(file)
            .addFileToGenerate("test.proto")
            .setParameter("lang=" + lang)
            .build();

    CodeGeneratorResponse response = new PbtkPluginRunner().run(request);
    assertFalse(response.hasError(), lang + ": " + response.getError());

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < response.getFileCount(); i++) {
      sb.append(response.getFile(i).getContent());
    }
    return sb.toString();
  }

  private void assertContainsAny(String code, List<String> patterns, String lang, String desc) {
    for (String pattern : patterns) {
      if (code.contains(pattern)) {
        return;
      }
    }
    StringBuilder msg = new StringBuilder();
    msg.append("[").append(lang).append("] ").append(desc);
    msg.append(": expected at least one of ");
    msg.append(patterns);
    msg.append(" but none found in generated code.");
    assertTrue(false, msg.toString());
  }

  // ======================================================================
  // 1. Scalar fields (string + int32)
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testScalarFields(LangExpectation lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("name", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generate(msg, lang.lang());
    assertContainsAny(
        code, lang.patterns().get("scalar_field_decl"), lang.lang(), "scalar field declarations");
  }

  // ======================================================================
  // 2. Pbtk serialize method exists
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testPbtkSerializeExists(LangExpectation lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generate(msg, lang.lang());
    assertContainsAny(
        code, lang.patterns().get("pbtk_serialize"), lang.lang(), "pbtk serialize method");
  }

  // ======================================================================
  // 3. Pbtk deserialize method exists
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testPbtkDeserializeExists(LangExpectation lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("x", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .build();
    String code = generate(msg, lang.lang());
    assertContainsAny(
        code, lang.patterns().get("pbtk_deserialize"), lang.lang(), "pbtk deserialize method");
  }

  // ======================================================================
  // 4. Nested message (!<n>m pattern)
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testNestedMessage(LangExpectation lang) {
    DescriptorProto innerMsg =
        DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(scalarField("value", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Outer")
            .addNestedType(innerMsg)
            .addField(messageField("inner", 1, ".test.Outer.Inner"))
            .build();
    String code = generate(msg, lang.lang());
    assertContainsAny(
        code, lang.patterns().get("nested_message"), lang.lang(), "nested message !<n>m pattern");
  }

  // ======================================================================
  // 5. Repeated field (for loop with pbtk append)
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testRepeatedField(LangExpectation lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(repeatedField("tags", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build();
    String code = generate(msg, lang.lang());
    // Repeated string fields emit !1s prefix per element and use URL encoding
    assertContainsAny(
        code, lang.patterns().get("pbtk_prefix"), lang.lang(), "repeated field pbtk prefix");
    assertContainsAny(
        code,
        lang.patterns().get("string_url_encode"),
        lang.lang(),
        "string URL encoding in repeated field");
  }

  // ======================================================================
  // 6. Enum field (!<n>e pattern)
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testEnumField(LangExpectation lang) {
    EnumDescriptorProto statusEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1).build())
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addEnumType(statusEnum)
            .addField(enumField("status", 1, ".test.Msg.Status"))
            .build();
    String code = generate(msg, lang.lang());
    assertContainsAny(
        code, lang.patterns().get("enum_field"), lang.lang(), "enum field !<n>e pattern");
  }

  // ======================================================================
  // 7. Bool encoding (0/1)
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testBoolEncoding(LangExpectation lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("active", 1, FieldDescriptorProto.Type.TYPE_BOOL))
            .build();
    String code = generate(msg, lang.lang());
    assertContainsAny(code, lang.patterns().get("bool_encoding"), lang.lang(), "bool 0/1 encoding");
  }

  // ======================================================================
  // 8. Bytes base64 (z type char)
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testBytesBase64(LangExpectation lang) {
    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addField(scalarField("data", 1, FieldDescriptorProto.Type.TYPE_BYTES))
            .build();
    String code = generate(msg, lang.lang());
    assertContainsAny(
        code, lang.patterns().get("bytes_base64"), lang.lang(), "bytes base64 with z type char");
  }

  // ======================================================================
  // 9. Map field (!<n>m2 pattern)
  // ======================================================================

  @ParameterizedTest(name = "{0}")
  @MethodSource("languages")
  void testMapField(LangExpectation lang) {
    DescriptorProto mapEntry =
        DescriptorProto.newBuilder()
            .setName("MetadataEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
            .addField(scalarField("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("value", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("Msg")
            .addNestedType(mapEntry)
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("metadata")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Msg.MetadataEntry")
                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build())
            .build();

    String code = generate(msg, lang.lang());
    // Map entries serialize as !<num>m2 (message with 2 sub-fields for key+value)
    assertTrue(
        code.contains("!1m2"),
        "[" + lang.lang() + "] map field: expected !1m2 pattern in generated code");
  }
}
