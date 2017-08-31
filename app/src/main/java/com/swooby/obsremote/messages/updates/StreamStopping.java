package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.WebSocketService;

public class StreamStopping extends Update
{

    @Override
    public void dispatchUpdate(WebSocketService serv)
    {
        serv.notifyOnStreamStopping();
    }

}
