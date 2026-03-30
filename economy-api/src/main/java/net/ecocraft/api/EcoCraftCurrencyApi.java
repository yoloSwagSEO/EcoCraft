package net.ecocraft.api;

import net.ecocraft.api.currency.ExternalCurrencyAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static registry for external currency adapters.
 * <p>
 * External mods register their {@link ExternalCurrencyAdapter} here so that
 * EcoCraft modules (auction-house, mail, etc.) can interact with foreign currencies.
 */
public final class EcoCraftCurrencyApi {

    private static final List<ExternalCurrencyAdapter> adapters = new CopyOnWriteArrayList<>();

    private EcoCraftCurrencyApi() {}

    /** Register an external currency adapter. */
    public static void registerAdapter(ExternalCurrencyAdapter adapter) {
        if (adapter == null) throw new IllegalArgumentException("Adapter cannot be null");
        adapters.add(adapter);
    }

    /** Returns an unmodifiable snapshot of all registered adapters. */
    public static List<ExternalCurrencyAdapter> getAdapters() {
        return List.copyOf(adapters);
    }

    /** Find an adapter by its mod id. */
    public static @Nullable ExternalCurrencyAdapter getAdapter(String modId) {
        for (var a : adapters) {
            if (a.modId().equals(modId)) return a;
        }
        return null;
    }

    /** Find the adapter that provides the given currency id. */
    public static @Nullable ExternalCurrencyAdapter getAdapterForCurrency(String currencyId) {
        for (var a : adapters) {
            if (a.getCurrency().id().equals(currencyId)) return a;
        }
        return null;
    }

    /** Clear all registered adapters. Intended for testing only. */
    public static void clearAdapters() {
        adapters.clear();
    }
}
