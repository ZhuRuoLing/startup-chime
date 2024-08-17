package icu.takeneko.startup.chime.sound;

import icu.takeneko.startup.chime.ClientMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class AudioThread extends Thread{
    private final AtomicInteger sampleIndex = new AtomicInteger();
    private final CountDownLatch preparationLatch = new CountDownLatch(1);
    private final Logger logger = LoggerFactory.getLogger("AudioThread");

    public AudioThread(){
        this.setName("ChimeThread");
        this.setDaemon(true);
    }

    @Override
    public void run() {
        VorbisTrack track = new VorbisTrack(ClientMod.CHIME_PATH.toString(), sampleIndex);
        AudioRenderer renderer = new AudioRenderer(track);
        preparationLatch.countDown();
        renderer.play();
        while (true) {
            if (!renderer.update(false)){
                break;
            }
        }
        track.close();
        renderer.close();
    }

    public CountDownLatch getPreparationLatch() {
        return preparationLatch;
    }
}
