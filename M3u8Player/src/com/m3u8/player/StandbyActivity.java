package com.m3u8.player;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

public class StandbyActivity extends Activity {

	public static boolean SHOWING = false;
	public static AtomicLong lastKeyEventAt = new AtomicLong(System.currentTimeMillis());
	public static ReentrantLock lock = new ReentrantLock();
	public static int RESULT_CODE = 1;

	public static final int STANDBY_INTERVAL = 1000 * 60 * 60 * 4; // 4 hours
//	public static final int STANDBY_INTERVAL = 1000 * 30 ; // 4 hours
	public static final int CALLER_ACTIVITY_HOOME = 1;
	public static final int CALLER_ACTIVITY_PLAYER = 2;

	public static final String STANDBY_OVER_ACTION = "Standby over";
	public static final String STANDBY_CALLER_KEY = "standby activity caller";

	static final int IMAGE_LOADED = 1;
	static final int EXIT = 2;

	static final long PRESENTATION_TIME = 5000;
	static final String IMAGE_URL = "http://logos.albiptv.ch/werblogo.jpg";

	public static final String TAG = "StandbyActivity";
	Bitmap bmp;
	ImageView iView;

	public static void setKeyEventTime() {
		lock.lock();
		try {
			long time = System.currentTimeMillis();
			if (time < 1400000000000l) {
				Log.e(TAG, "Error while obtaining currrent time from system, time returned by the system : " + time
						+ " aborting function !!!");
				return;
			}
			lastKeyEventAt.set(time);
		} finally {
			lock.unlock();
		}
	}

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case IMAGE_LOADED:
				iView.setImageBitmap(bmp);
				break;

			case EXIT:
				iView.setVisibility(View.GONE);
				SHOWING = false;
				returnAndExit();
				break;

			default:
				Log.i(TAG, "Unknown message : " + msg.what);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_standby);
		SHOWING = true;
		iView = (ImageView) findViewById(R.id.server_image);
		loadImage();
	}

	private void loadImage() {
		Thread t = new Thread() {
			public void run() {
				URL url;
				try {
					url = new URL(IMAGE_URL);
					bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
					mHandler.obtainMessage(IMAGE_LOADED).sendToTarget();
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		};
		t.start();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		iView.setVisibility(View.VISIBLE);
		mHandler.postDelayed(new Runnable() {

			@Override
			public void run() {
				mHandler.obtainMessage(EXIT).sendToTarget();
			}
		}, PRESENTATION_TIME);
		return true;
	}

	private void returnAndExit() {
		Intent incomingIntent = getIntent();
		int callerId = incomingIntent.getIntExtra(STANDBY_CALLER_KEY, -1);
		Intent returnIntent = null;
		if (callerId == CALLER_ACTIVITY_HOOME) {
			returnIntent = new Intent(this, HomeActivity.class);
		} else if (callerId == CALLER_ACTIVITY_PLAYER) {
			returnIntent = new Intent(this, PlayerActivity.class);
		}
		if (returnIntent != null) {
			returnIntent.setAction(incomingIntent.getAction());
			returnIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			returnIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(returnIntent);
		}
		finish();
	}
}
