from app.observability import error_reporting


def test_report_source_failure_captures_the_exception(mocker):
    capture_mock = mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")
    error = RuntimeError("musicbrainz is down")

    error_reporting.report_source_failure("musicbrainz", error, title="Song", artist="Artist")

    capture_mock.assert_called_once_with(error)


def test_report_openai_failure_captures_the_exception(mocker):
    capture_mock = mocker.patch.object(error_reporting.sentry_sdk, "capture_exception")
    error = RuntimeError("OpenAI is down")

    error_reporting.report_openai_failure(error)

    capture_mock.assert_called_once_with(error)
