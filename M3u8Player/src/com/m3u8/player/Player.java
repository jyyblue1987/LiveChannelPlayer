package com.m3u8.player;

import android.os.Handler;
import android.util.Log;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnErrorListener;

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
	
	tv.danmaku.ijk.media.widget.MediaController ijk_controller = null;
	tv.danmaku.ijk.media.widget.VideoView ijk_view;
	
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
		else
			ijk_controller.show();
	}
	
	public void hideMediaController()
	{
		if( m_nPlayView == 0 )
			vitamio_controller.hide();
		else
			ijk_controller.hide();
	}
	
	public int getPlayerView()
	{
		return m_nPlayView;
	}
	
	public Player(PlayerActivity parent, io.vov.vitamio.widget.VideoView view, final Handler mHandler) {
		this.parent = parent;
		this.vitamio_view = view;
		this.mHandler = mHandler;
		
		m_nPlayView = 0;
		
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
	
	public Player(PlayerActivity parent, tv.danmaku.ijk.media.widget.VideoView view, final Handler mHandler) {
		this.parent = parent;
		this.ijk_view = view;
		this.mHandler = mHandler;
		
		m_nPlayView = 1;
		view.setOnPreparedListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnPreparedListener() {
			
			@Override
			public void onPrepared(tv.danmaku.ijk.media.player.IMediaPlayer mp) {
				Log.i(TAG, "Playback started !!!");
				mHandler.obtainMessage(PlayerActivity.VIDEO_STARTED).sendToTarget();
				mHandler.removeCallbacks(reconnect);
			}
		});
		
		view.setOnErrorListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnErrorListener() {
			
			@Override
			public boolean onError(tv.danmaku.ijk.media.player.IMediaPlayer mp, int what, int extra) {
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
		else
			return ijk_view.isPlaying();
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
		else
			ijk_view.stopPlayback();
		
		Log.w(TAG, "Playing : " + url);
		try {
			if( m_nPlayView == 0 )
			{
				vitamio_view.setVideoPath(url);
				vitamio_view.requestFocus();
				vitamio_view.start();	
			}
			else
			{
				ijk_view.setVideoPath(url);
				ijk_view.requestFocus();
				ijk_view.start();	
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
		else
		{
			if (ijk_view != null && ijk_view.isPlaying()) {
				ijk_view.stopPlayback();
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
