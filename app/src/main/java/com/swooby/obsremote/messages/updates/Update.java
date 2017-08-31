package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.WebSocketService;
import com.swooby.obsremote.messages.IncomingMessage;
import com.google.gson.annotations.SerializedName;

public abstract class Update implements IncomingMessage
{
    @SerializedName("update-type")
    protected String updateType;

    public Update()
    {
        updateType = this.getClass().getSimpleName();
    }
    
    @Override
    public boolean isUpdate()
    {
        return true;
    }
    
    public abstract void dispatchUpdate(WebSocketService serv);
    
}
