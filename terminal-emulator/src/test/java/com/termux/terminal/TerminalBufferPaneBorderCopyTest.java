package com.termux.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Copying text out of a full-screen TUI that draws pane borders (herdr draws its panes with a
 * right-edge one-eighth block, U+2595) must not carry the border glyph or its padding into the
 * clipboard.
 */
public class TerminalBufferPaneBorderCopyTest {

    private static final int COLUMNS = 60;

    private static TerminalBuffer bufferWithRows(String... rows) {
        TerminalBuffer buffer = new TerminalBuffer(COLUMNS, rows.length, rows.length);
        for (int row = 0; row < rows.length; row++) {
            String text = rows[row];
            for (int column = 0; column < text.length(); column++) {
                buffer.setChar(column, row, text.charAt(column), TextStyle.NORMAL);
            }
        }
        return buffer;
    }

    private static String copyAll(TerminalBuffer buffer, int lastRow) {
        return buffer.getSelectedText(0, 0, COLUMNS - 1, lastRow, true, true);
    }

    @Test
    public void borderBetweenTextOnOneRowBecomesASingleSpace() {
        TerminalBuffer buffer = bufferWithRows("git checkout -b x &&    \u2595    git add y");

        assertEquals("git checkout -b x && git add y", copyAll(buffer, 0));
    }

    @Test
    public void rowsEndingInABorderJoinIntoOneLine() {
        TerminalBuffer buffer = bufferWithRows(
                "git checkout -b x &&    \u2595",
                "    git add y && git commit    \u2595",
                "    -m done");

        assertEquals("git checkout -b x && git add y && git commit -m done", copyAll(buffer, 2));
    }

    @Test
    public void ordinaryRowsStillBreakAsBefore() {
        TerminalBuffer buffer = bufferWithRows("first line", "second line");

        assertEquals("first line\nsecond line", copyAll(buffer, 1));
    }
}
