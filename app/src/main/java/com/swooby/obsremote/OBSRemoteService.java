package com.swooby.obsremote;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.stetho.DumperPluginsProvider;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.Stetho.Initializer;
import com.facebook.stetho.Stetho.InitializerBuilder;
import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.swooby.obsremote.messages.IncomingMessage;
import com.swooby.obsremote.messages.ResponseHandler;
import com.swooby.obsremote.messages.requests.Authenticate;
import com.swooby.obsremote.messages.requests.GetAuthRequired;
import com.swooby.obsremote.messages.requests.GetVersion;
import com.swooby.obsremote.messages.requests.Request;
import com.swooby.obsremote.messages.responses.AuthRequiredResp;
import com.swooby.obsremote.messages.responses.Response;
import com.swooby.obsremote.messages.responses.VersionResponse;
import com.swooby.obsremote.messages.updates.Update;
import com.swooby.obsremote.messages.util.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class OBSRemoteService
        extends Service
{
    private static final String TAG = "OBSRemoteService";

    public static final  float    appVersion     = 1.1f;
    private static final String[] wsSubProtocols = { "obsapi" };

    //private final WebSocketConnection remoteConnection = new WebSocketConnection();
    private OkHttpClient client;
    private WebSocket    websocket;

    private Set<RemoteUpdateListener>        listeners        = new HashSet<>();
    private HashMap<String, ResponseHandler> responseHandlers = new HashMap<>();

    /* status members */
    private boolean streaming;
    public  Object  previewOnly;

    private static String salted = "";
    private boolean authRequired;
    private boolean authenticated;

    private final Handler handler = new Handler();

    public static OkHttpClient initialize(@NonNull Context applicationContext)
    {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

        //noinspection PointlessBooleanExpression
        if (BuildConfig.DEBUG)
        {
            clientBuilder.addNetworkInterceptor(new StethoInterceptor());

            DumperPluginsProvider dumperPluginsProvider = Stetho.defaultDumperPluginsProvider(applicationContext);

            InitializerBuilder initializerBuilder = Stetho.newInitializerBuilder(applicationContext)
                    .enableDumpapp(dumperPluginsProvider);

        /*
        RealmInspectorModulesProvider realmInspectorModulesProvider = RealmInspectorModulesProvider.builder(applicationContext)
                //...
                .build();
        initializerBuilder.enableWebKitInspector(realmInspectorModulesProvider);
        */

            Initializer initializer = initializerBuilder.build();

            // TODO:(pv) Dang singletons! What if something else has already called Stetho.initilize(…)?!?!?
            Stetho.initialize(initializer);
        }

        // TODO:(pv) I absolutely cannot get the GzipRequestInterceptor to successfully *POST* a non-null body...
        //  Add example of what is failing...
        //sDefaultHttpClientBuilder.addInterceptor(new GzipRequestInterceptor());

        if (true)// && BuildConfig.DEBUG)
        {
            clientBuilder
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS);
        }

        return clientBuilder.build();
    }

    public void connect()
    {
        String hostname = getApp().getDefaultHostname();
        String wsuri = "ws://" + hostname + ":4444/";

        if (true)
        {
            if (client == null)
            {
                client = initialize(getApplicationContext());

                //Request request = new Request
                okhttp3.Request request = new okhttp3.Request.Builder().url(wsuri).build();

                websocket = client.newWebSocket(request, new OBSHandler());
            }
            else
            {
                checkVersion();
            }
        }
        else
        {
            /*
            try
            {
                if (remoteConnection.isConnected())
                {
                    checkVersion();
                }
                else
                {
                    remoteConnection.connect(wsuri, wsSubProtocols,
                            new WSHandler(), new WebSocketOptions(),
                            null);
                }
            }
            catch (WebSocketException e)
            {

                Log.d(TAG, e.toString());
            }
            */
        }
    }

    public void disconnect()
    {
        client.dispatcher().executorService().shutdown();
        //remoteConnection.disconnect();
        resetState();
    }

    private void resetState()
    {
        responseHandlers.clear();
        setStreaming(false);
        previewOnly = false;
        
        /* Don't reset salted we're holding on to this for auto re-authenticate */
        // salted = "";
        authRequired = false;
        authenticated = false;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.d(TAG, "WebSocketService stopped");
        notifyOnClose(0, "Service destroyed");

        listeners.clear();
        client.dispatcher().executorService().shutdown();
        //remoteConnection.disconnect();
    }

    public OBSRemoteApplication getApp()
    {
        return (OBSRemoteApplication) getApplicationContext();
    }

    public class LocalBinder
            extends Binder
    {
        public OBSRemoteService getService()
        {
            // Return this instance of WebSocketService so clients can call public methods
            return OBSRemoteService.this;
        }
    }

    private boolean bound = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        /* if nothing is bound try connecting, else cancle any shutdowns happening */
        if (!bound)
        {
            startShutdown();
        }
        else
        {
            cancelShutdown();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        cancelShutdown();

        bound = true;

        // start self
        startService(new Intent(this, OBSRemoteService.class));

        return new LocalBinder();
    }

    @Override
    public void onRebind(Intent intent)
    {
        cancelShutdown();
    }

    @Override
    public boolean onUnbind(Intent i)
    {
        bound = false;

        //commented out: go ahead and shutdown while streaming
        //if(!streaming)
        startShutdown();

        // don't want rebind
        return true;
    }

    public void startShutdown()
    {
        // post a callback to be run in 1 minute
        handler.postDelayed(delayedShutdown, 1000L * 60);
        Log.d(TAG, "Starting shutdown!");
    }

    private Runnable delayedShutdown = new Runnable()
    {
        @Override
        public void run()
        {
            stopSelf();
        }
    };

    /**
     * Cancel any shutdown timer that may have been set.
     */
    private void cancelShutdown()
    {
        // remove any shutdown callbacks registered
        Log.d(TAG, "Canceling shutdown!");
        handler.removeCallbacks(delayedShutdown);
    }
    
    /*private Notification notification;
    private NotificationManager notfManager;
    private static final int NOTIFICATION_ID = 2106;
    */

    public void setStreaming(boolean newStreaming)
    {
        /*if(!streaming && newStreaming)
        {
            notfManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            
            RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.remote_notification);
            
            Intent startRemote = new Intent(this, Remote.class);
            startRemote.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            
            // Gets a PendingIntent containing the entire back stack
            PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, startRemote, 0);
            
            Builder builder = new Builder(this);
            // Set Icon
            builder.setSmallIcon(R.drawable.notification_icon);
            // Set Ticker Message
            builder.setTicker("Streaming");
            // Don't Dismiss Notification
            builder.setAutoCancel(false);
            // Set PendingIntent into Notification
            builder.setContentIntent(resultPendingIntent);
            // Set RemoteViews into Notification
            builder.setContent(remoteViews);
            
            notification = builder.build();
            
            // have to set this manually to deal with support library bug *facepalm*
            notification.contentView = remoteViews;
            
            cancelShutdown();
            
            startForeground(NOTIFICATION_ID, notification);
        }
        else if(streaming && !newStreaming)        {
            //stop foreground
            stopForeground(true);
            notification = null;
            
            // if we stop streaming and no activities active shutdown
            if(!bound)
            {
                startShutdown();
            }
        }*/

        streaming = newStreaming;
    }
    
    /*private long lastTimeUpdated = 0;
    float maxStrain = 0;
    
    public void updateNotification(int totalStreamTime, int fps,
            float strain, int numDroppedFrames, int numTotalFrames, int bps)
    {
        if(notification != null)
        {
            
            long currentTime = System.currentTimeMillis();
            
            maxStrain = Math.max(maxStrain, strain);
            
            if(currentTime - lastTimeUpdated < 1000)
                return;
            
            lastTimeUpdated = currentTime;
            
            notification.contentView.setTextViewText(R.id.notificationtime, getString(R.string.timerunning) + " " + Remote.getTimeString(totalStreamTime));
            
            notification.contentView.setTextViewText(R.id.notificationfps, getString(R.string.fps) + " " + fps);
            
            notification.contentView.setTextViewText(R.id.notificationbittratevalue, Remote.getBitrateString(bps));
            
            if(android.os.Build.VERSION.SDK_INT > 9)
            {
                notification.contentView.setTextColor(R.id.notificationbittratevalue, Remote.strainToColor(maxStrain));
            }
            
            notification.contentView.setTextViewText(R.id.notificationdropped, getString(R.string.droppedframes) + " " + numDroppedFrames);
            
            notfManager.notify(NOTIFICATION_ID, notification);
            
            maxStrain = 0;            
        }
    }
    */

    public boolean getStreaming()
    {
        return streaming;
    }

    private class OBSHandler
            extends WebSocketListener
    {
        @Override
        public void onOpen(WebSocket webSocket, okhttp3.Response response)
        {
            Log.d(TAG, "onOpen(...)");
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    checkVersion();
                }
            });
        }

        @Override
        public void onMessage(WebSocket webSocket, final String text)
        {
            Log.d(TAG, "onMessage(..., text:" + text + ")");
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    handleIncomingMessage(text);
                }
            });
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes)
        {
            Log.d(TAG, "onMessage(..., bytes:" + bytes + ")");
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason)
        {
            Log.d(TAG, "onClosing(..., code:" + code + ", reason:" + reason + ")");
        }

        @Override
        public void onClosed(WebSocket webSocket, final int code, final String reason)
        {
            Log.d(TAG, "onClosed(..., code:" + code + ", reason:" + reason + ")");
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    notifyOnClose(code, reason);
                }
            });
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable throwable, okhttp3.Response response)
        {
            Log.d(TAG, "onFailure(..., throwable:" + throwable + ", response:" + response + ")");
            handler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    notifyOnClose(2, null);
                }
            });
        }
    }

    /*
    private class WSHandler
            implements WebSocket.ConnectionHandler
    {
        @Override
        public void onTextMessage(String message)
        {
            //Log.d(TAG, "IonTextMessage(message:" + message + ")");
            handleIncomingMessage(message);
        }

        @Override
        public void onOpen()
        {
            Log.d(TAG, "onOpen()");
            checkVersion();
        }

        @Override
        public void onClose(int code, String reason)
        {
            Log.d(TAG, "onClose(code:" + code + ", reason:" + reason + ")");
            notifyOnClose(code, reason);
        }

        @Override
        public void onBinaryMessage(byte[] arg0)
        {
            //nothing
        }

        @Override
        public void onRawTextMessage(byte[] arg0)
        {
            //nothing
        }
    }
    */

    public void sendRequest(Request request)
    {
        sendRequest(request, null);
    }

    public void sendRequest(Request request, ResponseHandler messageHandler)
    {
        String messageJson = getApp().getGson().toJson(request);

        if (messageHandler != null)
        {
            responseHandlers.put(request.messageId, messageHandler);
        }

        websocket.send(messageJson);
        //remoteConnection.sendTextMessage(messageJson);
    }

    public void handleIncomingMessage(String message)
    {
        IncomingMessage inc = getApp().getGson().fromJson(message, IncomingMessage.class);
        if (inc == null)
        {
            return;
        }

        if (inc.isUpdate())
        {
            Update update = (Update) inc;

            /* polymorphic update dispatch */
            update.dispatchUpdate(this);
        }
        else
        {
            //it's a response
            Response resp;
            try
            {
                resp = (Response) inc;
            }
            catch (ClassCastException e)
            {
                Log.e(TAG, "Failed to cast response.");
                return;
            }

            String messageId = resp.getID();
            ResponseHandler handler = responseHandlers.get(messageId);
            if (handler != null)
            {
                handler.handleResponse(resp, message);
            }
        }
    }

    /* auth stuff */
    public void autoAuthenticate(String s)
    {
        salted = s;
        authenticateWithSalted(salted);
    }

    public void authenticate(String password)
    {
        String salt = getApp().getAuthSalt();
        getApp().getAuthChallenge();

        salted = OBSRemoteApplication.sign(password, salt);
        authenticateWithSalted(salted);
    }

    public void authenticateWithSalted(String salted)
    {
        String challenge = getApp().getAuthChallenge();
        String hashed;

        hashed = OBSRemoteApplication.sign(salted, challenge);

        sendRequest(new Authenticate(hashed), new ResponseHandler()
        {
            @Override
            public void handleResponse(Response resp, String jsonMessage)
            {

                if (resp.isOk())
                {
                    notifyOnAuthenticated();
                }
                else
                {
                    getApp().setAuthSalted("");

                    // try authenticating again
                    notifyOnFailedAuthentication(resp.getError());
                }
            }
        });
    }

    private void checkVersion()
    {
        sendRequest(new GetVersion(), new ResponseHandler()
        {
            @Override
            public void handleResponse(Response resp, String jsonMessage)
            {
                VersionResponse vResp = getApp().getGson().fromJson(jsonMessage, VersionResponse.class);

                if (vResp.version != appVersion)
                {
                    /* throw a fit */
                    Log.d(OBSRemoteApplication.TAG, "Version mismatch.");

                    client.dispatcher().executorService().shutdown();
                    //remoteConnection.disconnect();

                    notifyOnVersionMismatch(vResp.version);
                }
                else
                {
                    Log.d(OBSRemoteApplication.TAG, "Version good.");
                    checkAuthRequired();
                }
            }
        });
    }

    private void checkAuthRequired()
    {
        sendRequest(new GetAuthRequired(), new ResponseHandler()
        {
            @Override
            public void handleResponse(Response resp, String jsonMessage)
            {
                AuthRequiredResp authResp = getApp().getGson().fromJson(jsonMessage, AuthRequiredResp.class);
                authRequired = authResp.authRequired;

                if (authRequired)
                {
                    getApp().setAuthChallenge(authResp.challenge);

                    if (getApp().getAuthSalt().equals(authResp.salt))
                    {
                        if (!salted.equals(""))
                        {
                            autoAuthenticate(salted);
                        }
                        else if (getApp().getRememberPassword() && !getApp().getAuthSalted().equals(""))
                        {
                            /* circumstances right to try auto authenticate */
                            autoAuthenticate(getApp().getAuthSalted());
                        }
                        else
                        {
                            notifyNeedsAuthentication();
                        }
                    }
                    else
                    {
                        /* else notify authentication needed */
                        getApp().setAuthSalt(authResp.salt);

                        notifyNeedsAuthentication();
                    }
                }
                else
                {
                    notifyOnAuthenticated();
                }
            }
        });
    }

    public void addUpdateListener(RemoteUpdateListener listener)
    {
        listeners.add(listener);
    }

    public void removeUpdateListener(RemoteUpdateListener listener)
    {
        listeners.remove(listener);
    }

    public boolean isConnected()
    {
        return websocket != null;//remoteConnection.isConnected();
    }

    /* is everything ready for normal operation */
    public boolean isReady()
    {
        return isConnected() && (!authRequired || authenticated);
    }

    public boolean needsAuth()
    {
        return authRequired;
    }

    public boolean authenticated()
    {
        return authenticated;
    }

    /* methods for updating listeners */
    private void notifyNeedsAuthentication()
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onNeedsAuthentication();
        }
    }

    private void notifyOnAuthenticated()
    {
        authenticated = true;

        if (authRequired && getApp().getRememberPassword())
        {
            getApp().setAuthSalted(salted);
        }

        for (RemoteUpdateListener listener : listeners)
        {
            listener.onConnectionAuthenticated();
        }
    }

    private void notifyOnFailedAuthentication(final String message)
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onFailedAuthentication(message);
        }
    }

    private void notifyOnClose(final int code, final String reason)
    {
        resetState();

        for (RemoteUpdateListener listener : listeners)
        {
            listener.onConnectionClosed(code, reason);
        }
    }

    public void notifyOnStreamStarting(final boolean previewOnly)
    {
        setStreaming(true);

        this.previewOnly = previewOnly;

        for (RemoteUpdateListener listener : listeners)
        {
            listener.onStreamStarting(previewOnly);
        }
    }

    public void notifyOnStreamStopping()
    {
        setStreaming(false);

        this.previewOnly = false;

        for (RemoteUpdateListener listener : listeners)
        {
            listener.onStreamStopping();
        }
    }

    public void notifyStreamStatusUpdate(final int totalStreamTime, final int fps,
                                         final float strain, final int numDroppedFrames, final int numTotalFrames, final int bps)
    {
        //updateNotification(totalStreamTime, fps, strain, numDroppedFrames, numTotalFrames, bps);

        for (RemoteUpdateListener listener : listeners)
        {
            listener.onStreamStatusUpdate(totalStreamTime, fps, strain, numDroppedFrames, numTotalFrames, bps);
        }
    }

    public void notifyOnSceneSwitch(final String sceneName)
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onSceneSwitch(sceneName);
        }
    }

    public void notifyOnScenesChanged()
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onScenesChanged();
        }
    }

    public void notifySourceChange(final String sourceName, final Source source)
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onSourceChanged(sourceName, source);
        }
    }

    public void notifySourceOrderChanged(final ArrayList<String> sources)
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onSourceOrderChanged(sources);
        }
    }

    public void notifyRepopulateSources(final ArrayList<Source> sources)
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onRepopulateSources(sources);
        }
    }

    public void notifyVolumeChanged(final String channel, final boolean finalValue,
                                    final float volume, final boolean muted)
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onVolumeChanged(channel, finalValue, volume, muted);
        }
    }

    protected void notifyOnVersionMismatch(final float version)
    {
        for (RemoteUpdateListener listener : listeners)
        {
            listener.onVersionMismatch(version);
        }
    }
}
