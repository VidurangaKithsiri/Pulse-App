package com.example.pulsediagnosis;

public class ConnectionRequest {

        private String patientId;
        private String guardianId;
        private String status;
        private long timestamp;

        public ConnectionRequest() {
            // Required for Firebase
        }

        public ConnectionRequest(
                String patientId,
                String guardianId,
                String status,
                long timestamp
        ) {
            this.patientId = patientId;
            this.guardianId = guardianId;
            this.status = status;
            this.timestamp = timestamp;
        }

        public String getPatientId() {
            return patientId;
        }

        public void setPatientId(String patientId) {
            this.patientId = patientId;
        }

        public String getGuardianId() {
            return guardianId;
        }

        public void setGuardianId(String guardianId) {
            this.guardianId = guardianId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
