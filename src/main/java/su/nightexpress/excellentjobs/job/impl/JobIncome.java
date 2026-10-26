package su.nightexpress.excellentjobs.job.impl;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentjobs.job.payment.CurrencyPayments;
import su.nightexpress.nightcore.bridge.currency.Currency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public class JobIncome {

    private final Map<Currency, Double> currencyMap;

    public JobIncome() {
        this.currencyMap = new ConcurrentHashMap<>();
    }

    public void payAndClear(@NotNull Player player) {
        this.payPending(player);
    }

    /** Pay each pending currency once, retaining rejected payments for a later attempt. */
    public JobIncome payPending(@NotNull Player player) {
        return this.payPending((currency, amount) -> CurrencyPayments.deposit(player, currency, amount));
    }

    synchronized JobIncome payPending(BiPredicate<Currency, Double> deposit) {
        JobIncome paid = new JobIncome();
        this.currencyMap.forEach((currency, amount) -> {
            if (deposit.test(currency, amount)) {
                this.reduce(currency, amount);
                paid.add(currency, amount);
            }
        });
        return paid;
    }

    public void pay(@NotNull Player player) {
        this.currencyMap.forEach((currency, amount) -> {
            CurrencyPayments.deposit(player, currency, amount);
        });
    }

    public synchronized void clear() {
        this.currencyMap.clear();
    }

    public synchronized void set(@NotNull Currency currency, double amount) {
        if (amount <= 0) {
            this.remove(currency);
            return;
        }

        this.currencyMap.put(currency, amount);
    }

    public synchronized void add(@NotNull Currency currency, double amount) {
        if (amount == 0) return;

        double has = this.currencyMap.getOrDefault(currency, 0D);

        this.set(currency, has + amount);
    }

    public synchronized void reduce(@NotNull Currency currency, double amount) {
        this.add(currency, -amount);
    }

    public synchronized void remove(@NotNull Currency currency) {
        this.currencyMap.remove(currency);
    }

    public boolean isEmpty() {
        return this.currencyMap.isEmpty() || this.currencyMap.values().stream().mapToDouble(amount -> amount).sum() == 0D;
    }

    @NotNull
    public Map<Currency, Double> getCurrencyMap() {
        return this.currencyMap;
    }
}
