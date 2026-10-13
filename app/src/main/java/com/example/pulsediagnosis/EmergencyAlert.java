package com.example.pulsediagnosis;

public class EmergencyAlert {

    private String patientId;
    private String prediction;
    private String riskLevel;

    private double bpm;

    private long timestamp;

    private boolean notified;

    public EmergencyAlert() {
    }

    public EmergencyAlert(
            String patientId,
            String prediction,
            String riskLevel,
            double bpm,
            long timestamp,
            boolean notified
    ) {

        this.patientId = patientId;
        this.prediction = prediction;
        this.riskLevel = riskLevel;
        this.bpm = bpm;
        this.timestamp = timestamp;
        this.notified = notified;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public double getBpm() {
        return bpm;
    }

    public void setBpm(double bpm) {
        this.bpm = bpm;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }
}