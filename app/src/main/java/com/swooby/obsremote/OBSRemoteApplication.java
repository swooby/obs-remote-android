package com.swooby.obsremote;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.swooby.obsremote.messages.IncomingMessage;
import com.swooby.obsremote.messages.IncomingMessageAdapter;

import java.security.MessageDigest;

public class OBSRemoteApplication
        extends Application
{
    public static final String TAG = "com.swooby.obsremote";

    /* Preference names */
    private static final String HOST             = "hostname";
    private static final String REMEMBERPASSWORD = "rememberPassword";
    private static final String SALT             = "salt";
    private static final String SALTED           = "salted";

    private Gson   gson;
    private String authChallenge;

    public OBSRemoteApplication()
    {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(IncomingMessage.class, new IncomingMessageAdapter());
        gson = builder.create();
    }

    public Gson getGson()
    {
        return gson;
    }

    public String getDefaultHostname()
    {
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(this);
        return prefMgr.getString(HOST, HOST);
    }

    public void setDefaultHostname(String host)
    {
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(this);
        Editor prefEdit = prefMgr.edit();

        prefEdit.putString(HOST, host);

        prefEdit.apply();
    }

    public boolean getRememberPassword()
    {
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(this);
        return prefMgr.getBoolean(REMEMBERPASSWORD, false);
    }

    public void setRememberPass(boolean rememberPassword)
    {
        SharedPreferences prefMgr = PreferenceManager
                .getDefaultSharedPreferences(this);
        Editor prefEdit = prefMgr.edit();

        prefEdit.putBoolean(REMEMBERPASSWORD, rememberPassword);

        prefEdit.apply();
    }

    public static String sign(String password, String salt)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((password + salt).getBytes("UTF8"));
            return Base64.encodeToString(md.digest(), Base64.NO_WRAP);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Sign failed: ", e);
        }
        return "";
    }

    public void setAuthSalt(String salt)
    {
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(this);
        Editor prefEdit = prefMgr.edit();

        prefEdit.putString(SALT, salt);

        prefEdit.apply();
    }

    public String getAuthSalt()
    {
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(this);
        return prefMgr.getString(SALT, "");
    }

    public void setAuthSalted(String salted)
    {
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(this);
        Editor prefEdit = prefMgr.edit();

        prefEdit.putString(SALTED, salted);

        prefEdit.apply();
    }

    public String getAuthSalted()
    {
        SharedPreferences prefMgr = PreferenceManager.getDefaultSharedPreferences(this);
        return prefMgr.getString(SALTED, "");
    }

    public void setAuthChallenge(String challenge)
    {
        this.authChallenge = challenge;
    }

    public String getAuthChallenge()
    {
        return authChallenge;
    }
}
