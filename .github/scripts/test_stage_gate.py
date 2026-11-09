#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Tests for stage_gate.py. Run: python3 .github/scripts/test_stage_gate.py"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stage_gate as sg  # noqa: E402

REPO = "o/r"


class GateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.old_cwd = os.getcwd()
        os.chdir(self.tmp.name)
        for args in (["init", "-q", "-b", "develop"], ["config", "user.name", "T"],
                     ["config", "user.email", "t@example.invalid"], ["config", "commit.gpgsign", "false"]):
            self.git(*args)
        self.first = self.commit("one")
        self.second = self.commit("two")
        self.git("update-ref", "refs/remotes/origin/develop", self.second)
        self.git("checkout", "-q", "-b", "feature/x")
        self.feature = self.commit("feature only")
        self.git("checkout", "-q", "develop")
        self.calls = []
        self.dev_urls = {}   # sha -> url of a successful Development run
        self.qa_runs = []    # QA run objects as the GitHub API returns them (any status)
        self.qa_jobs = {}    # run id -> job objects of its latest attempt
        self.api_fails = False
        self.jobs_api_fails = False
        self.original = sg.gh_api
        sg.gh_api = self.fake_gh

    def tearDown(self):
        sg.gh_api = self.original
        os.chdir(self.old_cwd)
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, message):
        with open("f.txt", "a", encoding="utf-8") as f:
            f.write(message + "\n")
        self.git("add", "f.txt")
        self.git("commit", "-q", "-m", message)
        return self.git("rev-parse", "HEAD")

    def fake_gh(self, *args):
        self.calls.append(args)
        if self.api_fails:
            return subprocess.CompletedProcess(args, 1, "", "gh: Bad credentials (HTTP 401)")
        endpoint = args[1]
        if "development.yml" in endpoint:
            sha = endpoint.split("head_sha=")[1].split("&")[0]
            out = self.dev_urls.get(sha, "")
        elif "/jobs" in endpoint:
            if self.jobs_api_fails:
                return subprocess.CompletedProcess(args, 1, "", "gh: Server Error (HTTP 502)")
            run_id = int(endpoint.split("/runs/")[1].split("/")[0])
            out = "\n".join(json.dumps(job) for job in self.qa_jobs.get(run_id, []))
        else:
            # like the API with branch=develop&event=workflow_dispatch, then the jq title filter
            assert "branch=develop" in endpoint and "event=workflow_dispatch" in endpoint, endpoint
            jq = args[-1]
            out = "\n".join(json.dumps(run) for run in self.qa_runs
                            if run["head_branch"] == "develop" and run["event"] == "workflow_dispatch"
                            and f'"{run["display_title"]}"' in jq)
        return subprocess.CompletedProcess(args, 0, out + "\n" if out else "", "")

    def add_qa_run(self, sha, run_id, status="completed", conclusion="success", jobs=None, **fields):
        """Adds a QA run like the real API returns it; by default a genuine, fully successful one."""
        run = {"id": run_id, "run_number": run_id, "display_title": f"QA {sha}", "status": status,
               "conclusion": conclusion if status == "completed" else None, "event": "workflow_dispatch",
               "head_branch": "develop", "path": ".github/workflows/qa.yml",
               "repository": {"full_name": REPO}, "head_repository": {"full_name": REPO},
               "html_url": f"https://qa/{run_id}"}
        run.update(fields)
        self.qa_runs.append(run)
        self.qa_jobs[run_id] = jobs if jobs is not None else [
            {"name": "Verify commit", "status": "completed", "conclusion": "success",
             "steps": [{"name": "Commit is on develop and passed Development", "conclusion": "success"}]},
            {"name": "QA APK", "status": "completed", "conclusion": "success",
             "steps": [{"name": "Confirm the checked-out commit", "conclusion": "success"}]},
        ]
        return run

    # --- SHA format -------------------------------------------------------------------------

    def test_short_sha_rejected(self):
        with self.assertRaisesRegex(sg.GateError, "full 40-character"):
            sg.gate("qa", self.second[:7], REPO)

    def test_non_hex_rejected(self):
        with self.assertRaisesRegex(sg.GateError, "full 40-character"):
            sg.gate("qa", "g" * 40, REPO)
        with self.assertRaisesRegex(sg.GateError, "full 40-character"):
            sg.gate("qa", "develop", REPO)

    def test_upper_case_and_spaces_are_rejected(self):
        # the QA run name uses the raw input, so only the exact form can be matched later
        self.dev_urls[self.second] = "https://dev/1"
        for value in (self.second.upper(), f" {self.second}", f"{self.second}\n"):
            with self.assertRaisesRegex(sg.GateError, "lower-case"):
                sg.gate("qa", value, REPO)
        self.assertEqual(sg.gate("qa", self.second, REPO)["sha"], self.second)

    # --- on develop ---------------------------------------------------------------------------

    def test_commit_not_on_develop_rejected(self):
        self.dev_urls[self.feature] = "https://dev/1"
        with self.assertRaisesRegex(sg.GateError, "not on develop"):
            sg.gate("qa", self.feature, REPO)

    def test_unknown_commit_rejected(self):
        with self.assertRaisesRegex(sg.GateError, "does not exist"):
            sg.gate("qa", "1" * 40, REPO)

    def test_older_develop_commit_is_on_develop(self):
        self.dev_urls[self.first] = "https://dev/1"
        self.assertEqual(sg.gate("qa", self.first, REPO)["run_url"], "https://dev/1")

    def test_missing_develop_ref(self):
        with self.assertRaisesRegex(sg.GateError, "fetch-depth"):
            sg.gate("qa", self.second, REPO, develop_ref="origin/missing")

    # --- QA needs Development ------------------------------------------------------------------

    def test_qa_requires_successful_development_run(self):
        with self.assertRaisesRegex(sg.GateError, "no successful Development run"):
            sg.gate("qa", self.second, REPO)

    def test_qa_with_development_run(self):
        self.dev_urls[self.second] = "https://dev/7"
        self.assertEqual(sg.gate("qa", self.second, REPO), {"sha": self.second, "run_url": "https://dev/7"})
        endpoint = self.calls[-1][1]
        self.assertIn(f"head_sha={self.second}", endpoint)
        self.assertIn("status=success", endpoint)

    def test_development_run_of_other_commit_does_not_count(self):
        self.dev_urls[self.first] = "https://dev/1"
        with self.assertRaisesRegex(sg.GateError, "no successful Development run"):
            sg.gate("qa", self.second, REPO)

    # --- Release needs QA ----------------------------------------------------------------------

    def test_release_requires_successful_qa_run(self):
        self.dev_urls[self.second] = "https://dev/1"  # Development alone is not enough
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)

    def test_release_with_qa_run(self):
        self.add_qa_run(self.second, 3)
        self.assertEqual(sg.gate("release", self.second, REPO), {"sha": self.second, "run_url": "https://qa/3"})
        runs_call = next(c for c in self.calls if "/workflows/qa.yml/runs" in c[1])
        self.assertIn("branch=develop", runs_call[1])
        self.assertIn("event=workflow_dispatch", runs_call[1])
        self.assertIn(f'"QA {self.second}"', runs_call[-1])
        self.assertIn("filter=latest", self.calls[-1][1])  # the jobs of the latest attempt

    def test_qa_run_of_other_commit_does_not_count(self):
        # develop moved on after QA: QA tested `first`, the release asks for `second`
        self.add_qa_run(self.first, 1)
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)
        self.assertEqual(sg.gate("release", self.first, REPO)["sha"], self.first)

    def test_qa_run_for_a_prefix_of_the_sha_does_not_count(self):
        self.add_qa_run(self.second[:39], 1)
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)

    def test_release_of_feature_commit_rejected_even_with_qa_run(self):
        self.add_qa_run(self.feature, 9)
        with self.assertRaisesRegex(sg.GateError, "not on develop"):
            sg.gate("release", self.feature, REPO)

    def test_failed_qa_run_blocks_release(self):
        self.add_qa_run(self.second, 4, conclusion="failure")
        with self.assertRaisesRegex(sg.GateError, "no successful QA run.*'failure'"):
            sg.gate("release", self.second, REPO)

    def test_cancelled_qa_run_blocks_release(self):
        self.add_qa_run(self.second, 4, conclusion="cancelled")
        with self.assertRaisesRegex(sg.GateError, "'cancelled'"):
            sg.gate("release", self.second, REPO)

    def test_other_unsuccessful_conclusions_block_release(self):
        for run_id, conclusion in enumerate(("timed_out", "action_required", "neutral", "skipped",
                                             "stale", "startup_failure", None), start=10):
            self.qa_runs.clear()
            self.add_qa_run(self.second, run_id, conclusion=conclusion)
            with self.assertRaisesRegex(sg.GateError, "no successful QA run", msg=str(conclusion)):
                sg.gate("release", self.second, REPO)

    def test_running_qa_run_blocks_release(self):
        for run_id, status in enumerate(("queued", "in_progress", "waiting", "requested", "pending"), start=20):
            self.qa_runs.clear()
            self.add_qa_run(self.second, run_id, status=status)
            with self.assertRaisesRegex(sg.GateError, "not finished", msg=status):
                sg.gate("release", self.second, REPO)

    def test_newest_qa_run_decides(self):
        # a later failed QA run for the same commit is not overridden by an earlier success
        self.add_qa_run(self.second, 5)
        self.add_qa_run(self.second, 6, conclusion="failure")
        with self.assertRaisesRegex(sg.GateError, "newest QA run.*'failure'.*https://qa/6"):
            sg.gate("release", self.second, REPO)
        # ... a later success after a failure counts (QA #13 failed, #14 passed for 74f701f0)
        self.add_qa_run(self.second, 7)
        self.assertEqual(sg.gate("release", self.second, REPO)["run_url"], "https://qa/7")

    def test_newest_qa_run_still_running_blocks_release(self):
        self.add_qa_run(self.second, 5)
        self.add_qa_run(self.second, 8, status="in_progress")
        with self.assertRaisesRegex(sg.GateError, "not finished.*https://qa/8"):
            sg.gate("release", self.second, REPO)

    def test_newest_is_by_run_id_not_list_order(self):
        self.add_qa_run(self.second, 9)
        self.add_qa_run(self.second, 2, conclusion="failure")  # older run listed later
        self.assertEqual(sg.gate("release", self.second, REPO)["run_url"], "https://qa/9")

    def test_qa_run_with_skipped_jobs_blocks_release(self):
        # the run is "success" when its jobs were skipped, but nothing was built
        skipped = [{"name": "Verify commit", "conclusion": "skipped", "steps": []},
                   {"name": "QA APK", "conclusion": "skipped", "steps": []}]
        self.add_qa_run(self.second, 4, jobs=skipped)
        with self.assertRaisesRegex(sg.GateError, "'Verify commit' is skipped"):
            sg.gate("release", self.second, REPO)

    def test_qa_run_without_build_job_blocks_release(self):
        only_gate = [{"name": "Verify commit", "conclusion": "success", "steps": []}]
        self.add_qa_run(self.second, 4, jobs=only_gate)
        with self.assertRaisesRegex(sg.GateError, "'QA APK' is missing"):
            sg.gate("release", self.second, REPO)

    def test_qa_run_without_commit_confirmation_blocks_release(self):
        no_confirm = [{"name": "Verify commit", "conclusion": "success", "steps": []},
                      {"name": "QA APK", "conclusion": "success",
                       "steps": [{"name": "Build Debug APK", "conclusion": "success"}]}]
        self.add_qa_run(self.second, 4, jobs=no_confirm)
        with self.assertRaisesRegex(sg.GateError, "did not confirm"):
            sg.gate("release", self.second, REPO)
        self.qa_jobs[4][1]["steps"].append({"name": "Confirm the checked-out commit", "conclusion": "skipped"})
        with self.assertRaisesRegex(sg.GateError, "is skipped"):
            sg.gate("release", self.second, REPO)

    def test_qa_run_from_another_workflow_file_does_not_count(self):
        self.add_qa_run(self.second, 4, path=".github/workflows/qa-copy.yml")
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)

    def test_qa_run_from_a_fork_does_not_count(self):
        self.add_qa_run(self.second, 4, head_repository={"full_name": "someone/RCBBoard"})
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)
        self.qa_runs.clear()
        self.add_qa_run(self.second, 5, repository={"full_name": "someone/RCBBoard"})
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)

    def test_qa_run_on_another_branch_does_not_count(self):
        # a modified qa.yml on a feature branch could skip the checks: never accepted
        self.add_qa_run(self.second, 4, head_branch="feature/x")
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)

    def test_qa_run_not_started_by_hand_does_not_count(self):
        self.add_qa_run(self.second, 4, event="push")
        with self.assertRaisesRegex(sg.GateError, "no successful QA run"):
            sg.gate("release", self.second, REPO)

    def test_branch_and_event_are_checked_even_if_the_api_filter_is_ignored(self):
        # defence in depth: the script itself rejects runs the query should already have left out
        for field in ({"head_branch": "feature/x"}, {"event": "push"}):
            self.qa_runs.clear()
            self.add_qa_run(self.second, 4, **field)
            self.assertFalse(sg._is_genuine_qa_run(self.qa_runs[0], REPO), field)
        self.qa_runs.clear()
        self.assertTrue(sg._is_genuine_qa_run(self.add_qa_run(self.second, 4), REPO))

    def test_genuine_run_is_found_next_to_ignored_ones(self):
        self.add_qa_run(self.second, 30, path=".github/workflows/qa-copy.yml")  # newer, not genuine
        self.add_qa_run(self.second, 12)
        self.assertEqual(sg.gate("release", self.second, REPO)["run_url"], "https://qa/12")

    def test_development_run_does_not_replace_qa(self):
        self.dev_urls[self.second] = "https://dev/1"
        self.add_qa_run(self.second, 4, conclusion="failure")
        with self.assertRaisesRegex(sg.GateError, "'failure'"):
            sg.gate("release", self.second, REPO)

    def test_jobs_api_error_fails_safe(self):
        self.add_qa_run(self.second, 4)
        self.jobs_api_fails = True
        with self.assertRaisesRegex(sg.GateError, "could not list the jobs"):
            sg.gate("release", self.second, REPO)

    def test_malformed_api_answer_fails_safe(self):
        sg.gh_api = lambda *args: subprocess.CompletedProcess(args, 0, "not json\n", "")
        with self.assertRaisesRegex(sg.GateError, "unexpected answer"):
            sg.gate("release", self.second, REPO)
        sg.gh_api = lambda *args: subprocess.CompletedProcess(args, 0, '"a string"\n', "")
        with self.assertRaisesRegex(sg.GateError, "unexpected answer"):
            sg.gate("release", self.second, REPO)

    def test_run_without_id_fails_safe(self):
        run = self.add_qa_run(self.second, 4)
        del run["id"]
        with self.assertRaisesRegex(sg.GateError, "no id"):
            sg.gate("release", self.second, REPO)

    def test_release_rejects_bad_sha_before_any_api_call(self):
        for value in (self.second[:7], self.second.upper(), "", "develop", f"{self.second}\n"):
            with self.assertRaisesRegex(sg.GateError, "full 40-character"):
                sg.gate("release", value, REPO)
        self.assertEqual(self.calls, [])

    # --- API failures fail safe ----------------------------------------------------------------

    def test_api_error_fails_safe(self):
        self.api_fails = True
        with self.assertRaisesRegex(sg.GateError, "could not list"):
            sg.gate("release", self.second, REPO)

    def test_missing_gh_fails_safe(self):
        sg.gh_api = self.original
        real_run = sg.subprocess.run

        def run_without_gh(cmd, *args, **kwargs):
            if cmd[0] == "gh":
                raise FileNotFoundError("gh")
            return real_run(cmd, *args, **kwargs)
        sg.subprocess.run = run_without_gh
        try:
            with self.assertRaisesRegex(sg.GateError, "GitHub CLI"):
                sg.gate("qa", self.second, REPO)
        finally:
            sg.subprocess.run = real_run


if __name__ == "__main__":
    unittest.main(verbosity=2)
