package com.example.yhmtmt.aws2;

import android.content.SharedPreferences;

public class AppData {

    private static AppData inst = new AppData();
    String addr = "192.168.128.2";
    int port = 20000;

    public static AppData getInstance(){
        return inst;
    }

    public void load(SharedPreferences settings){

        addr = settings.getString("address","192.168.128.2");
        port = settings.getInt("port", 20000);
    }

    public void save(SharedPreferences settings){
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("address", addr);
        editor.putInt("port", port);

    }
}
