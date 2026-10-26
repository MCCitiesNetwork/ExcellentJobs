package su.nightexpress.excellentjobs.job.payment;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import su.nightexpress.nightcore.bridge.currency.Currency;

public final class CurrencyPayments {
    private CurrencyPayments() {}

    public static boolean deposit(Player player, Currency currency, double amount) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Job payouts must run on the server thread");
        }
        if ("vault".equals(currency.getInternalId())) {
            // Let the installed economy provider own funding and transaction atomicity.
            // NightCore's void deposit method discards Vault's failure response.
            RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            return registration != null && registration.getProvider()
                .depositPlayer(player, amount).transactionSuccess();
        }
        currency.deposit(player, amount);
        return true;
    }
}
