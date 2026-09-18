"""python3 -m pytest ansible/tests  (needs ansible-core on the path for AnsibleFilterError)"""
import importlib.util
import pathlib

import pytest

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
    assert ds["jdbc"] == {"url": "jdbc:oracle:thin:@db", "transactions": "xa", "min-size": 5, "max-size": 6,
                          "transaction-isolation-level": "read-committed"}
    assert ds["metrics"] == {"enabled": True}
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
