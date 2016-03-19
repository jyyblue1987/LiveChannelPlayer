package com.m3u8.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TVBootReceiver extends BroadcastReceiver {

	public static final String START_APP_ON_BOOT = "app started by boot receiver";
	static final String TAG = "TVBootReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Intent i = new Intent(context, HomeActivity.class);
		i.setAction(START_APP_ON_BOOT);
		Log.i(TAG, "Starting PlayerActivity with FLAG_ACTIVITY_SINGLE_TOP !!!");
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(i);
	}

}
