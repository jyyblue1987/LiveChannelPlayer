package com.m3u8.player;

import android.os.Handler;
import android.util.Log;

public class Player {
	
	static String TAG = "Player";
	
	static final int RECONNECT_TIMEOUT = 1000;

	PlayerActivity parent;
	Handler mHandler;
	
	String current_stream;
	int volume = 50;
	
	int m_nPlayView = 0;
	
	io.vov.vitamio.widget.MediaController vitamio_controller = null;
	io.vov.vitamio.widget.VideoView vitamio_view;
	
	Runnable reconnect = new Runnable() {
		@Override
		public void run() {
			mHandler.obtainMessage(PlayerActivity.RECONNECT).sendToTarget();
		}
	};
	
	public void showMediaController()
	{
		if( m_nPlayView == 0 )
			vitamio_controller.show();
	}
	
	public void hideMediaController()
	{
		if( m_nPlayView == 0 )
			vitamio_controller.hide();
	}
	
	public Player(PlayerActivity parent, io.vov.vitamio.widget.VideoView view, final Handler mHandler) {
		this.parent = parent;
		this.vitamio_view = view;
		this.mHandler = mHandler;
		
		view.setOnPreparedListener(new io.vov.vitamio.MediaPlayer.OnPreparedListener() {
			
			@Override
			public void onPrepared(io.vov.vitamio.MediaPlayer mp) {
				Log.i(TAG, "Playback started !!!");
				mHandler.obtainMessage(PlayerActivity.VIDEO_STARTED).sendToTarget();
				mHandler.removeCallbacks(reconnect);
			}
		});
		
		view.setOnErrorListener(new io.vov.vitamio.MediaPlayer.OnErrorListener() {
			
			@Override
			public boolean onError(io.vov.vitamio.MediaPlayer mp, int what, int extra) {
				Log.i(TAG, "Error on stream, reconnect in 3 seconds !!!");
				mHandler.removeCallbacks(reconnect);
				mHandler.postDelayed(reconnect, RECONNECT_TIMEOUT);
				mHandler.obtainMessage(PlayerActivity.PLAYER_ERROR).sendToTarget();				
				return false;
			}
		});

	}
	
	public boolean isPlaying() {
		if( m_nPlayView == 0 )
			return vitamio_view.isPlaying();
		
		return true;
	}
	
	public String getCurrentStream() {
		return current_stream;
	}
	
	public void reconnect() {
		stop();
		play(current_stream);
	}
	
	public void play(String url) {
		if( m_nPlayView == 0 )
			vitamio_view.stopPlayback();	
		Log.w(TAG, "Playing : " + url);
		try {
			if( m_nPlayView == 0 )
			{
				vitamio_view.setVideoPath(url);
				vitamio_view.requestFocus();
				vitamio_view.start();	
			}
			
			
			current_stream = url;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		if( m_nPlayView == 0 )
		{
			if (vitamio_view != null && vitamio_view.isPlaying()) {
				vitamio_view.stopPlayback();
			}	
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

	
}
