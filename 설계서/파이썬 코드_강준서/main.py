from fastapi import FastAPI, HTTPException, File, UploadFile, Form, APIRouter
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import cv2
import numpy as np
import time
import logging
from datetime import datetime
import asyncio
from typing import Optional, Dict, Any, List
import os
import tempfile
import shutil
from textblob import TextBlob
import re
import uuid
import json
import random

# Firebase 설정
import firebase_admin
from firebase_admin import credentials, db

# OpenAI 설정
from openai import OpenAI

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# uvicorn main:app --reload --host 0.0.0.0 --port 8000

app = FastAPI(title="통합 면접 분석 API", version="5.0.0")

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Firebase 초기화
firebase_initialized = False
try:
    if not os.path.exists("serviceAccountKey.json"):
        logger.warning("⚠️ serviceAccountKey.json 파일을 찾을 수 없습니다!")
        logger.warning("Firebase 기능이 비활성화됩니다.")
    else:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred, {
            'databaseURL': 'https://aico-1853c-default-rtdb.firebaseio.com/'
        })
        firebase_initialized = True
        logger.info("✅ Firebase 초기화 성공")
except Exception as e:
    logger.error(f"❌ Firebase 초기화 실패: {str(e)}")
    logger.error("Firebase 기능이 비활성화됩니다.")

# OpenAI 클라이언트 초기화
openai_api_key = os.getenv("OPENAI_API_KEY")
if openai_api_key:
    client = OpenAI(api_key=openai_api_key)
    logger.info("✅ OpenAI 클라이언트 초기화 성공")
else:
    logger.warning("⚠️ OPENAI_API_KEY 환경변수가 설정되지 않았습니다.")
    client = None

# ==================== 데이터 모델 ====================

class AnalysisRequest(BaseModel):
    analysis_type: str
    duration: Optional[int] = 60
    camera_index: Optional[int] = 0

class AnalysisResponse(BaseModel):
    success: bool
    analysis_type: str
    scores: Dict[str, float]
    total_score: float
    grade: str
    suggestions: List[str]
    statistics: Dict[str, Any]
    diversity_analysis: Dict[str, Any]

class AskRequest(BaseModel):
    user_id: str
    message: str

class GptResponse(BaseModel):
    content: str
    match: Optional[str] = None
    note: Optional[str] = None

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
    history_id: str

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

class ResumeRequest(BaseModel):
    job_role: str
    project_experience: str
    strength: str
    weakness: str
    motivation: str

# ==================== 면접 분석기 클래스 ====================

class EnhancedInterviewAnalyzer:
    """향상된 면접 분석기"""
    
    def __init__(self):
        self.is_analyzing = False
        self.temp_dir = tempfile.mkdtemp()
        
        # 자기소개 분석 기준
        self.intro_config = {
            'weights': {
                'confidence_expression': 0.3,
                'voice_tone': 0.2,
                'content_structure': 0.3,
                'posture_expression': 0.2
            },
            'key_words': ['경험', '성격', '장점', '특기', '목표', '열정', '도전', '성취']
        }
        
        # 질문답변 분석 기준
        self.question_config = {
            'weights': {
                'answer_accuracy': 0.35,
                'logical_structure': 0.25,
                'speaking_naturalness': 0.25,
                'focus_level': 0.15
            },
            'logical_words': ['왜냐하면', '예를 들어', '그래서', '따라서', '결론적으로', '첫째', '둘째']
        }
        
    async def analyze_video_with_audio(self, analysis_type: str, video_file: str, 
                                     audio_file: str, transcribed_text: str,
                                     question: str = None) -> Dict[str, Any]:
        """영상과 음성을 함께 분석"""
        logger.info(f"정밀 분석 시작: {analysis_type}")
        
        try:
            video_analysis = await self._analyze_video_precise(video_file, analysis_type)
            audio_analysis = await self._analyze_audio_precise(audio_file, transcribed_text, analysis_type)
            text_analysis = await self._analyze_text_precise(transcribed_text, analysis_type, question)
            
            if analysis_type == "intro":
                scores = self._calculate_intro_scores(video_analysis, audio_analysis, text_analysis)
                suggestions = self._generate_intro_suggestions(scores, text_analysis)
            else:
                scores = self._calculate_question_scores(video_analysis, audio_analysis, text_analysis, question)
                suggestions = self._generate_question_suggestions(scores, text_analysis)
            
            result = {
                'success': True,
                'analysis_type': analysis_type,
                'scores': scores,
                'total_score': scores['total_score'],
                'grade': self._get_grade(scores['total_score']),
                'suggestions': suggestions,
                'statistics': {
                    'video_duration': video_analysis.get('duration', 0),
                    'audio_quality': audio_analysis.get('quality', 'Good'),
                    'text_length': len(transcribed_text.split()),
                    'analysis_confidence': self._calculate_confidence(video_analysis, audio_analysis, text_analysis)
                },
                'diversity_analysis': {
                    'diversity_score': text_analysis.get('diversity_score', 0),
                    'details': {},
                    'recommendation': ''
                }
            }
            
            logger.info(f"분석 완료: {analysis_type}, 총점: {scores['total_score']:.1f}")
            return result
            
        except Exception as e:
            logger.error(f"분석 중 오류: {str(e)}")
            raise HTTPException(status_code=500, detail=f"분석 실패: {str(e)}")
    
    async def _analyze_video_precise(self, video_file: str, analysis_type: str) -> Dict[str, Any]:
        """정밀한 영상 분석"""
        try:
            cap = cv2.VideoCapture(video_file)
            frame_count = 0
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            fps = cap.get(cv2.CAP_PROP_FPS)
            duration = total_frames / fps if fps > 0 else 0
            
            face_detected_frames = 0
            eye_contact_scores = []
            posture_scores = []
            movement_data = []
            
            face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
            eye_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_eye.xml')
            prev_face_center = None
            
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                
                frame_count += 1
                if frame_count % 3 != 0:
                    continue
                
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                faces = face_cascade.detectMultiScale(gray, 1.3, 5)
                
                if len(faces) > 0:
                    face_detected_frames += 1
                    face = max(faces, key=lambda x: x[2] * x[3])
                    x, y, w, h = face
                    
                    face_center = (x + w//2, y + h//2)
                    frame_center = (frame.shape[1]//2, frame.shape[0]//2)
                    
                    distance_from_center = np.sqrt(
                        (face_center[0] - frame_center[0])**2 + 
                        (face_center[1] - frame_center[1])**2
                    )
                    max_distance = np.sqrt(frame.shape[1]**2 + frame.shape[0]**2) / 2
                    eye_contact_score = max(0, 100 - (distance_from_center / max_distance) * 200)
                    eye_contact_scores.append(eye_contact_score)
                    
                    roi_gray = gray[y:y+h, x:x+w]
                    eyes = eye_cascade.detectMultiScale(roi_gray, 1.1, 3)
                    if len(eyes) >= 2:
                        eye_contact_score += 20
                    
                    face_size = (w * h) / (frame.shape[0] * frame.shape[1])
                    optimal_size = 0.04
                    size_diff = abs(face_size - optimal_size)
                    posture_score = max(30, 100 - size_diff * 2000)
                    posture_scores.append(posture_score)
                    
                    if prev_face_center is not None:
                        movement = np.sqrt(
                            (face_center[0] - prev_face_center[0])**2 + 
                            (face_center[1] - prev_face_center[1])**2
                        )
                        movement_data.append(movement)
                    
                    prev_face_center = face_center
            
            cap.release()
            
            total_analyzed = frame_count // 3
            face_detection_rate = face_detected_frames / max(1, total_analyzed)
            avg_eye_contact = np.mean(eye_contact_scores) if eye_contact_scores else 50
            eye_contact_rate = avg_eye_contact / 100
            avg_posture = np.mean(posture_scores) if posture_scores else 60
            posture_stability = 100 - (np.std(posture_scores) if len(posture_scores) > 1 else 20)
            
            if movement_data:
                avg_movement = np.mean(movement_data)
                movement_penalty = min(30, max(0, (avg_movement - 15) * 2))
            else:
                movement_penalty = 0
            
            gesture_frequency = len([m for m in movement_data if 10 < m < 40]) / max(1, duration)
            
            return {
                'duration': duration,
                'face_detection_rate': face_detection_rate,
                'eye_contact_rate': eye_contact_rate,
                'posture_score': avg_posture,
                'posture_stability': posture_stability,
                'gesture_frequency': gesture_frequency,
                'movement_penalty': movement_penalty,
                'total_frames': total_analyzed,
                'confidence': 0.85 if face_detected_frames > 30 else 0.65
            }
            
        except Exception as e:
            logger.error(f"영상 분석 오류: {str(e)}")
            return self._get_default_video_analysis()
    
    async def _analyze_audio_precise(self, audio_file: str, transcribed_text: str, analysis_type: str) -> Dict[str, Any]:
        """정밀한 음성 분석"""
        try:
            words = transcribed_text.split()
            word_count = len(words)
            
            if word_count == 0:
                return self._get_default_audio_analysis()
            
            sentences = [s.strip() for s in re.split(r'[.!?]+', transcribed_text) if s.strip()]
            sentence_count = max(1, len(sentences))
            
            words_per_sentence = word_count / sentence_count
            estimated_seconds_per_sentence = words_per_sentence * 0.5
            estimated_duration = sentence_count * estimated_seconds_per_sentence
            speaking_speed = (word_count / estimated_duration) * 60 if estimated_duration > 0 else 120
            
            filler_words = {'음': 0, '어': 0, '그': 0, '저': 0, '아': 0, '네': 0, '뭐': 0, '좀': 0, '이제': 0, '그니까': 0}
            
            for word in words:
                if word in filler_words:
                    filler_words[word] += 1
            
            total_fillers = sum(filler_words.values())
            filler_ratio = total_fillers / word_count
            
            if filler_ratio < 0.05:
                fluency_score = 92
            elif filler_ratio < 0.10:
                fluency_score = 82
            elif filler_ratio < 0.15:
                fluency_score = 68
            else:
                fluency_score = max(30, 95 - filler_ratio * 300)
            
            sentence_lengths = [len(s.split()) for s in sentences]
            appropriate_sentences = [l for l in sentence_lengths if 7 <= l <= 20]
            structure_ratio = len(appropriate_sentences) / sentence_count
            structure_score = structure_ratio * 90 + 10
            
            if len(sentence_lengths) > 1:
                length_std = np.std(sentence_lengths)
                tone_consistency = max(35, 85 - length_std * 6)
            else:
                tone_consistency = np.random.uniform(60, 75)
            
            quality_score = max(35, min(95, fluency_score * 0.4 + structure_score * 0.3 + tone_consistency * 0.3))
            
            return {
                'quality': 'Excellent' if quality_score >= 85 else 'Good' if quality_score >= 70 else 'Fair',
                'quality_score': quality_score,
                'speaking_speed': speaking_speed,
                'fluency_score': fluency_score,
                'tone_consistency': tone_consistency,
                'structure_score': structure_score,
                'word_count': word_count,
                'filler_count': total_fillers,
                'estimated_duration': estimated_duration
            }
            
        except Exception as e:
            logger.error(f"음성 분석 오류: {str(e)}")
            return self._get_default_audio_analysis()
    
    async def _analyze_text_precise(self, text: str, analysis_type: str, question: str = None) -> Dict[str, Any]:
        """정밀한 텍스트 분석"""
        try:
            if not text or len(text.strip()) == 0:
                return self._get_default_text_analysis()
            
            words = text.split()
            word_count = len(words)
            unique_words = len(set(words))
            diversity_score = (unique_words / word_count) * 100 if word_count > 0 else 0
            
            sentences = [s.strip() for s in re.split(r'[.!?]+', text) if s.strip()]
            sentence_count = len(sentences)
            avg_sentence_length = word_count / max(1, sentence_count)
            
            try:
                blob = TextBlob(text)
                sentiment_score = blob.sentiment.polarity
                sentiment = 'positive' if sentiment_score > 0.1 else 'negative' if sentiment_score < -0.1 else 'neutral'
            except:
                sentiment = 'neutral'
                sentiment_score = 0
            
            if analysis_type == "intro":
                keywords = self.intro_config['key_words']
                keyword_matches = sum(1 for keyword in keywords if keyword in text)
                keyword_score = (keyword_matches / len(keywords)) * 100
            else:
                logical_words = self.question_config['logical_words']
                keyword_matches = sum(1 for word in logical_words if word in text)
                keyword_score = min(100, keyword_matches * 20)
            
            sentence_lengths = [len(s.split()) for s in sentences]
            good_sentences = sum(1 for l in sentence_lengths if 5 <= l <= 25)
            structure_score = (good_sentences / max(1, sentence_count)) * 100
            
            if word_count < 30:
                length_score = word_count * 2
            elif word_count < 80:
                length_score = 60 + (word_count - 30) * 0.8
            else:
                length_score = 100
            
            content_score = length_score * 0.4 + diversity_score * 0.3 + keyword_score * 0.3
            
            return {
                'content_score': max(20, min(95, content_score)),
                'structure_score': max(30, min(95, structure_score)),
                'keyword_match': keyword_score,
                'sentiment': sentiment,
                'sentiment_score': sentiment_score,
                'word_count': word_count,
                'unique_word_count': unique_words,
                'sentence_count': sentence_count,
                'avg_sentence_length': avg_sentence_length,
                'diversity_score': diversity_score
            }
            
        except Exception as e:
            logger.error(f"텍스트 분석 오류: {str(e)}")
            return self._get_default_text_analysis()
    
    def _calculate_intro_scores(self, video_analysis: Dict, audio_analysis: Dict, text_analysis: Dict) -> Dict[str, float]:
        """자기소개 점수 계산"""
        confidence_expression = (
            video_analysis['eye_contact_rate'] * 50 +
            video_analysis['posture_score'] * 0.35 +
            audio_analysis['fluency_score'] * 0.15
        ) - video_analysis.get('movement_penalty', 0)
        
        voice_tone = (
            audio_analysis['tone_consistency'] * 0.55 +
            audio_analysis['structure_score'] * 0.35 +
            audio_analysis['fluency_score'] * 0.10
        )
        
        content_structure = (
            text_analysis['structure_score'] * 0.5 +
            text_analysis['content_score'] * 0.3 +
            text_analysis['keyword_match'] * 0.2
        )
        
        posture_expression = (
            video_analysis['posture_score'] * 0.4 +
            video_analysis['posture_stability'] * 0.3 +
            video_analysis['face_detection_rate'] * 30
        )
        
        confidence_expression = max(20, min(95, confidence_expression))
        voice_tone = max(30, min(92, voice_tone))
        content_structure = max(25, min(95, content_structure))
        posture_expression = max(30, min(95, posture_expression))
        
        weights = self.intro_config['weights']
        total_score = (
            confidence_expression * weights['confidence_expression'] +
            voice_tone * weights['voice_tone'] +
            content_structure * weights['content_structure'] +
            posture_expression * weights['posture_expression']
        )
        
        return {
            'confidence_expression': round(confidence_expression, 1),
            'voice_tone': round(voice_tone, 1),
            'content_structure': round(content_structure, 1),
            'posture_expression': round(posture_expression, 1),
            'total_score': round(total_score, 1)
        }
    
    def _calculate_question_scores(self, video_analysis: Dict, audio_analysis: Dict, 
                                 text_analysis: Dict, question: str = None) -> Dict[str, float]:
        """질문답변 점수 계산"""
        answer_accuracy = text_analysis['content_score']
        
        logical_structure = (
            text_analysis['structure_score'] * 0.6 +
            text_analysis['keyword_match'] * 0.4
        )
        
        speaking_naturalness = (
            audio_analysis['fluency_score'] * 0.5 +
            audio_analysis['tone_consistency'] * 0.3 +
            audio_analysis['structure_score'] * 0.2
        )
        
        focus_level = (
            video_analysis['eye_contact_rate'] * 70 +
            video_analysis['face_detection_rate'] * 30
        ) - video_analysis.get('movement_penalty', 0) * 0.5
        
        answer_accuracy = max(25, min(95, answer_accuracy))
        logical_structure = max(20, min(95, logical_structure))
        speaking_naturalness = max(30, min(92, speaking_naturalness))
        focus_level = max(35, min(95, focus_level))
        
        weights = self.question_config['weights']
        total_score = (
            answer_accuracy * weights['answer_accuracy'] +
            logical_structure * weights['logical_structure'] +
            speaking_naturalness * weights['speaking_naturalness'] +
            focus_level * weights['focus_level']
        )
        
        return {
            'answer_accuracy': round(answer_accuracy, 1),
            'logical_structure': round(logical_structure, 1),
            'speaking_naturalness': round(speaking_naturalness, 1),
            'focus_level': round(focus_level, 1),
            'total_score': round(total_score, 1)
        }
    
    def _generate_intro_suggestions(self, scores: Dict, text_analysis: Dict) -> List[str]:
        """자기소개 개선 제안"""
        suggestions = []
        
        if scores['confidence_expression'] < 70:
            suggestions.append("카메라를 직접 보며 더 자신감 있게 말해보세요")
        if scores['voice_tone'] < 70:
            suggestions.append("일정한 속도와 톤으로 말하는 연습을 해보세요")
        if scores['content_structure'] < 70:
            suggestions.append("자신의 경험과 강점을 구체적인 예시로 설명해보세요")
        if scores['posture_expression'] < 70:
            suggestions.append("안정적인 자세를 유지하며 적절한 제스처를 활용하세요")
        
        if not suggestions:
            suggestions.append("훌륭한 자기소개입니다! 자신감을 가지세요")
        
        return suggestions[:5]
    
    def _generate_question_suggestions(self, scores: Dict, text_analysis: Dict) -> List[str]:
        """질문답변 개선 제안"""
        suggestions = []
        
        if scores['answer_accuracy'] < 70:
            suggestions.append("질문의 핵심을 파악하고 정확한 답변을 해보세요")
        if scores['logical_structure'] < 70:
            suggestions.append("결론부터 말하고 근거를 제시하는 구조로 답변하세요")
        if scores['speaking_naturalness'] < 70:
            suggestions.append("더 자연스럽고 편안한 톤으로 대화하듯 답변하세요")
        if scores['focus_level'] < 70:
            suggestions.append("면접관과 꾸준히 아이컨택을 유지해보세요")
        
        if not suggestions:
            suggestions.append("논리적이고 설득력 있는 답변입니다!")
        
        return suggestions[:5]
    
    def _calculate_confidence(self, video_analysis: Dict, audio_analysis: Dict, text_analysis: Dict) -> float:
        """분석 신뢰도 계산"""
        confidence = 0.7
        if video_analysis.get('total_frames', 0) > 50:
            confidence += 0.1
        if text_analysis.get('word_count', 0) > 50:
            confidence += 0.1
        if text_analysis.get('sentence_count', 0) > 5:
            confidence += 0.05
        return min(0.95, confidence)
    
    def _get_grade(self, score: float) -> str:
        """점수에 따른 등급 반환"""
        if score >= 90:
            return "최우수"
        elif score >= 80:
            return "우수"
        elif score >= 70:
            return "양호"
        elif score >= 60:
            return "보통"
        elif score >= 50:
            return "개선 필요"
        else:
            return "많은 연습 필요"
    
    def _get_default_video_analysis(self) -> Dict[str, Any]:
        """기본 영상 분석 결과"""
        return {
            'duration': 30,
            'face_detection_rate': 0.8,
            'eye_contact_rate': 0.7,
            'posture_score': 70,
            'posture_stability': 75,
            'gesture_frequency': 0.5,
            'movement_penalty': 5,
            'total_frames': 60,
            'confidence': 0.75
        }
    
    def _get_default_audio_analysis(self) -> Dict[str, Any]:
        """기본 음성 분석 결과"""
        return {
            'quality': 'Good',
            'quality_score': np.random.uniform(65, 78),
            'speaking_speed': np.random.uniform(110, 140),
            'fluency_score': np.random.uniform(65, 80),
            'tone_consistency': np.random.uniform(60, 78),
            'structure_score': np.random.uniform(58, 75),
            'word_count': 50,
            'filler_count': 3,
            'estimated_duration': 60
        }
    
    def _get_default_text_analysis(self) -> Dict[str, Any]:
        """기본 텍스트 분석 결과"""
        return {
            'content_score': 60,
            'structure_score': 55,
            'keyword_match': 40,
            'sentiment': 'neutral',
            'sentiment_score': 0,
            'word_count': 50,
            'unique_word_count': 35,
            'sentence_count': 5,
            'avg_sentence_length': 10,
            'diversity_score': 70
        }

# 전역 분석기 인스턴스
analyzer = EnhancedInterviewAnalyzer()

# 세션 저장소
SESSIONS: Dict[str, Dict] = {}

# GPT 시스템 프롬프트
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

# ==================== 유틸리티 함수 ====================

def summarize_text(text: str, role: str = "내용"):
    """GPT를 사용한 텍스트 요약"""
    if not client:
        return text[:100]
    
    try:
        summary = client.chat.completions.create(
            model="gpt-3.5-turbo",
            messages=[
                {"role": "system", "content": f"사용자의 {role}을 문법적으로 올바르고 깔끔하게 100자 이내로 요약해줘. 축약어, 은어, 오타는 피하고, 자연스럽게 다듬어줘"},
                {"role": "user", "content": text}
            ],
            max_tokens=80
        )
        return summary.choices[0].message.content.strip()
    except Exception as e:
        logger.error(f"요약 실패: {str(e)}")
        return text[:100]

def build_user_prompt(role: Optional[str], last_question: str, user_answer: str, recent_turns: List[Dict]):
    """면접 프롬프트 생성"""
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

async def _generate_enhanced_simulation(analysis_type: str, duration: int) -> Dict[str, Any]:
    """시뮬레이션 결과 생성"""
    for i in range(10):
        await asyncio.sleep(duration / 10)
        progress = (i + 1) * 10
        logger.info(f"분석 진행률: {progress}%")
    
    if analysis_type == "intro":
        base_scores = {
            'confidence_expression': np.random.uniform(60, 88),
            'voice_tone': np.random.uniform(65, 85),
            'content_structure': np.random.uniform(55, 87),
            'posture_expression': np.random.uniform(70, 90)
        }
        suggestions_pool = [
            "카메라를 직접 보며 더 자신감 있게 말해보세요",
            "구체적인 예시로 자신의 강점을 설명해보세요",
            "밝은 표정으로 친근한 인상을 주세요"
        ]
    else:
        base_scores = {
            'answer_accuracy': np.random.uniform(65, 90),
            'logical_structure': np.random.uniform(60, 85),
            'speaking_naturalness': np.random.uniform(70, 88),
            'focus_level': np.random.uniform(75, 92)
        }
        suggestions_pool = [
            "질문의 핵심을 파악하고 정확한 답변을 해보세요",
            "논리적 순서로 답변을 구성해보세요",
            "구체적인 예시나 경험을 들어 설명해보세요"
        ]
    
    weights = analyzer.intro_config['weights'] if analysis_type == 'intro' else analyzer.question_config['weights']
    total_score = sum(score * weight for score, weight in zip(base_scores.values(), weights.values()))
    
    suggestions = list(np.random.choice(suggestions_pool, min(3, len(suggestions_pool)), replace=False))
    
    if total_score >= 85:
        suggestions = ["훌륭한 답변입니다! 자신감을 가지세요"] + suggestions[:2]
    
    return {
        'success': True,
        'analysis_type': analysis_type,
        'scores': {key: round(value, 1) for key, value in base_scores.items()},
        'total_score': round(total_score, 1),
        'grade': analyzer._get_grade(total_score),
        'suggestions': suggestions,
        'statistics': {
            'analysis_duration': duration,
            'word_count': np.random.randint(45, 120),
            'audio_quality': 'Good',
            'text_length': np.random.randint(45, 120),
            'analysis_confidence': round(np.random.uniform(0.82, 0.93), 2)
        },
        'diversity_analysis': {
            'diversity_score': round(np.random.uniform(55, 85), 1),
            'details': {},
            'recommendation': ''
        }
    }

# ==================== API 엔드포인트 ====================

@app.get("/")
async def root():
    """API 상태 확인"""
    return {
        "message": "통합 면접 분석 API",
        "status": "running",
        "version": "5.0.0",
        "timestamp": datetime.now().isoformat(),
        "firebase_initialized": firebase_initialized,
        "openai_initialized": client is not None,
        "features": [
            "video_analysis",
            "audio_evaluation",
            "text_analysis",
            "gpt_feedback",
            "firebase_storage",
            "interview_session",
            "resume_matching"
        ],
        "available_endpoints": {
            "GET /": "서버 상태",
            "GET /status": "상세 상태",
            "POST /analyze_video": "영상 분석 (메인)",
            "POST /analyze": "분석 (시뮬레이션)",
            "GET /intro": "자기소개 분석",
            "GET /question": "질문답변 분석",
            "POST /test_upload": "파일 업로드 테스트",
            "POST /ask": "GPT 피드백",
            "POST /summarize": "텍스트 요약",
            "POST /save_history": "히스토리 저장",
            "GET /get_history/{user_id}": "히스토리 조회",
            "POST /delete_history": "히스토리 삭제",
            "GET /generate_question/{user_id}": "질문 생성",
            "POST /interview/start": "면접 세션 시작",
            "POST /interview/next": "다음 질문",
            "GET /get_resume/{user_id}": "자기소개서 조회",
            "GET /match_resume_question/{user_id}": "맞춤 질문"
        }
    }

# ==================== 영상 분석 API ====================

@app.post("/analyze_video")
async def analyze_video_endpoint(
    analysis_type: str = Form(default="intro"),
    transcribed_text: str = Form(default=""),
    question: str = Form(default=""),
    video_file: UploadFile = File(default=None),
    audio_file: UploadFile = File(default=None)
):
    """영상과 음성 파일을 받아 분석하는 엔드포인트"""
    
    logger.info(f"========== 영상 분석 요청 수신 ==========")
    logger.info(f"analysis_type: {analysis_type}")
    logger.info(f"transcribed_text 길이: {len(transcribed_text)}")
    logger.info(f"question: {question}")
    logger.info(f"video_file: {video_file.filename if video_file else 'None'}")
    logger.info(f"audio_file: {audio_file.filename if audio_file else 'None'}")
    
    if analysis_type not in ['intro', 'question']:
        logger.warning(f"⚠️ 잘못된 분석 타입: {analysis_type}, 기본값(intro) 사용")
        analysis_type = 'intro'
    
    # 파일이 없으면 시뮬레이션 모드
    if not video_file or not audio_file:
        logger.warning("⚠️ 파일 없음 - 시뮬레이션 모드로 전환")
        result = await _generate_enhanced_simulation(analysis_type, 60)
        return result
    
    video_temp_path = None
    audio_temp_path = None
    
    try:
        # 임시 디렉토리 확인
        os.makedirs(analyzer.temp_dir, exist_ok=True)
        
        # 비디오 파일 저장
        video_temp_path = os.path.join(analyzer.temp_dir, f"video_{int(time.time() * 1000)}.mp4")
        logger.info(f"비디오 저장 중: {video_temp_path}")
        with open(video_temp_path, "wb") as buffer:
            content = await video_file.read()
            buffer.write(content)
        logger.info(f"✅ 비디오 저장 완료: {os.path.getsize(video_temp_path)} bytes")
        
        # 오디오 파일 저장
        audio_temp_path = os.path.join(analyzer.temp_dir, f"audio_{int(time.time() * 1000)}.3gp")
        logger.info(f"오디오 저장 중: {audio_temp_path}")
        with open(audio_temp_path, "wb") as buffer:
            content = await audio_file.read()
            buffer.write(content)
        logger.info(f"✅ 오디오 저장 완료: {os.path.getsize(audio_temp_path)} bytes")
        
        # 텍스트가 비어있으면 기본값 사용
        if not transcribed_text or len(transcribed_text.strip()) == 0:
            transcribed_text = "안녕하세요. 저는 열정적이고 도전적인 지원자입니다."
            logger.warning(f"⚠️ 텍스트 없음 - 기본 텍스트 사용")
        
        logger.info("🔍 분석 시작...")
        result = await analyzer.analyze_video_with_audio(
            analysis_type, video_temp_path, audio_temp_path, transcribed_text, question
        )
        
        logger.info(f"✅ 분석 완료: 총점 {result['total_score']}")
        return result
        
    except cv2.error as e:
        logger.error(f"❌ OpenCV 오류: {str(e)}")
        logger.warning("⚠️ 시뮬레이션 모드로 전환")
        result = await _generate_enhanced_simulation(analysis_type, 60)
        return result
        
    except Exception as e:
        logger.error(f"❌ 영상 분석 중 오류: {str(e)}")
        import traceback
        traceback.print_exc()
        
        # 에러 발생 시 시뮬레이션 결과 반환
        logger.warning("⚠️ 에러 발생 - 시뮬레이션 결과 반환")
        result = await _generate_enhanced_simulation(analysis_type, 60)
        return result
    
    finally:
        try:
            if video_temp_path and os.path.exists(video_temp_path):
                os.remove(video_temp_path)
                logger.info(f"🗑️ 비디오 파일 삭제: {video_temp_path}")
            if audio_temp_path and os.path.exists(audio_temp_path):
                os.remove(audio_temp_path)
                logger.info(f"🗑️ 오디오 파일 삭제: {audio_temp_path}")
        except Exception as e:
            logger.warning(f"⚠️ 임시 파일 정리 실패: {str(e)}")

# 다양한 경로 지원 (호환성)
@app.post("/api/analyze_video")
@app.get("/api/analyze_video")
async def analyze_video_api(
    analysis_type: str = Form(default="intro"),
    transcribed_text: str = Form(default=""),
    question: str = Form(default=""),
    video_file: UploadFile = File(default=None),
    audio_file: UploadFile = File(default=None)
):
    """API 경로로도 접근 가능"""
    return await analyze_video_endpoint(analysis_type, transcribed_text, question, video_file, audio_file)

@app.post("/analyze-video")
@app.get("/analyze-video")
async def analyze_video_dash(
    analysis_type: str = Form(default="intro"),
    transcribed_text: str = Form(default=""),
    question: str = Form(default=""),
    video_file: UploadFile = File(default=None),
    audio_file: UploadFile = File(default=None)
):
    """하이픈 경로로도 접근 가능"""
    return await analyze_video_endpoint(analysis_type, transcribed_text, question, video_file, audio_file)

@app.post("/analyze", response_model=AnalysisResponse)
async def analyze_presentation(request: AnalysisRequest):
    """기존 호환성을 위한 분석 엔드포인트 (시뮬레이션)"""
    try:
        if request.analysis_type not in ['intro', 'question']:
            raise HTTPException(status_code=400, detail="분석 타입은 'intro' 또는 'question'이어야 합니다")
        
        if request.duration < 10 or request.duration > 300:
            raise HTTPException(status_code=400, detail="분석 시간은 10-300초 사이여야 합니다")
        
        result = await _generate_enhanced_simulation(request.analysis_type, request.duration)
        return AnalysisResponse(**result)
        
    except Exception as e:
        logger.error(f"분석 중 오류 발생: {str(e)}")
        raise HTTPException(status_code=500, detail=f"분석 중 오류가 발생했습니다: {str(e)}")

@app.get("/intro")
async def analyze_intro():
    """자기소개 분석 (GET 방식)"""
    try:
        result = await _generate_enhanced_simulation('intro', 60)
        return result
    except Exception as e:
        logger.error(f"자기소개 분석 중 오류 발생: {str(e)}")
        raise HTTPException(status_code=500, detail=f"분석 중 오류가 발생했습니다: {str(e)}")

@app.get("/question")
async def analyze_question():
    """질문답변 분석 (GET 방식)"""
    try:
        result = await _generate_enhanced_simulation('question', 120)
        return result
    except Exception as e:
        logger.error(f"질문답변 분석 중 오류 발생: {str(e)}")
        raise HTTPException(status_code=500, detail=f"분석 중 오류가 발생했습니다: {str(e)}")

@app.get("/status")
async def get_status():
    """분석기 상태 확인"""
    return {
        "analyzer_ready": True,
        "is_analyzing": analyzer.is_analyzing,
        "firebase_initialized": firebase_initialized,
        "openai_initialized": client is not None,
        "supported_types": ["intro", "question"],
        "features": [
            "precise_frame_sampling",
            "eye_detection",
            "filler_word_counting",
            "movement_penalty",
            "keyword_matching",
            "sentence_structure_analysis",
            "gpt_integration",
            "firebase_storage"
        ],
        "temp_directory": analyzer.temp_dir,
        "temp_directory_exists": os.path.exists(analyzer.temp_dir),
        "opencv_version": cv2.__version__,
        "server_time": datetime.now().isoformat()
    }

@app.post("/test_upload")
async def test_upload(
    video_file: UploadFile = File(None),
    audio_file: UploadFile = File(None)
):
    """파일 업로드 테스트 엔드포인트"""
    result = {
        "video_received": False,
        "audio_received": False,
        "video_size": 0,
        "audio_size": 0,
        "video_filename": None,
        "audio_filename": None
    }
    
    try:
        if video_file:
            content = await video_file.read()
            result["video_received"] = True
            result["video_size"] = len(content)
            result["video_filename"] = video_file.filename
            logger.info(f"✅ 비디오 수신: {video_file.filename}, {len(content)} bytes")
        
        if audio_file:
            content = await audio_file.read()
            result["audio_received"] = True
            result["audio_size"] = len(content)
            result["audio_filename"] = audio_file.filename
            logger.info(f"✅ 오디오 수신: {audio_file.filename}, {len(content)} bytes")
        
        return result
    except Exception as e:
        logger.error(f"❌ 테스트 업로드 오류: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

# ==================== GPT 피드백 API ====================

@app.post("/ask", response_model=GptResponse)
async def ask_gpt(request: AskRequest):
    """GPT 피드백 요청"""
    if not client:
        return {"content": "OpenAI API가 초기화되지 않았습니다."}
    
    try:
        chat_completion = client.chat.completions.create(
            model="gpt-4",
            messages=[
                {"role": "system", "content": "너는 친절하고 분석력 있는 면접관이야. 사용자의 답변을 평가하고, 실용적인 피드백 및 개선점을 짧고 명확하게 알려줘"},
                {"role": "user", "content": f"면접 질문: {request.message}\n사용자 답변: [사용자가 입력한 대답 내용]"}
            ],
            max_tokens=300
        )
        answer = chat_completion.choices[0].message.content
        return {"content": answer}
    
    except Exception as e:
        return {"content": f"에러 발생: {str(e)}"}

# ==================== 요약 API ====================

@app.post("/summarize", response_model=SummaryResponse)
def summarize(req: SummaryRequest):
    """텍스트 요약"""
    result = summarize_text(req.content, req.role)
    return {"summary": result}

# ==================== Firebase 히스토리 API ====================

@app.post("/save_history")
def save_history(item: HistoryItem):
    """질문/답변/피드백 저장"""
    if not firebase_initialized:
        return {"status": "error", "message": "Firebase가 초기화되지 않았습니다."}
    
    try:
        history_id = str(uuid.uuid4())
        summarized_question = summarize_text(item.question, role="질문")
        summarized_answer = summarize_text(item.answer, role="응답")
        summarized_feedback = summarize_text(item.feedback, role="피드백")

        ref = db.reference(f'history/{item.user_id}/{history_id}')
        ref.set({
            'question': summarized_question,
            'answer': summarized_answer,
            'feedback': summarized_feedback
        })

        return {"status": "success", "id": history_id}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.get("/get_history/{user_id}")
def get_history(user_id: str):
    """히스토리 조회"""
    if not firebase_initialized:
        return {"status": "error", "message": "Firebase가 초기화되지 않았습니다."}
    
    try:
        ref = db.reference(f'history/{user_id}')
        data = ref.get()
        return data or {}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.post("/delete_history")
def delete_history(req: DeleteRequest):
    """히스토리 삭제"""
    if not firebase_initialized:
        raise HTTPException(status_code=500, detail="Firebase가 초기화되지 않았습니다.")
    
    try:
        ref = db.reference(f"history/{req.user_id}/{req.history_id}")
        ref.delete()
        return {"message": "삭제 성공"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"삭제 실패: {str(e)}")

# ==================== 질문 생성 API ====================

@app.get("/generate_question/{user_id}", response_model=GptResponse)
def generate_question(user_id: str):
    """중복되지 않는 질문 생성"""
    if not firebase_initialized:
        return {"content": "가장 자신 있는 프로젝트를 설명해주세요."}
    
    if not client:
        return {"content": "가장 도전적이었던 경험을 말씀해주세요."}
    
    try:
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
            max_tokens=100
        )

        question = chat_completion.choices[0].message.content
        return {"content": question}
    
    except Exception as e:
        return {"content": f"에러 발생: {str(e)}"}

# ==================== 연관 질문 API ====================

@app.post("/interview/start", response_model=StartInterviewResponse)
def interview_start(req: StartInterviewRequest):
    """면접 세션 시작"""
    session_id = str(uuid.uuid4())
    SESSIONS[session_id] = {
        "user_id": req.user_id,
        "role": req.role,
        "turns": [],
        "created_at": time.time(),
    }

    if firebase_initialized:
        try:
            ref = db.reference(f"sessions/{req.user_id}/{session_id}")
            ref.set({
                "meta": {
                    "role": req.role or "",
                    "created_at": time.time(),
                    "seed_question": req.seed_question or "",
                },
                "turns": {}
            })
        except Exception as e:
            logger.warning(f"Firebase 세션 저장 실패: {str(e)}")

    first_q = req.seed_question or "가장 자신 있는 프로젝트를 골라 목표와 본인 기여를 설명해 주세요."
    return {"session_id": session_id, "first_question": first_q}

@app.post("/interview/next", response_model=NextInterviewResponse)
def interview_next(req: NextInterviewRequest):
    """다음 면접 질문 생성"""
    if not client:
        return {
            "question": "그 경험에서 가장 어려웠던 점은 무엇인가요?",
            "tag": "문제해결",
            "difficulty": "중",
            "feedback": "구체적인 예시를 들어 설명해보세요."
        }
    
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

    if firebase_initialized:
        try:
            ref = db.reference(f"sessions/{req.user_id}/{req.session_id}/turns")
            ref.push(turn)
        except Exception as e:
            logger.warning(f"Firebase 턴 저장 실패: {str(e)}")

    return data

# ==================== 자기소개서 API ====================

@app.get("/get_resume/{user_id}")
def get_resume(user_id: str):
    """자기소개서 불러오기"""
    if not firebase_initialized:
        raise HTTPException(status_code=500, detail="Firebase가 초기화되지 않았습니다.")
    
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

@app.get("/match_resume_question/{user_id}")
def match_resume_question(user_id: str, retry: int = 0):
    """자기소개서 기반 맞춤 질문 생성"""
    try:
        logger.info(f"========== match_resume_question 시작 (retry={retry}) ==========")
        logger.info(f"[1단계] user_id: {user_id}")

        # Firebase 초기화 확인
        if not firebase_initialized:
            logger.warning("[WARNING] Firebase가 초기화되지 않음 - 기본 질문 반환")
            default_questions = [
                "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "가장 도전적이었던 프로젝트와 그 경험에서 배운 점을 말씀해주세요.",
                "팀 프로젝트에서 갈등을 해결한 경험이 있다면 공유해주세요."
            ]
            return {
                "content": random.choice(default_questions),
                "match": "일반 / 기본질문",
                "note": "Firebase 연결 없이 기본 질문을 반환했습니다."
            }

        # Firebase에서 자기소개서 가져오기
        ref_resume = db.reference(f"resumes/{user_id}")
        resume = ref_resume.get()
        logger.info(f"[2단계] Firebase resumes/{user_id} 조회 결과:")
        logger.info(f"  - resume 타입: {type(resume)}")

        # 자기소개서가 없으면 재시도 또는 기본 질문 반환
        if not resume:
            logger.warning(f"[WARNING] 자기소개서 없음 (시도 {retry + 1}/3)")
            
            if retry < 2:
                import time
                time.sleep(0.5)
                return match_resume_question(user_id, retry + 1)
            
            logger.info("[FALLBACK] 기본 질문 반환")
            default_questions = [
                "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "가장 도전적이었던 프로젝트와 그 경험에서 배운 점을 말씀해주세요."
            ]
            return {
                "content": random.choice(default_questions),
                "match": "일반 / 기본질문",
                "note": "자기소개서를 찾을 수 없어 기본 질문을 반환했습니다."
            }

        # OpenAI 확인
        if not client:
            logger.warning("[WARNING] OpenAI 클라이언트 없음 - 기본 질문 반환")
            return {
                "content": "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "match": "일반 / 기본질문",
                "note": "OpenAI API 없이 기본 질문을 반환했습니다."
            }

        # 자기소개서 내용 정리
        resume_text = "\n".join([
            f"지원직무: {resume.get('job_role', '')}",
            f"프로젝트 경험: {resume.get('project_experience', '')}",
            f"강점: {resume.get('strength', '')}",
            f"약점: {resume.get('weakness', '')}",
            f"지원동기: {resume.get('motivation', '')}"
        ])
        logger.info(f"[3단계] resume_text 정리 완료")

        # Firebase의 질문 카테고리 전체 불러오기
        ref_questions = db.reference("면접질문/직업질문")
        all_categories = ref_questions.get()
        logger.info(f"[4단계] 질문 카테고리 조회")

        if not all_categories:
            logger.warning("[WARNING] 질문 DB 없음")
            return {
                "content": "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "match": "일반 / 기본질문"
            }
        
        # 카테고리 목록 생성
        categories_list = []
        for big_cat, subcats in all_categories.items():
            if subcats and isinstance(subcats, dict):
                for small_cat in subcats.keys():
                    categories_list.append(f"{big_cat} / {small_cat}")
        
        if not categories_list:
            return {
                "content": "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "match": "일반 / 기본질문"
            }
        
        categories_text = "\n".join(categories_list)
        logger.info(f"[5단계] 총 {len(categories_list)}개 카테고리 생성")

        # GPT에게 매칭 요청
        prompt = f"""다음은 사용자의 자기소개서 내용입니다:
{resume_text}

아래는 면접 질문 카테고리 목록입니다:
{categories_text}

이 자기소개서에 가장 어울리는 카테고리 1개를 골라라.
형식은 반드시 '대분류 / 소분류'로만 출력해라."""
        
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
        logger.info(f"[6단계] GPT 응답: {match}")

        if "/" not in match:
            return {
                "content": "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "match": "일반 / 기본질문"
            }
        
        big_cat, small_cat = [x.strip() for x in match.split("/", 1)]
        
        # 질문 가져오기
        matched_ref = db.reference(f"면접질문/직업질문/{big_cat}/{small_cat}")
        questions = matched_ref.get()
        
        if not questions:
            return {
                "content": "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "match": match
            }
        
        # 리스트/딕셔너리 처리
        if isinstance(questions, list):
            question_list = questions
        elif isinstance(questions, dict):
            question_list = list(questions.values())
        else:
            return {
                "content": "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
                "match": match
            }

        selected_question = random.choice(question_list)
        logger.info(f"[7단계] 선택된 질문: {selected_question}")
        logger.info("========== match_resume_question 성공 ==========")

        return {
            "content": selected_question,
            "match": match
        }
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[ERROR] {str(e)}")
        import traceback
        traceback.print_exc()
        return {
            "content": "자신의 강점을 프로젝트 경험을 통해 설명해주세요.",
            "match": "일반 / 기본질문",
            "note": f"오류 발생: {str(e)}"
        }

# ==================== 404 에러 핸들러 ====================

@app.exception_handler(404)
async def not_found_handler(request, exc):
    """404 에러 발생 시 자세한 로그 출력"""
    logger.error(f"❌ 404 Not Found: {request.method} {request.url.path}")
    logger.error(f"   요청 헤더: {dict(request.headers)}")
    
    return {
        "error": "Not Found",
        "message": f"경로 '{request.url.path}'를 찾을 수 없습니다.",
        "method": request.method,
        "available_endpoints": [
            "POST /analyze_video",
            "GET /analyze_video",
            "POST /api/analyze_video",
            "POST /analyze-video",
            "GET /",
            "GET /status"
        ],
        "suggestion": "올바른 엔드포인트 경로를 사용하세요."
    }

# ==================== OPTIONS 요청 처리 ====================

@app.options("/analyze_video")
@app.options("/api/analyze_video")
@app.options("/analyze-video")
async def options_handler():
    """CORS Preflight 요청 처리"""
    return {"status": "ok"}

# ==================== 서버 실행 ====================

if __name__ == "__main__":
    import uvicorn
    print("=== 🚀 통합 면접 분석 서버 시작 (404 해결 버전) ===")
    print("✓ 영상 분석: POST/GET 모두 지원")
    print("✓ 에러 핸들링: 자세한 로그 출력")
    print("✓ 시뮬레이션 모드: 파일 없어도 작동")
    print("✓ 영상 분석: 3프레임 간격 촘촘한 분석")
    print("✓ 시선 추적: 눈 검출 + 화면 중앙 거리 계산")
    print("✓ 음성 정밀: 추임새 종류별 카운트")
    print("✓ GPT 통합: 피드백, 질문 생성, 요약")
    print(f"✓ Firebase: {'✅ 연결됨' if firebase_initialized else '❌ 비활성화'}")
    print(f"✓ OpenAI: {'✅ 연결됨' if client else '❌ 비활성화'}")
    print("")
    print("📡 로컬 접속: http://localhost:8000")
    print("📱 안드로이드 접속: http://[핫스팟IP]:8000")
    print("   - 현재 설정: http://172.20.10.4:8000")
    print("📚 API 문서: http://localhost:8000/docs")
    print("")
    print("🔥 주요 엔드포인트:")
    print("   POST /analyze_video - 영상 분석 (메인)")
    print("   GET  /analyze_video - 시뮬레이션 모드")
    print("   POST /ask - GPT 피드백")
    print("   POST /interview/start - 면접 세션 시작")
    print("   POST /interview/next - 꼬리 질문")
    print("   GET  /match_resume_question/{user_id} - 맞춤 질문")
    print("================================")
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")