package icu.takeneko.startup.chime.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.*;
import org.lwjgl.system.*;

import java.io.*;
import java.net.URL;
import java.nio.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.*;

import static java.lang.Math.*;
import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

class VorbisTrack implements AutoCloseable {
    private final ByteBuffer encodedAudio;

    private final long handle;

    final int channels;
    final int sampleRate;

    final int samplesLength;
    final float samplesSec;

    private final AtomicInteger sampleIndex;

    VorbisTrack(String filePath, AtomicInteger sampleIndex) {
        try {
            encodedAudio = ioResourceToByteBuffer(filePath, 256 * 1024);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (MemoryStack stack = stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            handle = stb_vorbis_open_memory(encodedAudio, error, null);
            if (handle == NULL) {
                throw new RuntimeException("Failed to open Ogg Vorbis file. Error: " + error.get(0));
            }

            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            print(info);
            this.channels = info.channels();
            this.sampleRate = info.sample_rate();
        }

        this.samplesLength = stb_vorbis_stream_length_in_samples(handle);
        this.samplesSec = stb_vorbis_stream_length_in_seconds(handle);

        this.sampleIndex = sampleIndex;
        sampleIndex.set(0);
    }

    @Override
    public void close() {
        stb_vorbis_close(handle);
    }

    void progressBy(int samples) {
        sampleIndex.set(sampleIndex.get() + samples);
    }

    void setSampleIndex(int sampleIndex) {
        this.sampleIndex.set(sampleIndex);
    }

    void rewind() {
        seek(0);
    }

    void skip(int direction) {
        seek(min(max(0, stb_vorbis_get_sample_offset(handle) + direction * sampleRate), samplesLength));
    }

    void skipTo(float offset0to1) {
        seek(round(samplesLength * offset0to1));
    }

    // called from audio thread
    synchronized int getSamples(ShortBuffer pcm) {
        return stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
    }

    // called from UI thread
    private synchronized void seek(int sampleIndex) {
        stb_vorbis_seek(handle, sampleIndex);
        setSampleIndex(sampleIndex);
    }

    private void print(STBVorbisInfo info) {
        System.out.println("stream length, samples: " + stb_vorbis_stream_length_in_samples(handle));
        System.out.println("stream length, seconds: " + stb_vorbis_stream_length_in_seconds(handle));

        System.out.println();

        stb_vorbis_get_info(handle, info);

        System.out.println("channels = " + info.channels());
        System.out.println("sampleRate = " + info.sample_rate());
        System.out.println("maxFrameSize = " + info.max_frame_size());
        System.out.println("setupMemoryRequired = " + info.setup_memory_required());
        System.out.println("setupTempMemoryRequired() = " + info.setup_temp_memory_required());
        System.out.println("tempMemoryRequired = " + info.temp_memory_required());
    }

    public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        ByteBuffer buffer;

        Path path = resource.startsWith("http") ? null : Paths.get(resource);
        if (path != null && Files.isReadable(path)) {
            try (SeekableByteChannel fc = Files.newByteChannel(path)) {
                buffer = createByteBuffer((int)fc.size() + 1);
                while (fc.read(buffer) != -1) {
                    ;
                }
            }
        } else {
            try (
                    InputStream source = resource.startsWith("http")
                            ? new URL(resource).openStream()
                            : VorbisTrack.class.getClassLoader().getResourceAsStream(resource);
                    ReadableByteChannel rbc = Channels.newChannel(source)
            ) {
                buffer = createByteBuffer(bufferSize);

                while (true) {
                    int bytes = rbc.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (buffer.remaining() == 0) {
                        buffer = resizeBuffer(buffer, buffer.capacity() * 3 / 2); // 50%
                    }
                }
            }
        }

        buffer.flip();
        return memSlice(buffer);
    }

    private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity) {
        ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }
}