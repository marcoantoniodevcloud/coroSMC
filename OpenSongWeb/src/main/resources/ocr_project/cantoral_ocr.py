#!/usr/bin/env python
"""
Cantoral ChordPro OCR - experimental local prototype
Extracts text and geometry from a photo/image or PDF, identifies likely chord
labels, associates chord rows with lyric rows below them, and writes a
reviewable ChordPro draft plus an annotated JSON file.

This is a draft generator, not a guarantee of musically correct alignment.
All processing is local once dependencies/models are installed.
"""
from __future__ import annotations
import argparse
import json
import os
import re
import statistics
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFont

CHORD_RE = re.compile(
    r"^(?:[A-G](?:#|b)?(?:m|maj|min|dim|aug|sus|add)?"
    r"(?:\d{0,2})?(?:sus\d+)?(?:add\d+)?(?:/[A-G](?:#|b)?)?)$"
)

def is_chord(s: str) -> bool:
    s = s.strip().replace("♯", "#").replace("♭", "b")
    return bool(CHORD_RE.fullmatch(s))

def split_chord_sequence(text: str):
    """Split a detector result that contains several adjacent chord labels."""
    memo = {}
    def parse(start):
        if start == len(text):
            return []
        if start in memo:
            return memo[start]
        for end in range(len(text), start, -1):
            candidate = text[start:end]
            if is_chord(candidate):
                rest = parse(end)
                if rest is not None:
                    memo[start] = [candidate] + rest
                    return memo[start]
        memo[start] = None
        return None
    return parse(0)

def box_bounds(poly):
    xs = [float(p[0]) for p in poly]
    ys = [float(p[1]) for p in poly]
    return min(xs), min(ys), max(xs), max(ys)

def split_ocr_text(text: str, x1: float, y1: float, x2: float, y2: float, gray_image):
    """Recover word boundaries from whitespace in a detected text-line box.

    RapidOCR's detector often returns a whole lyric line as one box, and its
    recognizer can omit spaces even when they are visible in the image. Find
    vertical white gaps in the original crop and map them back to character
    boundaries. This works for both lyric lines and chord labels.
    """
    compact = "".join(str(text).split())
    if not compact:
        return []
    # Do not split legitimate chord labels such as Dm or C7 at the natural
    # gap between their glyphs. Concatenated groups (for example GmA7Dm) are
    # still processed because the whole string is not a chord label.
    if is_chord(compact):
        return [{"text": compact, "x1": x1, "y1": y1, "x2": x2, "y2": y2}]
    recognized_chords = list(re.finditer(r"\S+", str(text)))
    if len(recognized_chords) > 1 and all(is_chord(match.group(0)) for match in recognized_chords):
        text_length = max(1, len(str(text)))
        width = x2 - x1
        return [
            {
                "text": match.group(0),
                "x1": x1 + (match.start() / text_length) * width,
                "y1": y1,
                "x2": x1 + (match.end() / text_length) * width,
                "y2": y2,
            }
            for match in recognized_chords
        ]
    chord_sequence = split_chord_sequence(compact)
    if chord_sequence and len(chord_sequence) > 1:
        pieces = []
        offset = 0
        for chord in chord_sequence:
            word_x1 = x1 + (offset / len(compact)) * (x2 - x1)
            offset += len(chord)
            word_x2 = x1 + (offset / len(compact)) * (x2 - x1)
            pieces.append({"text": chord, "x1": word_x1, "y1": y1, "x2": word_x2, "y2": y2})
        return pieces

    # When the recognizer already found word spaces, preserve them. Re-cutting
    # those lines from pixel gaps was splitting clear words into fragments
    # (for example, "Sacramento" into "Sacra" + "mento"). Approximate each
    # recognized word's position proportionally inside the detector line box.
    recognized_words = list(re.finditer(r"\S+", str(text)))
    if len(recognized_words) > 1:
        text_length = max(1, len(str(text)))
        width = x2 - x1
        return [
            {
                "text": match.group(0),
                "x1": x1 + (match.start() / text_length) * width,
                "y1": y1,
                "x2": x1 + (match.end() / text_length) * width,
                "y2": y2,
            }
            for match in recognized_words
        ]

    boundaries = set()
    # Keep any separators already recognized by OCR.
    char_count = 0
    for char in str(text):
        if char.isspace():
            if 0 < char_count < len(compact):
                boundaries.add(char_count)
        else:
            char_count += 1

    image_h, image_w = gray_image.shape[:2]
    left = max(0, min(image_w, int(round(x1))))
    right = max(left, min(image_w, int(round(x2))))
    top = max(0, min(image_h, int(round(y1))))
    bottom = max(top, min(image_h, int(round(y2))))
    crop = gray_image[top:bottom, left:right]
    gaps = []
    if crop.size and crop.shape[1] > 1:
        # Ignore anti-aliased background noise; only count clearly dark ink.
        ink_by_column = np.any(crop < 210, axis=0)
        min_gap = max(4, int(round((bottom - top) * 0.20)))
        gap_start = None
        for col in range(len(ink_by_column) + 1):
            is_blank = col < len(ink_by_column) and not ink_by_column[col]
            if is_blank and gap_start is None:
                gap_start = col
            elif not is_blank and gap_start is not None:
                gap_end = col
                if gap_end - gap_start >= min_gap:
                    gaps.append((gap_start, gap_end))
                gap_start = None

    # Map each visible word gap to a character boundary using approximate
    # glyph widths, rather than assuming every character occupies equal space.
    gap_by_boundary = {}
    if gaps:
        font_size = max(8, int(round((bottom - top) * 0.92)))
        font = None
        for font_name in ("arial.ttf", r"C:\Windows\Fonts\arial.ttf", "DejaVuSans.ttf"):
            try:
                font = ImageFont.truetype(font_name, font_size)
                break
            except OSError:
                pass
        if font is None:
            font = ImageFont.load_default()
        text_width = max(1.0, float(font.getlength(compact)))
        gap_width_total = sum(end - start for start, end in gaps)
        scale = max(0.25, (right - left - gap_width_total) / text_width)
        previous_boundary = 0
        cumulative_gap_width = 0
        for gap_index, (gap_start, gap_end) in enumerate(gaps):
            # The gap's position is measured after all earlier gaps and half
            # of this gap. Search character boundaries using rendered widths.
            target_glyph_advance = (gap_start - cumulative_gap_width) / scale
            remaining = len(gaps) - gap_index - 1
            low = previous_boundary + 1
            high = len(compact) - remaining - 1
            if low > high:
                continue
            boundary = min(
                range(low, high + 1),
                key=lambda k: abs(float(font.getlength(compact[:k])) - target_glyph_advance)
            )
            boundaries.add(boundary)
            gap_by_boundary[boundary] = (gap_start, gap_end)
            previous_boundary = boundary
            cumulative_gap_width += gap_end - gap_start

    if not boundaries:
        return [{"text": compact, "x1": x1, "y1": y1, "x2": x2, "y2": y2}]

    # Split the line into words. Approximate each word's box by its character
    # span; the original OCR line box remains the source of the geometry.
    pieces = []
    start = 0
    for end in sorted(boundaries) + [len(compact)]:
        if end <= start:
            continue
        word = compact[start:end]
        previous_gap = gap_by_boundary.get(start)
        next_gap = gap_by_boundary.get(end)
        local_left = previous_gap[1] if previous_gap else 0
        local_right = next_gap[0] if next_gap else (right - left)
        if crop.size and local_right > local_left:
            word_ink = np.flatnonzero(np.any(crop[:, local_left:local_right] < 210, axis=0))
        else:
            word_ink = np.array([], dtype=int)
        if word_ink.size:
            word_x1 = left + local_left + int(word_ink[0])
            word_x2 = left + local_left + int(word_ink[-1]) + 1
        else:
            word_x1 = x1 + (start / len(compact)) * (x2 - x1)
            word_x2 = x1 + (end / len(compact)) * (x2 - x1)
        pieces.append({"text": word, "x1": word_x1, "y1": y1, "x2": word_x2, "y2": y2})
        start = end
    # Preserve the Spanish initial digraph "Ll" if a wide glyph gap split it.
    merged = []
    for piece in pieces:
        if merged and merged[-1]["text"] == "L" and piece["text"].startswith("l"):
            merged[-1]["text"] += piece["text"]
            merged[-1]["x2"] = piece["x2"]
        else:
            merged.append(piece)
    return merged

def load_pages(path: Path, dpi: int):
    suffix = path.suffix.lower()
    if suffix == ".pdf":
        try:
            import fitz
        except ImportError:
            raise RuntimeError("Para PDF instala PyMuPDF: pip install pymupdf")
        doc = fitz.open(path)
        pages = []
        for i, page in enumerate(doc):
            pix = page.get_pixmap(matrix=fitz.Matrix(dpi / 72, dpi / 72), alpha=False)
            out = path.parent / f".{path.stem}_page_{i+1}.png"
            pix.save(out)
            pages.append((out, i + 1, True))
        return pages
    return [(path, 1, False)]

def ocr_image(image_path: Path):
    try:
        from rapidocr_onnxruntime import RapidOCR
    except ImportError:
        raise RuntimeError(
            "Falta RapidOCR. Instala dependencias con: pip install -r requirements.txt"
        )
    model_dir = Path(os.environ.get(
        "OPENSONG_OCR_MODEL_DIR",
        str(Path(__file__).resolve().parent / "models"),
    ))
    latin_model = model_dir / "latin_PP-OCRv3_rec_mobile.onnx"
    latin_dictionary = model_dir / "latin_dict.txt"
    if latin_model.is_file() and latin_dictionary.is_file():
        engine = RapidOCR(
            rec_model_path=str(latin_model),
            rec_keys_path=str(latin_dictionary),
        )
    else:
        engine = RapidOCR()
    result, _ = engine(str(image_path))
    with Image.open(image_path) as source_image:
        gray_image = np.asarray(source_image.convert("L"))
    items = []
    for row in result or []:
        # RapidOCR returns [polygon, text, confidence]
        poly, text, score = row
        x1, y1, x2, y2 = box_bounds(poly)
        text = str(text).strip()
        if text:
            for word in split_ocr_text(text, x1, y1, x2, y2, gray_image):
                items.append({
                    **word,
                    "confidence": float(score),
                    "cx": (word["x1"] + word["x2"]) / 2,
                    "cy": (word["y1"] + word["y2"]) / 2
                })
    return items

def group_rows(items):
    """Group OCR tokens into rows by vertical center."""
    if not items:
        return []
    items = sorted(items, key=lambda a: (a["cy"], a["x1"]))
    rows = []
    for item in items:
        h = max(8, item["y2"] - item["y1"])
        best = None
        best_dist = 1e9
        for row in rows[-5:]:
            avg_y = sum(x["cy"] for x in row) / len(row)
            avg_h = sum(max(8, x["y2"]-x["y1"]) for x in row) / len(row)
            dist = abs(item["cy"] - avg_y)
            if dist < max(10, 0.55 * max(h, avg_h)) and dist < best_dist:
                best, best_dist = row, dist
        if best is None:
            rows.append([item])
        else:
            best.append(item)
    for row in rows:
        row.sort(key=lambda a: a["x1"])
    return sorted(rows, key=lambda r: min(x["cy"] for x in r))

def row_text(row):
    return " ".join(x["text"] for x in row).strip()

def tokens_as_chords(row):
    # OCR sometimes attaches punctuation; normalize common chord glyphs.
    out = []
    for token in row:
        t = token["text"].strip().replace("♯", "#").replace("♭", "b")
        t = t.strip(".,;:|")
        if is_chord(t):
            out.append({**token, "text": t})
    return out

def word_spans(row):
    # OCR boxes are words/tokens; preserve their actual bounding-box positions.
    return [x for x in row if x["text"].strip()]

def estimate_character_x(word, char_index, text):
    """Interpolate a character x-position inside an OCR token's bounding box."""
    text = text or word["text"]
    n = max(1, len(text))
    frac = min(1.0, max(0.0, char_index / n))
    return word["x1"] + frac * (word["x2"] - word["x1"])

def insert_chords_into_lyric(lyric_row, chord_tokens):
    """
    Place each chord at the nearest lyric character by x-coordinate.
    Character position is interpolated within OCR word boxes. Output is
    inline ChordPro: [G]word...; the rendering engine positions it above text.
    """
    words = word_spans(lyric_row)
    if not words:
        return row_text(lyric_row), []
    # Flatten lyric text with word boxes and estimated character positions.
    chars = []
    pieces = []
    for wi, word in enumerate(words):
        txt = word["text"]
        if wi:
            pieces.append(" ")
            chars.append({"char": " ", "x": (words[wi-1]["x2"] + word["x1"]) / 2})
        pieces.append(txt)
        width = max(1, len(txt))
        for ci, ch in enumerate(txt):
            chars.append({"char": ch, "x": estimate_character_x(word, ci + 0.5, txt)})
    lyric = "".join(pieces)
    placements = []
    for chord in chord_tokens:
        if not chars:
            continue
        nearest = min(range(len(chars)), key=lambda i: abs(chars[i]["x"] - chord["cx"]))
        placements.append({
            "chord": chord["text"],
            "char_index": nearest,
            "x_chord": chord["cx"],
            "x_lyric": chars[nearest]["x"],
            "delta_px": abs(chars[nearest]["x"] - chord["cx"])
        })
    # Insert from right to left to preserve offsets.
    result = lyric
    for p in sorted(placements, key=lambda p: p["char_index"], reverse=True):
        idx = p["char_index"]
        # If a chord is placed within a word, ChordPro inline notation is valid
        # and keeps the chord at the selected character/syllable position.
        result = result[:idx] + f'[{p["chord"]}]' + result[idx:]
    first_letter = re.search(r"[^\W\d_]", result, flags=re.UNICODE)
    if first_letter:
        index = first_letter.start()
        result = result[:index] + result[index].upper() + result[index + 1:]
    return result, placements

def process_page(image_path: Path, page_number: int, temporary: bool, output_dir: Path, dpi: int):
    items = ocr_image(image_path)
    rows = group_rows(items)
    # Candidate chord rows have at least one chord-like token and little prose.
    row_info = []
    for i, row in enumerate(rows):
        chords = tokens_as_chords(row)
        words = word_spans(row)
        chord_ratio = len(chords) / max(1, len(words))
        row_info.append({
            "index": i, "row": row, "chords": chords,
            "text": row_text(row), "chord_ratio": chord_ratio,
            "is_chord_row": bool(chords) and (chord_ratio >= 0.45 or len(words) <= 5)
        })

    used_chord_rows = set()
    output_lines = []
    audit = {
        "source_image": str(image_path),
        "page": page_number,
        "ocr_items": items,
        "rows": [],
        "unpaired_chord_rows": [],
        "warnings": [
            "Revisa el resultado: OCR y alineación son estimaciones, no una transcripción musical garantizada.",
            "Comprueba especialmente acordes con OCR ambiguo (Bm/8m, D7/D?), sílabas y líneas partidas."
        ]
    }

    # Guess title from first non-chord row, if available.
    title = ""
    for info in row_info[:5]:
        if info["text"] and not info["is_chord_row"]:
            title = info["text"]
            break
    if title:
        output_lines.append("{title: " + title.replace("{", "").replace("}", "") + "}")
        output_lines.append("")

    lyric_centers = [
        sum(item["cy"] for item in info["row"]) / len(info["row"])
        for info in row_info if info["text"] and not info["is_chord_row"]
    ]
    lyric_gaps = [right - left for left, right in zip(lyric_centers, lyric_centers[1:]) if right > left]
    stanza_gap_threshold = statistics.median(lyric_gaps) * 1.35 if lyric_gaps else float("inf")
    last_lyric_center = None

    def append_lyric_line(line, row):
        nonlocal last_lyric_center
        center = sum(item["cy"] for item in row) / len(row)
        if (last_lyric_center is not None
                and center - last_lyric_center > stanza_gap_threshold
                and output_lines and output_lines[-1] != ""):
            output_lines.append("")
        output_lines.append(line)
        last_lyric_center = center

    i = 0
    while i < len(row_info):
        info = row_info[i]
        if info["is_chord_row"]:
            # Find nearest following lyric row before another chord row, preferably below.
            target = None
            for j in range(i + 1, min(i + 4, len(row_info))):
                if row_info[j]["is_chord_row"]:
                    break
                if row_info[j]["text"]:
                    target = j
                    break
            if target is not None:
                lyric_row = row_info[target]["row"]
                converted, placements = insert_chords_into_lyric(lyric_row, info["chords"])
                append_lyric_line(converted, lyric_row)
                used_chord_rows.add(i)
                used_chord_rows.add(target)
                audit["rows"].append({
                    "chord_row": info["text"],
                    "lyric_row": row_info[target]["text"],
                    "output": converted,
                    "placements": placements,
                    "review": True
                })
                i = target + 1
                continue
            # Keep uncertain chord-only detections in the review JSON, but do
            # not inject editorial comments into the singer-facing ChordPro.
            audit["unpaired_chord_rows"].append({
                "row": info["text"],
                "tokens": [
                    {
                        "text": chord["text"],
                        "x1": chord["x1"], "y1": chord["y1"],
                        "x2": chord["x2"], "y2": chord["y2"],
                        "confidence": chord.get("confidence")
                    }
                    for chord in info["chords"]
                ]
            })
        elif info["text"]:
            append_lyric_line(info["text"], info["row"])
        i += 1

    # Preserve blank space between rows with a larger vertical gap.
    # Current draft keeps lines readable; user can adjust stanza breaks.
    stem = image_path.stem.replace("_page_" + str(page_number), "")
    out_cho = output_dir / f"{stem}_page{page_number}.cho"
    out_json = output_dir / f"{stem}_page{page_number}_review.json"
    out_cho.write_text("\n".join(output_lines).strip() + "\n", encoding="utf-8")
    out_json.write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
    if temporary:
        try:
            image_path.unlink()
        except OSError:
            pass
    return out_cho, out_json, len(items)

def main():
    parser = argparse.ArgumentParser(
        description="Convierte imágenes/PDF de cantos en borradores ChordPro con coordenadas OCR."
    )
    parser.add_argument("input", help="Ruta a imagen o PDF, o carpeta de imágenes/PDF")
    parser.add_argument("-o", "--output", default="chordpro_output", help="Carpeta de salida")
    parser.add_argument("--dpi", type=int, default=240, help="Resolución al rasterizar PDF (default 240)")
    args = parser.parse_args()
    source = Path(args.input).expanduser().resolve()
    output_dir = Path(args.output).expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    if not source.exists():
        print(f"No existe: {source}", file=sys.stderr)
        return 2
    supported = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tif", ".tiff", ".pdf"}
    files = [p for p in sorted(source.iterdir()) if p.suffix.lower() in supported] if source.is_dir() else [source]
    files = [p for p in files if p.suffix.lower() in supported]
    if not files:
        print("No encontré imágenes o PDF compatibles.", file=sys.stderr)
        return 2
    count = 0
    for file in files:
        try:
            pages = load_pages(file, args.dpi)
            for image_path, page_number, temporary in pages:
                cho, review, n = process_page(image_path, page_number, temporary, output_dir, args.dpi)
                print(f"OK: {file.name} -> {cho.name} | OCR tokens: {n} | revisión: {review.name}")
                count += 1
        except Exception as exc:
            print(f"ERROR procesando {file.name}: {exc}", file=sys.stderr)
    print(f"\nTerminado. Páginas procesadas: {count}. Revisa siempre los .cho antes de usarlos.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
