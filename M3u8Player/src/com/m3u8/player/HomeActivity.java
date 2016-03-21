package com.m3u8.player;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import io.vov.vitamio.Vitamio;

public class HomeActivity extends Activity {

	public static final String TAG = "HomeActivity";
	public static final String PLAY_LIVE_TV = "LIVE TV";
	public static final String PLAY_VOD_1 = "VOD 1";
	public static final String PLAY_VOD_2 = "VOD 2";
	public static final String PLAY_VOD_3 = "VOD 3";
	public static final String PLAY_RADIO_TV = "RADIO TV";

	GridView menu;
	MyGridAdapter adapter;
	
	int menuDepth = 0;
	ArrayList<String> levelOneOptions = new ArrayList<String>();
	ArrayList<String> levelTwoOptions = new ArrayList<String>();

	Activity parent;
	AlertDialog shutdownDialog;
	Handler mHandler;
	ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

	boolean checkerStarted = false;
	int		m_nSelectNum = 0;

	ScheduledFuture standbyCheckerTask;

	OnItemClickListener menuClickListener = new OnItemClickListener() {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (menuDepth == 0 && position == 1) {
				showVodMenu();
			} else {
				String action = getActionForCurrrentState(position);
				startPlayerActivity(action);
			}

		}
	};

	OnKeyListener menuKeyListener = new OnKeyListener() {

		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			StandbyActivity.setKeyEventTime();
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_DOWN:				
			case KeyEvent.KEYCODE_DPAD_UP:
				return true;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				if( event.getAction() == KeyEvent.ACTION_UP)
				{
					m_nSelectNum = (m_nSelectNum - 1) % menu.getCount();
					menu.setSelection(m_nSelectNum);
					adapter.notifyDataSetChanged();
				}
				return false;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if( event.getAction() == KeyEvent.ACTION_UP)
				{
					m_nSelectNum = (m_nSelectNum + 1) % menu.getCount();
					menu.setSelection(m_nSelectNum);
					adapter.notifyDataSetChanged();
				}
				return false;

			case KeyEvent.KEYCODE_BACK:
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					if (menuDepth == 1) {
						showFirstMenu();
						return true;
					} else if (menuDepth == 0) {
						showExitMenu();
						return true;
					}
				}
			}
			return false;
		}
	};

	@Override
	protected void onResume() {
		StandbyActivity.setKeyEventTime();
		super.onResume();
		setupStandbyChecker();
	}

	@Override
	protected void onPause() {
		super.onPause();
		cancelStandbyChecker();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Vitamio.isInitialized(this);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
		super.onCreate(savedInstanceState);

		StandbyActivity.setKeyEventTime();
		parent = this;
		setContentView(R.layout.activity_home);

		// parse the list if internet is ready
		M3UParser.getM3UParser(this);

		levelOneOptions.add("Live TV");
		levelOneOptions.add("Kinema");
		levelOneOptions.add("Radio");

		levelTwoOptions.add(getResources().getString(R.string.vod_sub_category1));
		levelTwoOptions.add(getResources().getString(R.string.vod_sub_category2));
		levelTwoOptions.add(getResources().getString(R.string.vod_sub_category3));

		adapter = new MyGridAdapter(this, levelOneOptions);

		menu = (GridView) findViewById(R.id.menu);
		menu.setAdapter(adapter);
		menu.setOnItemClickListener(menuClickListener);
		menu.setOnKeyListener(menuKeyListener);
		menu.setVisibility(View.VISIBLE);
		menu.requestFocus();

		String action = getIntent().getAction();

		mHandler = new Handler();
		
		m_nSelectNum = 0;
		menu.setSelection(0);
		if (action != null) {
			if (action.equalsIgnoreCase(PLAY_LIVE_TV)) {
				// do nothing
				m_nSelectNum = 0;
				menu.setSelection(0);
			} 
			else if (action.equalsIgnoreCase(PLAY_RADIO_TV)) {
				// do nothing
				m_nSelectNum = 2;
				menu.setSelection(2);
			}else if (action.equalsIgnoreCase(PLAY_VOD_1)) {
				// show vod menu, set selection to first category
				showVodMenu();
				m_nSelectNum = 0;
				menu.setSelection(0);				
				menu.requestFocusFromTouch();
			} else if (action.equalsIgnoreCase(PLAY_VOD_2)) {
				// show vod menu, set selection to second category
				showVodMenu();
				m_nSelectNum = 1;
				menu.requestFocusFromTouch();
				menu.setSelection(1);
			} else if (action.equalsIgnoreCase(PLAY_VOD_3)) {
				// show vod menu, set selection to third category
				showVodMenu();
				m_nSelectNum = 2;
				menu.requestFocusFromTouch();
				menu.setSelection(2);
			} else if (action.equalsIgnoreCase(StandbyActivity.STANDBY_OVER_ACTION)) {
				reumeFromStandby();
			}
		}
	}

	private String getActionForCurrrentState(int position) {
		if (menuDepth == 0) {
			if (position == 0) {
				return PLAY_LIVE_TV;			}
			if( position == 2 )
				return PLAY_RADIO_TV;
		} else if (menuDepth == 1) {
			if (position == 0) {
				return PLAY_VOD_1;
			} else if (position == 1) {
				return PLAY_VOD_2;
			}
			else if (position == 2) {
				return PLAY_VOD_3;
			}
		}
		return null;
	}

	private void simulateSleepMode() {
		int position = menu.getSelectedItemPosition();
		String action = getActionForCurrrentState(position);
		Intent i = new Intent(this, StandbyActivity.class);
		i.putExtra(StandbyActivity.STANDBY_CALLER_KEY, StandbyActivity.CALLER_ACTIVITY_HOOME);
		i.setAction(action);
		startActivity(i);
	}

	public void reumeFromStandby() {
		menu.requestFocusFromTouch();
	}

	@Override
	public void onBackPressed() {
		StandbyActivity.setKeyEventTime();
		showExitMenu();
	}

	private void setupStandbyChecker() {
		if (checkerStarted)
			return;
		standbyCheckerTask = scheduler.scheduleAtFixedRate(new Runnable() {
			public void run() {
				long timeDiff = System.currentTimeMillis() - StandbyActivity.lastKeyEventAt.get();
				if (timeDiff > StandbyActivity.STANDBY_INTERVAL && Helper.isConnected(parent) ) {
					if (!StandbyActivity.SHOWING) {
						Log.i(TAG, "Simulating sleep mode, timeDiff : " + timeDiff + " , currentTime : "
								+ System.currentTimeMillis() + " , last key event : " + StandbyActivity.lastKeyEventAt
								+ " , stand by interval : " + StandbyActivity.STANDBY_INTERVAL);
						simulateSleepMode();
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

	private void showVodMenu() {
		adapter = (MyGridAdapter) menu.getAdapter();
		adapter.setData(levelTwoOptions);
		menu.invalidateViews();
		menuDepth = 1;
	}

	private void showFirstMenu() {
		adapter = (MyGridAdapter) menu.getAdapter();
		adapter.setData(levelOneOptions);
		menu.invalidateViews();
		menuDepth = 0;
	}

	private void startPlayerActivity(String action) {
		Intent i = new Intent(this, PlayerActivity.class);
		i.setAction(action);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(i);
		finish();
	}

	private void showExitMenu() {
		shutdownDialog = new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.exit_menu))
				.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.e(TAG, "Shutdown application !!!");
						finish();
						android.os.Process.killProcess(android.os.Process.myPid());
					}
				}).setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).create();
		final long shownAt = System.currentTimeMillis();
		// shutdownDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
		//
		// @Override
		// public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent
		// event) {
		// switch (keyCode) {
		// case KeyEvent.KEYCODE_BACK:
		// // wait at least one second to process the key event
		// if ((System.currentTimeMillis() - shownAt) > 300) {
		// dialog.dismiss();
		// finish();
		// android.os.Process.killProcess(android.os.Process.myPid());
		// }
		// return true;
		//
		// default:
		// Log.i(TAG, "Unhandled key event for shutdown dialog !!!");
		// }
		// return false;
		// }
		// });
		shutdownDialog.show();
	}

	class MyGridAdapter extends BaseAdapter {

		private Context context;
		private ArrayList<String> values;

		public MyGridAdapter(Context context, ArrayList<String> values) {
			this.context = context;
			this.values = values;
		}

		public void setData(ArrayList<String> newData) {
			values = newData;
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
			String name = values.get(position);
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View elementView = inflater.inflate(R.layout.menu_element_layout, parent, false);
			TextView itemName = (TextView) elementView.findViewById(R.id.menu_item_name);
			ImageView itemLogo = (ImageView) elementView.findViewById(R.id.menu_logo);
			itemName.setText(name);
			
			if( menuDepth == 0 )
			{
				if (position == 0) 
					itemLogo.setImageResource(R.drawable.tv);
				
				if( position == 1)
					itemLogo.setImageResource(R.drawable.film);
				
				if( position == 2)
					itemLogo.setImageResource(R.drawable.radio);	
			}
			else
			{
				itemLogo.setImageResource(R.drawable.film);				
			}
			
			if( position == m_nSelectNum  )
				elementView.findViewById(R.id.lay_menu_item).setBackgroundResource(R.drawable.menu_selected);
			else
				elementView.findViewById(R.id.lay_menu_item).setBackgroundResource(R.drawable.menu_normal);
			
			return elementView;
		}

	}

}
