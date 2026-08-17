package com.jglass.diagnostic;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

final class AudioLoopbackController {
    interface Listener {
        void onRecordingStarted();

        void onPlaybackStarted(int durationMillis);

        void onIdle();

        void onError(String safeMessage);
    }

    private static final int SAMPLE_RATE_HZ = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int MAX_RECORDING_MILLIS = 5000;
    private static final int MAX_PCM_BYTES =
            SAMPLE_RATE_HZ * BYTES_PER_SAMPLE * MAX_RECORDING_MILLIS / 1000;

    private final Listener listener;
    private volatile boolean captureRequested;
    private volatile boolean cancelRequested;
    private volatile AudioRecord activeRecorder;
    private volatile AudioTrack activeTrack;
    private Thread worker;

    AudioLoopbackController(Listener listener) {
        this.listener = listener;
    }

    synchronized boolean isBusy() {
        return worker != null && worker.isAlive();
    }

    synchronized boolean isRecording() {
        return captureRequested && isBusy();
    }

    synchronized void startRecording() {
        if (isBusy()) {
            return;
        }

        captureRequested = true;
        cancelRequested = false;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                recordThenPlay();
            }
        }, "jglass-audio-loopback");
        worker.start();
    }

    void stopRecordingAndPlay() {
        captureRequested = false;
    }

    void cancel() {
        cancelRequested = true;
        captureRequested = false;

        AudioRecord recorder = activeRecorder;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
                // The worker owns final cleanup.
            }
        }

        AudioTrack track = activeTrack;
        if (track != null) {
            try {
                track.pause();
                track.flush();
            } catch (IllegalStateException ignored) {
                // The worker owns final cleanup.
            }
        }
    }

    private void recordThenPlay() {
        BoundedPcmBuffer pcm = new BoundedPcmBuffer(MAX_PCM_BYTES);
        try {
            int minimum = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                notifyError("Microphone format is unavailable");
                return;
            }

            int bufferSize = Math.max(minimum, 4096);
            AudioRecord recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
            activeRecorder = recorder;
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                notifyError("Microphone could not initialize");
                return;
            }

            recorder.startRecording();
            listener.onRecordingStarted();
            byte[] chunk = new byte[Math.min(bufferSize, 8192)];
            while (captureRequested && !cancelRequested && !pcm.isFull()) {
                int count = recorder.read(chunk, 0, Math.min(chunk.length, pcm.remaining()));
                if (count > 0) {
                    pcm.append(chunk, 0, count);
                } else if (count < 0) {
                    notifyError("Microphone read failed");
                    return;
                }
            }

            stopAndReleaseRecorder(recorder);
            activeRecorder = null;

            if (cancelRequested || pcm.size() == 0) {
                listener.onIdle();
                return;
            }

            byte[] captured = pcm.snapshot();
            pcm.clear();
            play(captured);
            clear(captured);
        } catch (RuntimeException error) {
            notifyError("Audio loopback failed");
        } finally {
            AudioRecord recorder = activeRecorder;
            if (recorder != null) {
                stopAndReleaseRecorder(recorder);
                activeRecorder = null;
            }
            AudioTrack track = activeTrack;
            if (track != null) {
                stopAndReleaseTrack(track);
                activeTrack = null;
            }
            synchronized (this) {
                worker = null;
            }
        }
    }

    private void play(byte[] captured) {
        int durationMillis = captured.length * 1000 / (SAMPLE_RATE_HZ * BYTES_PER_SAMPLE);
        int minimum = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) {
            notifyError("Playback format is unavailable");
            return;
        }

        AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minimum, 4096),
                AudioTrack.MODE_STREAM);
        activeTrack = track;
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            notifyError("Speaker could not initialize");
            return;
        }

        listener.onPlaybackStarted(durationMillis);
        track.play();
        int offset = 0;
        while (offset < captured.length && !cancelRequested) {
            int count = Math.min(4096, captured.length - offset);
            int written = track.write(captured, offset, count);
            if (written <= 0) {
                notifyError("Speaker write failed");
                return;
            }
            offset += written;
        }

        int expectedFrames = captured.length / BYTES_PER_SAMPLE;
        while (!cancelRequested && unsignedPlaybackHead(track) < expectedFrames) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancelRequested = true;
            }
        }

        stopAndReleaseTrack(track);
        activeTrack = null;
        listener.onIdle();
    }

    private static long unsignedPlaybackHead(AudioTrack track) {
        return track.getPlaybackHeadPosition() & 0xffffffffL;
    }

    private static void stopAndReleaseRecorder(AudioRecord recorder) {
        try {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
            }
        } catch (IllegalStateException ignored) {
            // Release is still required.
        }
        recorder.release();
    }

    private static void stopAndReleaseTrack(AudioTrack track) {
        try {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop();
            }
        } catch (IllegalStateException ignored) {
            // Release is still required.
        }
        track.release();
    }

    private void notifyError(String safeMessage) {
        captureRequested = false;
        listener.onError(safeMessage);
    }

    private static void clear(byte[] bytes) {
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = 0;
        }
    }
}
