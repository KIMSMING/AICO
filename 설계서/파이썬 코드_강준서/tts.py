from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
import os, base64
from openai import OpenAI

app = FastAPI()

# ✅ GPT API 키 불러오기 (환경변수 사용)
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

@app.post("/interview/voice")
async def tts_endpoint(request: Request):
    data = await request.json()
    text = data.get("text", "자기소개 1분이내로 해주세요.")

# 🗣️ TTS 호출: 'onyx'는 면접관처럼 또박또박한 중저음 목소리
    response = client.audio.speech.create(
        model="tts-1",           # 또는 "tts-1-hd" (고음질)
        voice="onyx",            # 면접관 스타일로 추천
        input=text           # 음성으로 만들 텍스트
    )

    audio_base64 = base64.b64encode(response.content).decode("utf-8")
    return JSONResponse({"audio_base64": audio_base64})