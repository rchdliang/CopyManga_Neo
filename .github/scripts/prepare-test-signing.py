"""Restore existing signing material on a disposable CI runner without logging it."""
import base64
import os
from pathlib import Path
import sys


REQUIRED = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)


def java_property(value):
    """Escape every UTF-16 code unit, including whitespace and property delimiters."""
    encoded = value.encode("utf-16-be")
    return "".join(f"\\u{int.from_bytes(encoded[i:i + 2], 'big'):04x}"
                   for i in range(0, len(encoded), 2))


def prepare(env):
    if any(not env.get(name, "").strip() for name in REQUIRED):
        raise ValueError("Missing signing configuration")
    # Allow the line-wrapped base64 used by existing release setup.
    keystore = base64.b64decode("".join(env[REQUIRED[0]].split()), validate=True)
    if not keystore:
        raise ValueError("Empty keystore")
    folder = Path(env["RUNNER_TEMP"]) / "android-test-signing"
    properties = Path(env["GITHUB_WORKSPACE"]) / "keystore.properties"
    # Refuse to overwrite anything; this helper is for a clean hosted runner only.
    if folder.exists() or properties.exists():
        raise ValueError("Signing destination already exists")
    folder.mkdir(mode=0o700)
    keyfile = folder / "release-keystore.jks"
    values = {
        "storeFile": str(keyfile.resolve()),
        "storePassword": env["ANDROID_KEYSTORE_PASSWORD"],
        "keyAlias": env["ANDROID_KEY_ALIAS"],
        "keyPassword": env["ANDROID_KEY_PASSWORD"],
    }
    # Explicit permissions, independent of the runner's default umask.
    with os.fdopen(os.open(keyfile, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as out:
        out.write(keystore)
    with os.fdopen(os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as out:
        out.write("".join(f"{key}={java_property(value)}\n" for key, value in values.items()))


if __name__ == "__main__":
    try:
        prepare(os.environ)
    except Exception:
        # Exceptions can contain sensitive input. Never emit their text/traceback.
        print("Cannot prepare CI test signing; check existing release signing configuration.", file=sys.stderr)
        sys.exit(1)
