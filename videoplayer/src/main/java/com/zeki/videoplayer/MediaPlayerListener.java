package com.zeki.videoplayer;

/**
 */
public interface MediaPlayerListener {
    void onPrepared();

    void onCompletion();

    void onAutoCompletion();

    void onBufferingUpdate(int percent);

    void onSeekComplete();

    void onError(int what, int extra);

    void onInfo(int what, int extra);

    void onVideoSizeChanged();

    void goBackThisListener();

    boolean backToOtherListener();

    void onScrollChange();

    int getScreenType();

    String getUrl();

    int getState();

    void autoFullscreenLeft();

    void autoFullscreenRight();

    void autoQuitFullscreen();

}
