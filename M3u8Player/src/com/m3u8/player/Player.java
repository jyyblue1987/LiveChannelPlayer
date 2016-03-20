package com.m3u8.player;

import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnErrorListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnPreparedListener;
import tv.danmaku.ijk.media.widget.MediaController;
import tv.danmaku.ijk.media.widget.VideoView;

public class Player {
	
	static String TAG = "Player";
	
	static final int RECONNECT_TIMEOUT = 1000;

	PlayerActivity parent;
	Handler mHandler;
	VideoView view;
	MediaController player;
	String current_stream;
	int mVideoWidth;
	int mVideoHeight, mVideoVisibleHeight, mVideoVisibleWidth, mSarNum, mSarDen;
	int volume = 50;
	
	MediaController controller = null;
	
	Runnable reconnect = new Runnable() {
		@Override
		public void run() {
			mHandler.obtainMessage(PlayerActivity.RECONNECT).sendToTarget();
		}
	};
	
	public void showMediaController()
	{
		controller.show();
	}
	
	public void hideMediaController()
	{
		controller.hide();
	}
	
	public Player(PlayerActivity parent, VideoView view, final Handler mHandler) {
		this.parent = parent;
		this.view = view;
		this.mHandler = mHandler;
		
		controller = new MediaController(parent);
		
		view.setMediaController(controller);
		
		view.setOnPreparedListener(new OnPreparedListener() {
			
			@Override
			public void onPrepared(IMediaPlayer mp) {
				Log.i(TAG, "Playback started !!!");
				mHandler.obtainMessage(PlayerActivity.VIDEO_STARTED).sendToTarget();
				mHandler.removeCallbacks(reconnect);
			}
		});
		
		view.setOnErrorListener(new OnErrorListener() {
			
			@Override
			public boolean onError(IMediaPlayer mp, int what, int extra) {
				Log.i(TAG, "Error on stream, reconnect in 3 seconds !!!");
				mHandler.removeCallbacks(reconnect);
				mHandler.postDelayed(reconnect, RECONNECT_TIMEOUT);
				mHandler.obtainMessage(PlayerActivity.PLAYER_ERROR).sendToTarget();
				return true;
			}
		});
		
		
//		controller.startActionMode(callback)
		
	
//		player.setEventListener(new EventListener() {
//			@Override
//			public void onEvent(Event event) {
//				// TODO Auto-generated method stub
//				switch (event.type) {
//				case Event.EndReached:
//				case Event.EncounteredError:
//					Log.i(TAG, "Error on stream, reconnect in 3 seconds !!!");
//					mHandler.removeCallbacks(reconnect);
//					mHandler.postDelayed(reconnect, RECONNECT_TIMEOUT);
//					mHandler.obtainMessage(PlayerActivity.PLAYER_ERROR).sendToTarget();
//					break;
//					
//				case Event.Playing:
//					Log.i(TAG, "Playback started !!!");
//					mHandler.obtainMessage(PlayerActivity.VIDEO_STARTED).sendToTarget();
//					mHandler.removeCallbacks(reconnect);
//					
//				default:
////					Log.i(TAG, "Unhandled event of type : " + event.type);
//				}
//			}
//		});
	}
	
	public boolean isPlaying() {
		return view.isPlaying();
	}
	
	public String getCurrentStream() {
		return current_stream;
	}
	
	public void reconnect() {
		stop();
		play(current_stream);
	}
	
	public void play(String url) {
		view.stopPlayback();
		Log.w(TAG, "Playing : " + url);
		Uri uri = Uri.parse(url);
		try {
			view.setVideoPath(url);
			view.requestFocus();
			view.start();
			
			current_stream = url;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		if (view != null && view.isPlaying()) {
			view.stopPlayback();
		}
	}
	
	public void release() {
		// TODDO release player
	}
	
	public void volumeUp() {
		volume ++;
		Log.i(TAG, "Setting volume to : " + volume);
//		player.setVolume(volume, volume);
	}
	
	public void volumeDown() {
		volume --;
		Log.i(TAG, "Setting volume to : " + volume);
//		player.setVolume(volume, volume);
	}

	public void setSize(int width, int height) {
		Log.w(TAG, "Set size called !!!");
		/*
		mVideoWidth = width;
		mVideoHeight = height;
		if (mVideoWidth * mVideoHeight <= 1)
			return;

		int w = parent.getWindow().getDecorView().getWidth();
		int h = parent.getWindow().getDecorView().getHeight();

		// getWindow().getDecorView() doesn't always take orientation into
		// account, we have to correct the values
		boolean isPortrait = parent.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
		if (w > h && isPortrait || w < h && !isPortrait) {
			int i = w;
			w = h;
			h = i;
		}

		float videoAR = (float) mVideoWidth / (float) mVideoHeight;
		float screenAR = (float) w / (float) h;

		if (screenAR < videoAR) {
			h = (int) (w / videoAR);
		} else {
			w = (int) (h * videoAR);
		}

		// force surface buffer size
		view.getHolder().setFixedSize(mVideoWidth, mVideoHeight);

		// set display size
		android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
		lp.width = w;
		lp.height = h;
		view.setLayoutParams(lp);
		view.invalidate();
		*/
		
	}

//	@Override
//	public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum,
//			int sarDen) {
//		Log.e(TAG, "setSurfaceLayout called !!!");
//		if (width * height == 0)
//			return;
//		// store video size
//		mVideoHeight = height;
//		mVideoWidth = width;
//		mVideoVisibleHeight = visibleHeight;
//		mVideoVisibleWidth = visibleWidth;
//		mSarNum = sarNum;
//		mSarDen = sarDen;
//		Message msg = mHandler.obtainMessage(PlayerActivity.SURFACE_LAYOUT);
//		msg.arg1 = width;
//		msg.arg2 = height;
//		mHandler.sendMessage(msg);
//	}
//
//	@Override
//	public void onSurfacesCreated(IVLCVout vlcVout) {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public void onSurfacesDestroyed(IVLCVout vlcVout) {
//		// TODO Auto-generated method stub
//	}


}
