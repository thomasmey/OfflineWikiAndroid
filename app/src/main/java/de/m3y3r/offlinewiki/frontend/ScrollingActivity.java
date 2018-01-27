package de.m3y3r.offlinewiki.frontend;

import android.arch.persistence.room.Room;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.widget.TextView;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.File;
import java.io.IOException;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.PageRetriever;
import de.m3y3r.offlinewiki.R;
import de.m3y3r.offlinewiki.WikiPage;
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
				try (
						SplitFileInputStream fis = new SplitFileInputStream(splitFile, Config.SPLIT_SIZE);
						BufferInputStream in = new BufferInputStream(fis);
						BZip2CompressorInputStream bZip2In = new BZip2CompressorInputStream(in, false);) {
					bZip2In.read();

					long posInBits = titleEntity.getBlockPositionInBits();
					fis.seek(posInBits / 8); // position underlying file to the bzip2 block start
					in.clearBuffer(); // clear buffer content
					bZip2In.resetBlock((byte) (posInBits % 8)); // consume superfluous bits
					// skip to next page; set uncompressed byte position
					long nextPagePos = titleEntity.getPageUncompressedPosition() - titleEntity.getBlockUncompressedPosition();
					bZip2In.skip(nextPagePos);
					PageRetriever pr = new PageRetriever(bZip2In);
					WikiPage page = pr.getNext();
					return page;
				} catch (IOException e) {
					e.printStackTrace();
				}
				return  null;
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
