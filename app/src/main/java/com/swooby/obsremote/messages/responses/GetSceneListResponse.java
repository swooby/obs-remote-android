package com.swooby.obsremote.messages.responses;

import java.util.ArrayList;

import com.swooby.obsremote.messages.util.Scene;
import com.google.gson.annotations.SerializedName;

public class GetSceneListResponse extends Response
{
    @SerializedName("current-scene")
    public String currentScene;
    
    public ArrayList<Scene> scenes;
}
