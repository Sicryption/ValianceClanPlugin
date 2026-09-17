package com.encryptiron.rest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends a drop's screenshot, once an event has said it wants the drop.
 *
 * Deliberately not a {@link BaseRestCommand}. That class exists to post a JSON
 * document with our standard header attached; this posts raw PNG bytes, the same
 * way the website's own image uploads work - no multipart, no base64, because
 * base64 would inflate a screenshot by a third for nothing.
 *
 * The submission ids come from the accept response and are the only thing
 * addressing this upload. There is no identity on the request beyond that,
 * matching the drop endpoint it follows: the plugin has no credentials to offer,
 * and the server bounds the endpoint by the ids being unpublished, single-use
 * and short-lived rather than by authenticating the caller.
 */
@Slf4j
@Singleton
public class SendDropScreenshot
{
    private static final MediaType IMAGE_PNG = MediaType.parse("image/png");
    private static final String MESSAGING_PROTOCOL = "http://";
    private static final int PORT = 8080;
    private static final String ENDPOINT = "/api/member/item_drop_screenshot";

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private ValianceConfig config;

    /**
     * @param eventId       the event that accepted the drop
     * @param submissionIds every submission that accept created, all of which
     *                      are looking at this one screenshot
     * @param png           the encoded frame
     *
     * Returns as soon as the call is queued - OkHttp does the sending on its own
     * dispatcher, so the client thread never waits on the network.
     */
    public void send(String eventId, List<String> submissionIds, byte[] png)
    {
        if (eventId == null || submissionIds == null || submissionIds.isEmpty() || png == null)
        {
            return;
        }

        URL url;
        try
        {
            url = new URL(MESSAGING_PROTOCOL + config.valianceServerUrl() + ":" + PORT + ENDPOINT);
        }
        catch (MalformedURLException e)
        {
            log.error("MalformedURL : " + e.getMessage());
            return;
        }

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "ValiancePlugin - " + MessageHeaderData.getPlayerName())
            .header("X-Event-Id", eventId)
            .header("X-Submission-Ids", String.join(",", submissionIds))
            .post(RequestBody.create(IMAGE_PNG, png))
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response closed = response)
                {
                    if (closed.isSuccessful())
                    {
                        log.debug("Sent drop screenshot for " + submissionIds.size() + " submission(s).");
                    }
                    else
                    {
                        // Warn rather than debug, and carry the server's own
                        // words. A refusal here is silent everywhere else - the
                        // drop still counts, the player sees nothing wrong, and
                        // the screenshot simply never appears on the site - so
                        // this line is the only place the reason is ever stated.
                        String reason = "";
                        try
                        {
                            reason = closed.body() == null ? "" : closed.body().string();
                        }
                        catch (IOException ignored)
                        {
                            // The code alone is still worth reporting.
                        }

                        log.warn("Drop screenshot refused with code {}: {}", closed.code(), reason);
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Failed to send drop screenshot: " + e.getMessage());
            }
        });
    }
}
