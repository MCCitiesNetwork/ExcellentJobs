package su.nightexpress.excellentjobs.job.impl;

import org.junit.jupiter.api.Test;
import su.nightexpress.nightcore.bridge.currency.Currency;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JobIncomeTest {
    private static Currency currency(String id) {
        return (Currency) Proxy.newProxyInstance(Currency.class.getClassLoader(), new Class<?>[]{Currency.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "getInternalId", "toString" -> id;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    @Test void rejectedPaymentRemainsPendingAndCanBeRetriedExactlyOnce() {
        Currency vault = currency("vault");
        JobIncome income = new JobIncome();
        income.add(vault, 12.5);
        assertTrue(income.payPending((currency, amount) -> false).isEmpty());
        assertEquals(12.5, income.getCurrencyMap().get(vault));
        double[] balance = {3.0};
        JobIncome paid = income.payPending((currency, amount) -> {
            assertSame(vault, currency);
            balance[0] += amount;
            return true;
        });
        assertEquals(15.5, balance[0]);
        assertEquals(12.5, paid.getCurrencyMap().get(vault));
        assertTrue(income.isEmpty());
        income.payPending((currency, amount) -> { fail("Already-paid income must not be paid twice"); return true; });
    }

    @Test void partialPayoutOnlyClearsSuccessfulCurrencies() {
        Currency vault = currency("vault");
        Currency xp = currency("xp_level");
        JobIncome income = new JobIncome();
        income.add(vault, 7.25);
        income.add(xp, 2);
        Map<Currency, Double> credited = new HashMap<>();
        JobIncome paid = income.payPending((currency, amount) -> {
            if (currency == vault) return false;
            credited.merge(currency, amount, Double::sum);
            return true;
        });
        assertEquals(Map.of(xp, 2.0), credited);
        assertEquals(Map.of(xp, 2.0), paid.getCurrencyMap());
        assertEquals(Map.of(vault, 7.25), income.getCurrencyMap());
        income.payPending((currency, amount) -> { credited.merge(currency, amount, Double::sum); return true; });
        assertEquals(Map.of(xp, 2.0, vault, 7.25), credited);
        assertTrue(income.isEmpty());
    }

    @Test void exceptionDoesNotErasePendingIncome() {
        Currency vault = currency("vault");
        JobIncome income = new JobIncome();
        income.add(vault, 9);
        assertThrows(IllegalStateException.class, () -> income.payPending((currency, amount) -> {
            throw new IllegalStateException("Provider unavailable");
        }));
        assertEquals(Map.of(vault, 9.0), income.getCurrencyMap());
    }
}
