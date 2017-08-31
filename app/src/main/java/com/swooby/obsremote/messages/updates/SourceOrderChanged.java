package com.swooby.obsremote.messages.updates;

import java.util.ArrayList;

import com.swooby.obsremote.WebSocketService;

public class SourceOrderChanged extends Update
{
    public ArrayList<String> sources;
    
    @Override
    public void dispatchUpdate(WebSocketService serv)
    {
        serv.notifySourceOrderChanged(sources);
    }

}
