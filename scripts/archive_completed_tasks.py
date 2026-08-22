"""Moves fully-checked-off TASKS.md sections into ARCHIVE.md.

A "## Story N - ..." section only moves once PROJECT_STATE.md also has that
story's status as Implemented, its row is removed there too. Any other
"## " section (audit fixes, dependency upgrades, bug batches, ...) moves
once every checkbox under it is checked, no PROJECT_STATE.md row involved.

Usage: python scripts/archive_completed_tasks.py [--check]
--check reports what would move without writing anything, and exits 1 if
there's anything to archive.
"""

import re
import sys
from pathlib import Path

DOCS_DIR = Path(__file__).resolve().parent.parent / "docs"
TASKS_PATH = DOCS_DIR / "TASKS.md"
ARCHIVE_PATH = DOCS_DIR / "ARCHIVE.md"
PROJECT_STATE_PATH = DOCS_DIR / "PROJECT_STATE.md"

SECTION_HEADING = re.compile(r"^## (.+)$", re.MULTILINE)
STORY_HEADING = re.compile(r"^Story (\d+) — ")
CHECKBOX = re.compile(r"^- \[([ x])] ", re.MULTILINE)
PROJECT_STATE_ROW = re.compile(r"^\|\s*(\d+)\s*\|.*\|\s*([^|]*?)\s*\|\s*$", re.MULTILINE)
ARCHIVE_PLACEHOLDER = "No stories archived yet."


def split_sections(text: str) -> tuple[str, list[str]]:
    headings = list(SECTION_HEADING.finditer(text))
    if not headings:
        return text, []

    preamble = text[: headings[0].start()]
    sections = []
    for i, heading in enumerate(headings):
        end = headings[i + 1].start() if i + 1 < len(headings) else len(text)
        sections.append(text[heading.start() : end].rstrip("\n"))
    return preamble, sections


def section_heading_text(section: str) -> str:
    return section.splitlines()[0].removeprefix("## ").strip()


def is_fully_checked(section: str) -> bool:
    boxes = CHECKBOX.findall(section)
    return bool(boxes) and all(box == "x" for box in boxes)


def project_state_status(project_state_text: str, story_id: str) -> str | None:
    for row in PROJECT_STATE_ROW.finditer(project_state_text):
        if row.group(1) == story_id:
            return row.group(2)
    return None


def remove_project_state_row(project_state_text: str, story_id: str) -> str:
    kept_lines = [
        line
        for line in project_state_text.splitlines(keepends=True)
        if not re.match(rf"^\|\s*{story_id}\s*\|", line)
    ]
    return "".join(kept_lines)


def join_sections(preamble: str, sections: list[str]) -> str:
    body = "\n\n".join(sections)
    return preamble.rstrip("\n") + ("\n\n" + body + "\n" if body else "\n")


def main() -> int:
    check_only = "--check" in sys.argv[1:]

    tasks_text = TASKS_PATH.read_text(encoding="utf-8")
    archive_text = ARCHIVE_PATH.read_text(encoding="utf-8")
    project_state_text = PROJECT_STATE_PATH.read_text(encoding="utf-8")

    preamble, sections = split_sections(tasks_text)

    kept_sections = []
    archived_sections = []

    for section in sections:
        heading = section_heading_text(section)

        if not is_fully_checked(section):
            kept_sections.append(section)
            continue

        story_match = STORY_HEADING.match(heading)
        if story_match:
            story_id = story_match.group(1)
            status = project_state_status(project_state_text, story_id)
            if status is None or not status.startswith("Implemented"):
                kept_sections.append(section)
                continue
            project_state_text = remove_project_state_row(project_state_text, story_id)

        archived_sections.append(section)

    if not archived_sections:
        print("Nothing to archive.")
        return 0

    for section in archived_sections:
        print(f"Archiving: {section_heading_text(section)}")

    if check_only:
        return 1

    new_tasks_text = join_sections(preamble, kept_sections)

    archive_body = archive_text.replace(ARCHIVE_PLACEHOLDER, "").rstrip("\n")
    new_archive_text = archive_body + "\n\n" + "\n\n".join(archived_sections) + "\n"

    TASKS_PATH.write_text(new_tasks_text, encoding="utf-8")
    ARCHIVE_PATH.write_text(new_archive_text, encoding="utf-8")
    PROJECT_STATE_PATH.write_text(project_state_text, encoding="utf-8")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
