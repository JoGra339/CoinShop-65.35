# CoinShop – Coins, Server-Shop, Spieler-Shop, Glücksrad und Investitionen

Ein Plugin für **Paper 26.3** (Java 25).

## Was kann das Plugin?

| Befehl | Wirkung |
|---|---|
| `/shop` | Öffnet das Hauptmenü: **Server-Shop**, **Spieler-Shop**, **Glücksrad**, **Investitionen** (und zeigt das Shop-Konto) |
| `/shop sell [Preis]` | Bietet das Item in deiner Hand im Spieler-Shop an (Alternative zum Menü) |
| `/coins [Spieler]` | Zeigt deinen Kontostand oder den eines anderen Spielers |
| `/pay [Spieler] [Betrag]` | Überweist Coins an einen anderen Spieler |
| `/baltop` | Rangliste der 10 reichsten Spieler |
| `/coinsadmin give\|take\|set [Spieler] [Betrag]` | Coins verwalten (nur Admins) |
| `/coinsadmin resetshop` | Server-Shop sofort neu auslosen, ohne Einkommen (nur Admins) |
| `/coinsadmin reload` | `config.yml` und `messages.yml` neu laden (nur Admins) |

### Coins verdienen
- **Erfolge nach Schwierigkeit:** leicht 10 / mittel 30 / schwer 100 / sehr schwer 250 / extrem 600 Coins. Die Stufe richtet sich nach der Erfolgsart (Aufgabe/Ziel/Herausforderung); besonders schwere Erfolge (z. B. Enderdrachen besiegen, Wither, alle Mobs töten) stehen in `config.yml` unter `achievements.tiers`. Einzelne Erfolge lassen sich unter `overrides` anpassen.
- **Börse** (Investitionen -> Börse): Ein Kurs für alle (1-100 Coins), ändert sich täglich zusammen mit dem Shop-Wechsel. Man kauft mit beliebig vielen Coins ganze Anteile und verkauft sie später zum dann gültigen Kurs. 10 % Gebühr bei Kauf und Verkauf (`stock.fee-percent`), die geht aufs Shop-Konto. Kursgewinne sind neu geschaffene Coins, Verluste verschwinden.
- **Tägliches Einkommen:** 10 Coins für jeden Spieler (auch offline), ausgezahlt zusammen mit dem Shop-Wechsel um 12:00 Uhr. Dazu kommt das Einkommen aus Investitions-Sets. Diese Coins werden neu erzeugt.
- **Verkaufen** im Spieler-Shop, **Gewinnen** am Glücksrad.
- Kein Startguthaben.

### Server-Shop
- 10 zufällige Angebote, **feste Preise**, täglich um 12:00 Uhr (Europe/Berlin) komplett neu.
- Es können **alle Items auftauchen, die es im Vanilla-Survival gibt** (Items wie Command-Blöcke, Spawn-Eier, Bedrock usw. sind ausgeschlossen, siehe `exclude` in der config).
- Dazu **verzauberte Rüstungsteile** (12 % pro Platz) und **Verzauberungsbücher** (18 % pro Platz).
- Die seltensten Items kosten bis zu **100.000 Coins** (Netheritblock 100.000, Elytra 37.500 ...). Das Drachenei gibt es im Shop nicht, alle anderen sind daran angelehnt. Teure Items erscheinen seltener.
- Menge pro Kauf ist zufällig (billige Items in großen Mengen, teure einzeln). Jeder Spieler darf jedes Angebot 3x pro Tag kaufen.

### Spieler-Shop
- Spieler bieten Items zu einem frei gewählten Gesamtpreis an. Der Verkäufer bekommt die Coins sofort (auch offline), abzüglich Steuer.
- **Verkaufs-Slots müssen gekauft werden:** der erste Slot ist gratis, der nächste kostet 5 Coins, jeder weitere kostet das Doppelte (10, 20, 40 ...), höchstens 32 Slots. Kauf unter *Spieler-Shop > Meine Angebote*.
- **Schutz gegen Zwischenlager-Missbrauch:** Slots kosten Coins, Einstellgebühr (5 %, nie erstattet), Angebote laufen nach 48 h ab, Preisgrenzen, Verkaufssteuer, Sperrliste.

### Konto des Shops
Alle Einnahmen des Shops landen hier und werden im Hauptmenü angezeigt:
Käufe im Server-Shop, Einstellgebühren, Verkaufssteuern, Slot-Käufe, Glücksrad-Einsätze und Käufe von Investitions-Items.

### Glücksrad
- Eine Drehung kostet 5 Coins (einstellbar), der Einsatz geht aufs Shop-Konto.
- **Jackpot = das gesamte Shop-Konto**, Chance 1 zu 10.000.
- Gewinnstufen: 1 zu 10 = 5 Coins, 1 zu 20 = 10, 1 zu 50 = 50, 1 zu 100 = 100/200/500, 1 zu 1000 = 1000/1500/2000. Alles bezahlt vom Shop-Konto (höchstens so viel, wie darauf ist).
- Die Gewinnchancen sieht man im Menü. Alles ist in der `config.yml` einstellbar.

### Investitionen (Sets)
- **25 Sets mit je 3 Items.** Teuerstes Set: 1000 Coins pro Item und +1000 Coins Einkommen pro Tag. Das billigste kostet 100 pro Item und bringt +100 pro Tag. Dazwischen sinken die Preise gleichmäßig.
- Jedes Item gibt es **nur einmal** auf dem Server (wie Straßen bei Monopoly). Im Menü sieht jeder, wem welches Item gehört.
- 1 Item eines Sets bringt 10 % des Einkommens, 2 Items 35 %, das komplette Set 100 %.
- Items sind **übertragbar:** im Set-Menü auf dein Item klicken, Spielernamen in den Chat schreiben, mit `ja` bestätigen.

---

## So bekommst du die fertige Plugin-Datei (`CoinShop.jar`)

### Weg A: Automatisch auf GitHub bauen lassen (nichts installieren)
1. Öffne dein Repository und lade den **gesamten Inhalt dieser ZIP** hoch. Bestehende Dateien werden dabei ersetzt. Der Ordner `.github/workflows/build.yml` muss erhalten bleiben (hast du schon).
2. Der Bau startet automatisch unter dem Reiter **Actions**.
3. Beim fertigen (grünen) Durchlauf unten bei **Artifacts** `CoinShop` herunterladen. Darin liegt die `CoinShop.jar`.
4. Bei einem roten Durchlauf: Schritt **Run ./gradlew build** öffnen und die Zeilen mit `error:` kopieren.

### Weg B: Auf dem eigenen PC bauen
1. Java 25 installieren (z. B. Temurin von <https://adoptium.net>).
2. In diesem Ordner: Windows `gradlew.bat build`, Mac/Linux `./gradlew build`.
3. Die Datei liegt danach in `build/libs/CoinShop.jar`.

## Installation / Update auf dem Server (Serverminer)

1. Server stoppen.
2. Alte `CoinShop.jar` im Ordner `plugins` durch die neue ersetzen.
3. **Wichtig beim Update:** Lösche im Ordner `plugins/CoinShop/` die Datei **`config.yml`**, damit die neue (mit Preisen, Glücksrad und Sets) erzeugt wird. Die Datenbank `coinshop.db` bleibt erhalten und damit alle Kontostände. Falls du Texte in `messages.yml` angepasst hast, sichere sie vorher. Fehlende Texte werden automatisch ergänzt.
4. Server starten. Es sollte `CoinShop wurde gestartet.` in der Konsole stehen.
5. Zum Testen: `/coinsadmin give DeinName 5000`, dann `/shop`.

## Wichtig: Noch nicht getestet

Der Code wurde geschrieben und auf Tippfehler geprüft, aber **nicht kompiliert und nicht auf einem echten Server getestet**. Beim Bau können noch Fehlermeldungen auftauchen (z. B. weil ein API-Name in Paper 26.3 anders heißt). Besonders neu sind Verzauberungen (Rüstung und Bücher) und die Animation im Glücksrad. Fehlermeldung kopieren, dann wird es behoben.

## Projektaufbau (zum Nachlesen)

```
src/main/resources/
  plugin.yml          Steckbrief: Name, Befehle, Rechte
  config.yml          Einstellungen (Preise, Glücksrad, Slots, Einkommen, 25 Sets)
  messages.yml        Alle Texte (deutsch, änderbar)
src/main/java/de/coinshop/
  CoinShopPlugin.java       Start/Stopp, verbindet alles
  Database.java             Speichert alles in einer SQLite-Datei (coinshop.db)
  Messages.java / ItemUtil.java / ChatPrompts.java   Hilfsklassen (Texte, Items, Chat-Eingaben)
  PlayerListener.java       Beitreten, Erfolge -> Coins, Chat-Eingaben
  CoinCommands.java         /coins /pay /baltop /coinsadmin
  ShopCommand.java          /shop
  ShopItemGenerator.java    Lost die 10 Server-Shop-Angebote aus (Items, Rüstung, Bücher)
  ServerShopManager.java    Täglicher Wechsel um 12:00 Uhr, Kauf im Server-Shop
  PlayerShopManager.java    Slots, Einstellen, Kaufen, Abbrechen, Ablaufen, Abholen
  WheelManager.java         Glücksrad-Logik (Einsatz, Jackpot, Trostpreise)
  InvestmentManager.java    Sets, Besitz, Übertragung, tägliches Einkommen
  gui/                      Alle Menüs (Haupt-, Server-Shop, Spieler-Shop, Meine Angebote,
                            Abholung, Glücksrad, Investitionen, Set)
```


## Update auf Version 64.4

Nicht kompiliert und nicht getestet. Neue Datei: `gui/CompanionInfo.java`. Server: `config.yml` löschen (neu: `support.base-heal`, `charge-seconds` entfällt).

- **Duelle:** Rollen und Fähigkeiten wie im Level (Heilen, Fähigkeiten, Verteidiger); NPC-Gruppen taktisch (`CombatSim.npcTeam`). Duell und Arena sind gesperrt, wenn kein Begleiter einsatzbereit ist.
- **Menüs:** „Meine Begleiter“ und „Gefolge“ sind ein Menü. Alle Begleiter-Menüs zeigen alle Werte und was die Fähigkeit betrifft (`CompanionInfo`).
- **Keine Drops** in der Expeditionswelt, keine Jockeys und Reiter.
- **Unterstützer:** Heilung 4 Leben (zwei Herzen), wächst mit Level, Seltenheit und Eigenschaften.
- **Mystery Box Stufe 5:** Lizenzen, Miner, Reviere, Routen, Begleiter, KI-Plätze und neu Begleiter-Plätze ohne Wertgrenze; Coins, Aktien und Krypto bleiben bei 50.000.

## Update auf Version 65.34

Neues Menü „Mystery Boxen heute“ (im Box-Lager über den Kompass): zeigt, wie viele Boxen heute noch aus welcher Quelle erhältlich sind. Expeditions-Quellen haben eigene Tageslimits (`expedition.box-caps.*`) zusätzlich zum Gesamtlimit.
Neu: `gui/BoxSourcesMenu.java`. Geändert: `ExpeditionManager.java`, `gui/MysteryBoxMenu.java`, `config.yml`. `config.yml` muss nicht gelöscht werden.

## Update auf Version 65.33

Mystery Boxen aus Expeditionen sind pro Spieler und Tag begrenzt (`expedition.box-daily-cap`, Standard 5).
Geändert: `ExpeditionManager.java`, `config.yml`. Keine neuen Dateien. `config.yml` muss nicht gelöscht werden.

## Update auf Version 65.32

Begleiter haben ein festes Aussehen (auch Zombie-Dorfbewohner mit Beruf, Schafe, Pferde) und bekommen keine zufällige Rüstung oder Waffen mehr; Rüstung nur über das Level.
Geändert: `CompanionVariants.java`, `ExpeditionManager.java`. Keine neuen Dateien, keine Config-Änderung.

## Update auf Version 65.31

Die Duell-Bühne wird nicht mehr geflutet. Jeder Teil einer Prüfung hat mindestens einen Angreifer.
Geändert: `ExpeditionManager.java`, `LiveFight.java`. Keine neuen Dateien, keine Config-Änderung.

## Update auf Version 65.30

Wasserwesen ersticken in Expeditionen und auf der Duell-Bühne nicht mehr (Ertrinken und Austrocknen aufgehoben). Gegnerischen Allays kann man nichts in die Hand geben.
Geändert: `ExpeditionManager.java`, `LiveFight.java`. Keine neuen Dateien, keine Config-Änderung.

## Update auf Version 65.29

Duell-Bühne: Es kämpft immer die echte Kreatur (kein Zombie-Ersatz mehr). Wände und Decke bis 15 Blöcke, Flieger starten in der Luft und werden zum Gegner geschoben, bei Wasserwesen wird die Bühne flach geflutet, Ghast-Feuerbälle sprengen nichts.
Geändert: `LiveFight.java`. Keine neuen Dateien, keine Config-Änderung.

## Update auf Version 65.28

Gegnerische Allays heilen und segnen wie andere Gegner-Unterstützer, leuchten nicht, schweben tiefer (1 Block) und werden von Begleitern bevorzugt angegriffen. Nach einem Treffer sind sie 6 Sekunden langsam, regenerieren nicht und können nicht geheilt werden (`expedition.enemy-allay.hurt-seconds`).
Geändert: `ExpeditionManager.java`, `config.yml`. Keine neuen Dateien. `config.yml` löschen, `messages.yml` bleibt.

## Update auf Version 65.26

Begleiter-Plätze: jeder weitere kostet das 1,5-fache (`companion-slot-growth` 1,5). Neuer geheimer Erfolg „Schokolaaade!“ (Kakaobohne finden). Ruhemodus ist im Profil als Bett-Knopf (Feld 38) schaltbar. Neuer geheimer Erfolg „Ist Mayonnaise auch ein Instrument?“ (Notenblock platzieren).
Geändert: `ExpeditionManager.java`, `SecretAchievements.java`, `SecretListener.java`, `LoginRewards.java`, `gui/ProfileMenu.java`, `config.yml`, `messages.yml`. Keine neuen Dateien. `config.yml` und `messages.yml` löschen.

## Update auf Version 65.25

Begleiter-Plätze günstiger (erster Platz 1.500 Coins, Faktor 1,2), Allay geschwächt (Heilrate, Segen, Regenerations-Aspekt), gegnerische Unterstützer heilen nur Gegner vom selben Typ (höchstens 4 Heilplätze, +1 je 20 Expeditionslevel), Fähigkeiten geben 2 Erfahrung je Einsatz (`expedition.power.xp-per-use`).
Geändert: `CompanionAspects.java`, `CompanionPowers.java`, `ExpeditionManager.java`, `config.yml`. Keine neuen oder gelöschten Dateien. `config.yml` löschen, `messages.yml` bleibt.

## Update auf Version 65.24

Mobköpfe lassen sich nur begrenzt pro Tag verkaufen (`blackmarket.bounty.mob-heads-daily-limit`, Standard 5).
Geändert: `MobHeadSale.java`, `gui/MobHeadMenu.java`, `config.yml`. Keine neuen Dateien.

## Update auf Version 65.23

Im Kopfgeld-Menü lassen sich Mobköpfe verkaufen (Zombie, Skelett, Creeper, Piglin, Wither-Skelett, Drache).
Neue Dateien: `MobHeadSale.java` und `gui/MobHeadMenu.java`. Neuer Abschnitt `blackmarket.bounty.mob-heads` in der `config.yml` (optional, Standardwerte stehen auch im Code).

## Update auf Version 65.22

Neuer Befehl `/umlauf` zeigt allen Spielern den Geldumlauf (Gesamtsumme, Durchschnitt, Median, Verlauf, Reichste).
Neue Dateien: `MoneySupply.java` und `gui/MoneySupplyMenu.java`; im Hauptmenü unter „Info und Konto“ gibt es zusätzlich den Knopf „Geldumlauf“. `config.yml` und `messages.yml` bleiben unverändert.

## Update auf Version 65.21

- Preisleitern auf die Laufzeit abgestimmt (ca. 60.000 Coins in 2 Monaten pro Spieler): Mine-Läufe, Großhandel-Branchen, Reviere, Slots, Firma, Routen, Miner, Drogen-Labor.
- Expedition: Multiplikator-Preis ×1,6 pro Stufe, Begleiter halber Preis, weitere Exemplare ×1,5, Begleiter-Plätze ×1,3 (neue Config-Werte, Code geändert).
- Keine neuen Dateien. `config.yml` löschen, `messages.yml` darf bleiben.

## Update auf Version 65.20

- Wirtschaft zurück auf den Ursprung (Stand 65.15 = etwa 1000 Coins pro aktivem Tag). Die Skalierungen aus 65.16 bis 65.19 sind zurückgenommen, alle neuen Funktionen bleiben.
- Startguthaben: 100 + 100 pro Tag seit Serverstart, höchstens 3000.
- Keine neuen Dateien. `config.yml` löschen, `messages.yml` darf bleiben.

## Update auf Version 65.19

- Richtwert 1000 Coins pro aktivem Tag: Tagesaufgaben wieder auf Ursprungswerte (Bonus 200), Grundeinkommen 60, Login 15/250, Ranglisten 20/10/6/4/2, Startguthaben 500+100/Tag, Arenen wie früher, Item-Sets ein Viertel.
- Keine neuen Dateien. `config.yml` löschen, `messages.yml` darf bleiben.

## Update auf Version 65.18

- Letzte Einnahmen ×4: Kollektionen, Endkampf, Firmen-Rangliste, Gesellschafter/CEO, Mob-Funde, Arenen. Expeditionen/Mine, Item-Sets, Routen unverändert (in sich abgestimmt).
- Keine neuen Dateien. `config.yml` löschen, `messages.yml` darf bleiben.

## Update auf Version 65.17

- Feste Coin-Kosten etwa ×2: Lizenzen, Slots, Reviere, Firmen, Mine-Läufe, Großhandel, Wett-Einsätze, Casino (Gewinne mit), Lizenz „Rentner“ zahlt ×2, Gratis-Glücksrad ×4. Item-Preise unverändert.
- Keine neuen Dateien. `config.yml` löschen, `messages.yml` darf bleiben.

## Update auf Version 65.16

- Einnahmen, die nicht aus Items kommen, ×4: Tagesaufgaben, Grundeinkommen, Login-Serie, Erfolge, geheime Erfolge, Ranglisten-Belohnung, Startguthaben, Willkommen-zurück-Bonus.
- Keine neuen Dateien. `config.yml` löschen (sonst bleiben die alten Werte), `messages.yml` darf bleiben.

## Update auf Version 65.15

- Wirtschaft gemessen und geglättet: Slot-Preise verdoppeln sich nicht mehr bei jedem Kauf (Spieler-Shop 1,4-fach, Reviere 1,3-fach, KI-Plätze 1,25-fach, jeweils mit Höchstpreis).
- Keine neuen Dateien. Alte `config.yml`: `player-shop.slot-price-multiplier` auf `1.4` setzen (oder Datei löschen).

## Update auf Version 65.14

- Grundeinkommen-Ausgleich ohne Obergrenze; Ranglisten-Belohnung wieder 10/5/3/2/1.
- Willkommen-zurück-Bonus (`welcome-back`), Gnadentag (`login-streak-grace`), Spielzeit-Konto (`playtime-account`), Ruhemodus (`/ruhemodus`, `rest-mode`).
- Neue Datei: `PlaytimeAccount.java`. `config.yml` und `messages.yml` müssen nicht gelöscht werden.

## Update auf Version 65.13

- Wett-Schutz aus 65.12 entfernt; neu: höchstens 3 kleine Wetten pro Woche (`bets.small-max-per-week`).
- Nicht geschaffte Tagesaufgaben bleiben bestehen (`quests.carry-max`), ohne Bonus für „Alle Aufgaben“.
- Tiefenmine: ungenutzte Läufe werden gesammelt (`expedition.mine.bank-max`).
- Startguthaben wächst mit den Tagen seit Serverstart (`coins.start-bonus-per-day`, `coins.start-bonus-max`).
- Grundeinkommen-Ausgleich für die Ärmsten (`daily-income.catchup`).
- Aus 65.12 weiter enthalten: angeheftete Aufgabe, Revier-Einstellfenster (`gui/RevierListMenu.java`).
- Keine neuen Dateien. `config.yml` und `messages.yml` müssen nicht gelöscht werden.

## Update auf Version 65.12

- Aufgaben-Menü: eine Tagesaufgabe anklicken heftet sie als Leiste oben an (Fortschritt dauerhaft sichtbar).
- Reviere: neues Einstellfenster (`gui/RevierListMenu.java`, neue Datei) mit Mengenwahl bis zum ganzen Stapel und Preisvorschau (Händlerpreis, Gebühr, Kaufchance).
- Wett-Schutz gegen Coin-Schieben: keine gegenseitigen Wetten, Wochen-Höchsteinsatz pro Spielerpaar, Gewinn-Bremse (`bets.pair-weekly-max-stake`, `bets.pair-cooldown-weeks`).
- `config.yml` muss nicht gelöscht werden, `messages.yml` auch nicht.

## Update auf Version 65.11

Nicht kompiliert und nicht getestet. Neue Datei: `gui/TodayMenu.java`. Server: `config.yml` löschen (neue Werte: Ranglisten-Bonus, `kill-reward-factor`, Mine `slot-prices`); `messages.yml` bleibt.

- **Safe Zone:** Alle Kreaturen (auch friedliche, außer Dorfbewohner, Händler, Benannte und Gezähmte) werden aus der Zone entfernt; in der Zone kann niemand schlagen (Spieler, Tiere, Monster; Admins ausgenommen).
- **Titel:** Erdenbrecher wirkt jetzt auf alle Abbau-Aufgaben (Blöcke, Stein, Holz, Diamanterze, Antiker Schrott). Alle Titel-Wirkungen geprüft: jeder Typ aus der config wird irgendwo benutzt.
- **Ranglisten-Bonus** Platz 1 bis 5: 60, 40, 25, 15, 10 Coins pro Tag (vorher 10, 5, 3, 2, 1).
- **Coins pro Gegner** in Levels und Tiefenmine wie in der Verteidigung (`kill-reward-factor`, 0,05; skaliert mit dem Belohnungs-Multiplikator). Levels zahlen es mit dem Level aus, die Mine direkt in die Beute.
- **/flüstern Name Text** (auch /whisper): private Nachricht.
- **Tiefenmine:** Zusätzliche Läufe pro Tag kaufbar (bis 10 insgesamt, Preise `mine.slot-prices`). Admin: `/expedition minereset` setzt auch den Zähler zurück.
- **Großhandel-Begleiter:** zeigen +Erfahrung und Level-Up-Meldung bei jeder Lieferung (`wholesale.xp-per-day`).
- **/heute:** Menü mit dem, was offen ist (Aufgaben, Tiefenmine, Abenteuer, Großhandel).
- **Aufgeräumt:** toter Code entfernt (`CompanionPowers.shortText`, `CombatSim.dominantType`, Bühnen-Zwischenspeicher in `LiveFight`).

## Update auf Version 65.10

Nicht kompiliert und nicht getestet. Keine neuen Dateien, keine Änderung an `config.yml` und `messages.yml`.

- **Duelle ohne Kreaturen (zweites Duell):** Die Kampfbühne wird bei jedem Kampf neu gebaut (vorher wurde sie übersprungen, wenn schon gebaut). Fehlende oder verschwundene Kämpfer werden vor dem Kampfbeginn neu erzeugt und im Log gemeldet (`Live-Kampf (…): n Kämpfer waren verschwunden`). Ursache nicht sicher bestimmt; bei Wiederholung bitte Log-Zeilen „Live-Kampf (“ schicken.
- **NPC-Teams fix und taktisch:** Das Team eines NPC hängt nicht mehr vom Team des Spielers ab. Es ist immer gleich aufgebaut (Schildträger, Kämpfer, Heiler) und gegen die eigene Schwäche des NPC-Typs gewählt (`CombatSim.npcTeam`).

## Update auf Version 65.9

Nicht kompiliert und nicht getestet. Neue Datei: `gui/CompanyDissolveMenu.java`. Server: `config.yml` löschen (neue Mine-Werte); `messages.yml` bleibt.

- **Tiefenmine:** Der Schacht öffnet erst, wenn alle Gegner der Ebene besiegt sind (bei Wächter-Ebenen zusätzlich der Wächter). Kein Überspringen mehr. Deutlich schwerer: `mobs-base` 3, `mobs-per-depth` 1,0, `hp-per-depth` 0,12, `dmg-per-depth` 0,08, `mob-level-factor` 2,0. CoinMobs erscheinen mit der gleichen Chance wie in Expeditionen (`coin-mob-chance-percent`); ihr Wert zählt direkt zur Beute der Mine.
- **Gewerbe:** Knopf **Insolvenz anmelden** im Menü Mein Gewerbe (mit Rückfrage): Gewerbe, Angestellte und KI-Mitarbeiter werden gelöscht, Neugründung ist jederzeit möglich (`CompanyManager.dissolve`).
- **Großhandel:** Beliebig viele Begleiter dürfen Aufträge bearbeiten (`wholesale.base-slots` und `slots-per-slot` werden nicht mehr benutzt).

## Update auf Version 65.8

Nicht kompiliert und nicht getestet. Keine neuen Dateien, keine Änderung an `config.yml` und `messages.yml`.

- **Anzeige in Duellen und Arenen:** Unterstützer zeigen über dem Kopf beide Zähler (✚ Heilungen, ✦ Tierfähigkeit); die Actionbar-Summe ✦ enthält die Tierfähigkeits-Einsätze der Unterstützer (`CombatSim.Frame.animalA/animalB`).

## Update auf Version 65.7

Nicht kompiliert und nicht getestet. Keine neuen Dateien, keine Änderung an `config.yml` und `messages.yml`.

- **Tierfähigkeiten auch in Duellen und Arenen:** Unterstützer heilen (zwei Einsätze, ✚) und setzen zusätzlich ihre Tierfähigkeit ein (zwei eigene Einsätze, in `CombatSim.Team.animalUses`). Die Anzeige ✚ zählt nur die Heil-Einsätze.

## Update auf Version 65.6

Nicht kompiliert und nicht getestet. Keine neuen Dateien, keine Änderung an `config.yml` und `messages.yml`.

- **Tierfähigkeiten bleiben bei allen Begleitern**, auch bei Unterstützern (in Levels wie vorher; in Duellen heilen Unterstützer weiter). Die Begleiter-Info zeigt bei Unterstützern die Tierfähigkeit und zusätzlich das Heilen.

## Update auf Version 65.5

Nicht kompiliert und nicht getestet. Keine neuen Dateien, keine Änderung an `config.yml` und `messages.yml`.

- **Verteidiger werden immer zuerst angegriffen:** In Levels und der Verteidigung wählen Gegner jeden lebenden Verteidiger vor allen anderen Zielen (auch vor dem Spieler). In Duellen und Arenen treffen Angriffe und Fähigkeiten zuerst die Verteidiger, erst danach die übrigen (`CombatSim.pick`).

## Update auf Version 65.4

Nicht kompiliert und nicht getestet. Keine neuen Dateien, keine Änderung an `config.yml` und `messages.yml`.

- **Eine Rolle pro Begleiter:** Arten auf der Verteidiger-Liste (Kupfergolem, Kuh, Schwein, Gürteltier, Schildkröte, Pferd, Esel, Maultier, Kamel, Pilzkuh, Schreiter, Schnüffler, Skelettpferd, Zombiepferd, Glücklicher Ghast, Schwefelwürfel) sind jetzt Verteidiger und keine Unterstützer mehr. Echte Unterstützer heilen nur, ihre Tierfähigkeit ruht.
- **Verteidiger leveln durch Überleben:** Für jeden Gegner, der fällt, während der Verteidiger noch steht, bekommt er einen Kill (gutgeschrieben bei geschafftem Level, `creditDefenders`).
- Begleiter-Info: Unterstützer zeigen nur „heilt“, Verteidiger „überlebte Gegner“.

## Update auf Version 65.3

Nicht kompiliert und nicht getestet. Keine neuen Dateien, keine Änderung an `config.yml` und `messages.yml`.

- **Duelle:** Unterstützer heilen erst, wenn ein Verbündeter unter 70 % Leben ist, und mindestens 25 % seines Lebens (vorher schon ab 95 % und oft nur wenige Leben, so verpuffte der Einsatz). Geändert in `CombatSim.java`.
- Rollen geprüft: siehe Handout (Unterstützer sind alle Kreaturen, die als Gegner keinen Schaden machen).

## Update auf Version 65.2

Nicht kompiliert und nicht getestet. Keine neuen Dateien. Keine Änderung an `config.yml` oder `messages.yml`.

- **Inventar sortieren:** Das Schild ist jetzt ein normales Item (Platz „Zweite Hand“ im Sortiermenü) und lässt sich auf jeden Inventarplatz legen und zurück. Im Spiel sind Tausch mit F und Klicks auf die zweite Hand erlaubt, nur die Rüstung bleibt fest.
- **Levelbeginn:** Die Chatliste der Fähigkeiten ist wieder entfernt.
- **Mine:** Jede Ebene hat mindestens einen echten Angreifer (kein reiner Unterstützer-Trupp).

## Update auf Version 65.1

Nicht kompiliert und nicht getestet. Keine neuen Dateien. Server: `config.yml` und `messages.yml` löschen (neu: `mobs-base`, `mobs-per-depth`, `hp-per-depth`, `dmg-per-depth`; `start-factor` 0,025; `mob-percent` entfällt).

- **Mine:** Wächter erscheint garantiert (Ausgang öffnet erst nach seinem Tod), jede Ebene ist strikt schwerer (mehr Gegner, mehr Leben und Schaden), kurze Anzeige (Ebene, Coins, Zeit, Gegner, Zustand), Belohnungs-Multiplikator wirkt auch auf Erze.
- **Spitzhacke** ist in jedem Ausrüstungs-Set enthalten.
- **Fähigkeiten** der Begleiter stehen in Versteigerung und Verkauf.
- **Duelle und Arenen:** Anzeige der übrigen Fähigkeiten-Einsätze (✦) und Heilungen (✚) je Kämpfer und in der Actionbar; Diagnose-Log „Live-Kampf (“ und Schutz gegen Abbruch, falls keine Kämpfer erscheinen.

## Update auf Version 65.0

Nicht kompiliert und nicht getestet. Neue Dateien: `MineZones.java`, `gui/MineMenu.java`, `gui/MineExitMenu.java`. Server: `config.yml` und `messages.yml` löschen (neu: `expedition.mine.*`, Lizenz `bergbau`).

- **Tiefenmine:** Lizenz Bergbau, einmal pro Tag, Ebenen mit Erzadern und Schacht, Zonen alle 10 Ebenen, Wächter alle 10 Ebenen, Aufsteigen mit voller Beute oder Weitergehen, Tod = 50 % der Beute. Erze bringen Coins (kaum etwas auf den ersten Ebenen, steigend mit Tiefe und Spitzhacke). Begleiter wie in Expeditionslevels. Sonderfunde (Geode, Goldader, Fossil = Mystery Box). Rangliste „tiefste Ebene“.
- **Spitzhacke:** neue Ausrüstung `Gear.PICKAXE` (40 Stufen).
- **Admin:** `/expedition minereset <Spieler>` hebt die Tagessperre auf.

## Update auf Version 64.5

- Begleiter-Info gekürzt: Seltenheit/Typ/Rolle in einer Zeile, Leben und Schaden, Fähigkeit mit „Betrifft“ (Unterstützer: Heilmenge), Eigenschaften, Level, Gefolge-Kosten.

## Update auf Version 64.3

Korrektur: Das NPC-Duell-Menü öffnete sich nach dem Klick sofort neu und verdeckte so die Aufstellung (Duell und Turnier ließen sich nicht starten). Behoben in `gui/NpcDuelMenu.java`.

Nicht kompiliert und nicht getestet. Neue Datei: `gui/FormationMenu.java`. Keine Änderung an `config.yml` oder `messages.yml`.

- **Aufstellung wie bei Teamfight Tactics:** vordere und hintere Reihe (Menü `FormationMenu`). Die hintere Reihe wird kaum getroffen, solange vorne noch jemand steht; in der Kampfbühne stehen die Reihen hintereinander, die hintere hält zuerst die Stellung. Das Menü öffnet sich vor NPC-Duellen, Turnier und Arenakämpfen; für Spieler-Duelle gibt es den Knopf **Aufstellung** im Duell-Menü.

## Update auf Version 64.2

Nicht kompiliert und nicht getestet. Keine neuen Dateien. Server: `config.yml` und `messages.yml` löschen.

- **Duelle und Arenen:** Alle Kämpfer beider Seiten kämpfen gleichzeitig (`CombatSim.Frame` statt Abschnitte, `LiveFight` zeigt sie parallel). Menüs der Zuschauer werden geschlossen. Stationierte Arena-Wächter, die ausfallen, verteidigen nicht (`guardUnfit`).
- **Ersatz-Menü:** Gefallene, verletzte oder ruhende Begleiter blockieren keinen Platz mehr (`spareCopies`).
- **CoinMob und Mini-Boss** nur noch mit 8 % Chance pro Level (vorher 30 %).
- **Unterstützer:** zwei Heilungen mit fester Menge pro Level/Welle (`support.charges`, `charge-seconds`).
- **Verteidigung:** Kreaturen, die sich nicht erzeugen lassen, werden nachgelegt; Kills durch Feuer, Gift oder Fähigkeiten deiner Begleiter zählen für dich.

## Update auf Version 64.1

Nicht kompiliert und nicht getestet. Keine neuen Dateien. Server: `config.yml` und `messages.yml` löschen.

- **Levelkurve flacher:** `expedition.budget-early-growth` (1,5 statt 2 pro Level), `budget-exponent` 0,45, `hp-factor` 0,02, `dmg-factor` 0,015.
- **Belohnungen:** Jede einzelne Belohnung aus Mystery Box und Loot-Mob ist höchstens Stufe × 10.000 wert (`expedition.reward-value-per-tier`); `mystery-box.coin-caps` entfällt.
- **Gruppenstärke** statt Deckstärke in allen Texten.
- **Tägliches Turnier** läuft jetzt Runde für Runde als Zuschau-Kampf.
- **Kaufphase entfernt** (Code-Auslöser, Config `shop-phase`, Meldungen, Tipp).
- **Training:** Verletzte, ruhende und trainierende Begleiter werden im Menü „Gefolge“ mit Restzeit aufgelistet (`ExpeditionManager.unavailableLines`).

## Update auf Version 64.0

- **Menüs:** Expeditionsmenü neu: Abenteuer (Level, Verteidigung, Duelle, Arenen), Begleiter (mit Training), Ausrüstung usw. Söldner-Markt jetzt im Schwarzmarkt. Neue Klassen `AdventureMenu`, `DuelMenu`, `DuelChallengeMenu` (Spieler per Menü herausfordern), `TeamPickMenu`.
- **Live-Kämpfe:** Duelle, NPC-Duelle und Untergrund-Arenen laufen als Zuschau-Kampf (`LiveFight`). Arenen öffnen sich erst mit dem Revier.
- **Rollen:** mehr Verteidiger; Rollenfilter im Kaufmenü; Einsätze und Heilplätze im Menü; Lebensanzeige und Einsätze über den Kreaturen und in der Aktionsleiste.
- **Expedition:** Ereignisse viel seltener, Händler-Ereignis entfernt, Begleiter suchen selbst Gegner, keine Unsichtbarkeit, mehr Begleiter in der Verteidigung.
- **Mystery Boxen:** Coins aus Boxen begrenzt (`mystery-box.coin-caps`). Entfernt: Knöpfe „So funktioniert es“.

## Update auf Version 63.0

- **Drogenkunden** kaufen unterschiedliche Mengen (Karl 3, Lea 5, Ali 4, Mona 6, Ben 3, Dr. Vogt 5, Rico 9, Madame X 12 pro Tag, täglich ±30 % Schwankung). `drug.demand-base` ist jetzt ein Faktor (3 = normal). Das Kundenmenü zeigt „X von Y“.
- **Hauptmenü** neu geordnet: Schnellzugriff, Schwarzmarkt und drei Bereiche (neue Klasse `gui/AreaMenu.java`).
- **Expeditionsmenü** in Reihen gegliedert (Kämpfen, Vorbereiten, Begleiter-Extras, Info); Zurück links unten, Kontostand rechts unten.

## Update auf Version 62.0

Nicht kompiliert und nicht getestet. Neue Datei: `gui/ExpeditionRankMenu.java`. Server: `config.yml` und `messages.yml` löschen.

- **Kupfergolem** kommt jetzt auch als friedlicher Gegner vor.
- **Prüfungen:** Anzeige im Levelmenü; wer scheitert oder geht, macht 24 Stunden lang im erreichten Teil weiter (`exam.resume-hours`).
- **Anleitung im Spiel:** Tipps (einmal je Spieler, `expedition.tips.enabled`), `/expedition hilfe`, Knopf **Anleitung**.
- **Bestenliste** (`/expedition bestenliste`): höchstes Level, bestandene Prüfungen, beste Verteidigungswelle.

## Update auf Version 61.1

Nicht kompiliert und nicht getestet. Keine neuen oder gelöschten Dateien. Server: `config.yml` und `messages.yml` löschen.

- **Tribut:** Begleiter mit 5 bis 6 Sternen kosten ein geopfertes Exemplar, ab 7 Sternen zwei (`expedition.tribute.min-stars` 5, `two-from-stars` 7).
- **Schwefelwürfel** (Sulfur Cube): als Unterstützer kaufbar und als friedliches Ziel in Gruft-Arenen.

## Update auf Version 61.0

Nicht kompiliert und nicht getestet. Neue Dateien: `TrainingManager.java`, `MercenaryManager.java`, `gui/EventTraderMenu.java`, `gui/TrainingMenu.java`, `gui/TrainingPickMenu.java`, `gui/MercMenu.java`, `gui/MercOfferMenu.java`. Geänderte Menü-Datei: `gui/ExpeditionMenu.java` (jetzt 4 Reihen). Server: `config.yml` und `messages.yml` löschen. `training.yml` und `mercs.yml` entstehen von selbst.

- **Verteidiger** machen normalen Schaden (`defender.dmg-factor` 1.0).
- **Sichtbare Rüstung** Stück für Stück (Stiefel, Hose, Brustpanzer, Helm), Wölfe, Pferde, Lamas mit Rüstung und Sätteln in zufälligen Farben (`visual-armor.*`).
- **Unterstützer mit Schaden** (z. B. Killerkaninchen) kämpfen weiterhin mit. Neue Begleiter: Nautilus, Zombie-Nautilus, Parched, Kamel-Husk.
- **Wellen-Ereignisse** (`expedition.event.*`), **Prüfungen** alle 10 Level (`expedition.exam.*`), **Training** (`/expedition training`), **Söldner** (`/expedition soeldner`).
- Nicht umgesetzt (laut Auftrag): Level-Modifikatoren, Nebenziele.

## Update auf Version 60.0

Nicht kompiliert und nicht getestet. Neue Dateien: `CombatSim.java`, `NpcDuels.java`, `ArenaManager.java`, `gui/NpcDuelMenu.java`, `gui/ArenaListMenu.java`, `gui/ArenaMenu.java`, `gui/ArenaStationMenu.java`. Server: `config.yml` und `messages.yml` löschen.

- **Spawn-Sperre** bis zum Serverstart (Radius 150, `schedule.preview-radius`); Geheim-Erfolge Schalke04 und Mönchengladbach entfernt.
- **Biom-Arenen:** Typ, der zur Arena passt, +25 % Leben und Schaden (Begleiter und Gegner).
- **Rolle Verteidiger**, **sichtbare Rüstung** der Begleiter nach Level.
- **NPC-Duelle** (`/expedition npc`) und **Untergrund-Arenen** (`/expedition arena`, Zustand in `arenas.yml`).

## Update auf Version 59.0

Nicht kompiliert und nicht getestet. Keine neuen oder gelöschten Dateien. Server: `config.yml` und `messages.yml` löschen.

- **Brauaufgaben** (Tagesaufgabe „Tränke brauen“) erst nach Öffnung des Nethers.
- **Border** gilt schon vor der Testphase (`schedule.border-before-preview`).
- **Begleiter sofort fit machen:** Shift+Klick im Begleiter-Menü gegen Coins (`expedition.heal-now.*`).
- Geheime Erfolge Schalke04 und Mönchengladbach gab es schon (einmal bzw. zweimal sterben), unverändert.

## Update auf Version 58.0

Nicht kompiliert und nicht getestet. Keine neuen oder gelöschten Dateien. Server: `config.yml` und `messages.yml` löschen.

- **Verteidigung mit Freunden:** Mitspieler müssen vor dem Start eingeladen werden (Wartestube mit Einladungsmenü, Annehmen per Chat-Knopf oder `/expedition annehmen`, Starten durch den Gastgeber). Späterer Beitritt ist nicht möglich.
- **Mini-Boss:** kommt etwa so oft wie ein CoinMob, ist stärker als die Level-Mobs (`mini-boss-*` in der config.yml) und lässt eine Mystery Box Stufe 1 oder 2 fallen.
- **Mystery Boxen verkaufen:** an den Shop (`mystery-box.sell-prices`) oder als Gegenstand an andere Spieler (Rechtsklick löst ihn ein).
- **Drogenhandel** braucht die neue Schwarzmarkt-Lizenz `drogenhandel` (25.000 Coins).
- **Handouts** neu gestaltet, nur noch relevante Inhalte.

## Update auf Version 57.0

Nicht kompiliert und nicht getestet. Neue Datei: `gui/ExpeditionSubstituteMenu.java`. Server: `config.yml` und `messages.yml` löschen.

- **Fähigkeiten:** Einsätze pro Level/Welle wachsen mit dem Level (`expedition.power.levels-per-use` 10, `max-uses` 8), gelten auch für Gegner; in der Verteidigung nach jeder Welle zurückgesetzt, ebenso die Heilplätze. Anzeige am Namen: ✦ Fähigkeits-Einsätze, ✚ freie Heilplätze. Keine Unsichtbarkeits-Fähigkeiten mehr.
- **Unterstützer** leveln nur durchs Heilen, nicht durch Kills.
- **Gegner** zeigen ihr Level im Namen.
- **Ersatz-Menü** vor dem Level, wenn weniger Begleiter dabei sind als möglich (Ersatz wählen, weiter, verlassen).
- **Coin-/Loot-Mobs** sind jetzt Kreaturen des Levels (kein Extra-Spawn). Sound beim Levelabschluss leiser (Glockenton).
- **Arena unzerstörbar:** kein Feuer, keine Ausbreitung, keine Lava-Flüsse, keine Blockexplosionen.
- **Speer** zeigt jetzt einen echten Speer als Symbol.

## Update auf Version 56.1

Nicht kompiliert und nicht getestet. Keine neuen Dateien. Server: `messages.yml` löschen.

- **Verteidigung zu mehreren:** Wer fällt, schaut als Zuschauer bis zur nächsten Welle zu und spielt dann wieder mit. Erst wenn alle Spieler gefallen sind (oder der Kern fällt), ist es vorbei.
- **Ausrüstung:** Neu kaufbar: **Dreizack** (Wurfwaffe mit Treue, Spießen, ab Stufe 30 Entladung).

## Update auf Version 56.0

Nicht kompiliert und nicht getestet. Neue Datei: `gui/DefenseInviteMenu.java`. Server: `messages.yml` löschen.

- **Einladen per Menü:** In der Verteidigung öffnet der Gastgeber das Menü „Mitspieler einladen" mit Rechtsklick in die Luft (Kommandostab), Schleichen + Rechtsklick oder `/expedition einladen`. Der Eingeladene bekommt eine Chat-Nachricht mit Knopf **[Annehmen]** (oder `/expedition annehmen`), danach wählt er seine Begleiter. Die Einladung gilt 2 Minuten.

## Update auf Version 55.2

Nicht kompiliert und nicht getestet. Keine neuen Dateien. Server: `config.yml` löschen (neue Einstellung `expedition.support.levels-per-slot`).

- **Unterstützer:** Du selbst belegst einen ihrer Heilplätze. Die Zahl der Heilplätze steigt mit dem Level des Unterstützers (alle 5 Level ein Platz mehr).

## Update auf Version 55.1

Nicht kompiliert und nicht getestet. Keine neuen Dateien, `config.yml`/`messages.yml` müssen nicht neu angelegt werden.

- **Erfolge:** Jeder Erfolg wird in der Expedition nur einmal gemeldet (keine Dauer-Meldungen mehr, kein Chat-Text), keine Coins, Rücknahme am Ende.
- **Unterstützer** heilen dich und Mitspieler jetzt aus größerer Nähe (9 Blöcke) und kommen früher zu dir.

## Update auf Version 55.0

Nicht kompiliert und nicht getestet. Keine neuen oder gelöschten Dateien. Server: `config.yml` und `messages.yml` löschen.

- **Ausruhen:** Gefallene Begleiter ruhen bis zum nächsten Shopwechsel; im Menü steht, wie lange noch.
- **Erfolge:** In einer Expedition gibt es keine Erfolge (sofort zurückgenommen, keine Coins).
- **Unterstützer:** heilen höchstens 2 Begleiter ihres eigenen Typs pro Level (`expedition.support.max-same-type`).
- **Sniffer** graben nichts aus. **Verteidigung:** Bauverbotszone um den Kern ist markiert; Begleiter vorher wählbar; mit `/expedition verteidigung <Spieler>` können bis zu 4 Spieler gemeinsam spielen. **Spezialfähigkeit:** nur einmal pro Level.

## Update auf Version 54.0

Nicht kompiliert und nicht getestet. Neue Dateien: `CompanionPowers.java`, `CompanionVariants.java`. Server: `config.yml` und `messages.yml` löschen.

- **Level-Regeln:** frische Arena je Level, keine Befehle außer `/verlassen` (Level abbrechen), kein Handeln, keine Erfolge, kein Abbaumüdigkeit-Fluch, kein Schnee von Schneegolems. Smaragde als Utensil entfernt.
- **Verteidigung:** 60 Sekunden Vorbereitungszeit, Kommandostab liegt immer im Inventar.
- **Begleiter:** zufällige Varianten (Biom-Wolf usw.), einzigartige Fähigkeiten, typabhängige Aspekte, Unterstützer heilen Begleiter und leveln durchs Heilen, Aggro auf die ganze Arena, Creaking bewegt sich auch beim Ansehen, Magier und Hexen verletzen Spieler und Begleiter nicht mehr, gefallene Begleiter ruhen bis zum Shopwechsel.
- **Gegner:** nutzen dasselbe System (heilende Unterstützer, Fähigkeiten, Angriffe auf Begleiter).
- **Menü:** Team-Markierung, Sortieren und Suchen, Stärke bei Reisen. **CoinMob** statt Coins-Mob; Belohnung auch bei Kill durch Begleiter.
- **Belohnungen:** Investitionen als Beute deutlich kleiner, Kreaturenzahlen schwanken stärker.
- **Hauptmenü:** untere Reihe symmetrisch.

## Update auf Version 51.1

- **Mystery Boxen sind jetzt reine Menü-Einträge:** kein Item mehr. Lager im Profil, Knopf (Truhe) in der Ecke oben rechts; Linksklick öffnet eine Box, Shift-Klick bis zu 10. Neues Menü `gui/MysteryBoxMenu.java`.
- **Mob-Drops (seltener):** normale Mobs lassen selten und ganz selten Coins und Boxen fallen (`mystery-box.mob-drop`), Boxen nur als Chat-Meldung.
- **Begleiter in Boxen:** neue Belohnung im Loot (Seltenheit und Stärke nach Stufe), auch beim Expeditions-Loot-Mob.
- Auktions-Boxen landen beim Abholen im Box-Lager.
- config.yml und messages.yml auf dem Server löschen. Neu ist `gui/MysteryBoxMenu.java`.

## Update auf Version 50.0

- **Mystery Boxen:** Neues Item (Stufe 1 bis 5), Rechtsklick öffnet es; der Inhalt entspricht dem Loot-Mob (Coins, Aktien, Krypto, Lizenzen, Miner, KI-Platz, Schwarzmarkt-Dinge). Neue Klasse `MysteryBox.java`, Einstellungen unter `mystery-box`.
- **Quellen:** alle Tagesaufgaben (einfache Box), Loginserie alle 7 Tage (mit der Zeit bessere Stufe, `login-streak.box-tiers`), Casino als zweitseltenste Belohnung (`box-odds` bei Rad, Automat und Rubbellos), Gratis-Glücksrad (`daily-wheel.box-per-1000`), Wochenauktion (`auction.box-chance-percent`).
- Die Dateien für GitLab und Codeberg sind wieder entfernt, nur der Build über GitHub bleibt.
- config.yml und messages.yml auf dem Server löschen. Neu ist `MysteryBox.java`.

## Update auf Version 49.1

- **Verteidigung lohnt sich nur wenig:** `expedition.defense.reward-factor` ist jetzt 0,05 (vorher 0,6).
- **Umgebungsschaden nicht tödlich:** Gegner in der Verteidigung nehmen Fall-, Feuer-, Lava-, Ertrinkungs- und Explosionsschaden wieder an, sterben daran aber nie (1 Leben bleibt, keine Belohnung).
- **Unterstützer autonom:** Sie bewegen sich selbstständig und prüfen, ob Spieler oder Kern Heilung brauchen; dann kommen sie hin und heilen im Umkreis von 7 Blöcken.
- config.yml auf dem Server löschen; messages.yml unverändert, keine Quelldateien löschen.

## Update auf Version 49.0

- **Sklavenhandel (vorher Kreaturenhandel):** Der Knopf im Schwarzmarkt heißt jetzt *Sklavenhandel*. Es laufen mehrere Versteigerungen gleichzeitig: die tägliche Kreatur des Shops und **Angebote der Spieler**. Alle enden beim Shop-Wechsel. Bieten: Linksklick Mindestgebot, Rechtsklick +25 %, Shift-Klick doppelt.
- **Eigene Begleiter verkaufen:** Neuer Knopf im Sklavenhandel. Ein Begleiter (nicht unterwegs) kann **an den Shop für die Hälfte des Werts** verkauft oder **an Spieler versteigert** werden (Startgebot 50/100/150 % vom Wert, 5 % Gebühr, höchstens 3 Angebote, nicht in den letzten 30 Minuten vor dem Shop-Wechsel; ohne Gebot kommt er zurück). Einstellungen unter `creature-auction` (`sell-percent`, `level-bonus-percent`, `fee-percent`, `max-lots-per-player`, `min-minutes-before-reset`). Neue Datei `gui/CreatureSellMenu.java`, `CreatureAuction.java` und `gui/CreatureAuctionMenu.java` ersetzt, neues Kontoauszug-Arten `kreaturen-verkauf`, `kreaturen-shop`, `kreaturen-angebot`.
- **Verteidigung ohne Level:** Immer ab Welle 1, endlose Wellen, jede Welle schwerer (`defense.wave-hp-growth`, `wave-dmg-growth`), Pause zwischen den Wellen (`wave-pause-seconds`), kein Gesamt-Zeitlimit. **Jeder Kill durch Spieler oder Begleiter bringt Coins** (Wert der Welle = Level-Belohnung der Wellenzahl x `reward-factor`, verteilt nach Kreaturenkosten), **Auszahlung am Ende** (Kern gefallen, verlassen, abgemeldet). Beste Welle im Menü. Entfallen: Verteidigungs-Level, `core-hp-per-level`, `level-minutes`, `intact-bonus`.
- **Kein Töten durch Umgebung in der Verteidigung:** Gegner nehmen keinen Fall-, Feuer-, Lava-, Ertrinkungs-, Kontakt- oder Explosionsschaden; solche Kills gäbe es auch nicht als Belohnung. Die Wegprüfung beim Bauen verlangt, dass jede freie Stelle der Arena erreichbar bleibt. Heruntergefallene Gegner werden zum Kern gesetzt.
- **Angreifer und Unterstützer:** Alle Kreaturen sind eingeteilt, je nachdem ob sie Schaden verursachen können. **Unterstützer** (harmlose Tiere, Hilfsgeist, Katze, Schneegolem ...) kämpfen als Begleiter nicht, sondern **heilen** den Spieler und in der Verteidigung auch den Kern (`expedition.support.*`, `defense.support-core-factor`). In den Verteidigungs-Wellen kommen nur Angreifer vor. Die Rolle steht im Begleiter-Laden, in den Begleiter-Menüs, im Sklavenhandel und in der Levelvorschau.
- config.yml und messages.yml auf dem Server löschen. Es gibt keine Quelldateien zu löschen; neu ist `gui/CreatureSellMenu.java`.

## Update auf Version 48.0

- **Kreaturenhandel (Schwarzmarkt):** Neuer Knopf im Schwarzmarkt-Menü. Täglich wird eine Kreatur mit den schlechtesten Werten (Seltenheit Normal, nur Schwächen wie *Schwächlich*/*Faul*) bis zum Shop-Wechsel versteigert. Alle Spieler mit Schwarzmarkt-Zugang bieten; das Gebot wird sofort abgebucht, Überbotene bekommen es zurück, das Geld des Gewinners geht aufs Shop-Konto, der Gewinner bekommt die Kreatur beim nächsten Shop-Wechsel (auch offline). Klasse `CreatureAuction`, Zustand in `plugins/CoinShop/creature-auction.yml`, Einstellungen unter `creature-auction`.
- **Loot-Mobs seltener:** im Level `expedition.loot-mob.per-10000: 3` (etwa 1 von 3300 statt 1 von 1000), auf Reisen `loot-event-permille: 1` (vorher 3).
- **Meilensteine an Boss-Level gebunden und schwerer:** Die 24 Reiseziele werden durch das Schaffen eines Boss-Levels (Vielfache von 50) freigeschaltet: 50, 100, 150, 200, 300, 400, 500, 650, 800, 1000, 1250, 1500, 1800, 2100, 2500, 3000, 3500, 4000, 5000, 6000, 7000, 8000, 9000, 10000 (`expedition.trip.milestones`).
- **Verteidigung (Tower Defense) mit Begleitern:** Knopf *Verteidigung* im Expeditionsmenü oder `/expedition verteidigung`. In der Mitte der Arena steht ein Kern (`expedition.defense.core-block`, Standard Leuchtfeuer) mit Leben. Die Gegner laufen zum Kern und greifen ihn an (Schaden pro Sekunde = Angriffsschaden × `core-damage-factor`); Spieler und Begleiter bekämpfen sie. Die Begleiter stehen auf festen Posten (Ring um den Kern) und kämpfen nur in `post-range`; mit dem **Kommandostab** (Rechtsklick auf Begleiter, dann Rechtsklick auf einen Block) werden Posten verschoben. **Der Kern darf nicht zugebaut werden:** Im Umkreis `free-radius` um den Kern ist Bauen verboten, und jeder Block wird abgelehnt, wenn danach ein Spawn-Bereich am Rand nicht mehr zu Fuß mit dem Kern verbunden wäre (Flutfüllung `pathsOpen`; ein Block auf Bodenhöhe oder in 2 Blöcken Höhe sperrt die Stelle). Verteidigungs-Level zählen für sich (`ex_td_<uuid>`), sind aber nie höher als das nächste Expeditionslevel; Belohnung = Level-Belohnung × `reward-factor` (0,6) plus bis zu 50 % bei unversehrtem Kern. Keine Coins- und Loot-Mobs in der Verteidigung.
- config.yml und messages.yml auf dem Server löschen. Es gibt keine Quelldateien zu löschen; neu sind `CreatureAuction.java` und `gui/CreatureAuctionMenu.java`.

## Update auf Version 47.0

- **Freecam:** Reichweite 1000 Blöcke (`freecam.max-distance`). Die Sichtweite des Clients kann die Kamera praktisch früher begrenzen.
- **Begleiter-Einkommen nur über Reisen:** Die Arbeit entfällt (`expedition.work.*`). Es gibt jetzt **24 Reiseziele** mit 24 Meilensteinen (`expedition.trip.milestones`), das Reisemenü hat 6 Reihen.
- **Begleiter im Großhandel:** Einzelne Begleiter werden einem offenen Auftrag zugeteilt (Knopf *Begleiter zuteilen* im Großhandel-Menü, Knopf *Großhandel* im Begleiter-Menü) und liefern täglich beim Shop-Wechsel (`CompanionLife.dailyWholesale`, aufgerufen in `WholesaleManager.dailyReset`). Menge = Grundmenge × Level-Faktor × **Eignung** (Experte 1,6 / passt 1,0 / fachfremd 0,5) × Seltenheit × Eigenschaften; Einstellungen unter `expedition.wholesale`. Die Eignung jeder Kreatur zu den 10 Branchen steht in `CompanionLife.STRONG` und `FITS`. Roster-Feld `work` enthält jetzt `order` (Auftragsnummer) statt `job`.
- **Drogenhandel an die Reviere gebunden:** Jedes freigekaufte Revier ist ein Standort mit eigenem Labor, Lager, Dealern und Hitze; Stammkunden wohnen in bestimmten Revieren. Revierabgabe vom Erlös: `drugs.boss-cut-percent` (5 %) an den Unterhändler, `drugs.pate-cut-percent` (2 %) an den Paten; Umsatz zählt für die Revier-Rangliste (`addRevierSale`). Neues Menü `DrugSiteMenu`, die anderen Drogenmenüs haben einen Revier-Parameter. Daten (neues Format) in `plugins/CoinShop/drugs/<uuid>.yml`; alte Drogendaten aus 46.0 werden nicht übernommen.
- **Namen neutralisiert:** *Deck* heißt *Gefolge*, *Tribut* *Opfer*; keine Spielvorbilder mehr in Texten. Config-Schlüssel bleiben.
- Wirtschaft: Alle Zahlen sind Schätzwerte; bitte beobachten (Reise-Beute, Lieferungen der Begleiter, Drogenhandel-Preise).
- config.yml und messages.yml auf dem Server löschen.

## Update auf Version 46.0

- **Jeder Begleiter ist ein Einzelwesen:** eigene Nummer, eigenes Level, **Seltenheit** (Normal/Selten/Episch/Legendär, Gewichte `expedition.rarity-weights`) und **Eigenschaften** (Klasse `CompanionTraits`). Gewählt wird jedes Exemplar einzeln (Menü *Deck*). Daten: `plugins/CoinShop/companions/<uuid>.yml` (neue Felder `info`, `taken`, `gone`, `extra`, `work`, `rest`, `duel`). Alte Auswahlen (Anzahl je Art) werden beim ersten Öffnen auf die ersten Exemplare übertragen; alte Exemplare bekommen Seltenheit Normal ohne Eigenschaften.
- **Jede Kreatur außer dem Enderdrachen** ist als Begleiter kaufbar (etwa 90 Arten, Tabelle `Companion.TABLE` in `ExpeditionManager`; Arten, die es in der Paper-Version nicht gibt, werden übersprungen). Wesen ohne eigenen Angriff schlagen von Hand zu. Preise: `expedition.companion.<schlüssel>` überschreibt den Grundpreis (Schlüssel = englischer Name klein ohne Unterstrich, z. B. `irongolem`, `witherskeleton`).
- **Palworld:** *Arbeit* (Bergbau/Farm/Lager, passives Einkommen, `expedition.work.*`), *Zucht* (Kind erbt Eigenschaften und Erfahrung, `expedition.breed.*`), *Eigenschaften*. Kollektion bewusst nicht umgesetzt.
- **Yu-Gi-Oh:** *Fusion* (18 Rezepte in `CompanionLife.RECIPES`) und *Veredelung*, *Tribut* für Arten ab 8 Sternen (`expedition.tribute.*`), *Deck-Limit* in Sternen (`expedition.deck.*`), *Seltenheit*, *Duelle* mit `/expedition duell Spieler [Einsatz]` (`expedition.duel.*`).
- **Pokémon (nur Typen):** acht Typen mit Stärken/Schwächen (x1,5 / x0,7) im Level (Begleiter gegen Gegner und umgekehrt) und auf Reisen (Typ des Ziels). Klasse `CompanionTypes`.
- **Neu im Schwarzmarkt: Drogenhandel** (Knopf im Schwarzmarkt-Hauptmenü, Schedule-I-Ideen, alles nur im Schwarzmarkt): Labor mit 5 Stufen, Chargen, Qualitätsstufen und Mischen, 8 Stammkunden mit Treue, Dealer als Angestellte, Hitze und Razzien. Einstellungen im Abschnitt `drugs:` der config.yml, Daten in `plugins/CoinShop/drugs/<uuid>.yml`. Ausgaben gehen aufs Shop-Konto, Verkäufe erzeugen neue Coins.
- Neue Menüs: Begleiter-Hauptmenü mit neun Knöpfen, Laden (seitenweise), Exemplar-Menü für Deck/Sammlung/Reise/Arbeit/Zucht/Fusion/Tribut/Abwahl, Typen und Rezepte, fünf Drogenhandel-Menüs. Entfallen: die alten Menüs Begleiter-Level, Verwalten, Exemplare je Art, Abwahl und Reise-Auswahl.
- Wirtschaft: Alle Zahlen sind Schätzwerte und in der config.yml einstellbar; bitte nach den ersten Tagen beobachten (vor allem Arbeitsertrag `work.coins-per-hour` und die Drogenhandel-Preise).
- config.yml und messages.yml auf dem Server löschen.

## Update auf Version 45.0

- **Jedes Begleiter-Exemplar levelt einzeln** (Daten in `plugins/CoinShop/companions/<uuid>.yml`). Ins Level kommen die stärksten verfügbaren Exemplare mit, auf Reisen gehen die schwächsten.
- **Kills zählen nur bei geschafftem Level.** Wer scheitert oder die Expedition verlässt, verliert die Kills dieses Levels.
- **Aspekte:** Jede Begleiterart hat einen Aspekt für Expeditionslevel (Schaden, Schutz, Level-Coins, Leben pro Sekunde) und einen für Reisen (Kreaturen, Beute, Sicherheit, Coins-Mob-Chance, Loot-Mob-Chance, Erfahrung). Siehe Klasse `CompanionAspects`.
- **Begleiterreisen neu:** Dauer wählbar (`expedition.trip.durations-hours`), nicht mehr an den Shop-Wechsel gebunden. Das Ergebnis (Kreaturen, Coins-Mobs, Loot-Mobs, Verletzte, Erfahrung, Beute) steht beim Start fest und wird im Reisemenü beim Überfahren der Truhe vor dem Abholen angezeigt. Mitreisende bekommen Erfahrung mit `expedition.trip.xp-factor` (0,2).
- Neue Menüs: Exemplare einer Begleiterart einzeln ansehen (Begleiter-Level, Klick auf die Art).
- Frühere Reisen aus 44.0 werden nicht übernommen (neues Speicherformat); Kills aus 44.0 gehen verloren.
- config.yml und messages.yml auf dem Server löschen.

## Update auf Version 44.0

- **Begleiter-Plätze ohne Obergrenze:** Jeder Platz muss weiter freigekauft werden und kostet das Doppelte des vorherigen (`companion-max-slots` entfällt, der Preis ist überlaufsicher).
- **Abwahl-Menü:** Sind mehr Begleiter gewählt, als das nächste Level Kreaturen hat, erscheint vor dem Start (und bei „Weiter“) ein Zwischenmenü zum Abwählen. Die Auswahl gilt nur für dieses Level.
- **Begleiter-Level:** Jede Begleiterart sammelt Kills in Expeditionslevels (Level 2: 20 Kills, Level 3: 60, Level 4: 120 ...; `companion-max-level` 50, `companion-level-bonus` 0,08 = +8 % Leben und Schaden je Level). Der Name zeigt "Lv.".
- **Begleiterreisen:** Begleiter gehen bis zum nächsten Shop-Wechsel auf Reise. Elf Ziele, freigeschaltet durch Meilensteine (`expedition.trip.milestones`). Beute = Anteil (`yield-percent`, 10 %) am möglichen Loot bis zum Meilenstein, abhängig von der Stärke der Gruppe. Unterwegs können Coins- und Loot-Mobs auftauchen, schwache Gruppen riskieren verletzte Begleiter (bis zum übernächsten Shop-Wechsel nicht verfügbar). Begleiter auf Reisen kommen nicht in Level mit. Beliebig viele Begleiter pro Reise.
- **Neue Menüs:** Expeditionen > Begleiter öffnet ein Hauptmenü mit Kaufen, Mitnehmen, Begleiter-Level und Reisen.
- config.yml und messages.yml auf dem Server löschen.

## Update auf Version 43.3

- Enderdrachenkampf: nur noch **eine** Lebensleiste (die Leiste des Servers wird alle 2 Ticks versteckt).
- Begleiter: Das Kauflimit pro Begleiter (bisher 10) ist **aufgehoben**; jedes weitere Exemplar kostet weiter das Doppelte.
- Begleiter: Es kommen höchstens so viele mit, wie das **nächste Level Kreaturen** hat (und nie mehr als freigekaufte Plätze).
- Utensilien: Die Tränke im Menü und im Level sind **echte Tränke**.
- config.yml: `companion-max-each` entfällt. Beide Dateien (config.yml, messages.yml) auf dem Server löschen.

## Update auf Version 3.0
Lösche auf dem Server einmalig `plugins/CoinShop/config.yml` und `messages.yml` (die Datenbank bleibt erhalten), damit die neuen Einstellungen (Börse, Erfolgsstufen) erzeugt werden.

## Update auf Version 4.0
- **Server-Shop ausverkauft:** Jedes Angebot gibt es nur 3-mal insgesamt (`server-shop.stock-per-offer`). Sind alle 14 Angebote ausverkauft, werden sofort neue ausgelost (ohne Einkommen/Börsenkurs).
- **Ranglisten** (Hauptmenü): Diamanterze, Antiker Schutt, Holz, Stein, Angeln, Mobs, wenigste Tode, meiste Tode, Villager-Handel, gewanderte Blöcke, besiegte Bosse (Warden, Enderdrache, Elder Guardian, Wither), platzierte Blöcke und alle abgebauten Blöcke. Die Zahlen kommen aus den Minecraft-Statistiken (zählen also auch Dinge von vor dem Plugin). Die Top 5 bekommen +10/+5/+3/+2/+1 Coins Tageseinkommen pro Liste (`rankings.bonus`). "Wenigste Tode" zählt erst ab 120 Minuten Spielzeit.
- **Geheime Erfolge** (Hauptmenü): 11 Stück, sichtbar erst nach dem Freischalten. Belohnungen in `secret-achievements`, Texte in `messages.yml` unter `secret`.
- Nach dem Update `plugins/CoinShop/messages.yml` löschen (damit die neuen Texte kommen). Die `config.yml` ergänzt sich fehlende Teile selbst, wenn du sie aber neu erzeugen lässt, hast du alle neuen Werte sichtbar.

## Update auf Version 5.0
- **Schlafen:** Schlafen 10 % der aktiven Spieler (nicht AFK, nicht im Zuschauer-Modus) einer Welt, läuft die Nacht im Zeitraffer ab. Es wird nur die Tageszeit beschleunigt, nichts sonst (`sleep.*` in der config.yml).
- **Teleportieren:** `/tp spawn` (oder `/spawn`), `/sethome`, `/home`, `/tpa [Spieler]`, `/tpaccept`, `/tpdeny`. Jede Teleportation startet nach 3 Sekunden (`teleport.delay-seconds`). Der Spawn ist der Welt-Spawn (mit dem normalen Befehl `/setworldspawn` änderbar). Admins können `/tp` weiterhin normal benutzen.
- **Freecam:** `/freecam` (oder `/cam`) schaltet ein und aus. Du fliegst im Zuschauer-Modus, aber nicht durch Blöcke (nur durch Glas) und höchstens 64 Blöcke weit weg. Dein Körper bleibt als Figur stehen, trägt deine Rüstung, hat deine Lebenspunkte und wird von Monstern angegriffen. Stirbt die Figur, stirbst du an dieser Stelle.
- **Suche im Spieler-Shop:** Knopf mit Fernglas unten (Linksklick = Suchbegriff in den Chat schreiben, Rechtsklick = Suche löschen). Auch deutsche Wörter wie Diamant oder Schwert funktionieren.

## Update auf Version 6.0
- **Server-Shop:** 14 Angebote, Elytra und Drachenei gibt es dort nicht mehr.
- **Shop-Reset:** `/shopreset` (nur Admins; auch `/coinsadmin resetshop`) macht den kompletten Reset: neue Angebote, neuer Börsenkurs und das tägliche Einkommen wird ausgezahlt (genau wie um 12:00 Uhr). Ist der Shop nur ausverkauft, werden dagegen nur die Angebote neu gemischt.
- **Ranglisten:** "Mobs" heißt jetzt "Kreaturen" (alle Tiere, Monster, Dorfbewohner ...), neu ist die Rangliste "Spielzeit". Haben mehrere Spieler denselben Wert, teilen sie sich die Boni ihrer Plätze fair (ein Rest-Coin geht per Zufall an einen von ihnen).
- **Einkommens-Übersicht:** Investitionen > Uhr-Symbol. Zeigt für jeden Spieler das Einkommen (Grundeinkommen, Sets, Ranglisten) und den reichsten Spieler.
- **Kollektionen** (Hauptmenü): 13 Stück (Schallplatten, Rüstungsverzierungen, Keramikscherben, Köpfe, Netherit-Ausrüstung, Seltene Schätze, Rohstoffe, Farbstoffe, Wolle, Korallen, Blumen, Setzlinge, Fische). Wer alle Items einer Kollektion einmal besessen hat, bekommt die Belohnung. Eigene Kollektionen kannst du in der config.yml unter `collections` ergänzen.
- **Sets teurer:** Ein Item kostet jetzt das 20-fache des Set-Preises (teuerstes Set: 20.000 Coins pro Item). Das Einkommen bleibt gleich (`investments.price-multiplier`).
- **Glücksrad:** Eine Drehung kostet 10 Coins. Das Gewinnfeld bleibt nach der Drehung stehen und zeigt genau den ausgezahlten Betrag.
- **Geheimer Erfolg "Big Tasty Bacon":** Wird jetzt durch das Töten eines Zombifizierten Piglins freigeschaltet.

## Update auf Version 6.1
- **Neue geheime Erfolge:** grünen Teppich platzieren, zwei Frösche paaren.
- **Kreaturen-Kollektionen:** Monster der Oberwelt, Nether-Kreaturen, Bauernhoftiere, Wildtiere, Wasserwesen, Dorfbewohner und Helfer, Bosse. Eine Kreatur zählt, wenn du sie getötet oder durch ein Fernglas angesehen hast.

## Update auf Version 7.0
- **Investitionen mit Zwischenmenü:** Hauptmenü > Investitionen öffnet jetzt ein Menü mit drei Bereichen: **Item-Sets**, **Börse** und **Einkommen**. Von dort geht es mit dem Zurück-Knopf immer eine Ebene zurück.
- **"Anteile" heißen jetzt "Aktien"** (Börse, Texte in `messages.yml`).
- **Server-Shop Preise:** Schwache Rüstung ist günstiger (Leder 25, Kupfer 60, Gold 100, Kette 200, Eisen 150, Schildkrötenpanzer 300). Verzauberungen zählen bei schwachen Materialien nur anteilig (`server-shop.enchant-material-factor`, z. B. Leder 25 %, Eisen 60 %, Diamant/Netherit 100 %).
- **Verzauberte Werkzeuge und Waffen:** Schwerter, Äxte, Spitzhacken, Schaufeln, Hacken, Bögen, Armbrüste, Dreizacke, Streitkolben und Angeln können jetzt verzaubert im Server-Shop auftauchen (`server-shop.enchanted-tool-chance`).
- **Karten** gibt es nur noch einzeln und kosten 750 Coins (`server-shop.single-only`, `prices.MAP`).
- **Teurere Verzauberungen:** Hohe Stufen werden überproportional teurer (`enchant-level-exponent`). Verzauberungen, die es nicht am Verzauberungstisch gibt, sind besonders teuer: Windstoß 10.000, Schleichtempo 10.000, Seelenläufer 9.000, Frostläufer 8.000, Reparatur 6.000 (die günstigste davon) (jeweils pro Stufe I).
- **Neue Kollektion:** Bannermuster.
- Nach dem Update `plugins/CoinShop/messages.yml` UND `config.yml` löschen (neue Preise und Texte).

## Update auf Version 8.0
- **Tägliche Aufgaben** (Hauptmenü oder `/aufgaben`): Jeden Tag (Wechsel um 12:00 Uhr) gibt es 3 Aufgaben für alle, z. B. "Baue 300 Blöcke ab". Belohnung je nach Größe der Aufgabe, plus Bonus für alle drei. Pool und Werte: `quests` in der config.yml.
- **Login-Serie (Teil des Einkommens):** Wer täglich (12:00 bis 12:00 Uhr) auf dem Server ist, baut eine Serie auf. Sie gibt einen Serien-Bonus (+5 pro Tag bis +30, jeder 7. Tag +100), der zusammen mit dem Grundeinkommen beim Shop-Wechsel ausgezahlt wird. Beim Beitreten gibt es keine separate Auszahlung. Einstellungen: `login-streak`.
- **Startbonus:** Neue Spieler bekommen einmalig 100 Coins (`coins.start-bonus`).
- **Titel** (Hauptmenü oder `/titel`): Titel stehen in Chat und Tab-Liste vor dem Namen. Platz 1 jeder Rangliste und der Reichste bekommen einen Titel, solange sie Erster sind. Dauerhafte Titel gibt es für Leistungen (Wächter des Reiches, Hüter der Reliquien, Wissender der Schatten, Held ohne Rast, Drache auf dem Goldhort, Legende des Servers). **Mehrere Titel:** Man kann beliebig viele besitzen, trägt aber immer nur einen. Im Menü wählt man einen aus, "Automatisch" nimmt den wichtigsten, "Kein Titel" schaltet ihn aus. Verliert man den gewählten Titel (jemand überholt einen), wird automatisch gewechselt.
- **Admin-Werkzeuge:** `/coinsadmin stats` (Wirtschaft: Coins im Umlauf, Durchschnitt, Reichster, Umsätze der letzten 24 Stunden), `/coinsadmin log [Spieler] [Anzahl]` (letzte Coin-Bewegungen, dauerhaft gespeichert), `/coinsadmin backup` (Sicherung jetzt).
- **Sicherungen:** Die Datenbank wird beim Start, alle 6 Stunden und beim Ausschalten nach `plugins/CoinShop/backups` kopiert (die neuesten 20 bleiben). Zurückspielen: Server aus, Sicherung nach `plugins/CoinShop` kopieren und in `coinshop.db` umbenennen.
- **Mehr Erfolge und Kollektionen:** 1 neuer geheimer Erfolg (20 Blöcke Fall überleben) und 4 neue Kollektionen (Betten, Teppiche, Beton, Glasierte Terrakotta). Menüs für Kollektionen und geheime Erfolge sind größer.
- Nach dem Update `plugins/CoinShop/messages.yml` UND `config.yml` löschen.

## Update auf Version 9.0
- **Spieler-Profil:** `/profil` (eigenes) oder `/profil Name` (auch für Spieler, die offline sind). Im Hauptmenü unter "Mein Profil", außerdem per Klick auf einen Spielerkopf in der Einkommens-Übersicht. Zeigt Kontostand und Platz in der Reichenliste, Titel, Spielzeit, Login-Serie, Einkommen, Erfolge, Kollektionen, Aufgaben, Statistiken (abgebaut, platziert, gewandert, Kreaturen, Bosse, Angeln, Handel, Tode), Ranglisten-Platzierungen (Top 10) und Investitions-Items. Die Börse bleibt privat. Die Zahlen von Offline-Spielern stammen vom letzten Speichern (beim Verlassen des Servers).

## Update auf Version 10.0
- **Kollektionen:** Wolle, Teppiche und Beton sind weg. Neu: **Eimer-Varianten** (11 Eimer, 2.500 Coins) und **Alle Axolotl-Farben** (5 Farben, 4.000 Coins). Eine Axolotl-Farbe zählt, wenn du einen Axolotl-Eimer dieser Farbe im Inventar oder in der Endertruhe hattest oder einen Axolotl dieser Farbe durchs Fernglas gesehen/getötet hast. Die Belohnung der Netherit-Kollektion ist von 20.000 auf 10.000 Coins gesunken. (Wer Wolle/Teppiche/Beton schon belohnt bekommen hat, behält die Belohnung.)
- **Einkommen per Klick:** Ein Klick auf deinen Kontostand (in jedem Menü) zeigt, woraus dein tägliches Einkommen besteht: Grundeinkommen, Login-Serie, jedes Set und jede Rangliste mit Bonus, dazu die Summe.
- **Casino:** Im Hauptmenü gibt es jetzt das **Casino** statt des Glücksrads. Dort: **Glücksrad**, **Spielautomat** (20 Coins, drei gleiche Symbole) und **Rubbellos** (5 Coins, sechs Felder aufdecken, dreimal gleich gewinnt). Alle drei zahlen vom Shop-Konto, haben ein paar Trostpreise und einen Jackpot (ganzes Shop-Konto) mit Chance 1 zu 10.000. Preise und Chancen stehen in der config.yml unter `casino` (Spielautomat, Rubbellos) und `wheel` (Glücksrad).

## Update auf Version 11.0
- **Epische Titel:** Alle Titel haben neue Namen (config.yml, Abschnitt `titles`), z. B. "Diamantenfürst der Tiefe", "Henker der Nacht", "Schrecken der Meere", "Herr über Gold und Gier". "Märtyrer" bleibt.
- **Casino neu abgestimmt:** Glücksrad 25 Coins (Jackpot 1:15.000), Spielautomat 50 Coins (Jackpot 1:20.000, Gewinne bis 8.000), Rubbellos 5 Coins (Jackpot 1:12.000, kleine Gewinne). Im Schnitt kommen rund 55 % des Einsatzes zurück.
- **Neues Spiel "Doppelt oder Nichts":** Einsatz 10 / 50 / 250 wählen, dann immer wieder verdoppeln (jede Verdopplung klappt zu 40 %) oder auszahlen. Höchststufe nach 7 Verdopplungen (Einsatz x 128, wird automatisch ausgezahlt). Ab Stufe 3 gibt es bei einer Niederlage 50 % des Einsatzes als Trostpreis. Wer den Server verlässt, bekommt seinen aktuellen Gewinn automatisch. Einstellungen: `casino.double` in der config.yml.
- **4 neue Kollektionen** (jetzt 28): "Rudel der Wölfe" (alle 9 Wolfsarten, 6.000), "Samtpfoten aller Art" (alle 11 Katzenarten, 6.000), "Meister der Verzauberung" (jede Verzauberung einmal besessen, 20.000), "Bunte Tierwelt" (alle Papageien, Frösche und Kaninchen, 5.000). Wolf-, Katzen-, Papagei-, Frosch- und Kaninchen-Arten zählen, wenn du ein Tier der Art tötest oder durch das Fernglas ansiehst.
- **Phantome:** Sie erscheinen nur noch bei Vollmond (Oberwelt) und im End. Im End lässt das Plugin in der Nähe von Spielern welche erscheinen (`phantoms` in der config.yml).

## Update auf Version 12.0
- **Titel:** Alle Titel haben höchstens zwei Wörter (außer "Henker der Nacht"): Goldkaiser, Diamantenfürst, Höllenschürfer, Waldschreck, Felsflüsterer, Köder-Casanova, Unsterblicher, Märtyrer, Smaragdbaron, Sohlen-Sultan, Titanenbezwinger, Bob, Erdenbrecher, Dauerbrenner; dauerhaft: Treues Stück, Hamster, Geheimnisträger, Rastloser Held, Scheich, Serverlegende.
- **Lizenzen** (`/lizenzen` oder Hauptmenü): 12 Lizenzen, einmal kaufen, für immer behalten. Steuerfrei, Gebührenfrei (Spieler-Shop), Casino-Freispiel (1 Spiel/Tag gratis), Stammkunde (10 % Rabatt im Server-Shop), Rentner (+40 Coins/Tag), Marktstand (+4 Slots), Treuekarte (+50 % Login-Serien-Bonus), Investor (+10 % Einkommen aus Item-Sets), Börsenhändler (5 % Börsen-Gebühr), Ruhmjäger (+25 % Ranglisten-Bonus), Fleißbiene (+25 % Aufgaben-Belohnung), Sammelwut (+15 % Kollektions-Belohnung). Preise und Werte: `licenses` in der config.yml.
- **Endkampf:** Bis zum Datum in `end-fight.start` (config.yml) ist das End gesperrt. Wer ins End-Portal geht, bekommt ein Anmelde-Menü. Zur Startzeit werden alle Angemeldeten, die online sind, ins End teleportiert; der Drache wird mit Strahlen, Blitzen und Geräuschen beschworen. Je mehr Kämpfer, desto mehr Leben und Schaden und desto häufiger die Angriffe. Neue Angriffe: Feuerball-Salven, Blitzschläge (mit Warnung), Phantom-Schwärme und ein Schrei, der zurückwirft. Bei 50 % wird er wütend, bei 25 % ruft er Verstärkung. Danach ist das End offen; alle, die beim Tod im End sind, bekommen 5.000 Coins. Befehle: `/endkampf` (Status), `/endkampf abmelden`, für Admins `/endkampf start|stop|reset` (zum Testen). Wer das End trotzdem betreten muss: Berechtigung `coinshop.endbypass`.

## Update auf Version 12.1
- **/farmwelt** (auch /farm): Teleport zu einem festen Punkt, mindestens 100.000 Blöcke vom Spawn entfernt (100.000 bis 120.000, in zufälliger Richtung). Der Punkt ist für alle gleich und wechselt jeden Tag zusammen mit dem Shop-Wechsel (12:00 Uhr). Liegt er im Wasser, wird in der Nähe ein sicherer Platz gesucht. Einstellungen: `farmworld` in der config.yml.
- **Titel:** Treues Stück (Login-Serie), Hamster (Kollektionen), Scheich (Coins).
- **Lizenzen:** Glückspilz und Eilreise sind ersetzt durch **Investor** (+10 % Einkommen aus Item-Sets, 25.000) und **Ruhmjäger** (+25 % Bonus aus den Ranglisten, 20.000). Die Beschreibung von Langzeitmarkt nennt jetzt ausdrücklich den Spieler-Shop.


## Update auf Version 13.0

- **Spieler-Shop:** Angebote bleiben dauerhaft im Shop, bis sie verkauft oder zurückgenommen werden. Alte Angebote werden beim Start ebenfalls dauerhaft gemacht.
- **Lizenz Treuekarte** (12.000 Coins) ersetzt den Langzeitmarkt: +50 % auf den Login-Serien-Bonus.
- **Serverstart 6. Dezember 2026, 12:00:** Vorher können nur Admins (OPs) beitreten, alle anderen sehen den Countdown. Einstellbar unter `schedule:` in der config.yml.
- **Nether** öffnet am 10. Dezember 2026, 12:00 (Auslegung von "nach dem 9. Dezember"). Vorher sind Nether-Portale und Teleports gesperrt; am Portal öffnet sich ein Countdown-Menü. Zur Öffnung gibt es eine Meldung an alle. Ausnahme: Recht `coinshop.netherbypass`.
- **/termine** zeigt Countdown zu Serverstart, Nether und Endkampf.
- **Endkampf** jetzt am 20. Dezember 2026, 12:00 (`end-fight.start`).


## Update auf Version 14.0 (Meinecraft Winterzeit 2)

- **Titel umbenannt:** Bau-Titan heißt jetzt Bob, Treuer Titan wird Treues Stück, Sammelgott wird Hamster, Münzmagnat wird Scheich.
- **Marktstand:** Die 4 Slots kommen zusätzlich zu Gratis- und gekauften Slots. Der Preis der nächsten gekauften Slots ändert sich nicht, die Gesamtgrenze von 32 Slots gilt aber weiter.
- **Freecam:** Radius 100 Blöcke (waagerecht), keine Grenze in Höhe und Tiefe (`freecam.max-distance`).
- **Testphase ab 3. Dezember 2026, 00:00** bis zum Start am 6. Dezember, 12:00 (`schedule:` in der config.yml): Alle dürfen beitreten, aber nur im Umkreis von 500 Blöcken um den Spawn. Es spawnen keine Monster/Tiere, niemand nimmt Schaden oder stirbt, nichts kann abgebaut, gebaut, eingesammelt oder hergestellt werden. Es zählen keine Coins, Erfolge, Aufgaben, Login-Serie, Kollektionen und geheimen Erfolge. Das Startguthaben gibt es erst zum offiziellen Start. Admins (OPs) sind ausgenommen.
- **Server-Icon und Logo** liegen als Dateien bei (server-icon.png gehört in den Server-Hauptordner).


## Update auf Version 15.0

- **Safe-Zone:** Im Umkreis von 500 Blöcken um den Spawn der Hauptwelt kann dauerhaft nichts abgebaut werden (Blöcke, Eimer, Rahmen/Bilder). Explosionen, Feuer und Monster verändern dort keine Blöcke. Bauen bleibt erlaubt (`safe-zone.block-place: true` sperrt es ebenfalls). Admins sind ausgenommen. Einstellungen: `safe-zone:` in der config.yml.
- **Minecraft 26.3:** Neue Kollektion "Neuheiten 2026" (Cushions, Golden Dandelion, Straw Bed, Shelf Mushroom, Sulfur Cube). Einträge, die es in der Server-Version nicht gibt, werden einfach übersprungen. Das Kollektions-Menü hat jetzt Platz für 35 Kollektionen.
- **Logo:** kleine Herbstbäume als Anspielung auf den Dappled Forest.


## Update auf Version 16.0

- **Kollektionen:** "Neuheiten 2026" ersetzt die Kollektion Betten, es sind wieder 28 Kollektionen.
- **Safe-Zone dauerhaft wie in der Testphase:** Im Umkreis von 500 Blöcken um den Spawn kann nichts abgebaut oder platziert werden, nichts explodiert/brennt, keine neuen Monster oder Tiere spawnen (vorhandene Monster werden entfernt), Spieler nehmen keinen Schaden und werden nicht hungrig. Shop, Coins, Inventar und Truhen funktionieren normal. Admins dürfen bauen. `safe-zone.block-place: false` erlaubt das Bauen für alle.
- **Border in der Testphase:** Testphase-Spieler sehen eine Grenze (500 Blöcke um den Spawn). Sie ist nur für sie sichtbar und verschwindet zum offiziellen Start (spätestens 10 Sekunden danach).
- **Logo:** mit Herbstbäumen, Cushions, Schwefelblock und Golden Dandelion. Das Handout ist bunter und nutzt die Logo-Farben.


## Update auf Version 17.0

- **Server-Shop:** Neben normalen Items gibt es jetzt Item-Variationen (`server-shop.variant-chance`, Standard 20 % der Angebote): alle Trank-Arten als Trank/Wurftrank/Verweiltrank/Pfeil, alle Verzauberungsbücher in allen Stufen (auch Flüche), Ziegenhörner, Verdächtiger Eintopf, Ominous Bottles, Feuerwerk, Axolotl- und Tropenfisch-Eimer, Rüstung mit Besatz, gefärbte Lederrüstung. Preise unter `variant-prices` und `potion-base`.
- **Preise für jedes Item:** Neue Preisrechnung: config-Eintrag, sonst Zutaten (Rezept + 10 %), sonst Standardpreis. Viele natürliche Items und Drops haben jetzt eigene Preise. `/coinsadmin preise` schreibt `preisliste.txt` und `preise-fehlen.txt` in den Plugin-Ordner (passiert auch 10 Sekunden nach dem Start). Items in `preise-fehlen.txt` haben nur den Standardpreis.
- **Voice-Chat-Mod:** Sprache löst Sculk-Vibrationen aus, Monster hören Sprechende im Radius 40 und laufen zur Quelle. Flüstern und Schleichen sind leiser. Alles unter `voicechat:` in der config.yml. Die Mod muss auf dem Server liegen (Simple Voice Chat, softdepend).

## Update auf Version 18.0

- **Warden und Voice-Chat:** Der Wächter hört jetzt auch den Proximity-Voice-Chat. Je öfter du sprichst, desto wütender wird er (ab 80 Wut greift er an). Einstellbar unter `voicechat.warden` (an/aus, Hörradius 32, Wut pro Impuls 30). Schleichen und Flüstern verkleinern den Radius wie bei anderen Monstern.
- **Ausverkaufte Slots werden ersetzt:** Ist ein Angebot im Server-Shop ausverkauft (alle 3 Stück gekauft), kommt an dessen Platz sofort ein neues Item (kein Duplikat der anderen Angebote). Alle Spieler bekommen eine Meldung. Nur dieser Slot wird erneuert, die anderen bleiben.
- **Jedes Item hat einen Preis:** ca. 140 weitere Preisregeln in der `config.yml` (Kupfer, Karten, Beton, Bündel, Knospen usw.).
- Neue Nachricht `server-slot-replaced` in der `messages.yml`.

Update: `src`, `README.md` auf GitHub ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt).

## Update auf Version 18.1

- **Tode in der Tab-Liste:** Neben jedem Namen steht die Zahl der Tode (aus der Minecraft-Statistik, also auch frühere Tode). Abschaltbar mit `tab-deaths.enabled: false`.

## Update auf Version 22.0

- **Geheime Erfolge (jetzt 18, zusammen 680 Coins):**
  - **Emma?** (20 Coins): Eine Katze töten.
  - Die Texte von Schalke04 und Mönchengladbach lauten jetzt wörtlich "Spiel scheiße." und "Spiel richtig scheiße.".

## Update auf Version 22.1

- **Shulkerboxen im Server-Shop:** Jeder Angebotsplatz ist mit 7 % eine Shulkerbox (ungefärbt oder eine der 16 Farben, jede Farbe höchstens einmal gleichzeitig). Das sind im Schnitt etwa ein Angebot pro Tag. Einstellbar unter `server-shop.shulker-chance` (0 = aus). Der Preis steht bei `server-shop.prices` unter `"*SHULKER_BOX"` (5000 Coins).

Update: `src`, `README.md` ersetzen, `config.yml` auf dem Server löschen.

## Update auf Version 18.2

- **Fehler behoben:** Die Preisberechnung hat Zwischenergebnisse nicht gemerkt und den Server beim Start praktisch endlos beschäftigt (Status blieb auf "starting"). Jetzt wird jedes Item nur einmal berechnet.

Update: nur `src` auf GitHub ersetzen und neu bauen lassen.

## Update auf Version 19.0

- **7 neue Kollektionen (jetzt 35):** Alle Ziegenhörner (jeder Ruf einzeln), Feinschmecker (Essen), Werkzeuge und Waffen, Rüstungsteile, Redstone-Technik, Alle Holzarten, Braustand-Zutaten. Das Kollektionsmenü hat genau 35 Plätze, mehr geht nicht.
- **Meine Platzierungen:** Im Ranglisten-Menü gibt es einen neuen Knopf (Fernglas). Dort siehst du in allen 14 Ranglisten auf einen Blick deinen Platz, deinen Wert, den Tagesbonus und den Abstand zum Spieler vor dir.
- **Nether-Sperre repariert:** Das Menü blitzte nur kurz auf, weil Minecraft jedes Menü sofort schließt, solange man im Portal steht. Jetzt wird man erst ein Stück aus dem Portal geschoben und das Menü öffnet sich danach.
- **Endkampf:** Der Drache hat jetzt eine eigene Lebensanzeige (Balken mit genauen Lebenspunkten, wird bei 50 % gelb und bei 25 % rot). Die Beschwörung läuft über Glocken: alle 3 Sekunden ein Schlag, nach dem 10. Schlag erscheint der Drache. Einstellbar unter `end-fight.bells` und `end-fight.bell-interval-seconds`.

Update: `src` und `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt).

## Update auf Version 19.1

- **Kollektion Netherit-Ausrüstung ersetzt** durch "Lichtquellen" (Fackeln, Laternen, Glowstone, Froschlichter, Kerzen ...). Es bleiben 35 Kollektionen.
- **Endkampf:** Bis der Drache erscheint, sind nur die Glockenschläge zu hören (keine anderen Geräusche, kein Donner, der Drache ist stumm bis zum Erwachen).
- Neue Handouts für Spieler und Admins (als PDF).

Update: `src`, `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt).

## Update auf Version 19.2

- **Border testen:** Die Border um den Spawn (Radius 500) erscheint nur in der Testphase (ab 3. Dezember) und nur für normale Spieler, nicht für Admins. Neu: `/coinsadmin border` schaltet sie für dich als Admin zum Testen ein und aus (nur für dich sichtbar). Die Safe-Zone selbst gilt dauerhaft, auch ohne sichtbare Border.

Update: `src`, `README.md` ersetzen, auf dem Server `messages.yml` im Ordner `plugins/CoinShop` löschen.

## Update auf Version 20.0

- **Safe-Zone verkleinert:** Radius jetzt 150 Blöcke um den Spawn (vorher 500). Einstellbar unter `safe-zone.radius`. Die Testphase-Border (Bewegungsradius 500) bleibt unverändert, sie ist eine eigene Einstellung (`schedule.preview-radius`).
- `/coinsadmin border` zeigt Admins jetzt die Grenze der Safe-Zone (150 Blöcke).

Update: `src`, `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen.

## Update auf Version 21.0

- **Neue geheime Erfolge (jetzt 17):**
  - **Gegengamble** (50 Coins): Im Casino (Glücksrad, Spielautomat, Rubbellos, Doppelt oder Nichts) verlieren und innerhalb von 60 Sekunden direkt wieder spielen. Die Zeit stellst du unter `secret-achievements.gegengamble-seconds` ein.
  - **Schalke04** (10 Coins): Einmal sterben.
  - **Mönchengladbach** (15 Coins): Zweimal sterben. Es zählt die Minecraft-Statistik "Tode".
- **TNT-Duper:** Das Plugin stört TNT-Duper nur innerhalb der Safe-Zone (150 Blöcke um den Spawn). Außerhalb funktionieren sie. Die nötigen Einstellungen stehen in `config/paper-global.yml` unter `unsupported-settings`: `allow-piston-duplication: true` und bei Bedarf `allow-headless-pistons: true`. Danach Server neu starten.

Update: `src`, `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt).

## Update auf Version 23.0

- **Teleport-Anfragen:** Im Chat erscheinen anklickbare Knöpfe [ANNEHMEN] und [ABLEHNEN].
- **Neuer geheimer Erfolg (jetzt 19):** "Warmer Vanilletee" (20 Coins, leicht): einen Trank der Sprungkraft brauen. Neu auch "Emma?" (Katze töten).
- **27 Item-Sets** (neu: Schäferei, Kristallhöhle).
- **14 Lizenzen mit Stufen:** Neu "Glückspilz" (Bonus bei Casino-Gewinnen) und "Erfolgsjäger" (Bonus-Coins bei Erfolgen). Fast alle Lizenzen lassen sich in bis zu 3 Stufen verbessern (`levels:` in der `config.yml`).
- **Endkampf:** Nur noch eine Lebensleiste für den Drachen. Die Belohnung wird nach Schaden am Drachen und zerstörten Kristallen aufgeteilt; jeder Überlebende bekommt mindestens 1.000 Coins (`end-fight.reward-min`, `reward-pool-per-player`, `reward-damage-weight`, `reward-crystal-weight`).
- **Farmwelt-Spot geschützt:** Im Radius `farmworld.protect-radius` (12) um den heutigen Spot kann niemand bauen, abbauen, Eimer leeren oder sprengen (Admins ausgenommen). Keine Fallen mehr.
- **Handel (`/handel Spieler`, auch `/trade`):** Beide Spieler legen Coins und/oder Set-Items ins gemeinsame Fenster. Jeder sieht das Angebot des anderen. Der Handel passiert nur, wenn beide zustimmen; jede Änderung setzt die Zustimmung zurück. Alles läuft in einer Datenbank-Transaktion (ganz oder gar nicht). Einstellungen: `trade.request-seconds`, `trade.session-seconds`.

Update: `src`, `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt). Hinweis: Der Code wurde nicht kompiliert oder getestet.

## Update auf Version 24.0

- **Schwarzmarkt (`/schwarzmarkt`, `/sm`, auch im Hauptmenü):** Eine eigene Oberfläche im Shop, freigeschaltet über die Lizenz **Schmuggler** (nur 1.000 Coins, kaufbar im normalen Lizenz-Menü). Jeden Tag (mit dem Shop-Wechsel um 12:00) gibt es **10 verschiedene, wertvolle Items**, von jedem nur **ein Stück**. Man kann so viele kaufen, wie man will. Preis: 45 % des normalen Werts. Grundchance, dass die Ware ankommt: **60 %** (plus Schmuggler und Glückspilz). Bei Pech sind die Coins weg, das Item bleibt im Laden. Keine Steuer, nur eine Gebühr (12 %), die aufs Shop-Konto geht.
- **Schmutzige Geschäfte (Schwarzmarkt-Lizenzen):** Nur im Schwarzmarkt unter "Schmutzige Geschäfte" sichtbar, nicht im normalen Lizenz-Menü. Dort verbessert man auch den **Schmuggler** (Stufe 1 bis 5, +2 bis +10 % Kaufchance). Dazu: **Hehler** (10 bis 50 % mehr Erlös beim Verkauf), **Hintertür** (5 bis 25 % Rabatt), **Bestechung** (20 bis 100 % der Gebühren erlassen), **Kopfgeldjäger** (10 bis 50 % mehr beim Kassieren), **Schwarzlager** (1 bis 5 zusätzliche Verkaufs-Slots). Alle Stufe 1 bis 5. Die anderen fünf brauchen zuerst den Schmuggler. Im normalen Lizenz-Menü gibt es 15 Lizenzen (14 normale und den Schmuggler zum Kaufen).
- **Verkaufen an den Händler:** Bis zu **32 Slots**. Items aus der Hand zu festen, schlechten Preisen (25 % des Werts) einstellen. Beim täglichen Wechsel kauft der Händler jedes eingestellte Item mit 20 % Chance. Der erste Slot ist gratis, weitere werden Stück für Stück gekauft (300 Coins, jeder weitere 20 % teurer, `slot-price-factor`).
- **Kopfgelder (`/kopfgeld`):** Nur sichtbar und nutzbar mit der Lizenz Schmuggler, auch zum Kassieren. Coins auf einen Spieler aussetzen (mindestens 500, 10 % Gebühr obendrauf). Wer ihn tötet, kassiert alle Kopfgelder auf ihn. Auftraggeber bleibt anonym. Zurückziehen möglich (Gebühr weg), Verfall nach 14 Tagen.
- **Normale Lizenzen wirken auch im Schwarzmarkt:** Stammkunde (Rabatt), Gebührenfrei (keine Gebühren), Glückspilz (Kaufchance +Wert/2).
- **Endkampf:** Der Drache macht pro Spieler mehr Schaden (jetzt 20 % pro weiterem Kämpfer, höchstens 400 %). Gilt für Biss, Feuerbälle, Drachenatem und Blitze.

Update: `src`, `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt). Der Code wurde nicht kompiliert oder getestet.

## Update auf Version 25.0

- **Aktienbörse und Krypto-Börse:** Die alte Börse heißt jetzt **Aktienbörse**. Daneben gibt es unter Investitionen die **Krypto-Börse** (`/krypto`, auch `/crypto`). Der Kurs von **Krypto** schwankt viel stärker (zwischen 1 und 10.000 Coins) und ändert sich täglich zum Shop-Wechsel. Kauf per festem oder eigenem Betrag, Verkauf alles oder eigene Menge (z. B. `1,5`). Gebühr wie an der Aktienbörse. Einstellungen unter `crypto:` in der `config.yml`.
- **Krypto-Miner:** Bis zu **32 verschiedene Miner**, nacheinander zu kaufen, **jeder doppelt so teuer wie der davor** (`crypto.miner.base-price` 2.000, `price-factor` 2,0) und bis **Stufe 10** verbesserbar. Jeder Miner gibt als Quest eine Liste **verschiedener Blöcke** vor (je Sorte einmal abbauen; selbst gesetzte Blöcke zählen nicht). Sind alle abgebaut, gibt es Krypto. **Je höher die Stufe, desto weniger Blöcke.** Pro Tag nur **eine** Miner-Quest.
- **Tagesereignisse:** Zum Shop-Wechsel passiert mit **50 %** (`events.chance-percent`) eines von **10 Ereignissen**: Großer Ausverkauf (Preise -20 % im Server-Shop und Schwarzmarkt), Dividenden-Tag (Item-Set-Einkommen +50 %), Erfolgsrausch (Erfolge doppelt), Sammlerfieber (Kollektionen doppelt), Fleißtag (Aufgaben doppelt), Treuefest (Login-Serien-Bonus doppelt), Casino-Fieber (Casino-Gewinne +50 %, ohne Jackpot), Steuerfreier Tag (keine Steuer im Spieler-Shop), Mining-Rausch (Miner-Quests doppelt) und Schmugglernacht (Kaufchance im Schwarzmarkt +30 Punkte). Die Werte stehen unter `events:`.
- **Habibi-Krypto / Hinterhof-Börse:** Die schmutzige Krypto-Variante, nur mit der Lizenz Schmuggler (Knopf im Schwarzmarkt). Noch wilderer Kurs. Jeden Tag mit **15 %** Chance ein **Rug Pull** (`crypto.dunkel.rugpull-percent`): Der Kurs fällt auf den Mindestpreis (1 Coin), und **alle Bestände aller Spieler schrumpfen um 50 %** (`rugpull-wipe-percent`).
- **Datenbank:** Neue Tabellen werden automatisch angelegt.

Update: `src`, `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt). Der Code wurde nicht kompiliert oder getestet.

## Update auf Version 26.0

- **20 Tagesereignisse (10 gute, 10 schlechte):** Zum Shop-Wechsel passiert mit 50 % (`events.chance-percent`) eines davon. Gut: Großer Ausverkauf, Dividenden-Tag, Erfolgsrausch, Sammlerfieber, Fleißtag, Treuefest, Casino-Fieber, Steuerfreier Tag, Mining-Rausch, Schmugglernacht. Schlecht: Inflation, Wirtschaftskrise, Erfolgsflaute, Aufgaben-Streik, Kater, Pechsträhne, Steuerprüfung, Minenstreik, Razzia, Händler im Urlaub. Alle Werte stehen unter `events:`.
- **Titel mit Wirkung:** Jeder Titel kann eine Wirkung haben, solange man ihn trägt (`effect:` in der `config.yml` unter `titles:`). Beispiele: Felsflüsterer lässt Stein-Aufgaben doppelt zählen, Höllenschürfer lässt Blöcke der Miner-Quest manchmal doppelt zählen, Goldkaiser gibt 5 % Rabatt im Server-Shop. Die Wirkung steht im Titel-Menü.
- **Reviere (`/revier`):** Die Verkaufs-Slots des Schwarzmarkts gibt es jetzt in **6 Revieren** mit je bis zu **32 Slots**. Die Reviere kauft man nacheinander frei (`revier.unlock-prices`). Der Startpreis des ersten Slots verdoppelt sich von Revier zu Revier (`revier.slot-base-price`), und jeder weitere Slot kostet doppelt so viel wie der davor. Alte Verkaufs-Items wandern in Revier 1. Die Lizenz Schwarzlager schenkt jetzt 1 bis 5 Gratis-Slots in jedem Revier.
- **Ranglisten, Revier-Boss und Pate:** Was der Händler einem zahlt, zählt als Wochen-Umsatz. Es gibt eine Rangliste pro Revier und eine Gesamt-Rangliste. **Jeden Sonntag zum Shop-Wechsel (12:00)** wird ausgewertet: Der beste Verkäufer eines Reviers ist dort **Revier-Boss** (10 % Rabatt im Server-Shop bis zur nächsten Auswertung). Wer in den meisten Revieren Boss ist (bei Gleichstand der größte Gesamtumsatz), wird **Pate** mit dem Titel Pate und bekommt 5 % aller Schwarzmarkt-Gebühren aus dem Shop-Konto. Danach beginnt die Woche neu. Ein Kopfgeld auf den Paten muss mindestens 10.000 Coins betragen.

Update: `src`, `README.md` ersetzen, auf dem Server `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` löschen (Datenbank bleibt). Der Code wurde nicht kompiliert oder getestet.

## Update auf Version 27.0

- **Buchhaltung** (`/buchhaltung`, auch `/konto`, im Hauptmenü und unter „Mein Einkommen“): zeigt, wann du was eingenommen oder ausgegeben hast – mit Filter (alles / Einnahmen / Ausgaben), Seiten und Summen. Die Buchungen werden dauerhaft gespeichert (nichts wird mehr gelöscht). Das tägliche Einkommen wird erst ab diesem Update mitgeschrieben.
- **Events:** Die Ereignisse zu Erfolgen, Aufgaben, Login-Serie und Miner-Quests sind raus. Es bleiben 10 gute und 10 schlechte Events (neu u. a. Gebührenfreier Tag, Hehler-Hochsaison, Zahltag, Marktplatz-Tag, Gebührenerhöhung, Schnäppchenjäger, Sparhaushalt, Marktplatz-Wucher). Alte Event-Schlüssel in der `config.yml` entfallen.
- **Revier-Namen:** Hehlerkeller, Schmugglerhafen, Diebesgasse, Nebelmarkt, Rabenspelunke, Villa der Paten.
- **Schwarzlager** gibt in jedem Revier einen zusätzlichen Gratis-Slot, schaltet aber keine Reviere frei. Sobald du ein Revier freikaufst, gilt der Bonus dort sofort.
- **Pate:** bekommt zusätzlich 5 % von allem, was der Händler Spielern abkauft (`revier.pate-sale-share-percent`). Der Verkäufer bekommt nicht weniger.
- Fehler behoben: Die Texte der Miner-Quest-Menüs wurden durch die Tagesaufgaben überschrieben.

Wichtig: `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` vor dem Start löschen.

## Update auf Version 28.0

- **Kopfgelder** gelten nur noch bis zum nächsten Shop-Wechsel (12:00 Uhr). Dann gehen die Coins zurück an den Auftraggeber (die Gebühr bleibt weg). Der Schlüssel `blackmarket.bounty.expire-days` entfällt.
- **Preise für einen Server von etwa zwei Monaten angepasst:** Lizenzen etwa 60 % günstiger (Schmuggler-Stufe 1 bleibt bei 1.000), Item-Sets kosten das 8-Fache statt 20-Fache ihres Tageseinkommens (das ganze Set rechnet sich nach etwa 24 Tagen), erster Krypto-Miner 1.000, Reviere 0 / 1.000 / 4.000 / 12.000 / 30.000 / 75.000, Slot-Startpreis 150, Kopfgeld mindestens 250 (auf den Paten 5.000), Titel „Scheich“ ab 50.000 Coins.
- **Pate-Auswertung** zählt nur den Umsatz in den Revieren, also nur das, was der Händler aus Verkaufs-Slots abkauft.
- **Wochenübersicht** in der Buchhaltung (Knopf „Wochenübersicht“): die letzten 7 Tage (je von 12:00 bis 12:00 Uhr), woher dein Geld kommt und wohin es geht.
- **Chat-Hinweise:** Wer Revier-Boss oder Pate wird, bekommt eine persönliche Nachricht (auch beim nächsten Beitritt, wenn er offline war). 30 Minuten vor der Sonntags-Auswertung gibt es eine Erinnerung mit dem eigenen Wochenumsatz (`revier.reminder-minutes`).
- **Schutzgeld** (`/schutzgeld`, auch Knopf im Revier-Menü): Der Pate bietet Spielern Schutz in allen Revieren an (5 % der Händler-Einnahmen, dafür +10 Prozentpunkte Kaufchance). Revier-Bosse bieten Schutz nur in ihrem Revier an, in dem sie Boss sind (2 %, +5 Punkte). Der Angebotene muss online sein und annehmen (klickbar im Chat). Verträge enden mit der Sonntags-Auswertung oder wenn einer sie beendet. Werte unter `schutzgeld:`.
- **Razzia-Spiel:** Am Tag der Razzia beschlagnahmt die Polizei jedes noch eingestellte Verkaufs-Item mit 15 % Chance (`events.raid-confiscate-percent`). Die Lizenz Bestechung senkt die Chance um bis zu 80 %.
- **Hehlerwaren-Auktion** (`/auktion`, Hauptmenü, nur mit Schmuggler): Jede Woche wird ein seltenes Item versteigert (Liste unter `auction.items`). Gebote werden sofort abgebucht, Überbotene bekommen ihr Geld zurück, das Ende ist sonntags beim Shop-Wechsel. Das Geld des Gewinners geht aufs Shop-Konto. Der Gewinn wird mit `/auktion` abgeholt.
- **Gratis-Glücksrad** (`/gluecksrad`, Hauptmenü): einmal pro Tag gratis drehen, Gewinne unter `daily-wheel.prizes`.
- **Drei Kurzzeit-Ereignisse** (höchstens eine Stunde, spontan, nur wenn jemand online ist): Blitzverkauf (Preise 30 % günstiger, 30 Minuten), Goldene Stunde (Casino-Gewinne doppelt, 20 Minuten), Gebührenfreie Stunde (45 Minuten). Einstellungen unter `flash:`. Zum Testen: `/coinsadmin flash blitz|gold|fee`.

Wichtig: `config.yml` und `messages.yml` im Ordner `plugins/CoinShop` vor dem Start löschen.

## Update auf Version 29.0

- **Kopfgeld bleibt 3 Shop-Resets** (Config `blackmarket.bounty.reset-count: 3`). Danach bekommt der Auftraggeber die Coins zurück (Gebühr bleibt weg).
- **Kopfgeld-Hinweise abschaltbar:** Im Kopfgeld-Menü gibt es einen Knopf „Kopfgeld-Hinweise AN/AUS“ (pro Spieler).
- **Platz-1-Hinweis:** Wer in einer Rangliste Platz 1 wird (Prüfung alle 5 Minuten), bekommt eine persönliche Chat-Nachricht (`title-you-first`) zusätzlich zur Server-Meldung.
- **Schutzgeld für Pate + Boss:** Ist der Pate zugleich Revier-Boss, kann er den Pate-Deal, den Boss-Deal oder beide anbieten (`/schutzgeld anbieten <Spieler> [0|Revier|beide]`; ohne Angabe erscheint eine klickbare Auswahl).
- **Neues Pate/Boss-Menü** (`/schutzgeld menu` oder Knopf im Revier-Menü): Spieler anklicken, Deal wählen, Kunden einsehen. Nur für Pate und Bosse.
- **Steuer auf Aktien und Krypto:** Beim Verkauf mit Gewinn kommt zur Gebühr eine Steuer auf den Gewinn (Satz wie die Verkaufssteuer im Spieler-Shop; Lizenz Steuerfrei, Ereignisse und Titel wirken). Nicht bei der Habibi-Krypto. Config: `stock.tax-enabled`, `crypto.tax-enabled`.
- **Kopfgeld nur auf Schwarzmarkt-Spieler** (Lizenz Schmuggler). Im Kopfgeld-Menü gibt es „Kein Kopfgeld auf mich“: Dann kann niemand ein Kopfgeld auf dich aussetzen, du kannst aber die Vorzüge des Schwarzmarkts nicht nutzen (kaufen, Reviere, Auktion, Schutzgeld, Habibi-Krypto, Kopfgelder kassieren, Händler kauft nichts) und nur noch zuschauen. Abmelden geht nicht, solange ein Kopfgeld auf dir liegt.
- **Pate + Boss:** Die Auswahl der Deals läuft jetzt über ein Menü statt über den Chat.
- Neue Config-Zeile `blackmarket.bounty.reset-count`; neue Texte in `messages.yml` – deshalb `config.yml` und `messages.yml` auf dem Server löschen. Die Datenbank bekommt automatisch eine neue Spalte.

### Ergänzungen zu Version 29.0
- **Schwarzmarkt-Mitglieder:** Im Schwarzmarkt-Menü gibt es den Knopf „Schwarzmarktkunden“ (nur dort und nur mit freigeschaltetem Schwarzmarkt einsehbar). Er zeigt alle Spieler mit der Lizenz Schmuggler, markiert Pate und Revier-Bosse und zeigt, ob ein Kopfgeld auf sie möglich ist.
- **Pate-Wirkung unabhängig vom Titel:** Solange man Pate ist, wirkt die Titel-Wirkung des Paten (+10 Prozentpunkte Kaufchance im Schwarzmarkt) auch dann, wenn man einen anderen Titel trägt, zusätzlich zu dessen Wirkung. Der Revier-Boss-Rabatt war schon immer unabhängig vom Titel.

## Update auf Version 30.0

- **Server-Shop:** Kein Kauflimit pro Spieler mehr (`server-shop.purchase-limit-per-offer: 0`). Das Lager pro Angebot (`stock-per-offer`, 3) bleibt.
- **Pate:** Bekommt keinen Anteil mehr vom Händler-Ankauf (`revier.pate-sale-share-percent: 0`). Die 5 % der Schwarzmarkt-Gebühren bleiben.
- **Schutzgeld-Verträge** bleiben nach der Sonntags-Auswertung bestehen, wenn der Anbieter immer noch Pate bzw. Boss im selben Revier ist (sonst enden sie). Neues Vertragsmenü (Revier-Menü, `/schutzgeld`): Verträge ansehen und per Klick beenden, auch der Kunde. Im Profil steht, an wen man Schutzgeld zahlt.
- **Investitionen:** Sets, Börse, Krypto. Unter **Krypto** gibt es ein Zwischenmenü mit **Kryptobörse** und **Kryptominer**.
- **Gebühren:** Aktien 5 % (`stock.fee-percent`), Krypto 10 % (`crypto.fee-percent`). Börsenhändler: 4 / 2 / 1 %. Steuer wird nur auf den Gewinn gezahlt.
- **Ereignisse:** Jeden Tag eines (`events.chance-percent: 100`), außer am Starttag (`schedule.server-start`).
- **Rug Pull:** nur noch 5 % pro Tag (`crypto.dunkel.rugpull-percent`).
- **Lizenzen:** Steuerfrei und Gebührenfrei haben jetzt auch 3 Stufen (40 / 70 / 100 % weniger; Stufe 3 = keine Steuer/Gebühr). Damit lassen sich alle 14 normalen Lizenzen dreimal verbessern.
- Neue Config-Zeilen und Texte: `config.yml` und `messages.yml` auf dem Server löschen. **Achtung:** Bereits gekaufte Lizenzen Steuerfrei/Gebührenfrei zählen als Stufe 1 (40 %); Besitzer können auf die Stufen 2 und 3 verbessern.

## Update auf Version 31.0

- Kopfgeld auf einen Revier-Boss: mindestens 2000 Coins (`revier.boss-bounty-min`).
- Revier-Bosse bekommen 2 % der Gebühren ihres Reviers (Revier-Freikauf und Slot-Käufe), aus dem Shop-Konto (`revier.boss-fee-share-percent`).
- Habibi-Krypto: täglich 12 % Chance auf einen Mondflug, der Kurs springt auf das Doppelte (`crypto.dunkel.moon-percent`, `moon-multiplier`).
- Dazu bleiben: Rug Pull 5 %, keine Steuer auf Habibi-Gewinne, Gebühren senkbar mit Bestechung und Gebührenfrei.

## Update auf Version 32.0

- Wer in einem Revier ein Item einstellt, zahlt 5 % vom Verkaufspreis als Einstellgebühr (`revier.list-fee-percent`). Gebührenfrei, Bestechung und Ereignisse senken oder erhöhen sie.
- Die Gebühr geht aufs Shop-Konto; der Pate bekommt davon seinen Anteil, der Revier-Boss 2 %.

## Update auf Version 33.0

- Miner-Quests bleiben bestehen, bis sie geschafft sind; danach gibt es pro Tag eine neue.
- Habibi-Krypto: Höchstkurs 20.000 (`crypto.dunkel.max-price`), Mondflug-Chance; neu: Schmugglerrouten im Schwarzmarkt (`blackmarket.routes`), die täglich Habibi-Krypto bringen.
- Ereignisse überarbeitet: Mining-Boom, Bullenmarkt, Bärenmarkt ersetzen Sammlerfieber, Casino-Fieber und Pechsträhne. Dividenden-Tag zahlt 1 Coin pro Aktie. Steuer- und Gebühren-Ereignisse wirken auf alle Steuern und Gebühren, Inflation auf alle Preise, Wirtschaftskrise auf alle Erträge.
- Schwarzmarkt-Ereignisse haben jetzt auch einen Effekt im normalen Shop; die Schwarzmarkt-Infos sehen nur freigeschaltete Spieler.

## Update auf Version 34.0

- **Großhandel** (neue Lizenz „Großhandel“, 3 Stufen, `/grosshandel`, Knopf im Hauptmenü nur für Lizenzinhaber): 5 Branchen (Landwirtschaft, Bergbau, Forstwirtschaft, Viehzucht, Baustoffe), einzeln freischaltbar (Preise `wholesale.sector-prices`). Jede Branche hat täglich einen Großauftrag: ein Item, Höchstmenge 10.000–100.000. Abgegebene Items sind weg, nur das angeforderte unveränderte Item wird angenommen. Auf die Prämie werden Steuern gezahlt. Wöchentlicher Branchen-Boss (Bonus + 2 % der Steuern). Infos und Ereignisse dazu sehen nur Lizenzinhaber.
- **Ereignisse neu**: Mining-Boom, Bullenmarkt und Bärenmarkt entfernt (neu: Fleißtag, Treuefest, Aufgaben-Streik). Es gibt jetzt drei getrennte Tagesereignisse: allgemein (15), Schwarzmarkt (5, wirken nur im Schwarzmarkt, nicht mehr im normalen Shop) und Großhandel (4).
- **Schmugglerrouten**: bis zu 32, jede doppelt so teuer wie die davor, Zoll erwischt 50 %, 0,2 Habibi-Krypto pro durchgekommener Route, 5 Stufen pro Route (Stufe 5 = 1,0 Habibi-Krypto).
- **Aktienkurs** jetzt zwischen 1 und 1000.
- Neue Config-Abschnitte `wholesale`, geänderte `events`, `blackmarket.routes`: bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 35.0

- **Handel** (`/handel Spieler`): Man kann jetzt auch Aktien, Krypto und (wenn beide den Schwarzmarkt freigeschaltet haben) Habibi-Krypto anbieten, zusätzlich zu Coins und Set-Items. Der Einstandswert wandert anteilig mit, damit die Gewinnsteuer stimmt. Titel, Lizenzen, Miner und Routen sind nicht handelbar.
- **Großhandel umgebaut:** Aufträge sind jetzt für jeden Spieler persönlich (Tabelle `wh_orders`), bleiben bis zur Erledigung bestehen und mit jedem Shop-Wechsel kommt pro freigeschalteter Branche ein neuer dazu. Jede Branche hat ein Thema (Feldfrüchte, Erze, Hölzer, Tierprodukte, Baustoffe).
- **Gesellschafter und CEO** statt Branchen-Boss: Wer in einer Branche in der Woche die meisten Aufträge erledigt, wird Gesellschafter (100 Coins von jedem dort erledigten Auftrag anderer). Wer in den meisten Branchen Gesellschafter ist, wird CEO (200 Coins von jedem erledigten Auftrag). Auswertung sonntags.
- Neue Config: `wholesale.shareholder-bonus`, `wholesale.ceo-bonus`, `wholesale.max-open-orders`; entfallen: `boss-bonus-percent`, `boss-tax-share-percent`, `boss-min-turnover`.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 36.0

- **Großhandel:** Die Prämie gibt es erst, wenn ein ganzer Auftrag abgegeben ist (Teillieferungen sind möglich, die Items sind sofort weg). Der Bonus für Gesellschafter (100) und CEO (200) wird dem Erledigenden von der Prämie abgezogen und direkt an sie gezahlt; danach wird die Steuer berechnet. Höchstens 32 offene Aufträge pro Branche (`wholesale.max-open-orders`). Neue Aufträge gibt es nur beim Betreten, wenn seit dem letzten Besuch mindestens ein Shop-Wechsel war: mehrere Wechsel in der Abwesenheit zählen als einer (ein Auftrag pro freigeschalteter Branche). Neue Rangliste im Großhandel-Menü (Tätige, Gesellschafter, CEO, Aufträge der Woche, Abstand). Die Revier-Rangliste zeigt jetzt ebenfalls den Abstand zum Ersten.
- **Ereignisse:** Die vier Großhandel-Ereignisse sind entfernt. Die Schwarzmarkt-Ereignisse wirken jetzt auch im normalen Shop: Schmugglernacht Einstellgebühr −25 %, Razzia Verkaufssteuer +50 % und Casino −10 %, Hehler-Hochsaison Verkaufssteuer −25 %, Händler im Urlaub Einstellgebühr −50 %, Schnäppchenjäger Server-Shop −10 % und Item-Sets −10 %. Spieler ohne Schwarzmarkt sehen nur die Wirkung mit neutralem Namen (Gebührenrabatt, Steuerkontrolle, Steuerrabatt, Ruhiger Markt, Preissenkung).
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 37.0

- **Razzia:** Im normalen Spieler-Shop wird jetzt ein zufälliges Angebot beschlagnahmt (Verkäufer wird benachrichtigt, auch offline beim nächsten Beitritt). Die 50 % Steuer und die 10 % Casino-Abzug entfallen. Neutraler Name: Polizeikontrolle.
- **Revier-Boss heißt jetzt Unterhändler.**
- **Neue auswählbare Titel** (`/titel`): Unterhändler (10 % Rabatt auf Revier-Slots), Gesellschafter (+10 % Großhandel-Prämie), CEO (+25 % Großhandel-Prämie). Titel werden nach jedem Shop-Reset neu vergeben. Der CEO bekommt die 100 Coins des Gesellschafters zusätzlich, wo er selbst Gesellschafter ist.
- **Großhandel-Ereignisse** ersetzen Fleißtag, Treuefest und Aufgaben-Streik: Auftragsboom, Rekordbedarf, Absatzflaute, Lagerüberschuss, Branchenförderung. Nur Großhandelskunden lesen sie ganz, alle anderen sehen die Wirkung im Shop mit neutralem Namen. Werte in `events.ws-*` in der config.yml.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 37.1

- **Titel Hamster** heißt jetzt **Sammelkönig**.
- **Neue Titel ohne Rabatt/Prämie:** Unterhändler gibt +5 Prozentpunkte Kaufchance im Schwarzmarkt, Gesellschafter macht neue Großhandel-Aufträge 10 % kleiner, CEO 20 % kleiner.
- **Großhandel-Ereignisse:** Nur noch Absatzflaute, Lagerüberschuss und Branchenförderung. Auftragsboom und Rekordbedarf sind entfernt.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 37.2

- **Gesellschafter- und CEO-Titel:** Im Titel-Menü schaltet ein **Rechtsklick** auf den Titel um, ob die neuen Großhandel-Aufträge um 10 % (Gesellschafter) bzw. 20 % (CEO) **kleiner oder größer** sind (Standard: kleiner). Größere Aufträge bringen entsprechend mehr Prämie, brauchen aber mehr Items. Gilt für Aufträge, die danach neu entstehen. Linksklick trägt den Titel wie gewohnt.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 38.0

- **Server-Shop:** Verzauberte Rüstung, Werkzeuge/Waffen (je 18 %) und Shulkerboxen (10 %) erscheinen häufiger. Alles andere bleibt gleich (`server-shop.*-chance`).
- **Kryptocoin:** Die normale Kryptowährung heißt jetzt Kryptocoin (Habibi-Krypto bleibt).
- **Sets:** Jedes Set hat eigene Rentabilität: Item-Preis = Set-Einkommen × 5 bis 12 (feste Zufallszahl je Set, `investments.price-multiplier-min/max`, pro Set mit `price-multiplier` überschreibbar).
- **Miner-Quest neu:** Mehrere Blocksorten mit Mengen (z. B. 100 Stein, 200 Gras, 50 Erze). Höhere Stufe = kleinere Mengen. Jede geschaffte Aufgabe meldet sich im Chat. Laufende alte Quests funktionieren weiter (jede Sorte einmal).
- **Großhandel:** Erste Branche gratis. Der Knopf heißt Großhandelskunden. Gesellschafter und CEO werden nach **Umsatz** (Summe der Prämien) bestimmt, nicht nach Anzahl der Aufträge.
- **Reviere:** Rabenspelunke heißt Meppen, Hehlerkeller heißt Neue Heimat. Einstellgebühr im Revier 10 %. Unterhändler werden nach **Umsatz** im Revier bestimmt (Händler-Verkäufe plus gezahlte Gebühren im Revier). Der Pate bekommt 5 % auch von Revier-Freikauf und Slot-Käufen. Unterhändler-Schutzgeld bleibt bei 2 %.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 38.1

- **Server-Shop:** Die Chancen von verzauberter Rüstung, Werkzeugen und Shulkerboxen sind wieder wie früher (12 %, 12 %, 7 %). Dafür haben Rüstungsteile, Werkzeuge/Waffen und Shulkerboxen bei den normalen Items ein höheres Gewicht (`server-shop.favorite-weight: 4.0`). Alle anderen Chancen (Bücher, Variationen, Ziegenhörner usw.) sind unverändert.
- **Handelsabkommen** (Knopf im Großhandel-Menü, ein Abkommen pro Spieler und Woche): Liefervertrag, Exklusivabkommen, Steuerabkommen, Kooperationsvertrag (zwei Spieler, gemeinsamer Auftrag, Prämie nach Beitrag aufgeteilt, Anfrage per `/grosshandel koop annehmen|ablehnen`) und Eilauftrag. Werte unter `wholesale.treaty` in der config.yml.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 39.0

- **Server-Shop:** Jeder Angebotsplatz ist mit je **10 %** (`server-shop.special-chance`) ein verzaubertes Rüstungsteil, ein verzaubertes Werkzeug/eine Waffe, eine Shulkerbox oder ein verzaubertes Buch. In den übrigen Plätzen hat **jedes andere Item die gleiche Chance** (auch Tränke, Ziegenhörner, Feuerwerk, Eimer, Rüstungsbesätze, gefärbte Lederrüstung). Die Schlüssel `enchanted-*-chance`, `variant-chance`, `shulker-chance`, `favorite-weight` und `rarity-divisor` gibt es nicht mehr.
- **Wettbüro** (neue Lizenz `wetten`, Befehl `/wetten`): Wetten auf die Tode eines Spielers in der Woche (0 bis 5 oder mehr). Tode durch andere Spieler zählen nicht. Quote aus der Todes-Geschichte des Spielers, Gebühr auf den Einsatz, Steuer auf den Gewinn, Auswertung sonntags zur Reset-Uhrzeit, Auszahlung auch offline. Einstellungen unter `bets:` in der config.yml.
- **Quoten türken** (Schwarzmarkt-Menü): Gegen Bezahlung und mit Erfolgschance wird die Quote eines Ergebnisses besser. Klappt es und tritt das Ergebnis ein, bekommt der Türker einen Anteil vom Gewinn der damit gewonnenen Wetten (`bets.rig`).
- Neue Tabellen `bets` und `bet_rigs` werden automatisch angelegt. Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 39.1

- **Server-Shop:** In den Gruppen Rüstung und Werkzeuge/Waffen gibt es jetzt auch **unverzauberte** Stücke (`server-shop.unenchanted-share: 0.4` = 40 %). Alle anderen Items und Varianten können weiterhin mit gleicher Chance erscheinen.
- **Wettbüro:** Hat der Spieler, auf den gewettet wurde, in der Woche **weniger als 60 Minuten gespielt** (`bets.min-play-minutes`), werden die Wetten auf ihn zurückgezahlt, die Gebühr bleibt beim Haus. Die Spielzeit wird neu mitgezählt (beginnt mit dem Update, Spieler mit laufender Sitzung ab Neustart).
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 39.2

- **Wettbüro, Schutz gegen absichtliches Sterben:** Tode durch `/kill` zählen nie. Einen festen Abstand zwischen Toden gibt es nicht.
- **Abmelden:** Mit `/wetten abmelden` (geht für alle Spieler, auch ohne Wettlizenz) oder dem Knopf im Wettbüro-Menü meldet man sich vom Wettbüro ab. Dann kann niemand auf einen wetten (auch kein Quoten-Türken), und man kann selbst nicht wetten. Mit `/wetten anmelden` kommt man zurück. Abmelden geht nur ohne offene Wetten auf oder von dir.
- **Auffällige Tode:** Stirbt ein Spieler in den 24 Stunden vor der Auswertung mindestens 3-mal (`bets.suspicious-deaths`, `bets.suspicious-window-hours`), werden alle Wetten auf ihn zurückgezahlt (Gebühr bleibt beim Haus).
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 39.3

- **Wettfenster:** Wetten (und Quoten türken) gehen nur noch von **Sonntag 12:00 bis Montag 12:00** (`bets.open-hours: 24`, gerechnet ab der Auswertung) und gelten für die kommende Woche. Davor und danach ist das Wettbüro nur zum Ansehen offen. **Direkt nach dem Update ist das Fenster zu, bis die erste Auswertung am Sonntag gelaufen ist.**
- **Auffällige Tode:** Stirbt ein Spieler in der letzten Stunde vor der Auswertung **mehr als 5-mal** (`bets.suspicious-deaths: 5`, `bets.suspicious-window-minutes: 60`), zählen diese Tode nicht mehr. (Ersetzt die Regel aus 39.2, bei der die Wetten zurückgezahlt wurden.)
- **Wettübersicht:** Neues Menü im Wettbüro zeigt, auf wen wie oft und mit wie vielen Coins gewettet wurde (je Ergebnis, mit Quote). Die Zahlen stehen auch in der Spielerauswahl und bei den Ergebnissen.
- **Quote und Wettverhalten:** Je mehr Einsätze auf einem Ergebnis im Verhältnis zu allen Einsätzen auf den Spieler liegen, desto schlechter wird seine Quote (`bets.pool-smoothing: 2000`, `bets.pool-max-cut: 0.5` = höchstens halbiert, nie unter `min-odds`). Deine Wette behält die Quote vom Zeitpunkt des Wettens.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 39.4

- **Wetten von 0 bis 10+:** Man kann jetzt auf 0 bis 10 und mehr Tode wetten (vorher bis 5). Sehr unwahrscheinliche Ergebnisse sind erst unter `bets.min-probability: 0.0005` gesperrt, die größte Quote ist 60 (`bets.max-odds`).
- **Todesnachrichten:** Zusätzlich zur Anzahl kann man bis zu 5 Arten von Todesnachrichten tippen (Sturz, Feuer und Lava, Ertrinken, Monster im Nahkampf, Pfeile und Geschosse, Explosion, Sturz in die Leere, Ersticken, Hunger/Gift/Magie, Sonstiges). Je mehr und je seltener die Arten, desto höher die Quote (Grundwerte `bets.cause-weights`, dazu die Geschichte des Spielers). Stimmen Anzahl **und** alle Arten: volle Quote. Stimmt nur die Anzahl: der Gewinn wird verkleinert (`bets.cause-partial-factor: 0.6`, dazu zählt der Anteil der richtigen Arten). Stimmt die Anzahl nicht, ist die Wette verloren.
- **Quote:** Todesanzahl der Woche, Todes-Geschichte, Einsätze auf jedes Ergebnis und die genannten Arten fließen in die Quote ein, die bei der Auswertung den Gewinn bestimmt. Die Datenbank bekommt in der Tabelle `bets` zwei neue Spalten (automatisch).
- Bitte `config.yml` und `messages.yml` löschen.


## Update auf Version 40.0

- **Neue Menüs:** Das Hauptmenü hat nur noch Wegweiser-Knöpfe **ohne Beschreibung** beim Hovern. *Shop* öffnet ein kleines Menü mit Server-Shop und Spieler-Shop. Das Gratis-Glücksrad ist jetzt im *Casino*, die Buchhaltung nur noch unter *Kontostand*. Alle „So funktioniert es“-Knöpfe sind weg.
- **Aufgaben-GUI:** Alle Tagesaufgaben (jetzt **3 leichte + 3 schwere**, Bonus bei allen 6: `quests.bonus-all: 120`) und alle laufenden Miner-Quests sind in einem Menü (`/aufgaben`).
- **Miner:** Jeder Kryptominer hat seine **eigene Quest**; alle laufen gleichzeitig. Alte Quests werden beim ersten Start automatisch übernommen (neue Tabelle `miner_quests`). Belohnung neu: `crypto.miner.reward-base`, `reward-growth` (je Miner) und `level-multipliers` (Stufe 1 bis 10).
- **Schwarzmarkt:** Betreten (Menü, `/schwarzmarkt`, `/auktion`, `/kopfgeld`) nur mit der Lizenz *Schmuggler*. Neues Hauptmenü mit Händler-Shop, Hehlerwaren-Auktion, Hinterhof-Börse, Reviere, Kopfgeld, Kunden, Quoten türken und Schwarzmarkt-Lizenzen. Die *Hinterhof-Börse* ist ein Zwischenmenü: Habibi-Kryptobörse oder Schmuggelrouten (Erträge: `blackmarket.routes.level-rewards` und `route-growth`).
- **Neu:** Lizenz *Buchmacher* (5 Stufen, +5 bis +25 Prozentpunkte Erfolgschance beim Quoten türken) und **Revier 7 „Frankfurt“** (`revier.unlock-prices` hat jetzt 7 Werte).
- **Großhandel:** Nur mit der Lizenz *Großhandel* (Knopf im Hauptmenü immer sichtbar, ohne Lizenz kommt eine Meldung). Neues Hauptmenü (Branchen und Aufträge, Handelsabkommen, Rangliste). Prämie = `wholesale.pay-factor: 0.2` (20 % des Wertes) mit Mengenrabatt `wholesale.size-degression: 0.4` (je größer der Auftrag, desto weniger pro Stück, bis 40 % weniger). Neues Handelsabkommen *Lagerabkommen* (`storage-fee`, `storage-orders`). Der *Kooperationsvertrag* gilt jetzt eine Woche für **alle** Aufträge von zwei Spielern.
- **Kollektion „Braustand“:** enthält jetzt alle Tränke (`potions:*`), Belohnung 12000. Das Kollektions-Menü blättert bei mehr als 45 Einträgen.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 40.1

- **Miner-Belohnung hängt von der Quest ab:** Zu Beginn jeder Quest wird ihr Aufwand berechnet (Erze zählen mehr als Erde, Laub weniger). Die Belohnung liegt zwischen 60 % und 180 % des Miner-Durchschnitts und steht im Quest-Menü und im Aufgaben-Menü.
- **Revier 7 heißt Frankfurt.**
- **Beschreibungen:** Im Hauptmenü haben nur noch Schwarzmarkt, Wettbüro und Großhandel eine Beschreibung (sie brauchen eine Lizenz). Das Wettbüro ist immer sichtbar; ohne Lizenz kommt eine Meldung.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 40.2

- **Lagerabkommen statt Prämienabkommen:** Für `wholesale.treaty.storage-fee` (4000 Coins) bekommst du eine Woche lang bei jedem Tageswechsel 2 statt 1 neuen Auftrag pro freigeschalteter Branche (`storage-orders`). Die Aufträge kommen, sobald du den Server betrittst (höchstens 32 offene pro Branche).
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 40.3

- Behebt zwei Build-Fehler aus 40.0 bis 40.2 (fehlende `import`-Zeilen für `WholesaleHubMenu` und `BlackMarketHubMenu`). Sonst nichts geändert.

## Update auf Version 40.5

- **Titel:** „Henker der Nacht“ heißt jetzt „Henker“.
- **Ereignisse:** Der Knopf ist jetzt im Hauptmenü oben rechts (Glocke), nicht mehr im Shop-Menü.
- **Wettlizenz** kostet 500 Coins.
- **Gewerbe (neu):** Im Großhandel-Menü gibt es „Mein Gewerbe“ und „Alle Firmen“. Man meldet ein Gewerbe an (`company.found-price`), benennt es, lädt Angestellte ein (auch ohne Lizenz), gibt Aufträge für sie frei und baut die Firma in 5 Stufen aus. Angestellte sehen im Großhandel nur die freigegebenen Aufträge ihres Arbeitgebers und bekommen beim Abschluss einen Lohn nach abgegebener Menge. Der Umsatz zählt nur dem Inhaber. Neue Tabellen `companies`, `company_staff`, `company_open`, `company_contrib` entstehen automatisch.
- **Erfolge neu gewichtet:** leicht 15, mittel 40, schwer 120, sehr schwer 300, extrem 700; geheime Erfolge 15 bis 250 Coins nach Aufwand.
- **Weniger Infos für Nichtberechtigte:** Kopfgeld-Meldungen, Revier-Meldungen, Schutzgeld im Profil und Titel zu Schwarzmarkt und Großhandel sieht nur, wer den Bereich freigeschaltet hat.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 43.2

Behoben: Der Build von 43.0 und 43.1 brach ab, weil das Utensil Smaragde im falschen Teil des Codes stand (Fehler `cannot find symbol EMERALD`). Es steht jetzt bei den Utensilien, sodass Smaragde zum Handeln mit Dorfbewohnern wirklich ins Inventar kommen. Keine config/messages-Änderung gegenüber 43.1.

## Update auf Version 43.1

Neu in den Expeditionen: Arenen haben ein **Dach** (statt unsichtbarer Barriere, mit Lampen); **Zeitanzeige** in der Aktionsleiste (verbleibende Zeit des Levels); **Geräusch** bei geschafftem Level; Kreaturen sterben nicht mehr an Biom oder Umgebung (Schneegolem schmilzt, Wassertiere trocknen aus ...); **nur der Hilfsgeist** teleportiert sich zum Spieler; Arena-Kreaturen greifen sich nicht mehr gegenseitig an; Begleiter greifen sich nicht an; der **Schaden friedlicher Tiere ist wieder weg**, dafür hat **jedes Level mindestens ein Monster, das Schaden macht**; Rüstung und Schild werden nach dem Levelstart mehrfach neu zum Client gesendet (sie waren vorhanden, wurden nur nicht angezeigt); **Expeditions-Kills zählen nicht mehr in die Tagesaufgaben** (die Aufgaben werden während einer Expedition nicht geprüft). messages.yml und config.yml löschen (messages.yml wegen der Zeitanzeige).

## Update auf Version 43.0

Neu: **Admins/OPs brauchen für Schwarzmarkt und Großhandel jetzt auch die Lizenz** (vorher gab es eine Ausnahme für `coinshop.admin`; wieder einschaltbar mit `admin-bypass-licenses: true`). **Geheime Erfolge** stehen im Hauptmenü oben in der Mitte. Expedition: friedliche Kreaturen laufen in normalem Tempo (Wandernder Händler war viel zu schnell), Dorfbewohner sind erwachsen und haben einen Beruf, damit man mit ihnen handeln kann; neues Utensil **Smaragde** (`expedition.utility.emerald`, 2500) als Handelsgeld; neues Menü **Inventar sortieren** (Expeditionsmenü, Platz 22): der Rucksack wird als Vorschau angezeigt und mit zwei Klicks umsortiert, die Anordnung gilt für jeden Levelstart (gespeichert unter `ex_ks_*`); **Begleiter handeln selbstständig** (nicht gezähmt, kein Teleport zum Spieler, eigene Zielwahl im ganzen Gebiet). Rüstung und Schild werden nach dem Levelstart zusätzlich nach 2, 20 und 60 Ticks geprüft und ersetzt. config.yml löschen.

## Update auf Version 42.3

Behoben: Der Build von 42.2 brach ab, weil das Symbol für den Verwüster (`Material.OMINOUS_BANNER`) in dieser Paper-Version nicht existiert. Es ist jetzt `WHITE_BANNER`. Sonst keine Änderung. Keine config/messages-Änderung.

## Update auf Version 42.2

Neu: Begleiter im Level sind gekennzeichnet (dunkelgrünes Leuchten, Herz vor dem Namen, Zusatz "(Begleiter)"). Jeder Begleiter-Platz kostet das Doppelte des vorherigen (`expedition.companion-slot-price` ist der Preis des ersten Platzes, 5000; Platz 20 kostet damit über 2,6 Milliarden). config.yml löschen.

## Update auf Version 42.1

Neu: **Firmenrang** ist wieder da (entfernt wurde nur der Knopf "So funktioniert der Firmenrang"), **größere Arenen** (Radius 22 bis 42 statt 14 bis 26), **Begleiter-Umbau**: 21 Begleiter, jeder bis zu 10-mal kaufbar (`expedition.companion-max-each`, jedes weitere Exemplar doppelt so teuer), bis zu 20 Begleiter gleichzeitig (`companion-max-slots`), jeder Platz muss freigekauft werden (`companion-slot-price`, Platz n kostet n mal so viel), ein zweiter Reiter "Begleiter verwalten" mit Links-/Rechtsklick; neue Begleiter: Ziege, Panda, Killerkaninchen, Schneegolem, Plagegeist, Wither-Skelett, Piglin-Brutalo, Verwüster, Axtkämpfer, Armbrustschütze, Sumpfschütze. **Friedliche Tiere** verursachen wieder Schaden (3 mal Schadensfaktor des Levels, einmal pro Sekunde in Reichweite, ohne zusätzlichen Rückstoß, Tempo unverändert). config.yml und messages.yml löschen (der Schlüssel `companion-slots` entfällt).

## Update auf Version 42.0

Neu: Hauptmenü nach Häufigkeit sortiert (Reihe 1: Shop, Aufgaben, Expeditionen, Casino), Schwarzmarkt und Großhandel zusätzlich an der Tür abgesichert, **Firmenrang entfernt** (Knopf, Sonntagsprämien, Ruf; die config-Schlüssel `company.rank-*` und `reputation-*` sind weg), **Pfeile** müssen in Expeditionen gekauft werden (Bogen ohne Unendlich), **9 neue Utensilien** (Enderperlen, Windkugeln, Feuerresistenz, Schnelligkeit, Regeneration, Schaden, Verlangsamung, Nachtsicht), **10 Begleiter** (`expedition.companion.*`, `expedition.companion-slots`), Mobs behalten normales Tempo und normalen Rückstoß und nutzen nur ihre Fähigkeiten aus dem normalen Spiel (die Proxy-Angriffe sind weg), Schutz gegen verschwindende Rüstung/Schild (`guardKit`, gesperrte Rüstungs- und Schildplätze). config.yml und messages.yml löschen.

## Update auf Version 41.7

- **Expeditionen:** Coins-Mobs und Loot-Mobs gibt es in **5 Stufen** (Eisen, Gold, Smaragd, Diamant, Netherit) mit eigener Leuchtfarbe und Stufe im Namen. Coins-Mobs: höhere Stufe = seltener, mehr Leben und mehr Coins (`expedition.coin-mob-tier-weights`, `coin-mob-tier-value`). Loot-Mobs: Stufe aus der Schwierigkeit, mit etwas Zufall (`loot-mob.tier-shift-percent`); sie bestimmt die Belohnung wie zuvor die Qualität.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 41.6

- **Loot-Mob:** Die Belohnung hängt jetzt von der Schwierigkeit ab (Qualität 1 bis 5 aus Level und Kosten der Kreatur, `expedition.loot-mob.tier-levels` und `tier-value-factor`). Höhere Qualität: mehr Wert, bessere Lizenzen, Miner und Reviere, und ab Qualität 3 zwei, ab 5 drei verschiedene Belohnungen. Die Qualität steht im Namen des Mobs.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 41.5

- **Expeditionen – Coins-Mob:** Jetzt eine ganz normale Kreatur des Levels, golden gekennzeichnet. Der Wert hängt von der Schwierigkeit ab (`expedition.coin-mob-cost-factor`). Auszahlung nur, wenn das Level geschafft wird.
- **Expeditionen – Loot-Mob (neu):** Äußerst selten (1 von 1000 Leveln, `expedition.loot-mob.permille`), violett und funkelnd. Wird er erlegt und das Level geschafft, wird eine wertvolle Shop-Belohnung ausgewürfelt: Coins, Aktien, Krypto, Habibi-Krypto, Lizenz (neu oder Stufe), Miner, Verkaufs-Slot, Revier, Schmugglerroute oder KI-Platz (nur, was der Spieler noch bekommen kann). Neue Klasse `LootRewards`.
- **Gewerbe:** Die 10.000 Coins gelten pro Angestelltem und Auftrag; alle zusammen höchstens 80 % der Prämie, sonst nach Beitrag geteilt.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 41.4

- **Expeditionen:** Das Creaking ist zurück (Bleicher Garten) und verwundbar. Jedes 50. Level (letztes Level einer Arena) ist ein Bosslevel mit mehr und schwereren Gegnern und einem Anführer (`expedition.boss-*`). Neues Zufallsereignis **Coins-Mobs**: goldene, leuchtende Tiere, die weglaufen; die Coins gibt es nach dem Level (`expedition.coin-mob-*`).
- **Gewerbe:** Nicht mehr ausbaubar, höchstens 32 Angestellte (`company.staff-max`), gleicher Lohn für alle (`company.wage-percent`). Jeder Angestellte bekommt höchstens 10.000 Coins pro Auftrag, alle zusammen höchstens 80 % der Prämie (`wage-max-percent`, `wage-cap-per-order`); reicht das nicht, wird nach der abgegebenen Menge geteilt.
- **Wettlizenz** kostet wieder 6.000 Coins. **Wettbaron** zählt nur noch illegale (schwarze) Wetten und bekommt Gebühren nur von diesen.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 41.3

- **Expeditionen:** Das Creaking spawnt nicht mehr (alle anderen Kreaturen außer dem Enderdrachen weiterhin).
- Aufräumen: zwei ungenutzte Texte (`protect-need-revier`, `protect-choose`) entfernt, Beschreibung des Befehls `/wetten` angepasst, `/expedition` im Spielerhandout ergänzt.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 41.2

- **Investitions-Sets:** Faktor jetzt 1 bis 5 (`investments.price-multiplier-min/-max`). Das komplette Set (3 Items) rechnet sich nach 3 bis 15 Tagen. Items, die schon jemandem gehören, behalten ihren Besitzer; die Preise im Menü ändern sich.
- **Expeditionen, Nahrung:** Mit jeder Stufe mehr (8 + Stufe) und alle 6 Stufen eine bessere Sorte.
- **Erfolge:** Der Fortschritt aller Erfolge wird vor der Expedition gesichert und danach zurückgesetzt; früherer Teilfortschritt bleibt. In der Arena gibt es keine Erfolgs-Coins.
- **Arenen und Kreaturen nach Spawn-Orten:** 17 Themen (u. a. Savanne, Kirschhain, Bleicher Garten, Prüfungskammer mit Sturmwirbel, Ozeanmonument unter Wasser). Fremde Kreaturen mischen sich nur aus derselben Welt (Oberwelt, Nether, Ende) darunter. Jede Kreatur außer dem Enderdrachen kann vorkommen (Bosse über `expedition.budget-cost-cap`). Kreaturen verändern keine Blöcke, Treffer auf Arena-Kreaturen werden nicht durch Sonderregeln unwirksam gemacht.
- **Balance:** Budget-Kurve flacher (`budget-exponent` 0.5), Leben/Schaden-Formeln abgeschwächt, Ausrüstungspreise ungefähr halbiert (Faktor 1.10), Utensilien und Multiplikator günstiger. Richtwerte: Stufe 16 etwa Level 65, komplette Ausrüstung etwa Level 1.500.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 41.1

- **Großhandel:** Doppelt so viele Branchen (10): neu Fischerei, Textil und Farben, Nether-Handel, Alchemie, Bäckerei und Küche. `wholesale.sector-prices` hat jetzt 10 Werte (Preis der 2. bis 10. Branche). Die Menüs haben mehr Plätze.
- **Expeditionen, Schwierigkeit:** Jedes Level hat ein Budget für Kreaturen (`budgetOf`): Level 1 bis 8 verdoppelt sich (1, 2, 4 ... 128), danach wächst es mit `expedition.budget-exponent`. Kreaturen haben Kosten (Huhn 1 ... Wächter 300), bunt gemischt aus friedlichen, neutralen und feindlichen. Friedliche Tiere laufen auf den Spieler zu und fügen Schaden zu. Zombies, Skelette, Piglins usw. tragen mit dem Level Rüstung und Waffen, dazu Varianten (Babys, Farben, geladene Creeper).
- **Arenen passen zum Thema** (12 Themen, Wechsel alle 50 Level, danach andere Form).
- **Ausrüstung:** Speer, Streitkolben (Mace), Goldäpfel (Stufe = Anzahl) und verzauberte Goldäpfel. Man darf auch ohne gelevelte Ausrüstung starten.
- **Multiplikator:** Start auf Stufe 1 (einfach), Stufe n = n-fache Belohnung, bis Stufe 10.
- **Statistiken:** Alle Spielerstatistiken werden vor der Expedition gesichert und danach zurückgesetzt. Kollektionen, Ranglisten, geheime Erfolge und Erfolgs-Coins zählen in der Arena nicht.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 41.0

- **Expeditionen:** Neuer Knopf im Hauptmenü (Slot 44) und Befehl `/expedition`. Kämpfe in einer eigenen Arena-Welt (`expedition.world`) gegen Kreaturen, Level 1 bis 10.000. Braucht die neue Lizenz `expedition` (3.000 Coins) und mindestens eine gelevelte Ausrüstung.
- **Ausrüstung:** Helm, Brustpanzer, Hose, Schuhe, Schwert, Axt, Bogen, Armbrust (je 40 Stufen), Nahrung (30), Blöcke (bis 64) und sechs einmalig kaufbare Utensilien. Alles in der config unter `expedition:`.
- **Belohnung:** Levelzahl in Coins pro erstmals geschafftem Level, mit Multiplikator (Stufen 0 bis 10).
- **Schwierigkeit:** Anzahl, Leben und Schaden der Kreaturen steigen mit festen, abgeschwächten Formeln (config), nicht wirklich verdoppelnd. Die Arena wechselt alle 50 Level.
- **Tode in der Arena** werden abgefangen: kein echter Tod, keine Statistik, keine Wette. Der Rucksack wird gesichert (Ordner `plugins/CoinShop/expedition`) und danach zurückgegeben, auch nach einem Absturz.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 40.9

- **Schwarzmarkt-Preise:** Revier-Freikauf, Revier-Slots, Schmugglerrouten, Schwarzmarkt-Lizenzen und der Insider-Tipp enthalten jetzt eine kleine Gebühr (`blackmarket.included-fee-percent`, 3). Bei der Auktion wird sie auf das Höchstgebot des Gewinners abgebucht. Die Gebühr zählt für den Paten. 0 schaltet sie aus.
- **Wettbaron:** Kleine Wetten zählen zum Wocheneinsatz (`bets.baron.small-weight`, 0.5).
- **Buchhaltung:** Alle Buchungsarten (Wetten, Gewerbe, Großhandel, Routen …) haben jetzt lesbare Namen.
- Config-Kommentare zu Wettlizenz und Branchenpreisen korrigiert.
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 40.8

- **KI-Mitarbeiter:** Bis zu 32 Plätze (`company.ai-max`), Stufe 10 liefert 1000 Items (`ai-items` 100 bis 1000). Mehrere KIs dürfen denselben Auftrag bearbeiten. Das Menü hat Seiten.
- **Firmenrang:** 30 % der Rang-Prämie gehen an die Angestellten (`company.rank-staff-percent`), verteilt nach ihren abgegebenen Items.
- **Wettbüro die ganze Woche offen:** Bisherige Wetten heißen jetzt „Große Wetten“ (nur Sonntag 12:00 bis Montag 12:00). Neu: **Kleine Wetten** auf den nächsten Tod eines Online-Spielers (wann und woran), jederzeit (`bets.small-*`, Tabelle `small_bets`).
- **Quote türken** (vorher Quoten türken) und neu **Wettmanipulation** (`bets.manipulation.*`, ein Tod mehr oder weniger im Wochenergebnis).
- **Wettbaron** (`bets.baron.*`, Titel `titles.wettbaron`): Wer pro Woche am meisten setzt, bekommt Titel, Gebührenanteil und eine Sonderquote. Den Namen änderst du in `config.yml` (`titles.wettbaron.name`) und `messages.yml` (`gui.bets.baron-name`, `bet-baron-*`).
- Behoben: In `messages.yml` fehlte der Text `gui.bmhub.illegal` (Knopf „Illegale Wetten“).
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 40.7

- **KI-Mitarbeiter:** Neues Menü im Gewerbe. Plätze (`company.ai-slot-price`, doppelt so teuer je Platz), Stufe 1 bis 10 (`ai-level-price`, `ai-items` 100 bis 500). Zum Tageswechsel liefern sie ihren Auftrag.
- **Firmenrang:** Jetzt Top 5 (`rank-prizes`) mit Ruf-Bonus (`reputation-turnover`, `reputation-bonus-percent`).
- **Wettbüro:** Für alle offen. Wettlizenz verdoppelt die Einsatzgrenze. Großhandel-Lizenz 10.000 Coins.
- **Illegale Wetten:** Schwarze Wette (`bets.black-*`), Insider-Tipp (`tip-price`).
- Bitte `config.yml` und `messages.yml` löschen.

## Update auf Version 40.6

- **Illegale Wetten:** Im Schwarzmarkt-Menü gibt es jetzt „Illegale Wetten“. Dort liegt „Quoten türken“ (weitere Untergrund-Wetten können hier dazukommen).
- **Lohn-Obergrenze:** Ein Angestellter bekommt pro Auftrag höchstens `company.wage-cap-per-order` (10000) Coins.
- **Kooperationsvertrag:** Angestellte können auch die vom Inhaber freigegebenen Aufträge seines Kooperationspartners bearbeiten.
- **Firmenrang:** Neues Menü im Großhandel. Sonntags bekommen die drei umsatzstärksten Firmen `company.rank-prizes` (3000, 1500, 750).
- **Gratis-Glücksrad:** Die letzten Bilder der Animation zeigen den echten Gewinn.
- Bitte `config.yml` und `messages.yml` löschen.


## Update auf Version 65.35

- **Ausdauer:** Tiefenmine (50), Expeditionslevel (5) und Verteidigungswelle (1) kosten beim Start Ausdauer; ohne genug Ausdauer kein Start. Höchstvorrat 100 (Start voll), +10 pro Stunde (auch offline) bis zum Höchstvorrat; unbegrenzt viele Stufen mit Coins erhöhen beides (+10 Höchstvorrat, +0,5 pro Stunde je Stufe). Befehl `/ausdauer`.
- Die Mine-Läufe pro Tag, gesparten Läufe und Zusatz-Läufe entfallen (alte Zusatz-Läufe werden zu Ausdauer-Stufen).
- Die Tageslimits für Mystery Boxen aus Expeditionen sind aufgehoben (0 = unbegrenzt).
- Neue Dateien: `Stamina.java`, `gui/StaminaMenu.java`. `config.yml` löschen (neuer Abschnitt `stamina:`).
- Ausdauer-Stufen sind auch als Mystery-Box-Belohnung möglich.
- Aktionsleiste in Expeditionslevel und Tiefenmine zeigt die besiegten Gegner und ihre Coins (`messages.yml` löschen).
