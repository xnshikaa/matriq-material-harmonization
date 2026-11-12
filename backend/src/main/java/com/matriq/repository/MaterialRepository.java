package com.matriq.repository;

import com.matriq.model.MaterialRecord;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for MaterialRecord objects.
 * Uses ConcurrentHashMap for thread safety during parallel import.
 * Data persists for the lifetime of the application process.
 */
@Repository
public class MaterialRepository {

    private final Map<String, MaterialRecord> store = new ConcurrentHashMap<>();

    public void save(MaterialRecord record) {
        store.put(record.getId(), record);
    }

    public void saveAll(List<MaterialRecord> records) {
        records.forEach(r -> store.put(r.getId(), r));
    }

    public Optional<MaterialRecord> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<MaterialRecord> findAll() {
        return new ArrayList<>(store.values());
    }

    public List<MaterialRecord> findByCompany(String companyName) {
        return store.values().stream()
            .filter(r -> companyName.equalsIgnoreCase(r.getCompanyName()))
            .collect(Collectors.toList());
    }

    public List<MaterialRecord> findByBatch(String batchId) {
        return store.values().stream()
            .filter(r -> batchId.equals(r.getImportBatchId()))
            .collect(Collectors.toList());
    }

    public List<MaterialRecord> findByStatus(MaterialRecord.ImportStatus status) {
        return store.values().stream()
            .filter(r -> status.equals(r.getStatus()))
            .collect(Collectors.toList());
    }

    public boolean existsByCompanyAndCode(String companyName, String originalCode) {
        return store.values().stream()
            .anyMatch(r -> companyName.equalsIgnoreCase(r.getCompanyName())
                       && originalCode.equalsIgnoreCase(r.getOriginalMaterialCode()));
    }

    public long count() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}
