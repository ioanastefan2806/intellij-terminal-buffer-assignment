package com.jetbrains.terminal.model;
public record TerminalCell(String text, TextAttributes attributes, boolean wideContinuation) {
    public static final TerminalCell EMPTY = new TerminalCell(" ", TextAttributes.DEFAULT, false);

    public static TerminalCell empty() {
        return EMPTY;
    }

    public static TerminalCell ofChar(char c, TextAttributes attributes) {
        return new TerminalCell(String.valueOf(c), attributes, false);
    }

    public static TerminalCell ofGrapheme(String grapheme, TextAttributes attributes) {
        return new TerminalCell(grapheme, attributes, false);
    }

    public static TerminalCell wideContinuation(TextAttributes attributes) {
        return new TerminalCell("", attributes, true);
    }

    public char character() {
        if (wideContinuation || text.isEmpty()) {
            return ' ';
        }
        return text.charAt(0);
    }

    public boolean isEmpty() {
        return !wideContinuation && " ".equals(text) && attributes.equals(TextAttributes.DEFAULT);
    }
}