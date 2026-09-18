"""Filters of the bdi_quarkus_app role: fragment translation, secret routing, contract validation.

Pure functions, unit-tested by ansible/tests/test_bdi_filters.py. They translate the EAP-era fragment
vocabulary (one kind per standalone.xml concern, see ansible/fragments/) into Quarkus keys, split what
belongs to the unit (platform output) from what belongs to application-<env>.yaml, route every
${fromvault.<scope>.<NAME>} placeholder to the secrets file, and check the result against the artifact's
META-INF/config-contract.json before anything touches a host.
"""
import re

from ansible.errors import AnsibleFilterError

PLACEHOLDER = re.compile(r"^\$\{fromvault\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_]+)\}$")

# kinds that are understood but produce no keys yet; the note tells ops where the concern went
PLANNED = {
    "cluster": "planned: HA timers -> bdi-scheduler (quartz clustered), caches -> bdi-cache-datagrid, sessions -> stateless; per-instance node name in instance.yaml",
    "tuning": "planned: worker/io threads and HTTP limits become bdi-config defaults (quarkus.thread-pool.*, quarkus.http.limits.*); EJB and JCA pools have no equivalent",
    "security_domain": "planned with the security POC: bdi-security-oidc / bdi-security-ldap starters and quarkus.http.auth.permission.* path policies",
    "rbac": "not applicable: management RBAC becomes AAP RBAC on the job templates plus firewalld on the management port",
}


def _deep_merge(base, extra, path=""):
    out = dict(base)
    for key, value in extra.items():
        here = f"{path}.{key}" if path else key
        if key in out and isinstance(out[key], dict) and isinstance(value, dict):
            out[key] = _deep_merge(out[key], value, here)
        elif key in out and out[key] != value:
            raise AnsibleFilterError(f"fragments disagree on {here}: {out[key]!r} vs {value!r}")
        else:
            out[key] = value
    return out


def _segment(key):
    key = str(key)
    return f'"{key}"' if "." in key else key


def _flatten(tree, prefix="", stringify=False):
    """Dotted keys the way SmallRye names them (segments with dots are quoted). With stringify, values
    become the strings Quarkus would see (lists comma-joined)."""
    flat = {}
    for key, value in tree.items():
        name = f"{prefix}.{_segment(key)}" if prefix else _segment(key)
        if isinstance(value, dict):
            flat.update(_flatten(value, name, stringify))
        elif not stringify:
            flat[name] = value
        elif isinstance(value, list):
            flat[name] = ",".join(str(v) for v in value)
        elif isinstance(value, bool):
            flat[name] = "true" if value else "false"
        elif value is None:
            flat[name] = ""
        else:
            flat[name] = str(value)
    return flat


def _split_key(name):
    """quarkus.log.category."it.bancaditalia".level -> [quarkus, log, category, it.bancaditalia, level]"""
    return [p.strip('"') for p in re.findall(r'"[^"]*"|[^.]+', name)]


def _unflatten(flat):
    tree = {}
    for name, value in flat.items():
        node = tree
        parts = _split_key(name)
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = value
    return tree


def _file(fragment):
    return fragment.get("_file", fragment.get("kind", "fragment"))


def _translate_datasource(fragment, warnings):
    name = fragment.get("name", "default")
    file = _file(fragment)
    jdbc, ds = {}, {}
    url = fragment.get("url") or fragment.get("xa_oracle_url") or fragment.get("xa_url") or fragment.get("connection_url")
    if url:
        jdbc["url"] = url
    if fragment.get("type") == "xa-data-source" or fragment.get("xa"):
        jdbc["transactions"] = "xa"
    if "pool_min" in fragment:
        jdbc["min-size"] = int(fragment["pool_min"])
    if "pool_max" in fragment:
        jdbc["max-size"] = int(fragment["pool_max"])
    if "trx_isolation" in fragment:
        level = str(fragment["trx_isolation"]).removeprefix("TRANSACTION_").lower().replace("_", "-")
        jdbc["transaction-isolation-level"] = level
    if "user" in fragment:
        ds["username"] = fragment["user"]
    if "password" in fragment:
        ds["password"] = fragment["password"]
    if fragment.get("enable_statistics"):
        ds["metrics"] = {"enabled": True}
    if "validate_on_match" in fragment:
        warnings.append(f"{file}: validate_on_match has no Quarkus key; Agroal validates connections in the background (quarkus.datasource.jdbc.background-validation-interval)")
    if "driver" in fragment:
        warnings.append(f"{file}: driver={fragment['driver']} is decided by the artifact (bdi-jpa-{fragment['driver']} starter), not by configuration")
    for ignored in ("jndi", "artifacts", "state", "enabled"):
        if ignored in fragment:
            warnings.append(f"{file}: {ignored} has no equivalent on Quarkus and was ignored")
    if jdbc:
        ds["jdbc"] = jdbc
    tree = ds if name == "default" else {name: ds}
    return {"quarkus": {"datasource": tree}}


def _translate_log_categories(fragment, warnings):
    categories = {}
    for entry in fragment.get("categories", []):
        categories[entry["name"]] = {"level": str(entry["level"]).upper()}
    return {"quarkus": {"log": {"category": categories}}} if categories else {}


def _translate_syslog(fragment, warnings):
    syslog = {"enable": bool(fragment.get("enabled", True))}
    for src, dst in (("endpoint", "endpoint"), ("protocol", "protocol"), ("app_name", "app-name"),
                     ("facility", "facility"), ("level", "level")):
        if src in fragment:
            syslog[dst] = fragment[src]
    if "json" in fragment:
        syslog["json"] = {"enabled": bool(fragment["json"])}
    return {"quarkus": {"log": {"syslog": syslog}}}


def _translate_log_handlers(fragment, warnings):
    tree = {}
    for handler in fragment.get("handlers", []):
        kind = handler.get("type")
        if kind == "file":
            file_cfg = {"enable": True, "path": handler["path"]}
            rotation = {}
            if "rotate_size" in handler:
                rotation["max-file-size"] = handler["rotate_size"]
            if "max_backups" in handler:
                rotation["max-backup-index"] = int(handler["max_backups"])
            if rotation:
                file_cfg["rotation"] = rotation
            if "json" in handler:
                file_cfg["json"] = {"enabled": bool(handler["json"])}
            tree = _deep_merge(tree, {"quarkus": {"log": {"file": file_cfg}}})
        elif kind == "syslog":
            tree = _deep_merge(tree, _translate_syslog(handler, warnings))
        else:
            warnings.append(f"{_file(fragment)}: {kind} handler dropped; stdout JSON to journald is the standard (bdi-config-observability)")
    return tree


def _translate_keystore(fragment, warnings):
    name = fragment.get("name", "default")
    tls = {}
    if "path" in fragment:
        tls["key-store"] = {"p12": {"path": fragment["path"]}}
        if "password" in fragment:
            tls["key-store"]["p12"]["password"] = fragment["password"]
        if "alias" in fragment:
            tls["key-store"]["p12"]["alias"] = fragment["alias"]
    if "truststore_path" in fragment:
        tls["trust-store"] = {"p12": {"path": fragment["truststore_path"]}}
        if "truststore_password" in fragment:
            tls["trust-store"]["p12"]["password"] = fragment["truststore_password"]
    if "reload_period" in fragment:
        tls["reload-period"] = fragment["reload_period"]
    tree = {"quarkus": {"tls": tls if name == "default" else {name: tls}}}
    if fragment.get("serve_https"):
        http = {"insecure-requests": "disabled"}
        if name != "default":
            http["tls-configuration-name"] = name
        tree = _deep_merge(tree, {"quarkus": {"http": http}})
    return tree


def _translate_properties(fragment, warnings):
    flat = {}
    for entry in fragment.get("properties", []):
        name = entry["name"]
        if name.startswith("jboss.") or name.startswith("org.jboss."):
            warnings.append(f"{_file(fragment)}: {name} is an EAP-internal property, dropped")
            continue
        flat[name] = entry["value"]
    return _unflatten(flat)


def _translate_instance(fragment, warnings, platform):
    keys = {}
    if "http_port" in fragment:
        keys["quarkus.http.port"] = int(fragment["http_port"])
    if "management_port" in fragment:
        keys["quarkus.management.port"] = int(fragment["management_port"])
    if "bind_address" in fragment:
        keys["quarkus.http.host"] = fragment["bind_address"]
        keys["quarkus.management.host"] = fragment["bind_address"]
    if "transaction_node_name" in fragment:
        keys["quarkus.transaction-manager.node-name"] = fragment["transaction_node_name"]
    if "transaction_object_store" in fragment:
        keys["quarkus.transaction-manager.object-store.directory"] = fragment["transaction_object_store"]
    if "port_offset" in fragment:
        warnings.append(f"{_file(fragment)}: port_offset is an EAP habit; give http_port and management_port explicitly")
    platform["instance"] = fragment.get("name", "default")
    return _unflatten(keys)


def _translate_java_opts(fragment, platform):
    flags = []
    heap = fragment.get("heap", {})
    if "max_ram_percentage" in heap:
        flags.append(f"-XX:MaxRAMPercentage={heap['max_ram_percentage']}")
    gc = fragment.get("gc")
    if gc:
        flags.append({"parallel": "-XX:+UseParallelGC", "g1": "-XX:+UseG1GC", "serial": "-XX:+UseSerialGC"}.get(gc, gc))
    flags.extend(fragment.get("extra", []))
    if fragment.get("debug"):
        flags.append("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
    platform["java_opts"] = " ".join(flags)


def bdi_render(fragments):
    """Merge the fragments of one application: config tree, secret refs, platform (unit-level) settings."""
    config, warnings, platform = {}, [], {}
    for fragment in fragments:
        kind = fragment.get("kind", "config")
        if kind == "datasource":
            tree = _translate_datasource(fragment, warnings)
        elif kind == "property":
            tree = _unflatten({fragment["name"]: fragment["value"]})
        elif kind == "properties":
            tree = _translate_properties(fragment, warnings)
        elif kind == "log_categories":
            tree = _translate_log_categories(fragment, warnings)
        elif kind == "log_handlers":
            tree = _translate_log_handlers(fragment, warnings)
        elif kind == "syslog":
            tree = _translate_syslog(fragment, warnings)
        elif kind == "keystore":
            tree = _translate_keystore(fragment, warnings)
        elif kind == "instance":
            tree = _translate_instance(fragment, warnings, platform)
        elif kind == "java_opts":
            _translate_java_opts(fragment, platform)
            tree = {}
        elif kind == "release":
            platform["release"] = {k: v for k, v in fragment.items() if k in ("version", "artifact_url", "checksum")}
            tree = {}
        elif kind == "vault":
            platform["vault"] = {"provider": fragment.get("provider"), "scopes": fragment.get("scopes", {})}
            tree = {}
        elif kind in PLANNED:
            warnings.append(f"{_file(fragment)}: {PLANNED[kind]}")
            tree = {}
        elif kind == "config":
            tree = {k: v for k, v in fragment.items() if k not in ("kind", "_file")}
        else:
            raise AnsibleFilterError(f"{_file(fragment)}: unknown fragment kind {kind!r}")
        config = _deep_merge(config, tree, "")
    flat = _flatten(config)
    secret_refs = {}
    for key, value in flat.items():
        match = PLACEHOLDER.match(value) if isinstance(value, str) else None
        if match:
            secret_refs[key] = {"scope": match.group(1), "name": match.group(2)}
    public = {k: v for k, v in flat.items() if k not in secret_refs}
    return {"config": _unflatten(public), "secret_refs": secret_refs, "platform": platform, "warnings": warnings}


def bdi_unflatten(flat):
    return _unflatten(flat)


def bdi_check(config, secrets, contract):
    """Validate rendered config + secrets against the artifact's contract (missing, misplaced, unknown)."""
    flat_config, flat_secrets = _flatten(config, stringify=True), _flatten(secrets, stringify=True)
    keys = contract.get("keys", [])
    missing, misplaced = [], []
    for key in keys:
        name = key["name"]
        if "*" in name:
            continue
        present = name in flat_config or name in flat_secrets
        if key.get("required") and not present:
            missing.append(name)
        if key.get("secret") and name in flat_config:
            misplaced.append(name)
    known = [k["name"] for k in keys]
    patterns = [re.compile("^" + re.escape(k).replace(r"\*", '("[^"]*"|[^.]+)') + "$") for k in known if "*" in k]
    unknown = [n for n in flat_config if n not in known and not any(p.match(n) for p in patterns)]
    return {"missing": missing, "misplaced": misplaced, "unknown": unknown}


class FilterModule:
    def filters(self):
        return {"bdi_render": bdi_render, "bdi_unflatten": bdi_unflatten, "bdi_check": bdi_check}
