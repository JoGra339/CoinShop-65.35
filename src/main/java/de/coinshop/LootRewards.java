package de.coinshop;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Belohnung des seltenen Loot-Mobs der Expeditionen: würfelt etwas sehr Wertvolles aus dem Shop aus.
 * Es kommen nur Dinge in Frage, die der Spieler wirklich bekommen kann (nichts doppelt, nichts über dem Maximum).
 */
public final class LootRewards {

    /** Das Ergebnis: Text für den Spieler und ein ungefährer Wert in Coins (für die Buchhaltung). */
    public record Result(String text, long value, int tier) { }

    private interface Grant {
        Result apply(UUID uuid);
    }

    private record Option(int weight, Grant grant) { }

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public LootRewards(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Qualität der Belohnung (1 bis 5) nach Schwierigkeit: Level (Schwellen in expedition.loot-mob.tier-levels)
     * und Kosten der Kreatur (ab 30 eine Stufe mehr, ab 100 zwei Stufen mehr).
     */
    public static int tierOf(CoinShopPlugin plugin, int level, int mobCost) {
        List<Integer> limits = plugin.getConfig().getIntegerList("expedition.loot-mob.tier-levels");
        if (limits.isEmpty()) {
            limits = List.of(20, 100, 500, 2000);
        }
        int tier = 1;
        for (int limit : limits) {
            if (level >= limit) {
                tier++;
            }
        }
        tier += mobCost >= 100 ? 2 : mobCost >= 30 ? 1 : 0;
        return Math.max(1, Math.min(5, tier));
    }

    /** Stufe eines Loot-Mobs: die Schwierigkeits-Stufe, mit etwas Zufall (je nach Config eine Stufe höher oder niedriger). */
    public int shiftTier(int base) {
        int pct = Math.max(0, Math.min(50, plugin.getConfig().getInt("expedition.loot-mob.tier-shift-percent", 20)));
        int r = random.nextInt(100);
        int tier = r < pct ? base - 1 : r < pct * 2 ? base + 1 : base;
        return Math.max(1, Math.min(5, tier));
    }

    /** Stufe eines CoinMobs: meist niedrig, mit steigendem Level öfter hoch (Gewichte in expedition.coin-mob-tier-weights). */
    public int coinTier(int level) {
        List<Integer> w = plugin.getConfig().getIntegerList("expedition.coin-mob-tier-weights");
        if (w.size() < 5) {
            w = List.of(60, 25, 10, 4, 1);
        }
        int levelTier = tierOf(plugin, level, 0);
        double[] weights = new double[5];
        double sum = 0;
        for (int i = 0; i < 5; i++) {
            weights[i] = Math.max(0, w.get(i)) * Math.pow(1.0 + 0.5 * (levelTier - 1), i);
            sum += weights[i];
        }
        double pick = random.nextDouble() * Math.max(1e-9, sum);
        for (int i = 0; i < 5; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return i + 1;
            }
        }
        return 1;
    }

    private double tierFactor(int tier) {
        List<Double> list = plugin.getConfig().getDoubleList("expedition.loot-mob.tier-value-factor");
        if (list.isEmpty()) {
            list = List.of(1.0, 1.5, 2.2, 3.2, 5.0);
        }
        return list.get(Math.max(0, Math.min(list.size() - 1, tier - 1)));
    }

    /** Wählt aus einer Liste (aufsteigend nach Wert sortiert) mit steigender Qualität eher die wertvollen Einträge. */
    private <T> T biased(List<T> sortedByValue, int tier) {
        double r = Math.pow(random.nextDouble(), 1.0 / tier);
        return sortedByValue.get(Math.min(sortedByValue.size() - 1, (int) (r * sortedByValue.size())));
    }

    /** Ziel-Wert der Belohnung in Coins: wächst mit Level und Qualität, schwankt zwischen 1- und 2,5-fach. */
    private long targetValue(int level, int tier) {
        long base = Math.max(1, plugin.getConfig().getLong("expedition.loot-mob.value-base", 40000));
        long perLevel = Math.max(0, plugin.getConfig().getLong("expedition.loot-mob.value-per-level", 40));
        double factor = 1.0 + random.nextDouble() * 1.5;
        return Math.round((base + perLevel * level) * factor * tierFactor(tier));
    }

    /** Würfelt und vergibt die Belohnung (eigene Transaktion). Gibt null zurück, wenn etwas schiefgeht. */
    public Result roll(Player player, int level, int tier) {
        return roll(player, level, tier, false);
    }

    /** Wie roll; mit box = true gilt für die Coins die niedrigere Obergrenze der Mystery Boxen (mystery-box.coin-caps). */
    public Result roll(Player player, int level, int tier, boolean box) {
        UUID uuid = player.getUniqueId();
        Database db = plugin.database();
        long value = targetValue(level, tier);
        // Jede einzelne Belohnung ist höchstens Stufe * 10.000 wert (Stufe 1: 10.000, Stufe 2: 20.000 ...)
        final long cap = Math.max(1, plugin.getConfig().getLong("expedition.reward-value-per-tier", 10000L)) * Math.max(1, tier);
        if (box) {
            value = Math.max(1, Math.round(cap * (0.4 + 0.6 * random.nextDouble())));
        }
        value = Math.min(value, cap);
        // Coins, Aktien und Krypto bleiben immer unter der Grenze; ab Stufe 5 dürfen Lizenzen, Miner, Reviere, Routen,
        // Begleiter, KI-Plätze und Begleiter-Plätze deutlich mehr wert sein
        final long ncap = tier >= 5 ? Long.MAX_VALUE : cap;
        final long value0 = value;
        List<Option> options = new ArrayList<>();
        boolean black = plugin.blackMarket().unlocked(player) && !plugin.blackMarket().bountyOptOut(uuid)
                && !plugin.timeline().previewFor(player);

        options.add(new Option(Math.max(10, 30 - 5 * (tier - 1)), id -> {
            db.addCoins(id, value0);
            return new Result(fmt(value0) + " Coins", value0, tier);
        }));
        // Investitionen (Aktien, Krypto) sind als Belohnung bewusst klein und selten
        long investValue = Math.max(1, Math.round(value0 * Math.max(0.01, Math.min(1.0,
                plugin.getConfig().getDouble("expedition.loot-invest-factor", 0.25)))));
        options.add(new Option(Math.max(2, 6 - (tier - 1)), id -> {
            long price = Math.max(1, plugin.stock().price());
            long shares = Math.max(1, investValue / price);
            Database.StockHolding h = db.getStockHolding(id);
            db.setStockHolding(id, h.shares() + shares, h.invested() + shares * price);
            return new Result(fmt(shares) + " Aktien (Kurs " + fmt(price) + ")", shares * price, tier);
        }));
        options.add(new Option(Math.max(2, 6 - (tier - 1)), id -> {
            long price = Math.max(1, plugin.crypto().price(CryptoManager.KRYPTO));
            long units = Math.max(1, investValue * 100 / price);
            Database.CryptoHolding h = db.getCryptoHolding(id, CryptoManager.KRYPTO);
            db.setCryptoHolding(id, CryptoManager.KRYPTO, h.units() + units, h.invested() + units * price / 100);
            return new Result(CryptoManager.fmt(units) + " Krypto (Kurs " + fmt(price) + ")", units * price / 100, tier);
        }));
        if (black) {
            options.add(new Option(Math.max(2, 4 - (tier - 1) / 2), id -> {
                long price = Math.max(1, plugin.crypto().price(CryptoManager.DUNKEL));
                long units = Math.max(1, investValue * 100 / price);
                Database.CryptoHolding h = db.getCryptoHolding(id, CryptoManager.DUNKEL);
                db.setCryptoHolding(id, CryptoManager.DUNKEL, h.units() + units, h.invested() + units * price / 100);
                return new Result(CryptoManager.fmt(units) + " Habibi-Krypto (Kurs " + fmt(price) + ")", units * price / 100, tier);
            }));
        }
        // Lizenz, die er noch nicht hat (ohne Schwarzmarkt-Lizenzen)
        List<String> missing = new ArrayList<>();
        List<String> upgradable = new ArrayList<>();
        for (String id : LicenseManager.IDS) {
            if (LicenseManager.BLACK_IDS.contains(id) || plugin.licenses().get(id) == null) {
                continue;
            }
            if (!plugin.licenses().has(uuid, id)) {
                missing.add(id);
            } else if (plugin.licenses().level(uuid, id) < plugin.licenses().maxLevel(id)) {
                upgradable.add(id);
            }
        }
        missing.removeIf(i -> plugin.licenses().get(i).price() > ncap);
        upgradable.removeIf(i -> plugin.licenses().get(i).price() > ncap);
        missing.sort(java.util.Comparator.comparingLong(i -> plugin.licenses().get(i).price()));
        upgradable.sort(java.util.Comparator.comparingLong(i -> plugin.licenses().get(i).price()));
        if (!missing.isEmpty()) {
            String pick = biased(missing, tier);
            options.add(new Option(12 + 2 * tier, id -> {
                db.addLicense(id, pick);
                plugin.licenses().owned(id).add(pick);
                LicenseManager.License l = plugin.licenses().get(pick);
                return new Result("die Lizenz " + l.name(), l.price(), tier);
            }));
        }
        if (!upgradable.isEmpty()) {
            String pick = biased(upgradable, tier);
            options.add(new Option(10 + 2 * tier, id -> {
                int next = plugin.licenses().level(id, pick) + 1;
                db.setMetaLong("license_level_" + id + "_" + pick, next);
                LicenseManager.License l = plugin.licenses().get(pick);
                return new Result("die Lizenz " + l.name() + " auf Stufe " + next, l.price(), tier);
            }));
        }
        // Miner: der nächste neue oder eine Stufe mehr
        int newMiner = 0;
        List<Integer> upMiners = new ArrayList<>();
        for (int i = 1; i <= CryptoManager.MINERS; i++) {
            int lv = plugin.crypto().minerLevel(uuid, i);
            if (lv < 1) {
                if (newMiner == 0 && (i == 1 || plugin.crypto().minerLevel(uuid, i - 1) > 0)) {
                    newMiner = i;
                }
            } else if (lv < CryptoManager.MAX_LEVEL) {
                upMiners.add(i);
            }
        }
        if (newMiner > 0 && plugin.crypto().minerPrice(newMiner) > ncap) {
            newMiner = 0;
        }
        final int cm = newMiner;
        upMiners.removeIf(i -> plugin.crypto().levelPrice(i, plugin.crypto().minerLevel(uuid, i)) > ncap);
        if (cm > 0) {
            int idx = cm;
            options.add(new Option(6 + 2 * tier, id -> {
                db.setMinerLevel(id, idx, 1);
                return new Result("den Miner " + plugin.crypto().minerName(idx), plugin.crypto().minerPrice(idx), tier);
            }));
        }
        if (!upMiners.isEmpty()) {
            int idx = biased(upMiners, tier);
            options.add(new Option(4 + 2 * tier, id -> {
                int next = plugin.crypto().minerLevel(id, idx) + 1;
                db.setMinerLevel(id, idx, next);
                return new Result("den Miner " + plugin.crypto().minerName(idx) + " auf Stufe " + next,
                        plugin.crypto().levelPrice(idx, next - 1), tier);
            }));
        }
        // Schwarzmarkt: Revier-Slot, neues Revier, Schmugglerroute
        if (black) {
            int unlocked = plugin.reviere().unlockedCount(uuid);
            List<Integer> withRoom = new ArrayList<>();
            for (int r = 1; r <= unlocked; r++) {
                if (plugin.reviere().slots(uuid, r) < RevierManager.MAX_SLOTS
                        && plugin.reviere().nextSlotPrice(uuid, r) <= ncap) {
                    withRoom.add(r);
                }
            }
            if (!withRoom.isEmpty()) {
                int revier = biased(withRoom, tier);
                options.add(new Option(6 + 2 * tier, id -> {
                    db.setMetaLong("rv_bought_" + id + "_" + revier, plugin.reviere().bought(id, revier) + 1);
                    return new Result("einen Verkaufs-Slot im Revier " + plugin.reviere().name(revier),
                            plugin.reviere().nextSlotPrice(id, revier), tier);
                }));
            }
            if (unlocked < RevierManager.COUNT && plugin.reviere().unlockPrice(unlocked + 1) <= ncap) {
                int next = unlocked + 1;
                options.add(new Option(3 + 2 * tier, id -> {
                    db.setMetaLong("rv_unlocked_" + id, next);
                    return new Result("das Revier " + plugin.reviere().name(next) + " (freigeschaltet)",
                            plugin.reviere().unlockPrice(next), tier);
                }));
            }
            int routes = plugin.routes().count(uuid);
            if (routes < plugin.routes().max() && plugin.routes().price(routes + 1) <= ncap) {
                options.add(new Option(3 + 2 * tier, id -> {
                    plugin.routes().grant(id);
                    return new Result("eine Schmugglerroute (" + plugin.routes().name(routes + 1) + ")",
                            plugin.routes().price(routes + 1), tier);
                }));
            }
        }
        // Begleiter: eine Kreatur, je höher die Stufe, desto stärker und seltener
        if (plugin.expeditions().enabled()) {
            List<ExpeditionManager.Companion> beasts = new ArrayList<>(List.of(ExpeditionManager.Companion.values()));
            beasts.removeIf(c -> c.defaultPrice > ncap);
            beasts.sort(java.util.Comparator.comparingLong(c -> c.defaultPrice));
            if (!beasts.isEmpty()) {
                ExpeditionManager.Companion beast = biased(beasts, tier);
                int[][] weights = {{70, 22, 7, 1}, {40, 40, 17, 3}, {15, 45, 32, 8}, {0, 30, 50, 20}, {0, 0, 50, 50}};
                int[] w = weights[Math.max(0, Math.min(4, tier - 1))];
                int x = random.nextInt(w[0] + w[1] + w[2] + w[3]);
                int rarityIdx = 0;
                for (int i = 0; i < 4; i++) {
                    if (x < w[i]) {
                        rarityIdx = i;
                        break;
                    }
                    x -= w[i];
                }
                while (rarityIdx > 0 && beast.defaultPrice * CompanionTraits.Rarity.of(rarityIdx).stats > ncap) {
                    rarityIdx--;
                }
                int rIdx = rarityIdx;
                options.add(new Option(8 + 2 * tier, id -> {
                    CompanionTraits.Rarity rarity = CompanionTraits.Rarity.of(rIdx);
                    List<String> traits = new ArrayList<>();
                    for (CompanionTraits.Trait t : CompanionTraits.rollTraits(random, rarity)) {
                        traits.add(t.name());
                    }
                    int copy = plugin.expeditions().life().createCopy(id,
                            new CompanionLife.Offspring(beast, rIdx, 1, traits, 0));
                    plugin.expeditions().roster().save(id);
                    return new Result("den Begleiter " + beast.label + " (" + rarity.label + ", Nr. " + (copy + 1) + ")",
                            Math.round(beast.defaultPrice * rarity.stats), tier);
                }));
            }
        }
        // Begleiter-Platz (nur wenn der Platzpreis unter der Grenze liegt, also bei Stufe 5 praktisch immer)
        if (plugin.expeditions().enabled() && plugin.expeditions().slotPrice(uuid) <= ncap) {
            options.add(new Option(Math.max(2, tier - 1), id -> {
                long price = plugin.expeditions().slotPrice(id);
                db.setMetaLong("ex_cs_" + id, plugin.expeditions().slotsUnlocked(id) + 1);
                return new Result("einen Begleiter-Platz (Gefolge)", price, tier);
            }));
        }
        // Ausdauer: eine Stufe (Preis wie beim Kauf, gleiche Obergrenze wie bei den Begleiter-Plätzen)
        if (plugin.stamina().enabled() && plugin.stamina().upgradePrice(uuid) >= 0 && plugin.stamina().upgradePrice(uuid) <= ncap) {
            options.add(new Option(Math.max(2, tier), id -> {
                long price = plugin.stamina().upgradePrice(id);
                int now = plugin.stamina().grantLevel(id);
                return new Result("eine Ausdauer-Stufe (jetzt Stufe " + now + ")", Math.max(0, price), tier);
            }));
        }
        // Firma: ein KI-Platz
        if (plugin.companies().company(uuid) != null && db.getAis(uuid).size() < plugin.companies().aiMax()
                && plugin.companies().aiSlotPrice(db.getAis(uuid).size()) <= ncap) {
            options.add(new Option(3 + 2 * tier, id -> {
                db.addAi(id);
                return new Result("einen KI-Mitarbeiter-Platz", plugin.companies().aiSlotPrice(db.getAis(id).size()), tier);
            }));
        }

        // Qualität 1-2: eine Belohnung, 3-4: zwei verschiedene, 5: drei verschiedene
        int rewards = tier >= 5 ? 3 : tier >= 3 ? 2 : 1;
        List<Option> chosen = new ArrayList<>();
        for (int n = 0; n < rewards && !options.isEmpty(); n++) {
            int sum = 0;
            for (Option o : options) {
                sum += o.weight();
            }
            int pick = random.nextInt(Math.max(1, sum));
            Option hit = options.get(0);
            for (Option o : options) {
                pick -= o.weight();
                if (pick < 0) {
                    hit = o;
                    break;
                }
            }
            chosen.add(hit);
            options.remove(hit);
        }
        db.begin();
        try {
            StringBuilder text = new StringBuilder();
            long total = 0;
            for (Option o : chosen) {
                Result r = o.grant().apply(uuid);
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(r.text());
                total += r.value();
            }
            db.commit();
            return new Result(text.toString(), total, tier);
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Loot-Mob-Belohnung fehlgeschlagen: " + e);
            return null;
        }
    }

    private static String fmt(long n) {
        return String.format(java.util.Locale.GERMANY, "%,d", n);
    }
}
