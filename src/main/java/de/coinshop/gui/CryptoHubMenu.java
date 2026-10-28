package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Unter "Krypto": die Kryptobörse und die Kryptominer. */
public final class CryptoHubMenu extends Menu {

    public CryptoHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.cryptohub.title"));
        Messages msg = plugin.messages();
        CryptoManager crypto = plugin.crypto();
        UUID uuid = viewer.getUniqueId();

        set(11, ItemUtil.icon(Material.AMETHYST_SHARD, msg.get("gui.cryptohub.exchange-name"),
                        msg.getList("gui.cryptohub.exchange-lore", "price", crypto.price(CryptoManager.KRYPTO),
                                "units", CryptoManager.fmt(crypto.holding(uuid, CryptoManager.KRYPTO).units()),
                                "events", plugin.events().activeText(viewer).isEmpty() ? "keins" : plugin.events().activeText(viewer))),
                (p, c) -> new CryptoMenu(plugin, p, CryptoManager.KRYPTO).open(p));
        set(15, ItemUtil.icon(Material.IRON_PICKAXE, msg.get("gui.cryptohub.miner-name"),
                        msg.getList("gui.cryptohub.miner-lore", "owned", crypto.ownedMiners(uuid),
                                "max", CryptoManager.MINERS)),
                (p, c) -> new MinerMenu(plugin, p).open(p));
        set(18, backButton(), (p, c) -> new InvestHubMenu(plugin, p).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
