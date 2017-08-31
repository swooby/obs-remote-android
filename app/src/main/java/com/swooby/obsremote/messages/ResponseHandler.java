package com.swooby.obsremote.messages;

import com.swooby.obsremote.messages.responses.Response;

public interface ResponseHandler
{
    void handleResponse(Response resp, String jsonMessage);
}
