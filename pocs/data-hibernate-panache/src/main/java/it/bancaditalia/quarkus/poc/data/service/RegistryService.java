package it.bancaditalia.quarkus.poc.data.service;

import it.bancaditalia.quarkus.poc.data.config.RegistryConfig;
import it.bancaditalia.quarkus.poc.data.domain.Address;
import it.bancaditalia.quarkus.poc.data.domain.Branch;
import it.bancaditalia.quarkus.poc.data.domain.Intermediary;
import it.bancaditalia.quarkus.poc.data.domain.IntermediaryType;
import it.bancaditalia.quarkus.poc.data.panache.IntermediaryRepository;
import it.bancaditalia.quarkus.poc.data.panache.IntermediaryTypeRepository;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySearch;
import it.bancaditalia.quarkus.poc.data.query.IntermediarySummary;
import it.bancaditalia.quarkus.poc.data.query.PageResult;
import it.bancaditalia.quarkus.poc.data.query.ProvinceCount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The business transactions of the register, on the Panache repository (the recommended shape for new code). */
@ApplicationScoped
public class RegistryService {

    @Inject
    IntermediaryRepository intermediaries;

    @Inject
    IntermediaryTypeRepository types;

    @Inject
    RegistryConfig config;

    private Pattern abiPattern;

    @Transactional
    public Intermediary register(String abi, String name, String typeCode, Address headquarters) {
        if (!abiPattern().matcher(abi).matches()) {
            throw new RegistryException("ABI '" + abi + "' does not match " + config.abiPattern());
        }
        if (intermediaries.findByAbi(abi).isPresent()) {
            throw new RegistryException("ABI " + abi + " is already registered");
        }
        IntermediaryType type = types.findByIdOptional(typeCode)
                .orElseThrow(() -> new RegistryException("Unknown intermediary type " + typeCode));
        Intermediary intermediary = new Intermediary(abi, name, type, headquarters);
        intermediaries.persist(intermediary);
        return intermediary;
    }

    public Intermediary get(String abi) {
        return intermediaries.findByAbi(abi).orElseThrow(() -> new NotRegisteredException(abi));
    }

    /**
     * Compare-and-set through {@code @Version}: the client sends the version it read; a stale one is a 409, and
     * so is the OptimisticLockException Hibernate raises if two requests race between the check and the flush.
     */
    @Transactional
    public Intermediary changeStatus(String abi, Intermediary.Status newStatus, int expectedVersion) {
        Intermediary intermediary = get(abi);
        if (intermediary.getVersion() != expectedVersion) {
            throw new StaleVersionException(abi, expectedVersion, intermediary.getVersion());
        }
        intermediary.changeStatus(newStatus);
        intermediaries.flush(); // the version check happens here, inside the method, not at commit
        return intermediary;
    }

    @Transactional
    public Branch addBranch(String abi, String code, Address address) {
        Branch branch = new Branch();
        branch.intermediary = get(abi);
        branch.code = code;
        branch.address = address;
        branch.persist(); // active record
        return branch;
    }

    public List<Branch> branches(String abi) {
        return Branch.ofIntermediary(get(abi));
    }

    public PageResult<IntermediarySummary> search(IntermediarySearch search, int page, int size, String sort,
            boolean ascending) {
        return intermediaries.search(search, page, Math.min(size, config.maxPageSize()), sort, ascending);
    }

    public List<ProvinceCount> branchesPerProvince(String abi) {
        return intermediaries.branchesPerProvince(get(abi).getAbi());
    }

    @Transactional
    public int suspendAllOfType(String typeCode) {
        return intermediaries.suspendAllOfType(typeCode);
    }

    public IntermediaryType type(String code) {
        return types.findByIdOptional(code).orElseThrow(() -> new RegistryException("Unknown intermediary type " + code));
    }

    public List<IntermediaryType> types() {
        return types.all();
    }

    public Map<Intermediary.Status, Long> countByStatus() {
        Map<Intermediary.Status, Long> counts = new EnumMap<>(Intermediary.Status.class);
        for (Intermediary.Status status : Intermediary.Status.values()) {
            counts.put(status, intermediaries.countByStatus(status));
        }
        return counts;
    }

    public String supervisor() {
        return config.supervisor();
    }

    private Pattern abiPattern() {
        if (abiPattern == null) {
            abiPattern = Pattern.compile(config.abiPattern());
        }
        return abiPattern;
    }
}
