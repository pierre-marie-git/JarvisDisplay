import os
import json
import asyncio
import logging
import urllib.request
import urllib.error
import uuid
from fastapi import APIRouter, Request, HTTPException
from fastapi.responses import StreamingResponse
from app.models import (
    MessageCreate, MessageCreateWithLines, MessageResponse, MatrixResponse,
    MatricesResponse, SourceStatus
)
from app.database import (
    get_messages, add_message, delete_message, clear_messages,
    get_sources, get_source, upsert_source, update_source_status
)
from app.auth import get_current_user
from app.matrix import text_to_matrix, matrices_from_messages, text_lines_to_matrix
from app.sources.base import load_source

logger = logging.getLogger("api")

router = APIRouter(prefix="/api", tags=["api"])

# ── Frame config ─────────────────────────────────────────────────────────────
CONFIG_FILE = os.path.join(os.path.dirname(__file__), "..", "config.json")


def _load_config() -> dict:
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE) as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def _save_config(cfg: dict):
    with open(CONFIG_FILE, "w") as f:
        json.dump(cfg, f, ensure_ascii=False)


def get_frame_url() -> str | None:
    return _load_config().get("frame_url")


def set_frame_url(url: str | None):
    cfg = _load_config()
    cfg["frame_url"] = url
    _save_config(cfg)


async def push_to_frame():
    """Push current matrices to the configured Android frame via HTTP POST."""
    frame_url = get_frame_url()
    if not frame_url:
        logger.debug("Frame push skipped: no frame_url configured")
        return

    messages = await get_messages(include_expired=False)
    sources = await get_sources()
    source_matrices = []
    for src in sources:
        if not src.get("enabled"):
            continue
        try:
            config = json.loads(src.get("config_json", "{}"))
            plugin = load_source(src["name"], config, enabled=True)
            if plugin:
                matrix_data = await plugin.get_matrix()
                if matrix_data and matrix_data.get("text"):
                    # Split on \n to get proper line structure for text_lines_to_matrix
                    lines = [{"text": ln, "offset": 0} for ln in matrix_data["text"].split("\n")]
                    if lines and any(ln.strip() for ln in matrix_data["text"].split("\n")):
                        rows = text_lines_to_matrix(lines)
                    else:
                        rows = text_to_matrix(matrix_data["text"])
                    source_matrices.append({
                        "rows": rows,
                        "duration": matrix_data.get("duration", 6),
                    })
        except Exception:
            pass

    matrices = matrices_from_messages(messages, source_matrices)
    payload = json.dumps({"matrices": matrices}, ensure_ascii=False).encode("utf-8")

    try:
        req = urllib.request.Request(
            frame_url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            resp.read()
        logger.info(f"Frame push OK → {frame_url} ({len(matrices)} matrices)")
    except urllib.error.URLError as e:
        logger.warning(f"Frame push failed → {frame_url}: {e}")
    except Exception as e:
        logger.warning(f"Frame push error: {e}")


# ── SSE client registry ──────────────────────────────────────────────────────
_sse_clients: dict[str, asyncio.Queue] = {}
_sse_lock = asyncio.Lock()


async def notify_sse_clients(data: dict):
    """Push data to all connected SSE clients."""
    async with _sse_lock:
        for queue in list(_sse_clients.values()):
            try:
                queue.put_nowait(data)
            except Exception:
                pass


async def sse_stream():
    """Yield SSE events for connected clients. Used by /stream and /admin-stream."""
    client_id = str(uuid.uuid4())
    queue: asyncio.Queue = asyncio.Queue()
    async with _sse_lock:
        _sse_clients[client_id] = queue
    try:
        while True:
            try:
                data = await asyncio.wait_for(queue.get(), timeout=30)
                yield f"event: matrix\ndata: {json.dumps(data)}\n\n".encode()
            except asyncio.TimeoutError:
                yield b"event: ping\ndata: {}\n\n"
    finally:
        async with _sse_lock:
            _sse_clients.pop(client_id, None)


async def gather_matrices() -> list[dict]:
    """Build the current matrices list from messages + sources."""
    import json as _json
    messages = await get_messages(include_expired=False)
    sources = await get_sources()
    source_matrices = []
    for src in sources:
        if not src.get("enabled"):
            continue
        try:
            config = json.loads(src.get("config_json", "{}"))
            plugin = load_source(src["name"], config, enabled=True)
            if plugin:
                matrix_data = await plugin.get_matrix()
                if matrix_data and matrix_data.get("text"):
                    lines = [{"text": ln, "offset": 0} for ln in matrix_data["text"].split("\n")]
                    if lines and any(ln.strip() for ln in matrix_data["text"].split("\n")):
                        rows = text_lines_to_matrix(lines)
                    else:
                        rows = text_to_matrix(matrix_data["text"])
                    source_matrices.append({
                        "rows": rows,
                        "duration": matrix_data.get("duration", 6),
                    })
        except Exception:
            pass

    # Build message matrices — use text_lines_to_matrix if lines_json is stored
    result = []
    for msg in messages:
        lines_json = msg.get("lines_json")
        if lines_json:
            try:
                lines = _json.loads(lines_json)
                # text_lines_to_matrix already returns a full 25-row grid
                # with vertical centering — send it directly
                rows = text_lines_to_matrix(lines)
            except Exception:
                rows = text_to_matrix(msg.get("text", ""))
        else:
            rows = text_to_matrix(msg.get("text", ""))
        result.append({
            "id": f"msg_{int(msg.get('id', 0) or 0)}",
            "rows": rows,
            "duration": 8,
        })
    for i, sm in enumerate(source_matrices):
        result.append({
            "id": f"src_{len(result)}",
            "rows": sm["rows"],
            "duration": sm.get("duration", 6),
        })
    return result


async def _push_loop():
    """Background task: push matrices to all SSE clients every 5 seconds."""
    while True:
        await asyncio.sleep(5)
        matrices = await gather_matrices()
        await notify_sse_clients({"matrices": [MatrixResponse(**m).model_dump() for m in matrices]})


# Kick off the background push task
import threading
_thread = threading.Thread(target=lambda: asyncio.run(_push_loop()), daemon=True)
_thread.start()

# ── Weather auto-refresh every 4 hours ──────────────────────────────────────
import asyncio, time

WEATHER_REFRESH_INTERVAL = 4 * 60 * 60  # 4 hours in seconds

def _weather_refresh_loop():
    """Daemon thread: refresh weather and push to frame every 4 hours."""
    while True:
        time.sleep(WEATHER_REFRESH_INTERVAL)
        try:
            # Run the push in a fresh asyncio event loop
            asyncio.run(push_to_frame())
            logger.info("Weather auto-refresh done → frame updated")
        except Exception as e:
            logger.warning(f"Weather auto-refresh failed: {e}")

_weather_thread = threading.Thread(target=_weather_refresh_loop, daemon=True)
_weather_thread.start()


async def require_auth(request: Request):
    username = await get_current_user(request)
    if not username:
        raise HTTPException(status_code=401, detail="Not authenticated")
    return username


# ── Frame config endpoints ───────────────────────────────────────────────────
@router.get("/frame/config")
async def get_frame_config(request: Request):
    """Get current frame URL configuration. Auth required."""
    await require_auth(request)
    return {"frame_url": get_frame_url()}


@router.post("/frame/config")
async def update_frame_config(request: Request, body: dict):
    """Set the Android frame URL. Auth required.

    Body: {"frame_url": "http://192.168.1.46:8767/api/matrices"}
    Set to null to disable frame push.
    """
    await require_auth(request)
    url = body.get("frame_url")
    if url is not None and not isinstance(url, str):
        raise HTTPException(status_code=400, detail="frame_url must be a string or null")
    set_frame_url(url)
    logger.info(f"Frame URL updated → {url}")
    return {"ok": True, "frame_url": url}


@router.post("/frame/push")
async def manual_frame_push(request: Request):
    """Manually trigger a push to the Android frame. Auth required."""
    await require_auth(request)
    await push_to_frame()
    return {"ok": True}


# ── SSE stream (for display frame — no auth) ─────────────────────────────────
@router.get("/stream")
async def stream_matrices():
    """SSE stream of matrices. No auth required (display frame connects here)."""
    return StreamingResponse(
        sse_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        }
    )


# ── Admin SSE stream (auth required) ────────────────────────────────────────
@router.get("/admin-stream")
async def admin_stream_matrices(request: Request):
    """SSE stream of matrices. Auth required (admin dashboard uses this)."""
    await require_auth(request)
    return StreamingResponse(
        sse_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        }
    )


# ── REST endpoints ────────────────────────────────────────────────────────────
@router.get("/health")
async def health_check():
    return {"status": "ok", "database": "connected", "frame_url": get_frame_url()}


@router.get("/messages")
async def list_messages(request: Request):
    await require_auth(request)
    messages = await get_messages()
    return [MessageResponse(**msg) for msg in messages]


@router.post("/messages")
async def create_message(body: MessageCreateWithLines, request: Request):
    await require_auth(request)
    import json as _json
    # If lines are provided alongside text, store them for structured rendering
    # Filter out blank/whitespace-only lines as a safety net
    lines_json = None
    if body.lines:
        cleaned = [ln.model_dump() for ln in body.lines if (ln.text or "").strip()]
        lines_json = _json.dumps(cleaned) if cleaned else None
    msg_id = await add_message(
        text=body.text,
        sender=body.sender,
        ttl=body.ttl,
        priority=body.priority,
        source=None,
        lines_json=lines_json,
    )
    # Push immediately to the Android frame
    await push_to_frame()
    return {"id": msg_id, "success": True}


@router.delete("/messages/{message_id}")
async def remove_message(message_id: int, request: Request):
    await require_auth(request)
    await delete_message(message_id)
    # Push immediately to reflect deletion
    await push_to_frame()
    return {"success": True}


@router.delete("/messages")
async def clear_all_messages(request: Request):
    await require_auth(request)
    await clear_messages()
    # Push immediately to reflect empty state
    await push_to_frame()
    return {"success": True}


@router.get("/matrices")
async def get_matrices(request: Request):
    matrices = await gather_matrices()
    return {"matrices": [MatrixResponse(**m) for m in matrices]}


@router.get("/sources")
async def list_sources(request: Request):
    await require_auth(request)
    sources = await get_sources()
    return [
        SourceStatus(
            name=s["name"],
            enabled=bool(s.get("enabled")),
            status=s.get("status", "unknown"),
            last_refresh=s.get("last_refresh"),
            config=json.loads(s.get("config_json", "{}")),
        )
        for s in sources
    ]


@router.put("/sources/{source_name}")
async def update_source(source_name: str, request: Request):
    """
    Update source config and/or enabled status.
    Body: { "config": {"key": "value"}, "enabled": true/false }
    """
    await require_auth(request)
    body = await request.json()
    src = await get_source(source_name)
    if not src:
        raise HTTPException(status_code=404, detail="Source not found")

    new_config = body.get("config")
    new_enabled = body.get("enabled")

    current_config = json.loads(src.get("config_json", "{}"))
    if new_config is not None:
        current_config.update(new_config)

    await upsert_source(
        name=source_name,
        config=current_config,
        enabled=new_enabled if new_enabled is not None else bool(src.get("enabled")),
        status=src.get("status", "unknown"),
    )
    return {"success": True}


@router.post("/sources/{source_name}/refresh")
async def refresh_source(source_name: str, request: Request):
    await require_auth(request)
    src = await get_source(source_name)
    if not src:
        raise HTTPException(status_code=404, detail="Source not found")

    try:
        config = json.loads(src.get("config_json", "{}"))
        plugin = load_source(source_name, config, enabled=True)
        if not plugin:
            raise HTTPException(status_code=404, detail="Source plugin not found")

        matrix_data = await plugin.get_matrix()
        status = "ok" if matrix_data else "error"
        await update_source_status(source_name, status)
        return {"success": True, "matrix": matrix_data}
    except Exception as e:
        await update_source_status(source_name, f"error: {str(e)}")
        return {"success": False, "error": str(e)}
