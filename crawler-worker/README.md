# crawler-worker

Local crawler service for article extraction, image staging, review state transitions, and draft push preparation.

## Run

```bash
python -m venv .venv
. .venv/Scripts/activate
pip install -e .
uvicorn app.main:app --reload --host 127.0.0.1 --port 17891
```
