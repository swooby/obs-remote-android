package com.swooby.obsremote.messages.updates;

import com.swooby.obsremote.WebSocketService;

public class VolumeChanged extends Update
{
    public String channel;
    
    public boolean finalValue;
    
    public float volume;
    
    public boolean muted;
    
    @Override
    public void dispatchUpdate(WebSocketService serv)
    {
        serv.notifyVolumeChanged(channel, finalValue, volume, muted);
    }

}
