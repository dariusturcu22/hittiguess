import unittest
from typing import Any, Dict, List

from scripts.inspect_databases import (
    extract_database_parameters_from_url,
    verify_service_boundaries,
)


class InspectDatabasesTest(unittest.TestCase):

    def test_extract_database_parameters_with_jdbc_and_sslmode(self) -> None:
        url = "jdbc:postgresql://ep-silent-frost-b2i3fnwt.c-6.eu-central-1.aws.neon.tech:5432/neondb?sslmode=require"
        parameters = extract_database_parameters_from_url(url)
        self.assertEqual(parameters["host"], "ep-silent-frost-b2i3fnwt.c-6.eu-central-1.aws.neon.tech")
        self.assertEqual(parameters["port"], 5432)
        self.assertEqual(parameters["database"], "neondb")
        self.assertEqual(parameters["sslmode"], "require")
        self.assertTrue(parameters["is_tls_required"])

    def test_extract_database_parameters_plain_url_without_sslmode(self) -> None:
        url = "postgresql://localhost:5433/testdb"
        parameters = extract_database_parameters_from_url(url)
        self.assertEqual(parameters["host"], "localhost")
        self.assertEqual(parameters["port"], 5433)
        self.assertEqual(parameters["database"], "testdb")
        self.assertEqual(parameters["sslmode"], "")
        self.assertFalse(parameters["is_tls_required"])

    def test_verify_service_boundaries_valid(self) -> None:
        core_environment: List[Dict[str, Any]] = [
            {"name": "DB_URL", "secretRef": "db-url"},
            {"name": "ANALYTICS_DB_URL", "secretRef": "analytics-db-url"},
        ]
        ai_environment: List[Dict[str, Any]] = [
            {"name": "DATABASE_URL", "secretRef": "database-url"},
            {"name": "OPENAI_API_KEY", "secretRef": "openai-api-key"},
        ]
        result = verify_service_boundaries(core_environment, ai_environment)
        self.assertTrue(result["all_boundaries_valid"])
        self.assertTrue(result["core_has_transactional_db"])
        self.assertTrue(result["core_has_analytics_db"])
        self.assertTrue(result["ai_has_database_url"])
        self.assertTrue(result["ai_has_no_analytics_db"])

    def test_verify_service_boundaries_leaked_analytics_to_ai(self) -> None:
        core_environment: List[Dict[str, Any]] = [
            {"name": "DB_URL", "secretRef": "db-url"},
            {"name": "ANALYTICS_DB_URL", "secretRef": "analytics-db-url"},
        ]
        ai_environment: List[Dict[str, Any]] = [
            {"name": "DATABASE_URL", "secretRef": "database-url"},
            {"name": "ANALYTICS_DB_URL", "secretRef": "analytics-db-url"},
        ]
        result = verify_service_boundaries(core_environment, ai_environment)
        self.assertFalse(result["all_boundaries_valid"])
        self.assertFalse(result["ai_has_no_analytics_db"])


if __name__ == "__main__":
    unittest.main()
