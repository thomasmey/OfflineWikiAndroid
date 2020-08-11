package de.m3y3r.offlinewiki.frontend;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;
import de.m3y3r.offlinewiki.R;

public class SettingsFragment extends PreferenceFragmentCompat {

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.preferences);
	}
}
