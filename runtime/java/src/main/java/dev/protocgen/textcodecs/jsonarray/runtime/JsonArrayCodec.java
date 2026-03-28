package dev.protocgen.textcodecs.jsonarray.runtime;

/**
 * Interface for messages that support JSON array serialization/deserialization. Generated code
 * implements this interface. Zero dependencies — no Jackson required.
 *
 * @param <T> the message type
 */
public interface JsonArrayCodec<T> {

  /** Serialize this message to a JSON array string. */
  String toJsonString();

  /** Serialize this message to UTF-8 bytes. */
  byte[] toJsonBytes();
}
