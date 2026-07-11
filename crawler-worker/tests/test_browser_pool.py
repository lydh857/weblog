import pytest

from app.services.browser_pool import BrowserPool


class _FakePage:
    def __init__(self) -> None:
        self.closed = False

    async def goto(self, url: str, wait_until: str, timeout: int) -> None:
        self.url = url
        self.wait_until = wait_until
        self.timeout = timeout

    async def content(self) -> str:
        return "<html><body>ok</body></html>"

    async def close(self) -> None:
        self.closed = True


class _FakeContext:
    def __init__(self) -> None:
        self.page = _FakePage()
        self.closed = False

    async def new_page(self) -> _FakePage:
        return self.page

    async def close(self) -> None:
        self.closed = True


class _FakeBrowser:
    def __init__(self) -> None:
        self.connected = True
        self.closed = False
        self.context_count = 0

    def is_connected(self) -> bool:
        return self.connected

    async def new_context(self) -> _FakeContext:
        self.context_count += 1
        return _FakeContext()

    async def close(self) -> None:
        self.closed = True
        self.connected = False


class _FakeChromium:
    def __init__(self, browser: _FakeBrowser) -> None:
        self.browser = browser
        self.launch_count = 0

    async def launch(self, headless: bool) -> _FakeBrowser:
        self.launch_count += 1
        return self.browser


class _FakePlaywright:
    def __init__(self, browser: _FakeBrowser) -> None:
        self.chromium = _FakeChromium(browser)
        self.stopped = False

    async def stop(self) -> None:
        self.stopped = True


@pytest.mark.asyncio
async def test_browser_pool_reuses_browser_instance() -> None:
    browser = _FakeBrowser()
    pool = BrowserPool()
    pool._playwright = _FakePlaywright(browser)

    html1 = await pool.render_html("https://example.com/a", 10)
    html2 = await pool.render_html("https://example.com/b", 10)

    assert html1 == "<html><body>ok</body></html>"
    assert html2 == "<html><body>ok</body></html>"
    assert pool._playwright.chromium.launch_count == 1
    assert browser.context_count == 2


@pytest.mark.asyncio
async def test_browser_pool_close_releases_resources() -> None:
    browser = _FakeBrowser()
    playwright = _FakePlaywright(browser)
    pool = BrowserPool()
    pool._browser = browser
    pool._playwright = playwright

    await pool.close()

    assert browser.closed is True
    assert playwright.stopped is True
