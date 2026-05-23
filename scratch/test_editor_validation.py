import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.append(str(ROOT / "tools" / "catalog_editor"))

from catalog_editor import CatalogEditor, load_catalog, CATALOG_PATH

class DummyEditor:
    def __init__(self):
        self.catalog = load_catalog(CATALOG_PATH)

    resolves_model_id = CatalogEditor.resolves_model_id
    matches_global_rule = CatalogEditor.matches_global_rule
    matches_model_family = staticmethod(CatalogEditor.matches_model_family)
    family_matches = staticmethod(CatalogEditor.family_matches)
    override_matches = CatalogEditor.override_matches
    matches_catalog_pattern = staticmethod(CatalogEditor.matches_catalog_pattern)
    find_model_display_names = CatalogEditor.find_model_display_names
        
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
