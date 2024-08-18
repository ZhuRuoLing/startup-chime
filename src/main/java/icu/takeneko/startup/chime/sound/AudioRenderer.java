/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package icu.takeneko.startup.chime.sound;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTThreadLocalContext.*;
import static org.lwjgl.openal.SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT;
import static org.lwjgl.system.MemoryUtil.*;

class AudioRenderer implements AutoCloseable {
    private static final int BUFFER_SIZE = 1024 * 8;

    private final VorbisTrack track;

    private final int format;

    private final long device;
    private final long context;

    private final int source;
    private final IntBuffer buffers;

    private final ShortBuffer pcm;

    long bufferOffset; // offset of last processed buffer
    long offset; // bufferOffset + offset of current buffer
    long lastOffset; // last offset update

    AudioRenderer(VorbisTrack track) {
        this.track = track;

        switch (track.channels) {
            case 1:
                this.format = AL_FORMAT_MONO16;
                break;
            case 2:
                this.format = AL_FORMAT_STEREO16;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported number of channels: " + track.channels);
        }

        device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) {
            throw new IllegalStateException("Failed to open the default device.");
        }

        context = alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) {
            throw new IllegalStateException("Failed to create an OpenAL context.");
        }

        this.pcm = memAllocShort(BUFFER_SIZE);

        alcSetThreadContext(context);

        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        AL.createCapabilities(deviceCaps);

        source = alGenSources();
        alSourcei(source, AL_DIRECT_CHANNELS_SOFT, AL_TRUE);

        buffers = memAllocInt(2);
        alGenBuffers(buffers);
    }

    @Override
    public void close() {
        alDeleteBuffers(buffers);
        alDeleteSources(source);

        memFree(buffers);
        memFree(pcm);

        alcSetThreadContext(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }

    private int stream(int buffer) {
        int samples = 0;

        while (samples < BUFFER_SIZE) {
            pcm.position(samples);
            int samplesPerChannel = track.getSamples(pcm);
            if (samplesPerChannel == 0) {
                break;
            }

            samples += samplesPerChannel * track.channels;
        }

        if (samples != 0) {
            pcm.position(0);
            pcm.limit(samples);
            alBufferData(buffer, format, pcm, track.sampleRate);
            pcm.limit(BUFFER_SIZE);
        }

        return samples;
    }

    boolean play() {
        for (int i = 0; i < buffers.limit(); i++) {
            if (stream(buffers.get(i)) == 0) {
                return false;
            }
        }

        alSourceQueueBuffers(source, buffers);
        alSourcePlay(source);

        return true;
    }

    boolean update(boolean loop) {
        int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);

        for (int i = 0; i < processed; i++) {
            bufferOffset += BUFFER_SIZE / track.channels;

            int buffer = alSourceUnqueueBuffers(source);

            if (stream(buffer) == 0) {
                boolean shouldExit = true;

                if (loop) {
                    track.rewind();
                    lastOffset = offset = bufferOffset = 0;
                    shouldExit = stream(buffer) == 0;
                }

                if (shouldExit) {
                    return false;
                }
            }
            alSourceQueueBuffers(source, buffer);
        }

        if (processed == 2) {
            alSourcePlay(source);
        }

        return true;
    }
}