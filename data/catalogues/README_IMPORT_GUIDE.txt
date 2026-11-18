MATRIQ — Source-grounded catalogue package
=========================================

INPUT: User-uploaded ZIP, specifically material_description_corpus.csv.
SOURCE ORGANISATIONS: Indian Oil Corporation Limited (IOCL), NTPC Limited (NTPC), Oil India Limited (OIL).

FOUR INGESTION FILES:
1. 01_IOCL_Procurement_Catalogue.csv — choose IOCL in the website.
2. 02_NTPC_Engineering_Materials.csv — choose NTPC in the website.
3. 03_OIL_Industrial_Materials_A.csv — choose Oil India Limited (OIL) in the website.
4. 04_OIL_Industrial_Materials_B.csv — choose the SAME Oil India Limited (OIL) again; this is a second batch, not another company.

HELD-OUT DEMONSTRATION FILE:
5. 05_DEMO_NTPC_Heldout_Engineering.csv — choose NTPC; this has different source rows from the main NTPC upload. Do not ingest it with the first four if you want a clean before/after demonstration.

FIELD MAPPING IN THE 7-STEP INGESTION WIZARD:
ItemCode -> Item Code / Material Code
Description -> Raw Material Description
Unit -> Unit of Measure
Organization -> Source Organization (if supported; otherwise choose organization in step 1)
MaterialCategory -> Material Category
MaterialGrade -> Material Grade
StandardSpecification -> Standard / Specification
Dimensions -> Dimensions
Quantity -> Quantity (if supported)
SourceCorpusID, SourceKind, TenderReference, SourceURL, DocumentURL -> provenance metadata; preserve if supported

CRITICAL SOURCE LIMITATIONS:
- These are tender/procurement-derived item descriptions, NOT official complete company material master catalogues.
- ItemCode values (e.g. NTPC-SRC-M000753) are GENERATED from original corpus_id for import and traceability. They are NOT official company material codes.
- Some descriptions are broad procurement categories, not precise engineering SKUs. Do not automatically merge them into verified identities.
- Grade, standard, dimensions are extracted conservatively from the original text, not independently certified. Blank means unavailable, not equivalent.
- Unit and quantity are preserved where supplied. Do not invent missing values.
- SourceURL / DocumentURL may be empty or internal relative paths, because the supplied corpus does not always contain public direct URLs.
- Do not claim these files demonstrate real cross-company matches unless the matching attributes genuinely support them. With these sources, many records will remain singletons or need review; this is correct.
- IOCL, NTPC and OIL are the only organisations supported by the uploaded corpus. Do not label OIL data as ONGC, HPCL, BPCL, GAIL or CPCL.
- The four ingestion files contain no duplicate corpus IDs, and the held-out demo uses no source rows from the four ingestion files.
- This dataset is for functional evaluation and a source-grounded demonstration, not a verified production material master.

EXPECTED WEB UI BEHAVIOUR:
- If the current frontend accepts only ItemCode, Description, Unit, map those first; extra columns should be ignored or mapped where supported.
- If the backend demands nonblank Unit, DO NOT fill arbitrary units. Review/adjust the validator or select rows with real units.
- Empty source records and sparse technical attributes should be marked for review, not forced into matches.
