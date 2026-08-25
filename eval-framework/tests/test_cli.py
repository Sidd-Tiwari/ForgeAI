import json

from typer.testing import CliRunner

from forge_eval.cli import app

runner = CliRunner()


def test_cli_run_passes_valid_dataset(tmp_path):
    dataset = tmp_path / "dataset.json"
    dataset.write_text(json.dumps([{
        "id": "case", "expected": [{"id": "bug", "severity": "HIGH"}],
        "actual": [{"id": "bug", "severity": "HIGH"}],
    }]), encoding="utf-8")
    result = runner.invoke(app, ["run", str(dataset), "--minimum-score", "0.9"])
    assert result.exit_code == 0
    assert '"passed": true' in result.stdout


def test_cli_run_rejects_missing_dataset(tmp_path):
    result = runner.invoke(app, ["run", str(tmp_path / "missing.json")])
    assert result.exit_code != 0


def test_cli_run_rejects_invalid_case_schema(tmp_path):
    dataset = tmp_path / "invalid.json"
    dataset.write_text('[{"id":"missing-fields"}]', encoding="utf-8")
    result = runner.invoke(app, ["run", str(dataset)])
    assert result.exit_code != 0


def test_cli_run_fails_quality_gate(tmp_path):
    dataset = tmp_path / "regression.json"
    dataset.write_text(json.dumps([{
        "id": "critical-miss", "expected": [{"id": "auth", "severity": "CRITICAL"}],
        "actual": [],
    }]), encoding="utf-8")
    result = runner.invoke(app, ["run", str(dataset), "--minimum-score", "0.9"])
    assert result.exit_code == 1
    assert '"passed": false' in result.stdout
