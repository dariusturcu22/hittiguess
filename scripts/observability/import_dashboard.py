#!/usr/bin/env python3
"""Imports the hittiguess overview dashboard into Grafana Cloud."""

import json
import os
import pathlib
import sys
import urllib.error
import urllib.request
from typing import Any, Dict, Optional, Tuple

DASHBOARD_FILE_PATH = (
    pathlib.Path(__file__).resolve().parents[2]
    / "observability"
    / "grafana"
    / "hittiguess-overview-dashboard.json"
)
DEFAULT_HTTP_TIMEOUT_SECONDS = 15.0
HTTP_OK_STATUS = 200


def load_dashboard_definition() -> Dict[str, Any]:
    file_content = DASHBOARD_FILE_PATH.read_text(encoding="utf-8")
    dashboard_json: Dict[str, Any] = json.loads(file_content)
    return dashboard_json


def prepare_import_payload(dashboard_data: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "dashboard": dashboard_data,
        "overwrite": True,
        "folderId": 0,
        "message": "Imported via hittiguess automated deployment tooling",
    }


def import_dashboard_to_grafana(
    grafana_url: str,
    api_token: str,
    dashboard_payload: Dict[str, Any],
) -> Tuple[bool, str]:
    normalized_url = grafana_url.rstrip("/")
    endpoint_url = f"{normalized_url}/api/dashboards/db"

    serialized_data = json.dumps(dashboard_payload).encode("utf-8")
    request = urllib.request.Request(
        endpoint_url,
        data=serialized_data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_token}",
            "Accept": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=DEFAULT_HTTP_TIMEOUT_SECONDS) as response:
            status_code = response.getcode()
            response_body = response.read().decode("utf-8")
            if status_code == HTTP_OK_STATUS:
                parsed_response = json.loads(response_body)
                dashboard_url = parsed_response.get("url", "")
                full_dashboard_url = f"{normalized_url}{dashboard_url}"
                return True, full_dashboard_url
            return False, f"Unexpected HTTP status {status_code}: {response_body}"
    except urllib.error.HTTPError as http_error:
        error_body = http_error.read().decode("utf-8")
        return False, f"Grafana API error ({http_error.code}): {error_body}"
    except Exception as connection_error:
        return False, f"Connection failed: {connection_error}"


def main() -> None:
    print("================================================================================")
    print("GRAFANA CLOUD DASHBOARD IMPORT")
    print("================================================================================")

    dashboard_data = load_dashboard_definition()
    dashboard_title = dashboard_data.get("title", "Unknown")
    dashboard_uid = dashboard_data.get("uid", "Unknown")
    panel_count = len(dashboard_data.get("panels", []))
    print(f"Loaded dashboard: '{dashboard_title}' (uid: {dashboard_uid}, panels: {panel_count})")

    grafana_url = os.environ.get("GRAFANA_URL", "").strip()
    api_token = os.environ.get("GRAFANA_API_KEY", "").strip() or os.environ.get(
        "GRAFANA_SERVICE_ACCOUNT_TOKEN", ""
    ).strip()

    if not grafana_url or not api_token:
        print("\nNote: GRAFANA_URL and GRAFANA_API_KEY environment variables are unset.")
        print("To import into a live Grafana instance, provide:")
        print("  GRAFANA_URL=https://<your-org>.grafana.net")
        print("  GRAFANA_API_KEY=<service-account-token>")
        print("Dashboard JSON definition in observability/grafana is valid and ready for import.")
        sys.exit(0)

    print(f"\nImporting dashboard to {grafana_url}...")
    import_payload = prepare_import_payload(dashboard_data)
    success, message = import_dashboard_to_grafana(grafana_url, api_token, import_payload)

    if success:
        print(f"Dashboard import succeeded. URL: {message}")
    else:
        print(f"Dashboard import failed: {message}")
        sys.exit(1)


if __name__ == "__main__":
    main()
