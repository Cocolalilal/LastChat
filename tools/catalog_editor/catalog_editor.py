import json
import re
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk


ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = ROOT / "catalog" / "lastchat_catalog.json"

MODEL_TYPES = ["CHAT", "IMAGE", "EMBEDDING"]
MODALITIES = ["TEXT", "IMAGE"]
ABILITIES = ["TOOL", "REASONING"]
IMAGE_METHODS = ["", "diffusion", "multimodal"]
REQUEST_MODES = ["auto", "enabled", "disabled"]


def empty_catalog():
    return {
        "schema_version": 2,
        "updated_at": "",
        "providers": [],
        "global_rules": [],
        "model_families": [],
        "model_overrides": [],
        "models": [],
    }


def load_catalog(path):
    if not path.exists():
        return empty_catalog()
    with path.open("r", encoding="utf-8") as fh:
        catalog = json.load(fh)
    catalog.setdefault("schema_version", 1)
    catalog.setdefault("providers", [])
    catalog.setdefault("global_rules", [])
    if "model_families" not in catalog:
        catalog["model_families"] = catalog.get("model_groups", [])
    catalog.pop("model_groups", None)
    catalog.setdefault("models", [])
    catalog.setdefault("model_overrides", [])
    if not catalog["model_overrides"] and catalog["models"]:
        catalog["model_overrides"] = [legacy_model_to_override(model) for model in catalog["models"]]
    catalog.pop("icons", None)
    for provider in catalog["providers"]:
        provider.pop("models", None)
    for model in catalog["models"]:
        model.pop("icon", None)
        if "family_id" not in model and model.get("group_id"):
            model["family_id"] = model.get("group_id")
        model.pop("group_id", None)
    strip_model_display_names(catalog)
    return catalog


def legacy_model_to_override(model):
    override = model.copy()
    override.pop("family_id", None)
    override.pop("group_id", None)
    override.pop("context_window", None)
    return override


def strip_model_display_names(catalog):
    for model in catalog.get("models", []):
        model.pop("display_name", None)
    for override in catalog.get("model_overrides", []):
        override.pop("display_name", None)
    for rule in catalog.get("global_rules", []):
        rule.pop("display_name", None)
    for family in catalog.get("model_families", []):
        family.pop("display_name", None)
        for version in family.get("versions", []):
            if isinstance(version, dict):
                version.pop("display_name", None)


def save_catalog(path, catalog):
    path.parent.mkdir(parents=True, exist_ok=True)
    strip_model_display_names(catalog)
    with path.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump(catalog, fh, indent=2, ensure_ascii=False, sort_keys=False)
        fh.write("\n")


def csv_to_list(value):
    return [item.strip() for item in value.split(",") if item.strip()]


def list_to_csv(value):
    return ", ".join(value or [])


def list_to_lines(value):
    return "\n".join(value or [])


def lines_to_list(value):
    return [item.strip() for item in value.splitlines() if item.strip()]


def parse_json_field(value, fallback):
    value = value.strip()
    if not value:
        return fallback
    return json.loads(value)


class CatalogEditor(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("LastChat Catalog Editor")
        self.geometry("1180x840")
        self.catalog_path = CATALOG_PATH
        self.catalog = load_catalog(self.catalog_path)
        self.filtered_provider_indices = []
        self.filtered_model_indices = []
        self.filtered_group_indices = []

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
        ttk.Button(top, text="Validate", command=self.validate_from_ui).pack(side=tk.LEFT, padx=4)
        ttk.Button(top, text="Migration Report", command=self.show_migration_report).pack(side=tk.LEFT, padx=4)
        ttk.Button(top, text="Save", command=self.save).pack(side=tk.LEFT, padx=4)

        self.status = tk.StringVar(value="Ready")
        ttk.Label(self, textvariable=self.status, padding=(8, 0)).pack(fill=tk.X)

        self.tabs = ttk.Notebook(self)
        self.tabs.pack(fill=tk.BOTH, expand=True, padx=8, pady=8)

    def make_tab(self, name):
        tab = ttk.Frame(self.tabs, padding=8)
        self.tabs.add(tab, text=name)
        left = ttk.Frame(tab)
        right = self.make_scrollable_frame(tab)
        left.pack(side=tk.LEFT, fill=tk.Y)
        return left, right

    def make_scrollable_frame(self, parent):
        container = ttk.Frame(parent)
        container.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(12, 0))

        canvas = tk.Canvas(container, highlightthickness=0)
        scrollbar = ttk.Scrollbar(container, orient=tk.VERTICAL, command=canvas.yview)
        frame = ttk.Frame(canvas)
        frame_window = canvas.create_window((0, 0), window=frame, anchor=tk.NW)

        frame.bind(
            "<Configure>",
            lambda _event: canvas.configure(scrollregion=canvas.bbox("all")),
        )
        canvas.bind(
            "<Configure>",
            lambda event: canvas.itemconfigure(frame_window, width=event.width),
        )
        canvas.configure(yscrollcommand=scrollbar.set)
        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        canvas.bind("<Enter>", lambda _event: self.bind_mousewheel(canvas))
        canvas.bind("<Leave>", lambda _event: self.unbind_mousewheel(canvas))
        frame.bind("<Enter>", lambda _event: self.bind_mousewheel(canvas))
        frame.bind("<Leave>", lambda _event: self.unbind_mousewheel(canvas))
        return frame

    def bind_mousewheel(self, canvas):
        canvas.bind_all("<MouseWheel>", lambda event: self.on_mousewheel(event, canvas))
        canvas.bind_all("<Button-4>", lambda event: self.on_mousewheel(event, canvas))
        canvas.bind_all("<Button-5>", lambda event: self.on_mousewheel(event, canvas))

    def unbind_mousewheel(self, canvas):
        canvas.unbind_all("<MouseWheel>")
        canvas.unbind_all("<Button-4>")
        canvas.unbind_all("<Button-5>")

    def on_mousewheel(self, event, canvas):
        if getattr(event, "num", None) == 4:
            canvas.yview_scroll(-1, "units")
        elif getattr(event, "num", None) == 5:
            canvas.yview_scroll(1, "units")
        else:
            canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

    def add_search_field(self, parent, variable, callback):
        search = ttk.Entry(parent, textvariable=variable)
        search.pack(fill=tk.X, pady=(0, 6))
        variable.trace_add("write", callback)
        return search

    def provider_matches_query(self, item):
        query = self.provider_search.get().strip()
        return self.item_matches_query(
            item,
            query,
            ["name", "id", "description", "type", "base_url", "icon", "setup_search_service"],
            ["setup_models"],
            ["setup_defaults", "balance_option"],
        )

    def model_matches_query(self, item):
        query = self.model_search.get().strip()
        return self.item_matches_query(
            item,
            query,
            ["id", "canonical_model_id", "type", "provider_slug"],
            ["api_aliases", "provider_ids", "provider_slugs", "input_modalities", "output_modalities", "abilities"],
            ["match_patterns", "exclude_patterns", "base_url_patterns"],
        )

    def family_matches_query(self, item):
        query = self.group_search.get().strip()
        return self.item_matches_query(
            item,
            query,
            ["id", "icon", "type", "provider_slug"],
            ["aliases", "match_patterns", "input_modalities", "output_modalities", "abilities"],
            ["versions"],
        )

    def item_matches_query(self, item, query, text_keys, list_keys, object_keys):
        terms = [term.lower() for term in query.split() if term.strip()]
        if not terms:
            return True
        haystack = []
        for key in text_keys:
            value = item.get(key)
            if value is not None:
                haystack.append(str(value))
        for key in list_keys:
            haystack.extend(str(value) for value in item.get(key, []))
        for key in object_keys:
            value = item.get(key)
            if value:
                haystack.append(json.dumps(value, ensure_ascii=False))
        searchable = " ".join(haystack).lower()
        return all(term in searchable for term in terms)

    def add_field(self, parent, label, row, multiline=False, height=5):
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky=tk.W, pady=3)
        if multiline:
            widget = tk.Text(parent, height=height, width=70, wrap=tk.WORD)
            widget.grid(row=row, column=1, sticky=tk.EW, pady=3)
        else:
            widget = ttk.Entry(parent, width=72)
            widget.grid(row=row, column=1, sticky=tk.EW, pady=3)
        return widget

    def add_combo(self, parent, label, row, values):
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky=tk.W, pady=3)
        widget = ttk.Combobox(parent, values=values, state="readonly", width=69)
        widget.grid(row=row, column=1, sticky=tk.EW, pady=3)
        return widget

    def add_checklist(self, parent, label, row, values):
        ttk.Label(parent, text=label).grid(row=row, column=0, sticky=tk.W, pady=3)
        frame = ttk.Frame(parent)
        frame.grid(row=row, column=1, sticky=tk.W, pady=3)
        variables = {}
        for column, value in enumerate(values):
            variable = tk.BooleanVar(value=False)
            ttk.Checkbutton(frame, text=value, variable=variable).grid(row=0, column=column, sticky=tk.W, padx=(0, 12))
            variables[value] = variable
        return variables

    def _build_providers_tab(self):
        left, right = self.make_tab("Providers")
        self.provider_search = tk.StringVar()
        self.add_search_field(left, self.provider_search, lambda *_args: self.refresh_provider_list())
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
        self.p_setup_recommended = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            form,
            text="Show in onboarding recommendations",
            variable=self.p_setup_recommended,
        ).grid(row=9, column=1, sticky=tk.W, pady=3)
        self.p_setup_order = self.add_field(form, "Setup order", 10)
        self.p_setup_description = self.add_field(form, "Setup description", 11, multiline=True, height=2)
        self.p_setup_models = self.add_field(form, "Setup models", 12, multiline=True, height=4)
        self.p_default_chat = self.add_field(form, "Default chat model", 13)
        self.p_default_title = self.add_field(form, "Default title model", 14)
        self.p_default_summarizer = self.add_field(form, "Default summarizer model", 15)
        self.p_default_ocr = self.add_field(form, "Default OCR model", 16)
        self.p_setup_search = self.add_field(form, "Setup search service", 17)
        self.p_reasoning_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            form,
            text="Custom reasoning payload",
            variable=self.p_reasoning_enabled,
            command=lambda: self.sync_reasoning_state(self.p_reasoning, self.p_reasoning_enabled),
        ).grid(row=18, column=1, sticky=tk.W, pady=3)
        self.p_stream_mode = self.add_combo(form, "Stream options mode", 19, REQUEST_MODES)
        self.p_image_mode = self.add_combo(form, "Image modalities mode", 20, REQUEST_MODES)
        self.p_replay_mode = self.add_combo(form, "Reasoning replay mode", 21, REQUEST_MODES)
        self.p_reasoning = self.add_field(form, "Reasoning behavior JSON", 22, multiline=True)
        ttk.Button(form, text="Apply Provider Changes", command=self.apply_provider).grid(row=23, column=1, sticky=tk.E, pady=8)
        form.columnconfigure(1, weight=1)

    def _build_models_tab(self):
        left, right = self.make_tab("Overrides")
        self.model_search = tk.StringVar()
        self.add_search_field(left, self.model_search, lambda *_args: self.refresh_model_list())
        self.model_list = tk.Listbox(left, width=36, height=26, exportselection=False)
        self.model_list.pack(fill=tk.Y, expand=True)
        self.model_list.bind("<<ListboxSelect>>", lambda _event: self.load_model_form())
        ttk.Button(left, text="Add Override", command=self.add_model).pack(fill=tk.X, pady=(8, 2))
        ttk.Button(left, text="Delete Override", command=self.delete_model).pack(fill=tk.X)

        form = ttk.Frame(right)
        form.pack(fill=tk.BOTH, expand=True)
        self.m_id = self.add_field(form, "Exact model id", 0)
        self.m_canonical = self.add_field(form, "Canonical id", 1)
        self.m_aliases = self.add_field(form, "API aliases", 2)
        self.m_patterns = self.add_field(form, "Match patterns", 3, multiline=True, height=3)
        self.m_excludes = self.add_field(form, "Exclude patterns", 4, multiline=True, height=2)
        self.m_providers = self.add_field(form, "Provider UUID constraints", 5)
        self.m_provider_slugs = self.add_field(form, "Provider slug constraints", 6)
        self.m_base_patterns = self.add_field(form, "Base URL patterns", 7, multiline=True, height=2)
        self.m_type = self.add_combo(form, "Type override", 8, ["", *MODEL_TYPES])
        self.m_image_method = self.add_combo(form, "Image method override", 9, IMAGE_METHODS)
        self.m_inputs = self.add_checklist(form, "Input modalities override", 10, MODALITIES)
        self.m_outputs = self.add_checklist(form, "Output modalities override", 11, MODALITIES)
        self.m_abilities = self.add_checklist(form, "Abilities override", 12, ABILITIES)
        self.m_slug = self.add_field(form, "Result provider slug", 13)
        self.m_input_cost = self.add_field(form, "Input cost per token", 14)
        self.m_output_cost = self.add_field(form, "Output cost per token", 15)
        self.m_reasoning_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(
            form,
            text="Custom reasoning payload",
            variable=self.m_reasoning_enabled,
            command=lambda: self.sync_reasoning_state(self.m_reasoning, self.m_reasoning_enabled),
        ).grid(row=16, column=1, sticky=tk.W, pady=3)
        self.m_reasoning = self.add_field(form, "Reasoning behavior JSON", 17, multiline=True)
        ttk.Button(form, text="Apply Override Changes", command=self.apply_model).grid(row=18, column=1, sticky=tk.E, pady=8)
        form.columnconfigure(1, weight=1)

    def _build_groups_tab(self):
        left, right = self.make_tab("Families")
        self.group_search = tk.StringVar()
        self.add_search_field(left, self.group_search, lambda *_args: self.refresh_group_list())
        self.group_list = tk.Listbox(left, width=32, height=26, exportselection=False)
        self.group_list.pack(fill=tk.Y, expand=True)
        self.group_list.bind("<<ListboxSelect>>", lambda _event: self.load_group_form())
        ttk.Button(left, text="Add Family", command=self.add_group).pack(fill=tk.X, pady=(8, 2))
        ttk.Button(left, text="Delete Family", command=self.delete_group).pack(fill=tk.X)

        form = ttk.Frame(right)
        form.pack(fill=tk.BOTH, expand=True)
        self.g_id = self.add_field(form, "Family id", 0)
        self.g_aliases = self.add_field(form, "Aliases", 1)
        self.g_patterns = self.add_field(form, "Match patterns", 2, multiline=True, height=3)
        self.g_icon = self.add_field(form, "Icon path", 3)
        self.g_type = self.add_combo(form, "Default type", 4, MODEL_TYPES)
        self.g_image_method = self.add_combo(form, "Default image method", 5, IMAGE_METHODS)
        self.g_inputs = self.add_checklist(form, "Default input modalities", 6, MODALITIES)
        self.g_outputs = self.add_checklist(form, "Default output modalities", 7, MODALITIES)
        self.g_abilities = self.add_checklist(form, "Default abilities", 8, ABILITIES)
        self.g_slug = self.add_field(form, "Default provider slug", 9)
        self.build_versions_editor(form, 10)
        self.build_family_tester(form, 11)
        ttk.Button(form, text="Apply Family Changes", command=self.apply_group).grid(row=12, column=1, sticky=tk.E, pady=8)
        form.columnconfigure(1, weight=1)

    def build_versions_editor(self, parent, row):
        ttk.Label(parent, text="Versions").grid(row=row, column=0, sticky=tk.NW, pady=3)
        frame = ttk.Frame(parent)
        frame.grid(row=row, column=1, sticky=tk.NSEW, pady=3)

        left = ttk.Frame(frame)
        left.pack(side=tk.LEFT, fill=tk.Y)
        self.version_list = tk.Listbox(left, width=28, height=9, exportselection=False)
        self.version_list.pack(fill=tk.Y, expand=True)
        self.version_list.bind("<<ListboxSelect>>", self.on_version_select)
        buttons = ttk.Frame(left)
        buttons.pack(fill=tk.X, pady=(4, 0))
        ttk.Button(buttons, text="+", width=3, command=self.add_version).pack(side=tk.LEFT)
        ttk.Button(buttons, text="-", width=3, command=self.delete_version).pack(side=tk.LEFT, padx=3)
        ttk.Button(buttons, text="Up", width=4, command=lambda: self.move_version(-1)).pack(side=tk.LEFT)
        ttk.Button(buttons, text="Down", width=5, command=lambda: self.move_version(1)).pack(side=tk.LEFT, padx=3)

        version_form = ttk.Frame(frame)
        version_form.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(10, 0))
        self.v_id = self.add_field(version_form, "Version id", 0)
        self.v_patterns = self.add_field(version_form, "Match patterns", 1, multiline=True, height=3)
        self.v_excludes = self.add_field(version_form, "Exclude patterns", 2, multiline=True, height=2)
        self.v_type = self.add_combo(version_form, "Type override", 3, ["", *MODEL_TYPES])
        self.v_image_method = self.add_combo(version_form, "Image method override", 4, IMAGE_METHODS)
        self.v_canonical = self.add_field(version_form, "Canonical id override", 5)
        self.v_slug = self.add_field(version_form, "Provider slug override", 6)
        self.v_inputs_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(version_form, text="Override input modalities", variable=self.v_inputs_enabled).grid(row=7, column=1, sticky=tk.W)
        self.v_inputs = self.add_checklist(version_form, "Input modalities", 8, MODALITIES)
        self.v_outputs_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(version_form, text="Override output modalities", variable=self.v_outputs_enabled).grid(row=9, column=1, sticky=tk.W)
        self.v_outputs = self.add_checklist(version_form, "Output modalities", 10, MODALITIES)
        self.v_abilities_enabled = tk.BooleanVar(value=False)
        ttk.Checkbutton(version_form, text="Override abilities", variable=self.v_abilities_enabled).grid(row=11, column=1, sticky=tk.W)
        self.v_abilities = self.add_checklist(version_form, "Abilities", 12, ABILITIES)
        version_form.columnconfigure(1, weight=1)

        self.current_versions = []
        self.current_version_index = None
        self.loading_version = False

    def build_family_tester(self, parent, row):
        ttk.Label(parent, text="Try model id").grid(row=row, column=0, sticky=tk.W, pady=3)
        frame = ttk.Frame(parent)
        frame.grid(row=row, column=1, sticky=tk.EW, pady=3)
        self.family_test_id = ttk.Entry(frame)
        self.family_test_id.pack(side=tk.LEFT, fill=tk.X, expand=True)
        ttk.Button(frame, text="Explain Match", command=self.explain_family_match).pack(side=tk.LEFT, padx=(6, 0))
        self.family_test_result = tk.StringVar(value="Enter a model id to see which family/version rules would catch it.")
        ttk.Label(parent, textvariable=self.family_test_result, wraplength=650).grid(row=row + 1, column=1, sticky=tk.W, pady=(0, 6))

    def refresh_all(self):
        self.refresh_provider_list()
        self.refresh_model_list()
        self.refresh_group_list()

    def refresh_provider_list(self):
        self.filtered_provider_indices = self.refresh_filtered_list(
            self.provider_list,
            self.catalog["providers"],
            self.provider_matches_query,
            lambda item: item.get("name", ""),
        )

    def refresh_model_list(self):
        self.filtered_model_indices = self.refresh_filtered_list(
            self.model_list,
            self.catalog["model_overrides"],
            self.model_matches_query,
            lambda item: item.get("id", ""),
        )

    def refresh_group_list(self):
        self.filtered_group_indices = self.refresh_filtered_list(
            self.group_list,
            self.catalog["model_families"],
            self.family_matches_query,
            lambda item: item.get("id", ""),
        )

    def refresh_list(self, listbox, items, label_key):
        selected = listbox.curselection()
        selected_index = selected[0] if selected else None
        listbox.delete(0, tk.END)
        for item in items:
            listbox.insert(tk.END, item.get(label_key, ""))
        if selected_index is not None and selected_index < len(items):
            listbox.selection_set(selected_index)

    def refresh_filtered_list(self, listbox, items, predicate, label):
        selected_catalog_index = self.selected_catalog_index(listbox)
        matching_indices = [
            index for index, item in enumerate(items)
            if predicate(item)
        ]
        listbox.delete(0, tk.END)
        for index in matching_indices:
            listbox.insert(tk.END, label(items[index]))
        if selected_catalog_index in matching_indices:
            filtered_index = matching_indices.index(selected_catalog_index)
            listbox.selection_set(filtered_index)
            listbox.activate(filtered_index)
            listbox.see(filtered_index)
        return matching_indices

    def selected_index(self, listbox):
        selection = listbox.curselection()
        return selection[0] if selection else None

    def selected_catalog_index(self, listbox):
        filtered_index = self.selected_index(listbox)
        if filtered_index is None:
            return None
        if listbox is self.provider_list:
            indices = self.filtered_provider_indices
        elif listbox is self.model_list:
            indices = self.filtered_model_indices
        elif listbox is self.group_list:
            indices = self.filtered_group_indices
        else:
            return filtered_index
        if filtered_index >= len(indices):
            return None
        return indices[filtered_index]

    def restore_selection(self, listbox, catalog_index):
        if catalog_index is None:
            return
        if listbox is self.provider_list:
            indices = self.filtered_provider_indices
        elif listbox is self.model_list:
            indices = self.filtered_model_indices
        elif listbox is self.group_list:
            indices = self.filtered_group_indices
        else:
            indices = list(range(listbox.size()))
        if catalog_index not in indices:
            return
        filtered_index = indices.index(catalog_index)
        listbox.selection_clear(0, tk.END)
        listbox.selection_set(filtered_index)
        listbox.activate(filtered_index)
        listbox.see(filtered_index)

    def set_entry(self, widget, value):
        if isinstance(widget, tk.Text):
            widget.delete("1.0", tk.END)
            widget.insert("1.0", value or "")
        elif isinstance(widget, ttk.Combobox):
            widget.set(value or "")
        else:
            widget.delete(0, tk.END)
            widget.insert(0, value or "")

    def get_entry(self, widget):
        if isinstance(widget, tk.Text):
            return widget.get("1.0", tk.END).strip()
        return widget.get().strip()

    def set_checklist(self, variables, values):
        selected = set(values or [])
        for value, variable in variables.items():
            variable.set(value in selected)

    def get_checklist(self, variables):
        return [value for value, variable in variables.items() if variable.get()]

    def sync_reasoning_state(self, widget, variable):
        state = tk.NORMAL if variable.get() else tk.DISABLED
        widget.configure(state=state)

    def set_reasoning_field(self, widget, variable, value):
        variable.set(bool(value))
        widget.configure(state=tk.NORMAL)
        self.set_entry(widget, json.dumps(value or {}, indent=2))
        self.sync_reasoning_state(widget, variable)

    def load_provider_form(self):
        idx = self.selected_catalog_index(self.provider_list)
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
        self.p_setup_recommended.set(bool(item.get("setup_recommended", False)))
        self.set_entry(self.p_setup_order, str(item.get("setup_order", "")))
        self.set_entry(self.p_setup_description, item.get("setup_description", ""))
        self.set_entry(self.p_setup_models, list_to_lines(item.get("setup_models", [])))
        setup_defaults = item.get("setup_defaults", {}) or {}
        self.set_entry(self.p_default_chat, setup_defaults.get("chat", ""))
        self.set_entry(self.p_default_title, setup_defaults.get("title", ""))
        self.set_entry(self.p_default_summarizer, setup_defaults.get("summarizer", ""))
        self.set_entry(self.p_default_ocr, setup_defaults.get("ocr", ""))
        self.set_entry(self.p_setup_search, item.get("setup_search_service", ""))
        self.set_entry(self.p_stream_mode, item.get("stream_options_mode", "auto"))
        self.set_entry(self.p_image_mode, item.get("image_response_modalities_mode", "auto"))
        self.set_entry(self.p_replay_mode, item.get("reasoning_content_replay_mode", "auto"))
        self.set_reasoning_field(self.p_reasoning, self.p_reasoning_enabled, item.get("reasoning_behavior", {}))

    def apply_provider(self):
        idx = self.selected_catalog_index(self.provider_list)
        if idx is None:
            self.status.set("Select a provider before applying changes.")
            return
        try:
            item = self.catalog["providers"][idx].copy()
            item.update({
                "id": self.get_entry(self.p_id),
                "name": self.get_entry(self.p_name),
                "description": self.get_entry(self.p_description),
                "type": self.get_entry(self.p_type) or "openai",
                "base_url": self.get_entry(self.p_base),
                "icon": self.get_entry(self.p_icon) or None,
                "preset": True,
            })
            self.set_optional(item, "chat_completions_path", self.get_entry(self.p_path) or "/chat/completions", "/chat/completions")
            self.set_optional(item, "use_response_api", self.get_entry(self.p_response).lower() == "true", False)
            self.set_optional(item, "balance_option", parse_json_field(self.get_entry(self.p_balance), {}), {})
            self.set_optional(item, "stream_options_mode", self.get_entry(self.p_stream_mode) or "auto", "auto")
            self.set_optional(item, "image_response_modalities_mode", self.get_entry(self.p_image_mode) or "auto", "auto")
            self.set_optional(item, "reasoning_content_replay_mode", self.get_entry(self.p_replay_mode) or "auto", "auto")
            setup_order = self.get_entry(self.p_setup_order)
            setup_defaults = self.clean_none({
                "chat": self.get_entry(self.p_default_chat) or None,
                "title": self.get_entry(self.p_default_title) or None,
                "summarizer": self.get_entry(self.p_default_summarizer) or None,
                "ocr": self.get_entry(self.p_default_ocr) or None,
            })
            if self.p_setup_recommended.get() or item.get("setup_recommended") is not None:
                item["setup_recommended"] = self.p_setup_recommended.get()
            else:
                item.pop("setup_recommended", None)
            item["setup_order"] = int(setup_order) if setup_order else None
            item["setup_description"] = self.get_entry(self.p_setup_description) or None
            item["setup_models"] = lines_to_list(self.get_entry(self.p_setup_models)) or None
            item["setup_defaults"] = setup_defaults or None
            item["setup_search_service"] = self.get_entry(self.p_setup_search) or None
            reasoning = parse_json_field(self.get_entry(self.p_reasoning), {}) if self.p_reasoning_enabled.get() else {}
            if reasoning:
                item["reasoning_behavior"] = reasoning
            elif "reasoning_behavior" in item:
                del item["reasoning_behavior"]
            self.catalog["providers"][idx] = self.clean_none(item)
            self.refresh_all()
            self.restore_selection(self.provider_list, idx)
            self.status.set(f"Applied provider changes: {item['name']}")
        except Exception as exc:
            messagebox.showerror("Provider error", str(exc))

    def load_model_form(self):
        idx = self.selected_catalog_index(self.model_list)
        if idx is None:
            return
        item = self.catalog["model_overrides"][idx]
        self.set_entry(self.m_id, item.get("id", ""))
        self.set_entry(self.m_canonical, item.get("canonical_model_id", ""))
        self.set_entry(self.m_aliases, list_to_csv(item.get("api_aliases", [])))
        self.set_entry(self.m_patterns, list_to_lines(item.get("match_patterns", [])))
        self.set_entry(self.m_excludes, list_to_lines(item.get("exclude_patterns", [])))
        self.set_entry(self.m_providers, list_to_csv(item.get("provider_ids", [])))
        self.set_entry(self.m_provider_slugs, list_to_csv(item.get("provider_slugs", [])))
        self.set_entry(self.m_base_patterns, list_to_lines(item.get("base_url_patterns", [])))
        self.set_entry(self.m_type, item.get("type", "CHAT"))
        self.set_entry(self.m_image_method, item.get("image_generation_method", ""))
        self.set_checklist(self.m_inputs, item.get("input_modalities", []))
        self.set_checklist(self.m_outputs, item.get("output_modalities", []))
        self.set_checklist(self.m_abilities, item.get("abilities", []))
        self.set_entry(self.m_slug, item.get("provider_slug", ""))
        self.set_entry(self.m_input_cost, str(item.get("input_cost_per_token", "")))
        self.set_entry(self.m_output_cost, str(item.get("output_cost_per_token", "")))
        self.set_reasoning_field(self.m_reasoning, self.m_reasoning_enabled, item.get("reasoning_behavior", {}))

    def apply_model(self):
        idx = self.selected_catalog_index(self.model_list)
        if idx is None:
            self.status.set("Select a model before applying changes.")
            return
        try:
            item = self.catalog["model_overrides"][idx].copy()
            item.pop("display_name", None)
            item.update({
                "id": self.get_entry(self.m_id),
                "canonical_model_id": self.get_entry(self.m_canonical) or None,
                "provider_ids": csv_to_list(self.get_entry(self.m_providers)),
                "provider_slugs": csv_to_list(self.get_entry(self.m_provider_slugs)),
                "match_patterns": lines_to_list(self.get_entry(self.m_patterns)),
                "exclude_patterns": lines_to_list(self.get_entry(self.m_excludes)),
                "base_url_patterns": lines_to_list(self.get_entry(self.m_base_patterns)),
                "type": self.get_entry(self.m_type) or "CHAT",
                "image_generation_method": self.get_entry(self.m_image_method) or None,
                "input_modalities": self.get_checklist(self.m_inputs) or ["TEXT"],
                "output_modalities": self.get_checklist(self.m_outputs) or ["TEXT"],
                "provider_slug": self.get_entry(self.m_slug) or None,
                "input_cost_per_token": float(self.get_entry(self.m_input_cost)) if self.get_entry(self.m_input_cost) else None,
                "output_cost_per_token": float(self.get_entry(self.m_output_cost)) if self.get_entry(self.m_output_cost) else None,
            })
            self.set_optional(item, "api_aliases", csv_to_list(self.get_entry(self.m_aliases)), [])
            self.set_optional(item, "abilities", self.get_checklist(self.m_abilities), [])
            reasoning = parse_json_field(self.get_entry(self.m_reasoning), {}) if self.m_reasoning_enabled.get() else {}
            if reasoning:
                item["reasoning_behavior"] = reasoning
            elif "reasoning_behavior" in item:
                del item["reasoning_behavior"]
            self.catalog["model_overrides"][idx] = self.clean_none(item)
            self.refresh_all()
            self.restore_selection(self.model_list, idx)
            self.status.set(f"Applied override changes: {item['id'] or item.get('match_patterns', ['pattern'])[0]}")
        except Exception as exc:
            messagebox.showerror("Model error", str(exc))

    def load_group_form(self):
        idx = self.selected_catalog_index(self.group_list)
        if idx is None:
            return
        item = self.catalog["model_families"][idx]
        self.set_entry(self.g_id, item.get("id", ""))
        self.set_entry(self.g_aliases, list_to_csv(item.get("aliases", [])))
        self.set_entry(self.g_patterns, list_to_lines(item.get("match_patterns", [])))
        self.set_entry(self.g_icon, item.get("icon", ""))
        self.set_entry(self.g_type, item.get("type", "CHAT"))
        self.set_entry(self.g_image_method, item.get("image_generation_method", ""))
        self.set_checklist(self.g_inputs, item.get("input_modalities", []))
        self.set_checklist(self.g_outputs, item.get("output_modalities", []))
        self.set_checklist(self.g_abilities, item.get("abilities", []))
        self.set_entry(self.g_slug, item.get("provider_slug", ""))
        self.load_versions(item.get("versions", []))

    def load_versions(self, versions):
        self.current_versions = [version.copy() for version in versions if isinstance(version, dict)]
        self.current_version_index = None
        self.refresh_version_list()
        if self.current_versions:
            self.version_list.selection_set(0)
            self.version_list.activate(0)
            self.load_version_form(0)
        else:
            self.clear_version_form()

    def refresh_version_list(self):
        selected = self.current_version_index
        self.version_list.delete(0, tk.END)
        for version in self.current_versions:
            self.version_list.insert(tk.END, self.version_label(version))
        if selected is not None and selected < len(self.current_versions):
            self.version_list.selection_set(selected)

    def version_label(self, version):
        version_id = version.get("id") or "untitled-version"
        pattern_count = len(version.get("match_patterns", []))
        if pattern_count:
            return f"{version_id} ({pattern_count} rules)"
        return version_id

    def on_version_select(self, _event=None):
        if self.loading_version:
            return
        selection = self.version_list.curselection()
        if not selection:
            return
        next_index = selection[0]
        if next_index == self.current_version_index:
            return
        self.save_current_version_form()
        self.load_version_form(next_index)

    def load_version_form(self, index):
        if index < 0 or index >= len(self.current_versions):
            self.clear_version_form()
            return
        self.loading_version = True
        self.current_version_index = index
        version = self.current_versions[index]
        self.set_entry(self.v_id, version.get("id", ""))
        self.set_entry(self.v_patterns, list_to_lines(version.get("match_patterns", [])))
        self.set_entry(self.v_excludes, list_to_lines(version.get("exclude_patterns", [])))
        self.set_entry(self.v_type, version.get("type", ""))
        self.set_entry(self.v_image_method, version.get("image_generation_method", ""))
        self.set_entry(self.v_canonical, version.get("canonical_model_id", ""))
        self.set_entry(self.v_slug, version.get("provider_slug", ""))
        self.v_inputs_enabled.set("input_modalities" in version)
        self.set_checklist(self.v_inputs, version.get("input_modalities", []))
        self.v_outputs_enabled.set("output_modalities" in version)
        self.set_checklist(self.v_outputs, version.get("output_modalities", []))
        self.v_abilities_enabled.set("abilities" in version)
        self.set_checklist(self.v_abilities, version.get("abilities", []))
        self.loading_version = False

    def clear_version_form(self):
        self.loading_version = True
        self.current_version_index = None
        for widget in (
            self.v_id,
            self.v_patterns,
            self.v_excludes,
            self.v_type,
            self.v_image_method,
            self.v_canonical,
            self.v_slug,
        ):
            self.set_entry(widget, "")
        self.v_inputs_enabled.set(False)
        self.v_outputs_enabled.set(False)
        self.v_abilities_enabled.set(False)
        self.set_checklist(self.v_inputs, [])
        self.set_checklist(self.v_outputs, [])
        self.set_checklist(self.v_abilities, [])
        self.loading_version = False

    def save_current_version_form(self):
        index = self.current_version_index
        if index is None or index < 0 or index >= len(self.current_versions):
            return
        version = self.current_versions[index].copy()
        version.pop("display_name", None)
        version.update({
            "id": self.get_entry(self.v_id),
            "match_patterns": lines_to_list(self.get_entry(self.v_patterns)),
            "exclude_patterns": lines_to_list(self.get_entry(self.v_excludes)),
            "type": self.get_entry(self.v_type) or None,
            "image_generation_method": self.get_entry(self.v_image_method) or None,
            "canonical_model_id": self.get_entry(self.v_canonical) or None,
            "provider_slug": self.get_entry(self.v_slug) or None,
        })
        if self.v_inputs_enabled.get():
            version["input_modalities"] = self.get_checklist(self.v_inputs) or ["TEXT"]
        else:
            version.pop("input_modalities", None)
        if not version.get("exclude_patterns"):
            version.pop("exclude_patterns", None)
        if self.v_outputs_enabled.get():
            version["output_modalities"] = self.get_checklist(self.v_outputs) or ["TEXT"]
        else:
            version.pop("output_modalities", None)
        if self.v_abilities_enabled.get():
            version["abilities"] = self.get_checklist(self.v_abilities)
        else:
            version.pop("abilities", None)
        self.current_versions[index] = self.clean_none(version)
        self.refresh_version_list()

    def add_version(self):
        self.save_current_version_form()
        self.current_versions.append({
            "id": "new-version",
            "match_patterns": [],
        })
        new_index = len(self.current_versions) - 1
        self.current_version_index = new_index
        self.refresh_version_list()
        self.version_list.selection_clear(0, tk.END)
        self.version_list.selection_set(new_index)
        self.version_list.activate(new_index)
        self.load_version_form(new_index)

    def delete_version(self):
        index = self.current_version_index
        if index is None:
            return
        del self.current_versions[index]
        next_index = min(index, len(self.current_versions) - 1)
        self.current_version_index = next_index if next_index >= 0 else None
        self.refresh_version_list()
        if self.current_version_index is None:
            self.clear_version_form()
        else:
            self.version_list.selection_set(self.current_version_index)
            self.load_version_form(self.current_version_index)

    def move_version(self, direction):
        index = self.current_version_index
        if index is None:
            return
        next_index = index + direction
        if next_index < 0 or next_index >= len(self.current_versions):
            return
        self.save_current_version_form()
        self.current_versions[index], self.current_versions[next_index] = (
            self.current_versions[next_index],
            self.current_versions[index],
        )
        self.current_version_index = next_index
        self.refresh_version_list()
        self.version_list.selection_clear(0, tk.END)
        self.version_list.selection_set(next_index)
        self.version_list.activate(next_index)
        self.load_version_form(next_index)

    def explain_family_match(self):
        model_id = self.family_test_id.get().strip()
        if not model_id:
            self.family_test_result.set("Enter a model id first.")
            return
        self.save_current_version_form()
        family = {
            "id": self.get_entry(self.g_id) or "(current family)",
            "match_patterns": lines_to_list(self.get_entry(self.g_patterns)),
            "versions": self.current_versions,
        }
        if not self.family_matches(family, model_id):
            if self.matches_global_rule(model_id) or any(self.override_matches(override, model_id) for override in self.catalog["model_overrides"]):
                self.family_test_result.set(self.resolution_summary(model_id, "none", []))
                return
            matching_families = [
                item.get("id", "")
                for item in self.catalog["model_families"]
                if self.family_matches(item, model_id)
            ]
            if matching_families:
                self.family_test_result.set(
                    f"No match for this family. Other matching families: {', '.join(matching_families)}."
                )
            else:
                self.family_test_result.set("No family rule matches this id yet.")
            return

        versions = [
            version.get("id", "untitled-version")
            for version in family.get("versions", [])
            if self.version_matches(version, model_id)
        ]
        if versions:
            self.family_test_result.set(
                self.resolution_summary(model_id, family["id"], versions)
            )
        else:
            self.family_test_result.set(
                self.resolution_summary(model_id, family["id"], [])
            )

    def resolution_summary(self, model_id, family_id, versions):
        global_rules = [
            rule.get("id", "global-rule")
            for rule in self.catalog.get("global_rules", [])
            if any(self.matches_catalog_pattern(model_id, pattern) for pattern in rule.get("match_patterns", []))
        ]
        overrides = [
            override.get("id", "") or ", ".join(override.get("match_patterns", [])) or "override"
            for override in self.catalog.get("model_overrides", [])
            if self.override_matches(override, model_id)
        ]
        parts = []
        if global_rules:
            parts.append(f"global: {', '.join(global_rules)}")
        parts.append(f"family: {family_id}")
        if versions:
            parts.append(f"versions: {', '.join(versions)}")
        if overrides:
            parts.append(f"overrides: {', '.join(overrides)}")
        return "Resolved by " + " | ".join(parts)

    def apply_group(self):
        idx = self.selected_catalog_index(self.group_list)
        if idx is None:
            self.status.set("Select a family before applying changes.")
            return
        self.save_current_version_form()
        item = self.catalog["model_families"][idx].copy()
        item.pop("display_name", None)
        item.update({
            "id": self.get_entry(self.g_id),
            "aliases": csv_to_list(self.get_entry(self.g_aliases)),
            "match_patterns": lines_to_list(self.get_entry(self.g_patterns)),
            "icon": self.get_entry(self.g_icon) or None,
            "type": self.get_entry(self.g_type) or "CHAT",
            "image_generation_method": self.get_entry(self.g_image_method) or None,
            "input_modalities": self.get_checklist(self.g_inputs) or ["TEXT"],
            "output_modalities": self.get_checklist(self.g_outputs) or ["TEXT"],
            "abilities": self.get_checklist(self.g_abilities),
            "provider_slug": self.get_entry(self.g_slug) or None,
            "versions": self.current_versions,
        })
        self.catalog["model_families"][idx] = self.clean_none(item)
        self.refresh_all()
        self.restore_selection(self.group_list, idx)
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
        self.provider_search.set("")
        self.refresh_all()
        self.provider_list.selection_clear(0, tk.END)
        self.provider_list.selection_set(tk.END)
        self.load_provider_form()

    def add_model(self):
        self.catalog["model_overrides"].append({
            "id": "new-model",
        })
        self.model_search.set("")
        self.refresh_all()
        self.model_list.selection_clear(0, tk.END)
        self.model_list.selection_set(tk.END)
        self.load_model_form()

    def add_group(self):
        self.catalog["model_families"].append({
            "id": "new-family",
            "aliases": [],
            "match_patterns": [],
            "type": "CHAT",
            "input_modalities": ["TEXT"],
            "output_modalities": ["TEXT"],
            "abilities": [],
            "versions": [],
        })
        self.group_search.set("")
        self.refresh_all()
        self.group_list.selection_clear(0, tk.END)
        self.group_list.selection_set(tk.END)
        self.load_group_form()

    def delete_provider(self):
        self.delete_selected(self.provider_list, self.catalog["providers"])

    def delete_model(self):
        self.delete_selected(self.model_list, self.catalog["model_overrides"])

    def delete_group(self):
        self.delete_selected(self.group_list, self.catalog["model_families"])

    def delete_selected(self, listbox, items):
        idx = self.selected_catalog_index(listbox)
        if idx is None:
            return
        if messagebox.askyesno("Delete", "Delete selected item?"):
            del items[idx]
            self.refresh_all()

    def move_selected(self, listbox, items, direction):
        idx = self.selected_catalog_index(listbox)
        if idx is None:
            return
        new_idx = idx + direction
        if new_idx < 0 or new_idx >= len(items):
            return
        items[idx], items[new_idx] = items[new_idx], items[idx]
        self.refresh_all()
        self.restore_selection(listbox, new_idx)
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

    def validate_from_ui(self):
        try:
            self.apply_current_tab_changes()
            self.validate_catalog(show_success=True)
        except Exception as exc:
            messagebox.showerror("Catalog invalid", str(exc))

    def show_migration_report(self):
        redundant = []
        needs_override = []
        unmatched = []
        for model in self.catalog.get("models", []):
            model_id = model.get("id", "")
            if not model_id:
                continue
            family_match = self.matches_model_family(model_id, self.catalog["model_families"])
            override_match = any(self.override_matches(override, model_id) for override in self.catalog["model_overrides"])
            if override_match:
                needs_override.append(model_id)
            elif family_match or self.matches_global_rule(model_id):
                redundant.append(model_id)
            else:
                unmatched.append(model_id)
        report = [
            f"Schema version: {self.catalog.get('schema_version')}",
            f"Providers: {len(self.catalog.get('providers', []))}",
            f"Families: {len(self.catalog.get('model_families', []))}",
            f"Global rules: {len(self.catalog.get('global_rules', []))}",
            f"Overrides: {len(self.catalog.get('model_overrides', []))}",
            f"Legacy models: {len(self.catalog.get('models', []))}",
            "",
            f"Legacy models covered by family/global rules: {len(redundant)}",
            f"Legacy models already covered by overrides: {len(needs_override)}",
            f"Legacy models still unmatched: {len(unmatched)}",
        ]
        if unmatched:
            report += ["", "Unmatched examples:", *unmatched[:20]]
        messagebox.showinfo("Catalog migration report", "\n".join(report))

    def apply_current_tab_changes(self):
        current_tab = self.tabs.tab(self.tabs.select(), "text")
        if current_tab == "Providers" and self.selected_catalog_index(self.provider_list) is not None:
            self.apply_provider()
        elif current_tab == "Overrides" and self.selected_catalog_index(self.model_list) is not None:
            self.apply_model()
        elif current_tab == "Families" and self.selected_catalog_index(self.group_list) is not None:
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
            override.get("id", "") or ", ".join(override.get("match_patterns", []))
            for override in self.catalog["model_overrides"]
            for provider_id in override.get("provider_ids", [])
            if provider_id not in provider_ids
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
        for rule in self.catalog.get("global_rules", []):
            rule_id = rule.get("id", "")
            for pattern in list(rule.get("match_patterns", [])) + list(rule.get("exclude_patterns", [])):
                try:
                    re.compile(pattern)
                except re.error:
                    bad_patterns.append(f"global/{rule_id}: {pattern}")
        for override in self.catalog["model_overrides"]:
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
            for override in self.catalog["model_overrides"]
            if override.get("id")
        }
        model_families = self.catalog["model_families"]
        missing_setup_refs = [
            f"{provider.get('name', '')}: {model_id}"
            for provider in self.catalog["providers"]
            for model_id in (
                list(provider.get("setup_models", []))
                + list((provider.get("setup_defaults") or {}).values())
            )
            if model_id
            and not self.resolves_model_id(model_id)
        ]
        duplicate_model_ids = sorted({
            model_id for model_id in override_ids
            if sum(1 for override in self.catalog["model_overrides"] if override.get("id", "").lower() == model_id) > 1
        })
        forbidden_display_names = self.find_model_display_names()
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
        if forbidden_display_names:
            problems.append("Model catalog display names are not allowed: " + ", ".join(forbidden_display_names))
        if problems:
            raise ValueError("\n".join(problems))
        if show_success:
            messagebox.showinfo("Catalog valid", "Catalog looks good.")
        return True

    def find_model_display_names(self):
        refs = []
        for index, model in enumerate(self.catalog.get("models", [])):
            if "display_name" in model:
                refs.append(f"models[{index}]")
        for index, override in enumerate(self.catalog.get("model_overrides", [])):
            if "display_name" in override:
                refs.append(f"model_overrides[{index}]")
        for index, rule in enumerate(self.catalog.get("global_rules", [])):
            if "display_name" in rule:
                refs.append(f"global_rules[{index}]")
        for family_index, family in enumerate(self.catalog.get("model_families", [])):
            if "display_name" in family:
                refs.append(f"model_families[{family_index}]")
            for version_index, version in enumerate(family.get("versions", [])):
                if isinstance(version, dict) and "display_name" in version:
                    refs.append(f"model_families[{family_index}].versions[{version_index}]")
        return refs

    @staticmethod
    def clean_none(value):
        return {key: item for key, item in value.items() if item is not None}

    @staticmethod
    def set_optional(item, key, value, default):
        if value != default or key in item:
            item[key] = value
        else:
            item.pop(key, None)

    def resolves_model_id(self, model_id):
        if self.matches_global_rule(model_id):
            return True
        if self.matches_model_family(model_id, self.catalog["model_families"]):
            return True
        return any(self.override_matches(override, model_id) for override in self.catalog["model_overrides"])

    def matches_global_rule(self, model_id):
        return any(
            any(self.matches_catalog_pattern(model_id, pattern) for pattern in rule.get("match_patterns", []))
            and not any(self.matches_catalog_pattern(model_id, pattern) for pattern in rule.get("exclude_patterns", []))
            for rule in self.catalog.get("global_rules", [])
        )

    def override_matches(self, override, model_id):
        if any(self.matches_catalog_pattern(model_id, pattern) for pattern in override.get("exclude_patterns", [])):
            return False
        exact_refs = [
            override.get("id", ""),
            *override.get("api_aliases", []),
        ]
        if model_id.lower() in {ref.lower() for ref in exact_refs if ref}:
            return True
        return any(self.matches_catalog_pattern(model_id, pattern) for pattern in override.get("match_patterns", []))

    @staticmethod
    def matches_model_family(model_id, model_families):
        for family in model_families:
            if CatalogEditor.family_matches(family, model_id):
                return True
        return False

    @staticmethod
    def family_matches(family, model_id):
        patterns = family.get("match_patterns", [])
        return any(CatalogEditor.matches_catalog_pattern(model_id, pattern) for pattern in patterns)

    @staticmethod
    def version_matches(version, model_id):
        excludes = version.get("exclude_patterns", [])
        if any(CatalogEditor.matches_catalog_pattern(model_id, pattern) for pattern in excludes):
            return False
        patterns = version.get("match_patterns", [])
        return any(CatalogEditor.matches_catalog_pattern(model_id, pattern) for pattern in patterns)

    @staticmethod
    def matches_catalog_pattern(value, pattern):
        if not pattern:
            return False
        try:
            return re.search(pattern, value, re.IGNORECASE) is not None
        except re.error:
            return False


if __name__ == "__main__":
    app = CatalogEditor()
    app.mainloop()
