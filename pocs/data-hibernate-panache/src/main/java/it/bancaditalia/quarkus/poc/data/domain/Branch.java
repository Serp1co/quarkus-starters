package it.bancaditalia.quarkus.poc.data.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.List;

/**
 * The active-record variation: the entity is its own repository (static finders, {@code persist()} on the
 * instance). Public fields are rewritten to accessors by Panache at build time. Fine for small, self-contained
 * entities; for anything a service must mock or that carries business rules, the repository pattern of
 * {@code IntermediaryRepository} is the recommended shape (README, "which one").
 */
@Entity
@Table(name = "branch")
public class Branch extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "branch_seq")
    @SequenceGenerator(name = "branch_seq", sequenceName = "branch_seq", allocationSize = 50)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intermediary_id")
    public Intermediary intermediary;

    @Column(nullable = false, length = 10)
    public String code;

    @Embedded
    public Address address;

    public static List<Branch> ofIntermediary(Intermediary intermediary) {
        return list("intermediary = ?1", Sort.by("code"), intermediary);
    }

    public static long countInProvince(String province) {
        return count("address.province", province);
    }
}
