package com.example.pulsediagnosis;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "user_session";
    private static final String KEY_ID = "USER_ID";
    private static final String KEY_TYPE = "USER_TYPE";

    SharedPreferences sp;
    SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sp.edit();
    }

    public void saveSession(String userId, String userType) {
        editor.putString(KEY_ID, userId);
        editor.putString(KEY_TYPE, userType);
        editor.apply();
    }

    public String getUserId() {
        return sp.getString(KEY_ID, null);
    }

    public String getUserType() {
        return sp.getString(KEY_TYPE, null);
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }

    public boolean isLoggedIn() {
        return getUserId() != null && getUserType() != null;
    }
}
