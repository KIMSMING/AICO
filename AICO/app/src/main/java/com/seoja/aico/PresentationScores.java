package com.seoja.aico;

/**
 * 프레젠테이션 분석 점수를 저장하는 클래스
 */
public class PresentationScores implements Cloneable {
    public float eyeContact = 0f;          // 시선 접촉 점수
    public float expressionVariety = 0f;   // 표정 다양성 점수
    public float voiceConsistency = 75f;   // 음성 일관성 점수 (기본값)
    public float naturalness = 0f;         // 자연스러움 점수

    /**
     * 기본 생성자
     */
    public PresentationScores() {
    }

    /**
     * 매개변수가 있는 생성자
     */
    public PresentationScores(float eyeContact, float expressionVariety,
                              float voiceConsistency, float naturalness) {
        this.eyeContact = eyeContact;
        this.expressionVariety = expressionVariety;
        this.voiceConsistency = voiceConsistency;
        this.naturalness = naturalness;
    }

    /**
     * 총점 계산 (4개 점수의 평균)
     *
     * @return 총점 (0-100)
     */
    public float getTotalScore() {
        return (eyeContact + expressionVariety + voiceConsistency + naturalness) / 4f;
    }

    /**
     * 가중치를 적용한 총점 계산
     *
     * @param eyeWeight         시선 접촉 가중치
     * @param expressionWeight  표정 다양성 가중치
     * @param voiceWeight       음성 일관성 가중치
     * @param naturalnessWeight 자연스러움 가중치
     * @return 가중 총점 (0-100)
     */
    public float getWeightedScore(float eyeWeight, float expressionWeight,
                                  float voiceWeight, float naturalnessWeight) {
        return eyeContact * eyeWeight +
                expressionVariety * expressionWeight +
                voiceConsistency * voiceWeight +
                naturalness * naturalnessWeight;
    }

    /**
     * 모든 점수를 초기화
     */
    public void reset() {
        eyeContact = 0f;
        expressionVariety = 0f;
        voiceConsistency = 75f;  // 기본값으로 복원
        naturalness = 0f;
    }

    /**
     * 점수가 유효한 범위(0-100)에 있는지 확인하고 조정
     */
    public void validateAndClamp() {
        eyeContact = Math.max(0f, Math.min(100f, eyeContact));
        expressionVariety = Math.max(0f, Math.min(100f, expressionVariety));
        voiceConsistency = Math.max(0f, Math.min(100f, voiceConsistency));
        naturalness = Math.max(0f, Math.min(100f, naturalness));
    }

    /**
     * 점수에 따른 등급 반환
     *
     * @return 등급 문자열
     */
    public String getGrade() {
        float total = getTotalScore();
        if (total >= 90) return "최우수";
        else if (total >= 80) return "우수";
        else if (total >= 70) return "양호";
        else if (total >= 60) return "보통";
        else if (total >= 50) return "개선 필요";
        else return "많은 연습 필요";
    }

    /**
     * 객체 복사
     */
    @Override
    public PresentationScores clone() {
        try {
            return (PresentationScores) super.clone();
        } catch (CloneNotSupportedException e) {
            // Cloneable을 구현했으므로 이 예외는 발생하지 않아야 함
            PresentationScores scores = new PresentationScores();
            scores.eyeContact = this.eyeContact;
            scores.expressionVariety = this.expressionVariety;
            scores.voiceConsistency = this.voiceConsistency;
            scores.naturalness = this.naturalness;
            return scores;
        }
    }

    /**
     * 문자열 표현
     */
    @Override
    public String toString() {
        return String.format("PresentationScores{eyeContact=%.1f, expressionVariety=%.1f, " +
                        "voiceConsistency=%.1f, naturalness=%.1f, total=%.1f}",
                eyeContact, expressionVariety, voiceConsistency, naturalness, getTotalScore());
    }

    /**
     * 객체 비교
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        PresentationScores that = (PresentationScores) obj;
        return Float.compare(that.eyeContact, eyeContact) == 0 &&
                Float.compare(that.expressionVariety, expressionVariety) == 0 &&
                Float.compare(that.voiceConsistency, voiceConsistency) == 0 &&
                Float.compare(that.naturalness, naturalness) == 0;
    }

    /**
     * 해시코드
     */
    @Override
    public int hashCode() {
        int result = Float.hashCode(eyeContact);
        result = 31 * result + Float.hashCode(expressionVariety);
        result = 31 * result + Float.hashCode(voiceConsistency);
        result = 31 * result + Float.hashCode(naturalness);
        return result;
    }
}