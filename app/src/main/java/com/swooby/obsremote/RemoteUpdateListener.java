package com.swooby.obsremote;

import com.swooby.obsremote.messages.util.Source;

import java.util.ArrayList;

public interface RemoteUpdateListener
{
    void onConnectionAuthenticated();

    void onConnectionClosed(int code, String reason);

    void onStreamStarting(boolean previewOnly);

    void onStreamStopping();

    void onFailedAuthentication(String message);

    void onNeedsAuthentication();

    void onStreamStatusUpdate(int totalStreamTime, int fps,
                              float strain, int numDroppedFrames, int numTotalFrames, int bps);

    void onSceneSwitch(String sceneName);

    void onScenesChanged();

    void onSourceChanged(String sourceName, Source source);

    void onSourceOrderChanged(ArrayList<String> sources);

    void onRepopulateSources(ArrayList<Source> sources);

    void onVolumeChanged(String channel, boolean finalValue, float volume, boolean muted);

    void onVersionMismatch(float version);
}
