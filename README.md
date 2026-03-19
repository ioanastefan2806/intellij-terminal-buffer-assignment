# Terminal Text Buffer 🖥️

This project implements a terminal text buffer in Java, including screen and scrollback management, cursor movement, text editing operations, text attributes, wide-character support, and resize behavior.

## What I implemented 🚀

I built a `TerminalBuffer` data structure that models how a terminal emulator stores text.

- **Screen + Scrollback model 📜**
  - The screen is a fixed-size grid (`width x height`).
  - Scrollback stores lines that leave the top of the screen.
  - Scrollback has a configurable maximum size and is trimmed FIFO.

- **Cell model 🔡**
  - Each cell is represented by `TerminalCell`.
  - A cell stores:
    - Character/grapheme text (or empty space)
    - `TextAttributes` (foreground, background, styles)
    - Wide-character continuation marker for 2-cell glyphs

- **Text attributes 🎨**
  - `TextAttributes` supports:
    - Foreground color: default (`-1`) or ANSI `0..15`
    - Background color: default (`-1`) or ANSI `0..15`
    - Styles bit flags: bold, italic, underline
  - Input validation prevents invalid color values.

- **Cursor operations 🎯**
  - Get/set cursor position.
  - Move up/down/left/right by N.
  - Cursor is clamped to valid screen bounds.
  - Writing supports logical end-of-line handling and wraps as needed.

- **Editing operations ✍️**
  - `writeText(...)`: overwrite mode from cursor position.
  - `insertText(...)`: insert mode that shifts content right and can overflow to following lines.
  - `fillLine(...)`: fill current row with character or clear row.
  - `insertEmptyLineAtBottom()`: moves top row to scrollback and appends empty row.
  - `clearEntireScreen()`: clears screen, preserves scrollback.
  - `clearScreenAndScrollback()`: clears both regions.

- **Content access 🔍**
  - `getCharacterAt(...)`
  - `getAttributesAt(...)`
  - `getLineAsString(...)`
  - `getScreenContentAsString(...)`
  - `getEntireContentAsString(...)`

- **Bonus features ✨**
  - **Wide characters** (e.g. CJK, emoji): consume 2 cells with continuation markers.
  - **Resize support**:
    - `REFLOW` strategy: re-wraps content to new width.
    - `PRESERVE_ROWS` strategy: keeps row boundaries where possible.

## Key design decisions and trade-offs ⚖️

- **Logical separation of visible and historical content 🧱**
  - Screen and scrollback are separate collections, but exposed through a unified row index for read APIs.

- **Wide character handling 🌏**
  - Wide characters are treated as 2-cell graphemes.
  - Continuation cells are explicitly marked to avoid rendering corruption during writes/inserts.

- **Attribute model simplicity 🧠**
  - Styles are represented as a bitmask for compact storage and fast checks.

- **Resize behavior is explicit 📏**
  - Different terminal emulators use different policies on resize, so two strategies were implemented.

## Testing ✅

The test suite (`TerminalBufferTest`) covers:

- setup validation and boundary conditions
- cursor clamping and movement
- overwrite and insert semantics
- line filling and clear operations
- screen/scrollback interactions
- attribute propagation and validation
- wide-character behavior
- resize behavior (`REFLOW` and `PRESERVE_ROWS`)
- edge cases such as single-row buffers

## How to run ▶️

### Run all project tests 🧪

```powershell
.\gradlew.bat test
```

### Run only `TerminalBufferTest` 🎯

```powershell
.\gradlew.bat test --tests "com.jetbrains.terminal.TerminalBufferTest"
```

### IntelliJ 💡

- Open `TerminalBufferTest.java`
- Click the green run icon next to the class name
- Select **Run 'TerminalBufferTest'**

## Environment note (Windows) 🪟

If Gradle cannot find Java, set `JAVA_HOME` to your JDK path, for example:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Then rerun tests.

## Possible future improvements 🔮

- More complete Unicode width support based on official East Asian Width data.
- Better grapheme cluster handling (combined emoji/diacritics).
- Additional terminal operations (delete char, erase in line, region scroll).
- Performance improvements for very large scrollback sizes.