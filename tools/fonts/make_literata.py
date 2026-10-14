"""Make the app's Literata cuts from the variable font of the Google Fonts repository.

Usage: make_literata.py "Literata[opsz,wght].ttf" OUT_DIR
Needs fonttools. Writes kb_literata_regular.ttf and kb_literata_bold.ttf: static instances at
optical size 14, subset to Basic Latin, Latin-1, Latin Extended-A and common punctuation.
"""
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

UNICODES = list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x2010, 0x2030)) + [0x2039, 0x203A, 0x20AC, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193]


def main(source, out_dir):
    for name, weight in (("regular", 400), ("bold", 700)):
        font = instancer.instantiateVariableFont(TTFont(source), {"wght": weight, "opsz": 14})
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=UNICODES)
        subsetter.subset(font)
        font.save(os.path.join(out_dir, f"kb_literata_{name}.ttf"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
