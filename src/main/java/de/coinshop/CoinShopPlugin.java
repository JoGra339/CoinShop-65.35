package de.coinshop;

import de.coinshop.gui.MenuListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

/**
 * Das Hauptprogramm des Plugins. Der Server startet hier:
 * onEnable() läuft beim Einschalten, onDisable() beim Ausschalten.
 */
public final class CoinShopPlugin extends JavaPlugin {

    private Messages messages;
    private Database database;
    private ServerShopManager serverShop;
    private PlayerShopManager playerShop;
    private ChatPrompts prompts;
    private InvestmentManager investments;
    private RouteManager routes;
    private WholesaleManager wholesale;
    private CompanyManager companies;
    private BetManager bets;
    private ExpeditionManager expeditions;
    private WheelManager wheel;
    private WheelManager slots;
    private WheelManager scratch;
    private DoubleManager doubleGame;
    private LicenseManager licenses;
    private DragonFightManager dragonFight;
    private TimelineManager timeline;
    private PriceBook priceBook;
    private FarmWorldManager farmWorld;
    private TradeManager trade;
    private BlackMarketManager blackMarket;
    private DrugTrade drugs;
    private CreatureAuction creatureAuction;
    private MysteryBox mysteryBox;
    private Mascot mascot;
    private StockManager stock;
    private EventManager events;
    private CryptoManager crypto;
    private RevierManager reviere;
    private ProtectionManager protection;
    private AuctionManager auctions;
    private DailyWheel dailyWheel;
    private FlashEventManager flash;
    private AchievementRewards rewards;
    private RankingManager rankings;
    private SecretAchievements secrets;
    private TeleportManager teleports;
    private SleepManager sleep;
    private FreecamManager freecam;
    private CollectionManager collections;
    private BackupManager backups;
    private QuestManager quests;
    private LoginRewards logins;
    private PlaytimeAccount playtime;
    private MoneySupply moneySupply;
    private Stamina stamina;
    private MobHeadSale mobHeads;
    private TitleManager titles;

    @Override
    public void onEnable() {
        // Legt config.yml an, falls es sie noch nicht gibt (inkl. Plugin-Ordner)
        saveDefaultConfig();

        messages = new Messages(this);
        messages.load();

        try {
            database = new Database(new java.io.File(getDataFolder(), "coinshop.db"), getLogger());
        } catch (SQLException e) {
            getLogger().severe("Datenbank konnte nicht geöffnet werden: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        prompts = new ChatPrompts();
        licenses = new LicenseManager(this);
        investments = new InvestmentManager(this);
        routes = new RouteManager(this);
        wholesale = new WholesaleManager(this);
        companies = new CompanyManager(this);
        bets = new BetManager(this);
        expeditions = new ExpeditionManager(this);
        wheel = new WheelManager(this, "wheel", "wheel");
        slots = new WheelManager(this, "slots", "casino.slots");
        scratch = new WheelManager(this, "scratch", "casino.scratch");
        doubleGame = new DoubleManager(this);
        events = new EventManager(this);
        reviere = new RevierManager(this);
        protection = new ProtectionManager(this);
        auctions = new AuctionManager(this);
        dailyWheel = new DailyWheel(this);
        flash = new FlashEventManager(this);
        stock = new StockManager(this);
        rewards = new AchievementRewards(this);
        rankings = new RankingManager(this);
        secrets = new SecretAchievements(this);
        teleports = new TeleportManager(this);
        sleep = new SleepManager(this);
        freecam = new FreecamManager(this);
        collections = new CollectionManager(this);
        priceBook = new PriceBook(this);
        serverShop = new ServerShopManager(this);
        // Nach dem Serverstart: Rezepte sind geladen -> Preislisten schreiben
        getServer().getScheduler().runTaskLater(this, () -> {
            priceBook.reload();
            int missing = priceBook.writeFiles();
            getLogger().info("Preisliste geschrieben (preisliste.txt). Items nur mit Standardpreis: " + missing);
        }, 20L * 10);
        playerShop = new PlayerShopManager(this);
        backups = new BackupManager(this);
        quests = new QuestManager(this);
        logins = new LoginRewards(this);
        playtime = new PlaytimeAccount(this);
        moneySupply = new MoneySupply(this);
        stamina = new Stamina(this);
        mobHeads = new MobHeadSale(this);
        moneySupply.start();
        titles = new TitleManager(this);
        dragonFight = new DragonFightManager(this);
        timeline = new TimelineManager(this);
        farmWorld = new FarmWorldManager(this);
        getServer().getPluginManager().registerEvents(farmWorld, this);

        // Auf Ereignisse im Spiel reagieren (Beitreten, Erfolge, Menü-Klicks, Chat)
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new SecretListener(this), this);
        getServer().getPluginManager().registerEvents(sleep, this);
        getServer().getPluginManager().registerEvents(freecam, this);
        getServer().getPluginManager().registerEvents(titles, this);
        getServer().getPluginManager().registerEvents(dragonFight, this);
        dragonFight.start();
        getServer().getPluginManager().registerEvents(timeline, this);
        timeline.start();
        // Voice-Chat-Mod (falls vorhanden): Sculk-Sensoren und Monster reagieren auf Sprache
        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            try {
                if (!VoiceChatBridge.register(this)) {
                    getLogger().warning("Voice-Chat-Mod gefunden, aber ihr Dienst ist nicht verfügbar.");
                }
            } catch (Throwable t) {
                getLogger().warning("Voice-Chat-Anbindung konnte nicht gestartet werden: " + t);
            }
        }
        SafeZone safeZone = new SafeZone(this);
        getServer().getPluginManager().registerEvents(safeZone, this);
        safeZone.start();
        TabDeaths tabDeaths = new TabDeaths(this);
        getServer().getPluginManager().registerEvents(tabDeaths, this);
        tabDeaths.start();
        PhantomRules phantomRules = new PhantomRules(this);
        getServer().getPluginManager().registerEvents(phantomRules, this);
        phantomRules.start();

        // Befehle verbinden (die Namen stehen in plugin.yml)
        CoinCommands coinCommands = new CoinCommands(this);
        for (String name : new String[]{"coins", "pay", "baltop", "coinsadmin", "shopreset"}) {
            register(name, coinCommands, coinCommands);
        }
        ShopCommand shopCommand = new ShopCommand(this);
        register("shop", shopCommand, shopCommand);
        TeleportCommands teleportCommands = new TeleportCommands(this);
        for (String name : new String[]{"tp", "spawn", "sethome", "home", "tpa", "tpaccept", "tpdeny"}) {
            register(name, teleportCommands, teleportCommands);
        }
        trade = new TradeManager(this);
        getServer().getPluginManager().registerEvents(trade, this);
        register("handel", trade, trade);
        blackMarket = new BlackMarketManager(this);
        drugs = new DrugTrade(this);
        creatureAuction = new CreatureAuction(this);
        mysteryBox = new MysteryBox(this);
        getServer().getPluginManager().registerEvents(mysteryBox, this);
        getServer().getPluginManager().registerEvents(new WorldActivity(this), this);
        mascot = new Mascot(this);
        getServer().getPluginManager().registerEvents(blackMarket, this);
        for (String name : new String[]{"schwarzmarkt", "kopfgeld"}) {
            PluginCommand bm = getCommand(name);
            if (bm != null) {
                bm.setExecutor(blackMarket);
            } else {
                getLogger().severe("Befehl '" + name + "' fehlt in der plugin.yml!");
            }
        }
        getServer().getPluginManager().registerEvents(reviere, this);
        getServer().getScheduler().runTaskTimer(this, reviere::reminderTick, 600L, 600L);
        register("revier", reviere, null);
        register("schutzgeld", protection, protection);
        getServer().getPluginManager().registerEvents(auctions, this);
        register("auktion", auctions, null);
        register("gluecksrad", dailyWheel, null);
        getServer().getPluginManager().registerEvents(wholesale, this);
        register("grosshandel", wholesale, null);
        getServer().getPluginManager().registerEvents(bets, this);
        register("wetten", bets, null);
        getServer().getPluginManager().registerEvents(expeditions, this);
        register("expedition", expeditions, null);
        register("verlassen", expeditions, null);
        getServer().getScheduler().runTaskTimer(this, expeditions::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, expeditions::proxyTick, 10L, 10L);
        flash.start();
        register("buchhaltung", new Ledger(this), null);
        crypto = new CryptoManager(this);
        getServer().getPluginManager().registerEvents(crypto, this);
        register("krypto", crypto, null);
        ExtraCommands extraCommands = new ExtraCommands(this);
        for (String name : new String[]{"titel", "aufgaben", "profil", "lizenzen", "endkampf", "farmwelt", "termine", "fluestern", "heute", "ruhemodus", "umlauf", "ausdauer"}) {
            PluginCommand extra = getCommand(name);
            if (extra != null) {
                extra.setExecutor(extraCommands);
                extra.setTabCompleter(extraCommands);
            } else {
                getLogger().severe("Befehl '" + name + "' fehlt in der plugin.yml!");
            }
        }
        PluginCommand freecamCommand = getCommand("freecam");
        if (freecamCommand != null) {
            freecamCommand.setExecutor(new FreecamCommand(this));
        }

        // Kollektionen: alle 10 Sekunden die Inventare der Online-Spieler prüfen
        getServer().getScheduler().runTaskTimer(this, collections::scanAll, 200L, 200L);

        sleep.start();
        freecam.start();

        // Server-Shop beim Start prüfen und danach alle 30 Sekunden (Wechsel um 12:00 Uhr)
        serverShop.checkReset();
        getServer().getScheduler().runTaskTimer(this, serverShop::checkReset, 20L * 30, 20L * 30);

        // Spieler-Angebote bleiben dauerhaft
        playerShop.makeAllPermanent(); // Angebote laufen nie ab

        // Statistiken für die Ranglisten regelmäßig einlesen
        long syncTicks = 20L * rankings.syncSeconds();
        getServer().getScheduler().runTaskTimer(this, rankings::syncOnline, syncTicks, syncTicks);

        // Fledermaus durch das Fernglas ansehen (geheimer Erfolg): alle halbe Sekunde prüfen
        getServer().getScheduler().runTaskTimer(this, secrets::checkBatSightings, 10L, 10L);

        // Sicherungen der Datenbank
        backups.start();
        // Das Protokoll (Buchhaltung) wird dauerhaft gespeichert und nie aufgeräumt.

        // Tägliche Aufgaben: alle 20 Sekunden prüfen
        getServer().getScheduler().runTaskTimer(this, quests::tickAll, 20L * 20, 20L * 20);
        getServer().getScheduler().runTaskTimer(this, quests::pinTick, 20L * 5, 20L * 5);

        // Titel: Platz-1-Titel alle 5 Minuten, dauerhafte Titel jede Minute prüfen
        getServer().getScheduler().runTaskLater(this, titles::updateRankTitles, 20L * 10);
        getServer().getScheduler().runTaskTimer(this, titles::updateRankTitles, 20L * 60 * 5, 20L * 60 * 5);
        getServer().getScheduler().runTaskTimer(this, titles::checkPermanentAll, 20L * 60, 20L * 60);

        getLogger().info("CoinShop wurde gestartet.");
    }

    @Override
    public void onDisable() {
        if (quests != null) {
            quests.hideAll();
        }
        if (freecam != null) {
            freecam.stopAll(); // alle Freecam-Spieler zurück an ihren Ort
        }
        if (doubleGame != null && database != null) {
            expeditions.shutdown(); // Spieler aus der Arena zurückschicken
            doubleGame.cashOutAll(); // laufende "Doppelt oder Nichts"-Spiele auszahlen
        }
        if (database != null) {
            if (backups != null && backups.enabled()) {
                backups.run(); // letzte Sicherung beim Ausschalten
            }
            database.close();
        }
    }

    private void register(String name, org.bukkit.command.CommandExecutor executor,
                          org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Befehl '" + name + "' fehlt in der plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    /** Lädt config.yml und messages.yml neu (/coinsadmin reload). */
    public void reloadAll() {
        reloadConfig();
        priceBook.reload();
        messages.load();
        investments.reload();
        collections.reload();
        titles.reload();
    }

    public PriceBook priceBook() {
        return priceBook;
    }

    public Messages messages() {
        return messages;
    }

    public Database database() {
        return database;
    }

    public ServerShopManager serverShop() {
        return serverShop;
    }

    public PlayerShopManager playerShop() {
        return playerShop;
    }

    public BlackMarketManager blackMarket() {
        return blackMarket;
    }

    public DrugTrade drugs() {
        return drugs;
    }

    public MysteryBox mysteryBoxes() {
        return mysteryBox;
    }

    public Mascot mascot() {
        return mascot;
    }

    public CreatureAuction creatureAuction() {
        return creatureAuction;
    }

    public TradeManager trade() {
        return trade;
    }

    public ChatPrompts prompts() {
        return prompts;
    }

    public ExpeditionManager expeditions() {
        return expeditions;
    }

    public BetManager bets() {
        return bets;
    }

    public CompanyManager companies() {
        return companies;
    }

    public WholesaleManager wholesale() {
        return wholesale;
    }

    public RouteManager routes() {
        return routes;
    }

    public InvestmentManager investments() {
        return investments;
    }

    public WheelManager wheel() {
        return wheel;
    }

    /** Spielautomat (Casino). */
    public WheelManager slots() {
        return slots;
    }

    /** Farmwelt-Ziel. */
    public FarmWorldManager farmWorld() {
        return farmWorld;
    }

    /** Endkampf. */
    public TimelineManager timeline() {
        return timeline;
    }

    public DragonFightManager dragonFight() {
        return dragonFight;
    }

    /** Lizenzen. */
    public LicenseManager licenses() {
        return licenses;
    }

    /** Doppelt oder Nichts (Casino). */
    public DoubleManager doubleGame() {
        return doubleGame;
    }

    /** Rubbellos (Casino). */
    public WheelManager scratch() {
        return scratch;
    }

    public RevierManager reviere() {
        return reviere;
    }

    public AuctionManager auctions() {
        return auctions;
    }

    public DailyWheel dailyWheel() {
        return dailyWheel;
    }

    public FlashEventManager flash() {
        return flash;
    }

    public ProtectionManager protection() {
        return protection;
    }

    public EventManager events() {
        return events;
    }

    public CryptoManager crypto() {
        return crypto;
    }

    public StockManager stock() {
        return stock;
    }

    public TeleportManager teleports() {
        return teleports;
    }

    public CollectionManager collections() {
        return collections;
    }

    public FreecamManager freecam() {
        return freecam;
    }

    public RankingManager rankings() {
        return rankings;
    }

    public SecretAchievements secrets() {
        return secrets;
    }

    public BackupManager backups() {
        return backups;
    }

    public QuestManager quests() {
        return quests;
    }

    public MobHeadSale mobHeads() {
        return mobHeads;
    }

    public Stamina stamina() {
        return stamina;
    }

    public MoneySupply moneySupply() {
        return moneySupply;
    }

    public PlaytimeAccount playtime() {
        return playtime;
    }

    public LoginRewards logins() {
        return logins;
    }

    public TitleManager titles() {
        return titles;
    }

    public AchievementRewards rewards() {
        return rewards;
    }
}
