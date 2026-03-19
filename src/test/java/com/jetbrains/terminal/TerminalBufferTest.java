package com.jetbrains.terminal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.jetbrains.terminal.model.TextAttributes;

class TerminalBufferTest {

    private TerminalBuffer buffer;
    private final int WIDTH = 10;
    private final int HEIGHT = 5;
    private final int SCROLLBACK = 2;

    @BeforeEach
    void setUp() {
        buffer = new TerminalBuffer(WIDTH, HEIGHT, SCROLLBACK);
    }

    @Test
    @DisplayName("Should wrap text to next line when width is exceeded")
    void testTextWrapping() {
        buffer.writeText("ABCDEFGHIJKLMNO");

        assertEquals("ABCDEFGHIJ", buffer.getLineAsString(0));
        assertEquals("KLMNO     ", buffer.getLineAsString(1));
    }

    @Test
    @DisplayName("Should move lines to scrollback when screen is full")
    void testScrolling() {
        TerminalBuffer smallBuffer = new TerminalBuffer(10, 2, 1);

        smallBuffer.writeText("LINE_ONE--");
        smallBuffer.writeText("LINE_TWO--");
        smallBuffer.writeText("LINE_THREE");

        assertEquals(1, smallBuffer.getScrollbackLineCount());
        assertEquals("LINE_ONE--", smallBuffer.getLineAsString(0));
        assertEquals("LINE_TWO--", smallBuffer.getLineAsString(1));
        assertEquals("LINE_THREE", smallBuffer.getLineAsString(2));
    }

    @Test
    @DisplayName("Cursor should not move out of bounds")
    void testCursorClamping() {
        buffer.setCursorPosition(900, 900);
        // It should be clamped to (WIDTH-1, HEIGHT-1)
        assertEquals(WIDTH - 1, buffer.getCursorX());
        assertEquals(HEIGHT - 1, buffer.getCursorY());
    }

    @Test
    @DisplayName("Edge Case: Writing exactly to the last column should not wrap until the next character")
    void testExactWidthBoundary() {
        buffer.writeText("1234567890");
        assertEquals(10, buffer.getCursorX(), "Cursor should be at position 10 (end of line)");

        buffer.writeText("A");
        assertEquals(1, buffer.getCursorX(), "Cursor should be at position 1 after wrap");
        assertEquals(1, buffer.getCursorY(), "Cursor should have moved to line 1");
    }

    @Test
    @DisplayName("Scrollback: Ensure 'First-In-First-Out' (FIFO) behavior when scrollback is full")
    void testScrollbackFifo() {
        // We have 5 lines of screen + 2 lines of scrollback = 7 total capacity.
        // Writing 8 lines should drop the very first line.
        for (int i = 0; i < 8; i++) {
            buffer.writeText("LINE_" + i + "____"); // 10 chars
        }

        String allContent = buffer.getEntireContentAsString();
        assertFalse(allContent.contains("LINE_0"), "The oldest line (LINE_0) should be purged");
        assertTrue(allContent.contains("LINE_1"), "LINE_1 should still be in scrollback");
        assertTrue(allContent.contains("LINE_7"), "LINE_7 should be on the screen");
    }

    @Test
    @DisplayName("Manual Cursor: Moving cursor manually should allow overriding specific text")
    void testManualOverwrite() {
        buffer.writeText("ORIGINAL");
        buffer.setCursorPosition(0, 0);
        buffer.writeText("NEW");

        String content = buffer.getScreenContentAsString();
        // Extract the first line and trim background spaces
        String firstLine = content.split("\n")[0].trim();

        // As seen in the debugger: "NEW" replaces "ORI", leaving "GINAL"
        assertEquals("NEWGINAL", firstLine, "The first 3 chars should be overwritten");
    }

    @Test
    @DisplayName("Clear Operations: Ensure screen is wiped correctly")
    void testClearAll() {
        buffer.writeText("Some Data");
        buffer.clearEntireScreen();

        assertEquals(0, buffer.getCursorX());
        assertEquals(0, buffer.getCursorY());
        assertTrue(buffer.getScreenContentAsString().replace("\n", "").trim().isEmpty(), "Screen should be empty");
    }

    @Test
    @DisplayName("Attributes set via setCurrentAttributes must be applied to written cells")
    void testCurrentAttributesAppliedToWrite() {
        TextAttributes attrs = new TextAttributes(1, 2, TextAttributes.BOLD | TextAttributes.UNDERLINE);
        buffer.setCurrentAttributes(attrs);
        buffer.setCursorPosition(0, 0);

        buffer.writeText("A");

        assertEquals('A', buffer.getCharacterAt(0, 0));
        assertEquals(attrs, buffer.getAttributesAt(0, 0));

        // Neighboring cells should still be empty with DEFAULT attributes.
        assertEquals(' ', buffer.getCharacterAt(1, 0));
        assertEquals(TextAttributes.DEFAULT, buffer.getAttributesAt(1, 0));
    }

    @Test
    @DisplayName("Write must override existing content at the cursor")
    void testWriteOverridesExistingContent() {
        TerminalBuffer b = new TerminalBuffer(8, 2, 0);
        b.setCursorPosition(0, 0);
        b.writeText("ABCDEFGH"); // fills the first line

        b.setCursorPosition(2, 0);
        b.writeText("Z");

        assertEquals("ABZDEFGH", b.getLineAsString(0));
    }

    @Test
    @DisplayName("Insert must shift existing content to the right and overflow into subsequent lines")
    void testInsertTextShiftsAndWraps() {
        TerminalBuffer b = new TerminalBuffer(5, 2, 2);
        b.setCursorPosition(0, 0);
        b.writeText("ABCDE");

        b.setCursorPosition(1, 0);
        b.insertText("Z");

        // Insert at index 1: A Z B C D, with the displaced 'E' overflowing into the next line.
        assertEquals("AZBCD", b.getLineAsString(0));
        assertEquals("E    ", b.getLineAsString(1));
    }

    @Test
    @DisplayName("FillLine must fill the entire current row (or clear it when passed null)")
    void testFillLine() {
        TerminalBuffer b = new TerminalBuffer(4, 2, 0);
        TextAttributes attrs = new TextAttributes(3, 4, TextAttributes.ITALIC);
        b.setCurrentAttributes(attrs);
        b.setCursorPosition(0, 1);

        b.fillLine('x');
        assertEquals("xxxx", b.getLineAsString(1));
        assertEquals(attrs, b.getAttributesAt(0, 1));

        b.fillLine(null);
        assertEquals("    ", b.getLineAsString(1));
        assertEquals(TextAttributes.DEFAULT, b.getAttributesAt(0, 1));
    }

    @Test
    @DisplayName("ClearEntireScreen must wipe only the screen and preserve scrollback")
    void testClearEntireScreenPreservesScrollback() {
        TerminalBuffer b = new TerminalBuffer(5, 2, 2);
        b.writeText("AAAAA");
        b.writeText("BBBBB");
        b.writeText("CCCCC"); // pushes AAAAA into scrollback

        assertTrue(b.getEntireContentAsString().contains("AAAAA"));
        b.clearEntireScreen();

        assertEquals(0, b.getCursorX());
        assertEquals(0, b.getCursorY());
        assertTrue(b.getScreenContentAsString().replace("\n", "").trim().isEmpty());
        // Scrollback should remain.
        assertTrue(b.getEntireContentAsString().contains("AAAAA"));
    }

    @Test
    @DisplayName("ClearScreenAndScrollback must wipe both regions")
    void testClearScreenAndScrollback() {
        TerminalBuffer b = new TerminalBuffer(5, 2, 2);
        b.writeText("AAAAA");
        b.writeText("BBBBB");
        b.writeText("CCCCC");

        b.clearScreenAndScrollback();

        assertEquals(0, b.getScrollbackLineCount());
        assertTrue(b.getEntireContentAsString().replace("\n", "").trim().isEmpty());
    }

    @Test
    @DisplayName("insertEmptyLineAtBottom must move the top screen line into scrollback")
    void testInsertEmptyLineAtBottom() {
        TerminalBuffer b = new TerminalBuffer(3, 2, 1);
        b.writeText("ABC");
        b.writeText("DEF");

        b.insertEmptyLineAtBottom();

        // Scrollback should now contain ABC, and DEF should be on the screen.
        assertEquals("ABC", b.getLineAsString(0));
        assertEquals("DEF", b.getLineAsString(1));
        assertEquals("   ", b.getLineAsString(2));
    }

    @Test
    @DisplayName("moveCursor must clamp to the screen bounds")
    void testMoveCursorClamps() {
        TerminalBuffer b = new TerminalBuffer(4, 3, 0);
        b.setCursorPosition(0, 0);
        b.moveCursorUp(10);
        b.moveCursorLeft(10);

        assertEquals(0, b.getCursorX());
        assertEquals(0, b.getCursorY());

        b.moveCursorRight(10);
        b.moveCursorDown(10);

        assertEquals(3, b.getCursorX());
        assertEquals(2, b.getCursorY());
    }

    @Test
    @DisplayName("Insert should work for height=1 without infinite looping (overflow triggers scroll)")
    void testInsertHeightOne() {
        TerminalBuffer b = new TerminalBuffer(4, 1, 10);
        b.setCursorPosition(0, 0);
        b.writeText("ABCD");

        b.setCursorPosition(2, 0);
        b.insertText("Z");

        // For a 1-row screen, insertion can push content through scrollback.
        // Validate no hang and that inserted/pushed data is preserved across buffer regions.
        assertEquals(1, b.getScrollbackLineCount());
        assertTrue(b.getEntireContentAsString().contains("Z"));
        assertTrue(b.getEntireContentAsString().contains("D"));
    }

    @Test
    @DisplayName("Wide characters should consume two cells and wrap correctly")
    void testWideCharacterWriteAndWrap() {
        TerminalBuffer b = new TerminalBuffer(4, 2, 2);
        String cjk = "\u754C";
        String emoji = "\uD83D\uDE42";
        b.writeText("A" + cjk + "B");

        assertEquals("A" + cjk + " B", b.getLineAsString(0));
        assertEquals("    ", b.getLineAsString(1));

        // Put cursor at last column and write a wide char; it should wrap first.
        b.setCursorPosition(3, 0);
        b.writeText(emoji);

        assertEquals("A" + cjk + " B", b.getLineAsString(0));
        assertTrue(b.getLineAsString(1).startsWith(emoji + " "));
    }

    @Test
    @DisplayName("Inserting a wide character should shift by two cells")
    void testInsertWideCharacter() {
        TerminalBuffer b = new TerminalBuffer(5, 2, 5);
        String cjk = "\u754C";
        b.writeText("ABCDE");
        b.setCursorPosition(1, 0);

        b.insertText(cjk);

        assertEquals("A" + cjk + " BC", b.getLineAsString(0));
        assertEquals("DE   ", b.getLineAsString(1));
    }

    @Test
    @DisplayName("Resize with REFLOW should rewrap content to new width")
    void testResizeReflow() {
        TerminalBuffer b = new TerminalBuffer(5, 2, 10);
        b.writeText("ABCDE12345");

        b.resize(4, 3); // default REFLOW

        String[] lines = b.getScreenContentAsString().split("\n");
        assertEquals(3, lines.length);
        assertEquals("E   ", lines[0]);
        assertEquals("1234", lines[1]);
        assertEquals("5   ", lines[2]);
    }

    @Test
    @DisplayName("Resize with PRESERVE_ROWS should keep row breaks where possible")
    void testResizePreserveRows() {
        TerminalBuffer b = new TerminalBuffer(5, 2, 10);
        b.writeText("ABCDE");
        b.writeText("FGHIJ");

        b.resize(6, 2, TerminalBuffer.ResizeStrategy.PRESERVE_ROWS);

        String[] lines = b.getScreenContentAsString().split("\n");
        assertEquals(2, lines.length);
        assertEquals("ABCDE ", lines[0]);
        assertEquals("FGHIJ ", lines[1]);
    }

    @Test
    @DisplayName("Content access should read from scrollback first, then screen")
    void testContentAccessAcrossScrollbackAndScreen() {
        TerminalBuffer b = new TerminalBuffer(4, 2, 2);
        b.writeText("1111");
        b.writeText("2222");
        b.writeText("3333"); // 1111 moves to scrollback

        assertEquals(1, b.getScrollbackLineCount());
        assertEquals("1111", b.getLineAsString(0));
        assertEquals("2222", b.getLineAsString(1));
        assertEquals("3333", b.getLineAsString(2));
    }

    @Test
    @DisplayName("Color attributes must support default or 16 ANSI colors only")
    void testTextAttributesColorRangeValidation() {
        TextAttributes valid = new TextAttributes(15, 0, TextAttributes.BOLD);
        assertEquals(15, valid.foreground());
        assertEquals(0, valid.background());
        assertTrue(valid.isBold());
        assertFalse(valid.isItalic());
        assertFalse(valid.isUnderline());

        TextAttributes defaults = TextAttributes.of(TextAttributes.DEFAULT_COLOR, TextAttributes.DEFAULT_COLOR, false, true, true);
        assertEquals(TextAttributes.DEFAULT_COLOR, defaults.foreground());
        assertEquals(TextAttributes.DEFAULT_COLOR, defaults.background());
        assertFalse(defaults.isBold());
        assertTrue(defaults.isItalic());
        assertTrue(defaults.isUnderline());

        assertThrows(IllegalArgumentException.class, () -> new TextAttributes(-2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextAttributes(0, 16, 0));
    }

    @Test
    @DisplayName("setCurrentAttributes(null) should revert to default attributes")
    void testSetCurrentAttributesNullFallsBackToDefault() {
        TerminalBuffer b = new TerminalBuffer(3, 1, 0);
        b.setCurrentAttributes(new TextAttributes(1, 2, TextAttributes.BOLD));
        b.setCurrentAttributes(null);
        b.writeText("A");

        assertEquals(TextAttributes.DEFAULT, b.getAttributesAt(0, 0));
    }
}