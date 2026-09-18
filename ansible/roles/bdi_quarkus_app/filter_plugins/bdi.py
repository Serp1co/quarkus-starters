"""Filters of the bdi_quarkus_app role: fragment translation, secret routing, contract validation.

Pure functions, unit-testable with `python3 -m pytest` (see tests in ansible/README.md). They implement the
translation from the EAP-era fragment vocabulary (datasource, property) to Quarkus keys, route every
${fromvault.<scope>.<NAME>} placeholder to the secrets file, and check the result against the artifact's
META-INF/config-contract.json before anything touches a host.
"""
import re

from ansible.errors import AnsibleFilterError

PLACEHOLDER = re.compile(r"^\$\{fromvault\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_]+)\}$")


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


def _flatten(tree, prefix="", stringify=False):
    """Dotted keys. With stringify, values become the strings Quarkus would see (lists comma-joined)."""
    flat = {}
    for key, value in tree.items():
        name = f"{prefix}.{key}" if prefix else str(key)
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


def _unflatten(flat):
    tree = {}
    for name, value in flat.items():
        node = tree
        parts = name.split(".")
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = value
    return tree


def _translate_datasource(fragment, warnings):
    name = fragment.get("name", "default")
    file = fragment.get("_file", "datasource")
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


def bdi_render(fragments):
    """Merge the fragments of one application into a config tree; route placeholders to secret_refs."""
    config, warnings = {}, []
    for fragment in fragments:
        kind = fragment.get("kind", "config")
        if kind == "datasource":
            tree = _translate_datasource(fragment, warnings)
        elif kind == "property":
            tree = _unflatten({fragment["name"]: fragment["value"]})
        elif kind == "config":
            tree = {k: v for k, v in fragment.items() if k not in ("kind", "_file")}
        else:
            raise AnsibleFilterError(f"{fragment.get('_file')}: unknown fragment kind {kind!r}")
        config = _deep_merge(config, tree, "")
    flat = _flatten(config)
    secret_refs = {}
    for key, value in flat.items():
        match = PLACEHOLDER.match(value) if isinstance(value, str) else None
        if match:
            secret_refs[key] = {"scope": match.group(1), "name": match.group(2)}
    public = {k: v for k, v in flat.items() if k not in secret_refs}
    return {"config": _unflatten(public), "secret_refs": secret_refs, "warnings": warnings}


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
    patterns = [re.compile("^" + re.escape(k).replace(r"\*", "[^.]+") + "$") for k in known if "*" in k]
    unknown = [n for n in flat_config if n not in known and not any(p.match(n) for p in patterns)]
    return {"missing": missing, "misplaced": misplaced, "unknown": unknown}


class FilterModule:
    def filters(self):
        return {"bdi_render": bdi_render, "bdi_unflatten": bdi_unflatten, "bdi_check": bdi_check}
