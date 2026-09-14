from app.observability import sentry


def test_init_sentry_is_a_no_op_when_no_dsn_is_configured(mocker):
    mocker.patch.object(sentry.settings, "sentry_dsn", None)
    init_mock = mocker.patch.object(sentry.sentry_sdk, "init")

    sentry.init_sentry()

    init_mock.assert_not_called()


def test_init_sentry_initializes_the_sdk_when_a_dsn_is_configured(mocker):
    mocker.patch.object(sentry.settings, "sentry_dsn", "https://example@o0.ingest.sentry.io/0")
    mocker.patch.object(sentry.settings, "sentry_traces_sample_rate", 0.5)
    init_mock = mocker.patch.object(sentry.sentry_sdk, "init")

    sentry.init_sentry()

    init_mock.assert_called_once()
    call_kwargs = init_mock.call_args.kwargs
    assert call_kwargs["dsn"] == "https://example@o0.ingest.sentry.io/0"
    assert call_kwargs["traces_sample_rate"] == 0.5
