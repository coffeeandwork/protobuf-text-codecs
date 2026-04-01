import com.example.User;
import com.example.Address;

/**
 * Standalone round-trip test for User/Address JSON array serialization.
 * Creates a User via Builder, serializes to JSON, verifies the output,
 * deserializes back, and verifies all fields match.
 *
 * No external dependencies required -- uses the built-in JsonArrayWriter/Reader.
 */
public class UserRoundTripTest {

    private static final String EXPECTED_JSON =
            "[\"Alice\",\"Smith\",30,[\"123 Main Street\",\"Springfield\",\"IL\",62704]]";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        try {
            testSerialize();
            testDeserialize();
            testRoundTrip();
        } catch (Exception e) {
            System.err.println("FATAL: Unexpected exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println();
        System.out.printf("Results: %d passed, %d failed%n", passed, failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testSerialize() {
        System.out.println("--- Test: Serialize User to JSON ---");

        Address address = Address.newBuilder()
                .setStreet("123 Main Street")
                .setCity("Springfield")
                .setState("IL")
                .setZip(62704)
                .build();

        User user = User.newBuilder()
                .setFirstname("Alice")
                .setLastname("Smith")
                .setAge(30)
                .setAddress(address)
                .build();

        String json = new String(user.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("  Serialized: " + json);

        if (EXPECTED_JSON.equals(json)) {
            pass("Serialization matches expected JSON");
        } else {
            fail("Serialization mismatch",
                    "expected: " + EXPECTED_JSON + "\n  actual:   " + json);
        }
    }

    private static void testDeserialize() {
        System.out.println("--- Test: Deserialize User from JSON ---");

        User user = User.parseFrom(EXPECTED_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        check("firstname", "Alice", user.getFirstname());
        check("lastname", "Smith", user.getLastname());
        check("age", 30, user.getAge());

        if (user.getAddress() == null) {
            fail("address", "expected non-null Address, got null");
        } else {
            check("address.street", "123 Main Street", user.getAddress().getStreet());
            check("address.city", "Springfield", user.getAddress().getCity());
            check("address.state", "IL", user.getAddress().getState());
            check("address.zip", 62704, user.getAddress().getZip());
        }
    }

    private static void testRoundTrip() {
        System.out.println("--- Test: Full round-trip (serialize -> deserialize -> serialize) ---");

        Address address = Address.newBuilder()
                .setStreet("123 Main Street")
                .setCity("Springfield")
                .setState("IL")
                .setZip(62704)
                .build();

        User original = User.newBuilder()
                .setFirstname("Alice")
                .setLastname("Smith")
                .setAge(30)
                .setAddress(address)
                .build();

        String json1 = new String(original.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        User deserialized = User.parseFrom(json1.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String json2 = new String(deserialized.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);

        if (json1.equals(json2)) {
            pass("Round-trip produces identical JSON");
        } else {
            fail("Round-trip JSON mismatch",
                    "first:  " + json1 + "\n  second: " + json2);
        }
    }

    private static void check(String field, Object expected, Object actual) {
        if (expected.equals(actual)) {
            pass(field + " = " + actual);
        } else {
            fail(field, "expected " + expected + ", got " + actual);
        }
    }

    private static void pass(String msg) {
        System.out.println("  PASS: " + msg);
        passed++;
    }

    private static void fail(String msg, String detail) {
        System.out.println("  FAIL: " + msg);
        System.out.println("  " + detail);
        failed++;
    }
}
