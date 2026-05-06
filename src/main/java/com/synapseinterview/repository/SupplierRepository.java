package com.synapseinterview.repository;

import com.synapseinterview.model.Supplier;
import com.synapseinterview.util.ZipMatcher;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class SupplierRepository {

    private final List<Supplier> suppliers = new ArrayList<>();

    public void save(Supplier supplier) {
        suppliers.add(supplier);
    }

    /**
     * Returns suppliers that carry the given category AND are eligible for the customer zip.
     * Eligibility: local zip match OR (mailOrder=true AND supplier.canMailOrder=true).
     */
    public List<Supplier> findEligible(String category, String customerZip, boolean mailOrder) {
        return suppliers.stream()
                .filter(s -> s.productCategories().contains(category))
                .filter(s -> ZipMatcher.matches(s.serviceZips(), customerZip)
                        || (mailOrder && s.canMailOrder()))
                .toList();
    }

    public List<Supplier> findAll() {
        return List.copyOf(suppliers);
    }
}
