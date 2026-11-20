# Controlled MATRIQ demo set

Import `IOCL_2025.csv`, `NTPC_2024.csv`, and `OIL_2023.csv` together. They contain 15 matched material
identities across three organisations, plus one deliberate grade conflict in the OIL file to create
three review cases. The base workspace has 46 records, 15 Unified Registry identities, and 3 pending
review cases.

One pipe identity is based on NTPC source record `NTPC-SRC-M000914` from the archived held-out
catalogue. The other identities and their company variants are synthetic examples using the same
column layout and material vocabulary. Every code is marked as a synthetic demo ID, not an official
company material code. Do not present these records as real procurement records or official company
catalogue data.

The held-out CSV as a whole is not a clean demo input: it creates 53 review cases, and its largest
merged pipe group depends on the current extractor treating pipe wall thickness as the item size.
This curated set uses only one pipe size from that file and avoids that ambiguous group.

`LIVE_DEMO_UPLOAD_2025.csv` is a separate 10-row input for the live walkthrough. Upload it for IOCL
after importing the base files; all 10 rows should join existing identities without adding review cases.
