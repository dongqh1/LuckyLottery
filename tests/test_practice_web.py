"""Browser checks for the offline code practice page, without starting mall services."""

from pathlib import Path

from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs/practice/index.html"


def test_offline_practice_answers_drafts_navigation_and_mobile():
    assert PAGE.is_file(), "The offline practice page has not been built yet"
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        page = context.new_page()
        errors = []
        network_requests = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.on("request", lambda request: network_requests.append(request.url))
        page.goto(PAGE.as_uri())
        page.wait_for_load_state("networkidle")

        assert page.locator("[data-question-id]").count() == 25
        assert page.locator("#answer-details").get_attribute("open") is None
        assert not page.locator("#answer-code").is_visible()
        initial_title = page.locator("#question-title").inner_text()
        starter = page.locator("#practice-editor").input_value()
        assert "TODO [L01-" in starter

        draft = starter + "\n// browser-check: 我写的抽奖实现\n"
        page.locator("#practice-editor").fill(draft)
        page.locator("[data-question-id='L02']").click()
        assert page.locator("#question-title").inner_text() != initial_title
        page.locator("[data-question-id='L01']").click()
        assert page.locator("#practice-editor").input_value() == draft
        assert not page.locator("#answer-code").is_visible()

        page.reload()
        page.wait_for_load_state("networkidle")
        assert page.locator("#practice-editor").input_value() == draft
        page.locator("#answer-details > summary").click()
        assert page.locator("#answer-code").is_visible()
        assert "TODO [" not in page.locator("#answer-code").inner_text()
        page.locator("#answer-details > summary").click()
        assert not page.locator("#answer-code").is_visible()

        page.locator("#mark-done").click()
        assert "1 / 25" in page.locator("#progress-count").inner_text()
        page.reload()
        page.wait_for_load_state("networkidle")
        assert "1 / 25" in page.locator("#progress-count").inner_text()

        page.locator("[data-service='points']").click()
        assert page.locator("[data-question-id]").count() == 5
        assert page.locator("#question-service").inner_text() == "积分服务"
        assert page.locator("#answer-details").get_attribute("open") is None
        page.locator("[data-service='all']").click()
        page.locator("#search-input").fill("不存在的练习-4721")
        assert page.locator("[data-question-id]").count() == 0
        assert page.locator("#empty-state").is_visible()
        page.locator("#search-input").fill("")
        assert page.locator("[data-question-id]").count() == 25

        page.locator("[data-question-id='L01']").click()
        page.once("dialog", lambda dialog: dialog.accept())
        page.locator("#reset-draft").click()
        assert page.locator("#practice-editor").input_value() == starter

        with page.expect_download() as download:
            page.locator("#export-drafts").click()
        assert download.value.suggested_filename.endswith(".json")

        page.set_viewport_size({"width": 390, "height": 844})
        assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Mobile page overflows horizontally"
        assert not errors, errors
        assert not any(url.startswith(("http://", "https://")) for url in network_requests)
        browser.close()
