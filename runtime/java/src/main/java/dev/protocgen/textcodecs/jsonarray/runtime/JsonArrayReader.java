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
package dev.protocgen.textcodecs.jsonarray.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON parser for positional arrays. Zero dependencies -- replaces
 * Jackson for deserialization.
 *
 * <p>Returns a lightweight {@code Object} tree: {@link List} for arrays, {@link Map} for objects,
 * {@link String}, {@link Long}, {@link Double}, {@link Boolean}, or {@code null}.
 */
public final class JsonArrayReader {

  private final String json;
  private int pos;

  private JsonArrayReader(String json) {
    this.json = json;
    this.pos = 0;
  }

  /**
   * Parse a JSON string into a {@link List} (array), {@link Map} (object), {@link String}, {@link
   * Long}, {@link Double}, {@link Boolean}, or {@code null}.
   */
  public static Object parse(String json) {
    JsonArrayReader reader = new JsonArrayReader(json);
    Object value = reader.readValue();
    reader.skipWhitespace();
    if (reader.pos < reader.json.length()) {
      throw reader.error("unexpected trailing content");
    }
    return value;
  }

  /** Parse and return as {@link List}, throwing if the top-level value is not an array. */
  @SuppressWarnings("unchecked")
  public static List<Object> parseArray(String json) {
    Object value = parse(json);
    if (value instanceof List) {
      return (List<Object>) value;
    }
    throw new IllegalArgumentException("expected JSON array but got: " + typeOf(value));
  }

  // ---------------------------------------------------------------------------
  // Core recursive-descent parser
  // ---------------------------------------------------------------------------

  private Object readValue() {
    skipWhitespace();
    if (pos >= json.length()) {
      throw error("unexpected end of input");
    }
    char c = json.charAt(pos);
    return switch (c) {
      case '"' -> readString();
      case '{' -> readObject();
      case '[' -> readArray();
      case 't', 'f' -> readBoolean();
      case 'n' -> readNull();
      default -> {
        if (c == '-' || (c >= '0' && c <= '9')) {
          yield readNumber();
        }
        throw error("unexpected character '" + c + "'");
      }
    };
  }

  // --- Arrays ----------------------------------------------------------------

  private List<Object> readArray() {
    expect('[');
    List<Object> list = new ArrayList<>();
    skipWhitespace();
    if (pos < json.length() && json.charAt(pos) == ']') {
      pos++;
      return list;
    }
    while (true) {
      list.add(readValue());
      skipWhitespace();
      if (pos >= json.length()) {
        throw error("unterminated array");
      }
      if (json.charAt(pos) == ']') {
        pos++;
        return list;
      }
      expect(',');
    }
  }

  // --- Objects ---------------------------------------------------------------

  private Map<String, Object> readObject() {
    expect('{');
    Map<String, Object> map = new LinkedHashMap<>();
    skipWhitespace();
    if (pos < json.length() && json.charAt(pos) == '}') {
      pos++;
      return map;
    }
    while (true) {
      skipWhitespace();
      if (pos >= json.length() || json.charAt(pos) != '"') {
        throw error("expected string key");
      }
      String key = readString();
      skipWhitespace();
      expect(':');
      Object value = readValue();
      map.put(key, value);
      skipWhitespace();
      if (pos >= json.length()) {
        throw error("unterminated object");
      }
      if (json.charAt(pos) == '}') {
        pos++;
        return map;
      }
      expect(',');
    }
  }

  // --- Strings ---------------------------------------------------------------

  private String readString() {
    expect('"');
    StringBuilder sb = new StringBuilder();
    while (pos < json.length()) {
      char c = json.charAt(pos++);
      if (c == '"') {
        return sb.toString();
      }
      if (c == '\\') {
        if (pos >= json.length()) {
          throw error("unterminated string escape");
        }
        char esc = json.charAt(pos++);
        switch (esc) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 > json.length()) {
              throw error("incomplete unicode escape");
            }
            String hex = json.substring(pos, pos + 4);
            try {
              sb.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
              throw error("invalid unicode escape: \\u" + hex);
            }
            pos += 4;
          }
          default -> throw error("invalid escape character: \\" + esc);
        }
      } else {
        sb.append(c);
      }
    }
    throw error("unterminated string");
  }

  // --- Numbers ---------------------------------------------------------------

  private Number readNumber() {
    int start = pos;
    boolean isDouble = false;

    // optional leading minus
    if (pos < json.length() && json.charAt(pos) == '-') {
      pos++;
    }
    // integer part
    if (pos < json.length() && json.charAt(pos) == '0') {
      pos++;
      // Leading zeros are not permitted in JSON numbers (RFC 8259 section 6)
      if (pos < json.length() && json.charAt(pos) >= '0' && json.charAt(pos) <= '9') {
        throw error("leading zeros not permitted in JSON numbers");
      }
    } else {
      readDigits();
    }
    // fractional part
    if (pos < json.length() && json.charAt(pos) == '.') {
      isDouble = true;
      pos++;
      readDigits();
    }
    // exponent
    if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
      isDouble = true;
      pos++;
      if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
        pos++;
      }
      readDigits();
    }

    String raw = json.substring(start, pos);
    try {
      if (isDouble) {
        return Double.parseDouble(raw);
      }
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      throw error("invalid number: " + raw);
    }
  }

  private void readDigits() {
    int start = pos;
    while (pos < json.length() && json.charAt(pos) >= '0' && json.charAt(pos) <= '9') {
      pos++;
    }
    if (pos == start) {
      throw error("expected digit");
    }
  }

  // --- Literals --------------------------------------------------------------

  private Boolean readBoolean() {
    if (json.startsWith("true", pos)) {
      pos += 4;
      return Boolean.TRUE;
    }
    if (json.startsWith("false", pos)) {
      pos += 5;
      return Boolean.FALSE;
    }
    throw error("expected 'true' or 'false'");
  }

  private Object readNull() {
    if (json.startsWith("null", pos)) {
      pos += 4;
      return null;
    }
    throw error("expected 'null'");
  }

  // --- Helpers ---------------------------------------------------------------

  private void skipWhitespace() {
    while (pos < json.length()) {
      char c = json.charAt(pos);
      if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
        break;
      }
      pos++;
    }
  }

  private void expect(char expected) {
    if (pos >= json.length() || json.charAt(pos) != expected) {
      throw error("expected '" + expected + "'");
    }
    pos++;
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException("JSON parse error at position " + pos + ": " + message);
  }

  private static String typeOf(Object value) {
    if (value == null) return "null";
    if (value instanceof List) return "array";
    if (value instanceof Map) return "object";
    return value.getClass().getSimpleName();
  }
}
