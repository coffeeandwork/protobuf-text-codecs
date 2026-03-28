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

import dev.protocgen.textcodecs.jsonarray.model.WellKnownType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WellKnownTypeTest {

  @Test
  void testFromTypeName() {
    assertEquals(WellKnownType.TIMESTAMP, WellKnownType.fromTypeName(".google.protobuf.Timestamp"));
  }

  @Test
  void testUnknownTypeName() {
    assertNull(WellKnownType.fromTypeName(".example.Foo"));
  }

  @Test
  void testIsWrapperType() {
    assertTrue(WellKnownType.INT32_VALUE.isWrapperType());
    assertTrue(WellKnownType.STRING_VALUE.isWrapperType());
    assertTrue(WellKnownType.BOOL_VALUE.isWrapperType());
    assertFalse(WellKnownType.TIMESTAMP.isWrapperType());
    assertFalse(WellKnownType.STRUCT.isWrapperType());
    assertFalse(WellKnownType.ANY.isWrapperType());
  }
}
