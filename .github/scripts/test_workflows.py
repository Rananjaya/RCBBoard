#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Checks of the workflow files that keep the release pipeline safe (see docs/ci-cd.md).

Plain text checks, no YAML library needed. They fail when a safety setting is removed or
when a job or step that stage_gate.py or the branch ruleset depends on is renamed.
Run: python3 .github/scripts/test_workflows.py
"""
from __future__ import annotations

import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import stage_gate as sg  # noqa: E402

WORKFLOWS = os.path.join(HERE, "..", "workflows")
APP_BUILD_FILE = os.path.join(HERE, "..", "..", "app", "build.gradle.kts")
# The existing RCBBoard production signing certificate; it must never change.
RELEASE_CERT_SHA256 = "0b01373116d4a7b57646c4c7e2ffc28af91870bdbeabb4b8c05c00f313e2291b"
# The registered application ID (permanent, see docs/ci-cd.md "Application ID"); debug builds add ".debug".
PRODUCTION_PACKAGE = "com.rananjaya.rcbboard"
DEBUG_PACKAGE = PRODUCTION_PACKAGE + ".debug"
# Job names in ci.yml = the required status checks of the develop branch ruleset.
REQUIRED_CHECKS = ("Release script tests", "Unit tests", "Native tests (ASan/UBSan)", "Debug build")


def read(name: str) -> str:
    with open(os.path.join(WORKFLOWS, name), encoding="utf-8") as f:
        return f.read()


def workflow_files() -> list[str]:
    return sorted(n for n in os.listdir(WORKFLOWS) if n.endswith((".yml", ".yaml")))


def step(text: str, name_prefix: str) -> str:
    """The text of the step whose name starts with name_prefix, up to the next step or job."""
    match = re.search(r"^(\s*)- name: " + re.escape(name_prefix) + r".*?$", text, re.MULTILINE)
    if not match:
        raise AssertionError(f"step '{name_prefix}' not found")
    indent = len(match.group(1))
    lines = [match.group(0)]
    for line in text[match.end() + 1:].splitlines():
        stripped = line.lstrip()
        if stripped and (len(line) - len(stripped) < indent
                         or (len(line) - len(stripped) == indent and stripped.startswith("- "))):
            break
        lines.append(line)
    return "\n".join(lines)


def job_names(text: str) -> list[str]:
    return [m.strip().strip('"') for m in re.findall(r"^    name: (.+)$", text, re.MULTILINE)]


class AllWorkflowsTest(unittest.TestCase):
    def test_expected_workflows_only(self):
        # build-debug-apk.yml was removed: CI's "Debug build" and Development/QA replace it
        self.assertEqual(workflow_files(), ["ci.yml", "development.yml", "qa.yml", "release.yml"])

    def test_actions_are_pinned_to_full_commit_shas(self):
        for name in workflow_files():
            for line in read(name).splitlines():
                m = re.match(r"^\s*(?:- )?uses: (\S+)", line)
                if not m or m.group(1).startswith("./"):
                    continue
                self.assertRegex(m.group(1), r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$", f"{name}: {line.strip()}")

    def test_default_permissions_are_set(self):
        for name in workflow_files():
            text = read(name)
            self.assertRegex(text, r"(?m)^permissions:", f"{name} has no top-level permissions")
            self.assertNotRegex(text, r"(?m)^permissions:\s*write-all", name)

    def test_no_privileged_pull_request_triggers(self):
        for name in workflow_files():
            text = read(name)
            self.assertNotIn("pull_request_target", text, name)
            self.assertNotIn("workflow_run", text, name)

    def test_no_inputs_expanded_inside_shell_scripts(self):
        # inputs reach shell scripts only through env:, never as ${{ inputs.* }} in run: text
        for name in workflow_files():
            in_run = False
            for line in read(name).splitlines():
                stripped = line.strip()
                if re.match(r"^(- )?run: *[|>]-?$", stripped):
                    in_run, indent = True, len(line) - len(line.lstrip())
                    continue
                if in_run and stripped and len(line) - len(line.lstrip()) <= indent:
                    in_run = False
                if in_run or re.match(r"^(- )?run: \S", stripped):
                    self.assertNotRegex(line, r"\$\{\{\s*(inputs|github\.event)\.", f"{name}: {stripped}")


class PackageIdTest(unittest.TestCase):
    def test_build_file_uses_the_registered_application_id(self):
        with open(APP_BUILD_FILE, encoding="utf-8") as f:
            ids = re.findall(r'^\s*applicationId = "([^"]+)"', f.read(), re.MULTILINE)
        self.assertEqual(ids, [PRODUCTION_PACKAGE])

    def test_release_signs_and_checks_the_production_package(self):
        text = read("release.yml")
        self.assertIn(f"  APP_PACKAGE: {PRODUCTION_PACKAGE}\n", text)
        self.assertIn('--package "$APP_PACKAGE"', step(text, "Sign and verify APK"))

    def test_debug_workflows_sign_and_check_the_debug_package(self):
        for name in ("development.yml", "qa.yml"):
            text = read(name)
            self.assertIn(f"  APP_ID: {DEBUG_PACKAGE}\n", text, name)
            self.assertIn('--package "$APP_ID"', step(text, "Sign and verify the APK"), name)

    def test_no_old_application_id_in_workflows(self):
        for name in workflow_files():
            self.assertNotIn("com.ranger.myheliboard", read(name), name)


class CiChecksTest(unittest.TestCase):
    def test_required_check_names_exist(self):
        # renaming a job would leave the branch ruleset waiting for a check that never reports
        self.assertEqual(sorted(job_names(read("ci.yml"))), sorted(REQUIRED_CHECKS))

    def test_ci_runs_for_every_pull_request_to_develop(self):
        text = read("ci.yml")
        self.assertRegex(text, r"pull_request:\n\s+branches: \[develop\]")
        self.assertNotRegex(text, r"(?m)^\s+paths(-ignore)?:")  # a path filter would leave checks missing


class QaTest(unittest.TestCase):
    def test_qa_names_match_the_release_gate(self):
        text = read("qa.yml")
        self.assertIn("run-name: QA ${{ inputs.commit }}\n", text)  # how stage_gate.py finds the run
        for job in sg.QA_REQUIRED_JOBS:
            self.assertIn(job, job_names(text))
        self.assertIn(f"- name: {sg.QA_COMMIT_STEP}\n", text)
        self.assertIn('[ "$(git rev-parse HEAD)" = "$COMMIT" ]', step(text, sg.QA_COMMIT_STEP))
        self.assertEqual(sg.QA_WORKFLOW_PATH, ".github/workflows/" + sg.QA_WORKFLOW)

    def test_qa_only_on_develop_and_needs_development(self):
        text = read("qa.yml")
        self.assertIn("github.ref == 'refs/heads/develop'", text)
        self.assertIn("stage_gate.py qa --sha", text)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.text = read("release.yml")

    def test_dry_run_is_the_default(self):
        block = re.search(r"\n      dry_run:\n((?:        .*\n)+)", self.text)
        self.assertIsNotNone(block)
        self.assertIn("type: boolean", block.group(1))
        self.assertIn("default: true", block.group(1))

    def test_only_manual_runs_on_develop(self):
        self.assertRegex(self.text, r"on:\n  workflow_dispatch:\n")
        self.assertNotRegex(self.text, r"(?m)^  (push|pull_request|schedule|workflow_call):")
        self.assertIn("github.ref == 'refs/heads/develop'", self.text)

    def test_release_gate_and_version_checks(self):
        self.assertIn('stage_gate.py release --sha "$COMMIT_INPUT"', step(self.text, "Commit is on develop and passed QA"))
        self.assertIn('--repo "$GITHUB_REPOSITORY"', step(self.text, "Work out the next version"))

    def test_publish_decision_needs_explicit_false(self):
        s = step(self.text, "Decide whether to publish")
        self.assertIn('elif [ "$DRY_RUN" != "false" ]; then', s)

    def test_tag_and_release_only_without_dry_run(self):
        for name in ("Create tag", "Create GitHub Release"):
            s = step(self.text, name)
            self.assertIn("if: ${{ !inputs.dry_run }}", s, name)
            self.assertIn('[ "$DRY_RUN" = "false" ] || {', s, name)

    def test_dry_run_never_uploads_the_apk(self):
        s = step(self.text, "Upload dry-run results")
        self.assertIn("if: inputs.dry_run", s)
        self.assertNotIn(".apk", s)
        self.assertIn("SHA256SUMS.txt", s)
        # the only places the production APK leaves the runner: the GitHub Release
        uploads = [m.start() for m in re.finditer(r"RCBBoard-\$VERSION\.apk\"", self.text)]
        self.assertEqual(len(uploads), 1)
        self.assertGreater(uploads[0], self.text.index("- name: Create GitHub Release"))

    def test_signing_and_certificate_check_unchanged(self):
        self.assertIn(f"EXPECTED_CERT_SHA256: {RELEASE_CERT_SHA256}\n", self.text)
        s = step(self.text, "Sign and verify APK")
        for part in ('--expected-cert-sha256 "$EXPECTED_CERT_SHA256"', "secrets.ANDROID_KEYSTORE_PASSWORD",
                     "secrets.ANDROID_KEY_ALIAS", "secrets.ANDROID_KEY_PASSWORD", '--package "$APP_PACKAGE"'):
            self.assertIn(part, s)
        self.assertIn(f"APP_PACKAGE: {PRODUCTION_PACKAGE}\n", self.text)
        self.assertIn("rm -f \"$RUNNER_TEMP/rcbboard-release.keystore\"", step(self.text, "Remove keystore"))
        self.assertIn("if: always()", step(self.text, "Remove keystore"))

    def test_release_job_uses_the_protected_environment(self):
        self.assertIn("    environment: release\n", self.text)
        self.assertIn("    if: needs.version.outputs.release == 'true'\n", self.text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
