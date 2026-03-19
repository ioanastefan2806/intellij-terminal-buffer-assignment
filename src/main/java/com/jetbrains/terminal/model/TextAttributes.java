package com.jetbrains.terminal.model;
public record TextAttributes(int foreground, int background, int styles) {
    public static final TextAttributes DEFAULT = new TextAttributes(-1, -1, 0);

    // -1 means terminal default color; otherwise allowed ANSI index is 0..15.
    public static final int DEFAULT_COLOR = -1;
    public static final int MIN_ANSI_COLOR = 0;
    public static final int MAX_ANSI_COLOR = 15;

    // Style constants
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int UNDERLINE = 4;

    public TextAttributes {
        validateColor("foreground", foreground);
        validateColor("background", background);
    }

    public static TextAttributes of(int foreground, int background, boolean bold, boolean italic, boolean underline) {
        int styles = 0;
        if (bold) {
            styles |= BOLD;
        }
        if (italic) {
            styles |= ITALIC;
        }
        if (underline) {
            styles |= UNDERLINE;
        }
        return new TextAttributes(foreground, background, styles);
    }

    public boolean isBold() {
        return (styles & BOLD) != 0;
    }

    public boolean isItalic() {
        return (styles & ITALIC) != 0;
    }

    public boolean isUnderline() {
        return (styles & UNDERLINE) != 0;
    }

    private static void validateColor(String name, int color) {
        if (color != DEFAULT_COLOR && (color < MIN_ANSI_COLOR || color > MAX_ANSI_COLOR)) {
            throw new IllegalArgumentException(name + " must be default(-1) or in range 0..15");
        }
    }
}