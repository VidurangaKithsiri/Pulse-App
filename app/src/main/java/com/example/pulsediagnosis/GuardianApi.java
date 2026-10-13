package com.example.pulsediagnosis;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Guardian operations use the app's existing authenticated Realtime Database connection. */
public final class GuardianApi {
    public static final class Result {
        private final Map<String,Object> data;
        Result(Map<String,Object> data) { this.data = data; }
        public Map<String,Object> getData() { return data; }
    }
    private GuardianApi() {}
    private static DatabaseReference root() { return FirebaseDatabase.getInstance().getReference(); }
    private static Result result(String status) { Map<String,Object> data = new HashMap<>(); data.put("status", status); return new Result(data); }
    private static String text(DataSnapshot s, String key) { Object v = s.child(key).getValue(); return v == null ? "" : String.valueOf(v); }
    private static boolean eligible(DataSnapshot profile) { return Boolean.TRUE.equals(profile.child("guardianEnabled").getValue(Boolean.class)) || "GUARDIAN".equalsIgnoreCase(text(profile,"role")); }
    private static String id(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : hash) out.append(String.format(Locale.US,"%02x", b & 255)); return out.toString();
    }
    private static String key(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (s.isEmpty() || s.length() > 180 || s.matches(".*[.#$\\[\\]/\\p{Cntrl}].*")) throw new IllegalArgumentException("Invalid account/request ID");
        return s;
    }
    public static Task<Result> call(String operation, Map<String,Object> args) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return Tasks.forException(new IllegalStateException("Please sign in again."));
        try {
            switch (operation) {
                case "sendGuardianRequest": return sendRequest(uid, key(args.get("guardianId")));
                case "decideGuardianRequest": return decide(uid,key(args.get("requestId")),String.valueOf(args.get("decision")));
                case "setGuardianConsent":
                    if (!(args.get("enabled") instanceof Boolean)) throw new IllegalArgumentException("Invalid consent");
                    return consent(uid,(Boolean)args.get("enabled"));
                case "acknowledgeGuardianAlert":
                    return root().child("GuardianAlerts").child(uid).child(key(args.get("alertId"))).child("acknowledgedAt")
                            .setValue(ServerValue.TIMESTAMP).continueWith(task -> { if (!task.isSuccessful()) throw task.getException(); return result("ACKNOWLEDGED"); });
                case "sendGuardianEmergency": return emergency(uid,args);
                default: return Tasks.forException(new IllegalArgumentException("Unsupported operation"));
            }
        } catch (Exception e) { return Tasks.forException(e); }
    }
    private static Task<Result> consent(String uid, boolean enabled) {
        return root().child("Users").child(uid).get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            DataSnapshot user = task.getResult();
            if (user.exists()) {
                Map<String,Object> fields = new HashMap<>(); fields.put("guardianEnabled",enabled);
                if ("GUARDIAN".equalsIgnoreCase(text(user,"role"))) fields.put("role","PATIENT");
                return root().child("Users").child(uid).updateChildren(fields).continueWith(done -> { if (!done.isSuccessful()) throw done.getException(); return result("UPDATED"); });
            }
            return root().child("Doctors").child(uid).get().continueWithTask(doctorTask -> {
                if (!doctorTask.isSuccessful()) throw doctorTask.getException();
                DataSnapshot doctor = doctorTask.getResult();
                if (!doctor.exists()) throw new IllegalStateException("Account profile not found");
                Map<String,Object> profile = new HashMap<>(); profile.put("name",text(doctor,"name")); profile.put("email",text(doctor,"email")); profile.put("role","DOCTOR"); profile.put("guardianEnabled",enabled);
                return root().child("Users").child(uid).setValue(profile).continueWith(done -> { if (!done.isSuccessful()) throw done.getException(); return result("UPDATED"); });
            });
        });
    }
    private static Task<Result> sendRequest(String uid, String guardian) {
        if (uid.equals(guardian)) return Tasks.forException(new IllegalArgumentException("You cannot request yourself"));
        return root().child("Users").child(guardian).get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            if (!eligible(task.getResult())) throw new IllegalStateException("This account has not agreed to act as a guardian");
            String guardianName = text(task.getResult(),"name");
            return root().child("GuardianLinks").child(uid).child(guardian).get().continueWithTask(linkTask -> {
                if (!linkTask.isSuccessful()) throw linkTask.getException();
                if (Boolean.TRUE.equals(linkTask.getResult().child("connected").getValue(Boolean.class))) throw new IllegalStateException("Guardian already connected");
                return root().child("ConnectionRequests").orderByChild("patientId").equalTo(uid).get().continueWithTask(requestTask -> {
                    if (!requestTask.isSuccessful()) throw requestTask.getException();
                    for (DataSnapshot row : requestTask.getResult().getChildren()) if (guardian.equals(text(row,"guardianId")) && "PENDING".equals(text(row,"status"))) return Tasks.forResult(result("PENDING"));
                    return root().child("Users").child(uid).get().continueWithTask(profileTask -> {
                        if (!profileTask.isSuccessful()) throw profileTask.getException();
                        Map<String,Object> request = new HashMap<>(); request.put("patientId",uid);request.put("guardianId",guardian);
                        request.put("patientName", text(profileTask.getResult(),"name").isEmpty() ? "User" : text(profileTask.getResult(),"name"));request.put("guardianName",guardianName);
                        request.put("status","PENDING");request.put("timestamp",ServerValue.TIMESTAMP);
                        return root().child("ConnectionRequests").child(id(uid+":"+guardian)).setValue(request).continueWith(done -> { if (!done.isSuccessful()) throw done.getException(); return result("PENDING"); });
                    });
                });
            });
        });
    }
    private static Task<Result> decide(String uid, String requestId, String decision) {
        if (!"ACCEPTED".equals(decision) && !"REJECTED".equals(decision)) return Tasks.forException(new IllegalArgumentException("Invalid decision"));
        return root().child("ConnectionRequests").child(requestId).get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            DataSnapshot request=task.getResult();
            if (!uid.equals(text(request,"guardianId"))) throw new IllegalStateException("This request does not belong to you");
            String status=text(request,"status");
            if (!"PENDING".equals(status) && !("ACCEPTED".equals(status) && "ACCEPTED".equals(decision))) throw new IllegalStateException("Request has already been decided");
            String patient=key(text(request,"patientId"));
            Map<String,Object> updates=new HashMap<>();
            updates.put("ConnectionRequests/"+requestId+"/status",decision);
            updates.put("ConnectionRequests/"+requestId+"/decidedAt",ServerValue.TIMESTAMP);
            if ("ACCEPTED".equals(decision)) {
                Map<String,Object> link=new HashMap<>();link.put("connected",true);link.put("connectedAt",ServerValue.TIMESTAMP);link.put("requestId",requestId);
                updates.put("GuardianLinks/"+patient+"/"+uid,link);
            }
            return root().updateChildren(updates).continueWith(done -> { if (!done.isSuccessful()) throw done.getException(); return result(decision); });
        });
    }
    private static Task<Result> emergency(String uid, Map<String,Object> args) throws Exception {
        String source=String.valueOf(args.get("source"));
        if (!"MANUAL".equals(source) && !"SENSOR_BPM".equals(source)) throw new IllegalArgumentException("Invalid source");
        Double bpm = args.get("bpm") instanceof Number ? ((Number)args.get("bpm")).doubleValue() : null;
        if (bpm != null && (Double.isNaN(bpm)||Double.isInfinite(bpm)||bpm<20||bpm>250)) throw new IllegalArgumentException("Invalid BPM");
        if ("SENSOR_BPM".equals(source) && (bpm==null || !(bpm<40 || bpm>140) || !(args.get("durationMs") instanceof Number) || ((Number)args.get("durationMs")).longValue()<10000 || !(args.get("sampleCount") instanceof Number) || ((Number)args.get("sampleCount")).intValue()<3)) throw new IllegalArgumentException("Automatic alert needs sustained abnormal samples");
        String alertId=id(uid+":"+key(args.get("eventId")));
        return root().child("GuardianLinks").child(uid).get().continueWithTask(linkTask -> {
            if (!linkTask.isSuccessful()) throw linkTask.getException();
            DataSnapshot links=linkTask.getResult();
            return root().child("Users").get().continueWithTask(userTask -> {
                if (!userTask.isSuccessful()) throw userTask.getException();
                DataSnapshot users=userTask.getResult();List<String> recipients=new ArrayList<>();
                for(DataSnapshot link:links.getChildren())if(Boolean.TRUE.equals(link.child("connected").getValue(Boolean.class)) && eligible(users.child(link.getKey())))recipients.add(link.getKey());
                if(recipients.isEmpty())throw new IllegalStateException("No accepted guardian. Ask your guardian to accept first.");
                DatabaseReference cooldown=root().child("GuardianAlertState").child(uid);
                TaskCompletionSource<Result> completion=new TaskCompletionSource<>();
                final long now=System.currentTimeMillis();
                cooldown.runTransaction(new Transaction.Handler(){
                    @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData data){
                        Long last=data.child("lastSentAt").getValue(Long.class);
                        if(last!=null && now-last<120000)return Transaction.abort();
                        Map<String,Object> value=new HashMap<>();value.put("lastSentAt",now);value.put("alertId",alertId);data.setValue(value);return Transaction.success(data);
                    }
                    @Override public void onComplete(DatabaseError error,boolean committed,DataSnapshot snapshot){
                        if(error!=null){completion.setException(error.toException());return;}
                        if(!committed){completion.setResult(result("COOLDOWN"));return;}
                        Map<String,Object> alert=new HashMap<>();alert.put("patientId",uid);alert.put("patientName",text(users.child(uid),"name"));
                        alert.put("source",source);alert.put("reason","MANUAL".equals(source)?"Emergency assistance requested":"Sustained abnormal pulse reading");
                        alert.put("timestamp",ServerValue.TIMESTAMP);alert.put("guardianCount",recipients.size());if(bpm!=null)alert.put("bpm",bpm);
                        Map<String,Object> updates=new HashMap<>();updates.put("EmergencyAlerts/"+uid+"/"+alertId,alert);
                        for(String g:recipients){Map<String,Object> inbox=new HashMap<>(alert);inbox.put("guardianId",g);inbox.put("pushStatus","IN_APP");updates.put("GuardianAlerts/"+g+"/"+alertId,inbox);}
                        root().updateChildren(updates).addOnSuccessListener(v->{Result result=result("SAVED");result.data.put("guardianCount",recipients.size());completion.setResult(result);})
                            .addOnFailureListener(e->{
                                cooldown.runTransaction(new Transaction.Handler(){
                                    @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData data){if(!alertId.equals(data.child("alertId").getValue(String.class)))return Transaction.abort();data.setValue(null);return Transaction.success(data);}
                                    @Override public void onComplete(DatabaseError ignored,boolean done,DataSnapshot data){completion.setException(e);}
                                });
                            });
                    }
                });return completion.getTask();
            });
        });
    }
}
