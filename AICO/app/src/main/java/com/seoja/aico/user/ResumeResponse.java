package com.seoja.aico.user;

public class ResumeResponse {
    private String status;
    private ResumeData resume;

    public String getStatus() {
        return status;
    }

    public ResumeData getResume() {
        return resume;
    }

    public static class ResumeData {
        private String job_role;
        private String project_experience;
        private String strength;
        private String weakness;
        private String motivation;

        public String getJobRole() {
            return job_role;
        }

        public String getProjectExp() {
            return project_experience;
        }

        public String getStrength() {
            return strength;
        }

        public String getWeakness() {
            return weakness;
        }

        public String getMotivation() {
            return motivation;
        }
    }
}