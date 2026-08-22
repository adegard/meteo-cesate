# Meteo Cesate (Android)

App Kotlin leggera per le previsioni di Cesate (MI): nessuna pubblicità, nessun cookie, nessun tracciamento.
Unica fonte dati: [Open-Meteo](https://open-meteo.com) (senza API key).

## Funzioni
- Condizioni attuali (temp, percepita, umidità, vento, pressione, UV, alba/tramonto)
- Prossime 24 ore e 7 giorni
- **Avvisi temporali**: notifica push (WorkManager, ogni 15 min) quando sono previsti temporali/grandinate entro 12 ore (codici WMO 95–99)

## Download APK
APK debug scaricabile dal branch `status` (`meteo-cesate-debug.apk`) o dagli artifact delle Actions.

## Build
```bash
./gradlew assembleDebug
```
La CI (GitHub Actions) compila automaticamente ad ogni push su `main`.

## Installare
Trasferisci l'APK sul telefono e consenti "Installa app sconosciute".
Al primo avvio premi **Avvisi ⛈️** per concedere il permesso notifiche.
