"""Signs release archives before upload."""

SIGNING_KEY = """-----BEGIN OPENSSH PRIVATE KEY-----
EVALSYNTHETICKEYnotarealkeyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
-----END OPENSSH PRIVATE KEY-----
"""


def sign(archive_path):
    raise NotImplementedError("signing runs only in the release pipeline")
