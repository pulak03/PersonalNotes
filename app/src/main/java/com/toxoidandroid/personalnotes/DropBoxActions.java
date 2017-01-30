package com.toxoidandroid.personalnotes;

import android.content.Context;
import android.content.SharedPreferences;

import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;

public class DropBoxActions {
    public static void loadAuth(AndroidAuthSession session, Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstant.ACCOUNT_PREFS_NAME,0);
        String key = prefs.getString(AppConstant.ACCESS_KEY_NAME, null);
        String secret = prefs.getString(AppConstant.ACCESS_SECRET_NAME, null);
        if(key == null || secret == null || key.length() == 0 || secret.length() == 0) {
            return;
        }
        if(key.equals("oauth2:")) {
            session.setOAuth2AccessToken(secret);
        } else {
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        }
    }

    public static void storeAuth(AndroidAuthSession session, Context context) {
        String oAuth2AccessToken = session.getOAuth2AccessToken();
        if(oAuth2AccessToken != null) {
            saveAuth(context, "oauth2:", oAuth2AccessToken);
            return;
        }
        AccessTokenPair oAuth1AccessToken = session.getAccessTokenPair();
        if(oAuth1AccessToken != null) {
            saveAuth(context, oAuth1AccessToken.key, oAuth1AccessToken.secret);
        }
    }

    private static void saveAuth(Context context, String accessKey, String accessSecret) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstant.ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(AppConstant.ACCESS_KEY_NAME, accessKey);
        edit.putString(AppConstant.ACCESS_SECRET_NAME, accessSecret);
        edit.apply();

    }

    public static void clearKeys(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstant.ACCOUNT_PREFS_NAME,0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        edit.apply();
    }

    public static AndroidAuthSession buildSession(Context context) {
        AppKeyPair appKeyPair = new AppKeyPair(AppConstant.APP_KEY, AppConstant.APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session, context);
        return session;
    }
}