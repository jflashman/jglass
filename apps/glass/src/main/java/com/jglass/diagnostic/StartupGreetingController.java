package com.jglass.diagnostic;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

final class StartupGreetingController {
    interface Listener {
        void onPlaybackStarted(String summary, String attribution);

        void onTextOnlyUnavailable(String fallbackText);

        void onFinished();
    }

    private final StartupGreetingStore store;
    private final StartupGreetingClient client;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean active;
    private boolean playbackStarted;
    private MediaPlayer player;

    StartupGreetingController(StartupGreetingStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
        client = new StartupGreetingClient();
    }

    boolean start(boolean force) {
        cancel();
        final String today = StartupGreetingRules.localDate(new Date());
        if (!StartupGreetingRules.shouldStart(store.lastGreetingDate(), today, force)) return false;
        active = true;
        playbackStarted = false;
        store.deleteDynamic();
        if (store.bridgeUrl() == null || store.deviceToken() == null) {
            useFallback(today);
            return true;
        }
        client.request(store.bridgeUrl(), store.deviceToken(), store.fallbackVersion(),
                new StartupGreetingClient.Listener() {
                    @Override
                    public void onSuccess(final StartupGreetingProtocol.Response response) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                handleResponse(today, response);
                            }
                        });
                    }

                    @Override
                    public void onFailure() {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                useFallback(today);
                            }
                        });
                    }
                });
        return true;
    }

    void cancel() {
        active = false;
        playbackStarted = false;
        client.cancel();
        main.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // Release remains required.
            }
            player.release();
            player = null;
        }
        store.deleteDynamic();
    }

    private void handleResponse(String today, StartupGreetingProtocol.Response response) {
        if (!active) return;
        try {
            if (!today.equals(response.greetingDate)) throw new IOException("Bridge date differs from Glass date");
            if (response.fallbackAssets != null) store.installFallbacks(response.fallbackAssets);
            File dynamic = store.writeDynamic(response.audio.bytes);
            play(dynamic, today, response.weatherSummary, response.attribution, true);
        } catch (IOException ignored) {
            useFallback(today);
        }
    }

    private void useFallback(String today) {
        if (!active) return;
        Calendar calendar = Calendar.getInstance();
        StartupGreetingRules.Period period = StartupGreetingRules.periodForHour(
                calendar.get(Calendar.HOUR_OF_DAY));
        File fallback = store.fallbackFile(period);
        if (fallback == null) {
            listener.onTextOnlyUnavailable(StartupGreetingRules.fallbackText(period));
            return;
        }
        play(fallback, today, "Nearby weather unavailable", "", false);
    }

    private void play(final File file, final String today, final String summary,
            final String attribution, final boolean deleteAfter) {
        if (!active) return;
        try {
            final MediaPlayer mediaPlayer = new MediaPlayer();
            player = mediaPlayer;
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer prepared) {
                    if (!active || player != prepared) return;
                    try {
                        prepared.start();
                        playbackStarted = true;
                        store.recordGreetingDate(today);
                        listener.onPlaybackStarted(summary, attribution);
                    } catch (RuntimeException failure) {
                        handlePlaybackFailure(prepared, today, deleteAfter);
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer completed) {
                    finishPlayback(completed, deleteAfter);
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer failed, int what, int extra) {
                    handlePlaybackFailure(failed, today, deleteAfter);
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (IOException | RuntimeException failure) {
            if (deleteAfter) {
                store.deleteDynamic();
                useFallback(today);
            } else {
                listener.onTextOnlyUnavailable("Nearby weather is unavailable.");
            }
        }
    }

    private void finishPlayback(MediaPlayer completed, boolean deleteAfter) {
        if (player == completed) player = null;
        completed.release();
        if (deleteAfter) store.deleteDynamic();
        if (active) {
            active = false;
            listener.onFinished();
        }
    }

    private void finishPlaybackAfterError(boolean deleteAfter) {
        if (deleteAfter) store.deleteDynamic();
        if (active) {
            active = false;
            listener.onFinished();
        }
    }

    private void handlePlaybackFailure(MediaPlayer failed, String today, boolean deleteAfter) {
        if (player == failed) player = null;
        failed.release();
        if (deleteAfter && active && !playbackStarted) {
            store.deleteDynamic();
            useFallback(today);
        } else if (active) {
            finishPlaybackAfterError(deleteAfter);
        }
    }
}
