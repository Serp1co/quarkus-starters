-- Register of supervised intermediaries. Types match what Hibernate ORM expects on PostgreSQL because the
-- schema management strategy is "validate" (bdi-config-flyway): entities and migrations must agree.

-- Reference data, cached in the second-level cache (@Cacheable)
create table intermediary_type (
    code        varchar(20) primary key,
    description varchar(120) not null
);
insert into intermediary_type (code, description) values
    ('BANK', 'Banca'),
    ('SIM',  'Societa di intermediazione mobiliare'),
    ('SGR',  'Societa di gestione del risparmio'),
    ('IP',   'Istituto di pagamento'),
    ('IMEL', 'Istituto di moneta elettronica');

-- Sequences with allocationSize 50: ids are assigned in memory, so inserts can be batched (IDENTITY cannot)
create sequence intermediary_seq start with 1 increment by 50;
create sequence branch_seq start with 1 increment by 50;

create table intermediary (
    id            bigint primary key,
    abi           varchar(5) not null,
    name          varchar(200) not null,
    type_code     varchar(20) not null references intermediary_type (code),
    status        varchar(20) not null,
    street        varchar(200),
    city          varchar(100),
    province      varchar(2),
    postal_code   varchar(5),
    version       integer not null,
    registered_at timestamp(6) with time zone not null,
    updated_at    timestamp(6) with time zone,
    constraint uk_intermediary_abi unique (abi)
);
create index ix_intermediary_status on intermediary (status);
create index ix_intermediary_type on intermediary (type_code);

create table branch (
    id              bigint primary key,
    intermediary_id bigint not null references intermediary (id),
    code            varchar(10) not null,
    street          varchar(200),
    city            varchar(100),
    province        varchar(2),
    postal_code     varchar(5),
    constraint uk_branch_code unique (intermediary_id, code)
);

-- Envers (bdi-config-audit conventions: _aud suffix, rev/revtype columns, revinfo revisions)
create sequence revinfo_seq start with 1 increment by 50;
create table revinfo (
    rev      integer primary key,
    revtstmp bigint
);
create table intermediary_aud (
    id            bigint not null,
    rev           integer not null references revinfo (rev),
    revtype       smallint,
    abi           varchar(5),
    name          varchar(200),
    type_code     varchar(20),
    status        varchar(20),
    street        varchar(200),
    city          varchar(100),
    province      varchar(2),
    postal_code   varchar(5),
    registered_at timestamp(6) with time zone,
    updated_at    timestamp(6) with time zone,
    primary key (id, rev)
);
