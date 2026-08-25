from forge_eval import evaluate

def test_perfect_review():
    result=evaluate({"id":"one","expected":[{"id":"bug","severity":"HIGH"}],"actual":[{"id":"bug","severity":"HIGH"}]})
    assert result.score == 1
    assert result.critical_misses == 0

def test_critical_miss_is_penalized():
    result=evaluate({"id":"one","expected":[{"id":"bug","severity":"CRITICAL"}],"actual":[]})
    assert result.score == 0
    assert result.critical_misses == 1
