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
package dev.protocgen.textcodecs.jsonarray.runtime;

/**
 * Lightweight JSON string builder utilities. Zero dependencies -- replaces Jackson for
 * serialization.
 */
public final class JsonArrayWriter {

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private JsonArrayWriter() {}

  /** Appends a JSON-escaped string (with surrounding quotes) to the StringBuilder. */
  public static void appendQuotedString(StringBuilder sb, String s) {
    sb.append('"');
    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      String escape = null;
      switch (c) {
        case '"':
          escape = "\\\"";
          break;
        case '\\':
          escape = "\\\\";
          break;
        case '\n':
          escape = "\\n";
          break;
        case '\r':
          escape = "\\r";
          break;
        case '\t':
          escape = "\\t";
          break;
        case '\b':
          escape = "\\b";
          break;
        case '\f':
          escape = "\\f";
          break;
        default:
          if (c < 0x20) {
            escape = "\\u00" + HEX[c >> 4] + HEX[c & 0xF];
          }
      }
      if (escape != null) {
        sb.append(s, start, i); // bulk copy safe segment
        sb.append(escape);
        start = i + 1;
      }
    }
    sb.append(s, start, s.length()); // final safe segment
    sb.append('"');
  }
}
