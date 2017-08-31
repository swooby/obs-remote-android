package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.OBSRemoteService;

public class StreamStopping extends Update
{

    @Override
    public void dispatchUpdate(OBSRemoteService serv)
    {
        serv.notifyOnStreamStopping();
    }

}
