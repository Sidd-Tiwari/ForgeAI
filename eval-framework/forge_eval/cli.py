import json
from pathlib import Path

import typer
from rich.console import Console

from .runner import evaluate

app=typer.Typer(help="Run deterministic ForgeAI review evaluations.")

@app.callback()
def main() -> None:
    """Evaluate AI review findings against labeled datasets."""

@app.command()
def run(dataset: Path, minimum_score: float=typer.Option(0.75, min=0, max=1), output: Path|None=None) -> None:
    cases=json.loads(dataset.read_text(encoding="utf-8")); results=[evaluate(case).model_dump() for case in cases]
    aggregate=sum(r["score"] for r in results)/len(results) if results else 0
    report={"score":aggregate,"minimum_score":minimum_score,"passed":aggregate>=minimum_score,"results":results}
    if output: output.write_text(json.dumps(report,indent=2),encoding="utf-8")
    Console().print_json(data=report)
    if not report["passed"]: raise typer.Exit(1)
