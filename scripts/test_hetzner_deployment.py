import unittest

from scripts.hetzner.rehearse_isolation import (
    parse_memory_string_to_mib,
    validate_alloy_configuration,
    validate_caddyfile,
    validate_compose_structure,
    validate_env_example,
)


class HetznerDeploymentTest(unittest.TestCase):

    def test_parse_memory_string_to_mib(self) -> None:
        self.assertEqual(parse_memory_string_to_mib("256M"), 256)
        self.assertEqual(parse_memory_string_to_mib("2048M"), 2048)
        self.assertEqual(parse_memory_string_to_mib("1G"), 1024)
        self.assertEqual(parse_memory_string_to_mib("2GiB"), 2048)
        self.assertEqual(parse_memory_string_to_mib("512MiB"), 512)

    def test_validate_compose_structure_valid(self) -> None:
        sample_compose = """
services:
  caddy:
    image: caddy:2.9-alpine
    ports:
      - "80:80"
      - "443:443"
    deploy:
      resources:
        limits:
          memory: 256M
  core:
    image: hittiguess-core:latest
    deploy:
      resources:
        limits:
          memory: 2048M
  ai:
    image: hittiguess-ai:latest
    deploy:
      resources:
        limits:
          memory: 1024M
  alloy:
    image: grafana/alloy:v1.19.2
    deploy:
      resources:
        limits:
          memory: 256M
"""
        result = validate_compose_structure(sample_compose)
        self.assertEqual(len(result["missing_services"]), 0)
        self.assertEqual(len(result["exposed_internal_services"]), 0)
        self.assertEqual(result["total_memory_limits_mib"], 3584)
        self.assertTrue(result["is_memory_budget_acceptable"])

    def test_validate_compose_structure_detects_exposed_internal_port(self) -> None:
        sample_compose = """
services:
  caddy:
    ports:
      - "80:80"
  core:
    ports:
      - "8080:8080"
  ai:
  alloy:
"""
        result = validate_compose_structure(sample_compose)
        self.assertIn("core", result["exposed_internal_services"])

    def test_validate_caddyfile_valid(self) -> None:
        sample_caddyfile = """
api.hittiguess.com {
    handle /caddy-health {
        respond "OK" 200
    }
    @forbidden_internal {
        path /actuator/prometheus
    }
    handle @forbidden_internal {
        respond "Forbidden" 403
    }
    handle {
        reverse_proxy core:8080
    }
}
"""
        result = validate_caddyfile(sample_caddyfile)
        self.assertTrue(result["is_caddyfile_valid"])
        self.assertTrue(result["has_caddy_health"])
        self.assertTrue(result["has_prometheus_block"])
        self.assertTrue(result["has_forbidden_response"])
        self.assertTrue(result["has_core_proxy"])

    def test_validate_caddyfile_missing_prometheus_block(self) -> None:
        sample_caddyfile = """
api.hittiguess.com {
    handle /caddy-health {
        respond "OK" 200
    }
    handle {
        reverse_proxy core:8080
    }
}
"""
        result = validate_caddyfile(sample_caddyfile)
        self.assertFalse(result["is_caddyfile_valid"])
        self.assertFalse(result["has_prometheus_block"])

    def test_validate_alloy_configuration_valid(self) -> None:
        sample_alloy = """
prometheus.scrape "backend" {
    targets = [{"__address__" = "core:8080"}]
    metrics_path = "/actuator/prometheus"
}
prometheus.scrape "ai_service" {
    targets = [{"__address__" = "ai:8000"}]
    metrics_path = "/metrics"
    http_headers = {"X-Internal-Api-Key" = ["secret"]}
}
prometheus.remote_write "grafana_cloud" {
    endpoint { url = "https://prometheus" }
}
"""
        result = validate_alloy_configuration(sample_alloy)
        self.assertTrue(result["is_alloy_valid"])
        self.assertTrue(result["scrapes_core_target"])
        self.assertTrue(result["scrapes_ai_target"])
        self.assertTrue(result["uses_ai_auth_header"])
        self.assertTrue(result["remote_writes_grafana"])

    def test_validate_env_example_complete(self) -> None:
        sample_env = """
API_DOMAIN=api.hittiguess.com
APP_ENV=prod
FRONTEND_URL=https://www.hittiguess.com
DB_URL=jdbc:postgresql://host/neondb
ANALYTICS_DB_URL=jdbc:postgresql://analytics/neondb
AI_DATABASE_URL=postgresql://host/neondb
JWT_SECRET=secret
INTERNAL_SERVICE_API_KEY=key
OPENAI_API_KEY=openai
DEEPINFRA_API_KEY=deepinfra
YOUTUBE_API_KEY=youtube
GRAFANA_CLOUD_PROMETHEUS_URL=https://prometheus
"""
        result = validate_env_example(sample_env)
        self.assertTrue(result["is_env_complete"])
        self.assertEqual(len(result["missing_essential_keys"]), 0)


if __name__ == "__main__":
    unittest.main()
