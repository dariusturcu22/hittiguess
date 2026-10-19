from openai import OpenAI

from app.config import settings

DEEPINFRA_BASE_URL = "https://api.deepinfra.com/v1/openai"

# A hung request otherwise waits forever, the SDK sets no default timeout of
# its own: the real fix for a provider-side model hang the validating spike
# found (ai/spikes/openai_compatible_spike.py).
REQUEST_TIMEOUT_SECONDS = 90.0

client = OpenAI(api_key=settings.deepinfra_api_key, base_url=DEEPINFRA_BASE_URL, timeout=REQUEST_TIMEOUT_SECONDS)
