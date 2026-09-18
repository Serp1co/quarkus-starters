package it.bancaditalia.quarkus.poc.data.panache;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import it.bancaditalia.quarkus.poc.data.domain.IntermediaryType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** Reference data. {@code findById} goes through the second-level cache: after the first load, no SQL. */
@ApplicationScoped
public class IntermediaryTypeRepository implements PanacheRepositoryBase<IntermediaryType, String> {

    public List<IntermediaryType> all() {
        return listAll(Sort.by("code"));
    }
}
