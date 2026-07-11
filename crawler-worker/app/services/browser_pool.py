from __future__ import annotations

import asyncio
import logging

from playwright.async_api import Browser, BrowserContext, Page, Playwright, async_playwright


logger = logging.getLogger(__name__)


class BrowserPool:
    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._playwright: Playwright | None = None
        self._browser: Browser | None = None

    async def ensure_browser(self) -> Browser:
        async with self._lock:
            if self._browser is not None and self._browser.is_connected():
                return self._browser

            if self._playwright is None:
                self._playwright = await async_playwright().start()

            self._browser = await self._playwright.chromium.launch(headless=True)
            logger.info("playwright browser started")
            return self._browser

    async def render_html(self, url: str, timeout_seconds: int) -> str:
        browser = await self.ensure_browser()
        context: BrowserContext | None = None
        page: Page | None = None
        try:
            context = await browser.new_context()
            page = await context.new_page()
            await page.goto(url, wait_until="networkidle", timeout=timeout_seconds * 1000)
            return await page.content()
        except Exception:
            await self._reset_browser()
            raise
        finally:
            if page is not None:
                await page.close()
            if context is not None:
                await context.close()

    async def _reset_browser(self) -> None:
        async with self._lock:
            browser = self._browser
            self._browser = None
            if browser is not None:
                try:
                    await browser.close()
                except Exception:
                    logger.warning("close browser during reset failed", exc_info=True)

    async def close(self) -> None:
        async with self._lock:
            browser = self._browser
            playwright = self._playwright
            self._browser = None
            self._playwright = None

        if browser is not None:
            await browser.close()
        if playwright is not None:
            await playwright.stop()


browser_pool = BrowserPool()
