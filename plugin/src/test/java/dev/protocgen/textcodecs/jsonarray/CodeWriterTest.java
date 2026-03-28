package dev.protocgen.textcodecs.jsonarray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CodeWriterTest {

  @Test
  void testSimpleLine() {
    CodeWriter w = new CodeWriter("  ");
    w.line("hello");
    assertEquals("hello\n", w.toString());
  }

  @Test
  void testIndentDedent() {
    CodeWriter w = new CodeWriter("  ");
    w.indent();
    w.line("indented");
    w.dedent();
    w.line("back");
    assertEquals("  indented\nback\n", w.toString());
  }

  @Test
  void testBlock() {
    CodeWriter w = new CodeWriter("  ");
    w.block("if (x)", () -> w.line("doSomething();"));
    assertEquals("if (x) {\n  doSomething();\n}\n", w.toString());
  }

  @Test
  void testNestedBlocks() {
    CodeWriter w = new CodeWriter("  ");
    w.block(
        "outer",
        () -> {
          w.line("a");
          w.block("inner", () -> w.line("b"));
        });
    String expected = "outer {\n  a\n  inner {\n    b\n  }\n}\n";
    assertEquals(expected, w.toString());
  }

  @Test
  void testBlankLine() {
    CodeWriter w = new CodeWriter("  ");
    w.line("before");
    w.blankLine();
    w.line("after");
    assertEquals("before\n\nafter\n", w.toString());
  }

  @Test
  void testRaw() {
    CodeWriter w = new CodeWriter("  ");
    w.indent();
    w.raw("no-indent-no-newline");
    // raw should not add indent even though indent level is 1, and no trailing newline
    assertEquals("no-indent-no-newline", w.toString());
  }

  @Test
  void testBlockContinue() {
    CodeWriter w = new CodeWriter("  ");
    w.blockContinue("if (x)", () -> w.line("a();"));
    // blockContinue should NOT emit a newline after the closing brace
    // so we can append " else {" etc.
    w.raw(" else ");
    w.block("", () -> w.line("b();"));
    String expected = "if (x) {\n  a();\n} else  {\n  b();\n}\n";
    assertEquals(expected, w.toString());
  }

  @Test
  void testBlockContinuePreservesIndent() {
    CodeWriter w = new CodeWriter("  ");
    w.indent();
    w.blockContinue("try", () -> w.line("doSomething();"));
    // After blockContinue, closing brace should be at the original indent level
    String result = w.toString();
    // The closing brace should be indented by one level (matching the header line)
    assertFalse(result.endsWith("\n"), "blockContinue should not end with newline");
  }

  @Test
  void testLineWithFormatArgs() {
    CodeWriter w = new CodeWriter("  ");
    w.line("int %s = %d;", "count", 42);
    assertEquals("int count = 42;\n", w.toString());
  }

  @Test
  void testLineWithFormatArgsAndIndent() {
    CodeWriter w = new CodeWriter("  ");
    w.indent();
    w.line("String %s = \"%s\";", "name", "hello");
    assertEquals("  String name = \"hello\";\n", w.toString());
  }

  @Test
  void testDedentBelowZeroDoesNotGoNegative() {
    CodeWriter w = new CodeWriter("  ");
    // Dedent when already at level 0 should not go negative
    w.dedent();
    w.dedent();
    w.dedent();
    w.line("still at zero");
    // If indent were negative, we'd get errors or no indent; at zero, just the text
    assertEquals("still at zero\n", w.toString());
  }

  @Test
  void testRawLine() {
    CodeWriter w = new CodeWriter("  ");
    w.indent();
    w.rawLine("// comment");
    // rawLine should not add indent but should add a newline
    assertEquals("// comment\n", w.toString());
  }

  @Test
  void testDefaultConstructorUsesFourSpaces() {
    CodeWriter w = new CodeWriter();
    w.indent();
    w.line("x");
    assertEquals("    x\n", w.toString());
  }

  @Test
  void testFluentChaining() {
    // Verify all methods return CodeWriter for chaining
    CodeWriter w = new CodeWriter("  ");
    String result =
        w.line("a").indent().line("b").dedent().blankLine().raw("c").rawLine("d").toString();
    assertEquals("a\n  b\n\ncd\n", result);
  }
}
