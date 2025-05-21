from fastapi import FastAPI
from pydantic import BaseModel
from openai import OpenAI
import os
import uuid
from firebase_config import db  #Firebase 연동 설정 파일에서 DB import (firebase_config.py에 있어야 함)

#GPT API 키 불러오기
openai_api_key = os.getenv("OPENAI_API_KEY")
client = OpenAI(api_key=openai_api_key)

app = FastAPI()

# ==================== 데이터 모델 ====================

class AskRequest(BaseModel):
    user_id: str       #유저 id
    message: str       #사용자의 답변 (or 질문)

class GptResponse(BaseModel):
    content: str       #GPT가 반환한 응답

class HistoryItem(BaseModel):
    user_id: str
    question: str
    answer: str
    feedback: str

# ==================== 요약 기능 ====================

def summarize_text(text: str, role: str = "내용"):  #GPT에게 텍스트를 간결하게 요약 요청
    try:
        summary = client.chat.completions.create(
            model = "gpt-3.5-turbo",    #요약은 비용 적게
            messages = [
                {"role": "system", "content": f"사용자의 {role}을 100자 이내로 요약해줘."},
                {"role": "user", "content": text}
            ]
        )
        return summary.choices[0].message.content.strip()
    except Exception as e:
        return f"[요약 실패]: {str(e)}"


# ==================== GPT 피드백 응답 API ====================

@app.post("/ask", response_model=GptResponse)
async def ask_gpt(request: AskRequest):
    try:
        #GPT에게 피드백 요청
        chat_completion = client.chat.completions.create(
            model="gpt-4",  # 또는 "gpt-3.5-turbo"
            messages=[
                {"role": "system", "content": "너는 친절하고 분석력 있는 면접관이야. 사용자의 답변에 대해 구체적이고, 실용적인 피드백을 줘."},
                {"role": "user", "content": f"면접 질문: {request.message}\n사용자 답변: [사용자가 입력한 대답 내용]"}
            ]
        )
        answer = chat_completion.choices[0].message.content
        return {"content" : answer}
    
    except Exception as e:
        return {"content": f"에러 발생: {str(e)}"}

# ==================== 질문/답변/피드백 저장 API ====================

@app.post("/save_history")
def save_history(item: HistoryItem):
    try:
        #고유한 히스토리 id 생성
        history_id = str(uuid.uuid4())

        # 질문/답변/피드백 각각 요약
        summarized_question = summarize_text(item.question, role="질문")
        summarized_answer = summarize_text(item.answer, role="응답")
        summarized_feedback = summarize_text(item.feedback, role="피드백")


        #Firebase에 저장
        ref = db.reference(f'history/{item.user_id}/{history_id}')
        ref.set({
            'question': summarized_question,
            'answer': summarized_answer,
            'feedback': summarized_feedback
        })

        return {"status": "success", "id": history_id}
    except Exception as e:
        return {"status": "error", "message": str(e)}
    
# ==================== 히스토리 조회 API ====================

@app.get("/get_history/{user_id}")
def get_history(user_id: str):
    try:
        ref = db.reference(f'history/{user_id}')
        data = ref.get()
        return data or {}
    except Exception as e:
        return {"status": "error", "message": str(e)}

# ==================== 중복되지 않는 질문 생성 API ====================

@app.get("/generate_question/{user_id}", response_model=GptResponse)
def generate_question(user_id: str):
    try:
        #이전 질문 불러오기
        ref = db.reference(f'history/{user_id}')
        history = ref.get()

        previous_questions = []
        if history:
            for record in history.values():
                if "question" in record:
                    previous_questions.append(record["question"])

        prompt = f"""
지금까지 사용자에게 다음과 같은 질문을 했습니다:
{chr(10).join(previous_questions)}

이전 질문과 중복되지 않는 새로운 면접 질문을 하나만 생성해줘.
"""
        chat_completion = client.chat.completions.create(
            model="gpt-4",
            messages=[
                {"role": "system", "content": "너는 진지한 직무 면접관이야."},
                {"role": "user", "content": prompt}
            ]
        )

        question = chat_completion.choices[0].message.content
        return {"content": question}
    
    except Exception as e:
        return {"content": f"에러 발생: {str(e)}"}