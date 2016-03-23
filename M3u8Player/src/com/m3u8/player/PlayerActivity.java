package com.m3u8.player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.m3u8.player.M3UParser.M3UElement;
import com.nostra13.universalimageloader.cache.memory.impl.WeakMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import io.vov.vitamio.widget.MediaController;
import io.vov.vitamio.widget.VideoView;

public class PlayerActivity extends Activity {

	static final String TAG = "PlayerActivity";

	static final int MIN_DISTANCE = 150;

	public static final int LIVE_TV_CATEGORY = 0;
	public static final int RADIO_TV_CATEGORY = 1;
	public static final int VOD_TV_CATEGORY1 = 2;
	public static final int VOD_TV_CATEGORY2 = 3;
	public static final int VOD_TV_CATEGORY3 = 4;
	
	
	
	public static final int DISPLAY_CHANNEL_LIST = 1;
	public static final int LIST_READY = 2;
	public static final int SURFACE_LAYOUT = 3;
	public static final int UPDATE_TIMER = 4;
	public static final int HIDE_INFO_PANEL = 5;
	public static final int PLAYER_ERROR = 6;
	public static final int VIDEO_STARTED = 7;
	public static final int RECONNECT = 8;
	public static final int M3U_PARSING_ERROR = 9;
	public static final int WAITING_FOR_NETWORK_CONNECTION = 10;
	public static final int JUMP_TO_CHANNEL = 11;
	public static final int CANEL_JUMP_TO_CHANNEL = 12;
	public static final int SIMULATE_SLEEP_MODE = 13;

	static final int INFO_PANEL_SHOW_PERIOD = 3000;
	static final int JUMP_TO_CHANNEL_TIMEOUT = 5000;

//	static final String SHARED_PREFFERENCE_CHANNEL_POSITION_KEY = "PlayingChannelPosition";
	static final String SHARED_PREFFERENCE_CHANNEL_NAME_KEY = "PlayingChannelName";
	static final String CURRENT_TIME_KEY = "currentTime";

	static final boolean GRID_VIEW = true;

	boolean timerStarted = false;
	boolean waitingForInternetConnection = false;
	boolean activityCreated = false;
	boolean checkerStarted = false;

	int selectedCategory = 0;
	
	io.vov.vitamio.widget.VideoView m_vitamioView;

	String M3Uurl;
	String thumbnailUrlPrefix = "http://logo.albiptv.ch/";
	ArrayList<M3UElement> m3uList;
	Activity parent;
	Player player;
	ListView channelList;
	GridView channelGrid;
	View gridPanel;
	View infoPanel, errorPanel, urlPanel, jumpToChannelView;
	TextView erorMessage, channelNumber, gridViewSelectedChannelName;
	AlertDialog shutdownDialog;
	AlertDialog urlDialog;
	DisplayImageOptions defaultImageOptions;
	ImageLoader iLoader;
	ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
	ScheduledFuture standbyCheckerTask;
	
	TextView		m_txtState = null;

	ArrayList<Integer> keySequence = new ArrayList<Integer>();

	private float touchEventX1, touchEventX2;
	
	private static Timer mTimer;
	private static UpdateDurationTask mDurationTask;

    private class UpdateDurationTask extends TimerTask {

        @Override
        public void run() {
        	runOnUiThread(new Runnable() {
                @Override
                public void run() {
                	updateDurationTask();
                }
            });
        }
    }
    
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case DISPLAY_CHANNEL_LIST:
				m3uList = M3UParser.getM3UParser(parent).getElementForCategory(selectedCategory);
				hideErrorPanel();
				if (GRID_VIEW) {
					displayChannelsInGridView();
				} else {
					displayChannels();
				}
				if (selectedCategory == LIVE_TV_CATEGORY) {
					if (!parent.isFinishing() && !player.isPlaying() && !StandbyActivity.SHOWING) {
//						playLastKnownChannel();
					}
				}
				break;

			case LIST_READY:
				// Maybe it is not needed
				break;

			case SURFACE_LAYOUT:
				player.setSize(msg.arg1, msg.arg2);
				break;

			case UPDATE_TIMER:
				String time = msg.getData().getString(CURRENT_TIME_KEY);
				updateTime(time);
				break;

			case HIDE_INFO_PANEL:
				hideInfoPanel();
				break;

			case PLAYER_ERROR:
				showErrorPanel(getResources().getString(R.string.stream_error));
				Log.i(TAG, getResources().getString(R.string.stream_error));
				break;

			case M3U_PARSING_ERROR:
				showErrorPanel("Failed to parse M3U list from link : " + M3Uurl);
				Log.i(TAG, "Failed to parse M3U List : ");
				break;

			case RECONNECT:
				player.reconnect();
				break;

			case VIDEO_STARTED:
				Log.i(TAG, "Video started, hiding error panel !!!");
				hideErrorPanel();
				break;

			case WAITING_FOR_NETWORK_CONNECTION:
				Log.i(TAG, "Internet connection now available, waiting for connection ...");
				showErrorPanel("Waiting for internet connection !!!");
				break;

			case JUMP_TO_CHANNEL:
				playChannelNumber(msg.arg1);
				break;

			case CANEL_JUMP_TO_CHANNEL:
				mHandler.removeCallbacks(JumpToChannel);
				hideJumpToChanelView();
				break;
				
			case SIMULATE_SLEEP_MODE:
				simulateSleepMode();
				break;

			default:
				Log.v(TAG, "Unhandled message: " + msg.what);
				break;
			}
		}
	};

	Runnable hideInfoPannelRunnable = new Runnable() {
		@Override
		public void run() {
			mHandler.obtainMessage(HIDE_INFO_PANEL).sendToTarget();
		}
	};

	Runnable JumpToChannel = new Runnable() {

		@Override
		public void run() {
			try {
				String text = channelNumber.getText().toString();
				int currentChannel = Integer.parseInt(text);
				currentChannel--;
				Message msg = mHandler.obtainMessage(JUMP_TO_CHANNEL);
				msg.arg1 = currentChannel;
				msg.sendToTarget();
			} catch (NumberFormatException e) {
				Log.e(TAG, "Bad value for jump to channel string !!!");
			}
		}
	};

	OnTouchListener channelListTouchListener = new OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			handleTouchEvent(event);
			return false;
		}
	};

	OnItemClickListener channelClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> a, View v, int position, long id) {
			if (GRID_VIEW) {
				playSelectedStreamInGridView(position);
			} else {
				playSelectedStreamInList(position);
			}
		}
	};

	OnKeyListener channelComponentKeyListener = new OnKeyListener() {

		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			StandbyActivity.setKeyEventTime();
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				onBackPressed();
				return true;
			}
			if (GRID_VIEW) {
				int columns = channelGrid.getNumColumns();
				int elements = channelGrid.getCount();
				int currentRow = channelGrid.getSelectedItemPosition() / columns;
				int numberOfRows = elements / columns;
				if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
					if (currentRow == 0) {
						return true;
					}
				}
				if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
					if (currentRow == numberOfRows) {
						return true;
					}
				}
			}
			return false;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		StandbyActivity.setKeyEventTime();
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_player);
		setupTimer();
		
		findViewById(R.id.btn_prev_down).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				buttonState = BACKRWARD_DOWN;	
				updateDurationTask();
			}
		});
		
		findViewById(R.id.btn_prev_up).setOnClickListener(new View.OnClickListener() {
					
			@Override
			public void onClick(View v) {
				buttonState = NONE_BOTH;	
				updateDurationTask();
			}
		});
		
		findViewById(R.id.btn_next_down).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				buttonState = FORWARD_DOWN;	
				updateDurationTask();
			}
		});
		
		findViewById(R.id.btn_next_up).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				buttonState = NONE_BOTH;
				updateDurationTask();
			}
		});
		
		m_txtState = (TextView)findViewById(R.id.txt_state);
	}

	MediaController controller = null;
	
	@Override
	protected void onResume() {
		super.onResume();
		StandbyActivity.setKeyEventTime();
		setupStandbyChecker();
		checkAction();
		
		buttonState = NONE_BOTH;
		mTimer = new Timer();
        mDurationTask = new UpdateDurationTask();
		mTimer.schedule(mDurationTask, 0, 4000);
		
		if (!activityCreated) {
			M3Uurl = Helper.getM3UListUrl(this);
			parent = this;
			if (!Helper.isConnected(parent)) {
				waitingForInternetConnection = true;
				mHandler.obtainMessage(WAITING_FOR_NETWORK_CONNECTION).sendToTarget();
			}
//			parseM3u8List();
//			SurfaceView view = (SurfaceView) findViewById(R.id.surface);
			m_vitamioView = (VideoView) findViewById(R.id.vitamio_view);
			m_vitamioView.setHardwareDecoder(true);
			
			m_vitamioView.setVideoLayout(VideoView.VIDEO_LAYOUT_STRETCH, 0);
			
			if( selectedCategory != LIVE_TV_CATEGORY )
			{
				controller = new MediaController(parent);				
				m_vitamioView.setMediaController(controller);	
			}
			else 
				controller = null;
			
			
			player = new Player(this, m_vitamioView, mHandler);
			infoPanel = findViewById(R.id.info_panel);
			errorPanel = findViewById(R.id.error_panel);
			jumpToChannelView = findViewById(R.id.jump_to_chanel_panel);
			erorMessage = (TextView) findViewById(R.id.error_message);
			gridViewSelectedChannelName = (TextView) findViewById(R.id.selected_grid_channel_name);
			channelNumber = (TextView) findViewById(R.id.channel_number_display);
			gridPanel = findViewById(R.id.gridview_panel);

			jumpToChannelView.setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					StandbyActivity.setKeyEventTime();
					switch (keyCode) {
					case KeyEvent.KEYCODE_DPAD_CENTER:						
						mHandler.post(JumpToChannel);
						return true;

					case KeyEvent.KEYCODE_BACK:
						mHandler.obtainMessage(CANEL_JUMP_TO_CHANNEL).sendToTarget();
						return true;

					default:
						Log.i(TAG, "Unhandled key event : " + keyCode + " for jumpToChannel view !!!");
					}
					return false;
				}
			});
			
			// tDownloader = new ThumbnailDownloader(this, mHandler);
			// tDownloader.start();

			// UNIVERSAL IMAGE LOADER SETUP
			defaultImageOptions = new DisplayImageOptions.Builder().cacheOnDisc(true).cacheInMemory(true)
					.imageScaleType(ImageScaleType.EXACTLY).displayer(new FadeInBitmapDisplayer(300)).build();

			ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(getApplicationContext())
					.defaultDisplayImageOptions(defaultImageOptions).memoryCache(new WeakMemoryCache())
					.discCacheSize(100 * 1024 * 1024).build();

			ImageLoader.getInstance().init(config);
			iLoader = ImageLoader.getInstance();
			activityCreated = true;
		}
	}
	
	private void checkAction() {
		String action = getIntent().getAction();
		if (action != null) {
			if (action.equalsIgnoreCase(HomeActivity.PLAY_LIVE_TV)) {
				selectedCategory = LIVE_TV_CATEGORY;
			}
			if (action.equalsIgnoreCase(HomeActivity.PLAY_RADIO_TV)) {
				selectedCategory = RADIO_TV_CATEGORY;
			}
			if (action.equalsIgnoreCase(HomeActivity.PLAY_VOD_1)) {
				selectedCategory = VOD_TV_CATEGORY1;
			}
			if (action.equalsIgnoreCase(HomeActivity.PLAY_VOD_2)) {
				selectedCategory = VOD_TV_CATEGORY2;
			}
			if (action.equalsIgnoreCase(HomeActivity.PLAY_VOD_3)) {
				selectedCategory = VOD_TV_CATEGORY3;
			}
			parseM3u8List();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		mDurationTask.cancel();
		mTimer.cancel();
		
		
		cancelStandbyChecker();
		player.stop();

	}

	@Override
	protected void onDestroy() {
		player.stop();
		
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		if ((channelList != null && channelList.getVisibility() == View.VISIBLE)
				|| (channelGrid != null && gridPanel.getVisibility() == View.VISIBLE)) {
			if (GRID_VIEW) {
				hideGridView();
			} else {
				hideChannelList();
			}
		} else {
			backToHomeMenu();
		}
		
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		StandbyActivity.setKeyEventTime();
		if (handleKeyEvent(keyCode, event)) {
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		StandbyActivity.setKeyEventTime();
		handleKeyUpEvent(keyCode, event);
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		handleTouchEvent(event);
		return super.onTouchEvent(event);
	}

	private void backToHomeMenu() {
		Intent i = new Intent(this, HomeActivity.class);
		switch (selectedCategory) {
		case LIVE_TV_CATEGORY:
			i.setAction(HomeActivity.PLAY_LIVE_TV);
			break;
		case RADIO_TV_CATEGORY:
			i.setAction(HomeActivity.PLAY_RADIO_TV);
			break;
		case VOD_TV_CATEGORY1:
			i.setAction(HomeActivity.PLAY_VOD_1);
			break;

		case VOD_TV_CATEGORY2:
			i.setAction(HomeActivity.PLAY_VOD_2);
			break;
		case VOD_TV_CATEGORY3:
			i.setAction(HomeActivity.PLAY_VOD_3);
			break;
		}
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(i);
		finish();
	}

	private void handleKeyUpEvent(int keyCode, KeyEvent event) {
		StandbyActivity.setKeyEventTime();
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
				|| keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			keySequence.add(keyCode);
		} else {
			keySequence.clear();
		}
		
		if (keySequence.size() >= 5) {
			checkKeyCombination();
		}
		
		if( keyCode != 57 )
		{
			buttonState = NONE_BOTH;
			updateDurationTask();
		}
	}
	
	private long previousClick = 0;
	private int  buttonState = NONE_BOTH;
	
	private static final int BACKRWARD_DOWN = 0;
	private static final int FORWARD_DOWN = 1;
	private static final int NONE_BOTH = 2;
	
	private void  updateDurationTask()
	{
		if( selectedCategory == LIVE_TV_CATEGORY )
			return;
		
		if( buttonState == NONE_BOTH || m_vitamioView.isInPlaybackState() == false )
		{
			controller.hide();
			m_txtState.setVisibility(View.GONE);
			previousClick = 0;
			return;
		}
		
		controller.show(8000);
		
		if( previousClick == 0 )
			previousClick = System.currentTimeMillis() - 4 * 1000;		
		
		m_txtState.setVisibility(View.VISIBLE);
		
		long current = System.currentTimeMillis();
		long gap = current - previousClick;
		previousClick = current;
		
		long currentPos = m_vitamioView.getCurrentPosition();
		
		if( buttonState == BACKRWARD_DOWN )
		{
			m_txtState.setText("Backward 8x");
			currentPos -= gap * 8;
		}
		else if( buttonState == FORWARD_DOWN )
		{
			m_txtState.setText("Forward 8x");
			currentPos += gap * 8;
		}
		
		if( currentPos < 0 || currentPos > m_vitamioView.getDuration() )
			currentPos = 0;
		
		m_vitamioView.seekTo(currentPos);
	}	
	
	private boolean handleKeyEvent(int keyCode, KeyEvent event) {
		StandbyActivity.setKeyEventTime();
		Log.i(TAG, "Key with code : " + keyCode + " , pressed !!!");
		if( keyCode != 57 )
			buttonState = NONE_BOTH;
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_LEFT:
			checkKeyCombination();
			buttonState = BACKRWARD_DOWN;
			updateDurationTask();
			break;

		case KeyEvent.KEYCODE_DPAD_RIGHT:
			buttonState = FORWARD_DOWN;
			updateDurationTask();
			break;

		case KeyEvent.KEYCODE_ENTER:
		case KeyEvent.KEYCODE_DPAD_CENTER:
			okButtonPressed();
			break;

		case KeyEvent.KEYCODE_CHANNEL_UP:
		case KeyEvent.KEYCODE_DPAD_UP:
			// channelList.onKeyDown(keyCode, event);
			// playSelectedStream(-1);
			playNextChannelFromList();
			break;

		case KeyEvent.KEYCODE_CHANNEL_DOWN:
		case KeyEvent.KEYCODE_DPAD_DOWN:
			// channelList.onKeyDown(keyCode, event);
			// playSelectedStream(-1);
			playPreviousChannelFromList();
			break;

		case KeyEvent.KEYCODE_VOLUME_UP:
			// player.volumeUp();
			// do nothing, let the box handle volume it self
			checkKeyCombination();
			break;

		case KeyEvent.KEYCODE_VOLUME_DOWN:
			// player.volumeDown();
			// do nothing, let the box handle volume it self
			break;

		case KeyEvent.KEYCODE_0:
			addDigitToChannelNumberTV("0");
			break;

		case KeyEvent.KEYCODE_1:
			addDigitToChannelNumberTV("1");
			break;

		case KeyEvent.KEYCODE_2:
			addDigitToChannelNumberTV("2");
			break;

		case KeyEvent.KEYCODE_3:
			addDigitToChannelNumberTV("3");
			break;

		case KeyEvent.KEYCODE_4:
			addDigitToChannelNumberTV("4");
			break;

		case KeyEvent.KEYCODE_5:
			addDigitToChannelNumberTV("5");
			break;

		case KeyEvent.KEYCODE_6:
			addDigitToChannelNumberTV("6");
			break;

		case KeyEvent.KEYCODE_7:
			addDigitToChannelNumberTV("7");
			break;

		case KeyEvent.KEYCODE_8:
			addDigitToChannelNumberTV("8");
			break;

		case KeyEvent.KEYCODE_9:
			addDigitToChannelNumberTV("9");
			break;

		case KeyEvent.KEYCODE_BACK:
			onBackPressed();
			return true;

		default:
			Log.i(TAG, "Not handeling key with code : " + keyCode);
		}
		return false;
	}

	private void addDigitToChannelNumberTV(String digit) {
		mHandler.removeCallbacks(JumpToChannel);
		String text = channelNumber.getText().toString();
		if (text.equalsIgnoreCase("----")) {
			text = "";
		}
		text = text + digit;
		int currentChannel = Integer.parseInt(text);
		if (GRID_VIEW) {
			if (currentChannel > channelGrid.getAdapter().getCount()) {
				text = "----";
			}
		} else {
			MyArrayAdapter adpt = (MyArrayAdapter) channelList.getAdapter();
			if (currentChannel > adpt.getActualCount()) {
				text = "----";
			}
		}
		channelNumber.setText(text);
		mHandler.postDelayed(JumpToChannel, JUMP_TO_CHANNEL_TIMEOUT);
		showJumpToChannelView();
	}

	private void showJumpToChannelView() {
		jumpToChannelView.setVisibility(View.VISIBLE);
		jumpToChannelView.requestFocus();
	}

	private void hideJumpToChanelView() {
		channelNumber.setText("----");
		jumpToChannelView.setVisibility(View.GONE);
	}

	private void playChannelNumber(int number) {
		if (GRID_VIEW) {
			channelGrid.requestFocusFromTouch();
			channelGrid.setSelection(number);
			channelGrid.performItemClick(channelGrid.getAdapter().getView(number, null, null), number,
					channelGrid.getAdapter().getItemId(number));
			hideJumpToChanelView();
		} else {
			channelList.requestFocusFromTouch();
			channelList.setSelection(number);
			channelList.performItemClick(channelList.getAdapter().getView(number, null, null), number,
					channelList.getAdapter().getItemId(number));
			hideJumpToChanelView();
		}
	}

	private void checkKeyCombination() {
		int sequenceNumber = 0;
		for (int keyCode : keySequence) {
			sequenceNumber = checkKeyForCombination(sequenceNumber, keyCode);
			if (sequenceNumber == 5) {
				showUrlMenu();
				keySequence.clear();
				return;
			}
		}
	}

	private int checkKeyForCombination(int sequenceNumber, int keyCode) {
		if (sequenceNumber == 0) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
				return 1;
			} else {
				return 0;
			}
		}
		if (sequenceNumber == 1) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
				return 2;
			} else {
				return checkKeyForCombination(0, keyCode);
			}
		}
		if (sequenceNumber == 2) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				return 3;
			} else {
				return checkKeyForCombination(0, keyCode);
			}
		}
		if (sequenceNumber == 3) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
				return 4;
			} else {
				return checkKeyForCombination(0, keyCode);
			}
		}
		if (sequenceNumber == 4) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
				return 5;
			} else {
				return checkKeyForCombination(0, keyCode);
			}
		}
		return 0;
	}

	private void showUrlMenu() {
		if (urlDialog == null || !urlDialog.isShowing()) {
			AlertDialog.Builder alertDialog = new AlertDialog.Builder(parent);
			alertDialog.setTitle("New URL");
			alertDialog.setMessage("Enter new server URL");

			final EditText input = new EditText(parent);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.MATCH_PARENT);
			input.setLayoutParams(lp);
			alertDialog.setView(input);

			alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					String newUrl = input.getText().toString();
					M3Uurl = newUrl;
					Helper.saveM3UListUrl(parent, newUrl);
					parseM3u8List();
					dialog.cancel();
				}
			});

			alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});

			urlDialog = alertDialog.create();
			urlDialog.show();
			input.requestFocus();
			InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
		}
	}

	private void showErrorPanel(String message) {
		erorMessage.setText(message);
		errorPanel.setVisibility(View.VISIBLE);
	}

	private void hideErrorPanel() {
		errorPanel.setVisibility(View.GONE);
	}

	private void setupTimer() {
		if (timerStarted)
			return;
		scheduler.scheduleAtFixedRate(new Runnable() {
			public void run() {
				SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
				String currentTime = sdf.format(new Date());
				Message m = mHandler.obtainMessage(UPDATE_TIMER);
				Bundle b = new Bundle();
				b.putString(CURRENT_TIME_KEY, currentTime);
				m.setData(b);
				m.sendToTarget();
			}
		}, 0, 1, TimeUnit.SECONDS);
		timerStarted = true;
	}
	
	private void setupStandbyChecker() {
		if (checkerStarted)
			return;
		standbyCheckerTask = scheduler.scheduleAtFixedRate(new Runnable() {
			public void run() {
				long timeDiff = System.currentTimeMillis() - StandbyActivity.lastKeyEventAt.get();
				if (timeDiff > StandbyActivity.STANDBY_INTERVAL && Helper.isConnected(parent)) {
					if (!StandbyActivity.SHOWING) {
						Log.i(TAG, "Simulating sleep mode, timeDiff : " + timeDiff + " , currentTime : "
								+ System.currentTimeMillis() + " , last key event : " + StandbyActivity.lastKeyEventAt
								+ " , stand by interval : " + StandbyActivity.STANDBY_INTERVAL);
						mHandler.obtainMessage(SIMULATE_SLEEP_MODE).sendToTarget();
					}
				}
			}
		}, 0, 5, TimeUnit.MINUTES);
		checkerStarted = true;
	}
	
	private void cancelStandbyChecker() {
		if (!checkerStarted)
			return;
		standbyCheckerTask.cancel(true);
		checkerStarted = false;
	}
	
	private void simulateSleepMode() {
		Intent i = new Intent(this, StandbyActivity.class);
		i.putExtra(StandbyActivity.STANDBY_CALLER_KEY, StandbyActivity.CALLER_ACTIVITY_PLAYER);
		i.setAction(getIntent().getAction());
		startActivity(i);
		finish();
	}
	
	private void reumeFromStandby() {
		if (GRID_VIEW) {
			if (channelGrid.getVisibility() == View.VISIBLE) {
				channelGrid.requestFocusFromTouch();
			} else {
//				recreateActivity();
				playLastKnownChannel();
			}
		} else {
			if (channelList.getVisibility() == View.VISIBLE) {
				channelList.requestFocusFromTouch();
			} else {
//				recreateActivity();
				playLastKnownChannel();
			}
		}
	}
	
	private void recreateActivity () {
		Log.w(TAG, "Recreate activiti called !!!");
        Intent i = getIntent();
        startActivity(i);
        finish();
	} 

	private void updateTime(String time) {
		TextView currentTime = (TextView) findViewById(R.id.current_time);
		currentTime.setText(time);
	}

	private void handleTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			touchEventX1 = event.getX();
			break;
		case MotionEvent.ACTION_UP:
			touchEventX2 = event.getX();
			float deltaX = touchEventX2 - touchEventX1;
			if (Math.abs(deltaX) > MIN_DISTANCE) {
				if (deltaX > 0) {
					if (GRID_VIEW) {
						// TODO implement GridView functionality
					} else {
						channelList.startAnimation(inFromLeftAnimation());
						channelList.setVisibility(View.VISIBLE);
					}
				} else {
					if (GRID_VIEW) {

					} else {
						channelList.startAnimation(outToLeftAnimation());
						channelList.setVisibility(View.INVISIBLE);
					}
				}
			} else {
				// consider as something else - a screen tap for example
			}
			break;
		}
	}

	private void playNextChannelFromList() {
		AbsListView chView;
		if (GRID_VIEW) {
			chView = channelGrid;
		} else {
			chView = channelList;
		}
		int currentPosition = chView.getSelectedItemPosition();
		if ((currentPosition + 1) > (chView.getAdapter().getCount() - 1)) {
			currentPosition = 0;
		} else {
			currentPosition += 1;
		}
		chView.requestFocusFromTouch();
		chView.setSelection(currentPosition);
		chView.performItemClick(chView.getAdapter().getView(currentPosition, null, null), currentPosition,
				chView.getAdapter().getItemId(currentPosition));
	}

	private void playPreviousChannelFromList() {
		AbsListView chView;
		if (GRID_VIEW) {
			chView = channelGrid;
		} else {
			chView = channelList;
		}
		int currentPosition = chView.getSelectedItemPosition();
		if ((currentPosition - 1) < 0) {
			currentPosition = chView.getAdapter().getCount() - 1;
		} else {
			currentPosition -= 1;
		}
		chView.requestFocusFromTouch();
		chView.setSelection(currentPosition);
		chView.performItemClick(chView.getAdapter().getView(currentPosition, null, null), currentPosition,
				chView.getAdapter().getItemId(currentPosition));
	}

	private void okButtonPressed() {
		if (GRID_VIEW) {
			showGridView();
		} else {
			showChannelList();
		}
	}

	private void hideChannelList() {
		if (channelList.getVisibility() == View.VISIBLE) {
			// animate playlist
			channelList.startAnimation(outToLeftAnimation());
			channelList.setVisibility(View.GONE);
		}
	}

	private void hideGridView() {
		if (channelGrid != null)
			channelGrid.setVisibility(View.GONE);
		gridPanel.setVisibility(View.GONE);
	}

	private void showChannelList() {
		if (channelList.getVisibility() != View.VISIBLE) {
			/// animate channelList
			channelList.startAnimation(inFromLeftAnimation());
			channelList.setVisibility(View.VISIBLE);
		}
	}

	private void showGridView() {
		if (channelGrid != null) {
			channelGrid.setVisibility(View.VISIBLE);
			channelGrid.requestFocusFromTouch();
		}
		gridPanel.setVisibility(View.VISIBLE);
	}

	private void hideInfoPanel() {
		if (infoPanel.getVisibility() == View.VISIBLE) {
			// animate info panel
			infoPanel.startAnimation(outToBottomAnimation());
			infoPanel.setVisibility(View.GONE);
		}
	}

	private void showInfoPanel() {
		if( controller != null )
			controller.hide();
		if (infoPanel.getVisibility() != View.VISIBLE) {
			// animate info panel
			infoPanel.startAnimation(inFromBottomAnimation());
			infoPanel.setVisibility(View.VISIBLE);
			mHandler.removeCallbacks(hideInfoPannelRunnable);
			mHandler.postDelayed(hideInfoPannelRunnable, INFO_PANEL_SHOW_PERIOD);
		}
	}

	private void parseM3u8List() {
		// register handler for m3UParser
		M3UParser parser = M3UParser.getM3UParser(parent);
		parser.registerHandler(mHandler);
		m3uList = parser.getElementForCategory(selectedCategory);
		if (m3uList != null) {
			// List ready, trigger handler
			mHandler.obtainMessage(DISPLAY_CHANNEL_LIST).sendToTarget();
		}
	}

	private Animation inFromRightAnimation() {

		Animation inFromRight = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, +1.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
				0.0f);
		inFromRight.setDuration(500);
		inFromRight.setInterpolator(new AccelerateInterpolator());
		return inFromRight;
	}

	private Animation outToLeftAnimation() {
		Animation outtoLeft = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
				-1.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
		outtoLeft.setDuration(500);
		outtoLeft.setInterpolator(new AccelerateInterpolator());
		return outtoLeft;
	}

	private Animation inFromLeftAnimation() {
		Animation inFromLeft = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, -1.0f, Animation.RELATIVE_TO_PARENT,
				0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
		inFromLeft.setDuration(500);
		inFromLeft.setInterpolator(new AccelerateInterpolator());
		return inFromLeft;
	}

	private Animation outToRightAnimation() {
		Animation outtoRight = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
				+1.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
		outtoRight.setDuration(500);
		outtoRight.setInterpolator(new AccelerateInterpolator());
		return outtoRight;
	}

	private Animation outToBottomAnimation() {
		Animation outtoRight = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
				0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, +1.0f);
		outtoRight.setDuration(500);
		outtoRight.setInterpolator(new AccelerateInterpolator());
		return outtoRight;
	}

	private Animation inFromBottomAnimation() {
		Animation outtoRight = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
				0.0f, Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
		outtoRight.setDuration(500);
		outtoRight.setInterpolator(new AccelerateInterpolator());
		return outtoRight;
	}

	private void displayChannels() {
		if (m3uList != null) {
			Log.i(TAG, "Displaying play list !!!");

			MyArrayAdapter adapter = new MyArrayAdapter(this, m3uList);
			channelList = (ListView) findViewById(R.id.channel_list);
			channelList.setAdapter(adapter);
			channelList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			channelList.setOnItemClickListener(channelClickListener);
			channelList.setOnTouchListener(channelListTouchListener);
			channelList.setOnKeyListener(channelComponentKeyListener);
			channelList.setVisibility(View.VISIBLE);
			
			SharedPreferences pref = getSharedPreferences(TAG, MODE_PRIVATE);
			String lastKnownChannelName = pref.getString(SHARED_PREFFERENCE_CHANNEL_NAME_KEY, "");
			
			int lastKnownChannelPosition = 0;
			for(int i = 0; i < m3uList.size(); i++ )
			{
				M3UElement item = m3uList.get(i);
				if( item == null )
					continue;
				
				if( lastKnownChannelName.equals( selectedCategory + "_" +  item.getName() ) )
				{
					lastKnownChannelPosition = i;
					break;
				}
			}
			
			channelList.setSelection(lastKnownChannelPosition);
			
			channelList.requestFocus();
		}
	}

	private void displayChannelsInGridView() {
		if (m3uList != null) {
			Log.i(TAG, "Displaying channels in grid !!!");
			MyGridAdapter adapter = new MyGridAdapter(this, m3uList);
			channelGrid = (GridView) findViewById(R.id.channel_gridview);
			channelGrid.setAdapter(adapter);
			channelGrid.setOnItemClickListener(channelClickListener);
			channelGrid.setOnTouchListener(channelListTouchListener);
			channelGrid.setOnKeyListener(channelComponentKeyListener);
			
			SharedPreferences pref = getSharedPreferences(TAG, MODE_PRIVATE);
			String lastKnownChannelName = pref.getString(SHARED_PREFFERENCE_CHANNEL_NAME_KEY, "");
			
			int lastKnownChannelPosition = 0;
			for(int i = 0; i < m3uList.size(); i++ )
			{
				M3UElement item = m3uList.get(i);
				if( item == null )
					continue;
				
				if( lastKnownChannelName.equals( selectedCategory + "_" +  item.getName() ) )
				{
					lastKnownChannelPosition = i;
					break;
				}
			}
			
			channelGrid.setSelection(lastKnownChannelPosition);
			
			android.view.ViewGroup.LayoutParams lp = channelGrid.getLayoutParams();
			Display display = getWindowManager().getDefaultDisplay();
			lp.width = display.getWidth() / 10 * 8;
			lp.height = display.getHeight() / 10 * 8;
			channelGrid.setLayoutParams(lp);
			channelGrid.invalidate();
			if (player != null && !player.isPlaying()) {
				channelGrid.setVisibility(View.VISIBLE);
				channelGrid.requestFocus();
				channelGrid.invalidateViews();
			}

			channelGrid.setOnItemSelectedListener(new OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					M3UElement element = (M3UElement) channelGrid.getItemAtPosition(position);
					String text = (position + 1) + ". " + element.getName();
					gridViewSelectedChannelName.setText(text);
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
					// TODO Auto-generated method stub

				}
			});
		}
	}

	private void playSelectedStreamInList(int position) {
		if (position == -1) {
			position = channelList.getSelectedItemPosition();
		}
		M3UElement element = (M3UElement) channelList.getItemAtPosition(position);
		MyArrayAdapter adapter = (MyArrayAdapter) channelList.getAdapter();
		position = position % adapter.getActualCount();
		playStream(position, element);
		hideChannelList();
		showInfoPanel();
	}

	private void playSelectedStreamInGridView(int position) {
		M3UElement element = (M3UElement) channelGrid.getItemAtPosition(position);
		playStream(position, element);
		hideGridView();
		showInfoPanel();
	}

	private void playStream(int position, M3UElement element) {
//		if (selectedCategory == LIVE_TV_CATEGORY) {
			// save previously played live channel
			SharedPreferences.Editor editor = getSharedPreferences(TAG, MODE_PRIVATE).edit();
//			editor.putInt(SHARED_PREFFERENCE_CHANNEL_POSITION_KEY, position);
			editor.putString(SHARED_PREFFERENCE_CHANNEL_NAME_KEY, selectedCategory + "_" +  element.getName());
			
			editor.commit();
//		}
		ImageView playingChannelLogo = (ImageView) findViewById(R.id.playing_channel_logo);
		TextView playingChannelName = (TextView) findViewById(R.id.playing_channel_label);
		String logoName = element.getName().toLowerCase().replace(" ", "_") + ".png";
		String thumbnailUrl = "http://logo.albiptv.ch/" + logoName;
		iLoader.displayImage(thumbnailUrl, playingChannelLogo, defaultImageOptions);
		playingChannelName.setText(element.getName());
		player.play(element.getUrl());
	}

	private void playLastKnownChannel() {
		int x = KeyEvent.KEYCODE_MEDIA_NEXT;
		int y = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
		
		AbsListView chList;
//		int channel_offset = 0;
		if (GRID_VIEW) {
			chList = channelGrid;
		} else {
			chList = channelList;
			MyArrayAdapter adapter = (MyArrayAdapter) chList.getAdapter();
//			channel_offset = adapter.getActualCount() * 100000;
		}
		SharedPreferences pref = getSharedPreferences(TAG, MODE_PRIVATE);
//		int lastKnownChannelPosition = pref.getInt(SHARED_PREFFERENCE_CHANNEL_POSITION_KEY, -1);
		String lastKnownChannelName = pref.getString(SHARED_PREFFERENCE_CHANNEL_NAME_KEY, "");
		
		int lastKnownChannelPosition = 0;
		for(int i = 0; i < chList.getCount(); i++ )
		{
			M3UElement item = (M3UElement)chList.getAdapter().getItem(i);
			if( item == null )
				continue;
			
			if( lastKnownChannelName.equals( selectedCategory + "_" +  item.getName() ) )
			{
				lastKnownChannelPosition = i;
				break;
			}
		}

		if (lastKnownChannelPosition < 0 || lastKnownChannelPosition > chList.getCount()) {
			Log.i(TAG, "Last played channel unknown !!!");
			chList.requestFocusFromTouch();
			chList.setSelection(0);
			return;
		}
		
//		lastKnownChannelPosition = 10;
		
		// channelList.setItemChecked(lastKnownChannelPosition, true);
//		lastKnownChannelPosition = lastKnownChannelPosition;
		chList.requestFocusFromTouch();
		chList.setSelection(lastKnownChannelPosition);
		chList.performItemClick(chList.getAdapter().getView(lastKnownChannelPosition, null, null),
				lastKnownChannelPosition, chList.getAdapter().getItemId(lastKnownChannelPosition));
		// player.play("http://somethingwrong.com");
	}

	class MyArrayAdapter extends BaseAdapter {

		private final Context context;
		private final ArrayList<M3UElement> values;

		public MyArrayAdapter(Context context, ArrayList<M3UElement> values) {
			this.context = context;
			this.values = values;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View rowView = inflater.inflate(R.layout.channel_list_row_layout, parent, false);
			TextView channel_name = (TextView) rowView.findViewById(R.id.channel_label);
			TextView channel_number = (TextView) rowView.findViewById(R.id.channel_number);
			ImageView logo = (ImageView) rowView.findViewById(R.id.logo);
			M3UElement element = values.get(position % values.size());
			channel_name.setText(element.getName());
			channel_number.setText(((position % values.size()) + 1) + ". ");

			// FORM URL when server start supporting this feature !!!
			String logoName = element.getName().toLowerCase().replace(" ", "_") + ".png";
			String thumbnailUrl = "http://logo.albiptv.ch/" + logoName;
			// tDownloader.requestloadImage(thumbnailUrl, logo);
			iLoader.displayImage(thumbnailUrl, logo, defaultImageOptions);
			return rowView;
		}

		@Override
		public int getCount() {
			if (values != null) {
				return Integer.MAX_VALUE;
			}
			return 0;
		}

		public int getActualCount() {
			if (values != null) {
				return values.size();
			}
			return 0;
		}

		@Override
		public Object getItem(int position) {
			return values.get(position % values.size());
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
	}

	class MyGridAdapter extends BaseAdapter {

		private final Context context;
		private final ArrayList<M3UElement> values;

		public MyGridAdapter(Context context, ArrayList<M3UElement> values) {
			this.context = context;
			this.values = values;
		}

		@Override
		public int getCount() {
			if (values != null) {
				return values.size();
			}
			return 0;
		}

		@Override
		public Object getItem(int position) {
			if (values != null) {
				return values.get(position);
			}
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			M3UElement element = values.get(position);
			View elementView = inflater.inflate(R.layout.grid_element_layout, parent, false);
			TextView channel_name = (TextView) elementView.findViewById(R.id.grid_channel_label);
			TextView channel_number = (TextView) elementView.findViewById(R.id.grid_channel_number);
			ImageView logo = (ImageView) elementView.findViewById(R.id.grid_logo);
			channel_name.setText(element.getName());
			channel_number.setText((position + 1) + ". ");
			// FORM URL when server start supporting this feature !!!
			String logoName = element.getName().toLowerCase().replace(" ", "_") + ".png";
			String thumbnailUrl = "http://logo.albiptv.ch/" + logoName;
			// tDownloader.requestloadImage(thumbnailUrl, logo);
			iLoader.displayImage(thumbnailUrl, logo, defaultImageOptions);
			return elementView;
		}

	}

}
