# Kirana Khata: how it's built (v1.1)

- **Stack:** Kotlin, Jetpack Compose, Room (SQLite). minSdk 26, target 35. Release is signed with `kirana-release.keystore` (keep it safe).
- **Money:** whole paise (`Long`). **GST rates:** whole units of 0.001% (2.5% = 2500, 0.125% = 125). No floating point in money or tax.
- **GST maths** (`data/Gst.kt`):
  - Inclusive: taxable = round(amount × 100000 ÷ (100000 + 2 × CGST)).
  - Exclusive: tax = round(amount × 2 × CGST ÷ 100000).
  - CGST = ⌈tax ÷ 2⌉ and SGST = tax − CGST, so the two always add up exactly to the tax.
- **GST data:** `assets/gst_rates_2025-09-22.json` is generated from the CBIC PDF by `tools/pdftext.py` and `tools/parse_gst.py`, with checks for no serial gaps, valid HSN codes and the rate matching the schedule. It's read-only reference data, not stored in the database. Loaded by `GstCatalog`.
- **Database (v6):**
  - customers, entries (balance triggers), bills, bill_items (GST snapshot per line), items (stock + HSN link), expenses, stock_moves, cash_entries.
  - Migrations: auto 1→5, then the hand-written `MIGRATION_5_6` (ADD COLUMN only, so data and triggers are kept).
- **Tests:**
  - JVM: `BillMathTest`, `GstMathTest`.
  - On phone: `AppFlowsTest`, `GstFlowsTest` (migrations, HSN search, catalog, GST bill + PDF).
  - Run on a phone with `adb install` + `am instrument`. **Never use `connectedAndroidTest`** (it uninstalls the app and wipes data).
