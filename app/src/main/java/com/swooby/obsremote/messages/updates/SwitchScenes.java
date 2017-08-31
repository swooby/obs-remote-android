package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.OBSRemoteService;
import com.google.gson.annotations.SerializedName;

public class SwitchScenes extends Update
{
    @SerializedName("scene-name")
    public String sceneName;

    @Override
    public void dispatchUpdate(OBSRemoteService serv)
    {
        serv.notifyOnSceneSwitch(sceneName);
    }
}
