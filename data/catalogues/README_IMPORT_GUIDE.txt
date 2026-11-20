MATRIQ controlled synthetic demonstration set
=============================================

Use all three CSVs together:
- IOCL_2025.csv
- NTPC_2024.csv
- OIL_2023.csv

The three files contain 15 aligned material identities, three records per identity, plus one deliberate
conflicting OIL pipe grade. Expected base result: 46 records, 15 Unified Registry identities, 45 unified
pair cases and three pending review cases.

The NTPC pipe template is based on source row NTPC-SRC-M000914 in the archived held-out catalogue.
Other rows and company variants are synthetic examples. Codes are synthetic demo identifiers, not
official company codes; these records are not real company procurement records. Do not present them as
production material data.

`LIVE_DEMO_UPLOAD_2025.csv` is a separate 10-row IOCL upload for demonstrating new records joining
existing identities. Keep it out of the base import; upload it during the live walkthrough.

Original source-grounded catalogues and their import guide are retained in ../archive/catalogues-original/
and are not part of the active demo import.
