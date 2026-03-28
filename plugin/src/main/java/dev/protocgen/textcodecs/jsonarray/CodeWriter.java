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

/**
 * Utility for building indented source code strings. Tracks indentation level and provides
 * convenient methods for emitting lines and blocks.
 */
public class CodeWriter {

  private final StringBuilder sb = new StringBuilder(4096); // pre-size for typical generated files
  private int indentLevel = 0;
  private final String indentUnit;
  private final String[] indentCache = new String[16]; // cache indent strings for levels 0-15

  public CodeWriter() {
    this("    "); // 4 spaces
  }

  public CodeWriter(String indentUnit) {
    this.indentUnit = indentUnit;
  }

  /** Emit a line with current indentation. */
  public CodeWriter line(String text) {
    appendIndent();
    sb.append(text);
    sb.append('\n');
    return this;
  }

  /** Emit a formatted line with current indentation. */
  public CodeWriter line(String format, Object... args) {
    appendIndent();
    sb.append(String.format(format, args));
    sb.append('\n');
    return this;
  }

  /** Emit an empty line. */
  public CodeWriter blankLine() {
    sb.append('\n');
    return this;
  }

  /**
   * Emit a block: header line, then indented body, then closing brace. Example: block("if (x)", ()
   * -> line("doSomething();")) produces: if (x) { doSomething(); }
   */
  public CodeWriter block(String header, Runnable body) {
    line(header + " {");
    indent();
    body.run();
    dedent();
    line("}");
    return this;
  }

  /**
   * Like block(), but with no trailing newline after the closing brace, allowing else/catch/etc to
   * follow.
   */
  public CodeWriter blockContinue(String header, Runnable body) {
    line(header + " {");
    indent();
    body.run();
    dedent();
    appendIndent();
    sb.append("}");
    return this;
  }

  /** Emit raw text without indentation or newline. */
  public CodeWriter raw(String text) {
    sb.append(text);
    return this;
  }

  /** Emit raw text followed by a newline, without indentation. */
  public CodeWriter rawLine(String text) {
    sb.append(text);
    sb.append('\n');
    return this;
  }

  public CodeWriter indent() {
    indentLevel++;
    return this;
  }

  public CodeWriter dedent() {
    if (indentLevel > 0) {
      indentLevel--;
    }
    return this;
  }

  private void appendIndent() {
    if (indentLevel == 0) return;
    if (indentLevel < indentCache.length) {
      String cached = indentCache[indentLevel];
      if (cached == null) {
        cached = indentUnit.repeat(indentLevel);
        indentCache[indentLevel] = cached;
      }
      sb.append(cached);
    } else {
      sb.append(indentUnit.repeat(indentLevel));
    }
  }

  @Override
  public String toString() {
    return sb.toString();
  }
}
