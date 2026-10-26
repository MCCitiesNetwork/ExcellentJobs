package su.nightexpress.excellentjobs.job.payment;

import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.AccountType;
import io.paradaux.treasury.model.economy.TransferRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import su.nightexpress.excellentjobs.config.Config;
import su.nightexpress.nightcore.bridge.currency.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public final class CurrencyPayments {
    private static final UUID SERVER_INITIATOR = new UUID(0L, 1L);

    private CurrencyPayments() {}

    public static boolean deposit(Player player, Currency currency, double amount) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Job payouts must run on the server thread");
        }
        if ("vault".equals(currency.getInternalId())) {
            try {
                return transfer(Bukkit.getServicesManager().load(TreasuryApi.class),
                    Config.GENERAL_PAYMENT_GOVERNMENT_ACCOUNT.get(), player.getUniqueId(), amount);
            } catch (RuntimeException failure) {
                Bukkit.getLogger().log(java.util.logging.Level.WARNING,
                    "[ExcellentJobs] Government payout failed; income remains pending", failure);
                return false;
            }
        }
        currency.deposit(player, amount);
        return true;
    }

    static boolean transfer(TreasuryApi treasury, String sourceName, UUID player, double amount) {
        if (treasury == null || !Double.isFinite(amount) || amount < 0.01) return false;
        var source = treasury.getGovernmentAccountByName(sourceName);
        if (source == null || source.getAccountType() != AccountType.GOVERNMENT || source.isArchived()) {
            return false;
        }
        var recipient = treasury.resolveOrCreatePersonal(player);
        // Treasury commits both postings atomically and enforces source-account policy.
        treasury.transfer(new TransferRequest(source.getAccountId(), recipient.getAccountId(),
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_EVEN),
            "ExcellentJobs job payout", SERVER_INITIATOR, null, "ExcellentJobs", null));
        return true;
    }
}
