# Message Wizard – Bildspeicher-Test

Diese Plugin-Version kopiert von Android freigegebene Bildanhänge sofort in
den privaten Speicher der App und verknüpft sie mit dem vorhandenen
Nachrichtenverlauf. Die sichtbare GDevelop-Bildanzeige folgt im nächsten
Schritt; diese Version prüft zuerst die zuverlässige Android-Speicherung.

Plugin-Version: `0.3.0-image-test.1`

## Was die Diagnose zusätzlich erfasst

- WhatsApp-Gruppenzusammenfassungen und WhatsApp-Unterbenachrichtigungen
- bekannte Android-Bildfelder wie `android.picture` und `android.pictureIcon`
- Typ und Abmessungen vorhandener Bitmap-Objekte
- MIME-Typ und URI von MessagingStyle-Anhängen
- einen direkten Lesetest für gefundene Medien-URIs
- Ergebnis der dauerhaften privaten Bildkopie (`Persistent image copy`)
- Typen sämtlicher Notification-Extras
- direktes Kopieren des Berichts in die Android-Zwischenablage

Gespeicherte Fotos werden auf maximal 1600 Pixel Kantenlänge verkleinert und
als JPEG mit Qualitätsstufe 82 abgelegt. Beim Löschen eines Verlaufseintrags
wird dessen Bilddatei ebenfalls entfernt. Beim Leeren eines Verlaufs werden
auch die dazugehörigen Bilder gelöscht.

Ein verknüpfter Verlaufseintrag enthält zusätzlich `hasImage`,
`imageMimeType`, `imageWidth`, `imageHeight`, `imageSizeBytes` und
`imageFileName`. Die JavaScript-Schnittstelle stellt außerdem Abfragen für
diese Werte sowie `getNotificationImageDataUrlById` bereit. Dadurch kann die
GDevelop-Erweiterung im nächsten Schritt das gespeicherte Bild über die stabile
Nachrichten-ID laden, ohne dass ein inzwischen verschobener Listenindex zum
falschen Foto führt.

Der vorhandene Plugin-Aufruf `getDebugReport` wird weiterverwendet. Mehrere
Benachrichtigungen werden im Bericht gesammelt, wobei der neueste Eintrag oben
steht. Der Bericht ist auf 120.000 Zeichen begrenzt.

## Empfohlener Testablauf

1. Die neue Plugin-ZIP in GDevelop einsetzen und eine neue Android-App bauen.
2. Von einem zweiten Gerät genau ein Foto senden und den Chat auf dem
   Testgerät geschlossen lassen.
3. Etwa fünf Sekunden warten, damit WhatsApp beziehungsweise Signal die
   Medien-URI in einer Benachrichtigungsaktualisierung nachreichen kann.
4. In der GDevelop-Testszene die Aktion
   `Copy_Debug_Report_To_Clipboard` ausführen.
5. Im neuesten Child-/MessagingStyle-Abschnitt muss bei der Bildnachricht
   `Persistent image copy: saved=true` stehen.
6. Den vollständigen Bericht zur Auswertung bereitstellen; zuerst WhatsApp,
   danach separat Signal testen.

Für den Test sollte der jeweilige Chat auf dem Testgerät geschlossen sein,
damit Android tatsächlich eine Benachrichtigung erzeugt. Außerdem sollten die
Benachrichtigungsvorschauen in WhatsApp beziehungsweise Signal aktiviert sein.
