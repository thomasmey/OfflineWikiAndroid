package de.m3y3r.offlinewiki.frontend;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.arch.persistence.room.Room;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;

import java.util.List;

import de.m3y3r.offlinewiki.R;
import de.m3y3r.offlinewiki.pagestore.room.TitleDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;
import de.m3y3r.offlinewiki.service.DownloadJob;
import de.m3y3r.offlinewiki.service.IndexerJob;

public class SearchActivity extends Activity {

	private volatile static android.os.Handler handler;
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

		ProgressBar progressBar = findViewById(R.id.progressBar);
		progressBar.setVisibility(View.INVISIBLE);

		JobScheduler jobScheduler = (JobScheduler) getApplicationContext().getSystemService(JOB_SCHEDULER_SERVICE);
		JobInfo jobInfo = new JobInfo.Builder(1, new ComponentName(getApplicationContext(), DownloadJob.class))
				.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
				//.setRequiresCharging(true)
				.build();
		jobScheduler.schedule(jobInfo);

		handler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				if(msg.what != 1) // 1 == ProgressBar
					return;

				if (msg.arg2 == 1) {
					progressBar.setVisibility(View.VISIBLE);
				} else if (msg.arg2 == 2) {
					progressBar.setVisibility(View.INVISIBLE);
				}

				progressBar.setProgress(msg.arg1);
			}
		};

		// get db
		titleDatabase = Room.databaseBuilder(getApplicationContext(), TitleDatabase.class, "title-database").build();

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

	@Override
	protected void onDestroy() {
		super.onDestroy();

		TitleDatabase db  = titleDatabase;
		titleDatabase = null;
		if(db != null)
			db.close();

		handler = null;
	}

	public static void updateProgressBar(int progess, int visible) {
		if(handler != null) {
			Handler h = handler;
			Message msg = h.obtainMessage(1, progess, visible);
			msg.sendToTarget();
		}
	}
}

