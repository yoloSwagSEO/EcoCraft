package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-line text input widget.
 * Extends BaseWidget directly — stores text as a single String with newlines,
 * splits into lines for rendering. Supports vertical scrolling, multi-line
 * selection, and clipboard operations.
 */
public class EcoTextArea extends BaseWidget {

    private static final int H_PADDING = 4;
    private static final int V_PADDING = 3;
    private static final int SCROLLBAR_WIDTH = 4;

    private final Font font;
    private final Theme theme;
    private String value = "";
    private int cursorPos = 0;
    private int selectionStart = -1;
    private int scrollOffset = 0; // vertical scroll in pixels
    private int maxLength = 1000;
    private Consumer<String> responder;
    private long lastBlinkTime = 0;
    private boolean cursorVisible = true;
    private boolean focused = false;

    // For double-click detection
    private long lastClickTime = 0;
    private int lastClickPos = -1;

    public EcoTextArea(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.font = font;
        this.theme = theme;
    }

    // --- API ---

    public String getValue() {
        return value;
    }

    public void setValue(String text) {
        if (text == null) text = "";
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        this.value = text;
        this.cursorPos = Math.min(cursorPos, value.length());
        this.selectionStart = -1;
        this.scrollOffset = 0;
        ensureCursorVisible();
        if (responder != null) {
            responder.accept(value);
        }
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
            cursorPos = Math.min(cursorPos, value.length());
            if (selectionStart > value.length()) selectionStart = -1;
        }
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onFocusGained() {
        this.focused = true;
        this.lastBlinkTime = System.currentTimeMillis();
        this.cursorVisible = true;
    }

    @Override
    public void onFocusLost() {
        this.focused = false;
        this.selectionStart = -1;
    }

    // --- Line utilities ---

    /**
     * Split value into lines by newline characters.
     * An empty string produces one empty line. A trailing newline produces an extra empty line.
     */
    private List<String> getLines() {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                lines.add(value.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(value.substring(start));
        return lines;
    }

    /** Returns {line, column} for a character index in the full text. */
    private int[] getLineCol(int charIndex) {
        charIndex = Math.max(0, Math.min(charIndex, value.length()));
        int line = 0;
        int col = 0;
        for (int i = 0; i < charIndex; i++) {
            if (value.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }

    /** Returns the character index for a given line and column. */
    private int getCharIndex(int line, int col) {
        List<String> lines = getLines();
        line = Math.max(0, Math.min(line, lines.size() - 1));
        col = Math.max(0, Math.min(col, lines.get(line).length()));
        int index = 0;
        for (int i = 0; i < line; i++) {
            index += lines.get(i).length() + 1; // +1 for newline
        }
        return index + col;
    }

    /** Returns the character index for the start of a given line. */
    private int getLineStart(int line) {
        List<String> lines = getLines();
        line = Math.max(0, Math.min(line, lines.size() - 1));
        int index = 0;
        for (int i = 0; i < line; i++) {
            index += lines.get(i).length() + 1;
        }
        return index;
    }

    /** Available text width inside the widget (minus padding and scrollbar). */
    private int getTextAreaWidth() {
        return getWidth() - H_PADDING * 2 - SCROLLBAR_WIDTH;
    }

    /** Visible text area height. */
    private int getTextAreaHeight() {
        return getHeight() - V_PADDING * 2;
    }

    /** Total content height in pixels. */
    private int getTotalContentHeight() {
        return getLines().size() * font.lineHeight;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // 1. Panel background with focused/unfocused border
        int borderColor = focused ? theme.borderAccent : theme.border;
        DrawUtils.drawPanel(graphics, x, y, w, h, theme.bgDark, borderColor);

        // 2. Scissor clipping for text area
        int clipX = x + H_PADDING;
        int clipY = y + V_PADDING;
        int clipW = getTextAreaWidth();
        int clipH = getTextAreaHeight();
        graphics.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);

        List<String> lines = getLines();
        int lineHeight = font.lineHeight;

        // 3. Draw selection highlight
        if (selectionStart >= 0 && selectionStart != cursorPos) {
            int selMin = Math.min(selectionStart, cursorPos);
            int selMax = Math.max(selectionStart, cursorPos);
            int[] selMinLC = getLineCol(selMin);
            int[] selMaxLC = getLineCol(selMax);

            for (int lineIdx = selMinLC[0]; lineIdx <= selMaxLC[1 - 1 + 1] && lineIdx < lines.size(); lineIdx++) {
                // This was overly complex, let me simplify
                break;
            }
            // Simplified multi-line selection rendering
            for (int lineIdx = selMinLC[0]; lineIdx <= selMaxLC[0] && lineIdx < lines.size(); lineIdx++) {
                String lineText = lines.get(lineIdx);
                int lineY = clipY + lineIdx * lineHeight - scrollOffset;

                int selStartCol;
                if (lineIdx == selMinLC[0]) {
                    selStartCol = selMinLC[1];
                } else {
                    selStartCol = 0;
                }

                int selEndCol;
                if (lineIdx == selMaxLC[0]) {
                    selEndCol = selMaxLC[1];
                } else {
                    selEndCol = lineText.length();
                }

                int selX1 = clipX + font.width(lineText.substring(0, Math.min(selStartCol, lineText.length())));
                int selX2 = clipX + font.width(lineText.substring(0, Math.min(selEndCol, lineText.length())));

                // If selection spans past end of line, extend slightly
                if (lineIdx != selMaxLC[0] && selEndCol >= lineText.length()) {
                    selX2 += font.width(" ");
                }

                graphics.fill(selX1, lineY - 1, selX2, lineY + lineHeight + 1, theme.accentBg);
            }
        }

        // 4. Draw text lines
        for (int i = 0; i < lines.size(); i++) {
            int lineY = clipY + i * lineHeight - scrollOffset;
            if (lineY + lineHeight < clipY) continue;
            if (lineY > clipY + clipH) break;
            String lineText = lines.get(i);
            if (!lineText.isEmpty()) {
                graphics.drawString(font, lineText, clipX, lineY, theme.textWhite, false);
            }
        }

        // 5. Draw cursor (blink every 500ms)
        if (focused) {
            long now = System.currentTimeMillis();
            if (now - lastBlinkTime >= 500) {
                cursorVisible = !cursorVisible;
                lastBlinkTime = now;
            }
            if (cursorVisible) {
                int[] lc = getLineCol(cursorPos);
                String curLineText = lines.get(lc[0]);
                int cursorX = clipX + font.width(curLineText.substring(0, Math.min(lc[1], curLineText.length())));
                int cursorY = clipY + lc[0] * lineHeight - scrollOffset;
                graphics.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + lineHeight + 1, theme.textWhite);
            }
        }

        graphics.disableScissor();

        // 6. Scrollbar
        int totalHeight = getTotalContentHeight();
        int visibleHeight = getTextAreaHeight();
        if (totalHeight > visibleHeight) {
            int scrollbarX = x + w - SCROLLBAR_WIDTH - 1;
            int scrollbarTrackY = y + V_PADDING;
            int scrollbarTrackH = visibleHeight;

            // Track background
            graphics.fill(scrollbarX, scrollbarTrackY, scrollbarX + SCROLLBAR_WIDTH,
                    scrollbarTrackY + scrollbarTrackH, theme.bgDarkest);

            // Thumb
            float thumbRatio = (float) visibleHeight / totalHeight;
            int thumbH = Math.max(8, (int) (scrollbarTrackH * thumbRatio));
            int maxScroll = totalHeight - visibleHeight;
            float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
            int thumbY = scrollbarTrackY + (int) ((scrollbarTrackH - thumbH) * scrollRatio);

            graphics.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbH, theme.textDim);
        }
    }

    // --- Mouse Events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int clickPos = getCharIndexAtMouse(mouseX, mouseY);
        long now = System.currentTimeMillis();

        // Double-click detection: select word
        if (now - lastClickTime < 400 && lastClickPos >= 0 && Math.abs(clickPos - lastClickPos) < 2) {
            selectWordAt(clickPos);
            lastClickTime = 0;
            return true;
        }

        lastClickTime = now;
        lastClickPos = clickPos;

        cursorPos = clickPos;
        selectionStart = -1;
        resetBlink();
        return true;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0) return false;
        if (selectionStart < 0) {
            selectionStart = cursorPos;
        }
        cursorPos = getCharIndexAtMouse(mouseX, mouseY);
        ensureCursorVisible();
        resetBlink();
        return true;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int totalHeight = getTotalContentHeight();
        int visibleHeight = getTextAreaHeight();
        if (totalHeight <= visibleHeight) return false;

        int maxScroll = totalHeight - visibleHeight;
        scrollOffset -= (int) (scrollY * font.lineHeight * 3);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    // --- Key Events ---

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insertText("\n");
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursor(ctrl ? findWordBoundaryLeft() : cursorPos - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursor(ctrl ? findWordBoundaryRight() : cursorPos + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveCursorVertically(-1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveCursorVertically(1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (ctrl) {
                    moveCursor(0, shift);
                } else {
                    // Move to start of current line
                    int[] lc = getLineCol(cursorPos);
                    moveCursor(getLineStart(lc[0]), shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (ctrl) {
                    moveCursor(value.length(), shift);
                } else {
                    // Move to end of current line
                    int[] lc = getLineCol(cursorPos);
                    List<String> lines = getLines();
                    moveCursor(getLineStart(lc[0]) + lines.get(lc[0]).length(), shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPos > 0) {
                    int deleteFrom = ctrl ? findWordBoundaryLeft() : cursorPos - 1;
                    value = value.substring(0, deleteFrom) + value.substring(cursorPos);
                    cursorPos = deleteFrom;
                    onValueChanged();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPos < value.length()) {
                    int deleteTo = ctrl ? findWordBoundaryRight() : cursorPos + 1;
                    value = value.substring(0, cursorPos) + value.substring(deleteTo);
                    onValueChanged();
                }
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    selectionStart = 0;
                    cursorPos = value.length();
                    ensureCursorVisible();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) {
                    copySelection();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl) {
                    copySelection();
                    deleteSelection();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    pasteFromClipboard();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onCharTyped(char codePoint, int modifiers) {
        if (Character.isISOControl(codePoint)) return false;

        String insertion = String.valueOf(codePoint);
        insertText(insertion);
        return true;
    }

    // --- Selection helpers ---

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionStart != cursorPos;
    }

    public String getSelectedText() {
        if (!hasSelection()) return "";
        int min = Math.min(selectionStart, cursorPos);
        int max = Math.max(selectionStart, cursorPos);
        return value.substring(min, max);
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int min = Math.min(selectionStart, cursorPos);
        int max = Math.max(selectionStart, cursorPos);
        value = value.substring(0, min) + value.substring(max);
        cursorPos = min;
        selectionStart = -1;
        onValueChanged();
    }

    private void selectWordAt(int index) {
        if (value.isEmpty()) return;
        index = Math.max(0, Math.min(index, value.length() - 1));

        int start = index;
        while (start > 0 && !isWordBreak(value.charAt(start - 1))) {
            start--;
        }
        int end = index;
        while (end < value.length() && !isWordBreak(value.charAt(end))) {
            end++;
        }
        selectionStart = start;
        cursorPos = end;
        ensureCursorVisible();
        resetBlink();
    }

    private boolean isWordBreak(char c) {
        return Character.isWhitespace(c) || c == '\n';
    }

    // --- Cursor movement ---

    private void moveCursor(int newPos, boolean shift) {
        newPos = Math.max(0, Math.min(newPos, value.length()));
        if (shift) {
            if (selectionStart < 0) {
                selectionStart = cursorPos;
            }
        } else {
            selectionStart = -1;
        }
        cursorPos = newPos;
        ensureCursorVisible();
        resetBlink();
    }

    private void moveCursorVertically(int lineDelta, boolean shift) {
        int[] lc = getLineCol(cursorPos);
        List<String> lines = getLines();
        int targetLine = lc[0] + lineDelta;

        if (targetLine < 0 || targetLine >= lines.size()) return;

        // Try to preserve the column, but clamp to line length
        String currentLineText = lines.get(lc[0]);
        int pixelX = font.width(currentLineText.substring(0, Math.min(lc[1], currentLineText.length())));

        // Find the closest column in the target line by pixel position
        String targetLineText = lines.get(targetLine);
        int targetCol = getColForPixelX(targetLineText, pixelX);
        int newPos = getCharIndex(targetLine, targetCol);

        moveCursor(newPos, shift);
    }

    /** Find the column in a line that's closest to the given pixel X position. */
    private int getColForPixelX(String lineText, int pixelX) {
        if (lineText.isEmpty()) return 0;

        int accumulated = 0;
        for (int i = 0; i < lineText.length(); i++) {
            int charWidth = font.width(String.valueOf(lineText.charAt(i)));
            if (accumulated + charWidth / 2 > pixelX) {
                return i;
            }
            accumulated += charWidth;
        }
        return lineText.length();
    }

    private int findWordBoundaryLeft() {
        if (cursorPos <= 0) return 0;
        int pos = cursorPos - 1;
        // Skip whitespace (but not newlines — stop at line boundary)
        while (pos > 0 && value.charAt(pos) != '\n' && Character.isWhitespace(value.charAt(pos))) pos--;
        // Skip word chars
        while (pos > 0 && value.charAt(pos - 1) != '\n' && !Character.isWhitespace(value.charAt(pos - 1))) pos--;
        return pos;
    }

    private int findWordBoundaryRight() {
        if (cursorPos >= value.length()) return value.length();
        int pos = cursorPos;
        // Skip word chars
        while (pos < value.length() && value.charAt(pos) != '\n' && !Character.isWhitespace(value.charAt(pos))) pos++;
        // Skip whitespace (but not newlines)
        while (pos < value.length() && value.charAt(pos) != '\n' && Character.isWhitespace(value.charAt(pos))) pos++;
        return pos;
    }

    // --- Text insertion ---

    private void insertText(String text) {
        if (hasSelection()) {
            int min = Math.min(selectionStart, cursorPos);
            int max = Math.max(selectionStart, cursorPos);
            String candidate = value.substring(0, min) + text + value.substring(max);
            if (candidate.length() > maxLength) {
                int available = maxLength - (value.length() - (max - min));
                if (available <= 0) return;
                text = text.substring(0, Math.min(text.length(), available));
                candidate = value.substring(0, min) + text + value.substring(max);
            }
            value = candidate;
            cursorPos = min + text.length();
            selectionStart = -1;
        } else {
            String candidate = value.substring(0, cursorPos) + text + value.substring(cursorPos);
            if (candidate.length() > maxLength) {
                int available = maxLength - value.length();
                if (available <= 0) return;
                text = text.substring(0, Math.min(text.length(), available));
                candidate = value.substring(0, cursorPos) + text + value.substring(cursorPos);
            }
            value = candidate;
            cursorPos += text.length();
        }
        onValueChanged();
    }

    // --- Clipboard ---

    private void copySelection() {
        String selected = getSelectedText();
        if (!selected.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selected);
        }
    }

    private void pasteFromClipboard() {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard != null && !clipboard.isEmpty()) {
            // Keep newlines for multi-line input, but normalize line endings
            clipboard = clipboard.replace("\r\n", "\n").replace("\r", "\n");
            insertText(clipboard);
        }
    }

    // --- Mouse position to character index ---

    private int getCharIndexAtMouse(double mouseX, double mouseY) {
        int clipX = getX() + H_PADDING;
        int clipY = getY() + V_PADDING;

        List<String> lines = getLines();
        int lineHeight = font.lineHeight;

        // Determine which line
        int relY = (int) mouseY - clipY + scrollOffset;
        int lineIdx = relY / lineHeight;
        lineIdx = Math.max(0, Math.min(lineIdx, lines.size() - 1));

        // Determine column within line
        String lineText = lines.get(lineIdx);
        int relX = (int) mouseX - clipX;

        int col;
        if (relX <= 0) {
            col = 0;
        } else {
            col = lineText.length();
            int accumulated = 0;
            for (int i = 0; i < lineText.length(); i++) {
                int charWidth = font.width(String.valueOf(lineText.charAt(i)));
                if (relX < accumulated + charWidth / 2) {
                    col = i;
                    break;
                }
                accumulated += charWidth;
            }
        }

        return getCharIndex(lineIdx, col);
    }

    // --- Scroll management ---

    private void ensureCursorVisible() {
        int[] lc = getLineCol(cursorPos);
        int lineHeight = font.lineHeight;
        int cursorY = lc[0] * lineHeight;
        int visibleHeight = getTextAreaHeight();

        if (cursorY < scrollOffset) {
            scrollOffset = cursorY;
        } else if (cursorY + lineHeight > scrollOffset + visibleHeight) {
            scrollOffset = cursorY + lineHeight - visibleHeight;
        }

        // Clamp scroll offset
        int maxScroll = Math.max(0, getTotalContentHeight() - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    // --- Helpers ---

    private void onValueChanged() {
        ensureCursorVisible();
        resetBlink();
        if (responder != null) {
            responder.accept(value);
        }
    }

    private void resetBlink() {
        lastBlinkTime = System.currentTimeMillis();
        cursorVisible = true;
    }
}
