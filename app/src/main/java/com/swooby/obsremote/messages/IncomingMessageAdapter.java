package com.swooby.obsremote.messages;

import android.util.Log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.swooby.obsremote.OBSRemoteApplication;
import com.swooby.obsremote.messages.responses.Response;

import java.lang.reflect.Type;

public class IncomingMessageAdapter
        implements JsonDeserializer<IncomingMessage>
{
    private static final String UPDATE_TYPE = "update-type";

    @Override
    public IncomingMessage deserialize(JsonElement json, Type typeOfT,
                                       JsonDeserializationContext context)
            throws JsonParseException
    {
        JsonObject jsonObject = json.getAsJsonObject();

        if (jsonObject.has(UPDATE_TYPE))
        {
            /* is an update */
            String updateName = jsonObject.get(UPDATE_TYPE).getAsString();
            Class<?> updateClass;
            try
            {
                updateClass = Class.forName("com.swooby.obsremote.messages.updates." + updateName);
            }
            catch (ClassNotFoundException e)
            {
                Log.e(OBSRemoteApplication.TAG, "Couldn't map update: " + updateName, e);
                return null;
            }

            return context.deserialize(jsonObject, updateClass);
        }
        else
        {
            /* is a response */
            return context.deserialize(jsonObject, Response.class);
        }
    }
}
