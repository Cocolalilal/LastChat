import urllib.request
import urllib.error
import os

ICON_NAMES = {
    "ai21": ["ai21", "ai21-labs"],
    "amazon": ["amazon", "amazon-bedrock", "aws"],
    "arcee": ["arcee", "arcee-ai"],
    "cogito": ["cogito", "deep-cogito", "deepcogito"],
    "essential": ["essential", "essential-ai", "essentialai"],
    "ibm": ["ibm"],
    "inclusionai": ["inclusion", "inclusion-ai", "inclusionai", "antgroup", "ant-group"],
    "inflection": ["inflection", "inflection-ai", "inflectionai", "pi"],
    "liquid": ["liquid", "liquid-ai", "liquidai"],
    "poolside": ["poolside", "poolside-ai"],
    "sao10k": ["sao10k"],
    "thedrummer": ["thedrummer", "drummer"],
    "xiaomi": ["xiaomi"]
}

# We can check raw github content or unpkg
# pattern 1: unpkg static-svg package
# https://unpkg.com/@lobehub/icons-static-svg@latest/icons/{name}.svg
# pattern 2: raw github
# https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/light/{name}.svg

TARGET_DIR = r"C:\Users\julian\Documents\LastChat\catalog\icons"
os.makedirs(TARGET_DIR, exist_ok=True)

print("Starting icon search...")
for key, candidates in ICON_NAMES.items():
    found = False
    for candidate in candidates:
        urls = [
            f"https://unpkg.com/@lobehub/icons-static-svg@latest/icons/{candidate}.svg",
            f"https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/light/{candidate}.svg"
        ]
        for url in urls:
            try:
                req = urllib.request.Request(
                    url, 
                    headers={'User-Agent': 'Mozilla/5.0'}
                )
                with urllib.request.urlopen(req) as response:
                    if response.status == 200:
                        content = response.read()
                        # Verify it's a valid SVG
                        if b"<svg" in content or b"<SVG" in content:
                            target_path = os.path.join(TARGET_DIR, f"{key}.svg")
                            with open(target_path, "wb") as f:
                                f.write(content)
                            print(f"Successfully downloaded {key}.svg from {url}")
                            found = True
                            break
            except urllib.error.HTTPError as e:
                pass
            except Exception as e:
                print(f"Error checking {url}: {e}")
        if found:
            break
    if not found:
        print(f"Could not find an icon for {key} in candidates {candidates}")
