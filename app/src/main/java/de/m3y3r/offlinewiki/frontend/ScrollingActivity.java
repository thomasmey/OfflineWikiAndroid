package de.m3y3r.offlinewiki.frontend;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;

import android.app.Application;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.widget.TextView;

import java.io.File;

import de.m3y3r.offlinewiki.R;
import de.m3y3r.offlinewiki.WikiPage;
import de.m3y3r.offlinewiki.pagestore.Store;
import de.m3y3r.offlinewiki.pagestore.bzip2.BZip2Store;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.room.RoomBlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexAccess;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.room.RoomIndexAccess;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.SplitFile;

public class ScrollingActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scrolling);

		Intent intent = getIntent();
		String title = (String) intent.getSerializableExtra("title");
//		TitleEntity titleEntity = (TitleEntity) intent.getSerializableExtra("titleEntity");

		ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
			@NonNull
			@Override
			public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
				return (T) new WikiPageViewModel(ScrollingActivity.this.getApplication());
			}
		};
		WikiPageViewModel model = new ViewModelProvider(this, factory).get(WikiPageViewModel.class);
		model.getWikiPage().observe(this, page -> {
				TextView textView = findViewById(R.id.text_view);
				textView.setText(page.getText());
				setTitle(page.getTitle());
		});

		if(!title.equals(model.getTitle())) {
			model.setTitle(title);
		}
	}
}

class WikiPageViewModel extends AndroidViewModel {
	private MutableLiveData<WikiPage> wikiPage = new MutableLiveData<>();
	private String title;

	public WikiPageViewModel(@NonNull Application application) {
		super(application);
	}

	public MutableLiveData<WikiPage> getWikiPage() {
		return wikiPage;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
		loadPage();
	}

	private void loadPage() {
		String xmlDumpUrlString = PreferenceManager.getDefaultSharedPreferences(getApplication().getApplicationContext()).getString("xmlDumpUrl", null);
		AppDatabase db = Room.databaseBuilder(getApplication().getApplicationContext(), AppDatabase.class, "title-database").build();

		AsyncTask task = new AsyncTask<Object, Object, WikiPage> () {
			@Override
			protected WikiPage doInBackground(Object[] objects) {
				try {
					// get entry from db
					XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
					SplitFile splitFile = new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity. getBaseName());
					IndexAccess indexAccess = new RoomIndexAccess(db, xmlDumpEntity.getId());
					BlockController blockController = new RoomBlockController(db, xmlDumpEntity.getId());
					Store<WikiPage, String> pageStore = new BZip2Store(indexAccess, blockController, splitFile);
					WikiPage wikiPage = pageStore.retrieveByIndexKey(title);
					return wikiPage;
				} finally {
					db.close();
				}
			}

			@Override
			protected void onPostExecute(WikiPage wikiPage) {
				WikiPageViewModel.this.getWikiPage().setValue(wikiPage);
			}
		};
		task.execute();
	}
}