package dev.mccities.excellentjobs.probe;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import su.nightexpress.excellentjobs.JobsPlugin;
import su.nightexpress.excellentjobs.api.event.JobPaymentEvent;
import su.nightexpress.excellentjobs.job.impl.Job;
import su.nightexpress.excellentjobs.job.impl.JobIncome;
import su.nightexpress.excellentjobs.user.JobUser;
import su.nightexpress.nightcore.integration.currency.EconomyBridge;

import java.lang.reflect.Proxy;
import java.util.UUID;

/** Explicit staging-only probe: creates a test account and pays it 0.03 through Treasury. */
public final class PayoutProbe extends JavaPlugin implements Listener {
    private final UUID id = UUID.fromString("2daf1841-94bf-4b9f-9a9f-6b84e5044321");
    private boolean cancel;
    private int events;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::verify, 100);
    }

    @EventHandler public void onPayment(JobPaymentEvent event) {
        if (!event.getPlayer().getUniqueId().equals(id)) return;
        check(Bukkit.isPrimaryThread() && !event.isAsynchronous(), "payment event is synchronous");
        events++;
        event.setCancelled(cancel);
    }

    private void verify() {
        JobsPlugin jobs = (JobsPlugin) Bukkit.getPluginManager().getPlugin("ExcellentJobs");
        check(jobs != null && jobs.isEnabled(), "ExcellentJobs enabled");
        Bukkit.getPluginManager().registerEvents(this, this);
        JobUser user = JobUser.create(id, "EJCompatProbe");
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "EJCompatProbe";
                case "getPlayer" -> proxy;
                case "isOnline", "isValid", "hasPlayedBefore" -> true;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "EJCompatProbe";
                case "getLocale" -> "en_us";
                case "locale" -> java.util.Locale.US;
                case "getWorld" -> Bukkit.getWorlds().getFirst();
                case "getLocation" -> Bukkit.getWorlds().getFirst().getSpawnLocation();
                default -> {
                    if (method.getReturnType() == void.class) yield null;
                    if (method.getReturnType() == boolean.class) yield false;
                    if (method.getReturnType() == int.class) yield 0;
                    if (method.getReturnType() == long.class) yield 0L;
                    if (method.getReturnType() == double.class) yield 0D;
                    if (method.getReturnType() == float.class) yield 0F;
                    yield null;
                }
            });
        jobs.getUserManager().cachePermanent(user);
        try {
            Economy economy = Bukkit.getServicesManager().load(Economy.class);
            check(economy != null && economy.getName().toLowerCase().contains("treasury"), "Treasury is the economy provider");
            var vault = EconomyBridge.getCurrency("vault");
            check(vault != null, "NightCore Vault currency available");
            Job job = jobs.getJobManager().getJobs().iterator().next();
            JobIncome income = user.getData(job).getIncome();
            double before = economy.getBalance(player);

            income.set(vault, 0.01);
            cancel = true;
            check(!jobs.getJobManager().payForJob(player, job), "cancelled payout rejected");
            same(before, economy.getBalance(player), "cancelled payout leaves balance unchanged");
            same(0.01, income.getCurrencyMap().get(vault), "cancelled income retained");
            cancel = false;

            // Treasury rejects amounts smaller than its one-cent minimum.
            income.set(vault, 0.001);
            check(!jobs.getJobManager().payForJob(player, job), "provider rejection propagated");
            same(before, economy.getBalance(player), "rejected payout leaves balance unchanged");
            same(0.001, income.getCurrencyMap().get(vault), "rejected income retained");

            income.set(vault, 0.01);
            jobs.getJobManager().payForJob(player);
            same(before + 0.01, economy.getBalance(player), "regular payout credited exactly once");
            check(income.isEmpty(), "successful income cleared");
            jobs.getJobManager().payForJob(player);
            same(before + 0.01, economy.getBalance(player), "repeat payout does not duplicate credit");

            income.set(vault, 0.02);
            jobs.getJobManager().handleQuit(player);
            same(before + 0.03, economy.getBalance(player), "logout payout credited");
            check(income.isEmpty(), "logout income cleared");
            check(events == 4, "four synchronous payment events observed");
            getLogger().info("PAYOUT_PROBE_PASS server=" + Bukkit.getVersion() + " plugin=" + jobs.getPluginMeta().getVersion()
                + " provider=" + economy.getName() + " before=" + before + " after=" + economy.getBalance(player));
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "PAYOUT_PROBE_FAIL", failure);
        } finally {
            HandlerList.unregisterAll((Listener) this);
            jobs.getUserManager().getLoadedByIdMap().remove(id);
            jobs.getUserManager().getLoadedByNameMap().values().remove(user);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void same(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < 0.0000001, message + ": expected " + expected + ", got " + actual);
    }
}
