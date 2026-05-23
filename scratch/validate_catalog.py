import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "catalog" / "lastchat_catalog.json"

def matches_catalog_pattern(value, pattern):
    if not pattern:
        return False
    try:
        return re.search(pattern, value, re.IGNORECASE) is not None
    except re.error:
        return False

def matches_global_rule(catalog, model_id):
    return any(
        any(matches_catalog_pattern(model_id, pattern) for pattern in rule.get("match_patterns", []))
        and not any(matches_catalog_pattern(model_id, pattern) for pattern in rule.get("exclude_patterns", []))
        for rule in catalog.get("global_rules", [])
    )

def override_matches(override, model_id):
    if any(matches_catalog_pattern(model_id, pattern) for pattern in override.get("exclude_patterns", [])):
        return False
    exact_refs = [
        override.get("id", ""),
        *override.get("api_aliases", []),
    ]
    if model_id.lower() in {ref.lower() for ref in exact_refs if ref}:
        return True
    return any(matches_catalog_pattern(model_id, pattern) for pattern in override.get("match_patterns", []))

def family_matches(family, model_id):
    patterns = family.get("match_patterns", [])
    return any(matches_catalog_pattern(model_id, pattern) for pattern in patterns)

def matches_model_family(model_id, model_families):
    for family in model_families:
        if family_matches(family, model_id):
            return True
    return False

def resolves_model_id(catalog, model_id):
    if matches_global_rule(catalog, model_id):
        return True
    if matches_model_family(model_id, catalog["model_families"]):
        return True
    return any(override_matches(override, model_id) for override in catalog["model_overrides"])

def main():
    print(f"Validating catalog at {CATALOG_PATH}...")
    with CATALOG_PATH.open("r", encoding="utf-8") as fh:
        catalog = json.load(fh)

    provider_ids = {provider.get("id") for provider in catalog["providers"]}
    family_ids = {family.get("id") for family in catalog["model_families"]}
    icon_paths = [
        *(provider.get("icon", "") for provider in catalog["providers"]),
        *(family.get("icon", "") for family in catalog["model_families"]),
    ]
    missing_icons = [
        path for path in icon_paths
        if path and not path.startswith(("http://", "https://")) and not (ROOT / "catalog" / path).exists()
    ]
    unknown_provider_refs = [
        override.get("id", "") or ", ".join(override.get("match_patterns", []))
        for override in catalog["model_overrides"]
        for provider_id in override.get("provider_ids", [])
        if provider_id not in provider_ids
    ]
    bad_family_versions = [
        family.get("id", "")
        for family in catalog["model_families"]
        if not isinstance(family.get("versions", []), list)
    ]
    bad_patterns = []
    for family in catalog["model_families"]:
        family_id = family.get("id", "")
        for pattern in family.get("match_patterns", []):
            try:
                re.compile(pattern)
            except re.error:
                bad_patterns.append(f"{family_id}: {pattern}")
        for version in family.get("versions", []):
            if not isinstance(version, dict):
                bad_family_versions.append(family_id)
                continue
            version_id = version.get("id", "")
            for pattern in list(version.get("match_patterns", [])) + list(version.get("exclude_patterns", [])):
                try:
                    re.compile(pattern)
                except re.error:
                    bad_patterns.append(f"{family_id}/{version_id}: {pattern}")
    for rule in catalog.get("global_rules", []):
        rule_id = rule.get("id", "")
        for pattern in list(rule.get("match_patterns", [])) + list(rule.get("exclude_patterns", [])):
            try:
                re.compile(pattern)
            except re.error:
                bad_patterns.append(f"global/{rule_id}: {pattern}")
    for override in catalog["model_overrides"]:
        override_id = override.get("id", "") or "override"
        for pattern in (
            list(override.get("match_patterns", []))
            + list(override.get("exclude_patterns", []))
            + list(override.get("base_url_patterns", []))
        ):
            try:
                re.compile(pattern)
            except re.error:
                bad_patterns.append(f"override/{override_id}: {pattern}")
    override_ids = {
        override.get("id", "").lower()
        for override in catalog["model_overrides"]
        if override.get("id")
    }
    missing_setup_refs = [
        f"{provider.get('name', '')}: {model_id}"
        for provider in catalog["providers"]
        for model_id in (
            list(provider.get("setup_models", []))
            + list((provider.get("setup_defaults") or {}).values())
        )
        if model_id
        and not resolves_model_id(catalog, model_id)
    ]
    duplicate_model_ids = sorted({
        model_id for model_id in override_ids
        if sum(1 for override in catalog["model_overrides"] if override.get("id", "").lower() == model_id) > 1
    })

    problems = []
    if missing_icons:
        problems.append("Missing icon files: " + ", ".join(missing_icons))
    if unknown_provider_refs:
        problems.append("Models with unknown provider ids: " + ", ".join(unknown_provider_refs))
    if bad_family_versions:
        problems.append("Families with non-list versions: " + ", ".join(bad_family_versions))
    if bad_patterns:
        problems.append("Invalid family regex patterns: " + ", ".join(bad_patterns))
    if missing_setup_refs:
        problems.append("Setup model refs not resolved by rules: " + ", ".join(missing_setup_refs))
    if duplicate_model_ids:
        problems.append("Duplicate model ids: " + ", ".join(duplicate_model_ids))

    if problems:
        print("VALIDATION FAILED:")
        for prob in problems:
            print(f"- {prob}")
        exit(1)
    else:
        print("Catalog is fully valid and clean!")

if __name__ == "__main__":
    main()
