package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Custom single-line text input widget.
 * Extends BaseWidget directly — does NOT wrap Minecraft's EditBox.
 * Focus is managed by the WidgetTree via onFocusGained/onFocusLost.
 */
public class EcoEditBox extends BaseWidget {

    private static final int H_PADDING = 4;

    private final Font font;
    private final Theme theme;
    private String value = "";
    private String hint = "";
    private int cursorPos = 0;
    private int selectionStart = -1;
    private int scrollOffset = 0;
    private int maxLength = Integer.MAX_VALUE;
    private Predicate<String> filter = s -> true;
    private Consumer<String> responder;
    private long lastBlinkTime = 0;
    private boolean cursorVisible = true;
    private boolean focused = false;

    // For double-click detection
    private long lastClickTime = 0;
    private int lastClickX = -1;

    public EcoEditBox(Font font, int x, int y, int width, int height, Theme theme) {
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

    public void setHint(String hint) {
        this.hint = hint != null ? hint : "";
    }

    public void setHint(Component hint) {
        this.hint = hint != null ? hint.getString() : "";
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter != null ? filter : s -> true;
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

        // 2. Scissor clipping
        int clipX = x + H_PADDING;
        int clipY = y + 1;
        int clipW = w - H_PADDING * 2;
        int clipH = h - 2;
        graphics.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);

        // Center text vertically, rounding down to push text 1px lower for visual balance
        int textY = y + (h - font.lineHeight + 1) / 2;

        // 3. Hint text when empty and not focused
        if (value.isEmpty() && !focused) {
            graphics.drawString(font, hint, clipX, textY, theme.textDim, false);
            graphics.disableScissor();
            return;
        }

        // 4. Draw selection highlight
        if (selectionStart >= 0 && selectionStart != cursorPos) {
            int selMin = Math.min(selectionStart, cursorPos);
            int selMax = Math.max(selectionStart, cursorPos);
            int selX1 = clipX + getXForCharIndex(selMin);
            int selX2 = clipX + getXForCharIndex(selMax);
            graphics.fill(selX1, textY - 1, selX2, textY + font.lineHeight + 1, theme.accentBg);
        }

        // 5. Draw text
        if (!value.isEmpty()) {
            graphics.drawString(font, value, clipX - scrollOffset, textY, theme.textWhite, false);
        }

        // 6. Draw cursor (blink every 500ms)
        if (focused) {
            long now = System.currentTimeMillis();
            if (now - lastBlinkTime >= 500) {
                cursorVisible = !cursorVisible;
                lastBlinkTime = now;
            }
            if (cursorVisible) {
                int cursorX = clipX + getXForCharIndex(cursorPos);
                graphics.fill(cursorX, textY - 1, cursorX + 1, textY + font.lineHeight + 1, theme.textWhite);
            }
        }

        // 7. Disable scissor
        graphics.disableScissor();
    }

    // --- Mouse Events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        long now = System.currentTimeMillis();
        int clickCharIndex = getCharIndexAtX(mouseX);

        // Double-click detection: select word
        if (now - lastClickTime < 400 && Math.abs((int) mouseX - lastClickX) < 4) {
            selectWordAt(clickCharIndex);
            lastClickTime = 0; // reset to prevent triple-click issues
            return true;
        }

        lastClickTime = now;
        lastClickX = (int) mouseX;

        cursorPos = clickCharIndex;
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
        cursorPos = getCharIndexAtX(mouseX);
        ensureCursorVisible();
        resetBlink();
        return true;
    }

    // --- Key Events ---

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursor(ctrl ? findWordBoundaryLeft() : cursorPos - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursor(ctrl ? findWordBoundaryRight() : cursorPos + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                moveCursor(0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                moveCursor(value.length(), shift);
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
        while (start > 0 && !Character.isWhitespace(value.charAt(start - 1))) {
            start--;
        }
        int end = index;
        while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
            end++;
        }
        selectionStart = start;
        cursorPos = end;
        ensureCursorVisible();
        resetBlink();
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

    private int findWordBoundaryLeft() {
        if (cursorPos <= 0) return 0;
        int pos = cursorPos - 1;
        // Skip whitespace
        while (pos > 0 && Character.isWhitespace(value.charAt(pos))) pos--;
        // Skip word chars
        while (pos > 0 && !Character.isWhitespace(value.charAt(pos - 1))) pos--;
        return pos;
    }

    private int findWordBoundaryRight() {
        if (cursorPos >= value.length()) return value.length();
        int pos = cursorPos;
        // Skip word chars
        while (pos < value.length() && !Character.isWhitespace(value.charAt(pos))) pos++;
        // Skip whitespace
        while (pos < value.length() && Character.isWhitespace(value.charAt(pos))) pos++;
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
            if (!filter.test(candidate)) return;
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
            if (!filter.test(candidate)) return;
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
            // Remove newlines for single-line input
            clipboard = clipboard.replace("\n", "").replace("\r", "");
            insertText(clipboard);
        }
    }

    // --- Text position calculation ---

    private int getCharIndexAtX(double mouseX) {
        int relX = (int) mouseX - getX() - H_PADDING + scrollOffset;
        if (relX <= 0) return 0;

        int accumulated = 0;
        for (int i = 0; i < value.length(); i++) {
            int charWidth = font.width(String.valueOf(value.charAt(i)));
            if (relX < accumulated + charWidth / 2) {
                return i;
            }
            accumulated += charWidth;
        }
        return value.length();
    }

    /**
     * Returns the X offset (in pixels) for the character at the given index,
     * already adjusted for scrollOffset.
     */
    private int getXForCharIndex(int index) {
        if (index <= 0) return -scrollOffset;
        String sub = value.substring(0, Math.min(index, value.length()));
        return font.width(sub) - scrollOffset;
    }

    private void ensureCursorVisible() {
        int cursorX = font.width(value.substring(0, cursorPos));
        int visibleWidth = getWidth() - H_PADDING * 2;

        if (cursorX - scrollOffset < 0) {
            scrollOffset = cursorX;
        } else if (cursorX - scrollOffset > visibleWidth) {
            scrollOffset = cursorX - visibleWidth;
        }
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
