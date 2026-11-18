# MATRIQ operational setup

The React application uses the root Spring Boot application at port 8081. The standalone backend under `backend/` remains available for compatibility testing. Its controllers require the explicit `legacy-tooling` Spring profile and bind to loopback by default. The root `pipeline-api` profile exists for compatibility tests of the original single-dataset API. Leave both profiles disabled for operational deployments.

## Start on localhost

On Windows, open `run-frontend.bat` to start the local backend and browser interface together. It opens the browser after the session endpoint responds. Keep the launcher window open while using MATRIQ. For separate processes, run these from the repository root in separate PowerShell terminals.

```powershell
$env:MATRIQ_SECURE_COOKIE = 'false'
mvn.cmd spring-boot:run
```

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Open `http://localhost:5173`. The root `run-backend.bat` also starts the canonical backend with the local HTTP cookie setting. Public registration creates a standard account. The three supplied organisations are registered initially. The dashboard counts source records from the operational store.

## Supplied catalogues

MATRIQ does not import files at normal startup. For an explicit import of the four supplied procurement catalogues, stop the backend using the target state directory and run the following from the repository root.

```powershell
mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=catalogue-import' '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --import-supplied-catalogues --harmonize'
```

The offline job uses the same importer, organisation checks and duplicate rules as the operational API. It skips previously imported identical files. It can be rerun after a partial failure. Leave off `--harmonize` to import without running matching. The held-out NTPC CSV is retained in `data/catalogues/` and can be selected in the normal import wizard. It is excluded from the offline job.

Imported batches track submitted nonblank CSV records, accepted records, skipped rows and the file digest. Accepted records retain their mapped values, source columns, record position and batch. Source-provided identifiers remain distinguishable from official company codes through the original `CodeOrigin` value. An organisation counts as contributing when it has at least one accepted row. A review or identity is in scope when at least one of its source records belongs to the selected organisation. The most recent harmonization run is a network metric and is labeled accordingly. Technical comparisons are broader than review cases, which are surfaced from the evidence-bearing subset of compared pairs.

## Accounts and reviewer assignment

Each login is backed by a servlet session. MATRIQ uses unique normalized email addresses and PBKDF2-HMAC-SHA-256 with 600,000 iterations and a random 32-byte salt per account. It uses constant-time hash comparison, session rotation on authentication, a per-session CSRF token, a 30-minute idle timeout and an eight-hour absolute authenticated lifetime. Cookies use HttpOnly, SameSite=Lax and Secure by default. Login and sign-up have per-account and per-address attempt limits. The `matriq.security` application logger records account creation and session events with account IDs. Retain these logs in the deployment log collector.

Only account UUIDs listed in `MATRIQ_REVIEWER_IDS` can record engineering decisions. An operator verifies the reviewer independently, obtains the relevant account UUID from the restricted `accounts.json` file, sets the comma-separated allowlist, and restarts the backend. Role checks run on the server. Public registration has no route to reviewer status. Standard accounts share the workspace and can browse, register organisations, import catalogues and run harmonization.

Password recovery is intentionally unavailable until reliable email delivery and verification are configured. Enabling it requires an authenticated SMTP or transactional email provider, verified addresses, a reset page and a server workflow with short-lived single-use tokens, generic responses and session invalidation on password change.

## Engineering decisions and storage

A proposal carries a comparison case ID. A verified MATRIQ code is published only after a reviewer confirms a backend-eligible relationship. Conflicting declared standards, pressure classes, manufacturing methods and dimensions block confirmation. Matching dimensions and sufficient evidence are required. Pump evidence can be reviewed and separated, while pump approval awaits a pump-specific technical model. MATRIQ does not represent these identifiers as government-issued codes.

Decisions persist the reviewer UUID, affected source records, evidence, rationale, action and time. Separation constraints also apply when a later record would bridge the separated groups. Clarifications are recorded in the case and require operational follow-up. Imports are append-only and duplicate source keys are skipped. There is currently no in-place, versioned correction workflow for an already-imported source key.

`MATRIQ_STATE_DIRECTORY` defaults to `.matriq-state`. Back up the whole directory and restrict access to the application service account. It holds account hashes, source records, batches, proposals, code history and decisions. Atomic file replacement and an exclusive process lock support a single backend process on a local filesystem. No active login sessions survive a process restart. Use one instance and plan a different shared persistence and session architecture before horizontal scaling. The current interactive harmonization limit is 2,500 accepted records. The underlying comparison engine compares pairs within material-type blocks and its vocabulary is finite.

## HTTPS deployment and verification

Serve the built frontend over HTTPS on the same origin as `/api/**`. Reverse-proxy the API to the canonical Java service. The static host needs an SPA history fallback for application routes and must forward the Cookie and Set-Cookie headers for the API. Keep Secure cookies enabled and prevent direct public access to the Java port. The API uses the container's client IP for throttling, so configure trusted proxy address handling and an edge rate limit appropriate to the deployment. The auth limiter and sessions are process-local.

```powershell
mvn.cmd test
mvn.cmd -f backend/pom.xml test
npm.cmd --prefix frontend run build
```

For the Edge test, start the backend with an empty isolated `MATRIQ_STATE_DIRECTORY` and local HTTP cookies, and serve the production build via `npm.cmd run preview -- --port 5173` in `frontend/`. From the repository root, run the command below with Node 20 and Microsoft Edge installed. Set `MATRIQ_EDGE` or `MATRIQ_BROWSER_URL` to use another executable or preview address.

```powershell
New-Item -ItemType Directory -Force scratch | Out-Null
node --experimental-websocket scripts/browser_workspace_test.mjs
```

## Files changed in the authenticated operational pass

- Root and deployment: `.gitignore`, `README.md`, `docs/OPERATIONS.md`, `run-backend.bat`, `run-frontend.bat`, `scripts/start-local.mjs`.
- Frontend entry and routing: `frontend/index.html`, `frontend/vite.config.ts`, `frontend/src/App.tsx`.
- Frontend components: `frontend/src/components/IntroVideo.tsx`, `frontend/src/components/MaterialScene.tsx`, `frontend/src/components/Sidebar.tsx`, `frontend/src/components/SpecificationComparison.tsx`, `frontend/src/components/TopBar.tsx`.
- Frontend pages: `frontend/src/pages/Authentication.tsx`, `frontend/src/pages/authentication.css`, `frontend/src/pages/Dashboard.tsx`, `frontend/src/pages/HarmonizationWorkspace.tsx`, `frontend/src/pages/MaterialCatalogues.tsx`, `frontend/src/pages/Organizations.tsx`, `frontend/src/pages/ReviewQueue.tsx`, `frontend/src/pages/UnifiedRegistry.tsx`.
- Frontend API and data types: `frontend/src/services/api.ts`, `frontend/src/types/workspace.ts`.
- Canonical API: `src/main/java/harmonizer/api/ApiExceptionHandler.java`, `src/main/java/harmonizer/api/AutocompleteController.java`, `src/main/java/harmonizer/api/CorsConfig.java`, `src/main/java/harmonizer/api/DatasetController.java`, `src/main/java/harmonizer/api/HarmonizationController.java`, `src/main/java/harmonizer/api/ScreeningController.java`.
- Canonical algorithms and parsing: `src/main/java/harmonizer/harmonization/HarmonizationEngine.java`, `src/main/java/harmonizer/harmonization/NationalCodeRegistry.java`, `src/main/java/harmonizer/io/CsvMaterialParser.java`, `src/main/java/harmonizer/matching/AttributeExtractor.java`.
- Canonical workspace: `src/main/java/harmonizer/workspace/Accounts.java`, `src/main/java/harmonizer/workspace/ApiAccessFilter.java`, `src/main/java/harmonizer/workspace/AuthController.java`, `src/main/java/harmonizer/workspace/CatalogueImport.java`, `src/main/java/harmonizer/workspace/EngineeringEvidence.java`, `src/main/java/harmonizer/workspace/OperationalWorkspace.java`, `src/main/java/harmonizer/workspace/StateFile.java`, `src/main/java/harmonizer/workspace/SuppliedCatalogueImport.java`, `src/main/java/harmonizer/workspace/WorkspaceController.java`, `src/main/java/harmonizer/workspace/WorkspaceStorage.java`, `src/main/resources/application.properties`.
- Legacy isolation: `backend/src/main/java/com/matriq/controller/AlgorithmController.java`, `backend/src/main/java/com/matriq/controller/BenchmarkController.java`, `backend/src/main/java/com/matriq/controller/HarmonizationController.java`, `backend/src/main/java/com/matriq/controller/MaterialController.java`, `backend/src/main/resources/application.properties`.
- Tests: `src/test/java/harmonizer/api/HarmonizationApiTest.java`, `src/test/java/harmonizer/workspace/WorkspaceIntegrationTest.java`, `scripts/browser_workspace_test.mjs`.
