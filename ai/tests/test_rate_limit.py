from app.rate_limit import SlidingWindowRateLimiter

MAX_REQUESTS_PER_WINDOW = 3
LONG_WINDOW_SECONDS = 600.0


def test_requests_up_to_and_including_the_limit_are_allowed():
    limiter = SlidingWindowRateLimiter(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW_SECONDS)

    for _ in range(MAX_REQUESTS_PER_WINDOW):
        assert limiter.allow("same-key") is True


def test_the_request_one_over_the_limit_is_rejected():
    limiter = SlidingWindowRateLimiter(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW_SECONDS)

    for _ in range(MAX_REQUESTS_PER_WINDOW):
        limiter.allow("same-key")

    assert limiter.allow("same-key") is False


def test_different_keys_are_rate_limited_independently():
    limiter = SlidingWindowRateLimiter(MAX_REQUESTS_PER_WINDOW, LONG_WINDOW_SECONDS)

    for _ in range(MAX_REQUESTS_PER_WINDOW):
        limiter.allow("key-one")
    assert limiter.allow("key-one") is False

    assert limiter.allow("key-two") is True
