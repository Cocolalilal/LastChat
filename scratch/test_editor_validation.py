import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.append(str(ROOT / "tools" / "catalog_editor"))

from catalog_editor import CatalogEditor, load_catalog, CATALOG_PATH

class DummyEditor:
    def __init__(self):
        self.catalog = load_catalog(CATALOG_PATH)
        
    def validate_catalog(self):
        # We call the validate_catalog method from CatalogEditor using DummyEditor self reference
        return CatalogEditor.validate_catalog(self, show_success=False)

if __name__ == "__main__":
    try:
        editor = DummyEditor()
        editor.validate_catalog()
        print("SUCCESS: CatalogEditor validation passed without errors!")
    except Exception as e:
        print("ERROR: CatalogEditor validation failed!")
        print(e)
        sys.exit(1)
