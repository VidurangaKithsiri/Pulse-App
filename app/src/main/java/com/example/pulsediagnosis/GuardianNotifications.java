package com.example.pulsediagnosis;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

/** Local notifications while the app is open; no server or Cloud Function calls. */
public final class GuardianNotifications {
    public static final String CHANNEL="pulse_guardian_alerts";
    private static final Set<Activity> screens=Collections.newSetFromMap(new WeakHashMap<>());
    private static Query query;
    private static ValueEventListener listener;
    private static String activeUid;
    private GuardianNotifications() {}
    public static void setup(Activity activity){
        channel(activity);
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(activity,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(activity,new String[]{Manifest.permission.POST_NOTIFICATIONS},7401);
        start(activity);
    }
    public static void register(String ignoredToken) { /* No push sender is required by this version. */ }
    public static void logout(Context context,Runnable done){detach();screens.clear();done.run();}
    private static void detach(){if(query!=null && listener!=null)query.removeEventListener(listener);query=null;listener=null;activeUid=null;}
    public static synchronized void start(Activity activity){
        screens.add(activity);String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;
        if(uid.equals(activeUid) && query!=null)return;
        detach();activeUid=uid;
        Context app=activity.getApplicationContext();
        query=FirebaseDatabase.getInstance().getReference("GuardianAlerts").child(uid).orderByChild("timestamp").limitToLast(30);
        listener=new ValueEventListener(){
            @Override public void onDataChange(@NonNull DataSnapshot data){
                if(!uid.equals(FirebaseAuth.getInstance().getUid()))return;
                SharedPreferences seen=app.getSharedPreferences("guardian_alerts_seen_"+uid,Context.MODE_PRIVATE);
                for(DataSnapshot row:data.getChildren()){
                    String id=row.getKey();if(id==null || row.child("acknowledgedAt").exists() || seen.getBoolean(id,false))continue;
                    seen.edit().putBoolean(id,true).apply();
                    show(app,id,"Emergency alert","A linked person needs assistance. Open Home → Guardians → Emergency alerts.");
                    Toast.makeText(app,"New emergency alert. Open Home → Guardians.",Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error){android.util.Log.w("GuardianAlerts",error.getMessage());}
        };query.addValueEventListener(listener);
    }
    public static synchronized void stop(Activity activity){screens.remove(activity);if(screens.isEmpty())detach();}
    public static void channel(Context context){if(Build.VERSION.SDK_INT>=26)context.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Guardian alerts",NotificationManager.IMPORTANCE_HIGH));}
    public static void show(Context context,String id,String title,String body){
        channel(context);if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent intent=new Intent(context,Home.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder=new NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pending);
        context.getSystemService(NotificationManager.class).notify(id.hashCode(),builder.build());
    }
}
