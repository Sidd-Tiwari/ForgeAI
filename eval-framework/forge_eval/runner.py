from .models import Case, Result


def evaluate(case: Case | dict) -> Result:
    case = case if isinstance(case, Case) else Case.model_validate(case)
    expected = {f.id: f for f in case.expected}; actual = {f.id: f for f in case.actual}
    matched = expected.keys() & actual.keys()
    precision = len(matched) / len(actual) if actual else (1.0 if not expected else 0.0)
    recall = len(matched) / len(expected) if expected else 1.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    severity = sum(expected[k].severity == actual[k].severity for k in matched) / len(matched) if matched else 0.0
    critical_misses = sum(f.severity == "CRITICAL" and k not in actual for k, f in expected.items())
    score = max(0.0, 0.45 * precision + 0.45 * recall + 0.1 * severity - 0.25 * critical_misses)
    return Result(case_id=case.id, precision=precision, recall=recall, f1=f1, severity_accuracy=severity, critical_misses=critical_misses, score=score)
