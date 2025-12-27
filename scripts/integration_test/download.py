import os
import pathlib as p
import platform
import re
import requests
import tarfile
import typing as t
import zipfile
from urllib.parse import quote


class Asset(t.TypedDict):
    name: str
    url: str


class Release(t.TypedDict):
    assets: t.List[Asset]


def download(*, out_dir: p.Path, tag: str) -> p.Path:
    github_token = os.environ["GITHUB_TOKEN"]
    session = requests.Session()
    session.headers.update({"Authorization": f"token {github_token}"})
    org = "temperlang"
    repo = "temper"
    # Get releases for tag.
    tag = quote(tag)
    url = f"https://api.github.com/repos/{org}/{repo}/releases/tags/{tag}"
    response = session.get(url)
    assert response.status_code == 200
    release: Release = response.json()
    # Find matching asset.
    platform_suffix = re.compile(rf"-{get_os()}-{get_arch()}\.(?:tgz|zip)", re.I)
    for asset in release["assets"]:
        if platform_suffix.search(asset["name"]):
            # asset is implicitly defined
            break
    else:
        raise ValueError(
            f"From {url}, {release['assets']} had no match for "
            + f"/{platform_suffix.pattern}/i"
        )

    # Download
    print(f"Downloading: {asset['name']}")
    archive_path = download_asset(asset=asset, out_dir=out_dir, session=session)
    # Extract
    sub = extract_temper(archive_path=archive_path)
    return sub / "temper"


def download_asset(
    *, asset: Asset, out_dir: p.Path, session: requests.Session
) -> p.Path:
    response = session.get(
        asset["url"],
        allow_redirects=True,
        headers={"Accept": "application/octet-stream"},
        stream=True,
    )
    assert response.status_code == 200
    archive_path = out_dir / asset["name"]
    with open(archive_path, "wb") as archive_out:
        for chunk in response.iter_content(chunk_size=1 << 13):
            archive_out.write(chunk)
    return archive_path


def extract_temper(*, archive_path: p.Path) -> p.Path:
    sub = archive_path.parent / archive_path.stem
    if archive_path.suffix == ".zip":
        with zipfile.ZipFile(archive_path, "r") as archive:
            # Expected at top level in zip, so make explicit sub.
            sub.mkdir()
            archive.extractall(sub)
    else:
        assert archive_path.suffix == ".tgz"
        with tarfile.open(archive_path, "r:gz") as archive:
            # Expected to have sub inside already.
            archive.extractall(archive_path.parent)
    return sub


def get_arch() -> str:
    return {
        "amd64": "(?:x64|amd64|x86-64)",
        "arm64": r"(?:aarch64|arm64)",
        "aarch64": r"(?:aarch64|arm64)",
        "x86_64": "(?:x64|amd64|x86-64)",
    }[platform.machine().lower()]


def get_os() -> str:
    return {
        "darwin": "(?:mac|macos|darwin)",
        "linux": "linux",
        "windows": "windows",
    }[platform.system().lower()]
