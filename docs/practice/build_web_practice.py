"""Build a self-contained, UTF-8 practice page using only the Python standard library."""

import json
import re
from collections import Counter
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SERVICES = ["lottery", "points", "coupon", "membership", "fulfillment"]
PREFIXES = {"lottery": "L", "points": "P", "coupon": "C", "membership": "M", "fulfillment": "F"}


def load_questions():
    questions = []
    required = {
        "id", "service", "title", "level", "minutes", "tags", "purpose", "component",
        "sources", "scope", "context", "requirements", "starter", "answer", "explanation",
        "checks", "language",
    }
    for path in sorted((HERE / "bank").glob("*.json")):
        records = json.loads(path.read_text(encoding="utf-8"))
        assert isinstance(records, list), f"{path}: expected question array"
        questions.extend(records)
    assert len(questions) == 25, f"Expected 25 exercises, got {len(questions)}"
    assert len({q["id"] for q in questions}) == len(questions), "Duplicate question IDs"
    counts = Counter(q["service"] for q in questions)
    assert counts == Counter({service: 5 for service in SERVICES}), counts
    steps = 0
    for question in questions:
        assert required <= question.keys(), f"{question.get('id')}: missing fields"
        qid = question["id"]
        assert re.fullmatch(PREFIXES[question["service"]] + r"0[1-5]", qid), qid
        assert question["language"] in {"java", "yaml", "lua"}, qid
        assert question["level"] in {"基础", "进阶", "综合"}, qid
        for field in ["tags", "context", "requirements", "explanation", "checks"]:
            assert isinstance(question[field], list) and question[field], (qid, field)
            assert all(isinstance(item, str) and item.strip() for item in question[field]), (qid, field)
        for field in ["title", "purpose", "component", "scope", "starter", "answer"]:
            assert isinstance(question[field], str) and question[field].strip(), (qid, field)
        todo_ids = re.findall(r"TODO\s+\[([^\]]+)\]", question["starter"])
        assert len(todo_ids) >= 3, f"{qid}: expected whole-step writing positions"
        assert len(todo_ids) == len(set(todo_ids)), f"{qid}: duplicate step IDs"
        assert all(step.startswith(qid + "-") for step in todo_ids), f"{qid}: wrong step ID"
        assert "TODO [" not in question["answer"], f"{qid}: unfinished answer"
        steps += len(todo_ids)
        assert question["sources"], f"{qid}: no source"
        for source in question["sources"]:
            source_path = (ROOT / source["path"]).resolve()
            assert source_path.is_relative_to(ROOT) and source_path.is_file(), (qid, source)
            lines = source_path.read_text(encoding="utf-8-sig").splitlines()
            assert isinstance(source["line"], int) and 1 <= source["line"] <= len(lines), (qid, source)
            assert source["path"].split("/")[0] == question["service"] + "-service", (qid, source)
            assert source["symbol"].strip(), (qid, source)
    questions.sort(key=lambda q: (SERVICES.index(q["service"]), q["id"]))
    return questions, counts, steps


def main():
    questions, counts, steps = load_questions()
    data = json.dumps({"sourceRoot": ROOT.as_posix(), "questions": questions}, ensure_ascii=False)
    # Escape HTML-significant characters before embedding data in a script element.
    for character, escaped in [("<", "\\u003c"), (">", "\\u003e"), ("&", "\\u0026"), ("\u2028", "\\u2028"), ("\u2029", "\\u2029")]:
        data = data.replace(character, escaped)
    page = (HERE / "web-shell.html").read_text(encoding="utf-8")
    page = page.replace("{{STYLES}}", (HERE / "web-style.css").read_text(encoding="utf-8"))
    page = page.replace("{{APP}}", (HERE / "web-app.js").read_text(encoding="utf-8"))
    page = page.replace("{{DATA}}", data)
    assert not any(marker in page for marker in ["{{STYLES}}", "{{APP}}", "{{DATA}}"])
    (HERE / "index.html").write_text(page, encoding="utf-8")
    print(f"Built index.html: {len(questions)} exercises, {steps} whole-step writing positions; {dict(counts)}")


if __name__ == "__main__":
    main()
