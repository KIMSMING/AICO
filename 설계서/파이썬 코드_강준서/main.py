from fastapi import FastAPI, APIRouter, HTTPException, Query
from pydantic import BaseModel
from openai import OpenAI
from typing import Optional, List, Dict
import os
import uuid
import time, json
import random
from firebase_config import db  # Firebase 연동 설정 파일에서 DB import (firebase_config.py에 있어야 함)

# uvicorn main:app --reload

# GPT API 키 불러오기
openai_api_key = os.getenv("OPENAI_API_KEY")
client = OpenAI(api_key=openai_api_key)

app = FastAPI()
router = APIRouter()

# ==================== 데이터 모델 ====================

class AskRequest(BaseModel):
    user_id: str       # 유저 id
    message: str       # 사용자의 답변 (or 질문)

class GptResponse(BaseModel):
    content: str       # GPT가 반환한 응답

class HistoryItem(BaseModel):
    user_id: str
    question: str
    answer: str
    feedback: str

class SummaryRequest(BaseModel):
    content: str
    role: str = "내용"

class SummaryResponse(BaseModel):
    summary: str

class DeleteRequest(BaseModel):
    user_id: str
    history_id: str    # Firebase 내에서 저장된 히스토리 키

class StartInterviewRequest(BaseModel):
    user_id: str
    role: Optional[str] = None
    seed_question: Optional[str] = None

class StartInterviewResponse(BaseModel):
    session_id: str
    first_question: str 

class NextInterviewRequest(BaseModel):
    user_id: str
    session_id: str
    last_question: str
    user_answer: str

class NextInterviewResponse(BaseModel):
    question: str
    tag: str
    difficulty: str
    feedback: str

# ==================== 요약 기능 ====================

def summarize_text(text: str, role: str = "내용"):  # GPT에게 텍스트를 간결하게 요약 요청
    try:
        summary = client.chat.completions.create(
            model = "gpt-3.5-turbo",    # 요약은 비용 적게
            messages = [
                {"role": "system", "content": f"사용자의 {role}을 문법적으로 올바르고 깔끔하게 100자 이내로 요약해줘. 축약어, 은어, 오타는 피하고, 자연스럽게 다듬어줘"},
                {"role": "user", "content": text}
            ],
            max_tokens = 80
        )
        return summary.choices[0].message.content.strip()
    except Exception as e:
        return f"[요약 실패]: {str(e)}"
    

@router.post("/summarize", response_model=SummaryResponse)
def summarize(req: SummaryRequest):
    result = summarize_text(req.content, req.role)
    return {"summary": result}

app.include_router(router)

# ==================== GPT 피드백 응답 API ====================

@app.post("/ask", response_model=GptResponse)
async def ask_gpt(request: AskRequest):
    try:
        # GPT에게 피드백 요청
        chat_completion = client.chat.completions.create(
            model="gpt-4",  # 또는 "gpt-3.5-turbo"
            messages=[
                {"role": "system", "content": "너는 친절하고 분석력 있는 면접관이야. 사용자의 답변을 평가하고, 실용적인 피드백 및 개선점을 짧고 명확하게 알려줘"},
                {"role": "user", "content": f"면접 질문: {request.message}\n사용자 답변: [사용자가 입력한 대답 내용]"}
            ],
            max_tokens=300
        )
        answer = chat_completion.choices[0].message.content
        return {"content" : answer}
    
    except Exception as e:
        return {"content": f"에러 발생: {str(e)}"}

# ==================== 질문/답변/피드백 저장 API ====================

@app.post("/save_history")
def save_history(item: HistoryItem):
    try:
        # 고유한 히스토리 id 생성
        history_id = str(uuid.uuid4())

        # 질문/답변/피드백 각각 요약
        summarized_question = summarize_text(item.question, role="질문")
        summarized_answer = summarize_text(item.answer, role="응답")
        summarized_feedback = summarize_text(item.feedback, role="피드백")


        # Firebase에 저장
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
        # 이전 질문 불러오기
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
            ],
            max_tokens = 100
        )

        question = chat_completion.choices[0].message.content
        return {"content": question}
    
    except Exception as e:
        return {"content": f"에러 발생: {str(e)}"}
    
# ==================== 히스토리 삭제 기능 ====================

@app.post("/delete_history")
def delete_history(req: DeleteRequest):
        try:
            print("삭제 요청 도착:", req.user_id, req.history_id)
            ref = db.reference(f"history/{req.user_id}/{req.history_id}")
            ref.delete()
            return {"message": "삭제 성공"}
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"삭제 실패: {str(e)}")
        
# ==================== 연관 질문 기능 ====================

SESSIONS: Dict[str, Dict] = {}

SYSTEM_PROMPT = """너는 채용 면접관이다.
- 직전 답변을 근거로 구체적 꼬리질문을 1개 생성한다.
- STAR(Action/Result) 검증, 정량지표/역할/의사결정 근거를 캐묻는다.
- 한글, 1~2문장, 간결.
반드시 JSON만 출력:
{
 "question": "...",
 "tag": "프로젝트/기여도|문제해결|협업|리더십|성능|아키텍처|테스트|커뮤니케이션 중 1",
 "difficulty": "하|중|상",
 "feedback": "지원자가 답할 때 강화하면 좋은 힌트"
}"""

def build_user_prompt(role: Optional[str], last_question: str, user_answer: str, recent_turns: List[Dict]):
    history_lines = []
    for t in recent_turns[-3:]:
        history_lines.append(f"Q: {t['q']}\nA: {t['a']}")
    history_text = "\n\n".join(history_lines) if history_lines else "(이전 없음)"
    return f"""직무: {role or "미지정"}
이전 대화(최근 3턴):
{history_text}

직전 질문: {last_question}
사용자 답변: {user_answer}

위 답변을 바탕으로 후속 질문 JSON을 출력하라.
"""

@app.post("/interview/start", response_model=StartInterviewResponse)
def interview_start(req: StartInterviewRequest):
    session_id = str(uuid.uuid4())
    SESSIONS[session_id] = {
        "user_id": req.user_id,
        "role": req.role,
        "turns": [],
        "created_at": time.time(),
    }

    # Firebase 세션 메타 저장
    ref = db.reference(f"sessions/{req.user_id}/{session_id}")
    ref.set({
        "meta": {
            "role": req.role or "",
            "created_at": time.time(),
            "seed_question": req.seed_question or "",
        },
        "turns": {}
    })

    first_q = req.seed_question or "가장 자신 있는 프로젝트를 골라 목표와 본인 기여를 설명해 주세요."
    return {"session_id": session_id, "first_question": None}

@app.post("/interview/next", response_model=NextInterviewResponse)
def interview_next(req: NextInterviewRequest):
    sess = SESSIONS.get(req.session_id)
    if not sess or sess["user_id"] != req.user_id:
        raise HTTPException(status_code=404, detail="세션 없음 or user_id 불일치")

    recent = [{"q": t["q"], "a": t["a"]} for t in sess["turns"]]
    prompt = build_user_prompt(sess.get("role"), req.last_question, req.user_answer, recent)

    chat = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt}
        ],
        max_tokens=300,
        temperature=0.4
    )

    content = chat.choices[0].message.content
    try:
        data = json.loads(content)
    except Exception:
        data = {
            "question": content.strip(),
            "tag": "문제해결",
            "difficulty": "중",
            "feedback": "수치와 본인 역할을 구체적으로 말해보세요."
        }

    turn = {
        "q": req.last_question,
        "a": req.user_answer,
        "followup": data,
        "ts": time.time()
    }
    sess["turns"].append(turn)

    # Firebase 저장
    ref = db.reference(f"sessions/{req.user_id}/{req.session_id}/turns")
    ref.push(turn)

    return data

# ==================== 자기소개서 불러오기 API ====================

@app.get("/get_resume/{user_id}")
def get_resume(user_id: str):
    """
    Firebase에 저장된 특정 사용자의 자기소개서를 불러오는 API
    """
    try:
        ref = db.reference(f"resumes/{user_id}")
        resume_data = ref.get()

        if not resume_data:
            raise HTTPException(status_code=404, detail="자기소개서가 없습니다.")

        return {
            "status": "success",
            "resume": resume_data
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"자기소개서 불러오기 실패: {str(e)}")

# ==================== 자기소개서 기반 맞춤 질문 생성 API ====================

@app.get("/match_resume_question/{user_id}")
def match_resume_question(user_id: str):
    """
    사용자 자기소개서를 기반으로 Firebase의 질문 카테고리 중
    가장 관련 있는 분야를 자동 선택하고, 그 질문 중 하나를 반환
    """
    try:
        print(f"[DEBUG] match_resume_question 호출됨 | user_id: {user_id}")

        # Firebase에서 자기소개서 가져오기
        ref_resume = db.reference(f"resumes/{user_id}")
        resume = ref_resume.get()
        print(f"[DEBUG] resume 데이터: {resume}")

        if not resume:
            raise HTTPException(status_code=404, detail="자기소개서 데이터가 없습니다.")

        # 자기소개서 내용을 요약하여 GPT에 전달
        resume_text = "\n".join([
            f"지원직무: {resume.get('job_role', '')}",
            f"프로젝트 경험: {resume.get('project_experience', '')}",
            f"강점: {resume.get('strength', '')}",
            f"약점: {resume.get('weakness', '')}",
            f"지원동기: {resume.get('motivation', '')}"
        ])
        print(f"🔥 [DEBUG] resume_text 정리 완료:\n{resume_text}")

        # Firebase의 질문 카테고리 전체 불러오기
        ref_questions = db.reference("면접질문/직업질문")
        all_categories = ref_questions.get()
        print(f"🔥 [DEBUG] 질문 카테고리 데이터: {all_categories}")

        if not all_categories:
            raise HTTPException(status_code=404, detail="면접질문 데이터가 없습니다.")
        
        # 대분류/소분류 목록을 문자열로 나열
        categories_list = []
        for big_cat, subcats in all_categories.items():
            if subcats:
                for small_cat in subcats.keys():
                    categories_list.append(f"{big_cat} / {small_cat}")
        categories_text = "\n".join(categories_list)
        print(f"🔥 [DEBUG] categories_text:\n{categories_text}")


        # GPT에게 가장 적합한 분야 매칭 요청
        prompt = f"""
    다음은 사용자의 자기소개서 내용입니다:
    {resume_text}

    아래는 면접 질문 카테고리 목록입니다:
    {categories_text}

    이 자기소개서에 가장 어울리는 카테고리 1개를 골라라.
    형식은 반드시 '대분류 / 소분류'로만 출력해라.
            """
        
        print("🔥 [DEBUG] GPT 요청 시작")

        # GPT 호출
        chat = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": "너는 면접관이다. 자기소개서 내용을 읽고 가장 관련된 질문 카테고리를 판단한다."},
                {"role": "user", "content": prompt}
            ],
            max_tokens=100,
            temperature=0.3
        )

        match = chat.choices[0].message.content.strip()
        print(f"🔥 [DEBUG] GPT 응답: {match}")

        if "/" not in match:
            raise HTTPException(status_code=500, detail=f"GPT 응답이 잘못되었습니다: {match}")
        
        big_cat, small_cat = [x.strip() for x in match.split("/")]

        # Firebase에서 해당 카테고리 질문 가져오기
        matched_ref = db.reference(f"면접질문/직업질문/{big_cat}/{small_cat}")
        questions = matched_ref.get()
        print(f"🔥 [DEBUG] 매칭된 카테고리 질문: {questions}")

        if not questions:
            raise HTTPException(status_code=404, detail=f"{match} 카테고리 질문이 없습니다.")
        

        # 리스트/딕셔너리 구분 처리
        if isinstance(questions, list):
            question_list = questions
        elif isinstance(questions, dict):
            question_list = list(questions.values())
        else:
            raise HTTPException(status_code=500, detail=f"알 수 없는 질문 데이터 형식: {type(questions)}")

        selected_question = random.choice(question_list)
        print(f"🔥 [DEBUG] 최종 선택된 질문: {selected_question}")

        return {"content": selected_question,
                "match": match
        }
    
    except Exception as e:
        import traceback
        print("🔥 [ERROR] 예외 발생:")
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"질문 생성 실패: {str(e)}")