package de.coinshop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mobköpfe verkaufen (im Kopfgeld-Menü): Zombie-, Skelett-, Creeper-, Piglin-, Wither-Skelett-Schädel und Drachenkopf.
 * Spielerköpfe gehören nicht dazu. Die Preise stehen in der config.yml unter blackmarket.bounty.mob-heads.
 */
public final class MobHeadSale {

    /** Welche Köpfe angenommen werden und was sie ohne Eintrag in der config bringen. */
    public static final Material[] HEADS = {
            Material.ZOMBIE_HEAD, Material.SKELETON_SKULL, Material.CREEPER_HEAD, Material.PIGLIN_HEAD,
            Material.WITHER_SKELETON_SKULL, Material.DRAGON_HEAD
    };
    private static final long[] DEFAULTS = {400, 400, 600, 500, 1500, 4000};

    private final CoinShopPlugin plugin;

    public MobHeadSale(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Wie viele Mobköpfe ein Spieler pro Tag höchstens verkaufen darf. */
    public int dailyLimit() {
        return Math.max(1, plugin.getConfig().getInt("blackmarket.bounty.mob-heads-daily-limit", 5));
    }

    private String usedKey(UUID id) {
        long day = ZonedDateTime.now(plugin.serverShop().zone()).toLocalDate().toEpochDay();
        return "mobhead_" + day + "_" + id;
    }

    /** Wie viele Köpfe hat der Spieler heute schon verkauft? */
    public int soldToday(Player player) {
        return (int) plugin.database().getMetaLong(usedKey(player.getUniqueId()), 0L);
    }

    public int remainingToday(Player player) {
        return Math.max(0, dailyLimit() - soldToday(player));
    }

    public long price(Material head) {
        for (int i = 0; i < HEADS.length; i++) {
            if (HEADS[i] == head) {
                return Math.max(0, plugin.getConfig().getLong("blackmarket.bounty.mob-heads." + head.name(), DEFAULTS[i]));
            }
        }
        return 0;
    }

    /** Wie viele Köpfe dieser Art liegen im Inventar? */
    public int count(Player player, Material head) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == head) {
                n += stack.getAmount();
            }
        }
        return n;
    }

    /**
     * Verkauft Köpfe dieser Art (head == null: alle Arten, die teuersten zuerst), aber höchstens so viele,
     * wie heute noch erlaubt sind. Gibt den Erlös zurück.
     */
    public long sell(Player player, Material head) {
        List<Material> kinds = new ArrayList<>();
        if (head == null) {
            for (Material m : HEADS) {
                kinds.add(m);
            }
            kinds.sort((a, b) -> Long.compare(price(b), price(a)));
        } else {
            kinds.add(head);
        }
        int left = remainingToday(player);
        long total = 0;
        int pieces = 0;
        for (Material kind : kinds) {
            if (left <= 0) {
                break;
            }
            int n = Math.min(count(player, kind), left);
            if (n <= 0) {
                continue;
            }
            player.getInventory().removeItem(new ItemStack(kind, n));
            total += n * price(kind);
            pieces += n;
            left -= n;
        }
        if (total <= 0) {
            return 0;
        }
        UUID id = player.getUniqueId();
        Database db = plugin.database();
        db.addCoins(id, total);
        db.setMetaLong(usedKey(id), soldToday(player) + pieces);
        db.logTx(id, player.getName(), "mobkopf-verkauf", total, pieces + " Mobköpfe verkauft");
        return total;
    }
}
