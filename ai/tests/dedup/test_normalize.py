from app.dedup.normalize import normalize_artist_and_title


def test_normalizes_case_and_whitespace():
    assert normalize_artist_and_title("  Daft   Punk ", "One More Time") == "daft punk one more time"


def test_strips_diacritics():
    assert normalize_artist_and_title("Beyoncé", "Déjà Vu") == "beyonce deja vu"


def test_strips_punctuation():
    assert normalize_artist_and_title("Guns N' Roses", "Don't Cry!") == "guns n roses don t cry"


def test_different_casing_and_punctuation_normalize_to_the_same_string():
    first = normalize_artist_and_title("The Weeknd", "Blinding Lights")
    second = normalize_artist_and_title("the WEEKND", "blinding-lights")
    assert first == second
