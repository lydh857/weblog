from __future__ import annotations

import ipaddress
import socket
from functools import lru_cache
from urllib.parse import urlparse

from app.core.config import settings


class UrlPolicyError(ValueError):
    pass


def ensure_url_allowed(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        raise UrlPolicyError("only http/https is allowed")
    if not parsed.hostname:
        raise UrlPolicyError("hostname is required")

    hostname = _normalize_host(parsed.hostname)
    allow_hosts, allow_networks = _parse_allowlist(settings.url_policy_allowlist)

    try:
        addresses = _resolve_host_addresses(hostname)
    except socket.gaierror as ex:
        raise UrlPolicyError(f"dns resolve failed: {ex}") from ex

    if settings.url_policy_allow_private:
        return

    host_allowed = hostname in allow_hosts
    for ip in addresses:
        if not _is_restricted_address(ip):
            continue
        ip_allowed = _is_ip_allowed(ip, allow_networks)
        if host_allowed or ip_allowed:
            continue
        raise UrlPolicyError(
            "target resolves to private/loopback/link-local address and is blocked; "
            "configure CRAWLER_URL_POLICY_ALLOWLIST or CRAWLER_URL_POLICY_ALLOW_PRIVATE=true"
        )


def _normalize_host(host: str) -> str:
    return host.strip().lower().rstrip(".")


@lru_cache(maxsize=1)
def _parse_allowlist(raw: str) -> tuple[set[str], tuple[ipaddress._BaseNetwork, ...]]:
    hosts: set[str] = set()
    networks: list[ipaddress._BaseNetwork] = []
    for token in raw.split(","):
        entry = token.strip()
        if not entry:
            continue
        normalized = _normalize_host(entry)
        try:
            if "/" in normalized:
                networks.append(ipaddress.ip_network(normalized, strict=False))
            else:
                addr = ipaddress.ip_address(normalized)
                prefix = 32 if addr.version == 4 else 128
                networks.append(ipaddress.ip_network(f"{addr}/{prefix}", strict=False))
            continue
        except ValueError:
            hosts.add(normalized)
    return hosts, tuple(networks)


def _resolve_host_addresses(hostname: str) -> set[ipaddress._BaseAddress]:
    rows = socket.getaddrinfo(hostname, None, type=socket.SOCK_STREAM)
    addresses: set[ipaddress._BaseAddress] = set()
    for row in rows:
        raw_addr = row[4][0]
        addresses.add(ipaddress.ip_address(raw_addr))
    return addresses


def _is_restricted_address(ip: ipaddress._BaseAddress) -> bool:
    return ip.is_private or ip.is_loopback or ip.is_link_local


def _is_ip_allowed(ip: ipaddress._BaseAddress, allow_networks: tuple[ipaddress._BaseNetwork, ...]) -> bool:
    return any(ip in network for network in allow_networks)
