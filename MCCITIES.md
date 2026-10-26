# MCCities 1.15.2 compatibility branch

This branch keeps ExcellentJobs 1.15.2's job configuration and database format.
It targets Minecraft 26.3, Java 25, and NightCore 2.16.7. It does not run the
ExcellentJobs 2.x migration.

The installed legacy custom build called `EconomyBridge.getVaultDebitAccountId()`.
That method is absent from NightCore 2.16.7 and is not part of upstream 1.15.2.
This branch uses the registered Vault economy provider for money payouts. With
Treasury, deposits are funded by the calling plugin's system account; the old
NightCore `Integration.Economy.Vault.DebitAccount` setting is not used.

Scheduled payouts now execute on the server thread, as do their payment events.
Vault rejection leaves the amount pending, successful currencies are removed
individually, and payment notifications describe only successful credits.
Pending income remains in memory, following the 1.15.2 data model; this change
is not a durable payment queue. Non-Vault currencies retain NightCore's void
payment contract and cannot report a rejected transaction.

The zone wire-block default uses `IRON_CHAIN`, the 26.3 material name.

## Build

Use JDK 25 and Maven 3.9.11 or newer:

```sh
bash scripts/bootstrap-legacy-deps.sh
mvn -B -ntp clean verify
```

The bootstrap installs three checksum-pinned official release JARs whose old
Maven coordinates no longer resolve reliably. It preserves the optional
LevelledMobs, EvenMoreFish and ProtocolLib integrations. Downloaded JARs stay
under `.context/`; they are not bundled in the plugin or committed.

Output: `target/ExcellentJobs-1.15.2-mccities.1.jar`.

## Verification

`JobIncomeTest` checks rejected-payment retention, exact-once retry, partial
multi-currency payments, and exceptions. A controlled mutation that treated
rejected deposits as successful was detected by two tests.

`integration-test/PayoutProbe.java` is a separate, staging-only plugin. It invokes
the actual job payout and logout paths with a synthetic player identity, the real
NightCore Vault currency, and Treasury. It checks cancellation, rejection of a
sub-cent payment, successful credit, duplicate prevention, and synchronous
payment events. Each run intentionally credits the dedicated test identity
`2daf1841-94bf-4b9f-9a9f-6b84e5044321` with 0.03 in staging. Never install the probe
on production. It disables itself after running; remove its JAR afterwards.

The probe is not included in the release JAR. After the normal build, run
`bash scripts/build-probe.sh` to produce `target/ExcellentJobsPayoutProbe.jar`.
It reports `PAYOUT_PROBE_PASS` or `PAYOUT_PROBE_FAIL` in the server log.

Validated on 2026-10-26 with UniverseSpigot 26.3, NightCore 2.16.7 and
TreasuryEconomy: five existing jobs loaded, the probe passed, and the staging
ledger showed matching ExcellentJobs SYSTEM debits and PERSONAL credits of 0.01
and 0.02. The dedicated test account's starting grant was established separately
before measuring payout deltas. The temporary probe was removed after testing.

Before replacing the plugin, stop the server and back up the old JAR and
`plugins/ExcellentJobs/`. Install only one ExcellentJobs JAR and restart.
This branch does not require a database migration.
