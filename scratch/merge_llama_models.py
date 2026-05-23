import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "catalog" / "lastchat_catalog.json"

def main():
    print("Loading catalog...")
    with CATALOG_PATH.open("r", encoding="utf-8") as fh:
        catalog = json.load(fh)
        
    # Find the models to merge
    models = catalog.get("models", [])
    merged_model = {
        "id": "meta-llama-3.1-8b-instruct",
        "display_name": "Llama 3.1 8B Instruct",
        "canonical_model_id": "meta-llama-3.1-8b-instruct",
        "provider_ids": [
            "7a9b0c10-d8f9-467f-94d0-258fe3da49b4",  # SambaNova
            "d8f4c29a-1fb4-45b9-a9c0-fd11c34a2e5d"   # FriendliAI
        ],
        "provider_slug": "meta",
        "family_id": "llama",
        "abilities": ["TOOL"]
    }
    
    # Filter out the two old models
    new_models = []
    found_sambanova = False
    found_friendli = False
    for m in models:
        mid = m.get("id", "")
        if mid == "Meta-Llama-3.1-8B-Instruct":
            found_sambanova = True
            continue
        if mid == "meta-llama-3.1-8b-instruct":
            found_friendli = True
            continue
        new_models.append(m)
        
    if not found_sambanova or not found_friendli:
        print(f"Warning: could not find both models. SambaNova found: {found_sambanova}, Friendli found: {found_friendli}")
    
    # Insert the merged model where the Friendli one was, or just append
    new_models.append(merged_model)
    catalog["models"] = new_models
    
    # Update SambaNova references
    providers = catalog.get("providers", [])
    for p in providers:
        if p.get("name") == "SambaNova":
            print("Updating SambaNova references...")
            # Update setup_models
            setup_models = p.get("setup_models", [])
            for idx, sm in enumerate(setup_models):
                if sm == "Meta-Llama-3.1-8B-Instruct":
                    setup_models[idx] = "meta-llama-3.1-8b-instruct"
            # Update setup_defaults
            setup_defaults = p.get("setup_defaults", {})
            for key, val in setup_defaults.items():
                if val == "Meta-Llama-3.1-8B-Instruct":
                    setup_defaults[key] = "meta-llama-3.1-8b-instruct"
                    
    # Save the updated catalog
    print("Saving updated catalog...")
    with CATALOG_PATH.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump(catalog, fh, indent=2, ensure_ascii=False, sort_keys=False)
        fh.write("\n")
    print("Done!")

if __name__ == "__main__":
    main()
