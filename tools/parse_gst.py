"""Parse the CBIC GST-rate PDF text (from pdftext.py) into compact JSON for the app, with validation.
Usage: parse_gst.py <gst.txt> <out.json>
Rates are CGST in units of 0.001% (2.5% -> 2500); total GST = 2 x CGST for intra-state supply."""
import json
import re
import sys

SECTIONS = [  # (header regex, schedule id, CGST in 0.001%)
    (r"^Schedule I – 2\.5%", "I", 2500),
    (r"^Schedule II – 9%", "II", 9000),
    (r"^Schedule III– 20%", "III", 20000),
    (r"^Schedule IV– 1\.5%", "IV", 1500),
    (r"^Schedule V – 0\.125%", "V", 125),
    (r"^Schedule VI – 0\.75%", "VI", 750),
    (r"^Schedule VII – 14%", "VII", 14000),
    (r"^2\. Exempted Goods", "EXEMPT", 0),
]
END = r"^3\. CGST Rates on Goods"
RATE_TOKEN = {2500: "2.5%", 9000: "9%", 20000: "20%", 1500: "1.5%", 125: "0.125%", 750: "0.75%", 14000: "14%", 0: "Nil"}

lines = open(sys.argv[1], encoding="utf-8").read().split("\n")

# Drop page markers and the bare page number printed under each.
clean, skip_next_number = [], False
for ln in lines:
    if ln.startswith("===== PAGE"):
        skip_next_number = True
        continue
    if skip_next_number and re.fullmatch(r"\s*\d+\s*", ln):
        skip_next_number = False
        continue
    skip_next_number = False
    clean.append(ln)

# Split into sections.
bounds = []
for i, ln in enumerate(clean):
    for pat, sid, rate in SECTIONS:
        if re.match(pat, ln):
            bounds.append((i, sid, rate))
    if re.match(END, ln):
        bounds.append((i, None, None))
        break

HSN_CODE = r"\d{2,8}(?:\s\d{2}){0,3}"
HSN_PART = re.compile(rf"^((?:{HSN_CODE})(?:\s*(?:,|or|and)\s*(?:{HSN_CODE}))*)(\s*\((?:Except|except)[^)]*\))?")
rows, report = [], []
for (start, sid, rate), (end, _, _) in zip(bounds, bounds[1:]):
    if sid is None:
        continue
    body = clean[start + 1:end]
    token = RATE_TOKEN[rate]
    expected = 1
    current = None
    found = []
    for ln in body:
        m = re.match(r"^(\d+)([A-Z]?)\.\s+(.*)$", ln)
        if m and (int(m.group(1)) == expected or (m.group(2) and int(m.group(1)) == expected - 1)):
            if current:
                found.append(current)
            current = [m.group(1) + m.group(2), m.group(3)]
            if not m.group(2):
                expected += 1
        elif current:
            current[1] += " " + ln
    if current:
        found.append(current)

    for serial, text in found:
        text = re.sub(r"\s+", " ", text).strip()
        text = re.sub(r"(\w) -(\w)", r"\1-\2", text)  # "pre -packaged" -> "pre-packaged"
        # The PDF encodes curly quotes as odd glyphs (U+2015/U+2016/U+2018); use plain quotes.
        text = text.translate({0xFFFD: "'", 0x2015: '"', 0x2016: '"', 0x2018: "'", 0x2019: "'"})
        text = re.sub(r"^(\d) (?=[A-Z])", r"0\1 ", text)  # single-digit chapter "6" -> "06"
        # The row ends at the last occurrence of the schedule's rate (drops trailing explanations).
        idx = text.rfind(" " + token)
        rate_ok = idx >= 0
        desc_part = text[:idx] if rate_ok else text
        hm = HSN_PART.match(desc_part)
        codes, except_clause = [], ""
        if hm:
            codes = []
            for piece in re.split(r"\s*(?:,|or|and)\s*", hm.group(1)):
                if not piece.strip():
                    continue
                joined = re.sub(r"\s", "", piece)
                # Two codes printed without a comma ("2711 12 00 2711 13 00") -> split by 4-digit heading groups.
                codes += re.findall(r"\d{4}(?:\s\d{2}){0,2}", piece) if len(joined) > 8 else [piece]
            codes = [re.sub(r"\s", "", c) for c in codes]
            except_clause = (hm.group(2) or "").strip()
            desc = desc_part[hm.end():].strip()
        elif desc_part.lower().startswith("any chapter"):
            desc = desc_part
        else:
            desc = desc_part
        if except_clause:
            desc = f"{except_clause} {desc}".strip()
        bad = [c for c in codes if not re.fullmatch(r"\d{2,8}", c)]
        if not rate_ok or bad or not desc:
            report.append(f"{sid}-{serial}: rate_ok={rate_ok} bad={bad} text={text[:90]!r}")
        rows.append({"id": f"{sid}-{serial}", "s": sid, "h": codes, "d": desc, "r": rate})

    serials = [int(re.match(r"\d+", s).group()) for s, _ in found]
    gaps = sorted(set(range(1, max(serials) + 1)) - set(serials)) if serials else []
    print(f"{sid:7} rows={len(found):4} last={max(serials) if serials else 0:4} gaps={gaps[:10]}")

json.dump(rows, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("total rows:", len(rows), "| rows without HSN:", sum(1 for r in rows if not r["h"]), "| issues:", len(report))
for r in report[:25]:
    print("  ", r)
