from __future__ import annotations

import hashlib
import json
import tempfile
import threading
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, RedirectResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parents[1]
WEB_DIR = ROOT / "polish-learning-web"
ANDROID_DIR = ROOT / "polish-phrasebook-android"
CACHE_DIR = WEB_DIR / ".tts-cache"

app = FastAPI(title="Polish Learning Desk")
app.mount("/polish-learning-web", StaticFiles(directory=WEB_DIR, html=True), name="polish-learning-web")
app.mount("/polish-phrasebook-android", StaticFiles(directory=ANDROID_DIR), name="polish-phrasebook-android")

_tts = None
_tts_lock = threading.Lock()
_tts_error = None


class TtsRequest(BaseModel):
    text: str = Field(min_length=1, max_length=700)
    lang: Literal["pl", "en", "na"] = "pl"
    voice: str = Field(default="M1", max_length=24)
    speed: float = Field(default=1.0, ge=0.7, le=1.5)
    steps: int = Field(default=8, ge=5, le=12)


@app.get("/")
def index() -> RedirectResponse:
    return RedirectResponse("/polish-learning-web/")


@app.get("/api/tts/status")
def tts_status() -> dict:
    try:
        import supertonic  # noqa: F401
    except Exception as error:
        return {
            "engine": "supertonic",
            "ready": False,
            "fallback": "browser speechSynthesis",
            "error": str(error),
        }
    return {
        "engine": "supertonic",
        "ready": True,
        "note": "The model loads on the first /api/tts request.",
        "languages": ["pl", "en"],
        "fallback": "browser speechSynthesis",
    }


@app.post("/api/tts")
def tts(request: TtsRequest) -> Response:
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Text is required.")

    cache_path = _cache_path(request)
    if cache_path.exists():
        return FileResponse(cache_path, media_type="audio/wav")

    try:
        engine = _get_tts()
        voice_style = engine.get_voice_style(voice_name=request.voice)
        wav, _duration = engine.synthesize(
            text=text,
            lang=request.lang,
            voice_style=voice_style,
            total_steps=request.steps,
            speed=request.speed,
        )
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
            temp_path = Path(temp_file.name)
        engine.save_audio(wav, str(temp_path))
        temp_path.replace(cache_path)
    except Exception as error:
        raise HTTPException(status_code=503, detail=f"Supertonic TTS unavailable: {error}") from error

    return FileResponse(cache_path, media_type="audio/wav")


def _get_tts():
    global _tts, _tts_error
    if _tts is not None:
        return _tts
    if _tts_error is not None:
        raise RuntimeError(_tts_error)

    with _tts_lock:
        if _tts is not None:
            return _tts
        try:
            from supertonic import TTS

            _tts = TTS(auto_download=True)
            return _tts
        except Exception as error:
            _tts_error = str(error)
            raise


def _cache_path(request: TtsRequest) -> Path:
    payload = json.dumps(
        {
            "engine": "supertonic-3",
            "text": request.text.strip(),
            "lang": request.lang,
            "voice": request.voice,
            "speed": request.speed,
            "steps": request.steps,
        },
        ensure_ascii=False,
        sort_keys=True,
    )
    digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()[:32]
    return CACHE_DIR / f"{digest}.wav"
