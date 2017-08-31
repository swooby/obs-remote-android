package com.swooby.obsremote.messages;

import com.swooby.obsremote.messages.responses.Response;

public interface ResponseHandler
{
    public void handleResponse(Response resp, String jsonMessage);
}
