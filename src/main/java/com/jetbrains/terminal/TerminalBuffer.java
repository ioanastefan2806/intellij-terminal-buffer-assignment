package com.jetbrains.terminal;

import com.jetbrains.terminal.model.TerminalCell;
import com.jetbrains.terminal.model.TextAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class TerminalBuffer {
    private int width;
    private int height;
    private final int maxScrollbackLines;

    /**
     * Scrollback lines, ordered from oldest (index 0) to newest (last).
     * These lines are unmodifiable from the public API.
     */
    private final Deque<TerminalCell[]> scrollback = new ArrayDeque<>();

    /** Screen lines, always exactly {@code height} and ordered from top (index 0) to bottom (last). */
    private final List<TerminalCell[]> screen = new ArrayList<>();

    private int cursorX = 0;
    private int cursorY = 0;
    private TextAttributes currentAttributes = TextAttributes.DEFAULT;

    public TerminalBuffer(int width, int height, int maxScrollback) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0");
        }
        if (maxScrollback < 0) {
            throw new IllegalArgumentException("maxScrollback must be >= 0");
        }
        this.width = width;
        this.height = height;
        this.maxScrollbackLines = maxScrollback;

        resetScreen();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public enum ResizeStrategy {
        REFLOW,
        PRESERVE_ROWS
    }

    public int getScrollbackLineCount() {
        return scrollback.size();
    }

    // --- Cursor Operations ---

    public void setCursorPosition(int column, int row) {
        this.cursorX = clamp(column, 0, width - 1);
        this.cursorY = clamp(row, 0, height - 1);
    }

    public void moveCursor(int deltaX, int deltaY) {
        setCursorPosition(cursorX + deltaX, cursorY + deltaY);
    }

    public void moveCursorUp(int n) {
        moveCursor(0, -n);
    }

    public void moveCursorDown(int n) {
        moveCursor(0, n);
    }

    public void moveCursorLeft(int n) {
        moveCursor(-n, 0);
    }

    public void moveCursorRight(int n) {
        moveCursor(n, 0);
    }

    public int getCursorX() {
        return cursorX;
    }

    public int getCursorY() {
        return cursorY;
    }

    // --- Attributes ---

    public void setCurrentAttributes(TextAttributes attributes) {
        this.currentAttributes = Objects.requireNonNullElse(attributes, TextAttributes.DEFAULT);
    }

    public TextAttributes getCurrentAttributes() {
        return currentAttributes;
    }

    // --- Editing Operations ---

    /**
     * Writes text starting at the current cursor position.
     * Existing cells are overridden.
     */
    public void writeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String grapheme : splitIntoGraphemes(text)) {
            ensureCursorWithinScreenForWrite();
            int cells = cellsForGrapheme(grapheme);
            if (cells == 2 && cursorX == width - 1) {
                advanceCursorByCells(1);
                ensureCursorWithinScreenForWrite();
            }

            writeGraphemeAtCursor(grapheme, cells, currentAttributes);
            advanceCursorByCells(cells);
        }
    }

    /**
     * Inserts text starting at the current cursor position.
     * This shifts existing screen content to the right and may wrap into subsequent lines.
     */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String grapheme : splitIntoGraphemes(text)) {
            ensureCursorWithinScreenForWrite();
            int cells = cellsForGrapheme(grapheme);
            if (cells == 2 && cursorX == width - 1) {
                advanceCursorByCells(1);
                ensureCursorWithinScreenForWrite();
            }

            insertGraphemeAtCursor(grapheme, cells, currentAttributes);
            advanceCursorByCells(cells);
        }
    }

    /**
     * Fills the current cursor line with the provided character (or empties if {@code null}).
     * Attributes are applied only when {@code fillCharacter != null}.
     */
    public void fillLine(Character fillCharacter) {
        // Ensure we can safely address the screen row even if the cursor is at a logical "end" position.
        cursorX = clamp(cursorX, 0, width - 1);
        cursorY = clamp(cursorY, 0, height - 1);

        TerminalCell[] line = screen.get(cursorY);
        for (int x = 0; x < width; x++) {
            if (fillCharacter == null) {
                line[x] = TerminalCell.empty();
            } else {
                line[x] = TerminalCell.ofChar(fillCharacter, currentAttributes);
            }
        }
    }

    /**
     * Inserts an empty line at the bottom of the screen.
     * The top screen line becomes part of the scrollback.
     */
    public void insertEmptyLineAtBottom() {
        TerminalCell[] top = screen.remove(0);
        scrollback.addLast(top);
        trimScrollbackIfNeeded();
        screen.add(makeEmptyLine());
        // Cursor points into screen coordinates; keep it within bounds.
        cursorX = clamp(cursorX, 0, width - 1);
        cursorY = clamp(cursorY, 0, height - 1);
    }

    /**
     * Clears only the editable screen. Scrollback is preserved.
     */
    public void clearEntireScreen() {
        resetScreen();
        cursorX = 0;
        cursorY = 0;
    }

    /**
     * Clears both the screen and scrollback and resets the cursor to (0, 0).
     */
    public void clearScreenAndScrollback() {
        scrollback.clear();
        clearEntireScreen();
    }

    // --- Content Access ---

    /**
     * Returns the character at {@code (column,row)}.
     * Row coordinates count from the top of scrollback (oldest) followed by the screen (top to bottom).
     */
    public char getCharacterAt(int column, int row) {
        return getCellAt(column, row).character();
    }

    /**
     * Returns cell attributes at {@code (column,row)}.
     * Row coordinates count from the top of scrollback (oldest) followed by the screen (top to bottom).
     */
    public TextAttributes getAttributesAt(int column, int row) {
        return getCellAt(column, row).attributes();
    }

    /**
     * Returns the full line as a {@link String} of length {@code width}, including spaces for empty cells.
     * Row coordinates count from the top of scrollback followed by the screen.
     */
    public String getLineAsString(int row) {
        TerminalCell[] line = getLine(row);
        StringBuilder sb = new StringBuilder(width);
        for (int x = 0; x < width; x++) {
            TerminalCell cell = line[x];
            if (cell.wideContinuation()) {
                sb.append(' ');
                continue;
            }
            if (cell.isEmpty()) {
                sb.append(' ');
            } else {
                sb.append(cell.text());
            }
        }
        return sb.toString();
    }

    /**
     * Returns all visible screen content as {@code height} lines joined by {@code '\n'}.
     */
    public String getScreenContentAsString() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            if (y > 0) {
                sb.append('\n');
            }
            sb.append(getLineAsString(scrollback.size() + y));
        }
        return sb.toString();
    }

    /**
     * Returns the entire buffer content (scrollback + screen) as lines joined by {@code '\n'}.
     */
    public String getEntireContentAsString() {
        StringBuilder sb = new StringBuilder();
        int totalLines = scrollback.size() + height;
        for (int row = 0; row < totalLines; row++) {
            if (row > 0) {
                sb.append('\n');
            }
            sb.append(getLineAsString(row));
        }
        return sb.toString();
    }

    // --- Internals ---

    private void resetScreen() {
        screen.clear();
        for (int y = 0; y < height; y++) {
            screen.add(makeEmptyLine());
        }
    }

    private TerminalCell[] makeEmptyLine() {
        TerminalCell[] line = new TerminalCell[width];
        for (int x = 0; x < width; x++) {
            line[x] = TerminalCell.empty();
        }
        return line;
    }

    private void advanceCursorByCells(int n) {
        // Cursor is allowed to move to the logical "end of line"/"end of screen"
        // (e.g. x==width or y==height). Scrolling/wrapping is resolved lazily
        // by `ensureCursorWithinScreenForWrite()` on the next write/insert.
        cursorX += n;
    }

    private void ensureCursorWithinScreenForWrite() {
        // Resolve horizontal overflow into row increments.
        while (cursorX >= width) {
            cursorX -= width;
            cursorY++;
        }

        // Resolve vertical overflow into scrolling.
        while (cursorY >= height) {
            // Move the top screen line to scrollback and add an empty line at bottom.
            TerminalCell[] top = screen.remove(0);
            scrollback.addLast(top);
            trimScrollbackIfNeeded();
            screen.add(makeEmptyLine());
            cursorY--;
        }

        // If the cursor was somehow moved to negative coordinates, clamp back.
        cursorX = clamp(cursorX, 0, width - 1);
        cursorY = clamp(cursorY, 0, height - 1);
    }

    private void trimScrollbackIfNeeded() {
        while (scrollback.size() > maxScrollbackLines) {
            scrollback.removeFirst();
        }
    }

    private TerminalCell getCellAt(int column, int row) {
        if (column < 0 || column >= width) {
            throw new IndexOutOfBoundsException("column out of bounds: " + column);
        }
        if (row < 0 || row >= scrollback.size() + height) {
            throw new IndexOutOfBoundsException("row out of bounds: " + row);
        }
        if (row < scrollback.size()) {
            int sbIndex = row;
            TerminalCell[] line = getLineFromDeque(scrollback, sbIndex);
            return line[column];
        }
        int screenIndex = row - scrollback.size();
        return screen.get(screenIndex)[column];
    }

    private TerminalCell[] getLine(int row) {
        if (row < 0 || row >= scrollback.size() + height) {
            throw new IndexOutOfBoundsException("row out of bounds: " + row);
        }
        if (row < scrollback.size()) {
            return getLineFromDeque(scrollback, row);
        }
        return screen.get(row - scrollback.size());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private static TerminalCell[] getLineFromDeque(Deque<TerminalCell[]> deque, int index) {
        if (index < 0 || index >= deque.size()) {
            throw new IndexOutOfBoundsException("index out of bounds: " + index);
        }
        int i = 0;
        for (TerminalCell[] line : deque) {
            if (i++ == index) {
                return line;
            }
        }
        // Should be unreachable due to bounds check above.
        throw new IllegalStateException("index not found in deque");
    }

    public void resize(int newWidth, int newHeight) {
        resize(newWidth, newHeight, ResizeStrategy.REFLOW);
    }

    public void resize(int newWidth, int newHeight, ResizeStrategy strategy) {
        if (newWidth <= 0) {
            throw new IllegalArgumentException("newWidth must be > 0");
        }
        if (newHeight <= 0) {
            throw new IllegalArgumentException("newHeight must be > 0");
        }

        List<LogicalCell> logical = extractLogicalCells();
        this.width = newWidth;
        this.height = newHeight;

        if (strategy == ResizeStrategy.PRESERVE_ROWS) {
            rebuildPreserveRows(logical);
        } else {
            rebuildReflow(logical);
        }
    }

    private void writeGraphemeAtCursor(String grapheme, int cells, TextAttributes attrs) {
        TerminalCell[] line = screen.get(cursorY);
        normalizeBoundaryForWrite(cursorY, cursorX);
        line[cursorX] = TerminalCell.ofGrapheme(grapheme, attrs);
        if (cells == 2) {
            line[cursorX + 1] = TerminalCell.wideContinuation(attrs);
        }
    }

    private void normalizeBoundaryForWrite(int y, int x) {
        TerminalCell[] line = screen.get(y);
        if (line[x].wideContinuation()) {
            int lead = x - 1;
            if (lead >= 0) {
                line[lead] = TerminalCell.empty();
            }
            line[x] = TerminalCell.empty();
        }
        if (x > 0 && line[x - 1].text().length() > 1) {
            line[x - 1] = TerminalCell.empty();
            line[x] = TerminalCell.empty();
        }
    }

    private void insertGraphemeAtCursor(String grapheme, int cells, TextAttributes attrs) {
        for (int i = 0; i < cells; i++) {
            insertSingleCellAtCursor();
        }
        writeGraphemeAtCursor(grapheme, cells, attrs);
    }

    private void insertSingleCellAtCursor() {
        TerminalCell[] line = screen.get(cursorY);
        TerminalCell carry = line[width - 1];
        for (int x = width - 1; x > cursorX; x--) {
            line[x] = line[x - 1];
        }
        line[cursorX] = TerminalCell.empty();

        int y = cursorY + 1;
        while (!carry.isEmpty()) {
            if (y >= height) {
                insertEmptyLineAtBottom();
                cursorY = clamp(cursorY - 1, 0, height - 1);
                y = height - 1;
            }
            TerminalCell[] nextLine = screen.get(y);
            TerminalCell nextCarry = nextLine[width - 1];
            for (int x = width - 1; x > 0; x--) {
                nextLine[x] = nextLine[x - 1];
            }
            nextLine[0] = carry;
            carry = nextCarry;
            y++;
        }
    }

    private List<String> splitIntoGraphemes(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            out.add(new String(Character.toChars(cp)));
            i += Character.charCount(cp);
        }
        return out;
    }

    private int cellsForGrapheme(String grapheme) {
        int cp = grapheme.codePointAt(0);
        return isWideCodePoint(cp) ? 2 : 1;
    }

    private static boolean isWideCodePoint(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)
                || (cp >= 0x2E80 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE10 && cp <= 0xFE19)
                || (cp >= 0xFE30 && cp <= 0xFE6F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x1F300 && cp <= 0x1FAFF)
                || (cp >= 0x20000 && cp <= 0x3FFFD);
    }

    private record LogicalCell(String text, TextAttributes attributes, int width) {
    }

    private List<LogicalCell> extractLogicalCells() {
        List<LogicalCell> out = new ArrayList<>();
        int totalRows = scrollback.size() + height;
        for (int row = 0; row < totalRows; row++) {
            TerminalCell[] line = getLine(row);
            for (int x = 0; x < line.length; x++) {
                TerminalCell cell = line[x];
                if (cell.wideContinuation()) {
                    continue;
                }
                if (cell.isEmpty()) {
                    out.add(new LogicalCell(" ", TextAttributes.DEFAULT, 1));
                } else {
                    int w = cellsForGrapheme(cell.text());
                    out.add(new LogicalCell(cell.text(), cell.attributes(), w));
                    if (w == 2 && x + 1 < line.length && line[x + 1].wideContinuation()) {
                        x++;
                    }
                }
            }
            if (row < totalRows - 1) {
                out.add(new LogicalCell("\n", TextAttributes.DEFAULT, 0));
            }
        }
        return out;
    }

    private void rebuildReflow(List<LogicalCell> cells) {
        scrollback.clear();
        resetScreen();
        cursorX = 0;
        cursorY = 0;

        for (LogicalCell cell : cells) {
            if ("\n".equals(cell.text())) {
                advanceCursorByCells(width - cursorX);
                ensureCursorWithinScreenForWrite();
                continue;
            }
            ensureCursorWithinScreenForWrite();
            int w = Math.min(cell.width(), width);
            if (w == 2 && cursorX == width - 1) {
                advanceCursorByCells(1);
                ensureCursorWithinScreenForWrite();
            }
            writeGraphemeAtCursor(cell.text(), w, cell.attributes());
            advanceCursorByCells(w);
        }
        cursorX = clamp(cursorX, 0, width - 1);
        cursorY = clamp(cursorY, 0, height - 1);
    }

    private void rebuildPreserveRows(List<LogicalCell> cells) {
        // Preserve-rows keeps hard line breaks from old rows and crops/pads by the new geometry.
        scrollback.clear();
        resetScreen();
        cursorX = 0;
        cursorY = 0;

        for (LogicalCell cell : cells) {
            if ("\n".equals(cell.text())) {
                cursorX = 0;
                cursorY++;
                ensureCursorWithinScreenForWrite();
                continue;
            }
            ensureCursorWithinScreenForWrite();
            int w = Math.min(cell.width(), width);
            if (cursorX + w > width) {
                cursorX = 0;
                cursorY++;
                ensureCursorWithinScreenForWrite();
            }
            writeGraphemeAtCursor(cell.text(), w, cell.attributes());
            advanceCursorByCells(w);
        }
        cursorX = clamp(cursorX, 0, width - 1);
        cursorY = clamp(cursorY, 0, height - 1);
    }
}