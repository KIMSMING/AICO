package com.seoja.aico.user;

public class ResumeRequest {
    private String user_id;
    private String job_role;
    private String project_experience;
    private String strength;
    private String weakness;
    private String motivation;

    public ResumeRequest(String user_id, String job_role, String project_experience,
                         String strength, String weakness, String motivation) {
        this.user_id = user_id;
        this.job_role = job_role;
        this.project_experience = project_experience;
        this.strength = strength;
        this.weakness = weakness;
        this.motivation = motivation;
    }

    public String getUserId() {
        return user_id;
    }

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

    public void setUserId(String user_id) {
        this.user_id = user_id;
    }

    public void setJobRole(String job_role) {
        this.job_role = job_role;
    }

    public void setProjectExp(String project_experience) {
        this.project_experience = project_experience;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public void setWeakness(String weakness) {
        this.weakness = weakness;
    }

    public void setMotivation(String motivation) {
        this.motivation = motivation;
    }
}
