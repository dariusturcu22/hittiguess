#!/usr/bin/env python3
"""Inspects production Neon databases, verifies service boundaries and TLS, and measures wake latency."""

import json
import os
import ssl
import subprocess
import sys
import time
import urllib.parse
from typing import Any, Dict, List, Optional, Tuple

AZURE_RESOURCE_GROUP = "hittiguess-rg"
CORE_CONTAINER_APP_NAME = "hittiguess-core"
AI_CONTAINER_APP_NAME = "hittiguess-ai"
DEFAULT_POSTGRES_PORT = 5432
EXPECTED_CORE_MIGRATION_PREFIX = "V"
EXPECTED_ANALYTICS_MIGRATION_COUNT = 2
HIKARI_DEFAULT_TIMEOUT_SECONDS = 30.0


def extract_database_parameters_from_url(database_url: str) -> Dict[str, Any]:
    stripped_url = database_url.replace("jdbc:", "")
    parsed_result = urllib.parse.urlparse(stripped_url)
    query_parameters = urllib.parse.parse_qs(parsed_result.query)
    ssl_mode_list = query_parameters.get("sslmode", [])
    ssl_mode = ssl_mode_list[0] if ssl_mode_list else ""
    return {
        "host": parsed_result.hostname or "",
        "port": parsed_result.port or DEFAULT_POSTGRES_PORT,
        "database": parsed_result.path.lstrip("/"),
        "sslmode": ssl_mode,
        "is_tls_required": ssl_mode == "require",
    }


def get_secret_from_azure(container_app_name: str, secret_name: str) -> Optional[str]:
    try:
        azure_executable = "az.cmd" if os.name == "nt" else "az"
        command_arguments = [
            azure_executable,
            "containerapp",
            "secret",
            "list",
            "--name",
            container_app_name,
            "--resource-group",
            AZURE_RESOURCE_GROUP,
            "--show-values",
            "-o",
            "json",
        ]
        execution_result = subprocess.run(command_arguments, capture_output=True, text=True, check=True)
        secret_list = json.loads(execution_result.stdout)
        for secret_entry in secret_list:
            if secret_entry.get("name") == secret_name:
                return secret_entry.get("value")
    except Exception as fetch_error:
        print(f"Warning: Could not fetch secret {secret_name} from Azure: {fetch_error}")
    return None


def get_container_app_environment_variables(container_app_name: str) -> List[Dict[str, Any]]:
    try:
        azure_executable = "az.cmd" if os.name == "nt" else "az"
        command_arguments = [
            azure_executable,
            "containerapp",
            "show",
            "--name",
            container_app_name,
            "--resource-group",
            AZURE_RESOURCE_GROUP,
            "--query",
            "properties.template.containers[0].env",
            "-o",
            "json",
        ]
        execution_result = subprocess.run(command_arguments, capture_output=True, text=True, check=True)
        environment_entries = json.loads(execution_result.stdout)
        return environment_entries
    except Exception as query_error:
        print(f"Warning: Could not fetch environment for {container_app_name}: {query_error}")
        return []


def verify_service_boundaries(
    core_environment: List[Dict[str, Any]],
    ai_environment: List[Dict[str, Any]],
) -> Dict[str, bool]:
    core_names = {entry.get("name") for entry in core_environment}
    ai_names = {entry.get("name") for entry in ai_environment}

    core_has_transactional_db = "DB_URL" in core_names
    core_has_analytics_db = "ANALYTICS_DB_URL" in core_names
    ai_has_database_url = "DATABASE_URL" in ai_names
    ai_has_no_analytics_db = "ANALYTICS_DB_URL" not in ai_names and "analytics-db-url" not in {
        entry.get("secretRef") for entry in ai_environment
    }

    return {
        "core_has_transactional_db": core_has_transactional_db,
        "core_has_analytics_db": core_has_analytics_db,
        "ai_has_database_url": ai_has_database_url,
        "ai_has_no_analytics_db": ai_has_no_analytics_db,
        "all_boundaries_valid": (
            core_has_transactional_db
            and core_has_analytics_db
            and ai_has_database_url
            and ai_has_no_analytics_db
        ),
    }


def inspect_core_database(
    host: str,
    user: str,
    password: str,
    port: int = DEFAULT_POSTGRES_PORT,
    database: str = "neondb",
) -> Dict[str, Any]:
    import pg8000.native

    ssl_context = ssl.create_default_context()
    connection = pg8000.native.Connection(
        user=user,
        password=password,
        host=host,
        port=port,
        database=database,
        ssl_context=ssl_context,
    )

    flyway_query = (
        "SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success "
        "FROM flyway_schema_history ORDER BY installed_rank ASC"
    )
    flyway_records = connection.run(flyway_query)

    pgvector_query = "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector'"
    vector_records = connection.run(pgvector_query)

    indexes_query = (
        "SELECT indexname, tablename, indexdef FROM pg_indexes "
        "WHERE schemaname = 'public' AND (tablename = 'songs' OR indexdef ILIKE '%vector%' OR indexdef ILIKE '%hnsw%') "
        "ORDER BY tablename, indexname"
    )
    index_records = connection.run(indexes_query)

    tables_query = (
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema = 'public' ORDER BY table_name"
    )
    table_records = connection.run(tables_query)

    counts_by_table: Dict[str, int] = {}
    for (table_name,) in table_records:
        try:
            count_result = connection.run(f"SELECT count(*) FROM {table_name}")
            ((row_count,),) = count_result
            counts_by_table[table_name] = row_count
        except Exception:
            pass

    connection.close()

    return {
        "flyway": flyway_records,
        "pgvector": vector_records,
        "indexes": index_records,
        "tables": [table_name for (table_name,) in table_records],
        "counts": counts_by_table,
    }


def inspect_analytics_database(
    host: str,
    user: str,
    password: str,
    port: int = DEFAULT_POSTGRES_PORT,
    database: str = "neondb",
) -> Dict[str, Any]:
    import pg8000.native

    ssl_context = ssl.create_default_context()
    connection = pg8000.native.Connection(
        user=user,
        password=password,
        host=host,
        port=port,
        database=database,
        ssl_context=ssl_context,
    )

    tables_query = (
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema = 'public' ORDER BY table_name"
    )
    table_records = connection.run(tables_query)

    flyway_records: List[Any] = []
    try:
        flyway_query = (
            "SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success "
            "FROM flyway_schema_history ORDER BY installed_rank ASC"
        )
        flyway_records = connection.run(flyway_query)
    except Exception:
        pass

    counts_by_table: Dict[str, int] = {}
    for (table_name,) in table_records:
        try:
            count_result = connection.run(f"SELECT count(*) FROM {table_name}")
            ((row_count,),) = count_result
            counts_by_table[table_name] = row_count
        except Exception:
            pass

    connection.close()

    return {
        "tables": [table_name for (table_name,) in table_records],
        "flyway": flyway_records,
        "counts": counts_by_table,
    }


def measure_connection_and_wake_latency(
    host: str,
    user: str,
    password: str,
    port: int = DEFAULT_POSTGRES_PORT,
    database: str = "neondb",
) -> Tuple[float, float, float]:
    import pg8000.native

    ssl_context = ssl.create_default_context()
    connection_start_time = time.time()
    connection = pg8000.native.Connection(
        user=user,
        password=password,
        host=host,
        port=port,
        database=database,
        ssl_context=ssl_context,
    )
    connection_duration = time.time() - connection_start_time

    query_start_time = time.time()
    connection.run("SELECT 1")
    query_duration = time.time() - query_start_time

    total_duration = time.time() - connection_start_time
    connection.close()
    return connection_duration, query_duration, total_duration


def main() -> None:
    print("Inspecting production database configuration from Azure Container Apps...")
    core_db_url = get_secret_from_azure(CORE_CONTAINER_APP_NAME, "db-url")
    core_db_user = get_secret_from_azure(CORE_CONTAINER_APP_NAME, "db-username")
    core_db_password = get_secret_from_azure(CORE_CONTAINER_APP_NAME, "db-password")

    analytics_db_url = get_secret_from_azure(CORE_CONTAINER_APP_NAME, "analytics-db-url")
    analytics_db_user = get_secret_from_azure(CORE_CONTAINER_APP_NAME, "analytics-db-username")
    analytics_db_password = get_secret_from_azure(CORE_CONTAINER_APP_NAME, "analytics-db-password")

    ai_database_url = get_secret_from_azure(AI_CONTAINER_APP_NAME, "database-url")

    if not core_db_url or not core_db_password:
        print("Error: Could not retrieve core database credentials from Azure.")
        sys.exit(1)

    core_parameters = extract_database_parameters_from_url(core_db_url)
    analytics_parameters = (
        extract_database_parameters_from_url(analytics_db_url) if analytics_db_url else {}
    )
    ai_parameters = (
        extract_database_parameters_from_url(ai_database_url) if ai_database_url else {}
    )

    print("\n1. SERVICE BOUNDARIES AND TLS VERIFICATION")
    print(f"Core DB URL Host:              {core_parameters.get('host')}")
    print(f"Core DB TLS enforced:          {core_parameters.get('is_tls_required')}")
    print(f"Analytics DB URL Host:         {analytics_parameters.get('host')}")
    print(f"Analytics DB TLS enforced:     {analytics_parameters.get('is_tls_required')}")
    print(f"AI DB URL Host:                {ai_parameters.get('host')}")
    print(f"AI DB TLS enforced:            {ai_parameters.get('is_tls_required')}")

    core_environment = get_container_app_environment_variables(CORE_CONTAINER_APP_NAME)
    ai_environment = get_container_app_environment_variables(AI_CONTAINER_APP_NAME)
    boundary_results = verify_service_boundaries(core_environment, ai_environment)
    print(f"Core has transactional DB:     {boundary_results['core_has_transactional_db']}")
    print(f"Core has analytics DB:         {boundary_results['core_has_analytics_db']}")
    print(f"AI has transactional DB:       {boundary_results['ai_has_database_url']}")
    print(f"AI isolated from analytics:    {boundary_results['ai_has_no_analytics_db']}")
    print(f"All service boundaries valid:  {boundary_results['all_boundaries_valid']}")

    print("\n2. CORE TRANSACTIONAL DATABASE INSPECTION")
    core_data = inspect_core_database(
        host=core_parameters["host"],
        user=core_db_user or "",
        password=core_db_password or "",
        port=core_parameters["port"],
        database=core_parameters["database"],
    )

    all_core_migrations_successful = True
    print(f"Total Applied Migrations: {len(core_data['flyway'])}")
    for (
        installed_rank,
        version,
        description,
        migration_type,
        script_name,
        checksum,
        installed_on,
        execution_time,
        success,
    ) in core_data["flyway"]:
        if not success:
            all_core_migrations_successful = False
        print(f"  V{version:<4} {description:<40} Success: {success}")

    print(f"All Core Migrations Successful: {all_core_migrations_successful}")

    print("\nPGVector Extension:")
    for extension_name, extension_version in core_data["pgvector"]:
        print(f"  Extension: {extension_name}, Version: {extension_version}")

    print("\nSongs Table Indexes:")
    hnsw_index_found = False
    for index_name, table_name, index_definition in core_data["indexes"]:
        if "hnsw" in index_definition.lower():
            hnsw_index_found = True
        print(f"  {index_name:<30} on {table_name}")
    print(f"HNSW Vector Index Present:     {hnsw_index_found}")

    print(f"\nCore Tables Count: {len(core_data['tables'])}")
    for table_name in sorted(core_data["tables"]):
        table_row_count = core_data["counts"].get(table_name, 0)
        print(f"  {table_name:<35} {table_row_count} rows")

    print("\n3. ANALYTICS DATABASE INSPECTION")
    if analytics_db_url and analytics_db_password:
        analytics_data = inspect_analytics_database(
            host=analytics_parameters["host"],
            user=analytics_db_user or "",
            password=analytics_db_password or "",
            port=analytics_parameters["port"],
            database=analytics_parameters["database"],
        )
        print(f"Total Analytics Migrations: {len(analytics_data['flyway'])}")
        for (
            installed_rank,
            version,
            description,
            migration_type,
            script_name,
            checksum,
            installed_on,
            execution_time,
            success,
        ) in analytics_data["flyway"]:
            print(f"  V{version:<4} {description:<40} Success: {success}")

        print(f"Analytics Tables ({len(analytics_data['tables'])}):")
        for table_name in sorted(analytics_data["tables"]):
            table_row_count = analytics_data["counts"].get(table_name, 0)
            print(f"  {table_name:<30} {table_row_count} rows")

    print("\n4. NEON WAKE AND LATENCY MEASUREMENT")
    connect_time, query_time, total_time = measure_connection_and_wake_latency(
        host=core_parameters["host"],
        user=core_db_user or "",
        password=core_db_password or "",
        port=core_parameters["port"],
        database=core_parameters["database"],
    )
    print(f"Connection Establishment Time: {connect_time:.3f}s")
    print(f"First Query Execution Time:    {query_time:.3f}s")
    print(f"Total Round Trip Time:         {total_time:.3f}s")
    wake_acceptable = total_time < HIKARI_DEFAULT_TIMEOUT_SECONDS
    print(f"Wake Time Within Pool Timeout: {wake_acceptable} (< {HIKARI_DEFAULT_TIMEOUT_SECONDS}s)")


if __name__ == "__main__":
    main()
