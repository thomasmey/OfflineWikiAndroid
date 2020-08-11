package de.m3y3r.offlinewiki.frontend;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.ItemKeyedDataSource;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.preference.PreferenceManager;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.m3y3r.offlinewiki.R;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.service.BlockFinderJob;
import de.m3y3r.offlinewiki.service.DownloadJob;
import de.m3y3r.offlinewiki.service.IndexerJob;

public class SearchActivity extends Activity {

	private volatile static android.os.Handler handler;
	private AppDatabase titleDatabase;
	private ExecutorService threadPool;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_search, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() ==  R.id.action_settings) {
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

		RecyclerView recyclerView = (RecyclerView) findViewById(R.id.list_view);
		recyclerView.setHasFixedSize(true);
		RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
		recyclerView.setLayoutManager(layoutManager);
		RecyclerView.Adapter adapter = new PagedTitleEntityAdapter();
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));

		ProgressBar progressBar = findViewById(R.id.progressBar);
		progressBar.setVisibility(View.INVISIBLE);

		this.threadPool = Executors.newCachedThreadPool(r -> new Thread(r,"background-"));

		JobScheduler jobScheduler = (JobScheduler) getApplicationContext().getSystemService(JOB_SCHEDULER_SERVICE);
		// start downloader job
		{
			JobInfo jobInfo = new JobInfo.Builder(1, new ComponentName(getApplicationContext(), DownloadJob.class))
					.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
					.build();
			jobScheduler.schedule(jobInfo);
		}

		// start blockfinder job
		{
			JobInfo jobInfo = new JobInfo.Builder(2, new ComponentName(getApplicationContext(), BlockFinderJob.class))
					.setRequiresCharging(true)
					.build();
			jobScheduler.schedule(jobInfo);
		}

		// start indexer job
		{
			JobInfo jobInfo = new JobInfo.Builder(3, new ComponentName(getApplicationContext(), IndexerJob.class))
					.setRequiresCharging(true)
					.build();
			jobScheduler.schedule(jobInfo);
		}

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
		titleDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "title-database").build();

		SearchView searchView = (SearchView) findViewById(R.id.search);
		SearchView.OnQueryTextListener qtl = new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				AsyncTask task = new AsyncTask<Object, Object, PagedList<TitleEntity>>() {
					@Override
					protected PagedList<TitleEntity> doInBackground(Object... params) {
						if(params == null || params.length < 1)
							return null;

						String xmlDumpUrl = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("xmlDumpUrl", null);
						XmlDumpEntity xmlDumpEntity = titleDatabase.getDao().getXmlDumpEntityByUrl(xmlDumpUrl);
						String query = (String) params[0];
						DataSource ds = new TitleEntityDataSource(titleDatabase, xmlDumpEntity.getId(), query);
						return new PagedList.Builder(ds, 25)
								.setFetchExecutor(threadPool)
								.setNotifyExecutor(MainThreadExecutor.getInstance())
								.build();
					}

					@Override
					protected void onPostExecute(PagedList<TitleEntity> titles) {
						RecyclerView listView = (RecyclerView) findViewById(R.id.list_view);
						PagedTitleEntityAdapter adapt = (PagedTitleEntityAdapter) listView.getAdapter();
						adapt.submitList(titles);
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

		if(titleDatabase != null) {
			titleDatabase.close();
			titleDatabase = null;
		}

		if(threadPool != null) {
			threadPool.shutdown();
			threadPool = null;
		}

		handler = null;
	}

	public static void updateProgressBar(int progress, int visible) {
		if(handler != null) {
			Handler h = handler;
			Message msg = h.obtainMessage(1, progress, visible);
			msg.sendToTarget();
		}
	}
}

class TitleViewHolder extends RecyclerView.ViewHolder {
	private TitleEntity title;

	public TitleViewHolder(@NonNull View itemView) {
		super(itemView);
	}

	public TitleEntity getTitle() {
		return title;
	}
	public void setTitle(TitleEntity titleEntity) {
		title = titleEntity;
	}
}

class PagedTitleEntityAdapter extends PagedListAdapter<TitleEntity, TitleViewHolder> implements View.OnClickListener {

	public PagedTitleEntityAdapter() {
		super(DIFF_CALLBACK);
	}

	@NonNull
	@Override
	public TitleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		// create a new view
		TextView v = (TextView) LayoutInflater.from(parent.getContext())
			.inflate(android.R.layout.simple_list_item_1, parent, false);
		return new TitleViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull TitleViewHolder holder, int position) {
		TitleEntity title = getItem(position);
		TextView textView = (TextView) holder.itemView;
		if(title != null) {
			textView.setText(title.getTitle());
			textView.setOnClickListener(this);
		} else {
			textView.setText(null);
		}
	}

	@Override
	public void onClick(View view) {
		TextView tv = (TextView) view;
		String title = tv.getText().toString();
//		TitleEntity titleEntity = holder.getTitle();
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("wikipage://" + title), view.getContext(), ScrollingActivity.class);
		intent.putExtra("title", title);
		view.getContext().startActivity(intent);
	}

     public static final DiffUtil.ItemCallback<TitleEntity> DIFF_CALLBACK =
             new DiffUtil.ItemCallback<TitleEntity>() {
         @Override
         public boolean areItemsTheSame(
                 @NonNull TitleEntity oldUser, @NonNull TitleEntity newUser) {
             // User properties may have changed if reloaded from the DB, but ID is fixed
             return oldUser.getTitle().equals(newUser.getTitle());
         }
         @Override
         public boolean areContentsTheSame(
                 @NonNull TitleEntity oldUser, @NonNull TitleEntity newUser) {
             // NOTE: if you use equals, your object must properly override Object#equals()
             // Incorrectly returning false here will result in too many animations.
             return oldUser.getTitle().equals(newUser.getTitle());
         }
     };
}

class TitleEntityDataSource extends ItemKeyedDataSource<String, TitleEntity> {

	private final AppDatabase db;
	private final int xmlDumpId;
	private final String title;

	public TitleEntityDataSource(AppDatabase db, int xmlDumpId, String title) {
		this.db = db;
		this.xmlDumpId = xmlDumpId;
		this.title = title;
	}

	@Override
	public void loadInitial(@NonNull LoadInitialParams<String> params, @NonNull LoadInitialCallback<TitleEntity> callback) {
		List<TitleEntity> titles;
		if(title.length() > 1 && title.charAt(0) == ' ') {
			String t = title.substring(1);
			titles = db.getDao().getTitleEntityByIndexKeyLikeInitial(xmlDumpId, params.requestedLoadSize, t);
		} else {
			titles = db.getDao().getTitleEntityByIndexKeyInitial(xmlDumpId, params.requestedLoadSize, title);
		}
		callback.onResult(titles);
	}

	@Override
	public void loadAfter(@NonNull LoadParams<String> params, @NonNull LoadCallback<TitleEntity> callback) {
		List<TitleEntity> titles;
		if(title.length() > 1 && title.charAt(0) == ' ') {
			String t = title.substring(1);
			titles = db.getDao().getTitleEntityByIndexKeyLikeAfter(xmlDumpId, params.requestedLoadSize, t, params.key);
		} else {
			titles = db.getDao().getTitleEntityByIndexKeyAfter(xmlDumpId, params.requestedLoadSize, params.key);
		}
		callback.onResult(titles);
	}

	@Override
	public void loadBefore(@NonNull LoadParams<String> params, @NonNull LoadCallback<TitleEntity> callback) {
		List<TitleEntity> titles;
		if(title.length() > 1 && title.charAt(0) == ' ') {
			String t = title.substring(1);
			titles = db.getDao().getTitleEntityByIndexKeyLikeBefore(xmlDumpId, params.requestedLoadSize, t, params.key);
		} else {
			titles = db.getDao().getTitleEntityByIndexKeyBefore(xmlDumpId, params.requestedLoadSize, params.key);
		}
		callback.onResult(titles);
	}

	@NonNull
	@Override
	public String getKey(@NonNull TitleEntity item) {
		return item.getTitle();
	}
}

class MainThreadExecutor implements Executor {
    private final Handler handler = new Handler(Looper.getMainLooper());
	private final static MainThreadExecutor instance = new MainThreadExecutor();

	public static Executor getInstance() {
		return instance;
	}

	@Override
    public void execute(Runnable r) {
        handler.post(r);
    }
}