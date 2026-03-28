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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Static helpers for encoding/decoding well-known proto types and scalars. Used by generated code
 * to keep generated classes smaller. Zero dependencies — no Jackson required.
 */
public final class FieldCodecs {

  private FieldCodecs() {}

  /**
   * Serialize a well-known type by appending its JSON representation to the given StringBuilder.
   * Returns true if a value was appended, false if null was appended.
   */
  public static boolean appendWellKnown(StringBuilder sb, Object value, String wktName) {
    if (value == null) {
      sb.append("null");
      return false;
    }
    switch (wktName) {
      case "TIMESTAMP" -> appendTimestamp(sb, value);
      case "DURATION" -> appendDuration(sb, value);
      case "EMPTY" -> sb.append("[]");
      case "FIELD_MASK" -> appendFieldMask(sb, value);
      default -> {
        if (wktName.endsWith("_VALUE")) {
          appendWrapper(sb, value);
        } else {
          sb.append("null");
        }
      }
    }
    return true;
  }

  // --- Timestamp ---

  private static void appendTimestamp(StringBuilder sb, Object value) {
    try {
      long seconds = (long) value.getClass().getMethod("getSeconds").invoke(value);
      int nanos = (int) value.getClass().getMethod("getNanos").invoke(value);
      Instant instant = Instant.ofEpochSecond(seconds, nanos);
      JsonArrayWriter.appendQuotedString(sb, DateTimeFormatter.ISO_INSTANT.format(instant));
    } catch (Exception e) {
      sb.append("null");
    }
  }

  // --- Duration ---

  private static void appendDuration(StringBuilder sb, Object value) {
    try {
      long seconds = (long) value.getClass().getMethod("getSeconds").invoke(value);
      int nanos = (int) value.getClass().getMethod("getNanos").invoke(value);
      String dur;
      if (nanos == 0) {
        dur = seconds + "s";
      } else {
        dur = seconds + "." + String.format("%09d", Math.abs(nanos)).replaceAll("0+$", "") + "s";
      }
      JsonArrayWriter.appendQuotedString(sb, dur);
    } catch (Exception e) {
      sb.append("null");
    }
  }

  // --- Wrapper types ---

  private static void appendWrapper(StringBuilder sb, Object value) {
    try {
      Object inner = value.getClass().getMethod("getValue").invoke(value);
      if (inner instanceof Boolean b) {
        sb.append(b);
      } else if (inner instanceof Number n) {
        sb.append(n);
      } else if (inner instanceof String s) {
        JsonArrayWriter.appendQuotedString(sb, s);
      } else if (inner instanceof byte[] bytes) {
        JsonArrayWriter.appendQuotedString(sb, Base64.getEncoder().encodeToString(bytes));
      } else {
        sb.append("null");
      }
    } catch (Exception e) {
      sb.append("null");
    }
  }

  // --- FieldMask ---

  private static void appendFieldMask(StringBuilder sb, Object value) {
    try {
      Object paths = value.getClass().getMethod("getPathsList").invoke(value);
      if (paths instanceof java.util.List<?> list) {
        JsonArrayWriter.appendQuotedString(
            sb, String.join(",", list.stream().map(Object::toString).toList()));
      } else {
        JsonArrayWriter.appendQuotedString(sb, "");
      }
    } catch (Exception e) {
      sb.append("null");
    }
  }
}
