package com.swooby.obsremote.messages.updates;

import java.util.ArrayList;

import com.swooby.obsremote.OBSRemoteService;

public class SourceOrderChanged extends Update
{
    public ArrayList<String> sources;
    
    @Override
    public void dispatchUpdate(OBSRemoteService serv)
    {
        serv.notifySourceOrderChanged(sources);
    }

}
