package net.ecocraft.gui.theme;

/**
 * Configurable color palette for all UI components.
 * Use Theme.dark() for the default WoW-inspired dark theme.
 */
public class Theme {
    // Backgrounds
    public final int bgDarkest, bgDark, bgMedium, bgLight, bgRowAlt;
    // Borders
    public final int border, borderLight, borderAccent;
    // Accent
    public final int accent, accentBg, accentBgDim;
    // Text
    public final int textWhite, textLight, textGrey, textDim, textDark;
    // Functional
    public final int success, successBg, warning, warningBg, danger, dangerBg, info, infoBg;
    // Rarity
    public final int rarityCommon, rarityUncommon, rarityRare, rarityEpic, rarityLegendary;
    // Disabled state
    public final int disabledBg, disabledText, disabledBorder;

    public Theme(Builder builder) {
        this.bgDarkest = builder.bgDarkest;
        this.bgDark = builder.bgDark;
        this.bgMedium = builder.bgMedium;
        this.bgLight = builder.bgLight;
        this.bgRowAlt = builder.bgRowAlt;
        this.border = builder.border;
        this.borderLight = builder.borderLight;
        this.borderAccent = builder.borderAccent;
        this.accent = builder.accent;
        this.accentBg = builder.accentBg;
        this.accentBgDim = builder.accentBgDim;
        this.textWhite = builder.textWhite;
        this.textLight = builder.textLight;
        this.textGrey = builder.textGrey;
        this.textDim = builder.textDim;
        this.textDark = builder.textDark;
        this.success = builder.success;
        this.successBg = builder.successBg;
        this.warning = builder.warning;
        this.warningBg = builder.warningBg;
        this.danger = builder.danger;
        this.dangerBg = builder.dangerBg;
        this.info = builder.info;
        this.infoBg = builder.infoBg;
        this.rarityCommon = builder.rarityCommon;
        this.rarityUncommon = builder.rarityUncommon;
        this.rarityRare = builder.rarityRare;
        this.rarityEpic = builder.rarityEpic;
        this.rarityLegendary = builder.rarityLegendary;
        this.disabledBg = builder.disabledBg;
        this.disabledText = builder.disabledText;
        this.disabledBorder = builder.disabledBorder;
    }

    /** Default WoW-inspired dark theme. */
    public static Theme dark() {
        return new Builder()
            .bgDarkest(0xFF0D0D1A).bgDark(0xFF12122A).bgMedium(0xFF1A1A2E)
            .bgLight(0xFF2A2A3E).bgRowAlt(0xFF0A0A18)
            .border(0xFF333333).borderLight(0xFF444444).borderAccent(0xFFFFD700)
            .accent(0xFFFFD700).accentBg(0xFF4A3A1A).accentBgDim(0xFF3A2A1A)
            .textWhite(0xFFFFFFFF).textLight(0xFFCCCCCC).textGrey(0xFFAAAAAA)
            .textDim(0xFF888888).textDark(0xFF666666)
            .success(0xFF4CAF50).successBg(0xFF1A3A1A)
            .warning(0xFFFF9800).warningBg(0xFF2A1A0A)
            .danger(0xFFFF6B6B).dangerBg(0xFF2A0A0A)
            .info(0xFF64B5F6).infoBg(0xFF0A1A2A)
            .rarityCommon(0xFFFFFFFF).rarityUncommon(0xFF1EFF00)
            .rarityRare(0xFF0070DD).rarityEpic(0xFFA335EE).rarityLegendary(0xFFFF8000)
            .disabledBg(0xFF1A1A1A).disabledText(0xFF555555).disabledBorder(0xFF2A2A2A)
            .build();
    }

    public static class Builder {
        int bgDarkest, bgDark, bgMedium, bgLight, bgRowAlt;
        int border, borderLight, borderAccent;
        int accent, accentBg, accentBgDim;
        int textWhite, textLight, textGrey, textDim, textDark;
        int success, successBg, warning, warningBg, danger, dangerBg, info, infoBg;
        int rarityCommon, rarityUncommon, rarityRare, rarityEpic, rarityLegendary;
        int disabledBg, disabledText, disabledBorder;

        public Builder bgDarkest(int c) { bgDarkest = c; return this; }
        public Builder bgDark(int c) { bgDark = c; return this; }
        public Builder bgMedium(int c) { bgMedium = c; return this; }
        public Builder bgLight(int c) { bgLight = c; return this; }
        public Builder bgRowAlt(int c) { bgRowAlt = c; return this; }
        public Builder border(int c) { border = c; return this; }
        public Builder borderLight(int c) { borderLight = c; return this; }
        public Builder borderAccent(int c) { borderAccent = c; return this; }
        public Builder accent(int c) { accent = c; return this; }
        public Builder accentBg(int c) { accentBg = c; return this; }
        public Builder accentBgDim(int c) { accentBgDim = c; return this; }
        public Builder textWhite(int c) { textWhite = c; return this; }
        public Builder textLight(int c) { textLight = c; return this; }
        public Builder textGrey(int c) { textGrey = c; return this; }
        public Builder textDim(int c) { textDim = c; return this; }
        public Builder textDark(int c) { textDark = c; return this; }
        public Builder success(int c) { success = c; return this; }
        public Builder successBg(int c) { successBg = c; return this; }
        public Builder warning(int c) { warning = c; return this; }
        public Builder warningBg(int c) { warningBg = c; return this; }
        public Builder danger(int c) { danger = c; return this; }
        public Builder dangerBg(int c) { dangerBg = c; return this; }
        public Builder info(int c) { info = c; return this; }
        public Builder infoBg(int c) { infoBg = c; return this; }
        public Builder rarityCommon(int c) { rarityCommon = c; return this; }
        public Builder rarityUncommon(int c) { rarityUncommon = c; return this; }
        public Builder rarityRare(int c) { rarityRare = c; return this; }
        public Builder rarityEpic(int c) { rarityEpic = c; return this; }
        public Builder rarityLegendary(int c) { rarityLegendary = c; return this; }
        public Builder disabledBg(int c) { disabledBg = c; return this; }
        public Builder disabledText(int c) { disabledText = c; return this; }
        public Builder disabledBorder(int c) { disabledBorder = c; return this; }
        public Theme build() { return new Theme(this); }
    }
}
