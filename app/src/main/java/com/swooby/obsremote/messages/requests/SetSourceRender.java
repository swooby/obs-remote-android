package com.swooby.obsremote.messages.requests;

public class SetSourceRender extends Request
{
    public String source;
    public boolean render;
    
    public SetSourceRender(String source, boolean render)
    {
        super("SetSourceRender");
        
        this.source = source;
        this.render = render;
    }
}
