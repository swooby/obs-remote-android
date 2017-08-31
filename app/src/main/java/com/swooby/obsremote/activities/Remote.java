package com.swooby.obsremote.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.mobeta.android.dslv.DragSortListView;
import com.swooby.obsremote.OBSRemoteApplication;
import com.swooby.obsremote.R;
import com.swooby.obsremote.RemoteUpdateListener;
import com.swooby.obsremote.WebSocketService;
import com.swooby.obsremote.WebSocketService.LocalBinder;
import com.swooby.obsremote.messages.ResponseHandler;
import com.swooby.obsremote.messages.requests.GetSceneList;
import com.swooby.obsremote.messages.requests.GetStreamingStatus;
import com.swooby.obsremote.messages.requests.SetCurrentScene;
import com.swooby.obsremote.messages.requests.SetSourceOrder;
import com.swooby.obsremote.messages.requests.SetSourceRender;
import com.swooby.obsremote.messages.requests.StartStopStreaming;
import com.swooby.obsremote.messages.responses.GetSceneListResponse;
import com.swooby.obsremote.messages.responses.Response;
import com.swooby.obsremote.messages.responses.StreamStatusResponse;
import com.swooby.obsremote.messages.util.Scene;
import com.swooby.obsremote.messages.util.Source;

import java.util.ArrayList;
import java.util.Locale;

public class Remote
        extends FragmentActivity
        implements RemoteUpdateListener
{
    public WebSocketService service;

    private SceneAdapter     sceneAdapter;
    private ArrayList<Scene> scenes;

    private Scene currentScene;

    private SourceAdapter sourceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        //Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_remote);
        
        /* setup scene adapter */
        sceneAdapter = new SceneAdapter(this, new ArrayList<Scene>());
        ListView sceneView = findViewById(R.id.ScenesListView);
        sceneView.setAdapter(sceneAdapter);

        Resources res = getResources();

        ColorDrawable darkgray = new ColorDrawable(res.getColor(R.color.darkgray));
        sceneView.setDivider(darkgray);
        sceneView.setDividerHeight(8);
        
        /* setup source adapter */
        sourceAdapter = new SourceAdapter(this, new ArrayList<Source>());
        DragSortListView sourcesView = findViewById(R.id.SourcesListView);
        sourcesView.setAdapter(sourceAdapter);
        sourcesView.setOnItemClickListener(new SourceItemClickListener(sourceAdapter));

        ColorDrawable lightgray = new ColorDrawable(res.getColor(R.color.buttonbackground));
        sourcesView.setDivider(lightgray);
        sourcesView.setDividerHeight(8);
    }

    protected void onStart()
    {
        super.onStart();

        //hide UI button until after setup
        Button toggleStreamingButton = findViewById(R.id.startstopbutton);
        ListView scenesView = findViewById(R.id.ScenesListView);
        DragSortListView sourcesView = findViewById(R.id.SourcesListView);
        ImageButton volumeButton = findViewById(R.id.volumebutton);
        LinearLayout statsPanel = findViewById(R.id.statspanel);

        toggleStreamingButton.setVisibility(View.INVISIBLE);
        scenesView.setVisibility(View.INVISIBLE);
        sourcesView.setVisibility(View.INVISIBLE);
        volumeButton.setVisibility(View.INVISIBLE);
        statsPanel.setVisibility(View.GONE);

        /* bind the service */
        Intent intent = new Intent(this, WebSocketService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        service.removeUpdateListener(Remote.this);
        unbindService(mConnection);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder binder)
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder localBinder = (LocalBinder) binder;

            service = localBinder.getService();

            service.addUpdateListener(Remote.this);

            if (service.isConnected())
            {
                if (service.needsAuth() && !service.authenticated())
                {
                    AuthDialogFragment.startAuthentication(Remote.this, getApp(), service);
                }
                else
                {
                    initialSetup();
                }
            }
            else
            {
                service.connect();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            service.removeUpdateListener(Remote.this);
            service = null;

            finish();
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.remote, menu);
        return true;
    }

    public void initialSetup()
    {
        updateStreamStatus();

        updateScenes();

        ImageButton volumeButton = findViewById(R.id.volumebutton);
        volumeButton.setVisibility(View.VISIBLE);
    }

    private void updateStreamStatus()
    {
        /* Get stream status */
        service.sendRequest(new GetStreamingStatus(), new ResponseHandler()
        {

            @Override
            public void handleResponse(Response resp, String jsonMessage)
            {
                StreamStatusResponse ssResp = getApp().getGson().fromJson(jsonMessage, StreamStatusResponse.class);

                updateStreaming(ssResp.streaming, ssResp.previewOnly);
            }
        });
    }

    private void updateScenes()
    {
        /* Get scenes */
        service.sendRequest(new GetSceneList(), new ResponseHandler()
        {

            @Override
            public void handleResponse(Response resp, String jsonMessage)
            {
                if (resp.isOk())
                {
                    ListView scenesView = findViewById(R.id.ScenesListView);
                    DragSortListView sourcesView = findViewById(R.id.SourcesListView);

                    scenesView.setVisibility(View.VISIBLE);
                    sourcesView.setVisibility(View.VISIBLE);

                    GetSceneListResponse scenesResp = getApp().getGson()
                            .fromJson(jsonMessage, GetSceneListResponse.class);

                    scenes = scenesResp.scenes;

                    sceneAdapter.setScenes(scenes);

                    setScene(scenesResp.currentScene);
                }
            }
        });
    }

    private class SceneAdapter
            extends ArrayAdapter<Scene>
    {
        String currentScene = "";

        SceneAdapter(Context context, ArrayList<Scene> scenes)
        {
            super(context, R.layout.scene_item, R.id.scenename, scenes);
        }

        void setCurrentScene(String scene)
        {
            currentScene = scene;

            notifyDataSetChanged();
        }

        void setScenes(ArrayList<Scene> scenes)
        {
            clear();
            for (Scene scene : scenes)
            {
                add(scene);
            }
        }

        public View getView(int position, View convertView, ViewGroup parent)
        {
            View view = super.getView(position, convertView, parent);

            String sceneName = getItem(position).name;

            if (sceneName.equals(currentScene))
            {
                view.setBackgroundResource(R.drawable.sceneselected);
                view.setOnClickListener(null);
            }
            else
            {
                view.setBackgroundResource(R.drawable.sceneunselected);
                OnClickListener listener = new OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        String sceneName = ((TextView) v.findViewById(R.id.scenename)).getText().toString();

                        service.sendRequest(new SetCurrentScene(sceneName));
                    }
                };

                view.setOnClickListener(listener);
            }

            return view;
        }
    }

    private class SourceAdapter
            extends ArrayAdapter<Source>
            implements DragSortListView.DropListener
    {
        SourceAdapter(Context context, ArrayList<Source> sources)
        {
            super(context, R.layout.source_item, R.id.sourcename, sources);
        }

        void setSources(ArrayList<Source> sources, boolean forceRefresh)
        {
            boolean refreshNeeded = sources.size() != getCount() || forceRefresh;

            if (!refreshNeeded)
            {
                for (int i = 0; i < Math.min(getCount(), sources.size()); i++)
                {
                    Source ns = sources.get(i);
                    Source os = getItem(i);

                    if (!ns.equals(os))
                    {
                        refreshNeeded = true;
                        break;
                    }
                }
            }

            if (!refreshNeeded)
            {
                return;
            }

            clear();
            for (Source source : sources)
            {
                add(source);
            }
        }

        public View getView(int position, View convertView, ViewGroup parent)
        {
            View view = super.getView(position, convertView, parent);
            TextView text = view.findViewById(R.id.sourcename);

            if (getItem(position).render)
            {
                view.setBackgroundResource(R.drawable.sourceon);
                text.setTextColor(getResources().getColor(R.color.textgray));
            }
            else
            {
                view.setBackgroundResource(R.drawable.sourceoff);
                text.setTextColor(getResources().getColor(R.color.textgraydisabled));
            }

            //OnClickListener listener = new SourceOnClickListener(getItem(position));

            //view.findViewById(R.id.sourceitem).setOnClickListener(listener);

            return view;
        }

        @Override
        public void drop(int from, int to)
        {
            Source s = getItem(from);
            remove(s);
            insert(s, to);

            ArrayList<String> sources = new ArrayList<String>();
            for (int i = 0; i < getCount(); i++)
            {
                sources.add(getItem(i).name);
            }

            service.sendRequest(new SetSourceOrder(sources));
        }
    }

    private class SourceItemClickListener
            implements AdapterView.OnItemClickListener
    {
        SourceAdapter adapter;

        SourceItemClickListener(SourceAdapter adapter)
        {
            this.adapter = adapter;
        }

        @Override
        public void onItemClick(AdapterView<?> adapterView, View itemView,
                                int itemNumber, long id)
        {
            Source source = adapter.getItem(itemNumber);
            service.sendRequest(new SetSourceRender(source.name, !source.render));
        }
    }

    public void updateStreaming(boolean streaming, boolean previewOnly)
    {
        WebSocketService serv = service;

        serv.setStreaming(streaming);
        serv.previewOnly = previewOnly;

        Button toggleStreamingButton = findViewById(R.id.startstopbutton);
        LinearLayout statsPanel = findViewById(R.id.statspanel);

        toggleStreamingButton.setVisibility(View.VISIBLE);

        if (serv.getStreaming())
        {
            toggleStreamingButton.setText(R.string.stopstreaming);
            toggleStreamingButton.setBackgroundResource(R.drawable.button_streaming_selector);
            statsPanel.setVisibility(View.VISIBLE);
        }
        else
        {
            toggleStreamingButton.setText(R.string.startstreaming);
            toggleStreamingButton.setBackgroundResource(R.drawable.buttonselector);
            statsPanel.setVisibility(View.GONE);
        }
    }

    private void setScene(String sceneName)
    {
        sceneAdapter.setCurrentScene(sceneName);

        for (Scene scene : scenes)
        {
            if (scene.name.equals(sceneName))
            {
                sourceAdapter.setSources(scene.sources, true);
                currentScene = scene;
            }
        }
    }

    public void startStopStreaming(View view)
    {
        service.sendRequest(new StartStopStreaming());
    }

    public void adjustVolume(View view)
    {
        // startup volume dialog
        VolumeDialogFragment.startDialog(this, service);
    }

    public OBSRemoteApplication getApp()
    {
        return (OBSRemoteApplication) getApplicationContext();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        /* Finish immediately on back press */
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            Splash.autoConnect = false;
            service.disconnect();
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onConnectionAuthenticated()
    {
        initialSetup();
    }

    @Override
    public void onConnectionClosed(int code, String reason)
    {
        finish();
    }

    @Override
    public void onStreamStarting(boolean previewOnly)
    {
        updateStreaming(true, false);
    }

    @Override
    public void onStreamStopping()
    {
        updateStreaming(false, false);
    }

    @Override
    public void onFailedAuthentication(String message)
    {
        AuthDialogFragment.startAuthentication(Remote.this, getApp(), service, message);
    }

    @Override
    public void onNeedsAuthentication()
    {
        AuthDialogFragment.startAuthentication(Remote.this, getApp(), service);
    }

    public static int strainToColor(float strain)
    {
        int green = 255;
        if (strain > 50.0)
        {
            green = (int) (((50.0 - (strain - 50.0)) / 50.0) * 255.0);
        }

        float red = strain / 50;
        if (red > 1.0)
        {
            red = 1.0f;
        }

        red = red * 255;

        return Color.rgb((int) red, green, 0);
    }

    public static String getTimeString(Locale locale, int timeInMillisec)
    {
        int sec = timeInMillisec / 1000;
        return String.format(locale, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }

    public static String getBitrateString(int bps)
    {
        return bps * 8 / 1000 + " kbps";
    }

    @Override
    public void onStreamStatusUpdate(int totalTimeStreaming, int fps,
                                     float strain, int numDroppedFrames, int numTotalFrames, int bps)
    {
        TextView droppedFrames = findViewById(R.id.droppedValue);
        TextView timeStreaming = findViewById(R.id.timeValue);
        TextView bitrate = findViewById(R.id.bitrateValue);
        TextView fpsLbl = findViewById(R.id.fpsValue);

        Locale locale = Locale.getDefault();

        fpsLbl.setText(String.format(locale, "%d", fps));

        timeStreaming.setText(getTimeString(locale, totalTimeStreaming));

        droppedFrames.setText(getDroppedFramesString(locale, numDroppedFrames, numTotalFrames));

        bitrate.setText(getBitrateString(bps));

        bitrate.setTextColor(strainToColor(strain));
    }

    public static String getDroppedFramesString(Locale locale,
                                                int numDroppedFrames,
                                                int numTotalFrames)
    {
        return String.format(locale, "%d (%.2f%%)", numDroppedFrames, numDroppedFrames / (float) numTotalFrames * 100);
    }

    @Override
    public void onSceneSwitch(String sceneName)
    {
        setScene(sceneName);
    }

    @Override
    public void onScenesChanged()
    {
        updateScenes();
    }

    @Override
    public void onSourceChanged(String sourceName, Source source)
    {
        /* find current scene */
        if (currentScene == null)
        {
            return;
        }

        for (Source s : currentScene.sources)
        {
            if (sourceName.equals(s.name))
            {
                s.conform(source);
                break;
            }
        }

        sourceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onSourceOrderChanged(ArrayList<String> sources)
    {
        /* find current scene */
        if (currentScene == null)
        {
            return;
        }

        ArrayList<Source> newSources = new ArrayList<>();

        for (int x = 0; x < sources.size(); x++)
        {
            for (Source oldSource : currentScene.sources)
            {
                if (oldSource.name.equals(sources.get(x)))
                {
                    newSources.add(oldSource);
                    break;
                }
            }
        }

        currentScene.sources = newSources;

        sourceAdapter.setSources(currentScene.sources, false);
    }

    @Override
    public void onRepopulateSources(ArrayList<Source> sources)
    {
        if (currentScene == null)
        {
            return;
        }

        currentScene.sources = sources;

        sourceAdapter.setSources(sources, true);
    }

    @Override
    public void onVolumeChanged(String channel, boolean finalValue,
                                float volume, boolean muted)
    {
        // do nothing
    }

    @Override
    public void onVersionMismatch(float version)
    {
        // do nothing
    }
}
