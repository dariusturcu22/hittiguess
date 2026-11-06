from datetime import datetime, timedelta, timezone
from email.utils import format_datetime

import httpx
import pytest

from app.metadata.sources import http_retry
from app.metadata.sources.http_retry import MAX_RETRY_AFTER_SECONDS, get_with_backoff, parse_retry_after_seconds

URL = "https://example.test/resource"
BASE_DELAY_SECONDS = 1.0
HTTP_DATE_OFFSET_SECONDS = 30
HTTP_DATE_TOLERANCE_SECONDS = 5
HUGE_RETRY_AFTER_SECONDS = 86400


def _response(status_code: int, headers: dict | None = None) -> httpx.Response:
    return httpx.Response(status_code, headers=headers, request=httpx.Request("GET", URL))


def _http_date_in_future() -> str:
    retry_at = datetime.now(timezone.utc) + timedelta(seconds=HTTP_DATE_OFFSET_SECONDS)
    return format_datetime(retry_at, usegmt=True)


@pytest.fixture
def sleep(mocker):
    return mocker.patch.object(http_retry.time, "sleep")


def _slept_seconds(sleep) -> list[float]:
    return [call.args[0] for call in sleep.call_args_list]


def test_parse_delta_seconds():
    assert parse_retry_after_seconds("7") == 7.0


def test_parse_http_date_returns_seconds_until_then():
    parsed = parse_retry_after_seconds(_http_date_in_future())
    assert parsed is not None
    assert abs(parsed - HTTP_DATE_OFFSET_SECONDS) <= HTTP_DATE_TOLERANCE_SECONDS


def test_parse_http_date_in_the_past_is_zero():
    assert parse_retry_after_seconds("Wed, 21 Oct 2015 07:28:00 GMT") == 0.0


@pytest.mark.parametrize("header", [None, "", "soon", "nan"])
def test_parse_unusable_values_return_none(header):
    assert parse_retry_after_seconds(header) is None


def test_parse_negative_is_zero():
    assert parse_retry_after_seconds("-5") == 0.0


def test_parse_caps_huge_values():
    assert parse_retry_after_seconds(str(HUGE_RETRY_AFTER_SECONDS)) == MAX_RETRY_AFTER_SECONDS
    assert parse_retry_after_seconds("inf") == MAX_RETRY_AFTER_SECONDS


def test_http_date_retry_after_backs_off_instead_of_raising(mocker, sleep):
    throttled = _response(429, {"Retry-After": _http_date_in_future()})
    success = _response(200)
    mocker.patch.object(http_retry.httpx, "get", side_effect=[throttled, success])

    result = get_with_backoff(URL, timeout=1, base_delay_seconds=BASE_DELAY_SECONDS)

    assert result is success
    (slept_seconds,) = _slept_seconds(sleep)
    assert abs(slept_seconds - HTTP_DATE_OFFSET_SECONDS) <= HTTP_DATE_TOLERANCE_SECONDS


def test_unparseable_retry_after_falls_back_to_backoff(mocker, sleep):
    throttled = _response(503, {"Retry-After": "later"})
    mocker.patch.object(http_retry.httpx, "get", side_effect=[throttled, _response(200)])

    get_with_backoff(URL, timeout=1, base_delay_seconds=BASE_DELAY_SECONDS)

    assert _slept_seconds(sleep) == [BASE_DELAY_SECONDS]


def test_huge_retry_after_is_capped(mocker, sleep):
    throttled = _response(429, {"Retry-After": str(HUGE_RETRY_AFTER_SECONDS)})
    mocker.patch.object(http_retry.httpx, "get", side_effect=[throttled, _response(200)])

    get_with_backoff(URL, timeout=1, base_delay_seconds=BASE_DELAY_SECONDS)

    assert _slept_seconds(sleep) == [MAX_RETRY_AFTER_SECONDS]


def test_no_sleep_after_the_final_attempt(mocker, sleep):
    max_retries = 3
    mocker.patch.object(http_retry.httpx, "get", side_effect=[_response(429)] * max_retries)

    with pytest.raises(httpx.HTTPStatusError):
        get_with_backoff(URL, timeout=1, max_retries=max_retries, base_delay_seconds=BASE_DELAY_SECONDS)

    assert len(_slept_seconds(sleep)) == max_retries - 1


def test_no_sleep_after_the_final_transport_error(mocker, sleep):
    max_retries = 2
    mocker.patch.object(http_retry.httpx, "get", side_effect=httpx.ConnectError("down"))

    with pytest.raises(httpx.ConnectError):
        get_with_backoff(URL, timeout=1, max_retries=max_retries, base_delay_seconds=BASE_DELAY_SECONDS)

    assert len(_slept_seconds(sleep)) == max_retries - 1


def test_final_http_error_wins_over_earlier_transport_error(mocker, sleep):
    mocker.patch.object(http_retry.httpx, "get", side_effect=[httpx.ConnectError("down"), _response(429)])

    with pytest.raises(httpx.HTTPStatusError) as raised:
        get_with_backoff(URL, timeout=1, max_retries=2, base_delay_seconds=BASE_DELAY_SECONDS)

    assert raised.value.response.status_code == 429


def test_final_transport_error_wins_over_earlier_http_error(mocker, sleep):
    final_error = httpx.ReadTimeout("slow")
    mocker.patch.object(http_retry.httpx, "get", side_effect=[_response(429), final_error])

    with pytest.raises(httpx.ReadTimeout) as raised:
        get_with_backoff(URL, timeout=1, max_retries=2, base_delay_seconds=BASE_DELAY_SECONDS)

    assert raised.value is final_error


def test_on_retry_called_for_each_failure(mocker, sleep):
    on_retry = mocker.Mock()
    mocker.patch.object(http_retry.httpx, "get", side_effect=[_response(429), _response(200)])

    get_with_backoff(URL, timeout=1, base_delay_seconds=BASE_DELAY_SECONDS, on_retry=on_retry)

    on_retry.assert_called_once()
