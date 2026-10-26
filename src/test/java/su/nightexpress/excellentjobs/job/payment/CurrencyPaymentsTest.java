package su.nightexpress.excellentjobs.job.payment;

import io.paradaux.treasury.api.TreasuryApi;
import io.paradaux.treasury.model.economy.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyPaymentsTest {
    private final UUID player = UUID.randomUUID();

    private TreasuryApi api(Account source, AtomicReference<TransferRequest> request, boolean reject) {
        return (TreasuryApi) Proxy.newProxyInstance(TreasuryApi.class.getClassLoader(),
            new Class<?>[]{TreasuryApi.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getGovernmentAccountByName" -> {
                    assertEquals("GovEducation", args[0]);
                    yield source;
                }
                case "resolveOrCreatePersonal" -> {
                    assertEquals(player, args[0]);
                    yield account(115112, AccountType.PERSONAL);
                }
                case "transfer" -> {
                    if (reject) throw new IllegalStateException("Rejected by ledger");
                    assertNull(request.getAndSet((TransferRequest) args[0]), "one atomic transfer");
                    yield 123L;
                }
                default -> throw new AssertionError("Unexpected API call: " + method.getName());
            });
    }

    private Account account(int id, AccountType type) {
        Account a = new Account();
        a.setAccountId(id);
        a.setAccountType(type);
        return a;
    }

    @Test void governmentFundsPlayerInOneTransfer() {
        var request = new AtomicReference<TransferRequest>();
        assertTrue(CurrencyPayments.transfer(api(account(41234, AccountType.GOVERNMENT), request, false),
            "GovEducation", player, 2.47));
        assertEquals(41234, request.get().fromAccountId());
        assertEquals(115112, request.get().toAccountId());
        assertEquals(new BigDecimal("2.47"), request.get().amount());
        assertEquals("ExcellentJobs", request.get().pluginSystem());
    }

    @Test void missingArchivedOrWrongTypeSourceNeverPays() {
        var request = new AtomicReference<TransferRequest>();
        assertFalse(CurrencyPayments.transfer(null, "GovEducation", player, 1));
        assertFalse(CurrencyPayments.transfer(api(null, request, false), "GovEducation", player, 1));
        Account source = account(41234, AccountType.PERSONAL);
        assertFalse(CurrencyPayments.transfer(api(source, request, false), "GovEducation", player, 1));
        source.setAccountType(AccountType.GOVERNMENT);
        source.setArchived(true);
        assertFalse(CurrencyPayments.transfer(api(source, request, false), "GovEducation", player, 1));
        assertNull(request.get());
    }

    @Test void invalidAmountsNeverReachTreasury() {
        TreasuryApi treasury = api(null, new AtomicReference<>(), false);
        for (double value : new double[]{0, -1, 0.001, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFalse(CurrencyPayments.transfer(treasury, "GovEducation", player, value));
        }
    }

    @Test void ledgerRejectionCannotBecomeSuccessfulPayment() {
        var request = new AtomicReference<TransferRequest>();
        assertThrows(IllegalStateException.class, () -> CurrencyPayments.transfer(
            api(account(41234, AccountType.GOVERNMENT), request, true), "GovEducation", player, 1));
        assertNull(request.get());
    }
}
