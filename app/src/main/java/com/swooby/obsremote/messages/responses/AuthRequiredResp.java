package com.swooby.obsremote.messages.responses;


public class AuthRequiredResp extends Response
{
    public boolean authRequired;
    public String challenge;
    public String salt;
}
