"""Filters of the bdi_quarkus_app role: fragment translation, secret routing, contract validation.

Pure functions, unit-tested by ansible/tests/test_bdi_filters.py. They translate the EAP-era fragment
vocabulary (one kind per standalone.xml concern, see ansible/fragments/) into Quarkus keys, split what
belongs to the unit (platform output) from what belongs to application-<env>.yaml, route every
${fromvault.<scope>.<NAME>} placeholder to the secrets file, and check the result against the artifact's
META-INF/config-contract.json before anything touches a host.
"""
import re
from decimal import Decimal

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


def _translate_datasource(fragment, warnings, platform):
    name = fragment.get("name", "default")
    file = _file(fragment)
    jdbc, ds = {}, {}
    url = fragment.get("url") or fragment.get("xa_oracle_url") or fragment.get("xa_url") or fragment.get("connection_url")
    if url:
        jdbc["url"] = url
    if fragment.get("type") == "xa-data-source" or fragment.get("xa"):
        # quarkus.datasource.jdbc.transactions is fixed when the artifact is built (bdi-jpa-xa starter): the
        # fragment states a requirement the validation checks against the contract, it renders nothing
        platform.setdefault("xa_datasources", []).append(name)
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
    if "enable_statistics" in fragment:
        # quarkus.datasource.metrics.enabled is a build-time key: pool metrics are part of the artifact
        # (bdi-observability, cookbook 13), so the fragment cannot switch them on per environment
        warnings.append(f"{file}: enable_statistics is a build-time choice of the artifact (datasource metrics come with bdi-observability); ignored")
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
            tree = _translate_datasource(fragment, warnings, platform)
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


ROLES_MAPPING = "quarkus.http.auth.roles-mapping."
PLATFORM_SOURCES = re.compile(r"(secrets\.yaml|instance\.yaml|application-[^/]+\.yaml)")
EXTERNAL_SOURCES = re.compile(r"SysPropConfigSource|EnvConfigSource|\.env")
DURATION = re.compile(r"^(P(\d+D)?(T(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)?|PT?(\d+H)?(\d+M)?(\d+(\.\d+)?S)?|(\d+H)?(\d+M)?(\d+(\.\d+)?S)?)$", re.I)


def _pattern(name):
    return re.compile("^" + re.escape(name).replace(r"\*", '("[^"]*"|[^.]+)') + "$")


def _family(keys, name):
    """The contract key a concrete key belongs to: itself, or the wildcard family it matches."""
    for key in keys:
        if key["name"] == name:
            return key
    for key in keys:
        if "*" in key["name"] and _pattern(key["name"]).match(name):
            return key
    return None


def _is_required(key, values):
    if key.get("required"):
        return True
    condition = key.get("required-if")
    if condition and "=" in condition:
        other, expected = condition.split("=", 1)
        return str(values.get(other.strip(), "")).strip() == expected.strip()
    return False


def _check_value(key, value):
    """The same rules as ConfigContract.check on the Java side: type, then constraints. Returns a problem or None."""
    text = str(value).strip()
    if key.get("secret"):
        return "secret is empty" if text == "" else None
    kind = key.get("type", "String")
    try:
        if kind in ("int", "Integer", "long", "Long", "short", "Short"):
            number = int(text)
        elif kind in ("BigDecimal", "double", "Double", "float", "Float"):
            number = Decimal(text)
        elif kind in ("boolean", "Boolean"):
            if text.lower() not in ("true", "false"):
                return f"'{text}' is not a boolean"
            number = None
        elif kind == "Duration":
            if not (text.isdigit() or (text and DURATION.match(text) and re.search(r"\d", text))):
                return f"'{text}' is not a valid Duration"
            number = None
        else:
            number = None
    except (ValueError, ArithmeticError):
        return f"'{text}' is not a valid {kind}"
    if number is not None:
        if "min" in key and number < Decimal(str(key["min"])):
            return f"{text} is below the minimum {key['min']}"
        if "max" in key and number > Decimal(str(key["max"])):
            return f"{text} is above the maximum {key['max']}"
    values = key.get("values") or []
    if values and not any(v.lower() == text.lower() or v.replace("_", "-").lower() == text.lower() for v in values):
        return f"'{text}' is not one of {values}"
    if key.get("pattern") and not re.fullmatch(key["pattern"], text):
        return f"'{text}' does not match {key['pattern']}"
    return None


def bdi_check(config, secrets, contract, xa_datasources=None):
    """Validate rendered config + secrets against the artifact's contract, before anything touches a host.

    missing:   required keys (outright, or through required-if) absent or blank
    misplaced: secret keys rendered in the inventory instead of the vault file (wildcard families included)
    fixed:     build-time keys rendered at all: they are fixed in the artifact, rendering them changes nothing
    invalid:   values that fail their type or constraints (int, boolean, Duration, min/max, values, pattern)
    unknown:   keys the contract does not declare (informational)
    xa:        datasources declared xa-data-source for an artifact built without bdi-jpa-xa
    """
    flat_config, flat_secrets = _flatten(config, stringify=True), _flatten(secrets, stringify=True)
    values = {**flat_config, **flat_secrets}
    keys = contract.get("keys", [])
    missing, misplaced, fixed, invalid, unknown = [], [], [], [], []
    for key in keys:
        name = key["name"]
        if "*" in name:
            continue
        present = name in values and str(values[name]).strip() != ""
        if _is_required(key, values) and not present:
            missing.append(name)
        if key.get("secret") and name in flat_config:
            misplaced.append(name)
        if key.get("phase") == "build-time" and name in values:
            fixed.append(name)
        if name in values and str(values[name]).strip() != "":
            problem = _check_value(key, values[name])
            if problem:
                invalid.append(f"{name}: {problem}")
    concrete = {k["name"] for k in keys if "*" not in k["name"]}
    for name, value in values.items():
        if name in concrete:
            continue
        key = _family(keys, name)
        if key is None:
            if name in flat_config:
                unknown.append(name)
            continue
        if key.get("secret") and name in flat_config:
            misplaced.append(name)
        if key.get("phase") == "build-time":
            fixed.append(name)
        problem = _check_value(key, value) if str(value).strip() != "" else None
        if problem:
            invalid.append(f"{name}: {problem}")
    roles = contract.get("roles", [])
    if roles and ROLES_MAPPING + "*" in {k["name"] for k in keys}:
        granted = set()
        for name, value in flat_config.items():
            if name.startswith(ROLES_MAPPING):
                granted.update(r.strip() for r in str(value).split(","))
        missing.extend(f"role:{r}" for r in roles if r not in granted)
    xa = []
    if xa_datasources:
        for ds in xa_datasources:
            key_name = "quarkus.datasource.jdbc.transactions" if ds == "default" else f"quarkus.datasource.{ds}.jdbc.transactions"
            key = _family(keys, key_name)
            if key is None or str(key.get("default", "")).lower() != "xa":
                xa.append(ds)
    return {"missing": missing, "misplaced": misplaced, "fixed": fixed, "invalid": invalid, "unknown": unknown, "xa": xa}


def bdi_verify(platform, rendered_config, env, version, config_revision):
    """Compare what the running instance reports on /q/platform with what the platform approved.

    Beyond completeness: every rendered non-secret key must be in effect with the rendered value and come from
    the platform's files (not shadowed by a JVM property or an environment variable); a platform key not
    rendered must not be set from outside the platform either; the configuration revision must be the one
    just rendered; no value may violate the contract. Secrets are checked for presence and source only.
    """
    flat = _flatten(rendered_config, stringify=True)
    problems = []
    application = platform.get("application", {})
    if application.get("version") != version:
        problems.append(f"running version {application.get('version')!r}, expected {version!r}")
    if env not in application.get("profiles", []):
        problems.append(f"active profiles {application.get('profiles')}, expected {env}")
    for name in platform.get("missing", []):
        problems.append(f"{name}: required, not provided")
    for violation in platform.get("violations", []):
        problems.append(f"{violation} (contract violation)")
    running_revision = platform.get("revision", {}).get("config", "")
    if config_revision and running_revision != config_revision:
        problems.append(f"configuration revision running {running_revision[:12]!r}, rendered {config_revision[:12]!r}: the instance did not pick up the rendered file")
    for echo in platform.get("config", []):
        key, source = echo.get("key"), echo.get("source") or ""
        if key in flat:
            if not echo.get("present"):
                problems.append(f"{key}: rendered, but not in effect")
            elif not echo.get("secret") and str(echo.get("value")) != flat[key]:
                problems.append(f"{key}: effective {echo.get('value')!r} differs from rendered {flat[key]!r} (source {source})")
            elif not PLATFORM_SOURCES.search(source):
                problems.append(f"{key}: rendered value shadowed by {source}")
        elif echo.get("present") and EXTERNAL_SOURCES.search(source):
            problems.append(f"{key}: set outside the platform by {source}")
    return problems


def bdi_systemd_quote(value):
    """One quoted assignment value for a systemd EnvironmentFile/Environment line: spaces stay inside the value."""
    text = str(value).replace("\\", "\\\\").replace('"', '\\"')
    return '"' + text + '"'


class FilterModule:
    def filters(self):
        return {"bdi_render": bdi_render, "bdi_unflatten": bdi_unflatten, "bdi_check": bdi_check,
            "bdi_verify": bdi_verify,
            "bdi_systemd_quote": bdi_systemd_quote,
        }
