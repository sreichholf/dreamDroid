## 1.5.439
* UPD: Update einiger Google/Android Bibliotheken, Themes entsprechend angepasst
* FIX: Fix Probleme mit builds für fdroid

## 1.5.438
* FIX: Kompatibilität mit Android < 5.0 wiederhergestellt
* FIX: Absturz beim Streamen behoben

## 1.5.437
* DEL: Auto-Theme-Wahl und mit ihr die Berechtigung für die grobe Position
* DEL: Die Funktion "Spenden" wurde ersatzlos gestrichen (~75% einer Spende wurden durch Google/Steuern aufgefressen)
* UPD: AndroidX Bibliotheken wurden auf Version 1.1 aktualisiert (wo verfügbar)
* UPD: Wechsel auf Android 10 SDK
* FIX: Abstürze wurden behoben

## 1.4.434
* Das Tracking-Modul wurde vollständig entfernt (die bliebtesten Features sind mittlerweile hinreichend bekannt) und mit ihm die Datenschutzerklärung
* NEU: Export der TabbedNavigationActivity via URL-Schema (danke Stefan H.)
* NEU: Sendungsfortschritt ist jetzt am oberen Rand
* FIX: Das Video-Overlay konnte bei bestimmten Timing durcheinander geraten
* DEV: Code cleanups

## 1.4.433
* FIX: Virtual remote widget
* FIX (potentiell): Lang-Drücken in Listen

## 1.4.432
* FIX: VirtualRemote war kaputt
* FIX: Möglicher fix für "nicht klickbare" Einträge in der Kanalliste bei einigen wenigen Nutzern

## 1.4.431
* Redesign des Video Players für alle Geräteklassen (mit PVR-Steuerung für Aufnahmen)
* Leichte Überarbeitung des EPG Dialogs (für TV neu hinzugefügt)
* Android 9 Stil für Buttons
* Dialog für den Langtext des lafuenden events im Videoplayer
* FIX: Verhalten beim Wiederherstellen von Backups
* FIX: Absturz in Verbindung mit der automatischen Konfiguration
* FIX: Absturz im Video Player bei Aufnahmen mit unzulässiger Längenangabe in den Metadaten

## 1.3.430
* FIX: Absturz wenn über einen externen Video-Player gestreamt werden soll
* FIX: Absturz wenn Encoderport/bitrate leer oder ungültig ist
* FIX: Fehler in der integrierten Bibliothek "gaugeview" der dazu führte dass das Androidd SDK die Berechtigung zum lesen des Telefon-Status automatisch ergänzte [https://github.com/...](https://github.com/sreichholf/dreamDroid/commit/f3eb97472a850ddbeca7bf91a14c4163f845cc35)

## 1.3.429
* Verbesserungen am Video Player (Rotation ohne Unterbrechung, Springen beim Abspielen auf Aufnahmen, Korrekte Zeitanzeige bei Aufnahmen)

## 1.3.428
* Der Video Player ist nun besser bedienbar (besonders auf TVs)
* TV: Die Einstellungen wurden aufgeräumt. In den Verbindungeinstellungen kann jetzt encoding eingestellt werden
* FIX: Der EPG für Aufnahmen war im Nacht-Modus hell

## 1.3.425
* FIX: Tastatursteuerung
* DEV: Code cleanup

## 1.3.424
* FIX: Absturz beim Bearbeiten eines Profils
* FIX: Abstürze auf Geräten mit Android < 8 (Kanalliste, Backup)

## 1.3.423
* FIX: Android TV Probleme (seit 1.2.420)
* NEU: Open Source Lizenz-Dialog (Menü -> Über)
* DEV: Etwas größere interne Änderungen um länger existierende Abstürze zu beheben (android-state mit livefront:bridge)
* DEV: Workaround für einen Android-Bug mit Map und Parcel [https://medium.com/...](https://medium.com/the-wtf-files/the-mysterious-case-of-the-bundle-and-the-map-7b15279a794e)

## 1.2.422
* FIX: Abstürze im Einstellungs-Backup
* FIX: "Eignungstest"-Fehler für AndroidTV

## 1.2.421
* FIX: unverschlüsseltes http

## 1.2.420
* NEU: Backup der Einstellungen
* FIX: Verbesserung an der "TV" Version von dreamDroid
* FIX: Abstürze behoben
* DEV: Wechsel zu Android SDK 28 und AndroidX
* DEV: Externe Bibliotheken aktualisiert
* DEV: Code Cleanups

## 1.2.418
* FIX: Die Picon-Synchronisation schrieb leere Dateien (Picons müssen ggf. erneut synchronisiert werden)

## 1.2.417
* NEU: Neuer Dialog für Änderungen
* FIX: Diverse Probleme und Abstürze die durch das Drehen des Handys oder bei einem späteren Aufruf der App enstanden sind
* IMP: Die Audio Qualität des Signal Meters wurde verbessert. Der Bildschirm bleibt nun dauerhaft an während Es offen ist

## 1.2.416
* FIX: Absturz beim Start auf Gerät mit älteren Android Versionen (vor Lollipop)

## 1.2.415
* Hinweis: Für Nutzer des Google Play Store sind alle Einträge nach Version 1.2-400 gültig!
* FIX: Virtual Remote Widget unter Android >= Oreo

## 1.2.414
* Weitere Änderungen am VideoPlayer
* Schnelles Blättern (FastScroll) funktioniert wieder

## 1.2.409
* DreamDroid ist jetzt freie Software, lizensiert unter GPLv3
* Einige Änderungen für die Veröffentlichung auf f-droid

## 1.2-405
* FIX: Sync-Benachrichtigungen unter Android >= Oreo

## 1.2-404
* Adaptives Icon für Android >= Oreo

## 1.2-403
* FIX: Update auf neusten VLC, hilft hoffentlich Video/Audio Probleme zu beseitigen

## 1.2-400
* FIX: Screenshots

## 1.2-398
* FIX: Diverse Abstürze
* FIX: Reload Button

## 1.2-397
* FIX: Abstürze beim Verlassen langer Listen (z.B. Timer Bearbeiten aus dem Sender-EPG)

## 1.2-396
* FIX: Diverse Abstürze
* FIX: Eine Änderung des Themes trat erst nach einem Neustart der App in Effekt

## 1.2-395
* FIX: Crash bei "Timer Bearbeiten" aus der EPG-Liste
* FIX: Fehlender Text bei Ja/Nein Dialogen

## 1.2-394
* NEU: Initiale Unterstützung von Android TV
* NEU: Interne Updates und Anpassungen
* NEU: Einige coole Verbesserungen der Navigation durch F. Edelmann
* NEU: Viele kleinere optische Anpassungen
* FIX: Einige potentielle Zertifikatsprobleme wurden behoben
* BESSER: Performance verbessert

## 1.1-386
* FIX: Abstürze im VideoPlayer

## 1.1-385
* FIX: Abstürze unter Android 7.0
* FIX: Kaputte Apspekt-Ratio im VideoPlayer in Verbindung mit Rotation/MultiWindow

## 1.1-384
* NEU: Tonspur/Untertitel Auswahl im Videoplayer
* FIX: Ein paar (mögliche) Abstürze
* BESSER: kleinere Verbesserungen an der Benutzeroberfläche

## 1.1-381
* FIX: "Lade..." wurde nach rotation in einer Liste angezeigt

## 1.1-380
* FIX: Fehlerbehandlung in diversen Listen verbessert
* FIX: Workaround für die Probleme mit der Funktion "EPG" in Verbindung mit älteren Dreamboxen

## 1.1-379
* FIX: der fix für Profilnamen ist nun tats. enthalten

## 1.1-378
* NEW: Picons können nun direkt über http(s) geladen werden und müssen nicht mehr vorab synchronisiert werden (Vorsicht wg. Datenvolumen!)
* FIX: Profil-Namen konnten nicht mehr geändert werden da das Feld nicht zu sehen war
* FIX: Beim Wechsel von "EPG" nach "Einstellungen" blieb die Datums/Zeitwahl des EPG stehen

## 1.1-377
* NEU: EPG Infos werden nun nicht mehr in einem Popup sondern in einem "Bottom Sheet" angzeigt
* BESSER: Das Verhalten des Videoplayers in Sachen "was wird angezeigt und was nicht" wurde verbessert
* NEU: Videoplayer kann nun in Aufnahmen springen, leider ist es derzeit nicht möglich den Fortschritt anzuzeigen :(
* NEU: Videoplayer-Gesten für Helligkeit und Lautstärke im (Helligkeit links, Lautstärke rechts)
* FIX: Kanalliste im Video Player crashte wenn man einen Marker wählte
* FIX: Zeilenumbrüche werden nicht mehr aus dem EPG gefiltert, könnte zu Problemen auf älteren Boxen führen (falls ja, bitte mitteilen)
* FIX: Unter Android >= 6.0 wir die Berechtigung für den Standort jetzt erst angefragt wenn das Thema manuell auf "Auto" gestellt wird. Neues Standard Thema ist "Tag"
* BESSER: Die Ordnerwahl in der Filmliste basiert nun auf einem Floating Action Button
* BESSER: Die Bouquetwahl unter "Umschalten" gleicht nun der aus "EPG"
* BESSER: Die überprüfung des Verbindungsprofils wird nicht mehr ständig neu durchgeführt
* TEC: Viele Grafiken durch Vector-Grafiken ersetzt
* TEC: Wechsel von UniversalImageLoader zu Picasso
* TEC: Cleanups und Aktualisierung externer Bibliotheken

## 1.1-372
* FIX: Diverse kleinere Fehler behoben die unter gewissen Umständen zu Abstürzen führten
* FIX: Die App ist nun wieder deutlich kleiner

## 1.1
* NEU: Integrierter Videoplayer auf VLC-Basis
* NEU: Support für Encoder-basierte Streams auf DM820 und DM7080
* NEU: Unterstützung der Android 6.x Berechtigungen
* NEU: Automatisches Tag/Nacht Thema (Einstellbar, Automatik benötigt ungefähren Standort zur Tag/Nacht-Berechnung)
* NEU: Android N MultiWindow Unterstützung
* UPD: UI wurde auf aktuelle Technologien aktualisiert (GROSSE interne Änderungen)
* FIX: Diverse Fehlerbehbungen (defekter EPG auf älteren Dreamboxen, doppelte Tags, ...)
* NEU: Fehler die wir noch nicht gefunden haben ;)

