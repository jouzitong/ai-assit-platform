import json
import os
from html import unescape
from html.parser import HTMLParser
from typing import Any
from urllib import error, parse, request

from agents import function_tool


DEFAULT_WEB_SEARCH_URL = "https://html.duckduckgo.com/html/"
DEFAULT_TIMEOUT_SECONDS = 20
DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/126.0.0.0 Safari/537.36"
)


def _web_search_url() -> str:
    return (os.getenv("AI_AGENT_WEB_SEARCH_URL") or DEFAULT_WEB_SEARCH_URL).strip()


def _web_search_timeout() -> int:
    raw = (os.getenv("AI_AGENT_WEB_SEARCH_TIMEOUT") or "").strip()
    if not raw:
        return DEFAULT_TIMEOUT_SECONDS
    try:
        return max(1, int(raw))
    except ValueError:
        return DEFAULT_TIMEOUT_SECONDS


def _web_search_method() -> str:
    method = (os.getenv("AI_AGENT_WEB_SEARCH_METHOD") or "POST").strip().upper()
    return method if method in {"GET", "POST"} else "POST"


def _web_search_headers() -> dict[str, str]:
    headers = {
        "Content-Type": "application/x-www-form-urlencoded; charset=utf-8",
        "User-Agent": (os.getenv("AI_AGENT_WEB_SEARCH_USER_AGENT") or DEFAULT_USER_AGENT).strip(),
    }
    token = (os.getenv("AI_AGENT_WEB_SEARCH_TOKEN") or "").strip()
    if token:
        headers["Authorization"] = token if token.lower().startswith("bearer ") else f"Bearer {token}"
    return headers


def _build_html_search_request(query_text: str, region: str | None) -> request.Request:
    payload = {"q": query_text}
    if region:
        payload["kl"] = region
    method = _web_search_method()
    encoded_payload = parse.urlencode(payload)
    if method == "GET":
        separator = "&" if "?" in _web_search_url() else "?"
        return request.Request(
            f"{_web_search_url()}{separator}{encoded_payload}",
            headers=_web_search_headers(),
            method="GET",
        )
    body = encoded_payload.encode("utf-8")
    return request.Request(
        _web_search_url(),
        data=body,
        headers=_web_search_headers(),
        method=method,
    )


def _decode_result_url(raw_url: str) -> str:
    if not raw_url:
        return ""
    if raw_url.startswith("//"):
        raw_url = "https:" + raw_url
    parsed = parse.urlparse(raw_url)
    query = parse.parse_qs(parsed.query)
    uddg = query.get("uddg")
    if uddg and uddg[0]:
        return parse.unquote(uddg[0])
    return raw_url


def _normalize_space(value: str) -> str:
    return " ".join(unescape(value or "").split())


class _DuckDuckGoHtmlParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.items: list[dict[str, Any]] = []
        self._current_item: dict[str, Any] | None = None
        self._capture_title = False
        self._capture_snippet_depth = 0
        self._title_parts: list[str] = []
        self._snippet_parts: list[str] = []

    def _flush_current_item(self) -> None:
        if self._current_item is None:
            return
        self._current_item["title"] = _normalize_space("".join(self._title_parts))
        self._current_item["snippet"] = _normalize_space("".join(self._snippet_parts))
        if self._current_item.get("title") and self._current_item.get("url"):
            self.items.append(self._current_item)
        self._current_item = None
        self._title_parts = []
        self._snippet_parts = []
        self._capture_title = False
        self._capture_snippet_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr_map = {key: value or "" for key, value in attrs}
        class_name = attr_map.get("class", "")
        if tag == "a" and "result__a" in class_name:
            self._flush_current_item()
            self._current_item = {
                "title": "",
                "url": _decode_result_url(attr_map.get("href", "")),
                "snippet": "",
                "source": "duckduckgo",
            }
            self._title_parts = []
            self._snippet_parts = []
            self._capture_title = True
            return
        if self._current_item and "result__snippet" in class_name:
            self._capture_snippet_depth += 1

    def handle_endtag(self, tag: str) -> None:
        if self._capture_title and tag == "a":
            self._capture_title = False
            if self._current_item is not None:
                self._current_item["title"] = _normalize_space("".join(self._title_parts))
            return
        if self._capture_snippet_depth > 0 and tag in {"a", "div", "span"}:
            self._capture_snippet_depth -= 1
            if self._capture_snippet_depth == 0 and self._current_item is not None:
                self._flush_current_item()

    def handle_data(self, data: str) -> None:
        if self._capture_title:
            self._title_parts.append(data)
        if self._capture_snippet_depth > 0:
            self._snippet_parts.append(data)

    def close(self) -> None:
        super().close()
        self._flush_current_item()
        deduped: list[dict[str, Any]] = []
        seen: set[str] = set()
        for item in self.items:
            url = str(item.get("url") or "")
            if not url or url in seen:
                continue
            deduped.append(item)
            seen.add(url)
        self.items = deduped


def _normalize_items_from_json(data: Any) -> list[dict[str, Any]]:
    candidates = data.get("items") if isinstance(data, dict) else None
    if not isinstance(candidates, list):
        candidates = data.get("results") if isinstance(data, dict) else None
    normalized: list[dict[str, Any]] = []
    if not isinstance(candidates, list):
        return normalized
    for item in candidates:
        if not isinstance(item, dict):
            continue
        title = _normalize_space(str(item.get("title") or item.get("name") or ""))
        url = str(item.get("url") or item.get("link") or "").strip()
        snippet = _normalize_space(str(item.get("snippet") or item.get("description") or ""))
        if title and url:
            normalized.append(
                {
                    "title": title,
                    "url": url,
                    "snippet": snippet,
                    "source": str(item.get("source") or "web"),
                }
            )
    return normalized


def _request_web_search(query_text: str, region: str | None) -> dict[str, Any]:
    req = _build_html_search_request(query_text, region)
    try:
        with request.urlopen(req, timeout=_web_search_timeout()) as response:
            content_type = response.headers.get("Content-Type", "")
            text = response.read().decode("utf-8", errors="replace")
    except error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        return {
            "success": False,
            "provider": "web",
            "error": f"Web search HTTP {exc.code}: {text[:500]}",
            "items": [],
        }
    except error.URLError as exc:
        return {
            "success": False,
            "provider": "web",
            "error": f"Web search request failed: {exc.reason}",
            "items": [],
        }

    if "json" in content_type.lower():
        try:
            data = json.loads(text) if text else {}
        except json.JSONDecodeError:
            return {
                "success": False,
                "provider": "web",
                "error": f"Web search returned invalid JSON: {text[:500]}",
                "items": [],
            }
        return {
            "success": True,
            "provider": "web",
            "items": _normalize_items_from_json(data),
        }

    parser = _DuckDuckGoHtmlParser()
    parser.feed(text)
    parser.close()
    return {
        "success": True,
        "provider": "duckduckgo",
        "items": parser.items,
    }


@function_tool
def web_search_tool(
    query: str,
    max_results: int = 5,
    region: str | None = None,
) -> dict[str, Any]:
    """Search public web pages and return a short list of results."""

    if not isinstance(query, str) or not query.strip():
        return {"tool": "web_search_tool", "success": False, "error": "query is required."}
    try:
        resolved_max_results = max(1, int(max_results or 5))
    except (TypeError, ValueError):
        resolved_max_results = 5

    normalized_region = region.strip() if isinstance(region, str) and region.strip() else None
    result = _request_web_search(query.strip(), normalized_region)
    result["tool"] = "web_search_tool"
    result["query"] = query.strip()
    result["region"] = normalized_region
    result["maxResults"] = resolved_max_results
    result["items"] = result.get("items", [])[: result["maxResults"]]
    if result.get("success"):
        result["summary"] = f"Returned {len(result['items'])} web search results."
        if not result["items"]:
            result["warnings"] = [
                "No search results were parsed. Configure AI_AGENT_WEB_SEARCH_URL if your environment needs a custom search endpoint."
            ]
    return result
