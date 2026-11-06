import unittest
from typing import Any, Dict

from scripts.observability.import_dashboard import (
    load_dashboard_definition,
    prepare_import_payload,
)
from scripts.observability.setup_uptime_monitoring import (
    build_synthetic_monitoring_specification,
)
from scripts.observability.verify_telemetry_delivery import (
    mask_secret,
    verify_otlp_delivery,
    verify_prometheus_delivery,
    verify_sentry_delivery,
)


class ObservabilityToolingTest(unittest.TestCase):

    def test_load_and_prepare_dashboard(self) -> None:
        dashboard = load_dashboard_definition()
        self.assertEqual(dashboard.get("uid"), "hittiguess-overview")
        self.assertGreater(len(dashboard.get("panels", [])), 0)

        payload = prepare_import_payload(dashboard)
        self.assertTrue(payload["overwrite"])
        self.assertEqual(payload["folderId"], 0)
        self.assertIn("dashboard", payload)

    def test_build_synthetic_monitoring_specification(self) -> None:
        target_url = "https://api.hittiguess.com/actuator/health"
        spec = build_synthetic_monitoring_specification(target_url)
        self.assertEqual(spec["target"], target_url)
        self.assertEqual(spec["job"], "hittiguess-core-uptime")
        self.assertEqual(spec["frequency"], 60000)
        self.assertEqual(spec["settings"]["http"]["validStatusCodes"], [200])

    def test_mask_secret(self) -> None:
        self.assertEqual(mask_secret(""), "[REDACTED]")
        self.assertEqual(mask_secret("short"), "[REDACTED]")
        masked = mask_secret("supersecretkey123456")
        self.assertTrue(masked.startswith("sup..."))
        self.assertTrue(masked.endswith("456 (length 20)"))

    def test_telemetry_delivery_credential_gated_skips_when_unset(self) -> None:
        sentry_status, sentry_reason = verify_sentry_delivery(None)
        self.assertEqual(sentry_status, "SKIPPED")
        self.assertIn("not configured", sentry_reason)

        otlp_status, otlp_reason = verify_otlp_delivery(None, None)
        self.assertEqual(otlp_status, "SKIPPED")
        self.assertIn("not configured", otlp_reason)

        prom_status, prom_reason = verify_prometheus_delivery(None, None, None)
        self.assertEqual(prom_status, "SKIPPED")
        self.assertIn("not configured", prom_reason)


if __name__ == "__main__":
    unittest.main()
