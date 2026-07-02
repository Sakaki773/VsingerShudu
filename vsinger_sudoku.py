from __future__ import annotations

import random
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import tkinter as tk
from tkinter import messagebox

from PIL import Image, ImageEnhance, ImageTk


APP_TITLE = "V家数独"
BASE_DIR = Path(__file__).resolve().parent
ASSET_DIR = BASE_DIR / "assets"
HERO_IMAGE = ASSET_DIR / "luotianyi_hero.jpg"
SOFT_IMAGE = ASSET_DIR / "luotianyi_soft.jpg"
PC_WELCOME_IMAGE = ASSET_DIR / "pc" / "welcome.jpg"
PC_GAME_BACKGROUND = ASSET_DIR / "pc" / "game_background.jpg"
PC_ASSET_DIR = ASSET_DIR / "pc"

WINDOW_W = 560
WINDOW_H = 920
BOARD_SIZE = 540

THEME = {
    "bg": "#F2FBFF",
    "panel": "#FFFFFF",
    "cyan": "#66CCFF",
    "cyan_dark": "#168FC9",
    "cyan_deep": "#006CA8",
    "cyan_soft": "#DDF5FF",
    "gold": "#D89A00",
    "gold_soft": "#FFF3A6",
    "line": "#B8BEC2",
    "heavy_line": "#090909",
    "fixed": "#111111",
    "result": "#168FC9",
    "note": "#314047",
    "danger": "#E6002D",
    "danger_bg": "#FFE2E8",
    "press": "#FFF68F",
    "press_related": "#E7F7FF",
    "press_box": "#D6F0FF",
}


DIFFICULTIES = {
    "简单": 42,
    "标准": 34,
    "大师": 28,
}


def deep_copy_grid(grid: list[list[int]]) -> list[list[int]]:
    return [row[:] for row in grid]


def deep_copy_notes(notes: list[list[set[int]]]) -> list[list[set[int]]]:
    return [[set(cell) for cell in row] for row in notes]


def pattern(row: int, col: int) -> int:
    return (3 * (row % 3) + row // 3 + col) % 9


def shuffled(values: Iterable[int]) -> list[int]:
    values = list(values)
    random.shuffle(values)
    return values


def generate_solution() -> list[list[int]]:
    rows = [group * 3 + row for group in shuffled(range(3)) for row in shuffled(range(3))]
    cols = [group * 3 + col for group in shuffled(range(3)) for col in shuffled(range(3))]
    nums = shuffled(range(1, 10))
    return [[nums[pattern(row, col)] for col in cols] for row in rows]


def solve_count(board: list[list[int]], limit: int = 2) -> int:
    rows = [0] * 9
    cols = [0] * 9
    boxes = [0] * 9
    empties: list[tuple[int, int]] = []

    for r in range(9):
        for c in range(9):
            n = board[r][c]
            if n:
                bit = 1 << n
                box = (r // 3) * 3 + c // 3
                if rows[r] & bit or cols[c] & bit or boxes[box] & bit:
                    return 0
                rows[r] |= bit
                cols[c] |= bit
                boxes[box] |= bit
            else:
                empties.append((r, c))

    full_mask = sum(1 << n for n in range(1, 10))
    count = 0

    def backtrack() -> None:
        nonlocal count
        if count >= limit:
            return
        best_index = -1
        best_mask = 0
        best_options = 10
        for i, (r, c) in enumerate(empties):
            if board[r][c] != 0:
                continue
            box = (r // 3) * 3 + c // 3
            mask = full_mask & ~(rows[r] | cols[c] | boxes[box])
            options = mask.bit_count()
            if options == 0:
                return
            if options < best_options:
                best_index = i
                best_mask = mask
                best_options = options
                if options == 1:
                    break
        if best_index == -1:
            count += 1
            return

        r, c = empties[best_index]
        box = (r // 3) * 3 + c // 3
        choices = [n for n in range(1, 10) if best_mask & (1 << n)]
        random.shuffle(choices)
        for n in choices:
            bit = 1 << n
            board[r][c] = n
            rows[r] |= bit
            cols[c] |= bit
            boxes[box] |= bit
            backtrack()
            rows[r] &= ~bit
            cols[c] &= ~bit
            boxes[box] &= ~bit
            board[r][c] = 0
            if count >= limit:
                return

    backtrack()
    return count


def generate_puzzle(difficulty: str) -> tuple[list[list[int]], list[list[int]]]:
    target_givens = DIFFICULTIES[difficulty]
    solution = generate_solution()
    puzzle = deep_copy_grid(solution)
    cells = [(r, c) for r in range(9) for c in range(9)]
    random.shuffle(cells)

    givens = 81
    attempts_without_progress = 0
    while cells and givens > target_givens and attempts_without_progress < 120:
        r, c = cells.pop()
        old = puzzle[r][c]
        puzzle[r][c] = 0
        test_board = deep_copy_grid(puzzle)
        if solve_count(test_board, limit=2) == 1:
            givens -= 1
            attempts_without_progress = 0
        else:
            puzzle[r][c] = old
            attempts_without_progress += 1

    return puzzle, solution


@dataclass
class Snapshot:
    player: list[list[int]]
    notes: list[list[set[int]]]


class VsingerSudokuApp:
    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title(APP_TITLE)
        self.root.geometry(f"{WINDOW_W}x{WINDOW_H}")
        self.root.minsize(500, 820)
        self.root.configure(bg=THEME["bg"])

        self.difficulty = "标准"
        self.puzzle = [[0] * 9 for _ in range(9)]
        self.solution = [[0] * 9 for _ in range(9)]
        self.player = [[0] * 9 for _ in range(9)]
        self.notes = [[set() for _ in range(9)] for _ in range(9)]
        self.history: list[Snapshot] = []

        self.selected_digit = 1
        self.mode = "result"
        self.tool = "digit"
        self.pressed_cell: tuple[int, int] | None = None
        self.checked_wrong_cells: set[tuple[int, int]] = set()
        self.check_flash_until = 0.0
        self.completed = False

        self.start_time = time.time()
        self.timer_job: str | None = None

        self.images: dict[str, ImageTk.PhotoImage] = {}
        self.welcome_art_path: Path | None = None
        self.game_art_path: Path | None = None
        self.board_canvas: tk.Canvas | None = None
        self.timer_var = tk.StringVar(value="00:00")
        self.status_var = tk.StringVar(value="先选数字，再点棋盘")
        self.difficulty_var = tk.StringVar(value=self.difficulty)
        self.mode_var = tk.StringVar(value="结果数字")
        self.digit_buttons: dict[int, tk.Canvas] = {}
        self.clear_button: tk.Button | None = None
        self.mode_button: tk.Button | None = None

        self.show_welcome()
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def run(self) -> None:
        self.root.mainloop()

    def clear_root(self) -> None:
        if self.timer_job:
            self.root.after_cancel(self.timer_job)
            self.timer_job = None
        for child in self.root.winfo_children():
            child.destroy()

    def art_pool(self, prefix: str) -> list[Path]:
        pool = sorted(PC_ASSET_DIR.glob(f"{prefix}_*.jpg"))
        fallback = {
            "welcome": PC_WELCOME_IMAGE,
            "game_background": PC_GAME_BACKGROUND,
        }.get(prefix)
        if fallback and fallback.exists():
            pool.append(fallback)
        return [path for path in pool if path.exists()]

    def choose_art(self, prefix: str, fallback: Path) -> Path:
        pool = self.art_pool(prefix)
        return random.choice(pool) if pool else fallback

    def load_cover_image(
        self,
        path: Path,
        size: tuple[int, int],
        *,
        dim: float = 1.0,
        white_overlay: int = 0,
    ) -> ImageTk.PhotoImage | None:
        if not path.exists():
            return None
        try:
            image = Image.open(path).convert("RGB")
            src_w, src_h = image.size
            dst_w, dst_h = size
            scale = max(dst_w / src_w, dst_h / src_h)
            new_size = (int(src_w * scale), int(src_h * scale))
            image = image.resize(new_size, Image.Resampling.LANCZOS)
            left = (new_size[0] - dst_w) // 2
            top = (new_size[1] - dst_h) // 2
            image = image.crop((left, top, left + dst_w, top + dst_h))
            if dim != 1.0:
                image = ImageEnhance.Brightness(image).enhance(dim)
            if white_overlay:
                overlay = Image.new("RGB", size, "white")
                image = Image.blend(image, overlay, white_overlay / 255)
            return ImageTk.PhotoImage(image)
        except Exception:
            return None

    def show_welcome(self) -> None:
        self.clear_root()
        canvas = tk.Canvas(self.root, width=WINDOW_W, height=WINDOW_H, highlightthickness=0)
        canvas.pack(fill="both", expand=True)

        self.welcome_art_path = self.choose_art("welcome", HERO_IMAGE)
        hero = self.load_cover_image(self.welcome_art_path, (WINDOW_W, WINDOW_H), dim=0.9)
        if hero:
            self.images["welcome"] = hero
            canvas.create_image(0, 0, image=hero, anchor="nw")
        else:
            canvas.configure(bg=THEME["bg"])
            for i in range(0, WINDOW_H, 4):
                ratio = i / WINDOW_H
                color = self.mix_color("#EAF8FA", "#FFFFFF", ratio)
                canvas.create_rectangle(0, i, WINDOW_W, i + 4, outline="", fill=color)

        canvas.create_rectangle(0, 0, WINDOW_W, WINDOW_H, fill="#FFFFFF", outline="", stipple="gray25")
        canvas.create_text(
            WINDOW_W // 2,
            126,
            text=APP_TITLE,
            fill=THEME["cyan_dark"],
            font=("Microsoft YaHei UI", 40, "bold"),
        )
        canvas.create_text(
            WINDOW_W // 2,
            178,
            text="洛天依主题 · 9x9 随机唯一解",
            fill="#21444B",
            font=("Microsoft YaHei UI", 16),
        )

        card = tk.Frame(canvas, bg="#FFFFFF", bd=0, highlightthickness=1, highlightbackground="#D4EEF1")
        canvas.create_window(WINDOW_W // 2, 680, window=card, width=430, height=198)

        tk.Label(
            card,
            text="选择难度",
            bg="#FFFFFF",
            fg="#1F3D42",
            font=("Microsoft YaHei UI", 15, "bold"),
        ).pack(pady=(20, 10))

        diff_row = tk.Frame(card, bg="#FFFFFF")
        diff_row.pack()
        for diff in DIFFICULTIES:
            tk.Radiobutton(
                diff_row,
                text=diff,
                value=diff,
                variable=self.difficulty_var,
                indicatoron=False,
                width=7,
                bd=0,
                padx=10,
                pady=8,
                bg="#EAF4F5",
                fg="#1D3337",
                selectcolor=THEME["cyan_soft"],
                activebackground=THEME["cyan_soft"],
                font=("Microsoft YaHei UI", 12, "bold"),
            ).pack(side="left", padx=7)

        tk.Button(
            card,
            text="开始演奏",
            command=self.start_game_from_welcome,
            bg=THEME["cyan"],
            fg="#062C31",
            activebackground=THEME["cyan_soft"],
            bd=0,
            padx=42,
            pady=11,
            font=("Microsoft YaHei UI", 15, "bold"),
            cursor="hand2",
        ).pack(pady=(22, 0))

    @staticmethod
    def mix_color(left: str, right: str, ratio: float) -> str:
        l = tuple(int(left[i : i + 2], 16) for i in (1, 3, 5))
        r = tuple(int(right[i : i + 2], 16) for i in (1, 3, 5))
        mixed = tuple(round(a * (1 - ratio) + b * ratio) for a, b in zip(l, r))
        return f"#{mixed[0]:02x}{mixed[1]:02x}{mixed[2]:02x}"

    def start_game_from_welcome(self) -> None:
        self.difficulty = self.difficulty_var.get()
        self.game_art_path = self.choose_art("game_background", SOFT_IMAGE)
        self.new_game()

    def build_game_ui(self) -> None:
        self.clear_root()
        bg_canvas = tk.Canvas(self.root, width=WINDOW_W, height=WINDOW_H, highlightthickness=0)
        bg_canvas.pack(fill="both", expand=True)
        bg_path = self.game_art_path or self.choose_art("game_background", SOFT_IMAGE)
        game_bg = self.load_cover_image(bg_path, (WINDOW_W, WINDOW_H), white_overlay=35)
        if game_bg:
            self.images["game_bg"] = game_bg
            bg_canvas.create_image(0, 0, image=game_bg, anchor="nw")
        else:
            bg_canvas.configure(bg=THEME["bg"])

        root_frame = tk.Frame(bg_canvas, bg=THEME["bg"])
        bg_canvas.create_window(WINDOW_W // 2, WINDOW_H // 2, window=root_frame, width=WINDOW_W - 8)

        header = tk.Frame(root_frame, bg=THEME["panel"], padx=12, pady=10)
        header.pack(fill="x", padx=10, pady=(10, 6))

        title_col = tk.Frame(header, bg=THEME["panel"])
        title_col.pack(side="left", fill="y")
        tk.Label(
            title_col,
            text=APP_TITLE,
            bg=THEME["panel"],
            fg=THEME["cyan_dark"],
            font=("Microsoft YaHei UI", 20, "bold"),
        ).pack(anchor="w")
        tk.Label(
            title_col,
            textvariable=self.status_var,
            bg=THEME["panel"],
            fg="#607277",
            font=("Microsoft YaHei UI", 10),
        ).pack(anchor="w", pady=(2, 0))

        right = tk.Frame(header, bg=THEME["panel"])
        right.pack(side="right", fill="y")
        tk.Label(
            right,
            textvariable=self.timer_var,
            bg=THEME["panel"],
            fg="#222222",
            font=("Consolas", 20, "bold"),
        ).pack(anchor="e")

        diff_row = tk.Frame(right, bg=THEME["panel"])
        diff_row.pack(anchor="e", pady=(4, 0))
        for diff in DIFFICULTIES:
            tk.Radiobutton(
                diff_row,
                text=diff,
                value=diff,
                variable=self.difficulty_var,
                command=self.change_difficulty,
                indicatoron=False,
                bd=0,
                padx=8,
                pady=4,
                bg="#EEF6F7",
                fg="#213C42",
                selectcolor=THEME["cyan_soft"],
                activebackground=THEME["cyan_soft"],
                font=("Microsoft YaHei UI", 9, "bold"),
            ).pack(side="left", padx=2)

        board_wrap = tk.Frame(root_frame, bg="#101010", padx=3, pady=3)
        board_wrap.pack(padx=8, pady=(2, 8))
        self.board_canvas = tk.Canvas(
            board_wrap,
            width=BOARD_SIZE,
            height=BOARD_SIZE,
            bg="#FFFFFF",
            highlightthickness=0,
        )
        self.board_canvas.pack()
        self.board_canvas.bind("<ButtonPress-1>", self.on_board_press)
        self.board_canvas.bind("<ButtonRelease-1>", self.on_board_release)
        self.board_canvas.bind("<Leave>", self.on_board_leave)

        control = tk.Frame(root_frame, bg=THEME["bg"], padx=8)
        control.pack(fill="x")

        first_row = tk.Frame(control, bg=THEME["bg"])
        first_row.pack(fill="x", pady=(0, 8))
        second_row = tk.Frame(control, bg=THEME["bg"])
        second_row.pack(fill="x", pady=(0, 8))
        tool_row = tk.Frame(control, bg=THEME["bg"])
        tool_row.pack(fill="x")

        self.digit_buttons = {}
        for digit in range(1, 6):
            self.digit_buttons[digit] = self.make_digit_button(first_row, digit)
        self.clear_button = self.make_tool_button(first_row, "C", self.select_clear_tool)

        for digit in range(6, 10):
            self.digit_buttons[digit] = self.make_digit_button(second_row, digit)
        self.mode_button = self.make_tool_button(second_row, "结果数字", self.toggle_mode)
        self.make_tool_button(second_row, "辅助", self.open_assist_menu)

        self.make_small_button(tool_row, "撤销", self.undo)
        self.make_small_button(tool_row, "提示", self.give_hint)
        self.make_small_button(tool_row, "检查", self.check_board)
        self.make_small_button(tool_row, "重新开始", self.restart)
        self.make_small_button(tool_row, "新题", self.new_game)

        self.update_control_styles()
        self.draw_board()
        self.start_timer()

    def make_digit_button(self, parent: tk.Frame, digit: int) -> tk.Canvas:
        btn = tk.Canvas(
            parent,
            height=92,
            highlightthickness=0,
            cursor="hand2",
        )
        btn.bind("<Button-1>", lambda _event, d=digit: self.select_digit(d))
        btn.bind("<Configure>", lambda _event, d=digit, b=btn: self.draw_digit_button(b, d))
        btn.pack(side="left", fill="both", expand=True, padx=4)
        return btn

    def make_tool_button(self, parent: tk.Frame, text: str, command) -> tk.Button:
        btn = tk.Button(
            parent,
            text=text,
            command=command,
            bd=0,
            height=4,
            font=("Microsoft YaHei UI", 13, "bold"),
            cursor="hand2",
            wraplength=72,
        )
        btn.pack(side="left", fill="both", expand=True, padx=4)
        return btn

    def make_small_button(self, parent: tk.Frame, text: str, command) -> tk.Button:
        btn = tk.Button(
            parent,
            text=text,
            command=command,
            bd=0,
            bg="#FFFFFF",
            fg="#25464C",
            activebackground=THEME["cyan_soft"],
            pady=8,
            font=("Microsoft YaHei UI", 10, "bold"),
            cursor="hand2",
        )
        btn.pack(side="left", fill="x", expand=True, padx=3)
        return btn

    def new_game(self) -> None:
        self.difficulty = self.difficulty_var.get()
        self.game_art_path = self.choose_art("game_background", SOFT_IMAGE)
        self.status_var.set("正在生成唯一解题目...")
        self.root.update_idletasks()
        self.puzzle, self.solution = generate_puzzle(self.difficulty)
        self.player = [[0] * 9 for _ in range(9)]
        self.notes = [[set() for _ in range(9)] for _ in range(9)]
        self.history = []
        self.selected_digit = 1
        self.mode = "result"
        self.tool = "digit"
        self.checked_wrong_cells.clear()
        self.completed = False
        self.start_time = time.time()
        self.status_var.set("先选数字，再点棋盘")
        self.difficulty_var.set(self.difficulty)
        self.build_game_ui()

    def restart(self) -> None:
        if not messagebox.askyesno("重新开始", "恢复当前题目的初始状态？"):
            return
        self.push_history()
        self.player = [[0] * 9 for _ in range(9)]
        self.notes = [[set() for _ in range(9)] for _ in range(9)]
        self.checked_wrong_cells.clear()
        self.completed = False
        self.start_time = time.time()
        self.status_var.set("已重新开始")
        self.update_control_styles()
        self.draw_board()

    def change_difficulty(self) -> None:
        requested = self.difficulty_var.get()
        if requested == self.difficulty:
            return
        if messagebox.askyesno("切换难度", f"生成一局新的 {requested} 题目？"):
            self.difficulty = requested
            self.new_game()
        else:
            self.difficulty_var.set(self.difficulty)

    def start_timer(self) -> None:
        elapsed = int(time.time() - self.start_time)
        minutes, seconds = divmod(elapsed, 60)
        self.timer_var.set(f"{minutes:02d}:{seconds:02d}")
        self.timer_job = self.root.after(1000, self.start_timer)

    def select_digit(self, digit: int) -> None:
        self.selected_digit = digit
        self.tool = "digit"
        self.status_var.set(f"已选 {digit} · {self.mode_text()}")
        self.update_control_styles()
        self.draw_board()

    def select_clear_tool(self) -> None:
        self.tool = "clear"
        self.status_var.set("清除工具：点格子清除最上层内容")
        self.update_control_styles()
        self.draw_board()

    def toggle_mode(self) -> None:
        self.mode = "assist" if self.mode == "result" else "result"
        self.mode_var.set(self.mode_text())
        self.tool = "digit"
        self.status_var.set(f"已切换到 {self.mode_text()}")
        self.update_control_styles()
        self.draw_board()

    def mode_text(self) -> str:
        return "结果数字" if self.mode == "result" else "辅助数字"

    def update_control_styles(self) -> None:
        for digit, btn in self.digit_buttons.items():
            self.draw_digit_button(btn, digit)
        if self.clear_button:
            self.clear_button.configure(
                bg="#DADDE0" if self.tool != "clear" else "#AAB2B8",
                fg="#101010",
                activebackground="#C9D0D5",
                relief="sunken" if self.tool == "clear" else "flat",
            )
        if self.mode_button:
            self.mode_button.configure(
                text=self.mode_text(),
                bg=THEME["cyan"] if self.mode == "result" else THEME["gold_soft"],
                fg="#062C31",
                activebackground=THEME["cyan_soft"],
            )

    def remaining_digit_count(self, digit: int) -> int:
        used = sum(1 for r in range(9) for c in range(9) if self.value_at(r, c) == digit)
        return max(0, 9 - used)

    def draw_digit_button(self, canvas: tk.Canvas, digit: int) -> None:
        width = max(canvas.winfo_width(), 64)
        height = max(canvas.winfo_height(), 78)
        selected = self.tool == "digit" and digit == self.selected_digit
        remaining = self.remaining_digit_count(digit)
        exhausted = remaining == 0
        fill = "#D8DEE3" if exhausted else (THEME["cyan"] if selected else "#FFFFFF")
        text_fill = "#7A858C" if exhausted else ("#062C31" if selected else "#1A3337")
        canvas.configure(bg=THEME["bg"])
        canvas.delete("all")
        outline = THEME["cyan_dark"] if selected else ""
        outline_width = 3 if selected else 0
        canvas.create_rectangle(2, 2, width - 2, height - 2, fill=fill, outline=outline, width=outline_width)
        canvas.create_text(
            width / 2,
            height / 2 + 6,
            text=str(digit),
            fill=text_fill,
            font=("Microsoft YaHei UI", 20, "bold"),
        )
        canvas.create_text(
            width - 14,
            14,
            text=str(remaining),
            fill=text_fill,
            font=("Microsoft YaHei UI", 10, "bold"),
            anchor="ne",
        )

    def cell_from_event(self, event: tk.Event) -> tuple[int, int] | None:
        if not (0 <= event.x < BOARD_SIZE and 0 <= event.y < BOARD_SIZE):
            return None
        cell = BOARD_SIZE / 9
        return int(event.y // cell), int(event.x // cell)

    def on_board_press(self, event: tk.Event) -> None:
        self.pressed_cell = self.cell_from_event(event)
        self.draw_board()

    def on_board_release(self, event: tk.Event) -> None:
        start_cell = self.pressed_cell
        end_cell = self.cell_from_event(event)
        self.pressed_cell = None
        if start_cell and start_cell == end_cell:
            self.apply_cell_action(*start_cell)
        self.draw_board()

    def on_board_leave(self, _event: tk.Event) -> None:
        if self.pressed_cell is not None:
            self.pressed_cell = None
            self.draw_board()

    def is_fixed(self, r: int, c: int) -> bool:
        return self.puzzle[r][c] != 0

    def value_at(self, r: int, c: int) -> int:
        return self.puzzle[r][c] or self.player[r][c]

    def push_history(self) -> None:
        self.history.append(Snapshot(deep_copy_grid(self.player), deep_copy_notes(self.notes)))
        if len(self.history) > 200:
            self.history.pop(0)

    def apply_cell_action(self, r: int, c: int) -> None:
        if self.is_fixed(r, c):
            self.status_var.set("题目数字不可修改")
            return

        changed = False
        if self.tool == "clear":
            if self.player[r][c]:
                self.push_history()
                self.player[r][c] = 0
                changed = True
                self.status_var.set("已清除结果数字，辅助数字重新显示")
            elif self.notes[r][c]:
                self.push_history()
                self.notes[r][c].clear()
                changed = True
                self.status_var.set("已清除该格辅助数字")
            else:
                self.status_var.set("这个格子已经是空的")
        elif self.mode == "result":
            self.push_history()
            if self.player[r][c] == self.selected_digit:
                self.player[r][c] = 0
                self.status_var.set(f"已清除结果数字 {self.selected_digit}")
            else:
                self.player[r][c] = self.selected_digit
                self.status_var.set(f"已填入结果数字 {self.selected_digit}")
            changed = True
        else:
            if self.player[r][c]:
                self.status_var.set("该格已有结果数字，辅助数字被冻结")
                return
            self.push_history()
            if self.selected_digit in self.notes[r][c]:
                self.notes[r][c].remove(self.selected_digit)
                self.status_var.set(f"已移除辅助数字 {self.selected_digit}")
            else:
                self.notes[r][c].add(self.selected_digit)
                self.status_var.set(f"已添加辅助数字 {self.selected_digit}")
            changed = True

        if changed:
            self.checked_wrong_cells.clear()
            self.update_control_styles()
            self.check_completion()

    def conflict_cells(self) -> set[tuple[int, int]]:
        conflicts: set[tuple[int, int]] = set()

        def scan(cells: list[tuple[int, int]]) -> None:
            seen: dict[int, list[tuple[int, int]]] = {}
            for r, c in cells:
                value = self.value_at(r, c)
                if value:
                    seen.setdefault(value, []).append((r, c))
            for group in seen.values():
                if len(group) > 1:
                    conflicts.update(group)

        for r in range(9):
            scan([(r, c) for c in range(9)])
        for c in range(9):
            scan([(r, c) for r in range(9)])
        for br in range(0, 9, 3):
            for bc in range(0, 9, 3):
                scan([(r, c) for r in range(br, br + 3) for c in range(bc, bc + 3)])
        return conflicts

    def draw_board(self) -> None:
        if not self.board_canvas:
            return
        canvas = self.board_canvas
        canvas.delete("all")
        cell = BOARD_SIZE / 9
        conflicts = self.conflict_cells()
        now = time.time()
        if self.check_flash_until and now > self.check_flash_until:
            self.checked_wrong_cells.clear()
            self.check_flash_until = 0

        for r in range(9):
            for c in range(9):
                x1 = c * cell
                y1 = r * cell
                x2 = x1 + cell
                y2 = y1 + cell
                fill = "#FFFFFF"

                value = self.value_at(r, c)
                if self.tool == "digit" and value == self.selected_digit:
                    fill = THEME["cyan_soft"]

                if (r, c) in conflicts or (r, c) in self.checked_wrong_cells:
                    fill = THEME["danger_bg"]

                if self.pressed_cell:
                    pr, pc = self.pressed_cell
                    same_box = r // 3 == pr // 3 and c // 3 == pc // 3
                    if same_box:
                        fill = THEME["press_box"]
                    if r == pr or c == pc:
                        fill = THEME["press_related"]
                    if (r, c) == self.pressed_cell:
                        fill = THEME["press"]

                canvas.create_rectangle(x1, y1, x2, y2, fill=fill, outline="")

        for r in range(9):
            for c in range(9):
                value = self.value_at(r, c)
                x = c * cell + cell / 2
                y = r * cell + cell / 2
                conflict = (r, c) in conflicts or (r, c) in self.checked_wrong_cells

                if value:
                    fill = THEME["danger"] if conflict else (THEME["fixed"] if self.is_fixed(r, c) else THEME["result"])
                    canvas.create_text(
                        x,
                        y,
                        text=str(value),
                        fill=fill,
                        font=("Arial", int(cell * 0.62), "bold" if self.is_fixed(r, c) else "normal"),
                    )
                elif self.notes[r][c]:
                    self.draw_notes(canvas, r, c, cell)

        for i in range(10):
            width = 4 if i % 3 == 0 else 1
            fill = THEME["heavy_line"] if i % 3 == 0 else THEME["line"]
            pos = i * cell
            canvas.create_line(pos, 0, pos, BOARD_SIZE, fill=fill, width=width)
            canvas.create_line(0, pos, BOARD_SIZE, pos, fill=fill, width=width)

    def draw_notes(self, canvas: tk.Canvas, r: int, c: int, cell: float) -> None:
        base_x = c * cell
        base_y = r * cell
        sub = cell / 3
        for n in sorted(self.notes[r][c]):
            nr = (n - 1) // 3
            nc = (n - 1) % 3
            x = base_x + nc * sub + sub / 2
            y = base_y + nr * sub + sub / 2
            fill = THEME["cyan_dark"] if n == self.selected_digit and self.mode == "assist" else THEME["note"]
            canvas.create_text(
                x,
                y,
                text=str(n),
                fill=fill,
                font=("Arial", max(8, int(cell * 0.19)), "bold"),
            )

    def candidates_for(self, r: int, c: int) -> set[int]:
        if self.value_at(r, c):
            return set()
        used = set()
        used.update(self.value_at(r, col) for col in range(9))
        used.update(self.value_at(row, c) for row in range(9))
        br = (r // 3) * 3
        bc = (c // 3) * 3
        used.update(self.value_at(row, col) for row in range(br, br + 3) for col in range(bc, bc + 3))
        return {n for n in range(1, 10) if n not in used}

    def open_assist_menu(self) -> None:
        menu = tk.Toplevel(self.root)
        menu.title("辅助")
        menu.configure(bg="#FFFFFF")
        menu.resizable(False, False)
        menu.transient(self.root)
        menu.grab_set()
        x = self.root.winfo_rootx() + 82
        y = self.root.winfo_rooty() + 220
        menu.geometry(f"400x360+{x}+{y}")

        tk.Label(
            menu,
            text="辅助操作",
            bg="#FFFFFF",
            fg=THEME["cyan_dark"],
            font=("Microsoft YaHei UI", 18, "bold"),
        ).pack(pady=(20, 12))

        actions = [
            ("填充全部辅助数字", self.fill_all_notes),
            ("清空全部辅助数字", self.clear_all_notes),
            ("清空结果数字", self.clear_player_results),
            ("全部清空", self.clear_all_player_content),
        ]
        for label, command in actions:
            tk.Button(
                menu,
                text=label,
                command=lambda cmd=command, win=menu: self.run_menu_action(cmd, win),
                bd=0,
                bg="#EAF7F8",
                fg="#183C43",
                activebackground=THEME["cyan_soft"],
                pady=10,
                font=("Microsoft YaHei UI", 12, "bold"),
                cursor="hand2",
            ).pack(fill="x", padx=32, pady=6)

        tk.Button(
            menu,
            text="关闭",
            command=menu.destroy,
            bd=0,
            bg="#F0F1F2",
            fg="#29393C",
            activebackground="#E2E5E7",
            pady=8,
            font=("Microsoft YaHei UI", 11),
            cursor="hand2",
        ).pack(fill="x", padx=32, pady=(12, 0))

    def run_menu_action(self, command, window: tk.Toplevel) -> None:
        window.destroy()
        command()

    def fill_all_notes(self) -> None:
        self.push_history()
        changed = False
        for r in range(9):
            for c in range(9):
                if self.is_fixed(r, c) or self.player[r][c]:
                    continue
                new_notes = self.candidates_for(r, c)
                if self.notes[r][c] != new_notes:
                    self.notes[r][c] = new_notes
                    changed = True
        if changed:
            self.status_var.set("已填充全部辅助数字")
        else:
            self.history.pop()
            self.status_var.set("辅助数字无需更新")
        self.draw_board()

    def clear_all_notes(self) -> None:
        if not any(self.notes[r][c] for r in range(9) for c in range(9)):
            self.status_var.set("没有辅助数字可清空")
            return
        self.push_history()
        self.notes = [[set() for _ in range(9)] for _ in range(9)]
        self.status_var.set("已清空全部辅助数字")
        self.draw_board()

    def clear_player_results(self) -> None:
        if not any(self.player[r][c] for r in range(9) for c in range(9)):
            self.status_var.set("没有玩家结果数字可清空")
            return
        self.push_history()
        self.player = [[0] * 9 for _ in range(9)]
        self.status_var.set("已清空结果数字，辅助数字保留")
        self.update_control_styles()
        self.draw_board()

    def clear_all_player_content(self) -> None:
        if not messagebox.askyesno("全部清空", "清空玩家填写的结果数字和辅助数字？题目数字会保留。"):
            return
        self.push_history()
        self.player = [[0] * 9 for _ in range(9)]
        self.notes = [[set() for _ in range(9)] for _ in range(9)]
        self.status_var.set("已全部清空，题目数字保留")
        self.update_control_styles()
        self.draw_board()

    def undo(self) -> None:
        if not self.history:
            self.status_var.set("没有可撤销的操作")
            return
        snapshot = self.history.pop()
        self.player = deep_copy_grid(snapshot.player)
        self.notes = deep_copy_notes(snapshot.notes)
        self.checked_wrong_cells.clear()
        self.status_var.set("已撤销一步")
        self.update_control_styles()
        self.draw_board()

    def give_hint(self) -> None:
        candidates = [
            (r, c)
            for r in range(9)
            for c in range(9)
            if not self.is_fixed(r, c) and self.value_at(r, c) != self.solution[r][c]
        ]
        if not candidates:
            self.status_var.set("已经没有需要提示的格子")
            return
        r, c = random.choice(candidates)
        self.push_history()
        self.player[r][c] = self.solution[r][c]
        self.status_var.set(f"提示：第 {r + 1} 行第 {c + 1} 列是 {self.solution[r][c]}")
        self.checked_wrong_cells.clear()
        self.update_control_styles()
        self.draw_board()
        self.check_completion()

    def check_board(self) -> None:
        wrong = set()
        for r in range(9):
            for c in range(9):
                if self.player[r][c] and self.player[r][c] != self.solution[r][c]:
                    wrong.add((r, c))
        wrong.update(self.conflict_cells())
        self.checked_wrong_cells = wrong
        self.check_flash_until = time.time() + 2.5
        if wrong:
            self.status_var.set(f"检查：发现 {len(wrong)} 个需要留意的格子")
        else:
            self.status_var.set("检查：目前没有发现错误")
        self.draw_board()
        self.root.after(2600, self.draw_board)

    def check_completion(self) -> None:
        if self.completed:
            return
        for r in range(9):
            for c in range(9):
                if self.value_at(r, c) != self.solution[r][c]:
                    self.draw_board()
                    return
        self.completed = True
        self.draw_board()
        elapsed = self.timer_var.get()
        messagebox.showinfo("完成演出", f"恭喜通关！\n难度：{self.difficulty}\n用时：{elapsed}")

    def on_close(self) -> None:
        if self.timer_job:
            self.root.after_cancel(self.timer_job)
        self.root.destroy()


if __name__ == "__main__":
    VsingerSudokuApp().run()
