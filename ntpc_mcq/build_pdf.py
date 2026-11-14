#!/usr/bin/env python3
"""Build the site manifest and per-lecture PDFs.

  data_en/*.json -> manifest.json (for index.html) + pdf/<Title>.pdf   (English)
  data/*.json    -> pdf_hindi/<Title>.pdf                               (Hindi, with --hindi)

Each PDF: key points, MCQs, then the answer key and short explanations at the end.
"""
import json, html, os, sys
from playwright.sync_api import sync_playwright

BASE = os.path.dirname(os.path.abspath(__file__))
lect = json.load(open(f"{BASE}/lectures.json"))
L = "ABCD"
e = html.escape

TEXT = {
    "en": dict(src="data_en", out="pdf", series="RRB NTPC GK PYQ Series", total="Total questions",
               kp="Key Points from this Lecture", mcq="MCQs", key="Answer Key", exp="Short Explanations"),
    "hi": dict(src="data", out="pdf_hindi", series="RRB NTPC GK PYQ Series", total="कुल प्रश्न",
               kp="इस लेक्चर की मुख्य बातें (Additional Info)", mcq="MCQs", key="उत्तर कुंजी (Answer Key)", exp="संक्षिप्त व्याख्या"),
}

CSS = """
@page{size:A4;margin:14mm}
body{font-family:'DejaVu Sans','FreeSans','FreeSerif',sans-serif;font-size:11pt;line-height:1.45;color:#111}
h1{text-align:center;font-size:19pt;margin:0 0 4pt}
.sub{text-align:center;color:#555;margin-bottom:10pt}
h2{font-size:13.5pt;border-bottom:1.5px solid #333;padding-bottom:2pt;margin:12pt 0 6pt}
.q{break-inside:avoid;margin:0 0 8pt}
.o{margin-left:14pt}
ul{margin:0;padding-left:16pt}
.key{page-break-before:always}
table{border-collapse:collapse;width:100%}
td{border:1px solid #999;padding:2pt 4pt;font-size:10pt;width:10%;text-align:center}
.exp{margin:3pt 0;font-size:9.5pt;color:#222}
"""


def subject(title):
    for s in ("Geography", "Polity", "History"):
        if s in title:
            return s
    return "Other"


def pdf_name(title):
    return title.replace(" ", "_") + ".pdf"


def build_html(d, t):
    m = d["mcqs"]
    h = [f"<html><head><meta charset='utf-8'><style>{CSS}</style></head><body>",
         f"<h1>{t['series']} — {e(d['title'])}</h1><div class='sub'>Parmar Sir · {t['total']}: {len(m)}</div>",
         f"<h2>{t['kp']}</h2><ul>" + "".join(f"<li>{e(s)}</li>" for s in d["summary"]) + "</ul>",
         f"<h2>{t['mcq']}</h2>"]
    for i, q in enumerate(m, 1):
        h.append(f"<div class='q'><b>Q{i}.</b> {e(q['q'])}"
                 + "".join(f"<div class='o'>({L[j]}) {e(o)}</div>" for j, o in enumerate(q["o"])) + "</div>")
    h.append(f"<div class='key'><h2>{t['key']}</h2><table>")
    for r in range(0, len(m), 10):
        h.append("<tr>" + "".join(f"<td>{i+1}. <b>{L[m[i]['a']]}</b></td>" for i in range(r, min(r + 10, len(m)))) + "</tr>")
    h.append(f"</table><h2>{t['exp']}</h2>")
    for i, q in enumerate(m, 1):
        if q.get("e"):
            h.append(f"<div class='exp'><b>{i}.</b> {e(q['e'])}</div>")
    h.append("</div></body></html>")
    return "".join(h)


def build(lang, page):
    t = TEXT[lang]
    os.makedirs(f"{BASE}/{t['out']}", exist_ok=True)
    manifest = []
    for vid in lect:
        src = f"{BASE}/{t['src']}/{vid}.json"
        if not os.path.exists(src):
            continue
        d = json.load(open(src))
        page.set_content(build_html(d, t))
        page.pdf(path=f"{BASE}/{t['out']}/{pdf_name(d['title'])}", format="A4", print_background=True)
        manifest.append({"id": vid, "title": d["title"], "subject": subject(d["title"]),
                         "count": len(d["mcqs"]), "pdf": f"{t['out']}/{pdf_name(d['title'])}"})
        print(lang, d["title"], len(d["mcqs"]))
    return manifest


if __name__ == "__main__":
    langs = ["en"] + (["hi"] if "--hindi" in sys.argv else [])
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path="/opt/pw-browsers/chromium", args=["--no-sandbox"])
        pg = b.new_page()
        for lang in langs:
            man = build(lang, pg)
            if lang == "en":
                json.dump(man, open(f"{BASE}/manifest.json", "w"), ensure_ascii=False, indent=1)
                print("manifest:", len(man), "lectures,", sum(x["count"] for x in man), "MCQs")
        b.close()
