.PHONY: setup up down test gateway-test python-test lint
setup:
	@test -f .env || cp .env.example .env
up:
	docker compose up --build
down:
	docker compose down
test: gateway-test python-test
gateway-test:
	cd gateway && mvn verify
python-test:
	python -m pytest autopr/tests eval-framework/tests
lint:
	cd gateway && mvn spotless:check
	python -m ruff check autopr eval-framework
