import json
import shutil
import sys
import re
import html
import unicodedata
from pathlib import Path
from html.parser import HTMLParser
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
import tkinter.font as tkfont
from PIL import Image, ImageTk

FILENAME_PATTERN = re.compile(r"questao_(\d+)\.json$", re.IGNORECASE)
OPTION_LABELS = ("A", "B", "C", "D", "E")
DEFAULT_VISIBLE_OPTIONS = ("A", "B", "C", "D")
IMAGE_TYPES = [("Imagens", "*.png *.jpg *.jpeg"), ("PNG", "*.png"), ("JPEG", "*.jpg *.jpeg")]
PREVIEW_IMAGE_MAX = (320, 220)
OPTION_PREVIEW_IMAGE_MAX = (280, 180)
QUESTION_BOLD_TAG = "question_bold"
QUESTION_UNDERLINE_TAG = "question_underline"
PDF_TEXT_REPLACEMENTS = {
    "\u00a0": " ",
    "\u2007": " ",
    "\u202f": " ",
    "\u2009": " ",
    "\u200a": " ",
    "\u200b": "",
    "\u200c": "",
    "\u200d": "",
    "\ufeff": "",
    "\u00ad": "",
    "\u2011": "-",
    "\u2013": "-",
    "\u2014": "-",
    "\u2212": "-",
    "\u2044": "/",
    "\u2215": "/",
    "\ufb01": "fi",
    "\ufb02": "fl",
    "\ufb00": "ff",
    "\ufb03": "ffi",
    "\ufb04": "ffl",
}
SUPERSCRIPT_REPLACEMENTS = {
    "\uFFFD0": "⁰",
    "\uFFFD1": "¹",
    "\uFFFD2": "²",
    "\uFFFD3": "³",
    "\uFFFD4": "⁴",
    "\uFFFD5": "⁵",
    "\uFFFD6": "⁶",
    "\uFFFD7": "⁷",
    "\uFFFD8": "⁸",
    "\uFFFD9": "⁹",
    "Â¹": "¹",
    "Â²": "²",
    "Â³": "³",
    "â°": "⁰",
    "â±": "ⁱ",
    "â´": "⁴",
    "âµ": "⁵",
    "â¶": "⁶",
    "â·": "⁷",
    "â¸": "⁸",
    "â¹": "⁹",
}
SUPERSCRIPT_DIGITS = {
    "0": "⁰",
    "1": "¹",
    "2": "²",
    "3": "³",
    "4": "⁴",
    "5": "⁵",
    "6": "⁶",
    "7": "⁷",
    "8": "⁸",
    "9": "⁹",
}


def runtime_directory() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def question_sort_key(path: Path) -> tuple[int, str]:
    match = FILENAME_PATTERN.match(path.name)
    order = int(match.group(1)) if match else sys.maxsize
    return order, path.name.lower()


def list_question_files(output_dir: Path) -> list[Path]:
    return sorted(output_dir.glob("questao_*.json"), key=question_sort_key)


def next_question_number(output_dir: Path) -> int:
    numbers = []
    for path in output_dir.glob("questao_*.json"):
        match = FILENAME_PATTERN.match(path.name)
        if match:
            numbers.append(int(match.group(1)))
    return max(numbers, default=0) + 1


def normalize_html_text(text: str) -> str:
    text = normalize_pdf_text(text).strip()
    return text.replace("\n", "<br>")


def html_to_editor_text(text: str) -> str:
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</?(b|strong|u|ins|div|p)>", "", text, flags=re.IGNORECASE)
    return normalize_pdf_text(html.unescape(text)).strip()


def normalize_pdf_text(text: str) -> str:
    text = html.unescape(text or "")
    text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\t", "    ")
    text = unicodedata.normalize("NFC", text)
    for source, target in PDF_TEXT_REPLACEMENTS.items():
        text = text.replace(source, target)
    for source, target in SUPERSCRIPT_REPLACEMENTS.items():
        text = text.replace(source, target)
    text = re.sub(r"[\uFFFDÂ]\s*\n\s*([0-9])", lambda match: SUPERSCRIPT_DIGITS.get(match.group(1), match.group(0)), text)
    text = re.sub(r"[\uFFFDÂ]\s*([0-9])", lambda match: SUPERSCRIPT_DIGITS.get(match.group(1), match.group(0)), text)
    return "".join(character for character in text if character == "\n" or character >= " ")


def detect_year(output_dir: Path) -> int:
    parent_name = output_dir.parent.name
    if parent_name.isdigit():
        return int(parent_name)
    return 0


def detect_subject(output_dir: Path) -> str:
    return output_dir.name


class QuestionHtmlParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.lines: list[dict] = [{"align": "left", "segments": []}]
        self.bold_depth = 0
        self.underline_depth = 0
        self.align_stack = ["left"]

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_map = {name.lower(): value for name, value in attrs}
        tag = tag.lower()
        if tag in {"b", "strong"}:
            self.bold_depth += 1
            return
        if tag in {"u", "ins"}:
            self.underline_depth += 1
            return
        if tag == "br":
            self._new_line()
            return
        if tag in {"div", "p"}:
            self.align_stack.append(self._extract_alignment(attrs_map))
            return

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag in {"b", "strong"}:
            self.bold_depth = max(0, self.bold_depth - 1)
            return
        if tag in {"u", "ins"}:
            self.underline_depth = max(0, self.underline_depth - 1)
            return
        if tag in {"div", "p"} and len(self.align_stack) > 1:
            self.align_stack.pop()
            self._new_line()

    def handle_data(self, data: str) -> None:
        if not data:
            return
        parts = data.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        for index, part in enumerate(parts):
            if part:
                self.lines[-1]["segments"].append(
                    {
                        "text": part,
                        "bold": self.bold_depth > 0,
                        "underline": self.underline_depth > 0,
                    }
                )
            if index < len(parts) - 1:
                self._new_line()

    def _extract_alignment(self, attrs_map: dict[str, str | None]) -> str:
        align_attr = (attrs_map.get("align") or "").strip().lower()
        return "left"

    def _new_line(self) -> None:
        if self.lines and not self.lines[-1]["segments"]:
            self.lines[-1]["align"] = self.align_stack[-1]
            return
        self.lines.append({"align": self.align_stack[-1], "segments": []})


def parse_question_html(text: str) -> list[dict]:
    parser = QuestionHtmlParser()
    parser.feed(text or "")
    parser.close()
    while len(parser.lines) > 1 and not parser.lines[-1]["segments"]:
        parser.lines.pop()
    return parser.lines or [{"align": "left", "segments": []}]


class QuestionGeneratorApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.output_dir = runtime_directory()
        self.current_edit_path: Path | None = None
        self.option_widgets: dict[str, tuple[ttk.Label, ttk.Frame]] = {}
        self.option_image_labels: dict[str, tk.StringVar] = {}
        self.option_image_sources: dict[str, Path | None] = {label: None for label in OPTION_LABELS}
        self.option_existing_images: dict[str, str | None] = {label: None for label in OPTION_LABELS}
        self.show_option_e = False
        self.question_image_source: Path | None = None
        self.question_existing_image: str | None = None
        self.explanation_image_source: Path | None = None
        self.explanation_existing_image: str | None = None
        self.preview_images: dict[str, ImageTk.PhotoImage] = {}

        self.root.title("Gerador de Questoes JSON")
        self.root.geometry("1440x980")
        self.root.minsize(1200, 780)

        self.include_explanation = tk.BooleanVar(value=False)
        self.correct_option = tk.StringVar(value="A")
        self.year_var = tk.StringVar(value=str(detect_year(self.output_dir) or ""))
        self.subject_var = tk.StringVar(value=detect_subject(self.output_dir))
        self.file_label = tk.StringVar()
        self.location_label = tk.StringVar(value=f"Saida: {self.output_dir}")
        self.mode_label = tk.StringVar(value="Modo: criando nova questao")
        self.option_toggle_label = tk.StringVar(value="Mostrar alternativa E")
        self.question_image_label = tk.StringVar(value="Sem imagem no enunciado")
        self.explanation_image_label = tk.StringVar(value="Sem imagem na explicacao")

        self.scroll_canvas: tk.Canvas | None = None
        self.scrollable_frame: ttk.Frame | None = None
        self.preview_canvas: tk.Canvas | None = None
        self.preview_scrollable_frame: ttk.Frame | None = None
        self.question_text: tk.Text | None = None
        self.question_text_scrollbar: ttk.Scrollbar | None = None
        self.question_text_font: tkfont.Font | None = None
        self.question_text_bold_font: tkfont.Font | None = None
        self.raw_question_html: str = ""
        self.question_html_window: tk.Toplevel | None = None
        self.question_html_text: tk.Text | None = None
        self.expanded_question_window: tk.Toplevel | None = None
        self.expanded_question_text: tk.Text | None = None
        self.last_active_option_widget: tk.Text | None = None
        self.option_texts: dict[str, tk.Text] = {}
        self.explanation_text: tk.Text | None = None
        self.save_edit_button: ttk.Button | None = None
        self.open_existing_button: ttk.Button | None = None
        self.file_selector: ttk.Combobox | None = None
        self.available_files: list[Path] = []

        self.preview_subject_var = tk.StringVar(value=detect_subject(self.output_dir))
        self.preview_question_var = tk.StringVar(value="")
        self.preview_explanation_var = tk.StringVar(value="")
        self.preview_question_image_widget: ttk.Label | None = None
        self.preview_question_text: tk.Text | None = None
        self.preview_question_text_font: tkfont.Font | None = None
        self.preview_question_text_bold_font: tkfont.Font | None = None
        self.preview_option_cards: dict[str, ttk.Frame] = {}
        self.preview_option_text_vars: dict[str, tk.StringVar] = {}
        self.preview_option_text_widgets: dict[str, tk.Text] = {}
        self.preview_option_image_widgets: dict[str, ttk.Label] = {}
        self.preview_explanation_image_widget: ttk.Label | None = None

        self.build_ui()
        self.bind_live_preview()
        self.refresh_available_files()
        self.refresh_next_file_label()
        self.update_option_e_visibility(False)
        self.update_preview()

    def build_ui(self) -> None:
        root_container = ttk.Frame(self.root, padding=12)
        root_container.pack(fill="both", expand=True)
        root_container.columnconfigure(0, weight=1)
        root_container.rowconfigure(1, weight=1)

        header = ttk.Frame(root_container)
        header.grid(row=0, column=0, sticky="ew", pady=(0, 12))
        header.columnconfigure(0, weight=1)
        ttk.Label(header, text="Gerador de questao por arquivo", font=("Segoe UI", 15, "bold")).grid(row=0, column=0, sticky="w")
        ttk.Label(header, textvariable=self.location_label).grid(row=1, column=0, sticky="w", pady=(4, 0))
        ttk.Label(header, textvariable=self.file_label, foreground="#0b5ed7").grid(row=2, column=0, sticky="w", pady=(4, 0))
        ttk.Label(header, textvariable=self.mode_label, foreground="#198754").grid(row=3, column=0, sticky="w", pady=(4, 0))

        split = ttk.Panedwindow(root_container, orient="horizontal")
        split.grid(row=1, column=0, sticky="nsew")
        left = ttk.Frame(split)
        right = ttk.Frame(split)
        split.add(left, weight=3)
        split.add(right, weight=2)

        self.build_editor_panel(left)
        self.build_preview_panel(right)

        buttons = ttk.Frame(root_container)
        buttons.grid(row=2, column=0, sticky="ew", pady=(12, 0))
        buttons.columnconfigure(1, weight=1)
        ttk.Button(buttons, text="Limpar", command=self.clear_form).grid(row=0, column=0, sticky="w")
        self.save_edit_button = ttk.Button(buttons, text="Salvar edicao", command=self.save_edit, state="disabled")
        self.save_edit_button.grid(row=0, column=1, sticky="e", padx=(0, 8))
        ttk.Button(buttons, text="Gerar JSON", command=self.generate_json).grid(row=0, column=2, sticky="e")
    def build_editor_panel(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(0, weight=1)
        scroll_container = ttk.Frame(parent)
        scroll_container.grid(row=0, column=0, sticky="nsew")
        scroll_container.columnconfigure(0, weight=1)
        scroll_container.rowconfigure(0, weight=1)

        self.scroll_canvas = tk.Canvas(scroll_container, highlightthickness=0)
        scrollbar = ttk.Scrollbar(scroll_container, orient="vertical", command=self.scroll_canvas.yview)
        self.scroll_canvas.configure(yscrollcommand=scrollbar.set)
        self.scroll_canvas.grid(row=0, column=0, sticky="nsew")
        scrollbar.grid(row=0, column=1, sticky="ns")

        self.scrollable_frame = ttk.Frame(self.scroll_canvas)
        self.scrollable_frame.columnconfigure(0, weight=1)
        window_id = self.scroll_canvas.create_window((0, 0), window=self.scrollable_frame, anchor="nw")

        def on_frame_configure(_event=None):
            self.scroll_canvas.configure(scrollregion=self.scroll_canvas.bbox("all"))

        def on_canvas_configure(event):
            self.scroll_canvas.itemconfigure(window_id, width=event.width)

        self.scrollable_frame.bind("<Configure>", on_frame_configure)
        self.scroll_canvas.bind("<Configure>", on_canvas_configure)
        self.bind_mousewheel()

        form = self.scrollable_frame
        metadata = ttk.Frame(form)
        metadata.grid(row=0, column=0, sticky="ew", pady=(0, 12))
        metadata.columnconfigure(1, weight=1)
        metadata.columnconfigure(3, weight=1)
        ttk.Label(metadata, text="Materia").grid(row=0, column=0, sticky="w")
        ttk.Entry(metadata, textvariable=self.subject_var).grid(row=0, column=1, sticky="ew", padx=(8, 16))
        ttk.Label(metadata, text="Ano").grid(row=0, column=2, sticky="w")
        ttk.Entry(metadata, textvariable=self.year_var, width=12).grid(row=0, column=3, sticky="ew", padx=(8, 0))

        existing = ttk.Frame(form)
        existing.grid(row=1, column=0, sticky="ew", pady=(0, 12))
        existing.columnconfigure(1, weight=1)
        ttk.Label(existing, text="Questao existente").grid(row=0, column=0, sticky="w")
        self.file_selector = ttk.Combobox(existing, state="readonly")
        self.file_selector.grid(row=0, column=1, sticky="ew", padx=(8, 8))
        self.open_existing_button = ttk.Button(existing, text="Abrir questao existente", command=self.open_selected_question)
        self.open_existing_button.grid(row=0, column=2, sticky="e")

        question_header = ttk.Frame(form)
        question_header.grid(row=2, column=0, sticky="ew")
        question_header.columnconfigure(0, weight=1)
        ttk.Label(question_header, text="Texto da questao").grid(row=0, column=0, sticky="w")
        ttk.Button(question_header, text="Codigo HTML", command=self.open_question_html_editor).grid(row=0, column=1, sticky="e", padx=(8, 0))
        ttk.Button(question_header, text="Maximizar", command=self.open_expanded_question_editor).grid(row=0, column=2, sticky="e", padx=(8, 0))

        question_toolbar = ttk.Frame(form)
        question_toolbar.grid(row=3, column=0, sticky="ew", pady=(4, 6))
        ttk.Button(question_toolbar, text="Negrito", command=self.toggle_question_bold).pack(side="left")
        ttk.Button(question_toolbar, text="Sublinhado", command=self.toggle_question_underline).pack(side="left", padx=(8, 0))

        question_text_frame = ttk.Frame(form)
        question_text_frame.grid(row=4, column=0, sticky="ew", pady=(0, 8))
        question_text_frame.columnconfigure(0, weight=1)
        self.question_text_scrollbar = ttk.Scrollbar(question_text_frame, orient="vertical")
        self.question_text = tk.Text(
            question_text_frame,
            wrap="word",
            height=8,
            font=("Segoe UI", 10),
            yscrollcommand=self.question_text_scrollbar.set,
            undo=True,
        )
        self.question_text.grid(row=0, column=0, sticky="ew")
        self.question_text_scrollbar.configure(command=self.question_text.yview)
        self.question_text_scrollbar.grid(row=0, column=1, sticky="ns")
        self.configure_question_text_widget(self.question_text)

        question_image_frame = ttk.Frame(form)
        question_image_frame.grid(row=5, column=0, sticky="ew", pady=(0, 12))
        question_image_frame.columnconfigure(1, weight=1)
        ttk.Label(question_image_frame, text="Imagem do enunciado").grid(row=0, column=0, sticky="w")
        ttk.Label(question_image_frame, textvariable=self.question_image_label).grid(row=0, column=1, sticky="w", padx=(8, 0))
        ttk.Button(question_image_frame, text="Selecionar imagem", command=self.select_question_image).grid(row=0, column=2, padx=(8, 0))
        ttk.Button(question_image_frame, text="Remover imagem", command=self.clear_question_image).grid(row=0, column=3, padx=(8, 0))

        option_header = ttk.Frame(form)
        option_header.grid(row=6, column=0, sticky="ew")
        option_header.columnconfigure(0, weight=1)
        ttk.Label(option_header, text="Alternativas em caixas separadas").grid(row=0, column=0, sticky="w")
        header_controls = ttk.Frame(option_header)
        header_controls.grid(row=0, column=1, sticky="e")
        ttk.Button(header_controls, text="Negrito na alternativa", command=self.toggle_active_option_bold).pack(side="left", padx=(0, 8))
        ttk.Button(header_controls, text="Sublinhado na alternativa", command=self.toggle_active_option_underline).pack(side="left", padx=(0, 12))
        ttk.Button(header_controls, textvariable=self.option_toggle_label, command=self.toggle_option_e).pack(side="left", padx=(0, 12))
        ttk.Label(header_controls, text="Correta:").pack(side="left", padx=(0, 6))
        ttk.Combobox(header_controls, state="readonly", width=5, textvariable=self.correct_option, values=OPTION_LABELS).pack(side="left")

        options_frame = ttk.Frame(form)
        options_frame.grid(row=7, column=0, sticky="ew", pady=(4, 12))
        options_frame.columnconfigure(1, weight=1)
        for index, label in enumerate(OPTION_LABELS):
            row_frame = ttk.Frame(options_frame)
            row_frame.grid(row=index, column=0, columnspan=2, sticky="ew", pady=(0, 8))
            row_frame.columnconfigure(1, weight=1)
            option_label = ttk.Label(row_frame, text=f"{label})")
            option_label.grid(row=0, column=0, sticky="nw", padx=(0, 8))
            option_text = tk.Text(row_frame, wrap="word", height=2, font=("Segoe UI", 10))
            option_text.grid(row=0, column=1, sticky="ew")
            self.configure_question_text_widget(option_text)
            image_label_var = tk.StringVar(value=f"Sem imagem na alternativa {label}")
            image_controls = ttk.Frame(row_frame)
            image_controls.grid(row=1, column=1, sticky="ew", pady=(4, 0))
            image_controls.columnconfigure(0, weight=1)
            ttk.Label(image_controls, textvariable=image_label_var).grid(row=0, column=0, sticky="w")
            ttk.Button(image_controls, text="Selecionar imagem", command=lambda current=label: self.select_option_image(current)).grid(row=0, column=1, padx=(8, 0))
            ttk.Button(image_controls, text="Remover imagem", command=lambda current=label: self.clear_option_image(current)).grid(row=0, column=2, padx=(8, 0))
            self.option_texts[label] = option_text
            self.option_widgets[label] = (option_label, row_frame)
            self.option_image_labels[label] = image_label_var

        ttk.Checkbutton(form, text="Incluir explicacao", variable=self.include_explanation, command=self.on_explanation_toggle).grid(row=8, column=0, sticky="w")
        ttk.Label(form, text="Explicacao").grid(row=9, column=0, sticky="w", pady=(8, 0))
        self.explanation_text = tk.Text(form, wrap="word", height=6, font=("Segoe UI", 10), state="disabled")
        self.explanation_text.grid(row=10, column=0, sticky="ew", pady=(4, 12))
        self.configure_question_text_widget(self.explanation_text)
        explanation_image_frame = ttk.Frame(form)
        explanation_image_frame.grid(row=11, column=0, sticky="ew", pady=(0, 12))
        explanation_image_frame.columnconfigure(1, weight=1)
        ttk.Label(explanation_image_frame, text="Imagem da explicacao").grid(row=0, column=0, sticky="w")
        ttk.Label(explanation_image_frame, textvariable=self.explanation_image_label).grid(row=0, column=1, sticky="w", padx=(8, 0))
        ttk.Button(explanation_image_frame, text="Selecionar imagem", command=self.select_explanation_image).grid(row=0, column=2, padx=(8, 0))
        ttk.Button(explanation_image_frame, text="Remover imagem", command=self.clear_explanation_image).grid(row=0, column=3, padx=(8, 0))
        ttk.Label(form, text="Preview em tempo real do lado direito.", foreground="#555555").grid(row=12, column=0, sticky="w", pady=(0, 12))

    def build_preview_panel(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(0, weight=1)
        preview_shell = ttk.Frame(parent, padding=(12, 0, 0, 0))
        preview_shell.grid(row=0, column=0, sticky="nsew")
        preview_shell.columnconfigure(0, weight=1)
        preview_shell.rowconfigure(0, weight=1)

        self.preview_canvas = tk.Canvas(preview_shell, highlightthickness=0)
        preview_scrollbar = ttk.Scrollbar(preview_shell, orient="vertical", command=self.preview_canvas.yview)
        self.preview_canvas.configure(yscrollcommand=preview_scrollbar.set)
        self.preview_canvas.grid(row=0, column=0, sticky="nsew")
        preview_scrollbar.grid(row=0, column=1, sticky="ns")

        self.preview_scrollable_frame = ttk.Frame(self.preview_canvas)
        self.preview_scrollable_frame.columnconfigure(0, weight=1)
        preview_window_id = self.preview_canvas.create_window((0, 0), window=self.preview_scrollable_frame, anchor="nw")

        def on_preview_frame_configure(_event=None):
            self.preview_canvas.configure(scrollregion=self.preview_canvas.bbox("all"))

        def on_preview_canvas_configure(event):
            self.preview_canvas.itemconfigure(preview_window_id, width=event.width)

        self.preview_scrollable_frame.bind("<Configure>", on_preview_frame_configure)
        self.preview_canvas.bind("<Configure>", on_preview_canvas_configure)

        card = ttk.Frame(self.preview_scrollable_frame, padding=16, relief="solid", borderwidth=1)
        card.grid(row=0, column=0, sticky="nsew")
        card.columnconfigure(0, weight=1)
        ttk.Label(card, text="Previa do Simulado", font=("Segoe UI", 13, "bold")).grid(row=0, column=0, sticky="w", pady=(0, 12))

        header = ttk.Frame(card)
        header.grid(row=1, column=0, sticky="ew", pady=(0, 8))
        header.columnconfigure(0, weight=1)
        ttk.Label(header, text="Questao 1/1", font=("Segoe UI", 10, "bold")).grid(row=0, column=0, sticky="w")
        ttk.Label(header, text="00:00", foreground="#0b5ed7").grid(row=0, column=1, sticky="e")
        ttk.Progressbar(card, mode="determinate", maximum=100, value=100).grid(row=2, column=0, sticky="ew", pady=(0, 12))
        self.preview_subject_chip = ttk.Label(card, textvariable=self.preview_subject_var, padding=(10, 4))
        self.preview_subject_chip.grid(row=3, column=0, sticky="w", pady=(0, 8))
        self.preview_question_text = tk.Text(
            card,
            wrap="word",
            height=8,
            font=("Segoe UI", 11),
            relief="flat",
            borderwidth=0,
            highlightthickness=0,
            background=self.root.cget("bg"),
            state="disabled",
        )
        self.preview_question_text.grid(row=4, column=0, sticky="ew")
        self.configure_preview_question_text_widget()
        self.preview_question_image_widget = ttk.Label(card)
        self.preview_question_image_widget.grid(row=5, column=0, sticky="ew", pady=(12, 12))

        options_container = ttk.Frame(card)
        options_container.grid(row=6, column=0, sticky="ew")
        options_container.columnconfigure(0, weight=1)
        for index, label in enumerate(OPTION_LABELS):
            option_card = ttk.Frame(options_container, padding=12, relief="solid", borderwidth=1)
            option_card.grid(row=index, column=0, sticky="ew", pady=(0, 8))
            option_card.columnconfigure(0, weight=1)
            text_var = tk.StringVar(value=f"{label})")
            text_widget = tk.Text(
                option_card,
                wrap="word",
                height=2,
                font=("Segoe UI", 10),
                relief="flat",
                borderwidth=0,
                highlightthickness=0,
                background=self.root.cget("bg"),
                state="disabled",
            )
            text_widget.grid(row=0, column=0, sticky="ew")
            self.configure_preview_text_widget(text_widget)
            image_widget = ttk.Label(option_card)
            image_widget.grid(row=1, column=0, sticky="w", pady=(8, 0))
            self.preview_option_cards[label] = option_card
            self.preview_option_text_vars[label] = text_var
            self.preview_option_text_widgets[label] = text_widget
            self.preview_option_image_widgets[label] = image_widget

        self.preview_explanation_title = ttk.Label(card, text="Explicacao:", font=("Segoe UI", 10, "bold"))
        self.preview_explanation_title.grid(row=7, column=0, sticky="w", pady=(12, 0))
        self.preview_explanation_label = ttk.Label(card, textvariable=self.preview_explanation_var, wraplength=380, justify="left")
        self.preview_explanation_label.grid(row=8, column=0, sticky="w", pady=(4, 0))
        self.preview_explanation_image_widget = ttk.Label(card)
        self.preview_explanation_image_widget.grid(row=9, column=0, sticky="w", pady=(8, 0))
    def bind_live_preview(self) -> None:
        self.subject_var.trace_add("write", lambda *_: self.update_preview())
        self.correct_option.trace_add("write", lambda *_: self.update_preview())
        self.include_explanation.trace_add("write", lambda *_: self.update_preview())
        self.question_text.bind("<KeyRelease>", self.on_question_text_changed)
        self.explanation_text.bind("<KeyRelease>", lambda _e: self.update_preview())
        for text in self.option_texts.values():
            text.bind("<KeyRelease>", lambda _e: self.update_preview())

    def configure_question_text_widget(self, widget: tk.Text) -> None:
        base_font = tkfont.Font(font=widget.cget("font"))
        bold_font = tkfont.Font(font=widget.cget("font"))
        bold_font.configure(weight="bold")
        widget.configure(font=base_font, exportselection=False)
        widget.tag_configure(QUESTION_BOLD_TAG, font=bold_font)
        widget.tag_configure(QUESTION_UNDERLINE_TAG, underline=True)
        self.bind_normalized_paste(widget)
        widget.bind("<FocusIn>", lambda _event, target=widget: self.remember_active_editor(target), add="+")
        widget.bind("<ButtonRelease-1>", lambda _event, target=widget: self.remember_active_editor(target), add="+")
        if widget is self.question_text:
            self.question_text_font = base_font
            self.question_text_bold_font = bold_font
            self.bind_question_text_mousewheel(widget)

    def configure_preview_text_widget(self, widget: tk.Text) -> None:
        base_font = tkfont.Font(font=widget.cget("font"))
        bold_font = tkfont.Font(font=widget.cget("font"))
        bold_font.configure(weight="bold")
        widget.configure(font=base_font)
        widget.tag_configure(QUESTION_BOLD_TAG, font=bold_font)
        widget.tag_configure(QUESTION_UNDERLINE_TAG, underline=True)

    def configure_preview_question_text_widget(self) -> None:
        if self.preview_question_text is None:
            return
        base_font = tkfont.Font(font=self.preview_question_text.cget("font"))
        bold_font = tkfont.Font(font=self.preview_question_text.cget("font"))
        bold_font.configure(weight="bold")
        self.preview_question_text_font = base_font
        self.preview_question_text_bold_font = bold_font
        self.configure_preview_text_widget(self.preview_question_text)

    def bind_question_text_mousewheel(self, widget: tk.Text) -> None:
        def _scroll(event):
            if event.delta:
                widget.yview_scroll(int(-1 * (event.delta / 120)), "units")
            elif getattr(event, "num", None) == 4:
                widget.yview_scroll(-1, "units")
            elif getattr(event, "num", None) == 5:
                widget.yview_scroll(1, "units")
            return "break"

        widget.bind("<MouseWheel>", _scroll)
        widget.bind("<Button-4>", _scroll)
        widget.bind("<Button-5>", _scroll)

    def bind_normalized_paste(self, widget: tk.Text) -> None:
        widget.bind("<<Paste>>", lambda event, target=widget: self.handle_normalized_paste(event, target))
        widget.bind("<Control-v>", lambda event, target=widget: self.handle_normalized_paste(event, target))
        widget.bind("<Control-V>", lambda event, target=widget: self.handle_normalized_paste(event, target))

    def handle_normalized_paste(self, _event, widget: tk.Text):
        try:
            clipboard_text = self.root.clipboard_get()
        except tk.TclError:
            return "break"
        normalized = normalize_pdf_text(clipboard_text)
        if not normalized:
            return "break"
        try:
            start, end = widget.index("sel.first"), widget.index("sel.last")
            widget.delete(start, end)
            insert_index = start
        except tk.TclError:
            insert_index = widget.index("insert")
        widget.insert(insert_index, normalized)
        if widget is self.question_text:
            self.on_question_text_changed()
        else:
            self.update_preview()
        return "break"

    def on_question_text_changed(self, _event=None) -> None:
        self.sync_expanded_editor_from_main()
        self.update_preview()

    def remember_active_editor(self, widget: tk.Text) -> None:
        if widget in self.option_texts.values():
            self.last_active_option_widget = widget

    def get_question_selection_range(self, widget: tk.Text | None = None) -> tuple[str, str] | None:
        target = widget or self.question_text
        if target is None:
            return None
        try:
            return target.index("sel.first"), target.index("sel.last")
        except tk.TclError:
            return None

    def toggle_question_bold(self) -> None:
        if self.question_text is None:
            return
        self.toggle_question_bold_on_widget(self.question_text)
        self.on_question_text_changed()

    def toggle_question_underline(self) -> None:
        if self.question_text is None:
            return
        self.toggle_question_underline_on_widget(self.question_text)
        self.on_question_text_changed()

    def get_active_option_widget(self) -> tk.Text | None:
        focused_widget = self.root.focus_get()
        for widget in self.option_texts.values():
            if focused_widget == widget:
                self.last_active_option_widget = widget
                return widget
        if self.last_active_option_widget in self.option_texts.values():
            return self.last_active_option_widget
        return None

    def toggle_active_option_bold(self) -> None:
        widget = self.get_active_option_widget()
        if widget is None:
            messagebox.showinfo("Negrito", "Clique dentro de uma alternativa e selecione o trecho que deseja formatar.")
            return
        self.toggle_question_bold_on_widget(widget)
        self.update_preview()

    def toggle_active_option_underline(self) -> None:
        widget = self.get_active_option_widget()
        if widget is None:
            messagebox.showinfo("Sublinhado", "Clique dentro de uma alternativa e selecione o trecho que deseja formatar.")
            return
        self.toggle_question_underline_on_widget(widget)
        self.update_preview()

    def get_question_html(self, widget: tk.Text | None = None) -> str:
        target = widget or self.question_text
        if target is None:
            return ""
        last_line = int(target.index("end-1c").split(".")[0])
        lines: list[str] = []
        for line_number in range(1, last_line + 1):
            start = f"{line_number}.0"
            end = f"{line_number}.end"
            line_text = normalize_pdf_text(target.get(start, end))
            if not line_text and line_number == last_line:
                continue
            lines.append(self.serialize_question_line(target, start, end))
        return "<br>".join(lines).strip()

    def get_widget_plain_text(self, widget: tk.Text | None) -> str:
        if widget is None:
            return ""
        return normalize_pdf_text(widget.get("1.0", "end")).strip()

    def serialize_question_line(self, widget: tk.Text, start: str, end: str) -> str:
        parts: list[str] = []
        active_tags: set[str] = set()
        for item_type, value, _index in widget.dump(start, end, text=True, tag=True):
            if item_type == "tagon":
                active_tags.add(value)
                continue
            if item_type == "tagoff":
                active_tags.discard(value)
                continue
            if item_type != "text" or value == "":
                continue
            escaped = html.escape(normalize_pdf_text(value))
            if QUESTION_BOLD_TAG in active_tags:
                escaped = f"<b>{escaped}</b>"
            if QUESTION_UNDERLINE_TAG in active_tags:
                escaped = f"<u>{escaped}</u>"
            parts.append(escaped)
        return "".join(parts)

    def set_widget_text_from_html(self, widget: tk.Text | None, html_text: str) -> None:
        if widget is None:
            return
        widget.delete("1.0", "end")
        parsed_lines = parse_question_html(html_text)
        for line_index, line_data in enumerate(parsed_lines):
            if line_index > 0:
                widget.insert("end", "\n")
            for segment in line_data["segments"]:
                normalized_segment = normalize_pdf_text(segment["text"])
                segment_start = widget.index("end-1c")
                widget.insert("end", normalized_segment)
                segment_end = widget.index("end-1c")
                if segment["bold"] and segment_start != segment_end:
                    widget.tag_add(QUESTION_BOLD_TAG, segment_start, segment_end)
                if segment.get("underline") and segment_start != segment_end:
                    widget.tag_add(QUESTION_UNDERLINE_TAG, segment_start, segment_end)

    def set_question_text_from_html(self, html_text: str) -> None:
        if self.question_text is None:
            return
        self.set_widget_text_from_html(self.question_text, html_text)
        self.raw_question_html = self.get_question_html()
        self.sync_expanded_editor_from_main()
        self.sync_question_html_window()

    def sync_question_html_window(self) -> None:
        if self.question_html_window is None or not self.question_html_window.winfo_exists() or self.question_html_text is None:
            return
        self.question_html_text.delete("1.0", "end")
        self.question_html_text.insert("1.0", self.get_question_html())

    def open_question_html_editor(self) -> None:
        if self.question_html_window is not None and self.question_html_window.winfo_exists():
            self.question_html_window.lift()
            self.question_html_window.focus_force()
            self.sync_question_html_window()
            return
        window = tk.Toplevel(self.root)
        window.title("Codigo HTML do enunciado")
        window.geometry("900x600")
        window.minsize(700, 420)
        window.transient(self.root)
        self.question_html_window = window

        container = ttk.Frame(window, padding=12)
        container.pack(fill="both", expand=True)
        container.columnconfigure(0, weight=1)
        container.rowconfigure(1, weight=1)
        ttk.Label(container, text="Edite o HTML completo do campo Texto da questao.").grid(row=0, column=0, sticky="w", pady=(0, 8))

        editor_frame = ttk.Frame(container)
        editor_frame.grid(row=1, column=0, sticky="nsew")
        editor_frame.columnconfigure(0, weight=1)
        editor_frame.rowconfigure(0, weight=1)
        scrollbar = ttk.Scrollbar(editor_frame, orient="vertical")
        self.question_html_text = tk.Text(editor_frame, wrap="word", font=("Consolas", 10), undo=True, yscrollcommand=scrollbar.set)
        self.question_html_text.grid(row=0, column=0, sticky="nsew")
        scrollbar.configure(command=self.question_html_text.yview)
        scrollbar.grid(row=0, column=1, sticky="ns")

        buttons = ttk.Frame(container)
        buttons.grid(row=2, column=0, sticky="e", pady=(8, 0))
        ttk.Button(buttons, text="Aplicar no editor", command=self.apply_question_html_from_window).pack(side="left", padx=(0, 8))
        ttk.Button(buttons, text="Atualizar do editor", command=self.sync_question_html_window).pack(side="left")

        self.sync_question_html_window()

    def apply_question_html_from_window(self) -> None:
        if self.question_html_text is None:
            return
        self.set_question_text_from_html(self.question_html_text.get("1.0", "end").strip())
        self.on_question_text_changed()

    def open_expanded_question_editor(self) -> None:
        if self.expanded_question_window is not None and self.expanded_question_window.winfo_exists():
            self.expanded_question_window.state("zoomed")
            self.expanded_question_window.lift()
            self.expanded_question_window.focus_force()
            self.sync_expanded_editor_from_main()
            return
        window = tk.Toplevel(self.root)
        window.title("Editor ampliado do enunciado")
        window.state("zoomed")
        window.minsize(900, 600)
        self.expanded_question_window = window

        container = ttk.Frame(window, padding=12)
        container.pack(fill="both", expand=True)
        container.columnconfigure(0, weight=1)
        container.rowconfigure(1, weight=1)

        toolbar = ttk.Frame(container)
        toolbar.grid(row=0, column=0, sticky="ew", pady=(0, 8))
        ttk.Button(toolbar, text="Negrito", command=self.toggle_question_bold_expanded).pack(side="left")
        ttk.Button(toolbar, text="Sublinhado", command=self.toggle_question_underline_expanded).pack(side="left", padx=(8, 0))
        ttk.Button(toolbar, text="Sincronizar agora", command=self.apply_expanded_editor_to_main).pack(side="left", padx=(20, 0))

        text_frame = ttk.Frame(container)
        text_frame.grid(row=1, column=0, sticky="nsew")
        text_frame.columnconfigure(0, weight=1)
        text_frame.rowconfigure(0, weight=1)
        scrollbar = ttk.Scrollbar(text_frame, orient="vertical")
        self.expanded_question_text = tk.Text(
            text_frame,
            wrap="word",
            font=("Segoe UI", 12),
            undo=True,
            yscrollcommand=scrollbar.set,
        )
        self.expanded_question_text.grid(row=0, column=0, sticky="nsew")
        scrollbar.configure(command=self.expanded_question_text.yview)
        scrollbar.grid(row=0, column=1, sticky="ns")
        self.configure_question_text_widget(self.expanded_question_text)
        self.expanded_question_text.bind("<KeyRelease>", lambda _e: self.apply_expanded_editor_to_main())
        self.bind_question_text_mousewheel(self.expanded_question_text)
        self.sync_expanded_editor_from_main()

    def sync_expanded_editor_from_main(self) -> None:
        if self.expanded_question_window is None or not self.expanded_question_window.winfo_exists() or self.expanded_question_text is None:
            return
        current_html = self.get_question_html()
        expanded_html = self.get_question_html(self.expanded_question_text)
        if current_html == expanded_html:
            return
        self.expanded_question_text.delete("1.0", "end")
        parsed_lines = parse_question_html(current_html)
        for line_index, line_data in enumerate(parsed_lines):
            if line_index > 0:
                self.expanded_question_text.insert("end", "\n")
            for segment in line_data["segments"]:
                start = self.expanded_question_text.index("end-1c")
                self.expanded_question_text.insert("end", segment["text"])
                end = self.expanded_question_text.index("end-1c")
                if segment["bold"] and start != end:
                    self.expanded_question_text.tag_add(QUESTION_BOLD_TAG, start, end)
                if segment.get("underline") and start != end:
                    self.expanded_question_text.tag_add(QUESTION_UNDERLINE_TAG, start, end)

    def apply_expanded_editor_to_main(self) -> None:
        if self.expanded_question_text is None or self.question_text is None:
            return
        expanded_html = self.get_question_html(self.expanded_question_text)
        current_html = self.get_question_html(self.question_text)
        if expanded_html == current_html:
            return
        self.set_question_text_from_html(expanded_html)
        self.on_question_text_changed()

    def toggle_question_bold_expanded(self) -> None:
        if self.expanded_question_text is None:
            return
        self.toggle_question_bold_on_widget(self.expanded_question_text)
        self.apply_expanded_editor_to_main()

    def toggle_question_underline_expanded(self) -> None:
        if self.expanded_question_text is None:
            return
        self.toggle_question_underline_on_widget(self.expanded_question_text)
        self.apply_expanded_editor_to_main()

    def toggle_question_bold_on_widget(self, widget: tk.Text) -> None:
        self.toggle_question_tag_on_widget(
            widget=widget,
            tag_name=QUESTION_BOLD_TAG,
            title="Negrito",
            message="Selecione um trecho do texto da questao para aplicar negrito.",
        )

    def toggle_question_underline_on_widget(self, widget: tk.Text) -> None:
        self.toggle_question_tag_on_widget(
            widget=widget,
            tag_name=QUESTION_UNDERLINE_TAG,
            title="Sublinhado",
            message="Selecione um trecho do texto da questao para aplicar sublinhado.",
        )

    def toggle_question_tag_on_widget(self, widget: tk.Text, tag_name: str, title: str, message: str) -> None:
        selection = self.get_question_selection_range(widget)
        if selection is None:
            messagebox.showinfo(title, message)
            return
        start, end = selection
        if tag_name in widget.tag_names(start) and tag_name in widget.tag_names(f"{end} -1c"):
            widget.tag_remove(tag_name, start, end)
        else:
            widget.tag_add(tag_name, start, end)


    def bind_mousewheel(self) -> None:
        def _is_descendant(widget, ancestor):
            current = widget
            while current is not None:
                if current == ancestor:
                    return True
                current = getattr(current, 'master', None)
            return False

        def _scroll_target(widget):
            if self.preview_canvas is not None and _is_descendant(widget, self.preview_canvas):
                return self.preview_canvas
            return self.scroll_canvas

        def _on_mousewheel(event):
            target = _scroll_target(event.widget)
            if target is None:
                return
            if event.delta:
                target.yview_scroll(int(-1 * (event.delta / 120)), "units")
            elif getattr(event, "num", None) == 4:
                target.yview_scroll(-1, "units")
            elif getattr(event, "num", None) == 5:
                target.yview_scroll(1, "units")

        self.root.bind_all("<MouseWheel>", _on_mousewheel)
        self.root.bind_all("<Button-4>", _on_mousewheel)
        self.root.bind_all("<Button-5>", _on_mousewheel)

    def load_preview_image(self, source: Path | None, key: str, size: tuple[int, int]) -> ImageTk.PhotoImage | None:
        if source is None or not source.exists():
            self.preview_images.pop(key, None)
            return None
        image = Image.open(source)
        image.thumbnail(size)
        photo = ImageTk.PhotoImage(image)
        self.preview_images[key] = photo
        return photo

    def update_preview(self) -> None:
        self.preview_subject_var.set(self.subject_var.get().strip() or "Materia")
        self.render_question_preview()

        explanation_text = self.explanation_text.get("1.0", "end").strip() if self.include_explanation.get() else ""
        self.preview_explanation_var.set(explanation_text)
        explanation_image_path = None
        if self.include_explanation.get():
            explanation_image_path = self.explanation_image_source
            if explanation_image_path is None and self.explanation_existing_image:
                explanation_image_path = self.output_dir / self.explanation_existing_image
        explanation_photo = self.load_preview_image(explanation_image_path, "explanation", PREVIEW_IMAGE_MAX)
        if explanation_text or explanation_photo:
            self.preview_explanation_title.grid()
            if explanation_text:
                self.preview_explanation_label.grid()
            else:
                self.preview_explanation_label.grid_remove()
            if explanation_photo and self.preview_explanation_image_widget is not None:
                self.preview_explanation_image_widget.configure(image=explanation_photo)
                self.preview_explanation_image_widget.grid()
            elif self.preview_explanation_image_widget is not None:
                self.preview_explanation_image_widget.configure(image="")
                self.preview_explanation_image_widget.grid_remove()
        else:
            self.preview_explanation_title.grid_remove()
            self.preview_explanation_label.grid_remove()
            if self.preview_explanation_image_widget is not None:
                self.preview_explanation_image_widget.configure(image="")
                self.preview_explanation_image_widget.grid_remove()

        question_image_path = self.question_image_source
        if question_image_path is None and self.question_existing_image:
            question_image_path = self.output_dir / self.question_existing_image
        question_photo = self.load_preview_image(question_image_path, "question", PREVIEW_IMAGE_MAX)
        if question_photo:
            self.preview_question_image_widget.configure(image=question_photo)
            self.preview_question_image_widget.grid()
        else:
            self.preview_question_image_widget.configure(image="")
            self.preview_question_image_widget.grid_remove()

        visible_labels = OPTION_LABELS if self.show_option_e else DEFAULT_VISIBLE_OPTIONS
        for label in OPTION_LABELS:
            card = self.preview_option_cards[label]
            if label in visible_labels:
                card.grid()
            else:
                card.grid_remove()
                continue
            self.render_preview_text(
                widget=self.preview_option_text_widgets[label],
                html_text=self.get_question_html(self.option_texts[label]),
                placeholder="",
                prefix=f"{label}) ",
                min_lines=2,
                max_lines=6,
            )
            image_path = self.option_image_sources[label]
            if image_path is None and self.option_existing_images[label]:
                image_path = self.output_dir / self.option_existing_images[label]
            option_photo = self.load_preview_image(image_path, f"option_{label}", OPTION_PREVIEW_IMAGE_MAX)
            widget = self.preview_option_image_widgets[label]
            if option_photo:
                widget.configure(image=option_photo)
                widget.grid()
            else:
                widget.configure(image="")
                widget.grid_remove()

    def render_question_preview(self) -> None:
        if self.preview_question_text is None:
            return
        self.render_preview_text(
            widget=self.preview_question_text,
            html_text=self.get_question_html(),
            placeholder="Texto da questao",
            min_lines=4,
            max_lines=12,
        )

    def render_preview_text(
        self,
        widget: tk.Text,
        html_text: str,
        placeholder: str,
        prefix: str = "",
        min_lines: int = 2,
        max_lines: int = 12,
    ) -> None:
        widget.configure(state="normal")
        widget.delete("1.0", "end")
        widget.tag_remove(QUESTION_BOLD_TAG, "1.0", "end")
        widget.tag_remove(QUESTION_UNDERLINE_TAG, "1.0", "end")
        parsed_lines = parse_question_html(html_text)
        if not any(line["segments"] for line in parsed_lines):
            parsed_lines = [{"align": "left", "segments": [{"text": placeholder, "bold": False, "underline": False}]}]
        for line_index, line_data in enumerate(parsed_lines):
            if line_index > 0:
                widget.insert("end", "\n")
            if line_index == 0 and prefix:
                widget.insert("end", prefix)
            for segment in line_data["segments"]:
                start = widget.index("end-1c")
                widget.insert("end", segment["text"])
                end = widget.index("end-1c")
                if segment["bold"] and start != end:
                    widget.tag_add(QUESTION_BOLD_TAG, start, end)
                if segment.get("underline") and start != end:
                    widget.tag_add(QUESTION_UNDERLINE_TAG, start, end)
        total_lines = max(min_lines, min(max_lines, int(widget.index("end-1c").split(".")[0]) + 1))
        widget.configure(height=total_lines, state="disabled")

    def refresh_available_files(self) -> None:
        self.available_files = list_question_files(self.output_dir)
        file_names = [path.name for path in self.available_files]
        self.file_selector["values"] = file_names
        if file_names:
            self.file_selector.set(file_names[0])
            self.open_existing_button.state(["!disabled"])
        else:
            self.file_selector.set("")
            self.open_existing_button.state(["disabled"])

    def refresh_next_file_label(self) -> None:
        next_number = next_question_number(self.output_dir)
        self.file_label.set(f"Proximo arquivo novo: questao_{next_number}.json")

    def set_mode_create(self) -> None:
        self.current_edit_path = None
        self.mode_label.set("Modo: criando nova questao")
        self.save_edit_button.state(["disabled"])
        self.refresh_next_file_label()

    def set_mode_edit(self, path: Path) -> None:
        self.current_edit_path = path
        self.mode_label.set(f"Modo: editando {path.name}")
        self.save_edit_button.state(["!disabled"])
        self.file_label.set(f"Arquivo em edicao: {path.name}")

    def update_option_e_visibility(self, visible: bool) -> None:
        self.show_option_e = visible
        label_widget, row_frame = self.option_widgets["E"]
        if visible:
            label_widget.grid()
            row_frame.grid()
            self.option_toggle_label.set("Ocultar alternativa E")
        else:
            label_widget.grid_remove()
            row_frame.grid_remove()
            self.option_toggle_label.set("Mostrar alternativa E")
            if self.correct_option.get() == "E":
                self.correct_option.set("A")
        self.update_preview()

    def toggle_option_e(self) -> None:
        self.update_option_e_visibility(not self.show_option_e)

    def clear_explanation_editor(self) -> None:
        self.explanation_text.configure(state="normal")
        self.explanation_text.delete("1.0", "end")

    def on_explanation_toggle(self) -> None:
        if self.include_explanation.get():
            self.explanation_text.configure(state="normal")
        else:
            self.clear_explanation_editor()
            self.clear_explanation_image(update_preview=False)
            self.explanation_text.configure(state="disabled")
        self.update_preview()

    def pick_image(self) -> Path | None:
        selected = filedialog.askopenfilename(title="Selecione uma imagem", filetypes=IMAGE_TYPES)
        return Path(selected) if selected else None

    def select_question_image(self) -> None:
        selected = self.pick_image()
        if selected:
            self.question_image_source = selected
            self.question_existing_image = None
            self.question_image_label.set(selected.name)
            self.update_preview()

    def clear_question_image(self) -> None:
        self.question_image_source = None
        self.question_existing_image = None
        self.question_image_label.set("Sem imagem no enunciado")
        self.update_preview()

    def select_explanation_image(self) -> None:
        selected = self.pick_image()
        if selected:
            self.include_explanation.set(True)
            self.explanation_text.configure(state="normal")
            self.explanation_image_source = selected
            self.explanation_existing_image = None
            self.explanation_image_label.set(selected.name)
            self.update_preview()

    def clear_explanation_image(self, update_preview: bool = True) -> None:
        self.explanation_image_source = None
        self.explanation_existing_image = None
        self.explanation_image_label.set("Sem imagem na explicacao")
        if update_preview:
            self.update_preview()

    def select_option_image(self, label: str) -> None:
        selected = self.pick_image()
        if selected:
            self.option_image_sources[label] = selected
            self.option_existing_images[label] = None
            self.option_image_labels[label].set(selected.name)
            if label == "E":
                self.update_option_e_visibility(True)
            self.update_preview()

    def clear_option_image(self, label: str) -> None:
        self.option_image_sources[label] = None
        self.option_existing_images[label] = None
        self.option_image_labels[label].set(f"Sem imagem na alternativa {label}")
        self.update_preview()
    def copy_image(self, source: Path, file_name: str) -> str:
        target = self.output_dir / file_name
        if source.resolve() != target.resolve():
            shutil.copy2(source, target)
        return file_name

    def delete_if_exists(self, image_name: str | None) -> None:
        if not image_name:
            return
        path = self.output_dir / image_name
        if path.exists() and path.is_file():
            path.unlink()

    def option_has_content(self, label: str) -> bool:
        plain_text = self.get_widget_plain_text(self.option_texts[label])
        return bool(plain_text or self.option_image_sources[label] or self.option_existing_images[label])

    def collect_active_option_labels(self) -> list[str]:
        labels_to_check = OPTION_LABELS if self.show_option_e else DEFAULT_VISIBLE_OPTIONS
        return [label for label in labels_to_check if self.option_has_content(label)]

    def build_option_images(self, question_number: int, labels: list[str]) -> list[str | None]:
        result: list[str | None] = []
        for label in labels:
            selected_source = self.option_image_sources[label]
            existing_image = self.option_existing_images[label]
            if selected_source is not None:
                image_name = self.copy_image(selected_source, f"questao_{question_number}_alt_{label}{selected_source.suffix.lower()}")
                if existing_image and existing_image != image_name:
                    self.delete_if_exists(existing_image)
                result.append(image_name)
            else:
                result.append(existing_image)
        return result

    def parse_options(self, labels: list[str]) -> list[str]:
        options = []
        for label in labels:
            options.append(self.get_question_html(self.option_texts[label]))
        if len(options) < 2:
            raise ValueError("Informe pelo menos 2 alternativas.")
        return options

    def parse_year(self) -> int:
        year_text = self.year_var.get().strip()
        if not year_text:
            raise ValueError("Informe o ano.")
        if not year_text.isdigit():
            raise ValueError("Ano deve conter apenas numeros.")
        return int(year_text)

    def parse_subject(self) -> str:
        subject = self.subject_var.get().strip()
        if not subject:
            raise ValueError("Informe a materia.")
        return subject

    def build_payload(self, question_number: int) -> dict:
        question_plain = self.get_widget_plain_text(self.question_text)
        if not question_plain:
            raise ValueError("Cole o texto da questao.")
        question_html = self.get_question_html()
        active_option_labels = self.collect_active_option_labels()
        options = self.parse_options(active_option_labels)
        option_images = self.build_option_images(question_number, active_option_labels)
        selected_letter = self.correct_option.get().strip().upper()
        if selected_letter not in active_option_labels:
            raise ValueError("A alternativa correta selecionada nao existe na lista informada.")
        correct_index = active_option_labels.index(selected_letter)
        explanation = ""
        explanation_image_name = self.explanation_existing_image
        if self.include_explanation.get():
            explanation = self.get_widget_plain_text(self.explanation_text)
            if self.explanation_image_source is not None:
                explanation_image_name = self.copy_image(
                    self.explanation_image_source,
                    f"questao_{question_number}_explicacao{self.explanation_image_source.suffix.lower()}"
                )
            elif self.explanation_existing_image is None:
                explanation_image_name = None
            if not explanation and not explanation_image_name:
                raise ValueError("Voce marcou explicacao, mas nao informou texto nem imagem.")
        else:
            explanation_image_name = None
        question_image_name = self.question_existing_image
        if self.question_image_source is not None:
            question_image_name = self.copy_image(self.question_image_source, f"questao_{question_number}_enunciado{self.question_image_source.suffix.lower()}")
        elif self.question_existing_image is None:
            question_image_name = None
        return {
            "id": question_number,
            "text": question_html,
            "questionImage": question_image_name,
            "options": options,
            "optionImages": option_images,
            "correctOption": correct_index,
            "subject": self.parse_subject(),
            "explanation": normalize_html_text(explanation),
            "explanationImage": explanation_image_name,
            "year": self.parse_year(),
        }

    def populate_form(self, payload: dict, path: Path) -> None:
        self.clear_form(refresh_only=False)
        self.set_question_text_from_html(payload.get("text", ""))
        question_image = payload.get("questionImage")
        self.question_existing_image = question_image
        if question_image:
            self.question_image_source = self.output_dir / question_image
            self.question_image_label.set(question_image)
        options = payload.get("options", [])
        option_images = payload.get("optionImages", [])
        self.update_option_e_visibility(len(options) >= 5)
        for index, option in enumerate(options):
            label = OPTION_LABELS[index]
            self.set_widget_text_from_html(self.option_texts[label], option)
            image_name = option_images[index] if index < len(option_images) else None
            self.option_existing_images[label] = image_name
            if image_name:
                self.option_image_sources[label] = self.output_dir / image_name
                self.option_image_labels[label].set(image_name)
        correct_option = int(payload.get("correctOption", 0))
        self.correct_option.set(chr(ord("A") + correct_option))
        self.subject_var.set(str(payload.get("subject", detect_subject(self.output_dir))))
        year_value = payload.get("year", detect_year(self.output_dir))
        self.year_var.set(str(year_value) if year_value is not None else "")
        explanation = html_to_editor_text(payload.get("explanation", ""))
        explanation_image = payload.get("explanationImage")
        self.explanation_existing_image = explanation_image
        if explanation_image:
            self.explanation_image_source = self.output_dir / explanation_image
            self.explanation_image_label.set(explanation_image)
        if explanation or explanation_image:
            self.include_explanation.set(True)
            self.on_explanation_toggle()
            self.clear_explanation_editor()
            self.explanation_text.insert("1.0", normalize_pdf_text(explanation))
        else:
            self.include_explanation.set(False)
            self.on_explanation_toggle()
        self.set_mode_edit(path)
        self.update_preview()

    def open_selected_question(self) -> None:
        selected_name = self.file_selector.get().strip()
        if not selected_name:
            messagebox.showerror("Erro", "Selecione um arquivo para abrir.")
            return
        selected_path = self.output_dir / selected_name
        try:
            with selected_path.open("r", encoding="utf-8") as file:
                payload = json.load(file)
            self.populate_form(payload, selected_path)
        except Exception as exc:
            messagebox.showerror("Erro", f"Nao foi possivel abrir o arquivo.\n{exc}")

    def write_payload(self, payload: dict, target_path: Path) -> None:
        with target_path.open("w", encoding="utf-8") as file:
            json.dump(payload, file, ensure_ascii=False, indent=2)

    def generate_json(self) -> None:
        try:
            question_number = next_question_number(self.output_dir)
            payload = self.build_payload(question_number)
            target_path = self.output_dir / f"questao_{question_number}.json"
            self.write_payload(payload, target_path)
            self.refresh_available_files()
            self.clear_form(refresh_only=False)
            self.refresh_next_file_label()
            messagebox.showinfo("Arquivo gerado", f"JSON salvo em:\n{target_path}")
        except Exception as exc:
            messagebox.showerror("Erro", str(exc))

    def save_edit(self) -> None:
        if self.current_edit_path is None:
            messagebox.showerror("Erro", "Nenhum arquivo aberto para edicao.")
            return
        try:
            match = FILENAME_PATTERN.match(self.current_edit_path.name)
            if not match:
                raise ValueError("O arquivo em edicao nao segue o padrao questao_N.json.")
            question_number = int(match.group(1))
            payload = self.build_payload(question_number)
            self.write_payload(payload, self.current_edit_path)
            self.refresh_available_files()
            self.set_mode_edit(self.current_edit_path)
            messagebox.showinfo("Edicao salva", f"Arquivo atualizado:\n{self.current_edit_path}")
        except Exception as exc:
            messagebox.showerror("Erro", str(exc))

    def clear_form(self, refresh_only: bool = True) -> None:
        self.last_active_option_widget = None
        self.question_text.delete("1.0", "end")
        self.raw_question_html = ""
        self.sync_question_html_window()
        self.sync_expanded_editor_from_main()
        self.question_image_source = None
        self.question_existing_image = None
        self.question_image_label.set("Sem imagem no enunciado")
        self.explanation_image_source = None
        self.explanation_existing_image = None
        self.explanation_image_label.set("Sem imagem na explicacao")
        for label in OPTION_LABELS:
            self.option_texts[label].delete("1.0", "end")
            self.option_image_sources[label] = None
            self.option_existing_images[label] = None
            self.option_image_labels[label].set(f"Sem imagem na alternativa {label}")
        self.include_explanation.set(False)
        self.correct_option.set("A")
        self.subject_var.set(detect_subject(self.output_dir))
        detected_year = detect_year(self.output_dir)
        self.year_var.set(str(detected_year) if detected_year else "")
        self.update_option_e_visibility(False)
        self.on_explanation_toggle()
        self.set_mode_create()
        if refresh_only:
            self.refresh_available_files()
        self.update_preview()


def main() -> None:
    root = tk.Tk()
    style = ttk.Style(root)
    if "vista" in style.theme_names():
        style.theme_use("vista")
    QuestionGeneratorApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
