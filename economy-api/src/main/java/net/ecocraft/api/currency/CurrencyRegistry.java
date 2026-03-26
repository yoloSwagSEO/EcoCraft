package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public interface CurrencyRegistry {
    void register(Currency currency);
    @Nullable Currency getById(String id);
    Currency getDefault();
    List<Currency> listAll();
    boolean exists(String id);
}
