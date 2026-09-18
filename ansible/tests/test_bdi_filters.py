"""python3 -m pytest ansible/tests  (needs ansible-core on the path for AnsibleFilterError)"""
import importlib.util
import pathlib

import pytest

ROLE = pathlib.Path(__file__).resolve().parent.parent / "roles/bdi_quarkus_app"
SPEC = importlib.util.spec_from_file_location(
    "bdi", pathlib.Path(__file__).resolve().parent.parent / "roles/bdi_quarkus_app/filter_plugins/bdi.py")
bdi = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bdi)


def test_datasource_fragment_translates_the_eap_vocabulary():
    out = bdi.bdi_render([{
        "_file": "datasource.default.yaml", "kind": "datasource", "name": "default", "type": "xa-data-source",
        "driver": "oracle", "jndi": "java:/jdbc/X", "xa_oracle_url": "jdbc:oracle:thin:@db", "user": "${fromvault.app.USERNAME}",
        "password": "${fromvault.app.PASSWORD}", "pool_max": 6, "pool_min": 5, "enable_statistics": True,
        "trx_isolation": "TRANSACTION_READ_COMMITTED", "validate_on_match": True, "artifacts": "smart-ear.ear"}])
    ds = out["config"]["quarkus"]["datasource"]
    assert ds["jdbc"] == {"url": "jdbc:oracle:thin:@db", "min-size": 5, "max-size": 6,
                          "transaction-isolation-level": "read-committed"}
    assert out["platform"]["xa_datasources"] == ["default"], "XA is a build-time fact of the artifact, checked, not rendered"
    assert "metrics" not in ds, "datasource metrics are a build-time choice of the artifact, not a rendered key"
    assert any("enable_statistics" in w for w in out["warnings"])
    assert "username" not in ds and "password" not in ds  # routed to the secrets file
    assert out["secret_refs"] == {"quarkus.datasource.username": {"scope": "app", "name": "USERNAME"},
                                  "quarkus.datasource.password": {"scope": "app", "name": "PASSWORD"}}
    assert any("validate_on_match" in w for w in out["warnings"])
    assert any("jndi" in w for w in out["warnings"])


def test_named_datasource_and_properties_and_categories():
    out = bdi.bdi_render([
        {"kind": "datasource", "name": "audit", "url": "jdbc:postgresql://db/audit"},
        {"kind": "properties", "properties": [{"name": "ledger.currency", "value": "EUR"},
                                               {"name": "jboss.as.management.blocking.timeout", "value": "600"}]},
        {"kind": "log_categories", "categories": [{"name": "it.bancaditalia", "level": "debug"}]},
    ])
    assert out["config"]["quarkus"]["datasource"]["audit"]["jdbc"]["url"] == "jdbc:postgresql://db/audit"
    assert out["config"]["ledger"] == {"currency": "EUR"}
    assert out["config"]["quarkus"]["log"]["category"] == {"it.bancaditalia": {"level": "DEBUG"}}
    assert any("EAP-internal" in w for w in out["warnings"])
    flat = bdi._flatten(out["config"], stringify=True)
    assert flat['quarkus.log.category."it.bancaditalia".level'] == "DEBUG"


def test_platform_outputs_and_planned_kinds():
    out = bdi.bdi_render([
        {"kind": "java_opts", "heap": {"max_ram_percentage": 60}, "gc": "g1", "extra": ["-Dx=1"]},
        {"kind": "instance", "name": "be1", "http_port": 8180, "management_port": 9100, "transaction_node_name": "be1",
         "transaction_object_store": "/var/lib/bdi/x/be1/ObjectStore"},
        {"kind": "release", "version": "1.2.3", "artifact_url": "https://nexus/x.zip"},
        {"kind": "vault", "provider": "hashicorp", "scopes": {"app": "secret/data/uat/x/app"}},
        {"kind": "cluster", "_file": "cluster.yaml"},
        {"kind": "rbac", "_file": "rbac.yaml"},
    ])
    assert out["platform"]["java_opts"] == "-XX:MaxRAMPercentage=60 -XX:+UseG1GC -Dx=1"
    assert out["platform"]["instance"] == "be1"
    assert out["platform"]["release"] == {"version": "1.2.3", "artifact_url": "https://nexus/x.zip"}
    assert out["platform"]["vault"]["scopes"]["app"] == "secret/data/uat/x/app"
    assert out["config"]["quarkus"]["http"]["port"] == 8180
    assert out["config"]["quarkus"]["transaction-manager"] == {"node-name": "be1", "object-store": {"directory": "/var/lib/bdi/x/be1/ObjectStore"}}
    assert len([w for w in out["warnings"] if "cluster.yaml" in w or "rbac.yaml" in w]) == 2


def test_keystore_syslog_and_file_handler():
    out = bdi.bdi_render([
        {"kind": "keystore", "name": "default", "path": "/etc/bdi/x/tls/server.p12", "password": "${fromvault.tls.KS}",
         "reload_period": "1H", "serve_https": True},
        {"kind": "syslog", "endpoint": "siem:514", "protocol": "tcp", "app_name": "x", "json": True},
        {"kind": "log_handlers", "handlers": [{"type": "file", "path": "/var/log/x.log", "rotate_size": "50M", "max_backups": 3},
                                               {"type": "async"}]},
    ])
    q = out["config"]["quarkus"]
    assert q["tls"]["key-store"]["p12"]["path"] == "/etc/bdi/x/tls/server.p12"
    assert q["tls"]["reload-period"] == "1H"
    assert q["http"]["insecure-requests"] == "disabled"
    assert out["secret_refs"]['quarkus.tls.key-store.p12.password'] == {"scope": "tls", "name": "KS"}
    assert q["log"]["syslog"] == {"enable": True, "endpoint": "siem:514", "protocol": "tcp", "app-name": "x", "json": {"enabled": True}}
    assert q["log"]["file"]["rotation"] == {"max-file-size": "50M", "max-backup-index": 3}
    assert any("async handler dropped" in w for w in out["warnings"])


def test_conflicting_fragments_and_unknown_kind_fail():
    with pytest.raises(Exception, match="disagree"):
        bdi.bdi_render([{"kind": "config", "a": {"b": 1}}, {"kind": "config", "a": {"b": 2}}])
    with pytest.raises(Exception, match="unknown fragment kind"):
        bdi.bdi_render([{"kind": "nope"}])


def test_contract_check():
    contract = {"keys": [
        {"name": "ledger.transfer.max-amount", "required": True, "secret": False},
        {"name": "quarkus.datasource.password", "required": True, "secret": True},
        {"name": "quarkus.log.category.*.level", "required": False, "secret": False},
    ]}
    result = bdi.bdi_check({"quarkus": {"datasource": {"password": "clear"}, "log": {"category": {"a.b": {"level": "INFO"}}},
                                        "http": {"port": 8080}}},
                           {}, contract)
    assert result["missing"] == ["ledger.transfer.max-amount"]
    assert result["misplaced"] == ["quarkus.datasource.password"]
    assert result["unknown"] == ["quarkus.http.port"]

def test_contract_check_reports_unmapped_roles():
    contract = {"keys": [{"name": "quarkus.http.auth.roles-mapping.*", "required": False, "secret": False}],
                "roles": ["admin", "operator", "reader"]}
    rendered = {"quarkus": {"http": {"auth": {"roles-mapping": {"APP-ADMINS": "admin,reader", "APP-READERS": "reader"}}}}}
    result = bdi.bdi_check(rendered, {}, contract)
    assert result["missing"] == ["role:operator"]
    assert result["unknown"] == []
    # no security module in the contract: roles are not checked
    assert bdi.bdi_check(rendered, {}, {"keys": [], "roles": ["admin"]})["missing"] == []


def test_contract_check_types_wildcards_phases_and_conditions():
    contract = {"keys": [
        {"name": "quarkus.management.port", "type": "int", "required": True, "min": "1", "max": "65535"},
        {"name": "quarkus.datasource.password", "type": "String", "required": True, "secret": True},
        {"name": "quarkus.datasource.*.password", "type": "String", "secret": True},
        {"name": "quarkus.datasource.*.jdbc.max-size", "type": "int", "min": "1", "max": "500"},
        {"name": "quarkus.datasource.jdbc.transactions", "type": "String", "default": "enabled", "phase": "build-time",
         "values": ["enabled", "xa", "disabled"]},
        {"name": "quarkus.oidc.application-type", "type": "String", "default": "service", "values": ["service", "web-app"]},
        {"name": "quarkus.oidc.credentials.secret", "type": "String", "secret": True, "required-if": "quarkus.oidc.application-type=web-app"},
        {"name": "quarkus.security.ldap.cache.max-age", "type": "Duration", "default": "60S"},
        {"name": "quarkus.flyway.migrate-at-start", "type": "boolean", "default": "true"},
    ]}
    rendered = {"quarkus": {
        "management": {"port": "not-a-number"},
        "datasource": {"jdbc": {"transactions": "xa"}, "reporting": {"password": "clear", "jdbc": {"max-size": 9000}}},
        "oidc": {"application-type": "web-app"},
        "security": {"ldap": {"cache": {"max-age": "soon"}}},
        "flyway": {"migrate-at-start": "yes"},
    }}
    result = bdi.bdi_check(rendered, {"quarkus": {"datasource": {"password": ""}}}, contract, xa_datasources=["default"])
    assert result["missing"] == ["quarkus.datasource.password", "quarkus.oidc.credentials.secret"]
    assert result["misplaced"] == ["quarkus.datasource.reporting.password"]
    assert result["fixed"] == ["quarkus.datasource.jdbc.transactions"]
    assert [i.split(":")[0] for i in result["invalid"]] == [
        "quarkus.management.port", "quarkus.security.ldap.cache.max-age", "quarkus.flyway.migrate-at-start",
        "quarkus.datasource.reporting.jdbc.max-size"]
    assert result["unknown"] == []
    assert result["xa"] == ["default"], "the artifact is built with local transactions: the xa-data-source fragment is refused"
    # a clean rendering, and an artifact built with bdi-jpa-xa
    xa_contract = {"keys": contract["keys"][:4] + [{"name": "quarkus.datasource.jdbc.transactions", "type": "String", "default": "xa", "phase": "build-time"}]}
    ok = bdi.bdi_check({"quarkus": {"management": {"port": 9000}, "datasource": {"reporting": {"jdbc": {"max-size": 5}}}}},
                       {"quarkus": {"datasource": {"password": "s3cr3t", "reporting": {"password": "x"}}}}, xa_contract, ["default"])
    assert ok == {"missing": [], "misplaced": [], "fixed": [], "invalid": [], "unknown": [], "xa": []}


def test_datasource_xa_is_a_requirement_not_a_key():
    out = bdi.bdi_render([{"kind": "datasource", "name": "default", "type": "xa-data-source", "url": "jdbc:postgresql://db/x", "user": "u"}])
    assert "transactions" not in out["config"]["quarkus"]["datasource"].get("jdbc", {})
    assert out["platform"]["xa_datasources"] == ["default"]


def test_verify_compares_effective_values_sources_and_revisions():
    rendered = {"quarkus": {"log": {"level": "INFO"}, "http": {"port": 8080}}}
    platform = {
        "application": {"name": "app", "version": "1.0", "profiles": ["uat"]},
        "revision": {"config": "abc", "secrets": "2026-09-18T00:00:00Z"},
        "missing": [], "violations": ["quarkus.management.port: 'x' is not a valid int"],
        "config": [
            {"key": "quarkus.log.level", "present": True, "value": "DEBUG", "source": "SysPropConfigSource", "secret": False},
            {"key": "quarkus.http.port", "present": True, "value": "8080", "source": "YamlConfigSource[source=file:/etc/bdi/app/application-uat.yaml]", "secret": False},
            {"key": "quarkus.management.port", "present": True, "value": "9001", "source": "EnvConfigSource", "secret": False},
            {"key": "quarkus.datasource.password", "present": True, "value": "******", "source": "YamlConfigSource[source=file:/etc/bdi/app/secrets.yaml]", "secret": True},
        ]}
    problems = bdi.bdi_verify(platform, rendered, "uat", "1.0", "abc")
    assert any(p.startswith("quarkus.log.level: effective 'DEBUG'") for p in problems)
    assert any(p.startswith("quarkus.management.port: set outside the platform by EnvConfigSource") for p in problems)
    assert any("contract violation" in p for p in problems)
    assert len(problems) == 3
    assert bdi.bdi_verify(platform, rendered, "prod", "2.0", "def")[:3] == [
        "running version '1.0', expected '2.0'", "active profiles ['uat'], expected prod",
        "quarkus.management.port: 'x' is not a valid int (contract violation)"]
    good = dict(platform, violations=[], config=[c for c in platform["config"] if c["key"] in ("quarkus.http.port", "quarkus.datasource.password")])
    assert bdi.bdi_verify(good, rendered, "uat", "1.0", "abc") == []


def test_systemd_environment_keeps_every_flag(tmp_path):
    import shutil
    import subprocess
    from jinja2 import Environment, FileSystemLoader
    assert bdi.bdi_systemd_quote('-XX:+UseG1GC -Dx=1 -Dq="a b"') == '"-XX:+UseG1GC -Dx=1 -Dq=\\"a b\\""'
    env = Environment(loader=FileSystemLoader(str(ROLE / "templates")))
    env.filters["bdi_systemd_quote"] = bdi.bdi_systemd_quote
    vars_ = dict(ansible_managed="test", bdi_app="x", bdi_env="uat", bdi_service_user="bdi-app", bdi_service_group="bdi-app",
                 bdi_root=str(tmp_path / "opt"), bdi_etc=str(tmp_path / "etc"), bdi_var=str(tmp_path / "var"),
                 bdi_java="/usr/bin/java", bdi_java_opts="-XX:MaxRAMPercentage=60 -XX:+UseG1GC -Dx=1")
    environment = env.get_template("environment.j2").render(**vars_)
    assert 'JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseG1GC -Dx=1"' in environment
    (tmp_path / "etc" / "x").mkdir(parents=True)
    (tmp_path / "etc" / "x" / "environment").write_text(environment)
    unit = env.get_template("app.service.j2").render(**vars_)
    assert "Environment=JAVA_OPTS" not in unit and "EnvironmentFile=" in unit
    if shutil.which("systemd-analyze"):
        unit_file = tmp_path / "bdi-x.service"
        unit_file.write_text(unit)
        result = subprocess.run(["systemd-analyze", "verify", str(unit_file)], capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        assert "-XX:+UseG1GC" not in result.stderr and "Invalid environment" not in result.stderr
