import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "catalog" / "lastchat_catalog.json"

def main():
    print(f"Reading catalog from {CATALOG_PATH}...")
    with CATALOG_PATH.open("r", encoding="utf-8") as fh:
        catalog = json.load(fh)

    # 1. Add Providers
    new_providers = [
        {
          "id": "2269a913-9b16-43b8-89c0-fd2b4a5d3c8c",
          "name": "GitHub Models",
          "description": "Prototyping platform with access to global models (Llama, GPT, Gemini, Phi, Mistral, Qwen, etc.).",
          "type": "openai",
          "base_url": "https://models.github.ai/inference",
          "icon": "icons/github.svg",
          "preset": True,
          "signup_url": "https://github.com/marketplace/models",
          "api_key_url": "https://github.com/settings/tokens"
        },
        {
          "id": "2995fc1d-78ad-4a67-9c6a-fd2b4a5d3c90",
          "name": "AI21 Labs",
          "description": "AI21 Labs provides access to the hybrid SSM-Transformer Jamba model family.",
          "type": "openai",
          "base_url": "https://api.ai21.com/studio/v1",
          "icon": "icons/ai21.svg",
          "preset": True,
          "signup_url": "https://studio.ai21.com/",
          "api_key_url": "https://studio.ai21.com/account/api-key"
        },
        {
          "id": "c1d92a0e-c1d4-45aa-9b24-78fdbe4a3198",
          "name": "Amazon Bedrock",
          "description": "Amazon Bedrock is a fully managed service that offers a choice of high-performing foundation models.",
          "type": "openai",
          "base_url": "https://bedrock-runtime.us-east-1.amazonaws.com/v1",
          "icon": "icons/amazon.svg",
          "preset": True
        },
        {
          "id": "e3f92a0e-c1d4-45aa-9b24-78fdbe4a3199",
          "name": "Arcee AI",
          "description": "Arcee AI provides access to specialized domain-specific and agentic models.",
          "type": "openai",
          "base_url": "https://api.arcee.ai/v1",
          "icon": "icons/arcee.svg",
          "preset": True,
          "signup_url": "https://app.arcee.ai/",
          "api_key_url": "https://app.arcee.ai/settings"
        },
        {
          "id": "f1f92a0e-c1d4-45aa-9b24-78fdbe4a3200",
          "name": "Deep Cogito",
          "description": "Deep Cogito offers advanced hybrid reasoning models based on Iterated Distillation and Amplification.",
          "type": "openai",
          "base_url": "https://api.deepcogito.com/v1",
          "icon": "icons/cogito.svg",
          "preset": True
        },
        {
          "id": "a1f92a0e-c1d4-45aa-9b24-78fdbe4a3201",
          "name": "Essential AI",
          "description": "Essential AI provides access to the RNJ family of models optimized for code, STEM, and agentic tasks.",
          "type": "openai",
          "base_url": "https://api.essential.ai/v1",
          "icon": "icons/essential.svg",
          "preset": True
        },
        {
          "id": "b1f92a0e-c1d4-45aa-9b24-78fdbe4a3202",
          "name": "Liquid AI",
          "description": "Liquid AI provides high-performance generative models built on a proprietary liquid neural network architecture.",
          "type": "openai",
          "base_url": "https://api.liquid.ai/v1",
          "icon": "icons/liquid.svg",
          "preset": True
        },
        {
          "id": "d1f92a0e-c1d4-45aa-9b24-78fdbe4a3203",
          "name": "Poolside",
          "description": "Poolside offers coding-specialized models built for agentic software engineering workflows.",
          "type": "openai",
          "base_url": "https://api.poolside.ai/v1",
          "icon": "icons/poolside.svg",
          "preset": True
        }
    ]

    existing_provider_ids = {p["id"] for p in catalog["providers"]}
    for p in new_providers:
        if p["id"] not in existing_provider_ids:
            catalog["providers"].append(p)
            print(f"Added provider: {p['name']}")
        else:
            print(f"Provider already exists: {p['name']}")

    # 2. Modify Existing Families
    for family in catalog["model_families"]:
        if family["id"] == "ernie":
            # Update match patterns
            family["match_patterns"] = ["(^|[/._-])(?:ernie|qianfan|wenxin|cobuddy)(?=$|[:/._-])"]
            # Clear or extend versions
            v_map = {v["id"]: v for v in family.get("versions", [])}
            new_versions = [
                {
                  "id": "ernie-5.1",
                  "display_name": "ERNIE 5.1",
                  "match_patterns": ["ernie-5\\.1"]
                },
                {
                  "id": "ernie-4.5",
                  "display_name": "ERNIE 4.5",
                  "match_patterns": ["ernie-4\\.5"]
                },
                {
                  "id": "ernie-x1",
                  "display_name": "ERNIE X1",
                  "match_patterns": ["ernie-x1"],
                  "abilities": ["TOOL", "REASONING"]
                },
                {
                  "id": "ernie-speed",
                  "display_name": "ERNIE Speed",
                  "match_patterns": ["ernie-speed"]
                },
                {
                  "id": "ernie-lite",
                  "display_name": "ERNIE Lite",
                  "match_patterns": ["ernie-lite"]
                },
                {
                  "id": "ernie-tiny",
                  "display_name": "ERNIE Tiny",
                  "match_patterns": ["ernie-tiny"]
                },
                {
                  "id": "qianfan-vl",
                  "display_name": "Qianfan VL",
                  "match_patterns": ["qianfan-vl"],
                  "input_modalities": ["TEXT", "IMAGE"]
                },
                {
                  "id": "qianfan-ocr",
                  "display_name": "Qianfan OCR",
                  "match_patterns": ["qianfan-ocr"],
                  "input_modalities": ["TEXT", "IMAGE"]
                },
                {
                  "id": "cobuddy",
                  "display_name": "CoBuddy",
                  "match_patterns": ["cobuddy"]
                }
            ]
            for nv in new_versions:
                v_map[nv["id"]] = nv
            family["versions"] = list(v_map.values())
            print("Updated family: ERNIE")

        elif family["id"] == "doubao":
            v_map = {v["id"]: v for v in family.get("versions", [])}
            new_versions = [
                {
                  "id": "doubao-pro",
                  "display_name": "Doubao Pro",
                  "match_patterns": ["doubao.*pro"]
                },
                {
                  "id": "doubao-lite",
                  "display_name": "Doubao Lite",
                  "match_patterns": ["doubao.*lite"]
                }
            ]
            for nv in new_versions:
                v_map[nv["id"]] = nv
            family["versions"] = list(v_map.values())
            print("Updated family: Doubao")

        elif family["id"] == "hunyuan":
            v_map = {v["id"]: v for v in family.get("versions", [])}
            new_versions = [
                {
                  "id": "hunyuan-hy3",
                  "display_name": "Hunyuan Hy3",
                  "match_patterns": ["hunyuan.*hy3", "hy3"],
                  "input_modalities": ["TEXT", "IMAGE"],
                  "abilities": ["TOOL", "REASONING"]
                },
                {
                  "id": "hunyuan-2.0",
                  "display_name": "Hunyuan 2.0",
                  "match_patterns": ["hunyuan-2\\.0"]
                }
            ]
            for nv in new_versions:
                v_map[nv["id"]] = nv
            family["versions"] = list(v_map.values())
            print("Updated family: Hunyuan")

    # 3. Add New Families
    new_families = [
        {
          "id": "jamba",
          "display_name": "Jamba",
          "aliases": ["jamba", "ai21"],
          "match_patterns": ["(^|[/._-])jamba(?=$|[:/._-])"],
          "icon": "icons/jamba.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "ai21",
          "versions": []
        },
        {
          "id": "nemotron",
          "display_name": "Nemotron",
          "aliases": ["nemotron"],
          "match_patterns": ["(^|[/._-])nemotron(?=$|[:/._-])"],
          "icon": "icons/nvidia.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL", "REASONING"],
          "provider_slug": "nvidia",
          "versions": [
            {
              "id": "nemotron-nano-omni",
              "display_name": "Nemotron 3 Nano Omni",
              "match_patterns": ["nemotron.*nano-omni"],
              "input_modalities": ["TEXT", "IMAGE"]
            },
            {
              "id": "nemotron-3-nano",
              "display_name": "Nemotron 3 Nano",
              "match_patterns": ["nemotron-3-nano"]
            },
            {
              "id": "nemotron-3-super",
              "display_name": "Nemotron 3 Super",
              "match_patterns": ["nemotron-3-super"]
            },
            {
              "id": "nemotron-4-340b",
              "display_name": "Nemotron 4 340B",
              "match_patterns": ["nemotron-4-340b"]
            },
            {
              "id": "llama-3.1-nemotron",
              "display_name": "Llama 3.1 Nemotron",
              "match_patterns": ["llama-3\\.1-nemotron"]
            }
          ]
        },
        {
          "id": "amazon-nova",
          "display_name": "Amazon Nova",
          "aliases": ["nova", "amazon"],
          "match_patterns": ["(^|[/._-])nova(?=$|[:/._-])"],
          "icon": "icons/amazon.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT", "IMAGE"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "amazon",
          "versions": [
            {
              "id": "nova-pro",
              "display_name": "Amazon Nova Pro",
              "match_patterns": ["nova.*pro"]
            },
            {
              "id": "nova-lite",
              "display_name": "Amazon Nova Lite",
              "match_patterns": ["nova.*lite"]
            },
            {
              "id": "nova-micro",
              "display_name": "Amazon Nova Micro",
              "match_patterns": ["nova.*micro"],
              "input_modalities": ["TEXT"]
            },
            {
              "id": "nova-canvas",
              "display_name": "Amazon Nova Canvas",
              "match_patterns": ["nova.*canvas"],
              "type": "IMAGE",
              "image_generation_method": "diffusion",
              "input_modalities": ["TEXT"],
              "output_modalities": ["IMAGE"],
              "abilities": []
            }
          ]
        },
        {
          "id": "amazon-titan",
          "display_name": "Amazon Titan",
          "aliases": ["titan", "amazon"],
          "match_patterns": ["(^|[/._-])titan(?=$|[:/._-])"],
          "icon": "icons/amazon.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "amazon",
          "versions": [
            {
              "id": "titan-text-premier",
              "display_name": "Titan Text Premier",
              "match_patterns": ["titan-text-premier"]
            },
            {
              "id": "titan-text-express",
              "display_name": "Titan Text Express",
              "match_patterns": ["titan-text-express"]
            },
            {
              "id": "titan-text-lite",
              "display_name": "Titan Text Lite",
              "match_patterns": ["titan-text-lite"]
            },
            {
              "id": "titan-image",
              "display_name": "Titan Image Generator",
              "match_patterns": ["titan.*image"],
              "type": "IMAGE",
              "image_generation_method": "diffusion",
              "input_modalities": ["TEXT"],
              "output_modalities": ["IMAGE"],
              "abilities": []
            },
            {
              "id": "titan-embed",
              "display_name": "Titan Embeddings",
              "match_patterns": ["titan.*embed"],
              "type": "EMBEDDING",
              "input_modalities": ["TEXT"],
              "output_modalities": ["TEXT"],
              "abilities": []
            }
          ]
        },
        {
          "id": "arcee",
          "display_name": "Arcee",
          "aliases": ["arcee", "arcee-ai"],
          "match_patterns": ["(^|[/._-])arcee(?=$|[:/._-])"],
          "icon": "icons/arcee.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "arcee",
          "versions": [
            {
              "id": "trinity-thinking",
              "display_name": "Trinity Large Thinking",
              "match_patterns": ["trinity-large-thinking"],
              "abilities": ["TOOL", "REASONING"]
            },
            {
              "id": "arcee-agent",
              "display_name": "Arcee Agent",
              "match_patterns": ["arcee-agent"],
              "abilities": ["TOOL"]
            },
            {
              "id": "arcee-spark",
              "display_name": "Arcee Spark",
              "match_patterns": ["arcee-spark"]
            }
          ]
        },
        {
          "id": "cogito",
          "display_name": "Cogito",
          "aliases": ["cogito", "deepcogito"],
          "match_patterns": ["(^|[/._-])cogito(?=$|[:/._-])"],
          "icon": "icons/cogito.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL", "REASONING"],
          "provider_slug": "cogito",
          "versions": []
        },
        {
          "id": "essential-ai",
          "display_name": "Essential AI",
          "aliases": ["rnj", "essentialai"],
          "match_patterns": ["(^|[/._-])rnj(?=$|[:/._-])", "(^|[/._-])essentialai(?=$|[:/._-])"],
          "icon": "icons/essential.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "essential-ai",
          "versions": []
        },
        {
          "id": "granite",
          "display_name": "Granite",
          "aliases": ["granite", "ibm"],
          "match_patterns": ["(^|[/._-])granite(?=$|[:/._-])"],
          "icon": "icons/ibm.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "ibm",
          "versions": [
            {
              "id": "granite-guardian",
              "display_name": "Granite Guardian",
              "match_patterns": ["granite-guardian"]
            },
            {
              "id": "granite-code",
              "display_name": "Granite Code",
              "match_patterns": ["granite-code"]
            }
          ]
        },
        {
          "id": "inclusion-ai",
          "display_name": "inclusionAI",
          "aliases": ["inclusionai", "inclusion-ai", "antgroup", "ant-group"],
          "match_patterns": ["(^|[/._-])inclusionai(?=$|[:/._-])", "(^|[/._-])antgroup(?=$|[:/._-])"],
          "icon": "icons/inclusionai.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "inclusion-ai",
          "versions": [
            {
              "id": "ling",
              "display_name": "Ling",
              "match_patterns": ["ling"]
            },
            {
              "id": "ring",
              "display_name": "Ring",
              "match_patterns": ["ring"],
              "abilities": ["TOOL", "REASONING"]
            },
            {
              "id": "ming",
              "display_name": "Ming",
              "match_patterns": ["ming"],
              "input_modalities": ["TEXT", "IMAGE"]
            }
          ]
        },
        {
          "id": "inflection",
          "display_name": "Inflection",
          "aliases": ["inflection"],
          "match_patterns": ["(^|[/._-])inflection(?=$|[:/._-])"],
          "icon": "icons/inflection.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "inflection",
          "versions": [
            {
              "id": "inflection-3-pi",
              "display_name": "Inflection 3 Pi",
              "match_patterns": ["inflection-3-pi"]
            },
            {
              "id": "inflection-3-productivity",
              "display_name": "Inflection 3 Productivity",
              "match_patterns": ["inflection-3-productivity"],
              "abilities": ["TOOL"]
            }
          ]
        },
        {
          "id": "intfloat",
          "display_name": "intfloat",
          "aliases": ["intfloat", "e5"],
          "match_patterns": ["(^|[/._-])intfloat(?=$|[:/._-])", "(^|[/._-])e5(?=$|[:/._-])"],
          "icon": "icons/huggingface.svg",
          "type": "EMBEDDING",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": [],
          "provider_slug": "huggingface",
          "versions": []
        },
        {
          "id": "liquid",
          "display_name": "Liquid LFM",
          "aliases": ["liquid", "liquidai", "lfm"],
          "match_patterns": ["(^|[/._-])liquid(?=$|[:/._-])", "(^|[/._-])lfm(?=$|[:/._-])"],
          "icon": "icons/liquid.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "liquid",
          "versions": []
        },
        {
          "id": "poolside",
          "display_name": "Poolside Laguna",
          "aliases": ["poolside", "laguna"],
          "match_patterns": ["(^|[/._-])poolside(?=$|[:/._-])", "(^|[/._-])laguna(?=$|[:/._-])"],
          "icon": "icons/poolside.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "poolside",
          "versions": [
            {
              "id": "laguna-m",
              "display_name": "Laguna M.1",
              "match_patterns": ["laguna[-_]?m"]
            },
            {
              "id": "laguna-xs",
              "display_name": "Laguna XS.2",
              "match_patterns": ["laguna[-_]?xs"]
            }
          ]
        },
        {
          "id": "sao10k",
          "display_name": "Sao10K",
          "aliases": ["sao10k"],
          "match_patterns": ["(^|[/._-])sao10k(?=$|[:/._-])"],
          "icon": "icons/sao10k.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "sao10k",
          "versions": []
        },
        {
          "id": "sentence-transformers",
          "display_name": "Sentence Transformers",
          "aliases": ["sentence-transformers", "all-minilm", "all-mpnet"],
          "match_patterns": ["(^|[/._-])sentence-transformers(?=$|[:/._-])", "(^|[/._-])all-minilm(?=$|[:/._-])", "(^|[/._-])all-mpnet(?=$|[:/._-])"],
          "icon": "icons/huggingface.svg",
          "type": "EMBEDDING",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": [],
          "provider_slug": "huggingface",
          "versions": []
        },
        {
          "id": "thedrummer",
          "display_name": "TheDrummer",
          "aliases": ["thedrummer", "unholy"],
          "match_patterns": ["(^|[/._-])thedrummer(?=$|[:/._-])", "(^|[/._-])unholy(?=$|[:/._-])"],
          "icon": "icons/thedrummer.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "thedrummer",
          "versions": []
        },
        {
          "id": "xiaomi",
          "display_name": "Xiaomi",
          "aliases": ["xiaomi", "milm", "mimo"],
          "match_patterns": ["(^|[/._-])xiaomi(?=$|[:/._-])", "(^|[/._-])milm(?=$|[:/._-])", "(^|[/._-])mimo(?=$|[:/._-])"],
          "icon": "icons/xiaomi.svg",
          "type": "CHAT",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL"],
          "provider_slug": "xiaomi",
          "versions": [
            {
              "id": "milm",
              "display_name": "MiLM",
              "match_patterns": ["milm"]
            },
            {
              "id": "mimo",
              "display_name": "MiMo",
              "match_patterns": ["mimo"]
            }
          ]
        }
    ]

    existing_family_ids = {f["id"] for f in catalog["model_families"]}
    for f in new_families:
        if f["id"] not in existing_family_ids:
            catalog["model_families"].append(f)
            print(f"Added family: {f['display_name']}")
        else:
            print(f"Family already exists: {f['display_name']}")

    # 4. Add Overrides
    new_overrides = [
        {
          "id": "openrouter/auto",
          "display_name": "OpenRouter Auto",
          "canonical_model_id": "openrouter/auto",
          "provider_ids": ["d5734028-d39b-4d41-9841-fd648d65440e"],
          "type": "CHAT",
          "input_modalities": ["TEXT", "IMAGE"],
          "output_modalities": ["TEXT"],
          "abilities": ["TOOL", "REASONING"],
          "provider_slug": "openrouter"
        },
        {
          "id": "text-embedding-3-small",
          "display_name": "Text Embedding 3 Small",
          "canonical_model_id": "text-embedding-3-small",
          "provider_ids": ["8f9d0c75-8f29-4a27-9c2b-f8d4fd5f3e91"],
          "type": "EMBEDDING",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"]
        },
        {
          "id": "text-embedding-3-large",
          "display_name": "Text Embedding 3 Large",
          "canonical_model_id": "text-embedding-3-large",
          "provider_ids": ["8f9d0c75-8f29-4a27-9c2b-f8d4fd5f3e91"],
          "type": "EMBEDDING",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"]
        },
        {
          "id": "text-embedding-ada-002",
          "display_name": "Text Embedding Ada 002",
          "canonical_model_id": "text-embedding-ada-002",
          "provider_ids": ["8f9d0c75-8f29-4a27-9c2b-f8d4fd5f3e91"],
          "type": "EMBEDDING",
          "input_modalities": ["TEXT"],
          "output_modalities": ["TEXT"]
        }
    ]

    existing_override_ids = {o.get("id") for o in catalog["model_overrides"] if o.get("id")}
    for o in new_overrides:
        if o["id"] not in existing_override_ids:
            catalog["model_overrides"].append(o)
            print(f"Added override: {o['display_name']}")
        else:
            print(f"Override already exists: {o['display_name']}")

    # Save Catalog with correct date
    catalog["updated_at"] = "2026-05-23"
    print(f"Saving catalog to {CATALOG_PATH}...")
    with CATALOG_PATH.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump(catalog, fh, indent=2, ensure_ascii=False, sort_keys=False)
        fh.write("\n")
    print("Catalog updated successfully!")

if __name__ == "__main__":
    main()
