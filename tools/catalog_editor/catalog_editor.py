import json
import re
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk


ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = ROOT / "catalog" / "lastchat_catalog.json"


def empty_catalog():
    return {
        "schema_version": 1,
        "updated_at": "",
        "providers": [],
        "model_families": [],
        "models": [],
    }


def load_catalog(path):
    if not path.exists():
        return empty_catalog()
    with path.open("r", encoding="utf-8") as fh:
        catalog = json.load(fh)
    catalog.setdefault("schema_version", 1)
    catalog.setdefault("providers", [])
    if "model_families" not in catalog:
        catalog["model_families"] = catalog.get("model_groups", [])
    catalog.pop("model_groups", None)
    catalog.setdefault("models", [])
    catalog.pop("icons", None)
    for provider in catalog["providers"]:
        provider.pop("models", None)
    for model in catalog["models"]:
        model.pop("icon", None)
        if "family_id" not in model and model.get("group_id"):
            model["family_id"] = model.get("group_id")
        model.pop("group_id", None)
    return catalog


def save_catalog(path, catalog):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump(catalog, fh, indent=2, ensure_ascii=False, sort_keys=False)
        fh.write("\n")


def csv_to_list(value):
    return [item.strip() for item in value.split(",") if item.strip()]


def list_to_csv(value):
    return ", ".join(value or [])


def parse_json_field(value, fallback):
    value = value.strip()
    if not value:
        return fallback
    return json.loads(value)


class CatalogEditor(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("LastChat Catalog Editor")
        self.geometry("1040x680")
        self.catalog_path = CATALOG_PATH
        self.catalog = load_catalog(self.catalog_path)

        self._build_shell()
        self._build_providers_tab()
        self._build_models_tab()
        self._build_groups_tab()
        self.refresh_all()

    def _build_shell(self):
        top = ttk.Frame(self, padding=8)
        top.pack(fill=tk.X)
        ttk.Label(top, text=str(self.catalog_path)).pack(side=tk.LEFT, fill=tk.X, expand=True)
        ttk.Button(top, text="Open", command=self.open_catalog).pack(side=tk.LEFT, padx=4)
        ttk.Button(top, text="Validate", command=self.validate_catalog).pack(side=tk.LEFT, padx=4)
        ttk.Button(top, text="Save", command=self.save).pack(side=tk.LEFT, padx=4)

        self.status = tk.StringVar(value="Ready")
        ttk.Label(self, textvariable=self.status, padding=(8, 0)).pack(fill=tk.X)

        self.tabs = ttk.Notebook(self)
        self.tabs.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

    def make_tab(self, name):
        tab = ttk.Frame(self.tabs, padding=8)
        self.tabs.add(tab, text=name)
        left = ttk.Frame(tab)
        right = ttk.Frame(tab)
        left.pack(side=tk.LEFT, fill=tk.Y)
        right.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(12, 0))
        return left, right

    def add_field(self, parent, label, row, multiline=False):
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky=tk.W, pady=3)
        if multiline:
            widget = tk.Text(parent, height=5, width=70, wrap=tk.WORD)
            widget.grid(row=row, column=1, sticky=tk.EW, pady=3)
        else:
            widget = ttk.Entry(parent, width=72)
            widget.grid(row=row, column=1, sticky=tk.EW, pady=3)
        return widget

    def _build_providers_tab(self):
        left, right = self.make_tab("Providers")
        self.provider_list = tk.Listbox(left, width=32, height=26, exportselection=False)
        self.provider_list.pack(fill=tk.Y, expand=True)
        self.provider_list.bind("<<ListboxSelect>>", lambda _event: self.load_provider_form())
        ttk.Button(left, text="Add Provider", command=self.add_provider).pack(fill=tk.X, pady=(8, 2))
        ttk.Button(left, text="Move Up", command=lambda: self.move_selected(self.provider_list, self.catalog["providers"], -1)).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Move Down", command=lambda: self.move_selected(self.provider_list, self.catalog["providers"], 1)).pack(fill=tk.X, pady=2)
        ttk.Button(left, text="Delete Provider", command=self.delete_provider).pack(fill=tk.X, pady=(2, 0))

        form = ttk.Frame(right)
        form.pack(fill=tk.BOTH, expand=True)
        self.p_id = self.add_field(form, "Stable UUID", 0)
        self.p_name = self.add_field(form, "Name", 1)
        self.p_description = self.add_field(form, "Description", 2)
        self.p_type = self.add_field(form, "Type (openai/google/claude)", 3)
        self.p_base = self.add_field(form, "Base URL", 4)
        self.p_path = self.add_field(form, "Chat path", 5)
        self.p_response = self.add_field(form, "Use Responses API (true/false)", 6)
        self.p_balance = self.add_field(form, "Balance JSON", 7, multiline=True)
        self.p_icon = self.add_field(form, "Icon path", 8)
        self.p_reasoning_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            form,
            text="Custom reasoning payload",
            variable=self.p_reasoning_enabled,
            command=lambda: self.sync_reasoning_state(self.p_reasoning, self.p_reasoning_enabled),
        ).grid(row=9, column=1, sticky=tk.W, pady=3)
        self.p_stream_mode = self.add_field(form, "Stream options mode (auto/enabled/disabled)", 10)
        self.p_image_mode = self.add_field(form, "Image modalities mode (auto/enabled/disabled)", 11)
        self.p_replay_mode = self.add_field(form, "Reasoning replay mode (auto/enabled/disabled)", 12)
        self.p_reasoning = self.add_field(form, "Reasoning behavior JSON", 13, multiline=True)
        ttk.Button(form, text="Apply Provider Changes", command=self.apply_provider).grid(row=14, column=1, sticky=tk.E, pady=8)
        form.columnconfigure(1, weight=1)

    def _build_models_tab(self):
        left, right = self.make_tab("Models")
        self.model_list = tk.Listbox(left, width=36, height=26, exportselection=False)
        self.model_list.pack(fill=tk.Y, expand=True)
        self.model_list.bind("<<ListboxSelect>>", lambda _event: self.load_model_form())
        ttk.Button(left, text="Add Model", command=self.add_model).pack(fill=tk.X, pady=(8, 2))
        ttk.Button(left, text="Delete Model", command=self.delete_model).pack(fill=tk.X)

        form = ttk.Frame(right)
        form.pack(fill=tk.BOTH, expand=True)
        self.m_id = self.add_field(form, "Model id", 0)
        self.m_display = self.add_field(form, "Display name", 1)
        self.m_canonical = self.add_field(form, "Canonical id", 2)
        self.m_aliases = self.add_field(form, "API aliases", 3)
        self.m_providers = self.add_field(form, "Provider UUIDs", 4)
        self.m_type = self.add_field(form, "Type (CHAT/IMAGE/EMBEDDING)", 5)
        self.m_image_method = self.add_field(form, "Image method", 6)
        self.m_inputs = self.add_field(form, "Input modalities", 7)
        self.m_outputs = self.add_field(form, "Output modalities", 8)
        self.m_abilities = self.add_field(form, "Abilities", 9)
        self.m_group = self.add_field(form, "Family id", 10)
        self.m_slug = self.add_field(form, "Provider slug", 11)
        self.m_reasoning_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            form,
            text="Custom reasoning payload",
            variable=self.m_reasoning_enabled,
            command=lambda: self.sync_reasoning_state(self.m_reasoning, self.m_reasoning_enabled),
        ).grid(row=12, column=1, sticky=tk.W, pady=3)
        self.m_reasoning = self.add_field(form, "Reasoning behavior JSON", 13, multiline=True)
        ttk.Button(form, text="Apply Model Changes", command=self.apply_model).grid(row=14, column=1, sticky=tk.E, pady=8)
        form.columnconfigure(1, weight=1)

    def _build_groups_tab(self):
        left, right = self.make_tab("Families")
        self.group_list = tk.Listbox(left, width=32, height=26, exportselection=False)
        self.group_list.pack(fill=tk.Y, expand=True)
        self.group_list.bind("<<ListboxSelect>>", lambda _event: self.load_group_form())
        ttk.Button(left, text="Add Family", command=self.add_group).pack(fill=tk.X, pady=(8, 2))
        ttk.Button(left, text="Delete Family", command=self.delete_group).pack(fill=tk.X)

        form = ttk.Frame(right)
        form.pack(fill=tk.BOTH, expand=True)
        self.g_id = self.add_field(form, "Family id", 0)
        self.g_name = self.add_field(form, "Display name", 1)
        self.g_aliases = self.add_field(form, "Aliases", 2)
        self.g_patterns = self.add_field(form, "Match patterns", 3)
        self.g_icon = self.add_field(form, "Icon path", 4)
        self.g_type = self.add_field(form, "Default type (CHAT/IMAGE/EMBEDDING)", 5)
        self.g_image_method = self.add_field(form, "Default image method", 6)
        self.g_inputs = self.add_field(form, "Default input modalities", 7)
        self.g_outputs = self.add_field(form, "Default output modalities", 8)
        self.g_abilities = self.add_field(form, "Default abilities", 9)
        self.g_slug = self.add_field(form, "Default provider slug", 10)
        self.g_versions = self.add_field(form, "Versions JSON", 11, multiline=True)
        ttk.Button(form, text="Apply Family Changes", command=self.apply_group).grid(row=12, column=1, sticky=tk.E, pady=8)
        form.columnconfigure(1, weight=1)

    def refresh_all(self):
        self.refresh_list(self.provider_list, self.catalog["providers"], "name")
        self.refresh_list(self.model_list, self.catalog["models"], "id")
        self.refresh_list(self.group_list, self.catalog["model_families"], "id")

    def refresh_list(self, listbox, items, label_key):
        selected = listbox.curselection()
        selected_index = selected[0] if selected else None
        listbox.delete(0, tk.END)
        for item in items:
            listbox.insert(tk.END, item.get(label_key, ""))
        if selected_index is not None and selected_index < len(items):
            listbox.selection_set(selected_index)

    def selected_index(self, listbox):
        selection = listbox.curselection()
        return selection[0] if selection else None

    def set_entry(self, widget, value):
        if isinstance(widget, tk.Text):
            widget.delete("1.0", tk.END)
            widget.insert("1.0", value or "")
        else:
            widget.delete(0, tk.END)
            widget.insert(0, value or "")

    def get_entry(self, widget):
        if isinstance(widget, tk.Text):
            return widget.get("1.0", tk.END).strip()
        return widget.get().strip()

    def sync_reasoning_state(self, widget, variable):
        state = tk.NORMAL if variable.get() else tk.DISABLED
        widget.configure(state=state)

    def set_reasoning_field(self, widget, variable, value):
        variable.set(bool(value))
        widget.configure(state=tk.NORMAL)
        self.set_entry(widget, json.dumps(value or {}, indent=2))
        self.sync_reasoning_state(widget, variable)

    def load_provider_form(self):
        idx = self.selected_index(self.provider_list)
        if idx is None:
            return
        item = self.catalog["providers"][idx]
        self.set_entry(self.p_id, item.get("id", ""))
        self.set_entry(self.p_name, item.get("name", ""))
        self.set_entry(self.p_description, item.get("description", ""))
        self.set_entry(self.p_type, item.get("type", "openai"))
        self.set_entry(self.p_base, item.get("base_url", ""))
        self.set_entry(self.p_path, item.get("chat_completions_path", "/chat/completions"))
        self.set_entry(self.p_response, str(item.get("use_response_api", False)).lower())
        self.set_entry(self.p_balance, json.dumps(item.get("balance_option", {}), indent=2))
        self.set_entry(self.p_icon, item.get("icon", ""))
        self.set_entry(self.p_stream_mode, item.get("stream_options_mode", "auto"))
        self.set_entry(self.p_image_mode, item.get("image_response_modalities_mode", "auto"))
        self.set_entry(self.p_replay_mode, item.get("reasoning_content_replay_mode", "auto"))
        self.set_reasoning_field(self.p_reasoning, self.p_reasoning_enabled, item.get("reasoning_behavior", {}))

    def apply_provider(self):
        idx = self.selected_index(self.provider_list)
        if idx is None:
            self.status.set("Select a provider before applying changes.")
            return
        try:
            item = {
                "id": self.get_entry(self.p_id),
                "name": self.get_entry(self.p_name),
                "description": self.get_entry(self.p_description),
                "type": self.get_entry(self.p_type) or "openai",
                "base_url": self.get_entry(self.p_base),
                "chat_completions_path": self.get_entry(self.p_path) or "/chat/completions",
                "use_response_api": self.get_entry(self.p_response).lower() == "true",
                "balance_option": parse_json_field(self.get_entry(self.p_balance), {}),
                "icon": self.get_entry(self.p_icon) or None,
                "preset": True,
                "stream_options_mode": self.get_entry(self.p_stream_mode) or "auto",
                "image_response_modalities_mode": self.get_entry(self.p_image_mode) or "auto",
                "reasoning_content_replay_mode": self.get_entry(self.p_replay_mode) or "auto",
            }
            reasoning = parse_json_field(self.get_entry(self.p_reasoning), {}) if self.p_reasoning_enabled.get() else {}
            if reasoning:
                item["reasoning_behavior"] = reasoning
            self.catalog["providers"][idx] = self.clean_none(item)
            self.refresh_all()
            self.provider_list.selection_clear(0, tk.END)
            self.provider_list.selection_set(idx)
            self.provider_list.activate(idx)
            self.status.set(f"Applied provider changes: {item['name']}")
        except Exception as exc:
            messagebox.showerror("Provider error", str(exc))

    def load_model_form(self):
        idx = self.selected_index(self.model_list)
        if idx is None:
            return
        item = self.catalog["models"][idx]
        self.set_entry(self.m_id, item.get("id", ""))
        self.set_entry(self.m_display, item.get("display_name", ""))
        self.set_entry(self.m_canonical, item.get("canonical_model_id", ""))
        self.set_entry(self.m_aliases, list_to_csv(item.get("api_aliases", [])))
        self.set_entry(self.m_providers, list_to_csv(item.get("provider_ids", [])))
        self.set_entry(self.m_type, item.get("type", "CHAT"))
        self.set_entry(self.m_image_method, item.get("image_generation_method", ""))
        self.set_entry(self.m_inputs, list_to_csv(item.get("input_modalities", [])))
        self.set_entry(self.m_outputs, list_to_csv(item.get("output_modalities", [])))
        self.set_entry(self.m_abilities, list_to_csv(item.get("abilities", [])))
        self.set_entry(self.m_group, item.get("family_id", item.get("group_id", "")))
        self.set_entry(self.m_slug, item.get("provider_slug", ""))
        self.set_reasoning_field(self.m_reasoning, self.m_reasoning_enabled, item.get("reasoning_behavior", {}))

    def apply_model(self):
        idx = self.selected_index(self.model_list)
        if idx is None:
            self.status.set("Select a model before applying changes.")
            return
        try:
            item = {
                "id": self.get_entry(self.m_id),
                "display_name": self.get_entry(self.m_display),
                "canonical_model_id": self.get_entry(self.m_canonical) or None,
                "api_aliases": csv_to_list(self.get_entry(self.m_aliases)),
                "provider_ids": csv_to_list(self.get_entry(self.m_providers)),
                "type": self.get_entry(self.m_type) or "CHAT",
                "image_generation_method": self.get_entry(self.m_image_method) or None,
                "input_modalities": csv_to_list(self.get_entry(self.m_inputs)) or ["TEXT"],
                "output_modalities": csv_to_list(self.get_entry(self.m_outputs)) or ["TEXT"],
                "abilities": csv_to_list(self.get_entry(self.m_abilities)),
                "family_id": self.get_entry(self.m_group) or None,
                "provider_slug": self.get_entry(self.m_slug) or None,
            }
            reasoning = parse_json_field(self.get_entry(self.m_reasoning), {}) if self.m_reasoning_enabled.get() else {}
            if reasoning:
                item["reasoning_behavior"] = reasoning
            self.catalog["models"][idx] = self.clean_none(item)
            self.refresh_all()
            self.model_list.selection_clear(0, tk.END)
            self.model_list.selection_set(idx)
            self.model_list.activate(idx)
            self.status.set(f"Applied model changes: {item['id']}")
        except Exception as exc:
            messagebox.showerror("Model error", str(exc))

    def load_group_form(self):
        idx = self.selected_index(self.group_list)
        if idx is None:
            return
        item = self.catalog["model_families"][idx]
        self.set_entry(self.g_id, item.get("id", ""))
        self.set_entry(self.g_name, item.get("display_name", ""))
        self.set_entry(self.g_aliases, list_to_csv(item.get("aliases", [])))
        self.set_entry(self.g_patterns, list_to_csv(item.get("match_patterns", [])))
        self.set_entry(self.g_icon, item.get("icon", ""))
        self.set_entry(self.g_type, item.get("type", "CHAT"))
        self.set_entry(self.g_image_method, item.get("image_generation_method", ""))
        self.set_entry(self.g_inputs, list_to_csv(item.get("input_modalities", [])))
        self.set_entry(self.g_outputs, list_to_csv(item.get("output_modalities", [])))
        self.set_entry(self.g_abilities, list_to_csv(item.get("abilities", [])))
        self.set_entry(self.g_slug, item.get("provider_slug", ""))
        self.set_entry(self.g_versions, json.dumps(item.get("versions", []), indent=2))

    def apply_group(self):
        idx = self.selected_index(self.group_list)
        if idx is None:
            self.status.set("Select a family before applying changes.")
            return
        item = self.clean_none({
            "id": self.get_entry(self.g_id),
            "display_name": self.get_entry(self.g_name),
            "aliases": csv_to_list(self.get_entry(self.g_aliases)),
            "match_patterns": csv_to_list(self.get_entry(self.g_patterns)),
            "icon": self.get_entry(self.g_icon) or None,
            "type": self.get_entry(self.g_type) or "CHAT",
            "image_generation_method": self.get_entry(self.g_image_method) or None,
            "input_modalities": csv_to_list(self.get_entry(self.g_inputs)) or ["TEXT"],
            "output_modalities": csv_to_list(self.get_entry(self.g_outputs)) or ["TEXT"],
            "abilities": csv_to_list(self.get_entry(self.g_abilities)),
            "provider_slug": self.get_entry(self.g_slug) or None,
            "versions": parse_json_field(self.get_entry(self.g_versions), []),
        })
        self.catalog["model_families"][idx] = item
        self.refresh_all()
        self.group_list.selection_clear(0, tk.END)
        self.group_list.selection_set(idx)
        self.group_list.activate(idx)
        self.status.set(f"Applied family changes: {item['id']}")

    def add_provider(self):
        self.catalog["providers"].append({
            "id": "",
            "name": "New Provider",
            "description": "",
            "type": "openai",
            "base_url": "https://example.com/v1",
            "chat_completions_path": "/chat/completions",
            "preset": True,
        })
        self.refresh_all()
        self.provider_list.selection_clear(0, tk.END)
        self.provider_list.selection_set(tk.END)
        self.load_provider_form()

    def add_model(self):
        self.catalog["models"].append({
            "id": "new-model",
            "display_name": "New Model",
            "type": "CHAT",
            "input_modalities": ["TEXT"],
            "output_modalities": ["TEXT"],
            "abilities": [],
        })
        self.refresh_all()
        self.model_list.selection_clear(0, tk.END)
        self.model_list.selection_set(tk.END)
        self.load_model_form()

    def add_group(self):
        self.catalog["model_families"].append({
            "id": "new-family",
            "display_name": "New Family",
            "aliases": [],
            "match_patterns": [],
            "type": "CHAT",
            "input_modalities": ["TEXT"],
            "output_modalities": ["TEXT"],
            "abilities": [],
            "versions": [],
        })
        self.refresh_all()
        self.group_list.selection_clear(0, tk.END)
        self.group_list.selection_set(tk.END)
        self.load_group_form()

    def delete_provider(self):
        self.delete_selected(self.provider_list, self.catalog["providers"])

    def delete_model(self):
        self.delete_selected(self.model_list, self.catalog["models"])

    def delete_group(self):
        self.delete_selected(self.group_list, self.catalog["model_families"])

    def delete_selected(self, listbox, items):
        idx = self.selected_index(listbox)
        if idx is None:
            return
        if messagebox.askyesno("Delete", "Delete selected item?"):
            del items[idx]
            self.refresh_all()

    def move_selected(self, listbox, items, direction):
        idx = self.selected_index(listbox)
        if idx is None:
            return
        new_idx = idx + direction
        if new_idx < 0 or new_idx >= len(items):
            return
        items[idx], items[new_idx] = items[new_idx], items[idx]
        self.refresh_all()
        listbox.selection_clear(0, tk.END)
        listbox.selection_set(new_idx)
        listbox.activate(new_idx)
        listbox.see(new_idx)
        if listbox is self.provider_list:
            self.load_provider_form()

    def open_catalog(self):
        filename = filedialog.askopenfilename(
            initialdir=str(ROOT / "catalog"),
            filetypes=[("JSON", "*.json"), ("All files", "*.*")]
        )
        if not filename:
            return
        self.catalog_path = Path(filename)
        self.catalog = load_catalog(self.catalog_path)
        self.refresh_all()
        self.status.set(f"Opened {self.catalog_path}")

    def save(self):
        try:
            self.apply_current_tab_changes()
            self.validate_catalog(show_success=False)
            save_catalog(self.catalog_path, self.catalog)
            self.status.set(f"Saved {self.catalog_path}")
        except Exception as exc:
            messagebox.showerror("Save failed", str(exc))

    def apply_current_tab_changes(self):
        current_tab = self.tabs.tab(self.tabs.select(), "text")
        if current_tab == "Providers" and self.selected_index(self.provider_list) is not None:
            self.apply_provider()
        elif current_tab == "Models" and self.selected_index(self.model_list) is not None:
            self.apply_model()
        elif current_tab == "Families" and self.selected_index(self.group_list) is not None:
            self.apply_group()

    def validate_catalog(self, show_success=True):
        provider_ids = {provider.get("id") for provider in self.catalog["providers"]}
        family_ids = {family.get("id") for family in self.catalog["model_families"]}
        icon_paths = [
            *(provider.get("icon", "") for provider in self.catalog["providers"]),
            *(family.get("icon", "") for family in self.catalog["model_families"]),
        ]
        missing_icons = [
            path for path in icon_paths
            if path and not path.startswith(("http://", "https://")) and not (ROOT / "catalog" / path).exists()
        ]
        unknown_provider_refs = [
            model.get("id", "")
            for model in self.catalog["models"]
            for provider_id in model.get("provider_ids", [])
            if provider_id not in provider_ids
        ]
        unknown_groups = [
            model.get("id", "")
            for model in self.catalog["models"]
            if (model.get("family_id") or model.get("group_id")) and (model.get("family_id") or model.get("group_id")) not in family_ids
        ]
        bad_family_versions = [
            family.get("id", "")
            for family in self.catalog["model_families"]
            if not isinstance(family.get("versions", []), list)
        ]
        bad_patterns = []
        for family in self.catalog["model_families"]:
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
        models_without_providers = [
            model.get("id", "")
            for model in self.catalog["models"]
            if not model.get("provider_ids")
        ]
        model_ids = {model.get("id", "").lower() for model in self.catalog["models"]}
        missing_setup_refs = [
            f"{provider.get('name', '')}: {model_id}"
            for provider in self.catalog["providers"]
            for model_id in (
                list(provider.get("setup_models", []))
                + list((provider.get("setup_defaults") or {}).values())
            )
            if model_id and model_id.lower() not in model_ids
        ]
        duplicate_model_ids = sorted({
            model_id for model_id in model_ids
            if sum(1 for model in self.catalog["models"] if model.get("id", "").lower() == model_id) > 1
        })
        problems = []
        if missing_icons:
            problems.append("Missing icon files: " + ", ".join(missing_icons))
        if unknown_provider_refs:
            problems.append("Models with unknown provider ids: " + ", ".join(unknown_provider_refs))
        if unknown_groups:
            problems.append("Models with unknown families: " + ", ".join(unknown_groups))
        if bad_family_versions:
            problems.append("Families with non-list versions: " + ", ".join(bad_family_versions))
        if bad_patterns:
            problems.append("Invalid family regex patterns: " + ", ".join(bad_patterns))
        if models_without_providers:
            problems.append("Models without provider ids: " + ", ".join(models_without_providers))
        if missing_setup_refs:
            problems.append("Setup model refs missing from models: " + ", ".join(missing_setup_refs))
        if duplicate_model_ids:
            problems.append("Duplicate model ids: " + ", ".join(duplicate_model_ids))
        if problems:
            raise ValueError("\n".join(problems))
        if show_success:
            messagebox.showinfo("Catalog valid", "Catalog looks good.")
        return True

    @staticmethod
    def clean_none(value):
        return {key: item for key, item in value.items() if item is not None}


if __name__ == "__main__":
    app = CatalogEditor()
    app.mainloop()
