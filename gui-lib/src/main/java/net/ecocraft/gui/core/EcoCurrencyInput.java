package net.ecocraft.gui.core;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.SubUnit;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

import org.jetbrains.annotations.Nullable;

/**
 * Composite currency input widget.
 * <p>
 * Two modes:
 * <ul>
 *   <li><b>Simple</b> (no sub-units): delegates to a single {@link EcoNumberInput} + symbol label</li>
 *   <li><b>Composite</b> (has sub-units): one column per sub-unit with up/down arrows</li>
 * </ul>
 * <p>
 * Stores a single {@code long value} in base units. For composite mode, display is
 * recalculated via {@link CurrencyFormatter#split(long, Currency)} every render.
 */
public class EcoCurrencyInput extends BaseWidget {

    private static final int ARROW_H = 10;
    private static final int FIELD_H = 16;
    private static final int COMPOSITE_HEIGHT = ARROW_H + FIELD_H + ARROW_H;
    private static final int SIMPLE_HEIGHT = FIELD_H;

    private static final int COL_GAP = 2;
    private static final int ARROW_CHAR_COLOR = 0xFFCCCCCC;

    private final Font font;
    private final Theme theme;
    private final Currency currency;
    private final boolean composite;

    private long value;
    private long min;
    private long max;
    private @Nullable LongConsumer responder;

    // Simple mode
    private @Nullable EcoNumberInput simpleInput;

    // Composite mode
    private final List<SubUnitField> subUnitFields = new ArrayList<>();

    public EcoCurrencyInput(Font font, int x, int y, int width, Currency currency, Theme theme) {
        super(x, y, width, currency.isComposite() ? COMPOSITE_HEIGHT : SIMPLE_HEIGHT);
        this.font = font;
        this.theme = theme;
        this.currency = currency;
        this.composite = currency.isComposite();
        this.value = 0;
        this.min = 0;
        this.max = Long.MAX_VALUE;

        if (composite) {
            buildCompositeLayout(x, y, width);
        } else if (font != null) {
            buildSimpleLayout(x, y, width);
        }
    }

    // --- Fluent API ---

    public EcoCurrencyInput min(long min) {
        this.min = min;
        if (!composite && simpleInput != null) {
            long divisor = pow10(currency.decimals());
            simpleInput.min(min / divisor);
        }
        return this;
    }

    public EcoCurrencyInput max(long max) {
        this.max = max;
        if (!composite && simpleInput != null) {
            long divisor = pow10(currency.decimals());
            simpleInput.max(max / divisor);
        }
        return this;
    }

    public EcoCurrencyInput responder(LongConsumer responder) {
        this.responder = responder;
        return this;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = clamp(value);
        if (!composite && simpleInput != null) {
            // Display in whole units
            long divisor = pow10(currency.decimals());
            simpleInput.setValue(this.value / divisor);
        }
        notifyResponder();
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    // --- Layout builders ---

    private void buildSimpleLayout(int x, int y, int width) {
        int symbolWidth = font.width(currency.symbol()) + 6;
        int inputWidth = width - symbolWidth;
        long divisor = pow10(currency.decimals());

        simpleInput = new EcoNumberInput(font, x, y, inputWidth, SIMPLE_HEIGHT, theme);
        // Display values in whole units, step by 1 whole unit
        simpleInput.min(min / divisor).max(max / divisor).step(1);
        simpleInput.responder(displayVal -> {
            // Convert display value back to smallest unit
            this.value = clamp(displayVal * divisor);
            notifyResponder();
        });
        addChild(simpleInput);
    }

    private static long pow10(int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) result *= 10;
        return result;
    }

    private void buildCompositeLayout(int x, int y, int width) {
        List<SubUnit> units = currency.subUnits();
        int count = units.size();
        int totalGap = COL_GAP * (count - 1);
        int colWidth = (width - totalGap) / count;

        int cx = x;
        for (int i = 0; i < count; i++) {
            SubUnit unit = units.get(i);
            int w = (i == count - 1) ? (width - (cx - x)) : colWidth; // last col takes remaining space
            subUnitFields.add(new SubUnitField(cx, y, w, unit, i));
            cx += w + COL_GAP;
        }
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        if (composite) {
            renderComposite(graphics, mouseX, mouseY);
        } else {
            renderSimple(graphics, mouseX, mouseY);
        }
    }

    private void renderSimple(GuiGraphics graphics, int mouseX, int mouseY) {
        // EcoNumberInput renders via child tree; just draw the symbol label
        if (simpleInput != null) {
            int labelX = simpleInput.getX() + simpleInput.getWidth() + 4;
            int labelY = getY() + (SIMPLE_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, currency.symbol(), labelX, labelY, theme.textLight, false);
        }
    }

    private void renderComposite(GuiGraphics graphics, int mouseX, int mouseY) {
        long[] parts = CurrencyFormatter.split(value, currency);

        for (SubUnitField field : subUnitFields) {
            long partValue = field.index < parts.length ? parts[field.index] : 0;
            renderSubUnitColumn(graphics, field, partValue, mouseX, mouseY);
        }
    }

    private void renderSubUnitColumn(GuiGraphics graphics, SubUnitField field, long partValue,
                                     int mouseX, int mouseY) {
        int cx = field.x;
        int cy = field.y;
        int cw = field.width;

        // Up arrow area
        int arrowUpY = cy;
        boolean hoverUp = isEnabled() && mouseX >= cx && mouseX < cx + cw
                && mouseY >= arrowUpY && mouseY < arrowUpY + ARROW_H;
        int upBg = hoverUp ? theme.bgLight : theme.bgMedium;
        DrawUtils.drawPanel(graphics, cx, arrowUpY, cw, ARROW_H, upBg, theme.border);
        String upArrow = "\u25B2";
        int upTextX = cx + (cw - font.width(upArrow)) / 2;
        int upTextY = arrowUpY + (ARROW_H - font.lineHeight) / 2;
        graphics.drawString(font, upArrow, upTextX, upTextY, isEnabled() ? theme.textLight : theme.disabledText, false);

        // Value field
        int fieldY = cy + ARROW_H;
        DrawUtils.drawPanel(graphics, cx, fieldY, cw, FIELD_H, theme.bgDark, theme.border);
        String valueStr = String.valueOf(partValue);
        int valTextX = cx + (cw - font.width(valueStr)) / 2;
        int valTextY = fieldY + (FIELD_H - font.lineHeight) / 2;
        graphics.drawString(font, valueStr, valTextX, valTextY, isEnabled() ? theme.textWhite : theme.disabledText, false);

        // Down arrow area
        int arrowDownY = cy + ARROW_H + FIELD_H;
        boolean hoverDown = isEnabled() && mouseX >= cx && mouseX < cx + cw
                && mouseY >= arrowDownY && mouseY < arrowDownY + ARROW_H;
        int downBg = hoverDown ? theme.bgLight : theme.bgMedium;
        DrawUtils.drawPanel(graphics, cx, arrowDownY, cw, ARROW_H, downBg, theme.border);
        String downArrow = "\u25BC";
        int downTextX = cx + (cw - font.width(downArrow)) / 2;
        int downTextY = arrowDownY + (ARROW_H - font.lineHeight) / 2;
        graphics.drawString(font, downArrow, downTextX, downTextY, isEnabled() ? theme.textLight : theme.disabledText, false);

        // Sub-unit label below
        String label = field.subUnit.code();
        int labelX = cx + (cw - font.width(label)) / 2;
        // Label is drawn just below the widget area (outside height)
        // Actually draw it overlapping the bottom arrow or just use textDim inside the down area
        // Better: draw label centered below the column
        int labelY = arrowDownY + ARROW_H + 1;
        graphics.drawString(font, label, labelX, labelY, theme.textDim, false);
    }

    // --- Mouse events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isEnabled() || !composite) return false;

        for (SubUnitField field : subUnitFields) {
            if (mouseX >= field.x && mouseX < field.x + field.width) {
                int arrowUpY = field.y;
                int arrowDownY = field.y + ARROW_H + FIELD_H;

                if (mouseY >= arrowUpY && mouseY < arrowUpY + ARROW_H) {
                    adjustSubUnit(field, 1);
                    return true;
                }
                if (mouseY >= arrowDownY && mouseY < arrowDownY + ARROW_H) {
                    adjustSubUnit(field, -1);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isEnabled() || !composite) return false;

        for (SubUnitField field : subUnitFields) {
            if (mouseX >= field.x && mouseX < field.x + field.width
                    && mouseY >= field.y && mouseY < field.y + COMPOSITE_HEIGHT) {
                int direction = scrollY > 0 ? 1 : -1;
                adjustSubUnit(field, direction);
                return true;
            }
        }
        return false;
    }

    // --- Value adjustment ---

    private void adjustSubUnit(SubUnitField field, int direction) {
        long delta = field.subUnit.multiplier() * direction;
        long newValue = clamp(value + delta);
        if (newValue != value) {
            value = newValue;
            notifyResponder();
        }
    }

    private long clamp(long v) {
        return Math.max(min, Math.min(max, v));
    }

    private void notifyResponder() {
        if (responder != null) {
            responder.accept(value);
        }
    }

    // --- Position updates ---

    @Override
    public void setPosition(int x, int y) {
        int dx = x - getX();
        int dy = y - getY();
        super.setPosition(x, y);

        if (composite) {
            for (SubUnitField field : subUnitFields) {
                field.x += dx;
                field.y += dy;
            }
        }
        // Simple mode: EcoNumberInput repositions via child tree
        if (simpleInput != null) {
            simpleInput.setPosition(simpleInput.getX() + dx, simpleInput.getY() + dy);
        }
    }

    // --- Inner class ---

    /**
     * Layout data for a single sub-unit column in composite mode.
     */
    private static class SubUnitField {
        int x, y, width;
        final SubUnit subUnit;
        final int index;

        SubUnitField(int x, int y, int width, SubUnit subUnit, int index) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.subUnit = subUnit;
            this.index = index;
        }
    }
}
