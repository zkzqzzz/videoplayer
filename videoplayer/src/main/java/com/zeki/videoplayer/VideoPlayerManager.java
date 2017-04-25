package com.zeki.videoplayer;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

/**
 * Put VideoPlayer into layout
 * From a VideoPlayer to another VideoPlayer
 */
public class VideoPlayerManager {

    public static WeakReference<MediaPlayerListener> CURRENT_SCROLL_LISTENER;
    public static LinkedList<WeakReference<MediaPlayerListener>> LISTENERLIST = new LinkedList<>();

    public static void putScrollListener(MediaPlayerListener listener) {
        if (listener.getScreenType() == VideoPlayer.SCREEN_WINDOW_TINY ||
                listener.getScreenType() == VideoPlayer.SCREEN_WINDOW_FULLSCREEN) return;
        CURRENT_SCROLL_LISTENER = new WeakReference<>(listener);//每次setUp的时候都应该add
    }

    public static void putListener(MediaPlayerListener listener) {
        LISTENERLIST.push(new WeakReference<>(listener));
    }
    public static void checkAndPutListener(MediaPlayerListener listener) {
        if (listener.getScreenType() == VideoPlayer.SCREEN_WINDOW_TINY ||
                listener.getScreenType() == VideoPlayer.SCREEN_WINDOW_FULLSCREEN) return;
        int location = -1;
        for (int i = 1; i < LISTENERLIST.size(); i++) {
            MediaPlayerListener mediaPlayerListener = LISTENERLIST.get(i).get();
            if (listener.getUrl().equals(mediaPlayerListener.getUrl())) {
                location = i;
            }
        }
        if (location != -1) {
            LISTENERLIST.remove(location);
            if (LISTENERLIST.size() <= location) {
                LISTENERLIST.addLast(new WeakReference<>(listener));
            } else {
                LISTENERLIST.set(location, new WeakReference<>(listener));

            }
        }
    }

    public static MediaPlayerListener popListener() {
        if (LISTENERLIST.size() == 0) {
            return null;
        }
        return LISTENERLIST.pop().get();
    }

    public static MediaPlayerListener getFirst() {
        if (LISTENERLIST.size() == 0) {
            return null;
        }
        return LISTENERLIST.getFirst().get();
    }

    public static void completeAll() {
        MediaPlayerListener ll = popListener();
        while (ll != null) {
            ll.onCompletion();
            ll = popListener();
        }
    }
}
