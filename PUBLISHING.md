# Publisere appen

Steg for steg fra kildekode til Google Play, pluss gratis alternativer. Kravene er sjekket i oktober 2026 – Google endrer dem jevnlig, så sjekk lenkene underveis.

## 1. Før første utgivelse

Disse valgene er vanskelige eller umulige å endre senere:

- [ ] **Navn.** Bytt det overalt om du vil ha et annet (se [sjekklisten nederst](#bytte-navn)).
- [ ] **Pakkenavn (`applicationId`)** i `app/build.gradle.kts`. Det kan **aldri** endres etter første publisering på Google Play. Bruk et navnerom du eier, for eksempel `io.github.<github-brukernavn>.passvarsling`. `namespace` (pakkene i koden) kan bli stående.
- [ ] **Kildekodelenke:** sett `passvarsling.sourceUrl` i `gradle.properties` til adressen til repoet, så får «Om appen» en lenke dit.
- [ ] **Kontakt-e-post** i [PRIVACY.md](PRIVACY.md) (to steder).

## 2. Legg koden på GitHub

1. Lag et **offentlig** repo på GitHub (uten README/lisens, de finnes allerede).
2. Push:
   ```bash
   git remote add origin https://github.com/<brukernavn>/<repo>.git
   git push -u origin main
   ```
3. Personvernerklæringen har da en offentlig adresse som Google Play kan lenke til:
   `https://github.com/<brukernavn>/<repo>/blob/main/PRIVACY.md`

## 3. Utviklerkonto på Google Play

1. Registrer deg på [play.google.com/console/signup](https://play.google.com/console/signup). Det koster **25 USD én gang**.
2. Velg **personlig konto** (eller organisasjon, som krever D-U-N-S-nummer, f.eks. for et enkeltpersonforetak). Google verifiserer identiteten din med legitimasjon.
3. Utviklernavnet og kontakt-e-posten vises offentlig i butikken.

**Viktig for nye personlige kontoer:** Før appen kan publiseres for alle, må den ha vært i en *lukket test* med **minst 12 testere som har vært med sammenhengende i 14 dager**. Deretter søker du om produksjonstilgang. Organisasjonskontoer er unntatt. ([Googles hjelpeside](https://support.google.com/googleplay/android-developer/answer/14151465))

## 4. Signeringsnøkkel

Google Play bruker *Play App Signing*: Google holder selve signeringsnøkkelen, og du signerer opplastingene med en **opplastingsnøkkel**.

```bash
keytool -genkeypair -v -keystore release.jks -alias passvarsling -keyalg RSA -keysize 4096 -validity 10000
```

Lag `keystore.properties` i prosjektmappa (den er i `.gitignore`, så den havner ikke på GitHub):

```properties
storeFile=release.jks
storePassword=…
keyAlias=passvarsling
keyPassword=…
```

Ta sikkerhetskopi av `release.jks` og passordene et trygt sted. Mister du opplastingsnøkkelen, kan Google tilbakestille den, men det tar tid.

> Apper fra Google Play blir signert med Googles nøkkel, APK-er fra GitHub med din. Android godtar ikke oppdatering på tvers, så en bruker må holde seg til én kilde (eller avinstallere først).

## 5. Bygg app-pakken

```bash
./gradlew bundleRelease -Pplay
```

Fila havner i `app/build/outputs/bundle/release/app-release.aab`.

`-Pplay` fjerner tillatelsen til å be direkte om unntak fra batterioptimalisering (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). Google Play tillater den bare for noen få typer apper, og den kan gi avslag. Uten den åpner appen i stedet Androids egen innstillingsside, med et hint om hva brukeren skal velge. GitHub-/F-Droid-bygg (uten `-Pplay`) beholder den direkte dialogen.

## 6. Opprett appen i Play Console

**Opprett app:** navn, standardspråk *norsk bokmål (nb-NO)*, «App», «Gratis».

**Butikkoppføring** – alt ligger klart i `fastlane/metadata/android/`:

| Felt | Fil |
| --- | --- |
| Navn (maks 30 tegn) | `nb-NO/title.txt` |
| Kort beskrivelse (maks 80) | `nb-NO/short_description.txt` |
| Full beskrivelse (maks 4000) | `nb-NO/full_description.txt` |
| Ikon 512 × 512 | `en-US/images/icon.png` |
| Funksjonsgrafikk 1024 × 500 | `nb-NO/images/featureGraphic.png` |
| Skjermbilder (2–8) | `nb-NO/images/phoneScreenshots/` |

Legg også til engelsk (en-US) som oversettelse med filene i `en-US/`. Kategori: *Reise og lokalt* passer godt.

**Appinnhold** (under «Policy og programmer»):

- **Personvernerklæring:** lenken fra steg 2.
- **Annonser:** Nei.
- **Apptilgang:** Alle funksjoner er tilgjengelige uten innlogging.
- **Innholdsvurdering:** fyll ut spørreskjemaet; ingenting av vold, sex, rus eller pengespill → laveste aldersgrense.
- **Målgruppe:** 18+ (appen er ikke laget for barn).
- **Datasikkerhet:** *Ingen data samles inn eller deles.* Begrunnelse: posisjonen behandles bare på telefonen, og forespørslene til politiet inneholder ingen opplysninger om brukeren. Ingen analyse, reklame eller krasjrapportering.
- **Offentlige apper:** Appen er ikke laget av eller for en myndighet. Beskrivelsen sier allerede at den er uavhengig av Politiet, og lenker til kilden (politiets timebestilling), slik Google krever for apper som viser offentlig informasjon.
- Nyheter, helse, finans, forgrunnstjenester, bakgrunnsposisjon: ikke relevant / nei.

## 7. Lukket test (12 testere i 14 dager)

1. **Testing → Lukket testing:** opprett et spor, legg til testerne (en e-postliste eller en Google-gruppe).
2. Last opp `app-release.aab`, skriv versjonsnotater (fra `fastlane/metadata/android/*/changelogs/`), og send til gjennomgang.
3. Del opt-in-lenken. Hver tester må godta invitasjonen med Google-kontoen sin og installere appen fra Play.
4. Hold minst 12 testere påmeldt i 14 sammenhengende dager. Venner og familie med Android-telefon fungerer fint; be dem gjerne følge et par kontorer og si fra om feil.
5. **Oversikt → Søk om produksjonstilgang**, og svar på spørsmålene om testen.

## 8. Produksjon

Når tilgangen er godkjent: **Produksjon → Opprett utgivelse**, velg samme app-pakke (eller last opp en ny), og rull ut. Gjennomgangen tar vanligvis fra noen timer til noen dager.

## 9. Oppdateringer

1. Øk `versionCode` (heltall, alltid høyere) og `versionName` i `app/build.gradle.kts`.
2. Skriv endringslogg i `fastlane/metadata/android/nb-NO/changelogs/<versionCode>.txt` (og `en-US`).
3. `./gradlew bundleRelease -Pplay` og last opp.

Google krever at appen sikter mot en nyere Android-versjon omtrent hvert år: fra 31. august 2026 må nye apper og oppdateringer ha `targetSdk` 36 (det har appen nå). Neste krav ventes å bli 37 i august 2027. Bump `compileSdk`/`targetSdk`, test, og gi ut en oppdatering i god tid. ([Googles krav](https://developer.android.com/google/play/requirements/target-sdk))

## 10. Gratis alternativer

- **GitHub Releases:** Lag en tagg (`git tag v1.1.0 && git push --tags`), så bygger `.github/workflows/release.yml` en signert APK og legger den ut. Hvilke hemmeligheter arbeidsflyten trenger, står øverst i fila.
- **F-Droid:** Appen bruker ingen Google-tjenester eller sporingsbiblioteker og har metadata i `fastlane/`, så den passer. Send den inn etter [F-Droids veiledning](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/). F-Droid bygger appen selv fra kildekoden.
- **IzzyOnDroid:** et F-Droid-kompatibelt arkiv som henter APK-en fra GitHub Releases. Raskere å komme inn i enn F-Droid.

> **Utviklerverifisering:** Google innfører krav om at alle Android-apper på sertifiserte telefoner skal komme fra en verifisert utvikler, også apper som installeres utenfor Play. Det håndheves fra 30. september 2026 i Brasil, Indonesia, Singapore og Thailand, og skal etter planen gjelde hele verden i 2027. Har du en Play Console-konto, kan du registrere deg der; for hobbyprosjekter finnes også en gratis konto med begrenset distribusjon. Følg med på [developer.android.com/developer-verification](https://developer.android.com/developer-verification/guides) før du satser på bare APK-distribusjon.

## Bytte navn

Om appen skal hete noe annet enn «Passvarsling»:

1. `app_name` i `app/src/main/res/values/strings.xml` (resten av tekstene i appen henter navnet derfra).
2. `fastlane/metadata/android/*/title.txt` og `full_description.txt` (begge språk).
3. Ny funksjonsgrafikk: `powershell -File tools/make_feature_graphic.ps1 -name "Nytt navn"`.
4. `README.md`, `PRIVACY.md` og navnet på APK-fila i `.github/workflows/release.yml`.
5. `User-Agent` i `app/src/main/java/io/github/passvarsling/net/Http.kt`, så politiet ser riktig navn.
6. Valgfritt: `rootProject.name` i `settings.gradle.kts`. Kodepakkene (`io.github.passvarsling`) kan bli som de er.
