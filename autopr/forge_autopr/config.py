from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    gateway_url: str = "http://localhost:8080"
    github_token: str = ""
    github_webhook_secret: str
    forgeai_internal_token: str
    max_diff_bytes: int = 200_000
    review_model: str = "claude-sonnet-4-20250514"
