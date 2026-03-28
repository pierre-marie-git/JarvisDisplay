# JarvisDisplay Backend Server

Reference implementation of the JarvisDisplay backend API in Python.

## Quick Start

```bash
python3 server.py
# Server runs on port 8765
```

## API Endpoints

### GET /api/matrices

Returns the current queue of display matrices.

```json
{
  "matrices": [
    {
      "id": "1",
      "rows": ["JARVIS ONLINE       ", "                    ", ...],
      "duration": 8
    }
  ]
}
```

### POST /api/display

Add a new message to the queue.

```bash
curl -X POST http://localhost:8765/api/display \
  -H "Content-Type: application/json" \
  -d '{"text":"HELLO WORLD","sender":"PM","ttl":2}'
```

Parameters:
- `text`: message text (auto-wrapped to 22 chars wide, 25 rows tall)
- `sender`: sender name
- `ttl`: time-to-live in minutes (after expiry the message is removed)

### GET /api/health

Health check endpoint.

```json
{"status": "ok"}
```

## Display Format

The frame uses a **22×25 character grid**. Each matrix row is exactly 22 characters.

Example matrix (simplified 5×5 for docs):
```
"JARVIS"
"      "
"ONLINE"
"      "
"      "
```

## Synology Deployment

The server is a single Python file with no external dependencies (uses stdlib only).

```bash
# On Synology via SSH
python3 server.py &
# Add to startup script to auto-restart
```

Environment variables:
- `PORT`: HTTP port (default: 8765)
- `HOST`: bind address (default: 0.0.0.0)
