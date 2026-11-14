#!/usr/bin/env python3
"""data/*.json -> HTML -> PDF (Chromium). Per lecture: summary, MCQs, answer key on last page."""
import json, html, os, sys, glob
from playwright.sync_api import sync_playwright

BASE = os.path.dirname(os.path.abspath(__file__))
lect = json.load(open(f"{BASE}/lectures.json"))
OUT = f"{BASE}/pdf"; os.makedirs(OUT, exist_ok=True)
L = "ABCD"
e = html.escape

CSS = """
@page{size:A4;margin:14mm}
body{font-family:'FreeSerif','FreeSans',serif;font-size:11.5pt;line-height:1.45}
h1{text-align:center;font-size:20pt;margin:0 0 4pt}
.sub{text-align:center;color:#555;margin-bottom:10pt}
h2{font-size:14pt;border-bottom:1.5px solid #333;padding-bottom:2pt;margin:12pt 0 6pt}
.q{break-inside:avoid;margin:0 0 8pt}
.o{margin-left:14pt}
ul{margin:0;padding-left:16pt}
.key{page-break-before:always}
table{border-collapse:collapse;width:100%}
td{border:1px solid #999;padding:2pt 4pt;font-size:10.5pt;width:10%;text-align:center}
.exp{margin:3pt 0;font-size:10pt;color:#222}
"""

def build(vid):
    d = json.load(open(f"{BASE}/data/{vid}.json"))
    m = d["mcqs"]
    h = [f"<html><head><meta charset='utf-8'><style>{CSS}</style></head><body>"]
    h.append(f"<h1>RRB NTPC GK PYQ Series — {e(d['title'])}</h1><div class='sub'>Parmar Sir · कुल प्रश्न: {len(m)}</div>")
    h.append("<h2>इस लेक्चर की मुख्य बातें (Additional Info)</h2><ul>" + "".join(f"<li>{e(s)}</li>" for s in d["summary"]) + "</ul>")
    h.append("<h2>MCQs</h2>")
    for i, q in enumerate(m, 1):
        h.append(f"<div class='q'><b>Q{i}.</b> {e(q['q'])}" + "".join(f"<div class='o'>({L[j]}) {e(o)}</div>" for j, o in enumerate(q["o"])) + "</div>")
    h.append("<div class='key'><h2>उत्तर कुंजी (Answer Key)</h2><table>")
    for r in range(0, len(m), 10):
        h.append("<tr>" + "".join(f"<td>{i+1}. <b>{L[m[i]['a']]}</b></td>" for i in range(r, min(r+10, len(m)))) + "</tr>")
    h.append("</table><h2>संक्षिप्त व्याख्या</h2>")
    for i, q in enumerate(m, 1):
        if q.get("e"):
            h.append(f"<div class='exp'><b>{i}.</b> {e(q['e'])}</div>")
    h.append("</div></body></html>")
    return "".join(h), d["title"], len(m)

if __name__ == "__main__":
    ids = [v for v in lect if os.path.exists(f"{BASE}/data/{v}.json")]
    with sync_playwright() as p:
        b = p.chromium.launch(); pg = b.new_page()
        for v in ids:
            h, t, n = build(v)
            pg.set_content(h)
            name = f"{OUT}/{t.replace(' ', '_')}.pdf"
            pg.pdf(path=name, format="A4", print_background=True)
            print(name, n)
        b.close()
