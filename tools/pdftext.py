"""Dump text of a PDF to a .txt file, page by page. Usage: pdftext.py <in.pdf> <out.txt>"""
import sys
from pypdf import PdfReader

reader = PdfReader(sys.argv[1])
with open(sys.argv[2], "w", encoding="utf-8") as out:
    for i, page in enumerate(reader.pages, 1):
        out.write(f"\n===== PAGE {i} =====\n")
        out.write(page.extract_text() or "")
print("pages:", len(reader.pages))
