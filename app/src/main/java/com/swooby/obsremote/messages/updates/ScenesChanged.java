package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.WebSocketService;

public class ScenesChanged extends Update
{

    @Override
    public void dispatchUpdate(WebSocketService serv)
    {
        serv.notifyOnScenesChanged();
    }

}
