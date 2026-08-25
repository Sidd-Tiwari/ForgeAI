from pydantic import BaseModel, Field


class Finding(BaseModel):
    id: str
    severity: str

class Case(BaseModel):
    id: str
    expected: list[Finding]
    actual: list[Finding]

class Result(BaseModel):
    case_id: str
    precision: float
    recall: float
    f1: float
    severity_accuracy: float
    critical_misses: int
    score: float = Field(ge=0, le=1)
