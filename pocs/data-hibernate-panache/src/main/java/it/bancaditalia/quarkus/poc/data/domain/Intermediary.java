package it.bancaditalia.quarkus.poc.data.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * A supervised intermediary in the register. Plain JPA with private fields and accessors: this entity works
 * unchanged with the EntityManager DAO and with the Panache repository, which is the point of the POC.
 * {@code @Audited}: every insert, update and delete is a revision in intermediary_aud (bdi-jpa-audit).
 */
@Entity
@Table(name = "intermediary")
@Audited
@NamedQuery(name = Intermediary.BY_ABI, query = "select i from Intermediary i where i.abi = :abi")
@NamedQuery(name = Intermediary.COUNT_BY_STATUS, query = "select count(i) from Intermediary i where i.status = :status")
public class Intermediary {

    public static final String BY_ABI = "Intermediary.byAbi";
    public static final String COUNT_BY_STATUS = "Intermediary.countByStatus";

    public enum Status {
        ACTIVE, SUSPENDED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "intermediary_seq")
    @SequenceGenerator(name = "intermediary_seq", sequenceName = "intermediary_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 5)
    private String abi;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_code")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED) // the type's code is history, the type is not
    private IntermediaryType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Embedded
    private Address headquarters;

    @Version
    private int version;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "intermediary")
    @NotAudited // branches have their own life; the register's history is the intermediary's
    private List<Branch> branches = new ArrayList<>();

    protected Intermediary() {
    }

    public Intermediary(String abi, String name, IntermediaryType type, Address headquarters) {
        this.abi = abi;
        this.name = name;
        this.type = type;
        this.headquarters = headquarters;
        this.status = Status.ACTIVE;
        this.registeredAt = Instant.now();
    }

    public void changeStatus(Status newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public void rename(String newName) {
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAbi() {
        return abi;
    }

    public String getName() {
        return name;
    }

    public IntermediaryType getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public Address getHeadquarters() {
        return headquarters;
    }

    public int getVersion() {
        return version;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Branch> getBranches() {
        return branches;
    }
}
