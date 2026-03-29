package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;

/**
 * Severity level of a toast notification.
 * Determines the accent color of the left bar.
 */
public enum ToastLevel {
    INFO {
        @Override
        public int getColor(Theme theme) { return theme.accent; }
    },
    SUCCESS {
        @Override
        public int getColor(Theme theme) { return theme.success; }
    },
    WARNING {
        @Override
        public int getColor(Theme theme) { return theme.warning; }
    },
    ERROR {
        @Override
        public int getColor(Theme theme) { return theme.danger; }
    };

    /** Returns the accent color for this level given the current theme. */
    public abstract int getColor(Theme theme);
}
