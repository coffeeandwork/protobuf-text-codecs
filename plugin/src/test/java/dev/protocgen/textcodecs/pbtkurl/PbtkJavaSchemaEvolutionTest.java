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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that generated Java pbtk URL code embodies the correct schema evolution contract. Since
 * generated code cannot be compiled within JUnit, we inspect the generated source for specific
 * behavioral patterns that ensure forward and backward compatibility.
 *
 * <p>Covers: pbtk URL v1/v2 serializer output, deserializer field dispatch, default cases for
 * unknown fields, and builder default values.
 */
class PbtkJavaSchemaEvolutionTest {

  private final PbtkPluginRunner pbtkRunner = new PbtkPluginRunner();

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

  /** Generate pbtk URL Java code for a single proto3 message. */
  private String generatePbtk(DescriptorProto msg) {
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
            .setParameter("lang=java")
            .build();

    CodeGeneratorResponse response = pbtkRunner.run(request);
    assertFalse(response.hasError(), "Expected no error, got: " + response.getError());
    assertTrue(response.getFileCount() > 0, "Expected at least one generated file");
    return response.getFile(0).getContent();
  }

  // ======================================================================
  // 1. Pbtk URL v1 serializer output
  // ======================================================================

  @Test
  void testPbtkUrlV1SerializerOutput() {
    DescriptorProto v1User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generatePbtk(v1User);

    // v1 serializer produces !1s<firstname>!2s<lastname>
    assertTrue(code.contains("\"!1s\""), "v1 pbtk serializer has !1s for firstname");
    assertTrue(code.contains("\"!2s\""), "v1 pbtk serializer has !2s for lastname");
    assertFalse(code.contains("\"!3i\""), "v1 pbtk serializer must not have !3i");

    // v1 serializer uses URLEncoder for string fields
    assertTrue(
        code.contains("URLEncoder.encode(this.firstname"),
        "v1 pbtk serializer URL-encodes firstname");
    assertTrue(
        code.contains("URLEncoder.encode(this.lastname"),
        "v1 pbtk serializer URL-encodes lastname");
  }

  // ======================================================================
  // 2. Pbtk URL v2 serializer output
  // ======================================================================

  @Test
  void testPbtkUrlV2SerializerOutput() {
    DescriptorProto v2User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generatePbtk(v2User);

    // v2 serializer produces !1s<firstname>!2s<lastname>!3i<age>
    assertTrue(code.contains("\"!1s\""), "v2 pbtk serializer has !1s for firstname");
    assertTrue(code.contains("\"!2s\""), "v2 pbtk serializer has !2s for lastname");
    assertTrue(code.contains("\"!3i\""), "v2 pbtk serializer has !3i for age");

    // v2 serializer appends the age value
    assertTrue(code.contains("append(this.age)"), "v2 pbtk serializer appends age value");
  }

  // ======================================================================
  // 3. Pbtk URL v2 deserializer handles missing age
  // ======================================================================

  @Test
  void testPbtkUrlV2DeserializerHandlesMissingAge() {
    DescriptorProto v2User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("age", 3, FieldDescriptorProto.Type.TYPE_INT32))
            .build();

    String code = generatePbtk(v2User);

    // v2 deserializer uses switch on field number -- unknown fields hit the default case
    assertTrue(code.contains("switch (fieldNum)"), "pbtk deserializer uses switch on fieldNum");
    assertTrue(code.contains("case 1:"), "pbtk deserializer handles field 1");
    assertTrue(code.contains("case 2:"), "pbtk deserializer handles field 2");
    assertTrue(code.contains("case 3:"), "pbtk deserializer handles field 3");

    // When age is missing from input, the builder default (0) applies
    assertTrue(code.contains("private int age = 0"), "builder initializes age to 0");

    // The default case skips unknown tokens without error
    assertTrue(code.contains("default:"), "pbtk deserializer has default case for unknown fields");
  }

  // ======================================================================
  // 4. Pbtk URL v1 deserializer ignores unknown tokens
  // ======================================================================

  @Test
  void testPbtkUrlV1DeserializerIgnoresUnknownTokens() {
    DescriptorProto v1User =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(scalarField("firstname", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(scalarField("lastname", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build();

    String code = generatePbtk(v1User);

    // v1 deserializer has cases only for fields 1 and 2
    assertTrue(code.contains("case 1:"), "v1 pbtk deserializer handles field 1");
    assertTrue(code.contains("case 2:"), "v1 pbtk deserializer handles field 2");
    assertFalse(code.contains("case 3:"), "v1 pbtk deserializer must not have case 3");

    // The default case silently skips unknown field tokens (like !3i30)
    assertTrue(
        code.contains("default:"), "v1 pbtk deserializer has default case to skip unknown fields");

    // The deserializer increments offset and consumed for unknown tokens, then continues
    assertTrue(
        code.contains("offset[0]++; consumed++; break"),
        "v1 pbtk deserializer advances past unknown tokens");
  }
}
