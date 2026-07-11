import socket

import pytest

from app.core.config import settings
from app.services import url_policy


def _mock_getaddrinfo(*_args, **_kwargs):
    return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", 0))]


def test_should_block_private_address_by_default(monkeypatch):
    monkeypatch.setattr(settings, "url_policy_allow_private", False)
    monkeypatch.setattr(settings, "url_policy_allowlist", "")
    url_policy._parse_allowlist.cache_clear()
    monkeypatch.setattr("app.services.url_policy.socket.getaddrinfo", _mock_getaddrinfo)

    with pytest.raises(url_policy.UrlPolicyError):
        url_policy.ensure_url_allowed("http://localhost:8080/article")


def test_should_allow_private_address_when_host_allowlisted(monkeypatch):
    monkeypatch.setattr(settings, "url_policy_allow_private", False)
    monkeypatch.setattr(settings, "url_policy_allowlist", "localhost")
    url_policy._parse_allowlist.cache_clear()
    monkeypatch.setattr("app.services.url_policy.socket.getaddrinfo", _mock_getaddrinfo)

    url_policy.ensure_url_allowed("http://localhost:8080/article")


def test_should_allow_private_address_when_cidr_allowlisted(monkeypatch):
    monkeypatch.setattr(settings, "url_policy_allow_private", False)
    monkeypatch.setattr(settings, "url_policy_allowlist", "127.0.0.0/8")
    url_policy._parse_allowlist.cache_clear()
    monkeypatch.setattr("app.services.url_policy.socket.getaddrinfo", _mock_getaddrinfo)

    url_policy.ensure_url_allowed("http://example.local/internal")


def test_should_allow_private_address_when_allow_private_enabled(monkeypatch):
    monkeypatch.setattr(settings, "url_policy_allow_private", True)
    monkeypatch.setattr(settings, "url_policy_allowlist", "")
    url_policy._parse_allowlist.cache_clear()
    monkeypatch.setattr("app.services.url_policy.socket.getaddrinfo", _mock_getaddrinfo)

    url_policy.ensure_url_allowed("http://localhost:8080/article")
