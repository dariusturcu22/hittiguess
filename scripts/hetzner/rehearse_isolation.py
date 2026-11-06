#!/usr/bin/env python3
"""Rehearses and validates the Hetzner deployment configuration in isolation."""

import pathlib
import sys
from typing import Any, Dict, List, Set

CURRENT_SCRIPT_DIRECTORY = pathlib.Path(__file__).resolve().parent
COMPOSE_FILE_PATH = CURRENT_SCRIPT_DIRECTORY / "docker-compose.yml"
CADDYFILE_PATH = CURRENT_SCRIPT_DIRECTORY / "Caddyfile"
ALLOY_CONFIG_PATH = CURRENT_SCRIPT_DIRECTORY / "config.alloy"
ENV_EXAMPLE_PATH = CURRENT_SCRIPT_DIRECTORY / ".env.hetzner.example"

REQUIRED_SERVICES = {"caddy", "core", "ai", "alloy"}
PERMITTED_HOST_PORTS = {"80:80", "443:443", "443:443/udp"}
INTERNAL_NETWORK_NAME = "internal_network"
MAX_TOTAL_MEMORY_BUDGET_MIB = 4096
HTTP_FORBIDDEN_STATUS = 403


def parse_memory_string_to_mib(memory_string: str) -> int:
    clean_memory = memory_string.strip()
    if clean_memory.endswith("M"):
        return int(clean_memory.rstrip("M"))
    if clean_memory.endswith("G"):
        return int(clean_memory.rstrip("G")) * 1024
    if clean_memory.endswith("MiB"):
        return int(clean_memory.rstrip("MiB"))
    if clean_memory.endswith("GiB"):
        return int(clean_memory.rstrip("GiB")) * 1024
    return int(clean_memory)


def validate_compose_structure(compose_content: str) -> Dict[str, Any]:
    lines = compose_content.splitlines()
    found_services: Set[str] = set()
    services_with_ports: Dict[str, List[str]] = {}
    service_memory_limits: Dict[str, int] = {}
    current_service_name = ""
    in_services_block = False
    in_limits_block = False

    for raw_line in lines:
        stripped_line = raw_line.strip()
        indentation = len(raw_line) - len(raw_line.lstrip(" "))

        if raw_line.startswith("services:"):
            in_services_block = True
            continue

        if in_services_block and indentation == 2 and stripped_line.endswith(":"):
            service_candidate = stripped_line.rstrip(":")
            if not service_candidate.startswith("#"):
                current_service_name = service_candidate
                found_services.add(current_service_name)
                services_with_ports[current_service_name] = []
                in_limits_block = False
            continue

        if indentation == 0 and not raw_line.startswith("services:") and stripped_line.endswith(":"):
            in_services_block = False
            current_service_name = ""
            in_limits_block = False

        if "limits:" in stripped_line:
            in_limits_block = True
            continue

        if "reservations:" in stripped_line:
            in_limits_block = False
            continue

        if current_service_name and in_limits_block and "memory:" in stripped_line:
            tokens = stripped_line.split(":")
            if len(tokens) == 2:
                field_label, memory_value = tokens
                if field_label.strip() == "memory":
                    parsed_mib = parse_memory_string_to_mib(memory_value.strip())
                    service_memory_limits[current_service_name] = parsed_mib

        if current_service_name and stripped_line.startswith("- \"") and ":" in stripped_line:
            port_entry = stripped_line.strip("- \"")
            services_with_ports[current_service_name].append(port_entry)

    missing_services = REQUIRED_SERVICES - found_services
    exposed_internal_services = [
        service_name
        for service_name, port_list in services_with_ports.items()
        if service_name != "caddy" and port_list
    ]

    total_memory_limits = sum(service_memory_limits.values())

    return {
        "found_services": found_services,
        "missing_services": missing_services,
        "exposed_internal_services": exposed_internal_services,
        "caddy_ports": services_with_ports.get("caddy", []),
        "service_memory_limits": service_memory_limits,
        "total_memory_limits_mib": total_memory_limits,
        "is_memory_budget_acceptable": total_memory_limits <= MAX_TOTAL_MEMORY_BUDGET_MIB,
    }


def validate_caddyfile(caddyfile_content: str) -> Dict[str, bool]:
    has_caddy_health = "/caddy-health" in caddyfile_content
    has_prometheus_block = "/actuator/prometheus" in caddyfile_content
    has_forbidden_response = f'respond "Forbidden" {HTTP_FORBIDDEN_STATUS}' in caddyfile_content
    has_core_proxy = "reverse_proxy core:8080" in caddyfile_content

    all_valid = (
        has_caddy_health
        and has_prometheus_block
        and has_forbidden_response
        and has_core_proxy
    )
    return {
        "has_caddy_health": has_caddy_health,
        "has_prometheus_block": has_prometheus_block,
        "has_forbidden_response": has_forbidden_response,
        "has_core_proxy": has_core_proxy,
        "is_caddyfile_valid": all_valid,
    }


def validate_alloy_configuration(alloy_content: str) -> Dict[str, bool]:
    scrapes_core_target = '"core:8080"' in alloy_content
    scrapes_core_prometheus_path = '"/actuator/prometheus"' in alloy_content
    scrapes_ai_target = '"ai:8000"' in alloy_content
    scrapes_ai_metrics_path = '"/metrics"' in alloy_content
    uses_ai_auth_header = '"X-Internal-Api-Key"' in alloy_content
    remote_writes_grafana = 'prometheus.remote_write "grafana_cloud"' in alloy_content

    all_valid = (
        scrapes_core_target
        and scrapes_core_prometheus_path
        and scrapes_ai_target
        and scrapes_ai_metrics_path
        and uses_ai_auth_header
        and remote_writes_grafana
    )
    return {
        "scrapes_core_target": scrapes_core_target,
        "scrapes_core_prometheus_path": scrapes_core_prometheus_path,
        "scrapes_ai_target": scrapes_ai_target,
        "scrapes_ai_metrics_path": scrapes_ai_metrics_path,
        "uses_ai_auth_header": uses_ai_auth_header,
        "remote_writes_grafana": remote_writes_grafana,
        "is_alloy_valid": all_valid,
    }


def validate_env_example(env_content: str) -> Dict[str, Any]:
    lines = env_content.splitlines()
    parsed_variable_names: Set[str] = set()

    for raw_line in lines:
        stripped_line = raw_line.strip()
        if stripped_line and not stripped_line.startswith("#") and "=" in stripped_line:
            variable_name, assignment_value = stripped_line.split("=", 1)
            parsed_variable_names.add(variable_name.strip())

    essential_keys = {
        "API_DOMAIN",
        "APP_ENV",
        "FRONTEND_URL",
        "DB_URL",
        "ANALYTICS_DB_URL",
        "AI_DATABASE_URL",
        "JWT_SECRET",
        "INTERNAL_SERVICE_API_KEY",
        "OPENAI_API_KEY",
        "DEEPINFRA_API_KEY",
        "YOUTUBE_API_KEY",
        "GRAFANA_CLOUD_PROMETHEUS_URL",
    }
    missing_essential_keys = essential_keys - parsed_variable_names
    return {
        "parsed_keys_count": len(parsed_variable_names),
        "missing_essential_keys": missing_essential_keys,
        "is_env_complete": len(missing_essential_keys) == 0,
    }


def main() -> None:
    print("================================================================================")
    print("HETZNER DEPLOYMENT ISOLATION REHEARSAL")
    print("================================================================================")

    print("\n1. Validating docker-compose.yml...")
    compose_content = COMPOSE_FILE_PATH.read_text(encoding="utf-8")
    compose_results = validate_compose_structure(compose_content)
    print(f"  Services discovered:        {sorted(compose_results['found_services'])}")
    print(f"  Missing required services:  {compose_results['missing_services'] or 'None'}")
    print(f"  Exposed internal services:  {compose_results['exposed_internal_services'] or 'None (Strictly isolated)'}")
    print(f"  Total memory limits:        {compose_results['total_memory_limits_mib']} MiB (Budget <= {MAX_TOTAL_MEMORY_BUDGET_MIB} MiB)")
    if compose_results["missing_services"] or compose_results["exposed_internal_services"]:
        print("  Error: Docker Compose configuration validation failed.")
        sys.exit(1)

    print("\n2. Validating Caddy reverse proxy configuration...")
    caddy_content = CADDYFILE_PATH.read_text(encoding="utf-8")
    caddy_results = validate_caddyfile(caddy_content)
    print(f"  Caddy healthcheck defined:  {caddy_results['has_caddy_health']}")
    print(f"  Prometheus block defined:   {caddy_results['has_prometheus_block']}")
    print(f"  Forbidden response (403):   {caddy_results['has_forbidden_response']}")
    print(f"  Core reverse proxy defined: {caddy_results['has_core_proxy']}")
    if not caddy_results["is_caddyfile_valid"]:
        print("  Error: Caddyfile configuration validation failed.")
        sys.exit(1)

    print("\n3. Validating Grafana Alloy configuration...")
    alloy_content = ALLOY_CONFIG_PATH.read_text(encoding="utf-8")
    alloy_results = validate_alloy_configuration(alloy_content)
    print(f"  Scrapes Core target:        {alloy_results['scrapes_core_target']}")
    print(f"  Scrapes AI target:          {alloy_results['scrapes_ai_target']}")
    print(f"  Authenticates to AI:        {alloy_results['uses_ai_auth_header']}")
    print(f"  Remote writes to Grafana:   {alloy_results['remote_writes_grafana']}")
    if not alloy_results["is_alloy_valid"]:
        print("  Error: Alloy configuration validation failed.")
        sys.exit(1)

    print("\n4. Validating environment secrets template...")
    env_content = ENV_EXAMPLE_PATH.read_text(encoding="utf-8")
    env_results = validate_env_example(env_content)
    print(f"  Documented configuration keys: {env_results['parsed_keys_count']}")
    print(f"  Missing essential keys:        {env_results['missing_essential_keys'] or 'None'}")
    if not env_results["is_env_complete"]:
        print("  Error: Environment template validation failed.")
        sys.exit(1)

    print("\n================================================================================")
    print("ALL REHEARSAL CHECKS PASSED (Isolated deployment specification verified)")
    print("================================================================================")


if __name__ == "__main__":
    main()
