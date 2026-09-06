# Bonded Store Tracker

A slop chest / bonded store on a phone. Add the people, keep the stock, sell to
the crew, and pull a report of who bought what — with a unique QR label for
every product so a sale is one scan.

Built to sit alongside **CREW DATA MASTER** in `MrHakan/SHIP-STUFF`: same
bridge-console look, same UTC-only, offline-first habits.

```
Sell      who is buying, what, how many — one screen, three taps
Stock     prices, quantities, what the shelf is worth, QR labels
People    add, edit, sign off, delete
Report    this month and every month before it, per person, in UTC
```

## Four screens, nothing else

**Sell** — pick the person, pick the product (or scan its label), set the
quantity, add. Stock comes down as the sale goes on. The list underneath is
what that person has bought this month, newest first.

**Stock** — three numbers at the top: what the shelf is worth, what has sold
this month, what cash the account should be holding. One line under them shows
what last month left behind — cash and item worth — and opens the month sheet
where those figures, the restocking spend and **Close month** live. Then the
product list: tap a row to edit price or quantity, tap the QR button for that
product's label, or **QR labels** to print the whole sheet.

**People** — the personnel list. Add, edit, sign off, delete.
*Sign off is not delete*: they stay on the list, greyed at the bottom, until the
month is closed, because what they bought is still coming off the final wage.
**MASTER ENTERTAINMENT** sits below as a fixed ship account — it can never be
deleted and is always available to sell to.

**Report** — pick a month; every month that has sales stays in the list, so last
month, and the month before that, are always one tap away. Totals split three
ways (sold in total, charged to crew, issued to Master Entertainment), then
every person — Master Entertainment first — with what they spent. Tap a person
to see each purchase with its **UTC date and time**.

## Moving a purchase to Master Entertainment

In a person's report detail, each line carries a **→ ENT** button, and there is
**Move everything to Master Ent.** for the whole month at once. The line moves
off the crew member's account and onto the ship's — the stock is untouched,
because the goods left the store either way. The moved line remembers where it
came from and can be sent back with **↩ BACK**.

## The QR codes

Every product gets an id the moment it is created, and never gets another one.
The label encodes:

```
BS1|<product id>|<code>|<name>|<price>
```

so a generic phone scanner shows the crew a readable line, while the app reads
the id. Renaming a product or changing its price does not invalidate a label
already stuck on a shelf.

The QR encoder is written into the page — byte mode, error-correction level M,
versions 1–10 (up to 213 characters). No library, no CDN, no network: it draws
the same on a ship with the aerial disconnected as it does anywhere else.

## Month end

**Close month** carries the cash in hand and the stock worth into the next month
as its two opening figures, and drops anyone who has signed off. Sales are never
deleted — old months keep reporting exactly what they reported at the time.

```
cash in hand = cash carried in + charged to crew − spent restocking
```

Master entertainment is stock issued on the Master's account, so it is reported
separately and never counted as cash taken.

## Where the data lives

On Android the record is a file the app owns:

```
<app files>/data/state.json          the ledger — written whole, atomically
<app files>/data/backups/            a rolling copy, at most one per 10 min, 20 kept
```

Each save writes a temporary file, flushes it to disk, then renames it over
`state.json`. A rename either replaces the file completely or does nothing, so a
crash or a flat battery can cost the newest edit but never the ledger.

The page's own `localStorage` is kept as a mirror, not the record. That
distinction matters: WebView storage is a cache the system is allowed to clear —
clear the app's data, run low on space, reinstall, and it is gone. On startup the
file wins if it exists; if it does not but browser storage does, that data is
lifted into the file straight away, so upgrading from an older build loses
nothing. A pending save is flushed when the app goes to the background, because
Android kills a backgrounded WebView without warning.

Opened in a desktop browser there is no file — `localStorage` is all there is,
and **Backup / restore** says so plainly. Take backups there.

`Ledger.java` deliberately has no Android imports so it can be tested on a plain
JVM; `gradle test` runs those 13 tests, and CI runs them before it packages
anything.

## Sending a month to CREW DATA MASTER

The ship PC's slop chest lives in **CREW DATA MASTER**, and it is what prints
the deductions sheet and the slop chest report. **Report → Send to Crew Data
Master** hands it a month in the same `slopx` payload `slop-mobile.html`
produces, so on the ship PC it goes in through **Slop Chest · Sales → Import
phone entries** — paste the code, or load the file.

What travels:

| here | there |
|---|---|
| crew purchase | `entries[<crew no>] = [product, qty, UTC time]` |
| Master Entertainment | `ent = [item, qty, price, note, UTC time]` |
| a moved line | its note says where it came from |
| the product list | name, unit, price, and the QR code as the SKU |

**People are matched by crew number.** Anyone without one is held back rather
than filed under a number that does not exist, and the screen names them. Give
them a number on the People screen and their lines go with the next send.

**The ship PC adds what it receives, it does not reconcile.** Sending the same
lines twice charges the crew twice. So every line records when it was sent and
only unsent lines go by default; sending a whole month again is possible, behind
a warning that says how many lines would be charged a second time.

Prices for products the ship PC already knows come from *its* catalog, not this
one — the format carries no price on crew lines. Master entertainment lines do
carry their price.

## Getting data off the phone

**Export month as CSV** writes the month's lines — UTC date, UTC time, account,
rank, product, code, quantity, unit price, amount — plus the account summary.
**Backup / restore** writes the whole database as JSON, and restores from
pasted backup text. Both go through the Android share sheet.

## Releasing a new version

Either way works — GitHub Actions builds the APK, publishes the release and
attaches it.

From a terminal:

```sh
git tag v1.2.3
git push origin v1.2.3
```

Or from the browser, which needs no git at all: **Releases → Draft a new
release → Choose a tag → type `v1.2.3` → Create new tag → Publish release.**
The build starts on publish and attaches the APK to that release a couple of
minutes later.

The tag sets the version: `v1.2.3` becomes versionName `1.2.3` and versionCode
`10203` (`major×10000 + minor×100 + patch`). versionCode has to increase with
every release or Android refuses to install the newer APK over the older one, so
tag in order and never re-use a tag.

Every ordinary push builds and checks an APK too — **Actions → Build APK →
`bonded-store-apk`** — it is just not published.

Each build verifies that the page inside the APK is byte-for-byte `app/index.html`,
that the APK is signed, and that its version matches the tag. Any of those
failing fails the build.

The build itself lives in one place, `build.yml`, which both workflows call:

```
build.yml     test, build, verify, upload   (called, never triggered on its own)
android.yml   every push                    -> build.yml
release.yml   a version tag or a release    -> build.yml, then attach the APK
```

So a release is packaged by exactly the path CI proves. A check added to the
build cannot be missing from the release.

### Set up the signing key first — once

**An APK can only be installed over one signed with the same key.** Without a
stable key every release gets a fresh throwaway signature, so updating means
uninstalling first — and uninstalling erases the store's ledger. Five minutes
now avoids that forever.

Make the key and keep it somewhere safe (losing it means no more in-place
updates, ever):

```sh
keytool -genkeypair -v -keystore bonded-store.jks -alias bondedstore \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 bonded-store.jks       # macOS: base64 -i bonded-store.jks
```

Then add four repository secrets under **Settings → Secrets and variables →
Actions**:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the base64 output above |
| `ANDROID_KEYSTORE_PASSWORD` | the store password you chose |
| `ANDROID_KEY_ALIAS` | `bondedstore` |
| `ANDROID_KEY_PASSWORD` | the key password you chose |

Never commit `bonded-store.jks` — this repository is public. Until the secrets
exist the workflow still builds, but it warns, and the release notes say the APK
is debug-signed and cannot upgrade an earlier install.

## Building it yourself

With the Android SDK installed:

```sh
cd android
gradle assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
```

Install with `adb install -r app-release.apk`, or copy the file to the phone and
open it (Android will ask you to allow installing from that source). Add
`-PappVersionName=1.2.3 -PappVersionCode=10203` to stamp a version; without a
keystore in the environment the build falls back to the debug key.

## Layout

```
app/index.html      the whole application — UI, ledger, QR encoder
android/            a WebView shell; the only Java is origin, print, share, back
.github/workflows/  builds the APK and checks the packaged page matches app/
```

`app/index.html` also opens straight in a desktop browser, which is the quickest
way to try a change. The Android build copies it in — there is no second copy to
keep in step, and CI fails if the packaged page ever differs from the source.

## What the app is allowed to do

`CAMERA`, and only for reading a product label. There is **no INTERNET
permission** — the app cannot phone home, and the data never leaves the device
except through a file you share yourself. The ledger lives in the WebView's own
storage; the page is served from `https://appassets.androidplatform.net` rather
than `file://`, which is what makes that storage (and the camera) work at all.

## Requirements

Android 7.0 (API 24) or newer.
