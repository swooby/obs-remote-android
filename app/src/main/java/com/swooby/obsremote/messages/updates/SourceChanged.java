package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.OBSRemoteService;
import com.swooby.obsremote.messages.util.Source;
import com.google.gson.annotations.SerializedName;

public class SourceChanged extends Update
{
    @SerializedName("source-name")
    public String sourceName;
    
    public Source source;
    
    @Override
    public void dispatchUpdate(OBSRemoteService serv)
    {
        serv.notifySourceChange(sourceName, source);
        
    }

}
