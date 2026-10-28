package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Stamina;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Ausdauer: Vorrat, Kosten der drei Modi und die Stufe, die die Auffüllung steigert. */
public final class StaminaMenu extends Menu {

    private static Component t(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public StaminaMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Ausdauer"));
        Stamina st = plugin.stamina();
        UUID uuid = viewer.getUniqueId();
        int level = st.level(uuid);

        set(4, ItemUtil.icon(Material.GOLDEN_CARROT, t("Deine Ausdauer: " + Stamina.format(st.get(uuid)) + " von " + Stamina.format(st.max(uuid)), NamedTextColor.GOLD), List.of(
                t("Füllt sich auf: +" + Stamina.format(st.perHour(uuid)) + " pro Stunde (auch offline).", NamedTextColor.GREEN),
                t("Voll in " + (st.secondsUntilFull(uuid) == 0 ? "0 Min. (voll)" : Stamina.duration(st.secondsUntilFull(uuid))) + ".", NamedTextColor.GRAY),
                t("Der Preis wird beim Start abgezogen.", NamedTextColor.GRAY),
                t("Ohne genug Ausdauer kannst du nicht starten.", NamedTextColor.GRAY))), null);

        Stamina.Mode[] modes = Stamina.Mode.values();
        Material[] icons = {Material.IRON_PICKAXE, Material.DIAMOND_SWORD, Material.BEACON};
        String[] unit = {"pro Lauf", "pro Level", "pro Welle"};
        int[] slots = {11, 13, 15};
        for (int i = 0; i < modes.length; i++) {
            boolean ok = st.has(uuid, modes[i]);
            set(slots[i], ItemUtil.icon(icons[i], t(modes[i].label, NamedTextColor.GOLD), List.of(
                    t(Stamina.format(st.cost(modes[i])) + " Ausdauer " + unit[i], NamedTextColor.YELLOW),
                    ok ? t("Du kannst starten.", NamedTextColor.GREEN)
                            : t("Reicht in " + Stamina.duration(st.secondsUntil(uuid, modes[i])) + ".", NamedTextColor.RED))), null);
        }

        long price = st.upgradePrice(uuid);
        List<Component> up = new java.util.ArrayList<>();
        up.add(t("Stufe " + level + (st.maxLevel() == Integer.MAX_VALUE ? " (keine Obergrenze)" : " von " + st.maxLevel()), NamedTextColor.YELLOW));
        if (price < 0) {
            up.add(t("Höchststufe erreicht", NamedTextColor.GREEN));
        } else {
            up.add(t("Nächste Stufe: Höchstvorrat " + Stamina.format(st.max(uuid) + plugin.getConfig().getDouble("stamina.max-per-level", 10))
                    + ", +" + Stamina.format(plugin.getConfig().getDouble("stamina.per-hour-per-level", 0.5)) + " pro Stunde", NamedTextColor.GRAY));
            up.add(t("Preis: " + price + " Coins", NamedTextColor.GOLD));
            up.add(t("Klicken: kaufen", NamedTextColor.GREEN));
        }
        set(22, ItemUtil.icon(Material.EXPERIENCE_BOTTLE, t("Ausdauer steigern", NamedTextColor.GOLD), up), (p, c) -> {
            plugin.stamina().upgrade(p);
            new StaminaMenu(plugin, p).open(p);
        });

        set(27, backButton(), (p, c) -> new AdventureMenu(plugin, p).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
