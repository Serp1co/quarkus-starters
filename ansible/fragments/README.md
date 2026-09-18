# Fragment catalogue: the EAP `standalone.xml` files and their Quarkus parallel

One skeleton per YAML the EAP playbooks template today. Each skeleton documents the fields, the Quarkus key
or platform setting they become, and the **owner** on the Quarkus side: an *application fragment* under
`apps/<app>/` rendered into `application-<env>.yaml`, a *platform* setting rendered into the systemd unit or
handled by the roles, or *dropped* because the concern no longer exists. `status` says whether the role
translates it today (`translated`), only recognises it (`planned`), or ignores it (`not applicable`).

| EAP file | Kind | Quarkus parallel | Owner | Status |
|---|---|---|---|---|
| `datasources.yml`, `datasource.<name>.yaml` | `datasource` | `quarkus.datasource[.<name>].*`; XA and driver come from the `bdi-jpa-*` starter; `jndi` gone | application | translated |
| `system_properties.yml`, `system_properties.<name>.yml` | `properties` / `property` | keys of the rendered file; EAP-internal `jboss.*` dropped | application | translated |
| `log_categories.yml` | `log_categories` | `quarkus.log.category."<name>".level` | application | translated |
| `log_handlers.yml` | `log_handlers` | file handler to `quarkus.log.file.*`, syslog to `quarkus.log.syslog.*`; async and periodic dropped: stdout JSON to journald is the standard | application, mostly platform | partial |
| `syslog.yml` | `syslog` | `quarkus.log.syslog.*`, or rsyslog forwarding of journald on the host | application or platform | translated |
| `keystore.yml` | `keystore` | TLS registry `quarkus.tls[.<name>].*`, `reload-period` for AAP-rotated certificates; passwords from the vault | application + platform | translated |
| `java_opts.yml` | `java_opts` | `JAVA_OPTS` of the unit (`MaxRAMPercentage`, GC, extras) | platform | translated (unit) |
| `jboss-osc-raccolta-be1.yml` (one server instance) | `instance` | one unit per instance: `quarkus.http.port`, `quarkus.management.port`, bind host, `quarkus.transaction-manager.node-name` | platform (host_vars) | translated |
| `release.yml` | `release` | version and artifact URL, defaults for the deploy survey | inventory (GitOps) | translated |
| `vault.yml` | `vault` | the `${fromvault.<scope>.*}` scopes and the vault paths they resolve from; values only ever in `secrets.yaml` | platform + application | translated (scopes) |
| `cluster.yml` | `cluster` | no runtime cluster: stateless behind the LB; HA timers to clustered Quartz; caches to Data Grid; per-instance node name | platform + starters (planned) | planned |
| `etuning.yml` | `tuning` | `quarkus.thread-pool.*`, `quarkus.http.limits.*`, `quarkus.http.io-threads`; EJB/JCA pools gone | platform defaults (`bdi-config-*`) | planned |
| `security_domains.yml` | `security_domain` | `bdi-security-oidc` (RHBK) or `bdi-security-ldap` (AD/LDAP) starter; `web.xml` constraints to `quarkus.http.auth.permission.*` | application + platform | planned (cookbook 12) |
| `rbac.yml` | `rbac` | none: AAP RBAC on the job templates, firewalld on the management port, optionally `quarkus.management.auth.*` | AAP | not applicable |

Conventions the skeletons follow:

- a fragment is YAML with a `kind`; anything else is a `config` fragment, a subtree of the final file;
- vault placeholders keep the EAP syntax `${fromvault.<scope>.<NAME>}` and always end in `secrets.yaml`;
- what the artifact decides (driver, XA, extensions) is not configuration: the role notes it and moves on;
- what has no Quarkus counterpart is dropped with a note in the job log, never silently.

The translation lives in [`../roles/bdi_quarkus_app/filter_plugins/bdi.py`](../roles/bdi_quarkus_app/filter_plugins/bdi.py),
unit-tested by [`../tests/test_bdi_filters.py`](../tests/test_bdi_filters.py). To add a kind: a skeleton
here, a `_translate_<kind>` function, a test, a row in this table.
