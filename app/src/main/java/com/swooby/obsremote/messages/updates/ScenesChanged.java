package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.OBSRemoteService;

public class ScenesChanged extends Update
{

    @Override
    public void dispatchUpdate(OBSRemoteService serv)
    {
        serv.notifyOnScenesChanged();
    }

}
