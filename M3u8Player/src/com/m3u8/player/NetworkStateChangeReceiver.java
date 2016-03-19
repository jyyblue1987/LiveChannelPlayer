package com.m3u8.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkStateChangeReceiver extends BroadcastReceiver {

	public static String ACTION_INTERNET_CONNECTION_AVAILABLE = "Internet connection available";
	public static final String TAG = "NetworkStateChangeReceiver";
	
	private static boolean firstConnection = true;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("app", "Network connectivity change");
		if (intent.getExtras() != null) {
			NetworkInfo ni = (NetworkInfo) intent.getExtras().get(ConnectivityManager.EXTRA_NETWORK_INFO);
			if (ni != null && ni.getState() == NetworkInfo.State.CONNECTED) {
				if (Helper.isActivityActive("com.m3u8.player", context)) {
					Log.i(TAG, "Network " + ni.getTypeName() + " connected");
//					Intent i = new Intent(context, PlayerActivity.class);
//					i.setAction(ACTION_INTERNET_CONNECTION_AVAILABLE);
//					i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//					i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//					context.startActivity(i);
					M3UParser parser = M3UParser.getM3UParser(context);
					parser.internetConnectionAvailable();
					if (firstConnection) {
						StandbyActivity.setKeyEventTime();
						firstConnection = false;
					}
				} else {
					Log.i(TAG, "State changed but activity is not running, ignoring !!!");
				}
			} else if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE)) {
				Log.d(TAG, "There's no network connectivity");
			}
		}
	}

}
