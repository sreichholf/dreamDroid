#!/usr/bin/env python3
"""Print a Markdown summary of JUnit XML results: counts, then each failed test.

usage: junit-summary.py TITLE DIR...  (reads DIR/**/TEST-*.xml)
Under GitHub Actions the summary goes to $GITHUB_STEP_SUMMARY and each failed test becomes an
::error annotation on stdout, so it shows on the run page. Elsewhere the summary goes to stdout.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

title, dirs = sys.argv[1], sys.argv[2:]
summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
out = open(summary_path, "a") if summary_path else sys.stdout


def escape(value, prop=False):
    value = value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    return value.replace(":", "%3A").replace(",", "%2C") if prop else value


files = sorted({f for d in dirs for f in glob.glob(os.path.join(d, "**", "TEST-*.xml"), recursive=True)})
if not files:
    print(f"### {title}\n\nNo test results.", file=out)
    sys.exit(0)

tests = skipped = 0
failed = []
for path in files:
    for case in ET.parse(path).getroot().iter("testcase"):
        tests += 1
        if case.find("skipped") is not None:
            skipped += 1
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is not None:
            text = (problem.get("message") or problem.text or "").strip()
            failed.append((f"{case.get('classname')}.{case.get('name')}", text.splitlines()[0] if text else ""))

print(f"### {title}\n\n{tests} tests, {len(failed)} failed, {skipped} skipped", file=out)
if failed:
    print(file=out)
for name, line in failed:
    print(f"- `{name}`: {line}", file=out)
    if summary_path:
        print(f"::error title={escape(name, prop=True)}::{escape(line)}")
