package com.swooby.obsremote.messages.updates;

import java.util.ArrayList;

import com.swooby.obsremote.WebSocketService;
import com.swooby.obsremote.messages.util.Source;

public class RepopulateSources extends Update
{
    public ArrayList<Source> sources;
    
    @Override
    public void dispatchUpdate(WebSocketService serv)
    {
        serv.notifyRepopulateSources(sources);
    }

}
