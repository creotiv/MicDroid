/* InstrumentalLibrary.java

   Copyright (c) 2010 Ethan Chen

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License along
   with this program; if not, write to the Free Software Foundation, Inc.,
   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.intervigil.micdroid;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import com.google.ads.AdView;
import com.intervigil.micdroid.helper.AdHelper;
import com.intervigil.micdroid.helper.ApplicationHelper;
import com.intervigil.micdroid.helper.PreferenceHelper;
import com.intervigil.micdroid.model.Instrumental;
import com.intervigil.micdroid.model.Recording;
import com.intervigil.micdroid.wave.WaveReader;


public class InstrumentalLibrary extends Activity {

    private static final String STATE_LOAD_IN_PROGRESS = "load_instrumentals_in_progress";

    private Boolean showAds;
    private AdView ad;
    private ListView library;
    private InstrumentalAdapter libraryAdapter;
    private ArrayList<Instrumental> instrumentals;
    private LoadInstrumentalsTask loadInstrumentalsTask;

    private ProgressDialog loadInstrumentalSpinner;

    /**
     * Called when the activity is starting. This is where most initialization
     * should go: calling setContentView(int) to inflate the activity's UI, etc.
     * 
     * @param savedInstanceState
     *            Activity's saved state, if any.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recording_library);

        showAds = PreferenceHelper.getShowAds(InstrumentalLibrary.this);

        ad = (AdView) findViewById(R.id.instrumental_ad);
        AdHelper.GenerateAd(ad, showAds);

        library = (ListView) findViewById(R.id.recording_library_list);
        library.setOnItemClickListener(libraryClickListener);
        library.setOnCreateContextMenuListener(instrumentalItemListener);

        Object savedInstrumentals = getLastNonConfigurationInstance();
        if (savedInstrumentals == null) {
            instrumentals = new ArrayList<Instrumental>();
            this.libraryAdapter = new InstrumentalAdapter(this,
                    R.layout.instrumental_library_row, instrumentals);
            library.setAdapter(libraryAdapter);
            loadInstrumentalsTask = (LoadInstrumentalsTask) new LoadInstrumentalsTask()
                    .execute((Void) null);
        } else {
            instrumentals = (ArrayList<Instrumental>) savedInstrumentals;
            this.libraryAdapter = new InstrumentalAdapter(this,
                    R.layout.instrumental_library_row, instrumentals);
            library.setAdapter(libraryAdapter);
            this.libraryAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onStart() {
        Log.i(getPackageName(), "onStart()");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.i(getPackageName(), "onResume()");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(getPackageName(), "onPause()");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(getPackageName(), "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(getPackageName(), "onDestroy()");
        super.onDestroy();

        onCancelLoadInstrumentals();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.i(getPackageName(), "onSaveInstanceState()");
        super.onSaveInstanceState(outState);

        saveLoadInstrumentalsTask(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.i(getPackageName(), "onRestoreInstanceState()");
        super.onRestoreInstanceState(savedInstanceState);

        restoreLoadInstrumentalsTask(savedInstanceState);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        final ArrayList<Instrumental> instrumentalList = instrumentals;
        return instrumentalList;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();
        Instrumental track = (Instrumental) libraryAdapter
                .getItem(info.position);

        switch (item.getItemId()) {
        case R.string.instrumental_options_set:
            PreferenceHelper.setInstrumentalTrack(InstrumentalLibrary.this,
                    track.getName());
            Toast
                    .makeText(InstrumentalLibrary.this,
                            R.string.instrumental_options_track_set,
                            Toast.LENGTH_SHORT).show();
            break;
        case R.string.instrumental_options_unset:
            String selectedTrack = PreferenceHelper
                    .getInstrumentalTrack(InstrumentalLibrary.this);
            if (selectedTrack.equals(track.getName())) {
                PreferenceHelper.setInstrumentalTrack(InstrumentalLibrary.this,
                        Constants.EMPTY_STRING);
                Toast.makeText(InstrumentalLibrary.this,
                        R.string.instrumental_options_track_unset,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(InstrumentalLibrary.this,
                        R.string.instrumental_options_track_unset_error,
                        Toast.LENGTH_SHORT).show();
            }
            break;
        case R.string.instrumental_options_remove:
            if (track.asFile().delete()) {
                Toast.makeText(InstrumentalLibrary.this,
                        R.string.instrumental_options_track_deleted,
                        Toast.LENGTH_SHORT).show();
            }
            break;
        default:
            break;
        }
        // force a reload of all instrumentals
        loadInstrumentalsTask = (LoadInstrumentalsTask) new LoadInstrumentalsTask()
                .execute((Void) null);
        return true;
    }

    private OnItemClickListener libraryClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            view.showContextMenu();
        }
    };

    private OnCreateContextMenuListener instrumentalItemListener = new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(R.string.instrumental_options_title);
            menu.add(Menu.NONE, R.string.instrumental_options_set, Menu.NONE,
                    R.string.instrumental_options_set);
            menu.add(Menu.NONE, R.string.instrumental_options_unset, Menu.NONE,
                    R.string.instrumental_options_unset);
            menu.add(Menu.NONE, R.string.instrumental_options_remove,
                    Menu.NONE, R.string.instrumental_options_remove);
        }
    };

    private class InstrumentalAdapter extends ArrayAdapter<Instrumental> {
        private String selectedTrack;

        public InstrumentalAdapter(Context context, int textViewResourceId,
                List<Instrumental> objects) {
            super(context, textViewResourceId, objects);
            selectedTrack = PreferenceHelper
                    .getInstrumentalTrack(InstrumentalLibrary.this);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = vi.inflate(R.layout.instrumental_library_row, parent,
                        false);
            }

            Instrumental track = this.getItem(position);
            if (track != null) {
                Drawable rowIcon = selectedTrack.equals(track.getName()) ? getResources()
                        .getDrawable(R.drawable.android_music)
                        : getResources().getDrawable(R.drawable.android_music);

                ((ImageView) view.findViewById(R.id.instrumental_row_icon))
                        .setImageDrawable(rowIcon);
                ((TextView) view.findViewById(R.id.instrumental_row_first_line))
                        .setText("Name: " + track.getName());
                ((TextView) view
                        .findViewById(R.id.instrumental_row_second_line))
                        .setText("Length: " + track.getLength());
            }

            return view;
        }
    }

    private void onCancelLoadInstrumentals() {
        if (loadInstrumentalsTask != null
                && loadInstrumentalsTask.getStatus() == AsyncTask.Status.RUNNING) {
            loadInstrumentalsTask.cancel(true);
            loadInstrumentalsTask = null;
        }
    }

    private void saveLoadInstrumentalsTask(Bundle outState) {
        final LoadInstrumentalsTask task = loadInstrumentalsTask;
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(true);
            outState.putBoolean(STATE_LOAD_IN_PROGRESS, true);
        }
    }

    private void restoreLoadInstrumentalsTask(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean(STATE_LOAD_IN_PROGRESS)) {
            loadInstrumentalsTask = (LoadInstrumentalsTask) new LoadInstrumentalsTask()
                    .execute((Void) null);
        }
    }

    private class LoadInstrumentalsTask extends
            AsyncTask<Void, Instrumental, Void> {
        // Async load all the instrumentals already in the directory

        @Override
        protected void onPreExecute() {
            libraryAdapter.clear();
            loadInstrumentalSpinner = new ProgressDialog(
                    InstrumentalLibrary.this);
            loadInstrumentalSpinner.setMessage("Loading instrumentals");
            loadInstrumentalSpinner.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            File instrumentalDir = new File(ApplicationHelper
                    .getInstrumentalDirectory());
            File[] waveFiles = instrumentalDir.listFiles();

            if (waveFiles != null) {
                for (int i = 0; i < waveFiles.length; i++) {
                    if (waveFiles[i].isFile()) {
                        WaveReader reader = new WaveReader(waveFiles[i]);

                        try {
                            reader.openWave();
                            Instrumental r = new Instrumental(instrumentalDir
                                    .getAbsolutePath(), waveFiles[i].getName(),
                                    reader.getLength(), reader.getDataSize()
                                            + Recording.WAVE_HEADER_SIZE);
                            reader.closeWaveFile();
                            reader = null;

                            publishProgress(r);

                        } catch (IOException e) {
                            // yes I know it sucks that we do control flow with
                            // an exception here, fix it later
                            Log
                                    .i(
                                            "InstrumentalLibrary",
                                            String
                                                    .format(
                                                            "Non-wave file %s found in instrumental directory!",
                                                            waveFiles[i]
                                                                    .getName()));
                        }
                    }
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Instrumental... values) {
            Instrumental r = values[0];
            if (r != null) {
                libraryAdapter.add(r);
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            loadInstrumentalSpinner.dismiss();
        }
    }
}
