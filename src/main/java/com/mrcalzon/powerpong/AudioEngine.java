package com.mrcalzon.powerpong;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tiny generated-sound OpenAL layer. Reflection keeps the game able to compile
 * with a stock JDK while Maven supplies LWJGL and its native runtime in the final JAR.
 */
final class AudioEngine implements AutoCloseable {
    enum Sound {
        PADDLE,
        WALL,
        SCORE,
        POWER_UP,
        POWER_SHOT,
        FREEZE
    }

    private final Map<Sound, Integer> buffers = new EnumMap<>(Sound.class);
    private final Map<Sound, Integer> sources = new EnumMap<>(Sound.class);
    private Class<?> al10;
    private Class<?> alc10;
    private Method alSourcePlay;
    private Method alSourceStop;
    private Method alDeleteSources;
    private Method alDeleteBuffers;
    private long device;
    private long context;
    private boolean available;

    AudioEngine(boolean enabled) {
        if (!enabled) {
            return;
        }
        try {
            initialize();
            available = true;
        } catch (Throwable failure) {
            System.err.println("PowerPong audio disabled: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            closeSilently();
        }
    }

    boolean isAvailable() {
        return available;
    }

    void play(Sound sound) {
        if (!available) {
            return;
        }
        Integer source = sources.get(sound);
        if (source == null) {
            return;
        }
        try {
            alSourceStop.invoke(null, source);
            alSourcePlay.invoke(null, source);
        } catch (ReflectiveOperationException ignored) {
            // Audio is non-essential; a failed source does not stop the match.
        }
    }

    private void initialize() throws Exception {
        Class<?> version = Class.forName("org.lwjgl.Version");
        Object lwjglVersion = version.getMethod("getVersion").invoke(null);
        System.out.println("PowerPong using LWJGL " + lwjglVersion + " OpenAL audio");

        alc10 = Class.forName("org.lwjgl.openal.ALC10");
        al10 = Class.forName("org.lwjgl.openal.AL10");
        Class<?> alc = Class.forName("org.lwjgl.openal.ALC");
        Class<?> al = Class.forName("org.lwjgl.openal.AL");
        Class<?> alcCapabilities = Class.forName("org.lwjgl.openal.ALCCapabilities");

        Method alcOpenDevice = alc10.getMethod("alcOpenDevice", ByteBuffer.class);
        Method alcCreateContext = alc10.getMethod("alcCreateContext", long.class, IntBuffer.class);
        Method alcMakeContextCurrent = alc10.getMethod("alcMakeContextCurrent", long.class);

        device = (long) alcOpenDevice.invoke(null, new Object[]{null});
        if (device == 0L) {
            throw new IllegalStateException("OpenAL could not open the default audio device");
        }
        context = (long) alcCreateContext.invoke(null, device, null);
        if (context == 0L || !(boolean) alcMakeContextCurrent.invoke(null, context)) {
            throw new IllegalStateException("OpenAL could not create an audio context");
        }

        Object capabilities = alc.getMethod("createCapabilities", long.class).invoke(null, device);
        al.getMethod("createCapabilities", alcCapabilities).invoke(null, capabilities);

        Method alGenBuffers = al10.getMethod("alGenBuffers");
        Method alGenSources = al10.getMethod("alGenSources");
        Method alBufferData = al10.getMethod("alBufferData", int.class, int.class, ShortBuffer.class, int.class);
        Method alSourcei = al10.getMethod("alSourcei", int.class, int.class, int.class);
        Method alSourcef = al10.getMethod("alSourcef", int.class, int.class, float.class);
        alSourcePlay = al10.getMethod("alSourcePlay", int.class);
        alSourceStop = al10.getMethod("alSourceStop", int.class);
        alDeleteSources = al10.getMethod("alDeleteSources", int.class);
        alDeleteBuffers = al10.getMethod("alDeleteBuffers", int.class);

        int formatMono16 = intConstant("AL_FORMAT_MONO16");
        int alBuffer = intConstant("AL_BUFFER");
        int alGain = intConstant("AL_GAIN");

        for (Sound sound : Sound.values()) {
            Tone tone = toneFor(sound);
            int buffer = (int) alGenBuffers.invoke(null);
            int source = (int) alGenSources.invoke(null);
            ShortBuffer pcm = synthesize(tone);
            alBufferData.invoke(null, buffer, formatMono16, pcm, tone.sampleRate);
            alSourcei.invoke(null, source, alBuffer, buffer);
            alSourcef.invoke(null, source, alGain, tone.gain);
            buffers.put(sound, buffer);
            sources.put(sound, source);
        }
    }

    private int intConstant(String name) throws ReflectiveOperationException {
        Field field = al10.getField(name);
        return field.getInt(null);
    }

    private static Tone toneFor(Sound sound) {
        return switch (sound) {
            case PADDLE -> new Tone(510, 0.055, 0.34f, Wave.SQUARE, 44_100);
            case WALL -> new Tone(290, 0.045, 0.19f, Wave.SINE, 44_100);
            case SCORE -> new Tone(125, 0.24, 0.33f, Wave.SAW, 44_100);
            case POWER_UP -> new Tone(860, 0.16, 0.30f, Wave.SINE, 44_100);
            case POWER_SHOT -> new Tone(180, 0.20, 0.36f, Wave.SAW, 44_100);
            case FREEZE -> new Tone(1050, 0.28, 0.24f, Wave.SINE, 44_100);
        };
    }

    private static ShortBuffer synthesize(Tone tone) {
        int sampleCount = Math.max(1, (int) (tone.seconds * tone.sampleRate));
        ByteBuffer bytes = ByteBuffer.allocateDirect(sampleCount * Short.BYTES).order(ByteOrder.nativeOrder());
        ShortBuffer samples = bytes.asShortBuffer();
        for (int i = 0; i < sampleCount; i++) {
            double t = i / (double) tone.sampleRate;
            double phase = 2.0 * Math.PI * tone.frequency * t;
            double wave = switch (tone.wave) {
                case SINE -> Math.sin(phase);
                case SQUARE -> Math.sin(phase) >= 0 ? 1.0 : -1.0;
                case SAW -> 2.0 * ((tone.frequency * t) - Math.floor(0.5 + tone.frequency * t));
            };
            double envelope = Math.pow(1.0 - i / (double) sampleCount, 2.0);
            samples.put((short) (wave * envelope * Short.MAX_VALUE * 0.82));
        }
        samples.flip();
        return samples;
    }

    @Override
    public void close() {
        if (!available && device == 0L && context == 0L) {
            return;
        }
        closeSilently();
        available = false;
    }

    private void closeSilently() {
        try {
            if (alDeleteSources != null) {
                for (int source : sources.values()) {
                    alDeleteSources.invoke(null, source);
                }
            }
            if (alDeleteBuffers != null) {
                for (int buffer : buffers.values()) {
                    alDeleteBuffers.invoke(null, buffer);
                }
            }
        } catch (Throwable ignored) {
            // Best-effort cleanup.
        }
        try {
            if (alc10 != null && context != 0L) {
                alc10.getMethod("alcDestroyContext", long.class).invoke(null, context);
            }
            if (alc10 != null && device != 0L) {
                alc10.getMethod("alcCloseDevice", long.class).invoke(null, device);
            }
        } catch (Throwable ignored) {
            // Best-effort cleanup.
        }
        sources.clear();
        buffers.clear();
        context = 0L;
        device = 0L;
    }

    private enum Wave {
        SINE,
        SQUARE,
        SAW
    }

    private record Tone(double frequency, double seconds, float gain, Wave wave, int sampleRate) {
    }
}
