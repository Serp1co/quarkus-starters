# Ansible: configuration and deployment of Quarkus applications

The platform side of the programme (design notes §3 stage 1 and §5), as a proposal that runs. The point
of comparison is the EAP workflow in use today: one small YAML fragment per resource, vault placeholders,
an `artifacts:` link, Ansible rendering `standalone.xml` and deploying the EAR. Nothing of that workflow is
lost; what changes is what the fragments render into.

| Today, EAP 8 | Tomorrow, Quarkus |
|---|---|
| `datasource.cruscottoDS.yaml`: `name`, `jndi`, `driver`, `user`, `password`, `xa_oracle_url`, `pool_max`, ... | the same fragment, translated by the role into `quarkus.datasource.*` keys; `driver` and `type: xa-data-source` become the `bdi-jpa-*` starter the developer chose; `jndi` has no equivalent, the code binds to the logical datasource |
| `system_properties.procedimenti_username.yml`: `name`, `value` | a `property` fragment: `name: quarkus.log.level`, `value: INFO`, landing in the rendered file |
| `artifacts: smart-ear.ear` links a fragment to a deployment | a fragment belongs to one application directory, `apps/<app>/`; one application, one process, one artifact |
| `${fromvault.app.PASSWORD}` resolved by the vault task | the same placeholder syntax; the role routes every placeholder to `secrets.yaml` (0600) and refuses a secret written in clear text |
| rendered `standalone.xml`, developers bind to JNDI names | rendered `application-<env>.yaml`, developers bind to logical config keys |
| deploy the EAR through the management CLI | fetch the versioned zip from Nexus, release/`current` layout, hardened systemd unit, restart gated on `/q/health/ready` |
| nothing checks the configuration before deploying | the rendered files are validated against `META-INF/config-contract.json` of the exact artifact, before the host is touched |
| nothing checks the deployment after | `/q/platform` reports version, profile and the source of every key |

The full catalogue of EAP fragment files and their parallel is in [`fragments/README.md`](fragments/README.md),
with one skeleton per file.

## Layout

```
ansible/
  playbooks/deploy.yml        rolling deploy of one app to one environment (serial: 1, health-gated, verified)
  playbooks/rollback.yml      flip current to the previous release, restart, gate, verify
  playbooks/validate.yml      render + validate only (the AAP pre-deploy check, or a CI check on the inventory)
  roles/bdi_quarkus_app/      render -> artifact -> validate -> install -> service -> verify
    filter_plugins/bdi.py     fragment translation, secret routing, contract check (pure Python)
    templates/app.service.j2  the hardened unit; never in an application repository
  inventory/sandbox/          one "VM" (the controller itself), a uat group, the fragments of POC A, a vault stand-in
    apps/<app>/*.yaml         the fragments ops write: datasource.*, config.*, properties, log_categories, keystore, ...
  fragments/                  one skeleton per EAP fragment file, with its Quarkus parallel and owner
  tests/                      unit tests of the translation filter (python3 -m pytest ansible/tests)
```

## The flow, task by task

1. **render** (controller). Reads `apps/<app>/*.yaml`, translates `datasource` and `property` fragments,
   merges `config` fragments as they are (they are YAML subtrees of the final file, straight from the
   contract), and fails on two fragments that disagree on a key. Every `${fromvault.<scope>.<NAME>}` is
   pulled out into a secret reference and resolved through the vault of the estate. In the sandbox the
   resolution reads `vault/<env>.yml`; in the bank that one task becomes a `community.hashi_vault` or
   CyberArk lookup, or the file is `ansible-vault` encrypted. Translation notes tell ops what had no
   equivalent (`validate_on_match`, `jndi`, `artifacts`).
2. **artifact** (host). `get_url` of the versioned zip from Nexus into `/opt/bdi/<app>/releases/<version>/`,
   unpack, read `META-INF/config-contract.json` out of the application jar.
3. **validate**. Required keys present (in the rendered file or the secrets file), secret keys not in the
   rendered file, undeclared keys reported. Fails before any file or service changes, with the list.
4. **install**. `application-<env>.yaml` (0640) and `secrets.yaml` (0600) under `/etc/bdi/<app>/`, the
   previous release remembered in `/opt/bdi/<app>/previous`, `current` pointed at the new release.
5. **service** (`bdi_service_manager: systemd`). The unit from the template, `daemon-reload`, restart,
   then `/q/health/ready` polled until 200. With `serial: 1` this is the rolling update behind the load
   balancer; `bdi_health_retries` is the gate's patience.
6. **verify**. `/q/platform`: running version equals the requested one, the environment is the active
   profile, `missing` is empty, and the source of every contract key is printed for the job log.

Rollback flips `current` back to `previous`, restarts and verifies. The rendered configuration is not
touched: it belongs to the environment, not to the release.

## What the rendered file looks like

From the three fragments in `inventory/sandbox/apps/poc-a-jakarta-classic/`:

```yaml
# Rendered by Ansible for poc-a-jakarta-classic 1.0.0-SNAPSHOT, env=uat. Do not edit: change the fragments under apps/poc-a-jakarta-classic.
ledger:
  currency: EUR
  transfer:
    max-amount: '1500.00'
    blocked-ibans:
    - IT66X0100503200000012345678
quarkus:
  datasource:
    metrics:
      enabled: true
    jdbc:
      url: jdbc:postgresql://127.0.0.1:5432/poc
      transactions: xa
      min-size: 2
      max-size: 6
      transaction-isolation-level: read-committed
  log:
    level: INFO
```

`secrets.yaml` holds `quarkus.datasource.username` and `password`, because both carried a placeholder.

## Running it in the sandbox

The sandbox inventory targets the controller itself, with no systemd and no `/etc`:

```bash
cd ansible
ZIP=$(ls ../pocs/a-jakarta-classic/target/*-quarkus-app.zip)          # ./mvnw package builds it
E="-e bdi_app=poc-a-jakarta-classic -e bdi_version=1.0.0-SNAPSHOT -e bdi_artifact_url=file://$ZIP \
   -e bdi_root=/tmp/vm/opt/bdi -e bdi_etc=/tmp/vm/etc/bdi -e bdi_service_manager=none -e bdi_become=false"
ansible-playbook playbooks/validate.yml $E                            # render + contract check only
ansible-playbook playbooks/deploy.yml   $E --skip-tags verify          # files and release; no systemd here
# start it the way the unit would, then verify
( cd /tmp/vm/opt/bdi/poc-a-jakarta-classic/current && QUARKUS_PROFILE=uat \
  QUARKUS_CONFIG_LOCATIONS=/tmp/vm/etc/bdi/poc-a-jakarta-classic/application-uat.yaml,/tmp/vm/etc/bdi/poc-a-jakarta-classic/secrets.yaml \
  java -jar quarkus-app/quarkus-run.jar ) &
ansible-playbook playbooks/deploy.yml   $E --tags verify
ansible-playbook playbooks/rollback.yml $E
ansible-playbook playbooks/deploy.yml   $E --check --diff --skip-tags verify   # drift detection
```

On a RHEL host the same playbooks run with the defaults of `group_vars/all.yml` (`/opt/bdi`, `/etc/bdi`,
the `bdi-app` service user, systemd) and `become: true`. Provisioning of the host (JDK, user,
directories, SELinux contexts, firewalld for 8080 and 9000, journald) is a separate role on
`redhat.rhel_system_roles`, to come with cookbook 14.

## As an AAP job template

- Project: this repository (or the bank-owned collection it becomes), execution environment with the
  collections of `requirements.yml` and the vault client.
- Inventory: one per environment group, `apps/` versioned next to it. Who edits `apps/` is the open
  question 6 of the design notes: ops only, or developers through a pull request that ops approve.
- Survey: `bdi_app`, `bdi_version`, `limit` (the environment group). `bdi_artifact_url` derived from the
  Nexus layout, or asked.
- Workflow: `validate.yml` → approval node (prod) → `deploy.yml` (serial 1) → on failure `rollback.yml`.
- Credentials: the vault credential type of the estate feeds the placeholder resolution; the machine
  credential is the platform's, never the application's.

## Known gaps

- Stage 2 (Podman + Quadlet) and stage 3 (OpenShift) are not in the role yet; the `bdi_service_manager`
  switch is where the Quadlet variant plugs in, and stage 3 renders the same fragments into a ConfigMap
  and a Secret.
- `validate_on_match`, `enable_statistics` and friends: the translation table in `filter_plugins/bdi.py`
  covers the fragment fields seen so far; extend it with the fields of the real inventory.
- The contract lists only the keys the starters declare; Agroal keys such as `min-size` are reported as
  undeclared, not refused. Either the config modules declare more keys, or the check learns the Quarkus
  key catalogue of the stream.
