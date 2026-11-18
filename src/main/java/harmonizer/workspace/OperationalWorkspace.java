package harmonizer.workspace;

import harmonizer.harmonization.*;
import harmonizer.matching.EquivalenceMatcher;
import harmonizer.matching.MatchEvidence;
import harmonizer.matching.VerifiedEdge;
import harmonizer.model.MaterialRecord;
import java.io.IOException;
import java.time.Instant;
import java.time.Year;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperationalWorkspace {
    record Organisation(String code, String name, String type, String contactEmail, String location) {}
    enum Status { POSSIBLE, INSUFFICIENT, CONFLICT, CONFIRMED, SEPARATED }
    enum Action { APPROVE, SEPARATE, CLARIFY }
    record Proposal(String id, int recordA, int recordB, Status status, boolean clarified,
                    EngineeringEvidence.Evidence evidence) {}
    record Decision(String id, String proposalId, int recordA, int recordB, String reviewerId,
                    String reviewerName, Action action, String rationale, Instant timestamp,
                    EngineeringEvidence.Evidence evidence) {}
    record Identity(MaterialGroup group, Instant issuedAt, Instant updatedAt) {}
    record Run(Instant timestamp, int records, int comparisons) {}
    record View(List<Organisation> organizations, List<CatalogueImport.Source> records,
                List<CatalogueImport.Batch> batches, List<Proposal> proposals, List<Identity> registry,
                List<Decision> decisions, Run run) {}
    record State(View view, List<ReviewConstraint> constraints, NationalCodeRegistry.State codeState,
                 Map<String, Instant> issuance) {}

    private final WorkspaceStorage storage;
    private final EquivalenceMatcher matcher = new EquivalenceMatcher();
    private State state;

    public OperationalWorkspace(WorkspaceStorage storage) throws IOException {
        this.storage = storage;
        var organizations = List.of(
                new Organisation("IOCL", "Indian Oil Corporation Limited", "Oil & Gas — Refining & Petroleum", "", ""),
                new Organisation("NTPC", "NTPC Limited", "Power & Energy — Power Generation", "", ""),
                new Organisation("OIL", "Oil India Limited", "Oil & Gas — Exploration & Production", "", ""));
        state = storage.read("workspace.json", State.class,
                new State(new View(organizations, List.of(), List.of(), List.of(), List.of(), List.of(), null),
                        List.of(), new NationalCodeRegistry(Year.now().getValue()).snapshot(), Map.of()));
    }

    synchronized View view() {
        return state.view();
    }

    synchronized View register(Organisation input) throws IOException {
        String code = clean(input.code(), 30).toUpperCase(Locale.ROOT);
        String name = clean(input.name(), 150);
        String sector = clean(input.type(), 100);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,29}") || name.isEmpty() || sector.isEmpty() || code.equals("ALL")) {
            throw new IllegalArgumentException("Enter a name, sector and valid organisation code.");
        }
        View current = state.view();
        if (current.organizations().stream().anyMatch(org -> org.code().equals(code))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organisation code already exists.");
        }
        var organisations = append(current.organizations(), new Organisation(code, name, sector,
                clean(input.contactEmail(), 254), clean(input.location(), 150)));
        return save(new State(new View(organisations, current.records(), current.batches(), current.proposals(),
                current.registry(), current.decisions(), current.run()), state.constraints(), state.codeState(), state.issuance()));
    }

    synchronized CatalogueImport.Batch importFile(byte[] bytes, String filename, CatalogueImport.Options options,
                                                   String actorId) throws IOException {
        View current = state.view();
        var prepared = prepareImport(bytes, filename, options, actorId, current);
        var records = new ArrayList<>(current.records());
        records.addAll(prepared.records());
        save(new State(new View(current.organizations(), List.copyOf(records), append(current.batches(), prepared.batch()),
                current.proposals(), current.registry(), current.decisions(), current.run()),
                state.constraints(), state.codeState(), state.issuance()));
        return prepared.batch();
    }

    synchronized CatalogueImport.Validation validateImport(byte[] bytes, String filename, CatalogueImport.Options options) {
        var prepared = prepareImport(bytes, filename, options, "", state.view());
        var batch = prepared.batch();
        return new CatalogueImport.Validation(batch.recordCount(), batch.validRows(), batch.warnings(),
                prepared.records().stream().limit(20).toList());
    }

    private CatalogueImport.Prepared prepareImport(byte[] bytes, String filename, CatalogueImport.Options options,
                                                    String actorId, View current) {
        if (current.organizations().stream().noneMatch(org -> org.code().equals(options.organization()))) {
            throw new IllegalArgumentException("Select a registered organisation.");
        }
        var metadata = new CatalogueImport.Options(options.organization(), clean(options.name(), 150),
                clean(options.period(), 100), clean(options.reference(), 500), options.mapping());
        return CatalogueImport.prepare(bytes, clean(filename, 255), metadata, actorId,
                current.records(), current.batches());
    }

    synchronized View run() throws IOException {
        return reconcile(state.view().decisions(), state.constraints(), null, true);
    }

    synchronized View decide(String id, Action action, String rationale, Accounts.User reviewer) throws IOException {
        if (!reviewer.reviewer()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer access is required.");
        String note = clean(rationale, 2000);
        if (action == null || note.isBlank()) throw new IllegalArgumentException("Select a decision and enter a rationale.");
        View current = state.view();
        var proposal = current.proposals().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review case was not found."));
        if (proposal.status() == Status.SEPARATED || (proposal.status() == Status.CONFIRMED && action != Action.SEPARATE)
                || (proposal.clarified() && action == Action.CLARIFY)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This decision has already been recorded.");
        }
        if (action == Action.APPROVE && (!proposal.evidence().approvable() || proposal.status() != Status.POSSIBLE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, proposal.evidence().summary());
        }
        var decision = new Decision(UUID.randomUUID().toString(), id, proposal.recordA(), proposal.recordB(),
                reviewer.id(), reviewer.name(), action, note, Instant.now(), proposal.evidence());
        List<ReviewConstraint> constraints = state.constraints();
        if (action == Action.SEPARATE) {
            Set<String> left = activeGroupKeys(current, proposal.recordA());
            Set<String> right = activeGroupKeys(current, proposal.recordB());
            if (!Collections.disjoint(left, right)) {
                left = Set.of(key(current.records().get(proposal.recordA() - 1).material()));
                right = Set.of(key(current.records().get(proposal.recordB() - 1).material()));
            }
            constraints = append(constraints, new ReviewConstraint(left, right, note));
        }
        return reconcile(append(current.decisions(), decision), constraints, decision, false);
    }

    private View reconcile(List<Decision> decisions, List<ReviewConstraint> constraints, Decision pending, boolean newRun)
            throws IOException {
        View current = state.view();
        int recordCount = newRun ? current.records().size() : current.run().records();
        if (recordCount == 0) throw new IllegalStateException("Import a catalogue before running harmonization.");
        if (recordCount > 2500) throw new IllegalStateException("The current harmonization limit is 2,500 accepted records.");
        List<CatalogueImport.Source> sources = current.records().subList(0, recordCount);
        List<MaterialRecord> materials = sources.stream().map(CatalogueImport.Source::material).toList();
        Set<String> approvedIds = new HashSet<>();
        Set<String> separatedIds = new HashSet<>();
        Set<String> clarifiedIds = new HashSet<>();
        for (Decision decision : decisions) {
            switch (decision.action()) {
                case APPROVE -> approvedIds.add(decision.proposalId());
                case SEPARATE -> {
                    approvedIds.remove(decision.proposalId());
                    separatedIds.add(decision.proposalId());
                }
                case CLARIFY -> clarifiedIds.add(decision.proposalId());
            }
        }

        var outcome = matcher.match(materials);
        var evidenceByPair = new HashMap<String, EngineeringEvidence.Evidence>();
        var candidates = new ArrayList<MatchEvidence>();
        var approvedEdges = new ArrayList<VerifiedEdge>();
        for (VerifiedEdge edge : outcome.verified()) {
            var evidence = evaluate(sources, edge.evidence());
            String id = pair(edge.recordA(), edge.recordB());
            evidenceByPair.put(id, evidence);
            candidates.add(edge.evidence());
            if (evidence.approvable() && approvedIds.contains(id)) approvedEdges.add(edge);
        }
        for (MatchEvidence base : outcome.rejected()) {
            if (base.attributeStates().criticalMatches() > 0 || "pump".equals(base.attributesA().itemType())
                    && evaluate(sources, base).conflict()) {
                evidenceByPair.put(pair(base.recordA(), base.recordB()), evaluate(sources, base));
                candidates.add(base);
            }
        }
        var allowed = HarmonizationEngine.compatibleWithReviewConstraints(approvedEdges, materials, constraints);
        var codes = NationalCodeRegistry.restore(state.codeState());
        var groups = new MaterialGroupingService(codes).groupDetailed(materials, allowed, outcome.attributesByRecord()).groups();
        var issued = new ArrayList<Identity>();
        var issuance = new HashMap<>(state.issuance());
        var publishedPairs = new HashSet<String>();
        Instant now = Instant.now();
        for (MaterialGroup group : groups) {
            if (!group.hasNationalCode()) continue;
            List<MaterialRecord> members = group.members();
            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    String id = pair(members.get(i).id(), members.get(j).id());
                    var evidence = evidenceByPair.get(id);
                    if (evidence == null || !evidence.approvable() || separatedIds.contains(id)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "The combined identity has unresolved pairwise engineering evidence.");
                    }
                    publishedPairs.add(id);
                }
            }
            var previous = current.registry().stream().filter(row -> row.group().equals(group)).findFirst();
            Instant first = issuance.computeIfAbsent(group.nationalCode(), ignored -> now);
            issued.add(new Identity(group, first, previous.map(Identity::updatedAt).orElse(now)));
        }
        if (pending != null && pending.action() == Action.APPROVE && !publishedPairs.contains(pending.proposalId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This confirmation would violate compatibility or an existing separation decision.");
        }

        var proposals = new ArrayList<Proposal>();
        for (MatchEvidence base : candidates) {
            String id = pair(base.recordA(), base.recordB());
            var evidence = evidenceByPair.get(id);
            Status status = separatedIds.contains(id) ? Status.SEPARATED
                    : approvedIds.contains(id) && publishedPairs.contains(id) ? Status.CONFIRMED
                    : evidence.conflict() ? Status.CONFLICT : evidence.approvable() ? Status.POSSIBLE : Status.INSUFFICIENT;
            proposals.add(new Proposal(id, base.recordA(), base.recordB(), status, clarifiedIds.contains(id), evidence));
        }
        Run run = newRun ? new Run(now, recordCount, outcome.candidatePairs()) : current.run();
        return save(new State(new View(current.organizations(), current.records(), current.batches(), proposals, issued,
                decisions, run), constraints, codes.snapshot(), issuance));
    }

    private View save(State updated) throws IOException {
        storage.write("workspace.json", updated);
        state = updated;
        return state.view();
    }

    private static EngineeringEvidence.Evidence evaluate(List<CatalogueImport.Source> records, MatchEvidence base) {
        return EngineeringEvidence.compare(records.get(base.recordA() - 1), records.get(base.recordB() - 1), base);
    }

    private static Set<String> activeGroupKeys(View view, int id) {
        return view.registry().stream().map(Identity::group)
                .filter(group -> group.members().stream().anyMatch(member -> member.id() == id))
                .findFirst().map(group -> group.members().stream().map(OperationalWorkspace::key)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .orElseGet(() -> Set.of(key(view.records().get(id - 1).material())));
    }

    private static String key(MaterialRecord record) {
        return CatalogueImport.key(record.cpse(), record.code());
    }

    private static String pair(int a, int b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    private static String clean(String input, int max) {
        String value = input == null ? "" : input.strip();
        if (value.length() > max) throw new IllegalArgumentException("A field exceeds its length limit.");
        return value;
    }

    private static <T> List<T> append(List<T> values, T value) {
        var next = new ArrayList<>(values);
        next.add(value);
        return List.copyOf(next);
    }
}
