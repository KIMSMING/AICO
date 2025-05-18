# main.py
from fastapi import FastAPI
from pydantic import BaseModel
import openai
import os

# GPT API 키 불러오기 (환경변수 or 직접 입력 가능)
openai.api_key = os.getenv("OPENAI_API_KEY")

app = FastAPI()

class AskRequest(BaseModel):
    message: str

class GptResponse(BaseModel):
    content: str

@app.post("/ask", response_model=GptResponse)
async def ask_gpt(request: AskRequest):
    try:
        response = openai.chat.completions.create(
            model="gpt-4",
            messages=[
                {"role": "system", "content": "너는 친절한 면접관이야."},
                {"role": "user", "content": request.message}
            ]
        )
        answer = response.choices[0].message.content
        return {"content": answer}
    except Exception as e:
        return {"content": f"에러: {str(e)}"}
