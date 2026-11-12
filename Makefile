SHELL := /bin/bash

.PHONY: up down reset logs test

up:
	./scripts/up.sh

down:
	docker compose down

reset:
	docker compose down -v

logs:
	docker compose logs -f

test:
	cd backend && mvn test
	cd frontend && npm test
