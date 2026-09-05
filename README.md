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

## Getting data off the phone

**Export month as CSV** writes the month's lines — UTC date, UTC time, account,
rank, product, code, quantity, unit price, amount — plus the account summary.
**Backup / restore** writes the whole database as JSON, and restores from
pasted backup text. Both go through the Android share sheet.

## Building the APK

The APK is built by GitHub Actions on every push: **Actions → Build APK →
`bonded-store-apk`**. Publish a release and it is attached to that release too.

Locally, with the Android SDK installed:

```sh
cd android
gradle assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

Install with `adb install -r app-debug.apk`, or copy the file to the phone and
open it (Android will ask you to allow installing from that source).

The APK is debug-signed for sideloading, not for Play. To publish, add a real
`signingConfig` in `android/app/build.gradle`.

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
