package de.coinshop;

import de.coinshop.gui.WholesaleHubMenu;
import de.coinshop.gui.WholesaleMenu;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Der Großhandel (Lizenz "grosshandel"): zehn Branchen, die man in beliebiger Reihenfolge freischaltet. Jeder Spieler hat
 * pro Branche eigene Großaufträge (Item aus dem Thema der Branche, 10.000 bis 100.000 Stück). Ein Auftrag bleibt, bis er
 * erledigt ist; mit jedem Shop-Wechsel kommt pro Branche ein neuer dazu. Abgegebene Items sind weg, auf die Prämie wird
 * Steuer gezahlt. Wer in einer Branche pro Woche die meisten Aufträge erledigt, wird Gesellschafter (100 Coins von jedem
 * dort erledigten Auftrag anderer). Wer in den meisten Branchen Gesellschafter ist, wird CEO (200 Coins von jedem
 * erledigten Auftrag aller Branchen). Auswertung sonntags beim Shop-Wechsel.
 * Merkzettel (meta): wh_sec_UUID (Bitmaske der Branchen), wh_t_BRANCHE_UUID (Aufträge dieser Woche),
 * wh_bm_/wh_bl_BRANCHE (Gesellschafter), wh_cm/wh_cl (CEO), wh_last_eval (Auswertung). Tabelle wh_orders (Aufträge).
 */
public final class WholesaleManager implements CommandExecutor, org.bukkit.event.Listener {

    /** Eine Branche mit ihrem Thema; das Thema bestimmt, welche Items beauftragt werden. */
    public record Sector(String id, String name, String theme, Material icon, List<Material> items) { }

    public static final List<Sector> SECTORS = List.of(
            new Sector("landwirtschaft", "Landwirtschaft", "Feldfrüchte und Saatgut", Material.WHEAT, List.of(
                    Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROT, Material.POTATO, Material.BEETROOT,
                    Material.MELON_SLICE, Material.PUMPKIN, Material.SWEET_BERRIES, Material.SUGAR_CANE,
                    Material.COCOA_BEANS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS)),
            new Sector("bergbau", "Bergbau", "Erze und Gestein", Material.IRON_PICKAXE, List.of(
                    Material.COBBLESTONE, Material.COAL, Material.RAW_IRON, Material.RAW_COPPER, Material.RAW_GOLD,
                    Material.REDSTONE, Material.LAPIS_LAZULI, Material.FLINT, Material.DEEPSLATE, Material.QUARTZ,
                    Material.AMETHYST_SHARD)),
            new Sector("forstwirtschaft", "Forstwirtschaft", "Hölzer", Material.IRON_AXE, List.of(
                    Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
                    Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
                    Material.STICK, Material.CHARCOAL)),
            new Sector("viehzucht", "Viehzucht", "Tierprodukte", Material.LEAD, List.of(
                    Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON, Material.RABBIT,
                    Material.LEATHER, Material.WHITE_WOOL, Material.EGG, Material.FEATHER)),
            new Sector("baustoffe", "Baustoffe", "Steine, Ziegel und Glas", Material.BRICKS, List.of(
                    Material.SAND, Material.GLASS, Material.BRICK, Material.STONE_BRICKS, Material.TERRACOTTA,
                    Material.SMOOTH_STONE, Material.CLAY_BALL, Material.DIORITE, Material.ANDESITE,
                    Material.GRANITE, Material.GRAVEL)),
            new Sector("fischerei", "Fischerei", "Fische und Meeresgüter", Material.FISHING_ROD, List.of(
                    Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH, Material.INK_SAC,
                    Material.KELP, Material.SEA_PICKLE, Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS,
                    Material.LILY_PAD)),
            new Sector("textil", "Textil und Farben", "Fäden, Farben und Papier", Material.RED_DYE, List.of(
                    Material.STRING, Material.RED_DYE, Material.YELLOW_DYE, Material.GREEN_DYE, Material.BLUE_DYE,
                    Material.WHITE_DYE, Material.PAPER, Material.BONE_MEAL, Material.BLACK_DYE)),
            new Sector("nether", "Nether-Handel", "Güter aus dem Nether", Material.NETHER_WART, List.of(
                    Material.NETHERRACK, Material.NETHER_WART, Material.SOUL_SAND, Material.GLOWSTONE_DUST,
                    Material.BLAZE_ROD, Material.MAGMA_CREAM, Material.NETHER_BRICK, Material.BASALT,
                    Material.BLACKSTONE, Material.CRIMSON_STEM, Material.WARPED_STEM)),
            new Sector("alchemie", "Alchemie", "Zutaten für Tränke und Technik", Material.BREWING_STAND, List.of(
                    Material.GUNPOWDER, Material.SLIME_BALL, Material.SPIDER_EYE, Material.GLASS_BOTTLE,
                    Material.HONEYCOMB, Material.BLAZE_POWDER, Material.REDSTONE_TORCH, Material.GLISTERING_MELON_SLICE,
                    Material.FERMENTED_SPIDER_EYE)),
            new Sector("baeckerei", "Bäckerei und Küche", "Gebackenes und Gekochtes", Material.BREAD, List.of(
                    Material.BREAD, Material.COOKIE, Material.PUMPKIN_PIE, Material.BAKED_POTATO,
                    Material.COOKED_CHICKEN, Material.COOKED_PORKCHOP, Material.COOKED_COD, Material.DRIED_KELP,
                    Material.SUGAR, Material.HONEY_BOTTLE)));

    private final CoinShopPlugin plugin;

    private final WholesaleTreaties treaties;

    public WholesaleManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        this.treaties = new WholesaleTreaties(plugin);
    }

    public WholesaleTreaties treaties() {
        return treaties;
    }

    // ------------------------------------------------------------------
    //  Zugang
    // ------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("wholesale.enabled", true);
    }

    /** Hat der Spieler die Großhandellizenz (Admins immer)? Nur diese Spieler bekommen Infos zum Großhandel. */
    public boolean unlocked(Player player) {
        return enabled() && ((plugin.getConfig().getBoolean("admin-bypass-licenses", false) && player.hasPermission("coinshop.admin"))
                || plugin.licenses().has(player.getUniqueId(), "grosshandel"));
    }

    public boolean allowed(Player player) {
        if (!unlocked(player)) {
            plugin.messages().send(player, "wh-locked");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("koop")) {
            if (allowed(player)) {
                if (args[1].equalsIgnoreCase("annehmen")) {
                    treaties.accept(player);
                } else if (args[1].equalsIgnoreCase("ablehnen")) {
                    treaties.decline(player);
                }
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("gewerbe")) {
            if (args[1].equalsIgnoreCase("annehmen")) {
                plugin.companies().accept(player);
            } else if (args[1].equalsIgnoreCase("ablehnen")) {
                plugin.companies().decline(player);
            }
            return true;
        }
        openFor(player);
        return true;
    }

    /** Öffnet den Großhandel: Lizenzinhaber das Hauptmenü, Angestellte ohne Lizenz nur ihren Arbeitsplatz. */
    public void openFor(Player player) {
        if (unlocked(player)) {
            new WholesaleHubMenu(plugin, player).open(player);
        } else if (enabled() && plugin.companies().employerOf(player.getUniqueId()) != null) {
            new de.coinshop.gui.StaffHomeMenu(plugin, player).open(player);
        } else {
            plugin.messages().send(player, "wh-locked");
        }
    }

    // ------------------------------------------------------------------
    //  Branchen
    // ------------------------------------------------------------------

    private long mask(UUID uuid) {
        return plugin.database().getMetaLong("wh_sec_" + uuid, 0);
    }

    public boolean hasSector(UUID uuid, int sector) {
        return (mask(uuid) & (1L << sector)) != 0;
    }

    public int sectorCount(UUID uuid) {
        return Long.bitCount(mask(uuid));
    }

    /** Preis der nächsten Branche (steigt mit jeder freigeschalteten; Ausverkauf und Inflation wirken). */
    public long unlockPrice(UUID uuid) {
        if (sectorCount(uuid) == 0) {
            return 0; // die erste Branche ist gratis
        }
        List<Long> prices = plugin.getConfig().getLongList("wholesale.sector-prices");
        if (prices.isEmpty()) {
            prices = List.of(5000L, 15000L, 40000L, 100000L, 250000L, 400000L, 600000L, 850000L, 1200000L, 1600000L);
        }
        int n = Math.min(sectorCount(uuid), prices.size() - 1);
        return Math.max(1, Math.round(prices.get(n) * plugin.events().saleFactor()));
    }

    /** "Gratis" oder "1.000 Coins" für Menüs und Nachrichten. */
    public String priceText(long price) {
        return price <= 0 ? "Gratis" : String.format(java.util.Locale.GERMANY, "%,d Coins", price);
    }

    public void unlockSector(Player player, int sector) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!allowed(player) || sector < 0 || sector >= SECTORS.size() || hasSector(uuid, sector)) {
            return;
        }
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return;
        }
        long price = unlockPrice(uuid);
        db.begin();
        try {
            if (price > 0 && !db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            if (price > 0) {
                db.addShopAccount(price);
            }
            db.setMetaLong("wh_sec_" + uuid, mask(uuid) | (1L << sector));
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Freischalten einer Branche: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        createOrder(uuid, sector); // der erste Auftrag kommt sofort
        if (mask(uuid) == (1L << sector)) { // erste Branche: ab jetzt zählt der nächste Shop-Wechsel
            db.setMetaLong("wh_seen_" + uuid, resets());
        }
        db.logTx(uuid, player.getName(), "grosshandel-branche", -price, SECTORS.get(sector).name());
        msg.send(player, "wh-sector-unlocked", "name", SECTORS.get(sector).name(), "price", priceText(price));
    }

    // ------------------------------------------------------------------
    //  Aufträge
    // ------------------------------------------------------------------

    private final Random random = new Random();

    private long newAmount(UUID uuid) {
        long min = Math.max(1, plugin.getConfig().getLong("wholesale.min-amount", 10000));
        long max = Math.max(min, plugin.getConfig().getLong("wholesale.max-amount", 100000));
        long steps = Math.max(1, (max - min) / 1000);
        long amount = min + (long) random.nextInt((int) steps + 1) * 1000;
        // Titel Gesellschafter / CEO: Aufträge nach Wahl kleiner oder größer
        double size = plugin.titles().effect(uuid, "ws-order-size", "") / 100.0;
        double title = Math.max(0.1, 1.0 + (orderSizeLarger(uuid) ? size : -size));
        return Math.max(1, Math.round(amount * plugin.events().wholesaleAmountFactor() * title));
    }

    /** Hat der Spieler mit seinem Titel (Gesellschafter / CEO) "größere Aufträge" gewählt? Standard: kleinere. */
    public boolean orderSizeLarger(UUID uuid) {
        return plugin.database().getMetaLong("ws_size_" + uuid, 0) == 1;
    }

    public boolean toggleOrderSize(UUID uuid) {
        boolean larger = !orderSizeLarger(uuid);
        plugin.database().setMetaLong("ws_size_" + uuid, larger ? 1 : 0);
        return larger;
    }

    /** Legt einen neuen Auftrag für den Spieler an (Item aus dem Thema der Branche). */
    public void createOrder(UUID uuid, int sector) {
        List<Material> items = SECTORS.get(sector).items();
        Material material = items.get(random.nextInt(items.size()));
        plugin.database().addWsOrder(uuid, sector, material.name(), newAmount(uuid));
    }

    /** Legt einen Eilauftrag an und gibt seine Id zurück (der Auftrag mit der höchsten Id der Branche). */
    public long createRushOrder(UUID uuid, int sector) {
        createOrder(uuid, sector);
        long id = 0;
        for (Database.WsOrder o : plugin.database().getWsOrders(uuid, sector)) {
            id = Math.max(id, o.id());
        }
        return id;
    }

    public List<Database.WsOrder> orders(UUID uuid, int sector) {
        return plugin.database().getWsOrders(uuid, sector);
    }

    /** Prämie pro Stück ohne Boni. */
    public long unitPrice(Material material) {
        double factor = Math.max(0.01, plugin.getConfig().getDouble("wholesale.pay-factor", 0.2));
        return Math.max(1, Math.round(plugin.priceBook().price(material) * factor));
    }

    /** Wochenzähler: erledigte Aufträge dieser Woche in der Branche. */
    public long weeklyDone(UUID uuid, int sector) {
        return plugin.database().getMetaLong("wh_t_" + SECTORS.get(sector).id() + "_" + uuid, 0);
    }

    /** Bonus in Prozent aus der Lizenz. */
    public double bonusPercent(UUID uuid) {
        return plugin.licenses().has(uuid, "grosshandel") ? plugin.licenses().value(uuid, "grosshandel") : 0;
    }

    /** Prämie pro Stück für diesen Spieler (mit Lizenz-Bonus und Ereignissen). */
    public double effectiveUnit(UUID uuid, Material material) {
        return unitPrice(material) * (1.0 + bonusPercent(uuid) / 100.0)
                * plugin.events().earnings() * plugin.events().wholesalePayFactor();
    }

    private static boolean plain(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material && !stack.hasItemMeta();
    }

    /** Vergütung für einen vollständig erledigten Auftrag (vor Abzügen). */
    public long grossFor(UUID uuid, Database.WsOrder order) {
        Material material = Material.matchMaterial(order.material());
        if (material == null) {
            return 0;
        }
        return Math.max(1, Math.round(order.amount() * effectiveUnit(uuid, material) * treaties.payFactor(uuid, order)
                * sizeFactor(order.amount())));
    }

    /**
     * Mengenrabatt: je mehr Items ein Auftrag verlangt, desto weniger Prämie gibt es pro Stück. Beim kleinsten Auftrag
     * (min-amount) ist der Faktor 1,0, beim größten (max-amount) 1 minus size-degression (Standard 0,4 = 40 % weniger).
     */
    public double sizeFactor(long amount) {
        long min = Math.max(1, plugin.getConfig().getLong("wholesale.min-amount", 10000));
        long max = Math.max(min + 1, plugin.getConfig().getLong("wholesale.max-amount", 100000));
        double degression = Math.max(0, Math.min(0.9, plugin.getConfig().getDouble("wholesale.size-degression", 0.4)));
        double t = Math.max(0.0, Math.min(1.0, (amount - min) / (double) (max - min)));
        return 1.0 - degression * t;
    }

    /** Wie viel von der Vergütung gehen an Gesellschafter und CEO? (Bonus wird von der Vergütung abgezogen.) */
    public long[] bonuses(UUID payer, int sector, long gross) {
        long shareBonus = Math.max(0, plugin.getConfig().getLong("wholesale.shareholder-bonus", 100));
        long ceoBonus = Math.max(0, plugin.getConfig().getLong("wholesale.ceo-bonus", 200));
        UUID share = shareholder(sector);
        UUID ceo = ceo();
        long s = share != null && !share.equals(payer) ? shareBonus : 0;
        long c = ceo != null && !ceo.equals(payer) ? ceoBonus : 0;
        long total = s + c;
        if (total > gross) { // nie mehr abziehen, als es zu verdienen gibt
            s = s * gross / total;
            c = gross - s;
            if (ceo == null || ceo.equals(payer)) {
                c = 0;
            }
        }
        return new long[]{s, c};
    }

    /**
     * Gibt Items für einen Auftrag ab. all = alles Passende bis zur Restmenge, sonst ein Stapel. Die Items sind weg;
     * die Vergütung gibt es erst, wenn der ganze Auftrag erfüllt ist. Bei einem gemeinsamen Auftrag (Kooperationsvertrag)
     * dürfen beide Spieler abgeben, die Prämie wird nach Beitrag aufgeteilt.
     */
    public void deliver(Player player, long orderId, boolean all) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        boolean licensed = unlocked(player);
        if (!licensed && !(enabled() && plugin.companies().employerOf(uuid) != null)) {
            msg.send(player, "wh-locked");
            return;
        }
        Database.WsOrder order = db.getWsOrder(orderId);
        boolean own = order != null && (order.owner().equals(uuid) || (licensed && uuid.equals(treaties.partnerOf(order.owner()))));
        boolean staff = !own && plugin.companies().mayWork(uuid, order);
        if (order == null || !(own || staff)) {
            msg.send(player, "wh-order-gone");
            return;
        }
        if (treaties.rushExpired(orderId)) { // abgelaufener Eilauftrag
            db.deleteWsOrder(orderId);
            msg.send(player, "wh-order-gone");
            return;
        }
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return;
        }
        Material material = Material.matchMaterial(order.material());
        if (material == null) {
            db.deleteWsOrder(orderId);
            return;
        }
        long left = Math.max(0, order.amount() - order.delivered());
        PlayerInventory inv = player.getInventory();
        long taken = 0;
        for (int i = 0; i < inv.getSize() && taken < left; i++) {
            ItemStack stack = inv.getItem(i);
            if (!plain(stack, material)) {
                continue;
            }
            long take = Math.min(stack.getAmount(), left - taken);
            taken += take;
            if (take >= stack.getAmount()) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - (int) take);
            }
            if (!all) {
                break;
            }
        }
        if (taken <= 0) {
            msg.send(player, "wh-nothing", "item", material.name());
            return;
        }
        settle(player, uuid, order, material, taken, left, staff, inv, player.getName());
    }

    /** Ein KI-Mitarbeiter liefert Items für einen Auftrag seines Inhabers (wie eine Abgabe, nur ohne Spieler). */
    public void aiDeliver(UUID owner, Database.WsOrder order, long want) {
        Database db = plugin.database();
        Material material = Material.matchMaterial(order.material());
        if (material == null || treaties.rushExpired(order.id())) {
            db.deleteWsOrder(order.id());
            return;
        }
        long left = Math.max(0, order.amount() - order.delivered());
        long taken = Math.min(left, want);
        if (taken <= 0) {
            return;
        }
        settle(null, owner, order, material, taken, left, false, null, "KI");
    }

    /** Verbucht abgegebene Items: Fortschritt, bei Abschluss Prämie, Löhne, Boni und Meldungen. player darf null sein (KI). */
    private void settle(Player player, UUID uuid, Database.WsOrder order, Material material, long taken, long left,
                        boolean staff, PlayerInventory inv, String who) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        long orderId = order.id();
        boolean done = order.delivered() + taken >= order.amount();
        String sectorId = SECTORS.get(order.sector()).id();
        String sectorName = SECTORS.get(order.sector()).name();
        UUID partner = treaties.partnerOf(order.owner());
        UUID owner = order.owner();
        UUID employer = staff ? plugin.companies().employerOf(uuid) : null;
        UUID payer = staff ? employer : uuid; // bei Angestellten wird der Arbeitgeber bezahlt (auch im Kooperationsvertrag)
        long wages = 0;
        long wageSum = 0;
        long[] wageEach = new long[0];
        Map<UUID, Long> wagePaid = new java.util.LinkedHashMap<>();
        List<Database.Contrib> wageList = new ArrayList<>();
        UUID shareholder = shareholder(order.sector());
        UUID ceo = ceo();
        long gross = 0;
        long[] bonus = {0, 0};
        long tax = 0;
        long net = 0;
        // Gemeinsamer Auftrag: Anteil des anderen Spielers
        UUID other = null;
        long otherNet = 0;
        long[] totalBonus = {0, 0};
        db.begin();
        try {
            if (partner != null) {
                treaties.addContribution(orderId, staff ? employer : uuid, taken);
            }
            if (staff) {
                db.addContrib(orderId, uuid, taken);
                db.addStaffItems(uuid, taken);
            }
            if (done) {
                long fullTotal = grossFor(owner, order);
                wageList = db.getContribs(orderId);
                wageEach = new long[wageList.size()];
                wageSum = 0;
                for (int i = 0; i < wageList.size(); i++) {
                    Database.Contrib c = wageList.get(i);
                    // Lohn nach Menge, für alle Angestellten gleich
                    wageEach[i] = Math.min(plugin.companies().wageCap(), plugin.companies().wageFor(material, c.amount()));
                    wageSum += wageEach[i];
                }
                // Jeder Angestellte bekommt höchstens 10.000 Coins pro Auftrag, alle zusammen höchstens 80 % der Prämie.
                // Reicht das nicht, wird es nach der abgegebenen Menge (Beitrag) aufgeteilt.
                long wageLimit = fullTotal * plugin.companies().wageMaxPercent() / 100;
                if (wageSum > wageLimit) {
                    long scaled = 0;
                    for (int i = 0; i < wageEach.length; i++) {
                        wageEach[i] = wageSum <= 0 ? 0 : wageEach[i] * wageLimit / wageSum;
                        scaled += wageEach[i];
                    }
                    wageSum = scaled;
                }
                wages = wageSum;
                long total = fullTotal - wages;
                long[] allBonus = bonuses(owner, order.sector(), total);
                totalBonus = allBonus;
                if (partner == null) {
                    gross = total;
                    bonus = allBonus;
                    long afterBonus = gross - bonus[0] - bonus[1];
                    tax = taxFor(payer, afterBonus);
                    net = afterBonus - tax;
                    db.addCoins(payer, net);
                    // Umsatz zählt voll dem Inhaber (auch der Teil, der als Lohn an Angestellte geht)
                    db.setMetaLong("wh_t_" + sectorId + "_" + payer, weeklyDone(payer, order.sector()) + fullTotal);
                    addTurnover(payer, fullTotal);
                    treaties.onOrderDone(payer, order.sector());
                } else {
                    // Fair aufteilen: nach der Menge, die jeder abgegeben hat
                    long cOwner = treaties.contribution(orderId, owner);
                    long cPartner = treaties.contribution(orderId, partner);
                    long sum = Math.max(1, cOwner + cPartner);
                    long[] shares = {total * cOwner / sum, 0};
                    shares[1] = total - shares[0];
                    long[] bonusOwner = {allBonus[0] * cOwner / sum, allBonus[1] * cOwner / sum};
                    long[] bonusPartner = {allBonus[0] - bonusOwner[0], allBonus[1] - bonusOwner[1]};
                    long afterOwner = shares[0] - bonusOwner[0] - bonusOwner[1];
                    long afterPartner = shares[1] - bonusPartner[0] - bonusPartner[1];
                    long taxOwner = taxFor(owner, afterOwner);
                    long taxPartner = taxFor(partner, afterPartner);
                    db.addCoins(owner, afterOwner - taxOwner);
                    db.addCoins(partner, afterPartner - taxPartner);
                    // Der Lohn der Angestellten gehört zum Umsatz der Firma, bei der sie arbeiten
                    long wagesOwnerSide = 0;
                    for (int i = 0; i < wageList.size(); i++) {
                        if (owner.equals(plugin.companies().employerOf(wageList.get(i).member()))) {
                            wagesOwnerSide += wageEach[i];
                        }
                    }
                    long turnOwner = shares[0] + wagesOwnerSide;
                    long turnPartner = shares[1] + (wages - wagesOwnerSide);
                    db.setMetaLong("wh_t_" + sectorId + "_" + owner, weeklyDone(owner, order.sector()) + turnOwner);
                    db.setMetaLong("wh_t_" + sectorId + "_" + partner, weeklyDone(partner, order.sector()) + turnPartner);
                    addTurnover(owner, turnOwner);
                    addTurnover(partner, turnPartner);
                    boolean mineOwner = payer.equals(owner);
                    gross = mineOwner ? shares[0] : shares[1];
                    bonus = mineOwner ? bonusOwner : bonusPartner;
                    tax = mineOwner ? taxOwner : taxPartner;
                    net = mineOwner ? afterOwner - taxOwner : afterPartner - taxPartner;
                    other = mineOwner ? partner : owner;
                    otherNet = mineOwner ? afterPartner - taxPartner : afterOwner - taxOwner;
                    if (taxOwner + taxPartner > 0) {
                        db.addShopAccount(taxOwner + taxPartner);
                    }
                    treaties.onOrderDone(owner, order.sector());
                    treaties.onOrderDone(partner, order.sector());
                    treaties.clearContribution(orderId, owner, partner);
                }
                if (allBonus[0] > 0 && shareholder != null) {
                    db.addCoins(shareholder, allBonus[0]);
                }
                if (allBonus[1] > 0 && ceo != null) {
                    db.addCoins(ceo, allBonus[1]);
                }
                if (partner == null && tax > 0) {
                    db.addShopAccount(tax);
                }
                if (wages > 0) {
                    for (int i = 0; i < wageList.size(); i++) {
                        Database.Contrib c = wageList.get(i);
                        if (wageEach[i] > 0) {
                            db.addCoins(c.member(), wageEach[i]);
                            db.addStaffWages(c.member(), wageEach[i]);
                            wagePaid.put(c.member(), wageEach[i]);
                        }
                    }
                }
                db.deleteWsOrder(orderId);
            } else {
                db.setWsDelivered(orderId, order.delivered() + taken);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            if (inv != null && player != null) {
                inv.addItem(new ItemStack(material, (int) Math.min(taken, Integer.MAX_VALUE)))
                        .values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
            plugin.getLogger().severe("Fehler bei der Großhandel-Abgabe: " + e.getMessage());
            if (player != null) {
                msg.send(player, "error");
            }
            return;
        }
        if (!done) {
            if (player != null) {
                msg.send(player, "wh-partial", "amount", taken, "left", left - taken);
            }
            return;
        }
        db.logTx(payer, db.getName(payer), "grosshandel", net,
                order.amount() + "x " + material.name() + " (Steuer " + tax + ", Bonus " + (bonus[0] + bonus[1]) + ")");
        Player payerOnline = (staff || player == null) ? org.bukkit.Bukkit.getPlayer(payer) : player;
        if (payerOnline != null) {
            msg.send(payerOnline, "wh-order-done", "sector", sectorName, "gross", gross,
                    "bonus", bonus[0] + bonus[1], "tax", tax, "net", net);
            if (wages > 0) {
                msg.send(payerOnline, "co-wages-paid", "wages", wages);
            }
        }
        for (Map.Entry<UUID, Long> entry : wagePaid.entrySet()) {
            db.logTx(entry.getKey(), db.getName(entry.getKey()), "gewerbe-lohn", entry.getValue(),
                    "Lohn: " + order.amount() + "x " + material.name());
            Player member = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (member != null) {
                msg.send(member, "co-wage", "wage", entry.getValue(), "sector", sectorName);
            }
        }
        if (other != null) {
            db.logTx(other, db.getName(other), "grosshandel", otherNet,
                    "Anteil am gemeinsamen Auftrag " + order.amount() + "x " + material.name());
            Player otherOnline = org.bukkit.Bukkit.getPlayer(other);
            if (otherOnline != null) {
                msg.send(otherOnline, "tr-coop-done", "player", who, "sector", sectorName, "net", otherNet);
            }
        }
        notifyBonus(shareholder, totalBonus[0], "wh-bonus-shareholder", sectorName, who);
        notifyBonus(ceo, totalBonus[1], "wh-bonus-ceo", sectorName, who);
    
    }

    /** Gesamtumsatz einer Firma (für den Firmenrang). */
    private void addTurnover(UUID uuid, long amount) {
        if (amount > 0 && plugin.database().getCompany(uuid) != null) {
            plugin.database().setMetaLong("co_turn_" + uuid, totalTurnover(uuid) + amount);
        }
    }

    public long totalTurnover(UUID uuid) {
        return plugin.database().getMetaLong("co_turn_" + uuid, 0);
    }

    /** Steuer auf die Prämie (Ereignis Branchenförderung und Steuerabkommen wirken). */
    private long taxFor(UUID uuid, long amount) {
        return Math.round(plugin.stock().taxOn(uuid, amount) * plugin.events().wholesaleTaxFactor()
                * treaties.taxFactorFor(uuid));
    }

    private void notifyBonus(UUID to, long amount, String key, String sector, String from) {
        if (to == null || amount <= 0) {
            return;
        }
        plugin.database().logTx(to, plugin.database().getName(to), "grosshandel-bonus", amount, sector + " von " + from);
        Player online = org.bukkit.Bukkit.getPlayer(to);
        if (online != null) {
            plugin.messages().send(online, key, "amount", amount, "sector", sector, "player", from);
        }
    }

    // ------------------------------------------------------------------
    //  Gesellschafter und CEO
    // ------------------------------------------------------------------

    public UUID shareholder(int sector) {
        String id = SECTORS.get(sector).id();
        Database db = plugin.database();
        long most = db.getMetaLong("wh_bm_" + id, 0);
        long least = db.getMetaLong("wh_bl_" + id, 0);
        return most == 0 && least == 0 ? null : new UUID(most, least);
    }

    public boolean isShareholder(UUID uuid, int sector) {
        UUID s = shareholder(sector);
        return s != null && s.equals(uuid);
    }

    public UUID ceo() {
        long most = plugin.database().getMetaLong("wh_cm", 0);
        long least = plugin.database().getMetaLong("wh_cl", 0);
        return most == 0 && least == 0 ? null : new UUID(most, least);
    }

    public boolean isCeo(UUID uuid) {
        UUID c = ceo();
        return c != null && c.equals(uuid);
    }

    // ------------------------------------------------------------------
    //  Neue Aufträge: nur beim Betreten, höchstens einer pro Branche und Abwesenheit
    // ------------------------------------------------------------------

    private long resets() {
        return plugin.database().getMetaLong("wh_resets", 0);
    }

    public int maxOrders() {
        return Math.max(1, Math.min(32, plugin.getConfig().getInt("wholesale.max-open-orders", 32)));
    }

    /** Gibt dem Spieler pro freigeschalteter Branche einen neuen Auftrag, wenn seit seinem letzten Mal ein Shop-Wechsel war. */
    public void grantPending(Player player) {
        UUID uuid = player.getUniqueId();
        Database db = plugin.database();
        long mask = mask(uuid);
        if (mask == 0 || db.getMetaLong("wh_seen_" + uuid, 0) >= resets()) {
            return;
        }
        db.setMetaLong("wh_seen_" + uuid, resets());
        int added = 0;
        for (int s = 0; s < SECTORS.size(); s++) {
            if ((mask & (1L << s)) != 0 && treaties.mayGetOrders(uuid, s)) {
                for (int n = 0; n < treaties.ordersPerReset(uuid) && db.getWsOrders(uuid, s).size() < maxOrders(); n++) {
                    createOrder(uuid, s);
                    added++;
                }
            }
        }
        if (added > 0) {
            plugin.messages().send(player, "wh-new-orders", "count", added);
        }
    }

    @org.bukkit.event.EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        try {
            if (!plugin.timeline().previewFor(event.getPlayer())) {
                grantPending(event.getPlayer());
            }
            treaties.onJoin(event.getPlayer());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Großhandel: neue Aufträge konnten nicht vergeben werden: " + e);
        }
    }

    /** Täglich beim Shop-Wechsel: Zähler erhöhen (mehrere Wechsel in Abwesenheit zählen nur einmal), Wochenauswertung. */
    public void dailyReset() {
        try {
            plugin.database().setMetaLong("wh_resets", resets() + 1);
            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!plugin.timeline().previewFor(online)) {
                    grantPending(online);
                }
            }
            plugin.companies().dailyAi(); // KI-Mitarbeiter liefern vor der Wochenwertung
            plugin.expeditions().life().dailyWholesale(); // Begleiter liefern ebenfalls
            weeklyCheck();
            treaties.dailyReset(); // Verträge abrechnen, abgelaufene Eilaufträge löschen
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Großhandel-Tageswechsel fehlgeschlagen: " + e);
        }
    }

    /** Sonntags: Gesellschafter pro Branche, dann der CEO. Danach beginnt die Woche neu. */
    private void weeklyCheck() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        if (now.getDayOfWeek() != DayOfWeek.SUNDAY) {
            return;
        }
        long today = now.toLocalDate().toEpochDay();
        Database db = plugin.database();
        if (db.getMetaLong("wh_last_eval", -1) == today) {
            return;
        }
        db.setMetaLong("wh_last_eval", today);
        java.util.Map<UUID, Integer> seats = new java.util.HashMap<>();
        java.util.Map<UUID, Long> totalDone = new java.util.HashMap<>();
        for (int s = 0; s < SECTORS.size(); s++) {
            String id = SECTORS.get(s).id();
            String prefix = "wh_t_" + id + "_";
            UUID incumbent = shareholder(s);
            UUID best = null;
            long bestValue = 0;
            for (Map.Entry<String, Long> entry : db.getMetaPrefix(prefix).entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey().substring(prefix.length()));
                    long value = entry.getValue();
                    totalDone.merge(uuid, value, Long::sum);
                    if (value > bestValue || (value == bestValue && value > 0 && uuid.equals(incumbent))) {
                        bestValue = value;
                        best = uuid;
                    }
                } catch (IllegalArgumentException ignored) {
                    // kein Spieler-Eintrag
                }
            }
            if (best != null && bestValue > 0) {
                db.setMetaLong("wh_bm_" + id, best.getMostSignificantBits());
                db.setMetaLong("wh_bl_" + id, best.getLeastSignificantBits());
                seats.merge(best, 1, Integer::sum);
                Player online = org.bukkit.Bukkit.getPlayer(best);
                if (online != null) {
                    plugin.messages().send(online, "wh-shareholder-new", "name", SECTORS.get(s).name());
                }
            } else {
                db.setMetaLong("wh_bm_" + id, 0);
                db.setMetaLong("wh_bl_" + id, 0);
            }
            db.deleteMetaPrefix(prefix);
        }
        rewardCompanies(totalDone);
        UUID incumbentCeo = ceo();
        UUID ceo = null;
        int bestSeats = 0;
        long bestDone = -1;
        for (Map.Entry<UUID, Integer> entry : seats.entrySet()) {
            long done = totalDone.getOrDefault(entry.getKey(), 0L);
            boolean better = entry.getValue() > bestSeats || (entry.getValue() == bestSeats && done > bestDone)
                    || (entry.getValue() == bestSeats && done == bestDone && entry.getKey().equals(incumbentCeo));
            if (better) {
                ceo = entry.getKey();
                bestSeats = entry.getValue();
                bestDone = done;
            }
        }
        db.setMetaLong("wh_cm", ceo == null ? 0 : ceo.getMostSignificantBits());
        db.setMetaLong("wh_cl", ceo == null ? 0 : ceo.getLeastSignificantBits());
        if (ceo != null) {
            Player online = org.bukkit.Bukkit.getPlayer(ceo);
            if (online != null) {
                plugin.messages().send(online, "wh-ceo-new");
            }
        }
    }

    // ------------------------------------------------------------------
    //  Firmenrang
    // ------------------------------------------------------------------

    /** Firmen nach dem Umsatz dieser Woche, beste zuerst (nur Firmen mit Umsatz). */
    public List<Standing> companyBoard() {
        List<Standing> list = new ArrayList<>();
        for (Database.Company company : plugin.database().getCompanies()) {
            long value = weeklyTotal(company.owner());
            if (value > 0) {
                list.add(new Standing(company.owner(), value));
            }
        }
        list.sort((a, b) -> Long.compare(b.value(), a.value()));
        return list;
    }

    /** Rufstufe der Firma (0 bis 5) nach Gesamtumsatz. */
    public int reputation(UUID owner) {
        List<Long> steps = plugin.getConfig().getLongList("company.reputation-turnover");
        long total = totalTurnover(owner);
        int level = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (total >= steps.get(i)) {
                level = i + 1;
            }
        }
        return level;
    }

    /** Wie viel Prozent mehr Rang-Prämie die Rufstufe bringt. */
    public long reputationBonus(int level) {
        List<Long> bonus = plugin.getConfig().getLongList("company.reputation-bonus-percent");
        if (level <= 0 || bonus.isEmpty()) {
            return 0;
        }
        return bonus.get(Math.min(bonus.size(), level) - 1);
    }

    public long companyWins(UUID owner) {
        return plugin.database().getMetaLong("co_wins_" + owner, 0);
    }

    /** Rang der Firma in der letzten Wochenwertung (0 = nicht platziert). */
    public long companyLastRank(UUID owner) {
        return plugin.database().getMetaLong("co_last_" + owner, 0);
    }

    /** Sonntags: die besten Firmen der Woche bekommen eine Prämie (company.rank-prizes). */
    private void rewardCompanies(Map<UUID, Long> totalDone) {
        Database db = plugin.database();
        for (Database.Company company : db.getCompanies()) {
            db.setMetaLong("co_last_" + company.owner(), 0);
        }
        List<Standing> ranking = new ArrayList<>();
        for (Database.Company company : db.getCompanies()) {
            long value = totalDone.getOrDefault(company.owner(), 0L);
            if (value > 0) {
                ranking.add(new Standing(company.owner(), value));
            }
        }
        ranking.sort((a, b) -> Long.compare(b.value(), a.value()));
        List<Long> prizes = plugin.getConfig().getLongList("company.rank-prizes");
        for (int i = 0; i < ranking.size() && i < prizes.size(); i++) {
            UUID owner = ranking.get(i).uuid();
            long prize = Math.max(0, prizes.get(i));
            prize += prize * reputationBonus(reputation(owner)) / 100; // guter Ruf: mehr Prämie
            db.setMetaLong("co_last_" + owner, i + 1);
            if (i == 0) {
                db.setMetaLong("co_wins_" + owner, companyWins(owner) + 1);
            }
            // Ein Teil der Prämie fällt auf die Angestellten ab (nach den Items, die sie bisher abgegeben haben)
            long staffPart = 0;
            List<Database.Staff> team = db.getStaffOf(owner);
            long teamItems = 0;
            for (Database.Staff s : team) {
                teamItems += Math.max(0, s.items());
            }
            long staffPercent = Math.max(0, Math.min(100, plugin.getConfig().getLong("company.rank-staff-percent", 30)));
            if (prize > 0 && teamItems > 0) {
                staffPart = prize * staffPercent / 100;
                long paid = 0;
                for (Database.Staff s : team) {
                    long each = staffPart * Math.max(0, s.items()) / teamItems;
                    if (each <= 0) {
                        continue;
                    }
                    paid += each;
                    db.addCoins(s.member(), each);
                    db.logTx(s.member(), db.getName(s.member()), "firmenrang-anteil", each, "Anteil am Firmenrang " + (i + 1));
                    Player staffOnline = org.bukkit.Bukkit.getPlayer(s.member());
                    if (staffOnline != null) {
                        plugin.messages().send(staffOnline, "co-rank-staff-reward", "rank", i + 1, "prize", each);
                    }
                }
                staffPart = paid;
            }
            long ownerPrize = prize - staffPart;
            if (ownerPrize > 0) {
                db.addCoins(owner, ownerPrize);
                db.logTx(owner, db.getName(owner), "firmenrang", ownerPrize, "Firmenrang " + (i + 1));
            }
            Player online = org.bukkit.Bukkit.getPlayer(owner);
            if (online != null) {
                plugin.messages().send(online, "co-rank-reward", "rank", i + 1, "prize", ownerPrize);
            }
        }
        if (!ranking.isEmpty()) {
            Database.Company best = db.getCompany(ranking.get(0).uuid());
            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (unlocked(online)) {
                    plugin.messages().send(online, "co-rank-announce", "name", best == null ? "-" : best.name(),
                            "value", ranking.get(0).value());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Rangliste
    // ------------------------------------------------------------------

    public record Standing(UUID uuid, long value) { }

    /** Erledigte Aufträge dieser Woche in einer Branche, beste zuerst. */
    public List<Standing> board(int sector) {
        String prefix = "wh_t_" + SECTORS.get(sector).id() + "_";
        List<Standing> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : plugin.database().getMetaPrefix(prefix).entrySet()) {
            try {
                list.add(new Standing(UUID.fromString(entry.getKey().substring(prefix.length())), entry.getValue()));
            } catch (IllegalArgumentException ignored) {
                // kein Spieler-Eintrag
            }
        }
        list.sort((x, y) -> Long.compare(y.value(), x.value()));
        return list;
    }

    /** Alle im Großhandel Tätigen (mind. eine Branche), sortiert nach Aufträgen dieser Woche gesamt. */
    public List<Standing> activeBoard() {
        java.util.Map<UUID, Long> total = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : plugin.database().getMetaPrefix("wh_sec_").entrySet()) {
            try {
                total.put(UUID.fromString(entry.getKey().substring("wh_sec_".length())), 0L);
            } catch (IllegalArgumentException ignored) {
                // kein Spieler-Eintrag
            }
        }
        for (int s = 0; s < SECTORS.size(); s++) {
            for (Standing st : board(s)) {
                total.merge(st.uuid(), st.value(), Long::sum);
            }
        }
        List<Standing> list = new ArrayList<>();
        total.forEach((k, v) -> list.add(new Standing(k, v)));
        list.sort((x, y) -> Long.compare(y.value(), x.value()));
        return list;
    }

    /** In wie vielen Branchen ist der Spieler Gesellschafter? */
    public int seatsOf(UUID uuid) {
        int n = 0;
        for (int s = 0; s < SECTORS.size(); s++) {
            if (isShareholder(uuid, s)) {
                n++;
            }
        }
        return n;
    }

    public long weeklyTotal(UUID uuid) {
        long sum = 0;
        for (int s = 0; s < SECTORS.size(); s++) {
            sum += weeklyDone(uuid, s);
        }
        return sum;
    }
}
