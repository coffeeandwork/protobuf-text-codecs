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

import dev.protocgen.textcodecs.jsonarray.codegen.java.JavaNameResolver;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaNameResolverTest {

  private final JavaNameResolver resolver = new JavaNameResolver();

  @Test
  void testSnakeToCamel() {
    assertEquals("firstName", resolver.fieldName("first_name"));
    assertEquals("aBC", resolver.fieldName("a_b_c"));
    assertEquals("", resolver.fieldName(""));
  }

  @Test
  void testSnakeToPascal() {
    assertEquals("getFirstName", resolver.getterName("first_name"));
    // snakeToPascal is exercised through getter/setter which prepend get/set
    // Verify directly via setter prefix
    assertEquals("setFirstName", resolver.setterName("first_name"));
  }

  @Test
  void testFieldName() {
    assertEquals("myField", resolver.fieldName("my_field"));
  }

  @Test
  void testGetterSetter() {
    assertEquals("getMyField", resolver.getterName("my_field"));
    assertEquals("setMyField", resolver.setterName("my_field"));
  }

  @Test
  void testResolvePackage() {
    // With java_package option set
    ProtoFile withJavaPackage =
        new ProtoFile(
            "user.proto",
            "example",
            "com.example.generated",
            null,
            "proto3",
            List.of(),
            List.of(),
            List.of());
    assertEquals("com.example.generated", resolver.resolvePackage(withJavaPackage));

    // Without java_package option — falls back to proto package
    ProtoFile withoutJavaPackage =
        new ProtoFile(
            "user.proto", "example", null, null, "proto3", List.of(), List.of(), List.of());
    assertEquals("example", resolver.resolvePackage(withoutJavaPackage));

    // Empty java_package — falls back to proto package
    ProtoFile emptyJavaPackage =
        new ProtoFile("user.proto", "example", "", null, "proto3", List.of(), List.of(), List.of());
    assertEquals("example", resolver.resolvePackage(emptyJavaPackage));
  }
}
