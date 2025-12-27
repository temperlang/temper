#!/usr/bin/env python3

"""
Generates integer sets to classify unicode codepoints. These integer sets identify the start
and continuation characters in identifiers. These are presently used in:
* be-py/src/commonMain/kotlin/lang/temper/be/py/PyIdentifierGrammar.kt
"""

from collections import defaultdict
from urllib.request import urlopen


def scan(data, tgt):
    data, *junk = line.split("#")
    data = data.strip()
    if not data:
        return
    rng, sec = data.split(";")
    low, *hi = rng.split("..")
    hi = hi[0] if hi else low
    tgt[sec.strip()].update(range(int(low, 16), int(hi, 16) + 1))


def show(tgt, sec, name):
    print(
        f"val {name}: IntRangeSet = IntRangeSet.new(\n"
        "    sortedUniqEvenLengthArray = intArrayOf("
    )
    i = iter(sorted(tgt[sec]))
    low = hi = next(i)
    for val in i:
        if val > hi + 1:
            print(f"        {low}, {hi + 1},")
            low = hi = val
        else:
            hi = val
    print(f"        {low}, {hi + 1}\n    )\n)")


dct = defaultdict(set)
with urlopen(
    "https://www.unicode.org/Public/13.0.0/ucd/DerivedCoreProperties.txt"
) as fh:
    for line in fh.read().decode("utf-8").split("\n"):
        scan(line, dct)

show(dct, "ID_Start", "idStart")
show(dct, "ID_Continue", "idContinue")
