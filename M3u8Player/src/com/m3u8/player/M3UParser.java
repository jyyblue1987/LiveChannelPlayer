package com.m3u8.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

public class M3UParser extends Thread {

	private static M3UParser parser;

	private static long SLEEP_PERIOD = 1000 * 60 * 60; // one hour
//	private static long SLEEP_PERIOD = 1000 * 30 ; // one hour

	Lock lock = new ReentrantLock();

	private static String TAG = "M3UParser";

	public static M3UParser getM3UParser(Context c) {
		if (parser == null) {
			parser = new M3UParser(c);
		}
		return parser;
	}

	ArrayList<M3UElement> allStreams;
	Context c;

	Handler registeredHandler;
	AtomicBoolean listParsingInProgress = new AtomicBoolean(false);

	private M3UParser(Context c) {
		this.c = c;
		start();
	}

	public void internetConnectionAvailable() {
		if (!listParsingInProgress.get() && allStreams == null) {
			this.interrupt();
		}
	}

	public void registerHandler(Handler h) {
		registeredHandler = h;
	}

	public void run() {
		while (true) {
			fetchM3UList();
			try {
				sleep(SLEEP_PERIOD);
			} catch (InterruptedException e) {
				Log.i(TAG, "Interuupt signal received, internet connection is probbably available");
				e.printStackTrace();
			}
		}
	}

	private void fetchM3UList() {
		try {
			listParsingInProgress.set(true);
			String url = Helper.getM3UListUrl(c);
			Log.i(TAG, "Fetching M3U list from : " + url);
			HttpResponse response = Helper.makeRequest(url);
			if (response == null) {
				sendMessageToHandler(PlayerActivity.M3U_PARSING_ERROR);
				return;
			}
			HttpEntity entetty = response.getEntity();
			parse(entetty.getContent());
			Log.i(TAG, "Sending display list message to UI thread !!!");
			if (allStreams != null)
				Log.i(TAG, "List parsed, list size : " + allStreams.size());
			sendMessageToHandler(PlayerActivity.DISPLAY_CHANNEL_LIST);
		} catch (IOException e) {
			sendMessageToHandler(PlayerActivity.M3U_PARSING_ERROR);
			e.printStackTrace();
		} catch (Exception e) {
			sendMessageToHandler(PlayerActivity.M3U_PARSING_ERROR);
			e.printStackTrace();
		} finally {
			listParsingInProgress.set(false);
		}
	}

	private void sendMessageToHandler(int message_id) {
		if (registeredHandler != null) {
			registeredHandler.obtainMessage(message_id).sendToTarget();
		}

	}

	private void parse(InputStream in) throws IOException {
		ArrayList<String> names = new ArrayList<>();
		ArrayList<String> urls = new ArrayList<>();

		/*
		 * String replyFromServer = EntityUtils.toString(entetty); String[]
		 * lines = replyFromServer.split("\n");
		 */

		// InputStream in = c.getAssets().open("tv_channels_test.m3u");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

		String line;
		// for (int i = 0; i < lines.length; i++) {
		while ((line = reader.readLine()) != null) {
			// line = lines[i];
			if (line.startsWith("#EXTINF")) {
				// it is a channel name
				String name = line.split(",")[1];
				names.add(name);
			}
			if (line.startsWith("http://")) {
				// it is a url
				urls.add(line);
			}
		}
		ArrayList<M3UElement> result = new ArrayList<M3UElement>();
		for (int i = 0; i < urls.size(); i++) {
			result.add(new M3UElement(names.get(i), urls.get(i)));
		}
		lock.lock();
		allStreams = result;
		lock.unlock();
	}

	public ArrayList<M3UElement> getElementForCategory(int category) {
		if (allStreams == null)
			return null;
		lock.lock();
		try {
			ArrayList<M3UElement> result = new ArrayList<M3UElement>();
			for (M3UElement el : allStreams) {
				switch (category) {
				case PlayerActivity.LIVE_TV_CATEGORY:
					if (!el.getName().startsWith("MOV") && !el.getName().startsWith("Radio")) {
						addElementToList(result, el);
					}
					continue;
				case PlayerActivity.RADIO_TV_CATEGORY:
					if (el.getName().startsWith("Radio")) {
						addElementToList(result, el);
					}
					continue;
				case PlayerActivity.VOD_TV_CATEGORY1:
					if (el.getName().startsWith("MOV Gjermanisht")) {
						addElementToList(result, el);
					}
					continue;

				case PlayerActivity.VOD_TV_CATEGORY2:
					if (el.getName().startsWith("MOV Shqip")) {
						addElementToList(result, el);
					}
					continue;
				case PlayerActivity.VOD_TV_CATEGORY3:
					if (el.getName().startsWith("MOV Kids")) {
						addElementToList(result, el);
					}
					continue;
				}
			}
			return result;
		} finally {
			lock.unlock();
		}
	}

	private void addElementToList(ArrayList<M3UElement> list, M3UElement element) {
		M3UElement newEl = element.clone();
		newEl.strip();
		list.add(newEl);
	}

	public class M3UElement {

		String name;
		String url;

		public M3UElement(String name, String url) {
			this.name = name;
			this.url = url;
		}

		String getName() {
			return name;
		}

		String getUrl() {
			return url;
		}

		public M3UElement clone() {
			return new M3UElement(name, url);
		}

		public void strip() {
			name = name.replace("MOV Gjermanisht", "");
			name = name.replace("MOV Shqip", "");
			name = name.trim();
		}
	}

}
