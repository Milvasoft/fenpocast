/*
 * Fenpo Cast: JNI bridge between UxPlay's receiver core (raop) and NativeReceiver.kt.
 *
 * The raop callbacks below follow uxplay.cpp, with GStreamer replaced by calls into Kotlin:
 * video goes to MediaCodec, audio to MediaCodec or (ALAC) to the decoder in alac_decoder.cpp,
 * and HLS video to Media3. uxplay.cpp's GLib main loop becomes a small control thread that
 * restarts the HTTP server and drops stale connections the same way.
 *
 * This file is part of Fenpo Cast and is licensed under the GNU General Public License v3.0.
 */

#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "dnssd.h"
#include "logger.h"
#include "raop.h"
#include "stream.h"

#include "receiver.h"

#define LOG_TAG "FenpoCastNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Seconds without the client's /feedback heartbeat (sent every 2 s) before the session is reset. */
#define MISSED_FEEDBACK_LIMIT 15
#define ALAC_MAX_FRAMES 4096

/* ---------------------------------------------------------------------------------------------
 * JNI plumbing
 * ------------------------------------------------------------------------------------------- */

static JavaVM *g_vm;
static jobject g_bridge;
static pthread_key_t g_env_key;

static struct {
    jmethodID register_service;
    jmethodID unregister_service;
    jmethodID on_client_request;
    jmethodID on_connections_changed;
    jmethodID on_connection_lost;
    jmethodID on_video_codec;
    jmethodID on_video_size;
    jmethodID on_video_frame;
    jmethodID on_video_paused;
    jmethodID on_video_flush;
    jmethodID on_video_reset;
    jmethodID on_audio_format;
    jmethodID on_audio_frame;
    jmethodID on_audio_flush;
    jmethodID on_audio_stop;
    jmethodID on_volume;
    jmethodID on_metadata;
    jmethodID on_cover_art;
    jmethodID on_cover_art_cleared;
    jmethodID on_progress;
    jmethodID on_pin;
    jmethodID on_hls_play;
    jmethodID on_hls_seek;
    jmethodID on_hls_rate;
    jmethodID on_hls_stop;
    jmethodID on_hls_pause;
} g_methods;

static void detach_thread(void *unused) {
    (*g_vm)->DetachCurrentThread(g_vm);
}

/* Returns a JNIEnv for the calling thread, attaching raop's worker threads on first use. */
static JNIEnv *get_env(void) {
    JNIEnv *env = NULL;
    if ((*g_vm)->GetEnv(g_vm, (void **) &env, JNI_VERSION_1_6) == JNI_OK) {
        return env;
    }
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) {
        return NULL;
    }
    pthread_setspecific(g_env_key, env);
    return env;
}

static void clear_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

static jbyteArray new_byte_array(JNIEnv *env, const void *data, int length) {
    jbyteArray array = (*env)->NewByteArray(env, length);
    if (array) {
        (*env)->SetByteArrayRegion(env, array, 0, length, (const jbyte *) data);
    }
    return array;
}

static jstring new_string(JNIEnv *env, const char *text) {
    return (*env)->NewStringUTF(env, text ? text : "");
}

#define CALL_VOID(method, ...)                                                 \
    do {                                                                       \
        JNIEnv *env_ = get_env();                                              \
        if (env_ && g_bridge) {                                                \
            (*env_)->CallVoidMethod(env_, g_bridge, g_methods.method, ##__VA_ARGS__); \
            clear_exception(env_);                                             \
        }                                                                      \
    } while (0)

/* ---------------------------------------------------------------------------------------------
 * Receiver state
 * ------------------------------------------------------------------------------------------- */

static raop_t *g_raop;
static dnssd_t *g_dnssd;
static unsigned short g_port;
static bool g_pin_required;

static char g_languages[64];

static atomic_int g_open_connections;
static atomic_int g_missed_feedback;
static double g_client_volume_db = 0.0;

static pthread_mutex_t g_audio_lock = PTHREAD_MUTEX_INITIALIZER;
static void *g_alac;
static int g_audio_ct;
static int16_t g_pcm[ALAC_MAX_FRAMES * 2];

/* HLS playback state, written by Kotlin, read by raop's /playback-info handler. */
static pthread_mutex_t g_playback_lock = PTHREAD_MUTEX_INITIALIZER;
static struct {
    double duration;
    double position;
    float rate;
    bool buffer_empty;
    bool buffer_full;
    bool ended;
} g_playback;

/* Control thread: the part of uxplay.cpp's main loop that touches raop. */
static pthread_t g_control_thread;
static pthread_mutex_t g_control_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_control_cond = PTHREAD_COND_INITIALIZER;
static bool g_control_running;
static bool g_reset_loop;
static bool g_relaunch;
static bool g_reset_httpd;
static bool g_preserve_connections;
static bool g_hls_eos;

static void request_reset(bool reset_httpd, bool preserve_connections) {
    pthread_mutex_lock(&g_control_lock);
    g_relaunch = true;
    g_reset_httpd = g_reset_httpd || reset_httpd;
    g_preserve_connections = preserve_connections;
    g_reset_loop = true;
    pthread_cond_signal(&g_control_cond);
    pthread_mutex_unlock(&g_control_lock);
}

static void *control_loop(void *unused) {
    pthread_mutex_lock(&g_control_lock);
    while (g_control_running) {
        struct timespec deadline;
        clock_gettime(CLOCK_REALTIME, &deadline);
        deadline.tv_sec += 1;
        int wait = pthread_cond_timedwait(&g_control_cond, &g_control_lock, &deadline);
        if (!g_control_running) {
            break;
        }

        if (wait != 0 && atomic_load(&g_open_connections) > 0) {
            /* One tick per second, like uxplay.cpp's feedback_callback. */
            int missed = atomic_fetch_add(&g_missed_feedback, 1) + 1;
            if (missed > MISSED_FEEDBACK_LIMIT) {
                LOGI("no feedback from the client for %d seconds, resetting", missed);
                atomic_store(&g_missed_feedback, 0);
                g_relaunch = true;
                g_reset_httpd = true;
                g_preserve_connections = false;
                g_reset_loop = true;
                pthread_mutex_unlock(&g_control_lock);
                CALL_VOID(on_connection_lost, 1);
                pthread_mutex_lock(&g_control_lock);
            }
        } else if (wait != 0) {
            atomic_store(&g_missed_feedback, 0);
        }

        if (g_hls_eos) {
            g_hls_eos = false;
            pthread_mutex_unlock(&g_control_lock);
            raop_handle_eos(g_raop);
            pthread_mutex_lock(&g_control_lock);
        }

        if (!g_reset_loop) {
            continue;
        }
        bool relaunch = g_relaunch;
        bool reset_httpd = g_reset_httpd;
        bool preserve = g_preserve_connections;
        g_reset_loop = false;
        g_relaunch = false;
        g_reset_httpd = false;
        g_preserve_connections = false;
        pthread_mutex_unlock(&g_control_lock);

        if (relaunch) {
            if (reset_httpd) {
                raop_stop_httpd(g_raop);
            }
            CALL_VOID(on_audio_stop);
            if (!preserve) {
                raop_remove_known_connections(g_raop);
            }
            if (reset_httpd) {
                unsigned short port = raop_get_port(g_raop);
                raop_start_httpd(g_raop, &port);
                raop_set_port(g_raop, port);
            }
        }
        pthread_mutex_lock(&g_control_lock);
    }
    pthread_mutex_unlock(&g_control_lock);
    return NULL;
}

/* ---------------------------------------------------------------------------------------------
 * raop callbacks (see uxplay.cpp for the GStreamer originals)
 * ------------------------------------------------------------------------------------------- */

static void cb_log(void *cls, int level, const char *msg) {
    int priority;
    switch (level) {
    case LOGGER_ERR:
        priority = ANDROID_LOG_ERROR;
        break;
    case LOGGER_WARNING:
        priority = ANDROID_LOG_WARN;
        break;
    case LOGGER_INFO:
    case LOGGER_NOTICE:
        priority = ANDROID_LOG_INFO;
        break;
    default:
        priority = ANDROID_LOG_DEBUG;
        break;
    }
    __android_log_write(priority, "UxPlay", msg);
}

static void cb_conn_init(void *cls) {
    int count = atomic_fetch_add(&g_open_connections, 1) + 1;
    CALL_VOID(on_connections_changed, count);
}

static void cb_conn_destroy(void *cls) {
    int count = atomic_fetch_sub(&g_open_connections, 1) - 1;
    if (count < 0) {
        atomic_store(&g_open_connections, 0);
        count = 0;
    }
    if (count == 0) {
        CALL_VOID(on_audio_stop);
    }
    CALL_VOID(on_connections_changed, count);
}

static void cb_conn_feedback(void *cls) {
    atomic_store(&g_missed_feedback, 0);
}

static void cb_conn_reset(void *cls, int reason) {
    LOGI("connection reset, reason %d", reason);
    CALL_VOID(on_connection_lost, reason);
    request_reset(true, false);
}

static void cb_video_reset(void *cls, reset_type_t type) {
    CALL_VOID(on_video_reset, (jint) type);
    switch (type) {
    case RESET_TYPE_NOHOLD:
        raop_destroy_airplay_video(g_raop, -1);
        request_reset(false, false);
        break;
    case RESET_TYPE_HLS_EOS:
        request_reset(false, false);
        break;
    case RESET_TYPE_RTP_TO_HLS_TEARDOWN:
        request_reset(false, true);
        break;
    case RESET_TYPE_RTP_SHUTDOWN:
        request_reset(false, false);
        break;
    case RESET_TYPE_HLS_SHUTDOWN:
        raop_destroy_airplay_video(g_raop, -1);
        raop_remove_hls_connections(g_raop);
        request_reset(false, true);
        break;
    case RESET_TYPE_ON_VIDEO_PLAY:
    default:
        break;
    }
}

static void cb_report_client_request(void *cls, char *deviceid, char *model, char *name, bool *admit) {
    LOGI("connection request from %s (%s)", name ? name : "?", model ? model : "?");
    *admit = true;
    JNIEnv *env = get_env();
    if (env && g_bridge) {
        jstring j_id = new_string(env, deviceid);
        jstring j_model = new_string(env, model);
        jstring j_name = new_string(env, name);
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_client_request, j_id, j_model, j_name);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, j_id);
        (*env)->DeleteLocalRef(env, j_model);
        (*env)->DeleteLocalRef(env, j_name);
    }
}

static void cb_display_pin(void *cls, char *pin) {
    JNIEnv *env = get_env();
    if (env && g_bridge) {
        jstring j_pin = new_string(env, pin);
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_pin, j_pin);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, j_pin);
    }
}

static const char *cb_passwd(void *cls, int *len) {
    /* -1: a random four-digit code for every connection, shown on screen through cb_display_pin. */
    *len = g_pin_required ? -1 : 0;
    return NULL;
}

static bool cb_check_register(void *cls, const char *pk) {
    return true;
}

static int cb_video_set_codec(void *cls, video_codec_t codec) {
    JNIEnv *env = get_env();
    if (!env || !g_bridge) {
        return -1;
    }
    jboolean ok = (*env)->CallBooleanMethod(env, g_bridge, g_methods.on_video_codec,
                                            (jboolean) (codec == VIDEO_CODEC_H265));
    clear_exception(env);
    return ok ? 0 : -1;
}

static void cb_video_report_size(void *cls, float *width_source, float *height_source, float *width,
                                 float *height) {
    CALL_VOID(on_video_size, (jfloat) *width, (jfloat) *height);
}

static void cb_video_process(void *cls, raop_ntp_t *ntp, video_decode_struct *data) {
    /* raop_rtp_mirror marks a packet it could not decrypt by breaking the first start code. */
    if (data->data_len < 5 || data->data[0] != 0 || data->data[1] != 0 || data->data[2] != 0 ||
        data->data[3] != 1) {
        return;
    }
    JNIEnv *env = get_env();
    if (!env || !g_bridge) {
        return;
    }
    jbyteArray frame = new_byte_array(env, data->data, data->data_len);
    if (frame) {
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_video_frame, frame, (jboolean) data->is_h265);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, frame);
    }
}

static void cb_video_pause(void *cls) {
    CALL_VOID(on_video_paused, JNI_TRUE);
}

static void cb_video_resume(void *cls) {
    CALL_VOID(on_video_paused, JNI_FALSE);
}

static void cb_video_flush(void *cls) {
    CALL_VOID(on_video_flush);
}

static void cb_audio_get_format(void *cls, unsigned char *ct, unsigned short *spf, bool *using_screen,
                                bool *is_media, uint64_t *audio_format) {
    LOGI("audio format ct=%d spf=%d screen=%d media=%d", *ct, *spf, *using_screen, *is_media);
    pthread_mutex_lock(&g_audio_lock);
    g_audio_ct = *ct;
    if (g_audio_ct == 2 && !g_alac) {
        g_alac = alac_decoder_create();
        if (!g_alac) {
            LOGE("could not create the ALAC decoder");
        }
    }
    pthread_mutex_unlock(&g_audio_lock);
    /* ALAC reaches Kotlin already decoded, as 16-bit PCM (ct = 1). */
    CALL_VOID(on_audio_format, (jint) (*ct == 2 ? 1 : *ct));
}

static void cb_audio_process(void *cls, raop_ntp_t *ntp, audio_decode_struct *data) {
    if (data->data_len <= 0) {
        return;
    }
    JNIEnv *env = get_env();
    if (!env || !g_bridge) {
        return;
    }
    jbyteArray frame = NULL;
    int type = data->ct;
    if (data->ct == 2) {
        if (data->data[0] != 0x20) {
            return;
        }
        pthread_mutex_lock(&g_audio_lock);
        int frames = g_alac ? alac_decoder_decode(g_alac, data->data, data->data_len, g_pcm, ALAC_MAX_FRAMES) : -1;
        if (frames > 0) {
            frame = new_byte_array(env, g_pcm, frames * 2 * (int) sizeof(int16_t));
        }
        pthread_mutex_unlock(&g_audio_lock);
        type = 1;
    } else if (data->ct == 8) {
        /* AAC-ELD frames start with 0x8c-0x8e (0x80-0x82 on iOS 9 and 10). */
        unsigned char first = data->data[0];
        if (!((first >= 0x8c && first <= 0x8e) || (first >= 0x80 && first <= 0x82))) {
            return;
        }
        frame = new_byte_array(env, data->data, data->data_len);
    } else {
        frame = new_byte_array(env, data->data, data->data_len);
    }
    if (frame) {
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_audio_frame, (jint) type, frame);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, frame);
    }
}

static void cb_audio_flush(void *cls) {
    CALL_VOID(on_audio_flush);
}

static double cb_audio_set_client_volume(void *cls) {
    return g_client_volume_db;
}

static void cb_audio_set_volume(void *cls, float volume) {
    g_client_volume_db = volume;
    CALL_VOID(on_volume, (jfloat) volume);
}

static void cb_audio_set_metadata(void *cls, const void *buffer, int buflen) {
    JNIEnv *env = get_env();
    if (!env || !g_bridge || !buffer || buflen <= 0) {
        return;
    }
    jbyteArray data = new_byte_array(env, buffer, buflen);
    if (data) {
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_metadata, data);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, data);
    }
}

static void cb_audio_set_coverart(void *cls, const void *buffer, int buflen) {
    JNIEnv *env = get_env();
    if (!env || !g_bridge) {
        return;
    }
    if (!buffer || buflen <= 0) {
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_cover_art_cleared);
        clear_exception(env);
        return;
    }
    jbyteArray data = new_byte_array(env, buffer, buflen);
    if (data) {
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_cover_art, data);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, data);
    }
}

static void cb_audio_stop_coverart_rendering(void *cls) {
    CALL_VOID(on_cover_art_cleared);
}

static void cb_audio_set_progress(void *cls, uint32_t *start, uint32_t *curr, uint32_t *end) {
    CALL_VOID(on_progress, (jlong) *start, (jlong) *curr, (jlong) *end);
}

static void cb_on_video_play(void *cls, const char *location, const float start_position) {
    LOGI("HLS video: %s from %.1f s", location, start_position);
    pthread_mutex_lock(&g_playback_lock);
    memset(&g_playback, 0, sizeof(g_playback));
    pthread_mutex_unlock(&g_playback_lock);

    JNIEnv *env = get_env();
    if (env && g_bridge) {
        jstring url = new_string(env, location);
        (*env)->CallVoidMethod(env, g_bridge, g_methods.on_hls_play, url, (jfloat) start_position);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, url);
    }
    cb_video_reset(cls, RESET_TYPE_ON_VIDEO_PLAY);
}

static void cb_on_video_scrub(void *cls, const float position) {
    CALL_VOID(on_hls_seek, (jfloat) position);
}

static void cb_on_video_rate(void *cls, const float rate) {
    CALL_VOID(on_hls_rate, (jfloat) rate);
}

static void cb_on_video_stop(void *cls) {
    CALL_VOID(on_hls_stop);
}

static float cb_on_video_playlist_remove(void *cls) {
    CALL_VOID(on_hls_pause);
    pthread_mutex_lock(&g_playback_lock);
    float position = (float) g_playback.position;
    pthread_mutex_unlock(&g_playback_lock);
    return position;
}

static void cb_on_video_acquire_playback_info(void *cls, playback_info_t *info) {
    pthread_mutex_lock(&g_playback_lock);
    info->duration = g_playback.duration;
    info->position = g_playback.position;
    info->rate = g_playback.rate;
    info->playback_buffer_empty = g_playback.buffer_empty;
    info->playback_buffer_full = g_playback.buffer_full;
    bool ended = g_playback.ended;
    pthread_mutex_unlock(&g_playback_lock);
    info->ready_to_play = true;
    info->playback_likely_to_keep_up = true;
    if (ended) {
        info->position = -1.0;
        info->duration = -1.0;
    }
}

/* ---------------------------------------------------------------------------------------------
 * NsdManager hooks used by dnssd_android.c
 * ------------------------------------------------------------------------------------------- */

int receiver_register_service(const char *type, const char *name, unsigned short port, const char **keys,
                              const char **values, int count) {
    JNIEnv *env = get_env();
    if (!env || !g_bridge) {
        return -1;
    }
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray j_keys = (*env)->NewObjectArray(env, count, string_class, NULL);
    jobjectArray j_values = (*env)->NewObjectArray(env, count, string_class, NULL);
    for (int i = 0; i < count; i++) {
        jstring key = new_string(env, keys[i]);
        jstring value = new_string(env, values[i]);
        (*env)->SetObjectArrayElement(env, j_keys, i, key);
        (*env)->SetObjectArrayElement(env, j_values, i, value);
        (*env)->DeleteLocalRef(env, key);
        (*env)->DeleteLocalRef(env, value);
    }
    jstring j_type = new_string(env, type);
    jstring j_name = new_string(env, name);
    jboolean ok = (*env)->CallBooleanMethod(env, g_bridge, g_methods.register_service, j_type, j_name, (jint) port,
                                            j_keys, j_values);
    clear_exception(env);
    (*env)->DeleteLocalRef(env, j_type);
    (*env)->DeleteLocalRef(env, j_name);
    (*env)->DeleteLocalRef(env, j_keys);
    (*env)->DeleteLocalRef(env, j_values);
    (*env)->DeleteLocalRef(env, string_class);
    return ok ? 0 : -1;
}

void receiver_unregister_service(const char *type) {
    JNIEnv *env = get_env();
    if (!env || !g_bridge) {
        return;
    }
    jstring j_type = new_string(env, type);
    (*env)->CallVoidMethod(env, g_bridge, g_methods.unregister_service, j_type);
    clear_exception(env);
    (*env)->DeleteLocalRef(env, j_type);
}

/* ---------------------------------------------------------------------------------------------
 * Start and stop
 * ------------------------------------------------------------------------------------------- */

static void set_features(dnssd_t *dnssd, bool hls, bool h265) {
    /* The feature set uxplay.cpp's start_dnssd() advertises, bit by bit. */
    static const int bits[32] = {
        0, 1, 1, 0,  0, 1, 1, 1,  0, 1, 1, 1,  1, 1, 1, 1,
        1, 1, 1, 1,  1, 1, 1, 0,  0, 1, 0, 1,  1, 0, 1, 0,
    };
    for (int i = 0; i < 32; i++) {
        dnssd_set_airplay_features(dnssd, i, bits[i]);
    }
    /* HLS video (bits 0 and 4), H.265 screen codec (bit 42), and the AirPlay 2 client pairing
       protocol instead of legacy pairing (bit 27 off). */
    dnssd_set_airplay_features(dnssd, 0, hls);
    dnssd_set_airplay_features(dnssd, 4, hls);
    dnssd_set_airplay_features(dnssd, 42, h265);
    dnssd_set_airplay_features(dnssd, 27, 0);
}

static int parse_device_id(const char *device_id, char hw_addr[6]) {
    unsigned int bytes[6];
    if (sscanf(device_id, "%2x:%2x:%2x:%2x:%2x:%2x", &bytes[0], &bytes[1], &bytes[2], &bytes[3], &bytes[4],
               &bytes[5]) != 6) {
        return -1;
    }
    for (int i = 0; i < 6; i++) {
        hw_addr[i] = (char) bytes[i];
    }
    return 0;
}

static void stop_receiver(void) {
    if (g_control_running) {
        pthread_mutex_lock(&g_control_lock);
        g_control_running = false;
        pthread_cond_signal(&g_control_cond);
        pthread_mutex_unlock(&g_control_lock);
        pthread_join(g_control_thread, NULL);
    }
    if (g_dnssd) {
        dnssd_unregister_raop(g_dnssd);
        dnssd_unregister_airplay(g_dnssd);
    }
    if (g_raop) {
        raop_destroy(g_raop);
        g_raop = NULL;
    }
    if (g_dnssd) {
        dnssd_destroy(g_dnssd);
        g_dnssd = NULL;
    }
    pthread_mutex_lock(&g_audio_lock);
    if (g_alac) {
        alac_decoder_destroy(g_alac);
        g_alac = NULL;
    }
    pthread_mutex_unlock(&g_audio_lock);
    atomic_store(&g_open_connections, 0);
    atomic_store(&g_missed_feedback, 0);
    g_port = 0;
}

static bool cache_methods(JNIEnv *env, jobject bridge) {
    jclass cls = (*env)->GetObjectClass(env, bridge);
#define METHOD(field, name, signature)                                         \
    g_methods.field = (*env)->GetMethodID(env, cls, name, signature);          \
    if (!g_methods.field) return false;
    METHOD(register_service, "registerService", "(Ljava/lang/String;Ljava/lang/String;I[Ljava/lang/String;[Ljava/lang/String;)Z")
    METHOD(unregister_service, "unregisterService", "(Ljava/lang/String;)V")
    METHOD(on_client_request, "onClientRequest", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V")
    METHOD(on_connections_changed, "onConnectionsChanged", "(I)V")
    METHOD(on_connection_lost, "onConnectionLost", "(I)V")
    METHOD(on_video_codec, "onVideoCodec", "(Z)Z")
    METHOD(on_video_size, "onVideoSize", "(FF)V")
    METHOD(on_video_frame, "onVideoFrame", "([BZ)V")
    METHOD(on_video_paused, "onVideoPaused", "(Z)V")
    METHOD(on_video_flush, "onVideoFlush", "()V")
    METHOD(on_video_reset, "onVideoReset", "(I)V")
    METHOD(on_audio_format, "onAudioFormat", "(I)V")
    METHOD(on_audio_frame, "onAudioFrame", "(I[B)V")
    METHOD(on_audio_flush, "onAudioFlush", "()V")
    METHOD(on_audio_stop, "onAudioStop", "()V")
    METHOD(on_volume, "onVolume", "(F)V")
    METHOD(on_metadata, "onMetadata", "([B)V")
    METHOD(on_cover_art, "onCoverArt", "([B)V")
    METHOD(on_cover_art_cleared, "onCoverArtCleared", "()V")
    METHOD(on_progress, "onProgress", "(JJJ)V")
    METHOD(on_pin, "onPin", "(Ljava/lang/String;)V")
    METHOD(on_hls_play, "onHlsPlay", "(Ljava/lang/String;F)V")
    METHOD(on_hls_seek, "onHlsSeek", "(F)V")
    METHOD(on_hls_rate, "onHlsRate", "(F)V")
    METHOD(on_hls_stop, "onHlsStop", "()V")
    METHOD(on_hls_pause, "onHlsPause", "()V")
#undef METHOD
    (*env)->DeleteLocalRef(env, cls);
    return true;
}

JNIEXPORT jint JNICALL
Java_com_fenpo_cast_receiver_NativeReceiver_nativeStart(JNIEnv *env, jobject thiz, jstring j_name,
                                                         jstring j_device_id, jstring j_key_file, jint width,
                                                         jint height, jint refresh_rate, jint max_fps,
                                                         jboolean h265, jboolean hls, jboolean pin_required,
                                                         jstring j_languages) {
    if (g_raop) {
        return g_port;
    }
    if (!g_bridge) {
        g_bridge = (*env)->NewGlobalRef(env, thiz);
    }
    if (!cache_methods(env, thiz)) {
        clear_exception(env);
        return -1;
    }

    const char *name = (*env)->GetStringUTFChars(env, j_name, NULL);
    const char *device_id = (*env)->GetStringUTFChars(env, j_device_id, NULL);
    const char *key_file = (*env)->GetStringUTFChars(env, j_key_file, NULL);
    const char *languages = (*env)->GetStringUTFChars(env, j_languages, NULL);
    snprintf(g_languages, sizeof(g_languages), "%s", languages);
    g_pin_required = pin_required;
    g_client_volume_db = 0.0;
    int result = 0;

    char hw_addr[6];
    if (parse_device_id(device_id, hw_addr)) {
        result = -2;
        goto done;
    }

    int dnssd_error = 0;
    g_dnssd = dnssd_init(name, (int) strlen(name), hw_addr, 6, pin_required ? 3 : 0, &dnssd_error);
    if (!g_dnssd || dnssd_error) {
        LOGE("dnssd_init failed: %d", dnssd_error);
        result = -3;
        goto done;
    }
    set_features(g_dnssd, hls, h265);

    raop_callbacks_t callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.conn_init = cb_conn_init;
    callbacks.conn_destroy = cb_conn_destroy;
    callbacks.conn_reset = cb_conn_reset;
    callbacks.conn_feedback = cb_conn_feedback;
    callbacks.audio_process = cb_audio_process;
    callbacks.video_process = cb_video_process;
    callbacks.audio_flush = cb_audio_flush;
    callbacks.video_flush = cb_video_flush;
    callbacks.video_pause = cb_video_pause;
    callbacks.video_resume = cb_video_resume;
    callbacks.audio_set_client_volume = cb_audio_set_client_volume;
    callbacks.audio_set_volume = cb_audio_set_volume;
    callbacks.audio_get_format = cb_audio_get_format;
    callbacks.video_report_size = cb_video_report_size;
    callbacks.audio_set_metadata = cb_audio_set_metadata;
    callbacks.audio_set_coverart = cb_audio_set_coverart;
    callbacks.audio_stop_coverart_rendering = cb_audio_stop_coverart_rendering;
    callbacks.audio_set_progress = cb_audio_set_progress;
    callbacks.report_client_request = cb_report_client_request;
    callbacks.display_pin = cb_display_pin;
    callbacks.check_register = cb_check_register;
    callbacks.passwd = cb_passwd;
    callbacks.video_reset = cb_video_reset;
    callbacks.video_set_codec = cb_video_set_codec;
    callbacks.on_video_play = cb_on_video_play;
    callbacks.on_video_scrub = cb_on_video_scrub;
    callbacks.on_video_rate = cb_on_video_rate;
    callbacks.on_video_stop = cb_on_video_stop;
    callbacks.on_video_playlist_remove = cb_on_video_playlist_remove;
    callbacks.on_video_acquire_playback_info = cb_on_video_acquire_playback_info;

    g_raop = raop_init(&callbacks);
    if (!g_raop) {
        result = -4;
        goto done;
    }
    raop_set_log_callback(g_raop, cb_log, NULL);
    raop_set_log_level(g_raop, LOGGER_INFO);
    /* nohold = 1: a second iPhone may take over the screen from the first. */
    if (raop_init2(g_raop, 1, device_id, key_file)) {
        free(g_raop);
        g_raop = NULL;
        result = -5;
        goto done;
    }

    raop_set_plist(g_raop, "width", width);
    raop_set_plist(g_raop, "height", height);
    raop_set_plist(g_raop, "refreshRate", refresh_rate);
    raop_set_plist(g_raop, "maxFPS", max_fps);
    if (hls) {
        raop_set_plist(g_raop, "hls", 1);
        raop_set_lang(g_raop, g_languages, g_languages, g_languages);
    }

    unsigned short tcp[3] = {0, 0, 0};
    unsigned short udp[3] = {0, 0, 0};
    raop_set_tcp_ports(g_raop, tcp);
    raop_set_udp_ports(g_raop, udp);

    unsigned short port = raop_get_port(g_raop);
    if (raop_start_httpd(g_raop, &port) < 0) {
        result = -6;
        goto done;
    }
    raop_set_port(g_raop, port);
    raop_set_dnssd(g_raop, g_dnssd);
    g_port = port;

    if (dnssd_register_raop(g_dnssd, port) || dnssd_register_airplay(g_dnssd, port)) {
        result = -7;
        goto done;
    }

    g_control_running = true;
    g_reset_loop = false;
    g_hls_eos = false;
    if (pthread_create(&g_control_thread, NULL, control_loop, NULL)) {
        g_control_running = false;
        result = -8;
        goto done;
    }
    result = port;

done:
    (*env)->ReleaseStringUTFChars(env, j_name, name);
    (*env)->ReleaseStringUTFChars(env, j_device_id, device_id);
    (*env)->ReleaseStringUTFChars(env, j_key_file, key_file);
    (*env)->ReleaseStringUTFChars(env, j_languages, languages);
    if (result < 0) {
        LOGE("receiver start failed: %d", result);
        stop_receiver();
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_fenpo_cast_receiver_NativeReceiver_nativeStop(JNIEnv *env, jobject thiz) {
    stop_receiver();
    if (g_bridge) {
        (*env)->DeleteGlobalRef(env, g_bridge);
        g_bridge = NULL;
    }
}

JNIEXPORT void JNICALL
Java_com_fenpo_cast_receiver_NativeReceiver_nativeDisconnect(JNIEnv *env, jobject thiz) {
    if (g_raop) {
        raop_destroy_airplay_video(g_raop, -1);
        request_reset(false, false);
    }
}

JNIEXPORT void JNICALL
Java_com_fenpo_cast_receiver_NativeReceiver_nativeSetPlaybackInfo(JNIEnv *env, jobject thiz, jdouble duration,
                                                                   jdouble position, jfloat rate,
                                                                   jboolean buffer_empty, jboolean buffer_full,
                                                                   jboolean ended) {
    pthread_mutex_lock(&g_playback_lock);
    g_playback.duration = duration;
    g_playback.position = position;
    g_playback.rate = rate;
    g_playback.buffer_empty = buffer_empty;
    g_playback.buffer_full = buffer_full;
    g_playback.ended = ended;
    pthread_mutex_unlock(&g_playback_lock);
}

JNIEXPORT void JNICALL
Java_com_fenpo_cast_receiver_NativeReceiver_nativeHlsEnded(JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&g_control_lock);
    if (g_control_running) {
        g_hls_eos = true;
        pthread_cond_signal(&g_control_cond);
    }
    pthread_mutex_unlock(&g_control_lock);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    pthread_key_create(&g_env_key, detach_thread);
    ntp_global_init();
    return JNI_VERSION_1_6;
}
