from pydantic import BaseModel, Field


class Finding(BaseModel):
    path: str
    line: int | None = None
    severity: str
    title: str
    explanation: str
    recommendation: str

class Review(BaseModel):
    summary: str
    risk_score: float = Field(ge=0, le=10)
    risk_level: str
    categories: dict[str, float]
    findings: list[Finding]
