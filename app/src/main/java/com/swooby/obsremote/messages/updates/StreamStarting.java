package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.OBSRemoteService;
import com.google.gson.annotations.SerializedName;

public class StreamStarting extends Update
{
    @SerializedName("preview-only")
    public boolean previewOnly;

    @Override
    public void dispatchUpdate(OBSRemoteService serv)
    {
        serv.notifyOnStreamStarting(previewOnly);
    }
}
