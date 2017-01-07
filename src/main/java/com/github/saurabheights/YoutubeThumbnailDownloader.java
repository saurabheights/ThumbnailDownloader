package com.github.saurabheights;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Thumbnail;
import com.google.api.services.youtube.model.ThumbnailDetails;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is used to download thumbnail from the Youtube platform using Youtube Video Url.
 * <p>
 * Created by SaurabhKhanduja on 05/08/16.
 */
public class YoutubeThumbnailDownloader {

    private static Logger logger = LoggerFactory.getLogger(YoutubeThumbnailDownloader.class);

    /**
     * Define a global instance of a YouTube object, which will be used to make
     * YouTube Data API requests.
     */
    private static YouTube youtube;

    /**
     * Define a global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Define a global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    /**
     * The google client API key.
     */
    private static String youtubeApiKey;

    /**
     * The name of the application registered with youtube.
     */
    private static String youtubeApplicationName;

    /**
     * The regex to extract video Id for youtube urls.
     * http://regexr.com/3dvbe
     * <p/>
     * Tested for following samples:-
     * https://www.youtube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index
     * https://www.youtube.com/user/IngridMichaelsonVEVO#p/a/u/1/QdK8U-VIH_o
     * https://www.youtube.com/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0
     * https://www.youtube.com/watch?v=0zM3nApSvMg#t=0m10s
     * https://www.youtube.com/embed/0zM3nApSvMg?rel=0
     * https://www.youtube.com/watch?v=0zM3nApSvMg
     * https://youtu.be/0zM3nApSvMg
     * https://m.youtube.com/watch?v=cYfmX8aGyv0
     * http://www.youtube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index
     * http://www.youtube.com/user/IngridMichaelsonVEVO#p/a/u/1/QdK8U-VIH_o
     * http://www.youtube.com/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0
     * http://www.youtube.com/watch?v=0zM3nApSvMg#t=0m10s
     * http://www.youtube.com/embed/0zM3nApSvMg?rel=0
     * http://www.youtube.com/watch?v=0zM3nApSvMg
     * http://youtu.be/0zM3nApSvMg
     * http://m.youtube.com/watch?v=cYfmX8aGyv0
     * www.youtube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index
     * www.youtube.com/user/IngridMichaelsonVEVO#p/a/u/1/QdK8U-VIH_o
     * www.youtube.com/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0
     * www.youtube.com/watch?v=0zM3nApSvMg#t=0m10s
     * www.youtube.com/embed/0zM3nApSvMg?rel=0
     * www.youtube.com/watch?v=0zM3nApSvMg
     * www.youtu.be/0zM3nApSvMg
     * www.m.youtube.com/watch?v=cYfmX8aGyv0
     * youtube.com/watch?v=0zM3nApSvMg&feature=feedrec_grec_index
     * youtube.com/user/IngridMichaelsonVEVO#p/a/u/1/QdK8U-VIH_o
     * youtube.com/v/0zM3nApSvMg?fs=1&amp;hl=en_US&amp;rel=0
     * youtube.com/watch?v=0zM3nApSvMg#t=0m10s
     * youtube.com/embed/0zM3nApSvMg?rel=0
     * youtube.com/watch?v=0zM3nApSvMg
     * youtu.be/0zM3nApSvMg
     * m.youtube.com/watch?v=cYfmX8aGyv0
     */
    private static String videoIdExtractorRegex = "(?:[?]v=|\\/embed\\/|\\/1\\/|\\/v\\/|(?:https:\\/\\/|http:\\/\\/|)" +
            "(?:www\\.|)?youtu\\.be\\/)([^&\\n?#]+)";

    /**
     * Precompile Pattern object
     */
    private static Pattern compiledPattern = Pattern.compile(videoIdExtractorRegex);


    static {
        try {
            loadConfig();
            init();
        } catch (Exception e) {
            logger.error("Failed to initialize YoutubeThumbnailDownloader.", e);
        }
    }

    private static void loadConfig() {
        Configurations configs = new Configurations();
        try {
            Configuration config = configs.properties(new File("config.properties"));
            youtubeApiKey = config.getString("youtube.api_key");
            if (youtubeApiKey == null) {
                throw new ConfigurationException("Configuration for youtube.api_key not found.");
            }
            youtubeApplicationName = config.getString("youtube.appName");
            if (youtubeApplicationName == null) {
                throw new ConfigurationException("Configuration for youtube.appName not found.");
            }
        } catch (ConfigurationException cex) {
            logger.warn("Youtube configuration could not be loaded. Check your config file config.properties.", cex);
        }
    }

    private static void init() throws Exception {
        try {
            // Authorize the request.
            if (null == youtubeApiKey) {
                throw new Exception("No api key in configuration file");
            }

            // This object is used to make YouTube Data API requests.
            youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpRequestInitializer() {
                public void initialize(HttpRequest request) throws IOException {
                }
            }).setApplicationName(youtubeApplicationName).build();
        } catch (Exception e) {
            throw new Exception("Failed to initialize youtube service", e);
        }
    }

    /**
     * This method extracts Video Id from Youtube Share Url using regex.
     *
     * @param youtubeUrl The share url of youtube video.
     * @return The video Id.
     */
    public static String getYoutubeVideoId(String youtubeUrl) throws IllegalArgumentException {
        if (StringUtils.isBlank(youtubeUrl)) {
            throw new IllegalArgumentException("Youtube Video url cannot be empty.");
        }

        Matcher matcher = compiledPattern.matcher(youtubeUrl);
        String videoId = null;
        if (matcher.find()) {
            try {
                videoId = matcher.group(1);
                logger.info("Video Id from youtube url : {}", videoId);
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to parse youtube url to feth video id: " + youtubeUrl);
            }
        } else {
            logger.error("Could not extract youtube video Id for youtube url: {}", youtubeUrl);
        }

        return videoId;
    }

    /**
     * This method checks the ThumbnailDetails object for best quality thumbnail.
     *
     * @param thumbnails
     * @return
     * @see <a href="https://developers.google.com/youtube/v3/docs/thumbnails">Thumbnail Doc</a>
     * <p/>
     * A sample json returned by youtube is as follows:
     * <p/>
     * {
     * "default": {
     * "height": 90,
     * "url": "https://i.ytimg.com/vi/C2omic5yWRQ/default.jpg",
     * "width": 120
     * },
     * "high": {
     * "height": 360,
     * "url": "https://i.ytimg.com/vi/C2omic5yWRQ/hqdefault.jpg",
     * "width": 480
     * },
     * "maxres": {
     * "height": 720,
     * "url": "https://i.ytimg.com/vi/C2omic5yWRQ/maxresdefault.jpg",
     * "width": 1280
     * },
     * "medium": {
     * "height": 180,
     * "url": "https://i.ytimg.com/vi/C2omic5yWRQ/mqdefault.jpg",
     * "width": 320
     * },
     * "standard": {
     * "height": 480,
     * "url": "https://i.ytimg.com/vi/C2omic5yWRQ/sddefault.jpg",
     * "width": 640
     * }
     * }
     * Note that: Each object that contains information about a thumbnail image size has a width property and a height
     * property. However, the width and height properties may not be returned for that image. As in Docs.
     */
    private static Thumbnail getBestQualityThumbnail(ThumbnailDetails thumbnails) {
        // Find thumbnail with the best quality
        if (thumbnails.getMaxres() != null && !StringUtils.isBlank(thumbnails.getMaxres().getUrl()))
            return thumbnails.getMaxres();

        if (thumbnails.getStandard() != null && !StringUtils.isBlank(thumbnails.getStandard().getUrl()))
            return thumbnails.getStandard();

        if (thumbnails.getHigh() != null && !StringUtils.isBlank(thumbnails.getHigh().getUrl()))
            return thumbnails.getHigh();

        if (thumbnails.getMedium() != null && !StringUtils.isBlank(thumbnails.getMedium().getUrl()))
            return thumbnails.getMedium();

        if (thumbnails.getDefault() != null && !StringUtils.isBlank(thumbnails.getDefault().getUrl()))
            return thumbnails.getDefault();

        logger.info("No youtube thumbnail found in ThumbnailDetails.");
        return null;
    }

    /**
     * This method fetches the thumbnail url for a youtube video id.
     */
    public static Thumbnail getBestQualityThumbnail(String videoId) throws IOException {
        // Call the YouTube Data API's videos.list method to retrieve videos.
        VideoListResponse videoListResponse;
        try {
            videoListResponse = youtube.
                    videos().
                    list("snippet").
                    setKey(youtubeApiKey).
                    setId(videoId).
                    execute();
        } catch (IOException e) {
            logger.error("Failed to fetch snippet of the videoId: {}", videoId);
            throw e;
        }

        // Since the API request specified a unique video ID, the API response should return exactly one video. If
        // the response does not contain a video, then the specified video ID was not found.
        List<Video> videoList = videoListResponse.getItems();
        if (videoList.isEmpty()) {
            logger.info("No such youtube video Id found: {} for video url: {}", videoId);
            return null;
        }

        if (videoList.size() == 0) {
            logger.info("No video data fetched for youtube video Id: {}", videoId);
            return null;
        }

        Video video = videoList.get(0);

        // Return Best Quality thumbnail
        if (null == video.getSnippet() || null == video.getSnippet().getThumbnails()) {
            logger.info("Youtube video object has no snippet or no thumbnail, snippet: {}", video.getSnippet());
            return null;
        }

        Thumbnail bestQualityThumbnail = getBestQualityThumbnail(video.getSnippet().getThumbnails());
        logger.info("Youtube Thumbnail Quality selected - (wxh): {}x{}",
                bestQualityThumbnail.getWidth(),
                bestQualityThumbnail.getHeight());
        return bestQualityThumbnail;
    }
}
