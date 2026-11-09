#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check that a commit may enter the QA or Production stage. Used by qa.yml and release.yml.

The same commit has to pass Development -> QA -> Production (see docs/ci-cd.md):

  stage_gate.py qa      --sha SHA --repo OWNER/NAME   # before a QA build
  stage_gate.py release --sha SHA --repo OWNER/NAME   # before a production release

Both stages require a full 40-character SHA of a commit on origin/develop. QA also needs a
successful Development run for exactly that SHA; Production needs a successful QA run for
exactly that SHA. QA runs are named "QA <sha>" (run-name in qa.yml), which is how they are
found. Nothing is guessed: a short SHA or another commit is never substituted.

For Production, the QA run must also be genuine and current:
* a manual (workflow_dispatch) run of .github/workflows/qa.yml on develop in this repository,
  so a modified copy of qa.yml on another branch or in a fork never counts;
* the newest QA run for that SHA: if a later QA run for the same SHA failed, was cancelled
  or is still running, the release stops (an older success does not override it);
* its jobs really ran: "Verify commit" and "QA APK" succeeded (a skipped job would still
  leave the run itself "successful") and "QA APK" confirmed the checked-out commit.

With --github-output, writes: sha, run_url (the Development or QA run that was found).
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from typing import Optional

DEVELOP_REF = "origin/develop"
DEVELOPMENT_WORKFLOW = "development.yml"
QA_WORKFLOW = "qa.yml"
QA_WORKFLOW_PATH = ".github/workflows/qa.yml"
# Job names in qa.yml that must have run and succeeded, and the step that confirms the commit.
QA_REQUIRED_JOBS = ("Verify commit", "QA APK")
QA_BUILD_JOB = "QA APK"
QA_COMMIT_STEP = "Confirm the checked-out commit"
SHA_RE = re.compile(r"[0-9a-f]{40}")


class GateError(Exception):
    pass


def gh_api(*args: str) -> subprocess.CompletedProcess:
    """Runs `gh api`; replaced in tests."""
    try:
        return subprocess.run(["gh", "api", *args], capture_output=True, text=True)
    except FileNotFoundError:
        raise GateError("the GitHub CLI (gh) is needed but was not found")


def git(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["git", *args], capture_output=True, text=True)


def validate_sha(value: str) -> str:
    # Exact form only (no trimming or case changes): the QA run is named after the raw input,
    # and the release looks for that name, so both must use exactly the same text.
    if not SHA_RE.fullmatch(value):  # fullmatch: "$" alone would accept a trailing newline
        raise GateError(f"'{value}' is not a full 40-character lower-case commit SHA. Copy the full "
                        "SHA from the Development run summary; short SHAs are not accepted.")
    return value


def check_on_develop(sha: str, develop_ref: str = DEVELOP_REF) -> None:
    if git("rev-parse", "--verify", "--quiet", f"{develop_ref}^{{commit}}").returncode != 0:
        raise GateError(f"{develop_ref} is not available; the checkout needs fetch-depth: 0")
    if git("cat-file", "-e", f"{sha}^{{commit}}").returncode != 0:
        raise GateError(f"commit {sha} does not exist in this repository")
    if git("merge-base", "--is-ancestor", sha, develop_ref).returncode != 0:
        raise GateError(f"commit {sha} is not on develop")


def _first_run(result: subprocess.CompletedProcess, what: str) -> Optional[str]:
    if result.returncode != 0:
        raise GateError(f"could not list {what} runs: {result.stderr.strip()}")
    urls = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    return urls[0] if urls else None


def successful_development_run(repo: str, sha: str) -> Optional[str]:
    """URL of a successful Development run on develop that built exactly `sha`, or None."""
    return _first_run(gh_api(
        "--paginate",
        f"repos/{repo}/actions/workflows/{DEVELOPMENT_WORKFLOW}/runs?head_sha={sha}&status=success&per_page=100",
        "--jq", '.workflow_runs[] | select(.head_branch == "develop") | .html_url'), "Development")


def _json_lines(result: subprocess.CompletedProcess, what: str) -> list[dict]:
    if result.returncode != 0:
        raise GateError(f"could not list {what}: {result.stderr.strip()}")
    items = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError:
            raise GateError(f"unexpected answer from the GitHub API while listing {what}")
        if not isinstance(item, dict):
            raise GateError(f"unexpected answer from the GitHub API while listing {what}")
        items.append(item)
    return items


def qa_runs(repo: str, sha: str) -> list[dict]:
    """All QA runs named "QA <sha>" that were started by hand on develop (any status)."""
    return _json_lines(gh_api(
        "--paginate",
        f"repos/{repo}/actions/workflows/{QA_WORKFLOW}/runs?branch=develop&event=workflow_dispatch&per_page=100",
        "--jq", f'.workflow_runs[] | select(.display_title == "QA {sha}") | tojson'), "QA runs")


def qa_jobs(repo: str, run_id: int) -> list[dict]:
    """The jobs of the latest attempt of a QA run."""
    return _json_lines(gh_api(
        "--paginate",
        f"repos/{repo}/actions/runs/{run_id}/jobs?filter=latest&per_page=100",
        "--jq", ".jobs[] | tojson"), "the jobs of the QA run")


def _is_genuine_qa_run(run: dict, repo: str) -> bool:
    return (run.get("head_branch") == "develop"
            and run.get("event") == "workflow_dispatch"
            and run.get("path") == QA_WORKFLOW_PATH
            and (run.get("repository") or {}).get("full_name") == repo
            and (run.get("head_repository") or {}).get("full_name") == repo)


def verified_qa_run(repo: str, sha: str) -> str:
    """URL of the QA run that approves `sha` for production; raises GateError otherwise."""
    runs = [run for run in qa_runs(repo, sha) if _is_genuine_qa_run(run, repo)]
    if not runs:
        raise GateError(f"there is no successful QA run for {sha}. Production only releases "
                        "commits that passed QA; run Actions > QA on develop with this exact SHA, "
                        "test the QA APK, then start the release again.")
    if not all(isinstance(run.get("id"), int) for run in runs):
        raise GateError("unexpected answer from the GitHub API: a QA run has no id")
    newest = max(runs, key=lambda run: run["id"])  # run ids only grow; a re-run keeps its id
    url = newest.get("html_url") or f"run {newest['id']}"
    if newest.get("status") != "completed":
        raise GateError(f"the newest QA run for {sha} has not finished yet ({url}). Wait for it to "
                        "succeed, then start the release again.")
    if newest.get("conclusion") != "success":
        raise GateError(f"there is no successful QA run for {sha}: the newest QA run for it ended "
                        f"with '{newest.get('conclusion')}' ({url}). An older successful QA run does "
                        "not count; run QA again with this SHA, test the APK, then start the release.")
    jobs = {job.get("name"): job for job in qa_jobs(repo, newest["id"])}
    for name in QA_REQUIRED_JOBS:
        job = jobs.get(name)
        conclusion = job.get("conclusion") if job else "missing"
        if conclusion != "success":
            raise GateError(f"the QA run {url} for {sha} did not build and verify the commit: its job "
                            f"'{name}' is {conclusion}. Run QA again with this SHA.")
    steps = {step.get("name"): step.get("conclusion") for step in jobs[QA_BUILD_JOB].get("steps") or []}
    if steps.get(QA_COMMIT_STEP) != "success":
        raise GateError(f"the QA run {url} did not confirm that it built {sha} (step "
                        f"'{QA_COMMIT_STEP}' is {steps.get(QA_COMMIT_STEP) or 'missing'}). Run QA again.")
    return url


def gate(stage: str, sha_input: str, repo: str, develop_ref: str = DEVELOP_REF) -> dict:
    sha = validate_sha(sha_input)
    check_on_develop(sha, develop_ref)
    if stage == "qa":
        url = successful_development_run(repo, sha)
        if not url:
            raise GateError(f"there is no successful Development run for {sha}. QA only builds "
                            "commits that passed the Development stage; run Actions > Development "
                            "for this commit first (it runs automatically for every push to develop).")
    elif stage == "release":
        url = verified_qa_run(repo, sha)
    else:
        raise GateError(f"unknown stage {stage}")
    return {"sha": sha, "run_url": url}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("stage", choices=["qa", "release"])
    parser.add_argument("--sha", required=True)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--github-output")
    args = parser.parse_args()
    try:
        result = gate(args.stage, args.sha, args.repo)
    except GateError as error:
        print(f"::error title={'QA' if args.stage == 'qa' else 'Release'} gate::{error}")
        return 1
    previous = "Development" if args.stage == "qa" else "QA"
    print(f"commit {result['sha']} is on develop; successful {previous} run: {result['run_url']}")
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as out:
            out.write(f"sha={result['sha']}\nrun_url={result['run_url']}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
