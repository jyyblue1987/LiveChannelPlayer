package com.m3u8.player;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.util.Log;

public class Helper {

	static String TAG = "Helper";
	static final String M3ULIST_KEY = "M3UlistKey";

	public static boolean isConnected(Context context) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = null;
		if (connectivityManager != null) {
			networkInfo = connectivityManager.getActiveNetworkInfo();
		}

		return networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED;
	}

	public static void saveM3UListUrl(Context c, String url) {
		SharedPreferences.Editor editor = c.getSharedPreferences("com.m3u8.player", Context.MODE_PRIVATE).edit();
		editor.putString(M3ULIST_KEY, url);
		editor.commit();
	}

	public static String getM3UListUrl(Context c) {
		SharedPreferences pref = c.getSharedPreferences("com.m3u8.player", Context.MODE_PRIVATE);
		// TODO set default value to emty string when done debugging
//		return pref.getString(M3ULIST_KEY,
//				"http://89.163.146.163:7798/get.php?username=test&password=securityTest&type=m3u");
//		return pref.getString(M3ULIST_KEY,
//				"http://live.albiptv.ch:7798/get.php?username=test&password=securityTest&type=m3u&output=hls");
		return pref.getString(M3ULIST_KEY,
				"http://158.69.227.57:8080/get.php?username=testus&password=test&type=m3u&output=mpegts");
		
		// return "http://192.168.1.4:9090/test.m3u";
	}

	public static boolean isActivityActive(String PackageName, Context c) {
		// Get the Activity Manager
		ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
		// Get a list of running tasks, we are only interested in the last one,
		// the top most so we give a 1 as parameter so we only get the topmost.
		for (RunningTaskInfo taskInfo : manager.getRunningTasks(10)) {
			// Get the info we need for comparison.
			ComponentName componentInfo = taskInfo.topActivity;
			if (componentInfo.getPackageName().equals(PackageName))
				return true;
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	public static HttpResponse makeRequest(String url) throws ClientProtocolException, IOException {
		HttpClient client = new DefaultHttpClient();
		MultipartEntity reqEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
		HttpPost request = new HttpPost(url);
		request.setHeader("Cache-Control", "no-cache");
		request.setEntity(reqEntity);
		HttpResponse response = client.execute(request);
		int status = response.getStatusLine().getStatusCode();
		if (status != 200) {
			Log.e(TAG, "Server returned HTTP ERROR : " + status + " , aborting reading from " + url);
			return null;
		}
		return response;
	}

}
