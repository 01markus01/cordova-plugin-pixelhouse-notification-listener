# Message Wizard – Bilddiagnose

Diese Plugin-Version ist ausschließlich für den ersten Test der von Android
bereitgestellten Bilddaten gedacht. Sie speichert noch keine Vorschaubilder.

Plugin-Version: `0.3.0-diagnostic.2`

## Was die Diagnose zusätzlich erfasst

- WhatsApp-Gruppenzusammenfassungen und WhatsApp-Unterbenachrichtigungen
- bekannte Android-Bildfelder wie `android.picture` und `android.pictureIcon`
- Typ und Abmessungen vorhandener Bitmap-Objekte
- MIME-Typ und URI von MessagingStyle-Anhängen
- einen direkten Lesetest für gefundene Medien-URIs
- Typen sämtlicher Notification-Extras
- direktes Kopieren des Berichts in die Android-Zwischenablage

Der vorhandene Plugin-Aufruf `getDebugReport` wird weiterverwendet. Mehrere
Benachrichtigungen werden im Bericht gesammelt, wobei der neueste Eintrag oben
steht. Der Bericht ist auf 120.000 Zeichen begrenzt.

## Empfohlener Testablauf

1. Den vorhandenen Nachrichtenverlauf nicht löschen. Neue Diagnoseabschnitte
   sind an der Versionszeile `0.3.0-diagnostic.2` zu erkennen und stehen oben.
2. Von einem zweiten Gerät genau eine normale Textnachricht senden.
3. Danach genau ein Foto senden und warten, bis die Android-Benachrichtigung
   vollständig angezeigt wurde.
4. In der GDevelop-Testszene die Aktion
   `Copy_Debug_Report_To_Clipboard` ausführen.
5. Den vollständigen Bericht aus der Zwischenablage zur Auswertung
   bereitstellen.
6. Den Ablauf zuerst mit WhatsApp und anschließend separat mit Signal
   wiederholen.

Für den Test sollte der jeweilige Chat auf dem Testgerät geschlossen sein,
damit Android tatsächlich eine Benachrichtigung erzeugt. Außerdem sollten die
Benachrichtigungsvorschauen in WhatsApp beziehungsweise Signal aktiviert sein.
