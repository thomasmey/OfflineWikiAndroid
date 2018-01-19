package de.m3y3r.offlinewiki.frontend;

import android.app.Activity;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;

import java.util.List;

import de.m3y3r.offlinewiki.R;
import de.m3y3r.offlinewiki.pagestore.bzip2.Indexer;
import de.m3y3r.offlinewiki.pagestore.room.TitleDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.Downloader;

public class SearchActivity extends Activity {

	private TitleDatabase titleDatabase;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_search, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_settings:
				Intent i = new Intent(this, SettingActivity.class);
				startActivity(i);
				return true;
		}
		return false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);

		PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.preferences, false);

		ListView listView = (ListView) findViewById(R.id.list_view);
		AdapterView.OnItemClickListener listener = new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				TitleEntity titleEntity = (TitleEntity) adapterView.getItemAtPosition(i);
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("wikipage://" + titleEntity.getTitle()), getApplicationContext(), ScrollingActivity.class);
				intent.putExtra("titleEntity", titleEntity);
				startActivity(intent);
			}
		};
		listView.setOnItemClickListener(listener);

		// get db
		titleDatabase = Room.databaseBuilder(getApplicationContext(), TitleDatabase.class, "title-database").build();
		// check if we have an xmldump file
		String xmlDumpUrl = PreferenceManager.getDefaultSharedPreferences(this).getString("xmlDumpUrl", null);

		Runnable xmlStartupTask = new XmlDumpStartupTask(xmlDumpUrl, titleDatabase, getApplicationContext());
		new Thread(xmlStartupTask).start();

		SearchView searchView = (SearchView) findViewById(R.id.search);
		SearchView.OnQueryTextListener qtl = new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				AsyncTask task = new AsyncTask<Object, Object, List<TitleEntity>>() {
					@Override
					protected List<TitleEntity> doInBackground(Object... params) {
						System.out.println("params=" +params);
						return titleDatabase.getDao().getTitleEntityByIndexKeyAscending(50, params[0].toString());
					}

					@Override
					protected void onPostExecute(List<TitleEntity> titles) {
						ArrayAdapter adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, titles);

						ListView listView = (ListView) findViewById(R.id.list_view);
						listView.setAdapter(adapter);
					}
				};
				task.execute(query);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String query) { return false; }
		};

		searchView.setOnQueryTextListener(qtl);
	}
}

class XmlDumpStartupTask implements Runnable {

	private final String xmlDumpUrlString;
	private final TitleDatabase db;
	private final Context ctx;

	public XmlDumpStartupTask(String xmlDumpUrlString, TitleDatabase db, Context ctx) {
		this.xmlDumpUrlString = xmlDumpUrlString;
		this.db = db;
		this.ctx = ctx;
	}

	@Override
	public void run() {

		if(xmlDumpUrlString == null) {
			throw new IllegalArgumentException();
		}

		// get entry from db
		XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
		try {

			Downloader downloader = new Downloader(ctx, db, xmlDumpEntity, xmlDumpUrlString);
			downloader.run();

			xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			Indexer indexer = new Indexer(db, xmlDumpEntity);
			indexer.run();

		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}
}