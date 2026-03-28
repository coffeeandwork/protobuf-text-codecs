package dev.protocgen.textcodecs.jsonarray;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeRegistryTest {

  // ---- registerFile and getMessage ----

  @Test
  void testRegisterAndGetMessage() {
    TypeRegistry registry = new TypeRegistry();

    DescriptorProto msg =
        DescriptorProto.newBuilder()
            .setName("User")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("name")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("user.proto")
            .setPackage("example")
            .addMessageType(msg)
            .build();

    registry.registerFile(file);

    DescriptorProto result = registry.getMessage(".example.User");
    assertNotNull(result);
    assertEquals("User", result.getName());
  }

  @Test
  void testGetMessageReturnsNullForUnknown() {
    TypeRegistry registry = new TypeRegistry();
    assertNull(registry.getMessage(".nonexistent.Type"));
  }

  @Test
  void testRegisterFileWithEmptyPackage() {
    TypeRegistry registry = new TypeRegistry();

    DescriptorProto msg = DescriptorProto.newBuilder().setName("Root").build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("root.proto")
            .setPackage("")
            .addMessageType(msg)
            .build();

    registry.registerFile(file);

    // With empty package, the prefix is just "."
    DescriptorProto result = registry.getMessage(".Root");
    assertNotNull(result);
    assertEquals("Root", result.getName());
  }

  // ---- nested type registration ----

  @Test
  void testNestedTypeRegistration() {
    TypeRegistry registry = new TypeRegistry();

    DescriptorProto inner =
        DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    DescriptorProto outer =
        DescriptorProto.newBuilder().setName("Outer").addNestedType(inner).build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("nested.proto")
            .setPackage("pkg")
            .addMessageType(outer)
            .build();

    registry.registerFile(file);

    // Both outer and inner should be registered
    assertNotNull(registry.getMessage(".pkg.Outer"));
    assertNotNull(registry.getMessage(".pkg.Outer.Inner"));
    assertEquals("Inner", registry.getMessage(".pkg.Outer.Inner").getName());
  }

  @Test
  void testDeeplyNestedTypes() {
    TypeRegistry registry = new TypeRegistry();

    DescriptorProto level3 = DescriptorProto.newBuilder().setName("Level3").build();
    DescriptorProto level2 =
        DescriptorProto.newBuilder().setName("Level2").addNestedType(level3).build();
    DescriptorProto level1 =
        DescriptorProto.newBuilder().setName("Level1").addNestedType(level2).build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("deep.proto")
            .setPackage("deep")
            .addMessageType(level1)
            .build();

    registry.registerFile(file);

    assertNotNull(registry.getMessage(".deep.Level1"));
    assertNotNull(registry.getMessage(".deep.Level1.Level2"));
    assertNotNull(registry.getMessage(".deep.Level1.Level2.Level3"));
  }

  // ---- isMapEntry detection ----

  @Test
  void testIsMapEntry() {
    TypeRegistry registry = new TypeRegistry();

    DescriptorProto mapEntry =
        DescriptorProto.newBuilder()
            .setName("LabelsEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("key")
                    .setNumber(1)
                    .setType(FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .addField(
                FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setNumber(2)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build())
            .build();

    DescriptorProto parent =
        DescriptorProto.newBuilder().setName("Resource").addNestedType(mapEntry).build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("resource.proto")
            .setPackage("example")
            .addMessageType(parent)
            .build();

    registry.registerFile(file);

    assertTrue(registry.isMapEntry(".example.Resource.LabelsEntry"));
  }

  @Test
  void testIsMapEntryReturnsFalseForNormalMessage() {
    TypeRegistry registry = new TypeRegistry();

    DescriptorProto normalMsg = DescriptorProto.newBuilder().setName("Normal").build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("normal.proto")
            .setPackage("example")
            .addMessageType(normalMsg)
            .build();

    registry.registerFile(file);

    assertFalse(registry.isMapEntry(".example.Normal"));
  }

  @Test
  void testIsMapEntryReturnsFalseForUnknownType() {
    TypeRegistry registry = new TypeRegistry();
    assertFalse(registry.isMapEntry(".nonexistent.Entry"));
  }

  // ---- getEnum ----

  @Test
  void testGetEnum() {
    TypeRegistry registry = new TypeRegistry();

    EnumDescriptorProto statusEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0).build())
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1).build())
            .build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("status.proto")
            .setPackage("example")
            .addEnumType(statusEnum)
            .build();

    registry.registerFile(file);

    EnumDescriptorProto result = registry.getEnum(".example.Status");
    assertNotNull(result);
    assertEquals("Status", result.getName());
    assertEquals(2, result.getValueCount());
  }

  @Test
  void testGetEnumReturnsNullForUnknown() {
    TypeRegistry registry = new TypeRegistry();
    assertNull(registry.getEnum(".nonexistent.Enum"));
  }

  @Test
  void testNestedEnumRegistration() {
    TypeRegistry registry = new TypeRegistry();

    EnumDescriptorProto nestedEnum =
        EnumDescriptorProto.newBuilder()
            .setName("Kind")
            .addValue(
                EnumValueDescriptorProto.newBuilder().setName("UNSPECIFIED").setNumber(0).build())
            .build();

    DescriptorProto msg =
        DescriptorProto.newBuilder().setName("Item").addEnumType(nestedEnum).build();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder()
            .setName("item.proto")
            .setPackage("example")
            .addMessageType(msg)
            .build();

    registry.registerFile(file);

    EnumDescriptorProto result = registry.getEnum(".example.Item.Kind");
    assertNotNull(result);
    assertEquals("Kind", result.getName());
  }

  // ---- getFile ----

  @Test
  void testGetFile() {
    TypeRegistry registry = new TypeRegistry();

    FileDescriptorProto file =
        FileDescriptorProto.newBuilder().setName("myfile.proto").setPackage("pkg").build();

    registry.registerFile(file);

    FileDescriptorProto result = registry.getFile("myfile.proto");
    assertNotNull(result);
    assertEquals("myfile.proto", result.getName());
  }

  @Test
  void testGetFileReturnsNullForUnknown() {
    TypeRegistry registry = new TypeRegistry();
    assertNull(registry.getFile("nonexistent.proto"));
  }

  // ---- multiple files ----

  @Test
  void testMultipleFileRegistration() {
    TypeRegistry registry = new TypeRegistry();

    DescriptorProto msg1 = DescriptorProto.newBuilder().setName("Alpha").build();
    FileDescriptorProto file1 =
        FileDescriptorProto.newBuilder()
            .setName("alpha.proto")
            .setPackage("one")
            .addMessageType(msg1)
            .build();

    DescriptorProto msg2 = DescriptorProto.newBuilder().setName("Beta").build();
    FileDescriptorProto file2 =
        FileDescriptorProto.newBuilder()
            .setName("beta.proto")
            .setPackage("two")
            .addMessageType(msg2)
            .build();

    registry.registerFile(file1);
    registry.registerFile(file2);

    assertNotNull(registry.getMessage(".one.Alpha"));
    assertNotNull(registry.getMessage(".two.Beta"));
    assertNotNull(registry.getFile("alpha.proto"));
    assertNotNull(registry.getFile("beta.proto"));
  }
}
