"""Shared helpers for inspecting and cleaning the book list.

The same normalisation rules will later be ported to the app's importer, so
every rule here is deterministic and documented.
"""
import re

_TR_LOWER = str.maketrans({"I": "ı", "İ": "i"})
_TR_UPPER = str.maketrans({"i": "İ", "ı": "I"})
_FOLD = str.maketrans({"ı": "i", "â": "a", "î": "i", "û": "u", "ê": "e", "ô": "o"})

MONTHS_TR = ("OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN", "TEMMUZ",
             "AĞUSTOS", "EYLÜL", "EKİM", "KASIM", "ARALIK")

SIGNED_WORD = "İMZALI"
SIGNED_NOTE = "İmzalı"
# A genre tag that shares the last title segment with the signed marker stays
# in the title ("OYUN - İMZALI ŞİİR" -> "OYUN - ŞİİR").
GENRE_TAGS = {"ŞİİR", "ŞİİRLER"}

_ROMAN = re.compile(r"^(?=[ivxlc])(c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})$")


def squeeze(text):
    """Trim and collapse every whitespace run (incl. NBSP) to one space."""
    return re.sub(r"\s+", " ", text.replace(" ", " ")).strip()


def tr_lower(text):
    """Lowercase with Turkish rules: I -> ı, İ -> i."""
    return text.translate(_TR_LOWER).lower()


def tr_upper(text):
    """Uppercase with Turkish rules: i -> İ, ı -> I."""
    return text.translate(_TR_UPPER).upper()


def strict_key(text):
    """Key for 'the same text': only spacing and letter case are ignored."""
    return tr_lower(squeeze(text))


def fold(text):
    """Loose key: also ignores dotted/dotless i, circumflex and punctuation."""
    folded = tr_lower(squeeze(text)).translate(_FOLD)
    folded = re.sub(r"[^\w\s]", " ", folded)
    return squeeze(folded)


def tr_title_case(text):
    """'BÜLENT ECEVİT'E' -> 'Bülent Ecevit'e' (suffix after apostrophe stays lower)."""
    words = []
    for word in squeeze(text).split(" "):
        head, apostrophe, tail = re.match(r"^([^'’]*)(['’]?)(.*)$", word).groups()
        head = tr_upper(head[:1]) + tr_lower(head[1:])
        words.append(head + apostrophe + tr_lower(tail))
    return " ".join(words)


def date_to_text(value, number_format):
    """Turn a date cell into the text its owner meant: 'NİSAN 1953' or '15 MART 1998'."""
    month = MONTHS_TR[value.month - 1]
    if "d" in (number_format or "").lower():
        return f"{value.day} {month} {value.year}"
    return f"{month} {value.year}"


def _has_signed_word(upper_text):
    return re.search(rf"(?<!\w){SIGNED_WORD}(?!\w)", upper_text) is not None


def _split_segment(upper_segment):
    """Return (kept_tail, note) for an upper-cased segment holding the signed word."""
    rest = squeeze(re.sub(rf"(?<!\w){SIGNED_WORD}(?!\w)", " ", upper_segment))
    if not rest:
        return "", SIGNED_NOTE
    if rest in GENRE_TAGS:
        return rest, SIGNED_NOTE
    # A dedication such as "BÜLENT ECEVİT'E İMZALI": keep it whole, readable.
    return "", f"{tr_title_case(rest)} imzalı"


def extract_signed(text):
    """Move a 'signed copy' marker out of a title or author cell.

    Returns (clean_text, note). note is None when the cell has no marker.
    Works on squeezed text; 'İMZASIZ' and other words are left alone.
    """
    text = squeeze(text)
    upper = tr_upper(text)
    if len(upper) != len(text) or not _has_signed_word(upper):
        return text, None

    # 1) trailing parenthesised group: "TITLE (İMZALI)", "TITLE (X'E İMZALI)"
    match = re.match(r"^(.*?)\s*\(([^()]*)\)$", upper)
    if match and _has_signed_word(match.group(2)):
        tail, note = _split_segment(match.group(2))
        head = text[:match.end(1)]
        return (f"{head} ({tail})" if tail else head), note

    # 2) last dash-separated segment: "TITLE - İMZALI", "TITLE - ŞİİR İMZALI"
    separators = list(re.finditer(r"\s+[-–]\s*|\s*[-–]\s+", upper))
    if separators:
        last = separators[-1]
        if _has_signed_word(upper[last.end():]):
            tail, note = _split_segment(upper[last.end():])
            head = text[:last.start()]
            return (f"{head}{text[last.start():last.end()]}{tail}" if tail else head), note

    # 3) the word sits inside the text without its own segment
    match = re.search(rf"(?<!\w){SIGNED_WORD}(?!\w)", upper)
    cleaned = squeeze(text[:match.start()] + " " + text[match.end():])
    cleaned = re.sub(r"(\s[-–])+(?=\s[-–]\s)", "", cleaned)
    cleaned = re.sub(r"^\s*[-–]\s+|\s+[-–]\s*$|\(\s*\)", "", cleaned)
    return squeeze(cleaned), SIGNED_NOTE


def is_volume_token(token):
    """Numbers and Roman numerals: tokens that tell volumes/issues apart."""
    return token.isdigit() or bool(_ROMAN.match(token))


def differs_only_by_number(title_a, title_b):
    """True when two titles are the same text with different numbers (vol. 2 / vol. 3)."""
    tokens_a, tokens_b = fold(title_a).split(), fold(title_b).split()
    words_a = [t for t in tokens_a if not is_volume_token(t)]
    words_b = [t for t in tokens_b if not is_volume_token(t)]
    numbers_a = [t for t in tokens_a if is_volume_token(t)]
    numbers_b = [t for t in tokens_b if is_volume_token(t)]
    return words_a == words_b and numbers_a != numbers_b
