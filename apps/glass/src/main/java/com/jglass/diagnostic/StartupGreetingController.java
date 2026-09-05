package com.jglass.diagnostic;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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

    private final Context context;
    private final StartupGreetingStore store;
    private final StartupGreetingClient client;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean active;
    private boolean playbackStarted;
    private boolean bootLaunch;
    private boolean networkFinished;
    private int requestAttempts;
    private long networkDeadline;
    private long generation;
    private MediaPlayer player;
    private final Runnable retryNetwork = new Runnable() {
        @Override
        public void run() {
            attemptBootRequest();
        }
    };
    private final Runnable networkTimedOut = new Runnable() {
        @Override
        public void run() {
            if (!active || playbackStarted || networkFinished) return;
            client.cancel();
            useFallback(currentLocalDate());
        }
    };

    StartupGreetingController(Context context, StartupGreetingStore store, Listener listener) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.listener = listener;
        client = new StartupGreetingClient();
    }

    boolean start(boolean force, boolean fromBoot) {
        cancel();
        final String today = StartupGreetingRules.localDate(new Date());
        if (!StartupGreetingRules.shouldStart(store.lastGreetingDate(), today, force)) return false;
        active = true;
        playbackStarted = false;
        bootLaunch = fromBoot;
        networkFinished = false;
        requestAttempts = 0;
        store.deleteDynamic();
        if (store.bridgeUrl() == null || store.deviceToken() == null) {
            useFallback(today);
            return true;
        }
        if (bootLaunch) {
            networkDeadline = SystemClock.elapsedRealtime()
                    + StartupGreetingRetryPolicy.BOOT_DEADLINE_MILLIS;
            main.postDelayed(networkTimedOut, StartupGreetingRetryPolicy.BOOT_DEADLINE_MILLIS);
            attemptBootRequest();
        } else {
            requestGreeting(today);
        }
        return true;
    }

    private void requestGreeting(final String today) {
        final long requestGeneration = generation;
        requestAttempts += 1;
        client.request(store.bridgeUrl(), store.deviceToken(), store.fallbackVersion(),
                new StartupGreetingClient.Listener() {
                    @Override
                    public void onSuccess(final StartupGreetingProtocol.Response response) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (generation != requestGeneration || networkFinished) return;
                                handleResponse(today, response);
                            }
                        });
                    }

                    @Override
                    public void onFailure() {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (generation != requestGeneration || networkFinished) return;
                                if (bootLaunch && active
                                        && StartupGreetingRetryPolicy.shouldRetryRequest(
                                                requestAttempts, remainingNetworkTime())) {
                                    main.postDelayed(retryNetwork,
                                            StartupGreetingRetryPolicy.REQUEST_RETRY_MILLIS);
                                } else {
                                    useFallback(today);
                                }
                            }
                        });
                    }
                });
    }

    private void attemptBootRequest() {
        if (!active || networkFinished) return;
        if (remainingNetworkTime() <= 0) {
            useFallback(currentLocalDate());
            return;
        }
        if (!networkConnected()) {
            main.postDelayed(retryNetwork,
                    StartupGreetingRetryPolicy.networkPollDelay(remainingNetworkTime()));
            return;
        }
        requestGreeting(currentLocalDate());
    }

    void cancel() {
        generation++;
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
        finishNetworkPhase();
        try {
            if (!today.equals(response.greetingDate)) throw new IOException("Bridge date differs from Glass date");
            File dynamic = store.writeDynamic(response.audio.bytes);
            play(dynamic, today, response.weatherSummary, response.attribution, true);
        } catch (IOException ignored) {
            useFallback(today);
        }
    }

    private void useFallback(String today) {
        if (!active) return;
        finishNetworkPhase();
        Calendar calendar = Calendar.getInstance();
        StartupGreetingRules.Period period = StartupGreetingRules.periodForHour(
                calendar.get(Calendar.HOUR_OF_DAY));
        StartupGreetingRules.FallbackCause cause = fallbackCause();
        try {
            File fallback = store.bundledFallbackFile(period, cause);
            play(fallback, today, "Nearby weather unavailable", "", false);
        } catch (IOException failure) {
            listener.onTextOnlyUnavailable(StartupGreetingRules.fallbackText(period, cause));
            return;
        }
    }

    private void finishNetworkPhase() {
        networkFinished = true;
        client.cancel();
        main.removeCallbacks(retryNetwork);
        main.removeCallbacks(networkTimedOut);
    }

    private long remainingNetworkTime() {
        return Math.max(0L, networkDeadline - SystemClock.elapsedRealtime());
    }

    private String currentLocalDate() {
        return StartupGreetingRules.localDate(new Date());
    }

    private boolean networkConnected() {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            NetworkInfo network = manager.getActiveNetworkInfo();
            return network != null && network.isConnected();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private StartupGreetingRules.FallbackCause fallbackCause() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return StartupGreetingRules.fallbackCause(adapter != null && adapter.isEnabled());
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
