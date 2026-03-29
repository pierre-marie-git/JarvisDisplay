"""
Matrix text layout for the 22-col × 25-row flip-dot display.
"""
import unicodedata

MATRIX_COLS = 22
MATRIX_ROWS = 25
MATRIX_SEPARATOR = " | "


# ── Low-level char width (accounts for emoji = 2 cells) ──────────────────────

def _char_width(char: str) -> int:
    """
    Display width in terminal/frame cells.
    - Multi-code-point grapheme cluster (emoji) = 2 cells
    - CJK / full-width = 2 cells
    - Narrow / neutral = 1 cell
    """
    try:
        if len(char) > 1:
            return 2  # grapheme cluster (emoji with variation selector)
        code = ord(char)
        # Specific ranges that East Asian Width says 'N' but render as 2 cells
        if (0x1F300 <= code <= 0x1F9FF or   # Extended Pictographs + Emoji
            0x2600 <= code <= 0x26FF):       # Miscellaneous Symbols (weather emoji)
            return 2
        w = unicodedata.east_asian_width(char)
        return 2 if w in ('W', 'F') else 1
    except Exception:
        return 1


# ── Core text layout ──────────────────────────────────────────────────────────

def _visual_len(text: str) -> int:
    """Total visual width of a string."""
    return sum(_char_width(c) for c in text)


def _apply_offset(text: str, offset: int, cols: int = MATRIX_COLS) -> str:
    """
    Render text into exactly cols cells, accounting for visual width.
    offset < 0 = left-align, offset > 0 = right-align, offset = 0 = center.
    """
    # Strip trailing whitespace for accurate visual measurement
    content = text.rstrip()
    if not content:
        return " " * cols

    # If the string's visual width already fills the column exactly,
    # preserve it as-is — avoids re-centering pre-formatted strings.
    if offset == 0 and _visual_len(text) == cols:
        return text

    visual = _visual_len(content)
    if visual >= cols:
        # Content too wide — truncate
        result = ""
        w = 0
        for c in content:
            cw = _char_width(c)
            if w + cw > cols:
                break
            result += c
            w += cw
        return result

    pad = cols - visual
    if offset == 0:
        # Center
        left = pad // 2
    elif offset < 0:
        # Left-aligned: pad applied to the right
        left = 0
    else:
        # Right-aligned: pad applied to the left
        left = pad

    right = pad - left
    return " " * left + content + " " * right


def text_to_matrix(text: str) -> list:
    """
    Convert a single text string into a 25×22 matrix.
    Each row is a fixed-width string.
    """
    raw_rows = text.split("\n")
    return _raw_rows_to_matrix(raw_rows)


def text_lines_to_matrix(lines: list) -> list:
    """
    Convert a list of {text, offset} dicts into a 25×22 matrix.
    Lines are vertically centered in the 25-row grid.
    Empty-text lines are treated as blank rows.
    """
    # Build the rows array from the structured lines
    raw_rows = []
    for ln in lines:
        if isinstance(ln, dict):
            raw_rows.append((ln.get("text", ""), ln.get("offset", 0)))
        else:
            raw_rows.append((str(ln), 0))

    # Convert each line to a full-width row string
    display_rows = []
    for text, offset in raw_rows:
        display_rows.append(_apply_offset(text, offset, MATRIX_COLS))

    return _raw_rows_to_matrix(display_rows)


def _raw_rows_to_matrix(raw_rows: list) -> list:
    """
    Put a list of strings into a 25-row × 22-col grid.
    Groups of consecutive non-blank rows (city blocks etc.) are kept together.
    Total blank rows are distributed as equal gaps between all groups, centered.
    """
    if not raw_rows:
        return [" " * MATRIX_COLS] * MATRIX_ROWS

    def fmt(r):
        return r if _visual_len(r) == MATRIX_COLS else _apply_offset(r, 0, MATRIX_COLS)

    formatted = [fmt(r) for r in raw_rows]

    # Group consecutive non-blank rows
    groups = []
    current = []
    for r in formatted:
        if r.strip():
            current.append(r)
        else:
            if current:
                groups.append(current)
                current = []
            # Blank row: if current group is non-empty, close it
            # This means blank rows act as group separators (intra-group spacing becomes inter-group)
            if groups and groups[-1]:
                pass  # group already closed above
    if current:
        groups.append(current)

    if not groups:
        return [" " * MATRIX_COLS] * MATRIX_ROWS

    total_content = sum(len(g) for g in groups)
    available = MATRIX_ROWS - total_content
    if available < 0:
        available = 0

    result = []

    # Single group: spread rows across full height with equal inter-row gaps
    if len(groups) == 1:
        n = len(groups[0])
        if n <= 1:
            top = available // 2
            bottom = available - top
            result.extend([" " * MATRIX_COLS] * top)
            result.extend(groups[0])
            result.extend([" " * MATRIX_COLS] * bottom)
        else:
            base_gap = max(1, available // (n + 1))
            extra = available - (n + 1) * base_gap
            top = base_gap + extra // 2
            bottom = base_gap + (extra - extra // 2)
            result.extend([" " * MATRIX_COLS] * top)
            for i, row in enumerate(groups[0]):
                result.append(row)
                if i < n - 1:
                    result.extend([" " * MATRIX_COLS] * base_gap)
            result.extend([" " * MATRIX_COLS] * bottom)
        return result[:MATRIX_ROWS]

    # Multiple groups: distribute equal gaps between groups, centered
    n_gaps = len(groups) + 1
    gap = max(1, available // n_gaps)
    extra = available - gap * n_gaps
    top = gap + extra
    inter = gap
    bottom = gap

    result.extend([" " * MATRIX_COLS] * top)

    for gi, group in enumerate(groups):
        result.extend(group)
        if gi < len(groups) - 1:
            result.extend([" " * MATRIX_COLS] * inter)

    result.extend([" " * MATRIX_COLS] * bottom)

    while len(result) < MATRIX_ROWS:
        result.append(" " * MATRIX_COLS)
    return result[:MATRIX_ROWS]


# ── Top-level matrix builder ──────────────────────────────────────────────────

def matrices_from_messages(messages: list, source_matrices: list) -> list:
    """
    Convert messages + source matrices into a list of MatrixResponse dicts.
    Each message uses lines_json if available, otherwise text_to_matrix.
    """
    import json as _json

    result = []
    for msg in messages:
        lines_json = msg.get("lines_json")
        if lines_json:
            try:
                lines = _json.loads(lines_json)
                rows = text_lines_to_matrix(lines)
            except Exception:
                rows = text_to_matrix(msg.get("text", ""))
        else:
            rows = text_to_matrix(msg.get("text", ""))
        result.append({
            "id": f"msg_{msg.get('id', 0)}",
            "rows": rows,
            "duration": 8,
        })
    for sm in source_matrices:
        result.append({
            "id": sm.get("id", "source"),
            "rows": sm.get("rows", [" " * MATRIX_COLS] * MATRIX_ROWS),
            "duration": sm.get("duration", 6),
        })
    return result
