package de.m3y3r.offlinewiki.frontend;

import androidx.room.Room;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.widget.TextView;

import java.io.File;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.R;
import de.m3y3r.offlinewiki.WikiPage;
import de.m3y3r.offlinewiki.pagestore.Store;
import de.m3y3r.offlinewiki.pagestore.bzip2.BZip2Store;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.room.RoomBlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexAccess;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.room.RoomIndexAccess;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.BufferInputStream;
import de.m3y3r.offlinewiki.utility.SplitFile;
import de.m3y3r.offlinewiki.utility.SplitFileInputStream;

public class ScrollingActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scrolling);

		Intent intent = getIntent();
		TitleEntity titleEntity = (TitleEntity) intent.getSerializableExtra("titleEntity");

		// get db
		AppDatabase titleDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "title-database").build();

		String xmlDumpUrlString = PreferenceManager.getDefaultSharedPreferences(this).getString("xmlDumpUrl", null);

		AsyncTask asyncTask = new AsyncTask() {
			@Override
			protected Object doInBackground(Object[] params) {
				String xmlDumpUrlString = (String) params[0];
				AppDatabase db = (AppDatabase) params[1];
				TitleEntity titleEntity = (TitleEntity) params[2];

				// get entry from db
				XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
				SplitFile splitFile = new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity. getBaseName());
				IndexAccess indexAccess = new RoomIndexAccess(db, xmlDumpEntity.getId());
				BlockController blockController = new RoomBlockController(db, xmlDumpEntity.getId());
				Store<WikiPage, String> pageStore = new BZip2Store(indexAccess, blockController, splitFile);
				WikiPage wikiPage = pageStore.retrieveByIndexKey(titleEntity.getTitle());
				return wikiPage;
			}

			@Override
			protected void onPostExecute(Object o) {
				WikiPage page = (WikiPage) o;
				TextView textView = findViewById(R.id.text_view);
				textView.setText(page.getText());
				setTitle(page.getTitle());
			}
		};

		asyncTask.execute(xmlDumpUrlString, titleDatabase, titleEntity);
		}


	}
