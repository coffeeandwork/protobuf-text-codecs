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
