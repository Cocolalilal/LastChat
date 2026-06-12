import sys
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.append(str(ROOT / "tools" / "catalog_editor"))

from catalog_editor import CatalogEditor, load_catalog, CATALOG_PATH, save_catalog

class MockWidget:
    def __init__(self, value):
        self.value = value
    def get(self, *args):
        return self.value

class MockBooleanVar:
    def __init__(self, value):
        self.value = value
    def get(self):
        return self.value

class MockListbox:
    def selection_clear(self, *args):
        pass
    def selection_set(self, *args):
        pass
    def activate(self, *args):
        pass

class DummyEditor:
    def __init__(self):
        self.catalog = load_catalog(CATALOG_PATH)
        # Mock status bar
        self.status = type("MockStatus", (), {"set": lambda self, x: print("Status set:", x)})()
        self.provider_list = MockListbox()
        # Mock widgets/variables used by apply_provider
        self.p_id = MockWidget("")
        self.p_name = MockWidget("")
        self.p_description = MockWidget("")
        self.p_type = MockWidget("")
        self.p_base = MockWidget("")
        self.p_path = MockWidget("")
        self.p_response = MockWidget("")
        self.p_balance = MockWidget("")
        self.p_icon = MockWidget("")
        self.p_stream_mode = MockWidget("")
        self.p_image_mode = MockWidget("")
        self.p_replay_mode = MockWidget("")
        self.p_reasoning = MockWidget("")
        self.p_reasoning_enabled = MockBooleanVar(False)
        
    def selected_index(self, listbox):
        return self.mock_selected_idx

    def get_entry(self, widget):
        return widget.get()

    def clean_none(self, value):
        return CatalogEditor.clean_none(value)

    def refresh_all(self):
        pass

    def apply_provider(self):
        CatalogEditor.apply_provider(self)

def run_test():
    # 1. Find NVIDIA NIM in the catalog
    editor = DummyEditor()
    nvidia_idx = None
    for idx, p in enumerate(editor.catalog["providers"]):
        if p.get("name") == "NVIDIA NIM":
            nvidia_idx = idx
            break
            
    assert nvidia_idx is not None, "NVIDIA NIM not found in catalog!"
    original_nvidia = editor.catalog["providers"][nvidia_idx].copy()
    
    # Check that original NVIDIA NIM has setup_models and setup_defaults
    assert "setup_models" in original_nvidia, "NVIDIA NIM must have setup_models originally"
    assert "setup_defaults" in original_nvidia, "NVIDIA NIM must have setup_defaults originally"
    assert "api_key_url" in original_nvidia, "NVIDIA NIM must have api_key_url originally"
    
    # 2. Simulate loading form and applying changes with a new description
    editor.mock_selected_idx = nvidia_idx
    editor.p_id.value = original_nvidia.get("id", "")
    editor.p_name.value = original_nvidia.get("name", "")
    editor.p_description.value = "Updated NVIDIA NIM description"  # the change
    editor.p_type.value = original_nvidia.get("type", "openai")
    editor.p_base.value = original_nvidia.get("base_url", "")
    editor.p_path.value = original_nvidia.get("chat_completions_path", "/chat/completions")
    editor.p_response.value = str(original_nvidia.get("use_response_api", False))
    editor.p_balance.value = json.dumps(original_nvidia.get("balance_option", {}))
    editor.p_icon.value = original_nvidia.get("icon", "")
    editor.p_stream_mode.value = original_nvidia.get("stream_options_mode", "auto")
    editor.p_image_mode.value = original_nvidia.get("image_response_modalities_mode", "auto")
    editor.p_replay_mode.value = original_nvidia.get("reasoning_content_replay_mode", "auto")
    editor.p_reasoning.value = "{}"
    editor.p_reasoning_enabled.value = False
    
    # Apply changes
    print("Applying provider changes...")
    editor.apply_provider()
    
    # 3. Check that the updated NVIDIA NIM provider still has all original keys!
    updated_nvidia = editor.catalog["providers"][nvidia_idx]
    
    print("Checking keys in updated provider...")
    assert updated_nvidia.get("description") == "Updated NVIDIA NIM description"
    assert "setup_models" in updated_nvidia, "setup_models was stripped!"
    assert "setup_defaults" in updated_nvidia, "setup_defaults was stripped!"
    assert "api_key_url" in updated_nvidia, "api_key_url was stripped!"
    assert updated_nvidia.get("setup_models") == original_nvidia.get("setup_models")
    assert updated_nvidia.get("setup_defaults") == original_nvidia.get("setup_defaults")
    assert updated_nvidia.get("api_key_url") == original_nvidia.get("api_key_url")
    
    print("SUCCESS: Editor save integrity test passed!")

if __name__ == "__main__":
    run_test()
