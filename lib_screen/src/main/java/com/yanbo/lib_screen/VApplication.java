package com.yanbo.lib_screen;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;


/**
 * Created by lzan13 on 2018/3/15.
 */
public class VApplication {
    private static Application mContext;
    private static Handler mHandler;

    public static Context getContext() {
        return mContext;
    }

    public static Handler getHandler() {
        return mHandler;
    }

    public static void init(Application context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }
}
