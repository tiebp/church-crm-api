SHELL := /bin/bash

KEYCLOAK_CONTAINER ?= keycloak
KEYCLOAK_URL ?= http://localhost:8080
KEYCLOAK_ADMIN_USERNAME ?= admin
KEYCLOAK_ADMIN_PASSWORD ?= admin

GREEN := $(shell printf '\033[0;32m')
YELLOW := $(shell printf '\033[0;33m')
RED := $(shell printf '\033[0;31m')
RESET := $(shell printf '\033[0m')

.PHONY: up down configure-keycloak test

up: down ## Start Keycloak and disable the HTTPS requirement
	@echo "$(GREEN)Starting Keycloak environment...$(RESET)"
	@docker compose up -d keycloak
	@echo "$(YELLOW)Waiting for Keycloak to be ready...$(RESET)"
	@for i in $$(seq 1 60); do \
		curl -sf $(KEYCLOAK_URL)/realms/master >/dev/null 2>&1 && \
		echo "$(GREEN)Keycloak is ready$(RESET)" && break; \
		echo "Waiting... ($$i/60)"; \
		sleep 2; \
	done
	@curl -sf $(KEYCLOAK_URL)/realms/master >/dev/null 2>&1 || \
		{ echo "$(RED)Keycloak failed to start$(RESET)"; exit 1; }
	@echo "$(YELLOW)Configuring Keycloak master realm to disable HTTPS requirement...$(RESET)"
	@$(MAKE) configure-keycloak
	@echo "$(GREEN)Keycloak configured (sslRequired=NONE)$(RESET)"
	@docker compose up terraform --build

configure-keycloak: ## Disable sslRequired on the master realm
	@docker exec $(KEYCLOAK_CONTAINER) /opt/keycloak/bin/kcadm.sh config credentials \
		--server http://localhost:8080 --realm master \
		--user=$(KEYCLOAK_ADMIN_USERNAME) --password=$(KEYCLOAK_ADMIN_PASSWORD) \
		|| { echo "$(RED)Failed to authenticate with $(KEYCLOAK_CONTAINER)$(RESET)"; exit 1; }
	@docker exec $(KEYCLOAK_CONTAINER) /opt/keycloak/bin/kcadm.sh update realms/master \
		-s sslRequired=NONE || { echo "$(RED)Failed to update $(KEYCLOAK_CONTAINER) master realm$(RESET)"; exit 1; }

down: ## Stop and remove the containers
	@docker compose down
	@docker image ls | grep church-crm | awk '{print $$3}' | xargs docker rmi -f || true

test: docker-up
	mvn clean compile verify
