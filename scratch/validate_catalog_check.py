import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "catalog" / "lastchat_catalog.json"

def run_check():
    with CATALOG_PATH.open("r", encoding="utf-8") as fh:
        catalog = json.load(fh)
    
    model_ids = {model.get("id", "").lower() for model in catalog["models"]}
    
    missing_setup_refs = []
    for provider in catalog["providers"]:
        refs = list(provider.get("setup_models", [])) + list((provider.get("setup_defaults") or {}).values())
        for model_id in refs:
            if model_id and model_id.lower() not in model_ids:
                missing_setup_refs.append(f"{provider.get('name', '')}: {model_id}")
                
    duplicate_model_ids = sorted({
        model_id for model_id in model_ids
        if sum(1 for model in catalog["models"] if model.get("id", "").lower() == model_id) > 1
    })
    
    print("missing_setup_refs:", missing_setup_refs)
    print("duplicate_model_ids:", duplicate_model_ids)

if __name__ == "__main__":
    run_check()
